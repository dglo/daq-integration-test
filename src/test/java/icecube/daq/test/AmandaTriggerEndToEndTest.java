package icecube.daq.test;

import icecube.daq.io.DAQComponentIOProcess;
import icecube.daq.io.SpliceablePayloadReader;
import icecube.daq.juggler.component.DAQCompException;
import icecube.daq.payload.ISourceID;
import icecube.daq.payload.IWriteablePayload;
import icecube.daq.payload.PayloadChecker;
import icecube.daq.payload.PayloadRegistry;
import icecube.daq.payload.RecordTypeRegistry;
import icecube.daq.payload.SourceIdRegistry;
import icecube.daq.payload.VitreousBufferCache;
import icecube.daq.splicer.HKN1Splicer;
import icecube.daq.splicer.Splicer;
import icecube.daq.splicer.SplicerException;
import icecube.daq.splicer.StrandTail;
import icecube.daq.trigger.IReadoutRequestElement;
import icecube.daq.trigger.ITriggerRequestPayload;
import icecube.daq.trigger.component.AmandaTriggerComponent;
import icecube.daq.trigger.control.TriggerManager;
import icecube.daq.trigger.exceptions.TriggerException;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.nio.channels.Selector;
import java.nio.channels.WritableByteChannel;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.BasicConfigurator;

