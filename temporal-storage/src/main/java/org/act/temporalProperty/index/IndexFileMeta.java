package org.act.temporalProperty.index;

import org.act.temporalProperty.query.TimePointL;
import org.act.temporalProperty.util.SliceInput;
import org.act.temporalProperty.util.SliceOutput;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

/**
 * Created by song on 2018-05-06.
 */
public class IndexFileMeta
{
    private long indexId;
    private long fileId;
    private long fileSize;
    private TimePointL startTime;
    private TimePointL endTime;

    //corresponding storage file properties, for single-property time value index and aggregation index.
    private long corFileId;
    private boolean corIsStable;

    //time group start point for aggregation index only. the last point is endTime + 1
    private TreeSet<TimePointL> timeGroup;

    public IndexFileMeta(long indexId, long fileId, long fileSize, TimePointL startTime, TimePointL endTime, long corFileId, Boolean corIsStable, Collection<TimePointL> timeGroup )
    {
        this.indexId = indexId;
        this.fileId = fileId;
        this.fileSize = fileSize;
        this.startTime = startTime;
        this.endTime = endTime;
        this.corFileId = corFileId;
        this.corIsStable = corIsStable;
        this.timeGroup = new TreeSet<>(timeGroup);
    }

    public IndexFileMeta() {
    }

    public long getIndexId()
    {
        return indexId;
    }

    public long getFileId()
    {
        return fileId;
    }

    public long getFileSize()
    {
        return fileSize;
    }

    public TimePointL getStartTime()
    {
        return startTime;
    }

    public TimePointL getEndTime()
    {
        return endTime;
    }

    public long getCorFileId()
    {
        return corFileId;
    }

    public boolean isCorIsStable()
    {
        return corIsStable;
    }

    public void setIndexId(long indexId) {
        this.indexId = indexId;
    }

    public void setFileId(long fileId) {
        this.fileId = fileId;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public void setStartTime(TimePointL startTime) {
        this.startTime = startTime;
    }

    public void setEndTime(TimePointL endTime) {
        this.endTime = endTime;
    }

    public void setCorFileId(long corFileId) {
        this.corFileId = corFileId;
    }

    public void setCorIsStable(boolean corIsStable) {
        this.corIsStable = corIsStable;
    }

    public TreeSet<TimePointL> getTimeGroup() {
        return timeGroup;
    }

    public void setTimeGroup(Collection<TimePointL> timeGroup) {
        this.timeGroup = new TreeSet<>(timeGroup);
    }

    public IndexFileMeta(SliceInput in){
        this.indexId = in.readLong();
        this.fileId = in.readLong();
        this.fileSize = in.readLong();
        this.startTime = TimePointL.decode(in);
        this.endTime = TimePointL.decode(in);
        this.corFileId = in.readLong();
        this.corIsStable = in.readBoolean();
        this.timeGroup = new TreeSet<>();
        int cnt = in.readInt();
        for(int i=0; i<cnt; i++){
            timeGroup.add(TimePointL.decode(in));
        }
    }

    public void encode(SliceOutput out){
        out.writeLong(indexId);
        out.writeLong(fileId);
        out.writeLong(fileSize);
        startTime.encode(out);
        endTime.encode(out);
        out.writeLong(corFileId);
        out.writeBoolean(corIsStable);
        out.writeInt(timeGroup.size());
        for(TimePointL t : timeGroup){
            t.encode(out);
        }
    }
}
