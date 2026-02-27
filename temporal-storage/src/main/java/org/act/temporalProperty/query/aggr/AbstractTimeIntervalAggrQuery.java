package org.act.temporalProperty.query.aggr;

import com.google.common.base.Preconditions;
import org.act.temporalProperty.impl.InternalEntry;
import org.act.temporalProperty.impl.InternalKey;
import org.act.temporalProperty.impl.ValueType;
import org.act.temporalProperty.query.TimePointL;
import org.act.temporalProperty.query.range.TimeRangeQuery;
import org.act.temporalProperty.util.Slice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Users who want custom aggregation query should extend this class
 * Created by song on 2018-04-01.
 */
public abstract class AbstractTimeIntervalAggrQuery<K,V> implements TimeIntervalEntryAggrQuery<K,V>, TimeRangeQuery {
    private final Map<K, V> groupValMap = new HashMap<>();
    private final Map<K, List<TimeIntervalEntry>> groupListMap = new HashMap<>();
    private final TimePointL endTime;
    private final TimePointL startTime;
    private Slice lastVal;
    private TimePointL lastTime;

    protected AbstractTimeIntervalAggrQuery( TimePointL startTime, TimePointL endTime )
    {
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public boolean onNewEntry(InternalEntry entry) {
        InternalKey key = entry.getKey();
        TimePointL time = key.getStartTime();
        if ( lastTime == null ) {
            int r = time.compareTo(startTime);
            if(r>0){
                lastTime = time;
                onEntry(startTime, time.pre(), null);
            } else {
                lastTime = startTime;
            }
        }else{
            onEntry( lastTime, time.pre(), lastVal );
            lastTime = time;
        }
        lastVal = key.getValueType()==ValueType.INVALID ? null : entry.getValue();
        return true;
    }

    public Object onReturn() {
        if(lastTime !=null && lastTime.compareTo(endTime)<=0){
            onEntry(lastTime, endTime, lastVal);
        }
        for(Entry<K, List<TimeIntervalEntry>> entry : groupListMap.entrySet()){
            V aggrValue = aggregate(entry.getKey(), entry.getValue());
            if(aggrValue!=null) groupValMap.put(entry.getKey(), aggrValue);
        }
        return onResult(groupValMap);
    }

    private void onEntry(TimePointL start, TimePointL end, Slice value){
        TimeIntervalEntry entry = new TimeIntervalEntry(start, end, value);
        K groupId = computeGroupId(entry);
        if(groupId!=null) {
            groupListMap.computeIfAbsent(groupId, k -> new ArrayList<>());
            groupListMap.get(groupId).add(entry);
        }
    }

    protected int asInt(Slice value){
        Preconditions.checkNotNull(value);
        Preconditions.checkArgument(value.length()>=4);
        return value.getInt(0);
    }

    protected float asFloat(Slice value){
        Preconditions.checkNotNull(value);
        Preconditions.checkArgument(value.length()>=4);
        return value.input().readFloat();
    }

    protected long asLong(Slice value){
        Preconditions.checkNotNull(value);
        Preconditions.checkArgument(value.length()>=8);
        return value.getLong(0);
    }

    protected double asDouble(Slice value){
        Preconditions.checkNotNull(value);
        Preconditions.checkArgument(value.length()>=8);
        return value.input().readDouble();
    }

}
