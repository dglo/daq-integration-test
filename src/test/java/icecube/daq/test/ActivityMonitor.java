package icecube.daq.test;

import icecube.daq.eventBuilder.EBComponent;
import icecube.daq.splicer.HKN1Splicer;
import icecube.daq.splicer.Splicer;
import icecube.daq.trigger.algorithm.ITriggerAlgorithm;
import icecube.daq.trigger.component.GlobalTriggerComponent;
import icecube.daq.trigger.component.IcetopTriggerComponent;
import icecube.daq.trigger.component.IniceTriggerComponent;
import icecube.daq.trigger.component.TriggerComponent;

import java.util.Arrays;
import java.util.HashMap;

class AlgorithmData
{
    private int queuedIn;
    private int queuedOut;
    private long sent;

    int getQueuedIn()
    {
        return queuedIn;
    }

    int getQueuedOut()
    {
        return queuedOut;
    }

    long getSent()
    {
        return sent;
    }

    void setQueuedIn(int val)
    {
        queuedIn = val;
    }

    void setQueuedOut(int val)
    {
        queuedOut = val;
    }

    void setSent(long val)
    {
        sent = val;
    }

    public String toString()
    {
        return String.format("%d->%d->%d", queuedIn, queuedOut, sent);
    }
}

interface ComponentMonitor
{
    boolean check();
    String getPrefix();
    Splicer getSplicer();
    boolean isStopped();
}

class TriggerMonitor
    implements ComponentMonitor
{
    private TriggerComponent comp;
    private String prefix;

    private long received;
    private long processed;
    private HashMap<ITriggerAlgorithm, AlgorithmData> algoData =
        new HashMap<ITriggerAlgorithm, AlgorithmData>();
    private ITriggerAlgorithm[] algoKeys;
    private long queuedOut;
    private long sent;
    private boolean stopped;
    private boolean summarized;

    TriggerMonitor(TriggerComponent comp, String prefix)
    {
        this.comp = comp;
        this.prefix = prefix;
    }

    public boolean check()
    {
        if (stopped != summarized) {
            summarized = stopped;
        }

        boolean newStopped = (comp == null ||
                              (!comp.getReader().isRunning() &&
                               comp.getWriter().isStopped()));

        boolean changed = false;
        if (comp != null && !summarized) {
            if (received != comp.getPayloadsReceived()) {
                received = comp.getPayloadsReceived();
                changed = true;
            }

            if (processed != comp.getTriggerManager().getTotalProcessed()) {
                processed = comp.getTriggerManager().getTotalProcessed();
                changed = true;
            }

            Iterable<ITriggerAlgorithm> iter = comp.getAlgorithms();
            if (iter == null) {
                throw new Error("No algorithms available from " +
                                comp.getClass().getName());
            }

            boolean added = false;
            for (ITriggerAlgorithm algo : iter) {
                if (!algoData.containsKey(algo)) {
                    algoData.put(algo, new AlgorithmData());
                    added = true;
                }
                AlgorithmData data = algoData.get(algo);
                if (data.getQueuedIn() != algo.getInputQueueSize()) {
                    data.setQueuedIn(algo.getInputQueueSize());
                    changed = true;
                }
                if (data.getQueuedOut() != algo.getNumberOfCachedRequests()) {
                    data.setQueuedOut(algo.getNumberOfCachedRequests());
                    changed = true;
                }
                if (data.getSent() != algo.getSentTriggerCount()) {
                    data.setSent(algo.getSentTriggerCount());
                    changed = true;
                }
            }
            if (added) {
                Object[] tmpKeys = algoData.keySet().toArray();
                Arrays.sort(tmpKeys);
                algoKeys = new ITriggerAlgorithm[tmpKeys.length];
                for (int i = 0; i < tmpKeys.length; i++) {
                    algoKeys[i] = (ITriggerAlgorithm) tmpKeys[i];
                }
            }

            if (queuedOut != comp.getTriggerManager().getNumOutputsQueued()) {
                queuedOut = comp.getTriggerManager().getNumOutputsQueued();
                changed = true;
            }

            if (sent != comp.getPayloadsSent()) {
                sent = comp.getPayloadsSent();
                changed = true;
            }
        }

        if (stopped != newStopped) {
            stopped = newStopped;
        }

        return changed;
    }

    public String getPrefix()
    {
        return prefix;
    }

    public long getSent()
    {
        return sent;
    }

    public Splicer getSplicer()
    {
        return comp.getSplicer();
    }

    public boolean isStopped()
    {
        return stopped;
    }

    public String toString()
    {
        if (comp == null) {
            return "";
        }

        if (summarized) {
            return " " + prefix + " stopped";
        }

        summarized = stopped;

        StringBuilder buf = new StringBuilder();

        if (algoKeys != null) {
            for (ITriggerAlgorithm algo : algoKeys) {
                if (buf.length() > 0) buf.append(' ');
                buf.append(algo.getTriggerName()).append(' ');
                buf.append(algoData.get(algo));
            }
        }

        return String.format(" %s %d->%d->[%s]->%d->%d", prefix, received,
                             processed, buf.toString(), queuedOut, sent);
    }
}

