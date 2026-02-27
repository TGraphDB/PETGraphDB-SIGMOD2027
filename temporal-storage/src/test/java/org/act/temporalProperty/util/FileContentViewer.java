package org.act.temporalProperty.util;

import com.google.common.base.Preconditions;
import org.act.temporalProperty.impl.*;
import org.act.temporalProperty.query.TimePointL;
import org.act.temporalProperty.table.FileChannelTable;
import org.act.temporalProperty.table.Table;
import org.act.temporalProperty.table.TableComparator;
import org.act.temporalProperty.table.TableIterator;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Created by song on 16-9-3.
 */
public class FileContentViewer
{
    private static Logger log = LoggerFactory.getLogger(FileContentViewer.class);

    public static void stableLinearSearch(File file, InternalKey key, Slice value) throws IOException {
        Preconditions.checkArgument(key!=null || value!=null, "at least one condition should not null");
        long[] result = new long[]{Long.MAX_VALUE, 0};
        iterateFile(file, new EntryCallBack() {
            public boolean onEntry(long entryCount, InternalKey key0, Slice value) {
                if(match(key0, value)){
                    if(entryCount<result[0]) result[0] = entryCount;
                    if(entryCount>result[1]) result[1] = entryCount;
                }
                return true;
            }
            private boolean match(InternalKey key0, Slice value0) {
                boolean keyMatched = (key!=null && key.equals(key0)) || (key==null);
                boolean valueMatched = (value!=null && value.equals(value0)) || (value==null);
                return (keyMatched && valueMatched);
            }
        });
        if(result[0]>result[1]){
            log.info("not found");
        }else {
            log.info("matched from {} to {}", result[0], result[1]);
        }
    }


    public static void stableLinearView(File file, int startCount, int endCount) throws IOException {
        Preconditions.checkArgument(startCount<=endCount, "start>end");
        iterateFile(file, new EntryCallBack() {
            public boolean onEntry(long entryCount, InternalKey key0, Slice value) {
                if(startCount<=entryCount && entryCount<=endCount){
                    log.trace("ENTRY({})[{}] eid({}) time({}) value({}) ",
                            entryCount,
                            key0.getValueType().toString().substring(0,6),
                            key0.getEntityId(),
                            key0.getStartTime(),
                            value);
                }
                return true;
            }
        });
    }


    public static void iterateFile(File dataFile, EntryCallBack callBack) throws IOException {
        FileInputStream inputStream = new FileInputStream( dataFile );
        FileChannel channel = inputStream.getChannel();
        Table table = new FileChannelTable( dataFile.getName(), channel, TableComparator.instance(), false );
        TableIterator iterator = table.iterator();
        iterator.seekToFirst();
        if( !iterator.hasNext() ) {
            log.info("Empty data file, no entry found");
            return;
        }else{
            log.info("File size: {}", humanReadableFileSize(channel.size()));
        }
        TimePointL maxTime = TimePointL.Init;
        TimePointL minTime = TimePointL.Now;
        long size = 0;
        long entryCount = 0;
        while( iterator.hasNext() ){
            Entry<Slice,Slice> entry = iterator.next();
            Slice key = entry.getKey();
            Slice value = entry.getValue();
            InternalKey internalKey = InternalKey.decode( key );
            if(!callBack.onEntry(entryCount, internalKey, value)) break;
            TimePointL time = internalKey.getStartTime();
            minTime = TimeIntervalUtil.min(minTime, time);
            maxTime = TimeIntervalUtil.max(maxTime, time);
            size += (key.length() + value.length());
            entryCount++;
        }
        inputStream.close();
        channel.close();
        log.info("Current content size: {} minTime: {} maxTime: {} entry count: {}", humanReadableFileSize(size), minTime, maxTime, entryCount);
    }

    private static String humanReadableFileSize(long size){
        float oneMB = 1024*1024;
        float oneKB = 1024;
        if( size > oneMB ){
            return ( size / oneMB ) + "MB";
        }else if ( size > oneKB ){
            return ( size / oneKB ) + "KB";
        }else{
            return size + "Byte";
        }
    }

    private static abstract class EntryCallBack{
        abstract public boolean onEntry(long entryCount, InternalKey key, Slice value);
    }
}
