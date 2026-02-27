
package org.act.temporalProperty.impl;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.Weigher;
import org.act.temporalProperty.table.FileChannelTable;
import org.act.temporalProperty.table.MMapTable;
import org.act.temporalProperty.table.Table;
import org.act.temporalProperty.table.UserComparator;
import org.act.temporalProperty.util.Finalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

/**
 * 对所有存储文件的缓存机制，使用FIFO的方式进行，如果需要对某个文件进行访问，可以直接从TableCache这里对文件的Iterator进行查询
 *
 */
public class TableCache
{
    private static Logger log = LoggerFactory.getLogger(TableCache.class);
    private final LoadingCache<String, TableAndFile> cache;
    private Finalizer<Table> finalizer = new Finalizer<>();
    private final Map<String, Long> loadFreq = new ConcurrentHashMap<>();

    public TableCache(int tableCacheSize, final UserComparator userComparator, final boolean verifyChecksums)
    {
        cache = CacheBuilder.newBuilder()
                .maximumWeight(tableCacheSize) // in MB
                .weigher(((Weigher<String, TableAndFile>) (key, value) -> {
                    long fileSizeMB = value.fileSize / 1024 / 1024;
                    if( 0 <= fileSizeMB && fileSizeMB <= Integer.MAX_VALUE ) {
                        return Math.toIntExact(fileSizeMB);
                    }else {
                        log.warn("ERROR fileSizeMB of {} out of range [0~Int.Max], got {}MB({})", key, fileSizeMB, value.fileSize);
                        return 1;
                    }
                }))
                .recordStats()
                .removalListener((RemovalListener<String, TableAndFile>) notification -> {
                    Table table = notification.getValue().getTable();
//                    try {
//                        table.close();
//                        System.out.println("CLOSE "+notification.getKey());
//                    } catch (IOException e) {
//                        throw new IllegalStateException(e);
//                    }
                    log.trace("RM "+notification.getKey());
                    finalizer.addCleanup(table, table.closer());
                })
                .build(new CacheLoader<String, TableAndFile>(){
                    @Override
                    public TableAndFile load(String filePath) throws IOException{
                        log.trace("LOAD "+filePath);
                        loadFreq.compute(filePath, (s, aLong) -> aLong==null ? 1 : aLong+1);
                        return new TableAndFile(filePath, userComparator, verifyChecksums);
                    }
                });
    }

    /**
     * 通过文件的编号，得到相应文件的Iterator
     * @param filePath 文件编号
     * @return 相应文件的Iterator
     */
    public SearchableIterator newIterator(String filePath)
    {
        return new PackInternalKeyIterator(getTable(filePath).iterator(), filePath);
    }

    public Table getTable(String filePath)
    {
        Table table;
        try {
            table = cache.get(filePath).getTable();
            log.trace("GOT "+filePath);
        }
        catch (ExecutionException e) {
            Throwable cause = e;
            if (e.getCause() != null) {
                cause = e.getCause();
            }
            throw new RuntimeException("Could not open table " + filePath, cause);
        }
        return table;
    }

    public Finalizer<Table> cleanUp(){
        Finalizer<Table> f = this.finalizer;
        this.finalizer = new Finalizer<>();
        return f;
    }

    /**
     * 关闭缓存，将缓存在内存中的文件channel关闭
     */
    public void close()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("TableCache.close: ");
        if(!loadFreq.isEmpty()){
            String path = new LinkedList<>(loadFreq.keySet()).getFirst();
            sb.append(new File(path).getParentFile().getAbsolutePath()).append('/');
        }
        loadFreq.forEach((fPath, freq)-> sb.append(new File(fPath).getName()).append("=").append(freq).append(','));
        System.out.println(sb);
        System.out.println(cache.stats());
        cache.invalidateAll();
        finalizer.destroy();
    }

    /**
     * 将某个文件从缓存中排除
     * @param filePath 文件绝对路径
     */
    public void evict(String filePath)
    {
        cache.invalidate(filePath);
    }

    public long currentCacheSize() {
        long totalSizeInMB = 0;
        for(TableAndFile value : cache.asMap().values()){
            totalSizeInMB += value.fileSize / 1024 / 1024;
        }
        return totalSizeInMB;
    }

    private static final class TableAndFile
    {
        private final Table table;
        private final FileChannel fileChannel;
        final long fileSize;

        private TableAndFile(String filePath, UserComparator userComparator, boolean verifyChecksums) throws IOException{
//            fileChannel = new RandomAccessFile(filePath,"rw").getChannel();
            fileChannel = new FileInputStream(filePath).getChannel();
            fileSize = fileChannel.size();
//            try {
//                FIXME
//                if ( true ) {
                    table = new MMapTable(filePath, fileChannel, userComparator, verifyChecksums);
//                }else{
//                    table = new FileChannelTable(filePath, fileChannel, userComparator, verifyChecksums);
//                }
//            } catch (IOException e) {
//                Closeables.closeQuietly(fileChannel);
//                throw e;
//            }
        }

        public Table getTable()
        {
            return table;
        }
    }
}