class EventBuilderMonitor
    implements ComponentMonitor
{
    private EBComponent comp;
    private String prefix;

    private long reqRcvd;
    private long reqQueued;
    private long reqSent;
    private long dataRcvd;
    private long dataQueued;
    private long evtsSent;

    private boolean stopped;
    private boolean summarized;

    EventBuilderMonitor(EBComponent comp, String prefix)
    {
        this.comp = comp;
        this.prefix = prefix;
    }

    public boolean check()
    {
        if (stopped != summarized) {
            summarized = stopped;
        }

        boolean newStopped = (comp == null ||
                              (!comp.getTriggerReader().isRunning() &&
                               comp.getRequestWriter().isStopped() &&
                               !comp.getDataReader().isRunning() &&
                               !comp.getDispatcher().isStarted()));

        boolean changed = false;
        if (!stopped && comp != null) {
            if (reqRcvd != comp.getTriggerRequestsReceived()) {
                reqRcvd = comp.getTriggerRequestsReceived();
                changed = true;
            }
            if (reqQueued !=
                comp.getMonitoringData().getNumTriggerRequestsQueued())
            {
                reqQueued =
                    comp.getMonitoringData().getNumTriggerRequestsQueued();
                changed = true;
            }
            if (reqSent != comp.getRequestsSent()) {
                reqSent = comp.getRequestsSent();
                changed = true;
            }
            if (dataRcvd != comp.getReadoutsReceived()) {
                dataRcvd = comp.getReadoutsReceived();
                changed = true;
            }
            if (dataQueued !=
                comp.getMonitoringData().getNumReadoutsQueued())
            {
                dataQueued =
                    comp.getMonitoringData().getNumReadoutsQueued();
                changed = true;
            }
            if (evtsSent != comp.getEventsSent()) {
                evtsSent = comp.getEventsSent();
                changed = true;
            }
        }

        if (stopped != newStopped) {
            stopped = newStopped;
        }

        return changed;
    }

    public void dumpStats()
    {
        icecube.daq.eventBuilder.backend.EventBuilderBackEnd be =
            comp.getBackEnd();

        StringBuilder buf = new StringBuilder();
        if (be.getNumTriggerRequestsQueued() > 0) {
            if (buf.length() > 0) buf.append(' ');
            buf.append("QueuedTRs ").append(be.getNumTriggerRequestsQueued());
        }
        if (be.getNumTriggerRequestsReceived() > 0) {
            if (buf.length() > 0) buf.append(' ');
            buf.append("TRsRcvd ").append(be.getNumTriggerRequestsReceived());
        }
        if (be.getNumReadoutsQueued() > 0) {
            if (buf.length() > 0) buf.append(' ');
            buf.append("QueuedROs ").append(be.getNumReadoutsQueued());
        }
        if (be.getNumReadoutsCached() > 0) {
            if (buf.length() > 0) buf.append(' ');
            buf.append("CachedROs ").append(be.getNumReadoutsCached());
        }
        if (be.getNumReadoutsReceived() > 0) {
            if (buf.length() > 0) buf.append(' ');
            buf.append("ROsRcvd ").append(be.getNumReadoutsReceived());
        }
        if (be.getNumBadEvents() > 0) {
            if (buf.length() > 0) buf.append(' ');
            buf.append("BadEvts ").append(be.getNumBadEvents());
        }
        if (be.getNumEventsSent() > 0) {
            if (buf.length() > 0) buf.append(' ');
            buf.append("EvtsSent ").append(be.getNumEventsSent());
        }
        if (buf.length() > 0) System.err.println("** BackEnd: " + buf);
    }

    public String getPrefix()
    {
        return prefix;
    }

    public long getSent()
    {
        return evtsSent;
    }

    public Splicer getSplicer()
    {
        return comp.getDataSplicer();
    }

    public boolean isStopped()
    {
        return stopped;
    }

    public String toString()
    {
        if (comp == null) {
            return "";
        }

        if (summarized) {
            return " " + prefix + " stopped";
        }

        summarized = stopped;
        return String.format(" EB %d->[%d]->%d->%d->[%d]->%d", reqRcvd,
                             reqQueued, reqSent, dataRcvd, dataQueued,
                             evtsSent);
    }
}

