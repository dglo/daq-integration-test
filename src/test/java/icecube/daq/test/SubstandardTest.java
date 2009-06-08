package icecube.daq.test;

import icecube.daq.io.PayloadReader;
import icecube.daq.io.SpliceablePayloadReader;
import icecube.daq.juggler.component.DAQCompException;
import icecube.daq.payload.IByteBufferCache;
import icecube.daq.payload.IUTCTime;
import icecube.daq.payload.IWriteablePayload;
import icecube.daq.payload.PayloadChecker;
import icecube.daq.payload.PayloadRegistry;
import icecube.daq.payload.RecordTypeRegistry;
import icecube.daq.payload.SourceIdRegistry;
import icecube.daq.payload.VitreousBufferCache;
import icecube.daq.splicer.HKN1Splicer;
import icecube.daq.splicer.SplicerException;
import icecube.daq.splicer.StrandTail;
import icecube.daq.trigger.IReadoutRequestElement;
import icecube.daq.trigger.ITriggerRequestPayload;
import icecube.daq.trigger.component.AmandaTriggerComponent;
import icecube.daq.trigger.component.IniceTriggerComponent;
import icecube.daq.trigger.component.GlobalTriggerComponent;
import icecube.daq.trigger.config.TriggerReadout;
import icecube.daq.trigger.control.GlobalTriggerManager;
import icecube.daq.trigger.control.TriggerManager;
import icecube.daq.trigger.exceptions.TriggerException;
import icecube.daq.trigger.impl.TriggerRequestPayloadFactory;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.nio.channels.Selector;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DataFormatException;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.log4j.BasicConfigurator;

