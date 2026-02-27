//package org.act.temporalProperty.impl.index.singleval;
//
//import org.act.temporalProperty.TemporalPropertyStore;
//import org.act.temporalProperty.impl.InternalEntry;
//import org.act.temporalProperty.index.IndexValueType;
//import org.act.temporalProperty.index.value.IndexQueryRegion;
//import org.act.temporalProperty.index.value.PropertyValueInterval;
//import org.act.temporalProperty.query.TimePointL;
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
//import java.util.List;
//
///**
// * Created by song on 2018-01-22.
// */
//public class BuildAndQueryTest {
//    private static Logger log = LoggerFactory.getLogger(BuildAndQueryTest.class);
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
//    @BeforeClass
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
//    }
//
//    @Before
//    public void buildIndex(){
//
//        proIds.add(1);
////        store.createProperty(1, ValueContentType.INT);
//        store.createValueIndex(20, 200, proIds);
////        store.createValueIndex(1288800300, 1288802460, proIds, types);
////        store.createValueIndex(1560, 27360, proIds);
//        log.info("create index done");
//    }
//
//    @Test
//    public void main() throws Throwable {
//        testRangeQuery(store);
////        List<Long> iterResult = queryByIter( 18300, 27000, 0, 200);
//
//        List<Long> indexResult = queryByIndex(18300, 27000, 0, 200);
//
//
////        for(int time=0; time<=18300; time+=100){
////            for(int value=0; value<=400; value+=20){
////
////            }
////        }
//        store.shutDown();
//    }
//
//    private List<Long> queryByIndex(int timeMin, int timeMax, int valueMin, int valueMax){
//        IndexQueryRegion condition = new IndexQueryRegion(new TimePointL(timeMin), new TimePointL(timeMax));
//        Slice minValue = new Slice(4);
//        minValue.setInt(0, valueMin);
//        Slice maxValue = new Slice(4);
//        maxValue.setInt(0, valueMax);
//        condition.add(new PropertyValueInterval(1, minValue, maxValue, IndexValueType.INT));
//        List<Long> result = store.getEntities(condition);
//        log.info("index result count {}", result.size());
//        return result;
//    }
//
//    private List<Long> queryByIter(int timeMin, int timeMax, int valueMin, int valueMax){
//        List<Long> result = new ArrayList<>();
//        for(Long entityId : importer.getRoadIdMap().values()){
//            try {
//                store.getRangeValue(entityId, 1, timeMin, timeMax, new EntityIdCallBack(new TimePointL(timeMin), new TimePointL(timeMax), valueMin, valueMax));
//            }catch (StopLoopException e){
//                result.add(entityId);
//            }catch (RuntimeException e){
//                log.debug("entity id: {} {}", entityId, e.getMessage());
//                result.add(entityId);
//            }
//        }
//        log.info("iterate result count {}", result.size());
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
//
//
//    private class EntityIdCallBack implements InternalEntryRangeQueryCallBack {
//        private int timeMin, timeMax, valueMin, valueMax, lastTime = -1;
//        private boolean first = true;
//
//        public EntityIdCallBack(int timeMin, int timeMax, int valueMin, int valueMax) {
//            this.timeMin = timeMin;
//            this.timeMax = timeMax;
//            this.valueMin = valueMin;
//            this.valueMax = valueMax;
//        }
//
//        private boolean overlap(int t1min, int t1max, int t2min, int t2max){
//            return (t1min<=t2max && t2min<=t1max);
//        }
//
//        public void onNewEntry(InternalEntry entry) {
//            int time = entry.getKey().getStartTime();
//            Slice value = entry.getValue();
//            int val = value.getInt(0);
//            if(first){
//                first=false;
//            }else{
//                if(lastTime>=time){
//                    log.trace("time not inc: last("+lastTime+") cur("+time+")");
////                    throw new RuntimeException("time not inc: last("+lastTime+") cur("+time+")");
//                }
//                if(overlap(lastTime, time, timeMin, timeMax) &&
//                        (valueMin <= val && val <= valueMax)){
//                    throw new StopLoopException();
//                }
//            }
//            lastTime = time;
//        }
//        public void setValueType(String valueType) {}
//        public Object onReturn() {return null;}
//    };
//
//    private class StopLoopException extends RuntimeException {
//    }
//}
