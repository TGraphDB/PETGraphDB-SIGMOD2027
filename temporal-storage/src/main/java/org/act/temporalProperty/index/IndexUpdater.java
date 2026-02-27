package org.act.temporalProperty.index;

import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.PeekingIterator;
import org.act.temporalProperty.impl.*;
import org.act.temporalProperty.index.aggregation.AggregationIndexEntry;
import org.act.temporalProperty.index.aggregation.AggregationIndexFileWriter;
import org.act.temporalProperty.index.aggregation.AggregationIndexMeta;
import org.act.temporalProperty.index.aggregation.Interval2AggrEntryIterator;
import org.act.temporalProperty.index.aggregation.MinMaxAggrEntryIterator;
import org.act.temporalProperty.index.aggregation.MinMaxAggrIndexWriter;
import org.act.temporalProperty.index.aggregation.TimeGroupBuilder;
import org.act.temporalProperty.index.value.IndexBuilderCallback;
import org.act.temporalProperty.index.value.IndexMetaData;
import org.act.temporalProperty.index.value.IndexTableReader;
import org.act.temporalProperty.index.value.IndexTableWriter;
import org.act.temporalProperty.index.value.rtree.IndexEntry;
import org.act.temporalProperty.index.value.rtree.IndexEntryOperator;
import org.act.temporalProperty.query.TimePointL;
import org.act.temporalProperty.query.aggr.ValueGroupingMap;
import org.act.temporalProperty.table.TwoLevelMergeIterator;
import org.act.temporalProperty.util.Slice;
import org.apache.commons.lang3.tuple.Triple;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NavigableSet;

/**
 * update index for one or more (multi-property time value index) property.
 * should update all index relevant.
 * first setMergeInfo() is called, then update called many times, then updateMeta() once, cleanUp() once.
 *
 * Created by song on 2018-05-06.
 */
public interface IndexUpdater
{
    void update( InternalEntry entry );

    void finish( FileMetaData targetMeta ) throws IOException;

    void updateMeta();

    void cleanUp() throws IOException;

    class AllIndexUpdater implements IndexUpdater
    {
        private final List<IndexUpdater> updaters = new ArrayList<>();

        public void update( InternalEntry entry )
        {
            updaters.forEach( indexUpdater -> indexUpdater.update( entry ) );
        }

        public void finish( FileMetaData targetMeta ) throws IOException
        {
            for ( IndexUpdater updater : updaters )
            {
                updater.finish( targetMeta );
            }
        }

        public void updateMeta()
        {
            updaters.forEach( IndexUpdater::updateMeta );
        }

        public void cleanUp() throws IOException
        {
            for ( IndexUpdater updater : updaters )
            {
                updater.cleanUp();
            }
        }

        public boolean isEmpty()
        {
            return updaters.isEmpty();
        }

        public void add( IndexUpdater indexUpdater )
        {
            updaters.add( indexUpdater );
        }
    }

    class SinglePropertyValueIndexFileUpdater implements IndexUpdater
    {
        protected final IndexMetaData meta;
        protected final List<Long> delFileId;
        private IndexFileMeta newFileMeta;
        private IndexBuilderCallback dataCollector;
        private IndexEntryOperator op;
        private IndexMetaManager sysIndexMeta;
        protected File indexDir;
        protected Boolean corIsStable;

        public SinglePropertyValueIndexFileUpdater( IndexMetaManager sysIndexMeta, File indexDir, IndexMetaData indexMetaData, List<Long> deletedUnstableFileId, Boolean corIsStable )
        {
            this.sysIndexMeta = sysIndexMeta;
            this.indexDir = indexDir;
            this.meta = indexMetaData;
            this.delFileId = deletedUnstableFileId;
            this.corIsStable = corIsStable;
            this.op = new IndexEntryOperator( meta.getValueTypes(), 4096 );
            this.dataCollector = new IndexBuilderCallback( meta.getPropertyIdList(), op );
        }

