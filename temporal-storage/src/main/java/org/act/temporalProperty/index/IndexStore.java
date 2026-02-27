package org.act.temporalProperty.index;

import org.act.temporalProperty.impl.*;
import org.act.temporalProperty.index.aggregation.*;
import org.act.temporalProperty.index.value.*;
import org.act.temporalProperty.index.value.rtree.IndexEntry;
import org.act.temporalProperty.meta.PropertyMetaData;
import org.act.temporalProperty.query.TimePointL;
import org.act.temporalProperty.query.aggr.AggregationIndexQueryResult;
import org.act.temporalProperty.query.aggr.ValueGroupingMap;
import org.act.temporalProperty.util.SliceOutput;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

import static org.act.temporalProperty.index.IndexType.*;
import static org.act.temporalProperty.index.IndexUpdater.*;

/**
 * Created by song on 2018-03-19.
 */
public class IndexStore {
    private final TemporalPropertyStoreImpl tpStore;
    private final File indexDir;
    private IndexTableCache cache;

    private AggregationIndexOperator aggr;
    private ValueIndexOperator value;
    private IndexMetaManager meta;

    public IndexStore(File indexDir, TemporalPropertyStoreImpl store, IndexMetaManager indexMetaManager) throws IOException {
        if(!indexDir.exists() && !indexDir.mkdir()) throw new IOException("unable to create index dir");
        this.tpStore = store;
        this.indexDir = indexDir;
        this.cache = new IndexTableCache(indexDir, 40);
        this.meta = indexMetaManager;
        this.aggr = new AggregationIndexOperator( indexDir, store, cache, meta );
        this.value = new ValueIndexOperator( indexDir, store, cache, meta );
    }

    public void close(){
        this.cache.close();
    }

    public void encode(SliceOutput out){
        //TODO fixme
    }

//    public List<IndexMetaData> availableSingleValIndexes(int proId, int start, int end){
//        TreeMap<Integer, IndexMetaData> map = singleVal.get(proId);
//        if(map==null){
//            return Collections.emptyList();
//        }else{
//            return new ArrayList<>(map.subMap(start, true, end, true).values());
//        }
//    }
//
//    public List<IndexMetaData> availableMultiValIndexes(List<Integer> proIds, int start, int end){
//        proIds.sort(Integer::compareTo);
//        Collection<List<IndexMetaData>> list = multiVal.subMap(start, true, end, true).values();
//        List<IndexMetaData> result = new ArrayList<>();
//        for(List<IndexMetaData> lst : list) {
//            result.addAll(lst);
//        }
//        return result;
//    }

    public long createValueIndex(TimePointL start, TimePointL end, List<Integer> proIds, List<IndexValueType> types) throws IOException {
        return value.create(start, end, proIds, types);
    }

    public long createAggrDurationIndex(PropertyMetaData pMeta, TimePointL start, TimePointL end, ValueGroupingMap valueGrouping, int every, int timeUnit) throws IOException {
        return aggr.createDuration(pMeta, start, end, valueGrouping, every, timeUnit);
    }

    public long createAggrMinMaxIndex(PropertyMetaData pMeta, TimePointL start, TimePointL end, int every, int timeUnit, IndexType type) throws IOException {
        return aggr.createMinMax(pMeta, start, end, every, timeUnit, type);
    }

    public List<IndexEntry> queryValueIndex( IndexQueryRegion condition, MemTable cache ) throws IOException {
        return value.query(condition, cache);
    }

    public long queryValueIndexCardinality( IndexQueryRegion condition, MemTable cache ) throws IOException {
        return value.cardinality(condition, cache);
    }

    public AggregationIndexQueryResult queryAggrIndex(long entityId, PropertyMetaData meta, TimePointL start, TimePointL end, long indexId, MemTable cache ) throws IOException {
        return aggr.query(entityId, meta.getPropertyId(), start, end, indexId, cache);
    }

    public void deleteIndex(int propertyId) {
        //Fixme TODO
    }

    public void deleteIndexById(long indexId) throws IOException {
        IndexMetaData m = meta.deleteIndex(indexId);
        if(m!=null) {
            for (IndexFileMeta fileMeta : m.allFiles()) {
                long fid = fileMeta.getFileId();
                String fileName = m.getType().isValueIndex() ? Filename.valIndexFileName(fid) : Filename.aggrIndexFileName(fid);
                Files.delete(new File(indexDir, fileName).toPath());
            }
        }
    }

