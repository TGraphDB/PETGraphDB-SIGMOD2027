package org.act.temporalProperty.query;

import com.google.common.base.Objects;
import org.act.temporalProperty.impl.InternalKey;
import org.act.temporalProperty.impl.ValueType;
import org.act.temporalProperty.util.SliceInput;
import org.act.temporalProperty.util.SliceOutput;
import org.act.temporalProperty.vo.EntityPropertyId;

/**
 * Created by song on 2018-05-05.
 */
public class TimeIntervalKey extends TimeInterval
{
    private final EntityPropertyId id;
    private final ValueType valueType;

    public TimeIntervalKey(EntityPropertyId id, TimePointL newStart, TimePointL end, ValueType valueType)
    {
        super( newStart, end );
        this.id = id;
        this.valueType = valueType;
    }

    public EntityPropertyId getId()
    {
        return id;
    }

    public ValueType getValueType() {
        return valueType;
    }

    public InternalKey getStartKey()
    {
        return new InternalKey( id, start(), valueType );
    }

    public InternalKey getEndKey()
    {
        return new InternalKey( id, end().next(), ValueType.UNKNOWN );
    }

    @Override
    public boolean equals( Object o )
    {
        if ( this == o )
        {
            return true;
        }
        if ( o == null || getClass() != o.getClass() )
        {
            return false;
        }
        TimeIntervalKey that = (TimeIntervalKey) o;
        return from() == that.from();
    }

    @Override
    public int hashCode()
    {
        return Objects.hashCode( from() );
    }
    @Override
    public TimeIntervalKey changeEnd( TimePointL newEnd )
    {
        return new TimeIntervalKey( this.id, start(), newEnd, valueType);
    }
    @Override
    public TimeIntervalKey changeStart(TimePointL newStart )
    {
        return new TimeIntervalKey( this.id, newStart, end(), valueType );
    }
    @Override
    public String toString()
    {
        return "TimeIntervalKey{start=" + start() + ", end=" + end() + ", pro=" + id.getPropertyId() + ", eid=" + id.getEntityId() + ", type=" +
                valueType + '}';
    }

    public boolean lessThan( int time )
    {
        return this.lessThan( new TimePointL( time ) );
    }

    public boolean greaterOrEq( int time )
    {
        return this.greaterOrEq( new TimePointL( time ) );
    }

    public boolean span( int minTime, int maxTime )
    {
        return this.span( new TimePointL( minTime ), new TimePointL( maxTime ) );
    }

    public boolean span( int time )
    {
        return this.span( new TimePointL( time ) );
    }

    public boolean between( int min, int maxTime )
    {
        return this.between( new TimePointL( min ), new TimePointL( maxTime ) );
    }

    @Override
    public int byteCount() {
        return id.byteCount()+8+8+4;
    }

    public void encode(SliceOutput out) {
        id.encode(out);
        start().encode(out);
        end().encode(out);
        out.writeInt(valueType.getPersistentId());
    }


    public static TimeIntervalKey decode(SliceInput in) {
        EntityPropertyId id = EntityPropertyId.decode(in);
        TimePointL start = TimePointL.decode(in);
        TimePointL end = TimePointL.decode(in);
        int valType = in.readInt();
        return new TimeIntervalKey(id, start, end, ValueType.getValueTypeByPersistentId(valType));
    }
}
