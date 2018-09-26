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

    @Override
    ByteBuffer buildStopMessage(ByteBuffer stopBuf)
    {
        return null;
    }

    @Override
    boolean isStopMessage(ByteBuffer buf)
    {
        return buf.limit() == STOP_MESSAGE_LENGTH &&
            buf.getInt(0) == STOP_MESSAGE_LENGTH;
    }

    @Override
    void finishThreadCleanup()
    {
        // do nothing
    }

    @Override
    void write(ByteBuffer buf)
        throws IOException
    {
        // do nothing
    }
}
