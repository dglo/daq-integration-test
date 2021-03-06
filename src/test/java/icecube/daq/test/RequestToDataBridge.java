package icecube.daq.test;

import icecube.daq.common.EventVersion;
import icecube.daq.io.DAQSourceIdOutputProcess;
import icecube.daq.io.DAQStreamReader;
import icecube.daq.payload.impl.PayloadFactory;
import icecube.daq.payload.IByteBufferCache;
import icecube.daq.payload.IPayload;
import icecube.daq.payload.IReadoutRequest;
import icecube.daq.payload.IReadoutRequestElement;
import icecube.daq.payload.ISourceID;
import icecube.daq.payload.PayloadException;
import icecube.daq.payload.PayloadRegistry;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

public class RequestToDataBridge
    extends PayloadFileBridge
{
    private static final Logger LOG = Logger.getLogger(RequestToDataBridge.class);

    private static short nextNum;

    private ISourceID srcId;
    private List<HitData> hitList;
    private int numRcvd;
    private int numEmpty;
    private int numSent;
    private int numDone;

    private PayloadFactory factory;

    private RequestToDataBridge(ISourceID srcId, ReadableByteChannel reqIn,
                          WritableByteChannel dataOut, List<HitData> hitList)
    {
        super(srcId.toString(), reqIn, dataOut);

        this.srcId = srcId;
        this.hitList = hitList;
    }

    public static Map<ISourceID, RequestToDataBridge>
        createLinks(DAQSourceIdOutputProcess reqOut,
                    PayloadValidator validator, DAQStreamReader dataIn,
                    IByteBufferCache dataCache, List<HitData> hitList)
        throws IOException
    {
        HashMap<ISourceID, RequestToDataBridge> bridgeMap =
            new HashMap<ISourceID, RequestToDataBridge>();

        for (ISourceID srcId : getSourceIds(hitList)) {
            Pipe outPipe = Pipe.open();

            Pipe.SinkChannel sinkOut = outPipe.sink();
            sinkOut.configureBlocking(false);

            Pipe.SourceChannel srcOut = outPipe.source();
            srcOut.configureBlocking(true);

            reqOut.addDataChannel(sinkOut, srcId);

            Pipe inPipe = Pipe.open();

            Pipe.SinkChannel sinkIn = inPipe.sink();
            sinkIn.configureBlocking(true);

            Pipe.SourceChannel srcIn = inPipe.source();
            srcIn.configureBlocking(false);

            dataIn.addDataChannel(srcIn, "r2dbChan", dataCache, 1024);

            RequestToDataBridge bridge =
                new RequestToDataBridge(srcId, srcOut, sinkIn, hitList);
            bridge.setValidator(validator);
            bridge.start();

            bridgeMap.put(srcId, bridge);
        }

        return bridgeMap;
    }

    private List<HitData> extractHits(int srcId, long firstTime, long lastTime)
    {
        ArrayList<HitData> list = new ArrayList<HitData>();

        for (HitData hit : hitList) {
            if (hit.getSourceID() == srcId && hit.getTime() >= firstTime &&
                hit.getTime() <= lastTime)
            {
                list.add(hit);
            }
        }

        return list;
    }

    public int getNumberDone()
    {
        return numDone;
    }

    public int getNumberEmpty()
    {
        return numEmpty;
    }

    public int getNumberReceived()
    {
        return numRcvd;
    }

    public int getNumberSent()
    {
        return numSent;
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

    private void sendHitRecordList(IReadoutRequest rReq)
        throws IOException
    {
        if (rReq == null || rReq.getReadoutRequestElements() == null) {
            if (LOG.isInfoEnabled()) {
                LOG.info("Ignoring empty rdoutReq " + rReq);
            }
            return;
        }

        numRcvd++;

        final int baseLen = 28;

        final int uid = rReq.getUID();

        final int trigType = 0;
        final int cfgId = 0;
        final int trigMode = 0;

        ByteBuffer buf = null;
        for (Object obj : rReq.getReadoutRequestElements()) {
            IReadoutRequestElement elem = (IReadoutRequestElement) obj;

            final int srcId = elem.getSourceID().getSourceID();
            final long firstTime = elem.getFirstTimeUTC().longValue();
            final long lastTime = elem.getLastTimeUTC().longValue();

            List<HitData> dataHits = extractHits(srcId, firstTime, lastTime);
            if (dataHits.size() == 0) {
                numEmpty++;
                continue;
            }

            int hitLen = 0;
            for (HitData hit : dataHits) {
                hitLen += hit.getDeltaRecordLength();
            }

            final int bufLen = baseLen + hitLen;

            if (buf == null || buf.capacity() < bufLen) {
                buf = ByteBuffer.allocate(bufLen);
            }

            final int startPos = buf.position();

            // envelope
            buf.putInt(bufLen);
            buf.putInt(PayloadRegistry.PAYLOAD_ID_HIT_RECORD_LIST);
            buf.putLong(firstTime);

            // readout data record
            buf.putInt(uid);
            buf.putInt(srcId);
            buf.putInt(dataHits.size());

            HitData.setDefaultTriggerType(trigType);
            HitData.setDefaultConfigId(cfgId);
            HitData.setDefaultTriggerMode(trigMode);

            for (HitData hit : dataHits) {
                hit.putDeltaRecord(buf, firstTime);
            }

            if (buf.position() != startPos + bufLen) {
                throw new Error("Expected to put " + bufLen + " bytes, not " +
                                (buf.position() - startPos));
            }

            buf.position(0);
            buf.limit(bufLen);

            super.write(buf);
            numSent++;
        }

        numDone++;
    }

    private void sendReadoutData(IReadoutRequest rReq)
        throws IOException
    {
        final int baseLen = 54;

        final int uid = rReq.getUID();
        final short num = nextNum++;
        final short isLast = 1;

        final int trigType = 0;
        final int cfgId = 0;
        final int trigMode = 0;

        if (rReq == null || rReq.getReadoutRequestElements() == null) {
            return;
        }

        numRcvd++;

        ByteBuffer buf = null;
        for (Object obj : rReq.getReadoutRequestElements()) {
            IReadoutRequestElement elem = (IReadoutRequestElement) obj;

            final int srcId = elem.getSourceID().getSourceID();
            final long firstTime = elem.getFirstTimeUTC().longValue();
            final long lastTime = elem.getLastTimeUTC().longValue();

            List<HitData> dataHits = extractHits(srcId, firstTime, lastTime);
            if (dataHits.size() == 0) {
                numEmpty++;
                continue;
            }

            int hitLen = 0;
            for (HitData hit : dataHits) {
                hitLen += hit.getDeltaLength();
            }

            final int bufLen = baseLen + hitLen;

            if (buf == null || buf.capacity() < bufLen) {
                buf = ByteBuffer.allocate(bufLen);
            }

            final int startPos = buf.position();

            // envelope
            buf.putInt(bufLen);
            buf.putInt(PayloadRegistry.PAYLOAD_ID_READOUT_DATA);
            buf.putLong(firstTime);

            // readout data record
            buf.putShort((short) 1);
            buf.putInt(uid);
            buf.putShort(num);
            buf.putShort(isLast);
            buf.putInt(srcId);
            buf.putLong(firstTime);
            buf.putLong(lastTime);

            final int compHdrLen = 8;

            // composite header
            buf.putInt(bufLen - (baseLen - compHdrLen));
            buf.putShort((short) 1);
            buf.putShort((short) dataHits.size());

            HitData.setDefaultTriggerType(trigType);
            HitData.setDefaultConfigId(cfgId);
            HitData.setDefaultTriggerMode(trigMode);

            for (HitData hit : dataHits) {
                hit.putDelta(buf);
            }

            if (buf.position() != startPos + bufLen) {
                throw new Error("Expected to put " + bufLen + " bytes, not " +
                                (buf.position() - startPos));
            }

            buf.position(0);
            buf.limit(bufLen);

            super.write(buf);
            numSent++;
        }

        numDone++;
    }

    public void sendStop()
        throws IOException
    {
        ByteBuffer buf = ByteBuffer.allocate(4);
        buf.putInt(4);
        buf.flip();

        super.write(buf);
    }

    @Override
    void write(ByteBuffer buf)
        throws IOException
    {
        // assume stop messages are valid
        if (buf.limit() >= 4 && buf.getInt(0) == 4) {
            sendStop();
        } else {
            if (factory == null) {
                factory = new PayloadFactory(null);
            }

            IPayload payload;
            try {
                payload = factory.getPayload(buf, 0);
                if (payload == null) {
                    LOG.error("Couldn't create payload from " + buf.limit() +
                              "-byte buffer");
                }
            } catch (PayloadException ex) {
                LOG.error("Couldn't validate byte buffer", ex);
                payload = null;
            }

            if (payload != null) {
                try {
                    ((IPayload) payload).loadPayload();
                } catch (Exception ex) {
                    LOG.error("Couldn't load payload", ex);
                    payload = null;
                }

                if (EventVersion.VERSION < 5) {
                    sendReadoutData((IReadoutRequest) payload);
                } else {
                    sendHitRecordList((IReadoutRequest) payload);
                }
            }
        }
    }
}
