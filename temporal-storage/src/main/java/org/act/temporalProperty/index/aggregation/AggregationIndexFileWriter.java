package org.act.temporalProperty.index.aggregation;

import org.act.temporalProperty.impl.*;
import org.act.temporalProperty.query.aggr.AggregationIndexKey;
import org.act.temporalProperty.table.TableBuilder;
import org.act.temporalProperty.table.TableComparator;
import org.act.temporalProperty.util.Slice;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.*;

/**
 * Created by song on 2018-04-05.
 */
public class AggregationIndexFileWriter {

    private final Iterator<AggregationIndexEntry> data;
    private final File file;

    public AggregationIndexFileWriter(List<AggregationIndexEntry> data, File file) {
        this.data = data.iterator();
        this.file = file;
    }

    public long write() throws IOException {
        try(FileOutputStream targetStream = new FileOutputStream(file)) {
            FileChannel targetChannel = targetStream.getChannel();
            TableBuilder builder = new TableBuilder( new Options(), targetChannel, AggregationIndexKey.sliceComparator);
            // merge same AggregationIndexKey, sum up their duration.
            AggregationIndexEntry lastEntry = null;
            long duration = 0;
            while (data.hasNext()) {
                AggregationIndexEntry entry = data.next();
                if(lastEntry==null){
                    duration = entry.getDuration();
                }else if(entry.getKey().equals(lastEntry.getKey())) {
                    duration += entry.getDuration();
                } else{ // key not equal, so we add lastKey.
                    Slice dur = new Slice(8);
                    dur.setLong(0, duration);
                    builder.add(lastEntry.getKey().encode(), dur);
                    duration = entry.getDuration();
                }
                lastEntry = entry;
            }
            if(lastEntry!=null) {
                Slice dur = new Slice(8);
                dur.setLong(0, duration);
                builder.add(lastEntry.getKey().encode(), dur);
            }
            builder.finish();
            long fileSize = targetChannel.size();
            targetChannel.close();
            return fileSize;
        }
    }
}
