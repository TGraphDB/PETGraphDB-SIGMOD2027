//package org.act.temporalProperty.impl.index;
//
//import com.google.common.collect.HashBasedTable;
//import com.google.common.collect.Table;
//import org.act.temporalProperty.index.value.rtree.IndexEntry;
//import org.act.temporalProperty.util.Slice;
//import org.apache.commons.lang3.SystemUtils;
//
//import java.io.BufferedReader;
//import java.io.File;
//import java.io.FileReader;
//import java.io.IOException;
//import java.nio.ByteBuffer;
//import java.nio.ByteOrder;
//import java.util.ArrayList;
//import java.util.Calendar;
//import java.util.Comparator;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//import java.util.Set;
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
//
//    // traffic data dir path
//    private String dataPath;
//    private final File dataDir;
//
//    // ---%--the files filtered by (startTime, endTime)
//    //private List<File> dataFileList;
//    private List<File> totalFileList;
//
//    private Map<String, Long> entityIdMap;
//
//    //private Map<Integer, ArrayList<IndexEntry>> entryMap;
//
//    private final int inputFileCount;
//
//    List<IndexEntry> entryList;
//
//    public SourceCompare(String dataPath, int inputFileCount) {
//        this.dataPath = dataPath;
//        this.dataDir = new File(dataPath);
//        //this.dataFileList = new ArrayList<File>();
//        //this.entryMap = new HashMap<Integer, ArrayList<IndexEntry>>();
//        this.entityIdMap = new HashMap<String, Long>();
//        this.totalFileList = new ArrayList<File>();
//        this.inputFileCount = inputFileCount;
//        this.entryList = new ArrayList<IndexEntry>();
//    }
//
//    public List<IndexEntry> queryBySource(int timeMin, int timeMax, int valueMin, int valueMax) {
//
//        int ret1 = importFiles();
//        if(ret1 < 0)
//            return null;
//
//        return filterEntry(timeMin, timeMax, valueMin, valueMax);
//    }
//
//    // According to the (startTime, endTime; inputFileCount) to filter files in dataDir
//    /* Read (startTime, endTime, [propertyId]; entityId, propertyValue) into a array (iterator); List<List<entry>()> --- each file one list;
//     *  use propertyValue to filter; HashMap<travelTime, EntryList>
//     */
//
//    private int importFiles() {
//
//        if (!this.dataDir.isDirectory()) {
//            System.out.println("SoureCompare-importFiles: dataDir = %s is not a Directory.");
//            return -1;
//        }
//
//        for (File file : this.dataDir.listFiles()) {
//            if (file.isFile() && file.getName().startsWith("TJamData_201") && file.getName().endsWith(".csv")) {
//                totalFileList.add(file);
//            }
//        }
//        totalFileList.sort(Comparator.comparing(File::getName));
//
//        return 0;
//    }
//
//    private List<IndexEntry> filterEntry(int startTime, int endTime, int valueMin, int valueMax){
//
//        int fileCount = Math.min(totalFileList.size(), inputFileCount);
//
//        for (int i = 0; i < fileCount; i++) {
//            File file = totalFileList.get(i);
//            int sTime = timeStr2int(file.getName().substring(9, 21)) - 1288800000;
//
//            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
//
//                String line = br.readLine();
//                while ((line = br.readLine()) != null) {
//                    String[] fields = line.split(",");
//
//                    String gridId = fields[1];
//                    String chainId = fields[2];
//                    Long entityId = getEntityId(gridId, chainId);
//
//                    int travelTime = Integer.valueOf(fields[6]);
//
//                   // if((travelTime >= valueMin) && (travelTime <= valueMax)) {
//
//                        //int eTime = getEndTime(i + 1, endTime);
//                        Slice value = new Slice(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(travelTime).array());
//
//                        entryList.add(new IndexEntry(entityId, sTime, endTime, new Slice[]{value}));
//
//                   // }
//
//                }
//            } catch (IOException e) {
//                System.out.println("FileReader: NullIOException.");
//            }
//        }
//
//        return mergeEntryList(startTime, endTime, valueMin, valueMax);
//    }
//
//    private List<IndexEntry> mergeEntryList(int startTime, int endTime, int valueMin, int valueMax) {
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
//                    Slice value = entity.getValue(0);
//                    int eTime = 0;
//                    int travelTime = value.getInt(0);
//
//                    while(j < entityIdList.size()) {
//
//                        if(j + 1 == entityIdList.size()) {
//                            eTime = entity.getEnd();
//
//                            if((travelTime >= valueMin) && (travelTime <= valueMax) &&
//                                    (sTime <= endTime) && (eTime >= startTime)) {
//                                entity = new IndexEntry(entityId, sTime, eTime, new Slice[]{value});
//                                mergeList.add(entity);
//                            }
//
//                            j++;
//                            break;
//
//                        } else if(!entityIdList.get(j).getValue(0).equals(entityIdList.get(j + 1).getValue(0))){
//                            eTime = entityIdList.get(j + 1).getStart() - 1;
//
//                            if((travelTime >= valueMin) && (travelTime <= valueMax) &&
//                                    (sTime <= endTime) && (eTime >= startTime)) {
//                                entity = new IndexEntry(entityId, sTime, eTime, new Slice[]{value});
//                                mergeList.add(entity);
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
//       // entryList.clear();
//       // entryList = null;
//
//        return mergeList;
//    }
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
//    private Table<IndexEntry, String, String> queryFromSource( Long entityId, List<IndexEntry> sourceEntities ) {
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
//        } catch (IOException e) {
//            System.out.println("SourceCompare -- queryByFileName");
//        }
//
//        return sourceString;
//    }
//
//    private int getEndTime(int fileNum, int endTime){
//        if(fileNum < totalFileList.size()) {
//            File file = totalFileList.get(fileNum);
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
