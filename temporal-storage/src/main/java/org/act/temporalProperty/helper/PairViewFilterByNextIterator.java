package org.act.temporalProperty.helper;

import com.google.common.collect.AbstractIterator;

import java.util.Iterator;

/**
 * Created by song on 2018-03-28.
 */
public abstract class PairViewFilterByNextIterator<T> extends AbstractIterator<T> {
    private final Iterator<T> in;
    private T first;
    private T second;

    public PairViewFilterByNextIterator(Iterator<T> in){
        this.in = in;
        if(in.hasNext()) first =in.next();
        if(in.hasNext()) second =in.next();
    }

    @Override
    protected T computeNext() {
        while(first !=null && second !=null){
            if(shouldReturnFirst(first, second)){
                return shift2next();
            }else{
                shift2next();
            }
        }
        if(first !=null){ // next==null
//            assert second ==null:"next should be null!";
            if(shouldReturnFirst(first, null)){
                return shift2next();
            }else{
                return endOfData();
            }
        }else{
            return endOfData();
        }
    }

    private T shift2next() {
        T tmp = first;
        first = second;
        if(in.hasNext()){
            second = in.next();
        }else{
            second = null;
        }
        return tmp;
    }

    protected abstract boolean shouldReturnFirst(T cur, T next);

    @Override
    public String toString() {
        return "PairViewFilterByNextIterator{" +
                "in=" + in +
                ", first=" + first +
                ", second=" + second +
                '}';
    }
}
