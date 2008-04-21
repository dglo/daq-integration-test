package icecube.daq.test;

import icecube.daq.common.DAQCmdInterface;
import icecube.daq.io.DAQComponentIOProcess;
import icecube.daq.io.DAQComponentObserver;
import icecube.daq.io.ErrorState;
import icecube.daq.io.NormalState;
import icecube.daq.io.PayloadReader;
import icecube.daq.io.SpliceablePayloadReader;
import icecube.daq.juggler.component.DAQCompException;
import icecube.daq.payload.IUTCTime;
import icecube.daq.payload.IWriteablePayload;
import icecube.daq.payload.PayloadChecker;
import icecube.daq.payload.PayloadRegistry;
import icecube.daq.payload.RecordTypeRegistry;
import icecube.daq.payload.SourceIdRegistry;
import icecube.daq.payload.VitreousBufferCache;
import icecube.daq.splicer.HKN1Splicer;
import icecube.daq.splicer.Splicer;
import icecube.daq.splicer.SplicerException;
import icecube.daq.trigger.ITriggerRequestPayload;
import icecube.daq.trigger.component.GlobalTriggerComponent;
import icecube.daq.trigger.control.GlobalTriggerManager;
import icecube.daq.trigger.exceptions.TriggerException;
import icecube.daq.trigger.impl.TriggerRequestPayloadFactory;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.zip.DataFormatException;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.BasicConfigurator;

