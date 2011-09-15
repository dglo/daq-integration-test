package icecube.daq.test;

import icecube.daq.eventBuilder.EBComponent;
import icecube.daq.eventBuilder.monitoring.MonitoringData;
import icecube.daq.io.DAQComponentOutputProcess;
import icecube.daq.io.DAQSourceIdOutputProcess;
import icecube.daq.io.PayloadReader;
import icecube.daq.juggler.component.DAQCompException;
import icecube.daq.juggler.component.DAQConnector;
import icecube.daq.payload.IByteBufferCache;
import icecube.daq.payload.ISourceID;
import icecube.daq.payload.IWriteablePayload;
import icecube.daq.payload.PayloadRegistry;
import icecube.daq.payload.SourceIdRegistry;
import icecube.daq.payload.impl.VitreousBufferCache;
import icecube.daq.sender.Sender;
import icecube.daq.splicer.SplicerException;
import icecube.daq.stringhub.StringHubComponent;
import icecube.daq.trigger.component.AmandaTriggerComponent;
import icecube.daq.trigger.component.IcetopTriggerComponent;
import icecube.daq.trigger.component.IniceTriggerComponent;
import icecube.daq.trigger.component.GlobalTriggerComponent;
import icecube.daq.trigger.component.TriggerComponent;
import icecube.daq.trigger.control.ITriggerManager;
import icecube.daq.trigger.exceptions.TriggerException;
import icecube.daq.util.DOMRegistry;
import icecube.daq.util.IDOMRegistry;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.nio.channels.SelectableChannel;
import java.nio.channels.Selector;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.zip.DataFormatException;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;

