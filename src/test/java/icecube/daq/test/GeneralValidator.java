package icecube.daq.test;

import icecube.daq.payload.IWriteablePayload;
import icecube.daq.payload.PayloadChecker;

import org.apache.log4j.Logger;

class GeneralValidator
    extends BaseValidator
{
    private static final Logger LOG = Logger.getLogger(GeneralValidator.class);

    @Override
    public boolean validate(IWriteablePayload payload)
    {
        if (!PayloadChecker.validatePayload(payload, true)) {
            LOG.error("Payload is not valid");
            return false;
        }

        return true;
    }
}
