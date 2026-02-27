package org.act.temporalProperty.index.value;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.PeekingIterator;
import org.act.temporalProperty.exception.TPSRuntimeException;
import org.act.temporalProperty.helper.EqualValFilterIterator;
import org.act.temporalProperty.helper.SameLevelMergeIterator;
import org.act.temporalProperty.impl.*;
import org.act.temporalProperty.index.IndexFileMeta;
import org.act.temporalProperty.index.IndexMetaManager;
import org.act.temporalProperty.index.IndexTableCache;
import org.act.temporalProperty.index.IndexValueType;
import org.act.temporalProperty.index.PropertyFilterIterator;
import org.act.temporalProperty.index.value.cardinality.HyperLogLog;
import org.act.temporalProperty.index.value.rtree.IndexEntry;
import org.act.temporalProperty.index.value.rtree.IndexEntryOperator;
import org.act.temporalProperty.query.TemporalValue;
import org.act.temporalProperty.query.TimeInterval;
import org.act.temporalProperty.query.TimePointL;
import org.act.temporalProperty.util.TimeIntervalUtil;
import org.apache.commons.lang3.tuple.Triple;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.*;

import static org.act.temporalProperty.index.IndexType.*;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;

/**
 * Created by song on 2018-04-07.
 */
public class ValueIndexOperator
{
    private final TemporalPropertyStoreImpl tpStore;
    private final IndexTableCache cache;
    private final File indexDir;
    private final IndexMetaManager sysIndexMeta;

    public ValueIndexOperator( File indexDir, TemporalPropertyStoreImpl store, IndexTableCache cache, IndexMetaManager meta )
    {
        this.indexDir = indexDir;
        this.tpStore = store;
        this.cache = cache;
        this.sysIndexMeta = meta;
    }

    public long create(TimePointL start, TimePointL end, List<Integer> proIds, List<IndexValueType> types) throws IOException {
        long indexId = sysIndexMeta.nextIndexId();
        if (proIds.size() == 1) {
            sysIndexMeta.addOfflineMeta( new IndexMetaData( indexId, SINGLE_VALUE, proIds, types, start, end ) );
        } else {
            sysIndexMeta.addOfflineMeta( new IndexMetaData( indexId, MULTI_VALUE, proIds, types, start, end ) );
        }
        return indexId;
    }

    public List<IndexEntry> query( IndexQueryRegion condition, MemTable cache ) throws IOException
    {
        IndexMetaData meta = valIndexCoverRegion(condition);
        Iterator<IndexEntry> iter = this.getQueryIterator( condition, meta, cache );
        List<IndexEntry> result = new ArrayList<>();
        while(iter.hasNext()){
            result.add(iter.next());
        }
        return result;
    }

    public long cardinality( IndexQueryRegion condition, MemTable cache ) throws IOException
    {
        IndexMetaData meta = valIndexCoverRegion(condition);
        Iterator<HyperLogLog> iter = this.getCardinality( condition, meta, cache );
        HyperLogLog result = HyperLogLog.defaultBuilder();
        while(iter.hasNext()){
            result.addAll(iter.next());
        }
        return result.cardinality();
    }

    private IndexMetaData valIndexCoverRegion(IndexQueryRegion condition) {
        List<PropertyValueInterval> proIntervals = condition.getPropertyValueIntervals();
        List<Integer> pids = proIntervals.stream().map( PropertyValueInterval::getProId ).collect( Collectors.toList() );
        List<IndexMetaData> indexList = sysIndexMeta.getValueIndex( pids, condition.getTimeMin(), condition.getTimeMax() );
        for(IndexMetaData meta : indexList){
            TimePointL s = meta.getTimeStart();
            TimePointL e = meta.getTimeEnd();
            if(TimeIntervalUtil.contains( s, e, condition.getTimeMin(), condition.getTimeMax())){
                return meta;
            }
        }
        throw new TPSRuntimeException("no valid index for query {pid: "+pids+", time: "+condition.getTimeMin()+"~"+condition.getTimeMax()+"}");
    }

