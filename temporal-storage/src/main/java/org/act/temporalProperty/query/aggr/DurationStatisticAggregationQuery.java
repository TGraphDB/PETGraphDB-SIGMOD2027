package org.act.temporalProperty.query.aggr;

import org.act.temporalProperty.query.TimePointL;

import java.util.*;

/**
 * Users who want custom aggregation query should extend this class, rather than implement AggregationQuery interface
 * Created by song on 2018-04-01.
 */
public abstract class DurationStatisticAggregationQuery<K> extends AbstractTimeIntervalAggrQuery<K,Integer> {

    public DurationStatisticAggregationQuery(TimePointL startTime, TimePointL endTime) {
        super(startTime, endTime);
    }

    @Override
    public Integer aggregate(K k, Collection<TimeIntervalEntry> groupItems) {
        int timeSumDuration = 0;
        for(TimeIntervalEntry entry : groupItems){
            timeSumDuration += (entry.end().val()-entry.start().val()+1);
        }
        return timeSumDuration;
    }

}
