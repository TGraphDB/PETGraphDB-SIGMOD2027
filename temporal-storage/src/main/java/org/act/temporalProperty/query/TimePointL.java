package org.act.temporalProperty.query;


import com.alibaba.fastjson.annotation.JSONField;
import com.alibaba.fastjson.annotation.JSONType;
import com.alibaba.fastjson.parser.DefaultJSONParser;
import com.alibaba.fastjson.parser.ParserConfig;
import com.alibaba.fastjson.parser.deserializer.ObjectDeserializer;
import com.alibaba.fastjson.serializer.JSONSerializer;
import com.alibaba.fastjson.serializer.ObjectSerializer;
import com.alibaba.fastjson.serializer.SerializeConfig;
import com.google.common.base.Preconditions;
import org.act.temporalProperty.util.Slice;
import org.act.temporalProperty.util.SliceInput;
import org.act.temporalProperty.util.SliceOutput;
import org.act.temporalProperty.util.Slices;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Objects;

import static org.act.temporalProperty.util.SizeOf.SIZE_OF_LONG;

/**
 * Created by song on 2018-05-09.
 *
 * Implementation of TimePoint, valid range [0, 2^61-2] (2^61-2==2305843009213693949==about 73 years' nanoseconds, 1 seconds = 10^9 nanoseconds).
 * 2^61 is used as NOW. -1 is used as INIT (but is stored as 2^61-1)
 * because when store, we use first 3 bit to represent value type (8 types), check InternalKey.encode for details
 * thus negative number can not be stored.
 */
public class TimePointL implements TPoint<TimePointL>
{
    @JSONField(serialize=false)
    public static final long INIT_VAL = -1L;
    @JSONField(serialize=false)
    public static final long NOW_VAL = Long.MAX_VALUE >> 2; // (Long.MAX_VALUE == 2^63)
    @JSONField(serialize=false)
    public static final long INIT_STORAGE = (Long.MAX_VALUE >> 2) - 1L;
    @JSONField(serialize=false)
    public static final long NOW_STORAGE = NOW_VAL;

    @JSONField(serialize=false)
    public static final TimePointL Now = new TimePointL(true){
        @Override public boolean isNow() { return true; }
        @Override public boolean isInit(){ return false; }
        @Override public TimePointL pre() { throw new UnsupportedOperationException("should not call pre on TimePoint.NOW"); }
        @Override public TimePointL next() { throw new UnsupportedOperationException("should not call next on TimePoint.NOW"); }
        @Override public String toString() { return "NOW"; }
    };
    @JSONField(serialize=false)
    public static final TimePointL Init = new TimePointL(false){
        @Override public boolean isNow() { return false; }
        @Override public boolean isInit(){ return true; }
        @Override public TimePointL pre() { throw new UnsupportedOperationException("should not call pre on TimePoint.INIT"); }
        @Override public TimePointL next() { throw new UnsupportedOperationException("should not call next on TimePoint.INIT"); }
        @Override public String toString() { return "INIT"; }
        @Override public void encode(SliceOutput out) { out.writeLong(INIT_STORAGE); }
    };

    protected long time;

    public TimePointL( long time )
    {
        this.time = time;
        Preconditions.checkArgument(0 <= time && time <= NOW_VAL - 2, "invalid time value {}, only support 0 to {}", time, NOW_VAL -2);
    }

    // this constructor is used for now and init only.
    protected TimePointL( boolean isNow )
    {
        if(isNow) this.time = NOW_VAL;
        else this.time = INIT_VAL;
    }

    @Override
    public TimePointL pre()
    {
        return new TimePointL( time - 1 );
    }

    @Override
    public TimePointL next()
    {
        return new TimePointL( time + 1 );
    }

    @Override
    public boolean isNow()
    {
        return false;
    }

    @Override
    public boolean isInit()
    {
        return false;
    }

    public long val()
    {
        return time;
    }

    public long getTime()
    {
        return time;
    }

    public int valInt()
    {
        return Math.toIntExact(time);
    }

    @Override
    public int compareTo( TimePointL o )
    {
        return Long.compare( time, o.time );
    }

    @Override
    public String toString()
    {
        return String.valueOf( time );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TimePointL that = (TimePointL) o;
        return time == that.time;
    }

    @Override
    public int hashCode() {
        return Objects.hash(time);
    }

    public Slice encode(){
        Slice raw = Slices.allocate(SIZE_OF_LONG);
        encode(raw.output());
        return raw;
    }

    public static TimePointL decode(Slice in) {
        return decode(in.input());
    }

    public static TimePointL decode(SliceInput in) {
        long t = in.readLong();
        if(t == NOW_STORAGE) return Now;
        else if(t == INIT_STORAGE) return Init;
        else return new TimePointL(t);
    }

    public void encode(SliceOutput out) {
        out.writeLong(time);
    }

//// unable to use because cannot en/decode subclass without SerializerFeature.WriteClassName
//// because Init has type: org.act.temporalProperty.query.TimePointL$2
//    static{
//        SerializeConfig.getGlobalInstance().put(TimePointL.class, new TPLEnDecoder());
//        ParserConfig.getGlobalInstance().putDeserializer(TimePointL.class, new TPLEnDecoder());
//    }
//    public static class TPLEnDecoder implements ObjectDeserializer, ObjectSerializer {
//        @Override
//        public TimePointL deserialze(DefaultJSONParser parser, Type type, Object fieldName) {
//            assert type==TimePointL.class;
//            String valStr = parser.parseObject(String.class);
//            if(valStr.equalsIgnoreCase("init")) return TimePointL.Init;
//            else if(valStr.equalsIgnoreCase("now")) return TimePointL.Now;
//            else //throw new IllegalStateException("expect init or now, but got "+valStr);
//            return new TimePointL(parser.parseObject(Long.class));
//        }
//        @Override
//        public int getFastMatchToken() {
//            return 0;
//        }
//        @Override
//        public void write(JSONSerializer serializer, Object object, Object fieldName, Type fieldType, int features) throws IOException {
//            if(object instanceof TimePointL){
//                TimePointL v = (TimePointL) object;
//                if(v.isInit()) serializer.write("Init");
//                else if(v.isNow()) serializer.write("Now");
//                else serializer.write(v.time);
//            }
//        }
//    }
}
