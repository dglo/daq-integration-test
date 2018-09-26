package icecube.daq.test;

import icecube.daq.io.DispatchException;
import icecube.daq.io.Dispatcher;
import icecube.daq.io.StreamMetaData;
import icecube.daq.payload.IByteBufferCache;
import icecube.daq.payload.IEventPayload;
import icecube.daq.payload.IWriteablePayload;
import icecube.daq.payload.PayloadChecker;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

public class MockDispatcher
    implements Dispatcher
{
    private IByteBufferCache bufMgr;
    private int numSeen;
    private int numBad;
    private int readOnlyTrigger;
    private boolean readOnly;
    private boolean started;

    public MockDispatcher()
    {
        this(null);
    }

    public MockDispatcher(IByteBufferCache bufMgr)
    {
        this.bufMgr = bufMgr;
    }

    @Override
    public void close()
        throws DispatchException
    {
        // do nothing
    }

    @Override
    public void dataBoundary()
        throws DispatchException
    {
        throw new Error("Unimplemented");
    }

    @Override
    public void dataBoundary(String msg)
        throws DispatchException
    {
        if (msg.startsWith(START_PREFIX)) {
            if (started) {
                throw new Error("Dispatcher has already been started");
            }

            started = true;
        } else if (msg.startsWith(STOP_PREFIX)) {
            if (!started) {
                throw new Error("Dispatcher is already stopped");
            }

            started = false;
        }
    }

    public void dispatchEvent(ByteBuffer buf, long ticks)
        throws DispatchException
    {
        throw new Error("Unimplemented");
    }

    @Override
    public void dispatchEvent(IWriteablePayload pay)
        throws DispatchException
    {
        numSeen++;

        if (readOnlyTrigger > 0 && numSeen >= readOnlyTrigger) {
            readOnly = true;
        }

        if (!PayloadChecker.validateEvent((IEventPayload) pay, true)) {
            numBad++;
        }

        if (readOnly) {
            IOException ioe = new IOException("Read-only file system");
            throw new DispatchException("Could not dispatch event", ioe);
        }

        ByteBuffer buf;
        if (bufMgr == null) {
            buf = ByteBuffer.allocate(pay.length());
        } else {
            buf = bufMgr.acquireBuffer(pay.length());
        }

        try {
            pay.writePayload(false, 0, buf);
        } catch (java.io.IOException ioe) {
            System.err.println("Couldn't write payload " + pay);
            ioe.printStackTrace();
            buf = null;
        }

        if (bufMgr != null && buf != null) {
            bufMgr.returnBuffer(buf);
        }
    }

    /**
     * Get the byte buffer cache being used.
     *
     * @return byte buffer cache
     */
    @Override
    public IByteBufferCache getByteBufferCache()
    {
        return bufMgr;
    }

    @Override
    public long getDiskAvailable()
    {
        return 0;
    }

    @Override
    public long getDiskSize()
    {
        return 0;
    }

    @Override
    public File getDispatchDestStorage()
    {
        throw new Error("Unimplemented");
    }

    @Override
    public long getFirstDispatchedTime()
    {
        return Long.MIN_VALUE;
    }

    @Override
    public StreamMetaData getMetaData()
    {
        return new StreamMetaData(0L, 0L);
    }

    @Override
    public long getNumBytesWritten()
    {
        return 0;
    }

    @Override
    public long getNumDispatchedEvents()
    {
        return numSeen;
    }

    public int getNumberOfBadEvents()
    {
        return numBad;
    }

    @Override
    public int getRunNumber()
    {
        throw new Error("Unimplemented");
    }

    @Override
    public long getTotalDispatchedEvents()
    {
        return numSeen;
    }

    @Override
    public boolean isStarted()
    {
        return started;
    }

    @Override
    public void setDispatchDestStorage(String destDir)
    {
        // do nothing
    }

    @Override
    public void setMaxFileSize(long x0)
    {
        throw new Error("Unimplemented");
    }

    public void setReadOnly(boolean readOnly)
    {
        this.readOnly = readOnly;
    }

    /**
     * Trigger a read-only filesystem event after <tt>eventCount</tt> events.
     *
     * @param eventCount number of events needed to trigger a read-only
     *                   filesystem
     */
    public void setReadOnlyTrigger(int eventCount)
    {
        this.readOnlyTrigger = eventCount;
    }

    @Override
    public String toString()
    {
        if (numBad == 0) {
            return "Dispatcher saw " + numSeen + " payloads";
        }

        return "Dispatcher saw " + numBad + " bad payloads (of " + numSeen +
            ")";
    }
}