public class SubstandardTest
    extends TestCase
{
    private static final MockAppender appender =
        new MockAppender(/*org.apache.log4j.Level.ALL*/)/*.setVerbose(true)*/;

    private static final MockSourceID AMANDA_TRIGGER_SOURCE_ID =
        new MockSourceID(SourceIdRegistry.AMANDA_TRIGGER_SOURCE_ID);
    private static final MockSourceID INICE_TRIGGER_SOURCE_ID =
        new MockSourceID(SourceIdRegistry.INICE_TRIGGER_SOURCE_ID);

    public SubstandardTest(String name)
    {
        super(name);
    }

    private static ByteBuffer buildHit(long time, int recType, int cfgId,
                                       int srcId, long domId, int mode)
        throws DataFormatException, IOException
    {
        final int bufLen = 38;

        ByteBuffer hitBuf = ByteBuffer.allocate(bufLen);

        hitBuf.putInt(0, bufLen);
        hitBuf.putInt(4, PayloadRegistry.PAYLOAD_ID_SIMPLE_HIT);
        hitBuf.putLong(8, time);

        hitBuf.putInt(16, recType);
        hitBuf.putInt(20, cfgId);
        hitBuf.putInt(24, srcId);
        hitBuf.putLong(28, domId);
        hitBuf.putShort(36, (short) mode);

        hitBuf.position(0);

        return hitBuf;
    }

    private static ByteBuffer buildTrigger(int uid, long firstTime,
                                           long lastTime, int trigType,
                                           int cfgId, int srcId, int rrUID,
                                           int rrSrcId)
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

    private static ArrayList<ByteBuffer> getAmandaTriggers()
        throws DataFormatException, IOException
    {
        ArrayList<ByteBuffer> list =
            new ArrayList<ByteBuffer>();

        list.add(buildTrigger(1, 24014178845056200L, 24014178845056215L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(2, 24014178864269600L, 24014178864269615L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(3, 24014178945613900L, 24014178945613915L, 0,
                              12, 10000, 0, 10000));

        list.add(buildTrigger(4, 24014179030393700L, 24014179030393715L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(5, 24014179180300900L, 24014179180300915L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(6, 24014179226750200L, 24014179226750215L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(7, 24014179229654800L, 24014179229654815L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(8, 24014179286915500L, 24014179286915515L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(9, 24014179287380100L, 24014179287380115L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(10, 24014179296524400L, 24014179296524415L, 0,
                              12, 10000, 0, 10000));

        list.add(buildTrigger(11, 24014179306071600L, 24014179306071615L, 0,
                              12, 10000, 0, 10000));

        list.add(buildTrigger(12, 24014179350095500L, 24014179350095515L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(13, 24014179377939200L, 24014179377939215L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(14, 24014179487071000L, 24014179487071015L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(15, 24014179492237100L, 24014179492237115L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(16, 24014179658034600L, 24014179658034615L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(17, 24014179658550400L, 24014179658550415L, 0,
                              16, 10000, 0, 10000));

        list.add(buildTrigger(18, 24014179665158100L, 24014179665158115L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(19, 24014179716994100L, 24014179716994115L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(20, 24014179768227200L, 24014179768227215L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(21, 24014179804967000L, 24014179804967015L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(22, 24014179817276700L, 24014179817276715L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(23, 24014179948225000L, 24014179948225015L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(24, 24014180000000700L, 24014180000000715L, 0,
                              64, 10000, 0, 10000));

        list.add(buildTrigger(25, 24014180058294000L, 24014180058294015L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(26, 24014180076701000L, 24014180076701015L, 0,
                              20, 10000, 0, 10000));

        list.add(buildTrigger(27, 24014180090670700L, 24014180090670715L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(28, 24014180303544700L, 24014180303544715L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(29, 24014180380339300L, 24014180380339315L, 0,
                              16, 10000, 0, 10000));

        list.add(buildTrigger(30, 24014180389416900L, 24014180389416915L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(31, 24014180454355400L, 24014180454355415L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(32, 24014180462933300L, 24014180462933315L, 0,
                              20, 10000, 0, 10000));

        list.add(buildTrigger(33, 24014180523165100L, 24014180523165115L, 0,
                              12, 10000, 0, 10000));

        list.add(buildTrigger(34, 24014180578363200L, 24014180578363215L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(35, 24014180587423600L, 24014180587423615L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(36, 24014180632729000L, 24014180632729015L, 0,
                              20, 10000, 0, 10000));

        list.add(buildTrigger(37, 24014180684788800L, 24014180684788815L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(38, 24014180730273300L, 24014180730273315L, 0,
                              20, 10000, 0, 10000));

        list.add(buildTrigger(39, 24014180775799600L, 24014180775799615L, 0,
                              12, 10000, 0, 10000));

        list.add(buildTrigger(40, 24014180807608000L, 24014180807608015L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(41, 24014180829746400L, 24014180829746415L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(42, 24014180856020900L, 24014180856020915L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(43, 24014180858254800L, 24014180858254815L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(44, 24014180877030700L, 24014180877030715L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(45, 24014180886663400L, 24014180886663415L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(46, 24014180887988900L, 24014180887988915L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(47, 24014180920196500L, 24014180920196515L, 0,
                              16, 10000, 0, 10000));

        list.add(buildTrigger(48, 24014181061460800L, 24014181061460815L, 0,
                              12, 10000, 0, 10000));

        list.add(buildTrigger(49, 24014181105964400L, 24014181105964415L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(50, 24014181152171000L, 24014181152171015L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(51, 24014181155746600L, 24014181155746615L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(52, 24014181179858700L, 24014181179858715L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(53, 24014181210738100L, 24014181210738115L, 0,
                              12, 10000, 0, 10000));

        list.add(buildTrigger(54, 24014181294724200L, 24014181294724215L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(55, 24014181363709800L, 24014181363709815L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(56, 24014181523384600L, 24014181523384615L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(57, 24014181539504900L, 24014181539504915L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(58, 24014181550805700L, 24014181550805715L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(59, 24014181554201600L, 24014181554201615L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(60, 24014181563569200L, 24014181563569215L, 0,
                              16, 10000, 0, 10000));

        list.add(buildTrigger(61, 24014181595306800L, 24014181595306815L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(62, 24014181618433600L, 24014181618433615L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(63, 24014181651969800L, 24014181651969815L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(64, 24014181683585200L, 24014181683585215L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(65, 24014181725281800L, 24014181725281815L, 0,
                              12, 10000, 0, 10000));

        list.add(buildTrigger(66, 24014181726612700L, 24014181726612715L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(67, 24014181793025200L, 24014181793025215L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(68, 24014181824187900L, 24014181824187915L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(69, 24014181858432400L, 24014181858432415L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(70, 24014181873993400L, 24014181873993415L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(71, 24014181874279000L, 24014181874279015L, 0,
                              20, 10000, 0, 10000));

        list.add(buildTrigger(72, 24014181884807600L, 24014181884807615L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(73, 24014181932939900L, 24014181932939915L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(74, 24014181970699500L, 24014181970699515L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(75, 24014181977793800L, 24014181977793815L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(76, 24014182039508600L, 24014182039508615L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(77, 24014182045540500L, 24014182045540515L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(78, 24014182068439500L, 24014182068439515L, 0,
                              16, 10000, 0, 10000));

        list.add(buildTrigger(79, 24014182070481800L, 24014182070481815L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(80, 24014182278330600L, 24014182278330615L, 0,
                              16, 10000, 0, 10000));

        list.add(buildTrigger(81, 24014182303744100L, 24014182303744115L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(82, 24014182311986100L, 24014182311986115L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(83, 24014182320065200L, 24014182320065215L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(84, 24014182381403400L, 24014182381403415L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(85, 24014182432570100L, 24014182432570115L, 0,
                              12, 10000, 0, 10000));

        list.add(buildTrigger(86, 24014182494879300L, 24014182494879315L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(87, 24014182588665600L, 24014182588665615L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(88, 24014182643395000L, 24014182643395015L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(89, 24014182776662300L, 24014182776662315L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(90, 24014182786322200L, 24014182786322215L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(91, 24014182802617900L, 24014182802617915L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(92, 24014182825082900L, 24014182825082915L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(93, 24014182870969500L, 24014182870969515L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(94, 24014182977777800L, 24014182977777815L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(95, 24014183075094100L, 24014183075094115L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(96, 24014183152179800L, 24014183152179815L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(97, 24014183271224200L, 24014183271224215L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(98, 24014183276480200L, 24014183276480215L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(99, 24014183303247500L, 24014183303247515L, 0,
                              12, 10000, 0, 10000));

        list.add(buildTrigger(100, 24014183320105700L, 24014183320105715L, 0,
                              12, 10000, 0, 10000));

        list.add(buildTrigger(101, 24014183410993800L, 24014183410993815L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(102, 24014183619004900L, 24014183619004915L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(103, 24014183621452200L, 24014183621452215L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(104, 24014183624192100L, 24014183624192115L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(105, 24014183625191300L, 24014183625191315L, 0,
                              1, 10000, 0, 10000));

        list.add(buildTrigger(106, 24014183646077700L, 24014183646077715L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(107, 24014183736641000L, 24014183736641015L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(108, 24014183772034500L, 24014183772034515L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(109, 24014183835076100L, 24014183835076115L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(110, 24014183886956300L, 24014183886956315L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(111, 24014183941778700L, 24014183941778715L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(112, 24014183943493400L, 24014183943493415L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(113, 24014183978791300L, 24014183978791315L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(114, 24014184012946700L, 24014184012946715L, 0,
                              12, 10000, 0, 10000));

        list.add(buildTrigger(115, 24014184127326600L, 24014184127326615L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(116, 24014184332807400L, 24014184332807415L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(117, 24014184373225000L, 24014184373225015L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(118, 24014184417795900L, 24014184417795915L, 0,
                              12, 10000, 0, 10000));

        list.add(buildTrigger(119, 24014184452370800L, 24014184452370815L, 0,
                              12, 10000, 0, 10000));

        list.add(buildTrigger(120, 24014184492043000L, 24014184492043015L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(121, 24014184501267600L, 24014184501267615L, 0,
                              12, 10000, 0, 10000));

        list.add(buildTrigger(122, 24014184537349300L, 24014184537349315L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(123, 24014184539599700L, 24014184539599715L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(124, 24014184558760600L, 24014184558760615L, 0,
                              20, 10000, 0, 10000));

        list.add(buildTrigger(125, 24014184606728800L, 24014184606728815L, 0,
                              12, 10000, 0, 10000));

        list.add(buildTrigger(126, 24014184634578600L, 24014184634578615L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(127, 24014184685593400L, 24014184685593415L, 0,
                              12, 10000, 0, 10000));

        list.add(buildTrigger(128, 24014184703634700L, 24014184703634715L, 0,
                              12, 10000, 0, 10000));

        list.add(buildTrigger(129, 24014184764145900L, 24014184764145915L, 0,
                              12, 10000, 0, 10000));

        list.add(buildTrigger(130, 24014184808673400L, 24014184808673415L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(131, 24014184820042300L, 24014184820042315L, 0,
                              12, 10000, 0, 10000));

        list.add(buildTrigger(132, 24014184854411300L, 24014184854411315L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(133, 24014184874327400L, 24014184874327415L, 0,
                              16, 10000, 0, 10000));

        list.add(buildTrigger(134, 24014184883638500L, 24014184883638515L, 0,
                              12, 10000, 0, 10000));

        list.add(buildTrigger(135, 24014184898984200L, 24014184898984215L, 0,
                              20, 10000, 0, 10000));

        list.add(buildTrigger(136, 24014184929064100L, 24014184929064115L, 0,
                              12, 10000, 0, 10000));

        list.add(buildTrigger(137, 24014184986350400L, 24014184986350415L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(138, 24014185020206800L, 24014185020206815L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(139, 24014185155731800L, 24014185155731815L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(140, 24014185201424600L, 24014185201424615L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(141, 24014185208867400L, 24014185208867415L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(142, 24014185282295100L, 24014185282295115L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(143, 24014185297919600L, 24014185297919615L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(144, 24014185309921400L, 24014185309921415L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(145, 24014185396805500L, 24014185396805515L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(146, 24014185412186100L, 24014185412186115L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(147, 24014185423944700L, 24014185423944715L, 0,
                              16, 10000, 0, 10000));

        list.add(buildTrigger(148, 24014185442379700L, 24014185442379715L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(149, 24014185520389500L, 24014185520389515L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(150, 24014185698259000L, 24014185698259015L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(151, 24014185699318600L, 24014185699318615L, 0,
                              20, 10000, 0, 10000));

        list.add(buildTrigger(152, 24014185804177700L, 24014185804177715L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(153, 24014185832288700L, 24014185832288715L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(154, 24014185875252500L, 24014185875252515L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(155, 24014185882054000L, 24014185882054015L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(156, 24014185890126900L, 24014185890126915L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(157, 24014185948913500L, 24014185948913515L, 0,
                              12, 10000, 0, 10000));

        list.add(buildTrigger(158, 24014185961459300L, 24014185961459315L, 0,
                              16, 10000, 0, 10000));

        list.add(buildTrigger(159, 24014185981740200L, 24014185981740215L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(160, 24014185983321200L, 24014185983321215L, 0,
                              12, 10000, 0, 10000));

        list.add(buildTrigger(161, 24014186071357400L, 24014186071357415L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(162, 24014186084541800L, 24014186084541815L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(163, 24014186125727300L, 24014186125727315L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(164, 24014186143867900L, 24014186143867915L, 0,
                              12, 10000, 0, 10000));

        list.add(buildTrigger(165, 24014186151288700L, 24014186151288715L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(166, 24014186347904000L, 24014186347904015L, 0,
                              16, 10000, 0, 10000));

        list.add(buildTrigger(167, 24014186366887300L, 24014186366887315L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(168, 24014186397401000L, 24014186397401015L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(169, 24014186411085900L, 24014186411085915L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(170, 24014186486601200L, 24014186486601215L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(171, 24014186490068200L, 24014186490068215L, 0,
                              20, 10000, 0, 10000));

        list.add(buildTrigger(172, 24014186511436900L, 24014186511436915L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(173, 24014186573595300L, 24014186573595315L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(174, 24014186664941100L, 24014186664941115L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(175, 24014186733174800L, 24014186733174815L, 0,
                              16, 10000, 0, 10000));

        list.add(buildTrigger(176, 24014186829383000L, 24014186829383015L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(177, 24014186835070500L, 24014186835070515L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(178, 24014186926118800L, 24014186926118815L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(179, 24014186932764600L, 24014186932764615L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(180, 24014186955896800L, 24014186955896815L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(181, 24014186969327400L, 24014186969327415L, 0,
                              20, 10000, 0, 10000));

        list.add(buildTrigger(182, 24014187004068300L, 24014187004068315L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(183, 24014187073711900L, 24014187073711915L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(184, 24014187099171800L, 24014187099171815L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(185, 24014187105010800L, 24014187105010815L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(186, 24014187135654500L, 24014187135654515L, 0,
                              16, 10000, 0, 10000));

        list.add(buildTrigger(187, 24014187163127100L, 24014187163127115L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(188, 24014187239002100L, 24014187239002115L, 0,
                              12, 10000, 0, 10000));

        list.add(buildTrigger(189, 24014187240492200L, 24014187240492215L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(190, 24014187346612400L, 24014187346612415L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(191, 24014187370827300L, 24014187370827315L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(192, 24014187397495600L, 24014187397495615L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(193, 24014187406412300L, 24014187406412315L, 0,
                              16, 10000, 0, 10000));

        list.add(buildTrigger(194, 24014187406666700L, 24014187406666715L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(195, 24014187407463600L, 24014187407463615L, 0,
                              12, 10000, 0, 10000));

        list.add(buildTrigger(196, 24014187427343700L, 24014187427343715L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(197, 24014187435157400L, 24014187435157415L, 0,
                              16, 10000, 0, 10000));

        list.add(buildTrigger(198, 24014187729129100L, 24014187729129115L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(199, 24014187741577500L, 24014187741577515L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(200, 24014187770365700L, 24014187770365715L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(201, 24014187790382800L, 24014187790382815L, 0,
                              12, 10000, 0, 10000));

        list.add(buildTrigger(202, 24014187806667000L, 24014187806667015L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(203, 24014187853490200L, 24014187853490215L, 0,
                              12, 10000, 0, 10000));

        list.add(buildTrigger(204, 24014188005700000L, 24014188005700015L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(205, 24014188028093800L, 24014188028093815L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(206, 24014188130675800L, 24014188130675815L, 0,
                              20, 10000, 0, 10000));

        list.add(buildTrigger(207, 24014188156044900L, 24014188156044915L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(208, 24014188157491600L, 24014188157491615L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(209, 24014188199903900L, 24014188199903915L, 0,
                              12, 10000, 0, 10000));

        list.add(buildTrigger(210, 24014188263780000L, 24014188263780015L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(211, 24014188284146100L, 24014188284146115L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(212, 24014188304126300L, 24014188304126315L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(213, 24014188319549800L, 24014188319549815L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(214, 24014188375326900L, 24014188375326915L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(215, 24014188529575300L, 24014188529575315L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(216, 24014188568846400L, 24014188568846415L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(217, 24014188581402100L, 24014188581402115L, 0,
                              12, 10000, 0, 10000));

        list.add(buildTrigger(218, 24014188589167800L, 24014188589167815L, 0,
                              16, 10000, 0, 10000));

        list.add(buildTrigger(219, 24014188606648900L, 24014188606648915L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(220, 24014188665854000L, 24014188665854015L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(221, 24014188811071400L, 24014188811071415L, 0,
                              12, 10000, 0, 10000));

        list.add(buildTrigger(222, 24014188843133600L, 24014188843133615L, 0,
                              12, 10000, 0, 10000));

        list.add(buildTrigger(223, 24014188846634600L, 24014188846634615L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(224, 24014188936300900L, 24014188936300915L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(225, 24014188958301600L, 24014188958301615L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(226, 24014189018823300L, 24014189018823315L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(227, 24014189066539500L, 24014189066539515L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(228, 24014189104756900L, 24014189104756915L, 0,
                              20, 10000, 0, 10000));

        list.add(buildTrigger(229, 24014189111490300L, 24014189111490315L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(230, 24014189177631800L, 24014189177631815L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(231, 24014189216876300L, 24014189216876315L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(232, 24014189248056700L, 24014189248056715L, 0,
                              12, 10000, 0, 10000));

        list.add(buildTrigger(233, 24014189325617600L, 24014189325617615L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(234, 24014189342825000L, 24014189342825015L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(235, 24014189386579500L, 24014189386579515L, 0,
                              12, 10000, 0, 10000));

        list.add(buildTrigger(236, 24014189388095900L, 24014189388095915L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(237, 24014189392385900L, 24014189392385915L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(238, 24014189472640700L, 24014189472640715L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(239, 24014189560090300L, 24014189560090315L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(240, 24014189560274600L, 24014189560274615L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(241, 24014189578667100L, 24014189578667115L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(242, 24014189601830900L, 24014189601830915L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(243, 24014189626866800L, 24014189626866815L, 0,
                              12, 10000, 0, 10000));

        list.add(buildTrigger(244, 24014189660721100L, 24014189660721115L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(245, 24014189833028800L, 24014189833028815L, 0,
                              16, 10000, 0, 10000));

        list.add(buildTrigger(246, 24014189898050000L, 24014189898050015L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(247, 24014189941484400L, 24014189941484415L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(248, 24014189990097400L, 24014189990097415L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(249, 24014189999546100L, 24014189999546115L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(250, 24014190000000700L, 24014190000000715L, 0,
                              64, 10000, 0, 10000));

        list.add(buildTrigger(251, 24014190117915100L, 24014190117915115L, 0,
                              12, 10000, 0, 10000));

        list.add(buildTrigger(252, 24014190151436000L, 24014190151436015L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(253, 24014190197474500L, 24014190197474515L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(254, 24014190301043400L, 24014190301043415L, 0,
                              16, 10000, 0, 10000));

        list.add(buildTrigger(255, 24014190500272200L, 24014190500272215L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(256, 24014190504567900L, 24014190504567915L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(257, 24014190539678500L, 24014190539678515L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(258, 24014190621291500L, 24014190621291515L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(259, 24014190645450600L, 24014190645450615L, 0,
                              12, 10000, 0, 10000));

        list.add(buildTrigger(260, 24014190685370000L, 24014190685370015L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(261, 24014190713984900L, 24014190713984915L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(262, 24014190760604700L, 24014190760604715L, 0,
                              12, 10000, 0, 10000));

        list.add(buildTrigger(263, 24014190888351900L, 24014190888351915L, 0,
                              12, 10000, 0, 10000));

        list.add(buildTrigger(264, 24014190950853400L, 24014190950853415L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(265, 24014191050779200L, 24014191050779215L, 0,
                              12, 10000, 0, 10000));

        list.add(buildTrigger(266, 24014191246377600L, 24014191246377615L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(267, 24014191278131100L, 24014191278131115L, 0,
                              20, 10000, 0, 10000));

        list.add(buildTrigger(268, 24014191296795400L, 24014191296795415L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(269, 24014191376391500L, 24014191376391515L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(270, 24014191394407800L, 24014191394407815L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(271, 24014191397904200L, 24014191397904215L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(272, 24014191426080600L, 24014191426080615L, 0,
                              12, 10000, 0, 10000));

        list.add(buildTrigger(273, 24014191456450000L, 24014191456450015L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(274, 24014191526244200L, 24014191526244215L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(275, 24014191733388000L, 24014191733388015L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(276, 24014191776534800L, 24014191776534815L, 0,
                              20, 10000, 0, 10000));

        list.add(buildTrigger(277, 24014191830250100L, 24014191830250115L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(278, 24014191838031000L, 24014191838031015L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(279, 24014191839185900L, 24014191839185915L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(280, 24014191860890100L, 24014191860890115L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(281, 24014191883631200L, 24014191883631215L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(282, 24014191943856200L, 24014191943856215L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(283, 24014192062315200L, 24014192062315215L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(284, 24014192088305100L, 24014192088305115L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(285, 24014192180102400L, 24014192180102415L, 0,
                              20, 10000, 0, 10000));

        list.add(buildTrigger(286, 24014192188130100L, 24014192188130115L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(287, 24014192241199000L, 24014192241199015L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(288, 24014192286504100L, 24014192286504115L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(289, 24014192303394400L, 24014192303394415L, 0,
                              16, 10000, 0, 10000));

        list.add(buildTrigger(290, 24014192533418500L, 24014192533418515L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(291, 24014192547949500L, 24014192547949515L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(292, 24014192559874500L, 24014192559874515L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(293, 24014192605497500L, 24014192605497515L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(294, 24014192768238300L, 24014192768238315L, 0,
                              12, 10000, 0, 10000));

        list.add(buildTrigger(295, 24014192785229700L, 24014192785229715L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(296, 24014192834235000L, 24014192834235015L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(297, 24014192838864900L, 24014192838864915L, 0,
                              12, 10000, 0, 10000));

        list.add(buildTrigger(298, 24014193000005800L, 24014193000005815L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(299, 24014193164359900L, 24014193164359915L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(300, 24014193176596900L, 24014193176596915L, 0,
                              20, 10000, 0, 10000));

        list.add(buildTrigger(301, 24014193218968700L, 24014193218968715L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(302, 24014193369709400L, 24014193369709415L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(303, 24014193384794200L, 24014193384794215L, 0,
                              1, 10000, 0, 10000));

        list.add(buildTrigger(304, 24014193458425600L, 24014193458425615L, 0,
                              20, 10000, 0, 10000));

        list.add(buildTrigger(305, 24014193480648800L, 24014193480648815L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(306, 24014193535763700L, 24014193535763715L, 0,
                              16, 10000, 0, 10000));

        list.add(buildTrigger(307, 24014193565961000L, 24014193565961015L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(308, 24014193639581600L, 24014193639581615L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(309, 24014193663431900L, 24014193663431915L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(310, 24014193755974500L, 24014193755974515L, 0,
                              12, 10000, 0, 10000));

        list.add(buildTrigger(311, 24014193765193800L, 24014193765193815L, 0,
                              20, 10000, 0, 10000));

        list.add(buildTrigger(312, 24014193832485500L, 24014193832485515L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(313, 24014193911677900L, 24014193911677915L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(314, 24014193912470000L, 24014193912470015L, 0,
                              16, 10000, 0, 10000));

        list.add(buildTrigger(315, 24014193954381000L, 24014193954381015L, 0,
                              20, 10000, 0, 10000));

        list.add(buildTrigger(316, 24014193966988200L, 24014193966988215L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(317, 24014193996754100L, 24014193996754115L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(318, 24014194076408800L, 24014194076408815L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(319, 24014194270501800L, 24014194270501815L, 0,
                              12, 10000, 0, 10000));

        list.add(buildTrigger(320, 24014194291168200L, 24014194291168215L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(321, 24014194434057400L, 24014194434057415L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(322, 24014194502884400L, 24014194502884415L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(323, 24014194536060900L, 24014194536060915L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(324, 24014194556022900L, 24014194556022915L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(325, 24014194585028300L, 24014194585028315L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(326, 24014194596196200L, 24014194596196215L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(327, 24014194631262600L, 24014194631262615L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(328, 24014194668500700L, 24014194668500715L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(329, 24014194717545600L, 24014194717545615L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(330, 24014194872255600L, 24014194872255615L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(331, 24014194904871400L, 24014194904871415L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(332, 24014195050116800L, 24014195050116815L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(333, 24014195071179200L, 24014195071179215L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(334, 24014195109247300L, 24014195109247315L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(335, 24014195115346400L, 24014195115346415L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(336, 24014195116170300L, 24014195116170315L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(337, 24014195153361500L, 24014195153361515L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(338, 24014195208232700L, 24014195208232715L, 0,
                              20, 10000, 0, 10000));

        list.add(buildTrigger(339, 24014195256054200L, 24014195256054215L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(340, 24014195282226700L, 24014195282226715L, 0,
                              16, 10000, 0, 10000));

        list.add(buildTrigger(341, 24014195415472900L, 24014195415472915L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(342, 24014195466534800L, 24014195466534815L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(343, 24014195554866200L, 24014195554866215L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(344, 24014195653366900L, 24014195653366915L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(345, 24014195693911300L, 24014195693911315L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(346, 24014195752349300L, 24014195752349315L, 0,
                              16, 10000, 0, 10000));

        list.add(buildTrigger(347, 24014195796919300L, 24014195796919315L, 0,
                              12, 10000, 0, 10000));

        list.add(buildTrigger(348, 24014196018135000L, 24014196018135015L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(349, 24014196025561500L, 24014196025561515L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(350, 24014196083952400L, 24014196083952415L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(351, 24014196151265000L, 24014196151265015L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(352, 24014196165206700L, 24014196165206715L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(353, 24014196170858000L, 24014196170858015L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(354, 24014196366214600L, 24014196366214615L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(355, 24014196407638500L, 24014196407638515L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(356, 24014196452865600L, 24014196452865615L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(357, 24014196463107800L, 24014196463107815L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(358, 24014196554428600L, 24014196554428615L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(359, 24014196605729500L, 24014196605729515L, 0,
                              12, 10000, 0, 10000));

        list.add(buildTrigger(360, 24014196618043900L, 24014196618043915L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(361, 24014196623785500L, 24014196623785515L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(362, 24014196660859100L, 24014196660859115L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(363, 24014196679689600L, 24014196679689615L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(364, 24014196806737800L, 24014196806737815L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(365, 24014196834021900L, 24014196834021915L, 0,
                              16, 10000, 0, 10000));

        list.add(buildTrigger(366, 24014196968151800L, 24014196968151815L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(367, 24014197211051300L, 24014197211051315L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(368, 24014197281297400L, 24014197281297415L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(369, 24014197354235700L, 24014197354235715L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(370, 24014197406947800L, 24014197406947815L, 0,
                              20, 10000, 0, 10000));

        list.add(buildTrigger(371, 24014197422519100L, 24014197422519115L, 0,
                              16, 10000, 0, 10000));

        list.add(buildTrigger(372, 24014197489103700L, 24014197489103715L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(373, 24014197527217800L, 24014197527217815L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(374, 24014197578230700L, 24014197578230715L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(375, 24014197589523500L, 24014197589523515L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(376, 24014197602321700L, 24014197602321715L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(377, 24014197612733400L, 24014197612733415L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(378, 24014197643002300L, 24014197643002315L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(379, 24014197647819100L, 24014197647819115L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(380, 24014197669515900L, 24014197669515915L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(381, 24014197687362800L, 24014197687362815L, 0,
                              20, 10000, 0, 10000));

        list.add(buildTrigger(382, 24014197747667200L, 24014197747667215L, 0,
                              12, 10000, 0, 10000));

        list.add(buildTrigger(383, 24014197748102600L, 24014197748102615L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(384, 24014197799978100L, 24014197799978115L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(385, 24014197947791900L, 24014197947791915L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(386, 24014197954016100L, 24014197954016115L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(387, 24014197965500000L, 24014197965500015L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(388, 24014198110774600L, 24014198110774615L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(389, 24014198118634900L, 24014198118634915L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(390, 24014198137270300L, 24014198137270315L, 0,
                              12, 10000, 0, 10000));

        list.add(buildTrigger(391, 24014198191608800L, 24014198191608815L, 0,
                              12, 10000, 0, 10000));

        list.add(buildTrigger(392, 24014198232934600L, 24014198232934615L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(393, 24014198273053000L, 24014198273053015L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(394, 24014198322648100L, 24014198322648115L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(395, 24014198377415600L, 24014198377415615L, 0,
                              12, 10000, 0, 10000));

        list.add(buildTrigger(396, 24014198445619500L, 24014198445619515L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(397, 24014198530052800L, 24014198530052815L, 0,
                              12, 10000, 0, 10000));

        list.add(buildTrigger(398, 24014198597199000L, 24014198597199015L, 0,
                              20, 10000, 0, 10000));

        list.add(buildTrigger(399, 24014198628782800L, 24014198628782815L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(400, 24014198662216900L, 24014198662216915L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(401, 24014198794103400L, 24014198794103415L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(402, 24014198813380800L, 24014198813380815L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(403, 24014198848953200L, 24014198848953215L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(404, 24014198894928200L, 24014198894928215L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(405, 24014198910875500L, 24014198910875515L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(406, 24014198928848100L, 24014198928848115L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(407, 24014198980420200L, 24014198980420215L, 0,
                              12, 10000, 0, 10000));

        list.add(buildTrigger(408, 24014198985792000L, 24014198985792015L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(409, 24014199005766100L, 24014199005766115L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(410, 24014199286989600L, 24014199286989615L, 0,
                              16, 10000, 0, 10000));

        list.add(buildTrigger(411, 24014199296006900L, 24014199296006915L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(412, 24014199300905500L, 24014199300905515L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(413, 24014199323344400L, 24014199323344415L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(414, 24014199341203900L, 24014199341203915L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(415, 24014199436118400L, 24014199436118415L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(416, 24014199461822800L, 24014199461822815L, 0,
                              20, 10000, 0, 10000));

        list.add(buildTrigger(417, 24014199466232000L, 24014199466232015L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(418, 24014199477164400L, 24014199477164415L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(419, 24014199529269200L, 24014199529269215L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(420, 24014199538770700L, 24014199538770715L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(421, 24014199538874200L, 24014199538874215L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(422, 24014199601509300L, 24014199601509315L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(423, 24014199649688900L, 24014199649688915L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(424, 24014199723082000L, 24014199723082015L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(425, 24014199732225900L, 24014199732225915L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(426, 24014199818632400L, 24014199818632415L, 0,
                              12, 10000, 0, 10000));

        list.add(buildTrigger(427, 24014199857698100L, 24014199857698115L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(428, 24014199889722200L, 24014199889722215L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(429, 24014199960585600L, 24014199960585615L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(430, 24014199971497100L, 24014199971497115L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(431, 24014200000000700L, 24014200000000715L, 0,
                              64, 10000, 0, 10000));

        list.add(buildTrigger(432, 24014200035105400L, 24014200035105415L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(433, 24014200047280200L, 24014200047280215L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(434, 24014200091970700L, 24014200091970715L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(435, 24014200103497300L, 24014200103497315L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(436, 24014200167208900L, 24014200167208915L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(437, 24014200192459300L, 24014200192459315L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(438, 24014200232304900L, 24014200232304915L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(439, 24014200247055500L, 24014200247055515L, 0,
                              16, 10000, 0, 10000));

        list.add(buildTrigger(440, 24014200266829700L, 24014200266829715L, 0,
                              12, 10000, 0, 10000));

        list.add(buildTrigger(441, 24014200349224600L, 24014200349224615L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(442, 24014200383685500L, 24014200383685515L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(443, 24014200404048900L, 24014200404048915L, 0,
                              16, 10000, 0, 10000));

        list.add(buildTrigger(444, 24014200451839800L, 24014200451839815L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(445, 24014200454032900L, 24014200454032915L, 0,
                              12, 10000, 0, 10000));

        list.add(buildTrigger(446, 24014200480861800L, 24014200480861815L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(447, 24014200485614100L, 24014200485614115L, 0,
                              16, 10000, 0, 10000));

        list.add(buildTrigger(448, 24014200517302700L, 24014200517302715L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(449, 24014200622648200L, 24014200622648215L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(450, 24014200626707500L, 24014200626707515L, 0,
                              20, 10000, 0, 10000));

        list.add(buildTrigger(451, 24014200786385900L, 24014200786385915L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(452, 24014200830720900L, 24014200830720915L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(453, 24014200852105400L, 24014200852105415L, 0,
                              20, 10000, 0, 10000));

        list.add(buildTrigger(454, 24014200855152300L, 24014200855152315L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(455, 24014200938431300L, 24014200938431315L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(456, 24014200952047000L, 24014200952047015L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(457, 24014200991282900L, 24014200991282915L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(458, 24014201023505600L, 24014201023505615L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(459, 24014201037547200L, 24014201037547215L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(460, 24014201047603000L, 24014201047603015L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(461, 24014201053310600L, 24014201053310615L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(462, 24014201073676000L, 24014201073676015L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(463, 24014201079633200L, 24014201079633215L, 0,
                              20, 10000, 0, 10000));

        list.add(buildTrigger(464, 24014201085045300L, 24014201085045315L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(465, 24014201113213200L, 24014201113213215L, 0,
                              20, 10000, 0, 10000));

        list.add(buildTrigger(466, 24014201117956800L, 24014201117956815L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(467, 24014201163937700L, 24014201163937715L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(468, 24014201247301800L, 24014201247301815L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(469, 24014201390768300L, 24014201390768315L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(470, 24014201396924600L, 24014201396924615L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(471, 24014201596517900L, 24014201596517915L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(472, 24014201736441200L, 24014201736441215L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(473, 24014201742522200L, 24014201742522215L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(474, 24014201796159100L, 24014201796159115L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(475, 24014201824268900L, 24014201824268915L, 0,
                              20, 10000, 0, 10000));

        list.add(buildTrigger(476, 24014201882657200L, 24014201882657215L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(477, 24014201908656800L, 24014201908656815L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(478, 24014201939263300L, 24014201939263315L, 0,
                              12, 10000, 0, 10000));

        list.add(buildTrigger(479, 24014201960529800L, 24014201960529815L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(480, 24014202027647900L, 24014202027647915L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(481, 24014202046450100L, 24014202046450115L, 0,
                              20, 10000, 0, 10000));

        list.add(buildTrigger(482, 24014202112270300L, 24014202112270315L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(483, 24014202130308100L, 24014202130308115L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(484, 24014202171348400L, 24014202171348415L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(485, 24014202193265600L, 24014202193265615L, 0,
                              12, 10000, 0, 10000));

        list.add(buildTrigger(486, 24014202200240000L, 24014202200240015L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(487, 24014202214626400L, 24014202214626415L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(488, 24014202244226500L, 24014202244226515L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(489, 24014202288694100L, 24014202288694115L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(490, 24014202318252100L, 24014202318252115L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(491, 24014202368950600L, 24014202368950615L, 0,
                              20, 10000, 0, 10000));

        list.add(buildTrigger(492, 24014202382859100L, 24014202382859115L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(493, 24014202392249100L, 24014202392249115L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(494, 24014202423042400L, 24014202423042415L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(495, 24014202610850700L, 24014202610850715L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(496, 24014202719973300L, 24014202719973315L, 0,
                              2, 10000, 0, 10000));

        list.add(buildTrigger(497, 24014202750568700L, 24014202750568715L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(498, 24014202810516300L, 24014202810516315L, 0,
                              4, 10000, 0, 10000));

        list.add(buildTrigger(499, 24014203035224400L, 24014203035224415L, 0,
                              28, 10000, 0, 10000));

        list.add(buildTrigger(500, 24014203048840100L, 24014203048840115L, 0,
                              16, 10000, 0, 10000));

        return list;
    }

    private static ArrayList<ByteBuffer> getInIceHits()
        throws DataFormatException, IOException
    {
        ArrayList<ByteBuffer> list =
            new ArrayList<ByteBuffer>();

        list.add(buildHit(24014640657650675L, 2, 0, 12021, 0x6f242f105485L, 2));
        list.add(buildHit(24014640657651927L, 2, 0, 12021, 0x423ed83846c3L, 2));
        list.add(buildHit(24014640716369397L, 2, 0, 12030, 0xf4bddca5717cL, 2));
        list.add(buildHit(24014640716369418L, 2, 0, 12030, 0x61487316ca54L, 2));
        list.add(buildHit(24014640716369775L, 2, 0, 12030, 0xb7f25f056104L, 2));
        list.add(buildHit(24014640716370105L, 2, 0, 12030, 0xf3857903416dL, 2));
        list.add(buildHit(24014640716376900L, 2, 0, 12030, 0xeb9f5614053eL, 2));
        list.add(buildHit(24014640716377804L, 2, 0, 12030, 0xf299368f44fbL, 2));
        list.add(buildHit(24014640716383263L, 2, 0, 12030, 0xaa6f56d166a1L, 2));
        list.add(buildHit(24014640744779926L, 2, 0, 12030, 0xfac5284c8165L, 2));
        list.add(buildHit(24014640745980935L, 2, 0, 12021, 0xacd733b2b248L, 2));
        list.add(buildHit(24014640745983942L, 2, 0, 12021, 0x6f242f105485L, 2));
        list.add(buildHit(24014640745987407L, 2, 0, 12021, 0xa2a379faa0baL, 2));
        list.add(buildHit(24014640756028677L, 2, 0, 12029, 0x547cf31ff4c8L, 2));
        list.add(buildHit(24014640756030751L, 2, 0, 12029, 0xf765a906660eL, 2));
        list.add(buildHit(24014640756035403L, 2, 0, 12029, 0x9ef3fedb9d36L, 2));
        list.add(buildHit(24014640756042416L, 2, 0, 12030, 0x820a6a039815L, 2));
        list.add(buildHit(24014640756043205L, 2, 0, 12030, 0xfac5284c8165L, 2));
        list.add(buildHit(24014640756043883L, 2, 0, 12030, 0xc0f1ad586123L, 2));
        list.add(buildHit(24014640858068083L, 2, 0, 12021, 0x9513f8a612b5L, 2));
        list.add(buildHit(24014640858068890L, 2, 0, 12021, 0x916ceadb214dL, 2));
        list.add(buildHit(24014640858073738L, 2, 0, 12021, 0x16a5b9112b00L, 2));
        list.add(buildHit(24014640905160889L, 2, 0, 12029, 0x12d19e75bc5aL, 2));
        list.add(buildHit(24014640922863933L, 2, 0, 12030, 0x866c809f3657L, 2));
        list.add(buildHit(24014640922864544L, 2, 0, 12030, 0xd2a0f0a1b941L, 2));
        list.add(buildHit(24014640922866271L, 2, 0, 12030, 0x1cbe412e2d37L, 2));
        list.add(buildHit(24014640945124704L, 2, 0, 12029, 0xf49ee981a008L, 2));
        list.add(buildHit(24014640945124854L, 2, 0, 12029, 0x396a5d961de1L, 2));
        list.add(buildHit(24014640945125790L, 2, 0, 12029, 0x6368b5d0bb78L, 2));
        list.add(buildHit(24014640959687646L, 2, 0, 12029, 0x6e41ab3703aaL, 2));
        list.add(buildHit(24014640959687863L, 2, 0, 12029, 0xc992e597a438L, 2));
        list.add(buildHit(24014640959688506L, 2, 0, 12029, 0x81f0a16ec914L, 2));
        list.add(buildHit(24014640968550065L, 2, 0, 12021, 0xd337025ec466L, 2));
        list.add(buildHit(24014640968558046L, 2, 0, 12021, 0xa727fddbb7f4L, 2));
        list.add(buildHit(24014640969390937L, 2, 0, 12030, 0x28a8ef99c234L, 2));
        list.add(buildHit(24014640969391474L, 2, 0, 12030, 0xaa6f56d166a1L, 2));
        list.add(buildHit(24014640976725488L, 2, 0, 12021, 0x6f242f105485L, 2));
        list.add(buildHit(24014640976725808L, 2, 0, 12021, 0xa727fddbb7f4L, 2));
        list.add(buildHit(24014640976725963L, 2, 0, 12021, 0x5e17341bb4ebL, 2));
        list.add(buildHit(24014640976726348L, 2, 0, 12021, 0xb467b12814cbL, 2));
        list.add(buildHit(24014640976731737L, 2, 0, 12029, 0x454611892eeaL, 2));
        list.add(buildHit(24014640976731784L, 2, 0, 12021, 0xacd733b2b248L, 2));
        list.add(buildHit(24014640976732166L, 2, 0, 12029, 0x10dbd5f897a7L, 2));
        list.add(buildHit(24014641018641439L, 2, 0, 12030, 0xf299368f44fbL, 2));
        list.add(buildHit(24014641018641693L, 2, 0, 12030, 0xaa6f56d166a1L, 2));
        list.add(buildHit(24014641018645959L, 2, 0, 12030, 0xe9ca36873110L, 2));
        list.add(buildHit(24014641018648231L, 2, 0, 12030, 0x28a8ef99c234L, 2));
        list.add(buildHit(24014641019892346L, 2, 0, 12021, 0x5b9ae7909352L, 2));
        list.add(buildHit(24014641019900146L, 2, 0, 12021, 0x66504b293808L, 2));
        list.add(buildHit(24014641022407750L, 2, 0, 12029, 0xf765a906660eL, 2));
        list.add(buildHit(24014641022409775L, 2, 0, 12029, 0x6368b5d0bb78L, 2));
        list.add(buildHit(24014641022411768L, 2, 0, 12029, 0xfc11ec76b61cL, 2));
        list.add(buildHit(24014641022957729L, 2, 0, 12021, 0x50cb49c42356L, 2));
        list.add(buildHit(24014641022958087L, 2, 0, 12021, 0x6c34a4a77c08L, 2));
        list.add(buildHit(24014641022958110L, 2, 0, 12021, 0x8d0872b30a8aL, 2));
        list.add(buildHit(24014641022958219L, 2, 0, 12021, 0x0df7b060acadL, 2));
        list.add(buildHit(24014641022958385L, 2, 0, 12021, 0x57bb7c43b042L, 2));
        list.add(buildHit(24014641022958993L, 2, 0, 12021, 0xacf3852b67e7L, 2));
        list.add(buildHit(24014641022962290L, 2, 0, 12021, 0x8b9f35308e27L, 2));
        list.add(buildHit(24014641022962622L, 2, 0, 12021, 0x9a1136171c74L, 2));
        list.add(buildHit(24014641023030866L, 2, 0, 12021, 0x6c34a4a77c08L, 2));
        list.add(buildHit(24014641023036484L, 2, 0, 12021, 0x50cb49c42356L, 2));
        list.add(buildHit(24014641023044462L, 2, 0, 12021, 0x0df7b060acadL, 2));
        list.add(buildHit(24014641042856019L, 2, 0, 12021, 0xfb0944d283fdL, 2));
        list.add(buildHit(24014641042865116L, 2, 0, 12021, 0x38ae7fdfc4c7L, 2));
        list.add(buildHit(24014641052799009L, 2, 0, 12021, 0x85d69873ad04L, 2));
        list.add(buildHit(24014641052799114L, 2, 0, 12021, 0xffb9b1b82c88L, 2));
        list.add(buildHit(24014641052800568L, 2, 0, 12021, 0xde18daf832b4L, 2));
        list.add(buildHit(24014641080236690L, 2, 0, 12030, 0x866c809f3657L, 2));
        list.add(buildHit(24014641080239123L, 2, 0, 12030, 0x1cbe412e2d37L, 2));
        list.add(buildHit(24014641087921506L, 2, 0, 12029, 0x8f96faaca054L, 2));
        list.add(buildHit(24014641087931071L, 2, 0, 12029, 0x7b25f215b425L, 2));
        list.add(buildHit(24014641109426341L, 2, 0, 12039, 0xaad2afa683f5L, 2));
        list.add(buildHit(24014641116191362L, 2, 0, 12039, 0xfb2a9e5fd451L, 2));
        list.add(buildHit(24014641118988859L, 2, 0, 12021, 0x5b9ae7909352L, 2));
        list.add(buildHit(24014641118990144L, 2, 0, 12021, 0xabcfd5e5a352L, 2));
        list.add(buildHit(24014641118991144L, 2, 0, 12021, 0xfe92d7ff4480L, 2));
        list.add(buildHit(24014641118995798L, 2, 0, 12021, 0x66504b293808L, 2));
        list.add(buildHit(24014641130742736L, 2, 0, 12030, 0x724f1ae21fe6L, 2));
        list.add(buildHit(24014641130743726L, 2, 0, 12030, 0xa37441207611L, 2));
        list.add(buildHit(24014641130749383L, 2, 0, 12029, 0x14be85ac9369L, 2));
        list.add(buildHit(24014641130749398L, 2, 0, 12029, 0xa4bdff4f7bd0L, 2));
        list.add(buildHit(24014641163263024L, 2, 0, 12021, 0xffb9b1b82c88L, 2));
        list.add(buildHit(24014641163263728L, 2, 0, 12021, 0x858a79abc807L, 2));
        list.add(buildHit(24014641163264156L, 2, 0, 12021, 0x427ea29c4bb5L, 2));
        list.add(buildHit(24014641163266020L, 2, 0, 12021, 0x85d69873ad04L, 2));
        list.add(buildHit(24014641163267298L, 2, 0, 12021, 0x4255e2b1c79fL, 2));
        list.add(buildHit(24014641163269724L, 2, 0, 12021, 0x8b9f35308e27L, 2));
        list.add(buildHit(24014641163272849L, 2, 0, 12021, 0x57bb7c43b042L, 2));
        list.add(buildHit(24014641187713622L, 2, 0, 12029, 0x76716b1074c1L, 2));
        list.add(buildHit(24014641187715925L, 2, 0, 12029, 0x426393f54b3cL, 2));
        list.add(buildHit(24014641202752302L, 2, 0, 12029, 0x8f96faaca054L, 2));
        list.add(buildHit(24014641202753225L, 2, 0, 12029, 0x6368b5d0bb78L, 2));
        list.add(buildHit(24014641202754014L, 2, 0, 12029, 0x396a5d961de1L, 2));
        list.add(buildHit(24014641202756016L, 2, 0, 12029, 0xf49ee981a008L, 2));
        list.add(buildHit(24014641202757577L, 2, 0, 12029, 0xb5e30e459da4L, 2));
        list.add(buildHit(24014641202757601L, 2, 0, 12029, 0x3513aaef1b5bL, 2));
        list.add(buildHit(24014641206883689L, 2, 0, 12021, 0xfe92d7ff4480L, 2));
        list.add(buildHit(24014641206885115L, 2, 0, 12021, 0xfb0944d283fdL, 2));
        list.add(buildHit(24014641206885554L, 2, 0, 12021, 0x6e6733a279a7L, 2));
        list.add(buildHit(24014641206887397L, 2, 0, 12021, 0xabcfd5e5a352L, 2));
        list.add(buildHit(24014641208578975L, 2, 0, 12040, 0x74578c1716d2L, 2));
        list.add(buildHit(24014641214919621L, 2, 0, 12040, 0x882f452eba8fL, 2));
        list.add(buildHit(24014641214920286L, 2, 0, 12040, 0x53764a27a084L, 2));
        list.add(buildHit(24014641214924921L, 2, 0, 12030, 0x02cbcbc2c8bdL, 2));
        list.add(buildHit(24014641214925126L, 2, 0, 12030, 0x5616e19cb827L, 2));
        list.add(buildHit(24014641214926257L, 2, 0, 12030, 0xb821873ff31aL, 2));
        list.add(buildHit(24014641214927537L, 2, 0, 12030, 0xd030f9de4010L, 2));
        list.add(buildHit(24014641214927686L, 2, 0, 12030, 0x1fd95fe5fa1cL, 2));
        list.add(buildHit(24014641214928404L, 2, 0, 12030, 0xfac5284c8165L, 2));
        list.add(buildHit(24014641214928462L, 2, 0, 12030, 0x6c7b32bac895L, 2));
        list.add(buildHit(24014641214938829L, 2, 0, 12021, 0x82b54295e9cbL, 2));
        list.add(buildHit(24014641214941232L, 2, 0, 12021, 0xd337025ec466L, 2));
        list.add(buildHit(24014641214947049L, 2, 0, 12021, 0x455c5f0f8a3fL, 2));
        list.add(buildHit(24014641214949181L, 2, 0, 12021, 0xa727fddbb7f4L, 2));
        list.add(buildHit(24014641214949744L, 2, 0, 12021, 0xef3e4ba9cba4L, 2));
        list.add(buildHit(24014641214950026L, 2, 0, 12021, 0x755fc7596d46L, 2));
        list.add(buildHit(24014641237825161L, 2, 0, 12029, 0xec54677bbb9eL, 2));
        list.add(buildHit(24014641237825174L, 2, 0, 12029, 0xf2c126e81f64L, 2));
        list.add(buildHit(24014641239538667L, 2, 0, 12039, 0xeeb05853ee18L, 2));
        list.add(buildHit(24014641239539649L, 2, 0, 12039, 0xba3dbfcffc51L, 2));
        list.add(buildHit(24014641239785951L, 2, 0, 12030, 0xb7f25f056104L, 2));
        list.add(buildHit(24014641239786437L, 2, 0, 12040, 0x882f452eba8fL, 2));
        list.add(buildHit(24014641239786571L, 2, 0, 12040, 0x5705aa68d315L, 2));
        list.add(buildHit(24014641239787637L, 2, 0, 12040, 0xccbbe3ebe148L, 2));
        list.add(buildHit(24014641239788523L, 2, 0, 12040, 0x488e1c575627L, 2));
        list.add(buildHit(24014641239790291L, 2, 0, 12030, 0xf4bddca5717cL, 2));
        list.add(buildHit(24014641239794525L, 2, 0, 12040, 0xd6531d7f2215L, 2));
        list.add(buildHit(24014641239798656L, 2, 0, 12040, 0xd5edcc4d9a95L, 2));
        list.add(buildHit(24014641244985263L, 2, 0, 12021, 0x858a79abc807L, 2));
        list.add(buildHit(24014641244991419L, 2, 0, 12021, 0x427ea29c4bb5L, 2));
        list.add(buildHit(24014641248524358L, 2, 0, 12046, 0x06fce7d3cef3L, 2));
        list.add(buildHit(24014641250671248L, 2, 0, 12030, 0xa0c9794bcdc3L, 2));
        list.add(buildHit(24014641250680678L, 2, 0, 12030, 0xe133f6ea4687L, 2));
        list.add(buildHit(24014641259919927L, 2, 0, 12029, 0x173206b4f36cL, 2));
        list.add(buildHit(24014641259920160L, 2, 0, 12029, 0x81f0a16ec914L, 2));
        list.add(buildHit(24014641259921969L, 2, 0, 12029, 0x9ef3fedb9d36L, 2));
        list.add(buildHit(24014641275251584L, 2, 0, 12030, 0xf4bddca5717cL, 2));
        list.add(buildHit(24014641275254264L, 2, 0, 12030, 0x98a9e8e72cf1L, 2));
        list.add(buildHit(24014641277426886L, 2, 0, 12039, 0x19a31737fcbcL, 2));
        list.add(buildHit(24014641282400229L, 2, 0, 12021, 0xfe92d7ff4480L, 2));
        list.add(buildHit(24014641282400364L, 2, 0, 12021, 0xabcfd5e5a352L, 2));
        list.add(buildHit(24014641288792416L, 2, 0, 12046, 0x431d2cb68a55L, 2));
        list.add(buildHit(24014641290725029L, 2, 0, 12038, 0xbcd324dddc3dL, 2));
        list.add(buildHit(24014641290726506L, 2, 0, 12038, 0x9a97dd8b6516L, 2));
        list.add(buildHit(24014641304380179L, 2, 0, 12046, 0xe68a6acda6b4L, 2));
        list.add(buildHit(24014641319209008L, 2, 0, 12046, 0x3ee49849ffa6L, 2));
        list.add(buildHit(24014641344699616L, 2, 0, 12021, 0x32bb0201e5a7L, 2));
        list.add(buildHit(24014641344707793L, 2, 0, 12021, 0xabcfd5e5a352L, 2));
        list.add(buildHit(24014641345287586L, 2, 0, 12040, 0xefae979ed29fL, 2));
        list.add(buildHit(24014641345288850L, 2, 0, 12040, 0xe2e44c2f3563L, 2));
        list.add(buildHit(24014641345292967L, 2, 0, 12040, 0x87a7b9eb12aaL, 2));
        list.add(buildHit(24014641345294341L, 2, 0, 12039, 0xf4c0db8e6918L, 2));
        list.add(buildHit(24014641345294563L, 2, 0, 12039, 0xf7178188b3fdL, 2));
        list.add(buildHit(24014641345296853L, 2, 0, 12029, 0x396a5d961de1L, 2));
        list.add(buildHit(24014641345297616L, 2, 0, 12029, 0xf49ee981a008L, 2));
        list.add(buildHit(24014641358277149L, 2, 0, 12040, 0xd8010bc749c2L, 2));
        list.add(buildHit(24014641358280326L, 2, 0, 12040, 0x3dac398e0d9dL, 2));
        list.add(buildHit(24014641383975191L, 2, 0, 12040, 0x98a02fd6dbbbL, 2));
        list.add(buildHit(24014641383981907L, 2, 0, 12040, 0x87a7b9eb12aaL, 2));
        list.add(buildHit(24014641390921083L, 2, 0, 12046, 0xa979e9acaf2eL, 2));
        list.add(buildHit(24014641390941545L, 2, 0, 12030, 0x820a6a039815L, 2));
        list.add(buildHit(24014641390942116L, 2, 0, 12030, 0x6c7b32bac895L, 2));
        list.add(buildHit(24014641390942371L, 2, 0, 12030, 0xfac5284c8165L, 2));
        list.add(buildHit(24014641390944949L, 2, 0, 12030, 0xc0f1ad586123L, 2));
        list.add(buildHit(24014641390945539L, 2, 0, 12039, 0xc752d559fa24L, 2));
        list.add(buildHit(24014641390946713L, 2, 0, 12039, 0x9f78a4f96b83L, 2));
        list.add(buildHit(24014641390948495L, 2, 0, 12039, 0x087c5871c6a5L, 2));
        list.add(buildHit(24014641390952804L, 2, 0, 12039, 0x0a08de118fecL, 2));
        list.add(buildHit(24014641407320059L, 2, 0, 12039, 0x8b56bf63b908L, 2));
        list.add(buildHit(24014641407320308L, 2, 0, 12039, 0x9bc72c6ea776L, 2));
        list.add(buildHit(24014641407320455L, 2, 0, 12039, 0xa767a48cfe8aL, 2));
        list.add(buildHit(24014641407321433L, 2, 0, 12039, 0x7829d43058f7L, 2));
        list.add(buildHit(24014641407323174L, 2, 0, 12039, 0xef089997c7a2L, 2));
        list.add(buildHit(24014641407324706L, 2, 0, 12039, 0xc4b2d9e19419L, 2));
        list.add(buildHit(24014641407326191L, 2, 0, 12030, 0xf67f7b185ef7L, 2));
        list.add(buildHit(24014641407326711L, 2, 0, 12030, 0x866c809f3657L, 2));
        list.add(buildHit(24014641407326810L, 2, 0, 12030, 0x1cbe412e2d37L, 2));
        list.add(buildHit(24014641407326919L, 2, 0, 12030, 0xd2a0f0a1b941L, 2));
        list.add(buildHit(24014641407327370L, 2, 0, 12030, 0x58a90e542f8eL, 2));
        list.add(buildHit(24014641407328687L, 2, 0, 12039, 0xd815cc08657fL, 2));
        list.add(buildHit(24014641407329114L, 2, 0, 12030, 0x5616e19cb827L, 2));
        list.add(buildHit(24014641407330270L, 2, 0, 12030, 0x02cbcbc2c8bdL, 2));
        list.add(buildHit(24014641417350459L, 2, 0, 12039, 0x64581493a40bL, 2));
        list.add(buildHit(24014641417351219L, 2, 0, 12039, 0x78f7ff2c2829L, 2));
        list.add(buildHit(24014641417353855L, 2, 0, 12039, 0x03e493c9dd49L, 2));
        list.add(buildHit(24014641417354281L, 2, 0, 12039, 0x087c5871c6a5L, 2));
        list.add(buildHit(24014641437384053L, 2, 0, 12047, 0x5140bda6e07bL, 2));
        list.add(buildHit(24014641437386797L, 2, 0, 12046, 0x5f5c45dbb89fL, 2));
        list.add(buildHit(24014641437388083L, 2, 0, 12046, 0x33fe5a61e3c4L, 2));
        list.add(buildHit(24014641437389249L, 2, 0, 12046, 0xf634ed54c5f0L, 2));
        list.add(buildHit(24014641437391762L, 2, 0, 12046, 0xc51dfaa9f463L, 2));
        list.add(buildHit(24014641437397247L, 2, 0, 12046, 0x3ee49849ffa6L, 2));
        list.add(buildHit(24014641439046339L, 2, 0, 12067, 0xd307af164254L, 2));
        list.add(buildHit(24014641440067090L, 2, 0, 12047, 0x400e34e99096L, 2));
        list.add(buildHit(24014641440073055L, 2, 0, 12047, 0xed17776c9aa8L, 2));
        list.add(buildHit(24014641443888235L, 2, 0, 12029, 0xf4610aed678cL, 2));
        list.add(buildHit(24014641443889187L, 2, 0, 12029, 0xf765a906660eL, 2));
        list.add(buildHit(24014641445956621L, 2, 0, 12039, 0x19a31737fcbcL, 2));
        list.add(buildHit(24014641445957535L, 2, 0, 12039, 0x3f48845900d0L, 2));
        list.add(buildHit(24014641445958529L, 2, 0, 12039, 0x9bc72c6ea776L, 2));
        list.add(buildHit(24014641445958589L, 2, 0, 12039, 0xba3dbfcffc51L, 2));
        list.add(buildHit(24014641449533864L, 2, 0, 12029, 0x1551e2c72d57L, 2));
        list.add(buildHit(24014641449537747L, 2, 0, 12029, 0x80bf89d1a264L, 2));
        list.add(buildHit(24014641466788345L, 2, 0, 12030, 0xbb6614295b8cL, 2));
        list.add(buildHit(24014641466792183L, 2, 0, 12030, 0x7e2b00bad815L, 2));
        list.add(buildHit(24014641473711960L, 2, 0, 12067, 0x9b54b20558bdL, 2));
        list.add(buildHit(24014641473712347L, 2, 0, 12067, 0xd307af164254L, 2));
        list.add(buildHit(24014641473712705L, 2, 0, 12067, 0xa29598735544L, 2));
        list.add(buildHit(24014641473713047L, 2, 0, 12067, 0xaf90a0a41adeL, 2));
        list.add(buildHit(24014641473713736L, 2, 0, 12067, 0x06614a6ffcd4L, 2));
        list.add(buildHit(24014641473715156L, 2, 0, 12067, 0x8a3dc4b3c68eL, 2));
        list.add(buildHit(24014641473720305L, 2, 0, 12067, 0x1713d9af3072L, 2));
        list.add(buildHit(24014641473721640L, 2, 0, 12067, 0xd897bae245bbL, 2));
        list.add(buildHit(24014641473723180L, 2, 0, 12067, 0x729a1180042dL, 2));
        list.add(buildHit(24014641496403959L, 2, 0, 12047, 0xb3950d840546L, 2));
        list.add(buildHit(24014641496412180L, 2, 0, 12038, 0x02e07c2ba458L, 2));
        list.add(buildHit(24014641496412796L, 2, 0, 12038, 0x12a8d213e2acL, 2));
        list.add(buildHit(24014641503259721L, 2, 0, 12048, 0xf578db9a8423L, 2));
        list.add(buildHit(24014641507507439L, 2, 0, 12059, 0x8e78563d2179L, 2));
        list.add(buildHit(24014641507508463L, 2, 0, 12059, 0x1ab4a406af2dL, 2));
        list.add(buildHit(24014641511400334L, 2, 0, 12029, 0xc7f5e1b27dd2L, 2));
        list.add(buildHit(24014641511408430L, 2, 0, 12029, 0xb352db1a3e9aL, 2));
        list.add(buildHit(24014641518776104L, 2, 0, 12067, 0x3f028cb686b7L, 2));
        list.add(buildHit(24014641518776353L, 2, 0, 12067, 0x6c62cee79e41L, 2));
        list.add(buildHit(24014641518776651L, 2, 0, 12067, 0x2869b3f98c1fL, 2));
        list.add(buildHit(24014641518778626L, 2, 0, 12067, 0x6e5557701717L, 2));
        list.add(buildHit(24014641518779277L, 2, 0, 12067, 0xe44c73438cb0L, 2));
        list.add(buildHit(24014641519073829L, 2, 0, 12029, 0xa59c9e8c40b6L, 2));
        list.add(buildHit(24014641519079489L, 2, 0, 12029, 0xb5e30e459da4L, 2));
        list.add(buildHit(24014641519926467L, 2, 0, 12067, 0xa29598735544L, 2));
        list.add(buildHit(24014641531509929L, 2, 0, 12039, 0x93fdad986c6fL, 2));
        list.add(buildHit(24014641531511725L, 2, 0, 12039, 0x0b1d26a591b0L, 2));
        list.add(buildHit(24014641532468452L, 2, 0, 12046, 0x5e59556bd24cL, 2));
        list.add(buildHit(24014641540047356L, 2, 0, 12067, 0xc65d57f9ef16L, 2));
        list.add(buildHit(24014641540052770L, 2, 0, 12067, 0x066b7ebe2c26L, 2));
        list.add(buildHit(24014641540056989L, 2, 0, 12040, 0xefae979ed29fL, 2));
        list.add(buildHit(24014641540057361L, 2, 0, 12040, 0xe2e44c2f3563L, 2));
        list.add(buildHit(24014641541207370L, 2, 0, 12021, 0x32bb0201e5a7L, 2));
        list.add(buildHit(24014641541208364L, 2, 0, 12021, 0xfe92d7ff4480L, 2));
        list.add(buildHit(24014641541208862L, 2, 0, 12021, 0x38ae7fdfc4c7L, 2));
        list.add(buildHit(24014641541212048L, 2, 0, 12021, 0x72fecfd94382L, 2));
        list.add(buildHit(24014641547378677L, 2, 0, 12050, 0x91c65f3c9956L, 2));
        list.add(buildHit(24014641547379010L, 2, 0, 12050, 0x444cbb6e075cL, 2));
        list.add(buildHit(24014641547380315L, 2, 0, 12050, 0x4baa79b64278L, 2));
        list.add(buildHit(24014641547380428L, 2, 0, 12050, 0x5b5cd7a4fb85L, 2));
        list.add(buildHit(24014641547380690L, 2, 0, 12050, 0x9d5d0842334eL, 2));
        list.add(buildHit(24014641552740313L, 2, 0, 12067, 0xd307af164254L, 2));
        list.add(buildHit(24014641553115830L, 2, 0, 12038, 0x6432e28f770fL, 2));
        list.add(buildHit(24014641553116489L, 2, 0, 12038, 0x811227c3746cL, 2));
        list.add(buildHit(24014641553116966L, 2, 0, 12038, 0x4d983ea5297aL, 2));
        list.add(buildHit(24014641554619250L, 2, 0, 12050, 0x56c3aab8b518L, 2));
        list.add(buildHit(24014641554619427L, 2, 0, 12050, 0xaaaabc1deec3L, 2));
        list.add(buildHit(24014641557366004L, 2, 0, 12059, 0x83d8f521b859L, 2));
        list.add(buildHit(24014641557367325L, 2, 0, 12059, 0xb47fbd7d6299L, 2));
        list.add(buildHit(24014641557369789L, 2, 0, 12067, 0x091be8177ab9L, 2));
        list.add(buildHit(24014641557376230L, 2, 0, 12067, 0xd51568afe402L, 2));
        list.add(buildHit(24014641565142932L, 2, 0, 12065, 0xd97c010a948bL, 2));
        list.add(buildHit(24014641565143707L, 2, 0, 12065, 0x70e229a3d5ebL, 2));
        list.add(buildHit(24014641565143937L, 2, 0, 12065, 0x359b91e881e6L, 2));
        list.add(buildHit(24014641565144440L, 2, 0, 12065, 0xdb32d1a4bdb4L, 2));
        list.add(buildHit(24014641565144499L, 2, 0, 12065, 0x8ca33eaaaad0L, 2));
        list.add(buildHit(24014641565145081L, 2, 0, 12065, 0x6584af07a218L, 2));
        list.add(buildHit(24014641565149160L, 2, 0, 12065, 0x6204d28017f5L, 2));
        list.add(buildHit(24014641565151941L, 2, 0, 12065, 0xec9dc048fc34L, 2));
        list.add(buildHit(24014641565152123L, 2, 0, 12065, 0xfc2bb7c46ce5L, 2));
        list.add(buildHit(24014641573729969L, 2, 0, 12021, 0xe53c98680186L, 2));
        list.add(buildHit(24014641573730818L, 2, 0, 12021, 0xb804f6f38a45L, 2));
        list.add(buildHit(24014641573730939L, 2, 0, 12021, 0x16a5b9112b00L, 2));
        list.add(buildHit(24014641573732411L, 2, 0, 12021, 0x4255e2b1c79fL, 2));
        list.add(buildHit(24014641573732656L, 2, 0, 12021, 0x858a79abc807L, 2));
        list.add(buildHit(24014641573745782L, 2, 0, 12049, 0x6d7d54d2f112L, 2));
        list.add(buildHit(24014641573751005L, 2, 0, 12049, 0x006714f851d4L, 2));
        list.add(buildHit(24014641573751677L, 2, 0, 12059, 0xcd573e1e4b1aL, 2));
        list.add(buildHit(24014641573753192L, 2, 0, 12059, 0x5eef7f4656bbL, 2));
        list.add(buildHit(24014641587599442L, 2, 0, 12046, 0x895c42df4492L, 2));
        list.add(buildHit(24014641587599795L, 2, 0, 12046, 0x1f64ae8147c5L, 2));
        list.add(buildHit(24014641587599966L, 2, 0, 12046, 0xfeb392361dbdL, 2));
        list.add(buildHit(24014641587600658L, 2, 0, 12046, 0xf2d6f61aa895L, 2));
        list.add(buildHit(24014641599647659L, 2, 0, 12067, 0xaf90a0a41adeL, 2));
        list.add(buildHit(24014641600873940L, 2, 0, 12066, 0xc09ad7a7ea16L, 2));
        list.add(buildHit(24014641600876483L, 2, 0, 12066, 0x667f78126c95L, 2));
        list.add(buildHit(24014641604222596L, 2, 0, 12021, 0x85d69873ad04L, 2));
        list.add(buildHit(24014641604229024L, 2, 0, 12021, 0x72fecfd94382L, 2));
        list.add(buildHit(24014641606781695L, 2, 0, 12050, 0x9662aea5669eL, 2));
        list.add(buildHit(24014641606781902L, 2, 0, 12050, 0x137b441e8e46L, 2));
        list.add(buildHit(24014641606784792L, 2, 0, 12050, 0x8c7165c1ee37L, 2));
        list.add(buildHit(24014641606787330L, 2, 0, 12050, 0xcb5d0a5f04c9L, 2));
        list.add(buildHit(24014641606787878L, 2, 0, 12050, 0xf1ef76a51884L, 2));
        list.add(buildHit(24014641606788891L, 2, 0, 12049, 0x08b13a790a37L, 2));
        list.add(buildHit(24014641606791314L, 2, 0, 12040, 0x3dac398e0d9dL, 2));
        list.add(buildHit(24014641606791655L, 2, 0, 12040, 0xd54044599535L, 2));
        list.add(buildHit(24014641617160349L, 2, 0, 12065, 0xfc2bb7c46ce5L, 2));
        list.add(buildHit(24014641617160404L, 2, 0, 12065, 0x6584af07a218L, 2));
        list.add(buildHit(24014641617164518L, 2, 0, 12065, 0x359b91e881e6L, 2));
        list.add(buildHit(24014641626294428L, 2, 0, 12065, 0x0923e78e8b10L, 2));
        list.add(buildHit(24014641626301818L, 2, 0, 12073, 0x02aeeef35302L, 2));
        list.add(buildHit(24014641626301883L, 2, 0, 12073, 0x144b7b85d859L, 2));
        list.add(buildHit(24014641626302593L, 2, 0, 12073, 0x416e145910adL, 2));
        list.add(buildHit(24014641628338837L, 2, 0, 12029, 0xffa332fbf3a4L, 2));
        list.add(buildHit(24014641628338990L, 2, 0, 12029, 0x134cbe8a1688L, 2));
        list.add(buildHit(24014641631296723L, 2, 0, 12067, 0x091be8177ab9L, 2));
        list.add(buildHit(24014641635152648L, 2, 0, 12073, 0x2911d83d429bL, 2));
        list.add(buildHit(24014641635161599L, 2, 0, 12073, 0x144211cb2ed5L, 2));
        list.add(buildHit(24014641635485407L, 2, 0, 12029, 0x14be85ac9369L, 2));
        list.add(buildHit(24014641635485413L, 2, 0, 12029, 0x1375759346b9L, 2));
        list.add(buildHit(24014641635487571L, 2, 0, 12029, 0xe003d4bb5d53L, 2));
        list.add(buildHit(24014641635488408L, 2, 0, 12029, 0xf765a906660eL, 2));
        list.add(buildHit(24014641635488782L, 2, 0, 12029, 0xb897262bf58aL, 2));
        list.add(buildHit(24014641635491680L, 2, 0, 12039, 0x9f78a4f96b83L, 2));
        list.add(buildHit(24014641635501034L, 2, 0, 12039, 0xc752d559fa24L, 2));
        list.add(buildHit(24014641640455793L, 2, 0, 12029, 0xfc11ec76b61cL, 2));
        list.add(buildHit(24014641640456065L, 2, 0, 12029, 0x0f8b70fee5bdL, 2));
        list.add(buildHit(24014641640456272L, 2, 0, 12029, 0x3513aaef1b5bL, 2));
        list.add(buildHit(24014641640456359L, 2, 0, 12029, 0xb5e30e459da4L, 2));
        list.add(buildHit(24014641640457023L, 2, 0, 12029, 0x6b22691c7809L, 2));
        list.add(buildHit(24014641640457714L, 2, 0, 12029, 0xa59c9e8c40b6L, 2));
        list.add(buildHit(24014641640460600L, 2, 0, 12029, 0x426393f54b3cL, 2));
        list.add(buildHit(24014641640540565L, 2, 0, 12029, 0xfc11ec76b61cL, 2));
        list.add(buildHit(24014641640547051L, 2, 0, 12029, 0x0f8b70fee5bdL, 2));
        list.add(buildHit(24014641641226643L, 2, 0, 12038, 0x2ba475921bffL, 2));
        list.add(buildHit(24014641641227684L, 2, 0, 12038, 0xbcd324dddc3dL, 2));
        list.add(buildHit(24014641641245933L, 2, 0, 12066, 0x331f61511a11L, 2));
        list.add(buildHit(24014641641246097L, 2, 0, 12066, 0x43149cdc4362L, 2));
        list.add(buildHit(24014641641249904L, 2, 0, 12066, 0xc09ad7a7ea16L, 2));
        list.add(buildHit(24014641643102379L, 2, 0, 12050, 0x5b5cd7a4fb85L, 2));
        list.add(buildHit(24014641643102687L, 2, 0, 12050, 0x444cbb6e075cL, 2));
        list.add(buildHit(24014641643104394L, 2, 0, 12050, 0x91c65f3c9956L, 2));
        list.add(buildHit(24014641643106056L, 2, 0, 12050, 0x4baa79b64278L, 2));
        list.add(buildHit(24014641643366702L, 2, 0, 12057, 0x6a177631b1f9L, 2));
        list.add(buildHit(24014641656745426L, 2, 0, 12046, 0xd758cad34d26L, 2));
        list.add(buildHit(24014641656752643L, 2, 0, 12046, 0x9a1b31d3ebe5L, 2));
        list.add(buildHit(24014641664724980L, 2, 0, 12073, 0x97785ea71b24L, 2));
        list.add(buildHit(24014641669042645L, 2, 0, 12046, 0x9a1b31d3ebe5L, 2));
        list.add(buildHit(24014641669046488L, 2, 0, 12046, 0x5352ea563daeL, 2));
        list.add(buildHit(24014641674731080L, 2, 0, 12039, 0x965883cfbedbL, 2));
        list.add(buildHit(24014641674738952L, 2, 0, 12039, 0x0a08de118fecL, 2));
        list.add(buildHit(24014641675810791L, 2, 0, 12065, 0xdb32d1a4bdb4L, 2));
        list.add(buildHit(24014641675811309L, 2, 0, 12065, 0x45277d29f268L, 2));
        list.add(buildHit(24014641675815942L, 2, 0, 12065, 0x349c9e233928L, 2));
        list.add(buildHit(24014641686392092L, 2, 0, 12066, 0x0af1d2727f85L, 2));
        list.add(buildHit(24014641686393265L, 2, 0, 12067, 0x8a3dc4b3c68eL, 2));
        list.add(buildHit(24014641686394870L, 2, 0, 12066, 0xdb21e2db154fL, 2));
        list.add(buildHit(24014641686395349L, 2, 0, 12067, 0x1713d9af3072L, 2));
        list.add(buildHit(24014641686397844L, 2, 0, 12059, 0x9b60779fe19fL, 2));
        list.add(buildHit(24014641686398413L, 2, 0, 12059, 0x307106e2b5ccL, 2));
        list.add(buildHit(24014641686398656L, 2, 0, 12066, 0xfce45a8cb7e9L, 2));
        list.add(buildHit(24014641686399304L, 2, 0, 12066, 0x581a272aeb03L, 2));
        list.add(buildHit(24014641686403172L, 2, 0, 12040, 0x2706114d34dfL, 2));
        list.add(buildHit(24014641686403453L, 2, 0, 12040, 0xb804e4971533L, 2));
        list.add(buildHit(24014641686403714L, 2, 0, 12040, 0xa40f157a0658L, 2));
        list.add(buildHit(24014641686404472L, 2, 0, 12040, 0xc6224ecabf6fL, 2));
        list.add(buildHit(24014641686404513L, 2, 0, 12040, 0x8d84a21acb2dL, 2));
        list.add(buildHit(24014641686406004L, 2, 0, 12040, 0x57b56c6739a6L, 2));
        list.add(buildHit(24014641686409554L, 2, 0, 12040, 0x3e1e4659ea06L, 2));
        list.add(buildHit(24014641688562019L, 2, 0, 12040, 0x7ce10459887eL, 2));
        list.add(buildHit(24014641688568392L, 2, 0, 12040, 0x06a01c105181L, 2));
        list.add(buildHit(24014641689025758L, 2, 0, 12040, 0x17b7f9bc90f2L, 2));
        list.add(buildHit(24014641689030399L, 2, 0, 12040, 0x74578c1716d2L, 2));
        list.add(buildHit(24014641695180658L, 2, 0, 12040, 0xd8010bc749c2L, 2));
        list.add(buildHit(24014641695182775L, 2, 0, 12040, 0x17b7f9bc90f2L, 2));
        list.add(buildHit(24014641697409730L, 2, 0, 12047, 0x43a20bfe10d6L, 2));
        list.add(buildHit(24014641697417560L, 2, 0, 12047, 0x400e34e99096L, 2));
        list.add(buildHit(24014641704062458L, 2, 0, 12072, 0xd6ab8075e2fbL, 2));
        list.add(buildHit(24014641706444004L, 2, 0, 12048, 0x55c986ace722L, 2));
        list.add(buildHit(24014641710433024L, 2, 0, 12072, 0x7cd75e9125d2L, 2));
        list.add(buildHit(24014641713525144L, 2, 0, 12057, 0xca64bc22dac2L, 2));
        list.add(buildHit(24014641713536090L, 2, 0, 12065, 0xd4ff422e05b5L, 2));
        list.add(buildHit(24014641723664850L, 2, 0, 12030, 0xb1fc89162c0bL, 2));
        list.add(buildHit(24014641724910975L, 2, 0, 12021, 0x66504b293808L, 2));
        list.add(buildHit(24014641724914689L, 2, 0, 12021, 0x5b9ae7909352L, 2));
        list.add(buildHit(24014641726479648L, 2, 0, 12040, 0x80134b136c39L, 2));
        list.add(buildHit(24014641726481047L, 2, 0, 12040, 0x57b56c6739a6L, 2));
        list.add(buildHit(24014641726484050L, 2, 0, 12049, 0xc861a5ae476aL, 2));
        list.add(buildHit(24014641728296150L, 2, 0, 12040, 0x80134b136c39L, 2));
        list.add(buildHit(24014641728296508L, 2, 0, 12040, 0x3e1e4659ea06L, 2));
        list.add(buildHit(24014641740027951L, 2, 0, 12021, 0x72fecfd94382L, 2));
        list.add(buildHit(24014641742468691L, 2, 0, 12049, 0xb9293b8feb2bL, 2));
        list.add(buildHit(24014641757133861L, 2, 0, 12065, 0xec9dc048fc34L, 2));
        list.add(buildHit(24014641765021728L, 2, 0, 12073, 0xb188cd497484L, 2));
        list.add(buildHit(24014641765024792L, 2, 0, 12073, 0x416e145910adL, 2));
        list.add(buildHit(24014641767777092L, 2, 0, 12046, 0xee02a0020ce6L, 2));
        list.add(buildHit(24014641767780527L, 2, 0, 12046, 0xe8fd68861537L, 2));
        list.add(buildHit(24014641782390207L, 2, 0, 12030, 0xf4bddca5717cL, 2));
        list.add(buildHit(24014641782390226L, 2, 0, 12030, 0x61487316ca54L, 2));
        list.add(buildHit(24014641782390735L, 2, 0, 12030, 0xb7f25f056104L, 2));
        list.add(buildHit(24014641782392047L, 2, 0, 12030, 0x98a9e8e72cf1L, 2));
        list.add(buildHit(24014641782401924L, 2, 0, 12021, 0x916ceadb214dL, 2));
        list.add(buildHit(24014641782402362L, 2, 0, 12021, 0x16a5b9112b00L, 2));
        list.add(buildHit(24014641782406407L, 2, 0, 12021, 0x8b9f35308e27L, 2));
        list.add(buildHit(24014641782414401L, 2, 0, 12021, 0x57bb7c43b042L, 2));
        list.add(buildHit(24014641786576868L, 2, 0, 12072, 0x95a5e41f1dfdL, 2));
        list.add(buildHit(24014641789343801L, 2, 0, 12029, 0xb897262bf58aL, 2));
        list.add(buildHit(24014641789352180L, 2, 0, 12029, 0x7b25f215b425L, 2));
        list.add(buildHit(24014641789354657L, 2, 0, 12057, 0x7c113f8e4709L, 2));
        list.add(buildHit(24014641789358251L, 2, 0, 12057, 0x919d4a9a9b02L, 2));
        list.add(buildHit(24014641789366429L, 2, 0, 12072, 0x46f402ad2d21L, 2));
        list.add(buildHit(24014641789366506L, 2, 0, 12072, 0xcb127bc2ed6bL, 2));
        list.add(buildHit(24014641789367314L, 2, 0, 12072, 0xb447984564dfL, 2));
        list.add(buildHit(24014641789367412L, 2, 0, 12072, 0x42a50c08b545L, 2));
        list.add(buildHit(24014641789368999L, 2, 0, 12072, 0x8e23c7c0cdc1L, 2));
        list.add(buildHit(24014641792654119L, 2, 0, 12047, 0x6aa68f2742c8L, 2));
        list.add(buildHit(24014641792655049L, 2, 0, 12047, 0x400e34e99096L, 2));
        list.add(buildHit(24014641794660721L, 2, 0, 12073, 0xae92b55a541fL, 2));
        list.add(buildHit(24014641794661082L, 2, 0, 12073, 0x3ea2812c77e0L, 2));
        list.add(buildHit(24014641794663414L, 2, 0, 12073, 0x94034ed50ac9L, 2));
        list.add(buildHit(24014641794664890L, 2, 0, 12073, 0xf7c070310592L, 2));
        list.add(buildHit(24014641796465263L, 2, 0, 12050, 0xbeb37f6943b8L, 2));
        list.add(buildHit(24014641796472009L, 2, 0, 12050, 0xb2c4461c8b5fL, 2));
        list.add(buildHit(24014641796952766L, 2, 0, 12066, 0x3096fbff3f93L, 2));
        list.add(buildHit(24014641796954754L, 2, 0, 12066, 0xa3ae4b646a70L, 2));
        list.add(buildHit(24014641799728304L, 2, 0, 12030, 0x724f1ae21fe6L, 2));
        list.add(buildHit(24014641799729227L, 2, 0, 12030, 0x61487316ca54L, 2));
        list.add(buildHit(24014641799730242L, 2, 0, 12030, 0xeb9f5614053eL, 2));
        list.add(buildHit(24014641799731267L, 2, 0, 12030, 0xe9ca36873110L, 2));
        list.add(buildHit(24014641799732332L, 2, 0, 12030, 0xb7f25f056104L, 2));
        list.add(buildHit(24014641799732376L, 2, 0, 12030, 0xa37441207611L, 2));
        list.add(buildHit(24014641803248648L, 2, 0, 12047, 0x031811262140L, 2));
        list.add(buildHit(24014641803258295L, 2, 0, 12047, 0x6e9d3e508910L, 2));
        list.add(buildHit(24014641806719639L, 2, 0, 12038, 0xbcd324dddc3dL, 2));
        list.add(buildHit(24014641806720316L, 2, 0, 12038, 0x9a97dd8b6516L, 2));
        list.add(buildHit(24014641806720457L, 2, 0, 12038, 0xfc385400b72aL, 2));
        list.add(buildHit(24014641806720818L, 2, 0, 12038, 0x6432e28f770fL, 2));
        list.add(buildHit(24014641806720940L, 2, 0, 12029, 0x81f0a16ec914L, 2));
        list.add(buildHit(24014641806720970L, 2, 0, 12038, 0xa0d899b577cbL, 2));
        list.add(buildHit(24014641806721629L, 2, 0, 12029, 0xc992e597a438L, 2));
        list.add(buildHit(24014641806722444L, 2, 0, 12038, 0x811227c3746cL, 2));
        list.add(buildHit(24014641806724321L, 2, 0, 12038, 0x798e62def158L, 2));
        list.add(buildHit(24014641806726384L, 2, 0, 12038, 0xe0b0716d897eL, 2));
        list.add(buildHit(24014641806729167L, 2, 0, 12038, 0x02e07c2ba458L, 2));
        list.add(buildHit(24014641808400443L, 2, 0, 12046, 0xd99fda201f20L, 2));
        list.add(buildHit(24014641808401177L, 2, 0, 12046, 0xe68a6acda6b4L, 2));
        list.add(buildHit(24014641809551647L, 2, 0, 12048, 0xbbf321953f5aL, 2));
        list.add(buildHit(24014641809555240L, 2, 0, 12048, 0x166817e1fbd9L, 2));
        list.add(buildHit(24014641811809471L, 2, 0, 12073, 0xae92b55a541fL, 2));
        list.add(buildHit(24014641811811622L, 2, 0, 12073, 0x3ea2812c77e0L, 2));
        list.add(buildHit(24014641812129828L, 2, 0, 12065, 0xe5b9c62cda24L, 2));
        list.add(buildHit(24014641812135019L, 2, 0, 12065, 0x4d5e6c4d16a6L, 2));
        list.add(buildHit(24014641821602949L, 2, 0, 12029, 0x6b22691c7809L, 2));
        list.add(buildHit(24014641825940979L, 2, 0, 12046, 0x5352ea563daeL, 2));
        list.add(buildHit(24014641825941091L, 2, 0, 12046, 0xd601cd46b263L, 2));
        list.add(buildHit(24014641825946915L, 2, 0, 12046, 0x20dca7d5a9e5L, 2));
        list.add(buildHit(24014641829696757L, 2, 0, 12040, 0xd0995f822b43L, 2));
        list.add(buildHit(24014641829698001L, 2, 0, 12040, 0x882f452eba8fL, 2));
        list.add(buildHit(24014641829699513L, 2, 0, 12040, 0x488e1c575627L, 2));
        list.add(buildHit(24014641829703377L, 2, 0, 12021, 0xb804f6f38a45L, 2));
        list.add(buildHit(24014641829703516L, 2, 0, 12021, 0xe53c98680186L, 2));
        list.add(buildHit(24014641829706732L, 2, 0, 12021, 0x16a5b9112b00L, 2));
        list.add(buildHit(24014641829707239L, 2, 0, 12021, 0x427ea29c4bb5L, 2));
        list.add(buildHit(24014641829718886L, 2, 0, 12021, 0xd638529ba5adL, 2));
        list.add(buildHit(24014641829720228L, 2, 0, 12021, 0xf004760c91e6L, 2));
        list.add(buildHit(24014641829835131L, 2, 0, 12048, 0x4853a9933e7dL, 2));
        list.add(buildHit(24014641829836031L, 2, 0, 12048, 0x5f6cd6cec834L, 2));
        list.add(buildHit(24014641838254774L, 2, 0, 12030, 0x61487316ca54L, 2));
        list.add(buildHit(24014641838254790L, 2, 0, 12030, 0xf4bddca5717cL, 2));
        list.add(buildHit(24014641838255292L, 2, 0, 12030, 0xf3857903416dL, 2));
        list.add(buildHit(24014641840236264L, 2, 0, 12067, 0xd51568afe402L, 2));
        list.add(buildHit(24014641840237931L, 2, 0, 12067, 0x1248d1f7b12bL, 2));
        list.add(buildHit(24014641840239210L, 2, 0, 12067, 0x35daf4dd3653L, 2));
        list.add(buildHit(24014641843112174L, 2, 0, 12067, 0x1248d1f7b12bL, 2));
        list.add(buildHit(24014641848872254L, 2, 0, 12059, 0xe12b26b755eaL, 2));
        list.add(buildHit(24014641848872707L, 2, 0, 12059, 0x7eb4dfe7d988L, 2));
        list.add(buildHit(24014641848872760L, 2, 0, 12059, 0x26c8d1767fceL, 2));
        list.add(buildHit(24014641848873796L, 2, 0, 12059, 0x1ab4a406af2dL, 2));
        list.add(buildHit(24014641848874867L, 2, 0, 12059, 0x02496b7fcac7L, 2));
        list.add(buildHit(24014641848874971L, 2, 0, 12059, 0x8e78563d2179L, 2));
        list.add(buildHit(24014641848876069L, 2, 0, 12059, 0xe6dc25b457fdL, 2));
        list.add(buildHit(24014641848878753L, 2, 0, 12059, 0x73659395df5eL, 2));
        list.add(buildHit(24014641849095087L, 2, 0, 12040, 0x882f452eba8fL, 2));
        list.add(buildHit(24014641849102733L, 2, 0, 12040, 0x53764a27a084L, 2));
        list.add(buildHit(24014641850156429L, 2, 0, 12039, 0x7829d43058f7L, 2));
        list.add(buildHit(24014641850156547L, 2, 0, 12039, 0xef089997c7a2L, 2));
        list.add(buildHit(24014641852593862L, 2, 0, 12057, 0x4b9b86a719c4L, 2));
        list.add(buildHit(24014641852595316L, 2, 0, 12057, 0x9db074a86547L, 2));
        list.add(buildHit(24014641852596188L, 2, 0, 12057, 0xbd483327b420L, 2));
        list.add(buildHit(24014641852596733L, 2, 0, 12057, 0xaec80a53122cL, 2));
        list.add(buildHit(24014641852598479L, 2, 0, 12057, 0xc58d0397e2c5L, 2));
        list.add(buildHit(24014641852598995L, 2, 0, 12057, 0xac4bf0dc3520L, 2));
        list.add(buildHit(24014641852599505L, 2, 0, 12057, 0x529b0aea563eL, 2));
        list.add(buildHit(24014641852600389L, 2, 0, 12057, 0xc9da531b2a3bL, 2));
        list.add(buildHit(24014641852601847L, 2, 0, 12047, 0x7daebc111939L, 2));
        list.add(buildHit(24014641852602106L, 2, 0, 12047, 0x8e7008deff55L, 2));
        list.add(buildHit(24014641852602753L, 2, 0, 12047, 0xed17776c9aa8L, 2));
        list.add(buildHit(24014641852604783L, 2, 0, 12056, 0x178fb493ef75L, 2));
        list.add(buildHit(24014641852608685L, 2, 0, 12046, 0xf7e6ff439090L, 2));
        list.add(buildHit(24014641852608990L, 2, 0, 12046, 0x04da1c75b9fbL, 2));
        list.add(buildHit(24014641852609262L, 2, 0, 12046, 0x06fce7d3cef3L, 2));
        list.add(buildHit(24014641852612458L, 2, 0, 12046, 0x7ac5ba8d796cL, 2));
        list.add(buildHit(24014641852622123L, 2, 0, 12046, 0xa94e7dcdf7b4L, 2));
        list.add(buildHit(24014641853667654L, 2, 0, 12046, 0xf2d6f61aa895L, 2));
        list.add(buildHit(24014641853670942L, 2, 0, 12046, 0x7ac5ba8d796cL, 2));
        list.add(buildHit(24014641853688564L, 2, 0, 12072, 0x03728e4a666dL, 2));
        list.add(buildHit(24014641853693415L, 2, 0, 12072, 0x46f402ad2d21L, 2));
        list.add(buildHit(24014641856240433L, 2, 0, 12073, 0x55c4f65e789bL, 2));
        list.add(buildHit(24014641856242227L, 2, 0, 12073, 0x3ea2812c77e0L, 2));
        list.add(buildHit(24014641865952875L, 2, 0, 12049, 0x7a06273239a8L, 2));
        list.add(buildHit(24014641865953642L, 2, 0, 12049, 0xf7809fdb01c0L, 2));
        list.add(buildHit(24014641865953981L, 2, 0, 12049, 0xe6e30655009bL, 2));
        list.add(buildHit(24014641865954703L, 2, 0, 12049, 0x8bbbb008b86fL, 2));
        list.add(buildHit(24014641865954849L, 2, 0, 12049, 0x8074af27dcc2L, 2));

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
        return new TestSuite(SubstandardTest.class);
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
        // build amanda server
        Selector sel = Selector.open();

        int port = ServerUtil.createServer(sel);

        File cfgFile =
            DAQTestUtil.buildConfigFile(getClass().getResource("/").getPath(),
                                        "sps-icecube-amanda-008");

        PayloadValidator trValidator = new TriggerValidator();
        PayloadValidator gtValidator = new GlobalTriggerValidator();

        // set up global trigger
        GlobalTriggerComponent gtComp = new GlobalTriggerComponent();
        gtComp.setGlobalConfigurationDir(cfgFile.getParent());
        gtComp.start(false);

        gtComp.configuring(cfgFile.getName());

        DAQTestUtil.connectToSink("gtOut", gtComp.getWriter(),
                                  gtComp.getOutputCache(), gtValidator);

        // set up in-ice trigger
        IniceTriggerComponent iiComp = new IniceTriggerComponent();
        iiComp.setGlobalConfigurationDir(cfgFile.getParent());
        iiComp.start(false);

        iiComp.configuring(cfgFile.getName());

        DAQTestUtil.glueComponents("IIT->GT",
                                   iiComp.getWriter(), iiComp.getOutputCache(),
                                   trValidator,
                                   gtComp.getReader(), gtComp.getInputCache());

        WritableByteChannel[] iiTails =
            DAQTestUtil.connectToReader(iiComp.getReader(),
                                        iiComp.getInputCache(),
                                        1);

        // set up amanda trigger
        AmandaTriggerComponent amComp =
            new AmandaTriggerComponent("localhost", port);
        amComp.setGlobalConfigurationDir(cfgFile.getParent());
        amComp.start(false);

        amComp.configuring(cfgFile.getName());

        DAQTestUtil.glueComponents("AM->GT",
                                   amComp.getWriter(), amComp.getOutputCache(),
                                   trValidator,
                                   gtComp.getReader(), gtComp.getInputCache());

        // start I/O engines
        DAQTestUtil.startIOProcess(gtComp.getReader());
        DAQTestUtil.startIOProcess(gtComp.getWriter());
        DAQTestUtil.startIOProcess(iiComp.getReader());
        DAQTestUtil.startIOProcess(iiComp.getWriter());
        DAQTestUtil.startIOProcess(amComp.getReader());
        DAQTestUtil.startIOProcess(amComp.getWriter());

        WritableByteChannel[] amTails = new WritableByteChannel[] {
            ServerUtil.acceptChannel(sel),
        };

        // load data into input channels
        List<ByteBuffer> amList = getAmandaTriggers();
        List<ByteBuffer> iiList = getInIceHits();

        final int numAmanda = amList.size();

        for (int i = 0; true; i++) {
            boolean sentData = false;
            if (i < amList.size()) {
                ByteBuffer bb = amList.get(i);
                bb.position(0);
                amTails[0].write(bb);
                sentData = true;
            }
            if (i < iiList.size()) {
                ByteBuffer bb = iiList.get(i);
                bb.position(0);
                iiTails[0].write(bb);
                sentData = true;
            }
            if (!sentData) break;
        }

        DAQTestUtil.sendStops(amTails);
        DAQTestUtil.sendStops(iiTails);

        DAQTestUtil.waitUntilStopped(amComp.getReader(), amComp.getSplicer(),
                                     "AMStopMsg");
        DAQTestUtil.waitUntilStopped(amComp.getWriter(), null, "AMStopMsg");
        DAQTestUtil.waitUntilStopped(iiComp.getReader(), iiComp.getSplicer(),
                                     "IIStopMsg");
        DAQTestUtil.waitUntilStopped(iiComp.getWriter(), null, "IIStopMsg");
        DAQTestUtil.waitUntilStopped(gtComp.getReader(), gtComp.getSplicer(),
                                     "GTStopMsg");
        DAQTestUtil.waitUntilStopped(gtComp.getWriter(), null, "GTStopMsg");

        amComp.flush();
        iiComp.flush();
        gtComp.flush();

        try {
            Thread.sleep(1000);
        } catch (InterruptedException ie) {
            // ignore interrupts
        }

        assertEquals("Unexpected number of in-ice triggers",
                     16, iiComp.getPayloadsSent() - 1);
        assertEquals("Unexpected number of Amanda triggers",
                     numAmanda, amComp.getPayloadsSent() - 1);
        assertEquals("Unexpected number of global triggers",
                     509, gtComp.getPayloadsSent() - 1);

        assertFalse("Found invalid global trigger(s)",
                    gtValidator.foundInvalid());
        assertFalse("Found invalid trigger(s)", trValidator.foundInvalid());

        System.err.println("XXX Ignoring extra log msgs");
        appender.clear();
    }

    public static void main(String[] args)
    {
        TestRunner.run(suite());
    }
}
