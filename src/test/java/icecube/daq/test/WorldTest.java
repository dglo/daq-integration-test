package icecube.daq.test;

import icecube.daq.common.MockAppender;
import icecube.daq.eventBuilder.EBComponent;
import icecube.daq.eventBuilder.GlobalTriggerReader;
import icecube.daq.eventBuilder.SPDataAnalysis;
import icecube.daq.eventBuilder.backend.EventBuilderBackEnd;
import icecube.daq.eventBuilder.monitoring.MonitoringData;
import icecube.daq.juggler.component.DAQCompException;
import icecube.daq.juggler.component.IComponent;
import icecube.daq.payload.IByteBufferCache;
import icecube.daq.payload.ISourceID;
import icecube.daq.payload.IUTCTime;
import icecube.daq.payload.PayloadChecker;
import icecube.daq.payload.PayloadFormatException;
import icecube.daq.payload.PayloadRegistry;
import icecube.daq.payload.SourceIdRegistry;
import icecube.daq.payload.impl.TriggerRequest;
import icecube.daq.payload.impl.VitreousBufferCache;
import icecube.daq.splicer.HKN1Splicer;
import icecube.daq.splicer.Splicer;
import icecube.daq.splicer.SplicerException;
import icecube.daq.splicer.StrandTail;
import icecube.daq.trigger.component.IniceTriggerComponent;
import icecube.daq.trigger.component.GlobalTriggerComponent;
import icecube.daq.trigger.config.TriggerReadout;
import icecube.daq.trigger.control.TriggerManager;
import icecube.daq.trigger.exceptions.TriggerException;
import icecube.daq.util.DOMRegistryFactory;
import icecube.daq.util.IDOMRegistry;
import icecube.daq.util.LocatePDAQ;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

import org.apache.log4j.BasicConfigurator;

