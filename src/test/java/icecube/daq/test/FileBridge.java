package icecube.daq.test;

import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.nio.channels.ReadableByteChannel;

public abstract class FileBridge
    extends PayloadConsumer
{
    public FileBridge(File dataFile)
        throws IOException
    {
        super(dataFile.toString(), new FileInputStream(dataFile).getChannel());
    }

    FileBridge(String inputName, ReadableByteChannel chanIn)
    {
        super(inputName, chanIn);
    }
}
