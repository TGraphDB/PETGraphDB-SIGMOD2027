package org.act.temporalProperty.table;

import org.act.temporalProperty.helper.EqualValFilterIterator;
import org.act.temporalProperty.helper.InvalidEntityFilterIterator;
import org.act.temporalProperty.helper.SameLevelMergeIterator;
import org.act.temporalProperty.impl.BackgroundTask;
import org.act.temporalProperty.impl.FileBuffer;
import org.act.temporalProperty.impl.FileMetaData;
import org.act.temporalProperty.impl.Filename;
import org.act.temporalProperty.impl.InternalEntry;
import org.act.temporalProperty.impl.InternalKey;
import org.act.temporalProperty.impl.MemTable;
import org.act.temporalProperty.impl.Options;
import org.act.temporalProperty.impl.SearchableIterator;
import org.act.temporalProperty.impl.TableCache;
import org.act.temporalProperty.impl.UnknownToInvalidIterator;
import org.act.temporalProperty.index.IndexStore;
import org.act.temporalProperty.index.IndexUpdater;
import org.act.temporalProperty.meta.PropertyMetaData;
import org.act.temporalProperty.meta.SystemMeta;
import org.act.temporalProperty.query.TimePointL;
import org.act.temporalProperty.util.Finalizer;
import org.act.temporalProperty.util.TableLatestValueIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;

import static org.act.temporalProperty.impl.TemporalPropertyStoreImpl.BULK_MODE;

/**
 * 文件合并过程
 *
 */
public class MergeProcess extends Thread
{
    private final SystemMeta systemMeta;
    private final String storeDir;
    private volatile MemTable memTable = null;
    private volatile boolean shouldGo = true;
    private volatile boolean hasIndexToCreate = false;
    private static Logger log = LoggerFactory.getLogger( MergeProcess.class );
    private final TableCache cache;
    private final IndexStore index;

    public MergeProcess(String storePath, SystemMeta systemMeta, TableCache cache, IndexStore index) {
        this.storeDir = storePath;
        this.systemMeta = systemMeta;
        this.cache = cache;
        this.index = index;
    }

    public boolean isIdle() throws Exception {
        return this.memTable == null;
    }

    // this is called from a writer thread.
    // the caller should get write lock first.
    public void add(MemTable memTable) throws Exception {
        assert(this.memTable==null);
        this.memTable = memTable;
        if(BULK_MODE) startMergeProcess(memTable);
    }

    private String getMyName(){
        StringBuilder sb = new StringBuilder("TPS");
        if(storeDir.endsWith("temporal.node.properties")){
            sb.append("-Node");
        }else if(storeDir.endsWith("temporal.relationship.properties")){
            sb.append("-Rel");
        }
        String myName = sb.toString();
        Set<Thread> allThreads = Thread.getAllStackTraces().keySet();
        for(Thread t : allThreads){
            if(t.getName().equals(myName)){
                return myName+"("+storeDir+")";
            }
        }
        return myName;
    }

    public void shutdown() throws InterruptedException {
        this.shouldGo = false;
        this.join();
    }

