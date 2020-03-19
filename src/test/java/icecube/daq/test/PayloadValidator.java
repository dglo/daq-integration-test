package icecube.daq.test;

import icecube.daq.payload.IPayload;

import java.nio.ByteBuffer;

public interface PayloadValidator
{
    boolean foundInvalid();
    boolean validate(ByteBuffer payBuf);
    boolean validate(IPayload payload);
}
