package icecube.daq.test;

import icecube.daq.common.MockAppender;
import icecube.daq.eventBuilder.EBComponent;
import icecube.daq.eventBuilder.GlobalTriggerReader;
import icecube.daq.eventBuilder.RequestPayloadOutputEngine;
import icecube.daq.eventBuilder.SPDataAnalysis;
import icecube.daq.eventBuilder.backend.EventBuilderBackEnd;
import icecube.daq.eventBuilder.monitoring.MonitoringData;
import icecube.daq.io.DAQComponentIOProcess;
import icecube.daq.io.Dispatcher;
import icecube.daq.io.FileDispatcher;
import icecube.daq.juggler.component.DAQCompException;
import icecube.daq.juggler.component.DAQConnector;
import icecube.daq.payload.IByteBufferCache;
import icecube.daq.payload.IReadoutRequest;
import icecube.daq.payload.IReadoutRequestElement;
import icecube.daq.payload.ISourceID;
import icecube.daq.payload.IWriteablePayload;
import icecube.daq.payload.PayloadRegistry;
import icecube.daq.payload.SourceIdRegistry;
import icecube.daq.payload.impl.TriggerRequest;
import icecube.daq.payload.impl.VitreousBufferCache;
import icecube.daq.splicer.HKN1Splicer;
import icecube.daq.splicer.Splicer;
import icecube.daq.util.DOMRegistryException;
import icecube.daq.util.DOMRegistryFactory;
import icecube.daq.util.DOMInfo;
import icecube.daq.util.IDOMRegistry;
import icecube.daq.util.LocatePDAQ;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.BasicConfigurator;

/**
 * Test event builder.
 */