    @Override
    public void run(){
        Thread.currentThread().setName(getMyName());
        try{
            while(!Thread.interrupted()) {
                if(shouldGo) {
                    if ( memTable != null )
                    {
                        startMergeProcess(memTable);
                    } else {
                        if ( hasIndexToCreate )
                        {
                            hasIndexToCreate = false;
                            startMergeProcess( new MemTable() );
                        }
                        else
                        {
                            Thread.sleep( 100 );
                        }
                    }
                }else{
                    if ( memTable != null )
                    {
                        startMergeProcess(memTable);
                        return;
                    } else {
                        return;
                    }
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
//        catch (Throwable e ){
//            e.printStackTrace();
//            log.error( "error happens when dump memtable to disc", e );
//        }
    }

    /**
     * 触发数据写入磁盘，如果需要还需要对文件进行合并
     *
     * @param temp 需要写入磁盘的MemTable
     * @throws IOException
     */
    private static String debugInfo;
    private void startMergeProcess( MemTable temp ) throws IOException
    {
        List<BackgroundTask> taskList = new LinkedList<>();
        if ( !temp.isEmpty() )
        {

            Map<Integer,MemTable> tables = temp.separateByProperty();
            for ( Entry<Integer,MemTable> propEntry : tables.entrySet() )
            {
                MergeTask task = systemMeta.getStore( propEntry.getKey() ).merge( propEntry.getValue() );
                if ( task != null )
                {
                    taskList.add( task );
                }
            }
        }
        else
        {
            taskList.addAll( index.createNewIndexTasks() );
        }

        for ( BackgroundTask task : taskList )
        {
            task.runTask();
        }

        Finalizer<Table> f = null;
        systemMeta.lock.mergeLockExclusive();
        try
        {
            for ( BackgroundTask task : taskList )
            {
                task.updateMeta();
            }
            systemMeta.setStableMemTable(false);
            systemMeta.force( new File( storeDir ) );
            memTable = null;
            f = cache.cleanUp();
            systemMeta.lock.mergeDone();
        }
        finally
        {
            systemMeta.lock.mergeUnlockExclusive();
        }

        if(f!=null) {
            System.out.println("CLOSE FILES (CACHE)");
            f.destroy();
        }
        for ( BackgroundTask task : taskList )
        {
            task.cleanUp();
        }
    }

    public void createNewIndex()
    {
        hasIndexToCreate = true;
    }

    // 将MemTable写入磁盘并与UnStableFile进行合并
    public static class MergeTask implements BackgroundTask
    {
        private final File propStoreDir;
        private final MemTable mem;
        private final TableCache cache;
        private final List<FileMetaData> mergeParticipants;
        private final PropertyMetaData pMeta;

        private final List<Closeable> channel2close = new LinkedList<>();
        private final List<File> files2delete = new LinkedList<>();
        private final List<String> table2evict = new LinkedList<>();
        private final TimePointL mergeParticipantsMinTime;

        private int entryCount;
        private TimePointL minTime;
        private TimePointL maxTime;
        private FileChannel targetChannel;
        private IndexStore index;
        private IndexUpdater indexUpdater;
        private FileMetaData targetMeta;

        /**
         * @param memTable2merge 写入磁盘的MemTable
         * @param proMeta 属性元信息
         * @param cache 用来读取UnStableFile的缓存结构
         * @param index
         */
        public MergeTask( File propStoreDir, MemTable memTable2merge, PropertyMetaData proMeta, TableCache cache, IndexStore index ){
            this.propStoreDir = propStoreDir;
            this.mem = memTable2merge;
            this.pMeta = proMeta;
            this.cache = cache;
            this.index = index;
            this.mergeParticipants = getFile2Merge(proMeta.getUnStableFiles());
            if(!onlyDumpMemTable()) {
                this.mergeParticipantsMinTime = calcMergeMinTime();
            }else{
                this.mergeParticipantsMinTime = TimePointL.Init;
            }
        }

        private TimePointL calcMergeMinTime() {
            FileMetaData last = mergeParticipants.get(mergeParticipants.size() - 1);
            return pMeta.getUnStableFiles().get(last.getNumber()).getSmallest();
        }

        private TableBuilder mergeInit(String targetFileName) throws IOException
        {
            boolean success;
            File targetFile = new File( propStoreDir, targetFileName );
            debugInfo = "Merge "+mergeParticipants+" TO "+targetFile.getAbsolutePath();
//            Files.deleteIfExists(targetFile.toPath());
            if( targetFile.exists() ) {
                success = targetFile.delete();
                if (!success) {
                    throw new IOException("merge init error: fail to delete exist file");
                }
            }
            success = targetFile.createNewFile();
            if (success) {
                FileOutputStream targetStream = new FileOutputStream(targetFile);
                targetChannel = targetStream.getChannel();
                this.channel2close.add( targetStream );
                this.channel2close.add( targetChannel );
                return new TableBuilder( new Options(), targetChannel, TableComparator.instance() );
            }else{
                throw new IOException("merge init error: fail to create file");
            }
        }

        //deleteObsoleteFiles
        @Override
        public void cleanUp() throws IOException
        {
//            close Unused
            for ( Closeable c : channel2close ) {
                c.close();
            }
//            delete unused.
            Iterator<String> it = pMeta.old2delete.iterator();
            while(it.hasNext()){
                File f = new File(it.next());
                if(!f.exists()) {
                    it.remove();
                }else{
                    try{
                        Files.delete(f.toPath());
                        it.remove();
                    }catch (IOException e){
                        System.err.println("SNH(Failed to delete): "+f.getAbsolutePath()+" "+e.getMessage());
                    }
                }
            }
            for( File f : files2delete ) Files.deleteIfExists( f.toPath() );
//            clean up index.
            indexUpdater.cleanUp();
        }

        private List<FileMetaData> getFile2Merge(SortedMap<Long, FileMetaData> files) {
            List<FileMetaData> toMerge = new LinkedList<>();
            for( Long fileNo : new long[]{0,1,2,3,4} ) {
                FileMetaData metaData = files.get( fileNo );
                if( null == metaData ) break;
                else toMerge.add( metaData );
            }
            return toMerge;
        }

        private SearchableIterator getDataIterator(){
            if(onlyDumpMemTable()) {
                return new UnknownToInvalidIterator( this.mem.iterator() );
            }else{
                SameLevelMergeIterator unstableIter = new SameLevelMergeIterator();
                for (FileMetaData fMeta : mergeParticipants) {
                    long fileNumber = fMeta.getNumber();
                    int fVersion = fMeta.getVersion();
//                    log.debug("merge {}", fileNumber);
                    String mergeSourcePath = Filename.unPath(propStoreDir, fileNumber, fVersion);
                    SearchableIterator mergeIterator;
                    FileBuffer filebuffer = pMeta.getUnstableBuffers(fileNumber);
                    if (null != filebuffer) {
                        mergeIterator = TwoLevelMergeIterator.merge(filebuffer.iterator(), cache.newIterator(mergeSourcePath));
                        channel2close.add(filebuffer);
                        files2delete.add(new File(propStoreDir, Filename.unbufferFileName(fileNumber, fVersion)));
                    } else {
                        mergeIterator = cache.newIterator(mergeSourcePath);
                    }
                    unstableIter.add(mergeIterator);

                    table2evict.add(mergeSourcePath);
                    files2delete.add(new File(mergeSourcePath));
                }
                SearchableIterator diskDataIter;
                if (createStableFile() && pMeta.hasStable()) {
                    diskDataIter = TwoLevelMergeIterator.merge( unstableIter, stableLatestValIter(mergeParticipantsMinTime));
                } else {
                    diskDataIter = unstableIter;
                }
                return new UnknownToInvalidIterator(
                        new InvalidEntityFilterIterator(
                                new EqualValFilterIterator(
                                        TwoLevelMergeIterator.merge(this.mem.iterator(), diskDataIter) )));
            }
        }

        public boolean createStableFile(){
            return mergeParticipants.size()>=5;
        }

        public boolean onlyDumpMemTable(){
            return mergeParticipants.isEmpty();
        }

        // build new File
        @Override
        public void runTask() throws IOException
        {
            maxTime = TimePointL.Init;
            minTime = TimePointL.Now;
            entryCount = 0;

            String targetFileName;

            if(createStableFile()) {
                long fileId = pMeta.nextStableId();
                targetFileName = Filename.stableFileName( fileId, 0 );
                indexUpdater = index.onMergeUpdate( pMeta.getPropertyId(), mergedMemTableAndBuffer(), Collections.emptyList() );
            }else if(!onlyDumpMemTable()){
                long fileId = mergeParticipants.size();
                targetFileName = Filename.unStableFileName( fileId, 0 );
                indexUpdater = index.emptyUpdate();
            }else{
                long fileId = mergeParticipants.size();
                targetFileName = Filename.unStableFileName( fileId, 0 );
                indexUpdater = index.emptyUpdate();
            }

            TableBuilder builder = this.mergeInit(targetFileName);
            System.out.println("----"+debugInfo);
            SearchableIterator buildIterator = getDataIterator();
            while( buildIterator.hasNext() ){
                InternalEntry entry = buildIterator.next();
                InternalKey key = entry.getKey();
//                DebugIterator.checkE(key, "merge to disk file");
                if( key.getStartTime().compareTo(minTime) < 0 ) minTime = key.getStartTime();
                if( key.getStartTime().compareTo(maxTime) > 0 ) maxTime = key.getStartTime();
                try {
                    builder.add(entry.getKey().encode(), entry.getValue());
                }catch(AssertionError e){
                    System.err.println(buildIterator);
                    throw e;
                }
                indexUpdater.update( entry );
                entryCount++;
            }
            builder.finish();
            System.out.println("---------------Merge done. write "+entryCount+" entries.");
            this.targetMeta = generateNewFileMeta();
            indexUpdater.finish( targetMeta );
        }

        private MemTable mergedMemTableAndBuffer()
        {
//            MemTable result = new MemTable();
//            for ( Long fileNumber : mergeParticipants )
//            {
//                FileBuffer filebuffer = pMeta.getUnstableBuffers( fileNumber );
//                if ( null != filebuffer )
//                {
//                    result.merge( filebuffer.getMemTable() );
//                }
//            }
            return mem;
        }

        private FileMetaData generateNewFileMeta() throws IOException
        {
            // build new meta
            FileMetaData targetMeta;

            if(onlyDumpMemTable()){
                TimePointL startTime;
                if(pMeta.hasDiskFile()) {
                    startTime=pMeta.diskFileMaxTime().next();
                }else{
                    startTime=TimePointL.Init;
                }
                targetMeta = new FileMetaData( 0, targetChannel.size(), startTime, maxTime );
            }else{
                long fileNumber;
                if(createStableFile()){
                    fileNumber = pMeta.nextStableId();
                }else {
                    fileNumber = mergeParticipants.size();
                }
                assert mergeParticipantsMinTime.compareTo(minTime)<=0:"start time should <= minTime! ("+mergeParticipantsMinTime+", min:"+minTime+")";
                targetMeta = new FileMetaData( fileNumber, targetChannel.size(), mergeParticipantsMinTime, maxTime );
            }
            return targetMeta;
        }

        @Override
        public void updateMeta()
        {
            // remove old meta
            for(FileMetaData fMeta  : mergeParticipants ){
                long fileNum = fMeta.getNumber();
                pMeta.delUnstable( fileNum );
                pMeta.delUnstableBuffer( fileNum );
            }

            if(createStableFile()){
                pMeta.addStable( targetMeta );
            }else{
                pMeta.addUnstable( targetMeta );
            }

            indexUpdater.updateMeta();

            // evictUnused(cache);
            for ( String filePath : table2evict ) {
                cache.evict( filePath );
            }
        }

        // this should only be called when pMeta.hasStable() is true.
        private SearchableIterator stableLatestValIter(TimePointL mergeResultStartTime) {
            FileMetaData meta = pMeta.latestStableMeta();
            String filePath = Filename.stPath(propStoreDir, meta.getNumber(), meta.getVersion());
            SearchableIterator fileIterator = cache.newIterator(filePath);
            FileBuffer buffer = pMeta.getStableBuffers( meta.getNumber() );
            if( null != buffer ){
                fileIterator = TwoLevelMergeIterator.merge(buffer.iterator(), fileIterator);
            }
            return TableLatestValueIterator.setNewStart(fileIterator, mergeResultStartTime);
        }
    }

}
