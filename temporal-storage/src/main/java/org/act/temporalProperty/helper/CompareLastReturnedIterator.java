package org.act.temporalProperty.helper;

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.PeekingIterator;

import java.util.Iterator;

/**
 * Created by song on 2018-03-28.
 */
public abstract class CompareLastReturnedIterator<T> extends AbstractIterator<T> {
    private final PeekingIterator<T> in;
    private T lastReturned;

    public CompareLastReturnedIterator(PeekingIterator<T> in){
        this.in = in;
        lastReturned = null;
    }

    @Override
    protected T computeNext() {
        while(in.hasNext() && !shouldReturnSecond(lastReturned, in.peek())){
            in.next();
        }
        if(in.hasNext()){
            lastReturned = in.peek();
            return in.next();
        }else{
            return endOfData();
        }
    }

    protected abstract boolean shouldReturnSecond(T lastReturned, T cur);

    @Override
    public String toString() {
        return "CompareLastReturnedIterator{" +
                "in=" + in +
                ", lastReturned=" + lastReturned +
                '}';
    }
}