public class WorldTest
    extends TestCase
{
    private static final MockAppender appender =
        new MockAppender(/*org.apache.log4j.Level.ALL*/)/*.setVerbose(true)*/;

    private static final int BASE_SIMHUB_ID =
        SourceIdRegistry.SIMULATION_HUB_SOURCE_ID;

    private static final MockSourceID INICE_TRIGGER_SOURCE_ID =
        new MockSourceID(SourceIdRegistry.INICE_TRIGGER_SOURCE_ID);

    private static final int NUM_HUBS = 5;

    private static final int RUN_NUMBER = 1234;

    private static final String DISPATCH_DEST =
        System.getProperty("java.io.tmpdir");

    private IniceTriggerComponent iiComp;
    private GlobalTriggerComponent gtComp;
    private EBComponent ebComp;

    private Pipe[] iiTails;

    private static final int[] hubId = new int[] {
        21, 29, 30, 38, 39, 40, 46, 47, 48,
        49, 50, 56, 57, 59, 65, 66, 67, 72, 73
    };

    private static final long[][] hubDOM = new long[][] {
        { 0x38ae7fdfc4c7L, 0x423ed83846c3L, 0xd638529ba5adL,
          0x6f242f105485L, 0xacf3852b67e7L, 0x858a79abc807L },
        { 0x9ef3fedb9d36L, 0x396a5d961de1L, 0xa59c9e8c40b6L,
          0x6e41ab3703aaL, 0x1551e2c72d57L, 0xb897262bf58aL },
        { 0x1cbe412e2d37L, 0xbb6614295b8cL, 0xfac5284c8165L,
          0xb1fc89162c0bL, 0x724f1ae21fe6L, 0x58a90e542f8eL },
        { 0x4d983ea5297aL, 0x6432e28f770fL, 0xa0d899b577cbL,
          0x12a8d213e2acL, 0x798e62def158L, 0x811227c3746cL },
        { 0x19a31737fcbcL, 0x3f48845900d0L, 0xf4c0db8e6918L,
          0xba3dbfcffc51L, 0xd815cc08657fL, 0x93fdad986c6fL },
        { 0xe2e44c2f3563L, 0x17b7f9bc90f2L, 0x488e1c575627L,
          0xa40f157a0658L, 0x80134b136c39L, 0xd8010bc749c2L },
        { 0x20dca7d5a9e5L, 0xfeb392361dbdL, 0xf7e6ff439090L,
          0xf634ed54c5f0L, 0x3ee49849ffa6L, 0xa979e9acaf2eL },
        { 0x6e9d3e508910L, 0xed17776c9aa8L, 0x8e7008deff55L,
          0x5140bda6e07bL, 0x43a20bfe10d6L, 0x6aa68f2742c8L },
        { 0x4853a9933e7dL, 0x166817e1fbd9L, 0xbbf321953f5aL,
          0xf578db9a8423L, 0x5f6cd6cec834L, 0x55c986ace722L },
        { 0x006714f851d4L, 0xe6e30655009bL, 0x8bbbb008b86fL,
          0x7a06273239a8L, 0x6d7d54d2f112L, 0xf7809fdb01c0L },
        { 0xb2c4461c8b5fL, 0x9d5d0842334eL, 0xaaaabc1deec3L,
          0x9662aea5669eL, 0x137b441e8e46L, 0x4baa79b64278L },
        { 0x178fb493ef75L, 0xd7f8f7bfdf5cL, 0xed02ce817a2eL,
          0x4a3e558dbb75L, 0x9a4c2fc88494L, 0x03f964a55891L },
        { 0x919d4a9a9b02L, 0xac4bf0dc3520L, 0x7c113f8e4709L,
          0xca64bc22dac2L, 0x529b0aea563eL, 0x4b9b86a719c4L },
        { 0x83d8f521b859L, 0xe6dc25b457fdL, 0x73659395df5eL,
          0x26c8d1767fceL, 0xcd573e1e4b1aL, 0x02496b7fcac7L },
        { 0x0923e78e8b10L, 0x45277d29f268L, 0x4d5e6c4d16a6L,
          0xdb32d1a4bdb4L, 0x70e229a3d5ebL, 0xfc2bb7c46ce5L },
        { 0xa3ae4b646a70L, 0x3096fbff3f93L, 0x43149cdc4362L,
          0x667f78126c95L, 0xc09ad7a7ea16L, 0x581a272aeb03L },
        { 0x3f028cb686b7L, 0xd51568afe402L, 0x35daf4dd3653L,
          0x8a3dc4b3c68eL, 0x091be8177ab9L, 0x1713d9af3072L },
        { 0x95a5e41f1dfdL, 0xcb127bc2ed6bL, 0xd6ab8075e2fbL,
          0x8e23c7c0cdc1L, 0x46f402ad2d21L, 0x03728e4a666dL },
        { 0x02aeeef35302L, 0x97785ea71b24L, 0x144211cb2ed5L,
          0xf7c070310592L, 0x94034ed50ac9L, 0xae92b55a541fL },
    };

    public WorldTest(String name)
    {
        super(name);
    }

    private static ByteBuffer buildHit(long time, int recType, int cfgId,
                                       int srcId, long domId, int mode)
    {
        final int bufLen = 38;

        ByteBuffer hitBuf = ByteBuffer.allocate(bufLen);

        hitBuf.putInt(0, bufLen);
        hitBuf.putInt(4, PayloadRegistry.PAYLOAD_ID_SIMPLE_HIT);
        hitBuf.putLong(8, time);

        hitBuf.putInt(16, recType);
        hitBuf.putInt(20, cfgId);
        hitBuf.putInt(24, srcId);
        hitBuf.putLong(28, domId);
        hitBuf.putShort(36, (short) mode);

        hitBuf.position(0);

        return hitBuf;
    }

    private static ByteBuffer buildTrigger(int uid, long firstTime,
                                           long lastTime, int trigType,
                                           int cfgId, int srcId, int rrUID,
                                           int rrSrcId)
    {
        final int bufLen = 104;

        ByteBuffer trigBuf = ByteBuffer.allocate(bufLen);

        trigBuf.putInt(0, bufLen);
        trigBuf.putInt(4, PayloadRegistry.PAYLOAD_ID_TRIGGER_REQUEST);
        trigBuf.putLong(8, firstTime);

        trigBuf.putShort(16, TriggerRequest.RECORD_TYPE);
        trigBuf.putInt(18, uid);
        trigBuf.putInt(22, trigType);
        trigBuf.putInt(26, cfgId);
        trigBuf.putInt(30, srcId);
        trigBuf.putLong(34, firstTime);
        trigBuf.putLong(42, lastTime);

        trigBuf.putShort(50, (short) 0xff);
        trigBuf.putInt(52, rrUID);
        trigBuf.putInt(56, rrSrcId);
        trigBuf.putInt(60, 1);

        trigBuf.putInt(96, 8);
        trigBuf.putShort(100, (short) 1);
        trigBuf.putShort(102, (short) 0);

        trigBuf.position(0);

        return trigBuf;
    }

    private void checkLogMessages()
    {
        try {
            for (int i = 0; i < appender.getNumberOfMessages(); i++) {
                String msg = (String) appender.getMessage(i);

                if (!(msg.startsWith("Clearing ") &&
                      msg.endsWith(" rope entries")) &&
                    !msg.startsWith("Resetting counter ") &&
                    !msg.startsWith("No match for timegate ") &&
                    !msg.startsWith("Sending empty event for window") &&
                    !msg.startsWith("Couldn't move temp file ") &&
                    !msg.endsWith(" not exist!  Using current directory."))
                {
                    fail("Bad log message#" + i + ": " +
                         appender.getMessage(i));
                }
            }
        } finally {
            appender.clear();
        }
    }

    private static ArrayList<HitData> getInIceHits(IDOMRegistry domRegistry,
                                                   int numEvents)
    {
        ArrayList<HitData> list =
            new ArrayList<HitData>();

        HitData.setDefaultTriggerType(2);
        HitData.setDefaultConfigId(0);
        HitData.setDefaultTriggerMode(2);
        HitData.setDOMRegistry(domRegistry);

        // these are trigger parameters
        final int numHitsPerWindow = 8;
        final int timeWindow = 5000;
        final int readoutWindow = 200000;

        final int numDOMsPerHub = 6;
        final int totHubDOMs = hubId.length * numDOMsPerHub;

        long nextTime = 100000L;
        long timeStep = timeWindow / (numHitsPerWindow + 1);
        for (int e = 0, d = 0; e < numEvents; e++) {
            long first = nextTime;
            for (int h = 0; h < hubId.length + numHitsPerWindow; h++, d++) {
                final int hubIdx = d % hubId.length;
                final int domIdx = (d / hubId.length) % numDOMsPerHub;

                int hub = 12000 + hubId[hubIdx];
                long dom = hubDOM[hubIdx][domIdx];
                list.add(new HitData(nextTime, hub, dom));
                nextTime += timeStep;
            }
            long last = nextTime - timeStep;

            nextTime += readoutWindow;
        }

        return list;
    }

    private void sendHits(List<ISourceID> idList, List<HitData> hitList,
                          int startIndex, int numToSend)
        throws IOException
    {
        ByteBuffer simpleBuf = ByteBuffer.allocate(HitData.SIMPLE_LENGTH);

        for (int h = 0; h < numToSend; h++) {
            HitData hd = hitList.get(startIndex + h);

            simpleBuf.clear();
            hd.putSimple(simpleBuf);
            simpleBuf.flip();

            boolean written = false;
            for (int i = 0; i < iiTails.length; i++) {
                ISourceID srcId = idList.get(i);
                if (srcId.getSourceID() == hd.getSourceID()) {
                    iiTails[i].sink().write(simpleBuf);
                    written = true;
                    break;
                }
            }

            if (!written) {
                fail("Couldn't write to source " + hd.getSourceID());
            }
        }
    }

    @Override
    protected void setUp()
        throws Exception
    {
        super.setUp();

        DAQTestUtil.clearCachedChannels();

        appender.clear();

        BasicConfigurator.resetConfiguration();
        BasicConfigurator.configure(appender);
    }

    public static Test suite()
    {
        return new TestSuite(WorldTest.class);
    }

    private void switchToNewRun(int runNum)
        throws DAQCompException
    {
        iiComp.switching(runNum);
        gtComp.switching(runNum);
        ebComp.switching(runNum);
        PayloadChecker.setRunNumber(runNum);
    }

    @Override
    protected void tearDown()
        throws Exception
    {
        try {
            appender.assertNoLogMessages();

            for (IComponent comp : new IComponent[] { ebComp, gtComp, iiComp }) {
                if (comp != null) {
                    try {
                        comp.closeAll();
                    } catch (IOException ioe) {
                        System.err.println("Failed to close " + comp);
                        ioe.printStackTrace();
                    }
                }
            }
        } finally {
            DAQTestUtil.removeDispatchedFiles(DISPATCH_DEST);
        }

        if (iiTails != null) {
            DAQTestUtil.closePipeList(iiTails);
        }

        DAQTestUtil.logOpenChannels();

        PayloadChecker.clearRunNumber();

        System.clearProperty(LocatePDAQ.CONFIG_DIR_PROPERTY);

        super.tearDown();
    }

    public void testEndToEnd()
        throws DAQCompException, IOException, PayloadFormatException,
               SplicerException, TriggerException
    {
        final boolean dumpActivity = false;
        final boolean dumpSplicers = false;
        final boolean dumpBEStats = false;

        final int numEvents = 100;

        File cfgFile =
            DAQTestUtil.buildConfigFile(getClass().getResource("/").getPath(),
                                        "sps-2013-no-physminbias-001");

        System.setProperty(LocatePDAQ.CONFIG_DIR_PROPERTY,
                           cfgFile.getParent());

        IDOMRegistry domRegistry;
        try {
            domRegistry = DOMRegistryFactory.load(cfgFile.getParent());
        } catch (Exception ex) {
            throw new Error("Cannot load DOM registry", ex);
        }

        // get list of all hits
        List<HitData> hitList = getInIceHits(domRegistry, numEvents);

        PayloadValidator validator = new TriggerValidator();

        PayloadChecker.setRunNumber(RUN_NUMBER);

        // set up event builder
        ebComp = new EBComponent();
        ebComp.setValidateEvents(true);
        ebComp.setDispatchDestStorage(DISPATCH_DEST);
        ebComp.setGlobalConfigurationDir(cfgFile.getParent());
        ebComp.setAlerter(new MockAlerter());
        ebComp.initialize();

        ebComp.start(false);

        Map<ISourceID, RequestToDataBridge> bridgeMap =
            RequestToDataBridge.createLinks(ebComp.getRequestWriter(), null,
                                            ebComp.getDataReader(),
                                            ebComp.getDataCache(), hitList);

        List<ISourceID> idList = new ArrayList<ISourceID>(bridgeMap.keySet());

        // set up global trigger
        gtComp = new GlobalTriggerComponent();
        gtComp.setGlobalConfigurationDir(cfgFile.getParent());
        gtComp.setAlerter(new MockAlerter());
        gtComp.initialize();
        gtComp.start(false);

        gtComp.configuring(cfgFile.getName());

        DAQTestUtil.glueComponents("GT->EB",
                                   gtComp.getWriter(), gtComp.getOutputCache(),
                                   validator,
                                   ebComp.getTriggerReader(),
                                   ebComp.getTriggerCache());

        // set up in-ice trigger
        iiComp = new IniceTriggerComponent();
        iiComp.setGlobalConfigurationDir(cfgFile.getParent());
        iiComp.setAlerter(new MockAlerter());
        iiComp.initialize();
        iiComp.start(false);

        iiComp.configuring(cfgFile.getName());

        DAQTestUtil.glueComponents("IIT->GT",
                                   iiComp.getWriter(), iiComp.getOutputCache(),
                                   validator,
                                   gtComp.getReader(), gtComp.getInputCache());

        iiTails = DAQTestUtil.connectToReader(iiComp.getReader(),
                                              iiComp.getInputCache(),
                                              idList.size());

        DAQTestUtil.startComponentIO(ebComp, gtComp, null, iiComp, null,
                                     RUN_NUMBER, IComponent.DOMMODE_NORMAL);

        ActivityMonitor activity =
            new ActivityMonitor(iiComp, null, gtComp, ebComp);

        sendHits(idList, hitList, 0, hitList.size());

        activity.waitForStasis(10, 1000, numEvents, dumpActivity,
                               dumpSplicers);
        if (dumpBEStats) activity.dumpBackEndStats();

        //assertEquals("Global trigger/event mismatch",
        //             gtComp.getPayloadsSent() - 1, ebComp.getEventsSent());

        DAQTestUtil.sendStops(iiTails);

        activity.waitForStasis(10, 1000, numEvents, dumpActivity,
                               dumpSplicers);
        if (dumpBEStats) activity.dumpBackEndStats();

        int ebRunChk = 0;
        while (ebComp.isBackEndRunning() && ebRunChk++ < 10) {
            Thread.yield();
        }

        activity.waitForStasis(10, 1000, numEvents, dumpActivity,
                               dumpSplicers);
        if (dumpBEStats) activity.dumpBackEndStats();

        assertEquals("Missing in-ice trigger requests",
                     numEvents + 1, iiComp.getPayloadsSent());
        assertEquals("Missing global trigger requests",
                     numEvents + 1, gtComp.getPayloadsSent());
        assertEquals("Missing trigger requests in eventBuilder",
                     numEvents, ebComp.getTriggerRequestsReceived());
        assertEquals("Missing events", numEvents, ebComp.getEventsSent());

        DAQTestUtil.checkCaches(ebComp, gtComp, null, iiComp, null);
        DAQTestUtil.destroyComponentIO(ebComp, gtComp, null, iiComp, null);

        if (appender.getNumberOfMessages() > 0) {
            System.err.println("XXX Ignoring extra log msgs");
            appender.clear();
        }
    }

    public void testSwitchRun()
        throws DAQCompException, IOException, PayloadFormatException,
               SplicerException, TriggerException
    {
        final boolean dumpActivity = false;
        final boolean dumpSplicers = false;
        final boolean dumpBEStats = false;

        final int numEvents = 100;

        File cfgFile =
            DAQTestUtil.buildConfigFile(getClass().getResource("/").getPath(),
                                        "sps-2013-no-physminbias-001");

        IDOMRegistry domRegistry;
        try {
            domRegistry = DOMRegistryFactory.load(cfgFile.getParent());
        } catch (Exception ex) {
            throw new Error("Cannot load DOM registry", ex);
        }

        System.setProperty(LocatePDAQ.CONFIG_DIR_PROPERTY,
                           cfgFile.getParent());

        // get list of all hits
        List<HitData> hitList = getInIceHits(domRegistry, numEvents);

        PayloadValidator validator = new TriggerValidator();

        PayloadChecker.setRunNumber(RUN_NUMBER);

        // set up event builder
        ebComp = new EBComponent();
        ebComp.setValidateEvents(true);
        ebComp.setDispatchDestStorage(DISPATCH_DEST);
        ebComp.setGlobalConfigurationDir(cfgFile.getParent());
        ebComp.setAlerter(new MockAlerter());
        ebComp.initialize();

        ebComp.start(false);

        Map<ISourceID, RequestToDataBridge> bridgeMap =
            RequestToDataBridge.createLinks(ebComp.getRequestWriter(), null,
                                            ebComp.getDataReader(),
                                            ebComp.getDataCache(), hitList);

        List<ISourceID> idList = new ArrayList<ISourceID>(bridgeMap.keySet());

        // set up global trigger
        gtComp = new GlobalTriggerComponent();
        gtComp.setGlobalConfigurationDir(cfgFile.getParent());
        gtComp.setAlerter(new MockAlerter());
        gtComp.initialize();
        gtComp.start(false);

        gtComp.configuring(cfgFile.getName());

        DAQTestUtil.glueComponents("GT->EB",
                                   gtComp.getWriter(), gtComp.getOutputCache(),
                                   validator,
                                   ebComp.getTriggerReader(),
                                   ebComp.getTriggerCache());

        // set up in-ice trigger
        iiComp = new IniceTriggerComponent();
        iiComp.setGlobalConfigurationDir(cfgFile.getParent());
        iiComp.setAlerter(new MockAlerter());
        iiComp.initialize();
        iiComp.start(false);

        iiComp.configuring(cfgFile.getName());

        DAQTestUtil.glueComponents("IIT->GT",
                                   iiComp.getWriter(), iiComp.getOutputCache(),
                                   validator,
                                   gtComp.getReader(), gtComp.getInputCache());

        iiTails = DAQTestUtil.connectToReader(iiComp.getReader(),
                                              iiComp.getInputCache(),
                                              idList.size());

        DAQTestUtil.startComponentIO(ebComp, gtComp, null, iiComp, null,
                                     RUN_NUMBER, IComponent.DOMMODE_NORMAL);

        ActivityMonitor activity =
            new ActivityMonitor(iiComp, null, gtComp, ebComp);

        final int midpoint = hitList.size() / 2;
        sendHits(idList, hitList, 0, midpoint);

        activity.waitForStasis(10, 1000, numEvents, dumpActivity,
                               dumpSplicers);
        if (dumpBEStats) activity.dumpBackEndStats();

        final long prevTRsRcvd = ebComp.getTriggerRequestsReceived();
        final long prevEvtsSent = ebComp.getEventsSent();

        //assertEquals("Global trigger/event mismatch",
        //             gtComp.getPayloadsSent() - 1, prevEvtsSent);

        switchToNewRun(RUN_NUMBER + 3);

        sendHits(idList, hitList, midpoint, hitList.size() - midpoint);

        activity.waitForStasis(10, 1000, numEvents, dumpActivity,
                               dumpSplicers);
        if (dumpBEStats) activity.dumpBackEndStats();

        //assertEquals("Global trigger/event mismatch",
        //             gtComp.getPayloadsSent() - 2,
        //             prevEvtsSent + ebComp.getEventsSent());

        DAQTestUtil.sendStops(iiTails);

        activity.waitForStasis(10, 1000, numEvents, dumpActivity,
                               dumpSplicers);

        int ebRunChk = 0;
        while (ebComp.isBackEndRunning() && ebRunChk++ < 10) {
            Thread.yield();
        }

        activity.waitForStasis(10, 1000, numEvents, dumpActivity,
                               dumpSplicers);
        if (dumpBEStats) activity.dumpBackEndStats();

        assertEquals("Missing in-ice trigger requests",
                     numEvents + 1, iiComp.getPayloadsSent());
        assertEquals("Missing global trigger requests",
                     numEvents + 1, gtComp.getPayloadsSent());

        final long totalTRs =
            prevTRsRcvd + ebComp.getTriggerRequestsReceived();

        final String descr;
        if (totalTRs > numEvents) {
            descr = "Extra";
        } else {
            descr = "Missing";
        }
        assertTrue(descr + " trigger requests in eventBuilder (got " +
                   totalTRs + ", expected " + (numEvents - 1) + " or " +
                   numEvents + ")",
                   totalTRs == numEvents - 1 || totalTRs == numEvents);

        assertEquals("Missing events", numEvents,
                     prevEvtsSent + ebComp.getEventsSent());

        DAQTestUtil.checkCaches(ebComp, gtComp, null, iiComp, null, false);
        DAQTestUtil.destroyComponentIO(ebComp, gtComp, null, iiComp, null);

        if (appender.getNumberOfMessages() > 0) {
            System.err.println("XXX Ignoring extra log msgs");
            appender.clear();
        }
    }

    public static void main(String[] args)
    {
        TestRunner.run(suite());
    }
}
