package org.act.temporalProperty.index;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Table;
import com.google.common.collect.TreeBasedTable;
import org.act.temporalProperty.index.aggregation.AggregationIndexMeta;
import org.act.temporalProperty.index.value.IndexMetaData;
import org.act.temporalProperty.query.TimePointL;
import org.act.temporalProperty.util.TimeIntervalUtil;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * just like systemMeta.
 * Created by song on 2018-05-07.
 */
public class IndexMetaManager
{
    private final AtomicLong nextIndexId;
    private final AtomicLong nextFileId;

    private final HashMap<Long,IndexMetaData> byId = new HashMap<>();

    private final LinkedList<IndexMetaData> offLineIndexes = new LinkedList<>();

    public IndexMetaManager( Set<IndexMetaData> indexes, long nextId, long nextFileId )
    {
        this.nextIndexId = new AtomicLong( nextId );
        this.nextFileId = new AtomicLong( nextFileId );
        for ( IndexMetaData meta : indexes )
        {
            addMeta( meta );
        }
    }

    public long nextIndexId()
    {
        return nextIndexId.getAndIncrement();
    }

    public long nextFileId()
    {
        return nextFileId.getAndIncrement();
    }

    public long getNextIndexId() {
        return nextIndexId.get();
    }

    public long getNextFileId() {
        return nextFileId.get();
    }

    public void addOfflineMeta(IndexMetaData indexMetaData )
    {
        offLineIndexes.add( indexMetaData );
    }

    public List<IndexMetaData> offLineValueIndexes()
    {
        return offLineIndexes.stream().filter( indexMetaData ->
                                               {
                                                   IndexType t = indexMetaData.getType();
                                                   return t == IndexType.SINGLE_VALUE || t == IndexType.MULTI_VALUE;
                                               } ).collect( Collectors.toList() );
    }

    public List<AggregationIndexMeta> offLineAggrIndexes()
    {
        return offLineIndexes.stream().filter( indexMetaData ->
                                               {
                                                   IndexType t = indexMetaData.getType();
                                                   return t.getId() >= 2;
                                               } ).map( indexMetaData -> (AggregationIndexMeta) indexMetaData ).collect( Collectors.toList() );
    }

    public void setOnline( IndexMetaData meta )
    {
        meta.setOnline(true);
        offLineIndexes.remove( meta );
        addMeta( meta );
    }

    public IndexMetaData getByIndexId( long indexId )
    {
        return byId.get( indexId );
    }

    public List<IndexMetaData> getByProId( int propertyId )
    {
        List<IndexMetaData> result = new ArrayList<>();
        for(IndexMetaData meta : byId.values()){
            if( new HashSet<>(meta.getPropertyIdList()).contains(propertyId)) {
                result.add(meta);
            }
        }
        return result;
    }

    public void addMeta( IndexMetaData indexMeta )
    {
        if ( indexMeta.isOnline() )
        {
            byId.put( indexMeta.getId(), indexMeta );
        }
        else
        {
            offLineIndexes.add( indexMeta );
        }
    }

    public List<IndexMetaData> getValueIndex(List<Integer> pids, TimePointL timeMin, TimePointL timeMax )
    {
        Set<IndexMetaData> result = new HashSet<>();
        for(IndexMetaData meta : byId.values()){
            if(     meta.getType().isValueIndex() &&
                    containsAll(meta.getPropertyIdList(), pids) &&
                    TimeIntervalUtil.overlap( meta.getTimeStart(), meta.getTimeEnd(), timeMin, timeMax)) {
                result.add(meta);
            }
        }
        return new ArrayList<>(result);
    }

    private boolean containsAll( List<Integer> a, List<Integer> b )
    {
        return new HashSet<>( a ).containsAll(b);
    }

    public List<IndexMetaData> allIndexes()
    {
        List<IndexMetaData> all = new ArrayList<>();
        all.addAll( byId.values() );
        all.addAll( offLineIndexes );
        return all;
    }

    public boolean isOnline( long indexId )
    {
        return byId.get( indexId ) != null;
    }

    public IndexMetaData deleteIndex( long indexId ){
        IndexMetaData m = byId.get(indexId);
        if(m!=null){
            m.setOnline(false);
            byId.remove(indexId);
        }
        offLineIndexes.removeIf(mm -> mm.getId() == indexId);
        return m;
    }
}
