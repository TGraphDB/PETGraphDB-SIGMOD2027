package org.act.temporalProperty.meta;

import com.google.common.base.Preconditions;
import org.act.temporalProperty.exception.TPSNHException;
import org.act.temporalProperty.impl.FileBuffer;
import org.act.temporalProperty.impl.FileMetaData;
import org.act.temporalProperty.query.TimePointL;
import org.act.temporalProperty.util.Slice;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

/**
 * Created by song on 2018-01-17.
 */
public class PropertyMetaData {
    private final int propertyId;
    private final ValueContentType type;
    private final TreeMap<TimePointL, FileMetaData> unstableByTime = new TreeMap<>();
    private final TreeMap<TimePointL, FileMetaData> stableByTime = new TreeMap<>();
    //所有的StableFile的元信息
    private final TreeMap<Long, FileMetaData> stableFiles = new TreeMap<>();
    //所有StableFile对应的Buffer
    private final TreeMap<Long, FileBuffer> stableFileBuffers = new TreeMap<>();
    //所有的UnStableFile的元信息
    private final TreeMap<Long, FileMetaData> unStableFiles = new TreeMap<>();
    //所有UnStableFile对应的Buffer
    private final TreeMap<Long, FileBuffer> unStableFileBuffers = new TreeMap<>();
    //    private final TreeMap<Long, FileMetaData> memLogs = new TreeMap<>();

    public transient final LinkedList<String> old2delete = new LinkedList<>(); //abs path

    public PropertyMetaData(int propertyId, ValueContentType type){
        this.propertyId = propertyId;
        this.type = type;
    }

    public Integer getPropertyId() {
        return propertyId;
    }

    public ValueContentType getType() {
        return type;
    }

    public TreeMap<Long, FileMetaData> getStableFiles() {
        return stableFiles;
    }

    public TreeMap<Long, FileMetaData> getUnStableFiles() {
        return unStableFiles;
    }

    public Slice encode() {
        return PropertyMetaDataController.encode(this);
    }

    public long nextStableId(){
        return stableFiles.size();
    }

    public void addUnstable(FileMetaData file) {
        unStableFiles.put(file.getNumber(), file);
        unstableByTime.put(file.getSmallest(), file);
    }

    public void addStable(FileMetaData file) {
        stableFiles.put(file.getNumber(), file);
        stableByTime.put(file.getSmallest(), file);
    }

    public FileMetaData latestStableMeta(){
        Entry<TimePointL, FileMetaData> entry = stableByTime.lastEntry();
        if(entry!=null) return entry.getValue();
        else return null;
    }

    public FileBuffer getUnstableBuffers(long number) {
        return unStableFileBuffers.get(number);
    }

    public FileBuffer getStableBuffers(long number) {
        return stableFileBuffers.get(number);
    }

    public TreeMap<Long, FileBuffer> getUnstableBuffers() {
        return unStableFileBuffers;
    }

    public TreeMap<Long, FileBuffer> getStableBuffers() {
        return stableFileBuffers;
    }

    public void delUnstable(Long fileNumber) {
        FileMetaData meta = unStableFiles.get(fileNumber);
        unstableByTime.remove(meta.getSmallest());
        unStableFiles.remove(meta.getNumber());
    }

    public void delUnstableBuffer(Long fileNumber) {
        unStableFileBuffers.remove(fileNumber);
    }

    public void delStableBuffer(long number) {
        stableFileBuffers.remove(number);
    }

    // returned meta's time is ASC order
    public List<FileMetaData> overlappedStable(TimePointL startTime, TimePointL endTime) {
        TimePointL start = stableByTime.floorKey(startTime);
        if(start!=null) {
            startTime = start;
        }
        return new ArrayList<>(stableByTime.subMap(startTime, true, endTime, true).values());
    }

    /**
     * Return unstable metas whose corresponding file needed to be searched when query value at given `time`
     * the returned meta list, time is ASC order
     * @param time
     */
    public List<FileMetaData> unFloorTime(TimePointL time) {
        return new ArrayList<>(unstableByTime.headMap(time, true).values());
    }

    public Set<Entry<TimePointL, FileMetaData>> unFloorTimeMetaList(TimePointL from, TimePointL to) {
        TimePointL start = unstableByTime.floorKey(from);
        if(start==null) {
            return null;
        }else{
            return unstableByTime.subMap(start, true, to, true).entrySet();
        }
    }

    public Set<Entry<TimePointL, FileMetaData>> stFloorTimeMetaIterator(TimePointL from, TimePointL to) {
        TimePointL start = stableByTime.floorKey(from);
        if(start==null) {
            return null;
        }else{
            return stableByTime.subMap(start, true, to, true).entrySet();
        }
    }

    public TimePointL diskFileMaxTime(){
        if(hasUnstable()) return unMaxTime();
        else if(hasStable()) return stMaxTime();
        else throw new TPSNHException("no disk file!");
    }

    /**
     * 返回StableLevel存储的数据的最晚有效时间
     * @return -1 if no stable file available.
     */
    public TimePointL stMaxTime(){
        if(hasStable()){
            return stableByTime.lastEntry().getValue().getLargest();
        }else{
            throw new TPSNHException("no stable file available!");
        }
    }

    public TimePointL unMaxTime() {
        if(hasUnstable()){
            return unstableByTime.lastEntry().getValue().getLargest();
        }else{
            throw new TPSNHException("no unstable files!");
        }
    }

    /**
     * @param time is usually larger than stMaxTime.
     * @return the stable meta which contains the time.
     */
    public FileMetaData getStContainsTime(TimePointL time) {
        assert hasStable():"no stable file!";
        TimePointL start = stableByTime.floorKey(time);
        Preconditions.checkNotNull(start, new TPSNHException("should have 0<=time but get null"));
        return stableByTime.get(start);
    }

    public boolean hasStable(){
        return !stableFiles.isEmpty();
    }

    public boolean hasUnstable(){
        return !unStableFiles.isEmpty();
    }

    public boolean hasDiskFile(){
        return hasUnstable() || hasStable();
    }

    public void addUnstableBuffer(long number, FileBuffer buffer) {
        unStableFileBuffers.put(number, buffer);
    }

    public void addStableBuffer(long number, FileBuffer buffer) {
        stableFileBuffers.put(number, buffer);
    }

    @Override
    public String toString() {
        return "PropertyMetaData{" +
                "propertyId=" + propertyId +
                ", type=" + type +
                ", stableFiles=" + stableFiles +
                ", stableFileBuffers=" + stableFileBuffers +
                ", unStableFiles=" + unStableFiles +
                ", unStableFileBuffers=" + unStableFileBuffers +
                '}';
    }

    public Collection<FileBuffer> overlappedBuffers(TimePointL startTime, TimePointL endTime )
    {
        List<FileBuffer> result = new ArrayList<>();
        if(hasStable())
        {
            for(Entry<TimePointL, FileMetaData> entry : stableByTime.tailMap(startTime, true).entrySet())
            {
                FileBuffer buffer = getStableBuffers( entry.getValue().getNumber() );
                if(buffer!=null) result.add(buffer);
            }
        }
        if(hasUnstable())
        {
            for(Entry<TimePointL, FileMetaData> entry : unstableByTime.headMap(endTime, true).entrySet())
            {
                FileBuffer buffer = getUnstableBuffers( entry.getValue().getNumber() );
                if(buffer!=null) result.add(buffer);
            }
        }
        return result;
    }
}
