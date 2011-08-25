package icecube.daq.test;

import icecube.daq.eventBuilder.EBComponent;
import icecube.daq.splicer.HKN1Splicer;
import icecube.daq.splicer.Splicer;
import icecube.daq.trigger.component.AmandaTriggerComponent;
import icecube.daq.trigger.component.GlobalTriggerComponent;
import icecube.daq.trigger.component.IcetopTriggerComponent;
import icecube.daq.trigger.component.IniceTriggerComponent;

public class ActivityMonitor
{
    private IniceTriggerComponent iiComp;
    private IcetopTriggerComponent itComp;
    private AmandaTriggerComponent amComp;
    private GlobalTriggerComponent gtComp;
    private EBComponent ebComp;

    private long iiRcvd;
    private long iiSent;
    private long itRcvd;
    private long itSent;
    private long amRcvd;
    private long amSent;
    private long gtRcvd;
    private long gtSent;
    private long reqRcvd;
    private long reqSent;
    private long dataRcvd;
    private long evtsSent;

    ActivityMonitor(IniceTriggerComponent iiComp,
                    IcetopTriggerComponent itComp,
                    AmandaTriggerComponent amComp,
                    GlobalTriggerComponent gtComp,
                    EBComponent ebComp)
    {
        this.iiComp = iiComp;
        this.itComp = itComp;
        this.amComp = amComp;
        this.gtComp = gtComp;
        this.ebComp = ebComp;
    }

    public void dumpBackEndStats()
    {
        icecube.daq.eventBuilder.backend.EventBuilderBackEnd be =
            ebComp.getBackEnd();

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

    private void dumpProgress(int rep, int expEvents, boolean dumpSplicers)
    {
        String iiStr;
        if (iiComp == null) {
            iiStr = "";
        } else {
            iiStr = String.format(" II %d->%d", iiRcvd, iiSent);
        }

        String itStr;
        if (itComp == null) {
            itStr = "";
        } else {
            itStr = String.format(" IT %d->%d", itRcvd, itSent);
        }

        String amStr;
        if (amComp == null) {
            amStr = "";
        } else {
            amStr = String.format(" AM %d->%d", amRcvd, amSent);
        }

        String gtStr;
        if (gtComp == null) {
            gtStr = "";
        } else {
            gtStr = String.format(" GT %d->%d", gtRcvd, gtSent);
        }

        String ebStr;
        if (ebComp == null) {
            ebStr = "";
        } else {
            ebStr = String.format(" EB %d->%d->%d->%d", reqRcvd, reqSent,
                                  dataRcvd, evtsSent);
        }

        System.err.println("#" + rep + ":" + iiStr + itStr + amStr + gtStr +
                           ebStr);

        if (dumpSplicers && iiSent < expEvents + 1) {
            dumpSplicer("II", iiComp.getSplicer());
        }

        if (dumpSplicers && itSent < expEvents + 1) {
            dumpSplicer("IT", itComp.getSplicer());
        }

        if (dumpSplicers && amSent < expEvents + 1) {
            dumpSplicer("AM", amComp.getSplicer());
        }

        if (dumpSplicers && gtSent < iiSent) {
            dumpSplicer("GT", gtComp.getSplicer());
        }

        if (dumpSplicers && evtsSent + 1 < gtSent) {
            dumpSplicer("EB", ebComp.getDataSplicer());
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

            if (iiComp != null && iiRcvd != iiComp.getPayloadsReceived()) {
                iiRcvd = iiComp.getPayloadsReceived();
                changed = true;
            }
            if (iiComp != null && iiSent != iiComp.getPayloadsSent()) {
                iiSent = iiComp.getPayloadsSent();
                changed = true;
            }
            if (itComp != null && itRcvd != itComp.getPayloadsReceived()) {
                itRcvd = itComp.getPayloadsReceived();
                changed = true;
            }
            if (itComp != null && itSent != itComp.getPayloadsSent()) {
                itSent = itComp.getPayloadsSent();
                changed = true;
            }
            if (amComp != null && amRcvd != amComp.getPayloadsReceived()) {
                amRcvd = amComp.getPayloadsReceived();
                changed = true;
            }
            if (amComp != null && amSent != amComp.getPayloadsSent()) {
                amSent = amComp.getPayloadsSent();
                changed = true;
            }
            if (gtComp != null && gtRcvd != gtComp.getPayloadsReceived()) {
                gtRcvd = gtComp.getPayloadsReceived();
                changed = true;
            }
            if (gtComp != null && gtSent != gtComp.getPayloadsSent()) {
                gtSent = gtComp.getPayloadsSent();
                changed = true;
            }
            if (ebComp != null &&
                reqRcvd != ebComp.getTriggerRequestsReceived()) {
                reqRcvd = ebComp.getTriggerRequestsReceived();
                changed = true;
            }
            if (ebComp != null && reqSent != ebComp.getRequestsSent()) {
                reqSent = ebComp.getRequestsSent();
                changed = true;
            }
            if (ebComp != null && dataRcvd != ebComp.getReadoutsReceived()) {
                dataRcvd = ebComp.getReadoutsReceived();
                changed = true;
            }
            if (ebComp != null && evtsSent != ebComp.getEventsSent()) {
                evtsSent = ebComp.getEventsSent();
                changed = true;
            }

            if (changed) {
                numStatic = 0;
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