    private Iterator<IndexEntry> getQueryIterator( IndexQueryRegion condition, IndexMetaData meta, MemTable cache ) throws IOException
    {
        List<IndexQueryRegion> regionsCanSpeedUp = excludeInvalidTime(condition, cache);
        List<Iterator<IndexEntry>> results = new ArrayList<>();
        for(IndexFileMeta fm : meta.getFilesByTime(condition.getTimeMin(), condition.getTimeMax())) {
            String fileName = Filename.valIndexFileName(fm.getFileId());
            IndexTableCache.IndexTable file = this.cache.getTable(new File(this.indexDir, fileName).getAbsolutePath());
            for (IndexQueryRegion subQuery : regionsCanSpeedUp) {
                results.add(file.iterator(subQuery));
            }
        }
        return Iterators.concat( results.iterator() );
    }

    private Iterator<HyperLogLog> getCardinality(IndexQueryRegion condition, IndexMetaData meta, MemTable cache ) throws IOException
    {
        String fileName = Filename.valIndexFileName(meta.getId());
        List<IndexQueryRegion> regionsCanSpeedUp = excludeInvalidTime( condition, cache );
        IndexTableCache.IndexTable file = this.cache.getTable( new File( this.indexDir, fileName ).getAbsolutePath() );
        List<HyperLogLog> results = new ArrayList<>();
        for ( IndexQueryRegion subQuery : regionsCanSpeedUp )
        {
            results.add( file.cardinalityEstimator( subQuery ) );
        }
        return results.iterator();
    }

    public List<BackgroundTask> createIndexTasks()
    {
        List<BackgroundTask> result = new ArrayList<>();
        List<IndexMetaData> notCreated = sysIndexMeta.offLineValueIndexes();
        for ( IndexMetaData meta : notCreated )
        {
            if ( meta.getType() == MULTI_VALUE )
            {
                result.add( new CreateMultiPropertyValueIndexTask( meta ) );
            }
            else
            {
                result.add( new CreateSinglePropertyValueIndexTask( meta ) );
            }
        }
        return result;
    }

    private List<IndexQueryRegion> excludeInvalidTime( IndexQueryRegion condition, MemTable cache )
    {
        Set<Integer> proIdSet = condition.getPropertyValueIntervals().stream().map( PropertyValueInterval::getProId ).collect( Collectors.toSet() );
        TemporalValue<Boolean> validTime = tpStore.coverTime( proIdSet, condition.getTimeMin(), condition.getTimeMax(), cache );
        if ( validTime.isEmpty() )
        {
            return Collections.singletonList( condition );
        }

        PeekingIterator<Entry<TimeInterval,Boolean>> iter;
        iter = validTime.intervalEntries( condition.getTimeMin(), condition.getTimeMax() );
        List<TimeInterval> validTimeList = Lists.newArrayList( Iterators.transform( iter, Entry::getKey));
        return subQueryRegions( validTimeList, condition );
    }

    public List<IndexQueryRegion> subQueryRegions(List<TimeInterval> validTime, IndexQueryRegion condition )
    {
        List<PropertyValueInterval> proVals = condition.getPropertyValueIntervals();
        return validTime.stream().map( interval ->
                                       {
                                           IndexQueryRegion query = new IndexQueryRegion( interval.start(), interval.end() );
                                           proVals.forEach(query::add);
                                           return query;
                                       } ).collect( Collectors.toList() );
    }

    public class CreateSinglePropertyValueIndexTask implements BackgroundTask
    {
        private final IndexMetaData iMeta;

        public CreateSinglePropertyValueIndexTask( IndexMetaData meta )
        {
            this.iMeta = meta;
            Preconditions.checkArgument( meta.getPropertyIdList().size() == 1, "expect one property, got " + meta.getPropertyIdList().size() );
        }

