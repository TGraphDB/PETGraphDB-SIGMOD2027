package org.act.temporalProperty.impl;

import com.google.common.collect.PeekingIterator;
import org.act.temporalProperty.TemporalPropertyStore;
import org.act.temporalProperty.exception.TPSNHException;
import org.act.temporalProperty.helper.DebugIterator;
import org.act.temporalProperty.helper.EPEntryIterator;
import org.act.temporalProperty.helper.EPRangeQueryIterator;
import org.act.temporalProperty.index.IndexStore;
import org.act.temporalProperty.index.IndexUpdater;
import org.act.temporalProperty.meta.PropertyMetaData;
import org.act.temporalProperty.query.TimeIntervalKey;
import org.act.temporalProperty.query.TimePointL;
import org.act.temporalProperty.table.TwoLevelMergeIterator;
import org.act.temporalProperty.table.MergeProcess.MergeTask;
import org.act.temporalProperty.table.Table;
import org.act.temporalProperty.table.TableBuilder;
import org.act.temporalProperty.table.TableComparator;
import org.act.temporalProperty.util.FileUtils;
import org.act.temporalProperty.util.Slice;
import org.apache.commons.lang3.tuple.Triple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;

import static org.act.temporalProperty.impl.TemporalPropertyStoreImpl.BULK_MODE;

/**
 * Created by song on 2018-03-14.
 */
public class SinglePropertyStore
{
    private final IndexStore index;
    private PropertyMetaData propertyMeta;
    private File proDir;
    private Logger log = LoggerFactory.getLogger( TemporalPropertyStoreImpl.class );
    private TableCache cache;

    /**
     * 实例化方法
     * @param dbDir 存储动态属性数据的目录地址
     */
    public SinglePropertyStore(PropertyMetaData propertyMeta, File dbDir, TableCache cache, IndexStore indexStore ) throws Exception{
        this.propertyMeta = propertyMeta;
        this.index = indexStore;
        this.proDir = new File(dbDir, propertyMeta.getPropertyId().toString());
        if(!proDir.exists() && !proDir.mkdir()) throw new IOException("create property dir failed: "+proDir.getAbsolutePath());
        this.cache = cache;
        this.loadBuffers();
    }

    private void loadBuffers() throws IOException {
        for(FileBuffer buffer : propertyMeta.getUnstableBuffers().values()){
            File bufferFile = new File(this.proDir, Filename.unbufferFileName(buffer.getNumber(), buffer.getVersion()));
            if (bufferFile.exists()) {
                buffer.init(bufferFile);
            }else{
                throw new IOException("buffer file not found: "+bufferFile.getAbsolutePath());
            }
        }
        for(FileBuffer buffer : propertyMeta.getStableBuffers().values()){
            File bufferFile = new File(this.proDir, Filename.stbufferFileName(buffer.getNumber(), buffer.getVersion()));
            if (bufferFile.exists()) {
                buffer.init(bufferFile);
            }else{
                throw new IOException("buffer file not found: "+bufferFile.getAbsolutePath());
            }
        }
    }

    /**
     * 进行时间点查询，参考{@link TemporalPropertyStore}中的说明
     */
    public Slice getPointValue(InternalKey searchKey)
    {
        TimePointL time = searchKey.getStartTime();
        boolean hasStable = propertyMeta.hasStable();
        if(propertyMeta.hasUnstable() && !(hasStable && time.compareTo(propertyMeta.stMaxTime()) <= 0)){
            Slice result = this.unPointValue( searchKey );
            if( null == result || result.length() == 0 ) {
                return null;
            }else {
                return result;
            }
        }else if(hasStable && time.compareTo(propertyMeta.stMaxTime())<=0){
//            System.out.print("①");
            FileMetaData meta = propertyMeta.getStContainsTime(time);
            return this.stPointValue(meta, searchKey);
        }else if(hasStable && !propertyMeta.hasUnstable()) {
//            System.out.print("②");
            return this.stPointValue(propertyMeta.latestStableMeta(), searchKey);
        }else{
            return null;
        }
    }

//    EPAppendIterator getRangeValueIter(EntityPropertyId id, TimePointL startTime, TimePointL endTime)
//    {
//        List<FileMetaData> stList = propertyMeta.overlappedStable(startTime, endTime);
//        List<FileMetaData> unList = propertyMeta.unFloorTime(endTime);
//        stList.sort(Comparator.comparing(FileMetaData::getSmallest));
//        unList.sort(Comparator.comparing(FileMetaData::getSmallest));
//
//        EPAppendIterator iterator = new EPAppendIterator(id);
//        for(FileMetaData meta : stList){
//            SearchableIterator fileIterator = this.cache.newIterator(Filename.stPath(proDir, meta.getNumber()));
//            FileBuffer buffer = propertyMeta.getStableBuffers( meta.getNumber() );
//            if( null != buffer ){
//                iterator.append(TwoLevelMergeIterator.merge(buffer.iterator(), fileIterator));
//            }else {
//                iterator.append(fileIterator);
//            }
//        }
//        for( FileMetaData meta : unList ){
//            SearchableIterator fileIterator = this.cache.newIterator(Filename.unPath(proDir, meta.getNumber()));
//            FileBuffer buffer = propertyMeta.getUnstableBuffers( meta.getNumber() );
//            if( null != buffer ){
//                iterator.append(TwoLevelMergeIterator.merge(buffer.iterator(), fileIterator));
//            }else {
//                iterator.append(fileIterator);
//            }
//        }
//        return iterator;
//    }