public class AmandaTriggerEndToEndTest
    extends TestCase
{
    private static final MockAppender appender =
        new MockAppender(/*org.apache.log4j.Level.ALL*/)/*.setVerbose(true)*/;

    private static final MockSourceID TRIGGER_SOURCE_ID =
        new MockSourceID(SourceIdRegistry.AMANDA_TRIGGER_SOURCE_ID);

    private static final int NUM_HITS_PER_TRIGGER = 8;
    private static final long TIME_BASE = 100000L;
    private static final long TIME_STEP =
        5000L / (long) (NUM_HITS_PER_TRIGGER + 1);

    private ByteBuffer trigBuf;
    private int trigUID = 1;

    public AmandaTriggerEndToEndTest(String name)
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
                !msg.startsWith("No match for timegate "))
            {
                fail("Bad log message#" + i + ": " + appender.getMessage(i));
            }
        }
        appender.clear();
    }

    private static int getAmandaConfigId(int trigType)
    {
        trigType -= 7;
        if (trigType < 0 || trigType > 8) {
            return -1;
        }

        int bit = 1;
        for (int i = 1; i <= trigType; i++) {
            bit <<= 1;
        }

        return bit;
    }

    private void sendAmandaData(WritableByteChannel[] tails, int numObjs)
        throws IOException
    {
        int trigType = 0;
        for (int i = 0; i < numObjs; i++) {
            long first = TIME_BASE + (long) (i + 1) * TIME_STEP;
            long last = TIME_BASE + ((long) (i + 2) * TIME_STEP) - 1L;

            final int tailIndex = i % tails.length;
            sendTrigger(tails[tailIndex], first, last, trigType,
                        TRIGGER_SOURCE_ID.getSourceID());

            trigType++;
            if (trigType < 7 || trigType > 11) {
                trigType = 7;
            }
        }
    }

    private void sendTrigger(WritableByteChannel chan, long firstTime,
                             long lastTime, int trigType, int srcId)
        throws IOException
    {
        final int bufLen = 72;

        if (trigBuf == null) {
            trigBuf = ByteBuffer.allocate(bufLen);
        }

        synchronized (trigBuf) {
            final int recType =
                RecordTypeRegistry.RECORD_TYPE_TRIGGER_REQUEST;
            final int uid = trigUID++;

            int amCfgId = getAmandaConfigId(trigType);
            if (amCfgId < 0) {
                amCfgId = 0;
            }

            trigBuf.putInt(0, bufLen);
            trigBuf.putInt(4, PayloadRegistry.PAYLOAD_ID_TRIGGER_REQUEST);
            trigBuf.putLong(8, firstTime);

            trigBuf.putShort(16, (short) recType);
            trigBuf.putInt(18, uid);
            trigBuf.putInt(22, trigType);
            trigBuf.putInt(26, amCfgId);
            trigBuf.putInt(30, srcId);
            trigBuf.putLong(34, firstTime);
            trigBuf.putLong(42, lastTime);

            trigBuf.putShort(50, (short) 0xff);
            trigBuf.putInt(52, uid);
            trigBuf.putInt(56, srcId);
            trigBuf.putInt(60, 0);

            trigBuf.putInt(64, 8);
            trigBuf.putShort(68, (short) 1);
            trigBuf.putShort(70, (short) 0);

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
        return new TestSuite(AmandaTriggerEndToEndTest.class);
    }

    protected void tearDown()
        throws Exception
    {
        assertEquals("Bad number of log messages",
                     0, appender.getNumberOfMessages());

        super.tearDown();
    }

    public void testEndToEnd()
        throws DAQCompException, IOException, SplicerException, TriggerException
    {
        final int numTails = 1;
        final int numObjs = numTails * 10;

        final long multiplier = 10000L;

        Selector sel = Selector.open();

        int port = ServerUtil.createServer(sel);

        File cfgFile =
            DAQTestUtil.buildConfigFile(getClass().getResource("/").getPath(),
                                        "sps-icecube-amanda-008");

        // set up amanda trigger
        AmandaTriggerComponent comp =
            new AmandaTriggerComponent("localhost", port);
        comp.setGlobalConfigurationDir(cfgFile.getParent());
        comp.start(false);

        comp.configuring(cfgFile.getName());

        AmandaValidator validator = new AmandaValidator();

        DAQTestUtil.connectToSink("amOut", comp.getWriter(), comp.getCache(),
                                  validator);

        DAQTestUtil.startIOProcess(comp.getReader());
        DAQTestUtil.startIOProcess(comp.getWriter());

        WritableByteChannel[] tails = new WritableByteChannel[] {
            ServerUtil.acceptChannel(sel),
        };

        // load data into input channels
        sendAmandaData(tails, numObjs);
        DAQTestUtil.sendStops(tails);

        DAQTestUtil.waitUntilStopped(comp.getReader(), comp.getSplicer(),
                                     "AMStopMsg");
        DAQTestUtil.waitUntilStopped(comp.getWriter(), null, "AMStopMsg");

        comp.flush();

        assertEquals("Bad number of payloads written",
                     numObjs, comp.getPayloadsSent());

        assertFalse("Found invalid payload(s)", validator.foundInvalid());

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

    class AmandaValidator
        extends BaseValidator
    {
        private Log LOG = LogFactory.getLog(AmandaValidator.class);

        private long nextStart;
        private long nextEnd;

        /**
         * Validate Amanda triggers.
         */
        AmandaValidator()
        {
            nextStart = TIME_BASE + TIME_STEP * 2L;
            nextEnd = nextStart;
        }

        public boolean validate(IWriteablePayload payload)
        {
            if (!(payload instanceof ITriggerRequestPayload)) {
                LOG.error("Unexpected payload " + payload.getClass().getName());
                return false;
            }

            //dumpPayloadBytes(payload);

            ITriggerRequestPayload tr = (ITriggerRequestPayload) payload;

            if (!PayloadChecker.validateTriggerRequest(tr, true)) {
                LOG.error("Trigger request " + tr + " is not valid");
                return false;
            }

            long firstTime = getUTC(tr.getFirstTimeUTC());
            long lastTime = getUTC(tr.getLastTimeUTC());

            if (firstTime != nextStart) {
                LOG.error("Expected first trigger time " + nextStart +
                          ", not " + firstTime);
                return false;
            } else if (lastTime != nextStart) {
                LOG.error("Expected last trigger time " + nextStart +
                          ", not " + lastTime);
                return false;
            }

            nextStart = firstTime + TIME_STEP;
            nextEnd = nextStart;

            return true;
        }
    }
}
