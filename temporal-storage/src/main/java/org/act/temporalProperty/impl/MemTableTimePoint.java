
package org.act.temporalProperty.impl;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterators;
import com.google.common.collect.PeekingIterator;
import org.act.temporalProperty.query.TimePointL;
import org.act.temporalProperty.table.UserComparator;
import org.act.temporalProperty.util.Slice;
import org.act.temporalProperty.util.TimeIntervalUtil;

import java.util.Map.Entry;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MemTable结构，是动态属性在内存中的存储结构
 *
 */
public class MemTableTimePoint implements SeekingIterable<Slice, Slice>
{
	/**
	 * 所有的数据存储在table中，使用SkipListMap
	 */
    private final ConcurrentSkipListMap<Slice, Slice> table;
    /**
     * 所占用的空间
     */
    private final AtomicLong approximateMemoryUsage = new AtomicLong();

    /**
     * 当前MemTable中数据的最小有效时间
     */
    private TimePointL start = TimePointL.Now;

    /**
     * 当前MemTable中数据的最大有效时间
     */
    private TimePointL end = TimePointL.Init;

    public MemTableTimePoint(UserComparator internalKeyComparator )
    {
        table = new ConcurrentSkipListMap<>(internalKeyComparator);
    }

    public TimePointL getStartTime()
    {
        return start;
    }

    public TimePointL getEndTime()
    {
        return end;
    }

    public boolean isEmpty()
    {
        return table.isEmpty();
    }

    public long approximateMemUsage()
    {
        return approximateMemoryUsage.get();
    }

    /**
     * 向MemTable中增加动态属性数据
     * @param key 动态属性record经过InternalKey编码后的key
     * @param value 值
     */
    public void addToNow(InternalKey key, Slice value )
    {
//        Preconditions.checkArgument( key.length() == 20, "key should all be 20 bytes" );
//        InternalKey internalKey = new InternalKey( key );
        delete( key, TimePointL.Init );

        TimePointL startTime = key.getStartTime();
        this.start = TimeIntervalUtil.min(startTime, start);
        this.end = TimeIntervalUtil.max(startTime, end);

        table.put(key.encode(), value);

        approximateMemoryUsage.addAndGet( value.length() + 20 ); // 20 is key.length()
    }

    public void addInterval(InternalKey key, int endTime, Slice value )
    {
//        Preconditions.checkArgument( key.length() == 20, "key should all be 20 bytes" );
//        InternalKey internalKey = new InternalKey( key );
        //Entry<InternalKey, Slice> entry = table.ceilingEntry(internalKey);



        TimePointL startTime = key.getStartTime();
        this.start = TimeIntervalUtil.min(startTime, start);
        this.end = TimeIntervalUtil.max(startTime, end);

        table.put(key.encode(), value);

        approximateMemoryUsage.addAndGet( value.length() + 20 ); // 20 is key.length()
    }

    private void delete(InternalKey from, TimePointL to){
        if(to==TimePointL.Init){
            table.tailMap( from.encode() ).keySet().removeIf( key -> InternalKey.decode(key).getEntityId()==from.getEntityId() );
        }else{
            table.tailMap( from.encode() ).keySet().removeIf( key -> {
                InternalKey tmp = InternalKey.decode( key );
                return (tmp.getEntityId()==from.getEntityId() && tmp.getStartTime().compareTo(to)<=0);
            });
        }
    }

    /**
     * 从MemTable中查询相应数据
     * @param key 动态属性record经过InternalKey编码后的key
     */
    public Slice get(InternalKey key)
    {
        Preconditions.checkNotNull(key, "key is null");
        Entry<Slice, Slice> entry = table.floorEntry( key.encode() );
        if (entry == null) {
            return null;
        }
        InternalKey ansKey = InternalKey.decode( entry.getKey() );
        if( !ansKey.getId().equals( key.getId() ) )
            return null;
        return entry.getValue();
    }

    /**
     * 返回能够对整个MemTable提供便利的iterator
     */
    @Override
    public MemTableIterator iterator()
    {
        return new MemTableIterator();
    }
    
    /**
     * 对MemTable提供遍历功能的Iterator
     *
     */
    public class MemTableIterator
            implements SeekingIterator<Slice,Slice>
    {
        private PeekingIterator<Entry<Slice, Slice>> iterator;

        public MemTableIterator()
        {
            iterator = Iterators.peekingIterator(table.entrySet().iterator());
        }

        @Override
        public boolean hasNext()
        {
            return iterator.hasNext();
        }

        /**
         * 将指针指向第一个数据项
         */
        @Override
        public void seekToFirst()
        {
            iterator = Iterators.peekingIterator(table.entrySet().iterator());
        }

        /**
         * 将指针指向制定key的数据项
         */
        @Override
        public void seek(Slice targetKey)
        {
            Slice fromkey = MemTableTimePoint.this.table.floorKey( targetKey );
            if( null == fromkey )
            {
                iterator = Iterators.peekingIterator(table.entrySet().iterator());
            }
            else    
                iterator = Iterators.peekingIterator(table.tailMap(fromkey).entrySet().iterator());
        }

        @Override
        public Entry<Slice, Slice> peek()
        {
            return iterator.peek();
        }

        @Override
        public Entry<Slice, Slice> next()
        {
            return iterator.next();
        }

        @Override
        public void remove()
        {
            throw new UnsupportedOperationException();
        }
    }

}
