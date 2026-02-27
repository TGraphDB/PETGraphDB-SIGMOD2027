package org.act.temporalProperty.yangfan;

import org.act.temporalProperty.index.value.rtree.IndexEntry;
import org.act.temporalProperty.query.TimePointL;
import org.act.temporalProperty.util.Slice;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * Created by Fan Yang on 2018-5-29, last updated on 2018-5-29
 *
 * class description:
 * This class is same as the DataFileImporter.java in package util.
 * It aims at importing data from source Data Directory.
 *
 * class usage:
 * 1. DataFileImporter dataFileImporter = new DataFileImporter();
 * 2. dataFileImporter.prepareData();
 * 3. return Source Data File List: List<File> dataFileList = dataFileImporter.getDataFileList();
 * 4. return Source Data list (IndexEntry): read one line by one line.
 */

public class dataFileImporter {

    private static Logger log = LoggerFactory.getLogger(dataFileImporter.class);
    public static final int NOW_TIME = 0x40000000; //2^30

    private List<File> dataFileList = new ArrayList<>();
    private List<IndexEntry> entryList = new ArrayList<>();
    private Map<String, Long> entityIdMap = new HashMap<>();
    private String dataPath;
    private String dbDir;
    int inputFileCount;

    public dataFileImporter(int inputFileCount){

        setDataPath();
        setDbDir();

        this.inputFileCount = inputFileCount;
    }

    public void prepareData() {
        importDataFiles(new File(dataPath));
        dataFileList.sort(Comparator.comparing(File::getName));

        importFileData();
        entryList.sort(Comparator.comparing(IndexEntry::getEntityId));
    }

    private void importDataFiles(File dataDir) {

        if (!dataDir.isDirectory()) return;

        for (File file : dataDir.listFiles()) {
            if (file.isFile() && file.getName().startsWith("TJamData_201") && file.getName().endsWith(".csv")) {
                dataFileList.add(file);
            } else if (file.isDirectory()) {
                importDataFiles(file);
            }
        }
    }

    private void importFileData(){

        int fileNum = Math.min(inputFileCount, dataFileList.size());
        for (int i = 0; i < fileNum; i++) {
            File file = dataFileList.get(i);
            int startTime = timeStr2int(file.getName().substring(9, 21)) - 1288800000;

            fileInput(file, startTime);
        }
    }

    private void fileInput(File file, int startTime) {

        BufferedReader br = null;

        try {

            br = new BufferedReader(new FileReader(file));
            String line = br.readLine();

            while ((line = br.readLine()) != null) {
                readDataLine(line, startTime);
            }

            br.close();
            br = null;

        }catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void readDataLine(String line, int startTime) {

        String[] fields = line.split(",");

        String gridId = fields[1];
        String chainId = fields[2];

        Long entityId = getEntityId(gridId, chainId);

        Slice travelTime = new Slice(4);
        travelTime.setInt(0, Integer.valueOf(fields[6]));

        Slice fullStatus = new Slice(4);
        fullStatus.setInt(0, Integer.valueOf(fields[7]));

        Slice vehicleCount = new Slice(4);
        vehicleCount.setInt(0, Integer.valueOf(fields[8]));

        Slice segmentCount = new Slice(4);
        segmentCount.setInt(0, Integer.valueOf(fields[9]));

        entryList.add(new IndexEntry(entityId, new TimePointL(startTime), new TimePointL(NOW_TIME),
                new Slice[]{travelTime, fullStatus, vehicleCount, segmentCount}));
    }

    private Long getEntityId(String gridId, String chainId) {

        String key = gridId + ":" + chainId;

        if (entityIdMap.containsKey(key)) {

            return entityIdMap.get(key);
        } else {

            long value = entityIdMap.size();
            entityIdMap.put(key, value);
            return value;
        }
    }

    /**
     * the same function as TrafficDataImporter.java->timeStr2int()
     */
    private static int timeStr2int(String tStr){
        String yearStr = tStr.substring(0,4);
        String monthStr = tStr.substring(4,6);
        String dayStr = tStr.substring(6,8);
        String hourStr = tStr.substring(8,10);
        String minuteStr = tStr.substring(10, 12);

        int year = Integer.parseInt(yearStr);
        int month = Integer.parseInt(monthStr)-1;//month count from 0 to 11, no 12
        int day = Integer.parseInt(dayStr);
        int hour = Integer.parseInt(hourStr);
        int minute = Integer.parseInt(minuteStr);

        Calendar ca= Calendar.getInstance();
        ca.set(year, month, day, hour, minute, 0); //seconds set to 0
        long timestamp = ca.getTimeInMillis();

        if(timestamp/1000<Integer.MAX_VALUE){
            return (int) (timestamp/1000);
        }else {
            throw new RuntimeException("timestamp larger than Integer.MAX_VALUE, this should not happen");
        }
    }


    private void setDataPath() {
        if(SystemUtils.IS_OS_WINDOWS){
            this.dataPath = "C:\\Users\\Administrator\\Desktop\\TGraph-source\\20101104.tar\\20101104";
//            dataPath = "D:\\songjh\\projects\\TGraph\\test-traffic-data\\20101105";
        }else{
            this.dataPath = "/home/song/tmp/road data/20101104";
        }
    }

    private void setDbDir() {
        if(SystemUtils.IS_OS_WINDOWS){
            this.dbDir = "temporal.property.test";
        }else{
            this.dbDir = "/tmp/temporal.property.test";
        }
    }

    public String getDataPath() {return this.dataPath; }

    public String getDbDir() {return this.dbDir; }

    public List<File> getDataFileList() {return dataFileList; }

    public List<IndexEntry> getEntryList() {return entryList; }
}
