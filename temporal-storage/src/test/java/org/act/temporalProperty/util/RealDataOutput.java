package org.act.temporalProperty.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;

public class RealDataOutput {

    private static Logger log = LoggerFactory.getLogger(TrafficDataImporter.class);

    private List<File> dataFileList;
    private final Map<String, Long> roadIdMap = new HashMap<>();
    private int minTime;
    private int maxTime;

    private long writeTime = 0;
    private long writeCount = 0;

    public RealDataOutput( List<File> dataFileList, int inputFileCount) throws IOException {
        this.dataFileList = dataFileList;
        this.inputData(inputFileCount);
    }

    public Map<String, Long> getRoadIdMap(){
        return roadIdMap;
    }

    public int getMinTime(){
        return minTime;
    }

    public int getMaxTime(){
        return maxTime;
    }

    public long getWriteTime() { return writeTime; }

    public long getWriteCount() { return writeCount; }

    private void inputData(int inputFileCount) throws IOException {
        Random random = new Random();
        try(BufferedWriter w = new BufferedWriter(new FileWriter("temporal.csv"))) {
            for (int i = 0; i < dataFileList.size() && i < inputFileCount; i++) {
                File file = dataFileList.get(i);
                int time = timeStr2int(file.getName().substring(9, 21)) - 1288800000;
                if (minTime > time) minTime = time;
                if (maxTime < time) maxTime = time;

                try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                    String line = br.readLine();
                    while ((line = br.readLine()) != null) {
                        if(random.nextInt(100)<6) {
                            w.write(input(time, line));
                            writeCount++;
                        }
                    }
                    br.close();
                }
                // if(i%10==0) log.info("input {} files, current {}, time {}", i, file.getName(), time);
            }
            //  log.info("input files done, {} roads", this.roadIdMap.size());
        }
    }

    private String input(int time, String line) {
        String[] fields = line.split(",");
//        int index = Integer.valueOf(fields[0]);

        String gridId = fields[1];
        String chainId = fields[2];
//        int ignore = Integer.valueOf(fields[3]);
//        type = Integer.valueOf(fields[4]);
//        length = Integer.valueOf(fields[5]);
        int travelTime = Integer.valueOf(fields[6]);
        int fullStatus = Integer.valueOf(fields[7]);
        int vehicleCount = Integer.valueOf(fields[8]);
        int segmentCount = Integer.valueOf(fields[9]);

        long startTime = System.currentTimeMillis();

        long roadId = getId(gridId, chainId);
//        log.debug("eid({}), time({}), travelTime({})", roadId, time, travelTime);

//        setIntProperty(store, time, roadId, 1, travelTime);
//        setIntProperty(store, time, roadId, 2, fullStatus);
//        setIntProperty(store, time, roadId, 3, vehicleCount);
//        setIntProperty(store, time, roadId, 4, segmentCount);

        long endTime = System.currentTimeMillis();
        writeTime += endTime - startTime;
        return time+","+travelTime+","+fullStatus+"\n";
    }


    private long getId(String gridId, String chainId) {
        String strKey = gridId+":"+chainId;
        if(roadIdMap.containsKey(strKey)){
            return roadIdMap.get(strKey);
        }else{
            long nextId = roadIdMap.size();
            roadIdMap.put(strKey, nextId);
            return nextId;
        }
    }

    private static int timeStr2int(String tStr){
        String yearStr = tStr.substring(0,4);
        String monthStr = tStr.substring(4,6);
        String dayStr = tStr.substring(6,8);
        String hourStr = tStr.substring(8,10);
        String minuteStr = tStr.substring(10, 12);
//        System.out.println(yearStr+" "+monthStr+" "+dayStr+" "+hourStr+" "+minuteStr);
        int year = Integer.parseInt(yearStr);
        int month = Integer.parseInt(monthStr)-1;//month count from 0 to 11, no 12
        int day = Integer.parseInt(dayStr);
        int hour = Integer.parseInt(hourStr);
        int minute = Integer.parseInt(minuteStr);
//        System.out.println(year+" "+month+" "+day+" "+hour+" "+minute);
        Calendar ca= Calendar.getInstance();
        ca.set(year, month, day, hour, minute, 0); //seconds set to 0
        long timestamp = ca.getTimeInMillis();
//        System.out.println(timestamp);
        if(timestamp/1000<Integer.MAX_VALUE){
            return (int) (timestamp/1000);
        }else {
            throw new RuntimeException("timestamp larger than Integer.MAX_VALUE, this should not happen");
        }
    }
    public static void main(String[] args) throws IOException {
        DataFileImporter d = new DataFileImporter(280);
        RealDataOutput r = new RealDataOutput(d.getDataFileList(), 400);

    }
}
