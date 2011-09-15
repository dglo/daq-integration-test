package icecube.daq.test;

import icecube.daq.eventBuilder.EBComponent;
import icecube.daq.eventBuilder.GlobalTriggerReader;
import icecube.daq.eventBuilder.SPDataAnalysis;
import icecube.daq.eventBuilder.backend.EventBuilderBackEnd;
import icecube.daq.eventBuilder.monitoring.MonitoringData;
import icecube.daq.io.SpliceablePayloadReader;
import icecube.daq.juggler.component.DAQCompException;
import icecube.daq.payload.IByteBufferCache;
import icecube.daq.payload.IPayloadDestination;
import icecube.daq.payload.ISourceID;
import icecube.daq.payload.IUTCTime;
import icecube.daq.payload.IWriteablePayload;
import icecube.daq.payload.PayloadChecker;
import icecube.daq.payload.PayloadRegistry;
import icecube.daq.payload.SourceIdRegistry;
import icecube.daq.payload.impl.TriggerRequest;
import icecube.daq.payload.impl.VitreousBufferCache;
import icecube.daq.splicer.HKN1Splicer;
import icecube.daq.splicer.Splicer;
import icecube.daq.splicer.SplicerException;
import icecube.daq.splicer.StrandTail;
import icecube.daq.trigger.component.IniceTriggerComponent;
import icecube.daq.trigger.component.GlobalTriggerComponent;
import icecube.daq.trigger.config.TriggerReadout;
import icecube.daq.trigger.control.GlobalTriggerManager;
import icecube.daq.trigger.control.TriggerManager;
import icecube.daq.trigger.exceptions.TriggerException;
import icecube.daq.util.DOMRegistry;
import icecube.daq.util.IDOMRegistry;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.zip.DataFormatException;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

import org.apache.log4j.BasicConfigurator;

