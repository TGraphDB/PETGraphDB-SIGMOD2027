//package org.act.temporalProperty.impl;
//
//import java.io.IOException;
//
//import org.act.temporalProperty.meta.PropertyMetaData;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
///**
// * StableLevel中存储着所有UnStableFile的相关元信息，并且是对其进行查询和写入的入口
// *
// */
//public class StableLevel
//{
//    private PropertyMetaData propertyMeta;
//    private String proDir;
//    private TableCache cache;
//    private static Logger log = LoggerFactory.getLogger( StableLevel.class );
//    private final int bufferMergeboundary = 2;
//
//    private RangeQueryIndex rangeQueryIndex;
//
//    StableLevel(String dbDir, PropertyMetaData propertyMeta)
//    {
//        this.proDir = dbDir;
//        this.rangeQueryIndex = new RangeQueryIndex(dbDir);
//        this.propertyMeta = propertyMeta;
//    }
//    /**
//     * 进行时间段查询
//     */
////    public void getRangeValue( Slice idSlice, int startTime, int endTime, RangeQueryCallBack callback )
////    {
////        for( FileMetaData metaData : this.files.values() )
////        {
////            if( null == metaData )
////                continue;
////            if( metaData.getSmallest() > endTime )
////                break;
////            if( TimeIntervalUtil.overlap( startTime, endTime, metaData.getSmallest(), metaData.getLargest() ) )
////            {
////                int start = Math.max( startTime, metaData.getSmallest() );
////                int end = Math.min( endTime, metaData.getLargest() );
////                if( start == metaData.getSmallest() && end == metaData.getLargest() && callback.getType() != RangeQueryCallBack.CallBackType.USER ){
////
////                	FileBuffer buffer = this.fileBuffers.get( metaData.getNumber() );
////                	InternalKey searchKey = new InternalKey(idSlice, start, 0, ValueType.VALUE);
////                	boolean hasUpdate = false;
////                    if( null != buffer )
////                    {
////                        MemTableIterator bufferiterator = buffer.iterator();
////                        bufferiterator.seek( searchKey.encode() );
////                        while( bufferiterator.hasNext() )
////                        {
////                            Entry<Slice,Slice> entry = bufferiterator.next();
////                            InternalKey key = new InternalKey( entry.getKey() );
////                            if( key.getId().equals( idSlice ) )
////                            {
////                                hasUpdate = true;
////                            }
////                            else
////                                break;
////                        }
////                    }
////                	if( hasUpdate ){//hasUpdate
////
////                		SeekingIterator<Slice, Slice> iterator = new BufferFileAndTableIterator(buffer.iterator(), this.cache.newIterator(metaData), TableComparator.instance() );
////                		iterator.seek( searchKey.encode() );
////                		int count = 0;
////                        Slice max = null;
////                        Slice min = null;
////                        Slice sum = null;
////                		while( iterator.hasNext() ){
////                			Entry<Slice,Slice> entry = iterator.next();
////                			InternalKey key = new InternalKey(entry.getKey());
////                			if(!key.getId().equals(searchKey.getId()))
////                				break;
////                			else{
////                				callback.onCall(key.getStartTime(), entry.getValue());
////                				count++;
////                                max = RangeQueryUtil.max(max,entry.getValue());
////                                min = RangeQueryUtil.min(min,entry.getValue());
////                                sum = RangeQueryUtil.sum(max,entry.getValue());
////                			}
////                		}
////                		try {
////                            File indexFile = new File(this.proDir + "/index" + metaData.getNumber() );
////                            FileOutputStream stream = new FileOutputStream(indexFile);
////                            FileChannel channel = stream.getChannel();
////							Table indexTable = new FileChannelTable(this.proDir + "/index" + metaData.getNumber(), channel, TableComparator.instance(), false);
////							TableUpdater updater = new TableUpdater(indexTable);
////							Slice countSlice = new Slice(4);
////							countSlice.setInt(0, count);
////							updater.update(idSlice, 0, 4, ValueType.VALUE, countSlice);
////							updater.update(idSlice, 1, 4, ValueType.VALUE, max);
////							updater.update(idSlice, 2, 4, ValueType.VALUE, min);
////							updater.update(idSlice, 3, 4, ValueType.VALUE, sum);
////						} catch (IOException e) {
////							e.printStackTrace();
////						}
////
////                	}
////                	else{
////	                	Slice value = this.rangeQueryIndex.get(metaData.getNumber(), callback.getType(), idSlice);
////	                	callback.onCallBatch(value);
////                	}
////                	continue;
////                }
////                InternalKey searchKey = new InternalKey( idSlice, start, 0, ValueType.VALUE );
////                SeekingIterator<Slice,Slice> iterator = this.cache.newIterator( metaData.getNumber() );
////                iterator.seek( searchKey.encode() );
////                while( iterator.hasNext() )
////                {
////                    Entry<Slice,Slice> entry = iterator.next();
////                    InternalKey key = new InternalKey( entry.getKey() );
////                    if( key.getId().equals( idSlice ) && key.getStartTime() <= end && key.getValueType().getPersistentId() != ValueType.DELETION.getPersistentId() )
////                    {
////                        callback.onCall( key.getStartTime(), entry.getValue() );
////                    }
////                    else
////                        break;
////                }
////                FileBuffer buffer = this.fileBuffers.get( metaData.getNumber() );
////                if( null != buffer )
////                {
////                    MemTableIterator bufferiterator = buffer.iterator();
////                    bufferiterator.seek( searchKey.encode() );
////                    while( bufferiterator.hasNext() )
////                    {
////                        Entry<Slice,Slice> entry = bufferiterator.next();
////                        InternalKey key = new InternalKey( entry.getKey() );
////                        if( key.getId().equals( idSlice ) && key.getStartTime() <= end
////                                && key.getValueType().getPersistentId() != ValueType.INVALID.getPersistentId()
////                                && key.getValueType().getPersistentId() != ValueType.DELETION.getPersistentId())
////                        {
////                            callback.onCall( key.getStartTime(), entry.getValue() );
////                        }
////                        else
////                            break;
////                    }
////                }
////            }
////        }
////    }
//
////    public EPAppendIterator getRangeValueIter(Slice idSlice, int startTime, int endTime)
////    {
////        EPAppendIterator appendIterator = new EPAppendIterator(idSlice);
////        for( FileMetaData metaData : propertyMeta.getStableFiles().values() ){
////            if( null == metaData ) throw new RuntimeException("SNH: null value in collections");
////            if( metaData.getSmallest() > endTime ) break;
////            if( TimeIntervalUtil.overlap( startTime, endTime, metaData.getSmallest(), metaData.getLargest() ) ){
////                SeekingIterator<Slice,Slice> mergedIterator = this.cache.newIterator(
////                        new File(proDir, String.valueOf(metaData.getNumber())).getAbsolutePath());
////                FileBuffer buffer = propertyMeta.getStableBuffers( metaData.getNumber() );
////                if( null != buffer ) {
////                    mergedIterator = new EPMergeIterator(idSlice, mergedIterator, buffer.iterator());
////                }
////                appendIterator.append(mergedIterator);
////            }
////        }
////        return appendIterator;
////    }
////    /**
////     * 进行写入
////     */
////    public boolean set( InternalKey key, Slice value )
////    {
////        try
////        {
////            insert2Bufferfile( key, value );
////        }
////        catch( Throwable t )
////        {
////            return false;
////        }
////        return true;
////    }
//
////    /**
////     * 针对某个Buffer的插入操作，
////     */
////    private void insert2Bufferfile( InternalKey key, Slice value ) throws Exception
////    {
////        int insertTime = key.getStartTime();
////        this.fileMetaLock.writeLock().lock();
////        for( long fileNumber : this.files.keySet() )
////        {
////            FileMetaData metaData = this.files.get( fileNumber );
////            if( null == metaData )
////                continue;
////            if( insertTime >= metaData.getSmallest() && insertTime <= metaData.getLargest() )
////            {
////                FileBuffer buffer = this.fileBuffers.get( fileNumber );
////                if( null == buffer )
////                {
////                    buffer = new FileBuffer( Filename.stbufferFileName( fileNumber ), this.proDir + "/" + Filename.stbufferFileName( fileNumber ) );
////                    this.fileBuffers.put( fileNumber, buffer );
////                }
////                buffer.add( key.encode(), value );
////                if(buffer.size()>1024*1024*10) {
////                    mergeBufferToFile( fileNumber );
////                }
////                break;
////            }
////        }
////        this.fileMetaLock.writeLock().unlock();
////    }
//
//    /**
//     * this method do three things:
//     * 1. merge (but not upgrade levels) buffer file to its responsible stable file.
//     * 2. update index
//     * 3. if no buffer then nothing happened.
//     * this method is ONLY used for stable files.
//     * note: meta data is not update, therefore the size of file is not update.
//     * @throws IOException
//     */
////    private void mergeBufferToFile(long number) throws IOException {
////        FileBuffer buffer = this.fileBuffers.get( number );
////        if( null != buffer )
////        {
////            Table table = this.cache.newTable( number );
////            String tempfilename = Filename.tempFileName( 7 );
////            File tempFile = new File(this.proDir + "/" + tempfilename );
////            if( !tempFile.exists() )
////                tempFile.createNewFile();
////            File indexFile = new File(this.proDir + "/index" + number);
////            boolean hasIndexFile = indexFile.exists();
////            if( hasIndexFile )
////            	indexFile = new File(this.proDir + "/index" + number + "temp");
////            FileOutputStream indexStream = new FileOutputStream( indexFile );
////            FileOutputStream stream = new FileOutputStream( tempFile );
////            FileChannel indexChannel = indexStream.getChannel();
////            FileChannel channel = stream.getChannel();
////            TableBuilder builder = new TableBuilder( new Options(), channel, TableComparator.instance() );
////            TableBuilder indexBuilder = new TableBuilder(new Options(), indexChannel, TableComparator.instance() );
////            List<SeekingIterator<Slice,Slice>> iterators = new ArrayList<SeekingIterator<Slice,Slice>>(2);
////            SeekingIterator<Slice,Slice> iterator = new BufferFileAndTableIterator( buffer.iterator(), table.iterator(), TableComparator.instance() );
////            iterators.add( iterator );
////            MergingIterator mergeIterator = new MergingIterator( iterators, TableComparator.instance() );
////            InternalKey lastKey = null;
////            int count = 0;
////            Slice max = null;
////            Slice min = null;
////            Slice sum = null;
////            while( mergeIterator.hasNext() )
////            {
////                Entry<Slice,Slice> entry = mergeIterator.next();
////                builder.add( entry.getKey(), entry.getValue() );
////                InternalKey currentKey = new InternalKey(entry.getKey());
////                if(lastKey == null || lastKey.getId().equals(currentKey.getId()) ){
////                    if( lastKey == null ){
////                        lastKey = currentKey;
////                        max = entry.getKey();
////                        min = entry.getKey();
////                        sum = entry.getKey();
////                    }
////                    count++;
////                    max = RangeQueryUtil.max(max,entry.getValue());
////                    min = RangeQueryUtil.min(min,entry.getValue());
////                    sum = RangeQueryUtil.sum(max,entry.getValue());
////                    continue;
////                }
////                else{
////                    InternalKey countKey = new InternalKey(lastKey.getId(), 0, 4, ValueType.VALUE);
////                    Slice countSlice = new Slice(4);
////                    countSlice.setInt(0, count);
////                    InternalKey maxKey = new InternalKey(lastKey.getId(), 1, max.length(), ValueType.VALUE);
////                    InternalKey minKey = new InternalKey(lastKey.getId(), 2, min.length(), ValueType.VALUE);
////                    InternalKey sumKey = new InternalKey(lastKey.getId(), 3, sum.length(), ValueType.VALUE);
////                    indexBuilder.add(countKey.encode(), countSlice);
////                    indexBuilder.add(maxKey.encode(), max);
////                    indexBuilder.add(minKey.encode(), min);
////                    indexBuilder.add(sumKey.encode(), sum);
////                    count = 1;
////                    max = entry.getKey();
////                    min = entry.getKey();
////                    sum = entry.getKey();
////                    lastKey = currentKey;
////                }
////            }
////            indexBuilder.finish();
////            builder.finish();
////            channel.close();
////            indexChannel.close();
////            stream.close();
////            indexStream.close();
////            table.close();
////            this.cache.evict( number );
////            File originFile = new File( this.proDir + "/" + Filename.stableFileName(number));
////            Files.delete( originFile.toPath() );
////            buffer.close();
////            Files.delete(new File(this.proDir + "/" + Filename.stbufferFileName( number ) ).toPath());
////            this.fileBuffers.put( number, null);
////            tempFile.renameTo(new File(this.proDir + "/" + Filename.stableFileName( number ) ) );
////            if( hasIndexFile ){
////            	Files.delete(indexFile.toPath());
////            	new File(this.proDir + "/index" + number + "temp").renameTo(new File(this.proDir + "/index" + number));
////            }
////        }
////    }
//}
