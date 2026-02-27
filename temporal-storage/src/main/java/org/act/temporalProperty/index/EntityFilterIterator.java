package org.act.temporalProperty.index;

import org.act.temporalProperty.helper.AbstractSearchableIterator;
import org.act.temporalProperty.impl.InternalEntry;
import org.act.temporalProperty.impl.InternalKey;
import org.act.temporalProperty.impl.SearchableIterator;

import java.util.Set;

/**
 * Created by song on 2018-04-05.
 */
public class EntityFilterIterator extends AbstractSearchableIterator {

    private final Set<Long> entityIdSet;
    private final SearchableIterator in;

    public EntityFilterIterator(Set<Long> entityIds, SearchableIterator in) {
        entityIdSet = entityIds;
        this.in = in;
    }

    @Override
    protected InternalEntry computeNext() {
        while(in.hasNext()){
            if(entityIdSet.contains(in.peek().getKey().getEntityId())){
                return in.next();
            }else{
                in.next();
            }
        }
        return endOfData();
    }

    @Override
    public void seekToFirst() {
        super.resetState();
        in.seekToFirst();
    }

    @Override
    public boolean seekFloor(InternalKey targetKey) {
        super.resetState();
        if(in.seekFloor(targetKey) && this.hasNext()){
            return (this.peek().getKey().compareTo(targetKey) <= 0);
        }else {
            return false;
        }
    }

    @Override
    public String toString() {
        return "EntityFilterIterator{" +
                "entityIdSet=" + entityIdSet +
                ", in=" + in +
                '}';
    }
}
