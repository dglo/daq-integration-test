package icecube.daq.test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

public class PayloadSink
    extends PayloadConsumer
{
    PayloadSink(String name, ReadableByteChannel chanIn)
    {
        super(name, chanIn);
    }

    ByteBuffer buildStopMessage(ByteBuffer stopBuf)
    {
        return null;
    }

    void finishThreadCleanup()
    {
        // do nothing
    }

    void write(ByteBuffer buf)
        throws IOException
    {
        // do nothing
    }
}
