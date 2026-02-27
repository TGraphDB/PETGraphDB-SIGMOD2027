package org.act.temporalProperty.impl.index;

import org.act.temporalProperty.TemporalPropertyStore;
import org.act.temporalProperty.util.DataFileImporter;
import org.act.temporalProperty.util.StoreBuilder;
import org.act.temporalProperty.util.TrafficDataImporter;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

public class PerformanceTest {
    private static Logger log = LoggerFactory.getLogger(PerformanceTest.class);

    private static DataFileImporter dataFileImporter;
    private static String dbDir;
    private static String dataPath;
    private static List<File> dataFileList;

    private StoreBuilder stBuilder;
    private static TrafficDataImporter importer;
    private static TemporalPropertyStore store;

    private long writeTime = 0;
    private long writeCount = 0;
    private int fileCount = 0;

    public PerformanceTest(int fileCount) {
        this.fileCount = fileCount;
    }

    public long getWriteTime() { return writeTime; }

    public long getWriteCount() { return writeCount; }

    public int getFileCount() { return fileCount; }

    public void init() throws Throwable {
        dataFileImporter = new DataFileImporter(280);
        dbDir = dataFileImporter.getDbDir();
        dataPath = dataFileImporter.getDataPath();
        dataFileList = dataFileImporter.getDataFileList();

        stBuilder = new StoreBuilder(dbDir, true);
        store = stBuilder.store();

        importer = new TrafficDataImporter(store, dataFileList, fileCount);
    }

    public long getFlushMemtableTime() {
        long startTime, endTime;

        startTime = System.currentTimeMillis();
        store.flushMemTable2Disk();
        endTime = System.currentTimeMillis();

        return (endTime - startTime);
    }


    @Test
    public void writeTest() throws Throwable {

       // long flushTime = 0;

        init();

        writeTime = importer.getWriteTime();
        writeCount = importer.getWriteCount();
        //flushTime = getFlushMemtableTime(); //flush operation may influence the performance of write (flush the DRAM-cache)

        log.info("inputFileCount = {}, writeCount = {}, writeTime = {}", fileCount, writeCount, writeTime);

        store.shutDown();
    }
}
