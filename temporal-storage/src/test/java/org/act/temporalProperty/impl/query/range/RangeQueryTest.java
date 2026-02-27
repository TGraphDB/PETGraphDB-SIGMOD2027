//package org.act.temporalProperty.impl.query.range;
//
//import org.act.temporalProperty.TemporalPropertyStore;
//import org.act.temporalProperty.impl.InternalEntry;
//import org.act.temporalProperty.impl.index.singleval.SourceCompare;
//import org.act.temporalProperty.query.TimePointL;
//import org.act.temporalProperty.query.range.InternalEntryRangeQueryCallBack;
//import org.act.temporalProperty.util.DataFileImporter;
//import org.act.temporalProperty.util.Slice;
//import org.act.temporalProperty.util.StoreBuilder;
//import org.act.temporalProperty.util.TrafficDataImporter;
//import org.junit.After;
//import org.junit.Before;
//import org.junit.Test;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.io.File;
//import java.util.ArrayList;
//import java.util.List;
//
///**
// * Created by song on 2018-01-23.
// */
//public class RangeQueryTest {
//    private static Logger log = LoggerFactory.getLogger(RangeQueryTest.class);
//
//    private static DataFileImporter dataFileImporter;
//    private TemporalPropertyStore store;
//    private StoreBuilder stBuilder;
//    private TrafficDataImporter importer;
//    private SourceCompare sourceEntry;
//
//    private static String dbDir;
//    private static String dataPath;
//    private static List<File> dataFileList;
//
//    List<Integer> proIds = new ArrayList<>(); // the list of the proIds which will be indexed and queried
//
//    @Before
//    public void initDB() throws Throwable {
//        dataFileImporter = new DataFileImporter(280);
//        dbDir = dataFileImporter.getDbDir();
//        dataPath = dataFileImporter.getDataPath();
//        dataFileList = dataFileImporter.getDataFileList();
//
//        stBuilder = new StoreBuilder(dbDir, true);
//        importer = new TrafficDataImporter(stBuilder.store(), dataFileList, 1000);
//        sourceEntry = new SourceCompare(dataPath, dataFileList, 1000);
//        log.info("time: {} - {}", importer.getMinTime(), importer.getMaxTime());
//        store = stBuilder.store();
//    }
//
//    @Test
//    public void test2fail() throws Throwable {
//        this.store.getRangeValue(62254,1,new TimePointL(0),Integer.MAX_VALUE-6, new CustomCallBack(62254){
//            public void onCall(int time, Slice value) { log.info("{} {}", time, value.getInt(0));}
//            public Object onReturn() { log.info("onReturn of test"); return null;}
//        });
//        log.info("=================");
//        this.store.getRangeValue(62254,1,18360,18959, new CustomCallBack(62254){
//            public void onCall(int time, Slice value) { log.info("{} {}", time, value.getInt(0));}
//            public Object onReturn() { log.info("onReturn of test"); return null;}
//        });
//    }
//
//    private class CustomCallBack implements InternalEntryRangeQueryCallBack {
//        private long entityId;
//        public CustomCallBack(long entityId){this.entityId=entityId;}
//        public void setValueType(String valueType) {}
//        public void onNewEntry(InternalEntry entry) {}
//        public Object onReturn() {return null;}
//    }
//
//    private class StopLoopException extends RuntimeException {
//        public StopLoopException() {}
//        public StopLoopException(String s) {
//            super(s);
//        }
//    }
//
//    @After
//    public void closeDB() throws Throwable {
//        if(store!=null) store.shutDown();
//    }
//
//}
