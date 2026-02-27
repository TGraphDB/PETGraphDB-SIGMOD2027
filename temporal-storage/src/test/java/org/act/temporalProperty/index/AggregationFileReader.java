package org.act.temporalProperty.index;

import org.act.temporalProperty.impl.Filename;
import org.act.temporalProperty.impl.SeekingIterator;
import org.act.temporalProperty.query.TimePointL;
import org.act.temporalProperty.query.aggr.AggregationIndexKey;
import org.act.temporalProperty.table.MMapTable;
import org.act.temporalProperty.util.Slice;
import org.junit.Test;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Created by song on 2018-05-11.
 */
public class AggregationFileReader
{
    @Test
    public void readFile() throws IOException
    {
        String indexDir = "/tmp/temporal.property.test/index/";
//        for(int i=0; i<=3; i++){
        int i=0;
        AggregationIndexKey search = new AggregationIndexKey( 0 , new TimePointL(0), 0 );
        read(indexDir+ Filename.aggrIndexFileName(i), search);
//            break;
//        }
    }

    private void read( String filePath, AggregationIndexKey searchKey ) throws IOException
    {
        try ( FileChannel channel = new FileInputStream( filePath ).getChannel() )
        {
            SeekingIterator<Slice,Slice> iterator = new MMapTable( filePath, channel, AggregationIndexKey.sliceComparator, false ).iterator();
            iterator.seek( searchKey.encode() );
            int count = 0;
            while ( iterator.hasNext() )
            {
                Entry<Slice,Slice> entry = iterator.next();
                AggregationIndexKey key = new AggregationIndexKey( entry.getKey() );
                System.out.println( key + " [" + entry.getValue().getInt( 0 ) + "]" );
                count++;
                if(count>100) break;
            }
        }
    }
}