        @Override
        public void runTask() throws IOException
        {
            IndexEntryOperator op = new IndexEntryOperator( iMeta.getValueTypes(), 4096 );
            List<Triple<Boolean,FileMetaData,SearchableIterator>> iterators =
                    tpStore.buildIndexIterator( iMeta.getTimeStart(), iMeta.getTimeEnd(), iMeta.getPropertyIdList() );
            for ( Triple<Boolean,FileMetaData,SearchableIterator> i : iterators )
            {
                SearchableIterator iterator = new EqualValFilterIterator(new PropertyFilterIterator( iMeta.getPropertyIdList(), i.getRight() ));
                IndexBuilderCallback dataCollector = new IndexBuilderCallback( iMeta.getPropertyIdList(), op );
                while ( iterator.hasNext() )
                {
                    InternalEntry entry = iterator.next();
                    InternalKey key = entry.getKey();
                    if ( key.getValueType() == ValueType.INVALID )
                    {
                        dataCollector.onCall( key.getPropertyId(), key.getEntityId(), key.getStartTime(), null );
                    }
                    else
                    {
                        dataCollector.onCall( key.getPropertyId(), key.getEntityId(), key.getStartTime(), entry.getValue() );
                    }
                }
                FileMetaData dataFileMeta = i.getMiddle();
                PeekingIterator<IndexEntry> data = dataCollector.getIterator( dataFileMeta.getSmallest(), dataFileMeta.getLargest() );

                long fileId = sysIndexMeta.nextFileId();
                String indexFilePath = Filename.valIndexFileName( fileId );
                try ( FileChannel channel = new FileOutputStream( new File( indexDir, indexFilePath ) ).getChannel() )
                {
                    IndexTableWriter writer = new IndexTableWriter( channel, op );
                    while ( data.hasNext() )
                    {
                        writer.add( data.next() );
                    }
                    writer.finish();
                    long fileSize = channel.size();
                    IndexFileMeta fileMeta = new IndexFileMeta( iMeta.getId(), fileId, fileSize, dataFileMeta.getSmallest(), dataFileMeta.getLargest(), i.getMiddle().getNumber(), i.getLeft(),
                                                                Collections.emptyList() );
                    iMeta.addFile( fileMeta );
                }
            }
        }

        @Override
        public void updateMeta() throws IOException
        {
            sysIndexMeta.setOnline( iMeta );
        }

        @Override
        public void cleanUp() throws IOException
        {
            // nothing to do. fresh creation.
        }
    }

    public class CreateMultiPropertyValueIndexTask implements BackgroundTask
    {
        private final IndexMetaData iMeta;

        public CreateMultiPropertyValueIndexTask( IndexMetaData meta )
        {
            this.iMeta = meta;
            Preconditions.checkArgument( meta.getPropertyIdList().size() > 1 );
        }

        @Override
        public void runTask() throws IOException
        {
            IndexEntryOperator op = new IndexEntryOperator( iMeta.getValueTypes(), 4096 );
            List<Triple<Boolean,FileMetaData,SearchableIterator>> iterators =
                    tpStore.buildIndexIterator( iMeta.getTimeStart(), iMeta.getTimeEnd(), iMeta.getPropertyIdList() );

            List<SearchableIterator> iteratorList = new ArrayList<>();
            for ( Triple<Boolean,FileMetaData,SearchableIterator> i : iterators )
            {
                iteratorList.add( i.getRight() );
            }
            SearchableIterator iterator = new EqualValFilterIterator(new PropertyFilterIterator( iMeta.getPropertyIdList(), new SameLevelMergeIterator( iteratorList ) ));
            IndexBuilderCallback dataCollector = new IndexBuilderCallback( iMeta.getPropertyIdList(), op );
            while ( iterator.hasNext() )
            {
                InternalEntry entry = iterator.next();
                InternalKey key = entry.getKey();
                if ( key.getValueType() == ValueType.INVALID )
                {
                    dataCollector.onCall( key.getPropertyId(), key.getEntityId(), key.getStartTime(), null );
                }
                else
                {
                    dataCollector.onCall( key.getPropertyId(), key.getEntityId(), key.getStartTime(), entry.getValue() );
                }
            }
            PeekingIterator<IndexEntry> data = dataCollector.getIterator( iMeta.getTimeStart(), iMeta.getTimeEnd() );

            long fileId = sysIndexMeta.nextFileId();
            String indexFilePath = Filename.valIndexFileName( fileId );
            try ( FileChannel channel = new FileOutputStream( new File( indexDir, indexFilePath ) ).getChannel() )
            {
                IndexTableWriter writer = new IndexTableWriter( channel, op );
                while ( data.hasNext() )
                {
                    writer.add( data.next() );
                }
                writer.finish();
                long fileSize = channel.size();
                IndexFileMeta fileMeta = new IndexFileMeta( iMeta.getId(), fileId, fileSize, iMeta.getTimeStart(), iMeta.getTimeEnd(), 0, false, Collections.emptyList() );
                iMeta.addFile( fileMeta );
            }
        }

        @Override
        public void updateMeta() throws IOException
        {
            sysIndexMeta.setOnline( iMeta );
        }

        @Override
        public void cleanUp() throws IOException
        {
            // nothing to do. fresh creation.
        }
    }
}
