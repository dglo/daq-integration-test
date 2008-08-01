package icecube.daq.test;

import icecube.daq.eventBuilder.EBComponent;
import icecube.daq.eventBuilder.GlobalTriggerReader;
import icecube.daq.eventBuilder.RequestPayloadOutputEngine;
import icecube.daq.eventBuilder.SPDataAnalysis;
import icecube.daq.eventBuilder.backend.EventBuilderBackEnd;
import icecube.daq.eventBuilder.monitoring.MonitoringData;
import icecube.daq.eventbuilder.impl.ReadoutDataPayloadFactory;
import icecube.daq.io.DAQComponentIOProcess;
import icecube.daq.io.FileDispatcher;
import icecube.daq.io.PayloadReader;
import icecube.daq.io.SpliceablePayloadReader;
import icecube.daq.juggler.component.DAQCompException;
import icecube.daq.payload.IByteBufferCache;
import icecube.daq.payload.IPayloadDestination;
import icecube.daq.payload.IPayloadDestinationCollection;
import icecube.daq.payload.IPayloadDestinationCollectionController;
import icecube.daq.payload.ISourceID;
import icecube.daq.payload.IWriteablePayload;
import icecube.daq.payload.PayloadRegistry;
import icecube.daq.payload.RecordTypeRegistry;
import icecube.daq.payload.SourceIdRegistry;
import icecube.daq.payload.VitreousBufferCache;
import icecube.daq.splicer.HKN1Splicer;
import icecube.daq.splicer.Splicer;
import icecube.daq.trigger.IReadoutRequest;
import icecube.daq.trigger.IReadoutRequestElement;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

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
        new MockAppender(/*org.apache.log4j.Level.ALL*/)/*.setVerbose(true)*/;

    private static final int SIMHUB_ID =
        SourceIdRegistry.SIMULATION_HUB_SOURCE_ID;

    private static final long TIME_BASE = 100L;
    private static final int TRIG_STEP = 10;
    private static final int NUM_TRIGGERS = 20;
    private static final int NUM_HUBS = 5;

    private static final int RUN_NUMBER = 1234;

    private static int trigUID = 1;

    private static ByteBuffer trigBuf;

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
        for (int i = 0; i < appender.getNumberOfMessages(); i++) {
            String msg = (String) appender.getMessage(i);

            if (!(msg.startsWith("Clearing ") &&
                  msg.endsWith(" rope entries")) &&
                !msg.startsWith("Resetting counter ") &&
                !msg.startsWith("No match for timegate ") &&
                !msg.startsWith("Sending empty event for window") &&
                !msg.endsWith("does not exist!  Using current directory.") &&
                !msg.equals("Cannot write to " +
                            FileDispatcher.DISPATCH_DEST_STORAGE + "!") &&
                !msg.startsWith("Couldn't move temp file ") &&
                !msg.equals("The last temp-physics file was not moved" +
                            " to the dispatch storage!!!") &&
                !msg.startsWith("Couldn't stop dispatcher ("))
            {
                fail("Bad log message#" + i + ": " + appender.getMessage(i));
            }
        }
        appender.clear();
    }

    private List<HitData> getHitList()
    {
        final long firstTime = TIME_BASE;
        final long timeRange = TRIG_STEP * NUM_TRIGGERS;

        final int numHits = NUM_TRIGGERS * 10;
        final long hitStep = timeRange / (long) numHits;

        ArrayList<HitData> list = new ArrayList<HitData>();

        HitData.setDefaultTriggerType(0);
        HitData.setDefaultConfigId(0);
        HitData.setDefaultTriggerMode(0);

        long time = TIME_BASE;
        for (int i = 0; i < numHits; i++) {
            list.add(new HitData(time, SIMHUB_ID + (i % NUM_HUBS), (long) i));

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

    public void sendGlobalTriggers(WritableByteChannel chan)
        throws IOException
    {
        int trigType = 0;
        for (int i = 0; i < NUM_TRIGGERS; i++) {
            long first = TIME_BASE + (long) (i + 1) * TRIG_STEP;
            long last = TIME_BASE + ((long) (i + 2) * TRIG_STEP) - 1L;

            sendTrigger(chan, first, last, trigType,
                        SourceIdRegistry.GLOBAL_TRIGGER_SOURCE_ID);

            trigType++;
            if (trigType < 7 || trigType > 11) {
                trigType = 7;
            }
        }
    }

    static void sendTrigger(WritableByteChannel chan, long firstTime,
                            long lastTime, int trigType, int srcId)
        throws IOException
    {
        final int bufLen = 108;

        if (trigBuf == null) {
            trigBuf = ByteBuffer.allocate(bufLen);
        }

        synchronized (trigBuf) {
            final int recType =
                RecordTypeRegistry.RECORD_TYPE_TRIGGER_REQUEST;
            final int uid = trigUID++;

            int cfgId = 0;

            // envelope
            trigBuf.putInt(0, bufLen);
            trigBuf.putInt(4, PayloadRegistry.PAYLOAD_ID_TRIGGER_REQUEST);
            trigBuf.putLong(8, firstTime);

            // trigger record
            trigBuf.putShort(16, (short) recType);
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
            trigBuf.putShort(104, (short) 0);

            trigBuf.position(0);
            chan.write(trigBuf);
        }
    }

    protected void setUp()
        throws Exception
    {
        super.setUp();

        appender.clear();

        BasicConfigurator.resetConfiguration();
        BasicConfigurator.configure(appender);
    }

    public static Test suite()
    {
        return new TestSuite(EventBuilderEndToEndTest.class);
    }

    protected void tearDown()
        throws Exception
    {
        assertEquals("Bad number of log messages",
                     0, appender.getNumberOfMessages());

        super.tearDown();
    }

    public void testEndToEnd()
        throws  DAQCompException, IOException
    {
        List<HitData> hitList = getHitList();

        EBComponent comp = new EBComponent(true);
        comp.start(false);
        comp.setRunNumber(RUN_NUMBER);
        comp.setDispatchDestStorage(System.getProperty("java.io.tmpdir"));

        WritableByteChannel gtChan =
            DAQTestUtil.connectToReader(comp.getTriggerReader(),
                                        comp.getTriggerCache());

        RequestToDataBridge.createLinks(comp.getRequestWriter(), null,
                                        comp.getDataReader(),
                                        comp.getDataCache(), hitList);

        DAQTestUtil.startIOProcess(comp.getTriggerReader());
        DAQTestUtil.startIOProcess(comp.getRequestWriter());
        DAQTestUtil.startIOProcess(comp.getDataReader());

        sendGlobalTriggers(gtChan);

        try {
            Thread.sleep(1000);
        } catch (InterruptedException ie) {
            // ignore interrupts
        }

        DAQTestUtil.sendStopMsg(gtChan);

        DAQTestUtil.waitUntilStopped(comp.getTriggerReader(), null,
                                     "EBStopMsg");
        DAQTestUtil.waitUntilStopped(comp.getRequestWriter(), null,
                                     "EBStopMsg");
        DAQTestUtil.waitUntilStopped(comp.getDataReader(),
                                     comp.getDataSplicer(), "EBStopMsg");

        try {
            Thread.sleep(1000);
        } catch (InterruptedException ie) {
            // ignore interrupts
        }

        if (appender.getLevel().equals(org.apache.log4j.Level.ALL)) {
            appender.clear();
        } else {
            checkLogMessages();
        }
    }

    public static void main(String[] args)
    {
        TestRunner.run(suite());
    }
}
