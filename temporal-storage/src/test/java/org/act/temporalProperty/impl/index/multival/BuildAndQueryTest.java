//package org.act.temporalProperty.impl.index.multival;
//
//import org.act.temporalProperty.TemporalPropertyStore;
//import org.act.temporalProperty.impl.InternalEntry;
//import org.act.temporalProperty.impl.index.singleval.SourceCompare;
//import org.act.temporalProperty.index.IndexValueType;
//import org.act.temporalProperty.index.value.IndexQueryRegion;
//import org.act.temporalProperty.index.value.PropertyValueInterval;
//import org.act.temporalProperty.index.value.rtree.IndexEntry;
//import org.act.temporalProperty.query.range.InternalEntryRangeQueryCallBack;
//import org.act.temporalProperty.util.DataFileImporter;
//import org.act.temporalProperty.util.Slice;
//import org.act.temporalProperty.util.StoreBuilder;
//import org.act.temporalProperty.util.TrafficDataImporter;
//import org.junit.After;
//import org.junit.Before;
//import org.junit.BeforeClass;
//import org.junit.Test;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.io.File;
//import java.util.ArrayList;
//import java.util.Comparator;
//import java.util.List;
//
///**
// * Created by song on 2018-01-22.
// */
//public class BuildAndQueryTest {
//    private static Logger log = LoggerFactory.getLogger(BuildAndQueryTest.class);
//
//    private static DataFileImporter dataFileImporter;
//    private static TemporalPropertyStore store;
//    private static StoreBuilder stBuilder;
//    private static TrafficDataImporter importer;
//    private static SourceCompare sourceEntry;
//
//    private static String dbDir;
//    private static String dataPath;
//    private static List<File> dataFileList;
//
//    List<Integer> proIds = new ArrayList<>(); // the list of the proIds which will be indexed and queried
//
//    @BeforeClass
//    public static void initDB() throws Throwable {
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
//    @Before
//    public void buildIndex(){
//
//        proIds.add(1);
//        proIds.add(2);
//        proIds.add(3);
//        proIds.add(4);
//        store.createValueIndex(1560, 27360, proIds);
//        log.info("create index done");
//    }
//
//    @Test
//    public void main() throws Throwable {
//
//        int proNum = proIds.size();
//        int[][] pValueIntervals = new int[proNum][2];
//        //pId = 1, valueMin = 0, valueMax = 200
//        pValueIntervals[0][0] = 0;
//        pValueIntervals[0][1] = 200;
//        //pId = 2, valueMin = 0, valueMax = 200
//        pValueIntervals[1][0] = 0;
//        pValueIntervals[1][1] = 200;
//        //pId = 3, valueMin = 0, valueMax = 200
//        pValueIntervals[2][0] = 0;
//        pValueIntervals[2][1] = 300;
//        //pId = 4, valueMin = 0, valueMax = 200
//        pValueIntervals[3][0] = 0;
//        pValueIntervals[3][1] = 200;
//
//        List<IndexEntry> rangeResult = sourceEntry.queryBySource(18300, 27000, proIds, pValueIntervals);
//        rangeResult.sort(Comparator.comparing(IndexEntry::getEntityId));
//        log.info("range result count {}", rangeResult.size());
//
//        List<IndexEntry> indexResult = queryByIndex(18300, 27000, pValueIntervals);
//        indexResult.sort(Comparator.comparing(IndexEntry::getEntityId));
//        log.info("index result count {}", indexResult.size());
//
//
//
//        store.shutDown();
//    }
//
//    private List<IndexEntry> queryByIndex(int timeMin, int timeMax, int[][] pValueIntervals){
//        IndexQueryRegion condition = new IndexQueryRegion(timeMin, timeMax);
//
//        for(int i = 0; i < proIds.size(); i++) {
//
//            int proId = proIds.get(i);
//            Slice valueMin = new Slice(4);
//            valueMin.setInt(0, pValueIntervals[i][0]);
//            Slice valueMax = new Slice(4);
//            valueMax.setInt(0, pValueIntervals[i][1]);
//
//            condition.add(new PropertyValueInterval(proId, valueMin, valueMax, IndexValueType.INT));
//        }
//
//        List<IndexEntry> result = store.getEntries(condition);
//
//        return result;
//    }
//
//    private List<IndexEntry> queryByRange(int timeMin, int timeMax, int[][] pValueIntervals){
//        List<IndexEntry> result = new ArrayList<>();
//
//        for(Long entityId : importer.getRoadIdMap().values()){
//
//            for (int i = 0; i < proIds.size(); i++) {
//                int proIdIndex = i;
//                store.getRangeValue(entityId, proIds.get(i), timeMin, timeMax,
//                        new EntityIdCallBack(entityId) {
//                            private boolean first = true;
//                            private Slice lastVal;
//                            private int lastTime = -1;
//                            public void onCall(int time, Slice value) {
//                                if(first) {
//                                    first = false;
//                                } else if(overlap(lastTime, time - 1, timeMin, timeMax)) {
//                                    int val = lastVal.getInt(0);
//                                    if(pValueIntervals[proIdIndex][0] <= val && val <= pValueIntervals[proIdIndex][1]) {
//                                        result.add(new IndexEntry(entityId, lastTime, time - 1, new Slice[]{lastVal}));
//                                    }
//                                }
//                                lastTime = time;
//                                lastVal = value;
//                            }
//                            public Object onReturn() {
//                                if(!first && lastTime <= timeMax) {
//                                    int val = lastVal.getInt(0);
//                                    if(pValueIntervals[proIdIndex][0] <= val && val <= pValueIntervals[proIdIndex][1]) {
//                                        result.add(new IndexEntry(entityId, lastTime, timeMax, new Slice[]{lastVal}));
//                                    }
//                                }
//                                return null;
//                            }
//                        });
//            }
//
//            result.sort(Comparator.comparing(IndexEntry::getEntityId));
//        }
//
//        return result;
//    }
//
//    @After
//    public void closeDB() throws Throwable {
//        if(store!=null) store.shutDown();
//    }
//
//
//
//    private static void testRangeQuery(TemporalPropertyStore store) {
//        store.getRangeValue(2, 1, 1560, 27000, new InternalEntryRangeQueryCallBack() {
//            public void setValueType(String valueType) {}
//            public void onNewEntry(InternalEntry entry) {
//                log.info("{} {}", entry.getKey().getStartTime(), entry.getValue().getInt(0));
//            }
//            public Object onReturn() {return null;}
//        });
//    }
//
//    private boolean overlap(int t1min, int t1max, int t2min, int t2max){
//        return (t1min<=t2max && t2min<=t1max);
//    }
//
//    private class EntityIdCallBack implements InternalEntryRangeQueryCallBack {
//        private long entityId;
//        public EntityIdCallBack(long entityId){this.entityId=entityId;}
//        public void setValueType(String valueType) {}
//        public void onNewEntry(InternalEntry entry) {}
//        public Object onReturn() {return null;}
//    }
//
//    private class StopLoopException extends RuntimeException {
//    }
//}