        @Override
        public void update( InternalEntry entry )
        {
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

        @Override
        public void updateMeta()
        {
            for ( Long fileId : delFileId )
            {
                meta.delFileByCorFileId( fileId, false );
            }
            meta.addFile( this.newFileMeta );
        }

        @Override
        public void cleanUp() throws IOException
        {
            for ( Long fileId : delFileId )
            {
                IndexFileMeta fMeta = meta.getByCorFileId( fileId, false );
                File originFile = new File( indexDir, Filename.aggrIndexFileName( fMeta.getFileId() ) );
                Files.delete( originFile.toPath() );
            }
        }

        @Override
        public void finish( FileMetaData targetMeta ) throws IOException
        {
            PeekingIterator<IndexEntry> data = dataCollector.getIterator( targetMeta.getSmallest(), targetMeta.getLargest() );
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
                this.newFileMeta = new IndexFileMeta( meta.getId(), fileId, fileSize, targetMeta.getSmallest(), targetMeta.getLargest(), targetMeta.getNumber(), corIsStable,
                                                      Collections.emptySet() );
            }
        }
    }

    class SinglePropertyValueBufferMergeUpdater extends SinglePropertyValueIndexFileUpdater
    {
        public SinglePropertyValueBufferMergeUpdater( IndexMetaManager sysIndexMeta, File indexDir, IndexMetaData indexMetaData, long deletedFileId, Boolean corIsStable )
        {
            super( sysIndexMeta, indexDir, indexMetaData, Lists.newArrayList( deletedFileId ), corIsStable );
        }

        @Override
        public void cleanUp() throws IOException
        {
            Long fileId = delFileId.get( 0 );
            IndexFileMeta fMeta = meta.getByCorFileId( fileId, corIsStable );
            File originFile = new File( indexDir, Filename.valIndexFileName( fMeta.getFileId() ) );
            Files.delete( originFile.toPath() );
        }
    }

    class MultiPropertyValueIndexFileUpdater implements IndexUpdater
    {
        protected final IndexMetaData meta;
        protected final List<Long> delFileId;
        protected IndexFileMeta newFileMeta;

        private int proId;
        private List<InternalEntry> propertyNewData = new ArrayList<>();
        private IndexMetaManager sysIndexMeta;
        private File indexDir;

        public MultiPropertyValueIndexFileUpdater( IndexMetaManager sysIndexMeta, File indexDir, IndexMetaData indexMetaData, List<Long> deletedUnstableFileId, int proId )
        {
            this.sysIndexMeta = sysIndexMeta;
            this.indexDir = indexDir;
            this.proId = proId;
            this.meta = indexMetaData;
            this.delFileId = deletedUnstableFileId;
        }

        @Override
        public void update( InternalEntry entry )
        {
            propertyNewData.add( entry );
        }

        @Override
        public void updateMeta()
        {
            for ( Long fileId : delFileId )
            {
                meta.delFileByCorFileId( fileId, false );
            }
            meta.addFile( this.newFileMeta );
        }

        @Override
        public void cleanUp() throws IOException
        {
            for ( Long fileId : delFileId )
            {
                IndexFileMeta fMeta = meta.getByCorFileId( fileId, false );
                File originFile = new File( indexDir, Filename.aggrIndexFileName( fMeta.getFileId() ) );
                Files.delete( originFile.toPath() );
            }
        }

        @Override
        public void finish( FileMetaData targetMeta ) throws IOException
        {
            IndexEntryOperator op = new IndexEntryOperator( meta.getValueTypes(), 4096 );
            IndexBuilderCallback dataCollector = new IndexBuilderCallback( meta.getPropertyIdList(), op );
            assert delFileId.size() == 1 : "multi file not implement.";
            String oldIndexFileName = Filename.valIndexFileName( meta.allFiles().get( 0 ).getFileId() );

            List<InternalEntry> propertyOldIntervalData = new ArrayList<>();
            try ( FileChannel readChannel = new FileInputStream( new File( indexDir, oldIndexFileName ) ).getChannel() )
            {
                IndexTableReader reader = new IndexTableReader( readChannel, op );
                List<Integer> proIds = meta.getPropertyIdList();
                while ( reader.hasNext() )
                {
                    IndexEntry entry = reader.next();
                    for ( int i = 0; i < proIds.size(); i++ )
                    {
                        Integer proId = proIds.get( i );
                        if ( proId == this.proId )
                        {
                            propertyOldIntervalData.add( new InternalEntry( new InternalKey( proId, entry.getEntityId(), entry.getStart(), ValueType.VALUE ), entry
                                    .getValue( i ) ) );
                        }
                        else
                        {
                            dataCollector.onCall( proId, entry.getEntityId(), entry.getStart(), entry.getValue( i ) );
                        }
                    }
                }
            }

            SearchableIterator merged =
                    TwoLevelMergeIterator.merge( new List2SearchableIterator( propertyNewData ), new List2SearchableIterator( propertyOldIntervalData ) );
            while ( merged.hasNext() )
            {
                InternalEntry entry = merged.next();
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

            PeekingIterator<IndexEntry> data = dataCollector.getIterator( targetMeta.getSmallest(), targetMeta.getLargest() );
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
                this.newFileMeta = new IndexFileMeta( meta.getId(), fileId, fileSize, targetMeta.getSmallest(), targetMeta.getLargest(), 0, false,
                                                      Collections.emptyList() );
            }
        }
    }

