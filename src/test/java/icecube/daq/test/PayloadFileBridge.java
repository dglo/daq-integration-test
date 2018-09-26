package icecube.daq.test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.WritableByteChannel;

public class PayloadFileBridge
    extends FileBridge
{
    private static final int STOP_MESSAGE_LENGTH = 4;

    private WritableByteChannel chanOut;

    private int writeMax;
    private int writeDelay;
    private int writeCount;

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

    @Override
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

    @Override
    void finishThreadCleanup()
    {
        try {
            chanOut.close();
        } catch (IOException ioe) {
            // ignore errors on close
        }

        chanOut = null;
    }

    @Override
    boolean isStopMessage(ByteBuffer buf)
    {
        return buf.limit() == STOP_MESSAGE_LENGTH &&
            buf.getInt(0) == STOP_MESSAGE_LENGTH;
    }

    /**
     * Sleep for a bit after writing a set of payloads
     *
     * @param count number of payloads to write
     * @param msecSleep milliseconds to sleep after <tt>count</tt> payloads
     */
    public void setWriteDelay(int count, int msecSleep)
    {
        writeMax = count;
        writeDelay = msecSleep;
    }

    @Override
    void write(ByteBuffer buf)
        throws IOException
    {
        if (writeMax > 0 && writeCount++ > writeMax) {
            writeCount = 0;
            try {
                Thread.sleep(writeDelay);
            } catch (Exception ex) {
                // do nothing
            }
        }

        int lenOut = chanOut.write(buf);
        if (lenOut != buf.limit()) {
            throw new Error("Expected to write " + buf.limit() +
                            " bytes, not " + lenOut);
        }
    }
}
