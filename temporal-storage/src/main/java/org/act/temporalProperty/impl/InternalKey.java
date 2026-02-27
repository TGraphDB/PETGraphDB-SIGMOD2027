
package org.act.temporalProperty.impl;

import org.act.temporalProperty.query.TimePointL;
import org.act.temporalProperty.util.*;

import com.google.common.base.Preconditions;
import org.act.temporalProperty.vo.EntityPropertyId;

import java.util.Objects;

import static org.act.temporalProperty.util.SizeOf.SIZE_OF_INT;
import static org.act.temporalProperty.util.SizeOf.SIZE_OF_LONG;

/**
 * InternalKey是将一个动态属性的点/边id，属性id，某个时间以及相关控制位编码为一个键值对中的键的机制。在设计文档中，我们将一个动态属性的数据分为多个record，而InternalKey就
 * 是一个record的key
 * 点/边id和属性id可联合确定一个时态值
 */
public class InternalKey implements Comparable<InternalKey>
{
    private final EntityPropertyId id;
    /**
     * 一个动态属性某个值的起始时间
     */
    private final TimePointL startTime;
    /**
     * 值类型：invalid or value or unknown
     */
    private final ValueType valueType;

    /**
     * 新建一个InternalKey，将相关信息传入，用于编码后生成一个Slice
     * @param Id
     * @param startTime
     * @param valueType
     */
    public InternalKey(EntityPropertyId Id, TimePointL startTime, ValueType valueType)
    {
        Preconditions.checkNotNull(Id);
        Preconditions.checkNotNull(valueType);
        Preconditions.checkNotNull(startTime);

        this.id = Id;
        this.startTime = startTime;
        this.valueType = valueType;
    }

    public InternalKey(int propertyId, long entityId, TimePointL startTime, ValueType valueType)
    {
        Preconditions.checkNotNull(startTime);
        Preconditions.checkNotNull(valueType);

        this.id = new EntityPropertyId(entityId, propertyId);
        this.startTime = startTime;
        this.valueType = valueType;
    }
    /**
     * 新建一个InternalKey，将相关信息传入，用于编码后生成一个Slice，通常用于查找
     * @param Id
     * @param startTime
     */
    public InternalKey(EntityPropertyId Id, TimePointL startTime)
    {
        this(Id, startTime, ValueType.VALUE);
    }

    /**
     * 返回唯一确定某个动态属性的标识，其中其点/边id保存在返回值的前8位，属性id保存在返回值的后4位。
     * @return 唯一确定某个动态属性的标识
     */
    public EntityPropertyId getId()
    {
        return id;
    }

    public int getPropertyId()
    {
        return id.getPropertyId();
    }

    public long getEntityId()
    {
        return id.getEntityId();
    }
    
    /**
     * @return 返回此key对应值的起始有效时间
     */
    public TimePointL getStartTime()
    {
        return startTime;
    }

    /**
     * @return 返回此key对应record的类型
     */
    public ValueType getValueType()
    {
        return valueType;
    }



    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        InternalKey that = (InternalKey) o;
        return  id.equals(that.id) &&
                startTime == that.startTime &&
                valueType == that.valueType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, startTime, valueType);
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("InternalKey");
        sb.append("{eid=").append(getEntityId());
        sb.append( " proId=" ).append( getPropertyId() );
        sb.append(", time=").append(getStartTime());
        sb.append(", valueType=").append(getValueType());
        sb.append('}');
        return sb.toString();
    }

    @Override
    public int compareTo(InternalKey o) {
        int result = this.getId().compareTo(o.getId());
        if( 0 != result ) {
            return result;
        }else {
            return this.getStartTime().compareTo(o.getStartTime());
        }
    }

    public Slice encode()
    {
        DynamicSliceOutput out = new DynamicSliceOutput(SIZE_OF_INT + SIZE_OF_LONG + SIZE_OF_LONG ); //int for propertyId, long for entityId and time+type
        getId().encode(out);
        if(startTime.isInit()){
            out.writeLong(SequenceNumber.packTimeAndValueType( TimePointL.INIT_STORAGE, valueType ) );
        }else{
            out.writeLong(SequenceNumber.packTimeAndValueType( startTime.val(), valueType ) );
        }
        return out.slice();
    }

    public static InternalKey decode(SliceInput in)
    {
        EntityPropertyId id = EntityPropertyId.decode(in);
        long tmp = in.readLong();
        long time = SequenceNumber.unpackTime(tmp);
        ValueType valueType = SequenceNumber.unpackValueType(tmp);
        if(time == TimePointL.INIT_STORAGE){
            return new InternalKey(id.getPropertyId(), id.getEntityId(), TimePointL.Init, valueType);
        }else {
            return new InternalKey(id.getPropertyId(), id.getEntityId(), new TimePointL(time), valueType);
        }
    }

    public static InternalKey decode(Slice in)
    {
        Preconditions.checkArgument(in.length() >= SIZE_OF_INT+2*SIZE_OF_LONG, "not a valid InternalKey slice, got len: %d", in.length());
        return decode(in.input());
    }
}
