package icecube.daq.test;

import icecube.daq.payload.ITriggerRequestPayload;
import icecube.daq.payload.IWriteablePayload;
import icecube.daq.payload.PayloadChecker;

import org.apache.log4j.Logger;

class TriggerValidator
    extends BaseValidator
{
    private static final Logger LOG = Logger.getLogger(TriggerValidator.class);

    @Override
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
