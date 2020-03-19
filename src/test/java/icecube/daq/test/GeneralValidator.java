package icecube.daq.test;

import icecube.daq.payload.IPayload;
import icecube.daq.payload.PayloadChecker;

import org.apache.log4j.Logger;

class GeneralValidator
    extends BaseValidator
{
    private static final Logger LOG = Logger.getLogger(GeneralValidator.class);

    @Override
    public boolean validate(IPayload payload)
    {
        if (!PayloadChecker.validatePayload(payload, true)) {
            LOG.error("Payload is not valid");
            return false;
        }

        return true;
    }
}
