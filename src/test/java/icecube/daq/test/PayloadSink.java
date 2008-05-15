package icecube.daq.test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

public class PayloadSink
    extends PayloadConsumer
{
    private int STOP_MESSAGE_LENGTH = 4;

    PayloadSink(String name, ReadableByteChannel chanIn)
    {
        super(name, chanIn);
    }

    ByteBuffer buildStopMessage(ByteBuffer stopBuf)
    {
        return null;
    }

    boolean isStopMessage(ByteBuffer buf)
    {
        return buf.limit() == STOP_MESSAGE_LENGTH &&
            buf.getInt(0) == STOP_MESSAGE_LENGTH;
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
