package org.act.temporalProperty.query.aggr;

import com.google.common.base.Objects;
import org.act.temporalProperty.query.TimePointL;
import org.act.temporalProperty.table.UserComparator;
import org.act.temporalProperty.util.DynamicSliceOutput;
import org.act.temporalProperty.util.Slice;
import org.act.temporalProperty.util.SliceInput;
import org.act.temporalProperty.util.SliceOutput;

/**
 * Created by song on 2018-04-05.
 */
public class AggregationIndexKey implements Comparable<AggregationIndexKey>{
    private long entityId;
    private TimePointL timeGroupId;
    private int valueGroupId;

    public AggregationIndexKey(Slice raw){
        SliceInput in = raw.input();
        entityId = in.readLong();
        timeGroupId = TimePointL.decode(in);
        valueGroupId = in.readInt();
    }

    public AggregationIndexKey(long entityId, TimePointL timeGroupId, int valueGroupId){
        this.entityId = entityId;
        this.timeGroupId = timeGroupId;
        this.valueGroupId = valueGroupId;
    }

    public long getEntityId() {
        return entityId;
    }

    public TimePointL getTimeGroupId() {
        return timeGroupId;
    }

    public int getValueGroupId() {
        return valueGroupId;
    }

    public Slice encode(){
        DynamicSliceOutput out = new DynamicSliceOutput(16);
        out.writeLong( entityId );
        timeGroupId.encode(out);
        out.writeInt( valueGroupId );
        return out.slice();
    }

    public static UserComparator sliceComparator = new UserComparator() {
        @Override
        public String name() {
            return "(entity-id, time-group-id, value-group-id)";
        }
        public Slice findShortestSeparator(Slice start, Slice limit) {
            throw new UnsupportedOperationException();
        }
        public Slice findShortSuccessor(Slice key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int compare(Slice o1, Slice o2) {
            return new AggregationIndexKey(o1).compareTo(new AggregationIndexKey(o2));
        }
    };

    @Override
    public int compareTo(AggregationIndexKey o) {
        int r = Long.compare(this.entityId, o.entityId);
        if(r==0){
            r = this.timeGroupId.compareTo(o.timeGroupId);
            if(r==0){
                return Integer.compare(this.valueGroupId, o.valueGroupId);
            }else{
                return r;
            }
        }else{
            return r;
        }
    }

    @Override
    public String toString()
    {
        return "AggregationIndexKey{" + "entityId=" + entityId + ", timeGroupId=" + timeGroupId + ", valueGroupId=" + valueGroupId + '}';
    }

    @Override
    public boolean equals( Object o )
    {
        if ( this == o )
        { return true; }
        if ( o == null || getClass() != o.getClass() )
        { return false; }
        AggregationIndexKey that = (AggregationIndexKey) o;
        return entityId == that.entityId && timeGroupId == that.timeGroupId && valueGroupId == that.valueGroupId;
    }

    @Override
    public int hashCode()
    {
        return Objects.hashCode( entityId, timeGroupId, valueGroupId );
    }
}
