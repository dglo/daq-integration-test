package icecube.daq.test;

import icecube.daq.payload.IUTCTime;
import icecube.daq.payload.IWriteablePayload;
import icecube.daq.payload.MasterPayloadFactory;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public abstract class BaseValidator
    implements PayloadValidator
{
    private static final Log LOG = LogFactory.getLog(BaseValidator.class);

    private MasterPayloadFactory factory;
    private boolean foundInvalid;

    public boolean foundInvalid()
    {
        return foundInvalid;
    }

    static long getUTC(IUTCTime time)
    {
        if (time == null) {
            return -1L;
        }

        return time.longValue();
    }

    static void dumpPayloadBytes(IWriteablePayload payload)
    {
        ByteBuffer buf = ByteBuffer.allocate(payload.getPayloadLength());

        int len;
        try {
            len = payload.writePayload(false, 0, buf);
        } catch (java.io.IOException ioe) {
            ioe.printStackTrace();
            len = -1;
        }

        StringBuffer strbuf = new StringBuffer();
        for (int i = 0; i < buf.limit(); i++) {
            String str = Integer.toHexString(buf.get(i));
            if (str.length() < 2) {
                strbuf.append('0').append(str);
            } else if (str.length() > 2) {
                strbuf.append(str.substring(str.length() - 2));
            } else {
                strbuf.append(str);
            }
            strbuf.append(' ');
        }

        System.err.println("LEN "+len+" HEX "+strbuf.toString());
    }

    public boolean validate(ByteBuffer payBuf)
    {
        // assume stop messages are valid
        if (payBuf.limit() >= 4 && payBuf.getInt(0) == 4) {
            return true;
        }

        if (factory == null) {
            factory = new MasterPayloadFactory();
        }

        IWriteablePayload payload;
        try {
            payload = factory.createPayload(0, payBuf, false);
        } catch (Exception ex) {
            LOG.error("Couldn't validate byte buffer", ex);
            foundInvalid = false;
            return false;
        }

        boolean isValid = validate(payload);

        foundInvalid |= !isValid;

        return isValid;
    }
}
