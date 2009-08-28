package icecube.daq.test;

import icecube.daq.io.DAQComponentIOProcess;
import icecube.daq.io.SpliceablePayloadReader;
import icecube.daq.juggler.component.DAQCompException;
import icecube.daq.payload.IByteBufferCache;
import icecube.daq.payload.ITriggerRequestPayload;
import icecube.daq.payload.IWriteablePayload;
import icecube.daq.payload.PayloadChecker;
import icecube.daq.payload.PayloadRegistry;
import icecube.daq.payload.SourceIdRegistry;
import icecube.daq.payload.impl.VitreousBufferCache;
import icecube.daq.splicer.HKN1Splicer;
import icecube.daq.splicer.Splicer;
import icecube.daq.splicer.SplicerException;
import icecube.daq.trigger.algorithm.AbstractTrigger;
import icecube.daq.trigger.component.IniceTriggerComponent;
import icecube.daq.trigger.control.TriggerManager;
import icecube.daq.trigger.exceptions.TriggerException;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.nio.channels.WritableByteChannel;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.BasicConfigurator;

public class InIceTriggerEndToEndTest
    extends TestCase
{
    private static final MockAppender appender =
        new MockAppender(/*org.apache.log4j.Level.ALL*/)/*.setVerbose(true)*/;

    private static final MockSourceID TRIGGER_SOURCE_ID =
        new MockSourceID(SourceIdRegistry.INICE_TRIGGER_SOURCE_ID);

    private static final int NUM_HITS_PER_TRIGGER = 8;
    private static final long TIME_BASE = 100000L;
    private static final long TIME_STEP =
        5000L / (long) (NUM_HITS_PER_TRIGGER + 1);

    private ByteBuffer hitBuf;

    public InIceTriggerEndToEndTest(String name)
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

    private void sendHit(WritableByteChannel chan, long time, int tailIndex,
                         long domId)
        throws IOException
    {
        final int bufLen = 38;

        if (hitBuf == null) {
            hitBuf = ByteBuffer.allocate(bufLen);
        }

        synchronized (hitBuf) {
            final int recType = AbstractTrigger.SPE_HIT;
            final int cfgId = 2;
            final int srcId = SourceIdRegistry.SIMULATION_HUB_SOURCE_ID;
            final short mode = 0;

            hitBuf.putInt(0, bufLen);
            hitBuf.putInt(4, PayloadRegistry.PAYLOAD_ID_SIMPLE_HIT);
            hitBuf.putLong(8, time);

            hitBuf.putInt(16, recType);
            hitBuf.putInt(20, cfgId);
            hitBuf.putInt(24, srcId + tailIndex);
            hitBuf.putLong(28, domId);
            hitBuf.putShort(36, mode);

            hitBuf.position(0);
            chan.write(hitBuf);
        }
    }

    private void sendInIceData(WritableByteChannel[] tails, int numObjs)
        throws IOException
    {
        for (int i = 0; i < numObjs; i++) {
            final long time;
            if (i == 0) {
                time = TIME_BASE;
            } else {
                time = (TIME_BASE * (((i - 1) / NUM_HITS_PER_TRIGGER) + 1)) +
                    (TIME_STEP * i);
            }

            final int tailIndex = i % tails.length;
            sendHit(tails[tailIndex], time, tailIndex, 987654321L * i);
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
        return new TestSuite(InIceTriggerEndToEndTest.class);
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
        final int numTails = 10;
        final int numObjs = numTails * 10;

        File cfgFile =
            DAQTestUtil.buildConfigFile(getClass().getResource("/").getPath(),
                                        "sps-icecube-amanda-008");

        // set up in-ice trigger
        IniceTriggerComponent comp = new IniceTriggerComponent();
        comp.setGlobalConfigurationDir(cfgFile.getParent());
        comp.start(false);

        comp.configuring(cfgFile.getName());

        WritableByteChannel[] tails =
            DAQTestUtil.connectToReader(comp.getReader(), comp.getInputCache(),
                                        numTails);

        InIceValidator validator = new InIceValidator();
        DAQTestUtil.connectToSink("iiOut", comp.getWriter(),
                                  comp.getOutputCache(), validator);

        DAQTestUtil.startIOProcess(comp.getReader());
        DAQTestUtil.startIOProcess(comp.getWriter());

        // load data into input channels
        sendInIceData(tails, numObjs);
        DAQTestUtil.sendStops(tails);

        DAQTestUtil.waitUntilStopped(comp.getReader(), comp.getSplicer(),
                                     "IIStopMsg");
        DAQTestUtil.waitUntilStopped(comp.getWriter(), null, "IIStopMsg");

        comp.flush();

        assertEquals("Bad number of payloads written",
                     numObjs / NUM_HITS_PER_TRIGGER,
                     comp.getPayloadsSent() - 1);

        IByteBufferCache inCache = comp.getInputCache();
        assertTrue("Input buffer cache is unbalanced (" + inCache + ")",
                   inCache.isBalanced());

        IByteBufferCache outCache = comp.getOutputCache();
        assertTrue("Output buffer cache is unbalanced (" + outCache + ")",
                   outCache.isBalanced());

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

    class InIceValidator
        extends BaseValidator
    {
        private Log LOG = LogFactory.getLog(InIceValidator.class);

        private long timeSpan;

        private boolean jumpHack = true;

        private long nextStart;
        private long nextEnd;

        InIceValidator()
        {
            timeSpan = TIME_STEP * (long) NUM_HITS_PER_TRIGGER;

            nextStart = TIME_BASE;
            nextEnd = nextStart + timeSpan;
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
                LOG.error("Trigger request is not valid");
                return false;
            }

            long firstTime = getUTC(tr.getFirstTimeUTC());
            long lastTime = getUTC(tr.getLastTimeUTC());

            if (firstTime != nextStart) {
                LOG.error("Expected first trigger time " + nextStart +
                          ", not " + firstTime);
                return false;
            } else if (lastTime != nextEnd) {
                LOG.error("Expected last trigger time " + nextEnd +
                          ", not " + lastTime);
                return false;
            }

            nextStart = firstTime + TIME_BASE + timeSpan;
            if (jumpHack) {
                nextStart += TIME_STEP;
                jumpHack = false;
            }
            nextEnd = lastTime + TIME_BASE + timeSpan;

            return true;
        }
    }
}
