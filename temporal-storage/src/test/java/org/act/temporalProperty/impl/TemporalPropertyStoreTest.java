//package org.act.temporalProperty.impl;
//
//import java.io.File;
//import java.util.concurrent.CountDownLatch;
//import java.util.concurrent.Executor;
//import java.util.concurrent.Executors;
//
//import junit.framework.Assert;
//
//import org.act.temporalProperty.TemporalPropertyStore;
//import org.act.temporalProperty.TemporalPropertyStoreFactory;
//import org.act.temporalProperty.util.FileUtils;
//import org.act.temporalProperty.util.Slice;
//import org.junit.Before;
//import org.junit.Test;
//
//public class TemporalPropertyStoreTest
//{
//    private final static int TIME_NUMS = 300;
//    private final static int PRO_NUMS = 60;
//    private final static int ID_NUMS = 600;
//    private final static String dbDir = "./target/TemporalPropertyStore";
//    private static TemporalPropertyStore store;
//
//    @Before
//    public void writeData() throws Throwable
//    {
//        makeDir();
//		store = TemporalPropertyStoreFactory.newPropertyStore( new File(dbDir) );
//        long start = System.currentTimeMillis();
//        singleThreadWrite();
//        //singleThreadRecureWrite();
//        //multiThreadWrite();
//        System.out.println( "write " + TIME_NUMS*PRO_NUMS*ID_NUMS*40/1024/1024 + "M data. use time :" + (System.currentTimeMillis() - start)/100 +"second" );
//    }
//
//
//    @Test
//    public void doNothing(){}
//
//    //@Test
//    public void readDataTest() throws InterruptedException
//    {
//        long start = System.currentTimeMillis();
//        for( int t = 0; t<TIME_NUMS; t++ )
//                for( int i = 0; i<ID_NUMS; i++ )
//                    for( int p =0; p<PRO_NUMS; p++ )
//                    {
//                        Slice idSlice = new Slice(12);
//                        idSlice.setLong( 0, i );
//                        idSlice.setInt( 8, p );
////                        if(t == 254 && i ==117 && p == 38 )
////                        {
////                            t++;
////                            t--;
////                        }
//                        Slice value = store.getPointValue( i, p, t );
//                        System.out.println( value.toString() );
//                        if( value == null )
//                            value = new Slice( 0 );
//                        InternalKey valueAsKey = new InternalKey( value );
//                        Assert.assertEquals( idSlice, valueAsKey.getId() );
//                        Assert.assertEquals( t, valueAsKey.getStartTime() );
////                        Assert.assertEquals( 20, valueAsKey.getValueLength() );
//                        Assert.assertEquals( ValueType.VALUE.getPersistentId(), valueAsKey.getValueType().getPersistentId() );
////                        Thread.currentThread().sleep( 100 );
//                    }
//        System.out.println( "Read " + TIME_NUMS*PRO_NUMS*ID_NUMS + "times data. use time :" + (System.currentTimeMillis() - start)/100 +"second" );
//    }
//
//    private void makeDir()
//    {
//        File db = new File(dbDir);
//        if( db.exists() )
//            FileUtils.deleteDirectoryContents( db );
//        db.mkdirs();
//    }
//
//    private void multiThreadWrite()
//    {
//        final CountDownLatch latch = new CountDownLatch( 1 );
//        final CountDownLatch latch2 = new CountDownLatch( 2 );
//        Executor executor = Executors.newFixedThreadPool( 2 );
//        executor.execute( new Runnable()
//        {
//            @Override
//            public void run()
//            {
//                try
//                {
//                    latch.await();
//                    for( int t = 0; t<TIME_NUMS; t++ )
//                    {
//                        System.out.println( t );
//                        for( int i = 0; i<ID_NUMS/2; i++ )
//                        {
//                            for( int p =0; p<PRO_NUMS; p++ )
//                            {
//                                Slice idSlice = new Slice(12);
//                                idSlice.setLong( 0, i );
//                                idSlice.setInt( 8, p );
//                                InternalKey key = new InternalKey( idSlice, t, 20, ValueType.VALUE );
//                                Slice valueSlice = key.encode();
//                                Assert.assertTrue( store.setProperty( key.encode(), valueSlice.copyBytes() ) );
//                            }
//                        }
//                    }
//                    latch2.countDown();
//                }
//                catch ( InterruptedException e )
//                {
//                    e.printStackTrace();
//                }
//            }
//        } );
//        executor.execute( new Runnable()
//        {
//            @Override
//            public void run()
//            {
//                try
//                {
//                    latch.await();
//                    for( int t = 0; t<TIME_NUMS; t++ )
//                    {
//                        System.out.println( t );
//                        for( int i = ID_NUMS/2; i<ID_NUMS; i++ )
//                        {
//                            for( int p =0; p<PRO_NUMS; p++ )
//                            {
//                                Slice idSlice = new Slice(12);
//                                idSlice.setLong( 0, i );
//                                idSlice.setInt( 8, p );
//                                InternalKey key = new InternalKey( idSlice, t, 20, ValueType.VALUE );
//                                Slice valueSlice = key.encode();
//                                Assert.assertTrue( store.setProperty( key.encode(), valueSlice.copyBytes() ) );
//                            }
//                        }
//                    }
//                    latch2.countDown();
//                }
//                catch ( InterruptedException e )
//                {
//                    e.printStackTrace();
//                }
//            }
//        } );
//        latch.countDown();
//        try
//        {
//            latch2.await();
//        }
//        catch ( InterruptedException e )
//        {
//            // TODO Auto-generated catch block
//            e.printStackTrace();
//        }
//    }
//
//    private void singleThreadWrite()
//    {
//        for( int t = 0; t<TIME_NUMS; t++ )
//            for( int i = 0; i<ID_NUMS; i++ )
//                for( int p =0; p<PRO_NUMS; p++ )
//                {
//                    Slice idSlice = new Slice(12);
//                    idSlice.setLong( 0, i );
//                    idSlice.setInt( 8, p );
//                    InternalKey key = new InternalKey( idSlice, t, 20, ValueType.VALUE );
//                    Slice valueSlice = key.encode();
//                    Assert.assertTrue( store.setProperty( key.encode(), valueSlice.copyBytes() ) );
//                }
//    }
//
//    private void singleThreadRecureWrite()
//    {
//        for( int t = 0; t<TIME_NUMS; t = t+3 )
//            for( int tt = 3; tt >=0; tt-- )
//                for( int i = 0; i<ID_NUMS; i++ )
//                    for( int p =0; p<PRO_NUMS; p++ )
//                    {
//                        Slice idSlice = new Slice(12);
//                        idSlice.setLong( 0, i );
//                        idSlice.setInt( 8, p );
//                        InternalKey key = new InternalKey( idSlice, t+tt, 20, ValueType.VALUE );
//                        Slice valueSlice = key.encode();
//                        Assert.assertTrue( store.setProperty( key.encode(), valueSlice.copyBytes() ) );
//                    }
//    }
//}
