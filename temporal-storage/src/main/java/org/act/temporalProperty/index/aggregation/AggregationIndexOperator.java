package org.act.temporalProperty.index.aggregation;

import com.google.common.collect.Lists;
import org.act.temporalProperty.exception.TPSRuntimeException;
import org.act.temporalProperty.impl.*;
import org.act.temporalProperty.index.EntityTimeIntervalEntry;
import org.act.temporalProperty.index.IndexFileMeta;
import org.act.temporalProperty.index.IndexMetaManager;
import org.act.temporalProperty.index.IndexTableCache;
import org.act.temporalProperty.index.IndexType;
import org.act.temporalProperty.index.IndexValueType;
import org.act.temporalProperty.index.PropertyFilterIterator;
import org.act.temporalProperty.index.SimplePoint2IntervalIterator;
import org.act.temporalProperty.meta.PropertyMetaData;
import org.act.temporalProperty.query.TimePointL;
import org.act.temporalProperty.query.aggr.*;
import org.act.temporalProperty.util.Slice;
import org.apache.commons.lang3.tuple.Triple;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;

import static org.act.temporalProperty.index.IndexType.*;
/**
 * Created by song on 2018-04-07.
 */
public class AggregationIndexOperator
{
    private final TemporalPropertyStoreImpl tpStore;
    private final IndexTableCache cache;
    private final File indexDir;
    private final IndexMetaManager sysIndexMeta;

    private final DurationIndexManager durationIndex = new DurationIndexManager();
    private final MinMaxIndexManager minMaxIndex = new MinMaxIndexManager();

    public AggregationIndexOperator( File indexDir, TemporalPropertyStoreImpl store, IndexTableCache cache, IndexMetaManager meta )
    {
        this.indexDir = indexDir;
        this.tpStore = store;
        this.cache = cache;
        this.sysIndexMeta = meta;
    }

    public long createDuration(PropertyMetaData pMeta, TimePointL start, TimePointL end, ValueGroupingMap valueGrouping, int every, int timeUnit) throws IOException {
        // 获得新索引文件的ID
        long indexId = sysIndexMeta.nextIndexId();
        //创建新的索引文件元信息
        AggregationIndexMeta meta = new AggregationIndexMeta( indexId,
                                                              AGGR_DURATION,
                                                              pMeta.getPropertyId(),
                                                              IndexValueType.convertFrom( pMeta.getType() ),
                                                              start,
                                                              end,
                                                              every,
                                                              timeUnit,
                                                              valueGrouping );
        // 添加元信息到meta
        sysIndexMeta.addOfflineMeta( meta );
        return indexId;
    }

    public long createMinMax(PropertyMetaData pMeta, TimePointL start, TimePointL end, int every, int timeUnit, IndexType type) throws IOException {
        // 获得新索引文件的ID
        long indexId = sysIndexMeta.nextIndexId();
        //创建新的索引文件元信息
        AggregationIndexMeta meta = new AggregationIndexMeta( indexId,
                                                              type,
                                                              pMeta.getPropertyId(),
                                                              IndexValueType.convertFrom( pMeta.getType() ),
                                                              start,
                                                              end,
                                                              every,
                                                              timeUnit,
                                                              new ValueGroupingMap.Empty() );
        // 添加元信息到meta
        sysIndexMeta.addOfflineMeta( meta );
        // 返回索引ID
        return indexId;
    }

    public AggregationIndexQueryResult query(long entityId, int proId, TimePointL start, TimePointL end, long indexId, MemTable cache ) throws IOException {
        AggregationIndexMeta meta = (AggregationIndexMeta) sysIndexMeta.getByIndexId( indexId );
        if(meta==null || meta.getPropertyIdList().get(0)!=proId){
            throw new TPSRuntimeException("no index with id {}", indexId);
        } else {
            IndexType indexType = meta.getType();
            if (indexType == AGGR_DURATION) {
                return durationIndex.query(entityId, meta, start, end, cache);
            } else if (indexType == AGGR_MIN || indexType == AGGR_MAX || indexType == AGGR_MIN_MAX) {
                return minMaxIndex.query(entityId, meta, start, end, cache);
            }else{
                throw new TPSRuntimeException("index is not aggregation type!");
            }
        }
    }

