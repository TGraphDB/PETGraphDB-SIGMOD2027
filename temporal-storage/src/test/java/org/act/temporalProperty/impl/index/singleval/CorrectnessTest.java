//package org.act.temporalProperty.impl.index.singleval;
//
//import com.google.common.collect.Table;
//import org.act.temporalProperty.TemporalPropertyStore;
//import org.act.temporalProperty.impl.InternalEntry;
//import org.act.temporalProperty.index.IndexValueType;
//import org.act.temporalProperty.index.value.IndexQueryRegion;
//import org.act.temporalProperty.index.value.PropertyValueInterval;
//import org.act.temporalProperty.index.value.rtree.IndexEntry;
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
//import java.util.*;
//
///**
// * Created by song on 2018-01-27.
// */
//public class CorrectnessTest {
//    private static Logger log = LoggerFactory.getLogger(CorrectnessTest.class);
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
//
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
//        store.createValueIndex(new TimePointL(1560), new TimePointL(27360), proIds);
//        log.info("create index done");
//    }
//
//    @Test
//    public void main() {
//        int startTime = 18300, endTime = 20000, valMin=0, valMax=200; //should return (18300, 20000); as importer is (0, 2760), return (2760, 20000) ???!!!! Wrong answer?
//        compare(startTime, endTime, valMin, valMax);
//    }
//
//    private void compare(int startTime, int endTime, int valMin, int valMax){
//
//        proIds.add(1);
//
//        int proNum = proIds.size();
//        int[][] pValueIntervals = new int[proNum][2];
//        //pId = 1, valueMin = 0, valueMax = 200
//        pValueIntervals[0][0] = valMin;
//        pValueIntervals[0][1] = valMax;
//
//        List<IndexEntry> rangeResult = sourceEntry.queryBySource(startTime, endTime, proIds, pValueIntervals);
//        rangeResult.sort(cmp);
//
//        List<IndexEntry> indexResult = queryByIndex(startTime, endTime, valMin, valMax);//27000
//        indexResult.sort(cmp);
//        log.info("index query complete");
//        List<IndexEntry> indexResult2 = sourceEntry.mergeIndexResult(indexResult);
//
////        List<IndexEntry> rangeResult = queryByRange( startTime, endTime, valMin, valMax);
////        rangeResult.sort(cmp);
////        log.info("range query complete");
//
//   //     log.info("size: range({}) vs index({})", rangeResult.size(), indexResult.size());
////
//    //    sameEntities(rangeResult, indexResult);
//
////        for(int i=0; i<rangeResult.size() && i<indexResult.size(); i++){
////            IndexEntry rangeE = rangeResult.get(i);
////            IndexEntry indexE = indexResult.get(i);
////            if(!rangeE.equals(indexE)){
////                if(!partialEqual(rangeE, indexE, startTime, endTime)) {
////                    log.debug("entry not equal. index({}) vs range({})", indexE, rangeE);
////                }
////            }
////            if(i>1000) return;
////        }
//
////        log.info("begin validation");
////        for(IndexEntry entry : indexResult){
////            validateByRangeQuery(entry.getEntityId(), 1, entry.getStart(), entry.getEnd(), entry.getValue(0));
////        }
//    }
//
//    private boolean sameEntities(List<IndexEntry> rangeResult, List<IndexEntry> indexResult){
//        Set<Long> rangeEntities = new HashSet<>();
//        Set<Long> indexEntities = new HashSet<>();
//        for(IndexEntry entry: indexResult){
//            indexEntities.add(entry.getEntityId());
//        }
//        for(IndexEntry entry: rangeResult){
//            rangeEntities.add(entry.getEntityId());
//        }
////        if(rangeEntities.size()!=indexEntities.size()) return false;
//
//        Set<Long> common = new HashSet<>(rangeEntities);
//        common.retainAll(indexEntities);
//
//        rangeEntities.removeAll(common);
//        log.debug("eid only in range: {}", rangeEntities);
//        indexEntities.removeAll(common);
//        log.debug("eid only in index: {}", indexEntities);
//
//        List<Table<IndexEntry, String, String>> diffLists = sourceEntry.listDiffer(rangeEntities, rangeResult);
//
//        for(Table<IndexEntry, String, String> entity : diffLists){
//            for(IndexEntry rowId : entity.rowKeySet()){
//                for(Map.Entry<String, String> x : entity.row(rowId).entrySet()) {
//                    log.debug("{} {} {}", rowId, x.getKey(), x.getValue());
//                }
//            }
//        }
//
//
//        return rangeEntities.isEmpty() && indexEntities.isEmpty();
//    }
//
////    private boolean partialEqual(IndexEntry rangeE, IndexEntry indexE, int startTime, int endTime) {
////        if(     rangeE.getStart().compareTo(startTime)<=0 &&
////                indexE.getStart()<=startTime &&
////                rangeE.getEnd()>=endTime &&
////                indexE.getEnd()>endTime &&
////                allNullOrEqual(rangeE.getEntityId(), indexE.getEntityId())){
////            boolean valEq = true;
////            for(int j=0; j<rangeE.valueLength(); j++){
////                if(!allNullOrEqual(rangeE.getValue(j), indexE.getValue(j))){
////                    valEq=false;
////                }
////            }
////            return valEq;
////        }
////        return false;
////    }
//
//    private boolean allNullOrEqual(Object a, Object b){
//        return (a==null && b==null) || (a!=null && b!=null && a.equals(b));
//    }
//
//    private boolean smallTest() {
//        store.getRangeValue(62254, 1, new TimePointL(0), new TimePointL(999999), new CustomCallBack(62254){
//            public void onNewValue(int time, Slice value) { log.info("{} {}", time, value.getInt(0));}
//            public Object onReturn() { log.info("onReturn of test"); return null;}
//        });
//        return true;
//    }
//
//    private List<IndexEntry> queryByIndex(int timeMin, int timeMax, int valueMin, int valueMax){
//        IndexQueryRegion condition = new IndexQueryRegion(new TimePointL(timeMin), new TimePointL(timeMax));
//        Slice minValue = new Slice(4);
//        minValue.setInt(0, valueMin);
//        Slice maxValue = new Slice(4);
//        maxValue.setInt(0, valueMax);
//        condition.add(new PropertyValueInterval(1, minValue, maxValue, IndexValueType.INT));
//        List<IndexEntry> result = store.getEntries(condition);
//        log.info("index result count {}", result.size());
//        return result;
//    }
//
////    private List<IndexEntry> queryByRange(int timeMin, int timeMax, int valueMin, int valueMax){
////        List<IndexEntry> result = new ArrayList<>();
////        int count=0;
////        for(Long entityId : importer.getRoadIdMap().values()){
////            store.getRangeValue(entityId, 1, timeMin, timeMax,
////                    new CustomCallBack(entityId){
////                        private boolean first = true;
////                        private int lastTime = -1;
////                        private Slice lastVal;
////                        public void onNewValue(int time, Slice value) {
////                            if (first) {
////                                first = false;
////                            } else if (overlap(lastTime, time-1, timeMin, timeMax)) {
////                                int val = lastVal.getInt(0);
////                                if (valueMin<=val && val<=valueMax){
////                                    result.add(new IndexEntry(entityId, lastTime, time-1, new Slice[]{lastVal}));
////                                }
////                            }
////                            lastTime = time;
////                            lastVal = value;
////                        }
////                        public Object onReturn() {
////                            if(!first && lastTime<=timeMax){
////                                int val = lastVal.getInt(0);
////                                if (valueMin<=val && val<=valueMax){
////                                    result.add(new IndexEntry(entityId, lastTime, timeMax, new Slice[]{lastVal}));
////                                }
////                            }
////                            return null;
////                        }
////                    });
////            if(++count%500==0) log.info("iterate range query result count {}", result.size());
////        }
////        return result;
////    }
////
////    private boolean validateByRangeQuery(long entityId, int proId, int timeMin, int timeMax, Slice value){
////        try {
////            if(entityId==62254){
////                log.info("hit validate {}", entityId);
////            }
////            store.getRangeValue(entityId, proId, timeMin, timeMax, new CustomCallBack(entityId) {
////                private boolean first = true;
////                private int lastTime = -1;
////                private Slice lastVal;
////                public void onNewValue(int time, Slice value0) {
////                    if (first) {
////                        first = false;
////                        if(time>timeMin) throw new StopLoopException("start time("+time+") later than timeMin");
////                    } else if (overlap(lastTime, time-1, timeMin, timeMax)) {
////                        if (!value.equals(value0)) throw new StopLoopException("value not equal, got "+value0.getInt(0));
////                    }
////                    lastTime = time;
////                    lastVal = value0;
////                }
////                public Object onReturn() {
////                    if(first){
////                        throw new StopLoopException("no value in this time range ");
////                    }else if(lastTime<=timeMax) {
////                        if (!value.equals(lastVal)) {
////                            throw new StopLoopException("value not equal, got " + lastVal.getInt(0));
////                        }
////                    }
////                    return null;
////                }
////            });
////        }catch (StopLoopException e){
////            log.info("validate failed: {} {} {} {} {} {}",
////                    entityId, proId, timeMin, timeMax, value.getInt(0), e.getMessage());
////            return false;
////        }
////        return true;
////    }
//
//    @Test
//    public void manualValidate(){
//        int timeMin=0,  timeMax=9999999;
//        long entityId = 353;
//
//        store.getRangeValue(entityId, 1, new TimePointL(timeMin), new TimePointL(timeMax),
//                new CustomCallBack(entityId){
//                    public void onNewValue(int time, Slice value) {
//                        log.debug("time({}) val({})", time, value.getInt(0));
//                    }
//                    public Object onReturn() {
//                        return null;
//                    }
//                });
//        List<IndexEntry> indexResult = queryByIndex(18300, 20000, 380, 400);
//        log.debug("result size {}", indexResult.size());
//        for(IndexEntry entry : indexResult) {
//            log.debug("{}", entry);
//        }
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
//    Comparator<IndexEntry> cmp = (o1, o2) -> {
//        int eidCmp = Long.compare(o1.getEntityId(), o2.getEntityId());
//        if (eidCmp == 0) {
//            int startCmp = o1.getStart().compareTo(o2.getStart());
//            if (startCmp == 0) {
//                return o1.getEnd().compareTo(o2.getEnd());
//            } else {
//                return startCmp;
//            }
//        } else {
//            return eidCmp;
//        }
//    };
//
//    @After
//    public void closeDB() throws Throwable {
//        if(store!=null) store.shutDown();
//    }
//
//}
