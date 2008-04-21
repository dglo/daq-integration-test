package icecube.daq.test;

import icecube.daq.payload.IWriteablePayload;
import icecube.daq.payload.PayloadChecker;
import icecube.daq.trigger.ITriggerRequestPayload;

class TriggerValidator
    extends BaseValidator
{
    public void validate(IWriteablePayload payload)
    {
        if (!(payload instanceof ITriggerRequestPayload)) {
            throw new Error("Unexpected payload " +
                            payload.getClass().getName());
        }

        ITriggerRequestPayload tr = (ITriggerRequestPayload) payload;

        if (!PayloadChecker.validateTriggerRequest(tr, true)) {
            throw new Error("Trigger request is not valid");
        }

        //System.err.println("GOT " + tr);
    }
}

