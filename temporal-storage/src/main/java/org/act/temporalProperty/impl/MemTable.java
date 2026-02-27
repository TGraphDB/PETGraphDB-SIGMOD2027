package org.act.temporalProperty.impl;

import com.google.common.base.Preconditions;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Iterators;
import com.google.common.collect.PeekingIterator;
import org.act.temporalProperty.exception.ValueUnknownException;
import org.act.temporalProperty.helper.AbstractSearchableIterator;
import org.act.temporalProperty.helper.DebugIterator;
import org.act.temporalProperty.query.TemporalValue;
import org.act.temporalProperty.query.TimeInterval;
import org.act.temporalProperty.query.TimeIntervalKey;
import org.act.temporalProperty.query.TimePointL;
import org.act.temporalProperty.util.Slice;
import org.act.temporalProperty.util.Slices;
import org.act.temporalProperty.vo.EntityPropertyId;
import org.act.temporalProperty.vo.TimeIntervalValueEntry;
import org.apache.commons.lang3.tuple.Triple;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Modified MemTable, which only expose time interval API.
 */
public class MemTable
{
//    private final TreeMap<EntityPropertyId, TemporalValue<Value>> table = new TreeMap<>(EntityPropertyId::compareTo);
    private final ConcurrentSkipListMap<EntityPropertyId, TemporalValue<Value>> table = new ConcurrentSkipListMap<>(EntityPropertyId::compareTo);

    private AtomicLong approximateMemoryUsage = new AtomicLong(0);

    public boolean isEmpty()
    {
        return table.isEmpty();
    }

    public long approximateMemUsage()
    {
        return approximateMemoryUsage.get();
    }

    public void addToNow( InternalKey key, Slice value )
    {
        Preconditions.checkArgument( key.getValueType() != ValueType.UNKNOWN );
        EntityPropertyId id = key.getId();
        add( id, new TimeInterval( key.getStartTime() ), new Value( key.getValueType(), value ) );
    }

    public void addInterval( InternalKey key, TimePointL endTime, Slice value )
    {
        Preconditions.checkNotNull( key );
        Preconditions.checkArgument( key.getStartTime().compareTo(endTime) <= 0 );
        add( key.getId(), new TimeInterval( key.getStartTime(), endTime ), new Value( key.getValueType(), value ) );
    }

    public void addInterval( TimeIntervalKey key, Slice value )
    {
        Preconditions.checkNotNull( key );
        ValueType valueType = key.getValueType();
        Preconditions.checkArgument( valueType!=ValueType.VALUE);
        add( key.getId(), key, new Value( key.getValueType(), value ) );
    }

    private void add(EntityPropertyId id, TimeInterval interval, Value value )
    {
//        System.out.println(interval);
        table.computeIfAbsent( id, ( i ) -> new TemporalValue<>() ).put( interval, value );
        approximateMemoryUsage.getAndAdd(id.byteCount() + 16 + value.val.length());
    }

    public Slice get( InternalKey key ) throws ValueUnknownException
    {
        Preconditions.checkNotNull( key, "key is null" );
        TemporalValue<Value> entityMap = table.get( key.getId() );
        if ( entityMap == null )
        {
            throw new ValueUnknownException(); //no entity
        }
        Value entry = entityMap.get( key.getStartTime() );
        if ( entry != null )
        {
            if ( entry.valueType != ValueType.INVALID )
            {
                return entry.val;
            }
            else //else: invalid value
            {
                return null;
            }
        }
        else //else: value is unknown.
        {
            throw new ValueUnknownException();
        }
    }

