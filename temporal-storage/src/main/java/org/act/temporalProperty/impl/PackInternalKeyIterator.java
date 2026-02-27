package org.act.temporalProperty.impl;

import org.act.temporalProperty.exception.TPSNHException;
import org.act.temporalProperty.helper.AbstractSearchableIterator;
import org.act.temporalProperty.table.TableIterator;
import org.act.temporalProperty.util.Slice;

import java.io.IOException;
import java.util.Map.Entry;

/**
 * Created by song on 2018-03-29.
 */
public class PackInternalKeyIterator extends AbstractSearchableIterator
{

    private final TableIterator in;
    private final String filePath;

    public PackInternalKeyIterator(TableIterator in, String filePath){
        this.in = in;
        this.filePath = filePath;
    }

    @Override
    protected InternalEntry computeNext() {
        try {
            if (in.hasNext()) {
                Entry<Slice, Slice> tmp = in.next();
                InternalEntry r = new InternalEntry(InternalKey.decode(tmp.getKey()), tmp.getValue());
//            if(r.getKey().getEntityId()==){
//                System.out.println(r.getKey()+" "+r.getValue().getInt(0));
//            }
                return r;
            } else {
                return endOfData();
            }
        }catch (Throwable e){
            throw new TPSNHException("Error occurs when read "+filePath, e);
        }
    }

    @Override
    public void seekToFirst() {
        this.resetState();
        in.seekToFirst();
    }

    @Override
    public boolean seekFloor(InternalKey targetKey) {
        in.seek(targetKey.encode());
        return super.seekFloor(targetKey);
    }

    @Override
    public String toString() {
        return "PackInternalKeyIterator{file=" + filePath+", "+
                "in=" + in +
                '}';
    }
}
