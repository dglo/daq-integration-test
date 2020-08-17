package icecube.daq.test;

import icecube.daq.eventBuilder.EBComponent;
import icecube.daq.io.DAQComponentIOProcess;
import icecube.daq.io.DAQComponentOutputProcess;
import icecube.daq.io.DAQStreamReader;
import icecube.daq.juggler.component.DAQCompException;
import icecube.daq.juggler.component.DAQConnector;
import icecube.daq.payload.IByteBufferCache;
import icecube.daq.splicer.Splicer;
import icecube.daq.stringhub.StringHubComponent;
import icecube.daq.trigger.component.GlobalTriggerComponent;
import icecube.daq.trigger.component.IcetopTriggerComponent;
import icecube.daq.trigger.component.IniceTriggerComponent;
import icecube.daq.trigger.component.TriggerComponent;

import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;

import junit.framework.Assert;

import org.apache.log4j.Logger;

class ChannelData
{
    private static final Logger LOG = Logger.getLogger(ChannelData.class);

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
            try {
                chan.close();
            } catch (IOException ioe) {
                // ignore errors
            }
        }
    }

    @Override
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
                                   StringHubComponent[] shComps)
        throws DAQCompException
    {
        checkCaches(ebComp, gtComp, itComp, iiComp, shComps, true);
    }

    public static void checkCaches(EBComponent ebComp,
                                   GlobalTriggerComponent gtComp,
                                   IcetopTriggerComponent itComp,
                                   IniceTriggerComponent iiComp,
                                   StringHubComponent[] shComps,
                                   boolean crossCheck)
        throws DAQCompException
    {
        final boolean debug = false;

        if (crossCheck && gtComp != null && ebComp != null) {
            assertEquals("Mismatch between global triggers sent and" +
                         " events sent", gtComp.getPayloadsSent() - 1,
                         ebComp.getEventsSent());
        }

        IByteBufferCache ebGTCache = null;

        if (ebComp != null) {
            ebGTCache =
                ebComp.getByteBufferCache(DAQConnector.TYPE_GLOBAL_TRIGGER);
            if (debug) System.err.println("EB GTcache " + ebGTCache);
            assertTrue("EB trigger buffer cache is unbalanced (" + ebGTCache +
                       ")", ebGTCache.isBalanced());
            assertTrue("EB trigger buffer cache is unused (" + ebGTCache +
                       ")", ebGTCache.getTotalBuffersAcquired() > 0);

            IByteBufferCache ebRDCache =
                ebComp.getByteBufferCache(DAQConnector.TYPE_READOUT_DATA);
            if (debug) System.err.println("EB RDcache " + ebRDCache);
            if (debug) System.err.println("EB RDrcvd " +
                                          ebComp.getReadoutsReceived());
            assertTrue("EB readout data buffer cache is unbalanced (" +
                       ebRDCache + ")", ebRDCache.isBalanced());
            assertTrue("EB readout data buffer cache is unused (" + ebRDCache +
                       ")", ebRDCache.getTotalBuffersAcquired() > 0);

            IByteBufferCache ebEvtCache =
                ebComp.getByteBufferCache(DAQConnector.TYPE_EVENT);
            if (debug) System.err.println("EB EVTcache " + ebEvtCache);
            assertTrue("EB event buffer cache is unbalanced (" + ebEvtCache +
                       ")", ebEvtCache.isBalanced());
            assertTrue("EB event buffer cache is unused (" + ebEvtCache + ")",
                       ebEvtCache.getTotalBuffersAcquired() > 0);

            IByteBufferCache ebGenCache =
                ebComp.getByteBufferCache(DAQConnector.TYPE_GENERIC_CACHE);
            if (debug) System.err.println("EB GENcache " + ebGenCache);
            assertTrue("EB Generic buffer cache is unbalanced (" + ebGenCache +
                       ")", ebGenCache.isBalanced());
            assertTrue("EB Generic buffer cache is unused (" + ebGenCache +
                       ")", ebGenCache.getTotalBuffersAcquired() > 0);

            assertEquals("Mismatch between readouts received and allocated",
                         ebComp.getReadoutsReceived(),
                         ebRDCache.getTotalBuffersAcquired());
        }

        if (gtComp != null) {
            checkTriggerCaches(gtComp, "Global trigger", debug);
        }
        if (itComp != null) {
            checkTriggerCaches(itComp, "Icetop trigger", debug);
        }
        if (iiComp != null) {
            checkTriggerCaches(iiComp, "In-ice trigger", debug);
        }

        if (crossCheck && gtComp != null && ebGTCache != null) {
            assertEquals("Mismatch between triggers sent and events sent",
                         (gtComp.getPayloadsSent() - 1),
                         ebGTCache.getTotalBuffersAcquired());
        }

        if (shComps != null) {
            for (int i = 0; i < shComps.length; i++) {
                final String name = "SH#" + shComps[i].getHubId();

                IByteBufferCache shRDCache =
                    shComps[i].getByteBufferCache(DAQConnector.TYPE_READOUT_DATA);
                if (debug) System.err.println("SH RDcache " + shRDCache);
                assertTrue(name + " readout data buffer cache is unbalanced" +
                           " (" + shRDCache + ")", shRDCache.isBalanced());
                //assertTrue(name + " readout data buffer cache is unused (" +
                //           shRDCache + ")",
                //           shRDCache.getTotalBuffersAcquired() > 0);

                IByteBufferCache shGenCache =
                    shComps[i].getByteBufferCache(DAQConnector.TYPE_GENERIC_CACHE);
                if (debug) System.err.println(name + " Gencache " +
                                              shGenCache);
                assertTrue(name + " generic buffer cache is unbalanced (" +
                       shGenCache + ")", shGenCache.isBalanced());
                assertTrue(name + " generic buffer cache is unused (" +
                           shGenCache + ")",
                           shGenCache.getTotalBuffersAcquired() > 0);

                IByteBufferCache shMoniCache =
                    shComps[i].getByteBufferCache(DAQConnector.TYPE_MONI_DATA);
                if (debug) System.err.println(name + " Monicache " +
                                              shMoniCache);
                assertTrue(name + " MONI buffer cache is unbalanced (" +
                           shMoniCache + ")", shMoniCache.isBalanced());
                assertTrue(name + " MONI buffer cache was used (" +
                           shMoniCache + ")",
                           shMoniCache.getTotalBuffersAcquired() == 0);

                IByteBufferCache shTCalCache =
                    shComps[i].getByteBufferCache(DAQConnector.TYPE_TCAL_DATA);
                if (debug) System.err.println(name + " TCalcache " +
                                              shTCalCache);
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

    public static final void closePipeList(Pipe[] list)
    {
        for (int i = 0; i < list.length; i++) {
            try {
                list[i].sink().close();
            } catch (IOException ioe) {
                // ignore errors on close
            }
            try {
                list[i].source().close();
            } catch (IOException ioe) {
                // ignore errors on close
            }
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

        out.addDataChannel(sinkOut, outCache, "Sink");

        if (startOut) {
            startIOProcess(out);
        }

        PayloadSink consumer = new PayloadSink(name, srcOut);
        consumer.setValidator(validator);
        consumer.start();

        return consumer;
    }

    public static Pipe connectToReader(DAQStreamReader rdr,
                                       IByteBufferCache cache)
        throws IOException
    {
        Pipe testPipe = Pipe.open();

        WritableByteChannel sinkChannel = testPipe.sink();
        chanData.add(new ChannelData("rdrSink", sinkChannel));
        testPipe.sink().configureBlocking(true);

        Pipe.SourceChannel sourceChannel = testPipe.source();
        chanData.add(new ChannelData("rdrSrc", sourceChannel));
        sourceChannel.configureBlocking(false);

        rdr.addDataChannel(sourceChannel, "rdrSink", cache, 1024);

        return testPipe;
    }

    public static Pipe[] connectToReader(DAQStreamReader rdr,
                                         IByteBufferCache cache,
                                         int numTails)
        throws IOException
    {
        Pipe[] chanList = new Pipe[numTails];

        for (int i = 0; i < chanList.length; i++) {
            chanList[i] = connectToReader(rdr, cache);
        }

        return chanList;
    }

    public static void destroyComponentIO(EBComponent ebComp,
                                          GlobalTriggerComponent gtComp,
                                          IcetopTriggerComponent itComp,
                                          IniceTriggerComponent iiComp,
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

        if (shComps != null) {
            for (int i = 0; i < shComps.length; i++) {
                shComps[i].getHitWriter().destroyProcessor();
                shComps[i].getRequestReader().destroyProcessor();
                shComps[i].getDataWriter().destroyProcessor();
            }
        }
    }

    public static void glueComponents(String name,
                                      DAQComponentOutputProcess out,
                                      IByteBufferCache outCache,
                                      PayloadValidator validator,
                                      DAQStreamReader in,
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

        out.addDataChannel(sinkOut, outCache, name + "*GLUE");

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

        in.addDataChannel(srcIn, "glueChan", inCache, 1024);

        if (startIn) {
            startIOProcess(in);
        }

        PayloadFileBridge bridge = new PayloadFileBridge(name, srcOut, sinkIn);
        bridge.setValidator(validator);
        bridge.start();
    }

    public static void initReader(DAQStreamReader rdr, Splicer splicer,
                                   String rdrName)
    {
        rdr.start();
        waitUntilStopped(rdr, splicer, "creation");
        if (!rdr.isStopped()) {
            throw new Error(rdrName + " in " + rdr.getPresentState() +
                            ", not Idle after creation");
        }
    }

    public static final void logOpenChannels()
    {
        for (ChannelData cd : chanData) {
            cd.logOpen();
        }
    }

    public static void removeDispatchedFiles(String destDir)
    {
        File dir = new File(destDir);
        for (File file : dir.listFiles(new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    if (!name.startsWith("physics_") &&
                        !name.startsWith("moni_") &&
                        !name.startsWith("sn_") &&
                        !name.startsWith("tcal_"))
                    {
                        return false;
                    }
                    if (!name.endsWith(".dat")) {
                        return false;
                    }
                    return true;
                }
            }))
        {
            try {
                if (!file.delete()) {
                    System.err.println("Cannot delete \"" + file + "\"");
                }
            } catch (SecurityException se) {
                System.err.println("Cannot delete \"" + file + "\"");
                se.printStackTrace();
            }
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

    public static void sendStops(Pipe[] tails)
        throws IOException
    {
        for (int i = 0; i < tails.length; i++) {
            sendStopMsg(tails[i].sink());
        }
    }

    public static void startComponentIO(EBComponent ebComp,
                                        GlobalTriggerComponent gtComp,
                                        IcetopTriggerComponent itComp,
                                        IniceTriggerComponent iiComp,
                                        StringHubComponent[] shComps,
                                        int runNumber, int domMode)
        throws DAQCompException, IOException
    {
        ArrayList<DAQComponentIOProcess> procList =
            new ArrayList<DAQComponentIOProcess>();

        if (ebComp != null) {
            ebComp.starting(runNumber, domMode);
            procList.add(ebComp.getTriggerReader());
            procList.add(ebComp.getRequestWriter());
            procList.add(ebComp.getDataReader());
        }
        if (gtComp != null) {
            gtComp.starting(runNumber, domMode);
            procList.add(gtComp.getReader());
            procList.add(gtComp.getWriter());
        }
        if (itComp != null) {
            itComp.starting(runNumber, domMode);
            procList.add(itComp.getReader());
            procList.add(itComp.getWriter());
        }
        if (iiComp != null) {
            iiComp.starting(runNumber, domMode);
            procList.add(iiComp.getReader());
            procList.add(iiComp.getWriter());
        }
        if (shComps != null) {
            for (int i = 0; i < shComps.length; i++) {
                // starting hubs involves hardware initialization
                // so do the bare minimum needed to init Sender
                shComps[i].setRunNumber(runNumber);
                shComps[i].setDOMMode(domMode);
                shComps[i].getSender().startup();
                procList.add(shComps[i].getHitWriter());
                procList.add(shComps[i].getRequestReader());
                procList.add(shComps[i].getDataWriter());
            }
        }

        for (DAQComponentIOProcess proc : procList) {
            if (!proc.isRunning()) {
                proc.startProcessing();
            }
        }

        for (DAQComponentIOProcess proc : procList) {
            if (!proc.isRunning()) {
                waitUntilRunning(proc);
            }
        }
    }

    private static void startIOProcess(DAQComponentIOProcess proc)
    {
        if (!proc.isRunning()) {
            proc.startProcessing();
            waitUntilRunning(proc);
        }
    }

    private static final void waitUntilRunning(DAQComponentIOProcess proc)
    {
        for (int i = 0; i < REPS && !proc.isRunning(); i++) {
            try {
                Thread.sleep(SLEEP_TIME);
            } catch (InterruptedException ie) {
                // ignore interrupts
            }
        }

        assertTrue("IOProcess in " + proc.getPresentState() +
                   ", not Running after StartSig", proc.isRunning());
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
                (splicer != null &&
                 splicer.getState() != Splicer.State.STOPPED)))
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
            assertTrue("Splicer in " + splicer.getState().name() +
                       ", not STOPPED after " + numReps + " reps of " + action +
                       extra, splicer.getState() == Splicer.State.STOPPED);
        }
    }
}