    void getRangeValueIter(EPRangeQueryIterator iterator, TimePointL startTime, TimePointL endTime)
    {
        List<FileMetaData> stList = propertyMeta.overlappedStable(startTime, endTime);
        List<FileMetaData> unList = propertyMeta.unFloorTime(endTime);
        stList.sort(Comparator.comparing(FileMetaData::getSmallest));
        unList.sort(Comparator.comparing(FileMetaData::getSmallest));

        for(FileMetaData meta : stList){
            SearchableIterator fileIterator = this.cache.newIterator(Filename.stPath(proDir, meta.getNumber(), meta.getVersion()));
            FileBuffer buffer = propertyMeta.getStableBuffers( meta.getNumber() );
            if( null != buffer ){
                iterator.appendStables(fileIterator, buffer.iterator(), meta);
            }else {
                iterator.appendStables(fileIterator, meta);
            }
        }
        for( FileMetaData meta : unList ){
            SearchableIterator fileIterator = this.cache.newIterator(Filename.unPath(proDir, meta.getNumber(), meta.getVersion()));
            FileBuffer buffer = propertyMeta.getUnstableBuffers( meta.getNumber() );
            if( null != buffer ){
                iterator.appendUnStables(fileIterator, buffer.iterator(), meta);
            }else {
                iterator.appendUnStables(fileIterator, meta);
            }
        }
    }

    private Slice unPointValue(InternalKey searchKey) {
        List<FileMetaData> checkList = new ArrayList<>(propertyMeta.getUnStableFiles().values());
        checkList.sort(Comparator.comparing(FileMetaData::getNumber));
        for (FileMetaData meta : checkList) {
            SearchableIterator iterator = new EPEntryIterator(searchKey.getId(),
                    this.cache.newIterator(Filename.unPath(proDir, meta.getNumber(), meta.getVersion())));
            FileBuffer buffer = propertyMeta.getUnstableBuffers(meta.getNumber());
            if (null != buffer) {
                iterator = TwoLevelMergeIterator.merge(new EPEntryIterator(searchKey.getId(), buffer.iterator()), iterator);
            }
            if(iterator.seekFloor(searchKey)){
                InternalEntry lastE = null;
                while(iterator.hasNext()) {
                    InternalEntry entry = iterator.next();
                    InternalKey resultKey = entry.getKey();
                    assert resultKey.getId().equals(searchKey.getId());
                    if (resultKey.getStartTime().compareTo(searchKey.getStartTime()) <= 0) {
                        lastE = entry;
                    }else break;
                }
                if(lastE!=null) {
//                    System.out.print("⑤("+meta.getNumber()+")");
                    return lastE.getValue();
                }
            } // else (searchKey smaller than iterator.firstKey) continue
        }
        // search unstable complete but not found. now search latest stable file
        FileMetaData meta = propertyMeta.latestStableMeta();
        if(meta!=null) {
//            System.out.print("③");
            return stPointValue(meta, searchKey);
        }else {
            return null;
        }
    }

