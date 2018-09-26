package icecube.daq.test;

import icecube.daq.payload.PayloadRegistry;
import icecube.daq.payload.impl.DOMID;
import icecube.daq.util.IDOMRegistry;

import java.nio.ByteBuffer;

public class HitData
{
    static final int DATA_MAX_LENGTH = 2047 - 12;
    static final int RAW_LENGTH = 38 + DATA_MAX_LENGTH;
    static final int SIMPLE_LENGTH = 38;
    static final int BASE_LENGTH = 58;
    static final int BASE_RECORD_LENGTH = 20;
    static final int EXPANDED_LENGTH = 4;

    private static int defaultTrigType = -1;
    private static int defaultCfgId = -1;
    private static int defaultTrigMode = -1;

    private static IDOMRegistry domRegistry = null;

    private long time;
    private int trigType;
    private int cfgId;
    private int srcId;
    private long domId;
    private int trigMode;
    private short chanId;

    private byte[] data = new byte[] {
        (byte) 0xff, (byte) 0xee, (byte) 0xdd,
        (byte) 0xcc, (byte) 0xbb, (byte) 0xaa
    };

    HitData(long time, int srcId, long domId)
    {
        this(time, defaultTrigType, defaultCfgId, srcId, domId,
             defaultTrigMode);
    }

    HitData(long time, int trigType, int cfgId, int srcId, long domId,
            int trigMode)
    {
        if (domRegistry == null) {
            throw new Error("DOM registry has not been set");
        }

        this.time = time;
        this.trigType = trigType;
        this.cfgId = cfgId;
        this.srcId = srcId;
        this.domId = domId;
        this.trigMode = trigMode;

        chanId = domRegistry.getChannelId(domId);
        if (chanId < 0) {
            throw new Error("Couldn't find channel ID for DOM " +
                            DOMID.toString(domId));
        }
    }

    long getDOMID() { return domId; }
    int getDeltaLength() { return BASE_LENGTH + data.length; }
    int getDeltaRecordLength() { return BASE_RECORD_LENGTH + data.length; }
    int getSourceID() { return srcId; }
    long getTime() { return time; }

    void putDelta(ByteBuffer buf)
    {
        putDeltaInternal(buf, true);
    }

    private void putDeltaInternal(ByteBuffer buf, boolean isExpanded)
    {
        final int startPos = buf.position();

        final int payLen;
        final int payType;

        if (isExpanded) {
            payLen = BASE_LENGTH + data.length;
            payType = PayloadRegistry.PAYLOAD_ID_COMPRESSED_HIT_DATA;
        } else {
            payLen = (BASE_LENGTH - EXPANDED_LENGTH) + data.length;
            payType = PayloadRegistry.PAYLOAD_ID_DELTA_HIT;
        }

        // hit envelope
        buf.putInt(payLen);
        buf.putInt(payType);

        // hit data
        if (isExpanded) {
            buf.putLong(time);
            buf.putInt(trigType);
            buf.putInt(cfgId);
            buf.putInt(srcId);
            buf.putLong(domId);
        } else {
            buf.putLong(domId);
            buf.putLong(Long.MIN_VALUE);
            buf.putLong(time);
        }


        final short orderChk = 1;
        final short version = 1;
        final short pedestal = 123;
        final long domclk = time;
        final int word0 = 0x10000 + data.length + 12 + (trigMode << 18);
        final int word2 = 12345;

        // delta compressed record
        buf.putShort(orderChk);
        buf.putShort(version);
        buf.putShort(pedestal);
        buf.putLong(domclk);
        buf.putInt(word0);
        buf.putInt(word2);

        buf.put(data);

        if (buf.position() != startPos + payLen) {
            throw new Error("Expected to put " + payLen + " bytes, not " +
                            (buf.position() - startPos));
        }
    }

    void putDeltaRecord(ByteBuffer buf, long baseTime)
    {
        final int startPos = buf.position();

        final int payLen = BASE_RECORD_LENGTH + data.length;

        final byte recType = (byte) 1;
        final byte flags = (byte) 0xff;

        // hit envelope
        buf.putShort((short) payLen);
        buf.put(recType);
        buf.put(flags);
        buf.putShort(chanId);
        buf.putInt((int) (time - baseTime));

        final short pedestal = 123;
        final long domclk = time;
        final int word0 = 0x10000 + data.length + 12 + (trigMode << 18);
        final int word2 = 12345;

        buf.putShort(pedestal);
        buf.putInt(word0);
        buf.putInt(word2);

        buf.put(data);

        if (buf.position() != startPos + payLen) {
            throw new Error("Expected to put " + payLen + " bytes, not " +
                            (buf.position() - startPos));
        }
    }

    void putRaw(ByteBuffer buf)
    {
        putDeltaInternal(buf, false);
    }

    void putSimple(ByteBuffer buf)
    {
        final int startPos = buf.position();

        final int payLen = SIMPLE_LENGTH;

        buf.putInt(payLen);
        buf.putInt(PayloadRegistry.PAYLOAD_ID_SIMPLE_HIT);
        buf.putLong(time);

        buf.putInt(trigType);
        buf.putInt(cfgId);
        buf.putInt(srcId);
        buf.putLong(domId);
        buf.putShort((short) trigMode);


        if (buf.position() != startPos + payLen) {
            throw new Error("Expected to put " + payLen + " bytes, not " +
                            (buf.position() - startPos));
        }
    }

    static void setDOMRegistry(IDOMRegistry reg) { domRegistry = reg; }
    static void setDefaultTriggerType(int val) { defaultTrigType = val; }
    static void setDefaultConfigId(int val) { defaultCfgId = val; }
    static void setDefaultTriggerMode(int val) { defaultTrigMode = val; }

    @Override
    public String toString()
    {
        return "HitData[" + time + " cfg " + cfgId + " src " + srcId +
            " dom " + Long.toHexString(domId) + " chan " + chanId +
            " mode " + trigMode + "]";
    }
}
