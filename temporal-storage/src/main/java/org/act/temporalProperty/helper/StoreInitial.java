package org.act.temporalProperty.helper;

import org.act.temporalProperty.exception.TPSMetaLoadFailedException;
import org.act.temporalProperty.exception.TPSRuntimeException;
import org.act.temporalProperty.impl.Filename;
import org.act.temporalProperty.impl.LogReader;
import org.act.temporalProperty.impl.MemTable;
import org.act.temporalProperty.vo.TimeIntervalValueEntry;
import org.act.temporalProperty.meta.SystemMeta;
import org.act.temporalProperty.meta.SystemMetaController;
import org.act.temporalProperty.meta.SystemMetaFile;
import org.act.temporalProperty.table.*;
import org.act.temporalProperty.util.Slice;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by song on 2018-01-17.
 */
public class StoreInitial {
    private final String metaName = "meta.info";
    private final String metaTmpName = "meta.info.tmp";
    private final File rootDir;

    public StoreInitial(File rootDir){
        this.rootDir = rootDir;
    }

    public FileReader init() throws IOException {
        if(!rootDir.exists()) Files.createDirectory(rootDir.toPath());
        File runLockFile = new File(rootDir, Filename.lockFileName());
        if(runLockFile.exists()){
            if ( !runLockFile.delete() )
            {
                throw new IOException( "is.running.lock is locked, may be another storage instance running on " + rootDir.getAbsolutePath() + "?" );
            }
        }
        Files.createFile(runLockFile.toPath());
        return new FileReader(runLockFile);
    }

    private SystemMeta findAndLoadMeta(File rootDir) throws TPSMetaLoadFailedException {
        if(rootDir!=null && rootDir.isDirectory()) {
            String[] files = rootDir.list();
            if(files!=null && files.length==0) return null;
            if(files!=null) {
                Set<String> fileSet = new HashSet<>(Arrays.asList(files));
                SystemMetaFile metaFile=null, metaTmpFile=null;
                if(fileSet.contains(metaName)) {
                    metaFile = SystemMetaController.readFromDisk(new File(rootDir, metaName));
                }
                if(fileSet.contains(metaTmpName)) {
                    metaTmpFile = SystemMetaController.readFromDisk(new File(rootDir, metaTmpName));
                }
                if(metaFile!=null && metaTmpFile!=null){
                    if(metaTmpFile.getTime()>metaFile.getTime()){
                        return SystemMetaController.decode(metaTmpFile.getMeta(), metaTmpFile.getVersion());
                    }else{
                        return SystemMetaController.decode(metaFile.getMeta(), metaFile.getVersion());
                    }
                }else if(metaFile!=null && metaTmpFile==null){
                    return SystemMetaController.decode(metaFile.getMeta(), metaFile.getVersion());
                }else if(metaFile==null && metaTmpFile!=null){
                    return SystemMetaController.decode(metaTmpFile.getMeta(), metaTmpFile.getVersion());
                }else{//metaFile==null && metaTmpFile==null
                    //throw new TPSMetaLoadFailedException("has meta file but both read failed");
                    return null;
                }
            }else{
                throw new TPSMetaLoadFailedException("failed to list dir: "+rootDir.getAbsolutePath());
            }
        }
        return null;
    }

    public SystemMeta getMetaInfo() throws TPSMetaLoadFailedException {
        SystemMeta meta = findAndLoadMeta(rootDir);
        if(meta==null){
            return new SystemMeta();
        }else{

            return meta;
        }
    }

    public MemTable getMemTable() {
        try{
            String tempFileName = Filename.tempFileName(0);
            File tempFile = new File( this.rootDir + "/" + tempFileName );

            MemTable memTable = new MemTable();
            if( tempFile.exists()){
                if((tempFile.length() >= Footer.ENCODED_LENGTH)) {
                    FileInputStream inputStream = new FileInputStream(tempFile);
                    FileChannel channel = inputStream.getChannel();
                    LogReader reader = new LogReader(channel, null, false, 0);
                    Slice rawEntry;
                    while((rawEntry = reader.readRecord())!=null)
                    {
                        TimeIntervalValueEntry entry = TimeIntervalValueEntry.decode( rawEntry.input() );
                        memTable.addInterval( entry.getKey(), entry.getValue() );
                    }
                    channel.close();
                    inputStream.close();
                }
                Files.delete(tempFile.toPath());
            }
            return memTable;
        }catch( IOException e ){
            throw new TPSRuntimeException( "Restore MemTable Failed!", e );
        }
    }

}