    private Slice stPointValue(FileMetaData meta, InternalKey searchKey){
        SearchableIterator iterator = new EPEntryIterator(searchKey.getId(),
                this.cache.newIterator(Filename.stPath(proDir, meta.getNumber(), meta.getVersion())));
        FileBuffer buffer = propertyMeta.getStableBuffers(meta.getNumber());
        if (null != buffer) {
            iterator = TwoLevelMergeIterator.merge(new EPEntryIterator(searchKey.getId(), buffer.iterator()), iterator);
        }
        if(iterator.seekFloor(searchKey)){
            InternalEntry lastE = null;
            while(iterator.hasNext()) {
                InternalEntry entry = iterator.next();
                InternalKey resultKey = entry.getKey();
                assert resultKey.getId().equals(searchKey.getId());
                if (resultKey.getStartTime().compareTo(searchKey.getStartTime()) <= 0) {
                    lastE = entry;
                }else break;
            }
            if(lastE!=null){
//                System.out.print("④("+meta.getNumber()+")");
                return lastE.getValue();
            }
            else{
                return null;
            }
        } else {
            return null;
        }
    }


    // this method runs in the background thread.
    // insert entry to file buffer, and pack remain entries to a MergeTask
    public MergeTask merge(MemTable memTable) throws IOException {
        PeekingIterator<Entry<TimeIntervalKey,Slice>> iterator = memTable.intervalEntryIterator();
        MemTable toMerge = new MemTable();
        boolean stExist = propertyMeta.hasStable();
        boolean unExist = propertyMeta.hasUnstable();
        while( iterator.hasNext() ){
            Entry<TimeIntervalKey,Slice> entry = iterator.next();
            TimeIntervalKey timeInterval = entry.getKey();
            DebugIterator.checkIntervalE(timeInterval, "find in stableMemTable");
            Slice val = entry.getValue();
            if( !unExist && !stExist ){
                toMerge.addInterval(timeInterval, val);
            }else if( unExist && !stExist){
                TimePointL unMaxTime = propertyMeta.unMaxTime();
                if(timeInterval.lessThan( unMaxTime.next() ) ) {
                    insertUnstableBuffer(timeInterval, val);
                }else if(timeInterval.greaterOrEq( unMaxTime.next() )){
                    toMerge.addInterval(timeInterval, val);
                }else{
                    insertUnstableBuffer( timeInterval.changeEnd( unMaxTime ), val );
                    toMerge.addInterval( timeInterval.changeStart( unMaxTime.next() ), val );
                }
            }else if(!unExist && stExist){
                TimePointL stMaxTime = propertyMeta.stMaxTime();
                if( timeInterval.lessThan( stMaxTime.next() )){
                    insertStableBuffer(timeInterval, val);
                }else if(timeInterval.greaterOrEq( stMaxTime.next() )){
                    toMerge.addInterval(timeInterval, val);
                }else{
                    insertStableBuffer( timeInterval.changeEnd( stMaxTime ), val );
                    toMerge.addInterval( timeInterval.changeStart( stMaxTime.next() ), val );
                }
            }else{//unExist && stExist
                TimePointL stMaxTime = propertyMeta.stMaxTime();
                TimePointL unMaxTime = propertyMeta.unMaxTime();
                if( timeInterval.span( stMaxTime, unMaxTime.next() )){ // timeInterval.start < stMaxTime <= unMaxTime+1 <= timeInterval.end
                    insertStableBuffer( timeInterval.changeEnd( stMaxTime ), val );
                    insertUnstableBuffer( timeInterval.changeStart( stMaxTime.next() ).changeEnd( unMaxTime ), val );
                    toMerge.addInterval( timeInterval.changeStart( unMaxTime.next() ), val );
                }else if( timeInterval.lessThan( stMaxTime.next() )){
                    insertStableBuffer(timeInterval, val );
                }else if(timeInterval.greaterOrEq( unMaxTime.next() )){
                    toMerge.addInterval( timeInterval, val );
                }else if(timeInterval.span( stMaxTime.next() )){ // timeInterval.start < stMaxTime+1 <= timeInterval.end
                    insertStableBuffer( timeInterval.changeEnd( stMaxTime ), val );
                    insertUnstableBuffer( timeInterval.changeStart( stMaxTime.next() ), val );
                }else if(timeInterval.span( unMaxTime.next() )){
                    insertUnstableBuffer( timeInterval.changeEnd( unMaxTime ), val );
                    toMerge.addInterval( timeInterval.changeStart( unMaxTime.next() ), val );
                }else if(timeInterval.between( stMaxTime.next(), unMaxTime )){ // stMaxTime+1 <= timeInterval.start <= timeInterval.end <= unMaxTime
                    insertUnstableBuffer( timeInterval, val );
                }else{
                    throw new TPSNHException( "no such scenery!" );
                }
            }
        }
        if(!toMerge.isEmpty()){
            return new MergeTask( proDir, toMerge, propertyMeta, this.cache, index );
        }else{
            return null;
        }
    }


