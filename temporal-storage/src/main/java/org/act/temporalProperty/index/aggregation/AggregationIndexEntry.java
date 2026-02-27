package org.act.temporalProperty.index.aggregation;

import org.act.temporalProperty.query.aggr.AggregationIndexKey;

import java.util.Map;

/**
 * Created by song on 2018-04-05.
 */
public class AggregationIndexEntry implements Map.Entry<AggregationIndexKey, Long> {
    private final AggregationIndexKey key;
    private long duration;

    public AggregationIndexEntry(AggregationIndexKey key) {
        this.key = key;
        this.duration = 0;
    }

    public AggregationIndexEntry(AggregationIndexKey key, long duration){
        this.key = key;
        this.duration = duration;
    }

    public long getDuration(){
        return duration;
    }

    public AggregationIndexKey getKey(){
        return key;
    }

    @Override
    public Long getValue() {
        return getDuration();
    }

    @Override
    public Long setValue(Long value) {
        Long old = duration;
        this.duration = value;
        return old;
    }
}