public class ActivityMonitor
{
    private TriggerMonitor iiMon;
    private TriggerMonitor itMon;
    private TriggerMonitor gtMon;
    private EventBuilderMonitor ebMon;
    private ComponentMonitor monList;

    public ActivityMonitor(IniceTriggerComponent iiComp,
                           IcetopTriggerComponent itComp,
                           GlobalTriggerComponent gtComp,
                           EBComponent ebComp)
    {
        iiMon = new TriggerMonitor(iiComp, "II");
        itMon = new TriggerMonitor(itComp, "IT");
        gtMon = new TriggerMonitor(gtComp, "GT");
        ebMon = new EventBuilderMonitor(ebComp, "EB");
    }

    public void dumpBackEndStats()
    {
        ebMon.dumpStats();
    }

    private void dumpProgress(int rep, int expEvents, boolean dumpSplicers)
    {
        System.err.println("#" + rep + ":" + iiMon + itMon + gtMon + ebMon);

        if (dumpSplicers && iiMon.getSent() < expEvents + 1) {
            dumpSplicer(iiMon);
        }

        if (dumpSplicers && itMon.getSent() < expEvents + 1) {
            dumpSplicer(itMon);
        }

        if (dumpSplicers && gtMon.getSent() < iiMon.getSent()) {
            dumpSplicer(gtMon);
        }

        if (dumpSplicers && ebMon.getSent() + 1 < gtMon.getSent()) {
            dumpSplicer(ebMon);
        }
    }

    private void dumpSplicer(ComponentMonitor mon)
    {
        final String title = mon.getPrefix();
        final Splicer splicer = mon.getSplicer();

        final String splats = "*********************";
        if (!(splicer instanceof HKN1Splicer)) {
            System.err.println(splats);
            System.err.println("*** Unknown " + title + " Splicer: " +
                               splicer.getClass().getName());
            System.err.println(splats);
        } else {
            System.err.println(splats);
            System.err.println("*** " + title + " Splicer");
            System.err.println(splats);
            String[] desc = ((HKN1Splicer) splicer).dumpDescription();
            for (int d = 0; d < desc.length; d++) {
                System.err.println("  " + desc[d]);
            }
        }
    }

    private boolean isChanged()
    {
        boolean changed = false;

        changed |= iiMon.check();
        changed |= itMon.check();
        changed |= gtMon.check();
        changed |= ebMon.check();

        return changed;
    }

    private boolean isStopped()
    {
        return iiMon.isStopped() && itMon.isStopped() && gtMon.isStopped() &&
            ebMon.isStopped();
    }

    public boolean waitForStasis(int staticReps, int maxReps, int expEvents,
                                 boolean verbose, boolean dumpSplicers)
    {
        final int SLEEP_MSEC = 100;

        int numStatic = 0;
        for (int i = 0; i < maxReps; i++) {

            if (isChanged()) {
                numStatic = 0;
            } else if (isStopped()) {
                numStatic += staticReps / 2;
            } else {
                numStatic++;
            }

            if (verbose) {
                dumpProgress(i, expEvents, dumpSplicers);
            }

            if (numStatic >= staticReps) {
                break;
            }

            try {
                Thread.sleep(SLEEP_MSEC);
            } catch (Throwable thr) {
                // ignore errors
            }
        }

        return numStatic >= staticReps;
    }
}
