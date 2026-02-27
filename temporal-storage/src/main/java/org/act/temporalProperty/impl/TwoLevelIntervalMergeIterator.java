package org.act.temporalProperty.impl;

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.PeekingIterator;
import org.act.temporalProperty.exception.TPSNHException;
import org.act.temporalProperty.query.TimePointL;
import org.act.temporalProperty.vo.TimeIntervalValueEntry;
import org.act.temporalProperty.query.TimeIntervalKey;
import org.act.temporalProperty.util.Slice;
import org.act.temporalProperty.vo.EntityPropertyId;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Map.Entry;

public class TwoLevelIntervalMergeIterator extends AbstractIterator<Entry<TimeIntervalKey,Slice>> implements IntervalIterator
{
    private final PeekingIterator<Entry<TimeIntervalKey,Slice>> old;
    private final PeekingIterator<Entry<TimeIntervalKey,Slice>> latest;
    private Pair<TimeIntervalKey,TimeIntervalKey> changedNextOldKey;

    public TwoLevelIntervalMergeIterator( PeekingIterator<Entry<TimeIntervalKey,Slice>> low, PeekingIterator<Entry<TimeIntervalKey,Slice>> high )
    {
        this.old = low;
        this.latest = high;
    }

    @Override
    protected Entry<TimeIntervalKey,Slice> computeNext()
    {
        if ( old.hasNext() && latest.hasNext() )
        {
            Entry<TimeIntervalKey,Slice> oldEntry = old.peek();
            Entry<TimeIntervalKey,Slice> newEntry = latest.peek();
            TimeIntervalKey oldKey = getNextOldKey( oldEntry );
            TimeIntervalKey newKey = newEntry.getKey();

            int r = oldKey.getStartKey().compareTo( newKey.getStartKey() );
            if ( oldKey.getId().equals( newKey.getId() ) )
            {
                if ( r < 0 )
                {
                    if ( oldKey.end().compareTo(newKey.start()) < 0 )
                    {
                        old.next();
                        return mayChangedOldEntry( oldKey, oldEntry.getValue() );
                    }
                    else
                    {
                        old.next();
                        return changeEndOldEntry( oldKey, oldEntry.getValue(), newKey.start().pre() );
                    }
                }
                else
                {
                    changedNextOldKey = null;
                    if ( oldKey.start().compareTo(newKey.start()) > 0 )
                    {
                        return latest.next();
                    }
                    else
                    {
                        pollOldUntil( newKey.getId(), newKey.end() );
                        return latest.next();
                    }
                }
            }
            else
            {
                if ( r < 0 )
                {
                    old.next();
                    return mayChangedOldEntry( oldKey, oldEntry.getValue() );
                }
                else if ( r > 0 )
                {
                    changedNextOldKey = null;
                    return latest.next();
                }
                else
                {
                    throw new TPSNHException( "internalKey equal but id not equal!" );
                }
            }
        }
        else if ( old.hasNext() )
        {
            return old.next();
        }
        else if ( latest.hasNext() )
        {
            return latest.next();
        }
        else
        {
            return endOfData();
        }
    }

    private Entry<TimeIntervalKey,Slice> changeEndOldEntry(TimeIntervalKey oldKey, Slice value, TimePointL newEnd )
    {
        return mayChangedOldEntry( oldKey.changeEnd( newEnd ), value );
    }

    private Entry<TimeIntervalKey,Slice> mayChangedOldEntry( TimeIntervalKey oldKey, Slice value )
    {
        TimeIntervalValueEntry tmp = new TimeIntervalValueEntry( oldKey, value );
        changedNextOldKey = null;
        return tmp;
    }

    private TimeIntervalKey getNextOldKey( Entry<TimeIntervalKey,Slice> oldEntry )
    {
        if ( changedNextOldKey == null )
        {
            return oldEntry.getKey();
        }
        else
        {
            if ( oldEntry.getKey() == changedNextOldKey.getLeft() ) // yes! need pointer equal here.
            {
                return changedNextOldKey.getRight();
            }
            else
            {
                return oldEntry.getKey();
            }
        }
    }

    private void pollOldUntil(EntityPropertyId id, TimePointL end )
    {
        while ( old.hasNext() )
        {
            Entry<TimeIntervalKey,Slice> oldEntry = old.peek();
            TimeIntervalKey oldKey = oldEntry.getKey();
            EntityPropertyId oldInternalKey = oldKey.getId();
            if ( !oldInternalKey.equals( id ) )
            {
                changedNextOldKey = null;
                return;
            }
            if ( oldKey.end().compareTo(end) <= 0 )
            {
                old.next();
            }
            else
            {
                break;
            }
        }
        if ( old.hasNext() ) // id must be equal.
        {
            Entry<TimeIntervalKey,Slice> oldEntry = old.peek();
            TimeIntervalKey oldKey = oldEntry.getKey();
            if ( oldKey.start().compareTo(end) <= 0 )
            {
                changedNextOldKey = Pair.of( oldKey, oldKey.changeStart( end.next() ) );
            }
        }
    }
}
