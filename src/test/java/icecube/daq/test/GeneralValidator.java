package icecube.daq.test;

import icecube.daq.payload.IWriteablePayload;
import icecube.daq.payload.PayloadChecker;

class GeneralValidator
    extends BaseValidator
{
    public void validate(IWriteablePayload payload)
    {
        if (!PayloadChecker.validatePayload(payload, true)) {
            throw new Error("Payload is not valid");
        }
    }
}

