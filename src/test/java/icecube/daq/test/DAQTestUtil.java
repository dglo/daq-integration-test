package icecube.daq.test;

import icecube.daq.eventBuilder.EBComponent;
import icecube.daq.io.DAQComponentIOProcess;
import icecube.daq.io.DAQComponentOutputProcess;
import icecube.daq.io.PayloadReader;
import icecube.daq.juggler.component.DAQCompException;
import icecube.daq.juggler.component.DAQConnector;
import icecube.daq.payload.IByteBufferCache;
import icecube.daq.splicer.Splicer;
import icecube.daq.stringhub.StringHubComponent;
import icecube.daq.trigger.component.AmandaTriggerComponent;
import icecube.daq.trigger.component.GlobalTriggerComponent;
import icecube.daq.trigger.component.IcetopTriggerComponent;
import icecube.daq.trigger.component.IniceTriggerComponent;
import icecube.daq.trigger.component.TriggerComponent;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;

import junit.framework.Assert;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

class ChannelData
{
    private static final Log LOG = LogFactory.getLog(ChannelData.class);

    private String name;
    private java.nio.channels.Channel chan;
    private Error stack;

    ChannelData(String name, java.nio.channels.Channel chan)
    {
        this.name = name;
        this.chan = chan;

        try {
            throw new Error("StackTrace");
        } catch (Error err) {
            stack = err;
        }
    }

    void logOpen()
    {
        if (chan.isOpen()) {
            LOG.error(toString() + " has not been closed");
        }
    }

    public String toString()
    {
        StringBuilder buf = new StringBuilder("Channel[");
        buf.append(name).append('/').append(chan.toString());

        if (stack != null) {
            buf.append("/ opened at ");
            buf.append(stack.getStackTrace()[1].toString());
        }

        return buf.append(']').toString();
    }
}

