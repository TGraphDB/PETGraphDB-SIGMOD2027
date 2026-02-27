package org.act.temporalProperty.index;

import com.google.common.collect.AbstractIterator;
import org.act.temporalProperty.exception.TGraphNotImplementedException;
import org.act.temporalProperty.impl.InternalEntry;
import org.act.temporalProperty.impl.InternalKey;
import org.act.temporalProperty.impl.SearchableIterator;

import java.util.List;

/**
 * Created by song on 2018-05-08.
 */
public class List2SearchableIterator extends AbstractIterator<InternalEntry> implements SearchableIterator
{
    private final List<InternalEntry> in;
    private int i = 0;

    public List2SearchableIterator( List<InternalEntry> in )
    {
        this.in = in;
    }

    @Override
    protected InternalEntry computeNext()
    {
        if ( i < in.size() )
        {
            InternalEntry tmp = in.get( i );
            i++;
            return tmp;
        }
        else
        {
            return endOfData();
        }
    }

    @Override
    public void seekToFirst()
    {
        throw new UnsupportedOperationException( new TGraphNotImplementedException() );
    }

    @Override
    public boolean seekFloor(InternalKey targetKey )
    {
        throw new UnsupportedOperationException( new TGraphNotImplementedException() );
    }

    @Override
    public String toString() {
        return "List2SearchableIterator{" +
                "in=" + in +
                ", i=" + i +
                '}';
    }
}