    class MultiPropertyValueBufferMergeUpdater extends MinMaxFileUpgradeUpdater
    {
        public MultiPropertyValueBufferMergeUpdater( IndexMetaManager meta, File indexDir, IndexMetaData indexMetaData, long deletedFileId, boolean isStable )
        {
            super( meta, indexDir, indexMetaData, Lists.newArrayList( deletedFileId ), isStable );
        }

        @Override
        public void cleanUp() throws IOException
        {
            for ( Long fileId : delFileId )
            {
                IndexFileMeta fMeta = meta.getByCorFileId( fileId, false );
                File originFile = new File( indexDir, Filename.aggrIndexFileName( fMeta.getFileId() ) );
                Files.delete( originFile.toPath() );
            }
        }
    }

    abstract class AggregationIndexFileUpdater implements IndexUpdater
    {
        protected final AggregationIndexMeta meta;
        protected final TimeGroupBuilder timeGroup;
        protected final List<Long> delFileId;
        protected final List<InternalEntry> data = new ArrayList<>();
        protected IndexFileMeta newFileMeta;
        protected IndexMetaManager sysIndexMeta;
        protected File indexDir;
        protected Boolean corIsStable;

        public AggregationIndexFileUpdater( IndexMetaManager sysIndexMeta, File indexDir, IndexMetaData indexMetaData, List<Long> deletedUnstableFileId, boolean corIsStable )
        {
            this.sysIndexMeta = sysIndexMeta;
            this.indexDir = indexDir;
            this.corIsStable = corIsStable;
            this.meta = (AggregationIndexMeta) indexMetaData;
            this.timeGroup = this.meta.getTimeGroupMap();
            this.delFileId = deletedUnstableFileId;
        }

        @Override
        public void update( InternalEntry entry )
        {
            data.add( entry );
        }

        @Override
        public void updateMeta()
        {
            for ( Long fileId : delFileId )
            {
                meta.delFileByCorFileId( fileId, false );
            }
            meta.addFile( this.newFileMeta );
        }

        @Override
        public void cleanUp() throws IOException
        {
            for ( Long fileId : delFileId )
            {
                IndexFileMeta fMeta = meta.getByCorFileId( fileId, false );
                File originFile = new File( indexDir, Filename.aggrIndexFileName( fMeta.getFileId() ) );
                Files.delete( originFile.toPath() );
            }
        }
    }

    // update index when multi storage file merge to higher level.
    class DurationMergeUpgradeUpdater extends AggregationIndexFileUpdater
    {
        public DurationMergeUpgradeUpdater( IndexMetaManager meta, File indexDir, IndexMetaData indexMetaData, List<Long> deletedUnstableFileId, boolean isStable )
        {
            super( meta, indexDir, indexMetaData, deletedUnstableFileId, isStable );
        }

        @Override
        public void finish( FileMetaData targetMeta ) throws IOException
        {
            PeekingIterator<InternalEntry> iterator = Iterators.peekingIterator( data.iterator() );
            // 将原始时间点Entry数据转换为时间区间Entry数据
            Iterator<EntityTimeIntervalEntry> interval = new SimplePoint2IntervalIterator( iterator, targetMeta.getLargest() );

            // 根据时间分块和value分区, 计算得出索引文件的Entry
            NavigableSet<TimePointL> subTimeGroup = timeGroup.calcNewGroup( targetMeta.getSmallest(), targetMeta.getLargest() );
            Iterator<AggregationIndexEntry> aggrEntries = new Interval2AggrEntryIterator( interval, meta.getValGroupMap(), subTimeGroup );
            // 将iterator的entry放入数组进行排序
            List<AggregationIndexEntry> data = Lists.newArrayList( aggrEntries );
            data.sort( Comparator.comparing( AggregationIndexEntry::getKey ) );
            // 排序后写入文件
            long fileId = sysIndexMeta.nextFileId();
            File indexFile = new File( indexDir, Filename.aggrIndexFileName( fileId ) );
            AggregationIndexFileWriter w = new AggregationIndexFileWriter( data, indexFile );
            long fileSize = w.write();
            this.newFileMeta = new IndexFileMeta( meta.getId(), fileId, fileSize, targetMeta.getSmallest(), targetMeta.getLargest(), targetMeta.getNumber(), corIsStable,
                                                  subTimeGroup );
        }
    }

