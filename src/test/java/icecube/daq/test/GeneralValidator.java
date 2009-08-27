package icecube.daq.test;

import icecube.daq.payload.IWriteablePayload;
import icecube.daq.payload.PayloadChecker;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

class GeneralValidator
    extends BaseValidator
{
    private static final Log LOG = LogFactory.getLog(GeneralValidator.class);

    public boolean validate(IWriteablePayload payload)
    {
        if (!PayloadChecker.validatePayload(payload, true)) {
            LOG.error("Payload is not valid");
            return false;
        }

        return true;
    }
}