public abstract class DAQTestCase
    extends TestCase
{
    private static final MockAppender appender =
        new MockAppender();
        //new MockAppender(org.apache.log4j.Level.ALL).setVerbose(true);

    private static final int RUN_NUMBER = 1234;

    private StringHubComponent[] shComps;
    private IniceTriggerComponent iiComp;
    private IcetopTriggerComponent itComp;
    private AmandaTriggerComponent amComp;
    private GlobalTriggerComponent gtComp;
    private EBComponent ebComp;
    private MinimalServer minServer;
    private List<Pipe> pipeList;
    private WritableByteChannel amTail;

    public DAQTestCase(String name)
    {
        super(name);
    }

    abstract StringHubComponent[] buildStringHubComponents()
        throws DAQCompException, IOException;

    private void checkLogMessages()
    {
        for (int i = 0; i < appender.getNumberOfMessages(); i++) {
            String msg = (String) appender.getMessage(i);

            if (!(msg.startsWith("Clearing ") &&
                  msg.endsWith(" rope entries")) &&
                !msg.startsWith("Resetting counter ") &&
                !msg.startsWith("No match for timegate ") &&
                !msg.startsWith("Sending empty event for window") &&
                !msg.endsWith("does not exist!  Using current directory."))
            {
                fail("Bad log message#" + i + ": " + appender.getMessage(i));
            }
        }
        appender.clear();
    }

    private static List<Pipe> connectHubsAndEB(StringHubComponent[] shComps,
                                               TriggerComponent itComp,
                                               TriggerComponent iiComp,
                                               EBComponent ebComp,
                                               PayloadValidator validator)
        throws DAQCompException, IOException
    {
        List<Pipe> pipeList = new ArrayList<Pipe>();

        // connect SH hit to triggers
        for (int n = 0; n < 2; n++) {
            final boolean connectInIce = (n == 0);

            TriggerComponent trigComp;
            if (connectInIce) {
                trigComp = iiComp;
            } else {
                trigComp = itComp;
            }

            if (trigComp == null) {
                continue;
            }

            PayloadReader hitRdr = trigComp.getReader();
            IByteBufferCache cache = trigComp.getInputCache();

            for (int i = 0; i < shComps.length; i++) {
                int srcId = shComps[i].getHubId();

                if ((connectInIce &&
                     SourceIdRegistry.isIniceHubSourceID(srcId)) ||
                    (!connectInIce &&
                     SourceIdRegistry.isIcetopHubSourceID(srcId)))
                {
                    Pipe pipe =
                        DAQTestUtil.connectToReader(hitRdr, cache, false);
                    ((SelectableChannel) pipe.sink()).configureBlocking(false);

                    DAQComponentOutputProcess outProc =
                        shComps[i].getHitWriter();
                    outProc.addDataChannel(pipe.sink(), shComps[i].getCache());

                    pipeList.add(pipe);
                }
            }
        }

        // connect EB req to SH
        DAQSourceIdOutputProcess dest = ebComp.getRequestWriter();
        for (int i = 0; i < shComps.length; i++) {
            PayloadReader rdr = shComps[i].getRequestReader();
            Pipe pipe =
                DAQTestUtil.connectToReader(rdr, shComps[i].getCache(), false);
            ((SelectableChannel) pipe.sink()).configureBlocking(false);

            dest.addDataChannel(pipe.sink(),
                                new MockSourceID(shComps[i].getHubId()));
            pipeList.add(pipe);
        }

        // connect SH data to EB
        PayloadReader dataRdr = ebComp.getDataReader();
        for (int i = 0; i < shComps.length; i++) {
            int hubNum = shComps[i].getHubId() % 100;
            IByteBufferCache dataCache =
                shComps[i].getByteBufferCache(DAQConnector.TYPE_READOUT_DATA);
            DAQTestUtil.glueComponents("SH#" + hubNum + "->EB",
                                       shComps[i].getDataWriter(), dataCache,
                                       validator,
                                       ebComp.getDataReader(),
                                       ebComp.getDataCache());
        }

        return pipeList;
    }

    void destroyComponentIO(EBComponent ebComp,
                            GlobalTriggerComponent gtComp,
                            IcetopTriggerComponent itComp,
                            IniceTriggerComponent iiComp,
                            AmandaTriggerComponent amComp,
                            StringHubComponent[] shComps)
    {
        if (ebComp != null) {
            ebComp.getTriggerReader().destroyProcessor();
            ebComp.getRequestWriter().destroyProcessor();
            ebComp.getDataReader().destroyProcessor();
        }
        if (gtComp != null) {
            gtComp.getReader().destroyProcessor();
            gtComp.getWriter().destroyProcessor();
        }
        if (itComp != null) {
            itComp.getReader().destroyProcessor();
            itComp.getWriter().destroyProcessor();
        }
        if (iiComp != null) {
            iiComp.getReader().destroyProcessor();
            iiComp.getWriter().destroyProcessor();
        }
        if (amComp != null) {
            amComp.getReader().destroyProcessor();
            amComp.getWriter().destroyProcessor();
        }

        for (int i = 0; i < shComps.length; i++) {
            shComps[i].getHitWriter().destroyProcessor();
            shComps[i].getRequestReader().destroyProcessor();
            shComps[i].getDataWriter().destroyProcessor();
        }
    }

    abstract int getNumberOfAmandaTriggerSent();

    abstract int getNumberOfExpectedEvents();

    abstract void initialize(IDOMRegistry domRegistry)
        throws DataFormatException, IOException;

    abstract void initializeAmandaInput(WritableByteChannel amTail)
        throws IOException;

    void monitorEventBuilder(EBComponent comp, int maxTries)
    {
        boolean isFinished = false;
        long prevRcvd = 0;
        long prevQueued = 0;
        long prevSent = 0;
        int numTries = 0;

        PayloadReader gtRdr = comp.getTriggerReader();
        PayloadReader rdoutRdr = comp.getDataReader();
        MonitoringData monData = comp.getMonitoringData();

        while (numTries < maxTries) {
            if (!gtRdr.isRunning() && !rdoutRdr.isRunning()) {
                break;
            }

            final boolean isRunning = rdoutRdr.isRunning();
            final long numRcvd = gtRdr.getTotalRecordsReceived();
            final long numQueued = monData.getNumTriggerRequestsQueued() +
                monData.getNumReadoutsQueued();
            final long numSent = monData.getNumEventsSent();

            //System.err.println(toString() + " (#" + numTries + ")");

            boolean stagnant = true;
            if (isRunning && numRcvd > prevRcvd) {
                prevRcvd = numRcvd;
                stagnant = false;
            }

            if (numQueued > prevQueued) {
                prevQueued = numQueued;
                stagnant = false;
            }

            if (numSent > prevSent) {
                prevSent = numSent;
                stagnant = false;
            }

            if (!stagnant) {
                numTries = 0;
            } else {
                numTries++;
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException ie) {
                // ignore interrupts
            }
        }
    }

    private void monitorHub(StringHubComponent shComp, int hubNum,
                            boolean checkHits, int maxTries)
    {
        boolean isFinished = false;
        long prevHits = 0;
        long prevRcvd = 0;
        long prevQueued = 0;
        long prevSent = 0;
        int numTries = 0;

        DAQComponentOutputProcess dataOut = shComp.getDataWriter();
        DAQComponentOutputProcess hitOut = shComp.getHitWriter();
        PayloadReader reqRdr = shComp.getRequestReader();
        Sender sender = shComp.getSender();

        while (numTries < maxTries) {
            boolean stopped;
            if (checkHits) {
                stopped = hitOut.isStopped();
            } else {
                stopped = !reqRdr.isRunning() && dataOut.isStopped();
            }

            if (stopped) {
                break;
            }

            //System.err.println(toString() + " (#" + numTries + ")");

            boolean stagnant = true;
            if (checkHits) {
                long[] recSent = hitOut.getRecordsSent();

                long numHits = 0L;
                if (recSent != null) {
                    for (int i = 0; i < recSent.length; i++) {
                        numHits += recSent[i];
                    }
                }

                if (numHits > prevHits) {
                    prevHits = numHits;
                    stagnant = false;
                }
            } else {
                final boolean isRunning = reqRdr.isRunning();
                final long numRcvd = reqRdr.getTotalRecordsReceived();
                final long numQueued = sender.getNumHitsQueued() +
                    sender.getNumReadoutRequestsQueued();

                long[] recSent = dataOut.getRecordsSent();

                long numSent = 0L;
                if (recSent != null) {
                    for (int i = 0; i < recSent.length; i++) {
                        numSent += recSent[i];
                    }
                }

                if (isRunning && numRcvd > prevRcvd) {
                    prevRcvd = numRcvd;
                    stagnant = false;
                }

                if (numQueued > prevQueued) {
                    prevQueued = numQueued;
                    stagnant = false;
                }

                if (numSent > prevSent) {
                    prevSent = numSent;
                    stagnant = false;
                }
            }

            if (!stagnant) {
                numTries = 0;
            } else {
                numTries++;
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException ie) {
                // ignore interrupts
            }
        }
    }

    private void monitorTrigger(TriggerComponent comp, int maxTries)
    {
        boolean isFinished = false;
        int prevRcvd = 0;
        int prevProc = 0;
        long prevSent = 0;
        int numTries = 0;

        PayloadReader reader = comp.getReader();
        ITriggerManager trigMgr = comp.getTriggerManager();
        DAQComponentOutputProcess writer = comp.getWriter();

        while (numTries < maxTries) {
            if (!reader.isRunning() && writer.isStopped()) {
                break;
            }

            final boolean isRunning = reader.isRunning();
            final int numRcvd = (int) reader.getTotalRecordsReceived();
            final int numProc = trigMgr.getCount();
            final long numSent = comp.getPayloadsSent();

            //System.err.println(comp.toString() + " (#" + numTries + ")");

            boolean stagnant = true;
            if (isRunning && numRcvd > prevRcvd) {
                prevRcvd = numRcvd;
                stagnant = false;
            }

            if (numProc > prevProc) {
                prevProc = numProc;
                stagnant = false;
            }

            if (numSent > prevSent) {
                prevSent = numSent;
                stagnant = false;
            }

            if (!stagnant) {
                numTries = 0;
            } else {
                numTries++;
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException ie) {
                // ignore interrupts
            }
        }
    }

    abstract boolean needAmandaTrig();

    abstract void sendData(StringHubComponent[] shComps)
        throws DataFormatException, IOException;

    void setLogLevel(Level level)
    {
        appender.setVerbose(true);
        appender.setFlushMessages(false);
        appender.setLevel(level);
    }

    protected void setUp()
        throws Exception
    {
        super.setUp();

        DAQTestUtil.clearCachedChannels();

        appender.clear();

        BasicConfigurator.resetConfiguration();
        BasicConfigurator.configure(appender);
    }

    void setVerbose(boolean val)
    {
        appender.setVerbose(val);
    }

    protected void tearDown()
        throws Exception
    {
        DAQTestUtil.logOpenChannels();

        assertEquals("Bad number of log messages",
                     0, appender.getNumberOfMessages());

        if (ebComp != null) ebComp.closeAll();
        if (gtComp != null) gtComp.closeAll();
        if (amComp != null) amComp.closeAll();
        if (itComp != null) itComp.closeAll();
        if (iiComp != null) iiComp.closeAll();

        if (shComps != null) {
            for (int i = 0; i < shComps.length; i++) {
                shComps[i].closeAll();
            }
        }

        if (pipeList != null) {
            for (Pipe pipe : pipeList) {
                try { pipe.sink().close(); } catch (Exception ex) { }
                try { pipe.source().close(); } catch (Exception ex) { }
            }
        }

        if (amTail != null) {
            try { amTail.close(); } catch (Exception ex) { }
        }

        super.tearDown();
    }

    public void testEndToEnd()
        throws DAQCompException, DataFormatException, IOException,
               SplicerException, TriggerException
    {
        final int numEvents = getNumberOfExpectedEvents();
        final boolean dumpActivity = false;
        final boolean dumpSplicers = false;
        final boolean dumpBEStats = false;

        File cfgFile =
            DAQTestUtil.buildConfigFile(getClass().getResource("/").getPath(),
                                        "sps-icecube-amanda-008");

        IDOMRegistry domRegistry;
        try {
            domRegistry = DOMRegistry.loadRegistry(cfgFile.getParent());
        } catch (Exception ex) {
            throw new Error("Cannot load DOM registry", ex);
        }

        initialize(domRegistry);

        PayloadValidator validator = new GeneralValidator();

        // set up string hubs
        shComps = buildStringHubComponents();
        for (int i = 0; i < shComps.length; i++) {
            shComps[i].setGlobalConfigurationDir(cfgFile.getParent());
        }

        // check for required trigger components
        boolean foundHub = false;
        boolean needInIceTrig = false;
        boolean needIceTopTrig = false;
        for (StringHubComponent shComp : shComps) {
            int srcId = shComp.getHubId();
            if (SourceIdRegistry.isIniceHubSourceID(srcId)) {
                needInIceTrig = true;
            } else if (SourceIdRegistry.isIcetopHubSourceID(srcId)) {
                needIceTopTrig = true;
            } else {
                throw new Error("Cannot determine trigger for hub#" + srcId);
            }
            foundHub = true;
        }
        if (!foundHub) {
            throw new Error("No hubs found from " + cfgFile);
        } else if (!needInIceTrig && !needIceTopTrig) {
            throw new Error("No icetop or in-ice hubs found");
        }

        // set up event builder
        ebComp = new EBComponent(true);

        IByteBufferCache ebEvtCache =
            ebComp.getByteBufferCache(DAQConnector.TYPE_EVENT);
        MockDispatcher disp = new MockDispatcher(ebEvtCache);

        ebComp.setDispatcher(disp);
        ebComp.start(false);
        ebComp.setRunNumber(RUN_NUMBER);
        ebComp.setDispatchDestStorage(System.getProperty("java.io.tmpdir"));
        ebComp.setGlobalConfigurationDir(cfgFile.getParent());

        ebComp.configuring(cfgFile.getName());

        // set up global trigger
        gtComp = new GlobalTriggerComponent();
        gtComp.setGlobalConfigurationDir(cfgFile.getParent());
        gtComp.start(false);

        gtComp.configuring(cfgFile.getName());

        DAQTestUtil.glueComponents("GT->EB",
                                   gtComp.getWriter(), gtComp.getOutputCache(),
                                   validator,
                                   ebComp.getTriggerReader(),
                                   ebComp.getTriggerCache());

        // set up icetop trigger
        if (!needIceTopTrig) {
            itComp = null;
        } else {
            itComp = new IcetopTriggerComponent();
            itComp.setGlobalConfigurationDir(cfgFile.getParent());
            itComp.start(false);

            itComp.configuring(cfgFile.getName());

            DAQTestUtil.glueComponents("ITT->GT",
                                       itComp.getWriter(),
                                       itComp.getOutputCache(),
                                       validator,
                                       gtComp.getReader(),
                                       gtComp.getInputCache());
        }

        // set up in-ice trigger
        if (!needInIceTrig) {
            iiComp = null;
        } else {
            iiComp = new IniceTriggerComponent();
            iiComp.setGlobalConfigurationDir(cfgFile.getParent());
            iiComp.start(false);

            iiComp.configuring(cfgFile.getName());

            DAQTestUtil.glueComponents("IIT->GT",
                                       iiComp.getWriter(),
                                       iiComp.getOutputCache(),
                                       validator,
                                       gtComp.getReader(),
                                       gtComp.getInputCache());
        }

        if (!needAmandaTrig()) {
            amComp = null;
        } else {
            // build amanda server
            minServer = new MinimalServer();
            int port = minServer.getPort();

            // set up amanda trigger
            amComp = new AmandaTriggerComponent("localhost", port);
            amComp.setGlobalConfigurationDir(cfgFile.getParent());
            amComp.start(false);

            amComp.configuring(cfgFile.getName());

            DAQTestUtil.glueComponents("AM->GT",
                                       amComp.getWriter(),
                                       amComp.getOutputCache(),
                                       validator,
                                       gtComp.getReader(),
                                       gtComp.getInputCache());
        }

        // finish setup
        pipeList =
            connectHubsAndEB(shComps, itComp, iiComp, ebComp, validator);

        DAQTestUtil.startComponentIO(ebComp, gtComp, itComp, iiComp, amComp,
                                     shComps);

        // finish amanda initialization
        if (amComp != null) {
            amTail = minServer.acceptChannel();

            initializeAmandaInput(amTail);
        }

        // start sending input data
        sendData(shComps);

        ActivityMonitor activity =
            new ActivityMonitor(iiComp, itComp, amComp, gtComp, ebComp);
        activity.waitForStasis(10, 100, numEvents, dumpActivity, dumpSplicers);
        if (dumpBEStats) activity.dumpBackEndStats();

        for (int i = 0; i < shComps.length; i++) {
            int hubNum = shComps[i].getHubId() % 100;

            monitorHub(shComps[i], hubNum, true, 10);
            DAQTestUtil.waitUntilStopped(shComps[i].getHitWriter(), null,
                                         "SH#" + hubNum + "HitStop");
        }

        if (amComp != null) {
            monitorTrigger(amComp, 10);
            DAQTestUtil.waitUntilStopped(amComp.getReader(),
                                         amComp.getSplicer(), "AMStopMsg");
            DAQTestUtil.waitUntilStopped(amComp.getWriter(), null, "AMStopMsg");
        }

        if (itComp != null) {
            monitorTrigger(itComp, 10);
            DAQTestUtil.waitUntilStopped(itComp.getReader(),
                                         itComp.getSplicer(), "ITStopMsg");
            DAQTestUtil.waitUntilStopped(itComp.getWriter(), null, "ITStopMsg");
        }

        if (iiComp != null) {
            monitorTrigger(iiComp, 10);
            DAQTestUtil.waitUntilStopped(iiComp.getReader(),
                                         iiComp.getSplicer(), "IIStopMsg");
            DAQTestUtil.waitUntilStopped(iiComp.getWriter(), null, "IIStopMsg");
        }

        monitorTrigger(gtComp, 10);
        DAQTestUtil.waitUntilStopped(gtComp.getReader(), gtComp.getSplicer(),
                                     "GTStopMsg");
        DAQTestUtil.waitUntilStopped(gtComp.getWriter(), null, "GTStopMsg");

        for (int i = 0; i < shComps.length; i++) {
            int hubNum = shComps[i].getHubId() % 100;

            monitorHub(shComps[i], hubNum, false, 10);
            DAQTestUtil.waitUntilStopped(shComps[i].getDataWriter(), null,
                                         "SH#" + hubNum + "DataStop");
        }

        activity.waitForStasis(10, 100, numEvents, dumpActivity, dumpSplicers);
        if (dumpBEStats) activity.dumpBackEndStats();

        monitorEventBuilder(ebComp, 10);
        DAQTestUtil.waitUntilStopped(ebComp.getTriggerReader(), null,
                                     "EBStopMsg");
        DAQTestUtil.waitUntilStopped(ebComp.getRequestWriter(), null,
                                     "EBStopMsg");
        DAQTestUtil.waitUntilStopped(ebComp.getDataReader(),
                                     ebComp.getDataSplicer(), "EBStopMsg");

        while (ebComp.isBackEndRunning()) {
            Thread.yield();
        }

        activity.waitForStasis(10, 100, numEvents, dumpActivity, dumpSplicers);
        if (dumpBEStats) activity.dumpBackEndStats();

        if (false) {
            if (iiComp != null) System.err.println("II " + iiComp);
            if (itComp != null) System.err.println("IT " + itComp);
            if (amComp != null) System.err.println("AM " + amComp);
            System.err.println("GT " + gtComp);
            System.err.println("EB " + ebComp);
        }

        if (disp.getNumberOfBadEvents() > 0) {
            fail(disp.toString());
        }

        MonitoringData monData = ebComp.getMonitoringData();

        assertEquals("Event builder dropped some global triggers",
                     gtComp.getPayloadsSent() - 1,
                     monData.getNumTriggerRequestsReceived());

        assertEquals("#trigger requests doesn't match # events (" +
                     monData.getNumTriggerRequestsQueued() + " TRs queued, " +
                     monData.getNumEventsSent() + " evts sent)",
                     monData.getNumTriggerRequestsReceived(),
                     (monData.getNumTriggerRequestsQueued() +
                      monData.getNumEventsSent()));

        System.err.println("XXX Ignoring extra log msgs");
        appender.clear();

        DAQTestUtil.checkCaches(ebComp, gtComp, itComp, iiComp, amComp,
                                shComps);
        destroyComponentIO(ebComp, gtComp, itComp, iiComp, amComp, shComps);
    }
}
