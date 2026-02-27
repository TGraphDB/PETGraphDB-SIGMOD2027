package org.act.temporalProperty.query.aggr;

import com.google.common.base.Preconditions;
import org.act.temporalProperty.impl.InternalEntry;
import org.act.temporalProperty.impl.InternalKey;
import org.act.temporalProperty.query.TimePointL;
import org.act.temporalProperty.query.range.InternalEntryRangeQueryCallBack;
import org.act.temporalProperty.util.Slice;

import java.util.*;
import java.util.Map.Entry;

/**
 * Users who want custom aggregation query should extend this class
 * Created by song on 2018-04-01.
 */
public abstract class SampleTimePointAggrQuery<K,V> implements InternalEntryRangeQueryCallBack {
    private final Map<K, V> groupValMap = new HashMap<>();
    private final Map<K, List<Entry<TimePointL, Slice>>> groupListMap = new HashMap<>();

    private TimePointL time = TimePointL.Init;
    private Slice lastValue;

    protected abstract K computeGroupId(TimePointL t, Slice value);

    protected abstract TimePointL computeNextTime(TimePointL time);

    protected abstract TimePointL computeStartTime();

    protected abstract Object onResult(Map<K, V> result);

    protected abstract V aggregate(K groupId, List<Entry<TimePointL, Slice>> groupItems);

    @Override
    public boolean onNewEntry(InternalEntry entry) {
        InternalKey key = entry.getKey();
        TimePointL curT = key.getStartTime();
        TimePointL t;
        if(time.isInit()) {
            t = computeStartTime();
        }else{
            t = computeNextTime(time);
        }
        while(t.compareTo(curT)<0) {
            addNewValue(t, lastValue);
            t = computeNextTime(t);
        }
        addNewValue(t, entry.getValue());
        time = t;
        lastValue = entry.getValue();
        return true;
    }

    @Override
    public void setValueType(String valueType) {
        // do nothing.
    }

    @Override
    public Object onReturn() {
        for(Entry<K, List<Entry<TimePointL, Slice>>> e: groupListMap.entrySet()){
            groupValMap.put(e.getKey(), aggregate(e.getKey(), e.getValue()));
        }
        return onResult(groupValMap);
    }

    private void addNewValue(TimePointL t, Slice value){
        K groupId = computeGroupId(t, value);
        groupListMap.computeIfAbsent(groupId, k -> new ArrayList<>());
        groupListMap.get(groupId).add(new TimePointValueEntry(t, value));
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

    private static class TimePointValueEntry implements Entry<TimePointL, Slice> {

        private TimePointL time;
        private Slice value;

        private TimePointValueEntry(TimePointL time, Slice value) {
            this.time = time;
            this.value = value;
        }

        @Override
        public TimePointL getKey() {
            return time;
        }

        @Override
        public Slice getValue() {
            return value;
        }

        @Override
        public Slice setValue(Slice value) {
            throw new UnsupportedOperationException();
        }
    }
}