public class GlobalTriggerEndToEndTest
    extends TestCase
{
    class ElemData
    {
        int type;
        long firstTime;
        long lastTime;
        long dom;
        int srcId;

        ElemData(int type, long firstTime, long lastTime, long dom, int srcId)
        {
            this.type = type;
            this.firstTime = firstTime;
            this.lastTime = lastTime;
            this.dom = dom;
            this.srcId = srcId;
        }
    }

    private static final MockAppender appender =
        new MockAppender(/*org.apache.log4j.Level.ALL*/)/*.setVerbose(true)*/;

    private static ByteBuffer hitBuf;

    public GlobalTriggerEndToEndTest(String name)
    {
        super(name);
    }

    private static ByteBuffer buildTrigger(int uid, long firstTime,
                                           long lastTime, int trigType,
                                           int cfgId, int srcId, int rrUID,
                                           int rrSrcId, ElemData elem)
        throws DataFormatException, IOException
    {
        final int bufLen = 104;

        ByteBuffer trigBuf = ByteBuffer.allocate(bufLen);

        final int recType =
            RecordTypeRegistry.RECORD_TYPE_TRIGGER_REQUEST;

        trigBuf.putInt(0, bufLen);
        trigBuf.putInt(4, PayloadRegistry.PAYLOAD_ID_TRIGGER_REQUEST);
        trigBuf.putLong(8, firstTime);

        trigBuf.putShort(16, (short) recType);
        trigBuf.putInt(18, uid);
        trigBuf.putInt(22, trigType);
        trigBuf.putInt(26, cfgId);
        trigBuf.putInt(30, srcId);
        trigBuf.putLong(34, firstTime);
        trigBuf.putLong(42, lastTime);

        trigBuf.putShort(50, (short) 0xff);
        trigBuf.putInt(52, rrUID);
        trigBuf.putInt(56, rrSrcId);
        trigBuf.putInt(60, 1);

        if (elem != null) {
            trigBuf.putInt(64, elem.type);
            trigBuf.putInt(68, elem.srcId);
            trigBuf.putLong(72, elem.firstTime);
            trigBuf.putLong(80, elem.lastTime);
            trigBuf.putLong(88, elem.dom);
        }

        trigBuf.putInt(96, 8);
        trigBuf.putShort(100, (short) 1);
        trigBuf.putShort(102, (short) 0);

        trigBuf.position(0);

        return trigBuf;
    }

    private void checkLogMessages()
    {
        for (int i = 0; i < appender.getNumberOfMessages(); i++) {
            String msg = (String) appender.getMessage(i);

            if (!(msg.startsWith("Clearing ") &&
                  msg.endsWith(" rope entries")) &&
                !msg.startsWith("Resetting counter ") &&
                !msg.startsWith("No match for timegate "))
            {
                fail("Bad log message#" + i + ": " + appender.getMessage(i));
            }
        }
        appender.clear();
    }

    private ArrayList<ByteBuffer> getTriggerList()
        throws DataFormatException, IOException
    {
        ArrayList<ByteBuffer> list =
            new ArrayList<ByteBuffer>();

        ElemData elem;

        elem = new ElemData(0, 24014178844806200L, 24014178845306200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(0, 24014178845056200L, 24014178845056200L, 8,
                              104, 10000, 0, 10000, elem));

        elem = new ElemData(0, 24014178864019600L, 24014178864519600L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(1, 24014178864269600L, 24014178864269600L, 8,
                              104, 10000, 1, 10000, elem));

        elem = null;
        list.add(buildTrigger(1, 24014178945613900L, 24014178945613900L, -1,
                              -1, 10000, 1, 10000, elem));

        elem = new ElemData(0, 24014179030143700L, 24014179030643700L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(2, 24014179030393700L, 24014179030393700L, 8,
                              104, 10000, 2, 10000, elem));

        elem = new ElemData(0, 24014179180050900L, 24014179180550900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(1, 24014179180300900L, 24014179180300900L, 9,
                              104, 10000, 1, 10000, elem));

        elem = null;
        list.add(buildTrigger(3, 24014179226750200L, 24014179226750200L, -1,
                              -1, 10000, 3, 10000, elem));

        elem = new ElemData(0, 24014179229404800L, 24014179229904800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(3, 24014179229654800L, 24014179229654800L, 9,
                              104, 10000, 3, 10000, elem));

        elem = new ElemData(0, 24014179286665500L, 24014179287165500L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(3, 24014179286915500L, 24014179286915500L, 8,
                              104, 10000, 3, 10000, elem));

        elem = null;
        list.add(buildTrigger(5, 24014179287380100L, 24014179287380100L, -1,
                              -1, 10000, 5, 10000, elem));

        elem = null;
        list.add(buildTrigger(6, 24014179296524400L, 24014179296524400L, -1,
                              -1, 10000, 6, 10000, elem));

        elem = null;
        list.add(buildTrigger(7, 24014179306071600L, 24014179306071600L, -1,
                              -1, 10000, 7, 10000, elem));

        elem = new ElemData(0, 24014179349845500L, 24014179350345500L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(7, 24014179350095500L, 24014179350095500L, 9,
                              104, 10000, 7, 10000, elem));

        elem = new ElemData(0, 24014179377689200L, 24014179378189200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(4, 24014179377939200L, 24014179377939200L, 8,
                              104, 10000, 4, 10000, elem));

        elem = null;
        list.add(buildTrigger(9, 24014179487071000L, 24014179487071000L, -1,
                              -1, 10000, 9, 10000, elem));

        elem = new ElemData(0, 24014179491987100L, 24014179492487100L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(5, 24014179492237100L, 24014179492237100L, 8,
                              104, 10000, 5, 10000, elem));

        elem = null;
        list.add(buildTrigger(11, 24014179658034600L, 24014179658034600L, -1,
                              -1, 10000, 11, 10000, elem));

        elem = new ElemData(0, 24014179658300400L, 24014179658800400L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(4, 24014179658550400L, 24014179658550400L, 11,
                              104, 10000, 4, 10000, elem));

        elem = new ElemData(0, 24014179664908100L, 24014179665408100L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(6, 24014179665158100L, 24014179665158100L, 8,
                              104, 10000, 6, 10000, elem));

        elem = new ElemData(0, 24014179716744100L, 24014179717244100L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(7, 24014179716994100L, 24014179716994100L, 8,
                              104, 10000, 7, 10000, elem));

        elem = new ElemData(0, 24014179767977200L, 24014179768477200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(8, 24014179768227200L, 24014179768227200L, 8,
                              104, 10000, 8, 10000, elem));

        elem = new ElemData(0, 24014179804717000L, 24014179805217000L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(9, 24014179804967000L, 24014179804967000L, 8,
                              104, 10000, 9, 10000, elem));

        elem = null;
        list.add(buildTrigger(13, 24014179817276700L, 24014179817276700L, -1,
                              -1, 10000, 13, 10000, elem));

        elem = new ElemData(0, 24014179947975000L, 24014179948475000L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(10, 24014179948225000L, 24014179948225000L, 8,
                              104, 10000, 10, 10000, elem));

        elem = new ElemData(0, 24014179999750700L, 24014180000250700L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(0, 24014180000000700L, 24014180000000700L, 12,
                              104, 10000, 0, 10000, elem));

        elem = new ElemData(0, 24014180058044000L, 24014180058544000L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(11, 24014180058294000L, 24014180058294000L, 8,
                              104, 10000, 11, 10000, elem));

        elem = null;
        list.add(buildTrigger(14, 24014180076701000L, 24014180076701000L, -1,
                              -1, 10000, 14, 10000, elem));

        elem = null;
        list.add(buildTrigger(16, 24014180090670700L, 24014180090670700L, -1,
                              -1, 10000, 16, 10000, elem));

        elem = new ElemData(0, 24014180303294700L, 24014180303794700L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(13, 24014180303544700L, 24014180303544700L, 9,
                              104, 10000, 13, 10000, elem));

        elem = new ElemData(0, 24014180380089300L, 24014180380589300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(8, 24014180380339300L, 24014180380339300L, 11,
                              104, 10000, 8, 10000, elem));

        elem = null;
        list.add(buildTrigger(18, 24014180389416900L, 24014180389416900L, -1,
                              -1, 10000, 18, 10000, elem));

        elem = new ElemData(0, 24014180454105400L, 24014180454605400L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(15, 24014180454355400L, 24014180454355400L, 9,
                              104, 10000, 15, 10000, elem));

        elem = null;
        list.add(buildTrigger(19, 24014180462933300L, 24014180462933300L, -1,
                              -1, 10000, 19, 10000, elem));

        elem = null;
        list.add(buildTrigger(20, 24014180523165100L, 24014180523165100L, -1,
                              -1, 10000, 20, 10000, elem));

        elem = new ElemData(0, 24014180578113200L, 24014180578613200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(12, 24014180578363200L, 24014180578363200L, 8,
                              104, 10000, 12, 10000, elem));

        elem = null;
        list.add(buildTrigger(22, 24014180587423600L, 24014180587423600L, -1,
                              -1, 10000, 22, 10000, elem));

        elem = null;
        list.add(buildTrigger(23, 24014180632729000L, 24014180632729000L, -1,
                              -1, 10000, 23, 10000, elem));

        elem = new ElemData(0, 24014180684538800L, 24014180685038800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(13, 24014180684788800L, 24014180684788800L, 8,
                              104, 10000, 13, 10000, elem));

        elem = null;
        list.add(buildTrigger(24, 24014180730273300L, 24014180730273300L, -1,
                              -1, 10000, 24, 10000, elem));

        elem = null;
        list.add(buildTrigger(25, 24014180775799600L, 24014180775799600L, -1,
                              -1, 10000, 25, 10000, elem));

        elem = new ElemData(0, 24014180807358000L, 24014180807858000L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(22, 24014180807608000L, 24014180807608000L, 9,
                              104, 10000, 22, 10000, elem));

        elem = new ElemData(0, 24014180829496400L, 24014180829996400L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(14, 24014180829746400L, 24014180829746400L, 8,
                              104, 10000, 14, 10000, elem));

        elem = new ElemData(0, 24014180855770900L, 24014180856270900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(15, 24014180856020900L, 24014180856020900L, 8,
                              104, 10000, 15, 10000, elem));

        elem = new ElemData(0, 24014180858004800L, 24014180858504800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(23, 24014180858254800L, 24014180858254800L, 9,
                              104, 10000, 23, 10000, elem));

        elem = null;
        list.add(buildTrigger(27, 24014180877030700L, 24014180877030700L, -1,
                              -1, 10000, 27, 10000, elem));

        elem = null;
        list.add(buildTrigger(29, 24014180886663400L, 24014180886663400L, -1,
                              -1, 10000, 29, 10000, elem));

        elem = new ElemData(0, 24014180887738900L, 24014180888238900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(26, 24014180887988900L, 24014180887988900L, 9,
                              104, 10000, 26, 10000, elem));

        elem = new ElemData(0, 24014180919946500L, 24014180920446500L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(16, 24014180920196500L, 24014180920196500L, 11,
                              104, 10000, 16, 10000, elem));

        elem = null;
        list.add(buildTrigger(30, 24014181061460800L, 24014181061460800L, -1,
                              -1, 10000, 30, 10000, elem));

        elem = new ElemData(0, 24014181105714400L, 24014181106214400L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(28, 24014181105964400L, 24014181105964400L, 9,
                              104, 10000, 28, 10000, elem));

        elem = new ElemData(0, 24014181151921000L, 24014181152421000L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(29, 24014181152171000L, 24014181152171000L, 9,
                              104, 10000, 29, 10000, elem));

        elem = null;
        list.add(buildTrigger(32, 24014181155746600L, 24014181155746600L, -1,
                              -1, 10000, 32, 10000, elem));

        elem = new ElemData(0, 24014181179608700L, 24014181180108700L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(31, 24014181179858700L, 24014181179858700L, 9,
                              104, 10000, 31, 10000, elem));

        elem = null;
        list.add(buildTrigger(33, 24014181210738100L, 24014181210738100L, -1,
                              -1, 10000, 33, 10000, elem));

        elem = new ElemData(0, 24014181294474200L, 24014181294974200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(33, 24014181294724200L, 24014181294724200L, 9,
                              104, 10000, 33, 10000, elem));

        elem = null;
        list.add(buildTrigger(35, 24014181363709800L, 24014181363709800L, -1,
                              -1, 10000, 35, 10000, elem));

        elem = new ElemData(0, 24014181523134600L, 24014181523634600L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(35, 24014181523384600L, 24014181523384600L, 9,
                              104, 10000, 35, 10000, elem));

        elem = new ElemData(0, 24014181539254900L, 24014181539754900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(36, 24014181539504900L, 24014181539504900L, 9,
                              104, 10000, 36, 10000, elem));

        elem = new ElemData(0, 24014181550555700L, 24014181551055700L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(37, 24014181550805700L, 24014181550805700L, 9,
                              104, 10000, 37, 10000, elem));

        elem = new ElemData(0, 24014181553951600L, 24014181554451600L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(38, 24014181554201600L, 24014181554201600L, 9,
                              104, 10000, 38, 10000, elem));

        elem = new ElemData(0, 24014181563319200L, 24014181563819200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(19, 24014181563569200L, 24014181563569200L, 11,
                              104, 10000, 19, 10000, elem));

        elem = new ElemData(0, 24014181595056800L, 24014181595556800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(16, 24014181595306800L, 24014181595306800L, 8,
                              104, 10000, 16, 10000, elem));

        elem = new ElemData(0, 24014181618183600L, 24014181618683600L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(17, 24014181618433600L, 24014181618433600L, 8,
                              104, 10000, 17, 10000, elem));

        elem = new ElemData(0, 24014181651719800L, 24014181652219800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(18, 24014181651969800L, 24014181651969800L, 8,
                              104, 10000, 18, 10000, elem));

        elem = new ElemData(0, 24014181683335200L, 24014181683835200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(39, 24014181683585200L, 24014181683585200L, 9,
                              104, 10000, 39, 10000, elem));

        elem = null;
        list.add(buildTrigger(36, 24014181725281800L, 24014181725281800L, -1,
                              -1, 10000, 36, 10000, elem));

        elem = null;
        list.add(buildTrigger(38, 24014181726612700L, 24014181726612700L, -1,
                              -1, 10000, 38, 10000, elem));

        elem = new ElemData(0, 24014181792775200L, 24014181793275200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(19, 24014181793025200L, 24014181793025200L, 8,
                              104, 10000, 19, 10000, elem));

        elem = new ElemData(0, 24014181823937900L, 24014181824437900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(20, 24014181824187900L, 24014181824187900L, 8,
                              104, 10000, 20, 10000, elem));

        elem = null;
        list.add(buildTrigger(40, 24014181858432400L, 24014181858432400L, -1,
                              -1, 10000, 40, 10000, elem));

        elem = new ElemData(0, 24014181873743400L, 24014181874243400L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(43, 24014181873993400L, 24014181873993400L, 9,
                              104, 10000, 43, 10000, elem));

        elem = null;
        list.add(buildTrigger(41, 24014181874279000L, 24014181874279000L, -1,
                              -1, 10000, 41, 10000, elem));

        elem = null;
        list.add(buildTrigger(43, 24014181884807600L, 24014181884807600L, -1,
                              -1, 10000, 43, 10000, elem));

        elem = null;
        list.add(buildTrigger(45, 24014181932939900L, 24014181932939900L, -1,
                              -1, 10000, 45, 10000, elem));

        elem = null;
        list.add(buildTrigger(47, 24014181970699500L, 24014181970699500L, -1,
                              -1, 10000, 47, 10000, elem));

        elem = new ElemData(0, 24014181977543800L, 24014181978043800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(48, 24014181977793800L, 24014181977793800L, 9,
                              104, 10000, 48, 10000, elem));

        elem = new ElemData(0, 24014182039258600L, 24014182039758600L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(49, 24014182039508600L, 24014182039508600L, 9,
                              104, 10000, 49, 10000, elem));

        elem = new ElemData(0, 24014182045290500L, 24014182045790500L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(21, 24014182045540500L, 24014182045540500L, 8,
                              104, 10000, 21, 10000, elem));

        elem = new ElemData(0, 24014182068189500L, 24014182068689500L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(26, 24014182068439500L, 24014182068439500L, 11,
                              104, 10000, 26, 10000, elem));

        elem = new ElemData(0, 24014182070231800L, 24014182070731800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(50, 24014182070481800L, 24014182070481800L, 9,
                              104, 10000, 50, 10000, elem));

        elem = new ElemData(0, 24014182278080600L, 24014182278580600L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(27, 24014182278330600L, 24014182278330600L, 11,
                              104, 10000, 27, 10000, elem));

        elem = null;
        list.add(buildTrigger(49, 24014182303744100L, 24014182303744100L, -1,
                              -1, 10000, 49, 10000, elem));

        elem = new ElemData(0, 24014182311736100L, 24014182312236100L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(52, 24014182311986100L, 24014182311986100L, 9,
                              104, 10000, 52, 10000, elem));

        elem = new ElemData(0, 24014182319815200L, 24014182320315200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(22, 24014182320065200L, 24014182320065200L, 8,
                              104, 10000, 22, 10000, elem));

        elem = new ElemData(0, 24014182381153400L, 24014182381653400L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(23, 24014182381403400L, 24014182381403400L, 8,
                              104, 10000, 23, 10000, elem));

        elem = null;
        list.add(buildTrigger(50, 24014182432570100L, 24014182432570100L, -1,
                              -1, 10000, 50, 10000, elem));

        elem = new ElemData(0, 24014182494629300L, 24014182495129300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(24, 24014182494879300L, 24014182494879300L, 8,
                              104, 10000, 24, 10000, elem));

        elem = null;
        list.add(buildTrigger(52, 24014182588665600L, 24014182588665600L, -1,
                              -1, 10000, 52, 10000, elem));

        elem = null;
        list.add(buildTrigger(54, 24014182643395000L, 24014182643395000L, -1,
                              -1, 10000, 54, 10000, elem));

        elem = new ElemData(0, 24014182776412300L, 24014182776912300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(56, 24014182776662300L, 24014182776662300L, 9,
                              104, 10000, 56, 10000, elem));

        elem = null;
        list.add(buildTrigger(56, 24014182786322200L, 24014182786322200L, -1,
                              -1, 10000, 56, 10000, elem));

        elem = new ElemData(0, 24014182802367900L, 24014182802867900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(25, 24014182802617900L, 24014182802617900L, 8,
                              104, 10000, 25, 10000, elem));

        elem = new ElemData(0, 24014182824832900L, 24014182825332900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(58, 24014182825082900L, 24014182825082900L, 9,
                              104, 10000, 58, 10000, elem));

        elem = null;
        list.add(buildTrigger(58, 24014182870969500L, 24014182870969500L, -1,
                              -1, 10000, 58, 10000, elem));

        elem = new ElemData(0, 24014182977527800L, 24014182978027800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(26, 24014182977777800L, 24014182977777800L, 8,
                              104, 10000, 26, 10000, elem));

        elem = null;
        list.add(buildTrigger(60, 24014183075094100L, 24014183075094100L, -1,
                              -1, 10000, 60, 10000, elem));

        elem = null;
        list.add(buildTrigger(62, 24014183152179800L, 24014183152179800L, -1,
                              -1, 10000, 62, 10000, elem));

        elem = new ElemData(0, 24014183270974200L, 24014183271474200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(27, 24014183271224200L, 24014183271224200L, 8,
                              104, 10000, 27, 10000, elem));

        elem = new ElemData(0, 24014183276230200L, 24014183276730200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(28, 24014183276480200L, 24014183276480200L, 8,
                              104, 10000, 28, 10000, elem));

        elem = null;
        list.add(buildTrigger(63, 24014183303247500L, 24014183303247500L, -1,
                              -1, 10000, 63, 10000, elem));

        elem = null;
        list.add(buildTrigger(64, 24014183320105700L, 24014183320105700L, -1,
                              -1, 10000, 64, 10000, elem));

        elem = null;
        list.add(buildTrigger(66, 24014183410993800L, 24014183410993800L, -1,
                              -1, 10000, 66, 10000, elem));

        elem = new ElemData(0, 24014183618754900L, 24014183619254900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(29, 24014183619004900L, 24014183619004900L, 8,
                              104, 10000, 29, 10000, elem));

        elem = null;
        list.add(buildTrigger(68, 24014183621452200L, 24014183621452200L, -1,
                              -1, 10000, 68, 10000, elem));

        elem = new ElemData(0, 24014183623942100L, 24014183624442100L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(30, 24014183624192100L, 24014183624192100L, 8,
                              104, 10000, 30, 10000, elem));

        elem = new ElemData(0, 24014183624941300L, 24014183625441300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(0, 24014183625191300L, 24014183625191300L, 7,
                              104, 10000, 0, 10000, elem));

        elem = null;
        list.add(buildTrigger(70, 24014183646077700L, 24014183646077700L, -1,
                              -1, 10000, 70, 10000, elem));

        elem = null;
        list.add(buildTrigger(72, 24014183736641000L, 24014183736641000L, -1,
                              -1, 10000, 72, 10000, elem));

        elem = new ElemData(0, 24014183771784500L, 24014183772284500L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(31, 24014183772034500L, 24014183772034500L, 8,
                              104, 10000, 31, 10000, elem));

        elem = new ElemData(0, 24014183834826100L, 24014183835326100L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(68, 24014183835076100L, 24014183835076100L, 9,
                              104, 10000, 68, 10000, elem));

        elem = new ElemData(0, 24014183886706300L, 24014183887206300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(32, 24014183886956300L, 24014183886956300L, 8,
                              104, 10000, 32, 10000, elem));

        elem = new ElemData(0, 24014183941528700L, 24014183942028700L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(69, 24014183941778700L, 24014183941778700L, 9,
                              104, 10000, 69, 10000, elem));

        elem = new ElemData(0, 24014183943243400L, 24014183943743400L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(33, 24014183943493400L, 24014183943493400L, 8,
                              104, 10000, 33, 10000, elem));

        elem = new ElemData(0, 24014183978541300L, 24014183979041300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(34, 24014183978791300L, 24014183978791300L, 8,
                              104, 10000, 34, 10000, elem));

        elem = null;
        list.add(buildTrigger(73, 24014184012946700L, 24014184012946700L, -1,
                              -1, 10000, 73, 10000, elem));

        elem = null;
        list.add(buildTrigger(75, 24014184127326600L, 24014184127326600L, -1,
                              -1, 10000, 75, 10000, elem));

        elem = new ElemData(0, 24014184332557400L, 24014184333057400L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(72, 24014184332807400L, 24014184332807400L, 9,
                              104, 10000, 72, 10000, elem));

        elem = null;
        list.add(buildTrigger(77, 24014184373225000L, 24014184373225000L, -1,
                              -1, 10000, 77, 10000, elem));

        elem = null;
        list.add(buildTrigger(78, 24014184417795900L, 24014184417795900L, -1,
                              -1, 10000, 78, 10000, elem));

        elem = null;
        list.add(buildTrigger(79, 24014184452370800L, 24014184452370800L, -1,
                              -1, 10000, 79, 10000, elem));

        elem = null;
        list.add(buildTrigger(81, 24014184492043000L, 24014184492043000L, -1,
                              -1, 10000, 81, 10000, elem));

        elem = null;
        list.add(buildTrigger(82, 24014184501267600L, 24014184501267600L, -1,
                              -1, 10000, 82, 10000, elem));

        elem = null;
        list.add(buildTrigger(84, 24014184537349300L, 24014184537349300L, -1,
                              -1, 10000, 84, 10000, elem));

        elem = new ElemData(0, 24014184539349700L, 24014184539849700L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(79, 24014184539599700L, 24014184539599700L, 9,
                              104, 10000, 79, 10000, elem));

        elem = null;
        list.add(buildTrigger(85, 24014184558760600L, 24014184558760600L, -1,
                              -1, 10000, 85, 10000, elem));

        elem = null;
        list.add(buildTrigger(86, 24014184606728800L, 24014184606728800L, -1,
                              -1, 10000, 86, 10000, elem));

        elem = null;
        list.add(buildTrigger(88, 24014184634578600L, 24014184634578600L, -1,
                              -1, 10000, 88, 10000, elem));

        elem = null;
        list.add(buildTrigger(89, 24014184685593400L, 24014184685593400L, -1,
                              -1, 10000, 89, 10000, elem));

        elem = null;
        list.add(buildTrigger(90, 24014184703634700L, 24014184703634700L, -1,
                              -1, 10000, 90, 10000, elem));

        elem = null;
        list.add(buildTrigger(91, 24014184764145900L, 24014184764145900L, -1,
                              -1, 10000, 91, 10000, elem));

        elem = new ElemData(0, 24014184808423400L, 24014184808923400L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(35, 24014184808673400L, 24014184808673400L, 8,
                              104, 10000, 35, 10000, elem));

        elem = null;
        list.add(buildTrigger(92, 24014184820042300L, 24014184820042300L, -1,
                              -1, 10000, 92, 10000, elem));

        elem = new ElemData(0, 24014184854161300L, 24014184854661300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(87, 24014184854411300L, 24014184854411300L, 9,
                              104, 10000, 87, 10000, elem));

        elem = new ElemData(0, 24014184874077400L, 24014184874577400L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(45, 24014184874327400L, 24014184874327400L, 11,
                              104, 10000, 45, 10000, elem));

        elem = null;
        list.add(buildTrigger(93, 24014184883638500L, 24014184883638500L, -1,
                              -1, 10000, 93, 10000, elem));

        elem = null;
        list.add(buildTrigger(94, 24014184898984200L, 24014184898984200L, -1,
                              -1, 10000, 94, 10000, elem));

        elem = null;
        list.add(buildTrigger(95, 24014184929064100L, 24014184929064100L, -1,
                              -1, 10000, 95, 10000, elem));

        elem = null;
        list.add(buildTrigger(97, 24014184986350400L, 24014184986350400L, -1,
                              -1, 10000, 97, 10000, elem));

        elem = new ElemData(0, 24014185019956800L, 24014185020456800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(92, 24014185020206800L, 24014185020206800L, 9,
                              104, 10000, 92, 10000, elem));

        elem = new ElemData(0, 24014185155481800L, 24014185155981800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(36, 24014185155731800L, 24014185155731800L, 8,
                              104, 10000, 36, 10000, elem));

        elem = new ElemData(0, 24014185201174600L, 24014185201674600L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(93, 24014185201424600L, 24014185201424600L, 9,
                              104, 10000, 93, 10000, elem));

        elem = new ElemData(0, 24014185208617400L, 24014185209117400L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(37, 24014185208867400L, 24014185208867400L, 8,
                              104, 10000, 37, 10000, elem));

        elem = new ElemData(0, 24014185282045100L, 24014185282545100L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(38, 24014185282295100L, 24014185282295100L, 8,
                              104, 10000, 38, 10000, elem));

        elem = new ElemData(0, 24014185297669600L, 24014185298169600L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(94, 24014185297919600L, 24014185297919600L, 9,
                              104, 10000, 94, 10000, elem));

        elem = new ElemData(0, 24014185309671400L, 24014185310171400L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(39, 24014185309921400L, 24014185309921400L, 8,
                              104, 10000, 39, 10000, elem));

        elem = new ElemData(0, 24014185396555500L, 24014185397055500L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(40, 24014185396805500L, 24014185396805500L, 8,
                              104, 10000, 40, 10000, elem));

        elem = null;
        list.add(buildTrigger(99, 24014185412186100L, 24014185412186100L, -1,
                              -1, 10000, 99, 10000, elem));

        elem = new ElemData(0, 24014185423694700L, 24014185424194700L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(49, 24014185423944700L, 24014185423944700L, 11,
                              104, 10000, 49, 10000, elem));

        elem = null;
        list.add(buildTrigger(101, 24014185442379700L, 24014185442379700L, -1,
                              -1, 10000, 101, 10000, elem));

        elem = null;
        list.add(buildTrigger(103, 24014185520389500L, 24014185520389500L, -1,
                              -1, 10000, 103, 10000, elem));

        elem = null;
        list.add(buildTrigger(105, 24014185698259000L, 24014185698259000L, -1,
                              -1, 10000, 105, 10000, elem));

        elem = null;
        list.add(buildTrigger(106, 24014185699318600L, 24014185699318600L, -1,
                              -1, 10000, 106, 10000, elem));

        elem = new ElemData(0, 24014185803927700L, 24014185804427700L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(41, 24014185804177700L, 24014185804177700L, 8,
                              104, 10000, 41, 10000, elem));

        elem = null;
        list.add(buildTrigger(108, 24014185832288700L, 24014185832288700L, -1,
                              -1, 10000, 108, 10000, elem));

        elem = null;
        list.add(buildTrigger(110, 24014185875252500L, 24014185875252500L, -1,
                              -1, 10000, 110, 10000, elem));

        elem = null;
        list.add(buildTrigger(112, 24014185882054000L, 24014185882054000L, -1,
                              -1, 10000, 112, 10000, elem));

        elem = new ElemData(0, 24014185889876900L, 24014185890376900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(103, 24014185890126900L, 24014185890126900L, 9,
                              104, 10000, 103, 10000, elem));

        elem = null;
        list.add(buildTrigger(113, 24014185948913500L, 24014185948913500L, -1,
                              -1, 10000, 113, 10000, elem));

        elem = new ElemData(0, 24014185961209300L, 24014185961709300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(57, 24014185961459300L, 24014185961459300L, 11,
                              104, 10000, 57, 10000, elem));

        elem = new ElemData(0, 24014185981490200L, 24014185981990200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(105, 24014185981740200L, 24014185981740200L, 9,
                              104, 10000, 105, 10000, elem));

        elem = null;
        list.add(buildTrigger(114, 24014185983321200L, 24014185983321200L, -1,
                              -1, 10000, 114, 10000, elem));

        elem = new ElemData(0, 24014186071107400L, 24014186071607400L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(42, 24014186071357400L, 24014186071357400L, 8,
                              104, 10000, 42, 10000, elem));

        elem = new ElemData(0, 24014186084291800L, 24014186084791800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(107, 24014186084541800L, 24014186084541800L, 9,
                              104, 10000, 107, 10000, elem));

        elem = new ElemData(0, 24014186125477300L, 24014186125977300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(108, 24014186125727300L, 24014186125727300L, 9,
                              104, 10000, 108, 10000, elem));

        elem = null;
        list.add(buildTrigger(115, 24014186143867900L, 24014186143867900L, -1,
                              -1, 10000, 115, 10000, elem));

        elem = new ElemData(0, 24014186151038700L, 24014186151538700L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(43, 24014186151288700L, 24014186151288700L, 8,
                              104, 10000, 43, 10000, elem));

        elem = new ElemData(0, 24014186347654000L, 24014186348154000L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(58, 24014186347904000L, 24014186347904000L, 11,
                              104, 10000, 58, 10000, elem));

        elem = new ElemData(0, 24014186366637300L, 24014186367137300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(44, 24014186366887300L, 24014186366887300L, 8,
                              104, 10000, 44, 10000, elem));

        elem = null;
        list.add(buildTrigger(117, 24014186397401000L, 24014186397401000L, -1,
                              -1, 10000, 117, 10000, elem));

        elem = new ElemData(0, 24014186410835900L, 24014186411335900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(45, 24014186411085900L, 24014186411085900L, 8,
                              104, 10000, 45, 10000, elem));

        elem = null;
        list.add(buildTrigger(119, 24014186486601200L, 24014186486601200L, -1,
                              -1, 10000, 119, 10000, elem));

        elem = null;
        list.add(buildTrigger(120, 24014186490068200L, 24014186490068200L, -1,
                              -1, 10000, 120, 10000, elem));

        elem = null;
        list.add(buildTrigger(122, 24014186511436900L, 24014186511436900L, -1,
                              -1, 10000, 122, 10000, elem));

        elem = null;
        list.add(buildTrigger(124, 24014186573595300L, 24014186573595300L, -1,
                              -1, 10000, 124, 10000, elem));

        elem = new ElemData(0, 24014186664691100L, 24014186665191100L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(46, 24014186664941100L, 24014186664941100L, 8,
                              104, 10000, 46, 10000, elem));

        elem = new ElemData(0, 24014186732924800L, 24014186733424800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(64, 24014186733174800L, 24014186733174800L, 11,
                              104, 10000, 64, 10000, elem));

        elem = null;
        list.add(buildTrigger(126, 24014186829383000L, 24014186829383000L, -1,
                              -1, 10000, 126, 10000, elem));

        elem = null;
        list.add(buildTrigger(128, 24014186835070500L, 24014186835070500L, -1,
                              -1, 10000, 128, 10000, elem));

        elem = new ElemData(0, 24014186925868800L, 24014186926368800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(47, 24014186926118800L, 24014186926118800L, 8,
                              104, 10000, 47, 10000, elem));

        elem = new ElemData(0, 24014186932514600L, 24014186933014600L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(48, 24014186932764600L, 24014186932764600L, 8,
                              104, 10000, 48, 10000, elem));

        elem = new ElemData(0, 24014186955646800L, 24014186956146800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(117, 24014186955896800L, 24014186955896800L, 9,
                              104, 10000, 117, 10000, elem));

        elem = null;
        list.add(buildTrigger(129, 24014186969327400L, 24014186969327400L, -1,
                              -1, 10000, 129, 10000, elem));

        elem = new ElemData(0, 24014187003818300L, 24014187004318300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(49, 24014187004068300L, 24014187004068300L, 8,
                              104, 10000, 49, 10000, elem));

        elem = null;
        list.add(buildTrigger(131, 24014187073711900L, 24014187073711900L, -1,
                              -1, 10000, 131, 10000, elem));

        elem = new ElemData(0, 24014187098921800L, 24014187099421800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(120, 24014187099171800L, 24014187099171800L, 9,
                              104, 10000, 120, 10000, elem));

        elem = new ElemData(0, 24014187104760800L, 24014187105260800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(50, 24014187105010800L, 24014187105010800L, 8,
                              104, 10000, 50, 10000, elem));

        elem = new ElemData(0, 24014187135404500L, 24014187135904500L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(69, 24014187135654500L, 24014187135654500L, 11,
                              104, 10000, 69, 10000, elem));

        elem = new ElemData(0, 24014187162877100L, 24014187163377100L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(121, 24014187163127100L, 24014187163127100L, 9,
                              104, 10000, 121, 10000, elem));

        elem = null;
        list.add(buildTrigger(132, 24014187239002100L, 24014187239002100L, -1,
                              -1, 10000, 132, 10000, elem));

        elem = new ElemData(0, 24014187240242200L, 24014187240742200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(123, 24014187240492200L, 24014187240492200L, 9,
                              104, 10000, 123, 10000, elem));

        elem = new ElemData(0, 24014187346362400L, 24014187346862400L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(51, 24014187346612400L, 24014187346612400L, 8,
                              104, 10000, 51, 10000, elem));

        elem = new ElemData(0, 24014187370577300L, 24014187371077300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(124, 24014187370827300L, 24014187370827300L, 9,
                              104, 10000, 124, 10000, elem));

        elem = new ElemData(0, 24014187397245600L, 24014187397745600L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(125, 24014187397495600L, 24014187397495600L, 9,
                              104, 10000, 125, 10000, elem));

        elem = new ElemData(0, 24014187406162300L, 24014187406662300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(70, 24014187406412300L, 24014187406412300L, 11,
                              104, 10000, 70, 10000, elem));

        elem = new ElemData(0, 24014187406416700L, 24014187406916700L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(52, 24014187406666700L, 24014187406666700L, 8,
                              104, 10000, 52, 10000, elem));

        elem = null;
        list.add(buildTrigger(133, 24014187407463600L, 24014187407463600L, -1,
                              -1, 10000, 133, 10000, elem));

        elem = null;
        list.add(buildTrigger(135, 24014187427343700L, 24014187427343700L, -1,
                              -1, 10000, 135, 10000, elem));

        elem = new ElemData(0, 24014187434907400L, 24014187435407400L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(72, 24014187435157400L, 24014187435157400L, 11,
                              104, 10000, 72, 10000, elem));

        elem = new ElemData(0, 24014187728879100L, 24014187729379100L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(128, 24014187729129100L, 24014187729129100L, 9,
                              104, 10000, 128, 10000, elem));

        elem = new ElemData(0, 24014187741327500L, 24014187741827500L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(129, 24014187741577500L, 24014187741577500L, 9,
                              104, 10000, 129, 10000, elem));

        elem = null;
        list.add(buildTrigger(137, 24014187770365700L, 24014187770365700L, -1,
                              -1, 10000, 137, 10000, elem));

        elem = null;
        list.add(buildTrigger(138, 24014187790382800L, 24014187790382800L, -1,
                              -1, 10000, 138, 10000, elem));

        elem = new ElemData(0, 24014187806417000L, 24014187806917000L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(132, 24014187806667000L, 24014187806667000L, 9,
                              104, 10000, 132, 10000, elem));

        elem = null;
        list.add(buildTrigger(139, 24014187853490200L, 24014187853490200L, -1,
                              -1, 10000, 139, 10000, elem));

        elem = new ElemData(0, 24014188005450000L, 24014188005950000L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(53, 24014188005700000L, 24014188005700000L, 8,
                              104, 10000, 53, 10000, elem));

        elem = new ElemData(0, 24014188027843800L, 24014188028343800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(54, 24014188028093800L, 24014188028093800L, 8,
                              104, 10000, 54, 10000, elem));

        elem = null;
        list.add(buildTrigger(140, 24014188130675800L, 24014188130675800L, -1,
                              -1, 10000, 140, 10000, elem));

        elem = null;
        list.add(buildTrigger(142, 24014188156044900L, 24014188156044900L, -1,
                              -1, 10000, 142, 10000, elem));

        elem = new ElemData(0, 24014188157241600L, 24014188157741600L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(55, 24014188157491600L, 24014188157491600L, 8,
                              104, 10000, 55, 10000, elem));

        elem = null;
        list.add(buildTrigger(143, 24014188199903900L, 24014188199903900L, -1,
                              -1, 10000, 143, 10000, elem));

        elem = new ElemData(0, 24014188263530000L, 24014188264030000L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(137, 24014188263780000L, 24014188263780000L, 9,
                              104, 10000, 137, 10000, elem));

        elem = new ElemData(0, 24014188283896100L, 24014188284396100L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(138, 24014188284146100L, 24014188284146100L, 9,
                              104, 10000, 138, 10000, elem));

        elem = new ElemData(0, 24014188303876300L, 24014188304376300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(56, 24014188304126300L, 24014188304126300L, 8,
                              104, 10000, 56, 10000, elem));

        elem = null;
        list.add(buildTrigger(145, 24014188319549800L, 24014188319549800L, -1,
                              -1, 10000, 145, 10000, elem));

        elem = new ElemData(0, 24014188375076900L, 24014188375576900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(57, 24014188375326900L, 24014188375326900L, 8,
                              104, 10000, 57, 10000, elem));

        elem = new ElemData(0, 24014188529325300L, 24014188529825300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(58, 24014188529575300L, 24014188529575300L, 8,
                              104, 10000, 58, 10000, elem));

        elem = new ElemData(0, 24014188568596400L, 24014188569096400L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(59, 24014188568846400L, 24014188568846400L, 8,
                              104, 10000, 59, 10000, elem));

        elem = null;
        list.add(buildTrigger(146, 24014188581402100L, 24014188581402100L, -1,
                              -1, 10000, 146, 10000, elem));

        elem = new ElemData(0, 24014188588917800L, 24014188589417800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(77, 24014188589167800L, 24014188589167800L, 11,
                              104, 10000, 77, 10000, elem));

        elem = null;
        list.add(buildTrigger(148, 24014188606648900L, 24014188606648900L, -1,
                              -1, 10000, 148, 10000, elem));

        elem = new ElemData(0, 24014188665604000L, 24014188666104000L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(142, 24014188665854000L, 24014188665854000L, 9,
                              104, 10000, 142, 10000, elem));

        elem = null;
        list.add(buildTrigger(149, 24014188811071400L, 24014188811071400L, -1,
                              -1, 10000, 149, 10000, elem));

        elem = null;
        list.add(buildTrigger(150, 24014188843133600L, 24014188843133600L, -1,
                              -1, 10000, 150, 10000, elem));

        elem = new ElemData(0, 24014188846384600L, 24014188846884600L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(60, 24014188846634600L, 24014188846634600L, 8,
                              104, 10000, 60, 10000, elem));

        elem = new ElemData(0, 24014188936050900L, 24014188936550900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(145, 24014188936300900L, 24014188936300900L, 9,
                              104, 10000, 145, 10000, elem));

        elem = null;
        list.add(buildTrigger(152, 24014188958301600L, 24014188958301600L, -1,
                              -1, 10000, 152, 10000, elem));

        elem = null;
        list.add(buildTrigger(154, 24014189018823300L, 24014189018823300L, -1,
                              -1, 10000, 154, 10000, elem));

        elem = new ElemData(0, 24014189066289500L, 24014189066789500L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(61, 24014189066539500L, 24014189066539500L, 8,
                              104, 10000, 61, 10000, elem));

        elem = null;
        list.add(buildTrigger(155, 24014189104756900L, 24014189104756900L, -1,
                              -1, 10000, 155, 10000, elem));

        elem = null;
        list.add(buildTrigger(157, 24014189111490300L, 24014189111490300L, -1,
                              -1, 10000, 157, 10000, elem));

        elem = new ElemData(0, 24014189177381800L, 24014189177881800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(150, 24014189177631800L, 24014189177631800L, 9,
                              104, 10000, 150, 10000, elem));

        elem = new ElemData(0, 24014189216626300L, 24014189217126300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(62, 24014189216876300L, 24014189216876300L, 8,
                              104, 10000, 62, 10000, elem));

        elem = null;
        list.add(buildTrigger(158, 24014189248056700L, 24014189248056700L, -1,
                              -1, 10000, 158, 10000, elem));

        elem = null;
        list.add(buildTrigger(160, 24014189325617600L, 24014189325617600L, -1,
                              -1, 10000, 160, 10000, elem));

        elem = new ElemData(0, 24014189342575000L, 24014189343075000L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(63, 24014189342825000L, 24014189342825000L, 8,
                              104, 10000, 63, 10000, elem));

        elem = null;
        list.add(buildTrigger(161, 24014189386579500L, 24014189386579500L, -1,
                              -1, 10000, 161, 10000, elem));

        elem = null;
        list.add(buildTrigger(163, 24014189388095900L, 24014189388095900L, -1,
                              -1, 10000, 163, 10000, elem));

        elem = new ElemData(0, 24014189392135900L, 24014189392635900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(64, 24014189392385900L, 24014189392385900L, 8,
                              104, 10000, 64, 10000, elem));

        elem = new ElemData(0, 24014189472390700L, 24014189472890700L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(65, 24014189472640700L, 24014189472640700L, 8,
                              104, 10000, 65, 10000, elem));

        elem = new ElemData(0, 24014189559840300L, 24014189560340300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(66, 24014189560090300L, 24014189560090300L, 8,
                              104, 10000, 66, 10000, elem));

        elem = new ElemData(0, 24014189560024600L, 24014189560524600L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(67, 24014189560274600L, 24014189560274600L, 8,
                              104, 10000, 67, 10000, elem));

        elem = new ElemData(0, 24014189578417100L, 24014189578917100L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(155, 24014189578667100L, 24014189578667100L, 9,
                              104, 10000, 155, 10000, elem));

        elem = new ElemData(0, 24014189601580900L, 24014189602080900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(68, 24014189601830900L, 24014189601830900L, 8,
                              104, 10000, 68, 10000, elem));

        elem = null;
        list.add(buildTrigger(164, 24014189626866800L, 24014189626866800L, -1,
                              -1, 10000, 164, 10000, elem));

        elem = new ElemData(0, 24014189660471100L, 24014189660971100L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(69, 24014189660721100L, 24014189660721100L, 8,
                              104, 10000, 69, 10000, elem));

        elem = new ElemData(0, 24014189832778800L, 24014189833278800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(85, 24014189833028800L, 24014189833028800L, 11,
                              104, 10000, 85, 10000, elem));

        elem = new ElemData(0, 24014189897800000L, 24014189898300000L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(70, 24014189898050000L, 24014189898050000L, 8,
                              104, 10000, 70, 10000, elem));

        elem = null;
        list.add(buildTrigger(166, 24014189941484400L, 24014189941484400L, -1,
                              -1, 10000, 166, 10000, elem));

        elem = null;
        list.add(buildTrigger(168, 24014189990097400L, 24014189990097400L, -1,
                              -1, 10000, 168, 10000, elem));

        elem = new ElemData(0, 24014189999296100L, 24014189999796100L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(159, 24014189999546100L, 24014189999546100L, 9,
                              104, 10000, 159, 10000, elem));

        elem = new ElemData(0, 24014189999750700L, 24014190000250700L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(1, 24014190000000700L, 24014190000000700L, 12,
                              104, 10000, 1, 10000, elem));

        elem = null;
        list.add(buildTrigger(169, 24014190117915100L, 24014190117915100L, -1,
                              -1, 10000, 169, 10000, elem));

        elem = new ElemData(0, 24014190151186000L, 24014190151686000L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(71, 24014190151436000L, 24014190151436000L, 8,
                              104, 10000, 71, 10000, elem));

        elem = null;
        list.add(buildTrigger(171, 24014190197474500L, 24014190197474500L, -1,
                              -1, 10000, 171, 10000, elem));

        elem = new ElemData(0, 24014190300793400L, 24014190301293400L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(89, 24014190301043400L, 24014190301043400L, 11,
                              104, 10000, 89, 10000, elem));

        elem = new ElemData(0, 24014190500022200L, 24014190500522200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(162, 24014190500272200L, 24014190500272200L, 9,
                              104, 10000, 162, 10000, elem));

        elem = new ElemData(0, 24014190504317900L, 24014190504817900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(163, 24014190504567900L, 24014190504567900L, 9,
                              104, 10000, 163, 10000, elem));

        elem = new ElemData(0, 24014190539428500L, 24014190539928500L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(72, 24014190539678500L, 24014190539678500L, 8,
                              104, 10000, 72, 10000, elem));

        elem = new ElemData(0, 24014190621041500L, 24014190621541500L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(73, 24014190621291500L, 24014190621291500L, 8,
                              104, 10000, 73, 10000, elem));

        elem = null;
        list.add(buildTrigger(172, 24014190645450600L, 24014190645450600L, -1,
                              -1, 10000, 172, 10000, elem));

        elem = new ElemData(0, 24014190685120000L, 24014190685620000L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(74, 24014190685370000L, 24014190685370000L, 8,
                              104, 10000, 74, 10000, elem));

        elem = null;
        list.add(buildTrigger(174, 24014190713984900L, 24014190713984900L, -1,
                              -1, 10000, 174, 10000, elem));

        elem = null;
        list.add(buildTrigger(175, 24014190760604700L, 24014190760604700L, -1,
                              -1, 10000, 175, 10000, elem));

        elem = null;
        list.add(buildTrigger(176, 24014190888351900L, 24014190888351900L, -1,
                              -1, 10000, 176, 10000, elem));

        elem = null;
        list.add(buildTrigger(178, 24014190950853400L, 24014190950853400L, -1,
                              -1, 10000, 178, 10000, elem));

        elem = null;
        list.add(buildTrigger(179, 24014191050779200L, 24014191050779200L, -1,
                              -1, 10000, 179, 10000, elem));

        elem = new ElemData(0, 24014191246127600L, 24014191246627600L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(170, 24014191246377600L, 24014191246377600L, 9,
                              104, 10000, 170, 10000, elem));

        elem = null;
        list.add(buildTrigger(180, 24014191278131100L, 24014191278131100L, -1,
                              -1, 10000, 180, 10000, elem));

        elem = new ElemData(0, 24014191296545400L, 24014191297045400L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(172, 24014191296795400L, 24014191296795400L, 9,
                              104, 10000, 172, 10000, elem));

        elem = new ElemData(0, 24014191376141500L, 24014191376641500L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(173, 24014191376391500L, 24014191376391500L, 9,
                              104, 10000, 173, 10000, elem));

        elem = null;
        list.add(buildTrigger(182, 24014191394407800L, 24014191394407800L, -1,
                              -1, 10000, 182, 10000, elem));

        elem = new ElemData(0, 24014191397654200L, 24014191398154200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(175, 24014191397904200L, 24014191397904200L, 9,
                              104, 10000, 175, 10000, elem));

        elem = null;
        list.add(buildTrigger(183, 24014191426080600L, 24014191426080600L, -1,
                              -1, 10000, 183, 10000, elem));

        elem = new ElemData(0, 24014191456200000L, 24014191456700000L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(177, 24014191456450000L, 24014191456450000L, 9,
                              104, 10000, 177, 10000, elem));

        elem = new ElemData(0, 24014191525994200L, 24014191526494200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(75, 24014191526244200L, 24014191526244200L, 8,
                              104, 10000, 75, 10000, elem));

        elem = new ElemData(0, 24014191733138000L, 24014191733638000L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(76, 24014191733388000L, 24014191733388000L, 8,
                              104, 10000, 76, 10000, elem));

        elem = null;
        list.add(buildTrigger(184, 24014191776534800L, 24014191776534800L, -1,
                              -1, 10000, 184, 10000, elem));

        elem = new ElemData(0, 24014191830000100L, 24014191830500100L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(179, 24014191830250100L, 24014191830250100L, 9,
                              104, 10000, 179, 10000, elem));

        elem = new ElemData(0, 24014191837781000L, 24014191838281000L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(77, 24014191838031000L, 24014191838031000L, 8,
                              104, 10000, 77, 10000, elem));

        elem = new ElemData(0, 24014191838935900L, 24014191839435900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(78, 24014191839185900L, 24014191839185900L, 8,
                              104, 10000, 78, 10000, elem));

        elem = null;
        list.add(buildTrigger(186, 24014191860890100L, 24014191860890100L, -1,
                              -1, 10000, 186, 10000, elem));

        elem = new ElemData(0, 24014191883381200L, 24014191883881200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(79, 24014191883631200L, 24014191883631200L, 8,
                              104, 10000, 79, 10000, elem));

        elem = new ElemData(0, 24014191943606200L, 24014191944106200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(181, 24014191943856200L, 24014191943856200L, 9,
                              104, 10000, 181, 10000, elem));

        elem = new ElemData(0, 24014192062065200L, 24014192062565200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(80, 24014192062315200L, 24014192062315200L, 8,
                              104, 10000, 80, 10000, elem));

        elem = new ElemData(0, 24014192088055100L, 24014192088555100L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(182, 24014192088305100L, 24014192088305100L, 9,
                              104, 10000, 182, 10000, elem));

        elem = null;
        list.add(buildTrigger(187, 24014192180102400L, 24014192180102400L, -1,
                              -1, 10000, 187, 10000, elem));

        elem = new ElemData(0, 24014192187880100L, 24014192188380100L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(81, 24014192188130100L, 24014192188130100L, 8,
                              104, 10000, 81, 10000, elem));

        elem = new ElemData(0, 24014192240949000L, 24014192241449000L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(82, 24014192241199000L, 24014192241199000L, 8,
                              104, 10000, 82, 10000, elem));

        elem = new ElemData(0, 24014192286254100L, 24014192286754100L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(184, 24014192286504100L, 24014192286504100L, 9,
                              104, 10000, 184, 10000, elem));

        elem = new ElemData(0, 24014192303144400L, 24014192303644400L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(97, 24014192303394400L, 24014192303394400L, 11,
                              104, 10000, 97, 10000, elem));

        elem = new ElemData(0, 24014192533168500L, 24014192533668500L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(185, 24014192533418500L, 24014192533418500L, 9,
                              104, 10000, 185, 10000, elem));

        elem = new ElemData(0, 24014192547699500L, 24014192548199500L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(83, 24014192547949500L, 24014192547949500L, 8,
                              104, 10000, 83, 10000, elem));

        elem = null;
        list.add(buildTrigger(189, 24014192559874500L, 24014192559874500L, -1,
                              -1, 10000, 189, 10000, elem));

        elem = null;
        list.add(buildTrigger(191, 24014192605497500L, 24014192605497500L, -1,
                              -1, 10000, 191, 10000, elem));

        elem = null;
        list.add(buildTrigger(192, 24014192768238300L, 24014192768238300L, -1,
                              -1, 10000, 192, 10000, elem));

        elem = new ElemData(0, 24014192784979700L, 24014192785479700L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(84, 24014192785229700L, 24014192785229700L, 8,
                              104, 10000, 84, 10000, elem));

        elem = new ElemData(0, 24014192833985000L, 24014192834485000L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(85, 24014192834235000L, 24014192834235000L, 8,
                              104, 10000, 85, 10000, elem));

        elem = null;
        list.add(buildTrigger(193, 24014192838864900L, 24014192838864900L, -1,
                              -1, 10000, 193, 10000, elem));

        elem = null;
        list.add(buildTrigger(195, 24014193000005800L, 24014193000005800L, -1,
                              -1, 10000, 195, 10000, elem));

        elem = new ElemData(0, 24014193164109900L, 24014193164609900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(86, 24014193164359900L, 24014193164359900L, 8,
                              104, 10000, 86, 10000, elem));

        elem = null;
        list.add(buildTrigger(196, 24014193176596900L, 24014193176596900L, -1,
                              -1, 10000, 196, 10000, elem));

        elem = new ElemData(0, 24014193218718700L, 24014193219218700L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(87, 24014193218968700L, 24014193218968700L, 8,
                              104, 10000, 87, 10000, elem));

        elem = new ElemData(0, 24014193369459400L, 24014193369959400L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(192, 24014193369709400L, 24014193369709400L, 9,
                              104, 10000, 192, 10000, elem));

        elem = new ElemData(0, 24014193384544200L, 24014193385044200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(1, 24014193384794200L, 24014193384794200L, 7,
                              104, 10000, 1, 10000, elem));

        elem = null;
        list.add(buildTrigger(197, 24014193458425600L, 24014193458425600L, -1,
                              -1, 10000, 197, 10000, elem));

        elem = new ElemData(0, 24014193480398800L, 24014193480898800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(88, 24014193480648800L, 24014193480648800L, 8,
                              104, 10000, 88, 10000, elem));

        elem = new ElemData(0, 24014193535513700L, 24014193536013700L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(103, 24014193535763700L, 24014193535763700L, 11,
                              104, 10000, 103, 10000, elem));

        elem = null;
        list.add(buildTrigger(199, 24014193565961000L, 24014193565961000L, -1,
                              -1, 10000, 199, 10000, elem));

        elem = new ElemData(0, 24014193639331600L, 24014193639831600L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(89, 24014193639581600L, 24014193639581600L, 8,
                              104, 10000, 89, 10000, elem));

        elem = new ElemData(0, 24014193663181900L, 24014193663681900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(90, 24014193663431900L, 24014193663431900L, 8,
                              104, 10000, 90, 10000, elem));

        elem = null;
        list.add(buildTrigger(200, 24014193755974500L, 24014193755974500L, -1,
                              -1, 10000, 200, 10000, elem));

        elem = null;
        list.add(buildTrigger(201, 24014193765193800L, 24014193765193800L, -1,
                              -1, 10000, 201, 10000, elem));

        elem = new ElemData(0, 24014193832235500L, 24014193832735500L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(91, 24014193832485500L, 24014193832485500L, 8,
                              104, 10000, 91, 10000, elem));

        elem = new ElemData(0, 24014193911427900L, 24014193911927900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(92, 24014193911677900L, 24014193911677900L, 8,
                              104, 10000, 92, 10000, elem));

        elem = new ElemData(0, 24014193912220000L, 24014193912720000L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(106, 24014193912470000L, 24014193912470000L, 11,
                              104, 10000, 106, 10000, elem));

        elem = null;
        list.add(buildTrigger(202, 24014193954381000L, 24014193954381000L, -1,
                              -1, 10000, 202, 10000, elem));

        elem = new ElemData(0, 24014193966738200L, 24014193967238200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(198, 24014193966988200L, 24014193966988200L, 9,
                              104, 10000, 198, 10000, elem));

        elem = new ElemData(0, 24014193996504100L, 24014193997004100L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(199, 24014193996754100L, 24014193996754100L, 9,
                              104, 10000, 199, 10000, elem));

        elem = new ElemData(0, 24014194076158800L, 24014194076658800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(200, 24014194076408800L, 24014194076408800L, 9,
                              104, 10000, 200, 10000, elem));

        elem = null;
        list.add(buildTrigger(203, 24014194270501800L, 24014194270501800L, -1,
                              -1, 10000, 203, 10000, elem));

        elem = new ElemData(0, 24014194290918200L, 24014194291418200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(93, 24014194291168200L, 24014194291168200L, 8,
                              104, 10000, 93, 10000, elem));

        elem = new ElemData(0, 24014194433807400L, 24014194434307400L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(202, 24014194434057400L, 24014194434057400L, 9,
                              104, 10000, 202, 10000, elem));

        elem = new ElemData(0, 24014194502634400L, 24014194503134400L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(94, 24014194502884400L, 24014194502884400L, 8,
                              104, 10000, 94, 10000, elem));

        elem = new ElemData(0, 24014194535810900L, 24014194536310900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(203, 24014194536060900L, 24014194536060900L, 9,
                              104, 10000, 203, 10000, elem));

        elem = new ElemData(0, 24014194555772900L, 24014194556272900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(204, 24014194556022900L, 24014194556022900L, 9,
                              104, 10000, 204, 10000, elem));

        elem = null;
        list.add(buildTrigger(205, 24014194585028300L, 24014194585028300L, -1,
                              -1, 10000, 205, 10000, elem));

        elem = new ElemData(0, 24014194595946200L, 24014194596446200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(95, 24014194596196200L, 24014194596196200L, 8,
                              104, 10000, 95, 10000, elem));

        elem = new ElemData(0, 24014194631012600L, 24014194631512600L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(96, 24014194631262600L, 24014194631262600L, 8,
                              104, 10000, 96, 10000, elem));

        elem = new ElemData(0, 24014194668250700L, 24014194668750700L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(206, 24014194668500700L, 24014194668500700L, 9,
                              104, 10000, 206, 10000, elem));

        elem = new ElemData(0, 24014194717295600L, 24014194717795600L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(97, 24014194717545600L, 24014194717545600L, 8,
                              104, 10000, 97, 10000, elem));

        elem = new ElemData(0, 24014194872005600L, 24014194872505600L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(98, 24014194872255600L, 24014194872255600L, 8,
                              104, 10000, 98, 10000, elem));

        elem = new ElemData(0, 24014194904621400L, 24014194905121400L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(99, 24014194904871400L, 24014194904871400L, 8,
                              104, 10000, 99, 10000, elem));

        elem = new ElemData(0, 24014195049866800L, 24014195050366800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(207, 24014195050116800L, 24014195050116800L, 9,
                              104, 10000, 207, 10000, elem));

        elem = new ElemData(0, 24014195070929200L, 24014195071429200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(100, 24014195071179200L, 24014195071179200L, 8,
                              104, 10000, 100, 10000, elem));

        elem = new ElemData(0, 24014195108997300L, 24014195109497300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(101, 24014195109247300L, 24014195109247300L, 8,
                              104, 10000, 101, 10000, elem));

        elem = new ElemData(0, 24014195115096400L, 24014195115596400L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(208, 24014195115346400L, 24014195115346400L, 9,
                              104, 10000, 208, 10000, elem));

        elem = null;
        list.add(buildTrigger(207, 24014195116170300L, 24014195116170300L, -1,
                              -1, 10000, 207, 10000, elem));

        elem = new ElemData(0, 24014195153111500L, 24014195153611500L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(210, 24014195153361500L, 24014195153361500L, 9,
                              104, 10000, 210, 10000, elem));

        elem = null;
        list.add(buildTrigger(208, 24014195208232700L, 24014195208232700L, -1,
                              -1, 10000, 208, 10000, elem));

        elem = null;
        list.add(buildTrigger(210, 24014195256054200L, 24014195256054200L, -1,
                              -1, 10000, 210, 10000, elem));

        elem = new ElemData(0, 24014195281976700L, 24014195282476700L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(112, 24014195282226700L, 24014195282226700L, 11,
                              104, 10000, 112, 10000, elem));

        elem = new ElemData(0, 24014195415222900L, 24014195415722900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(102, 24014195415472900L, 24014195415472900L, 8,
                              104, 10000, 102, 10000, elem));

        elem = new ElemData(0, 24014195466284800L, 24014195466784800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(213, 24014195466534800L, 24014195466534800L, 9,
                              104, 10000, 213, 10000, elem));

        elem = new ElemData(0, 24014195554616200L, 24014195555116200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(214, 24014195554866200L, 24014195554866200L, 9,
                              104, 10000, 214, 10000, elem));

        elem = null;
        list.add(buildTrigger(212, 24014195653366900L, 24014195653366900L, -1,
                              -1, 10000, 212, 10000, elem));

        elem = new ElemData(0, 24014195693661300L, 24014195694161300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(216, 24014195693911300L, 24014195693911300L, 9,
                              104, 10000, 216, 10000, elem));

        elem = new ElemData(0, 24014195752099300L, 24014195752599300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(114, 24014195752349300L, 24014195752349300L, 11,
                              104, 10000, 114, 10000, elem));

        elem = null;
        list.add(buildTrigger(213, 24014195796919300L, 24014195796919300L, -1,
                              -1, 10000, 213, 10000, elem));

        elem = new ElemData(0, 24014196017885000L, 24014196018385000L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(103, 24014196018135000L, 24014196018135000L, 8,
                              104, 10000, 103, 10000, elem));

        elem = null;
        list.add(buildTrigger(215, 24014196025561500L, 24014196025561500L, -1,
                              -1, 10000, 215, 10000, elem));

        elem = new ElemData(0, 24014196083702400L, 24014196084202400L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(104, 24014196083952400L, 24014196083952400L, 8,
                              104, 10000, 104, 10000, elem));

        elem = new ElemData(0, 24014196151015000L, 24014196151515000L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(105, 24014196151265000L, 24014196151265000L, 8,
                              104, 10000, 105, 10000, elem));

        elem = new ElemData(0, 24014196164956700L, 24014196165456700L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(106, 24014196165206700L, 24014196165206700L, 8,
                              104, 10000, 106, 10000, elem));

        elem = new ElemData(0, 24014196170608000L, 24014196171108000L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(107, 24014196170858000L, 24014196170858000L, 8,
                              104, 10000, 107, 10000, elem));

        elem = null;
        list.add(buildTrigger(217, 24014196366214600L, 24014196366214600L, -1,
                              -1, 10000, 217, 10000, elem));

        elem = new ElemData(0, 24014196407388500L, 24014196407888500L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(108, 24014196407638500L, 24014196407638500L, 8,
                              104, 10000, 108, 10000, elem));

        elem = new ElemData(0, 24014196452615600L, 24014196453115600L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(220, 24014196452865600L, 24014196452865600L, 9,
                              104, 10000, 220, 10000, elem));

        elem = new ElemData(0, 24014196462857800L, 24014196463357800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(109, 24014196463107800L, 24014196463107800L, 8,
                              104, 10000, 109, 10000, elem));

        elem = null;
        list.add(buildTrigger(219, 24014196554428600L, 24014196554428600L, -1,
                              -1, 10000, 219, 10000, elem));

        elem = null;
        list.add(buildTrigger(220, 24014196605729500L, 24014196605729500L, -1,
                              -1, 10000, 220, 10000, elem));

        elem = new ElemData(0, 24014196617793900L, 24014196618293900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(223, 24014196618043900L, 24014196618043900L, 9,
                              104, 10000, 223, 10000, elem));

        elem = new ElemData(0, 24014196623535500L, 24014196624035500L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(110, 24014196623785500L, 24014196623785500L, 8,
                              104, 10000, 110, 10000, elem));

        elem = new ElemData(0, 24014196660609100L, 24014196661109100L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(111, 24014196660859100L, 24014196660859100L, 8,
                              104, 10000, 111, 10000, elem));

        elem = null;
        list.add(buildTrigger(222, 24014196679689600L, 24014196679689600L, -1,
                              -1, 10000, 222, 10000, elem));

        elem = new ElemData(0, 24014196806487800L, 24014196806987800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(225, 24014196806737800L, 24014196806737800L, 9,
                              104, 10000, 225, 10000, elem));

        elem = new ElemData(0, 24014196833771900L, 24014196834271900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(119, 24014196834021900L, 24014196834021900L, 11,
                              104, 10000, 119, 10000, elem));

        elem = new ElemData(0, 24014196967901800L, 24014196968401800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(226, 24014196968151800L, 24014196968151800L, 9,
                              104, 10000, 226, 10000, elem));

        elem = new ElemData(0, 24014197210801300L, 24014197211301300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(227, 24014197211051300L, 24014197211051300L, 9,
                              104, 10000, 227, 10000, elem));

        elem = new ElemData(0, 24014197281047400L, 24014197281547400L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(228, 24014197281297400L, 24014197281297400L, 9,
                              104, 10000, 228, 10000, elem));

        elem = new ElemData(0, 24014197353985700L, 24014197354485700L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(112, 24014197354235700L, 24014197354235700L, 8,
                              104, 10000, 112, 10000, elem));

        elem = null;
        list.add(buildTrigger(223, 24014197406947800L, 24014197406947800L, -1,
                              -1, 10000, 223, 10000, elem));

        elem = new ElemData(0, 24014197422269100L, 24014197422769100L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(121, 24014197422519100L, 24014197422519100L, 11,
                              104, 10000, 121, 10000, elem));

        elem = new ElemData(0, 24014197488853700L, 24014197489353700L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(113, 24014197489103700L, 24014197489103700L, 8,
                              104, 10000, 113, 10000, elem));

        elem = new ElemData(0, 24014197526967800L, 24014197527467800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(230, 24014197527217800L, 24014197527217800L, 9,
                              104, 10000, 230, 10000, elem));

        elem = new ElemData(0, 24014197577980700L, 24014197578480700L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(114, 24014197578230700L, 24014197578230700L, 8,
                              104, 10000, 114, 10000, elem));

        elem = new ElemData(0, 24014197589273500L, 24014197589773500L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(115, 24014197589523500L, 24014197589523500L, 8,
                              104, 10000, 115, 10000, elem));

        elem = null;
        list.add(buildTrigger(225, 24014197602321700L, 24014197602321700L, -1,
                              -1, 10000, 225, 10000, elem));

        elem = new ElemData(0, 24014197612483400L, 24014197612983400L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(232, 24014197612733400L, 24014197612733400L, 9,
                              104, 10000, 232, 10000, elem));

        elem = new ElemData(0, 24014197642752300L, 24014197643252300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(116, 24014197643002300L, 24014197643002300L, 8,
                              104, 10000, 116, 10000, elem));

        elem = null;
        list.add(buildTrigger(227, 24014197647819100L, 24014197647819100L, -1,
                              -1, 10000, 227, 10000, elem));

        elem = null;
        list.add(buildTrigger(229, 24014197669515900L, 24014197669515900L, -1,
                              -1, 10000, 229, 10000, elem));

        elem = null;
        list.add(buildTrigger(230, 24014197687362800L, 24014197687362800L, -1,
                              -1, 10000, 230, 10000, elem));

        elem = null;
        list.add(buildTrigger(231, 24014197747667200L, 24014197747667200L, -1,
                              -1, 10000, 231, 10000, elem));

        elem = null;
        list.add(buildTrigger(233, 24014197748102600L, 24014197748102600L, -1,
                              -1, 10000, 233, 10000, elem));

        elem = new ElemData(0, 24014197799728100L, 24014197800228100L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(117, 24014197799978100L, 24014197799978100L, 8,
                              104, 10000, 117, 10000, elem));

        elem = new ElemData(0, 24014197947541900L, 24014197948041900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(238, 24014197947791900L, 24014197947791900L, 9,
                              104, 10000, 238, 10000, elem));

        elem = new ElemData(0, 24014197953766100L, 24014197954266100L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(118, 24014197954016100L, 24014197954016100L, 8,
                              104, 10000, 118, 10000, elem));

        elem = new ElemData(0, 24014197965250000L, 24014197965750000L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(239, 24014197965500000L, 24014197965500000L, 9,
                              104, 10000, 239, 10000, elem));

        elem = new ElemData(0, 24014198110524600L, 24014198111024600L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(240, 24014198110774600L, 24014198110774600L, 9,
                              104, 10000, 240, 10000, elem));

        elem = new ElemData(0, 24014198118384900L, 24014198118884900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(241, 24014198118634900L, 24014198118634900L, 9,
                              104, 10000, 241, 10000, elem));

        elem = null;
        list.add(buildTrigger(234, 24014198137270300L, 24014198137270300L, -1,
                              -1, 10000, 234, 10000, elem));

        elem = null;
        list.add(buildTrigger(235, 24014198191608800L, 24014198191608800L, -1,
                              -1, 10000, 235, 10000, elem));

        elem = new ElemData(0, 24014198232684600L, 24014198233184600L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(119, 24014198232934600L, 24014198232934600L, 8,
                              104, 10000, 119, 10000, elem));

        elem = null;
        list.add(buildTrigger(237, 24014198273053000L, 24014198273053000L, -1,
                              -1, 10000, 237, 10000, elem));

        elem = new ElemData(0, 24014198322398100L, 24014198322898100L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(245, 24014198322648100L, 24014198322648100L, 9,
                              104, 10000, 245, 10000, elem));

        elem = null;
        list.add(buildTrigger(238, 24014198377415600L, 24014198377415600L, -1,
                              -1, 10000, 238, 10000, elem));

        elem = new ElemData(0, 24014198445369500L, 24014198445869500L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(120, 24014198445619500L, 24014198445619500L, 8,
                              104, 10000, 120, 10000, elem));

        elem = null;
        list.add(buildTrigger(239, 24014198530052800L, 24014198530052800L, -1,
                              -1, 10000, 239, 10000, elem));

        elem = null;
        list.add(buildTrigger(240, 24014198597199000L, 24014198597199000L, -1,
                              -1, 10000, 240, 10000, elem));

        elem = new ElemData(0, 24014198628532800L, 24014198629032800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(121, 24014198628782800L, 24014198628782800L, 8,
                              104, 10000, 121, 10000, elem));

        elem = null;
        list.add(buildTrigger(242, 24014198662216900L, 24014198662216900L, -1,
                              -1, 10000, 242, 10000, elem));

        elem = null;
        list.add(buildTrigger(244, 24014198794103400L, 24014198794103400L, -1,
                              -1, 10000, 244, 10000, elem));

        elem = new ElemData(0, 24014198813130800L, 24014198813630800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(251, 24014198813380800L, 24014198813380800L, 9,
                              104, 10000, 251, 10000, elem));

        elem = new ElemData(0, 24014198848703200L, 24014198849203200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(252, 24014198848953200L, 24014198848953200L, 9,
                              104, 10000, 252, 10000, elem));

        elem = new ElemData(0, 24014198894678200L, 24014198895178200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(122, 24014198894928200L, 24014198894928200L, 8,
                              104, 10000, 122, 10000, elem));

        elem = new ElemData(0, 24014198910625500L, 24014198911125500L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(253, 24014198910875500L, 24014198910875500L, 9,
                              104, 10000, 253, 10000, elem));

        elem = new ElemData(0, 24014198928598100L, 24014198929098100L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(254, 24014198928848100L, 24014198928848100L, 9,
                              104, 10000, 254, 10000, elem));

        elem = null;
        list.add(buildTrigger(245, 24014198980420200L, 24014198980420200L, -1,
                              -1, 10000, 245, 10000, elem));

        elem = null;
        list.add(buildTrigger(247, 24014198985792000L, 24014198985792000L, -1,
                              -1, 10000, 247, 10000, elem));

        elem = new ElemData(0, 24014199005516100L, 24014199006016100L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(123, 24014199005766100L, 24014199005766100L, 8,
                              104, 10000, 123, 10000, elem));

        elem = new ElemData(0, 24014199286739600L, 24014199287239600L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(132, 24014199286989600L, 24014199286989600L, 11,
                              104, 10000, 132, 10000, elem));

        elem = new ElemData(0, 24014199295756900L, 24014199296256900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(257, 24014199296006900L, 24014199296006900L, 9,
                              104, 10000, 257, 10000, elem));

        elem = null;
        list.add(buildTrigger(249, 24014199300905500L, 24014199300905500L, -1,
                              -1, 10000, 249, 10000, elem));

        elem = new ElemData(0, 24014199323094400L, 24014199323594400L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(124, 24014199323344400L, 24014199323344400L, 8,
                              104, 10000, 124, 10000, elem));

        elem = new ElemData(0, 24014199340953900L, 24014199341453900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(259, 24014199341203900L, 24014199341203900L, 9,
                              104, 10000, 259, 10000, elem));

        elem = new ElemData(0, 24014199435868400L, 24014199436368400L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(125, 24014199436118400L, 24014199436118400L, 8,
                              104, 10000, 125, 10000, elem));

        elem = null;
        list.add(buildTrigger(250, 24014199461822800L, 24014199461822800L, -1,
                              -1, 10000, 250, 10000, elem));

        elem = null;
        list.add(buildTrigger(252, 24014199466232000L, 24014199466232000L, -1,
                              -1, 10000, 252, 10000, elem));

        elem = null;
        list.add(buildTrigger(254, 24014199477164400L, 24014199477164400L, -1,
                              -1, 10000, 254, 10000, elem));

        elem = new ElemData(0, 24014199529019200L, 24014199529519200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(126, 24014199529269200L, 24014199529269200L, 8,
                              104, 10000, 126, 10000, elem));

        elem = new ElemData(0, 24014199538520700L, 24014199539020700L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(263, 24014199538770700L, 24014199538770700L, 9,
                              104, 10000, 263, 10000, elem));

        elem = new ElemData(0, 24014199538624200L, 24014199539124200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(264, 24014199538874200L, 24014199538874200L, 9,
                              104, 10000, 264, 10000, elem));

        elem = new ElemData(0, 24014199601259300L, 24014199601759300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(265, 24014199601509300L, 24014199601509300L, 9,
                              104, 10000, 265, 10000, elem));

        elem = null;
        list.add(buildTrigger(256, 24014199649688900L, 24014199649688900L, -1,
                              -1, 10000, 256, 10000, elem));

        elem = null;
        list.add(buildTrigger(258, 24014199723082000L, 24014199723082000L, -1,
                              -1, 10000, 258, 10000, elem));

        elem = new ElemData(0, 24014199731975900L, 24014199732475900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(127, 24014199732225900L, 24014199732225900L, 8,
                              104, 10000, 127, 10000, elem));

        elem = null;
        list.add(buildTrigger(259, 24014199818632400L, 24014199818632400L, -1,
                              -1, 10000, 259, 10000, elem));

        elem = null;
        list.add(buildTrigger(261, 24014199857698100L, 24014199857698100L, -1,
                              -1, 10000, 261, 10000, elem));

        elem = new ElemData(0, 24014199889472200L, 24014199889972200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(270, 24014199889722200L, 24014199889722200L, 9,
                              104, 10000, 270, 10000, elem));

        elem = null;
        list.add(buildTrigger(263, 24014199960585600L, 24014199960585600L, -1,
                              -1, 10000, 263, 10000, elem));

        elem = new ElemData(0, 24014199971247100L, 24014199971747100L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(128, 24014199971497100L, 24014199971497100L, 8,
                              104, 10000, 128, 10000, elem));

        elem = new ElemData(0, 24014199999750700L, 24014200000250700L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(2, 24014200000000700L, 24014200000000700L, 12,
                              104, 10000, 2, 10000, elem));

        elem = new ElemData(0, 24014200034855400L, 24014200035355400L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(272, 24014200035105400L, 24014200035105400L, 9,
                              104, 10000, 272, 10000, elem));

        elem = new ElemData(0, 24014200047030200L, 24014200047530200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(129, 24014200047280200L, 24014200047280200L, 8,
                              104, 10000, 129, 10000, elem));

        elem = new ElemData(0, 24014200091720700L, 24014200092220700L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(130, 24014200091970700L, 24014200091970700L, 8,
                              104, 10000, 130, 10000, elem));

        elem = null;
        list.add(buildTrigger(265, 24014200103497300L, 24014200103497300L, -1,
                              -1, 10000, 265, 10000, elem));

        elem = null;
        list.add(buildTrigger(267, 24014200167208900L, 24014200167208900L, -1,
                              -1, 10000, 267, 10000, elem));

        elem = new ElemData(0, 24014200192209300L, 24014200192709300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(275, 24014200192459300L, 24014200192459300L, 9,
                              104, 10000, 275, 10000, elem));

        elem = new ElemData(0, 24014200232054900L, 24014200232554900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(131, 24014200232304900L, 24014200232304900L, 8,
                              104, 10000, 131, 10000, elem));

        elem = new ElemData(0, 24014200246805500L, 24014200247305500L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(143, 24014200247055500L, 24014200247055500L, 11,
                              104, 10000, 143, 10000, elem));

        elem = null;
        list.add(buildTrigger(268, 24014200266829700L, 24014200266829700L, -1,
                              -1, 10000, 268, 10000, elem));

        elem = new ElemData(0, 24014200348974600L, 24014200349474600L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(132, 24014200349224600L, 24014200349224600L, 8,
                              104, 10000, 132, 10000, elem));

        elem = new ElemData(0, 24014200383435500L, 24014200383935500L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(133, 24014200383685500L, 24014200383685500L, 8,
                              104, 10000, 133, 10000, elem));

        elem = new ElemData(0, 24014200403798900L, 24014200404298900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(144, 24014200404048900L, 24014200404048900L, 11,
                              104, 10000, 144, 10000, elem));

        elem = null;
        list.add(buildTrigger(270, 24014200451839800L, 24014200451839800L, -1,
                              -1, 10000, 270, 10000, elem));

        elem = null;
        list.add(buildTrigger(271, 24014200454032900L, 24014200454032900L, -1,
                              -1, 10000, 271, 10000, elem));

        elem = new ElemData(0, 24014200480611800L, 24014200481111800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(134, 24014200480861800L, 24014200480861800L, 8,
                              104, 10000, 134, 10000, elem));

        elem = new ElemData(0, 24014200485364100L, 24014200485864100L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(146, 24014200485614100L, 24014200485614100L, 11,
                              104, 10000, 146, 10000, elem));

        elem = new ElemData(0, 24014200517052700L, 24014200517552700L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(135, 24014200517302700L, 24014200517302700L, 8,
                              104, 10000, 135, 10000, elem));

        elem = new ElemData(0, 24014200622398200L, 24014200622898200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(136, 24014200622648200L, 24014200622648200L, 8,
                              104, 10000, 136, 10000, elem));

        elem = null;
        list.add(buildTrigger(272, 24014200626707500L, 24014200626707500L, -1,
                              -1, 10000, 272, 10000, elem));

        elem = new ElemData(0, 24014200786135900L, 24014200786635900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(137, 24014200786385900L, 24014200786385900L, 8,
                              104, 10000, 137, 10000, elem));

        elem = new ElemData(0, 24014200830470900L, 24014200830970900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(280, 24014200830720900L, 24014200830720900L, 9,
                              104, 10000, 280, 10000, elem));

        elem = null;
        list.add(buildTrigger(273, 24014200852105400L, 24014200852105400L, -1,
                              -1, 10000, 273, 10000, elem));

        elem = new ElemData(0, 24014200854902300L, 24014200855402300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(138, 24014200855152300L, 24014200855152300L, 8,
                              104, 10000, 138, 10000, elem));

        elem = new ElemData(0, 24014200938181300L, 24014200938681300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(139, 24014200938431300L, 24014200938431300L, 8,
                              104, 10000, 139, 10000, elem));

        elem = null;
        list.add(buildTrigger(275, 24014200952047000L, 24014200952047000L, -1,
                              -1, 10000, 275, 10000, elem));

        elem = new ElemData(0, 24014200991032900L, 24014200991532900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(283, 24014200991282900L, 24014200991282900L, 9,
                              104, 10000, 283, 10000, elem));

        elem = null;
        list.add(buildTrigger(277, 24014201023505600L, 24014201023505600L, -1,
                              -1, 10000, 277, 10000, elem));

        elem = null;
        list.add(buildTrigger(279, 24014201037547200L, 24014201037547200L, -1,
                              -1, 10000, 279, 10000, elem));

        elem = null;
        list.add(buildTrigger(281, 24014201047603000L, 24014201047603000L, -1,
                              -1, 10000, 281, 10000, elem));

        elem = new ElemData(0, 24014201053060600L, 24014201053560600L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(140, 24014201053310600L, 24014201053310600L, 8,
                              104, 10000, 140, 10000, elem));

        elem = null;
        list.add(buildTrigger(283, 24014201073676000L, 24014201073676000L, -1,
                              -1, 10000, 283, 10000, elem));

        elem = null;
        list.add(buildTrigger(284, 24014201079633200L, 24014201079633200L, -1,
                              -1, 10000, 284, 10000, elem));

        elem = new ElemData(0, 24014201084795300L, 24014201085295300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(289, 24014201085045300L, 24014201085045300L, 9,
                              104, 10000, 289, 10000, elem));

        elem = null;
        list.add(buildTrigger(285, 24014201113213200L, 24014201113213200L, -1,
                              -1, 10000, 285, 10000, elem));

        elem = new ElemData(0, 24014201117706800L, 24014201118206800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(291, 24014201117956800L, 24014201117956800L, 9,
                              104, 10000, 291, 10000, elem));

        elem = new ElemData(0, 24014201163687700L, 24014201164187700L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(141, 24014201163937700L, 24014201163937700L, 8,
                              104, 10000, 141, 10000, elem));

        elem = new ElemData(0, 24014201247051800L, 24014201247551800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(142, 24014201247301800L, 24014201247301800L, 8,
                              104, 10000, 142, 10000, elem));

        elem = null;
        list.add(buildTrigger(287, 24014201390768300L, 24014201390768300L, -1,
                              -1, 10000, 287, 10000, elem));

        elem = new ElemData(0, 24014201396674600L, 24014201397174600L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(143, 24014201396924600L, 24014201396924600L, 8,
                              104, 10000, 143, 10000, elem));

        elem = new ElemData(0, 24014201596267900L, 24014201596767900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(144, 24014201596517900L, 24014201596517900L, 8,
                              104, 10000, 144, 10000, elem));

        elem = null;
        list.add(buildTrigger(289, 24014201736441200L, 24014201736441200L, -1,
                              -1, 10000, 289, 10000, elem));

        elem = new ElemData(0, 24014201742272200L, 24014201742772200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(294, 24014201742522200L, 24014201742522200L, 9,
                              104, 10000, 294, 10000, elem));

        elem = new ElemData(0, 24014201795909100L, 24014201796409100L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(295, 24014201796159100L, 24014201796159100L, 9,
                              104, 10000, 295, 10000, elem));

        elem = null;
        list.add(buildTrigger(290, 24014201824268900L, 24014201824268900L, -1,
                              -1, 10000, 290, 10000, elem));

        elem = null;
        list.add(buildTrigger(292, 24014201882657200L, 24014201882657200L, -1,
                              -1, 10000, 292, 10000, elem));

        elem = new ElemData(0, 24014201908406800L, 24014201908906800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(145, 24014201908656800L, 24014201908656800L, 8,
                              104, 10000, 145, 10000, elem));

        elem = null;
        list.add(buildTrigger(293, 24014201939263300L, 24014201939263300L, -1,
                              -1, 10000, 293, 10000, elem));

        elem = new ElemData(0, 24014201960279800L, 24014201960779800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(146, 24014201960529800L, 24014201960529800L, 8,
                              104, 10000, 146, 10000, elem));

        elem = new ElemData(0, 24014202027397900L, 24014202027897900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(299, 24014202027647900L, 24014202027647900L, 9,
                              104, 10000, 299, 10000, elem));

        elem = null;
        list.add(buildTrigger(294, 24014202046450100L, 24014202046450100L, -1,
                              -1, 10000, 294, 10000, elem));

        elem = null;
        list.add(buildTrigger(296, 24014202112270300L, 24014202112270300L, -1,
                              -1, 10000, 296, 10000, elem));

        elem = new ElemData(0, 24014202130058100L, 24014202130558100L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(302, 24014202130308100L, 24014202130308100L, 9,
                              104, 10000, 302, 10000, elem));

        elem = null;
        list.add(buildTrigger(298, 24014202171348400L, 24014202171348400L, -1,
                              -1, 10000, 298, 10000, elem));

        elem = null;
        list.add(buildTrigger(299, 24014202193265600L, 24014202193265600L, -1,
                              -1, 10000, 299, 10000, elem));

        elem = new ElemData(0, 24014202199990000L, 24014202200490000L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(305, 24014202200240000L, 24014202200240000L, 9,
                              104, 10000, 305, 10000, elem));

        elem = null;
        list.add(buildTrigger(301, 24014202214626400L, 24014202214626400L, -1,
                              -1, 10000, 301, 10000, elem));

        elem = new ElemData(0, 24014202243976500L, 24014202244476500L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(147, 24014202244226500L, 24014202244226500L, 8,
                              104, 10000, 147, 10000, elem));

        elem = new ElemData(0, 24014202288444100L, 24014202288944100L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(148, 24014202288694100L, 24014202288694100L, 8,
                              104, 10000, 148, 10000, elem));

        elem = null;
        list.add(buildTrigger(303, 24014202318252100L, 24014202318252100L, -1,
                              -1, 10000, 303, 10000, elem));

        elem = null;
        list.add(buildTrigger(304, 24014202368950600L, 24014202368950600L, -1,
                              -1, 10000, 304, 10000, elem));

        elem = null;
        list.add(buildTrigger(306, 24014202382859100L, 24014202382859100L, -1,
                              -1, 10000, 306, 10000, elem));

        elem = null;
        list.add(buildTrigger(308, 24014202392249100L, 24014202392249100L, -1,
                              -1, 10000, 308, 10000, elem));

        elem = null;
        list.add(buildTrigger(310, 24014202423042400L, 24014202423042400L, -1,
                              -1, 10000, 310, 10000, elem));

        elem = null;
        list.add(buildTrigger(312, 24014202610850700L, 24014202610850700L, -1,
                              -1, 10000, 312, 10000, elem));

        elem = new ElemData(0, 24014202719723300L, 24014202720223300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(149, 24014202719973300L, 24014202719973300L, 8,
                              104, 10000, 149, 10000, elem));

        elem = new ElemData(0, 24014202750318700L, 24014202750818700L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(313, 24014202750568700L, 24014202750568700L, 9,
                              104, 10000, 313, 10000, elem));

        elem = new ElemData(0, 24014202810266300L, 24014202810766300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(314, 24014202810516300L, 24014202810516300L, 9,
                              104, 10000, 314, 10000, elem));

        elem = null;
        list.add(buildTrigger(314, 24014203035224400L, 24014203035224400L, -1,
                              -1, 10000, 314, 10000, elem));

        elem = new ElemData(0, 24014203048590100L, 24014203049090100L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(171, 24014203048840100L, 24014203048840100L, 11,
                              104, 10000, 171, 10000, elem));

        elem = new ElemData(0, 24014203054999000L, 24014203055499000L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(150, 24014203055249000L, 24014203055249000L, 8,
                              104, 10000, 150, 10000, elem));

        elem = null;
        list.add(buildTrigger(315, 24014203127040300L, 24014203127040300L, -1,
                              -1, 10000, 315, 10000, elem));

        elem = new ElemData(0, 24014203159236400L, 24014203159736400L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(151, 24014203159486400L, 24014203159486400L, 8,
                              104, 10000, 151, 10000, elem));

        elem = null;
        list.add(buildTrigger(316, 24014203172399800L, 24014203172399800L, -1,
                              -1, 10000, 316, 10000, elem));

        elem = null;
        list.add(buildTrigger(318, 24014203175277900L, 24014203175277900L, -1,
                              -1, 10000, 318, 10000, elem));

        elem = new ElemData(0, 24014203177182200L, 24014203177682200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(319, 24014203177432200L, 24014203177432200L, 9,
                              104, 10000, 319, 10000, elem));

        elem = null;
        list.add(buildTrigger(319, 24014203216577700L, 24014203216577700L, -1,
                              -1, 10000, 319, 10000, elem));

        elem = null;
        list.add(buildTrigger(321, 24014203235179800L, 24014203235179800L, -1,
                              -1, 10000, 321, 10000, elem));

        elem = null;
        list.add(buildTrigger(323, 24014203268307100L, 24014203268307100L, -1,
                              -1, 10000, 323, 10000, elem));

        elem = new ElemData(0, 24014203272684500L, 24014203273184500L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(152, 24014203272934500L, 24014203272934500L, 8,
                              104, 10000, 152, 10000, elem));

        elem = new ElemData(0, 24014203362901700L, 24014203363401700L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(323, 24014203363151700L, 24014203363151700L, 9,
                              104, 10000, 323, 10000, elem));

        elem = new ElemData(0, 24014203394219400L, 24014203394719400L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(324, 24014203394469400L, 24014203394469400L, 9,
                              104, 10000, 324, 10000, elem));

        elem = null;
        list.add(buildTrigger(324, 24014203483235800L, 24014203483235800L, -1,
                              -1, 10000, 324, 10000, elem));

        elem = null;
        list.add(buildTrigger(326, 24014203571205900L, 24014203571205900L, -1,
                              -1, 10000, 326, 10000, elem));

        elem = null;
        list.add(buildTrigger(327, 24014203599529600L, 24014203599529600L, -1,
                              -1, 10000, 327, 10000, elem));

        elem = new ElemData(0, 24014203648725000L, 24014203649225000L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(153, 24014203648975000L, 24014203648975000L, 8,
                              104, 10000, 153, 10000, elem));

        elem = new ElemData(0, 24014203655010200L, 24014203655510200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(328, 24014203655260200L, 24014203655260200L, 9,
                              104, 10000, 328, 10000, elem));

        elem = new ElemData(0, 24014203669085500L, 24014203669585500L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(154, 24014203669335500L, 24014203669335500L, 8,
                              104, 10000, 154, 10000, elem));

        elem = null;
        list.add(buildTrigger(329, 24014203669619700L, 24014203669619700L, -1,
                              -1, 10000, 329, 10000, elem));

        elem = new ElemData(0, 24014203804552800L, 24014203805052800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(155, 24014203804802800L, 24014203804802800L, 8,
                              104, 10000, 155, 10000, elem));

        elem = null;
        list.add(buildTrigger(330, 24014203824687200L, 24014203824687200L, -1,
                              -1, 10000, 330, 10000, elem));

        elem = null;
        list.add(buildTrigger(332, 24014203852172600L, 24014203852172600L, -1,
                              -1, 10000, 332, 10000, elem));

        elem = new ElemData(0, 24014203949481400L, 24014203949981400L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(332, 24014203949731400L, 24014203949731400L, 9,
                              104, 10000, 332, 10000, elem));

        elem = new ElemData(0, 24014203963998800L, 24014203964498800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(179, 24014203964248800L, 24014203964248800L, 11,
                              104, 10000, 179, 10000, elem));

        elem = new ElemData(0, 24014204075166700L, 24014204075666700L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(333, 24014204075416700L, 24014204075416700L, 9,
                              104, 10000, 333, 10000, elem));

        elem = null;
        list.add(buildTrigger(334, 24014204091948900L, 24014204091948900L, -1,
                              -1, 10000, 334, 10000, elem));

        elem = new ElemData(0, 24014204215759200L, 24014204216259200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(335, 24014204216009200L, 24014204216009200L, 9,
                              104, 10000, 335, 10000, elem));

        elem = new ElemData(0, 24014204239558300L, 24014204240058300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(336, 24014204239808300L, 24014204239808300L, 9,
                              104, 10000, 336, 10000, elem));

        elem = new ElemData(0, 24014204316771200L, 24014204317271200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(156, 24014204317021200L, 24014204317021200L, 8,
                              104, 10000, 156, 10000, elem));

        elem = new ElemData(0, 24014204330758100L, 24014204331258100L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(157, 24014204331008100L, 24014204331008100L, 8,
                              104, 10000, 157, 10000, elem));

        elem = null;
        list.add(buildTrigger(336, 24014204386813200L, 24014204386813200L, -1,
                              -1, 10000, 336, 10000, elem));

        elem = null;
        list.add(buildTrigger(337, 24014204406684500L, 24014204406684500L, -1,
                              -1, 10000, 337, 10000, elem));

        elem = null;
        list.add(buildTrigger(338, 24014204428218100L, 24014204428218100L, -1,
                              -1, 10000, 338, 10000, elem));

        elem = new ElemData(0, 24014204542484800L, 24014204542984800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(158, 24014204542734800L, 24014204542734800L, 8,
                              104, 10000, 158, 10000, elem));

        elem = new ElemData(0, 24014204616416400L, 24014204616916400L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(340, 24014204616666400L, 24014204616666400L, 9,
                              104, 10000, 340, 10000, elem));

        elem = null;
        list.add(buildTrigger(339, 24014204635601900L, 24014204635601900L, -1,
                              -1, 10000, 339, 10000, elem));

        elem = null;
        list.add(buildTrigger(340, 24014204641335300L, 24014204641335300L, -1,
                              -1, 10000, 340, 10000, elem));

        elem = new ElemData(0, 24014204680923400L, 24014204681423400L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(159, 24014204681173400L, 24014204681173400L, 8,
                              104, 10000, 159, 10000, elem));

        elem = new ElemData(0, 24014204686531700L, 24014204687031700L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(2, 24014204686781700L, 24014204686781700L, 7,
                              104, 10000, 2, 10000, elem));

        elem = null;
        list.add(buildTrigger(342, 24014204812333800L, 24014204812333800L, -1,
                              -1, 10000, 342, 10000, elem));

        elem = new ElemData(0, 24014204842204800L, 24014204842704800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(160, 24014204842454800L, 24014204842454800L, 8,
                              104, 10000, 160, 10000, elem));

        elem = new ElemData(0, 24014204860348100L, 24014204860848100L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(344, 24014204860598100L, 24014204860598100L, 9,
                              104, 10000, 344, 10000, elem));

        elem = null;
        list.add(buildTrigger(344, 24014204948401800L, 24014204948401800L, -1,
                              -1, 10000, 344, 10000, elem));

        elem = null;
        list.add(buildTrigger(346, 24014204957912400L, 24014204957912400L, -1,
                              -1, 10000, 346, 10000, elem));

        elem = null;
        list.add(buildTrigger(348, 24014205039430500L, 24014205039430500L, -1,
                              -1, 10000, 348, 10000, elem));

        elem = new ElemData(0, 24014205143341500L, 24014205143841500L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(161, 24014205143591500L, 24014205143591500L, 8,
                              104, 10000, 161, 10000, elem));

        elem = new ElemData(0, 24014205194695800L, 24014205195195800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(188, 24014205194945800L, 24014205194945800L, 11,
                              104, 10000, 188, 10000, elem));

        elem = null;
        list.add(buildTrigger(350, 24014205203442500L, 24014205203442500L, -1,
                              -1, 10000, 350, 10000, elem));

        elem = new ElemData(0, 24014205226933300L, 24014205227433300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(349, 24014205227183300L, 24014205227183300L, 9,
                              104, 10000, 349, 10000, elem));

        elem = new ElemData(0, 24014205306673600L, 24014205307173600L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(162, 24014205306923600L, 24014205306923600L, 8,
                              104, 10000, 162, 10000, elem));

        elem = new ElemData(0, 24014205476782600L, 24014205477282600L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(163, 24014205477032600L, 24014205477032600L, 8,
                              104, 10000, 163, 10000, elem));

        elem = new ElemData(0, 24014205539735300L, 24014205540235300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(350, 24014205539985300L, 24014205539985300L, 9,
                              104, 10000, 350, 10000, elem));

        elem = null;
        list.add(buildTrigger(352, 24014205542718700L, 24014205542718700L, -1,
                              -1, 10000, 352, 10000, elem));

        elem = null;
        list.add(buildTrigger(353, 24014205571124800L, 24014205571124800L, -1,
                              -1, 10000, 353, 10000, elem));

        elem = null;
        list.add(buildTrigger(354, 24014205586292500L, 24014205586292500L, -1,
                              -1, 10000, 354, 10000, elem));

        elem = new ElemData(0, 24014205655111500L, 24014205655611500L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(354, 24014205655361500L, 24014205655361500L, 9,
                              104, 10000, 354, 10000, elem));

        elem = null;
        list.add(buildTrigger(355, 24014205876954300L, 24014205876954300L, -1,
                              -1, 10000, 355, 10000, elem));

        elem = null;
        list.add(buildTrigger(356, 24014205937212600L, 24014205937212600L, -1,
                              -1, 10000, 356, 10000, elem));

        elem = new ElemData(0, 24014205985392300L, 24014205985892300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(164, 24014205985642300L, 24014205985642300L, 8,
                              104, 10000, 164, 10000, elem));

        elem = null;
        list.add(buildTrigger(357, 24014206054764400L, 24014206054764400L, -1,
                              -1, 10000, 357, 10000, elem));

        elem = null;
        list.add(buildTrigger(358, 24014206104473900L, 24014206104473900L, -1,
                              -1, 10000, 358, 10000, elem));

        elem = new ElemData(0, 24014206137277400L, 24014206137777400L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(192, 24014206137527400L, 24014206137527400L, 11,
                              104, 10000, 192, 10000, elem));

        elem = new ElemData(0, 24014206153322500L, 24014206153822500L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(359, 24014206153572500L, 24014206153572500L, 9,
                              104, 10000, 359, 10000, elem));

        elem = new ElemData(0, 24014206167156900L, 24014206167656900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(360, 24014206167406900L, 24014206167406900L, 9,
                              104, 10000, 360, 10000, elem));

        elem = null;
        list.add(buildTrigger(360, 24014206235702000L, 24014206235702000L, -1,
                              -1, 10000, 360, 10000, elem));

        elem = null;
        list.add(buildTrigger(362, 24014206276183800L, 24014206276183800L, -1,
                              -1, 10000, 362, 10000, elem));

        elem = null;
        list.add(buildTrigger(364, 24014206315269300L, 24014206315269300L, -1,
                              -1, 10000, 364, 10000, elem));

        elem = null;
        list.add(buildTrigger(366, 24014206376280000L, 24014206376280000L, -1,
                              -1, 10000, 366, 10000, elem));

        elem = new ElemData(0, 24014206544316500L, 24014206544816500L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(165, 24014206544566500L, 24014206544566500L, 8,
                              104, 10000, 165, 10000, elem));

        elem = null;
        list.add(buildTrigger(367, 24014206555253400L, 24014206555253400L, -1,
                              -1, 10000, 367, 10000, elem));

        elem = null;
        list.add(buildTrigger(368, 24014206582670400L, 24014206582670400L, -1,
                              -1, 10000, 368, 10000, elem));

        elem = null;
        list.add(buildTrigger(369, 24014206831800300L, 24014206831800300L, -1,
                              -1, 10000, 369, 10000, elem));

        elem = new ElemData(0, 24014206837937400L, 24014206838437400L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(198, 24014206838187400L, 24014206838187400L, 11,
                              104, 10000, 198, 10000, elem));

        elem = new ElemData(0, 24014206860739300L, 24014206861239300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(166, 24014206860989300L, 24014206860989300L, 8,
                              104, 10000, 166, 10000, elem));

        elem = null;
        list.add(buildTrigger(371, 24014206889695700L, 24014206889695700L, -1,
                              -1, 10000, 371, 10000, elem));

        elem = null;
        list.add(buildTrigger(372, 24014206960914800L, 24014206960914800L, -1,
                              -1, 10000, 372, 10000, elem));

        elem = null;
        list.add(buildTrigger(373, 24014206978310800L, 24014206978310800L, -1,
                              -1, 10000, 373, 10000, elem));

        elem = new ElemData(0, 24014207014925800L, 24014207015425800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(167, 24014207015175800L, 24014207015175800L, 8,
                              104, 10000, 167, 10000, elem));

        elem = new ElemData(0, 24014207026665800L, 24014207027165800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(201, 24014207026915800L, 24014207026915800L, 11,
                              104, 10000, 201, 10000, elem));

        elem = null;
        list.add(buildTrigger(374, 24014207126315000L, 24014207126315000L, -1,
                              -1, 10000, 374, 10000, elem));

        elem = null;
        list.add(buildTrigger(376, 24014207127577200L, 24014207127577200L, -1,
                              -1, 10000, 376, 10000, elem));

        elem = new ElemData(0, 24014207173588800L, 24014207174088800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(373, 24014207173838800L, 24014207173838800L, 9,
                              104, 10000, 373, 10000, elem));

        elem = null;
        list.add(buildTrigger(378, 24014207190656400L, 24014207190656400L, -1,
                              -1, 10000, 378, 10000, elem));

        elem = new ElemData(0, 24014207195331500L, 24014207195831500L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(375, 24014207195581500L, 24014207195581500L, 9,
                              104, 10000, 375, 10000, elem));

        elem = new ElemData(0, 24014207223911900L, 24014207224411900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(168, 24014207224161900L, 24014207224161900L, 8,
                              104, 10000, 168, 10000, elem));

        elem = new ElemData(0, 24014207272461600L, 24014207272961600L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(169, 24014207272711600L, 24014207272711600L, 8,
                              104, 10000, 169, 10000, elem));

        elem = new ElemData(0, 24014207300161400L, 24014207300661400L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(376, 24014207300411400L, 24014207300411400L, 9,
                              104, 10000, 376, 10000, elem));

        elem = new ElemData(0, 24014207331278700L, 24014207331778700L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(377, 24014207331528700L, 24014207331528700L, 9,
                              104, 10000, 377, 10000, elem));

        elem = new ElemData(0, 24014207337599700L, 24014207338099700L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(170, 24014207337849700L, 24014207337849700L, 8,
                              104, 10000, 170, 10000, elem));

        elem = new ElemData(0, 24014207563497200L, 24014207563997200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(171, 24014207563747200L, 24014207563747200L, 8,
                              104, 10000, 171, 10000, elem));

        elem = new ElemData(0, 24014207589066900L, 24014207589566900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(378, 24014207589316900L, 24014207589316900L, 9,
                              104, 10000, 378, 10000, elem));

        elem = new ElemData(0, 24014207603409700L, 24014207603909700L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(379, 24014207603659700L, 24014207603659700L, 9,
                              104, 10000, 379, 10000, elem));

        elem = null;
        list.add(buildTrigger(379, 24014207603814500L, 24014207603814500L, -1,
                              -1, 10000, 379, 10000, elem));

        elem = new ElemData(0, 24014207635177100L, 24014207635677100L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(172, 24014207635427100L, 24014207635427100L, 8,
                              104, 10000, 172, 10000, elem));

        elem = new ElemData(0, 24014207642843500L, 24014207643343500L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(173, 24014207643093500L, 24014207643093500L, 8,
                              104, 10000, 173, 10000, elem));

        elem = null;
        list.add(buildTrigger(381, 24014207646883700L, 24014207646883700L, -1,
                              -1, 10000, 381, 10000, elem));

        elem = new ElemData(0, 24014207660208400L, 24014207660708400L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(174, 24014207660458400L, 24014207660458400L, 8,
                              104, 10000, 174, 10000, elem));

        elem = new ElemData(0, 24014207665995300L, 24014207666495300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(175, 24014207666245300L, 24014207666245300L, 8,
                              104, 10000, 175, 10000, elem));

        elem = null;
        list.add(buildTrigger(383, 24014207713689600L, 24014207713689600L, -1,
                              -1, 10000, 383, 10000, elem));

        elem = new ElemData(0, 24014207731069200L, 24014207731569200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(383, 24014207731319200L, 24014207731319200L, 9,
                              104, 10000, 383, 10000, elem));

        elem = new ElemData(0, 24014207766071300L, 24014207766571300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(176, 24014207766321300L, 24014207766321300L, 8,
                              104, 10000, 176, 10000, elem));

        elem = null;
        list.add(buildTrigger(385, 24014207958143000L, 24014207958143000L, -1,
                              -1, 10000, 385, 10000, elem));

        elem = new ElemData(0, 24014207997884300L, 24014207998384300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(385, 24014207998134300L, 24014207998134300L, 9,
                              104, 10000, 385, 10000, elem));

        elem = new ElemData(0, 24014208008121400L, 24014208008621400L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(177, 24014208008371400L, 24014208008371400L, 8,
                              104, 10000, 177, 10000, elem));

        elem = new ElemData(0, 24014208042765700L, 24014208043265700L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(207, 24014208043015700L, 24014208043015700L, 11,
                              104, 10000, 207, 10000, elem));

        elem = new ElemData(0, 24014208044776200L, 24014208045276200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(178, 24014208045026200L, 24014208045026200L, 8,
                              104, 10000, 178, 10000, elem));

        elem = new ElemData(0, 24014208090952300L, 24014208091452300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(386, 24014208091202300L, 24014208091202300L, 9,
                              104, 10000, 386, 10000, elem));

        elem = new ElemData(0, 24014208181761200L, 24014208182261200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(387, 24014208182011200L, 24014208182011200L, 9,
                              104, 10000, 387, 10000, elem));

        elem = new ElemData(0, 24014208205553300L, 24014208206053300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(388, 24014208205803300L, 24014208205803300L, 9,
                              104, 10000, 388, 10000, elem));

        elem = null;
        list.add(buildTrigger(387, 24014208207664200L, 24014208207664200L, -1,
                              -1, 10000, 387, 10000, elem));

        elem = null;
        list.add(buildTrigger(388, 24014208224042800L, 24014208224042800L, -1,
                              -1, 10000, 388, 10000, elem));

        elem = new ElemData(0, 24014208267836000L, 24014208268336000L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(179, 24014208268086000L, 24014208268086000L, 8,
                              104, 10000, 179, 10000, elem));

        elem = null;
        list.add(buildTrigger(390, 24014208327261000L, 24014208327261000L, -1,
                              -1, 10000, 390, 10000, elem));

        elem = null;
        list.add(buildTrigger(392, 24014208344409600L, 24014208344409600L, -1,
                              -1, 10000, 392, 10000, elem));

        elem = new ElemData(0, 24014208344263400L, 24014208344763400L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(211, 24014208344513400L, 24014208344513400L, 11,
                              104, 10000, 211, 10000, elem));

        elem = new ElemData(0, 24014208385569700L, 24014208386069700L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(393, 24014208385819700L, 24014208385819700L, 9,
                              104, 10000, 393, 10000, elem));

        elem = null;
        list.add(buildTrigger(394, 24014208489154600L, 24014208489154600L, -1,
                              -1, 10000, 394, 10000, elem));

        elem = null;
        list.add(buildTrigger(396, 24014208602260600L, 24014208602260600L, -1,
                              -1, 10000, 396, 10000, elem));

        elem = null;
        list.add(buildTrigger(398, 24014208729885100L, 24014208729885100L, -1,
                              -1, 10000, 398, 10000, elem));

        elem = new ElemData(0, 24014208747089700L, 24014208747589700L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(397, 24014208747339700L, 24014208747339700L, 9,
                              104, 10000, 397, 10000, elem));

        elem = null;
        list.add(buildTrigger(399, 24014208788347000L, 24014208788347000L, -1,
                              -1, 10000, 399, 10000, elem));

        elem = null;
        list.add(buildTrigger(401, 24014208871086000L, 24014208871086000L, -1,
                              -1, 10000, 401, 10000, elem));

        elem = new ElemData(0, 24014208872120200L, 24014208872620200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(180, 24014208872370200L, 24014208872370200L, 8,
                              104, 10000, 180, 10000, elem));

        elem = new ElemData(0, 24014208900534700L, 24014208901034700L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(181, 24014208900784700L, 24014208900784700L, 8,
                              104, 10000, 181, 10000, elem));

        elem = null;
        list.add(buildTrigger(403, 24014209033959000L, 24014209033959000L, -1,
                              -1, 10000, 403, 10000, elem));

        elem = null;
        list.add(buildTrigger(405, 24014209050812200L, 24014209050812200L, -1,
                              -1, 10000, 405, 10000, elem));

        elem = new ElemData(0, 24014209072920300L, 24014209073420300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(182, 24014209073170300L, 24014209073170300L, 8,
                              104, 10000, 182, 10000, elem));

        elem = new ElemData(0, 24014209207908300L, 24014209208408300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(402, 24014209208158300L, 24014209208158300L, 9,
                              104, 10000, 402, 10000, elem));

        elem = null;
        list.add(buildTrigger(407, 24014209248128900L, 24014209248128900L, -1,
                              -1, 10000, 407, 10000, elem));

        elem = new ElemData(0, 24014209344672500L, 24014209345172500L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(183, 24014209344922500L, 24014209344922500L, 8,
                              104, 10000, 183, 10000, elem));

        elem = new ElemData(0, 24014209399715700L, 24014209400215700L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(184, 24014209399965700L, 24014209399965700L, 8,
                              104, 10000, 184, 10000, elem));

        elem = null;
        list.add(buildTrigger(409, 24014209412185900L, 24014209412185900L, -1,
                              -1, 10000, 409, 10000, elem));

        elem = null;
        list.add(buildTrigger(410, 24014209457737100L, 24014209457737100L, -1,
                              -1, 10000, 410, 10000, elem));

        elem = null;
        list.add(buildTrigger(412, 24014209469467300L, 24014209469467300L, -1,
                              -1, 10000, 412, 10000, elem));

        elem = null;
        list.add(buildTrigger(414, 24014209497938500L, 24014209497938500L, -1,
                              -1, 10000, 414, 10000, elem));

        elem = new ElemData(0, 24014209517027200L, 24014209517527200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(185, 24014209517277200L, 24014209517277200L, 8,
                              104, 10000, 185, 10000, elem));

        elem = null;
        list.add(buildTrigger(416, 24014209702500100L, 24014209702500100L, -1,
                              -1, 10000, 416, 10000, elem));

        elem = new ElemData(0, 24014209758740500L, 24014209759240500L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(186, 24014209758990500L, 24014209758990500L, 8,
                              104, 10000, 186, 10000, elem));

        elem = null;
        list.add(buildTrigger(418, 24014209766434600L, 24014209766434600L, -1,
                              -1, 10000, 418, 10000, elem));

        elem = new ElemData(0, 24014209794012900L, 24014209794512900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(410, 24014209794262900L, 24014209794262900L, 9,
                              104, 10000, 410, 10000, elem));

        elem = new ElemData(0, 24014209794247700L, 24014209794747700L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(411, 24014209794497700L, 24014209794497700L, 9,
                              104, 10000, 411, 10000, elem));

        elem = null;
        list.add(buildTrigger(420, 24014209800804000L, 24014209800804000L, -1,
                              -1, 10000, 420, 10000, elem));

        elem = new ElemData(0, 24014209881738700L, 24014209882238700L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(413, 24014209881988700L, 24014209881988700L, 9,
                              104, 10000, 413, 10000, elem));

        elem = null;
        list.add(buildTrigger(421, 24014209904956500L, 24014209904956500L, -1,
                              -1, 10000, 421, 10000, elem));

        elem = new ElemData(0, 24014209917749000L, 24014209918249000L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(226, 24014209917999000L, 24014209917999000L, 11,
                              104, 10000, 226, 10000, elem));

        elem = new ElemData(0, 24014209959489800L, 24014209959989800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(415, 24014209959739800L, 24014209959739800L, 9,
                              104, 10000, 415, 10000, elem));

        elem = new ElemData(0, 24014209963620000L, 24014209964120000L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(227, 24014209963870000L, 24014209963870000L, 11,
                              104, 10000, 227, 10000, elem));

        elem = new ElemData(0, 24014209978228000L, 24014209978728000L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(187, 24014209978478000L, 24014209978478000L, 8,
                              104, 10000, 187, 10000, elem));

        elem = new ElemData(0, 24014209999781700L, 24014210000281700L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(3, 24014210000031700L, 24014210000031700L, 12,
                              104, 10000, 3, 10000, elem));

        elem = null;
        list.add(buildTrigger(422, 24014210032821900L, 24014210032821900L, -1,
                              -1, 10000, 422, 10000, elem));

        elem = new ElemData(0, 24014210037184900L, 24014210037684900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(417, 24014210037434900L, 24014210037434900L, 9,
                              104, 10000, 417, 10000, elem));

        elem = null;
        list.add(buildTrigger(423, 24014210047846200L, 24014210047846200L, -1,
                              -1, 10000, 423, 10000, elem));

        elem = null;
        list.add(buildTrigger(424, 24014210192432800L, 24014210192432800L, -1,
                              -1, 10000, 424, 10000, elem));

        elem = new ElemData(0, 24014210219705900L, 24014210220205900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(188, 24014210219955900L, 24014210219955900L, 8,
                              104, 10000, 188, 10000, elem));

        elem = null;
        list.add(buildTrigger(425, 24014210230104000L, 24014210230104000L, -1,
                              -1, 10000, 425, 10000, elem));

        elem = null;
        list.add(buildTrigger(427, 24014210246203300L, 24014210246203300L, -1,
                              -1, 10000, 427, 10000, elem));

        elem = new ElemData(0, 24014210272770100L, 24014210273270100L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(189, 24014210273020100L, 24014210273020100L, 8,
                              104, 10000, 189, 10000, elem));

        elem = null;
        list.add(buildTrigger(428, 24014210337883000L, 24014210337883000L, -1,
                              -1, 10000, 428, 10000, elem));

        elem = null;
        list.add(buildTrigger(430, 24014210415854700L, 24014210415854700L, -1,
                              -1, 10000, 430, 10000, elem));

        elem = new ElemData(0, 24014210527830900L, 24014210528330900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(190, 24014210528080900L, 24014210528080900L, 8,
                              104, 10000, 190, 10000, elem));

        elem = new ElemData(0, 24014210564768200L, 24014210565268200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(424, 24014210565018200L, 24014210565018200L, 9,
                              104, 10000, 424, 10000, elem));

        elem = new ElemData(0, 24014210568170800L, 24014210568670800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(232, 24014210568420800L, 24014210568420800L, 11,
                              104, 10000, 232, 10000, elem));

        elem = null;
        list.add(buildTrigger(432, 24014210595911200L, 24014210595911200L, -1,
                              -1, 10000, 432, 10000, elem));

        elem = null;
        list.add(buildTrigger(434, 24014210657836300L, 24014210657836300L, -1,
                              -1, 10000, 434, 10000, elem));

        elem = null;
        list.add(buildTrigger(436, 24014210666995700L, 24014210666995700L, -1,
                              -1, 10000, 436, 10000, elem));

        elem = new ElemData(0, 24014210684006300L, 24014210684506300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(428, 24014210684256300L, 24014210684256300L, 9,
                              104, 10000, 428, 10000, elem));

        elem = new ElemData(0, 24014210726567200L, 24014210727067200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(191, 24014210726817200L, 24014210726817200L, 8,
                              104, 10000, 191, 10000, elem));

        elem = new ElemData(0, 24014210772616000L, 24014210773116000L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(429, 24014210772866000L, 24014210772866000L, 9,
                              104, 10000, 429, 10000, elem));

        elem = new ElemData(0, 24014210883268200L, 24014210883768200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(430, 24014210883518200L, 24014210883518200L, 9,
                              104, 10000, 430, 10000, elem));

        elem = null;
        list.add(buildTrigger(437, 24014210940205100L, 24014210940205100L, -1,
                              -1, 10000, 437, 10000, elem));

        elem = new ElemData(0, 24014211013389200L, 24014211013889200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(192, 24014211013639200L, 24014211013639200L, 8,
                              104, 10000, 192, 10000, elem));

        elem = new ElemData(0, 24014211119086400L, 24014211119586400L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(432, 24014211119336400L, 24014211119336400L, 9,
                              104, 10000, 432, 10000, elem));

        elem = null;
        list.add(buildTrigger(438, 24014211201464600L, 24014211201464600L, -1,
                              -1, 10000, 438, 10000, elem));

        elem = null;
        list.add(buildTrigger(439, 24014211274420800L, 24014211274420800L, -1,
                              -1, 10000, 439, 10000, elem));

        elem = new ElemData(0, 24014211336617700L, 24014211337117700L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(193, 24014211336867700L, 24014211336867700L, 8,
                              104, 10000, 193, 10000, elem));

        elem = new ElemData(0, 24014211352045000L, 24014211352545000L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(194, 24014211352295000L, 24014211352295000L, 8,
                              104, 10000, 194, 10000, elem));

        elem = null;
        list.add(buildTrigger(441, 24014211370642800L, 24014211370642800L, -1,
                              -1, 10000, 441, 10000, elem));

        elem = new ElemData(0, 24014211473559100L, 24014211474059100L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(237, 24014211473809100L, 24014211473809100L, 11,
                              104, 10000, 237, 10000, elem));

        elem = null;
        list.add(buildTrigger(443, 24014211490826200L, 24014211490826200L, -1,
                              -1, 10000, 443, 10000, elem));

        elem = new ElemData(0, 24014211513809200L, 24014211514309200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(195, 24014211514059200L, 24014211514059200L, 8,
                              104, 10000, 195, 10000, elem));

        elem = new ElemData(0, 24014211557021800L, 24014211557521800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(196, 24014211557271800L, 24014211557271800L, 8,
                              104, 10000, 196, 10000, elem));

        elem = null;
        list.add(buildTrigger(444, 24014211566007600L, 24014211566007600L, -1,
                              -1, 10000, 444, 10000, elem));

        elem = new ElemData(0, 24014211573586900L, 24014211574086900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(197, 24014211573836900L, 24014211573836900L, 8,
                              104, 10000, 197, 10000, elem));

        elem = new ElemData(0, 24014211587431800L, 24014211587931800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(198, 24014211587681800L, 24014211587681800L, 8,
                              104, 10000, 198, 10000, elem));

        elem = null;
        list.add(buildTrigger(445, 24014211757466300L, 24014211757466300L, -1,
                              -1, 10000, 445, 10000, elem));

        elem = new ElemData(0, 24014211881018900L, 24014211881518900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(199, 24014211881268900L, 24014211881268900L, 8,
                              104, 10000, 199, 10000, elem));

        elem = null;
        list.add(buildTrigger(447, 24014212022344700L, 24014212022344700L, -1,
                              -1, 10000, 447, 10000, elem));

        elem = new ElemData(0, 24014212052107300L, 24014212052607300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(440, 24014212052357300L, 24014212052357300L, 9,
                              104, 10000, 440, 10000, elem));

        elem = null;
        list.add(buildTrigger(448, 24014212086184900L, 24014212086184900L, -1,
                              -1, 10000, 448, 10000, elem));

        elem = new ElemData(0, 24014212108093200L, 24014212108593200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(200, 24014212108343200L, 24014212108343200L, 8,
                              104, 10000, 200, 10000, elem));

        elem = new ElemData(0, 24014212262703000L, 24014212263203000L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(201, 24014212262953000L, 24014212262953000L, 8,
                              104, 10000, 201, 10000, elem));

        elem = null;
        list.add(buildTrigger(449, 24014212335286300L, 24014212335286300L, -1,
                              -1, 10000, 449, 10000, elem));

        elem = new ElemData(0, 24014212439085000L, 24014212439585000L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(202, 24014212439335000L, 24014212439335000L, 8,
                              104, 10000, 202, 10000, elem));

        elem = null;
        list.add(buildTrigger(450, 24014212493587000L, 24014212493587000L, -1,
                              -1, 10000, 450, 10000, elem));

        elem = new ElemData(0, 24014212496713500L, 24014212497213500L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(444, 24014212496963500L, 24014212496963500L, 9,
                              104, 10000, 444, 10000, elem));

        elem = new ElemData(0, 24014212513606200L, 24014212514106200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(203, 24014212513856200L, 24014212513856200L, 8,
                              104, 10000, 203, 10000, elem));

        elem = new ElemData(0, 24014212566186200L, 24014212566686200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(240, 24014212566436200L, 24014212566436200L, 11,
                              104, 10000, 240, 10000, elem));

        elem = new ElemData(0, 24014212587519500L, 24014212588019500L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(445, 24014212587769500L, 24014212587769500L, 9,
                              104, 10000, 445, 10000, elem));

        elem = new ElemData(0, 24014212605120800L, 24014212605620800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(204, 24014212605370800L, 24014212605370800L, 8,
                              104, 10000, 204, 10000, elem));

        elem = new ElemData(0, 24014212650064100L, 24014212650564100L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(446, 24014212650314100L, 24014212650314100L, 9,
                              104, 10000, 446, 10000, elem));

        elem = new ElemData(0, 24014212728786100L, 24014212729286100L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(205, 24014212729036100L, 24014212729036100L, 8,
                              104, 10000, 205, 10000, elem));

        elem = new ElemData(0, 24014212730014700L, 24014212730514700L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(447, 24014212730264700L, 24014212730264700L, 9,
                              104, 10000, 447, 10000, elem));

        elem = new ElemData(0, 24014212773891600L, 24014212774391600L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(241, 24014212774141600L, 24014212774141600L, 11,
                              104, 10000, 241, 10000, elem));

        elem = new ElemData(0, 24014212809931200L, 24014212810431200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(206, 24014212810181200L, 24014212810181200L, 8,
                              104, 10000, 206, 10000, elem));

        elem = null;
        list.add(buildTrigger(451, 24014212816580700L, 24014212816580700L, -1,
                              -1, 10000, 451, 10000, elem));

        elem = new ElemData(0, 24014212830782700L, 24014212831282700L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(449, 24014212831032700L, 24014212831032700L, 9,
                              104, 10000, 449, 10000, elem));

        elem = new ElemData(0, 24014212839227900L, 24014212839727900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(242, 24014212839477900L, 24014212839477900L, 11,
                              104, 10000, 242, 10000, elem));

        elem = new ElemData(0, 24014212890573200L, 24014212891073200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(207, 24014212890823200L, 24014212890823200L, 8,
                              104, 10000, 207, 10000, elem));

        elem = null;
        list.add(buildTrigger(452, 24014212900716900L, 24014212900716900L, -1,
                              -1, 10000, 452, 10000, elem));

        elem = new ElemData(0, 24014212919070600L, 24014212919570600L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(208, 24014212919320600L, 24014212919320600L, 8,
                              104, 10000, 208, 10000, elem));

        elem = new ElemData(0, 24014213015744500L, 24014213016244500L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(451, 24014213015994500L, 24014213015994500L, 9,
                              104, 10000, 451, 10000, elem));

        elem = null;
        list.add(buildTrigger(453, 24014213022703500L, 24014213022703500L, -1,
                              -1, 10000, 453, 10000, elem));

        elem = null;
        list.add(buildTrigger(455, 24014213050555900L, 24014213050555900L, -1,
                              -1, 10000, 455, 10000, elem));

        elem = null;
        list.add(buildTrigger(456, 24014213089848200L, 24014213089848200L, -1,
                              -1, 10000, 456, 10000, elem));

        elem = new ElemData(0, 24014213096944000L, 24014213097444000L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(455, 24014213097194000L, 24014213097194000L, 9,
                              104, 10000, 455, 10000, elem));

        elem = new ElemData(0, 24014213146103300L, 24014213146603300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(209, 24014213146353300L, 24014213146353300L, 8,
                              104, 10000, 209, 10000, elem));

        elem = new ElemData(0, 24014213231464300L, 24014213231964300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(456, 24014213231714300L, 24014213231714300L, 9,
                              104, 10000, 456, 10000, elem));

        elem = new ElemData(0, 24014213354305500L, 24014213354805500L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(210, 24014213354555500L, 24014213354555500L, 8,
                              104, 10000, 210, 10000, elem));

        elem = new ElemData(0, 24014213467180300L, 24014213467680300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(211, 24014213467430300L, 24014213467430300L, 8,
                              104, 10000, 211, 10000, elem));

        elem = null;
        list.add(buildTrigger(458, 24014213481829300L, 24014213481829300L, -1,
                              -1, 10000, 458, 10000, elem));

        elem = new ElemData(0, 24014213534275400L, 24014213534775400L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(3, 24014213534525400L, 24014213534525400L, 7,
                              104, 10000, 3, 10000, elem));

        elem = new ElemData(0, 24014213581892000L, 24014213582392000L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(212, 24014213582142000L, 24014213582142000L, 8,
                              104, 10000, 212, 10000, elem));

        elem = null;
        list.add(buildTrigger(459, 24014213617362300L, 24014213617362300L, -1,
                              -1, 10000, 459, 10000, elem));

        elem = new ElemData(0, 24014213647866000L, 24014213648366000L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(459, 24014213648116000L, 24014213648116000L, 9,
                              104, 10000, 459, 10000, elem));

        elem = new ElemData(0, 24014213767449100L, 24014213767949100L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(213, 24014213767699100L, 24014213767699100L, 8,
                              104, 10000, 213, 10000, elem));

        elem = new ElemData(0, 24014213891255100L, 24014213891755100L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(214, 24014213891505100L, 24014213891505100L, 8,
                              104, 10000, 214, 10000, elem));

        elem = null;
        list.add(buildTrigger(461, 24014213926714400L, 24014213926714400L, -1,
                              -1, 10000, 461, 10000, elem));

        elem = null;
        list.add(buildTrigger(463, 24014214035334300L, 24014214035334300L, -1,
                              -1, 10000, 463, 10000, elem));

        elem = new ElemData(0, 24014214063448000L, 24014214063948000L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(215, 24014214063698000L, 24014214063698000L, 8,
                              104, 10000, 215, 10000, elem));

        elem = new ElemData(0, 24014214159850900L, 24014214160350900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(216, 24014214160100900L, 24014214160100900L, 8,
                              104, 10000, 216, 10000, elem));

        elem = null;
        list.add(buildTrigger(465, 24014214237689500L, 24014214237689500L, -1,
                              -1, 10000, 465, 10000, elem));

        elem = null;
        list.add(buildTrigger(466, 24014214261372600L, 24014214261372600L, -1,
                              -1, 10000, 466, 10000, elem));

        elem = null;
        list.add(buildTrigger(467, 24014214275564200L, 24014214275564200L, -1,
                              -1, 10000, 467, 10000, elem));

        elem = null;
        list.add(buildTrigger(469, 24014214298695500L, 24014214298695500L, -1,
                              -1, 10000, 469, 10000, elem));

        elem = new ElemData(0, 24014214307531000L, 24014214308031000L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(217, 24014214307781000L, 24014214307781000L, 8,
                              104, 10000, 217, 10000, elem));

        elem = null;
        list.add(buildTrigger(471, 24014214309741900L, 24014214309741900L, -1,
                              -1, 10000, 471, 10000, elem));

        elem = new ElemData(0, 24014214415543800L, 24014214416043800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(253, 24014214415793800L, 24014214415793800L, 11,
                              104, 10000, 253, 10000, elem));

        elem = null;
        list.add(buildTrigger(472, 24014214533330800L, 24014214533330800L, -1,
                              -1, 10000, 472, 10000, elem));

        elem = new ElemData(0, 24014214588329000L, 24014214588829000L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(218, 24014214588579000L, 24014214588579000L, 8,
                              104, 10000, 218, 10000, elem));

        elem = null;
        list.add(buildTrigger(473, 24014214595577700L, 24014214595577700L, -1,
                              -1, 10000, 473, 10000, elem));

        elem = new ElemData(0, 24014214596045800L, 24014214596545800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(469, 24014214596295800L, 24014214596295800L, 9,
                              104, 10000, 469, 10000, elem));

        elem = null;
        list.add(buildTrigger(475, 24014214623624700L, 24014214623624700L, -1,
                              -1, 10000, 475, 10000, elem));

        elem = null;
        list.add(buildTrigger(477, 24014214745481100L, 24014214745481100L, -1,
                              -1, 10000, 477, 10000, elem));

        elem = null;
        list.add(buildTrigger(479, 24014214892918600L, 24014214892918600L, -1,
                              -1, 10000, 479, 10000, elem));

        elem = new ElemData(0, 24014214922796600L, 24014214923296600L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(473, 24014214923046600L, 24014214923046600L, 9,
                              104, 10000, 473, 10000, elem));

        elem = null;
        list.add(buildTrigger(480, 24014214927589400L, 24014214927589400L, -1,
                              -1, 10000, 480, 10000, elem));

        elem = null;
        list.add(buildTrigger(482, 24014214928303900L, 24014214928303900L, -1,
                              -1, 10000, 482, 10000, elem));

        elem = new ElemData(0, 24014215014890300L, 24014215015390300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(219, 24014215015140300L, 24014215015140300L, 8,
                              104, 10000, 219, 10000, elem));

        elem = new ElemData(0, 24014215032656600L, 24014215033156600L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(220, 24014215032906600L, 24014215032906600L, 8,
                              104, 10000, 220, 10000, elem));

        elem = null;
        list.add(buildTrigger(483, 24014215105212100L, 24014215105212100L, -1,
                              -1, 10000, 483, 10000, elem));

        elem = null;
        list.add(buildTrigger(484, 24014215120249900L, 24014215120249900L, -1,
                              -1, 10000, 484, 10000, elem));

        elem = null;
        list.add(buildTrigger(485, 24014215173835300L, 24014215173835300L, -1,
                              -1, 10000, 485, 10000, elem));

        elem = new ElemData(0, 24014215194978300L, 24014215195478300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(479, 24014215195228300L, 24014215195228300L, 9,
                              104, 10000, 479, 10000, elem));

        elem = new ElemData(0, 24014215205486700L, 24014215205986700L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(480, 24014215205736700L, 24014215205736700L, 9,
                              104, 10000, 480, 10000, elem));

        elem = new ElemData(0, 24014215293319700L, 24014215293819700L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(221, 24014215293569700L, 24014215293569700L, 8,
                              104, 10000, 221, 10000, elem));

        elem = new ElemData(0, 24014215332480900L, 24014215332980900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(481, 24014215332730900L, 24014215332730900L, 9,
                              104, 10000, 481, 10000, elem));

        elem = new ElemData(0, 24014215393020800L, 24014215393520800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(482, 24014215393270800L, 24014215393270800L, 9,
                              104, 10000, 482, 10000, elem));

        elem = null;
        list.add(buildTrigger(487, 24014215449539100L, 24014215449539100L, -1,
                              -1, 10000, 487, 10000, elem));

        elem = null;
        list.add(buildTrigger(489, 24014215459753800L, 24014215459753800L, -1,
                              -1, 10000, 489, 10000, elem));

        elem = new ElemData(0, 24014215495315600L, 24014215495815600L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(485, 24014215495565600L, 24014215495565600L, 9,
                              104, 10000, 485, 10000, elem));

        elem = new ElemData(0, 24014215499253100L, 24014215499753100L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(486, 24014215499503100L, 24014215499503100L, 9,
                              104, 10000, 486, 10000, elem));

        elem = new ElemData(0, 24014215513331100L, 24014215513831100L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(222, 24014215513581100L, 24014215513581100L, 8,
                              104, 10000, 222, 10000, elem));

        elem = null;
        list.add(buildTrigger(491, 24014215519932700L, 24014215519932700L, -1,
                              -1, 10000, 491, 10000, elem));

        elem = null;
        list.add(buildTrigger(493, 24014215576967100L, 24014215576967100L, -1,
                              -1, 10000, 493, 10000, elem));

        elem = null;
        list.add(buildTrigger(494, 24014215620223200L, 24014215620223200L, -1,
                              -1, 10000, 494, 10000, elem));

        elem = new ElemData(0, 24014215695668600L, 24014215696168600L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(490, 24014215695918600L, 24014215695918600L, 9,
                              104, 10000, 490, 10000, elem));

        elem = null;
        list.add(buildTrigger(495, 24014215803041200L, 24014215803041200L, -1,
                              -1, 10000, 495, 10000, elem));

        elem = null;
        list.add(buildTrigger(496, 24014215881461500L, 24014215881461500L, -1,
                              -1, 10000, 496, 10000, elem));

        elem = new ElemData(0, 24014215889565900L, 24014215890065900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(493, 24014215889815900L, 24014215889815900L, 9,
                              104, 10000, 493, 10000, elem));

        elem = null;
        list.add(buildTrigger(497, 24014215906074600L, 24014215906074600L, -1,
                              -1, 10000, 497, 10000, elem));

        elem = null;
        list.add(buildTrigger(499, 24014215910232200L, 24014215910232200L, -1,
                              -1, 10000, 499, 10000, elem));

        elem = new ElemData(0, 24014215935942600L, 24014215936442600L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(496, 24014215936192600L, 24014215936192600L, 9,
                              104, 10000, 496, 10000, elem));

        elem = new ElemData(0, 24014215942561200L, 24014215943061200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(497, 24014215942811200L, 24014215942811200L, 9,
                              104, 10000, 497, 10000, elem));

        elem = null;
        list.add(buildTrigger(501, 24014216021708900L, 24014216021708900L, -1,
                              -1, 10000, 501, 10000, elem));

        elem = null;
        list.add(buildTrigger(503, 24014216073734900L, 24014216073734900L, -1,
                              -1, 10000, 503, 10000, elem));

        elem = null;
        list.add(buildTrigger(504, 24014216120355400L, 24014216120355400L, -1,
                              -1, 10000, 504, 10000, elem));

        elem = null;
        list.add(buildTrigger(505, 24014216135843400L, 24014216135843400L, -1,
                              -1, 10000, 505, 10000, elem));

        elem = null;
        list.add(buildTrigger(507, 24014216209290300L, 24014216209290300L, -1,
                              -1, 10000, 507, 10000, elem));

        elem = new ElemData(0, 24014216233227000L, 24014216233727000L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(223, 24014216233477000L, 24014216233477000L, 8,
                              104, 10000, 223, 10000, elem));

        elem = new ElemData(0, 24014216249630400L, 24014216250130400L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(224, 24014216249880400L, 24014216249880400L, 8,
                              104, 10000, 224, 10000, elem));

        elem = new ElemData(0, 24014216270309500L, 24014216270809500L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(225, 24014216270559500L, 24014216270559500L, 8,
                              104, 10000, 225, 10000, elem));

        elem = null;
        list.add(buildTrigger(508, 24014216273586500L, 24014216273586500L, -1,
                              -1, 10000, 508, 10000, elem));

        elem = new ElemData(0, 24014216351274400L, 24014216351774400L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(226, 24014216351524400L, 24014216351524400L, 8,
                              104, 10000, 226, 10000, elem));

        elem = null;
        list.add(buildTrigger(510, 24014216403942400L, 24014216403942400L, -1,
                              -1, 10000, 510, 10000, elem));

        elem = new ElemData(0, 24014216404430000L, 24014216404930000L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(505, 24014216404680000L, 24014216404680000L, 9,
                              104, 10000, 505, 10000, elem));

        elem = null;
        list.add(buildTrigger(511, 24014216438595000L, 24014216438595000L, -1,
                              -1, 10000, 511, 10000, elem));

        elem = null;
        list.add(buildTrigger(513, 24014216443609400L, 24014216443609400L, -1,
                              -1, 10000, 513, 10000, elem));

        elem = new ElemData(0, 24014216535154000L, 24014216535654000L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(227, 24014216535404000L, 24014216535404000L, 8,
                              104, 10000, 227, 10000, elem));

        elem = null;
        list.add(buildTrigger(515, 24014216635446100L, 24014216635446100L, -1,
                              -1, 10000, 515, 10000, elem));

        elem = null;
        list.add(buildTrigger(516, 24014216647464100L, 24014216647464100L, -1,
                              -1, 10000, 516, 10000, elem));

        elem = new ElemData(0, 24014216662169200L, 24014216662669200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(510, 24014216662419200L, 24014216662419200L, 9,
                              104, 10000, 510, 10000, elem));

        elem = null;
        list.add(buildTrigger(518, 24014216839863900L, 24014216839863900L, -1,
                              -1, 10000, 518, 10000, elem));

        elem = new ElemData(0, 24014216986153900L, 24014216986653900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(228, 24014216986403900L, 24014216986403900L, 8,
                              104, 10000, 228, 10000, elem));

        elem = new ElemData(0, 24014217064488400L, 24014217064988400L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(274, 24014217064738400L, 24014217064738400L, 11,
                              104, 10000, 274, 10000, elem));

        elem = new ElemData(0, 24014217097639400L, 24014217098139400L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(229, 24014217097889400L, 24014217097889400L, 8,
                              104, 10000, 229, 10000, elem));

        elem = new ElemData(0, 24014217199564200L, 24014217200064200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(275, 24014217199814200L, 24014217199814200L, 11,
                              104, 10000, 275, 10000, elem));

        elem = new ElemData(0, 24014217348620100L, 24014217349120100L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(276, 24014217348870100L, 24014217348870100L, 11,
                              104, 10000, 276, 10000, elem));

        elem = null;
        list.add(buildTrigger(519, 24014217424553400L, 24014217424553400L, -1,
                              -1, 10000, 519, 10000, elem));

        elem = new ElemData(0, 24014217508430600L, 24014217508930600L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(230, 24014217508680600L, 24014217508680600L, 8,
                              104, 10000, 230, 10000, elem));

        elem = new ElemData(0, 24014217517072700L, 24014217517572700L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(231, 24014217517322700L, 24014217517322700L, 8,
                              104, 10000, 231, 10000, elem));

        elem = null;
        list.add(buildTrigger(520, 24014217544569900L, 24014217544569900L, -1,
                              -1, 10000, 520, 10000, elem));

        elem = null;
        list.add(buildTrigger(521, 24014217637186700L, 24014217637186700L, -1,
                              -1, 10000, 521, 10000, elem));

        elem = new ElemData(0, 24014217665977500L, 24014217666477500L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(515, 24014217666227500L, 24014217666227500L, 9,
                              104, 10000, 515, 10000, elem));

        elem = new ElemData(0, 24014217679347800L, 24014217679847800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(232, 24014217679597800L, 24014217679597800L, 8,
                              104, 10000, 232, 10000, elem));

        elem = new ElemData(0, 24014217742844700L, 24014217743344700L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(233, 24014217743094700L, 24014217743094700L, 8,
                              104, 10000, 233, 10000, elem));

        elem = new ElemData(0, 24014217780421500L, 24014217780921500L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(516, 24014217780671500L, 24014217780671500L, 9,
                              104, 10000, 516, 10000, elem));

        elem = null;
        list.add(buildTrigger(523, 24014217809471700L, 24014217809471700L, -1,
                              -1, 10000, 523, 10000, elem));

        elem = null;
        list.add(buildTrigger(525, 24014217829226900L, 24014217829226900L, -1,
                              -1, 10000, 525, 10000, elem));

        elem = null;
        list.add(buildTrigger(527, 24014217853926500L, 24014217853926500L, -1,
                              -1, 10000, 527, 10000, elem));

        elem = new ElemData(0, 24014217873321600L, 24014217873821600L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(234, 24014217873571600L, 24014217873571600L, 8,
                              104, 10000, 234, 10000, elem));

        elem = null;
        list.add(buildTrigger(529, 24014217876657600L, 24014217876657600L, -1,
                              -1, 10000, 529, 10000, elem));

        elem = null;
        list.add(buildTrigger(531, 24014217906314400L, 24014217906314400L, -1,
                              -1, 10000, 531, 10000, elem));

        elem = null;
        list.add(buildTrigger(532, 24014217910260700L, 24014217910260700L, -1,
                              -1, 10000, 532, 10000, elem));

        elem = null;
        list.add(buildTrigger(533, 24014218058570400L, 24014218058570400L, -1,
                              -1, 10000, 533, 10000, elem));

        elem = new ElemData(0, 24014218062449500L, 24014218062949500L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(235, 24014218062699500L, 24014218062699500L, 8,
                              104, 10000, 235, 10000, elem));

        elem = new ElemData(0, 24014218073140000L, 24014218073640000L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(236, 24014218073390000L, 24014218073390000L, 8,
                              104, 10000, 236, 10000, elem));

        elem = new ElemData(0, 24014218221695500L, 24014218222195500L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(524, 24014218221945500L, 24014218221945500L, 9,
                              104, 10000, 524, 10000, elem));

        elem = null;
        list.add(buildTrigger(535, 24014218247904700L, 24014218247904700L, -1,
                              -1, 10000, 535, 10000, elem));

        elem = null;
        list.add(buildTrigger(536, 24014218341275200L, 24014218341275200L, -1,
                              -1, 10000, 536, 10000, elem));

        elem = null;
        list.add(buildTrigger(537, 24014218498850600L, 24014218498850600L, -1,
                              -1, 10000, 537, 10000, elem));

        elem = null;
        list.add(buildTrigger(538, 24014218551287200L, 24014218551287200L, -1,
                              -1, 10000, 538, 10000, elem));

        elem = new ElemData(0, 24014218560429200L, 24014218560929200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(529, 24014218560679200L, 24014218560679200L, 9,
                              104, 10000, 529, 10000, elem));

        elem = null;
        list.add(buildTrigger(540, 24014218591335900L, 24014218591335900L, -1,
                              -1, 10000, 540, 10000, elem));

        elem = null;
        list.add(buildTrigger(542, 24014218711785100L, 24014218711785100L, -1,
                              -1, 10000, 542, 10000, elem));

        elem = null;
        list.add(buildTrigger(544, 24014218717005000L, 24014218717005000L, -1,
                              -1, 10000, 544, 10000, elem));

        elem = new ElemData(0, 24014218808663800L, 24014218809163800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(288, 24014218808913800L, 24014218808913800L, 11,
                              104, 10000, 288, 10000, elem));

        elem = null;
        list.add(buildTrigger(546, 24014218826019500L, 24014218826019500L, -1,
                              -1, 10000, 546, 10000, elem));

        elem = new ElemData(0, 24014218850599100L, 24014218851099100L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(534, 24014218850849100L, 24014218850849100L, 9,
                              104, 10000, 534, 10000, elem));

        elem = null;
        list.add(buildTrigger(547, 24014218869274600L, 24014218869274600L, -1,
                              -1, 10000, 547, 10000, elem));

        elem = new ElemData(0, 24014218912785100L, 24014218913285100L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(237, 24014218913035100L, 24014218913035100L, 8,
                              104, 10000, 237, 10000, elem));

        elem = new ElemData(0, 24014218937945200L, 24014218938445200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(290, 24014218938195200L, 24014218938195200L, 11,
                              104, 10000, 290, 10000, elem));

        elem = new ElemData(0, 24014218939309800L, 24014218939809800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(238, 24014218939559800L, 24014218939559800L, 8,
                              104, 10000, 238, 10000, elem));

        elem = null;
        list.add(buildTrigger(549, 24014218945551400L, 24014218945551400L, -1,
                              -1, 10000, 549, 10000, elem));

        elem = new ElemData(0, 24014218980795000L, 24014218981295000L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(239, 24014218981045000L, 24014218981045000L, 8,
                              104, 10000, 239, 10000, elem));

        elem = new ElemData(0, 24014218982845700L, 24014218983345700L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(537, 24014218983095700L, 24014218983095700L, 9,
                              104, 10000, 537, 10000, elem));

        elem = new ElemData(0, 24014218991577800L, 24014218992077800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(240, 24014218991827800L, 24014218991827800L, 8,
                              104, 10000, 240, 10000, elem));

        elem = null;
        list.add(buildTrigger(551, 24014219038273200L, 24014219038273200L, -1,
                              -1, 10000, 551, 10000, elem));

        elem = null;
        list.add(buildTrigger(552, 24014219137455000L, 24014219137455000L, -1,
                              -1, 10000, 552, 10000, elem));

        elem = null;
        list.add(buildTrigger(554, 24014219212681000L, 24014219212681000L, -1,
                              -1, 10000, 554, 10000, elem));

        elem = new ElemData(0, 24014219265026700L, 24014219265526700L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(241, 24014219265276700L, 24014219265276700L, 8,
                              104, 10000, 241, 10000, elem));

        elem = new ElemData(0, 24014219292355100L, 24014219292855100L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(541, 24014219292605100L, 24014219292605100L, 9,
                              104, 10000, 541, 10000, elem));

        elem = new ElemData(0, 24014219428855400L, 24014219429355400L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(542, 24014219429105400L, 24014219429105400L, 9,
                              104, 10000, 542, 10000, elem));

        elem = new ElemData(0, 24014219473778300L, 24014219474278300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(543, 24014219474028300L, 24014219474028300L, 9,
                              104, 10000, 543, 10000, elem));

        elem = new ElemData(0, 24014219518484300L, 24014219518984300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(295, 24014219518734300L, 24014219518734300L, 11,
                              104, 10000, 295, 10000, elem));

        elem = null;
        list.add(buildTrigger(556, 24014219531452100L, 24014219531452100L, -1,
                              -1, 10000, 556, 10000, elem));

        elem = new ElemData(0, 24014219543430400L, 24014219543930400L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(297, 24014219543680400L, 24014219543680400L, 11,
                              104, 10000, 297, 10000, elem));

        elem = null;
        list.add(buildTrigger(557, 24014219593826200L, 24014219593826200L, -1,
                              -1, 10000, 557, 10000, elem));

        elem = new ElemData(0, 24014219606232400L, 24014219606732400L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(546, 24014219606482400L, 24014219606482400L, 9,
                              104, 10000, 546, 10000, elem));

        elem = new ElemData(0, 24014219630689000L, 24014219631189000L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(547, 24014219630939000L, 24014219630939000L, 9,
                              104, 10000, 547, 10000, elem));

        elem = null;
        list.add(buildTrigger(559, 24014219635244500L, 24014219635244500L, -1,
                              -1, 10000, 559, 10000, elem));

        elem = null;
        list.add(buildTrigger(560, 24014219635407000L, 24014219635407000L, -1,
                              -1, 10000, 560, 10000, elem));

        elem = new ElemData(0, 24014219769275600L, 24014219769775600L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(300, 24014219769525600L, 24014219769525600L, 11,
                              104, 10000, 300, 10000, elem));

        elem = null;
        list.add(buildTrigger(561, 24014219776069700L, 24014219776069700L, -1,
                              -1, 10000, 561, 10000, elem));

        elem = new ElemData(0, 24014219789072600L, 24014219789572600L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(242, 24014219789322600L, 24014219789322600L, 8,
                              104, 10000, 242, 10000, elem));

        elem = null;
        list.add(buildTrigger(563, 24014219805064200L, 24014219805064200L, -1,
                              -1, 10000, 563, 10000, elem));

        elem = null;
        list.add(buildTrigger(564, 24014219873375400L, 24014219873375400L, -1,
                              -1, 10000, 564, 10000, elem));

        elem = new ElemData(0, 24014219922222500L, 24014219922722500L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(243, 24014219922472500L, 24014219922472500L, 8,
                              104, 10000, 243, 10000, elem));

        elem = null;
        list.add(buildTrigger(565, 24014219988120000L, 24014219988120000L, -1,
                              -1, 10000, 565, 10000, elem));

        elem = new ElemData(0, 24014219999781700L, 24014220000281700L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(4, 24014220000031700L, 24014220000031700L, 12,
                              104, 10000, 4, 10000, elem));

        elem = new ElemData(0, 24014220010076700L, 24014220010576700L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(244, 24014220010326700L, 24014220010326700L, 8,
                              104, 10000, 244, 10000, elem));

        elem = new ElemData(0, 24014220033282300L, 24014220033782300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(554, 24014220033532300L, 24014220033532300L, 9,
                              104, 10000, 554, 10000, elem));

        elem = null;
        list.add(buildTrigger(566, 24014220055214600L, 24014220055214600L, -1,
                              -1, 10000, 566, 10000, elem));

        elem = null;
        list.add(buildTrigger(568, 24014220086126400L, 24014220086126400L, -1,
                              -1, 10000, 568, 10000, elem));

        elem = null;
        list.add(buildTrigger(570, 24014220185994200L, 24014220185994200L, -1,
                              -1, 10000, 570, 10000, elem));

        elem = new ElemData(0, 24014220219461900L, 24014220219961900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(245, 24014220219711900L, 24014220219711900L, 8,
                              104, 10000, 245, 10000, elem));

        elem = new ElemData(0, 24014220264189500L, 24014220264689500L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(246, 24014220264439500L, 24014220264439500L, 8,
                              104, 10000, 246, 10000, elem));

        elem = new ElemData(0, 24014220271429400L, 24014220271929400L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(247, 24014220271679400L, 24014220271679400L, 8,
                              104, 10000, 247, 10000, elem));

        elem = null;
        list.add(buildTrigger(572, 24014220332150300L, 24014220332150300L, -1,
                              -1, 10000, 572, 10000, elem));

        elem = new ElemData(0, 24014220481909800L, 24014220482409800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(248, 24014220482159800L, 24014220482159800L, 8,
                              104, 10000, 248, 10000, elem));

        elem = new ElemData(0, 24014220493966300L, 24014220494466300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(559, 24014220494216300L, 24014220494216300L, 9,
                              104, 10000, 559, 10000, elem));

        elem = null;
        list.add(buildTrigger(573, 24014220536125500L, 24014220536125500L, -1,
                              -1, 10000, 573, 10000, elem));

        elem = null;
        list.add(buildTrigger(574, 24014220536441400L, 24014220536441400L, -1,
                              -1, 10000, 574, 10000, elem));

        elem = new ElemData(0, 24014220604165700L, 24014220604665700L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(249, 24014220604415700L, 24014220604415700L, 8,
                              104, 10000, 249, 10000, elem));

        elem = null;
        list.add(buildTrigger(575, 24014220841770200L, 24014220841770200L, -1,
                              -1, 10000, 575, 10000, elem));

        elem = new ElemData(0, 24014220864233600L, 24014220864733600L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(250, 24014220864483600L, 24014220864483600L, 8,
                              104, 10000, 250, 10000, elem));

        elem = new ElemData(0, 24014220891024300L, 24014220891524300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(251, 24014220891274300L, 24014220891274300L, 8,
                              104, 10000, 251, 10000, elem));

        elem = null;
        list.add(buildTrigger(577, 24014220950243700L, 24014220950243700L, -1,
                              -1, 10000, 577, 10000, elem));

        elem = new ElemData(0, 24014220970369600L, 24014220970869600L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(252, 24014220970619600L, 24014220970619600L, 8,
                              104, 10000, 252, 10000, elem));

        elem = null;
        list.add(buildTrigger(578, 24014220997590800L, 24014220997590800L, -1,
                              -1, 10000, 578, 10000, elem));

        elem = new ElemData(0, 24014221013839900L, 24014221014339900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(565, 24014221014089900L, 24014221014089900L, 9,
                              104, 10000, 565, 10000, elem));

        elem = new ElemData(0, 24014221023593300L, 24014221024093300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(566, 24014221023843300L, 24014221023843300L, 9,
                              104, 10000, 566, 10000, elem));

        elem = new ElemData(0, 24014221025135100L, 24014221025635100L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(567, 24014221025385100L, 24014221025385100L, 9,
                              104, 10000, 567, 10000, elem));

        elem = null;
        list.add(buildTrigger(579, 24014221070755300L, 24014221070755300L, -1,
                              -1, 10000, 579, 10000, elem));

        elem = null;
        list.add(buildTrigger(580, 24014221100660900L, 24014221100660900L, -1,
                              -1, 10000, 580, 10000, elem));

        elem = null;
        list.add(buildTrigger(582, 24014221129797200L, 24014221129797200L, -1,
                              -1, 10000, 582, 10000, elem));

        elem = new ElemData(0, 24014221167252200L, 24014221167752200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(253, 24014221167502200L, 24014221167502200L, 8,
                              104, 10000, 253, 10000, elem));

        elem = null;
        list.add(buildTrigger(583, 24014221219285800L, 24014221219285800L, -1,
                              -1, 10000, 583, 10000, elem));

        elem = new ElemData(0, 24014221235320400L, 24014221235820400L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(572, 24014221235570400L, 24014221235570400L, 9,
                              104, 10000, 572, 10000, elem));

        elem = new ElemData(0, 24014221268430900L, 24014221268930900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(573, 24014221268680900L, 24014221268680900L, 9,
                              104, 10000, 573, 10000, elem));

        elem = null;
        list.add(buildTrigger(585, 24014221319275100L, 24014221319275100L, -1,
                              -1, 10000, 585, 10000, elem));

        elem = new ElemData(0, 24014221327300800L, 24014221327800800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(575, 24014221327550800L, 24014221327550800L, 9,
                              104, 10000, 575, 10000, elem));

        elem = null;
        list.add(buildTrigger(586, 24014221378601300L, 24014221378601300L, -1,
                              -1, 10000, 586, 10000, elem));

        elem = null;
        list.add(buildTrigger(588, 24014221443892100L, 24014221443892100L, -1,
                              -1, 10000, 588, 10000, elem));

        elem = new ElemData(0, 24014221519894500L, 24014221520394500L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(254, 24014221520144500L, 24014221520144500L, 8,
                              104, 10000, 254, 10000, elem));

        elem = null;
        list.add(buildTrigger(590, 24014221564562800L, 24014221564562800L, -1,
                              -1, 10000, 590, 10000, elem));

        elem = new ElemData(0, 24014221580967300L, 24014221581467300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(255, 24014221581217300L, 24014221581217300L, 8,
                              104, 10000, 255, 10000, elem));

        elem = null;
        list.add(buildTrigger(591, 24014221591892300L, 24014221591892300L, -1,
                              -1, 10000, 591, 10000, elem));

        elem = new ElemData(0, 24014221828891300L, 24014221829391300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(256, 24014221829141300L, 24014221829141300L, 8,
                              104, 10000, 256, 10000, elem));

        elem = new ElemData(0, 24014221912807100L, 24014221913307100L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(257, 24014221913057100L, 24014221913057100L, 8,
                              104, 10000, 257, 10000, elem));

        elem = null;
        list.add(buildTrigger(593, 24014222105716500L, 24014222105716500L, -1,
                              -1, 10000, 593, 10000, elem));

        elem = null;
        list.add(buildTrigger(595, 24014222117467300L, 24014222117467300L, -1,
                              -1, 10000, 595, 10000, elem));

        elem = null;
        list.add(buildTrigger(597, 24014222180891400L, 24014222180891400L, -1,
                              -1, 10000, 597, 10000, elem));

        elem = new ElemData(0, 24014222236052400L, 24014222236552400L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(258, 24014222236302400L, 24014222236302400L, 8,
                              104, 10000, 258, 10000, elem));

        elem = null;
        list.add(buildTrigger(599, 24014222267554800L, 24014222267554800L, -1,
                              -1, 10000, 599, 10000, elem));

        elem = null;
        list.add(buildTrigger(601, 24014222303876300L, 24014222303876300L, -1,
                              -1, 10000, 601, 10000, elem));

        elem = new ElemData(0, 24014222491222900L, 24014222491722900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(319, 24014222491472900L, 24014222491472900L, 11,
                              104, 10000, 319, 10000, elem));

        elem = null;
        list.add(buildTrigger(602, 24014222493512900L, 24014222493512900L, -1,
                              -1, 10000, 602, 10000, elem));

        elem = null;
        list.add(buildTrigger(604, 24014222612528700L, 24014222612528700L, -1,
                              -1, 10000, 604, 10000, elem));

        elem = null;
        list.add(buildTrigger(605, 24014222661661800L, 24014222661661800L, -1,
                              -1, 10000, 605, 10000, elem));

        elem = null;
        list.add(buildTrigger(606, 24014222670927800L, 24014222670927800L, -1,
                              -1, 10000, 606, 10000, elem));

        elem = new ElemData(0, 24014222796981900L, 24014222797481900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(259, 24014222797231900L, 24014222797231900L, 8,
                              104, 10000, 259, 10000, elem));

        elem = new ElemData(0, 24014222798150800L, 24014222798650800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(260, 24014222798400800L, 24014222798400800L, 8,
                              104, 10000, 260, 10000, elem));

        elem = new ElemData(0, 24014222813025000L, 24014222813525000L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(589, 24014222813275000L, 24014222813275000L, 9,
                              104, 10000, 589, 10000, elem));

        elem = new ElemData(0, 24014222860605700L, 24014222861105700L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(590, 24014222860855700L, 24014222860855700L, 9,
                              104, 10000, 590, 10000, elem));

        elem = new ElemData(0, 24014222882854900L, 24014222883354900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(591, 24014222883104900L, 24014222883104900L, 9,
                              104, 10000, 591, 10000, elem));

        elem = null;
        list.add(buildTrigger(608, 24014222898819900L, 24014222898819900L, -1,
                              -1, 10000, 608, 10000, elem));

        elem = new ElemData(0, 24014222961193000L, 24014222961693000L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(261, 24014222961443000L, 24014222961443000L, 8,
                              104, 10000, 261, 10000, elem));

        elem = null;
        list.add(buildTrigger(610, 24014222979447000L, 24014222979447000L, -1,
                              -1, 10000, 610, 10000, elem));

        elem = new ElemData(0, 24014222984708400L, 24014222985208400L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(262, 24014222984958400L, 24014222984958400L, 8,
                              104, 10000, 262, 10000, elem));

        elem = null;
        list.add(buildTrigger(611, 24014223063162900L, 24014223063162900L, -1,
                              -1, 10000, 611, 10000, elem));

        elem = new ElemData(0, 24014223117121300L, 24014223117621300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(595, 24014223117371300L, 24014223117371300L, 9,
                              104, 10000, 595, 10000, elem));

        elem = new ElemData(0, 24014223145724600L, 24014223146224600L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(596, 24014223145974600L, 24014223145974600L, 9,
                              104, 10000, 596, 10000, elem));

        elem = null;
        list.add(buildTrigger(613, 24014223151273300L, 24014223151273300L, -1,
                              -1, 10000, 613, 10000, elem));

        elem = new ElemData(0, 24014223215448900L, 24014223215948900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(325, 24014223215698900L, 24014223215698900L, 11,
                              104, 10000, 325, 10000, elem));

        elem = null;
        list.add(buildTrigger(615, 24014223376656400L, 24014223376656400L, -1,
                              -1, 10000, 615, 10000, elem));

        elem = null;
        list.add(buildTrigger(617, 24014223417289200L, 24014223417289200L, -1,
                              -1, 10000, 617, 10000, elem));

        elem = new ElemData(0, 24014223432859100L, 24014223433359100L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(600, 24014223433109100L, 24014223433109100L, 9,
                              104, 10000, 600, 10000, elem));

        elem = null;
        list.add(buildTrigger(618, 24014223556474600L, 24014223556474600L, -1,
                              -1, 10000, 618, 10000, elem));

        elem = null;
        list.add(buildTrigger(619, 24014223580168500L, 24014223580168500L, -1,
                              -1, 10000, 619, 10000, elem));

        elem = new ElemData(0, 24014223596630400L, 24014223597130400L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(328, 24014223596880400L, 24014223596880400L, 11,
                              104, 10000, 328, 10000, elem));

        elem = null;
        list.add(buildTrigger(620, 24014223722322700L, 24014223722322700L, -1,
                              -1, 10000, 620, 10000, elem));

        elem = new ElemData(0, 24014223835143900L, 24014223835643900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(604, 24014223835393900L, 24014223835393900L, 9,
                              104, 10000, 604, 10000, elem));

        elem = null;
        list.add(buildTrigger(622, 24014223881621500L, 24014223881621500L, -1,
                              -1, 10000, 622, 10000, elem));

        elem = null;
        list.add(buildTrigger(624, 24014223885457200L, 24014223885457200L, -1,
                              -1, 10000, 624, 10000, elem));

        elem = new ElemData(0, 24014223905687700L, 24014223906187700L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(263, 24014223905937700L, 24014223905937700L, 8,
                              104, 10000, 263, 10000, elem));

        elem = null;
        list.add(buildTrigger(625, 24014223911275800L, 24014223911275800L, -1,
                              -1, 10000, 625, 10000, elem));

        elem = new ElemData(0, 24014223928289300L, 24014223928789300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(264, 24014223928539300L, 24014223928539300L, 8,
                              104, 10000, 264, 10000, elem));

        elem = new ElemData(0, 24014223998038800L, 24014223998538800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(265, 24014223998288800L, 24014223998288800L, 8,
                              104, 10000, 265, 10000, elem));

        elem = new ElemData(0, 24014224041612800L, 24014224042112800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(266, 24014224041862800L, 24014224041862800L, 8,
                              104, 10000, 266, 10000, elem));

        elem = null;
        list.add(buildTrigger(626, 24014224063654700L, 24014224063654700L, -1,
                              -1, 10000, 626, 10000, elem));

        elem = null;
        list.add(buildTrigger(628, 24014224096530500L, 24014224096530500L, -1,
                              -1, 10000, 628, 10000, elem));

        elem = new ElemData(0, 24014224124227700L, 24014224124727700L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(267, 24014224124477700L, 24014224124477700L, 8,
                              104, 10000, 267, 10000, elem));

        elem = null;
        list.add(buildTrigger(630, 24014224124838900L, 24014224124838900L, -1,
                              -1, 10000, 630, 10000, elem));

        elem = new ElemData(0, 24014224125972000L, 24014224126472000L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(611, 24014224126222000L, 24014224126222000L, 9,
                              104, 10000, 611, 10000, elem));

        elem = new ElemData(0, 24014224273747500L, 24014224274247500L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(268, 24014224273997500L, 24014224273997500L, 8,
                              104, 10000, 268, 10000, elem));

        elem = new ElemData(0, 24014224296017500L, 24014224296517500L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(333, 24014224296267500L, 24014224296267500L, 11,
                              104, 10000, 333, 10000, elem));

        elem = new ElemData(0, 24014224337432000L, 24014224337932000L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(269, 24014224337682000L, 24014224337682000L, 8,
                              104, 10000, 269, 10000, elem));

        elem = new ElemData(0, 24014224374967500L, 24014224375467500L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(612, 24014224375217500L, 24014224375217500L, 9,
                              104, 10000, 612, 10000, elem));

        elem = new ElemData(0, 24014224396124400L, 24014224396624400L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(334, 24014224396374400L, 24014224396374400L, 11,
                              104, 10000, 334, 10000, elem));

        elem = null;
        list.add(buildTrigger(632, 24014224403127700L, 24014224403127700L, -1,
                              -1, 10000, 632, 10000, elem));

        elem = null;
        list.add(buildTrigger(634, 24014224485091500L, 24014224485091500L, -1,
                              -1, 10000, 634, 10000, elem));

        elem = new ElemData(0, 24014224517846500L, 24014224518346500L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(270, 24014224518096500L, 24014224518096500L, 8,
                              104, 10000, 270, 10000, elem));

        elem = new ElemData(0, 24014224529644100L, 24014224530144100L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(615, 24014224529894100L, 24014224529894100L, 9,
                              104, 10000, 615, 10000, elem));

        elem = new ElemData(0, 24014224628891700L, 24014224629391700L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(616, 24014224629141700L, 24014224629141700L, 9,
                              104, 10000, 616, 10000, elem));

        elem = new ElemData(0, 24014224635279900L, 24014224635779900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(271, 24014224635529900L, 24014224635529900L, 8,
                              104, 10000, 271, 10000, elem));

        elem = new ElemData(0, 24014224642999800L, 24014224643499800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(272, 24014224643249800L, 24014224643249800L, 8,
                              104, 10000, 272, 10000, elem));

        elem = new ElemData(0, 24014224653243600L, 24014224653743600L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(273, 24014224653493600L, 24014224653493600L, 8,
                              104, 10000, 273, 10000, elem));

        elem = new ElemData(0, 24014224658949200L, 24014224659449200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(617, 24014224659199200L, 24014224659199200L, 9,
                              104, 10000, 617, 10000, elem));

        elem = null;
        list.add(buildTrigger(635, 24014224711323900L, 24014224711323900L, -1,
                              -1, 10000, 635, 10000, elem));

        elem = new ElemData(0, 24014224809585700L, 24014224810085700L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(337, 24014224809835700L, 24014224809835700L, 11,
                              104, 10000, 337, 10000, elem));

        elem = new ElemData(0, 24014224813820800L, 24014224814320800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(274, 24014224814070800L, 24014224814070800L, 8,
                              104, 10000, 274, 10000, elem));

        elem = null;
        list.add(buildTrigger(636, 24014224886555500L, 24014224886555500L, -1,
                              -1, 10000, 636, 10000, elem));

        elem = null;
        list.add(buildTrigger(638, 24014224909257500L, 24014224909257500L, -1,
                              -1, 10000, 638, 10000, elem));

        elem = null;
        list.add(buildTrigger(640, 24014225056066000L, 24014225056066000L, -1,
                              -1, 10000, 640, 10000, elem));

        elem = null;
        list.add(buildTrigger(641, 24014225077902700L, 24014225077902700L, -1,
                              -1, 10000, 641, 10000, elem));

        elem = null;
        list.add(buildTrigger(643, 24014225110448600L, 24014225110448600L, -1,
                              -1, 10000, 643, 10000, elem));

        elem = null;
        list.add(buildTrigger(645, 24014225133132200L, 24014225133132200L, -1,
                              -1, 10000, 645, 10000, elem));

        elem = new ElemData(0, 24014225201730100L, 24014225202230100L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(275, 24014225201980100L, 24014225201980100L, 8,
                              104, 10000, 275, 10000, elem));

        elem = new ElemData(0, 24014225289094400L, 24014225289594400L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(625, 24014225289344400L, 24014225289344400L, 9,
                              104, 10000, 625, 10000, elem));

        elem = new ElemData(0, 24014225309527100L, 24014225310027100L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(276, 24014225309777100L, 24014225309777100L, 8,
                              104, 10000, 276, 10000, elem));

        elem = new ElemData(0, 24014225314049900L, 24014225314549900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(626, 24014225314299900L, 24014225314299900L, 9,
                              104, 10000, 626, 10000, elem));

        elem = null;
        list.add(buildTrigger(647, 24014225344587400L, 24014225344587400L, -1,
                              -1, 10000, 647, 10000, elem));

        elem = null;
        list.add(buildTrigger(648, 24014225347387000L, 24014225347387000L, -1,
                              -1, 10000, 648, 10000, elem));

        elem = new ElemData(0, 24014225378818500L, 24014225379318500L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(277, 24014225379068500L, 24014225379068500L, 8,
                              104, 10000, 277, 10000, elem));

        elem = null;
        list.add(buildTrigger(650, 24014225468728600L, 24014225468728600L, -1,
                              -1, 10000, 650, 10000, elem));

        elem = null;
        list.add(buildTrigger(651, 24014225493362000L, 24014225493362000L, -1,
                              -1, 10000, 651, 10000, elem));

        elem = null;
        list.add(buildTrigger(653, 24014225510683000L, 24014225510683000L, -1,
                              -1, 10000, 653, 10000, elem));

        elem = new ElemData(0, 24014225536057900L, 24014225536557900L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(632, 24014225536307900L, 24014225536307900L, 9,
                              104, 10000, 632, 10000, elem));

        elem = null;
        list.add(buildTrigger(654, 24014225589714600L, 24014225589714600L, -1,
                              -1, 10000, 654, 10000, elem));

        elem = null;
        list.add(buildTrigger(656, 24014225602190000L, 24014225602190000L, -1,
                              -1, 10000, 656, 10000, elem));

        elem = new ElemData(0, 24014225610124800L, 24014225610624800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(278, 24014225610374800L, 24014225610374800L, 8,
                              104, 10000, 278, 10000, elem));

        elem = null;
        list.add(buildTrigger(657, 24014225620961600L, 24014225620961600L, -1,
                              -1, 10000, 657, 10000, elem));

        elem = new ElemData(0, 24014225745336300L, 24014225745836300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(279, 24014225745586300L, 24014225745586300L, 8,
                              104, 10000, 279, 10000, elem));

        elem = null;
        list.add(buildTrigger(659, 24014225769818500L, 24014225769818500L, -1,
                              -1, 10000, 659, 10000, elem));

        elem = new ElemData(0, 24014225831799600L, 24014225832299600L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(280, 24014225832049600L, 24014225832049600L, 8,
                              104, 10000, 280, 10000, elem));

        elem = new ElemData(0, 24014225838777200L, 24014225839277200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(281, 24014225839027200L, 24014225839027200L, 8,
                              104, 10000, 281, 10000, elem));

        elem = new ElemData(0, 24014225910782300L, 24014225911282300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(637, 24014225911032300L, 24014225911032300L, 9,
                              104, 10000, 637, 10000, elem));

        elem = new ElemData(0, 24014225939710400L, 24014225940210400L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(282, 24014225939960400L, 24014225939960400L, 8,
                              104, 10000, 282, 10000, elem));

        elem = new ElemData(0, 24014226055276800L, 24014226055776800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(283, 24014226055526800L, 24014226055526800L, 8,
                              104, 10000, 283, 10000, elem));

        elem = null;
        list.add(buildTrigger(661, 24014226080871900L, 24014226080871900L, -1,
                              -1, 10000, 661, 10000, elem));

        elem = new ElemData(0, 24014226121403800L, 24014226121903800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(284, 24014226121653800L, 24014226121653800L, 8,
                              104, 10000, 284, 10000, elem));

        elem = null;
        list.add(buildTrigger(663, 24014226208364100L, 24014226208364100L, -1,
                              -1, 10000, 663, 10000, elem));

        elem = new ElemData(0, 24014226225167200L, 24014226225667200L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(285, 24014226225417200L, 24014226225417200L, 8,
                              104, 10000, 285, 10000, elem));

        elem = new ElemData(0, 24014226227135300L, 24014226227635300L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(640, 24014226227385300L, 24014226227385300L, 9,
                              104, 10000, 640, 10000, elem));

        elem = new ElemData(0, 24014226232088600L, 24014226232588600L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(641, 24014226232338600L, 24014226232338600L, 9,
                              104, 10000, 641, 10000, elem));

        elem = null;
        list.add(buildTrigger(664, 24014226310622500L, 24014226310622500L, -1,
                              -1, 10000, 664, 10000, elem));

        elem = new ElemData(0, 24014226321016600L, 24014226321516600L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(643, 24014226321266600L, 24014226321266600L, 9,
                              104, 10000, 643, 10000, elem));

        elem = new ElemData(0, 24014226394064400L, 24014226394564400L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(644, 24014226394314400L, 24014226394314400L, 9,
                              104, 10000, 644, 10000, elem));

        elem = null;
        list.add(buildTrigger(666, 24014226446214300L, 24014226446214300L, -1,
                              -1, 10000, 666, 10000, elem));

        elem = new ElemData(0, 24014226505798800L, 24014226506298800L,
                            0xffffffffffffffffL, -1);
        list.add(buildTrigger(646, 24014226506048800L, 24014226506048800L, 9,
                              104, 10000, 646, 10000, elem));

        return list;
    }

    protected void setUp()
        throws Exception
    {
        super.setUp();

        appender.clear();

        BasicConfigurator.resetConfiguration();
        BasicConfigurator.configure(appender);
    }

    public static Test suite()
    {
        return new TestSuite(GlobalTriggerEndToEndTest.class);
    }

    protected void tearDown()
        throws Exception
    {
        assertEquals("Bad number of log messages",
                     0, appender.getNumberOfMessages());

        super.tearDown();
    }

    public void testEndToEnd()
        throws DAQCompException, DataFormatException, IOException,
               SplicerException, TriggerException
    {
        final int numTails = 1;

        File cfgFile =
            DAQTestUtil.buildConfigFile(getClass().getResource("/").getPath(),
                                        "sps-icecube-amanda-008");

        // set up global trigger
        GlobalTriggerComponent comp = new GlobalTriggerComponent();
        comp.setGlobalConfigurationDir(cfgFile.getParent());
        comp.start(false);

        comp.configuring(cfgFile.getName());

        WritableByteChannel[] tails =
            DAQTestUtil.connectToReader(comp.getReader(), comp.getCache(),
                                        numTails);

        DAQTestUtil.connectToSink("gtOut", comp.getWriter(), comp.getCache(),
                                  new TriggerValidator());

        DAQTestUtil.startIOProcess(comp.getReader());
        DAQTestUtil.startIOProcess(comp.getWriter());

        for (ByteBuffer bb : getTriggerList()) {
            bb.position(0);
            tails[0].write(bb);
        }

        for (int i = 0; i < tails.length; i++) {
            DAQTestUtil.sendStopMsg(tails[i]);
        }

        DAQTestUtil.waitUntilStopped(comp.getReader(), comp.getSplicer(),
                                     "GTStopMsg");
        DAQTestUtil.waitUntilStopped(comp.getWriter(), null, "GTStopMsg");

        comp.flush();

        try {
            Thread.sleep(1000);
        } catch (InterruptedException ie) {
            // ignore interrupts
        }

        assertEquals("Unexpected number of global triggers",
                     565, comp.getPayloadsSent() - 1);

        if (appender.getLevel().equals(org.apache.log4j.Level.ALL)) {
            appender.clear();
        } else {
            checkLogMessages();
        }
    }

    public static void main(String[] args)
    {
        TestRunner.run(suite());
    }
}
