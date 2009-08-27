package icecube.daq.test;

import icecube.daq.payload.IWriteablePayload;

import java.nio.ByteBuffer;

public interface PayloadValidator
{
    boolean foundInvalid();
    boolean validate(ByteBuffer payBuf);
    boolean validate(IWriteablePayload payload);
}