    /**
     * update index if needed.
     */
    public List<BackgroundTask> createNewIndexTasks()
    {
        List<BackgroundTask> result = new ArrayList<>();
        result.addAll( this.value.createIndexTasks() );
        result.addAll( this.aggr.createIndexTasks() );
        return result;
    }

    public boolean isOnline( long indexId )
    {
        return meta.isOnline( indexId );
    }

    public IndexUpdater onBufferDelUpdate( int propertyId, boolean isStable, FileMetaData fMeta, MemTable mem )
    {
        IndexUpdater.AllIndexUpdater indexUpdater = new IndexUpdater.AllIndexUpdater();
        List<IndexMetaData> indexes = meta.getByProId( propertyId );
        for ( IndexMetaData i : indexes )
        {
            if ( i.getType() == IndexType.MULTI_VALUE )
            {
                for(IndexFileMeta fileMeta : i.allFiles())
                {
                    if(mem.overlap( propertyId, fileMeta.getStartTime(), fileMeta.getEndTime() ))
                    {
                        indexUpdater.add( new MultiPropertyValueBufferMergeUpdater( meta, indexDir, i, fMeta.getNumber(), isStable ) );
                    }
                }
            }
            else
            {
                IndexFileMeta fileMeta = i.getByCorFileId( fMeta.getNumber(), isStable );
                if ( fileMeta != null )
                {
                    if(mem.overlap( propertyId, fileMeta.getStartTime(), fileMeta.getEndTime() ))
                    {
                        if ( i.getType() == SINGLE_VALUE )
                        {
                            indexUpdater.add( new SinglePropertyValueBufferMergeUpdater(meta, indexDir, i, fMeta.getNumber(), isStable ) );
                        }
                        else if ( i.getType() == AGGR_DURATION )
                        {
                            indexUpdater.add( new DurationBufferMergeUpdater( meta, indexDir, i, fMeta.getNumber(), isStable) );
                        }
                        else
                        {
                            indexUpdater.add( new MinMaxBufferMergeUpdater( meta, indexDir, i, fMeta.getNumber(), isStable ));
                        }
                    }
                }
            }
        }
        if ( indexUpdater.isEmpty() )
        {
            return emptyUpdate();
        }
        return indexUpdater;
    }

    public IndexUpdater onMergeUpdate( int propertyId, MemTable mem, List<Long> mergeParticipants )
    {
        IndexUpdater.AllIndexUpdater indexUpdater = new IndexUpdater.AllIndexUpdater();
        List<IndexMetaData> indexes = meta.getByProId( propertyId );
        for ( IndexMetaData i : indexes )
        {
            if ( i.getType() == IndexType.MULTI_VALUE )
            {
                for(IndexFileMeta fileMeta : i.allFiles())
                {
                    if(mem.overlap( propertyId, fileMeta.getStartTime(), fileMeta.getEndTime() ))
                    {
                        indexUpdater.add(new MultiPropertyValueIndexFileUpdater(meta, indexDir, i, mergeParticipants, propertyId) );
                    }
                }
            }
            else
            {
                if ( i.getType() == SINGLE_VALUE )
                {
                    indexUpdater.add( new SinglePropertyValueIndexFileUpdater(meta, indexDir, i, mergeParticipants, true ) );
                }
                else if ( i.getType() == AGGR_DURATION )
                {
                    indexUpdater.add( new DurationMergeUpgradeUpdater( meta, indexDir, i, mergeParticipants, true ) );
                }
                else
                {
                    indexUpdater.add( new MinMaxFileUpgradeUpdater( meta, indexDir, i, mergeParticipants, true ) );
                }
            }
        }
        if ( indexUpdater.isEmpty() )
        {
            return emptyUpdate();
        }
        else
        {
            return indexUpdater;
        }
    }

    public IndexUpdater emptyUpdate()
    {
        return new IndexUpdater()
        {
            public void update( InternalEntry entry )
            {
            }

            public void finish( FileMetaData targetMeta ) throws IOException
            {
            }

            public void updateMeta()
            {
            }

            public void cleanUp()
            {
            }
        };
    }

    public List<IndexMetaData> list()
    {
        return meta.allIndexes();
    }
}
