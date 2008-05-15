package icecube.daq.test;

import icecube.daq.io.DAQComponentOutputProcess;
import icecube.daq.sender.Sender;
import icecube.daq.stringhub.StringHubComponent;

import java.io.IOException;
import java.io.File;
import java.nio.ByteBuffer;

public class DomHitFileBridge
    extends FileBridge
{
    private static final int STOP_MESSAGE_LENGTH = 32;

    private int hubNum;
    private Sender sender;
    private DAQComponentOutputProcess hitOut;

    public DomHitFileBridge(File dataFile, StringHubComponent shComp)
        throws IOException
    {
        super(dataFile);

        hubNum = shComp.getHubId() % 1000;
        sender = shComp.getSender();
        hitOut = shComp.getHitWriter();
    }

    /**
     * Build DOM stop message.
     *
     * @param stopBuff byte buffer to use (in not <tt>null</tt>)
     *
     * @return stop message
     */
    ByteBuffer buildStopMessage(ByteBuffer stopBuf)
    {
        if (stopBuf == null || stopBuf.capacity() < STOP_MESSAGE_LENGTH) {
            stopBuf = ByteBuffer.allocate(STOP_MESSAGE_LENGTH);
        }
        stopBuf.limit(STOP_MESSAGE_LENGTH);

        stopBuf.putInt(0, STOP_MESSAGE_LENGTH);
        stopBuf.putLong(24, Long.MAX_VALUE);

        stopBuf.position(0);

        return stopBuf;
    }

    boolean isStopMessage(ByteBuffer buf)
    {
        return buf.limit() == STOP_MESSAGE_LENGTH &&
            buf.getInt(0) == STOP_MESSAGE_LENGTH &&
            buf.getLong(24) == Long.MAX_VALUE;
    }

    void finishThreadCleanup()
    {
    }

    int getHubNumber()
    {
        return hubNum;
    }

    long getNumSent()
    {
        long[] recSent = hitOut.getRecordsSent();

        long total = 0L;
        if (recSent != null) {
            for (int i = 0; i < recSent.length; i++) {
                total += recSent[i];
            }
        }

        return (int) total;
    }

    void write(ByteBuffer buf)
    {
        // don't overwhelm other threads
        Thread.yield();

        sender.consume(buf);
    }
}
