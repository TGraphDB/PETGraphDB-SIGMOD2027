package org.act.temporalProperty.util;

//import org.act.temporalProperty.config.DataDownloader;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class DataFileImporter {
//    private static Logger log = LoggerFactory.getLogger(BuildAndQueryTest.class);

    private List<File> dataFileList;
    private String dataPath;
    private String dbDir;
    private File dataDir;

    public DataFileImporter(int fileCount) throws IOException {
        throw new UnsupportedOperationException("data downloader need refactor");
//        this.dataPath = TestConfiguration.get().testDataDir();
//        this.dbDir = TestConfiguration.get().dbDir();
//        this.dataFileList = new ArrayList<>();
//        this.dataDir = new File(dataPath);
//        importDataFiles(dataDir);
//        if(dataFileList.isEmpty()){
////            DataDownloader down = new DataDownloader();
////            dataFileList = down.download(fileCount);
//        }
//        dataFileList.sort(Comparator.comparing(File::getName));
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

    public String getDataPath() {return this.dataPath; }

    public String getDbDir() {return this.dbDir; }

    public List<File> getDataFileList() {return dataFileList; }
}
