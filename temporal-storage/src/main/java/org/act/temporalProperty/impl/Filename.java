package org.act.temporalProperty.impl;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import org.act.temporalProperty.query.aggr.AggregationQuery;

import java.io.File;
import java.io.IOException;
import java.util.List;
/**
 * 将文件编号转化为文件名的工具类
 *
 */
public final class Filename
{
    private Filename()
    {
    }

    /**
     * 各种文件的类型
     */
    public enum FileType
    {
        STBUFFER,
        BUFFER,
        LOG,
        DB_LOCK,
        STABLEFILE,
        UNSTABLEFILE,
        DESCRIPTOR,
        CURRENT,
        TEMP,
        INFO_LOG  // Either the current one, or an old one
    }

    public static String stbufferFileName( long number, int version)
    {
        return makeFileName( number, version,"st", "buffer" );
    }
    
    public static String unbufferFileName(long number, int version)
    {
        return makeFileName( number, version,"un", "buffer" );
    }

    /**
     * 返回对应编号的StableFile文件的名称.
     */
    public static String stableFileName(long number, int version)
    {
        return makeFileName(number, version, "st", "table");
    }

    public static String stPath(File proDir, long fileNumber, int version) {
        AggregationQuery.cnt[0]++;
        return new File(proDir, stableFileName(fileNumber, version)).getAbsolutePath();
    }
    
    /**
     * 返回对应编号的UnStableFile文件的名称
     */
    public static String unStableFileName(long number, int version)
    {
        return makeFileName( number, version, "un", "table" );
    }

    public static String unPath(File proDir, long fileNumber, int version) {
        return new File(proDir, unStableFileName(fileNumber, version)).getAbsolutePath();
    }

    public static String valIndexFileName(long fileId) {
        return makeFileName(fileId, 0, "value", "index");
    }

    public static String aggrIndexFileName(long fileId) {
        return makeFileName(fileId, 0, "aggr", "index");
    }

    /**
     * 返回锁文件的名称
     */
    public static String lockFileName()
    {
        return "IS.RUNNING.LOCK";
    }

    /**
     * 返回临时文件的名称
     */
    public static String tempFileName(long number)
    {
        return makeFileName(number, "dbtmp");
    }

    /**
     * If filename is a leveldb file, store the type of the file in *type.
     * The number encoded in the filename is stored in *number.  If the
     * filename was successfully parsed, returns true.  Else return false.
     * // Owned filenames have the form:
     *         //    dbname/CURRENT
     *         //    dbname/LOCK
     *         //    dbname/LOG
     *         //    dbname/LOG.old
     *         //    dbname/MANIFEST-[0-9]+
     *         //    dbname/[0-9]+.(log|sst|dbtmp)
     */
//    public static FileInfo parseFileName(File file)

    /**
     * Make the CURRENT file point to the descriptor file with the
     * specified number.
     *
     * @return true if successful; false otherwise
     */
//    public static boolean setCurrentFile(File databaseDir, long descriptorNumber)

    public static List<File> listFiles(File dir)
    {
        File[] files = dir.listFiles();
        if (files == null) {
            return ImmutableList.of();
        }
        return ImmutableList.copyOf(files);
    }

    private static String makeFileName(long number, String suffix)
    {
        Preconditions.checkArgument(number >= 0, "number is negative");
        Preconditions.checkNotNull(suffix, "suffix is null");
        return String.format("%06d.%s", number, suffix);
    }

    private static String makeFileName(long number, int version, String prefix, String suffix)
    {
        Preconditions.checkArgument(number >= 0, "number is negative");
        Preconditions.checkNotNull(suffix, "suffix is null");
        if(version>0){
            return String.format("%s.%06d.%d.%s", prefix, number, version, suffix);
        }else {
            return String.format("%s.%06d.%s", prefix, number, suffix);
        }
    }
}
