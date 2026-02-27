package org.act.temporalProperty.vo;

import org.act.temporalProperty.query.TimeIntervalKey;
import org.act.temporalProperty.util.DynamicSliceOutput;
import org.act.temporalProperty.util.Slice;
import org.act.temporalProperty.util.SliceInput;
import org.act.temporalProperty.util.SliceOutput;

import java.util.Map;

public class TimeIntervalValueEntry implements Map.Entry<TimeIntervalKey, Slice> {

    private final TimeIntervalKey key;
    private final Slice val;

    public TimeIntervalValueEntry(TimeIntervalKey key, Slice val )
    {
        this.key = key;
        this.val = val;
    }
    @Override
    public TimeIntervalKey getKey()
    {
        return key;
    }

    @Override
    public Slice getValue()
    {
        return val;
    }

    @Override
    public Slice setValue( Slice value )
    {
        throw new UnsupportedOperationException();
    }

    public static TimeIntervalValueEntry decode(SliceInput in)
    {
        TimeIntervalKey key = TimeIntervalKey.decode(in);
        int len = in.readByte();
        Slice value = in.readBytes(len);
        return new TimeIntervalValueEntry( key, value );
    }

    public Slice encode()
    {
        DynamicSliceOutput out = new DynamicSliceOutput( 64 );
        this.encode(out);
        return out.slice();
    }

    public void encode(SliceOutput out)
    {
        key.encode(out);
        out.writeByte(val.length());
        out.writeBytes( val );
    }
}
