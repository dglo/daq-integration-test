package icecube.daq.test;

import icecube.daq.payload.IEventPayload;
import icecube.daq.io.DAQComponentIOProcess;
import icecube.daq.io.PayloadFileReader;
import icecube.daq.io.SpliceablePayloadReader;
import icecube.daq.juggler.component.DAQCompException;
import icecube.daq.payload.TriggerRegistry;
import icecube.daq.payload.ILoadablePayload;
import icecube.daq.payload.ISourceID;
import icecube.daq.payload.ITriggerRequestPayload;
import icecube.daq.payload.IUTCTime;
import icecube.daq.payload.IWriteablePayload;
import icecube.daq.payload.SourceIdRegistry;
import icecube.daq.splicer.HKN1Splicer;
import icecube.daq.splicer.Splicer;
import icecube.daq.trigger.component.GlobalTriggerComponent;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Pipe;
import java.nio.channels.SelectableChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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

public class GlobalTriggerPhysicsDataTest
    extends TestCase
{
    private static final MockAppender appender =
        //new MockAppender(/*org.apache.log4j.Level.ALL*/)/*.setVerbose(true)*/;
        new MockAppender(org.apache.log4j.Level.WARN).setVerbose(false);

    private static final MockSourceID globalTrigSrcId =
        new MockSourceID(SourceIdRegistry.GLOBAL_TRIGGER_SOURCE_ID);

    private static HashMap<ISourceID, List<IWriteablePayload>> streams =
        new HashMap<ISourceID, List<IWriteablePayload>>();

    private static int numEventsInFile;

    private Pipe[] tails;

    public GlobalTriggerPhysicsDataTest(String name)
    {
        super(name);
    }

    private void checkLogMessages()
    {
    }

    private void dumpStreams(java.io.PrintStream out)
    {
        for (ISourceID srcId : streams.keySet()) {
            out.println(srcId.toString());

            for (IWriteablePayload pay : streams.get(srcId)) {
                ITriggerRequestPayload tr = (ITriggerRequestPayload) pay;

                String name =
                    TriggerRegistry.getTriggerName(tr.getTriggerType());
                if (name == null) {
                    name = "trigType<" + tr.getTriggerType() + ">";
                }

                out.println("  " + name +
                            "#" + tr.getTriggerConfigID() +
                            " u" + tr.getUID() +
                            " [" + tr.getFirstTimeUTC() +
                            "-" + tr.getLastTimeUTC() + "]");
            }
        }
    }

    private int extractStreams(String dataPath)
        throws IOException
    {
        PayloadFileReader rdr = new PayloadFileReader(dataPath);

        int numPayloads = 0;
        int numEvents = 0;
        for (Object obj : rdr) {
            ILoadablePayload payload = (ILoadablePayload) obj;
            numPayloads++;

            try {
                payload.loadPayload();
            } catch (Exception ex) {
                System.err.println("Couldn't load payload #" + numPayloads +
                                   " from " + dataPath);
                ex.printStackTrace();
                continue;
            }

            if (payload instanceof IEventPayload) {
                IEventPayload event = (IEventPayload) payload;
                numEvents++;

                extractTrigger(event.getTriggerRequestPayload());
            } else if (payload instanceof ITriggerRequestPayload) {
                ITriggerRequestPayload trig = (ITriggerRequestPayload) payload;
                numEvents++;

                extractTrigger(trig);
            } else {
                System.err.println("Ignoring non-event payload " +
                                   payload.getClass().getName() +
                                   " from " + dataPath);
                continue;
            }

        }

        return numEvents;
    }

    private void extractTrigger(IWriteablePayload pay)
    {
        try {
            ((ILoadablePayload) pay).loadPayload();
        } catch (Exception ex) {
            System.err.println("Couldn't load trigger request");
            ex.printStackTrace();
            return;
        }

        ITriggerRequestPayload trigReq = (ITriggerRequestPayload) pay;

        ISourceID srcId = trigReq.getSourceID();
        if (srcId != null && !srcId.equals(globalTrigSrcId)) {
            if (!streams.containsKey(srcId)) {
                streams.put(srcId, new ArrayList<IWriteablePayload>());
            }

            streams.get(srcId).add(trigReq);
        }

        List payList;
        try {
            payList = trigReq.getPayloads();
        } catch (Exception ex) {
            fail("Could not get list of payloads for " + trigReq);
            return;
        }

        for (Object obj : payList) {
            if (obj instanceof ITriggerRequestPayload) {
                extractTrigger((ITriggerRequestPayload) obj);
            }
        }
    }

    /**
     * Sort trigger request streams.
     */
    private void sortStreams()
    {
        TriggerComparator trigCmp =
            new TriggerComparator(TriggerComparator.CMP_TIME_CFGID);
        for (ISourceID srcId : streams.keySet()) {
            Collections.sort(streams.get(srcId), trigCmp);
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
        return new TestSuite(GlobalTriggerPhysicsDataTest.class);
    }

    protected void tearDown()
        throws Exception
    {
        assertEquals("Bad number of log messages",
                     0, appender.getNumberOfMessages());

        if (tails != null) {
            DAQTestUtil.closePipeList(tails);
        }

        super.tearDown();
    }

    public void testRealFile()
        throws DAQCompException, IOException
    {
        if (streams.size() == 0) {
            URL dataURL = getClass().getResource("/global_trigger.physics.dat");
            if (dataURL == null) {
                throw new IOException("Cannot find GT physics data");
            }

            numEventsInFile = extractStreams(dataURL.getPath());
            sortStreams();
            //dumpStreams(System.out);

            if (streams.size() == 0) {
                throw new Error(dataURL.getPath() + " seems to be empty");
            }
        }

        final int numTails = streams.size();

        File cfgFile =
            DAQTestUtil.buildConfigFile(getClass().getResource("/").getPath(),
                                        "sps-2013-no-physminbias-001");

        // set up global trigger
        GlobalTriggerComponent comp = new GlobalTriggerComponent();
        comp.setAlerter(new MockAlerter());
        comp.setGlobalConfigurationDir(cfgFile.getParent());
        comp.initialize();
        comp.setFirstGoodTime(1);
        comp.start(false);

        comp.configuring(cfgFile.getName());

        tails = DAQTestUtil.connectToReader(comp.getReader(),
                                            comp.getInputCache(), numTails);

        GlobalTriggerValidator validator = new GlobalTriggerValidator();

        DAQTestUtil.connectToSink("gtOut", comp.getWriter(),
                                  comp.getOutputCache(), validator);

        final int runNum = 12345;

        DAQTestUtil.startComponentIO(null, comp, null, null, null, runNum);

        PayloadProducer[] prod = new PayloadProducer[numTails];

        int nextTail = 0;
        for (ISourceID srcId : streams.keySet()) {
            List<IWriteablePayload> stream = streams.get(srcId);

            prod[nextTail] =
                new TriggerProducer(srcId.toString(), stream,
                                    tails[nextTail].sink());
            prod[nextTail].start();

            nextTail++;
        }

        // wait for all stream data to be written
        while (true) {
            boolean done = true;
            for (int i = 0; done && i < prod.length; i++) {
                if (prod[i].isRunning()) {
                    done = false;

                    try {
                        Thread.sleep(100);
                    } catch (Exception ex) {
                        // ignore exceptions
                    }
                }
            }
            if (done) {
                break;
            }
        }

        for (int i = 0; i < prod.length; i++) {
            if (prod[i].isRunning()) {
                fail("Producer #" + i + " is still running");
            }
        }

        int expEvents;
        if (numEventsInFile == 2494) {
            expEvents = 2483;
        } else if (numEventsInFile == 2455) {
            expEvents = 2370;
        } else {
            expEvents = numEventsInFile;
        }

        ActivityMonitor activity =
            new ActivityMonitor(null, null, comp, null);

        activity.waitForStasis(5, 150, expEvents, false, false);
        DAQTestUtil.waitUntilStopped(comp.getReader(), comp.getSplicer(),
                                     "GTStopMsg");

        activity.waitForStasis(5, 150, expEvents, false, false);
        DAQTestUtil.waitUntilStopped(comp.getWriter(), null, "GTStopMsg");

        if (false) {
            for (int i = 0; i < prod.length; i++) {
                System.out.println(prod[i].getName() + " wrote " +
                                   prod[i].getNumberWritten());
            }
        }

        assertEquals("Unexpected number of global triggers",
                     expEvents, comp.getPayloadsSent() - 1);

        assertFalse("Found invalid payload(s)", validator.foundInvalid());

        DAQTestUtil.destroyComponentIO(null, comp, null, null, null);

        try {
            if (!appender.getLevel().equals(org.apache.log4j.Level.ALL)) {
                for (int i = 0; i < appender.getNumberOfMessages(); i++) {
                    final String msg = (String) appender.getMessage(i);
                    if (!msg.startsWith("Resetting counter ")) {
                        fail("Got unexpected log message #" + i + ": " + msg);
                    }
                }
            }
        } finally {
            appender.clear();
        }
    }

    public static void main(String[] args)
    {
        TestRunner.run(suite());
    }

    class TriggerComparator
        implements Comparator
    {
        public static final int CMP_TIME_CFGID = 1;
        public static final int CMP_CFGID_TIME = 1;

        private int cmpOp;

        TriggerComparator(int cmpOp)
        {
            this.cmpOp = cmpOp;
        }

        public int compare(Object a, Object b)
        {
            ITriggerRequestPayload aTrig = (ITriggerRequestPayload) a;
            ITriggerRequestPayload bTrig = (ITriggerRequestPayload) b;

            int cmp;
            switch (cmpOp) {
            case CMP_TIME_CFGID:
                cmp = compareByTimeAndConfigId(aTrig, bTrig);
                break;
            default:
                throw new Error("Unknown comparison op#" + cmpOp);
            }

            return cmp;
        }

        private int compareByConfigIdAndTime(ITriggerRequestPayload aTrig,
                                             ITriggerRequestPayload bTrig)
        {
            int cmp;

            cmp = aTrig.getTriggerConfigID() - bTrig.getTriggerConfigID();
            if (cmp == 0) {
                cmp = aTrig.getTriggerType() - bTrig.getTriggerType();
                if (cmp == 0) {
                    IUTCTime aTime = aTrig.getLastTimeUTC();
                    IUTCTime bTime = bTrig.getLastTimeUTC();

                    if (aTime == null) {
                        if (bTime == null) {
                            cmp = 0;
                        }

                        cmp = 1;
                    } else if (bTime == null) {
                        cmp = -1;
                    } else {
                        long val = aTime.longValue() - bTime.longValue();
                        if (val < 0) {
                            cmp = -1;
                        } else if (val > 0) {
                            cmp = 1;
                        } else {
                            cmp = 0;
                        }
                    }
                }
            }

            return cmp;
        }

        private int compareByTimeAndConfigId(ITriggerRequestPayload aTrig,
                                             ITriggerRequestPayload bTrig)
        {
            int cmp;

            IUTCTime aTime = aTrig.getLastTimeUTC();
            IUTCTime bTime = bTrig.getLastTimeUTC();

            if (aTime == null) {
                if (bTime == null) {
                    cmp = 0;
                }

                cmp = 1;
            } else if (bTime == null) {
                cmp = -1;
            } else {
                long val = aTime.longValue() - bTime.longValue();
                if (val < 0) {
                    cmp = -1;
                } else if (val > 0) {
                    cmp = 1;
                } else {
                    cmp = 0;
                }
            }

            if (cmp == 0) {
                cmp = aTrig.getTriggerConfigID() - bTrig.getTriggerConfigID();
                if (cmp == 0) {
                    cmp = aTrig.getTriggerType() - bTrig.getTriggerType();
                }
            }

            return cmp;
        }

        public boolean equals(Object obj)
        {
            return obj.getClass().getName().equals(getClass().getName());
        }
    }

    abstract class PayloadProducer
        implements Runnable
    {
        private Log LOG = LogFactory.getLog(PayloadProducer.class);

        private String name;
        private Thread thread;
        private int numWritten;

        PayloadProducer(String name)
        {
            this.name = name;
        }

        abstract ByteBuffer buildStopMessage(ByteBuffer stopBuf);

        abstract void finishThreadCleanup();

        abstract IWriteablePayload getNextPayload();

        public String getName()
        {
            return name;
        }

        public int getNumberWritten()
        {
            return numWritten;
        }

        public boolean isRunning()
        {
            return thread != null;
        }

        public void run()
        {
            ByteBuffer buf = null;

            while (true) {
                IWriteablePayload pay = getNextPayload();
                if (pay == null) {
                    break;
                }

                if (buf == null || buf.capacity() < pay.length()) {
                    buf = ByteBuffer.allocate(pay.length());
                }

                buf.clear();
                try {
                    pay.writePayload(true, 0, buf);
                    write(buf);
                } catch (IOException ioe) {
                    LOG.error("Cannot write " + name + " payload #" +
                              numWritten, ioe);
                } finally {
                    numWritten++;
                }
            }

            buildStopMessage(buf);
            if (buf != null) {
                try {
                    write(buf);
                } catch (IOException ioe) {
                    LOG.error("Cannot write " + name + " stop message", ioe);
                }
            }

            finishThreadCleanup();

            thread = null;
        }

        public void start()
        {
            numWritten = 0;

            thread = new Thread(this);
            thread.setName(name);
            thread.start();
        }

        abstract void write(ByteBuffer buf)
            throws IOException;
    }

    class TriggerProducer
        extends PayloadProducer
    {
        private Log LOG = LogFactory.getLog(TriggerProducer.class);

        private static final int STOP_MESSAGE_LENGTH = 4;

        private List<IWriteablePayload> payloads;
        private WritableByteChannel chanOut;
        private Iterator<IWriteablePayload> iter;

        TriggerProducer(String name, List<IWriteablePayload> payloads,
                        WritableByteChannel chanOut)
        {
            super(name);

            this.payloads = payloads;
            this.chanOut = chanOut;

            if (chanOut instanceof SelectableChannel &&
                !((SelectableChannel) chanOut).isBlocking())
            {
                throw new Error("Output channel should be blocking");
            }
        }

        ByteBuffer buildStopMessage(ByteBuffer stopBuf)
        {
            if (stopBuf == null || stopBuf.capacity() < STOP_MESSAGE_LENGTH) {
                stopBuf = ByteBuffer.allocate(STOP_MESSAGE_LENGTH);
            }
            stopBuf.limit(STOP_MESSAGE_LENGTH);

            stopBuf.putInt(0, STOP_MESSAGE_LENGTH);

            stopBuf.position(0);

            return stopBuf;
        }

        void finishThreadCleanup()
        {
            try {
                chanOut.close();
            } catch (IOException ioe) {
                // ignore errors on close
            }

            chanOut = null;
        }

        IWriteablePayload getNextPayload()
        {
            if (iter == null) {
                iter = payloads.iterator();
            }

            if (!iter.hasNext()) {
                return null;
            }

            return iter.next();
        }

        void write(ByteBuffer buf)
            throws IOException
        {
            final int numBytes = buf.limit() - buf.position();
            if (numBytes <= 0) {
                LOG.error("Buffer " + buf + " has nothing to write");
            } else {
                int lenOut = chanOut.write(buf);
                if (lenOut != numBytes) {
                    throw new IOException("Expected to write " + numBytes +
                                          " bytes, not " + lenOut);
                }
            }

            Thread.yield();
        }
    }
}
