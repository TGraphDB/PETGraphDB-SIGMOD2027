package org.act.temporalProperty.table;

import org.act.temporalProperty.impl.InternalKey;
import org.act.temporalProperty.query.aggr.AggregationIndexKey;
import org.act.temporalProperty.util.Slice;

import com.google.common.primitives.Longs;

import java.util.Comparator;

public class TableComparator implements UserComparator
{

    private Comparator<Slice> userComparator;

    private TableComparator( Comparator<Slice> c )
    {
        this.userComparator = c;
    }
    
    public static TableComparator instance()
    {
        return new TableComparator(Comparator.comparing(InternalKey::decode));
    }

    public static TableComparator forAggrIndex()
    {
        return new TableComparator( AggregationIndexKey.sliceComparator );
    }

    @Override
    public int compare( Slice o1, Slice o2 )
    {
        return userComparator.compare(o1, o2);
    }

    @Override
    public String name()
    {
        return "leveldb.TableComparator";
    }

    @Override
    public Slice findShortestSeparator( Slice start, Slice limit )
    {
     // Find length of common prefix
        //int sharedBytes = BlockBuilder.calculateSharedBytes(start, limit);
        int sharedBytes = 12;

        // Do not shorten if one string is a prefix of the other
        if (sharedBytes < Math.min(start.length(), limit.length())) {
            // if we can add one to the last shared byte without overflow and the two keys differ by more than
            // one increment at this location.
            int lastSharedByte = start.getUnsignedByte(sharedBytes);
            if (lastSharedByte < 0xff && lastSharedByte + 1 < limit.getUnsignedByte(sharedBytes)) {
                Slice result = start.copySlice(0, sharedBytes + 1);
                result.setByte(sharedBytes, lastSharedByte + 1);

                assert (compare(result, limit) < 0) : "start must be less than last limit";
                return result;
            }
        }
        return start;
    }

    @Override
    public Slice findShortSuccessor( Slice key )
    {
        // Find first character that can be incremented
//        for (int i = 0; i < key.length(); i++) {
//            int b = key.getUnsignedByte(i);
//            if (b != 0xff) {
//                Slice result = key.copySlice(0, i + 1);
//                result.setByte(i, b + 1);
//                return result;
//            }
//        }
        // key is a run of 0xffs.  Leave it alone.
        return key;
    }
}
