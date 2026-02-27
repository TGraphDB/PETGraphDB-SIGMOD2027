package org.act.temporalProperty.index.value.rtree;


import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import org.act.temporalProperty.query.TimePointL;
import org.act.temporalProperty.util.DynamicSliceOutput;
import org.act.temporalProperty.util.Slice;
import org.act.temporalProperty.util.SliceInput;
import org.act.temporalProperty.util.SliceOutput;
import org.act.temporalProperty.util.VariableLengthQuantity;

/**
 * Created by song on 2018-01-21.
 */
public class IndexEntry {
    private final TimePointL start;
    private final TimePointL end;
    private final Slice[] value;
    private Long entityId = null;

    public IndexEntry(TimePointL start, TimePointL end, Slice[] value) {
        Preconditions.checkArgument(start.compareTo(end) <= 0, "start %s > end %s", start, end);
        this.start = start;
        this.end = end;
        this.value = value;
    }

    public IndexEntry(Slice content) {
        this(content.input());
    }

    public IndexEntry(SliceInput in) {
        byte flag = in.readByte();
        boolean hasEntityId = ( flag < 0 );
        int valueCount = flag<0 ? -flag : flag;
        if(hasEntityId){
            this.entityId = VariableLengthQuantity.readVariableLengthLong(in);
        }
        this.start = TimePointL.decode(in);
        this.end = TimePointL.decode(in);
        this.value = new Slice[valueCount];
        for(int i=0; i<valueCount; i++) {
            this.value[i] = nextEntry(in);
        }
    }

    public IndexEntry(long entityId, TimePointL startTime, TimePointL endTime, Slice[] values) {
        this(startTime, endTime, values);
        this.entityId = entityId;
    }

    private Slice nextEntry(SliceInput in){
        int len = VariableLengthQuantity.readVariableLengthInt(in);
        if(len==0) return null;
        else return in.readSlice(len);
    }

    private void putNextEntry(SliceOutput out, Slice entry) {
        if(entry==null){
            VariableLengthQuantity.writeVariableLengthInt(0, out);
        }else{
            VariableLengthQuantity.writeVariableLengthInt(entry.length(), out);
            out.writeBytes(entry);
        }
    }

    public Long getEntityId() {
        return entityId;
//        if(entityId!=null) return entityId;
//        else throw new RuntimeException("do not contains entityId");
    }

    public TimePointL getStart() {
        return start;
    }

    public TimePointL getEnd() {
        return end;
    }

    public Slice getValue(int i){
        return value[i];
    }

    public void encode(SliceOutput out, boolean withEntityId) {
        if(withEntityId){
            out.writeByte(0-value.length);
            VariableLengthQuantity.writeVariableLengthLong(entityId, out);
        }else{
            out.writeByte(value.length);
        }
        start.encode(out);
        end.encode(out);
        for(Slice entry : value){
            putNextEntry(out, entry);
        }
    }

    public void encode(SliceOutput out) {
        encode(out, false);
    }

    public Slice encode(boolean withEntityId) {
        DynamicSliceOutput out = new DynamicSliceOutput(64);
        encode(out, withEntityId);
        return out.slice();
    }

    public Slice encode() { return encode(false); }

    public int valueLength(){
        return value.length;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IndexEntry entry = (IndexEntry) o;
        boolean timeEq = (start == entry.start && end == entry.end);
        boolean eidEq = allNullOrEqual(entityId, entry.entityId);
        boolean valEq = true;
        for(int i=0; i<value.length; i++){
            if(!allNullOrEqual(value[i], entry.value[i])){
                valEq=false;
            }
        }
        return timeEq && eidEq && valEq;
    }

    private boolean allNullOrEqual(Object a, Object b){
        return (a==null && b==null) || (a!=null && b!=null && a.equals(b));
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(start, end);
    }

    @Override
    public String toString() {
        return "IndexEntry{" +
                "start=" + start +
                ", end=" + end +
                ", value=" + value2Str() +
                ", entityId=" + entityId +
                '}';
    }

    private String value2Str(){
        if(value==null){
            return "null";
        }else if(value.length==0){
            return "[empty]";
        }else{
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            for(int i=0; i<value.length; i++) {
                if (value[i] != null) {
                    sb.append(value[i].getInt(0));
                } else {
                    sb.append("null");
                }
                if(i<value.length-1) {
                    sb.append(", ");
                }
            }
            sb.append("]");
            return sb.toString();
        }
    }
}