    // update index when multi storage file merge to higher level.
    class MinMaxFileUpgradeUpdater extends AggregationIndexFileUpdater
    {
        public MinMaxFileUpgradeUpdater( IndexMetaManager meta, File indexDir, IndexMetaData indexMetaData, List<Long> deletedUnstableFileId, boolean isStable )
        {
            super( meta, indexDir, indexMetaData, deletedUnstableFileId, isStable );
        }

        @Override
        public void finish( FileMetaData targetMeta ) throws IOException
        {
            PeekingIterator<InternalEntry> iterator = Iterators.peekingIterator( data.iterator() );
            // 将原始时间点Entry数据转换为时间区间Entry数据
            Iterator<EntityTimeIntervalEntry> interval = new SimplePoint2IntervalIterator( iterator, targetMeta.getLargest() );

            // 根据时间分块和value分区, 计算得出索引文件的Entry
            NavigableSet<TimePointL> subTimeGroup = timeGroup.calcNewGroup( targetMeta.getSmallest(), targetMeta.getLargest() );
            MinMaxAggrEntryIterator minMax = new MinMaxAggrEntryIterator( interval, subTimeGroup );
            // 将iterator的entry放入数组进行排序
            ArrayList<Triple<Long, TimePointL, Slice>> data = Lists.newArrayList( minMax );
            data.sort( Triple::compareTo );
            // 排序后写入文件
            // 索引文件
            long fileId = sysIndexMeta.nextFileId();
            File indexFile = new File( indexDir, Filename.aggrIndexFileName( fileId ) );
            MinMaxAggrIndexWriter w =
                    new MinMaxAggrIndexWriter( data, indexFile, ValueGroupingMap.getComparator( this.meta.getValueTypes().get( 0 ) ), this.meta.getType() );
            long fileSize = w.write();
            this.newFileMeta = new IndexFileMeta( meta.getId(), fileId, fileSize, targetMeta.getSmallest(), targetMeta.getLargest(), targetMeta.getNumber(), corIsStable,
                                                  subTimeGroup );
        }
    }

    // update index when buffer merged into its corresponding storage file.
    class DurationBufferMergeUpdater extends DurationMergeUpgradeUpdater
    {

        public DurationBufferMergeUpdater( IndexMetaManager meta, File indexDir, IndexMetaData indexMetaData, long deletedFileId, boolean isStable )
        {
            super( meta, indexDir, indexMetaData, Lists.newArrayList( deletedFileId ), isStable );
        }

        @Override
        public void cleanUp() throws IOException
        {
            Long fileId = delFileId.get( 0 );
            IndexFileMeta fMeta = meta.getByCorFileId( fileId, corIsStable );
            File originFile = new File( indexDir, Filename.aggrIndexFileName( fMeta.getFileId() ) );
            Files.delete( originFile.toPath() );
        }
    }

    class MinMaxBufferMergeUpdater extends MinMaxFileUpgradeUpdater
    {

        public MinMaxBufferMergeUpdater( IndexMetaManager meta, File indexDir, IndexMetaData indexMetaData, long deletedFileId, boolean isStable )
        {
            super( meta, indexDir, indexMetaData, Lists.newArrayList( deletedFileId ), isStable );
        }

        @Override
        public void cleanUp() throws IOException
        {
            Long fileId = delFileId.get( 0 );
            IndexFileMeta fMeta = meta.getByCorFileId( fileId, corIsStable );
            File originFile = new File( indexDir, Filename.aggrIndexFileName( fMeta.getFileId() ) );
            Files.delete( originFile.toPath() );
        }
    }
}
