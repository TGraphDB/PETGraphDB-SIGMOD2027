package org.act.temporalProperty.helper;

import org.act.temporalProperty.exception.TPSRuntimeException;
import org.act.temporalProperty.impl.InternalEntry;
import org.act.temporalProperty.impl.InternalKey;
import org.act.temporalProperty.impl.SearchableIterator;
import org.act.temporalProperty.query.TimePointL;
import org.act.temporalProperty.util.Slice;
import org.act.temporalProperty.vo.EntityPropertyId;

/**
 * Created by song on 2018-01-24.
 */
public class EPEntryIterator extends AbstractSearchableIterator {

    public final SearchableIterator iter;
    private final EntityPropertyId id;

    public EPEntryIterator(EntityPropertyId entityPropertyId, SearchableIterator iterator){
        this.id = entityPropertyId;
        this.iter = iterator;
        this.seekToFirst();
    }

    @Override
    public void seekToFirst() {
        InternalKey earliestKey = new InternalKey(id, TimePointL.Init);
        iter.seekFloor(earliestKey);
        super.resetState();
    }

    @Override
    public boolean seekFloor(InternalKey targetKey )
    {
        if(targetKey.getId().equals( id ))
        {
            this.resetState();
            if(iter.seekFloor( targetKey ) && this.hasNext()){
                return (this.peek().getKey().compareTo(targetKey) <= 0);
            }else{
                return false;
            }
        }else{
            throw new TPSRuntimeException( "id not match!" );
        }
    }

    private boolean validId(InternalEntry entry) {
        return entry.getKey().getId().equals(id);
    }

    @Override
    protected InternalEntry computeNext() {
        while(iter.hasNext()){
            InternalEntry entry = iter.next();
            int r = entry.getKey().getId().compareTo(id);
            if(r<0) continue;
            else if(r==0) return entry;
            else return endOfData();
        }
        return endOfData();
    }

    @Override
    public String toString() {
        return "EPEntryIterator{" +
                "iter=" + iter +
                ", id=" + id +
                '}';
    }
}