    /**
     * 对某个已存在的UnStableFile的插入，插入到相应的Buffer中。如果不存在则新建一个Buffer
     * @param key
     * @param value
     */
    private void insertUnstableBuffer( TimeIntervalKey key, Slice value ) throws IOException
    {
        assert propertyMeta.hasUnstable();
        for(Entry<TimePointL, FileMetaData> entry : propertyMeta.unFloorTimeMetaList( key.start(), key.end() )){
            FileMetaData meta = entry.getValue();
            FileBuffer buffer = propertyMeta.getUnstableBuffers( meta.getNumber() );
            if( null == buffer ) {
                String fileName = Filename.unbufferFileName(meta.getNumber(), meta.getVersion());
                buffer = new FileBuffer(new File(this.proDir, fileName), meta.getNumber(), meta.getVersion());
                propertyMeta.addUnstableBuffer(meta.getNumber(), buffer);
            }
            TimeIntervalKey validKey = key;
            if(validKey.start().compareTo(meta.getSmallest())<0){
                validKey = validKey.changeStart(meta.getSmallest());
            }
            if(validKey.end().compareTo(meta.getLargest())>=0){
                validKey = validKey.changeEnd(TimePointL.Now);
            }
            DebugIterator.checkIntervalE(key, "insert to un."+meta.getNumber()+".buf");
            buffer.add( validKey, value );
            if(!BULK_MODE && buffer.size()>1024*1024*TemporalPropertyStoreImpl.FBUFFER_SIZE) {
                unBufferToFile( meta, buffer );
            }
        }
    }

    private void insertStableBuffer( TimeIntervalKey key, Slice value ) throws IOException {
        assert propertyMeta.hasStable();
        for(Entry<TimePointL, FileMetaData> entry : propertyMeta.stFloorTimeMetaIterator(key.start(), key.end())){
            FileMetaData meta = entry.getValue();
            FileBuffer buffer = propertyMeta.getStableBuffers( meta.getNumber() );
            if( null == buffer ) {
                String fileName = Filename.stbufferFileName(meta.getNumber(), meta.getVersion());
                buffer = new FileBuffer(new File(this.proDir, fileName), meta.getNumber(), meta.getVersion());
                propertyMeta.addStableBuffer(meta.getNumber(), buffer);
            }
            TimeIntervalKey validKey = key;
            if(validKey.start().compareTo(meta.getSmallest())<0){
                validKey = validKey.changeStart(meta.getSmallest());
            }
            if(validKey.end().compareTo(meta.getLargest())>=0){
                validKey = validKey.changeEnd(TimePointL.Now);
            }
            DebugIterator.checkIntervalE(key, "insert to st."+meta.getNumber()+".buf");
            buffer.add( validKey, value );
            if(buffer.size()>1024*1024*TemporalPropertyStoreImpl.FBUFFER_SIZE) {
                stBufferToFile( meta, buffer );
            }
        }
    }

    public void unBufferToFile(FileMetaData meta, FileBuffer buffer) throws IOException {
        String filePath = Filename.unPath(proDir, meta.getNumber(), meta.getVersion());
        int newVersion = meta.getVersion()+1;
        String targetPath = Filename.unPath(proDir, meta.getNumber(), newVersion);
        long fSize = buffer2file(filePath, targetPath, buffer, index.emptyUpdate()); //unstable file 没有索引文件
        propertyMeta.addUnstable(new FileMetaData(meta.getNumber(), fSize, meta.getSmallest(), meta.getLargest(), newVersion));
        propertyMeta.delUnstableBuffer(meta.getNumber());
        propertyMeta.old2delete.add(filePath);
        propertyMeta.old2delete.add(buffer.getFilePath());
    }

