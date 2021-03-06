package icecube.daq.test;

import icecube.daq.io.PayloadFileReader;
import icecube.daq.juggler.component.DAQCompException;
import icecube.daq.payload.IByteBufferCache;
import icecube.daq.payload.ISourceID;
import icecube.daq.payload.PayloadRegistry;
import icecube.daq.payload.SourceIdRegistry;
import icecube.daq.payload.impl.VitreousBufferCache;
import icecube.daq.splicer.SplicerException;
import icecube.daq.stringhub.StringHubComponent;
import icecube.daq.trigger.exceptions.TriggerException;
import icecube.daq.util.IDOMRegistry;

import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

import org.apache.log4j.BasicConfigurator;

public class DAQInTheFileTest
    extends DAQTestCase
{
    private static final int BASE_REALHUB_ID =
        SourceIdRegistry.STRING_HUB_SOURCE_ID;

    private File amTrigFile;
    private HashMap<ISourceID, File> shMap;
    private DomHitFileBridge[] shInput;
    private FileBridge amInput;

    public DAQInTheFileTest(String name)
    {
        super(name);
    }

    @Override
    StringHubComponent[] buildStringHubComponents(String configFile)
        throws DAQCompException, IOException
    {
        StringHubComponent[] shComps = new StringHubComponent[shMap.size()];
        shInput = new DomHitFileBridge[shComps.length];

        int num = 0;
        for (ISourceID srcId : shMap.keySet()) {
            shComps[num] = new StringHubComponent(srcId.getSourceID());
            shComps[num].setGlobalConfigurationDir(configFile);
            shComps[num].initialize();
            shComps[num].forceRandomMode();
            shComps[num].start(false);
            shInput[num] = new DomHitFileBridge(shMap.get(srcId), shComps[num]);
            num++;
        }

        return shComps;
    }

    @Override
    int getNumberOfExpectedEvents()
    {
        return 509;
    }

    @Override
    void initialize(IDOMRegistry domRegistry)
    {
        final String rawDataName = "raw_data";
        URL url = getClass().getResource("/" + rawDataName);
        if (url == null) {
            throw new Error("Cannot find raw data directory \"" +
                            rawDataName + "\"");
        }

        File dataDir = new File(url.getPath());
        if (!dataDir.isDirectory()) {
            throw new Error("Raw data object " + dataDir +
                            " is not a directory");
        }

        shMap = new HashMap<ISourceID, File>();

        for (File f : dataDir.listFiles()) {
            if (f.getName().startsWith("HIT-stringHub#")) {
                int num;
                try {
                    num = Integer.parseInt(f.getName().substring(14));
                } catch (NumberFormatException nfe) {
                    System.err.println("Bad hit file name " + f);
                    continue;
                }

                ISourceID srcId = new MockSourceID(BASE_REALHUB_ID + num);
                shMap.put(srcId, f);
            } else {
                System.err.println("Unknown data file " + f);
            }
        }
    }

    private void monitorInputs(int maxTries)
    {
        int prevAM = 0;
        int[] prevSH = new int[shInput.length];
        for (int i = 0; i < prevSH.length; i++) {
            prevSH[i] = 0;
        }

        int numTries = 0;

        StringBuilder rptBuf = new StringBuilder();

        boolean isRunning = true;
        while (isRunning && numTries < maxTries) {
            if (amInput != null) {
                isRunning = amInput.isRunning();
            } else {
                isRunning = false;
            }
            for (int i = 0; !isRunning && i < shInput.length; i++) {
                isRunning |= shInput[i].isRunning();
            }

            if (isRunning) {
                boolean stagnant = true;

                rptBuf.setLength(0);

                if (amInput != null && amInput.isRunning()) {
                    int numWr = amInput.getNumberWritten();

                    char sepCh;
                    if (numWr > prevAM) {
                        prevAM = numWr;
                        stagnant = false;
                        sepCh = ':';
                    } else {
                        sepCh = '!';
                    }

                    if (rptBuf.length() > 0) rptBuf.append(' ');
                    rptBuf.append("AM").append(sepCh).append(numWr);
                }

                for (int i = 0; i < shInput.length; i++) {
                    if (shInput[i].isRunning()) {
                        int hubNum = shInput[i].getHubNumber();
                        if (rptBuf.length() > 0) rptBuf.append(' ');
                        rptBuf.append("SH").append(hubNum);

                        int numWr = shInput[i].getNumberWritten();

                        char sepCh;
                        if (numWr > prevSH[i]) {
                            prevSH[i] = numWr;
                            stagnant = false;
                            sepCh = ':';
                        } else {
                            sepCh = '!';
                        }

                        rptBuf.append(sepCh).append(numWr);
                        rptBuf.append('<').append(shInput[i].getNumSent());
                        rptBuf.append('>');
                    }
                }

                if (!stagnant) {
                    numTries = 0;
                } else {
                    numTries++;
                }
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException ie) {
                // ignore interrupts
            }
        }

        if (isRunning) {
            throw new Error("Input bridges have not stopped after " + maxTries +
                            " tries");
        }
    }

    @Override
    void sendData(StringHubComponent[] shComps)
    {
        for (int i = 0; i < shInput.length; i++) {
            shInput[i].start();
        }
        if (amInput != null) {
            amInput.start();
        }

        monitorInputs(10);
    }

    public static Test suite()
    {
        return new TestSuite(DAQInTheFileTest.class);
    }

    public static void main(String[] args)
    {
        TestRunner.run(suite());
    }
}
