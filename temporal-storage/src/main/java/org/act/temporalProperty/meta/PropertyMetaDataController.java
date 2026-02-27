package org.act.temporalProperty.meta;

import org.act.temporalProperty.impl.FileBuffer;
import org.act.temporalProperty.impl.FileMetaData;
import org.act.temporalProperty.util.DynamicSliceOutput;
import org.act.temporalProperty.util.Slice;
import org.act.temporalProperty.util.SliceInput;
import org.act.temporalProperty.util.SliceOutput;

import java.util.Collection;
import java.util.Map;
import java.util.SortedMap;

/**
 * Created by song on 2018-01-17.
 */
public class PropertyMetaDataController {

    public static PropertyMetaData decode(SliceInput in, int version) {
        int pid = in.readInt();
        ValueContentType type = ValueContentType.decode(in.readInt());
        PropertyMetaData p = new PropertyMetaData(pid, type);
        // pack table unstable
        int count = in.readInt();
        for(int i=0; i<count; i++){
            FileMetaData file = FileMetaDataController.decode(in, version);
            p.addUnstable(file);
        }
        // pack table stable
        count = in.readInt();
        for(int i=0; i<count; i++){
            FileMetaData file = FileMetaDataController.decode(in, version);
            p.addStable(file);
        }
        // pack buffer unstable
        count = in.readInt();
        for(int i=0; i<count; i++){
            FileBuffer buffer = FileBuffer.decode(in, version);
            p.addUnstableBuffer(buffer.getNumber(), buffer);
        }
        // pack buffer stable
        count = in.readInt();
        for(int i=0; i<count; i++){
            FileBuffer buffer = FileBuffer.decode(in, version);
            p.addStableBuffer(buffer.getNumber(), buffer);
        }
        return p;
    }

    public static Slice encode(PropertyMetaData meta) {
        DynamicSliceOutput out = new DynamicSliceOutput(40);
        encode(out, meta);
        return out.slice();
    }

    public static void encode(SliceOutput out, PropertyMetaData meta) {
        out.writeInt(meta.getPropertyId());//4
        out.writeInt(meta.getType().getId());//4

        packTable(out, meta.getUnStableFiles().values());
        packTable(out, meta.getStableFiles().values());

        packBuffer(out, meta.getUnstableBuffers());
        packBuffer(out, meta.getStableBuffers());
    }

    private static void packTable(SliceOutput out, Collection<FileMetaData> tables){
        out.writeInt(tables.size());//4
        for(FileMetaData fMeta : tables){
            FileMetaDataController.encode(out, fMeta);//24
        }
    }

    private static void packBuffer(SliceOutput out, SortedMap<Long, FileBuffer> buffers){
        out.writeInt(buffers.size());
        for(FileBuffer fBuf : buffers.values()){
            fBuf.encode(out);
        }
    }
}
