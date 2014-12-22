package icecube.daq.test;

import icecube.daq.eventBuilder.EBComponent;
import icecube.daq.splicer.HKN1Splicer;
import icecube.daq.splicer.Splicer;
import icecube.daq.trigger.algorithm.INewAlgorithm;
import icecube.daq.trigger.common.ITriggerAlgorithm;
import icecube.daq.trigger.component.GlobalTriggerComponent;
import icecube.daq.trigger.component.IcetopTriggerComponent;
import icecube.daq.trigger.component.IniceTriggerComponent;
import icecube.daq.trigger.component.TriggerComponent;

import java.util.HashMap;

class AlgorithmData
{
    private int queuedIn;
    private int queuedOut;

    int getQueuedIn()
    {
        return queuedIn;
    }

    int getQueuedOut()
    {
        return queuedOut;
    }

    void setQueuedIn(int val)
    {
        queuedIn = val;
    }

    void setQueuedOut(int val)
    {
        queuedOut = val;
    }

    public String toString()
    {
        return String.format("%d->%d", queuedIn, queuedOut);
    }
}

class TriggerMonitor
{
    private TriggerComponent comp;
    private String prefix;

    private long received;
    private long processed;
    private HashMap<INewAlgorithm, AlgorithmData> algoData =
        new HashMap<INewAlgorithm, AlgorithmData>();;
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

        if (comp != null && comp.getAlgorithms() == null) {
            throw new Error("No algorithms available from " +
                            comp.getClass().getName());
        }

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
            for (ITriggerAlgorithm oldAlgo : comp.getAlgorithms()) {
                INewAlgorithm algo = (INewAlgorithm) oldAlgo;
                if (!algoData.containsKey(algo)) {
                    algoData.put(algo, new AlgorithmData());
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
        for (INewAlgorithm algo : algoData.keySet()) {
            if (buf.length() > 0) buf.append(' ');
            buf.append(algo.getTriggerName()).append(' ');
            buf.append(algoData.get(algo));
        }

        return String.format(" %s %d->%d->[%s]->%d->%d", prefix, received,
                             processed, buf.toString(), queuedOut, sent);
    }
}

class EventBuilderMonitor
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
        if (be.getNumBadTriggerRequests() > 0) {
            if (buf.length() > 0) buf.append(' ');
            buf.append("BadTRs ").append(be.getNumBadTriggerRequests());
        }
        if (be.getNumTriggerRequestsDropped() > 0) {
            if (buf.length() > 0) buf.append(' ');
            buf.append("DropTRs ").append(be.getNumTriggerRequestsDropped());
        }
        if (be.getNumTriggerRequestsQueued() > 0) {
            if (buf.length() > 0) buf.append(' ');
            buf.append("QueuedTRs ").append(be.getNumTriggerRequestsQueued());
        }
        if (be.getNumTriggerRequestsReceived() > 0) {
            if (buf.length() > 0) buf.append(' ');
            buf.append("TRsRcvd ").append(be.getNumTriggerRequestsReceived());
        }
        if (be.getNumNullReadouts() > 0) {
            if (buf.length() > 0) buf.append(' ');
            buf.append("NullROs ").append(be.getNumNullReadouts());
        }
        if (be.getNumBadReadouts() > 0) {
            if (buf.length() > 0) buf.append(' ');
            buf.append("BadROs ").append(be.getNumBadReadouts());
        }
        if (be.getNumReadoutsDiscarded() > 0) {
            if (buf.length() > 0) buf.append(' ');
            buf.append("DiscROs ").append(be.getNumReadoutsDiscarded());
        }
        if (be.getNumReadoutsDropped() > 0) {
            if (buf.length() > 0) buf.append(' ');
            buf.append("DropROs ").append(be.getNumReadoutsDropped());
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
        if (be.getNumNullEvents() > 0) {
            if (buf.length() > 0) buf.append(' ');
            buf.append("NullEvts ").append(be.getNumNullEvents());
        }
        if (be.getNumBadEvents() > 0) {
            if (buf.length() > 0) buf.append(' ');
            buf.append("BadEvts ").append(be.getNumBadEvents());
        }
        if (be.getNumEventsFailed() > 0) {
            if (buf.length() > 0) buf.append(' ');
            buf.append("FailedEvts ").append(be.getNumEventsFailed());
        }
        if (be.getNumEventsIgnored() > 0) {
            if (buf.length() > 0) buf.append(' ');
            buf.append("IgnEvts ").append(be.getNumEventsIgnored());
        }
        if (be.getNumEventsSent() > 0) {
            if (buf.length() > 0) buf.append(' ');
            buf.append("EvtsSent ").append(be.getNumEventsSent());
        }
        if (buf.length() > 0) System.err.println("** BackEnd: " + buf);
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

    ActivityMonitor(IniceTriggerComponent iiComp,
                    IcetopTriggerComponent itComp,
                    GlobalTriggerComponent gtComp,
                    EBComponent ebComp)
    {
        this.iiMon = new TriggerMonitor(iiComp, "II");
        this.itMon = new TriggerMonitor(itComp, "IT");
        this.gtMon = new TriggerMonitor(gtComp, "GT");
        this.ebMon = new EventBuilderMonitor(ebComp, "EB");
    }

    public void dumpBackEndStats()
    {
        ebMon.dumpStats();
    }

    private void dumpProgress(int rep, int expEvents, boolean dumpSplicers)
    {
        System.err.println("#" + rep + ":" + iiMon + itMon + gtMon + ebMon);

        if (dumpSplicers && iiMon.getSent() < expEvents + 1) {
            dumpSplicer("II", iiMon.getSplicer());
        }

        if (dumpSplicers && itMon.getSent() < expEvents + 1) {
            dumpSplicer("IT", itMon.getSplicer());
        }

        if (dumpSplicers && gtMon.getSent() < iiMon.getSent()) {
            dumpSplicer("GT", gtMon.getSplicer());
        }

        if (dumpSplicers && ebMon.getSent() + 1 < gtMon.getSent()) {
            dumpSplicer("EB", ebMon.getSplicer());
        }
    }

    private void dumpSplicer(String title, Splicer splicer)
    {
        System.err.println("*********************");
        System.err.println("*** " + title + " Splicer");
        System.err.println("*********************");
        String[] desc = ((HKN1Splicer) splicer).dumpDescription();
        for (int d = 0; d < desc.length; d++) {
            System.err.println("  " + desc[d]);
        }
    }

    boolean waitForStasis(int staticReps, int maxReps, int expEvents,
                          boolean verbose, boolean dumpSplicers)
    {
        final int SLEEP_MSEC = 100;

        int numStatic = 0;
        for (int i = 0; i < maxReps; i++) {
            boolean changed = false;

            changed |= iiMon.check();
            changed |= itMon.check();
            changed |= gtMon.check();
            changed |= ebMon.check();

            if (changed) {
                numStatic = 0;
            } else if (iiMon.isStopped() && itMon.isStopped() &&
                       gtMon.isStopped() && ebMon.isStopped())
            {
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