    public List<BackgroundTask> createIndexTasks()
    {
        List<BackgroundTask> result = new ArrayList<>();
        List<AggregationIndexMeta> notCreated = sysIndexMeta.offLineAggrIndexes();
        for ( AggregationIndexMeta meta : notCreated )
        {
            if ( meta.getType() == AGGR_DURATION )
            {
                result.add( new CreateDurationIndexTask( meta ) );
            }
            else
            {
                result.add( new CreateMinMaxIndexTask( meta ) );
            }
        }
        return result;
    }

    private class DurationIndexManager {

        public AggregationIndexQueryResult query(long entityId, AggregationIndexMeta meta, TimePointL start, TimePointL end, MemTable cache ) throws IOException {
            Map<Integer,Integer> result = new TreeMap<>();

            // 找出可以用索引加速的时间区间
            IntervalStatus timeGroups = accelerateGroups( meta, start, end, entityId, cache );
            if ( timeGroups.canAccelerate() )
            {
                // 如果可以加速, 则使用索引得到这段时间的结果
                result = queryIndex( entityId, meta, timeGroups );
            }
            // 找出不用索引加速的时间区间(列表)
            if ( timeGroups.needQueryStorage() )
            {
                int proId = meta.getPropertyIdList().get(0);
                for ( Entry<TimePointL, TimePointL> time : timeGroups.getQueryIntervals() )
                {
                    // 进行range查询并返回结果
                    TimePointL timeRangeStart = time.getKey();
                    TimePointL timeRangeEnd = time.getValue();
                    // 根据索引信息(timeUnit, every, valueGroup)构建一个range查询
                    DurationStatisticAggregationQuery<Integer> packedQuery = packQuery( meta, timeRangeStart, timeRangeEnd );
                    Map<Integer,Integer> rangeQueryResult =
                            (Map<Integer,Integer>) tpStore.getRangeValue( entityId, proId, timeRangeStart, timeRangeEnd, packedQuery, cache );
                    // 合并结果
                    result = mergeAggrResult(result, rangeQueryResult);
                }
            }
            return new AggregationIndexQueryResult( result, timeGroups.getAccelerateTime(), meta.getValGroupMap() );
        }

        private Map<Integer,Integer> queryIndex( long entityId, AggregationIndexMeta meta, IntervalStatus status ) throws IOException
        {
            TimePointL startTimeGroup = status.getTimeStartGroup();
            TimePointL endTimeGroup = status.getTimeEndGroup();
            Map<Integer,Integer> result = new TreeMap<>();
            Collection<IndexFileMeta> fileShouldSearch = meta.getFilesByTime( startTimeGroup, endTimeGroup );
            for ( IndexFileMeta fMeta : fileShouldSearch)
            {
                queryOneFile( result, fMeta.getFileId(), entityId, startTimeGroup, endTimeGroup, status );
            }
            return result;
        }

        private void queryOneFile(Map<Integer,Integer> result, long indexFileId, long entityId, TimePointL startTimeGroup, TimePointL endTimeGroup, IntervalStatus status ) throws IOException
        {
            String filePath = new File( indexDir, Filename.aggrIndexFileName( indexFileId ) ).getAbsolutePath();
            SeekingIterator<Slice,Slice> iterator = cache.getTable( filePath ).iterator();
            AggregationIndexKey searchKey = new AggregationIndexKey( entityId, startTimeGroup, 0 );
            iterator.seek( searchKey.encode() );
            while ( iterator.hasNext() )
            {
                Entry<Slice,Slice> entry = iterator.next();
                AggregationIndexKey key = new AggregationIndexKey( entry.getKey() );
                TimePointL timeGroupId = key.getTimeGroupId();
                if ( key.getEntityId() == entityId && startTimeGroup.compareTo(timeGroupId)<=0 && timeGroupId.compareTo(endTimeGroup) <= 0 )
                {
                    if ( status.isValid( timeGroupId ) )
                    {
                        result.putIfAbsent( key.getValueGroupId(), 0 );
                        int duration = entry.getValue().getInt( 0 );
                        result.merge( key.getValueGroupId(), duration, ( oldVal, newVal ) -> oldVal + newVal );
                    }//else: continue
                }
                else if(key.compareTo(searchKey)>0)
                {
                    return;
                }
            }
        }

        private DurationStatisticAggregationQuery<Integer> packQuery(AggregationIndexMeta meta, TimePointL start, TimePointL end )
        {
            ValueGroupingMap vGroup = meta.getValGroupMap();

            return new DurationStatisticAggregationQuery<Integer>( start, end )
            {
                @Override
                public void setValueType(String valueType) {}

                public Integer computeGroupId(TimeIntervalEntry entry) {
                    return vGroup.group(entry.value());
                }
                public Object onResult(Map result) {
                    return result;
                }
            };
        }

