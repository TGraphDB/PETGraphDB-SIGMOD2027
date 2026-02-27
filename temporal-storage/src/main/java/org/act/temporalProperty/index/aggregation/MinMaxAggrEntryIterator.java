package org.act.temporalProperty.index.aggregation;

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.PeekingIterator;
import org.act.temporalProperty.exception.TPSNHException;
import org.act.temporalProperty.index.EntityTimeIntervalEntry;
import org.act.temporalProperty.query.TimePointL;
import org.act.temporalProperty.util.Slice;
import org.act.temporalProperty.util.TimeIntervalUtil;
import org.apache.commons.lang3.tuple.Triple;

import java.util.Iterator;
import java.util.NavigableSet;
import java.util.Objects;

/**
 * Created by song on 2018-04-06.
 */
public class MinMaxAggrEntryIterator extends AbstractIterator<Triple<Long, TimePointL, Slice>> implements PeekingIterator<Triple<Long,TimePointL,Slice>> {
    private final Iterator<EntityTimeIntervalEntry> tpIter;
    private final NavigableSet<TimePointL> intervalStarts;
    private final TimePointL intervalBegin;
    private final TimePointL intervalFinish;

    private TimePointL eStart;
    private TimePointL eEnd;
    private EntityTimeIntervalEntry lastEntry;

    /**
     * @param iterator should only contains one property.
     * @param intervalStarts interval start time point TreeSet
     */
    public MinMaxAggrEntryIterator(Iterator<EntityTimeIntervalEntry> iterator, NavigableSet<TimePointL> intervalStarts ) {
        this.tpIter = iterator;
        this.intervalStarts = intervalStarts;
        if(intervalStarts.size()<2) throw new TPSNHException("time interval too less!");
        this.intervalBegin = intervalStarts.first();
        this.intervalFinish = intervalStarts.last().pre();
        if(intervalBegin.compareTo(intervalFinish)>0) throw new TPSNHException("time interval begin > finish!");
    }

    protected Triple<Long, TimePointL, Slice> computeNext() {
        if(lastEntry!=null){
            return computeTimeGroup(this.eStart, this.eEnd, lastEntry);
        }else {
            while (tpIter.hasNext()) {
                EntityTimeIntervalEntry cur = tpIter.next();
                TimePointL eStart = cur.start();
                TimePointL eEnd = cur.end();
                if(eEnd.compareTo(eStart)<0) throw new TPSNHException("end({}) < start({})", eEnd, eStart);
                if(TimeIntervalUtil.overlap(eStart, eEnd, intervalBegin, intervalFinish)){
                    if(eStart.compareTo(intervalBegin)<0) eStart=intervalBegin;
                    if(eEnd.compareTo(intervalFinish)>0) eEnd=intervalFinish;
                    return computeTimeGroup(eStart, eEnd, cur);
                }//else: not in [intervalBegin, intervalFinish], do nothing, continue next loop;
            }
            return endOfData();
        }
    }

    private Triple<Long, TimePointL, Slice> computeTimeGroup(TimePointL eStart, TimePointL eEnd, EntityTimeIntervalEntry entry) {
        TimePointL timeGroupId = intervalStarts.floor(eStart);
        // if eStart and eEnd both in the same time range.
        if(Objects.equals(timeGroupId, intervalStarts.floor(eEnd))){
            lastEntry = null;
            return Triple.of(entry.entityId(), timeGroupId, entry.value());
        }else{
            this.eStart = intervalStarts.higher(eStart);
            this.eEnd = eEnd;
            lastEntry = entry;
            return Triple.of(entry.entityId(), timeGroupId, entry.value());
        }
    }

}
