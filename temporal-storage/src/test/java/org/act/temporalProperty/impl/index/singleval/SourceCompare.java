//package org.act.temporalProperty.impl.index.singleval;
//
//import com.google.common.collect.HashBasedTable;
//import com.google.common.collect.Table;
//import org.act.temporalProperty.index.value.rtree.IndexEntry;
//import org.act.temporalProperty.util.Slice;
//import org.apache.commons.lang3.SystemUtils;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.io.*;
//import java.util.*;
///*
//* edit by yf
//* edit from 2018-03-13
//*
//* 1. According to the (startTime, endTime; inputFileCount) to filter files in dataDir
//* 2. Read (startTime, endTime, propertyId; entityId, propertyValue) into a array (iterator); List<List<entry>()> --- each file one list
//* 3. use <propertyValue, entry> as <K, V> build B+ tree (support range search), to reduce the size of B+ tree, each file one tree
//* 4. search the trees according to (valMin, valMax), store the result to a list
//* 5. compare the result with queryByRange and queryByIndex
//*
//* => for step 2.3.4, change B+ tree to HashMap<K, List>, then traverse the Map O(n) ; if the time is too long, change it to the B+ tree
//*    Notice: HashMap only support a single thread, for concurrent situation, it should be changed to HashTable
//*/
//
//public class SourceCompare {
//
//    public static final int NOW_TIME = 0x40000000; //2^30
//    private static Logger log = LoggerFactory.getLogger(SourceCompare.class);
//
//    private final int inputFileCount;
//    private String dataPath;
//    private List<File> dataFileList;
//    private Map<String, Long> entityIdMap;
//    private List<IndexEntry> entryList;
//
//    public SourceCompare(String dataPath, List<File> dataFileList, int inputFileCount) {
//        this.inputFileCount = inputFileCount;
//        this.dataPath = dataPath;
//        this.dataFileList = dataFileList;
//        this.entityIdMap = new HashMap<String, Long>();
//        this.entryList = new ArrayList<IndexEntry>();
//    }
//
//    public List<IndexEntry> queryBySource(int timeMin, int timeMax, List<Integer> proIds, int[][] pValueIntervals) {
//
//        int fileNum = Math.min(dataFileList.size(), inputFileCount);
//        for (int i = 0; i < fileNum; i++) {
//            File file = dataFileList.get(i);
//            int sTime = timeStr2int(file.getName().substring(9, 21)) - 1288800000;
//
//            inputFileData(file, sTime);
//
//            if(i%10==0) log.info("input {} files, current {}", i, file.getName());
//        }
//
//        return filterEntry(timeMin, timeMax, proIds, pValueIntervals);
//    }
//
//    private void inputFileData(File file, int sTime) {
//        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
//            String line = br.readLine();
//            while ((line = br.readLine()) != null) {
//                readDataLine(line, sTime);
//            }
//        } catch (IOException e) {
//            System.out.println("FileReader: NullIOException.");
//        }
//    }
//
//    public void readDataLine(String line, int sTime) {
//        String[] fields = line.split(",");
//
//        String gridId = fields[1];
//        String chainId = fields[2];
//        Long entityId = getEntityId(gridId, chainId);
//
//        Slice travelTime = new Slice(4);
//        travelTime.setInt(0, Integer.valueOf(fields[6]));
//
//        Slice fullStatus = new Slice(4);
//        fullStatus.setInt(0, Integer.valueOf(fields[7]));
//
//        Slice vehicleCount = new Slice(4);
//        vehicleCount.setInt(0, Integer.valueOf(fields[8]));
//
//        Slice segmentCount = new Slice(4);
//        segmentCount.setInt(0, Integer.valueOf(fields[9]));
//
//        entryList.add(new IndexEntry(entityId, sTime, NOW_TIME,
//                new Slice[]{travelTime, fullStatus, vehicleCount, segmentCount}));
//    }
//
//    private List<IndexEntry> filterEntry(int startTime, int endTime, List<Integer> proIds, int[][] pValueIntervals){
//
//        List<IndexEntry> mergeList = new ArrayList<>();
//
//        entryList.sort(Comparator.comparing(IndexEntry::getEntityId));
//
//        List<IndexEntry> entityIdList = new ArrayList<>();
//        for (int i = 0; i < entryList.size(); i++) {
//
//            IndexEntry entry = entryList.get(i);
//            entityIdList.add(entry);
//
//            if ((i + 1 == entryList.size()) || !entryList.get(i + 1).getEntityId().equals(entry.getEntityId())) {
//
//                entityIdList.sort(Comparator.comparing(IndexEntry::getStart));
//
//                for(int j = 0; j < entityIdList.size(); ) {
//
//                    IndexEntry entity = entityIdList.get(j);
//                    Long entityId = entity.getEntityId();
//                    int sTime = entity.getStart();
//                    int eTime = 0;
//                    Slice value = entity.getValue(0);
//                    int travelTime = value.getInt(0);
//
//                    while(j < entityIdList.size()) {
//
//                        if(j + 1 == entityIdList.size()) {
//                            //eTime = entity.getEnd();
//                            eTime = endTime;
//
//                            if((sTime <= endTime) && (eTime >= startTime)) {
//                                int k = 0;
//                                Slice[] pValues = new Slice[4];
//                                for(k = 0; k < proIds.size(); k++) {
//                                    int proId = proIds.get(k);
//                                    Slice pValue = entity.getValue(proId - 1);
//                                    pValues[proId - 1] = pValue;
//                                    if(pValue.getInt(0) < pValueIntervals[k][0] || pValue.getInt(0) > pValueIntervals[k][1]) {
//                                        break;
//                                    }
//                                }
//                                if(k == proIds.size()) {
//                                    entity = new IndexEntry(entityId, sTime, eTime, pValues);
//                                    mergeList.add(entity);
//                                }
//                            }
//
//                            j++;
//                            break;
//
//                        } else if(comparePValues(entityIdList.get(j), entityIdList.get(j + 1), proIds) == false){
//                            eTime = entityIdList.get(j + 1).getStart() - 1;
//
//                            if((sTime <= endTime) && (eTime >= startTime)) {
//                                int k = 0;
//                                Slice[] pValues = new Slice[4];
//                                for(k = 0; k < proIds.size(); k++) {
//                                    int proId = proIds.get(k);
//                                    Slice pValue = entity.getValue(proId - 1);
//                                    pValues[proId - 1] = pValue;
//                                    if(pValue.getInt(0) < pValueIntervals[k][0] || pValue.getInt(0) > pValueIntervals[k][1]) {
//                                        break;
//                                    }
//                                }
//                                if(k == proIds.size()) {
//                                    entity = new IndexEntry(entityId, sTime, eTime, pValues);
//                                    mergeList.add(entity);
//                                }
//                            }
//
//                            j++;
//                            break;
//                        }
//
//                        j++;
//                    }
//                }
//
//                entityIdList.clear();
//            }
//        }
//
//        entityIdList.clear();
//        entityIdList = null;
//
//        return mergeList;
//    }
//
//    private boolean comparePValues(IndexEntry entrySrc, IndexEntry entryDest, List<Integer> proIds) {
//
//        for(int i = 0; i < proIds.size(); i++) {
//            int proId = proIds.get(i);
//            if(!entrySrc.getValue(proId - 1).equals(entryDest.getValue(proId - 1))) {
//                return false;
//            }
//        }
//        return true;
//    }
//
//    public List<IndexEntry> mergeIndexResult(List<IndexEntry> indexResult) {
//
//        List<IndexEntry> mergeList = new ArrayList<>();
//
//        List<IndexEntry> entityIdList = new ArrayList<>();
//        for (int i = 0; i < indexResult.size(); i++) {
//
//            IndexEntry entry = indexResult.get(i);
//            entityIdList.add(entry);
//
//            if ((i + 1 == indexResult.size()) || !indexResult.get(i + 1).getEntityId().equals(entry.getEntityId())) {
//
//                entityIdList.sort(Comparator.comparing(IndexEntry::getStart));
//
//                for(int j = 0; j < entityIdList.size(); ) {
//
//                    IndexEntry entity = entityIdList.get(j);
//                    Long entityId = entity.getEntityId();
//                    int sTime = entity.getStart();
//                    Slice value = entity.getValue(0);
//
//                    while(j < entityIdList.size()) {
//
//                        if((j + 1 == entityIdList.size()) || (!entityIdList.get(j).getValue(0).equals(entityIdList.get(j + 1).getValue(0)))
//                                || (entityIdList.get(j).getEnd() + 1 != entityIdList.get(j + 1).getStart())) {
//
//                            entity = new IndexEntry(entityId, sTime,
//                                    entityIdList.get(j).getEnd(), new Slice[]{value});
//
//                            mergeList.add(entity);
//                            j++;
//                            break;
//                        }
//
//                        j++;
//                    }
//                }
//
//                entityIdList.clear();
//            }
//        }
//
//        entityIdList.clear();
//        entityIdList = null;
//
//        return mergeList;
//    }
//
//
//
//    public List<Table<IndexEntry, String, String>> listDiffer(Set<Long> entities, List<IndexEntry> rangequeries){
//
//        List<List<IndexEntry>> entityLists = new ArrayList<>();
//        List<Table<IndexEntry, String, String>> diffLists = new ArrayList<>();
//        List<String> src = new ArrayList<>();
//
//        for(Long entityId : entities) {
//            entityLists.add(queryByEntity(entityId));
//           // sourceLists.add(queryFromSource(entityId, rangequeries));
//            Table<IndexEntry, String, String> table = queryFromSource(entityId, rangequeries);
//
//            if(table != null)
//                diffLists.add(table);
//        }
//
//        return diffLists;
//    }
//
//    private List<IndexEntry> queryByEntity(Long entityId) {
//        List<IndexEntry> entityList = new ArrayList<IndexEntry>();
//
//        for(IndexEntry entry: entryList) {
//            if(entry.getEntityId().equals(entityId)) {
//                entityList.add(entry);
//            }
//        }
//
//        return entityList;
//    }
//
//
//    private Table<IndexEntry, String, String> queryFromSource(Long entityId, List<IndexEntry> sourceEntities) {
//        String fileName = null;
//        String sourceString = null;
//        Table<IndexEntry, String, String> table = HashBasedTable.create();
//
//        for(IndexEntry entry: sourceEntities) {
//            if(entry.getEntityId().equals(entityId)) {
//
//                fileName = getFileName(entry.getStart());
//                sourceString = queryByFileName(fileName, entry);
//
//                table.put(entry, fileName, sourceString);
//            }
//        }
//
//        return table;
//    }
//
//    private String queryByFileName(String fileName, IndexEntry entry) {
//        String sourceString = null;
//        if(SystemUtils.IS_OS_WINDOWS){
//            fileName = dataPath + "\\" + fileName;
//        }else{
//            fileName = dataPath + "/" + fileName;
//        }
//
//        File file = new File(fileName);
//
//        Long entityId = entry.getEntityId();
//        int travelTime = entry.getValue(0).getInt(0);
//        int sTime = entry.getStart();
//
//        int f_sTime = timeStr2int(file.getName().substring(9, 21)) - 1288800000;
//
//        Long tmp = 0L;
//
//        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
//
//            String line = br.readLine();
//            while((line = br.readLine()) != null) {
//                String[] fields = line.split(",");
//
//                Long f_entityId = getEntityId(fields[1], fields[2]);
//                int f_travelTime = Integer.valueOf(fields[6]);
//
//
//                if((f_entityId.equals(entityId)) && (f_travelTime == travelTime)) {
//                    sourceString = line;
//                    return sourceString;
//                }
//
//                if(f_entityId > 300){
//                    tmp = f_entityId;
//                }
//
//            }
//
//            br.close();
//
//        } catch (IOException e) {
//            System.out.println("SourceCompare -- queryByFileName");
//        }
//
//        return sourceString;
//    }
//
//    private int getEndTime(int fileNum, int endTime){
//        if(fileNum < dataFileList.size()) {
//            File file = dataFileList.get(fileNum);
//            return (timeStr2int(file.getName().substring(9, 21)) - 1288800000);
//        } else {
//            return endTime;
//        }
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
//
//    /*
//    the same function as TrafficDataImporter.java->timeStr2int()
//     */
//    private static int timeStr2int(String tStr){
//        String yearStr = tStr.substring(0,4);
//        String monthStr = tStr.substring(4,6);
//        String dayStr = tStr.substring(6,8);
//        String hourStr = tStr.substring(8,10);
//        String minuteStr = tStr.substring(10, 12);
////        System.out.println(yearStr+" "+monthStr+" "+dayStr+" "+hourStr+" "+minuteStr);
//        int year = Integer.parseInt(yearStr);
//        int month = Integer.parseInt(monthStr)-1;//month count from 0 to 11, no 12
//        int day = Integer.parseInt(dayStr);
//        int hour = Integer.parseInt(hourStr);
//        int minute = Integer.parseInt(minuteStr);
////        System.out.println(year+" "+month+" "+day+" "+hour+" "+minute);
//        Calendar ca= Calendar.getInstance();
//        ca.set(year, month, day, hour, minute, 0); //seconds set to 0
//        long timestamp = ca.getTimeInMillis();
////        System.out.println(timestamp);
//        if(timestamp/1000<Integer.MAX_VALUE){
//            return (int) (timestamp/1000);
//        }else {
//            throw new RuntimeException("timestamp larger than Integer.MAX_VALUE, this should not happen");
//        }
//    }
//
//    private String getFileName(long startTime){
//        String fileName = "TJamData_";
//        long timestamp = (startTime + 1288800000) * 1000;
//        Calendar ca = Calendar.getInstance();
//        ca.setTimeInMillis(timestamp);
//
//        int year = ca.get(Calendar.YEAR);
//        int month = ca.get(Calendar.MONTH) + 1;
//        int day = ca.get(Calendar.DAY_OF_MONTH);
//        int hour = ca.get(Calendar.HOUR_OF_DAY);
//        int minute = ca.get(Calendar.MINUTE);
//
//        String yearStr = Integer.toString(year);
//        String monthStr = Integer.toString(month);
//        if(monthStr.length() == 1)
//            monthStr = "0" + monthStr;
//        String dayStr = Integer.toString(day);
//        if(dayStr.length() == 1)
//            dayStr = "0" + dayStr;
//        String hourStr = Integer.toString(hour);
//        if(hourStr.length() == 1)
//            hourStr = "0" + hourStr;
//        String minuteStr = Integer.toString(minute);
//        if(minuteStr.length() == 1)
//            minuteStr = "0" + minuteStr;
//
//        fileName += yearStr;
//        fileName += monthStr;
//        fileName += dayStr;
//        fileName += hourStr;
//        fileName += minuteStr;
//
//        fileName += ".csv";
//        return fileName;
//    }
//
//}