        private Map<Integer,Integer> mergeAggrResult( Map<Integer,Integer> a, Map<Integer,Integer> b )
        {
            Map<Integer,Integer> smaller = (a.size() < b.size() ? a : b);
            Map<Integer,Integer> larger = (smaller == a ? b : a);
            for(Entry<Integer, Integer> entry : smaller.entrySet()){
                int valGroupId = entry.getKey();
                int sumOfDuration = entry.getValue();
                larger.merge(valGroupId, sumOfDuration, (old, cur) -> old+cur);
            }
            return larger;
        }

    }



    private class MinMaxIndexManager {

        public AggregationIndexQueryResult query(long entityId, AggregationIndexMeta meta, TimePointL start, TimePointL end, MemTable cache ) throws IOException {
            Map<Integer, Slice> result = new HashMap<>();
            Comparator<? super Slice> cp = ValueGroupingMap.getComparator( meta.getValueTypes().get( 0 ) );
            boolean shouldAddMin = (meta.getType()==AGGR_MIN || meta.getType()==AGGR_MIN_MAX);
            boolean shouldAddMax = (meta.getType()==AGGR_MAX || meta.getType()==AGGR_MIN_MAX);
            // 找出可以用索引加速的时间区间
            IntervalStatus timeGroups = accelerateGroups( meta, start, end, entityId, cache );
            //            Pair<Integer, Integer> timeGroups = calcNewGroup(meta, start, end);
//            long begin = System.currentTimeMillis();
            if ( timeGroups.canAccelerate() )
            {
                // 如果可以加速, 则使用索引得到这段时间的结果（排除不可加速的区间）
                result = queryIndex( entityId, meta, cp, timeGroups, shouldAddMin, shouldAddMax );
//                AggregationQuery.time.add(System.currentTimeMillis() - begin);
            }
            if ( timeGroups.needQueryStorage() )
            {
//                begin = System.currentTimeMillis();
                // 根据索引信息(timeUnit, every, valueGroup)构建一个range查询
                AbstractTimeIntervalAggrQuery packedQuery = packQuery( meta, cp, start, end );
                int proId = meta.getPropertyIdList().get(0);
                for ( Entry<TimePointL, TimePointL> time : timeGroups.getQueryIntervals() )
                {
                    // 进行range查询并返回结果
                    TimePointL timeRangeStart = time.getKey();
                    TimePointL timeRangeEnd = time.getValue();
                    Map<Integer,Slice> rangeQueryResult =
                            (Map<Integer,Slice>) tpStore.getRangeValue( entityId, proId, timeRangeStart, timeRangeEnd, packedQuery, cache );
                    // 合并结果
                    result = mergeAggrResult(result, rangeQueryResult, cp, shouldAddMin, shouldAddMax);
                }
//                AggregationQuery.time.add(System.currentTimeMillis() - begin);
            }
            return new AggregationIndexQueryResult( result, timeGroups.getAccelerateTime(), meta.getValueTypes().get( 0 ).toValueContentType() );
        }

        private Map<Integer,Slice> mergeAggrResult( Map<Integer,Slice> a, Map<Integer,Slice> b, Comparator<? super Slice> cp, boolean shouldAddMin, boolean shouldAddMax )
        {
            Map<Integer,Slice> smaller = (a.size() < b.size() ? a : b);
            Map<Integer,Slice> larger = (smaller == a ? b : a);
            for ( Entry<Integer,Slice> entry : smaller.entrySet() )
            {
                int valGroupId = entry.getKey();//MIN(0) or MAX(1)
                Slice val0 = entry.getValue();
                boolean isMin = valGroupId == AggregationQuery.MIN;
                if(val0 != null) larger.merge( valGroupId, val0, ( old, cur ) -> {
                    if ( old == null )
                    { return cur; }
                    else if ( isMin && shouldAddMin && cp.compare( cur, old ) < 0 )
                    { return cur; }
                    else if ( !isMin && shouldAddMax && cp.compare( cur, old ) > 0 )
                    { return cur; }
                    else
                    { return old; }
                });
            }
            return larger;
        }

