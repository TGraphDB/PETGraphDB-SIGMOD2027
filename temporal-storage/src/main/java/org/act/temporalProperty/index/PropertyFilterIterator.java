package org.act.temporalProperty.index;

import org.act.temporalProperty.helper.AbstractSearchableIterator;
import org.act.temporalProperty.impl.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This iterator's result only contains given properties.
 *
 * Created by song on 2018-01-25.
 */
public class PropertyFilterIterator extends AbstractSearchableIterator
{

    private final Set<Integer> idSet;
    private final SearchableIterator in;

    public PropertyFilterIterator(List<Integer> proIds, SearchableIterator iterator){
        this.in = iterator;
        this.idSet = new HashSet<>(proIds);
    }

    private boolean isValidProId(InternalEntry entry){
        return idSet.contains(entry.getKey().getPropertyId());
    }

    @Override
    protected InternalEntry computeNext() {
        while(in.hasNext()){
            InternalEntry entry = in.next();
            if(isValidProId(entry)){
                return entry;
            }
        }
        return endOfData();
    }

    @Override
    public void seekToFirst()
    {
        super.resetState();
        in.seekToFirst();
    }

    @Override
    public boolean seekFloor(InternalKey targetKey )
    {
        in.seekFloor( targetKey );
        return super.seekFloor(targetKey);
    }

    @Override
    public String toString() {
        return "PropertyFilterIterator{" +
                "idSet=" + idSet +
                ", in=" + in +
                '}';
    }
}
