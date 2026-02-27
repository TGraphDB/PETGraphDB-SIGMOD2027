//package org.act.temporalProperty.impl.index.singleval;
//
//import org.act.temporalProperty.TemporalPropertyStore;
//import org.act.temporalProperty.impl.InternalEntry;
//import org.act.temporalProperty.index.IndexValueType;
//import org.act.temporalProperty.index.value.IndexQueryRegion;
//import org.act.temporalProperty.index.value.IndexTableIterator;
//import org.act.temporalProperty.index.value.PropertyValueInterval;
//import org.act.temporalProperty.index.value.rtree.IndexEntry;
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
//import java.util.Iterator;
//import java.util.LinkedList;
//import java.util.List;
//
///**
// * Created by song on 2018-01-28.
// */
//public class PerformanceTest {
//    public static LinkedList<Integer> nodeAccessList;
//
//    private static Logger log = LoggerFactory.getLogger(PerformanceTest.class);
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
//
//        buildIndex();
//    }
//
//    private void buildIndex(){
//
//        proIds.add(1);
////        store.createValueIndex(1288803660, 1288824660, proIds, types);
////        store.createValueIndex(1288800300, 1288802460, proIds, types);
//        store.createValueIndex(1560, 27360, proIds);
//        log.info("create index done");
//    }
//
//    @Test
//    public void performance(){
//        LinkedList<Long> indexTimeList = new LinkedList<>();
//        LinkedList<Integer> resultSizeList = new LinkedList<>();
//        LinkedList<Integer> nodeAccessCount = new LinkedList<>();
//        List<Integer> valMinList = new ArrayList<>();
//        List<Integer> valMaxList = new ArrayList<>();
//        log.info("index query start");
//        for(int valMin=0; valMin<1000; valMin+=60) {
//            for (int valMax = valMin; valMax < 1000; valMax += 60) {
//                valMinList.add(valMin);
//                valMaxList.add(valMax);
//            }
//        }
//
//        for(int start=1560; start<27860; start+=300){
//            for(int end=start; end<27360; end+=300){
//                indexTimeList.add(System.currentTimeMillis());
//                for(int valMin=0; valMin<1000; valMin+=60){
//                    for(int valMax=valMin; valMax<1000; valMax+=60){
//                        List<IndexEntry> indexResult = queryByIndex(start, end, valMin, valMax);
//                        resultSizeList.add(indexResult.size());
//                        indexTimeList.add(System.currentTimeMillis());
//                        nodeAccessCount.add(IndexTableIterator.Statistic.nodeAccessList.size());
//                        IndexTableIterator.Statistic.nodeAccessList.clear();
//                    }
//                }
//
//                Long startTP = indexTimeList.pollFirst();
//                Iterator<Integer> sizeIter = resultSizeList.iterator();
//                Iterator<Long> indexIter = indexTimeList.iterator();
//                Iterator<Integer> nodeCountIter = nodeAccessCount.iterator();
//                int i=0;
//                while(indexIter.hasNext()){
//                    Long indexT = indexIter.next();
//                    int valMin = valMinList.get(i);
//                    int valMax = valMaxList.get(i);
//                    int size = sizeIter.next();
//                    int nodeAccessed = nodeCountIter.next();
//                    System.out.println(start+", "+end+", "+valMin+", "+valMax+", "+size+", "+(indexT-startTP)+", "+nodeAccessed);
//                    startTP = indexT;
//                    i++;
//                }
////                log.info("==================================");
//                resultSizeList.clear();
//                indexTimeList.clear();
//
//            }
//            break;
//        }
////        LinkedList<Long> rangeTimeList = new LinkedList<>();
////                        List<IndexEntry> rangeResult = queryByRange( start, end, valMin, valMax);
////                        rangeTimeList.add(System.currentTimeMillis());
////        log.info("range query done. loop {}, time {}ms, avg {}ms per query", loopCount, stop-tick, ((stop-tick)/(double)loopCount));
//    }
//
//
//    private List<IndexEntry> queryByIndex(int timeMin, int timeMax, int valueMin, int valueMax){
//        IndexQueryRegion condition = new IndexQueryRegion(timeMin, timeMax);
//        Slice minValue = new Slice(4);
//        minValue.setInt(0, valueMin);
//        Slice maxValue = new Slice(4);
//        maxValue.setInt(0, valueMax);
//        condition.add(new PropertyValueInterval(1, minValue, maxValue, IndexValueType.INT));
//        List<IndexEntry> result = store.getEntries(condition);
////        log.info("index result count {}", result.size());
//        return result;
//    }
//
//    private List<IndexEntry> queryByRange(int timeMin, int timeMax, int valueMin, int valueMax){
//        List<IndexEntry> result = new ArrayList<>();
//        for(Long entityId : importer.getRoadIdMap().values()){
//            store.getRangeValue(entityId, 1, timeMin, timeMax,
//                    new CustomCallBack(entityId){
//                        private boolean first = true;
//                        private int lastTime = -1;
//                        private Slice lastVal;
//                        public void onNewValue(int time, Slice value) {
//                            if (first) {
//                                first = false;
//                            } else if (overlap(lastTime, time-1, timeMin, timeMax)) {
//                                int val = lastVal.getInt(0);
//                                if (valueMin<=val && val<=valueMax){
//                                    result.add(new IndexEntry(entityId, lastTime, time-1, new Slice[]{lastVal}));
//                                }
//                            }
//                            lastTime = time;
//                            lastVal = value;
//                        }
//                        public Object onReturn() {
//                            if(!first && lastTime<=timeMax){
//                                int val = lastVal.getInt(0);
//                                if (valueMin<=val && val<=valueMax){
//                                    result.add(new IndexEntry(entityId, lastTime, timeMax, new Slice[]{lastVal}));
//                                }
//                            }
//                            return null;
//                        }
//                    });
//
//        }
////        log.info("iterate result count {}", result.size());
//        return result;
//    }
//
//    private boolean validateByRangeQuery(long entityId, int proId, int timeMin, int timeMax, Slice value){
//        try {
//            if(entityId==62254){
//                log.info("hit validate {}", entityId);
//            }
//            store.getRangeValue(entityId, proId, timeMin, timeMax, new CustomCallBack(entityId) {
//                private boolean first = true;
//                private int lastTime = -1;
//                private Slice lastVal;
//                public void onNewValue(int time, Slice value0) {
//                    if (first) {
//                        first = false;
//                        if(time>timeMin) throw new StopLoopException("start time("+time+") later than timeMin");
//                    } else if (overlap(lastTime, time-1, timeMin, timeMax)) {
//                        if (!value.equals(value0)) throw new StopLoopException("value not equal, got "+value0.getInt(0));
//                    }
//                    lastTime = time;
//                    lastVal = value0;
//                }
//                public Object onReturn() {
//                    if(first){
//                        throw new StopLoopException("no value in this time range ");
//                    }else if(lastTime<=timeMax) {
//                        if (!value.equals(lastVal)) {
//                            throw new StopLoopException("value not equal, got " + lastVal.getInt(0));
//                        }
//                    }
//                    return null;
//                }
//            });
//        }catch (StopLoopException e){
//            log.info("validate failed: {} {} {} {} {} {}",
//                    entityId, proId, timeMin, timeMax, value.getInt(0), e.getMessage());
//            return false;
//        }
//        return true;
//    }
//
//    private static boolean overlap(int t1min, int t1max, int t2min, int t2max){return (t1min<=t2max && t2min<=t1max);}
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
//    private class ComparisonTask{
//        List<IndexValueType> types;
//        IndexQueryRegion queryRegion;
//        long resultEntryCount;
//        long executionTime;
//
//    }
//
//    @After
//    public void closeDB() throws Throwable {
//        if(store!=null) store.shutDown();
//    }
//
//}
