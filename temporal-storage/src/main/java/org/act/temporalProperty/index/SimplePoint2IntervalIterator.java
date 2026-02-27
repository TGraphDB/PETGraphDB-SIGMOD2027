package org.act.temporalProperty.index;

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.PeekingIterator;
import org.act.temporalProperty.exception.TPSNHException;
import org.act.temporalProperty.impl.InternalEntry;
import org.act.temporalProperty.impl.InternalKey;
import org.act.temporalProperty.impl.ValueType;
import org.act.temporalProperty.query.TimePointL;

/**
 * Created by song on 2018-04-06.
 */
public class SimplePoint2IntervalIterator extends AbstractIterator<EntityTimeIntervalEntry> implements PeekingIterator<EntityTimeIntervalEntry> {
    private final OnePropertyChecker tpIter;
    private final TimePointL endTime;
    private InternalEntry lastEntry = null;

    /**
     * @param tpIter  should only contains one property.
     * @param endTime
     */
    public SimplePoint2IntervalIterator(PeekingIterator<InternalEntry> tpIter, TimePointL endTime ) {
        this.tpIter = new OnePropertyChecker(tpIter);
        this.endTime = endTime;
        if (tpIter.hasNext()) {
            lastEntry = tpIter.next();
        }
    }

    @Override
    protected EntityTimeIntervalEntry computeNext() {
        EntityTimeIntervalEntry newEntry;
        if (lastEntry == null) {
            return endOfData();
        } else {
            while(tpIter.hasNext()) {
                InternalKey lastKey = lastEntry.getKey();
                InternalEntry e = tpIter.next();
                InternalKey curKey = e.getKey();
                if ( curKey.getStartTime().compareTo(endTime) > 0 )
                { continue; }

                if(lastKey.getValueType()!=ValueType.INVALID) {
                    if (curKey.getEntityId() == lastKey.getEntityId()) {
                        newEntry = new EntityTimeIntervalEntry(lastKey.getEntityId(), lastKey.getStartTime(), curKey.getStartTime().pre(), lastEntry.getValue());
                    } else {
                        newEntry = new EntityTimeIntervalEntry(lastKey.getEntityId(), lastKey.getStartTime(), endTime, lastEntry.getValue());
                    }
                    lastEntry = e;
                    return newEntry;
                }else{
                    lastEntry = e;
                }
            }
            if(lastEntry!=null) {
                InternalKey lastKey = lastEntry.getKey();
                if(lastKey.getValueType()!=ValueType.INVALID) {
                    newEntry = new EntityTimeIntervalEntry(lastKey.getEntityId(), lastKey.getStartTime(), endTime, lastEntry.getValue());
                    lastEntry = null;
                    return newEntry;
                }else{
                    return endOfData();
                }
            }else{
                return endOfData();
            }
        }
    }

    private static class OnePropertyChecker extends AbstractIterator<InternalEntry> implements PeekingIterator<InternalEntry> {
        private final PeekingIterator<InternalEntry> tpIter;
        private int proId=-1;

        /**
         * @param tpIter  should only contains one property.
         */
        OnePropertyChecker( PeekingIterator<InternalEntry> tpIter ) {
            this.tpIter = tpIter;
        }

        @Override
        protected InternalEntry computeNext() {
            while (tpIter.hasNext()) {
                InternalKey curKey = tpIter.peek().getKey();
                if(proId==-1){
                    proId = curKey.getPropertyId();
                }else if (curKey.getPropertyId() == proId) {
                    return tpIter.next();
                } else {
                    throw new TPSNHException("got more than one property.");
                }
            }
            return endOfData();
        }
    }

}
