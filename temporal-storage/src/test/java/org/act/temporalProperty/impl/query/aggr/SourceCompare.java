//package org.act.temporalProperty.impl.query.aggr;
//
//import org.act.temporalProperty.TemporalPropertyStore;
//import org.act.temporalProperty.index.IndexType;
//import org.act.temporalProperty.query.aggr.TimeIntervalEntry;
//import org.act.temporalProperty.index.value.rtree.IndexEntry;
//import org.act.temporalProperty.meta.ValueContentType;
//import org.act.temporalProperty.query.aggr.AggregationIndexQueryResult;
//import org.act.temporalProperty.query.aggr.AggregationQuery;
//import org.act.temporalProperty.query.aggr.DurationStatisticAggregationQuery;
//import org.act.temporalProperty.query.aggr.ValueGroupingMap;
//import org.act.temporalProperty.util.DataFileImporter;
//import org.act.temporalProperty.util.Slice;
//import org.act.temporalProperty.util.StoreBuilder;
//import org.junit.Test;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.io.*;
//import java.util.*;
//
///*
// * 1. import sourceData
// * 2. Min & Max : according to the <startTime, endTime>, selecting entries and the MIN & MAX
// */
//public class SourceCompare
//{
//    private Logger log = LoggerFactory.getLogger( SourceCompare.class );
//    public static final int NOW_TIME = 0x40000000; //2^30
//    private static TemporalPropertyStore store;
//    private static DataFileImporter dataFileImporter;
//
//    static {
//        try {
//            dataFileImporter = new DataFileImporter(280);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
//
//    private static List<File> dataFileList = dataFileImporter.getDataFileList();
//    private Map<String, Long> entityIdMap = new HashMap<>();
//    private List<IndexEntry> entryList = new ArrayList<>();
////    private List<IndexEntry> mergeList = new ArrayList<>();
//    private Map<Long, List<IndexEntry>> mergeListMap = new HashMap<>();
//    private static int inputFileCount;
//
//   /* public SourceCompare(int inputFileCount) {
//        this.inputFileCount = inputFileCount;
//    }
//*/
//    @Test
//    public void test() throws Throwable {
//        log.info("SOURCE: importing... ");
//        this.inputFileCount = 100;
//        inputFileData();
//        mergeEntryList(1);
//        log.info("SOURCE: import done");
//        IndexEntry maxEntry = aggregationMax(Long.valueOf(5), 1,1560, 27770);
//        log.info("SOURCE: max query done. MAX={}", maxEntry.getValue(0).getInt(0));
//        IndexEntry minEntry = aggregationMin(Long.valueOf(5), 1,1560, 27770);
//        log.info("SOURCE: min query done. MIN={}", minEntry.getValue(0).getInt(0));
//        aggregationDuration(Long.valueOf(5), 1, 555, 27770, 20, 24);
//
//        log.info("SOURCE: duration query done");
//        log.info("TGRAPH: importing...");
//
//        dataImporter();
//        log.info("TGRAPH: import done");
//        rangeDuration(Long.valueOf(5), 1, 555, 27770, 20, 24);
//        log.info("TGRAPH: duration query (range) done");
//        indexDuration(Long.valueOf(5), 1, 555, 27770, 20, 24);
//        log.info("TGRAPH: duration query (index) done");
//        indexMinMax(Long.valueOf(5), 1,1560, 27770);
//        log.info("TGRAPH: min max query (index) done");
//        rangeMinMax(5, 1, 1560, 27770);
//        log.info("TGRAPH: min max query (range) done");
//        store.shutDown();
//        log.info("TGRAPH: system shutdown");
//    }
//
//    private void rangeMinMax( long entityId, int proId, int start, int end )
//    {
//        Object o = store.getRangeValue( entityId, proId, start, end, AggregationQuery.MinMax );
//        Map<Integer, Slice> val = (Map<Integer,Slice>) o;
//        log.info("min = {}, max = {}", val.get( AggregationQuery.MIN ).getInt( 0 ), val.get( AggregationQuery.MAX ).getInt( 0 ));
//    }
//
//    public void dataImporter() throws Throwable {
//
//        StoreBuilder storeBuilder = new StoreBuilder(dataFileImporter.getDbDir(), true);
//        store = storeBuilder.store();
//
//        store.createProperty(1, ValueContentType.INT);
//        store.createProperty(2, ValueContentType.INT);
//        store.createProperty(3, ValueContentType.INT);
//        store.createProperty(4, ValueContentType.INT);
//
//        int fileNum = Math.min(dataFileList.size(), inputFileCount);
//        for (int i = 0; i < fileNum; i++) {
//            File file = dataFileList.get(i);
//            int sTime = timeStr2int(file.getName().substring(9, 21)) - 1288886400; // for 2010-11-05 data. //1288800000;for 2010-11-04
//            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
//                String line = br.readLine();
//                while ((line = br.readLine()) != null) {
//                    String[] fields = line.split(",");
//                    Long entityId = getEntityId(fields[1], fields[2]);
//
//                    int travelTime = Integer.valueOf(fields[6]);
//
//                    int fullStatus = Integer.valueOf(fields[7]);
//
//                    int vehicleCount = Integer.valueOf(fields[8]);
//
//                    int segmentCount = Integer.valueOf(fields[9]);
//
//                    StoreBuilder.setIntProperty(store, sTime, entityId, 1, travelTime);
//                    StoreBuilder.setIntProperty(store, sTime, entityId, 2, fullStatus);
//                    StoreBuilder.setIntProperty(store, sTime, entityId, 3, vehicleCount);
//                    StoreBuilder.setIntProperty(store, sTime, entityId, 4, segmentCount);
//                }
//                br.close();
//            } catch (IOException e) {
//                log.info("inputFileData : NULLIOException.");
//            }
//        }
//    }
//
//    public void inputFileData() {
//
//        int fileNum = Math.min(dataFileList.size(), inputFileCount);
//        for (int i = 0; i < fileNum; i++) {
//            File file = dataFileList.get(i);
//            int sTime = timeStr2int(file.getName().substring(9, 21)) - 1288886400; // for 2010-11-05 data. //1288800000;for 2010-11-04
//            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
//                String line = br.readLine();
//                while ((line = br.readLine()) != null) {
//                    String[] fields = line.split(",");
//                    Long entityId = getEntityId(fields[1], fields[2]);
//
//                    Slice travelTime = new Slice(4);
//                    travelTime.setInt(0, Integer.valueOf(fields[6]));
//
//                    Slice fullStatus = new Slice(4);
//                    fullStatus.setInt(0, Integer.valueOf(fields[7]));
//
//                    Slice vehicleCount = new Slice(4);
//                    vehicleCount.setInt(0, Integer.valueOf(fields[8]));
//
//                    Slice segmentCount = new Slice(4);
//                    segmentCount.setInt(0, Integer.valueOf(fields[9]));
//
//                    entryList.add(new IndexEntry(entityId, sTime, NOW_TIME, new Slice[]{travelTime, fullStatus, vehicleCount, segmentCount}));
//                }
//                br.close();
//            } catch (IOException e) {
//                log.info("inputFileData : NULLIOException.");
//            }
//        }
//
//        entryList.sort(Comparator.comparing(IndexEntry::getEntityId));
//    }
//
//    public void mergeEntryList(int propertyId) {
//
//        List<IndexEntry> entityList = new ArrayList<>();
//        IndexEntry entry = null;
//        for (int i = 0; i < entryList.size(); i++) {
//
//            entry = entryList.get(i);
//            entityList.add(entry);
//
//            if ((i + 1 == entryList.size()) || (!entryList.get(i + 1).getEntityId().equals(entry.getEntityId()))) {
//
//                List<IndexEntry> mapList = new ArrayList<>();
//                Long entityId = entry.getEntityId();
//                entityList.sort(Comparator.comparing(IndexEntry::getStart));
//                for (int j = 0; j < entityList.size(); ) {
//
//                    entry = entityList.get(j);
//                    int sTime = entry.getStart();
//                    int eTime = entry.getEnd();
//                    Slice pValue = entry.getValue(propertyId - 1);
//                    Slice[] pValues = new Slice[4];
//                    pValues[propertyId - 1] = pValue;
//
//                    while (j + 1 < entityList.size()) {
//
//                        if (!entityList.get(j + 1).getValue(propertyId - 1).equals(entry.getValue(propertyId - 1))) {
//                            eTime = entityList.get(j + 1).getStart() - 1;
//                            break;
//                        }
//                        j++;
//                    }
//
//                    entry = new IndexEntry(entityId, sTime, eTime, pValues);
//                    //mergeList.add(entry);
//                    mapList.add(entry);
//                    j++;
//                }
//                mergeListMap.put(entityId, mapList);
//
//                entityList.clear();
//            }
//        }
//    }
//
//    public IndexEntry aggregationMax(Long entityId, int propertyId, int start, int end) {
//        Integer maxPValue = 0;
//        Integer pValue = 0;
//        IndexEntry maxEntry = null;
//        IndexEntry entry = null;
//        List<IndexEntry> entryList = null;
//
//        if (mergeListMap.containsKey(entityId) == false) {
//            return null;
//        } else {
//            entryList = mergeListMap.get(entityId);
//        }
//
//        for (int i = 0; i < entryList.size(); i++) {
//
//            entry = entryList.get(i);
//            if ((entry.getStart() <= end) && (entry.getEnd() >= start)) {
//                pValue = entry.getValue(propertyId - 1).getInt(0);
//                if (pValue.compareTo(maxPValue) > 0) {
//                    maxPValue = pValue;
//                    maxEntry = entry;
//                }
//            }
//        }
//
//        return maxEntry;
//    }
//
//    public IndexEntry aggregationMin(Long entityId, int propertyId, int start, int end) {
//        Integer minPValue = 0x40000000; //2^30
//        Integer pValue = 0;
//        IndexEntry minEntry = null;
//        IndexEntry entry = null;
//        List<IndexEntry> entryList = null;
//
//        if (mergeListMap.containsKey(entityId) == false) {
//            return null;
//        } else {
//            entryList = mergeListMap.get(entityId);
//        }
//
//        for (int i = 0; i < entryList.size(); i++) {
//
//            entry = entryList.get(i);
//            if ((entry.getStart() <= end) && (entry.getEnd() >= start)) {
//                pValue = entry.getValue(propertyId - 1).getInt(0);
//                if (pValue.compareTo(minPValue) < 0) {
//                    minPValue = pValue;
//                    minEntry = entry;
//                }
//            }
//        }
//
//        return minEntry;
//    }
//
//    public Object aggregationDuration(Long entityId, int propertyId, int start, int end, int minPValue, int maxPValue) {
//        int[] interval = {0, 0, 0};
//        IndexEntry entry = null;
//        Integer pValue = 0;
//        List<IndexEntry> entryList = null;
//        Map<Integer, Integer> result = new HashMap<>();
//
//        if (mergeListMap.containsKey(entityId) == false) {
//            return -1;
//        } else {
//            entryList = mergeListMap.get(entityId);
//        }
//
//        for (int i = 0; i < entryList.size(); i++) {
//
//            entry = entryList.get(i);
//            if ((entry.getStart() <= end) && (entry.getEnd() >= start)) {
//                pValue = entry.getValue(propertyId - 1).getInt(0);
//                if(pValue < minPValue) {
//                    interval[0] += (Math.min(entry.getEnd(), end) - Math.max(entry.getStart(), start) + 1);
//                } else if (pValue <= maxPValue) {
//                    interval[1] += (Math.min(entry.getEnd(), end) - Math.max(entry.getStart(), start) + 1);
//                } else {
//                    interval[2] += (Math.min(entry.getEnd(), end) - Math.max(entry.getStart(), start) + 1);
//                }
//                //if ((pValue >= minPValue) && (pValue <= maxPValue)) {
//                //   interval += (Math.min(entry.getEnd(), end) - Math.max(entry.getStart(), start) + 1); // add 1 or not?
//                //}
//            }
//        }
//        if(interval[0] > 0)
//            result.put(0, interval[0]);
//        if(interval[1] > 0)
//            result.put(1, interval[1]);
//        if(interval[2] > 0)
//            result.put(2, interval[2]);
//
//        for (Map.Entry<Integer, Integer> entry1 : result.entrySet()) {
//            log.info(entry1.getKey() + "," + entry1.getValue());
//        }
//
//        return result;
//    }
//
//    public void rangeDuration(Long entityId, int propertyId, int start, int end, int minPValue, int maxPValue) {
//
//        Object object = store.aggregate( entityId, propertyId, start, end, new DurationStatisticAggregationQuery<Integer>( start, end )
//        {
//            @Override
//            public Integer computeGroupId(TimeIntervalEntry entry) {
//                int val = asInt( entry.value() );
//                if ( val < minPValue )
//                {
//                    return 0;
//                }
//                else if ( val <= maxPValue )
//                {
//                    return 1;
//                } else {
//                    return 2;
//                }
//            }
//
//            @Override
//            public Object onResult(Map<Integer, Integer> result) {
//                for (Map.Entry<Integer, Integer> entry : result.entrySet()) {
//                    log.info(entry.getKey() + "," + entry.getValue());
//                }
//
//                return result;
//            }
//        } );
//    }
//
//    private void waitUntilIndexReady(long indexId)
//    {
//        try
//        {
//            while ( store.listIndex().stream().filter( indexMetaData -> indexMetaData.getId() == indexId && indexMetaData.isOnline() ).count() == 0 )
//            {
//                Thread.sleep( 100 );
//            }
//        }
//        catch ( InterruptedException e )
//        {
//            e.printStackTrace();
//        }
//    }
//
//    public void indexDuration(Long entityId, int propertyId, int start, int end, int minPValue, int maxPValue) {
//
//        ValueGroupingMap group = new ValueGroupingMap.IntValueGroupMap();
//        group.range2group(group.int2Slice(0), group.int2Slice(minPValue), 0);
//        group.range2group(group.int2Slice(minPValue), group.int2Slice(maxPValue + 1), 1);
//        group.range2group(group.int2Slice(maxPValue + 1), group.int2Slice(0x40000000), 2);
//
//        long indexId = store.createAggrDurationIndex(propertyId, start, end, group, 20, Calendar.MINUTE);
//        waitUntilIndexReady( indexId );
//        log.info("build duration index done");
//        AggregationIndexQueryResult result = store.getByIndex( indexId, entityId, propertyId, start, end );
//
//        for ( Map.Entry<Integer,Integer> entry : result.getDurationResult().entrySet() )
//        {
//            log.info( entry.getKey() + "," + entry.getValue() );
//        }
//        log.info("accelerate time: "+ result.getAccelerateTime());
//    }
//
//    public void indexMinMax(Long entityId, int propertyId, int start, int end) {
//        long indexId = store.createAggrMinMaxIndex(propertyId, start, end, 20, Calendar.MINUTE, IndexType.AGGR_MIN_MAX);
//        waitUntilIndexReady( indexId );
//        log.info("build min max index done");
//        AggregationIndexQueryResult result = store.getByIndex( indexId, entityId, propertyId, start, end );
//        for (Map.Entry<Integer, Slice> entry : result.getMinMaxResult().entrySet()) {
//            log.info(entry.getKey() + "," + entry.getValue().getInt(0));
//        }
//        log.info("accelerate time: "+ result.getAccelerateTime());
//    }
//
//    private Long getEntityId(String gridId, String chainId){
//
//        String key = gridId + ":" + chainId;
//
//        if(entityIdMap.containsKey(key)) {
//            return entityIdMap.get(key);
//        } else {
//            long value = entityIdMap.size();
//            entityIdMap.put(key, value);
//            return value;
//        }
//    }
//
//    /*
//   the same function as TrafficDataImporter.java->timeStr2int()
//    */
//    private static int timeStr2int(String tStr){
//        String yearStr = tStr.substring(0,4);
//        String monthStr = tStr.substring(4,6);
//        String dayStr = tStr.substring(6,8);
//        String hourStr = tStr.substring(8,10);
//        String minuteStr = tStr.substring(10, 12);
////        log.info(yearStr+" "+monthStr+" "+dayStr+" "+hourStr+" "+minuteStr);
//        int year = Integer.parseInt(yearStr);
//        int month = Integer.parseInt(monthStr)-1;//month count from 0 to 11, no 12
//        int day = Integer.parseInt(dayStr);
//        int hour = Integer.parseInt(hourStr);
//        int minute = Integer.parseInt(minuteStr);
////        log.info(year+" "+month+" "+day+" "+hour+" "+minute);
//        Calendar ca= Calendar.getInstance();
//        ca.set(year, month, day, hour, minute, 0); //seconds set to 0
//        long timestamp = ca.getTimeInMillis();
////        log.info(timestamp);
//        if(timestamp/1000<Integer.MAX_VALUE){
//            return (int) (timestamp/1000);
//        }else {
//            throw new RuntimeException("timestamp larger than Integer.MAX_VALUE, this should not happen");
//        }
//    }
//}