    public void stBufferToFile(FileMetaData meta, FileBuffer buffer) throws IOException {
        String filePath = Filename.stPath(proDir, meta.getNumber(), meta.getVersion());
        int newVersion = meta.getVersion()+1;
        String targetPath = Filename.stPath(proDir, meta.getNumber(), newVersion);
        IndexUpdater indexUpdater = index.onBufferDelUpdate( propertyMeta.getPropertyId(), true, meta, buffer.getMemTable());
        long fSize = buffer2file(filePath, targetPath, buffer, indexUpdater);
        propertyMeta.addStable(new FileMetaData(meta.getNumber(), fSize, meta.getSmallest(), meta.getLargest(), newVersion));
        propertyMeta.delStableBuffer(meta.getNumber());
        propertyMeta.old2delete.add(filePath);
        propertyMeta.old2delete.add(buffer.getFilePath());
        indexUpdater.finish(meta);
        indexUpdater.updateMeta();
        indexUpdater.cleanUp();
    }

    /* 会被stable file和unstable file的合并过程同时调用 */
    private long buffer2file( String sourcePath, String targetPath, FileBuffer buffer, IndexUpdater indexUpdater ) throws IOException {
        File targetFile = new File(targetPath);
        System.out.println("buffer merge: "+sourcePath + " with ~"+ (buffer.getMemTable().approximateMemUsage())+"Bytes");
        Files.deleteIfExists(targetFile.toPath());
        Files.createFile(targetFile.toPath());

        FileOutputStream stream = new FileOutputStream(targetFile);
        FileChannel channel = stream.getChannel();
        TableBuilder builder = new TableBuilder(new Options(), channel, TableComparator.instance());

        SearchableIterator iterator = TwoLevelMergeIterator.merge(buffer.iterator(), this.cache.newIterator(sourcePath));
        long entryCnt = 0; long size = 0;
        while (iterator.hasNext()) {
            InternalEntry entry = iterator.next();
            builder.add(entry.getKey().encode(), entry.getValue());
            entryCnt ++; size += entry.getKey().encode().length() + entry.getValue().length();
            indexUpdater.update( entry );
        }
        long fSize = builder.getFileSize();
        builder.finish();
        channel.close();
        stream.close();
        this.cache.evict(sourcePath);
        buffer.close();
        System.out.println("buffer merge done. target file [" + targetFile.getAbsolutePath() + "](" + entryCnt + " entries) file size=" + fSize);
        return fSize;
    }

    // boolean stand for: isStable.
    // do not build index for unstable files.
    List<Triple<Boolean, FileMetaData, SearchableIterator>> buildIndexIterator(TimePointL startTime, TimePointL endTime ) {
        List<FileMetaData> stList = propertyMeta.overlappedStable(startTime, endTime);
//        List<FileMetaData> unList = propertyMeta.unFloorTime(endTime);
        stList.sort(Comparator.comparing(FileMetaData::getSmallest));
//        unList.sort(Comparator.comparingInt(FileMetaData::getSmallest));

        List<Triple<Boolean, FileMetaData, SearchableIterator>> results = new ArrayList<>();
        for(FileMetaData meta : stList){
            SearchableIterator fileIterator = this.cache.newIterator(Filename.stPath(proDir, meta.getNumber(), meta.getVersion()));
            FileBuffer buffer = propertyMeta.getStableBuffers( meta.getNumber() );
            //todo: maybe should not include buffer data at index create time, but merge buffer data when query.
            if( null != buffer ){
                fileIterator = TwoLevelMergeIterator.merge(buffer.iterator(), fileIterator);
            }
            results.add(Triple.of( true, meta, fileIterator));
        }
//        for( FileMetaData meta : unList ){
//            SearchableIterator fileIterator = this.cache.newIterator(Filename.unPath(proDir, meta.getNumber()));
//            results.add(Triple.of( false, meta, fileIterator));
//        }
        return results;
    }

    public void destroy() throws IOException {
        for(FileMetaData f : propertyMeta.getUnStableFiles().values()) {
            String path = Filename.unPath(proDir, f.getNumber(), f.getVersion());
            cache.evict(path);
        }
        for(FileMetaData f : propertyMeta.getStableFiles().values()) {
            String path = Filename.stPath(proDir, f.getNumber(), f.getVersion());
            cache.evict(path);
        }
        for(FileBuffer buffer : propertyMeta.getUnstableBuffers().values()){
            buffer.close();
        }
        for(FileBuffer buffer : propertyMeta.getStableBuffers().values()){
            buffer.close();
        }
        FileUtils.deleteRecursively(proDir);
    }


//    @Override
//    public boolean delete( Slice id )
//    {
//        // TODO Auto-generated method stub
//        return false;
//    }
//




}