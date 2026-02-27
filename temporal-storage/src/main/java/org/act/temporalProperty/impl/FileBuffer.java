package org.act.temporalProperty.impl;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.act.temporalProperty.TemporalPropertyStore;
import org.act.temporalProperty.query.TimeIntervalKey;
import org.act.temporalProperty.table.UnSortedTable;
import org.act.temporalProperty.util.Slice;
import org.act.temporalProperty.util.SliceInput;
import org.act.temporalProperty.util.SliceOutput;

import static org.act.temporalProperty.impl.TemporalPropertyStoreImpl.BULK_MODE;

/**
 * 是系统处理对StableFile或UnStableFile插入操作的机制，对应设计文档中的Buffer,包括了内存中的结果和对应的备份文件。每一个FileBuffer固定对应一个StableFile或UnStableFile。
 *
 */
public class FileBuffer implements Closeable
{
    private MemTable memTable; //内存中保存数据
    private UnSortedTable discTable;//对应磁盘中的备份文件
    private long number =-1;
    private int version = 0;
    private String fName;

    public FileBuffer(long id){
        this.number = id;
        this.version = 0;
    }

    public FileBuffer(long id, int version){
        this.number = id;
        this.version = version;
    }

    public FileBuffer(File unSortedTableFile, long id, int version) throws IOException{
        this(id, version);
        this.fName = unSortedTableFile.getAbsolutePath();
        this.memTable = new MemTable();
        Files.deleteIfExists(unSortedTableFile.toPath());
        Files.createFile(unSortedTableFile.toPath());
        this.discTable = new UnSortedTable(unSortedTableFile);
    }

    public void init(File bufLogFile ) throws IOException{
        this.fName = bufLogFile.getAbsolutePath();
        this.memTable = new MemTable();
        this.discTable = new UnSortedTable(bufLogFile, this.memTable);
    }


    /**
     * 向Buffer中插入一个数据项
     * @param key 数据项的key
     * @param value 值
     * @throws IOException
     */
    public void add( TimeIntervalKey key, Slice value ) throws IOException{
        if(discTable==null || memTable==null ){
            throw new IOException("should init first!");
        }
        this.discTable.add( key, value );
        if(!BULK_MODE) this.memTable.addInterval( key, value );
    }

    public void force() throws IOException{
        discTable.addCheckPoint();
    }

    /**
     * 返回用于访问的iterator
     * @return
     */
    public SearchableIterator iterator()
    {
        if(BULK_MODE) {
            this.memTable = new MemTable();
            this.discTable.bulkLoadFromFile(this.memTable);
        }
        return this.memTable.iterator();
    }

    /**
     * 当Buffer参与任何合并过程之后，需要将其删除。在删除之前需调用close()方法。
     */
    @Override
    public void close() throws IOException
    {
        this.memTable = null;
        this.discTable.close();
    }

    public long size(){
        return this.memTable.approximateMemUsage();
    }

    public long getNumber() {
        return number;
    }

    public int getVersion() {
        return version;
    }

    public String getFilePath() {
        return fName;
    }

    @Override
    public String toString() {
        return "FileBuffer{" +
                "number=" + number +
                ", fName='" + fName + '\'' +
                ", memtable=" + memTable +
                '}';
    }

    public void encode(SliceOutput out) {
        out.writeLong(number);
        out.writeInt(version);
    }

    public static FileBuffer decode(SliceInput in, int version) {
        long id = in.readLong();
        int fVersion = in.readInt();
        if(version == TemporalPropertyStore.Version){
            return new FileBuffer(id, fVersion);
        }else {
            return new FileBuffer(id);
        }
    }

    public MemTable getMemTable()
    {
        return memTable;
    }
}
