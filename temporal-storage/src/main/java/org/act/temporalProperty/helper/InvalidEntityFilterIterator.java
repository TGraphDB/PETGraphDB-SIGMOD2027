package org.act.temporalProperty.helper;

import org.act.temporalProperty.impl.*;

/**
 * If an entity only contains one entry and the entry is INVALID, then remove such entries.
 * Created by song on 2018-03-28.
 */
public class InvalidEntityFilterIterator extends PairViewFilterByNextIterator<InternalEntry> implements SearchableIterator {
    private boolean lastTwoIDEqual=false;// true if entry[cur-1].id == entry[cur].id

    public InvalidEntityFilterIterator(SearchableIterator in) {
        super(in);
    }

    @Override
    protected boolean shouldReturnFirst(InternalEntry cur, InternalEntry next) {
        InternalKey curKey = cur.getKey();
        if(next!=null) {
            InternalKey nextKey = next.getKey();
            if (nextKey.getId().equals(curKey.getId())) {
                lastTwoIDEqual = true;
                return true;
            } else {
                if (!lastTwoIDEqual && curKey.getValueType() == ValueType.INVALID) {
                    lastTwoIDEqual = false;
                    return false;
                } else {
                    lastTwoIDEqual = false;
                    return true;
                }
            }
        }else{
            if(!lastTwoIDEqual && curKey.getValueType() == ValueType.INVALID){
                return false;
            }else{
                return true;
            }
        }
    }

    @Override
    public void seekToFirst() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean seekFloor(InternalKey targetKey) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        return "InvalidEntityFilterIterator{" +
                "lastTwoIDEqual=" + lastTwoIDEqual +
                "in=" + super.toString() +
                '}';
    }
}