public final class DAQTestUtil
    extends Assert
{
    private static final int REPS = 100;
    private static final int SLEEP_TIME = 100;

    private static ByteBuffer stopMsg;

    public static ArrayList<ChannelData> chanData =
        new ArrayList<ChannelData>();

    public static final File buildConfigFile(String rsrcDirName,
                                             String trigConfigName)
        throws IOException
    {
        File configDir = new File(rsrcDirName, "config");
        if (!configDir.isDirectory()) {
            throw new Error("Config directory \"" + configDir +
                            "\" does not exist");
        }

        File trigCfgDir = new File(configDir, "trigger");
        if (!trigCfgDir.isDirectory()) {
            throw new Error("Trigger config directory \"" + trigCfgDir +
                            "\" does not exist");
        }

        if (trigConfigName.endsWith(".xml")) {
            trigConfigName =
                trigConfigName.substring(0, trigConfigName.length() - 4);
        }

        File tempFile = File.createTempFile("tmpconfig-", ".xml", configDir);
        tempFile.deleteOnExit();

        FileWriter out = new FileWriter(tempFile);
        out.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        out.write("<runConfig>\n");
        out.write("<triggerConfig>" + trigConfigName + "</triggerConfig>\n");
        out.write("</runConfig>\n");
        out.close();

        return tempFile;
    }

    public static void checkCaches(EBComponent ebComp,
                                   GlobalTriggerComponent gtComp,
                                   IcetopTriggerComponent itComp,
                                   IniceTriggerComponent iiComp,
                                   AmandaTriggerComponent amComp,
                                   StringHubComponent[] shComps)
        throws DAQCompException
    {
        final boolean debug = false;

        assertEquals("Mismatch between global triggers sent and events sent",
                     gtComp.getPayloadsSent() - 1, ebComp.getEventsSent());

        IByteBufferCache ebGTCache =
            ebComp.getByteBufferCache(DAQConnector.TYPE_GLOBAL_TRIGGER);
        if (debug) System.err.println("EB GTcache " + ebGTCache);
        assertTrue("EB trigger buffer cache is unbalanced (" + ebGTCache + ")",
                   ebGTCache.isBalanced());
        assertTrue("EB trigger buffer cache is unused (" + ebGTCache +
                   ")", ebGTCache.getTotalBuffersAcquired() > 0);

        IByteBufferCache ebRDCache =
            ebComp.getByteBufferCache(DAQConnector.TYPE_READOUT_DATA);
        if (debug) System.err.println("EB RDcache " + ebRDCache);
        if (debug) System.err.println("EB RDrcvd " + ebComp.getReadoutsReceived());
        assertTrue("EB readout data buffer cache is unbalanced (" + ebRDCache +
                   ")", ebRDCache.isBalanced());
        assertTrue("EB readout data buffer cache is unused (" + ebRDCache +
                   ")", ebRDCache.getTotalBuffersAcquired() > 0);

        IByteBufferCache ebEvtCache =
            ebComp.getByteBufferCache(DAQConnector.TYPE_EVENT);
        if (debug) System.err.println("EB EVTcache " + ebEvtCache);
        assertTrue("EB event buffer cache is unbalanced (" + ebEvtCache + ")",
                   ebEvtCache.isBalanced());
        assertTrue("EB event buffer cache is unused (" + ebEvtCache + ")",
                   ebEvtCache.getTotalBuffersAcquired() > 0);

        IByteBufferCache ebGenCache =
            ebComp.getByteBufferCache(DAQConnector.TYPE_GENERIC_CACHE);
        if (debug) System.err.println("EB GENcache " + ebGenCache);
        assertTrue("EB Generic buffer cache is unbalanced (" + ebGenCache + ")",
                   ebGenCache.isBalanced());
        assertTrue("EB Generic buffer cache is unused (" + ebGenCache + ")",
                   ebGenCache.getTotalBuffersAcquired() > 0);

        assertEquals("Mismatch between readouts received and allocated",
                     ebComp.getReadoutsReceived(),
                     ebRDCache.getTotalBuffersAcquired());

        if (gtComp != null) checkTriggerCaches(gtComp, "Global trigger", debug);
        if (itComp != null) checkTriggerCaches(itComp, "Icetop trigger", debug);
        if (iiComp != null) checkTriggerCaches(iiComp, "In-ice trigger", debug);
        if (amComp != null) checkTriggerCaches(amComp, "Amanda trigger", debug);

        assertEquals("Mismatch between triggers sent and events sent",
                     (gtComp.getPayloadsSent() - 1),
                     ebGTCache.getTotalBuffersAcquired());

        for (int i = 0; shComps != null && i < shComps.length; i++) {
            final String name = "SH#" + shComps[i].getHubId();

            IByteBufferCache shRDCache =
                shComps[i].getByteBufferCache(DAQConnector.TYPE_READOUT_DATA);
            if (debug) System.err.println("SH RDcache " + shRDCache);
            assertTrue(name + " readout data buffer cache is unbalanced (" +
                       shRDCache + ")", shRDCache.isBalanced());
//           assertTrue(name + " readout data buffer cache is unused (" +
//                       shRDCache + ")",
//                       shRDCache.getTotalBuffersAcquired() > 0);
 System.err.println(name + " readout data buffer is unused");

            IByteBufferCache shGenCache =
                shComps[i].getByteBufferCache(DAQConnector.TYPE_GENERIC_CACHE);
            if (debug) System.err.println(name + " Gencache " + shGenCache);
            assertTrue(name + " generic buffer cache is unbalanced (" +
                       shGenCache + ")", shGenCache.isBalanced());
            assertTrue(name + " generic buffer cache is unused (" +
                       shGenCache + ")",
                       shGenCache.getTotalBuffersAcquired() > 0);

            IByteBufferCache shMoniCache =
                shComps[i].getByteBufferCache(DAQConnector.TYPE_MONI_DATA);
            if (debug) System.err.println(name + " Monicache " + shMoniCache);
            assertTrue(name + " MONI buffer cache is unbalanced (" +
                       shMoniCache + ")", shMoniCache.isBalanced());
            assertTrue(name + " MONI buffer cache was used (" +
                       shMoniCache + ")",
                       shMoniCache.getTotalBuffersAcquired() == 0);

            IByteBufferCache shTCalCache =
                shComps[i].getByteBufferCache(DAQConnector.TYPE_TCAL_DATA);
            if (debug) System.err.println(name + " TCalcache " + shTCalCache);
            assertTrue(name + " TCal buffer cache is unbalanced (" +
                       shTCalCache + ")", shTCalCache.isBalanced());
            assertTrue(name + " TCal buffer cache was used (" +
                       shTCalCache + ")",
                       shTCalCache.getTotalBuffersAcquired() == 0);

            IByteBufferCache shSNCache =
                shComps[i].getByteBufferCache(DAQConnector.TYPE_SN_DATA);
            if (debug) System.err.println(name + " SNcache " + shSNCache);
            assertTrue(name + " SN buffer cache is unbalanced (" +
                       shSNCache + ")", shSNCache.isBalanced());
            assertTrue(name + " SN buffer cache was used (" +
                       shSNCache + ")",
                       shSNCache.getTotalBuffersAcquired() == 0);
        }
    }

    private static void checkTriggerCaches(TriggerComponent comp, String name,
                                           boolean debug)
        throws DAQCompException
    {
        IByteBufferCache inCache = comp.getInputCache();
        if (debug) System.err.println(name+" INcache " + inCache);
        assertTrue(name + " input buffer cache is unbalanced (" + inCache + ")",
                   inCache.isBalanced());
        assertTrue(name + " input buffer cache was unused (" + inCache + ")",
                   inCache.getTotalBuffersAcquired() > 0);

        IByteBufferCache outCache = comp.getOutputCache();
        if (debug) System.err.println(name+" OUTcache " + outCache);
        assertTrue(name + " output buffer cache is unbalanced (" + outCache +
                   ")", outCache.isBalanced());
        assertTrue(name + " output buffer cache was unused (" + outCache + ")",
                   outCache.getTotalBuffersAcquired() > 0);

        assertEquals(name + " mismatch between triggers allocated and sent",
                     outCache.getTotalBuffersAcquired(),
                     comp.getPayloadsSent() - 1);
    }

    public static final void clearCachedChannels()
    {
        chanData.clear();
    }

    public static final void logOpenChannels()
    {
        for (ChannelData cd : chanData) {
            cd.logOpen();
        }
    }

    public static PayloadSink connectToSink(String name,
                                            DAQComponentOutputProcess out,
                                            IByteBufferCache outCache,
                                            PayloadValidator validator)
        throws IOException
    {
        final boolean startOut = false;
        final boolean startIn = false;

        Pipe outPipe = Pipe.open();

        Pipe.SinkChannel sinkOut = outPipe.sink();
        chanData.add(new ChannelData(name, sinkOut));
        sinkOut.configureBlocking(false);

        Pipe.SourceChannel srcOut = outPipe.source();
        chanData.add(new ChannelData(name, srcOut));
        srcOut.configureBlocking(true);

        out.addDataChannel(sinkOut, outCache);

        if (startOut) {
            startIOProcess(out);
        }

        PayloadSink consumer = new PayloadSink(name, srcOut);
        consumer.setValidator(validator);
        consumer.start();

        return consumer;
    }

    public static WritableByteChannel connectToReader(PayloadReader rdr,
                                                      IByteBufferCache cache)
        throws IOException
    {
        return connectToReader(rdr, cache, true);
    }

    public static WritableByteChannel[] connectToReader(PayloadReader rdr,
                                                        IByteBufferCache cache,
                                                        int numTails)
        throws IOException
    {
        return connectToReader(rdr, cache, numTails, true);
    }

    public static WritableByteChannel[] connectToReader(PayloadReader rdr,
                                                        IByteBufferCache cache,
                                                        int numTails,
                                                        boolean startReader)
        throws IOException
    {
        WritableByteChannel[] chanList = new WritableByteChannel[numTails];

        for (int i = 0; i < chanList.length; i++) {
            chanList[i] = connectToReader(rdr, cache, false);
        }

        if (startReader) {
            startIOProcess(rdr);
        }

        return chanList;
    }

    public static WritableByteChannel connectToReader(PayloadReader rdr,
                                                      IByteBufferCache cache,
                                                      boolean startReader)
        throws IOException
    {
        Pipe testPipe = Pipe.open();

        WritableByteChannel sinkChannel = testPipe.sink();
        chanData.add(new ChannelData("rdrSink", sinkChannel));
        testPipe.sink().configureBlocking(true);

        Pipe.SourceChannel sourceChannel = testPipe.source();
        chanData.add(new ChannelData("rdrSrc", sourceChannel));
        sourceChannel.configureBlocking(false);

        rdr.addDataChannel(sourceChannel, cache, 1024);

        if (startReader) {
            startIOProcess(rdr);
        }

        return sinkChannel;
    }

    public static void glueComponents(String name,
                                      DAQComponentOutputProcess out,
                                      IByteBufferCache outCache,
                                      PayloadValidator validator,
                                      PayloadReader in,
                                      IByteBufferCache inCache)
        throws IOException
    {
        final boolean startOut = false;
        final boolean startIn = false;

        Pipe outPipe = Pipe.open();

        Pipe.SinkChannel sinkOut = outPipe.sink();
        chanData.add(new ChannelData(name + "*OUT", sinkOut));
        sinkOut.configureBlocking(false);

        Pipe.SourceChannel srcOut = outPipe.source();
        chanData.add(new ChannelData(name + "*OUT", srcOut));
        srcOut.configureBlocking(true);

        out.addDataChannel(sinkOut, outCache);

        if (startOut) {
            startIOProcess(out);
        }

        Pipe inPipe = Pipe.open();

        Pipe.SinkChannel sinkIn = inPipe.sink();
        chanData.add(new ChannelData(name + "*IN", sinkIn));
        sinkIn.configureBlocking(true);

        Pipe.SourceChannel srcIn = inPipe.source();
        chanData.add(new ChannelData(name + "*IN", srcIn));
        srcIn.configureBlocking(false);

        in.addDataChannel(srcIn, inCache, 1024);

        if (startIn) {
            startIOProcess(in);
        }

        PayloadFileBridge bridge = new PayloadFileBridge(name, srcOut, sinkIn);
        bridge.setValidator(validator);
        bridge.start();
    }

    public static void initReader(PayloadReader rdr, Splicer splicer,
                                   String rdrName)
    {
        rdr.start();
        waitUntilStopped(rdr, splicer, "creation");
        if (!rdr.isStopped()) {
            throw new Error(rdrName + " in " + rdr.getPresentState() +
                            ", not Idle after creation");
        }
    }

    public static void sendStopMsg(WritableByteChannel chan)
        throws IOException
    {
        if (stopMsg == null) {
            stopMsg = ByteBuffer.allocate(4);
            stopMsg.putInt(0, 4);
            stopMsg.limit(4);
        }

        synchronized (stopMsg) {
            stopMsg.position(0);
            chan.write(stopMsg);
        }
    }

    public static void sendStops(WritableByteChannel[] tails)
        throws IOException
    {
        for (int i = 0; i < tails.length; i++) {
            sendStopMsg(tails[i]);
        }
    }

    public static void startIOProcess(DAQComponentIOProcess rdr)
    {
        if (!rdr.isRunning()) {
            rdr.startProcessing();
            waitUntilRunning(rdr);
        }
    }

    public static final void waitUntilRunning(DAQComponentIOProcess proc)
    {
        waitUntilRunning(proc, "");
    }

    public static final void waitUntilRunning(DAQComponentIOProcess proc,
                                              String extra)
    {
        for (int i = 0; i < REPS && !proc.isRunning(); i++) {
            try {
                Thread.sleep(SLEEP_TIME);
            } catch (InterruptedException ie) {
                // ignore interrupts
            }
        }

        assertTrue("IOProcess in " + proc.getPresentState() +
                   ", not Running after StartSig" + extra, proc.isRunning());
    }

    public static final void waitUntilStopped(DAQComponentIOProcess proc,
                                              Splicer splicer,
                                              String action)
    {
        waitUntilStopped(proc, splicer, action, "");
    }

    private static final void waitUntilStopped(DAQComponentIOProcess proc,
                                               Splicer splicer,
                                               String action,
                                               String extra)
    {
        waitUntilStopped(proc, splicer, action, "", REPS, SLEEP_TIME);
    }

    public static final void waitUntilStopped(DAQComponentIOProcess proc,
                                              Splicer splicer,
                                              String action,
                                              String extra,
                                              int maxReps, int sleepTime)
    {
        int numReps = 0;
        while (numReps < maxReps &&
               ((proc != null && !proc.isStopped()) ||
                (splicer != null && splicer.getState() != Splicer.STOPPED)))
        {
            numReps++;

            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException ie) {
                // ignore interrupts
            }
        }

        if (proc != null) {
            assertTrue("IOProcess in " + proc.getPresentState() +
                       ", not Idle after " + action + extra, proc.isStopped());
        }
        if (splicer != null) {
            assertTrue("Splicer in " + splicer.getStateString() +
                       ", not STOPPED after " + numReps + " reps of " + action +
                       extra,
                       splicer.getState() == Splicer.STOPPED);
        }
    }
}
