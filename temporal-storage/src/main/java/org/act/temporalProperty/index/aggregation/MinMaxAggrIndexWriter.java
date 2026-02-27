package org.act.temporalProperty.index.aggregation;

import org.act.temporalProperty.impl.Options;
import org.act.temporalProperty.index.IndexType;
import org.act.temporalProperty.query.TimePointL;
import org.act.temporalProperty.query.aggr.AggregationIndexKey;
import org.act.temporalProperty.query.aggr.AggregationQuery;
import org.act.temporalProperty.table.TableBuilder;
import org.act.temporalProperty.util.Slice;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.*;

import static org.act.temporalProperty.index.IndexType.AGGR_MAX;
import static org.act.temporalProperty.index.IndexType.AGGR_MIN;
import static org.act.temporalProperty.index.IndexType.AGGR_MIN_MAX;

/**
 * Created by song on 2018-04-05.
 */
public class MinMaxAggrIndexWriter {

    private final File file;
    private final Comparator<Slice> cp;
    private final Map<Pair<Long, TimePointL>, Slice> min = new TreeMap<Pair<Long, TimePointL>, Slice>();
    private final Map<Pair<Long, TimePointL>, Slice> max = new TreeMap<Pair<Long, TimePointL>, Slice>();
    private final boolean buildMin;
    private final boolean buildMax;

    public MinMaxAggrIndexWriter(List<Triple<Long, TimePointL, Slice>> data, File file, Comparator<Slice> valCp, IndexType type) {
        this.file = file;
        this.cp = valCp;
        buildMin = (type==AGGR_MIN || type==AGGR_MIN_MAX);
        buildMax = (type==AGGR_MAX || type==AGGR_MIN_MAX);
        for(Triple<Long, TimePointL, Slice> entry : data){
            Pair<Long, TimePointL> key = Pair.of(entry.getLeft(), entry.getMiddle());
            if(buildMin) min.merge(key, entry.getRight(), this::min);
            if(buildMax) max.merge(key, entry.getRight(), this::max);
        }
    }

    public long write() throws IOException {
        try(FileOutputStream targetStream = new FileOutputStream(file)) {
            FileChannel targetChannel = targetStream.getChannel();
            TableBuilder builder = new TableBuilder( new Options(), targetChannel, AggregationIndexKey.sliceComparator );
            Set<Pair<Long, TimePointL>> keys = (buildMax ? max.keySet() : min.keySet());
            for(Pair<Long, TimePointL> key : keys){
                if ( buildMin ) builder.add( toSlice( key, true ), min.get( key ) );
                if ( buildMax ) builder.add( toSlice( key, false ), max.get( key ) );
            }
            builder.finish();
            targetChannel.force(true);
            return targetChannel.size();
        }
    }

    private Slice min(Slice a, Slice b){
        if(cp.compare(a, b)<=0){
            return a;
        }else{
            return b;
        }
    }

    private Slice max(Slice a, Slice b){
        if(cp.compare(a, b)>=0){
            return a;
        }else{
            return b;
        }
    }

    private Slice toSlice(Pair<Long, TimePointL> key, boolean isMin )
    {
        long entityId = key.getLeft();
        TimePointL timeGroupId = key.getRight();
        return new AggregationIndexKey( entityId, timeGroupId, isMin ? AggregationQuery.MIN : AggregationQuery.MAX ).encode();
    }
}
