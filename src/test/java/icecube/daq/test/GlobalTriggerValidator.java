package icecube.daq.test;

import icecube.daq.payload.ITriggerRequestPayload;
import icecube.daq.payload.IUTCTime;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

class GlobalTriggerValidator
    extends TriggerValidator
{
    private Log LOG = LogFactory.getLog(GlobalTriggerValidator.class);

    private int prevUID;
    private long prevFirstTime = Long.MIN_VALUE;
    private long prevLastTime = Long.MIN_VALUE;

    @Override
    boolean validateTrigger(ITriggerRequestPayload tr)
    {
        super.validateTrigger(tr);

        IUTCTime firstTime = tr.getFirstTimeUTC();
        IUTCTime lastTime = tr.getLastTimeUTC();

        if (firstTime == null) {
            LOG.error("Trigger request " + tr + " first time is null");
            return false;
        } else if (lastTime == null) {
            LOG.error("Trigger request " + tr + " last time is null");
            return false;
        } else if (firstTime.longValue() < prevFirstTime) {
            try {
                throw new Error("FirstTime mismatch");
            } catch (Error err) {
                LOG.error("Trigger #" + tr.getUID() + " first time in [" +
                          firstTime + "-" + lastTime +
                          "] preceeds previous trigger#" + prevUID + " [" +
                          prevFirstTime + "-" + prevLastTime + "]", err);
            }
            return false;
        } else if (lastTime.longValue() < prevLastTime) {
            try {
                throw new Error("LastTime mismatch");
            } catch (Error err) {
                LOG.error("Trigger #" + tr.getUID() +
                          " last time in [" + firstTime + "-" +
                          lastTime + "] preceeds previous trigger#" +
                          prevUID + " [" + prevLastTime + "-" +
                          prevLastTime + "]", err);
            }
            return false;
        } else {
            prevUID = tr.getUID();
            prevFirstTime = firstTime.longValue();
            prevLastTime = lastTime.longValue();
        }

        return true;
    }
}