public class EventBuilderEndToEndTest
    extends TestCase
{
    private static final MockAppender appender =
        new MockAppender();
        //new MockAppender(org.apache.log4j.Level.ALL).setVerbose(true);

    private static final int SIMHUB_ID =
        SourceIdRegistry.SIMULATION_HUB_SOURCE_ID;

    private static final long TIME_BASE = 100L;
    private static final int TRIG_STEP = 10;
    private static final int NUM_TRIGGERS = 20;
    private static final int NUM_HUBS = 5;

    private static final int RUN_NUMBER = 1234;

    private static int trigUID = 1;

    private static ByteBuffer trigBuf;

    private Pipe gtPipe;

    /**
     * Test event builder.
     *
     * @param name class name
     */
    public EventBuilderEndToEndTest(String name)
    {
        super(name);
    }

    private void checkLogMessages()
    {
        try {
            for (int i = 0; i < appender.getNumberOfMessages(); i++) {
                String msg = (String) appender.getMessage(i);

                if (msg.startsWith("clearCache() called for ")) {
                    System.err.println(msg);
                    continue;
                }

                if (!(msg.startsWith("Clearing ") &&
                      msg.endsWith(" rope entries")) &&
                    !msg.startsWith("Resetting counter ") &&
                    !msg.startsWith("No match for timegate ") &&
                    !msg.startsWith("Sending empty event for window") &&
                    !msg.endsWith(" not exist or is not writable!" +
                                  "  Using current directory.") &&
                    !msg.equals("Cannot write to " +
                                FileDispatcher.DISPATCH_DEST_STORAGE + "!") &&
                    !msg.startsWith("Couldn't move temp file ") &&
                    !msg.equals("The last temp-physics file was not moved" +
                                " to the dispatch storage!!!") &&
                    !msg.startsWith("Couldn't stop dispatcher (") &&
                    !msg.startsWith("Switching from run ") &&
                    !msg.startsWith("GoodTime Stats: "))
                {
                    fail("Bad log message#" + i + ": " +
                         appender.getMessage(i));
                }
            }
        } finally {
            appender.clear();
        }
    }

    private List<HitData> getHitList(IDOMRegistry domRegistry)
        throws DOMRegistryException
    {
        final long firstTime = TIME_BASE;
        final long timeRange = TRIG_STEP * NUM_TRIGGERS;

        final int numHits = NUM_TRIGGERS * 10;
        final long hitStep = timeRange / (long) numHits;

        ArrayList<HitData> list = new ArrayList<HitData>();

        HitData.setDefaultTriggerType(0);
        HitData.setDefaultConfigId(0);
        HitData.setDefaultTriggerMode(0);
        HitData.setDOMRegistry(domRegistry);

        ArrayList<DOMInfo> realDOMs = new ArrayList<DOMInfo>();
        for (DOMInfo dom : domRegistry.allDOMs()) {
            if (dom.isRealDOM()) {
                if (dom.isRealDOM()) {
                    realDOMs.add(dom);
                }
            }
        }

        long[] domIdList = new long[realDOMs.size()];

        int nextIdx = 0;
        for (DOMInfo dom : realDOMs) {
            domIdList[nextIdx++] = dom.getNumericMainboardId();
        }

        long time = TIME_BASE;
        for (int i = 0; i < numHits; i++) {
            long domId = domIdList[i % domIdList.length];
            list.add(new HitData(time, SIMHUB_ID + (i % NUM_HUBS), domId));

            time += hitStep;
        }

        return list;
    }

    private static List<ISourceID> getSourceIds(List<HitData> hitList)
    {
        HashMap<ISourceID, ISourceID> map = new HashMap<ISourceID, ISourceID>();

        for (HitData hd : hitList) {
            MockSourceID newSrc = new MockSourceID(hd.getSourceID());

            if (!map.containsKey(newSrc)) {
                map.put(newSrc, newSrc);
            }
        }

        return new ArrayList(map.keySet());
    }

    public long sendGlobalTriggers(WritableByteChannel chan, long timeBase,
                                   long timeStep, int numTriggers)
        throws IOException
    {
        long curTime = timeBase;

        int trigType = 0;
        for (int i = 0; i < numTriggers; i++) {
            long nextTime = curTime + timeStep;

            if (trigType < 7 || trigType > 11) {
                trigType = 7;
            }

            sendTrigger(chan, curTime, nextTime - 1, trigType,
                        SourceIdRegistry.GLOBAL_TRIGGER_SOURCE_ID);

            curTime = nextTime;
            trigType++;
        }

        return curTime;
    }

    static void sendTrigger(WritableByteChannel chan, long firstTime,
                            long lastTime, int trigType, int srcId)
        throws IOException
    {
        final int bufLen = 104;

        if (trigBuf == null) {
            trigBuf = ByteBuffer.allocate(bufLen);
        }

        synchronized (trigBuf) {
            final int uid = trigUID++;

            int cfgId = 0;

            // envelope
            trigBuf.putInt(0, bufLen);
            trigBuf.putInt(4, PayloadRegistry.PAYLOAD_ID_TRIGGER_REQUEST);
            trigBuf.putLong(8, firstTime);

            // trigger record
            trigBuf.putShort(16, TriggerRequest.RECORD_TYPE);
            trigBuf.putInt(18, uid);
            trigBuf.putInt(22, trigType);
            trigBuf.putInt(26, cfgId);
            trigBuf.putInt(30, srcId);
            trigBuf.putLong(34, firstTime);
            trigBuf.putLong(42, lastTime);

            // readout request
            trigBuf.putShort(50, (short) 0xff);
            trigBuf.putInt(52, uid);
            trigBuf.putInt(56, srcId);
            trigBuf.putInt(60, 1);

            // readout request element
            trigBuf.putInt(64, IReadoutRequestElement.READOUT_TYPE_GLOBAL);
            trigBuf.putInt(68, -1);
            trigBuf.putLong(72, firstTime);
            trigBuf.putLong(80, lastTime);
            trigBuf.putLong(88, -1L);

            // composite header
            trigBuf.putInt(96, 8);
            trigBuf.putShort(100, (short) 1);
            trigBuf.putShort(102, (short) 0);

            trigBuf.position(0);
            chan.write(trigBuf);
        }
    }

    @Override
    protected void setUp()
        throws Exception
    {
        super.setUp();

        BasicConfigurator.resetConfiguration();
        BasicConfigurator.configure(appender);

        // ensure LocatePDAQ uses the test version of the config directory
        File configDir =
            new File(getClass().getResource("/config").getPath());
        if (!configDir.exists()) {
            throw new IllegalArgumentException("Cannot find config" +
                                               " directory under " +
                                               getClass().getResource("/"));
        }
        System.setProperty(LocatePDAQ.CONFIG_DIR_PROPERTY,
                           configDir.getAbsolutePath());
    }

    public static Test suite()
    {
        return new TestSuite(EventBuilderEndToEndTest.class);
    }

    private void switchToNewRun(EBComponent comp, int runNum)
        throws DAQCompException
    {
        trigUID = 1;
        comp.switching(runNum);
    }

    @Override
    protected void tearDown()
        throws Exception
    {
        try {
            appender.assertNoLogMessages();

            if (gtPipe != null) {
                try {
                    gtPipe.sink().close();
                } catch (IOException ioe) {
                    // ignore errors
                }
                try {
                    gtPipe.source().close();
                } catch (IOException ioe) {
                    // ignore errors
                }
            }
        } finally {
            System.clearProperty(LocatePDAQ.CONFIG_DIR_PROPERTY);
        }

        super.tearDown();
    }

    public void testEndToEnd()
        throws  DAQCompException, DOMRegistryException, IOException
    {
        final boolean dumpActivity = false;
        final boolean dumpSplicers = false;
        final boolean dumpBEStats = false;

        File cfgFile =
            DAQTestUtil.buildConfigFile(getClass().getResource("/").getPath(),
                                        "default-dom-geometry.xml");

        IDOMRegistry domRegistry;
        try {
            domRegistry = DOMRegistryFactory.load(cfgFile.getParent());
        } catch (Exception ex) {
            throw new Error("Cannot load DOM registry", ex);
        }

        // get list of all hits
        List<HitData> hitList = getHitList(domRegistry);

        // set up event builder
        EBComponent comp = new EBComponent();
        comp.setValidateEvents(true);
        comp.setGlobalConfigurationDir(cfgFile.getParent());
        comp.initialize();

        IByteBufferCache evtDataCache =
            comp.getByteBufferCache(DAQConnector.TYPE_EVENT);
        MockDispatcher dispatcher = new MockDispatcher(evtDataCache);
        comp.setDispatcher(dispatcher);

        comp.start(false);

        gtPipe = DAQTestUtil.connectToReader(comp.getTriggerReader(),
                                             comp.getTriggerCache());

        RequestToDataBridge.createLinks(comp.getRequestWriter(), null,
                                        comp.getDataReader(),
                                        comp.getDataCache(), hitList);

        DAQTestUtil.startComponentIO(comp, null, null, null, null, RUN_NUMBER);

        sendGlobalTriggers(gtPipe.sink(), TIME_BASE, TRIG_STEP, NUM_TRIGGERS);

        ActivityMonitor activity = new ActivityMonitor(null, null, null, comp);
        activity.waitForStasis(10, 1000, NUM_TRIGGERS - 1, dumpActivity,
                               dumpSplicers);
        if (dumpBEStats) activity.dumpBackEndStats();

        DAQTestUtil.sendStopMsg(gtPipe.sink());

        activity.waitForStasis(10, 1000, NUM_TRIGGERS - 1, dumpActivity,
                               dumpSplicers);
        if (dumpBEStats) activity.dumpBackEndStats();

        DAQTestUtil.checkCaches(comp, null, null, null, null);
        DAQTestUtil.destroyComponentIO(comp, null, null, null, null);

        if (appender.getLevel().equals(org.apache.log4j.Level.ALL)) {
            appender.clear();
        } else {
            checkLogMessages();
        }
    }

    public void testSwitchRun()
        throws  DAQCompException, DOMRegistryException, IOException
    {
        final boolean dumpActivity = false;
        final boolean dumpSplicers = false;
        final boolean dumpBEStats = false;

        File cfgFile =
            DAQTestUtil.buildConfigFile(getClass().getResource("/").getPath(),
                                        "default-dom-geometry.xml");

        IDOMRegistry domRegistry;
        try {
            domRegistry = DOMRegistryFactory.load(cfgFile.getParent());
        } catch (Exception ex) {
            throw new Error("Cannot load DOM registry", ex);
        }

        List<HitData> hitList = getHitList(domRegistry);

        EBComponent comp = new EBComponent();
        comp.setValidateEvents(true);
        comp.setGlobalConfigurationDir(cfgFile.getParent());
        comp.initialize();

        IByteBufferCache evtDataCache =
            comp.getByteBufferCache(DAQConnector.TYPE_EVENT);
        MockDispatcher dispatcher = new MockDispatcher(evtDataCache);
        comp.setDispatcher(dispatcher);

        comp.start(false);

        gtPipe = DAQTestUtil.connectToReader(comp.getTriggerReader(),
                                             comp.getTriggerCache());

        RequestToDataBridge.createLinks(comp.getRequestWriter(), null,
                                        comp.getDataReader(),
                                        comp.getDataCache(), hitList);

        DAQTestUtil.startComponentIO(comp, null, null, null, null,
                                     RUN_NUMBER);

        long curTime = sendGlobalTriggers(gtPipe.sink(), TIME_BASE, TRIG_STEP,
                                          NUM_TRIGGERS / 2);

        ActivityMonitor activity =
            new ActivityMonitor(null, null, null, comp);
        activity.waitForStasis(10, 1000, NUM_TRIGGERS - 1, dumpActivity,
                               dumpSplicers);
        if (dumpBEStats) activity.dumpBackEndStats();

        switchToNewRun(comp, RUN_NUMBER + 4);

        sendGlobalTriggers(gtPipe.sink(), curTime, TRIG_STEP, NUM_TRIGGERS / 2);

        activity.waitForStasis(10, 1000, NUM_TRIGGERS - 1, dumpActivity,
                               dumpSplicers);
        if (dumpBEStats) activity.dumpBackEndStats();

        DAQTestUtil.sendStopMsg(gtPipe.sink());

        activity.waitForStasis(10, 1000, NUM_TRIGGERS - 1, dumpActivity,
                               dumpSplicers);
        if (dumpBEStats) activity.dumpBackEndStats();

        DAQTestUtil.checkCaches(comp, null, null, null, null);
        DAQTestUtil.destroyComponentIO(comp, null, null, null, null);

        if (appender.getLevel().equals(org.apache.log4j.Level.ALL)) {
            appender.clear();
        } else {
            checkLogMessages();
        }
    }

    private void waitForEvents(EBComponent comp, int numEvents)
    {
        waitForEvents(comp, numEvents, 2000);
    }

    private void waitForEvents(EBComponent comp, int numEvents, int reps)
    {
        for (int i = 0; i < reps && comp.getEventsSent() < numEvents; i++) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException ie) {
                // ignore interrupts
            }
        }

        assertFalse("Expected " + numEvents + " events but only received " +
                    comp.getEventsSent(), comp.getEventsSent() < numEvents);
    }

    public static void main(String[] args)
    {
        TestRunner.run(suite());
    }
}
