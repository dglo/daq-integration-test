package icecube.daq.test;

import icecube.daq.payload.IWriteablePayload;
import icecube.daq.payload.PayloadChecker;
import icecube.daq.trigger.ITriggerRequestPayload;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

class TriggerValidator
    extends BaseValidator
{
    private static final Log LOG = LogFactory.getLog(TriggerValidator.class);

    public boolean validate(IWriteablePayload payload)
    {
        if (!(payload instanceof ITriggerRequestPayload)) {
            throw new Error("Unexpected payload " +
                            payload.getClass().getName());
        }

        return validateTrigger((ITriggerRequestPayload) payload);
    }

    boolean validateTrigger(ITriggerRequestPayload tr)
    {
        if (!PayloadChecker.validateTriggerRequest(tr, true)) {
            LOG.error("Trigger request " + tr + " is not valid");
            return false;
        }

        //System.err.println("GOT " + tr);

        return true;
    }
}

