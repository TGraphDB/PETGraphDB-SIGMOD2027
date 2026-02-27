package org.act.temporalProperty.impl;

import com.google.common.collect.PeekingIterator;
import org.act.temporalProperty.exception.ValueUnknownException;
import org.act.temporalProperty.query.TimeIntervalKey;
import org.act.temporalProperty.query.TimePointL;
import org.act.temporalProperty.util.Slice;
import org.act.temporalProperty.util.Slices;
import org.act.temporalProperty.vo.EntityPropertyId;
import org.junit.Test;

import java.util.Map;

public class MemTableTest
{ 
    private static MemTable table;
    private final static int TIME_NUMS = 100;
    private final static int PRO_NUMS = 50;
    private final static int ID_NUMS = 100;

//    @BeforeClass
//    public static void setUp()
//    {
//        table = new MemTable( TableComparator.instance() );
//        for( long i = 0; i<ID_NUMS; i++ )
//        {
//            for( int p = 0; p<PRO_NUMS; p++ )
//            {
//                for( int t = 0; t<TIME_NUMS; t++ )
//                {
//                    Slice idSlice = new Slice( 12 );
//                    idSlice.setLong( 0, i );
//                    idSlice.setInt( 8, p );
//                    Slice value = new Slice( 20 );
//                    value.setLong( 0, i );
//                    value.setInt( 8, p );
//                    long sequence = SequenceNumber.packSequenceAndValueType( t, ValueType.VALUE, ValueStatus.NEW );
//                    value.setLong( 12, sequence );
//                    InternalKey key = new InternalKey( idSlice, t, 20, ValueType.VALUE );
//                    table.addToNow( key.encode(), value );
//                }
//            }
//        }
//    }
//
    @Test
    public void intervalIterVsPointIter() {
        MemTable table = new MemTable();
        for(int t=10; t<100; t+=5){
            for(long entityId=0; entityId<10; entityId++) {
                if(entityId==0) {
                    set(table, entityId, 2, 1, 2, 3);
                    set(table, entityId, 2, 3, 3, 4);
                    set(table, entityId, 2, 4, 5, 5);
//                    set(store, entityId, 2, 6, 7);
                    set(table, entityId, 2, 8, 9, 6);
                }
                set(table, entityId, 2, t, t+4, t);
            }
        }

        for (SearchableIterator it = table.iterator(); it.hasNext();) {
            InternalEntry entry = it.next();
            System.out.println(entry.getKey()+" "+entry.getValue());
            try {
                table.get(entry.getKey());
            } catch (ValueUnknownException e) {
                e.printStackTrace();
            }
        }
        for (PeekingIterator<Map.Entry<TimeIntervalKey, Slice>> it = table.intervalEntryIterator(); it.hasNext();) {
            Map.Entry<TimeIntervalKey, Slice> entry = it.next();
            System.out.println(entry.getKey()+" "+entry.getValue());
        }

    }

    private void set(MemTable table, long entityId, int propId, int timeStart, int timeEnd, int value) {
        Slice valSlice = Slices.allocate(8);
        valSlice.output().writeInt(value);
        table.addInterval(new TimeIntervalKey(new EntityPropertyId(entityId, propId), new TimePointL(timeStart), new TimePointL(timeEnd), ValueType.INT), valSlice);
    }


//    //TODO update test to adopt the change: remove DELETION mark.
//    @Test
//    public void testChangeKey()
//    {
//        for( long i = 0; i<ID_NUMS; i++ )
//        {
//            for( int p = 0; p<PRO_NUMS; p++ )
//            {
//                for( int t = 0; t<TIME_NUMS; t++ )
//                {
//                    Slice idSlice = new Slice(12);
//                    idSlice.setLong( 0, i );
//                    idSlice.setInt( 8, p );
//                    Slice key = new InternalKey( idSlice, t, 20, ValueType.DELETION ).encode();
//                    Slice newvalue = new Slice( 20 );
//                    newvalue.setLong( 0, i );
//                    newvalue.setInt( 8, p );
//                    long sequnence = SequenceNumber.packSequenceAndValueType( t, 20, ValueType.VALUE );
//                    newvalue.setLong( 12, sequnence );
//                    table.add( key, newvalue );
//                }
//            }
//        }
//        MemTableIterator iterator = table.iterator();
//        for( long i = 0; i<ID_NUMS; i++ )
//        {
//            for( int p = 0; p<PRO_NUMS; p++ )
//            {
//                for( int t = 0; t<TIME_NUMS; t++ )
//                {
//                    Entry<Slice,Slice> entry = iterator.next();
//                    InternalKey valuekey = new InternalKey( entry.getValue() );
//                    InternalKey key = new InternalKey( entry.getKey() );
//                    Slice idSlice = new Slice( 12 );
//                    idSlice.setLong( 0, i );
//                    idSlice.setInt( 8, p );
//                    Assert.assertEquals( idSlice, valuekey.getId() );
//                    Assert.assertEquals( ValueType.VALUE.getPersistentId(), valuekey.getValueType().getPersistentId() );
//                    Assert.assertEquals( ValueType.DELETION.getPersistentId(), key.getValueType().getPersistentId() );
//                }
//            }
//        }
//    }
//
//    @Test
//    public void testChangeValue()
//    {
//        for( long i = 0; i<ID_NUMS; i++ )
//        {
//            for( int p = 0; p<PRO_NUMS; p++ )
//            {
//                for( int t = 0; t<TIME_NUMS; t++ )
//                {
//                    Slice idSlice = new Slice(12);
//                    idSlice.setLong( 0, i );
//                    idSlice.setInt( 8, p );
//                    Slice key = new InternalKey( idSlice, t, 20, ValueType.VALUE ).encode();
//                    Slice newvalue = new Slice( 20 );
//                    newvalue.setLong( 0, i+1 );
//                    newvalue.setInt( 8, p+1 );
//                    long sequnence = SequenceNumber.packSequenceAndValueType( t, 20, ValueType.DELETION );
//                    newvalue.setLong( 12, sequnence );
//                    table.add( key, newvalue );
//                }
//            }
//        }
//        MemTableIterator iterator = table.iterator();
//        for( long i = 0; i<ID_NUMS; i++ )
//        {
//            for( int p = 0; p<PRO_NUMS; p++ )
//            {
//                for( int t = 0; t<TIME_NUMS; t++ )
//                {
//                    Entry<Slice,Slice> entry = iterator.next();
//                    InternalKey valuekey = new InternalKey( entry.getValue() );
//                    InternalKey key = new InternalKey( entry.getKey() );
//                    Slice idSlice = new Slice( 12 );
//                    idSlice.setLong( 0, i+1 );
//                    idSlice.setInt( 8, p+1 );
//                    Assert.assertEquals( idSlice, valuekey.getId() );
//                    Assert.assertEquals( ValueType.DELETION.getPersistentId(), valuekey.getValueType().getPersistentId() );
//                    Assert.assertEquals( ValueType.VALUE.getPersistentId(), key.getValueType().getPersistentId() );
//                }
//            }
//        }
//    }
}
