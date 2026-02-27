package org.act.temporalProperty.helper;

import com.google.common.collect.PeekingIterator;
import org.act.temporalProperty.impl.InternalEntry;
import org.act.temporalProperty.impl.InternalKey;
import org.act.temporalProperty.impl.SearchableIterator;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;

import static com.google.common.base.Preconditions.checkState;

/**
 * Read only, searchable, one element looking forward {@code Iterator} implementation
 * (the searchable logic is implemented by this class's sub classes)
 *
 * How to use: checkout guava's AbstractIterator class.
 *
 * The only difference is add {@code resetState()} method to allow seek operations.
 * Implementations of this class MUST call this method BEFORE seek operations take place.
 *
 * A example of implementation seeking methods. {@code
 *     @Override
 *     public boolean seekFloor(InternalKey targetKey){
 *         resetState();
 *         if(this.hasNext()){
 *             return (this.peek().getKey().compareTo(targetKey) <= 0);
 *         }else {
 *             return false;
 *         }
 *     }
 * }
 * Created by song on 2018-03-31
 * Update by sjh on 2019-10-11
 */

public abstract class AbstractSearchableIterator implements SearchableIterator
{
//    public static Map<InternalKey, List<Integer>> from = new ConcurrentSkipListMap<>();//Collections.synchronizedMap(new WeakHashMap<>());
//    public static void searchKey(InternalKey key){
//        from.forEach((internalKey, peekingIterators) -> {
//            if(Objects.equals(internalKey, key)){
//                peekingIterators.forEach(System.out::println);
//            }
//        });
//    }

    private enum State
    {
        READY,     // We have computed the next element and haven't returned it yet.
        NOT_READY, // We haven't yet computed or have already returned the element.
        DONE,      // We have reached the end of the data and are finished.
        FAILED,    // We've suffered an exception and are kaput.
    }
    private State state = State.NOT_READY;
    private InternalEntry next;

    /**
     * this method should be override by user, and calls {@code endOfData()} when re
     */
    protected abstract InternalEntry computeNext();

    protected final InternalEntry endOfData()
    {
        state = State.DONE;
        return null;
    }

    protected void resetState()
    {
        state = State.NOT_READY;
        next = null;
    }

    /**
     * If you are going to call this, you must call this AFTER your custom seek operations.
     */
    @Override
    public boolean seekFloor(InternalKey targetKey){
        resetState();
        if(this.hasNext()){
            return (this.peek().getKey().compareTo(targetKey) <= 0);
        }else {
            return false;
        }
    }

    @Override
    public final boolean hasNext()
    {
        checkState( state != State.FAILED );
        switch ( state ) {
        case DONE: return false;
        case READY: return true;
        default: // do nothing
        }
        return tryToComputeNext();
    }

    private boolean tryToComputeNext()
    {
        state = State.FAILED; // temporary pessimism
        next = computeNext();
        if ( state != State.DONE )
        {
//            //add debug info
//            List<Integer> iters = from.get(next.getKey());
//            if(iters!=null) {
//                iters.add(this.hashCode());
//            }else{
//                iters = new LinkedList<>();
//                iters.add(this.hashCode());
//                from.put(next.getKey(), iters);
//            }
//            // end debug
            state = State.READY;
            return true;
        }
        return false;
    }

    @Override
    public final InternalEntry next()
    {
        if ( !hasNext() )
        {
            throw new NoSuchElementException();
        }
        state = State.NOT_READY;
        InternalEntry result = next;
        next = null;
        return result;
    }

    @Override
    public final InternalEntry peek()
    {
        if ( !hasNext() )
        {
            throw new NoSuchElementException();
        }
        return next;
    }

    @Override
    public final void remove() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        return "AbstractSearchableIterator{" +
                "state=" + state +
                '}';
    }
}
