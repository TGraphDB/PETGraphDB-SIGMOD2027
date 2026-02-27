package org.act.temporalProperty.index.aggregation;

import com.google.common.collect.Lists;
import org.act.temporalProperty.index.IndexFileMeta;
import org.act.temporalProperty.index.IndexValueType;
import org.act.temporalProperty.index.value.IndexMetaData;
import org.act.temporalProperty.index.IndexType;
import org.act.temporalProperty.query.TimePointL;
import org.act.temporalProperty.query.aggr.ValueGroupingMap;
import org.act.temporalProperty.util.Slice;
import org.act.temporalProperty.util.SliceInput;
import org.act.temporalProperty.util.SliceOutput;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Created by song on 2018-04-06.
 */
public class AggregationIndexMeta extends IndexMetaData {
    private final ValueGroupingMap vGroupMap;
    private final int tEvery;
    private final int timeUnit;
    private TimeGroupBuilder tGroupMap;

    public AggregationIndexMeta(long indexId, IndexType type, int pid, IndexValueType vType, TimePointL start, TimePointL end,
                                int tEvery, int timeUnit, ValueGroupingMap valueGroup) {
        super( indexId, type, Lists.newArrayList( pid ), Lists.newArrayList( vType ), start, end );
        this.vGroupMap = valueGroup;
        this.tEvery = tEvery;
        this.timeUnit = timeUnit;
        this.tGroupMap = new UnixTimestampTimeGroupBuilder( getTimeStart(), getTimeEnd(), tEvery, timeUnit );
    }

    public ValueGroupingMap getValGroupMap() {
        return vGroupMap;
    }

    public TimeGroupBuilder getTimeGroupMap()
    {
        return tGroupMap;
    }

    public int getTEvery() {
        return tEvery;
    }

    public int getTimeUnit() {
        return timeUnit;
    }

    @Override
    public String toString() {
        return "AggregationIndexMeta{" +
                "id=" + getId() +
                ", valueTypes=" + getValueTypes().get(0) +
                ", type=" + getType() +
                ", propertyId=" + getPropertyIdList().get(0) +
                ", timeStart=" + getTimeStart() +
                ", timeEnd=" + getTimeEnd() +
                ", online=" + isOnline() +
                ", vGroupMap=" + vGroupMap +
                ", tEvery=" + tEvery +
                ", timeUnit=" + timeUnit +
                '}';
    }

    @Override
    public void encode(SliceOutput out){
        super.encode(out);
        out.writeInt(tEvery);
        out.writeInt(timeUnit);
        vGroupMap.encode(out);
    }

    public static AggregationIndexMeta decode(SliceInput in, IndexType type){
        IndexMetaData meta = new IndexMetaData(in, type);
        int tEvery = in.readInt();
        int timeUnit = in.readInt();
        ValueGroupingMap valGroupMap = ValueGroupingMap.decode(in);
        AggregationIndexMeta aggrMeta = new AggregationIndexMeta(meta.getId(), type, meta.getPropertyIdList().get(0), meta.getValueTypes().get(0),
                meta.getTimeStart(), meta.getTimeEnd(), tEvery, timeUnit, valGroupMap);
        if(meta.isOnline()) aggrMeta.setOnline(true);
        meta.allFiles().forEach(aggrMeta::addFile);
        return aggrMeta;
    }

    public TreeSet<TimePointL> getTimeGroupAvailable(TimePointL start, TimePointL end )
    {
        TreeSet<TimePointL> result = new TreeSet<>();
        Collection<IndexFileMeta> files = this.getFilesByTime( start, end );
        for ( IndexFileMeta f : files )
        {
            result.addAll( f.getTimeGroup().subSet(start, true, end, true) );
        }
        return result;
    }
}