    public String toString()
    {
        Map<Integer,TemporalValue<Boolean>> result = new HashMap<>();
        StringBuilder sb = new StringBuilder();
        for ( Entry<EntityPropertyId, TemporalValue<Value>> entityEntry : table.entrySet() )
        {
            int proId = entityEntry.getKey().getPropertyId();
            result.putIfAbsent( proId, new TemporalValue<>() );
            TemporalValue<Boolean> tMap = result.get( proId );
            TemporalValue<Value> entityMap = entityEntry.getValue();
            Iterator<Entry<TimeInterval,Value>> it = entityMap.intervalEntries();
            while ( it.hasNext() )
            {
                Entry<TimeInterval,Value> entry = it.next();
                tMap.put( entry.getKey(), true );
            }
        }
        sb.append( "propertyCount(" ).append( result.size() ).append( ")" );
        for ( Entry<Integer,TemporalValue<Boolean>> entry : result.entrySet() )
        {
            sb.append( '[' ).append( entry.getKey() ).append( ']' );
            TimeInterval interval = entry.getValue().covered();
            sb.append( '(' ).append( interval.start() ).append( '~' ).append( interval.end() ).append( ") " );
        }
        return sb.toString();
    }

    public SearchableIterator iterator()
    {
        return DebugIterator.wrap(new MemTableIterator(table));
    }

    public PeekingIterator<Entry<TimeIntervalKey,Slice>> intervalEntryIterator()
    {
        return new IntervalIterator();
    }

    public boolean overlap( EntityPropertyId id, TimePointL startTime, TimePointL endTime )
    {
        TemporalValue<Value> entityMap = table.get( id );
        if ( entityMap == null )
        {
            return false;
        }
        else
        {
            return entityMap.overlap( startTime, endTime );
        }
    }

    public Map<Integer,MemTable> separateByProperty()
    {
        Map<Integer,MemTable> result = new TreeMap<>();
        for ( Entry<EntityPropertyId, TemporalValue<Value>> e : table.entrySet() )
        {
            int proId = e.getKey().getPropertyId();
            result.computeIfAbsent( proId, i -> new MemTable() ).addEntry( e.getKey(), e.getValue() );
        }
        return result;
    }

    private void addEntry( EntityPropertyId key, TemporalValue<Value> value )
    {
        table.put( key, value );
    }

    public boolean overlap(int proId, TimePointL startTime, TimePointL endTime )
    {
        for ( Entry<EntityPropertyId,TemporalValue<Value>> entityEntry : table.entrySet() )
        {
            if ( entityEntry.getKey().getPropertyId() == proId )
            {
                TemporalValue<Value> entityMap = entityEntry.getValue();
                if ( entityMap.overlap( startTime, endTime ) )
                {
                    return true;
                }
            }
        }
        return false;
    }

    public void coverTime( TemporalValue<Boolean> tMap, Set<Integer> proIds, TimePointL start, TimePointL end )
    {
        for ( Entry<EntityPropertyId, TemporalValue<Value>> entityEntry : table.entrySet() )
        {
            if ( proIds.contains( entityEntry.getKey().getPropertyId() ) )
            {
                TemporalValue<Value> entityMap = entityEntry.getValue();
                Iterator<Entry<TimeInterval,Value>> it = entityMap.intervalEntries( start, end );
                while ( it.hasNext() )
                {
                    Entry<TimeInterval,Value> entry = it.next();
                    tMap.put( entry.getKey(), true );
                }
            }
        }
    }

    private class Value
    {
        ValueType valueType;
        Slice val;

        Value(ValueType valueType, Slice val)
        {
            this.valueType = valueType;
            this.val = val;
        }
    }

    /**
     * 迭代结果中不可能有连续的Unknown项。
     * 理由：1. 同一个TemporalValue中不可能操作后出现连续两个Unknown标记的数据项。（产生unknown的原因是这里的值原先是unknown状态（可能
     * 是空或unknown，但都表示unknown状态）时才会产生UNKNOWN数据项，而不可能把原先有值的地方变成unknown。
     * 2. 每个temporalValue迭代出来的第一项也不可能是unknown的，因此多个temporalValue不会产生连续的unknown标记。
     */
    public static class MemTableIterator extends AbstractSearchableIterator
    {
        private final NavigableMap<EntityPropertyId, TemporalValue<Value>> table;
        private Iterator<Entry<EntityPropertyId, TemporalValue<Value>>> tPropIter;
        private PeekingIterator<Triple<TimePointL,Boolean,Value>> tValIter;
        private EntityPropertyId curId;