        private TreeMap<Integer,Slice> queryIndex( long entityId, AggregationIndexMeta meta, Comparator<? super Slice> cp, IntervalStatus status, boolean shouldAddMin, boolean shouldAddMax ) throws IOException
        {
            TimePointL startTimeGroup = status.getTimeStartGroup();
            TimePointL endTimeGroup = status.getTimeEndGroup();
            TreeMap<Integer,Slice> result = new TreeMap<>();
            for ( IndexFileMeta fMeta : meta.getFilesByTime( startTimeGroup, endTimeGroup ) )
            {
                queryOneFile( result, fMeta.getFileId(), entityId, startTimeGroup, endTimeGroup, cp, status, shouldAddMin, shouldAddMax );
            }
            return result;
        }

        private void queryOneFile(Map<Integer,Slice> result, long indexFileId, long entityId, TimePointL startTimeGroup, TimePointL endTimeGroup, Comparator<? super Slice> cp, IntervalStatus status, boolean shouldAddMin, boolean shouldAddMax ) throws IOException
        {AggregationQuery.cnt[0]++;
            String filePath = new File( indexDir, Filename.aggrIndexFileName( indexFileId ) ).getAbsolutePath();
            SeekingIterator<Slice,Slice> iterator = cache.getTable( filePath ).iterator();

            AggregationIndexKey searchKey = new AggregationIndexKey(entityId, startTimeGroup, AggregationQuery.MIN);
            iterator.seek( searchKey.encode() );
            while ( iterator.hasNext() )
            {
                Entry<Slice,Slice> entry = iterator.next();
                AggregationIndexKey key = new AggregationIndexKey( entry.getKey() );
                TimePointL timeGroupId = key.getTimeGroupId();
                if ( key.getEntityId() == entityId && startTimeGroup.compareTo(timeGroupId)<=0 && timeGroupId.compareTo(endTimeGroup)<=0 )
                {
                    if ( status.isValid( timeGroupId ) )
                    {
                        Slice val = entry.getValue();
                        if ( shouldAddMin && key.getValueGroupId() == AggregationQuery.MIN )
                        {
                            result.merge( AggregationQuery.MIN, val, ( oldVal, newVal ) -> (cp.compare( newVal, oldVal ) < 0) ? newVal : oldVal );
                        }
                        if ( shouldAddMax && key.getValueGroupId() == AggregationQuery.MAX )
                        {
                            result.merge( AggregationQuery.MAX, val, ( oldVal, newVal ) -> (cp.compare( newVal, oldVal ) > 0) ? newVal : oldVal );
                        }
                    }//else: continue
                }
                else if(key.compareTo(searchKey)>0)
                {
                    return;
                }
            }
        }

        private AbstractTimeIntervalAggrQuery packQuery(AggregationIndexMeta meta, Comparator<? super Slice> cp, TimePointL start, TimePointL end )
        {

            return new AbstractTimeIntervalAggrQuery<Integer,Slice>( start, end )
            {
                @Override
                public void setValueType(String valueType) {

                }

                Slice min=null,max=null;

                @Override
                public Integer computeGroupId( TimeIntervalEntry entry )
                {
                    Slice val = entry.value();
                    if(min==null){
                        min = val;
                    }
                    else if ( cp.compare( val, min ) < 0 )
                    {
                        min = val;
                        return null;
                    }//else do nothing.
                    if(max==null){
                        max = val;
                    }
                    else if ( cp.compare( val, max ) > 0 )
                    {
                        max = val;
                        return null;
                    }//else do nothing.
                    return null;
                }

                @Override
                public Slice aggregate( Integer integer, Collection<TimeIntervalEntry> groupItems )
                {
                    return null;
                }

                public Object onResult( Map<Integer,Slice> result )
                {
                    result = new TreeMap<>();
                    result.put( AggregationQuery.MAX, max );
                    result.put( AggregationQuery.MIN, min );
                    return result;
                }
            };
        }
    }

    private IntervalStatus accelerateGroups(AggregationIndexMeta meta, TimePointL start, TimePointL end, long entityId, MemTable cache )
    {
        int proId = meta.getPropertyIdList().get( 0 );
        List<TimePointL> time = Lists.newArrayList( meta.getTimeGroupAvailable( start, end.next() ) );
        IntervalStatus status = new IntervalStatus();
        if ( time.size() > 1 ) {
            for ( int i = 1; i < time.size(); i++ ) {
                TimePointL iStart = time.get( i - 1 );
                TimePointL iEnd = time.get( i );
                if ( !tpStore.cacheOverlap( proId, entityId, iStart, iEnd.pre(), cache ) ) {
                    status.addValidTimeGroup( iStart, iStart, iEnd.pre() );
                } else {
                    status.addInvalidTimeRange( iStart, iEnd.pre() );
                }
            }
            if ( time.get( 0 ).compareTo(start) > 0 ) {
                status.addInvalidTimeRange( start, time.get( 0 ).pre() );
            }
            if ( time.get( time.size() - 1 ).compareTo(end) <= 0 ) {
                status.addInvalidTimeRange( time.get( time.size() - 1 ), end );
            }
        } else {
            status.addInvalidTimeRange( start, end );
        }
        return status;
    }

