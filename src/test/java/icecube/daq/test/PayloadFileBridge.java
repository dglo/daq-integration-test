package icecube.daq.test;

import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.WritableByteChannel;

public class PayloadFileBridge
    extends FileBridge
{
    private static final int STOP_MESSAGE_LENGTH = 4;

    private WritableByteChannel chanOut;

    public PayloadFileBridge(File dataFile, WritableByteChannel chanOut)
        throws IOException
    {
        super(dataFile);

        this.chanOut = chanOut;

        if (chanOut instanceof SelectableChannel &&
            !((SelectableChannel) chanOut).isBlocking())
        {
            throw new Error("Output channel should be blocking");
        }
    }

    public PayloadFileBridge(String inputName, ReadableByteChannel chanIn,
                             WritableByteChannel chanOut)
    {
        super(inputName, chanIn);

        this.chanOut = chanOut;
    }

    ByteBuffer buildStopMessage(ByteBuffer stopBuf)
    {
        if (stopBuf == null || stopBuf.capacity() < STOP_MESSAGE_LENGTH) {
            stopBuf = ByteBuffer.allocate(STOP_MESSAGE_LENGTH);
        }
        stopBuf.limit(STOP_MESSAGE_LENGTH);

        stopBuf.putInt(0, STOP_MESSAGE_LENGTH);

        stopBuf.position(0);

        return stopBuf;
    }

    void finishThreadCleanup()
    {
        try {
            chanOut.close();
        } catch (IOException ioe) {
            // ignore errors on close
        }

        chanOut = null;
    }

    boolean isStopMessage(ByteBuffer buf)
    {
        return buf.limit() == STOP_MESSAGE_LENGTH &&
            buf.getInt(0) == STOP_MESSAGE_LENGTH;
    }

    void write(ByteBuffer buf)
        throws IOException
    {
        int lenOut = chanOut.write(buf);
        if (lenOut != buf.limit()) {
            throw new Error("Expected to write " + buf.limit() +
                            " bytes, not " + lenOut);
        }
    }
}