        MemTableIterator(NavigableMap<EntityPropertyId, TemporalValue<Value>> table)
        {
            this.table = table;
            this.tPropIter = table.entrySet().iterator();
        }

        @Override
        protected InternalEntry computeNext()
        {
            while ( tValIter == null || !tValIter.hasNext() )
            {
                if ( tPropIter.hasNext() ) {
                    Entry<EntityPropertyId, TemporalValue<Value>> entry = tPropIter.next();
                    TemporalValue<Value> tpValue = entry.getValue();
                    tValIter = tpValue.pointEntries();
                    curId = entry.getKey();
                } else {
                    return endOfData();
                }
            }
            Triple<TimePointL,Boolean,Value> entry = tValIter.next();
            Value v = entry.getRight();
            TimePointL startTime = entry.getLeft();
            Boolean isUnknown = entry.getMiddle();
            if ( isUnknown ) {
                return new InternalEntry( new InternalKey( curId, startTime, ValueType.UNKNOWN ), Slices.EMPTY_SLICE );
            } else {
                return new InternalEntry( new InternalKey( curId, startTime, v.valueType ), v.val );
            }
        }

        @Override
        public void seekToFirst()
        {
            super.resetState();
            tPropIter = table.entrySet().iterator();
            tValIter = null;
            curId = null;
        }

        @Override
        public boolean seekFloor(InternalKey targetKey )
        {
            super.resetState();
            EntityPropertyId result = table.floorKey(targetKey.getId());
            if(result != null){
                tPropIter = table.tailMap(result, true).entrySet().iterator();
                if(tPropIter.hasNext()){
                    Entry<EntityPropertyId, TemporalValue<Value>> entry = tPropIter.next();
                    curId = entry.getKey();
                    tValIter = entry.getValue().pointEntries(targetKey.getStartTime());
                }else{
                    tValIter = null;
                    curId = null;
                }
                return super.seekFloor(targetKey);
            }else{
                tPropIter = table.tailMap(targetKey.getId(), true).entrySet().iterator();
                tValIter = null;
                curId = null;
                return false;
            }
        }

        @Override
        public String toString() {
            return "MemTableIterator{}";
        }
    }

    private class IntervalIterator extends AbstractIterator<Entry<TimeIntervalKey,Slice>> implements PeekingIterator<Entry<TimeIntervalKey,Slice>>
    {
        private PeekingIterator<Entry<EntityPropertyId, TemporalValue<Value>>> tpIter;
        private PeekingIterator<Entry<TimeInterval,Value>> tvIntIter;
        private EntityPropertyId curId;

        private IntervalIterator()
        {
            tpIter = Iterators.peekingIterator( table.entrySet().iterator() );
        }

        @Override
        protected Entry<TimeIntervalKey,Slice> computeNext()
        {
            while ( tvIntIter == null || !tvIntIter.hasNext() )
            {
                if ( tpIter.hasNext() )
                {
                    Entry<EntityPropertyId, TemporalValue<Value>> entry = tpIter.next();
                    tvIntIter = entry.getValue().intervalEntries();
                    curId = entry.getKey();
                }
                else
                {
                    return endOfData();
                }
            }
            Entry<TimeInterval,Value> entry = tvIntIter.next();
            TimeInterval tInt = entry.getKey();
            Value value = entry.getValue();
            TimeIntervalKey intervalKey = new TimeIntervalKey( curId, tInt.start(), tInt.end(), value.valueType);
            return new TimeIntervalValueEntry( intervalKey, value.val );
        }
    }

}