    private class IntervalStatus
    {
        private int accelerateTime = 0;
        private Set<TimePointL> validTimeGroup = new TreeSet<>();
        private TreeMap<TimePointL,TimePointL> queryIntervals = new TreeMap<>();

        public void addValidTimeGroup(TimePointL timeGroupId, TimePointL start, TimePointL end )
        {
            validTimeGroup.add( timeGroupId );
            accelerateTime += (end.val() - start.val() + 1);
        }

        public void addInvalidTimeRange(TimePointL start, TimePointL end )
        {
            if ( queryIntervals.isEmpty() )
            {
                queryIntervals.put( start, end );
            }
            else
            {
                Entry<TimePointL, TimePointL> last = queryIntervals.lastEntry();
                if ( start.equals(last.getValue().next()) )
                {
                    queryIntervals.put( last.getKey(), end );
                }
                else
                {
                    queryIntervals.put( start, end );
                }
            }
        }

        public TimePointL getTimeStartGroup()
        {
            return Collections.min( validTimeGroup );
        }

        public TimePointL getTimeEndGroup()
        {
            return Collections.max( validTimeGroup );
        }

        public boolean isValid( TimePointL timeGroupId )
        {
            return validTimeGroup.contains( timeGroupId );
        }

        public boolean canAccelerate()
        {
            return !validTimeGroup.isEmpty();
        }

        public boolean needQueryStorage()
        {
            return !queryIntervals.isEmpty();
        }

        public Set<Entry<TimePointL, TimePointL>> getQueryIntervals()
        {
            return queryIntervals.entrySet();
        }

        public int getAccelerateTime()
        {
            return accelerateTime;
        }
    }

    private class CreateDurationIndexTask implements BackgroundTask
    {
        private final AggregationIndexMeta meta;
        TimeGroupBuilder timeGroup;

        public CreateDurationIndexTask( AggregationIndexMeta meta )
        {
            this.meta = meta;
            // 计算索引的最小时间分块及时间块对应的ID
            timeGroup = meta.getTimeGroupMap();
        }

        @Override
        public void runTask() throws IOException
        {
            // 获得构造索引文件需要的, 用于读取存储数据的iterator
            List<Triple<Boolean,FileMetaData,SearchableIterator>> raw = tpStore.buildIndexIterator( meta.getTimeStart(), meta.getTimeEnd(), meta.getPropertyIdList() );
            // 根据时间分块和value分区, 计算得出索引文件的Entry
            NavigableSet<TimePointL> subTimeGroup = timeGroup.calcNewGroup( meta.getTimeStart(), meta.getTimeEnd() );
            for ( int j=0; j<raw.size(); j++ )
            {
                Triple<Boolean,FileMetaData,SearchableIterator> i = raw.get(j);
                FileMetaData dataFileMeta = i.getMiddle();
                //把文件头时间加进来
                if(j>0) subTimeGroup.add(dataFileMeta.getSmallest());
                SearchableIterator iterator = new PropertyFilterIterator( meta.getPropertyIdList(), i.getRight() );
                // 将原始时间点Entry数据转换为时间区间Entry数据
                TimePointL timeEnd = dataFileMeta.getLargest().compareTo(meta.getTimeEnd()) < 0 ? dataFileMeta.getLargest() : meta.getTimeEnd();
                Iterator<EntityTimeIntervalEntry> interval = new SimplePoint2IntervalIterator( iterator, timeEnd );
                // 给原始entry附加时间分组信息
                Iterator<AggregationIndexEntry> aggrEntries = new Interval2AggrEntryIterator( interval, meta.getValGroupMap(), subTimeGroup );
                // 将iterator的entry放入数组进行排序
                List<AggregationIndexEntry> data = Lists.newArrayList( aggrEntries );
                data.sort( Comparator.comparing( AggregationIndexEntry::getKey ) );
                // 排序后写入文件
                long fileId = sysIndexMeta.nextFileId();
                File indexFile = new File( indexDir, Filename.aggrIndexFileName( fileId ) );
                AggregationIndexFileWriter w = new AggregationIndexFileWriter( data, indexFile );
                long fileSize = w.write();
                IndexFileMeta fileMeta = new IndexFileMeta(
                        meta.getId(),
                        fileId,
                        fileSize,
                        dataFileMeta.getSmallest(),
                        dataFileMeta.getLargest(),
                        i.getMiddle().getNumber(),
                        i.getLeft(),
                        subTimeGroup.subSet(dataFileMeta.getSmallest(), true, dataFileMeta.getLargest(), true) );
                meta.addFile( fileMeta );
            }
        }

