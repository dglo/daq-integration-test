package icecube.daq.test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectableChannel;

public abstract class PayloadConsumer
    implements Runnable
{
    private String inputName;
    private ReadableByteChannel chanIn;
    private Thread thread;
    private int numWritten;
    private PayloadValidator validator;


    public PayloadConsumer(String inputName, ReadableByteChannel chanIn)
    {
        this.inputName = inputName;
        this.chanIn = chanIn;

        if (chanIn instanceof SelectableChannel) {
            SelectableChannel selChan = (SelectableChannel) chanIn;
            if (!selChan.isBlocking()) {
                throw new Error("Got non-blocking channel");
            }
        }
    }

    abstract ByteBuffer buildStopMessage(ByteBuffer stopBuf);

    abstract void finishThreadCleanup();

    public int getNumberWritten()
    {
        return numWritten;
    }

    public boolean isRunning()
    {
        return thread != null;
    }

    abstract boolean isStopMessage(ByteBuffer buf);

    public void run()
    {
        ByteBuffer lenBuf = ByteBuffer.allocate(4);

        boolean sendStop = true;
        while (true) {
            lenBuf.rewind();
            int numBytes;
            try {
                numBytes = chanIn.read(lenBuf);
            } catch (IOException ioe) {
                throw new Error("Couldn't read length from " + inputName, ioe);
            }

            if (numBytes < 4) {
                break;
            }

            final int len = lenBuf.getInt(0);
            if (len < 4) {
                throw new Error("Bad length " + len);
            }

            ByteBuffer buf = ByteBuffer.allocate(len);
            buf.putInt(len);

            while (buf.position() != len) {
                int lenIn;
                try {
                    lenIn = chanIn.read(buf);
                } catch (IOException ioe) {
                    throw new Error("Couldn't read data from " + inputName,
                                    ioe);
                }
            }

            buf.flip();

            if (validator != null) {
                validator.validate(buf);
                if (buf.position() != 0) {
                    throw new Error("Validator " + validator +
                                    " changed buffer position");
                }
            }

            try {
                write(buf);
            } catch (IOException ioe) {
                throw new Error("Couldn't write " + len + " bytes from " +
                                inputName, ioe);
            }

            // don't overwhelm other threads
            Thread.yield();

            if (isStopMessage(buf)) {
                sendStop = false;
                break;
            }

            numWritten++;
        }

        try {
            chanIn.close();
        } catch (IOException ioe) {
            // ignore errors on close
        }

        if (sendStop) {
            ByteBuffer buf = buildStopMessage(null);
            if (buf != null) {
                try {
                    write(buf);
                } catch (IOException ioe) {
                    throw new Error("Couldn't write " + inputName +
                                    " stop message", ioe);
                }
            }
        }

        finishThreadCleanup();

        thread = null;
    }

    void setValidator(PayloadValidator validator)
    {
        this.validator = validator;
    }

    public void start()
    {
        numWritten = 0;

        thread = new Thread(this);
        thread.setName(inputName);
        thread.start();
    }

    abstract void write(ByteBuffer buf)
        throws IOException;

    public String toString()
    {
        return inputName + "#" + numWritten + (isRunning() ? "" : "(stopped)");
    }
}