public class EBReadonlyTest
    extends TestCase
{
    private static final MockAppender appender =
        new MockAppender(/*org.apache.log4j.Level.ALL*/)/*.setVerbose(true)*/;
        //new MockAppender(org.apache.log4j.Level.ALL).setVerbose(true);

    private static final int BASE_SIMHUB_ID =
        SourceIdRegistry.SIMULATION_HUB_SOURCE_ID;

    private static final MockSourceID INICE_TRIGGER_SOURCE_ID =
        new MockSourceID(SourceIdRegistry.INICE_TRIGGER_SOURCE_ID);

    private static final int NUM_HUBS = 5;

    private static final int RUN_NUMBER = 1234;

    private static final int TIME_STEP = 2500000;

    private static Random rand = new Random(1234567L);

    //private ListOpenFiles openFiles;

    private IniceTriggerComponent iiComp;
    private GlobalTriggerComponent gtComp;
    private EBComponent ebComp;

    private Pipe[] iiTails;

    public EBReadonlyTest(String name)
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

    private void checkLogMessages()
    {
        for (int i = 0; i < appender.getNumberOfMessages(); i++) {
            String msg = (String) appender.getMessage(i);

            if (!(msg.startsWith("Clearing ") &&
                  msg.endsWith(" rope entries")) &&
                !msg.startsWith("Resetting counter ") &&
                !msg.startsWith("No match for timegate ") &&
                !msg.startsWith("Sending empty event for window") &&
                !msg.endsWith("does not exist!  Using current directory."))
            {
                fail("Bad log message#" + i + ": " + appender.getMessage(i));
            }
        }
        appender.clear();
    }

    private static ArrayList<HitData> getInIceHits(IDOMRegistry domRegistry)
        throws DataFormatException, IOException
    {
        ArrayList<HitData> list =
            new ArrayList<HitData>();

        HitData.setDefaultTriggerType(2);
        HitData.setDefaultConfigId(0);
        HitData.setDefaultTriggerMode(2);
        HitData.setDOMRegistry(domRegistry);

        boolean useStatic = false;
        if (useStatic) {
            addStaticHits(list);
        } else {
            Set<String> domSet = domRegistry.keys();
            long[] domIdList = new long[domSet.size()];

            int nextIdx = 0;
            for (String mbStr : domSet) {
                try {
                    domIdList[nextIdx++] = Long.parseLong(mbStr, 16);
                } catch (NumberFormatException nfe) {
                    throw new Error("Bad mainboard ID \"" + mbStr + "\"");
                }
            }

            addGeneratedHits(domIdList, list);
        }

        return list;
    }

    private static ArrayList<HitData> addGeneratedHits(long[] domIdList,
                                                       ArrayList<HitData> list)
    {
        final int numStrings = 19;
        final int numHits = 5000;
        final int meanTime = 2421451;
        final int maxTime = meanTime * 2;

        long curTime = meanTime;
        for (int i = 0; i < numHits; i++) {
            int strNum = 12000 + rand.nextInt(numStrings) + 1;
            long domId = domIdList[rand.nextInt(domIdList.length)];

            int pct = rand.nextInt(100);

            long timeStep;
            if (pct < 30) {
                timeStep = rand.nextInt(1000);
            } else if (pct < 60) {
                timeStep = rand.nextInt(4000) + 1000;
            } else {
                timeStep = rand.nextLong() % maxTime;
                if (timeStep < 0) timeStep = -timeStep;
            }

            curTime += timeStep;

            list.add(new HitData(curTime, strNum, domId));
        }

        return list;
    }

    private static ArrayList<HitData> addStaticHits(ArrayList<HitData> list)
    {
        list.add(new HitData(24014640657650675L, 12021, 0x6f242f105485L));
        list.add(new HitData(24014640657651927L, 12021, 0x423ed83846c3L));
        list.add(new HitData(24014640716369397L, 12030, 0xf4bddca5717cL));
        list.add(new HitData(24014640716369418L, 12030, 0x61487316ca54L));
        list.add(new HitData(24014640716369775L, 12030, 0xb7f25f056104L));
        list.add(new HitData(24014640716370105L, 12030, 0xf3857903416dL));
        list.add(new HitData(24014640716376900L, 12030, 0xeb9f5614053eL));
        list.add(new HitData(24014640716377804L, 12030, 0xf299368f44fbL));
        list.add(new HitData(24014640716383263L, 12030, 0xaa6f56d166a1L));
        list.add(new HitData(24014640744779926L, 12030, 0xfac5284c8165L));
        list.add(new HitData(24014640745980935L, 12021, 0xacd733b2b248L));
        list.add(new HitData(24014640745983942L, 12021, 0x6f242f105485L));
        list.add(new HitData(24014640745987407L, 12021, 0xa2a379faa0baL));
        list.add(new HitData(24014640756028677L, 12029, 0x547cf31ff4c8L));
        list.add(new HitData(24014640756030751L, 12029, 0xf765a906660eL));
        list.add(new HitData(24014640756035403L, 12029, 0x9ef3fedb9d36L));
        list.add(new HitData(24014640756042416L, 12030, 0x820a6a039815L));
        list.add(new HitData(24014640756043205L, 12030, 0xfac5284c8165L));
        list.add(new HitData(24014640756043883L, 12030, 0xc0f1ad586123L));
        list.add(new HitData(24014640858068083L, 12021, 0x9513f8a612b5L));
        list.add(new HitData(24014640858068890L, 12021, 0x916ceadb214dL));
        list.add(new HitData(24014640858073738L, 12021, 0x16a5b9112b00L));
        list.add(new HitData(24014640905160889L, 12029, 0x12d19e75bc5aL));
        list.add(new HitData(24014640922863933L, 12030, 0x866c809f3657L));
        list.add(new HitData(24014640922864544L, 12030, 0xd2a0f0a1b941L));
        list.add(new HitData(24014640922866271L, 12030, 0x1cbe412e2d37L));
        list.add(new HitData(24014640945124704L, 12029, 0xf49ee981a008L));
        list.add(new HitData(24014640945124854L, 12029, 0x396a5d961de1L));
        list.add(new HitData(24014640945125790L, 12029, 0x6368b5d0bb78L));
        list.add(new HitData(24014640959687646L, 12029, 0x6e41ab3703aaL));
        list.add(new HitData(24014640959687863L, 12029, 0xc992e597a438L));
        list.add(new HitData(24014640959688506L, 12029, 0x81f0a16ec914L));
        list.add(new HitData(24014640968550065L, 12021, 0xd337025ec466L));
        list.add(new HitData(24014640968558046L, 12021, 0xa727fddbb7f4L));
        list.add(new HitData(24014640969390937L, 12030, 0x28a8ef99c234L));
        list.add(new HitData(24014640969391474L, 12030, 0xaa6f56d166a1L));
        list.add(new HitData(24014640976725488L, 12021, 0x6f242f105485L));
        list.add(new HitData(24014640976725808L, 12021, 0xa727fddbb7f4L));
        list.add(new HitData(24014640976725963L, 12021, 0x5e17341bb4ebL));
        list.add(new HitData(24014640976726348L, 12021, 0xb467b12814cbL));
        list.add(new HitData(24014640976731737L, 12029, 0x454611892eeaL));
        list.add(new HitData(24014640976731784L, 12021, 0xacd733b2b248L));
        list.add(new HitData(24014640976732166L, 12029, 0x10dbd5f897a7L));
        list.add(new HitData(24014641018641439L, 12030, 0xf299368f44fbL));
        list.add(new HitData(24014641018641693L, 12030, 0xaa6f56d166a1L));
        list.add(new HitData(24014641018645959L, 12030, 0xe9ca36873110L));
        list.add(new HitData(24014641018648231L, 12030, 0x28a8ef99c234L));
        list.add(new HitData(24014641019892346L, 12021, 0x5b9ae7909352L));
        list.add(new HitData(24014641019900146L, 12021, 0x66504b293808L));
        list.add(new HitData(24014641022407750L, 12029, 0xf765a906660eL));
        list.add(new HitData(24014641022409775L, 12029, 0x6368b5d0bb78L));
        list.add(new HitData(24014641022411768L, 12029, 0xfc11ec76b61cL));
        list.add(new HitData(24014641022957729L, 12021, 0x50cb49c42356L));
        list.add(new HitData(24014641022958087L, 12021, 0x6c34a4a77c08L));
        list.add(new HitData(24014641022958110L, 12021, 0x8d0872b30a8aL));
        list.add(new HitData(24014641022958219L, 12021, 0x0df7b060acadL));
        list.add(new HitData(24014641022958385L, 12021, 0x57bb7c43b042L));
        list.add(new HitData(24014641022958993L, 12021, 0xacf3852b67e7L));
        list.add(new HitData(24014641022962290L, 12021, 0x8b9f35308e27L));
        list.add(new HitData(24014641022962622L, 12021, 0x9a1136171c74L));
        list.add(new HitData(24014641023030866L, 12021, 0x6c34a4a77c08L));
        list.add(new HitData(24014641023036484L, 12021, 0x50cb49c42356L));
        list.add(new HitData(24014641023044462L, 12021, 0x0df7b060acadL));
        list.add(new HitData(24014641042856019L, 12021, 0xfb0944d283fdL));
        list.add(new HitData(24014641042865116L, 12021, 0x38ae7fdfc4c7L));
        list.add(new HitData(24014641052799009L, 12021, 0x85d69873ad04L));
        list.add(new HitData(24014641052799114L, 12021, 0xffb9b1b82c88L));
        list.add(new HitData(24014641052800568L, 12021, 0xde18daf832b4L));
        list.add(new HitData(24014641080236690L, 12030, 0x866c809f3657L));
        list.add(new HitData(24014641080239123L, 12030, 0x1cbe412e2d37L));
        list.add(new HitData(24014641087921506L, 12029, 0x8f96faaca054L));
        list.add(new HitData(24014641087931071L, 12029, 0x7b25f215b425L));
        list.add(new HitData(24014641109426341L, 12039, 0xaad2afa683f5L));
        list.add(new HitData(24014641116191362L, 12039, 0xfb2a9e5fd451L));
        list.add(new HitData(24014641118988859L, 12021, 0x5b9ae7909352L));
        list.add(new HitData(24014641118990144L, 12021, 0xabcfd5e5a352L));
        list.add(new HitData(24014641118991144L, 12021, 0xfe92d7ff4480L));
        list.add(new HitData(24014641118995798L, 12021, 0x66504b293808L));
        list.add(new HitData(24014641130742736L, 12030, 0x724f1ae21fe6L));
        list.add(new HitData(24014641130743726L, 12030, 0xa37441207611L));
        list.add(new HitData(24014641130749383L, 12029, 0x14be85ac9369L));
        list.add(new HitData(24014641130749398L, 12029, 0xa4bdff4f7bd0L));
        list.add(new HitData(24014641163263024L, 12021, 0xffb9b1b82c88L));
        list.add(new HitData(24014641163263728L, 12021, 0x858a79abc807L));
        list.add(new HitData(24014641163264156L, 12021, 0x427ea29c4bb5L));
        list.add(new HitData(24014641163266020L, 12021, 0x85d69873ad04L));
        list.add(new HitData(24014641163267298L, 12021, 0x4255e2b1c79fL));
        list.add(new HitData(24014641163269724L, 12021, 0x8b9f35308e27L));
        list.add(new HitData(24014641163272849L, 12021, 0x57bb7c43b042L));
        list.add(new HitData(24014641187713622L, 12029, 0x76716b1074c1L));
        list.add(new HitData(24014641187715925L, 12029, 0x426393f54b3cL));
        list.add(new HitData(24014641202752302L, 12029, 0x8f96faaca054L));
        list.add(new HitData(24014641202753225L, 12029, 0x6368b5d0bb78L));
        list.add(new HitData(24014641202754014L, 12029, 0x396a5d961de1L));
        list.add(new HitData(24014641202756016L, 12029, 0xf49ee981a008L));
        list.add(new HitData(24014641202757577L, 12029, 0xb5e30e459da4L));
        list.add(new HitData(24014641202757601L, 12029, 0x3513aaef1b5bL));
        list.add(new HitData(24014641206883689L, 12021, 0xfe92d7ff4480L));
        list.add(new HitData(24014641206885115L, 12021, 0xfb0944d283fdL));
        list.add(new HitData(24014641206885554L, 12021, 0x6e6733a279a7L));
        list.add(new HitData(24014641206887397L, 12021, 0xabcfd5e5a352L));
        list.add(new HitData(24014641208578975L, 12040, 0x74578c1716d2L));
        list.add(new HitData(24014641214919621L, 12040, 0x882f452eba8fL));
        list.add(new HitData(24014641214920286L, 12040, 0x53764a27a084L));
        list.add(new HitData(24014641214924921L, 12030, 0x02cbcbc2c8bdL));
        list.add(new HitData(24014641214925126L, 12030, 0x5616e19cb827L));
        list.add(new HitData(24014641214926257L, 12030, 0xb821873ff31aL));
        list.add(new HitData(24014641214927537L, 12030, 0xd030f9de4010L));
        list.add(new HitData(24014641214927686L, 12030, 0x1fd95fe5fa1cL));
        list.add(new HitData(24014641214928404L, 12030, 0xfac5284c8165L));
        list.add(new HitData(24014641214928462L, 12030, 0x6c7b32bac895L));
        list.add(new HitData(24014641214938829L, 12021, 0x82b54295e9cbL));
        list.add(new HitData(24014641214941232L, 12021, 0xd337025ec466L));
        list.add(new HitData(24014641214947049L, 12021, 0x455c5f0f8a3fL));
        list.add(new HitData(24014641214949181L, 12021, 0xa727fddbb7f4L));
        list.add(new HitData(24014641214949744L, 12021, 0xef3e4ba9cba4L));
        list.add(new HitData(24014641214950026L, 12021, 0x755fc7596d46L));
        list.add(new HitData(24014641237825161L, 12029, 0xec54677bbb9eL));
        list.add(new HitData(24014641237825174L, 12029, 0xf2c126e81f64L));
        list.add(new HitData(24014641239538667L, 12039, 0xeeb05853ee18L));
        list.add(new HitData(24014641239539649L, 12039, 0xba3dbfcffc51L));
        list.add(new HitData(24014641239785951L, 12030, 0xb7f25f056104L));
        list.add(new HitData(24014641239786437L, 12040, 0x882f452eba8fL));
        list.add(new HitData(24014641239786571L, 12040, 0x5705aa68d315L));
        list.add(new HitData(24014641239787637L, 12040, 0xccbbe3ebe148L));
        list.add(new HitData(24014641239788523L, 12040, 0x488e1c575627L));
        list.add(new HitData(24014641239790291L, 12030, 0xf4bddca5717cL));
        list.add(new HitData(24014641239794525L, 12040, 0xd6531d7f2215L));
        list.add(new HitData(24014641239798656L, 12040, 0xd5edcc4d9a95L));
        list.add(new HitData(24014641244985263L, 12021, 0x858a79abc807L));
        list.add(new HitData(24014641244991419L, 12021, 0x427ea29c4bb5L));
        list.add(new HitData(24014641248524358L, 12046, 0x06fce7d3cef3L));
        list.add(new HitData(24014641250671248L, 12030, 0xa0c9794bcdc3L));
        list.add(new HitData(24014641250680678L, 12030, 0xe133f6ea4687L));
        list.add(new HitData(24014641259919927L, 12029, 0x173206b4f36cL));
        list.add(new HitData(24014641259920160L, 12029, 0x81f0a16ec914L));
        list.add(new HitData(24014641259921969L, 12029, 0x9ef3fedb9d36L));
        list.add(new HitData(24014641275251584L, 12030, 0xf4bddca5717cL));
        list.add(new HitData(24014641275254264L, 12030, 0x98a9e8e72cf1L));
        list.add(new HitData(24014641277426886L, 12039, 0x19a31737fcbcL));
        list.add(new HitData(24014641282400229L, 12021, 0xfe92d7ff4480L));
        list.add(new HitData(24014641282400364L, 12021, 0xabcfd5e5a352L));
        list.add(new HitData(24014641288792416L, 12046, 0x431d2cb68a55L));
        list.add(new HitData(24014641290725029L, 12038, 0xbcd324dddc3dL));
        list.add(new HitData(24014641290726506L, 12038, 0x9a97dd8b6516L));
        list.add(new HitData(24014641304380179L, 12046, 0xe68a6acda6b4L));
        list.add(new HitData(24014641319209008L, 12046, 0x3ee49849ffa6L));
        list.add(new HitData(24014641344699616L, 12021, 0x32bb0201e5a7L));
        list.add(new HitData(24014641344707793L, 12021, 0xabcfd5e5a352L));
        list.add(new HitData(24014641345287586L, 12040, 0xefae979ed29fL));
        list.add(new HitData(24014641345288850L, 12040, 0xe2e44c2f3563L));
        list.add(new HitData(24014641345292967L, 12040, 0x87a7b9eb12aaL));
        list.add(new HitData(24014641345294341L, 12039, 0xf4c0db8e6918L));
        list.add(new HitData(24014641345294563L, 12039, 0xf7178188b3fdL));
        list.add(new HitData(24014641345296853L, 12029, 0x396a5d961de1L));
        list.add(new HitData(24014641345297616L, 12029, 0xf49ee981a008L));
        list.add(new HitData(24014641358277149L, 12040, 0xd8010bc749c2L));
        list.add(new HitData(24014641358280326L, 12040, 0x3dac398e0d9dL));
        list.add(new HitData(24014641383975191L, 12040, 0x98a02fd6dbbbL));
        list.add(new HitData(24014641383981907L, 12040, 0x87a7b9eb12aaL));
        list.add(new HitData(24014641390921083L, 12046, 0xa979e9acaf2eL));
        list.add(new HitData(24014641390941545L, 12030, 0x820a6a039815L));
        list.add(new HitData(24014641390942116L, 12030, 0x6c7b32bac895L));
        list.add(new HitData(24014641390942371L, 12030, 0xfac5284c8165L));
        list.add(new HitData(24014641390944949L, 12030, 0xc0f1ad586123L));
        list.add(new HitData(24014641390945539L, 12039, 0xc752d559fa24L));
        list.add(new HitData(24014641390946713L, 12039, 0x9f78a4f96b83L));
        list.add(new HitData(24014641390948495L, 12039, 0x087c5871c6a5L));
        list.add(new HitData(24014641390952804L, 12039, 0x0a08de118fecL));
        list.add(new HitData(24014641407320059L, 12039, 0x8b56bf63b908L));
        list.add(new HitData(24014641407320308L, 12039, 0x9bc72c6ea776L));
        list.add(new HitData(24014641407320455L, 12039, 0xa767a48cfe8aL));
        list.add(new HitData(24014641407321433L, 12039, 0x7829d43058f7L));
        list.add(new HitData(24014641407323174L, 12039, 0xef089997c7a2L));
        list.add(new HitData(24014641407324706L, 12039, 0xc4b2d9e19419L));
        list.add(new HitData(24014641407326191L, 12030, 0xf67f7b185ef7L));
        list.add(new HitData(24014641407326711L, 12030, 0x866c809f3657L));
        list.add(new HitData(24014641407326810L, 12030, 0x1cbe412e2d37L));
        list.add(new HitData(24014641407326919L, 12030, 0xd2a0f0a1b941L));
        list.add(new HitData(24014641407327370L, 12030, 0x58a90e542f8eL));
        list.add(new HitData(24014641407328687L, 12039, 0xd815cc08657fL));
        list.add(new HitData(24014641407329114L, 12030, 0x5616e19cb827L));
        list.add(new HitData(24014641407330270L, 12030, 0x02cbcbc2c8bdL));
        list.add(new HitData(24014641417350459L, 12039, 0x64581493a40bL));
        list.add(new HitData(24014641417351219L, 12039, 0x78f7ff2c2829L));
        list.add(new HitData(24014641417353855L, 12039, 0x03e493c9dd49L));
        list.add(new HitData(24014641417354281L, 12039, 0x087c5871c6a5L));
        list.add(new HitData(24014641437384053L, 12047, 0x5140bda6e07bL));
        list.add(new HitData(24014641437386797L, 12046, 0x5f5c45dbb89fL));
        list.add(new HitData(24014641437388083L, 12046, 0x33fe5a61e3c4L));
        list.add(new HitData(24014641437389249L, 12046, 0xf634ed54c5f0L));
        list.add(new HitData(24014641437391762L, 12046, 0xc51dfaa9f463L));
        list.add(new HitData(24014641437397247L, 12046, 0x3ee49849ffa6L));
        list.add(new HitData(24014641439046339L, 12067, 0xd307af164254L));
        list.add(new HitData(24014641440067090L, 12047, 0x400e34e99096L));
        list.add(new HitData(24014641440073055L, 12047, 0xed17776c9aa8L));
        list.add(new HitData(24014641443888235L, 12029, 0xf4610aed678cL));
        list.add(new HitData(24014641443889187L, 12029, 0xf765a906660eL));
        list.add(new HitData(24014641445956621L, 12039, 0x19a31737fcbcL));
        list.add(new HitData(24014641445957535L, 12039, 0x3f48845900d0L));
        list.add(new HitData(24014641445958529L, 12039, 0x9bc72c6ea776L));
        list.add(new HitData(24014641445958589L, 12039, 0xba3dbfcffc51L));
        list.add(new HitData(24014641449533864L, 12029, 0x1551e2c72d57L));
        list.add(new HitData(24014641449537747L, 12029, 0x80bf89d1a264L));
        list.add(new HitData(24014641466788345L, 12030, 0xbb6614295b8cL));
        list.add(new HitData(24014641466792183L, 12030, 0x7e2b00bad815L));
        list.add(new HitData(24014641473711960L, 12067, 0x9b54b20558bdL));
        list.add(new HitData(24014641473712347L, 12067, 0xd307af164254L));
        list.add(new HitData(24014641473712705L, 12067, 0xa29598735544L));
        list.add(new HitData(24014641473713047L, 12067, 0xaf90a0a41adeL));
        list.add(new HitData(24014641473713736L, 12067, 0x06614a6ffcd4L));
        list.add(new HitData(24014641473715156L, 12067, 0x8a3dc4b3c68eL));
        list.add(new HitData(24014641473720305L, 12067, 0x1713d9af3072L));
        list.add(new HitData(24014641473721640L, 12067, 0xd897bae245bbL));
        list.add(new HitData(24014641473723180L, 12067, 0x729a1180042dL));
        list.add(new HitData(24014641496403959L, 12047, 0xb3950d840546L));
        list.add(new HitData(24014641496412180L, 12038, 0x02e07c2ba458L));
        list.add(new HitData(24014641496412796L, 12038, 0x12a8d213e2acL));
        list.add(new HitData(24014641503259721L, 12048, 0xf578db9a8423L));
        list.add(new HitData(24014641507507439L, 12059, 0x8e78563d2179L));
        list.add(new HitData(24014641507508463L, 12059, 0x1ab4a406af2dL));
        list.add(new HitData(24014641511400334L, 12029, 0xc7f5e1b27dd2L));
        list.add(new HitData(24014641511408430L, 12029, 0xb352db1a3e9aL));
        list.add(new HitData(24014641518776104L, 12067, 0x3f028cb686b7L));
        list.add(new HitData(24014641518776353L, 12067, 0x6c62cee79e41L));
        list.add(new HitData(24014641518776651L, 12067, 0x2869b3f98c1fL));
        list.add(new HitData(24014641518778626L, 12067, 0x6e5557701717L));
        list.add(new HitData(24014641518779277L, 12067, 0xe44c73438cb0L));
        list.add(new HitData(24014641519073829L, 12029, 0xa59c9e8c40b6L));
        list.add(new HitData(24014641519079489L, 12029, 0xb5e30e459da4L));
        list.add(new HitData(24014641519926467L, 12067, 0xa29598735544L));
        list.add(new HitData(24014641531509929L, 12039, 0x93fdad986c6fL));
        list.add(new HitData(24014641531511725L, 12039, 0x0b1d26a591b0L));
        list.add(new HitData(24014641532468452L, 12046, 0x5e59556bd24cL));
        list.add(new HitData(24014641540047356L, 12067, 0xc65d57f9ef16L));
        list.add(new HitData(24014641540052770L, 12067, 0x066b7ebe2c26L));
        list.add(new HitData(24014641540056989L, 12040, 0xefae979ed29fL));
        list.add(new HitData(24014641540057361L, 12040, 0xe2e44c2f3563L));
        list.add(new HitData(24014641541207370L, 12021, 0x32bb0201e5a7L));
        list.add(new HitData(24014641541208364L, 12021, 0xfe92d7ff4480L));
        list.add(new HitData(24014641541208862L, 12021, 0x38ae7fdfc4c7L));
        list.add(new HitData(24014641541212048L, 12021, 0x72fecfd94382L));
        list.add(new HitData(24014641547378677L, 12050, 0x91c65f3c9956L));
        list.add(new HitData(24014641547379010L, 12050, 0x444cbb6e075cL));
        list.add(new HitData(24014641547380315L, 12050, 0x4baa79b64278L));
        list.add(new HitData(24014641547380428L, 12050, 0x5b5cd7a4fb85L));
        list.add(new HitData(24014641547380690L, 12050, 0x9d5d0842334eL));
        list.add(new HitData(24014641552740313L, 12067, 0xd307af164254L));
        list.add(new HitData(24014641553115830L, 12038, 0x6432e28f770fL));
        list.add(new HitData(24014641553116489L, 12038, 0x811227c3746cL));
        list.add(new HitData(24014641553116966L, 12038, 0x4d983ea5297aL));
        list.add(new HitData(24014641554619250L, 12050, 0x56c3aab8b518L));
        list.add(new HitData(24014641554619427L, 12050, 0xaaaabc1deec3L));
        list.add(new HitData(24014641557366004L, 12059, 0x83d8f521b859L));
        list.add(new HitData(24014641557367325L, 12059, 0xb47fbd7d6299L));
        list.add(new HitData(24014641557369789L, 12067, 0x091be8177ab9L));
        list.add(new HitData(24014641557376230L, 12067, 0xd51568afe402L));
        list.add(new HitData(24014641565142932L, 12065, 0xd97c010a948bL));
        list.add(new HitData(24014641565143707L, 12065, 0x70e229a3d5ebL));
        list.add(new HitData(24014641565143937L, 12065, 0x359b91e881e6L));
        list.add(new HitData(24014641565144440L, 12065, 0xdb32d1a4bdb4L));
        list.add(new HitData(24014641565144499L, 12065, 0x8ca33eaaaad0L));
        list.add(new HitData(24014641565145081L, 12065, 0x6584af07a218L));
        list.add(new HitData(24014641565149160L, 12065, 0x6204d28017f5L));
        list.add(new HitData(24014641565151941L, 12065, 0xec9dc048fc34L));
        list.add(new HitData(24014641565152123L, 12065, 0xfc2bb7c46ce5L));
        list.add(new HitData(24014641573729969L, 12021, 0xe53c98680186L));
        list.add(new HitData(24014641573730818L, 12021, 0xb804f6f38a45L));
        list.add(new HitData(24014641573730939L, 12021, 0x16a5b9112b00L));
        list.add(new HitData(24014641573732411L, 12021, 0x4255e2b1c79fL));
        list.add(new HitData(24014641573732656L, 12021, 0x858a79abc807L));
        list.add(new HitData(24014641573745782L, 12049, 0x6d7d54d2f112L));
        list.add(new HitData(24014641573751005L, 12049, 0x006714f851d4L));
        list.add(new HitData(24014641573751677L, 12059, 0xcd573e1e4b1aL));
        list.add(new HitData(24014641573753192L, 12059, 0x5eef7f4656bbL));
        list.add(new HitData(24014641587599442L, 12046, 0x895c42df4492L));
        list.add(new HitData(24014641587599795L, 12046, 0x1f64ae8147c5L));
        list.add(new HitData(24014641587599966L, 12046, 0xfeb392361dbdL));
        list.add(new HitData(24014641587600658L, 12046, 0xf2d6f61aa895L));
        list.add(new HitData(24014641599647659L, 12067, 0xaf90a0a41adeL));
        list.add(new HitData(24014641600873940L, 12066, 0xc09ad7a7ea16L));
        list.add(new HitData(24014641600876483L, 12066, 0x667f78126c95L));
        list.add(new HitData(24014641604222596L, 12021, 0x85d69873ad04L));
        list.add(new HitData(24014641604229024L, 12021, 0x72fecfd94382L));
        list.add(new HitData(24014641606781695L, 12050, 0x9662aea5669eL));
        list.add(new HitData(24014641606781902L, 12050, 0x137b441e8e46L));
        list.add(new HitData(24014641606784792L, 12050, 0x8c7165c1ee37L));
        list.add(new HitData(24014641606787330L, 12050, 0xcb5d0a5f04c9L));
        list.add(new HitData(24014641606787878L, 12050, 0xf1ef76a51884L));
        list.add(new HitData(24014641606788891L, 12049, 0x08b13a790a37L));
        list.add(new HitData(24014641606791314L, 12040, 0x3dac398e0d9dL));
        list.add(new HitData(24014641606791655L, 12040, 0xd54044599535L));
        list.add(new HitData(24014641617160349L, 12065, 0xfc2bb7c46ce5L));
        list.add(new HitData(24014641617160404L, 12065, 0x6584af07a218L));
        list.add(new HitData(24014641617164518L, 12065, 0x359b91e881e6L));
        list.add(new HitData(24014641626294428L, 12065, 0x0923e78e8b10L));
        list.add(new HitData(24014641626301818L, 12073, 0x02aeeef35302L));
        list.add(new HitData(24014641626301883L, 12073, 0x144b7b85d859L));
        list.add(new HitData(24014641626302593L, 12073, 0x416e145910adL));
        list.add(new HitData(24014641628338837L, 12029, 0xffa332fbf3a4L));
        list.add(new HitData(24014641628338990L, 12029, 0x134cbe8a1688L));
        list.add(new HitData(24014641631296723L, 12067, 0x091be8177ab9L));
        list.add(new HitData(24014641635152648L, 12073, 0x2911d83d429bL));
        list.add(new HitData(24014641635161599L, 12073, 0x144211cb2ed5L));
        list.add(new HitData(24014641635485407L, 12029, 0x14be85ac9369L));
        list.add(new HitData(24014641635485413L, 12029, 0x1375759346b9L));
        list.add(new HitData(24014641635487571L, 12029, 0xe003d4bb5d53L));
        list.add(new HitData(24014641635488408L, 12029, 0xf765a906660eL));
        list.add(new HitData(24014641635488782L, 12029, 0xb897262bf58aL));
        list.add(new HitData(24014641635491680L, 12039, 0x9f78a4f96b83L));
        list.add(new HitData(24014641635501034L, 12039, 0xc752d559fa24L));
        list.add(new HitData(24014641640455793L, 12029, 0xfc11ec76b61cL));
        list.add(new HitData(24014641640456065L, 12029, 0x0f8b70fee5bdL));
        list.add(new HitData(24014641640456272L, 12029, 0x3513aaef1b5bL));
        list.add(new HitData(24014641640456359L, 12029, 0xb5e30e459da4L));
        list.add(new HitData(24014641640457023L, 12029, 0x6b22691c7809L));
        list.add(new HitData(24014641640457714L, 12029, 0xa59c9e8c40b6L));
        list.add(new HitData(24014641640460600L, 12029, 0x426393f54b3cL));
        list.add(new HitData(24014641640540565L, 12029, 0xfc11ec76b61cL));
        list.add(new HitData(24014641640547051L, 12029, 0x0f8b70fee5bdL));
        list.add(new HitData(24014641641226643L, 12038, 0x2ba475921bffL));
        list.add(new HitData(24014641641227684L, 12038, 0xbcd324dddc3dL));
        list.add(new HitData(24014641641245933L, 12066, 0x331f61511a11L));
        list.add(new HitData(24014641641246097L, 12066, 0x43149cdc4362L));
        list.add(new HitData(24014641641249904L, 12066, 0xc09ad7a7ea16L));
        list.add(new HitData(24014641643102379L, 12050, 0x5b5cd7a4fb85L));
        list.add(new HitData(24014641643102687L, 12050, 0x444cbb6e075cL));
        list.add(new HitData(24014641643104394L, 12050, 0x91c65f3c9956L));
        list.add(new HitData(24014641643106056L, 12050, 0x4baa79b64278L));
        list.add(new HitData(24014641643366702L, 12057, 0x6a177631b1f9L));
        list.add(new HitData(24014641656745426L, 12046, 0xd758cad34d26L));
        list.add(new HitData(24014641656752643L, 12046, 0x9a1b31d3ebe5L));
        list.add(new HitData(24014641664724980L, 12073, 0x97785ea71b24L));
        list.add(new HitData(24014641669042645L, 12046, 0x9a1b31d3ebe5L));
        list.add(new HitData(24014641669046488L, 12046, 0x5352ea563daeL));
        list.add(new HitData(24014641674731080L, 12039, 0x965883cfbedbL));
        list.add(new HitData(24014641674738952L, 12039, 0x0a08de118fecL));
        list.add(new HitData(24014641675810791L, 12065, 0xdb32d1a4bdb4L));
        list.add(new HitData(24014641675811309L, 12065, 0x45277d29f268L));
        list.add(new HitData(24014641675815942L, 12065, 0x349c9e233928L));
        list.add(new HitData(24014641686392092L, 12066, 0x0af1d2727f85L));
        list.add(new HitData(24014641686393265L, 12067, 0x8a3dc4b3c68eL));
        list.add(new HitData(24014641686394870L, 12066, 0xdb21e2db154fL));
        list.add(new HitData(24014641686395349L, 12067, 0x1713d9af3072L));
        list.add(new HitData(24014641686397844L, 12059, 0x9b60779fe19fL));
        list.add(new HitData(24014641686398413L, 12059, 0x307106e2b5ccL));
        list.add(new HitData(24014641686398656L, 12066, 0xfce45a8cb7e9L));
        list.add(new HitData(24014641686399304L, 12066, 0x581a272aeb03L));
        list.add(new HitData(24014641686403172L, 12040, 0x2706114d34dfL));
        list.add(new HitData(24014641686403453L, 12040, 0xb804e4971533L));
        list.add(new HitData(24014641686403714L, 12040, 0xa40f157a0658L));
        list.add(new HitData(24014641686404472L, 12040, 0xc6224ecabf6fL));
        list.add(new HitData(24014641686404513L, 12040, 0x8d84a21acb2dL));
        list.add(new HitData(24014641686406004L, 12040, 0x57b56c6739a6L));
        list.add(new HitData(24014641686409554L, 12040, 0x3e1e4659ea06L));
        list.add(new HitData(24014641688562019L, 12040, 0x7ce10459887eL));
        list.add(new HitData(24014641688568392L, 12040, 0x06a01c105181L));
        list.add(new HitData(24014641689025758L, 12040, 0x17b7f9bc90f2L));
        list.add(new HitData(24014641689030399L, 12040, 0x74578c1716d2L));
        list.add(new HitData(24014641695180658L, 12040, 0xd8010bc749c2L));
        list.add(new HitData(24014641695182775L, 12040, 0x17b7f9bc90f2L));
        list.add(new HitData(24014641697409730L, 12047, 0x43a20bfe10d6L));
        list.add(new HitData(24014641697417560L, 12047, 0x400e34e99096L));
        list.add(new HitData(24014641704062458L, 12072, 0xd6ab8075e2fbL));
        list.add(new HitData(24014641706444004L, 12048, 0x55c986ace722L));
        list.add(new HitData(24014641710433024L, 12072, 0x7cd75e9125d2L));
        list.add(new HitData(24014641713525144L, 12057, 0xca64bc22dac2L));
        list.add(new HitData(24014641713536090L, 12065, 0xd4ff422e05b5L));
        list.add(new HitData(24014641723664850L, 12030, 0xb1fc89162c0bL));
        list.add(new HitData(24014641724910975L, 12021, 0x66504b293808L));
        list.add(new HitData(24014641724914689L, 12021, 0x5b9ae7909352L));
        list.add(new HitData(24014641726479648L, 12040, 0x80134b136c39L));
        list.add(new HitData(24014641726481047L, 12040, 0x57b56c6739a6L));
        list.add(new HitData(24014641726484050L, 12049, 0xc861a5ae476aL));
        list.add(new HitData(24014641728296150L, 12040, 0x80134b136c39L));
        list.add(new HitData(24014641728296508L, 12040, 0x3e1e4659ea06L));
        list.add(new HitData(24014641740027951L, 12021, 0x72fecfd94382L));
        list.add(new HitData(24014641742468691L, 12049, 0xb9293b8feb2bL));
        list.add(new HitData(24014641757133861L, 12065, 0xec9dc048fc34L));
        list.add(new HitData(24014641765021728L, 12073, 0xb188cd497484L));
        list.add(new HitData(24014641765024792L, 12073, 0x416e145910adL));
        list.add(new HitData(24014641767777092L, 12046, 0xee02a0020ce6L));
        list.add(new HitData(24014641767780527L, 12046, 0xe8fd68861537L));
        list.add(new HitData(24014641782390207L, 12030, 0xf4bddca5717cL));
        list.add(new HitData(24014641782390226L, 12030, 0x61487316ca54L));
        list.add(new HitData(24014641782390735L, 12030, 0xb7f25f056104L));
        list.add(new HitData(24014641782392047L, 12030, 0x98a9e8e72cf1L));
        list.add(new HitData(24014641782401924L, 12021, 0x916ceadb214dL));
        list.add(new HitData(24014641782402362L, 12021, 0x16a5b9112b00L));
        list.add(new HitData(24014641782406407L, 12021, 0x8b9f35308e27L));
        list.add(new HitData(24014641782414401L, 12021, 0x57bb7c43b042L));
        list.add(new HitData(24014641786576868L, 12072, 0x95a5e41f1dfdL));
        list.add(new HitData(24014641789343801L, 12029, 0xb897262bf58aL));
        list.add(new HitData(24014641789352180L, 12029, 0x7b25f215b425L));
        list.add(new HitData(24014641789354657L, 12057, 0x7c113f8e4709L));
        list.add(new HitData(24014641789358251L, 12057, 0x919d4a9a9b02L));
        list.add(new HitData(24014641789366429L, 12072, 0x46f402ad2d21L));
        list.add(new HitData(24014641789366506L, 12072, 0xcb127bc2ed6bL));
        list.add(new HitData(24014641789367314L, 12072, 0xb447984564dfL));
        list.add(new HitData(24014641789367412L, 12072, 0x42a50c08b545L));
        list.add(new HitData(24014641789368999L, 12072, 0x8e23c7c0cdc1L));
        list.add(new HitData(24014641792654119L, 12047, 0x6aa68f2742c8L));
        list.add(new HitData(24014641792655049L, 12047, 0x400e34e99096L));
        list.add(new HitData(24014641794660721L, 12073, 0xae92b55a541fL));
        list.add(new HitData(24014641794661082L, 12073, 0x3ea2812c77e0L));
        list.add(new HitData(24014641794663414L, 12073, 0x94034ed50ac9L));
        list.add(new HitData(24014641794664890L, 12073, 0xf7c070310592L));
        list.add(new HitData(24014641796465263L, 12050, 0xbeb37f6943b8L));
        list.add(new HitData(24014641796472009L, 12050, 0xb2c4461c8b5fL));
        list.add(new HitData(24014641796952766L, 12066, 0x3096fbff3f93L));
        list.add(new HitData(24014641796954754L, 12066, 0xa3ae4b646a70L));
        list.add(new HitData(24014641799728304L, 12030, 0x724f1ae21fe6L));
        list.add(new HitData(24014641799729227L, 12030, 0x61487316ca54L));
        list.add(new HitData(24014641799730242L, 12030, 0xeb9f5614053eL));
        list.add(new HitData(24014641799731267L, 12030, 0xe9ca36873110L));
        list.add(new HitData(24014641799732332L, 12030, 0xb7f25f056104L));
        list.add(new HitData(24014641799732376L, 12030, 0xa37441207611L));
        list.add(new HitData(24014641803248648L, 12047, 0x031811262140L));
        list.add(new HitData(24014641803258295L, 12047, 0x6e9d3e508910L));
        list.add(new HitData(24014641806719639L, 12038, 0xbcd324dddc3dL));
        list.add(new HitData(24014641806720316L, 12038, 0x9a97dd8b6516L));
        list.add(new HitData(24014641806720457L, 12038, 0xfc385400b72aL));
        list.add(new HitData(24014641806720818L, 12038, 0x6432e28f770fL));
        list.add(new HitData(24014641806720940L, 12029, 0x81f0a16ec914L));
        list.add(new HitData(24014641806720970L, 12038, 0xa0d899b577cbL));
        list.add(new HitData(24014641806721629L, 12029, 0xc992e597a438L));
        list.add(new HitData(24014641806722444L, 12038, 0x811227c3746cL));
        list.add(new HitData(24014641806724321L, 12038, 0x798e62def158L));
        list.add(new HitData(24014641806726384L, 12038, 0xe0b0716d897eL));
        list.add(new HitData(24014641806729167L, 12038, 0x02e07c2ba458L));
        list.add(new HitData(24014641808400443L, 12046, 0xd99fda201f20L));
        list.add(new HitData(24014641808401177L, 12046, 0xe68a6acda6b4L));
        list.add(new HitData(24014641809551647L, 12048, 0xbbf321953f5aL));
        list.add(new HitData(24014641809555240L, 12048, 0x166817e1fbd9L));
        list.add(new HitData(24014641811809471L, 12073, 0xae92b55a541fL));
        list.add(new HitData(24014641811811622L, 12073, 0x3ea2812c77e0L));
        list.add(new HitData(24014641812129828L, 12065, 0xe5b9c62cda24L));
        list.add(new HitData(24014641812135019L, 12065, 0x4d5e6c4d16a6L));
        list.add(new HitData(24014641821602949L, 12029, 0x6b22691c7809L));
        list.add(new HitData(24014641825940979L, 12046, 0x5352ea563daeL));
        list.add(new HitData(24014641825941091L, 12046, 0xd601cd46b263L));
        list.add(new HitData(24014641825946915L, 12046, 0x20dca7d5a9e5L));
        list.add(new HitData(24014641829696757L, 12040, 0xd0995f822b43L));
        list.add(new HitData(24014641829698001L, 12040, 0x882f452eba8fL));
        list.add(new HitData(24014641829699513L, 12040, 0x488e1c575627L));
        list.add(new HitData(24014641829703377L, 12021, 0xb804f6f38a45L));
        list.add(new HitData(24014641829703516L, 12021, 0xe53c98680186L));
        list.add(new HitData(24014641829706732L, 12021, 0x16a5b9112b00L));
        list.add(new HitData(24014641829707239L, 12021, 0x427ea29c4bb5L));
        list.add(new HitData(24014641829718886L, 12021, 0xd638529ba5adL));
        list.add(new HitData(24014641829720228L, 12021, 0xf004760c91e6L));
        list.add(new HitData(24014641829835131L, 12048, 0x4853a9933e7dL));
        list.add(new HitData(24014641829836031L, 12048, 0x5f6cd6cec834L));
        list.add(new HitData(24014641838254774L, 12030, 0x61487316ca54L));
        list.add(new HitData(24014641838254790L, 12030, 0xf4bddca5717cL));
        list.add(new HitData(24014641838255292L, 12030, 0xf3857903416dL));
        list.add(new HitData(24014641840236264L, 12067, 0xd51568afe402L));
        list.add(new HitData(24014641840237931L, 12067, 0x1248d1f7b12bL));
        list.add(new HitData(24014641840239210L, 12067, 0x35daf4dd3653L));
        list.add(new HitData(24014641843112174L, 12067, 0x1248d1f7b12bL));
        list.add(new HitData(24014641848872254L, 12059, 0xe12b26b755eaL));
        list.add(new HitData(24014641848872707L, 12059, 0x7eb4dfe7d988L));
        list.add(new HitData(24014641848872760L, 12059, 0x26c8d1767fceL));
        list.add(new HitData(24014641848873796L, 12059, 0x1ab4a406af2dL));
        list.add(new HitData(24014641848874867L, 12059, 0x02496b7fcac7L));
        list.add(new HitData(24014641848874971L, 12059, 0x8e78563d2179L));
        list.add(new HitData(24014641848876069L, 12059, 0xe6dc25b457fdL));
        list.add(new HitData(24014641848878753L, 12059, 0x73659395df5eL));
        list.add(new HitData(24014641849095087L, 12040, 0x882f452eba8fL));
        list.add(new HitData(24014641849102733L, 12040, 0x53764a27a084L));
        list.add(new HitData(24014641850156429L, 12039, 0x7829d43058f7L));
        list.add(new HitData(24014641850156547L, 12039, 0xef089997c7a2L));
        list.add(new HitData(24014641852593862L, 12057, 0x4b9b86a719c4L));
        list.add(new HitData(24014641852595316L, 12057, 0x9db074a86547L));
        list.add(new HitData(24014641852596188L, 12057, 0xbd483327b420L));
        list.add(new HitData(24014641852596733L, 12057, 0xaec80a53122cL));
        list.add(new HitData(24014641852598479L, 12057, 0xc58d0397e2c5L));
        list.add(new HitData(24014641852598995L, 12057, 0xac4bf0dc3520L));
        list.add(new HitData(24014641852599505L, 12057, 0x529b0aea563eL));
        list.add(new HitData(24014641852600389L, 12057, 0xc9da531b2a3bL));
        list.add(new HitData(24014641852601847L, 12047, 0x7daebc111939L));
        list.add(new HitData(24014641852602106L, 12047, 0x8e7008deff55L));
        list.add(new HitData(24014641852602753L, 12047, 0xed17776c9aa8L));
        list.add(new HitData(24014641852604783L, 12056, 0x178fb493ef75L));
        list.add(new HitData(24014641852608685L, 12046, 0xf7e6ff439090L));
        list.add(new HitData(24014641852608990L, 12046, 0x04da1c75b9fbL));
        list.add(new HitData(24014641852609262L, 12046, 0x06fce7d3cef3L));
        list.add(new HitData(24014641852612458L, 12046, 0x7ac5ba8d796cL));
        list.add(new HitData(24014641852622123L, 12046, 0xa94e7dcdf7b4L));
        list.add(new HitData(24014641853667654L, 12046, 0xf2d6f61aa895L));
        list.add(new HitData(24014641853670942L, 12046, 0x7ac5ba8d796cL));
        list.add(new HitData(24014641853688564L, 12072, 0x03728e4a666dL));
        list.add(new HitData(24014641853693415L, 12072, 0x46f402ad2d21L));
        list.add(new HitData(24014641856240433L, 12073, 0x55c4f65e789bL));
        list.add(new HitData(24014641856242227L, 12073, 0x3ea2812c77e0L));
        list.add(new HitData(24014641865952875L, 12049, 0x7a06273239a8L));
        list.add(new HitData(24014641865953642L, 12049, 0xf7809fdb01c0L));
        list.add(new HitData(24014641865953981L, 12049, 0xe6e30655009bL));
        list.add(new HitData(24014641865954703L, 12049, 0x8bbbb008b86fL));
        list.add(new HitData(24014641865954849L, 12049, 0x8074af27dcc2L));

        return list;
    }

    private static List<ISourceID> getSourceIds(List<HitData> hitList)
    {
        HashMap<ISourceID, ISourceID> map = new HashMap<ISourceID, ISourceID>();

        for (HitData hd : hitList) {
            MockSourceID newSrc = new MockSourceID(hd.getSourceID());

            if (!map.containsKey(newSrc)) {
                map.put(newSrc, newSrc);
            }
        }

        return new ArrayList(map.keySet());
    }

    protected void setUp()
        throws Exception
    {
        super.setUp();

        appender.clear();

        BasicConfigurator.resetConfiguration();
        BasicConfigurator.configure(appender);

        //openFiles = new ListOpenFiles(ListOpenFiles.getpid());
    }

    public static Test suite()
    {
        return new TestSuite(EBReadonlyTest.class);
    }

    protected void tearDown()
        throws Exception
    {
        assertEquals("Bad number of log messages",
                     0, appender.getNumberOfMessages());

        if (ebComp != null) ebComp.closeAll();
        if (gtComp != null) gtComp.closeAll();
        if (iiComp != null) iiComp.closeAll();

        if (iiTails != null) {
            for (int i = 0; i < iiTails.length; i++) {
                try {
                    iiTails[i].sink().close();
                } catch (IOException ioe) {
                    // ignore errors on close
                }
                try {
                    iiTails[i].source().close();
                } catch (IOException ioe) {
                    // ignore errors on close
                }
            }
        }

        //openFiles.diff(true, true);

        super.tearDown();
    }

    public void testEndToEnd()
        throws DAQCompException, DataFormatException, IOException,
               SplicerException, TriggerException
    {
        final int numEventsBeforeReadOnly = 10;

        File cfgFile =
            DAQTestUtil.buildConfigFile(getClass().getResource("/").getPath(),
                                        "in-ice-mbt-5");

        IDOMRegistry domRegistry;
        try {
            domRegistry = DOMRegistry.loadRegistry(cfgFile.getParent());
        } catch (Exception ex) {
            throw new Error("Cannot load DOM registry", ex);
        }

        // get list of all hits
        List<HitData> hitList = getInIceHits(domRegistry);

        PayloadValidator validator = new TriggerValidator();

        // set up event builder
        ebComp = new EBComponent(true);
        ebComp.start(false);
        ebComp.setRunNumber(RUN_NUMBER);
        ebComp.setDispatchDestStorage(System.getProperty("java.io.tmpdir"));
        ebComp.setGlobalConfigurationDir(cfgFile.getParent());

        IByteBufferCache evtDataCache =
            ebComp.getDispatcher().getByteBufferCache();
        MockDispatcher disp = new MockDispatcher(evtDataCache);
        ebComp.setDispatcher(disp);

        disp.setReadOnlyTrigger(numEventsBeforeReadOnly);

        Map<ISourceID, RequestToDataBridge> bridgeMap =
            RequestToDataBridge.createLinks(ebComp.getRequestWriter(), null,
                                            ebComp.getDataReader(),
                                            ebComp.getDataCache(),
                                            hitList);

        List<ISourceID> idList = new ArrayList<ISourceID>(bridgeMap.keySet());

        // set up global trigger
        gtComp = new GlobalTriggerComponent();
        gtComp.setGlobalConfigurationDir(cfgFile.getParent());
        gtComp.start(false);

        gtComp.configuring(cfgFile.getName());

        DAQTestUtil.glueComponents("GT->EB",
                                   gtComp.getWriter(), gtComp.getOutputCache(),
                                   validator,
                                   ebComp.getTriggerReader(),
                                   ebComp.getTriggerCache());

        // set up in-ice trigger
        iiComp = new IniceTriggerComponent();
        iiComp.setGlobalConfigurationDir(cfgFile.getParent());
        iiComp.start(false);

        iiComp.configuring(cfgFile.getName());

        DAQTestUtil.glueComponents("IIT->GT",
                                   iiComp.getWriter(), iiComp.getOutputCache(),
                                   validator,
                                   gtComp.getReader(), gtComp.getInputCache());

        iiTails = DAQTestUtil.connectToReader(iiComp.getReader(),
                                              iiComp.getInputCache(),
                                              idList.size());

        DAQTestUtil.startComponentIO(ebComp, gtComp, null, iiComp, null, null);

        ByteBuffer simpleBuf = ByteBuffer.allocate(HitData.SIMPLE_LENGTH);
        for (HitData hd : hitList) {
            simpleBuf.clear();
            hd.putSimple(simpleBuf);
            simpleBuf.flip();

            boolean written = false;
            for (int i = 0; i < iiTails.length; i++) {
                ISourceID srcId = idList.get(i);
                if (srcId.getSourceID() == hd.getSourceID()) {
                    iiTails[i].sink().write(simpleBuf);
                    written = true;
                    break;
                }
            }

            if (!written) {
                fail("Couldn't write to source " + hd.getSourceID());
            }
        }

        DAQTestUtil.sendStops(iiTails);

        ActivityMonitor activity =
            new ActivityMonitor(iiComp, null, null, gtComp, ebComp);
        activity.waitForStasis(10, 100, numEventsBeforeReadOnly, false, false);

        DAQTestUtil.waitUntilStopped(iiComp.getReader(), iiComp.getSplicer(),
                                     "IIInStopMsg", "", 1000, 100);

        DAQTestUtil.waitUntilStopped(iiComp.getWriter(), null, "IIOutStopMsg");
        DAQTestUtil.waitUntilStopped(gtComp.getReader(), gtComp.getSplicer(),
                                     "GTInStopMsg");

        // NOTE: EB input is stopped due to read-only error
        // so remaining output engines will not be stopped

        //assertTrue("Expected GTOut to be running",
        //           gtComp.getWriter().isRunning());
        DAQTestUtil.waitUntilStopped(ebComp.getTriggerReader(), null,
                                     "EBTrigStopMsg");

        //assertTrue("Expected EBReqOut to be running",
        //           ebComp.getRequestWriter().isRunning());
        //assertTrue("Expected EBDataIn to be running",
        //           ebComp.getDataReader().isRunning());
        //assertTrue("Expected EBDataSplicer to be running",
        //           ebComp.getDataSplicer().getState() == Splicer.STARTED);

        assertEquals("Unexpected number of events sent",
                     numEventsBeforeReadOnly, ebComp.getEventsSent());

        //DAQTestUtil.checkCaches(ebComp, gtComp, null, iiComp, null, null);

/*
        assertTrue("GTOutCache does not have a backlog",
                   gtComp.getOutputCache().getCurrentAquiredBuffers() > 0);
*/

        // Ignore extra log msgs
        appender.clear();
    }

    public static void main(String[] args)
    {
        TestRunner.run(suite());
    }
}
