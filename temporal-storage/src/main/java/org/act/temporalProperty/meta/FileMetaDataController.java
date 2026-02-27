package org.act.temporalProperty.meta;

import org.act.temporalProperty.TemporalPropertyStore;
import org.act.temporalProperty.impl.FileMetaData;
import org.act.temporalProperty.query.TimePointL;
import org.act.temporalProperty.util.DynamicSliceOutput;
import org.act.temporalProperty.util.Slice;
import org.act.temporalProperty.util.SliceInput;
import org.act.temporalProperty.util.SliceOutput;

/**
 * Created by song on 2018-01-17.
 */
public class FileMetaDataController {

    public static void encode(SliceOutput out, FileMetaData meta){
        out.writeLong(meta.getNumber());
        out.writeInt(meta.getVersion());
        out.writeLong(meta.getFileSize());
        meta.getSmallest().encode(out);
        meta.getLargest().encode(out);
    }

    public static FileMetaData decode(SliceInput in, int version){
        if(version == TemporalPropertyStore.Version){
            long fNum = in.readLong();
            int fVersion = in.readInt();
            long fSize = in.readLong();
            TimePointL smallest = TimePointL.decode(in);
            TimePointL largest = TimePointL.decode(in);
            return new FileMetaData(fNum, fSize, smallest, largest, fVersion);
        }else{
            return decodeV1(in);
        }
    }

    public static FileMetaData decodeV1(SliceInput in){
        long fNum = in.readLong();
        long fSize = in.readLong();
        TimePointL smallest = TimePointL.decode(in);
        TimePointL largest = TimePointL.decode(in);
        return new FileMetaData(fNum, fSize, smallest, largest);
    }

}