        @Override
        public void updateMeta() throws IOException
        {
            sysIndexMeta.setOnline( this.meta );
        }

        @Override
        public void cleanUp() throws IOException
        {
            // do nothing.
        }
    }

    private class CreateMinMaxIndexTask implements BackgroundTask
    {
        private final AggregationIndexMeta meta;
        private final TimeGroupBuilder timeGroup;

        public CreateMinMaxIndexTask( AggregationIndexMeta meta )
        {
            this.meta = meta;
            this.timeGroup = meta.getTimeGroupMap();
        }

        @Override
        public void runTask() throws IOException
        {
//            System.out.println("CreateMinMaxIndexTask.runTask: "+System.currentTimeMillis());
            // 获得构造索引文件需要的, 用于读取存储数据的iterator
            List<Triple<Boolean,FileMetaData,SearchableIterator>> raw = tpStore.buildIndexIterator( meta.getTimeStart(), meta.getTimeEnd(), meta.getPropertyIdList() );
            // 根据时间分块和value分区, 计算得出索引文件的Entry(最大最小值)
            NavigableSet<TimePointL> subTimeGroup = timeGroup.calcNewGroup( meta.getTimeStart(), meta.getTimeEnd() );
            for (int j=0; j<raw.size(); j++ )
            {
                Triple<Boolean,FileMetaData,SearchableIterator> i = raw.get(j);
                FileMetaData dataFileMeta = i.getMiddle();
                //把文件头时间加进来
                if( j>0 ) subTimeGroup.add(dataFileMeta.getSmallest());
                // 获得构造索引文件需要的, 用于读取存储数据的iterator
                SearchableIterator iterator = new PropertyFilterIterator( meta.getPropertyIdList(), i.getRight() );
                // 将原始时间点Entry数据转换为时间区间Entry数据
                TimePointL timeEnd = dataFileMeta.getLargest().compareTo(meta.getTimeEnd()) < 0 ? dataFileMeta.getLargest() : meta.getTimeEnd();
                Iterator<EntityTimeIntervalEntry> interval = new SimplePoint2IntervalIterator( iterator, timeEnd );
                // 给原始entry附加时间分组信息
                Iterator<Triple<Long,TimePointL,Slice>> minMax = new MinMaxAggrEntryIterator( interval, subTimeGroup );
                // 将iterator的entry放入数组进行排序
                List<Triple<Long,TimePointL,Slice>> data = Lists.newArrayList( minMax );
                data.sort( Triple::compareTo );
                // 排序后写入文件
                // 索引文件
                long fileId = sysIndexMeta.nextFileId();
                File indexFile = new File( indexDir, Filename.aggrIndexFileName( fileId ) );
                // 求最大最小值
                MinMaxAggrIndexWriter w = new MinMaxAggrIndexWriter( data, indexFile, ValueGroupingMap.getComparator( this.meta.getValueTypes().get( 0 ) ), this.meta.getType() );
                long fileSize = w.write();
                IndexFileMeta fileMeta = new IndexFileMeta(
                        meta.getId(),
                        fileId,
                        fileSize,
                        dataFileMeta.getSmallest(),
                        dataFileMeta.getLargest(),
                        i.getMiddle().getNumber(),
                        i.getLeft(),
                        subTimeGroup.subSet(dataFileMeta.getSmallest(), true, dataFileMeta.getLargest(), true) );
                meta.addFile( fileMeta );
            }
//            System.out.println("CreateMinMaxIndexTask.runTask: "+System.currentTimeMillis());
        }

        @Override
        public void updateMeta() throws IOException
        {
            sysIndexMeta.setOnline( this.meta );
        }

        @Override
        public void cleanUp() throws IOException
        {
            // do nothing.
        }
    }


}
