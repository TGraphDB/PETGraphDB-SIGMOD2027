package org.act.temporalProperty.util;

import org.act.temporalProperty.impl.InternalKey;
import org.act.temporalProperty.impl.MemTable;
import org.act.temporalProperty.impl.PackInternalKeyIterator;
import org.act.temporalProperty.impl.ValueType;
import org.act.temporalProperty.query.TimePointL;
import org.act.temporalProperty.table.TableComparator;
import org.act.temporalProperty.vo.EntityPropertyId;
import org.junit.Before;
import org.junit.Test;

public class TableLatestValueIteratorTest
{
    private MemTable table = new MemTable();
    private final int ID_NUM = 20;
    private final int PRO_NUM = 10;
    private final int TIME_NUM = 10;
    
    @Before
    public void setUp()
    {
        for( int t = 0; t<TIME_NUM; t++ )
            for( long i = 0; i<ID_NUM; i++ )
                for( int p = 0; p<PRO_NUM; p++ )
                {
                    InternalKey key = new InternalKey( new EntityPropertyId(i, p), new TimePointL(t), ValueType.VALUE );
                    table.addToNow( key, key.encode() );
                }
    }
    
    @Test
    public void test()
    {
        TableLatestValueIterator iterator = new TableLatestValueIterator( table.iterator() );
        while( iterator.hasNext() )
        {
            InternalKey key = iterator.next().getKey();
            System.out.println( key );
        }
    }
}
