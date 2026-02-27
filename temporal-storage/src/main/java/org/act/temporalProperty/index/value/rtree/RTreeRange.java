package org.act.temporalProperty.index.value.rtree;

import org.act.temporalProperty.util.Slice;
import org.act.temporalProperty.util.SliceInput;
import org.act.temporalProperty.util.SliceOutput;

import java.nio.ByteBuffer;
import java.util.List;

public class RTreeRange
{
    private final IndexEntryOperator op;
    private IndexEntry min;
    private IndexEntry max;

    public RTreeRange(IndexEntry min, IndexEntry max, IndexEntryOperator op) {
        this.min = min;
        this.max = max;
        this.op = op;
    }

    public RTreeRange(List<IndexEntry> entries, IndexEntryOperator op) {
        this.op = op;
        IndexEntry[] minMax = op.calcMinMax(entries);
        this.min = minMax[0];
        this.max = minMax[1];
    }

    public IndexEntry getMin() {
        return min;
    }

    public void setMin(IndexEntry min) {
        this.min = min;
    }

    public IndexEntry getMax() {
        return max;
    }

    public void setMax(IndexEntry max) {
        this.max = max;
    }

    public boolean overlap(RTreeRange bound) {
        for(int i=0;i<op.dimensionCount();i++){
            if(!overlap(bound, i)) return false;
        }
        return true;
    }

    public boolean contains(IndexEntry point) {
        for(int i = 0; i< this.op.dimensionCount(); i++){
            if(!(op.compare(min, point, i)<=0 && op.compare(point, max, i)<=0)){
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "RTreeRange{" +
                "min=" + op.toString(min) +
                ", max=" + op.toString(max) +
                '}';
    }

    public boolean overlap(RTreeRange bound, int dimIndex) {
        return  op.compare(this.min, bound.max, dimIndex)<=0 &&
                op.compare(bound.min, this.max, dimIndex)<=0;
    }

    public void encode(SliceOutput out){
        Slice minContent = getMin().encode();
        out.writeInt(minContent.length());
        out.writeBytes(minContent);

        Slice maxContent = getMax().encode();
        out.writeInt(maxContent.length());
        out.writeBytes(maxContent);
    }

    private static RTreeRange decode(Slice min, Slice max, IndexEntryOperator op) {
        return new RTreeRange(new IndexEntry(min), new IndexEntry(max), op);
    }

    public static RTreeRange decode(SliceInput in, IndexEntryOperator op) {
        int len = in.readInt();
        Slice min = in.readSlice(len);
        len = in.readInt();
        Slice max = in.readSlice(len);
        return decode(min, max, op);
    }

    public static RTreeRange decode(ByteBuffer map, IndexEntryOperator op) {
        int len = map.getInt();
        byte[] min = new byte[len];
        map.get(min);
        len = map.getInt();
        byte[] max = new byte[len];
        map.get(max);
        return RTreeRange.decode(new Slice(min), new Slice(max), op);
    }
}
