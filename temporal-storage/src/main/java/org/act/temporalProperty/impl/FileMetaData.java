package org.act.temporalProperty.impl;

import com.google.common.base.Function;
import org.act.temporalProperty.query.TimePointL;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 存储文件元信息，包括文件名，负责存储的有效时间段等
 *
 */
public class FileMetaData
{
    public static final Function<FileMetaData, TimePointL> GET_LARGEST_USER_KEY = new Function<FileMetaData, TimePointL>()
    {
        @Override
        public TimePointL apply(FileMetaData fileMetaData)
        {
            return fileMetaData.getLargest();
        }
    };

    /**
     * 文件编码，起文件名作用。
     */
    private final long number;

    private final int version;

    /**
     * 文件大小，以byte为单位
     */
    private transient final long fileSize;

    /**
     * 负责存储有效时间的起始时间
     */
    private final TimePointL smallest;

    /**
     * 负责存储有效时间的结束席间
     */
    private final TimePointL largest;
    // todo this mutable state should be moved elsewhere
    private transient final AtomicInteger allowedSeeks = new AtomicInteger(1 << 30);

    /**
     * 实例化
     * @param number 文件编号
     * @param fileSize 文件大小
     * @param smallest 有效时间的起始时间
     * @param largest 有效时间的结束时间
     */
    public FileMetaData(long number, long fileSize, TimePointL smallest, TimePointL largest)
    {
        this.number = number;
        this.fileSize = fileSize;
        this.smallest = smallest;
        this.largest = largest;
        this.version = 0;
    }

    public FileMetaData(long number, long fileSize, TimePointL smallest, TimePointL largest, int version)
    {
        this.number = number;
        this.fileSize = fileSize;
        this.smallest = smallest;
        this.largest = largest;
        this.version = version;
    }

    public long getFileSize()
    {
        return fileSize;
    }

    public long getNumber()
    {
        return number;
    }

    public int getVersion()
    {
        return version;
    }

    public TimePointL getSmallest()
    {
        return smallest;
    }

    public TimePointL getLargest()
    {
        return largest;
    }

    public int getAllowedSeeks()
    {
        return allowedSeeks.get();
    }

    public void setAllowedSeeks(int allowedSeeks)
    {
        this.allowedSeeks.set(allowedSeeks);
    }

    public void decrementAllowedSeeks()
    {
        allowedSeeks.getAndDecrement();
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("FileMetaData");
        sb.append("{(").append(number);
        sb.append(")v").append(version);
        sb.append(",").append(smallest);
        sb.append("~").append(largest);
        long fSize = fileSize / 1024 / 1024;
        sb.append(",").append(fSize==0 ? "<1" : fSize);
        sb.append("MB}");
        return sb.toString();
    }
}
