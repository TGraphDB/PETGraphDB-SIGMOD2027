package org.act.temporalProperty.query.aggr;

import com.google.common.base.Objects;
import org.act.temporalProperty.exception.TPSNHException;
import org.act.temporalProperty.index.IndexValueType;
import org.act.temporalProperty.meta.ValueContentType;
import org.act.temporalProperty.util.Slice;
import org.act.temporalProperty.util.SliceInput;
import org.act.temporalProperty.util.SliceOutput;


import java.util.*;
import java.util.Map.Entry;

/**
 * Created by song on 2018-04-06.
 */
public abstract class ValueGroupingMap {
    protected int UN_GROUPED_GID = Integer.MIN_VALUE;
    private ValueGroupingMap(){}
    public abstract void encode(SliceOutput out);

    public abstract int group(Slice v);

    public abstract Object groupStartVal(int grp);

    public static Comparator<Slice> INT_CMP = Comparator.comparingInt(o -> o.getInt(0));
    public static Comparator<Slice> LONG_CMP = Comparator.comparingLong(o -> o.getLong(0));;
    public static Comparator<Slice> FLOAT_CMP = Comparator.comparingDouble(o -> o.input().readFloat());
    public static Comparator<Slice> DOUBLE_CMP = Comparator.comparingDouble(o -> o.input().readDouble());
    public static Comparator<Slice> STR_CMP = Comparator.naturalOrder();

    public static Comparator<Slice> getComparator(IndexValueType valueType) {
        switch(valueType){
            case INT: return INT_CMP;
            case LONG: return LONG_CMP;
            case FLOAT: return FLOAT_CMP;
            case DOUBLE: return DOUBLE_CMP;
            case STRING: return STR_CMP;
            default:
                throw new TPSNHException("invalid value type");
        }
    }

    public static ValueGroupingMap decode(SliceInput in){
        int typeId = in.readInt();
        switch(typeId){
            case -1: return new Empty();
            case 0: return new IntValueGroupMap();
            case 1: return new IntRange(in);
            case 2: return new FloatRangeGroupMap(in);
            default:
                throw new UnsupportedOperationException("");
        }
    }

    protected Slice int2Slice( int val ){
        Slice s = new Slice(4);
        s.setInt(0, val);
        return s;
    }

    protected Slice long2Slice(long val){
        Slice s = new Slice(4);
        s.setLong(0, val);
        return s;
    }

    protected Slice float2Slice(float val){
        Slice s = new Slice(4);
        s.output().writeFloat(val);
        return s;
    }

    protected Slice double2Slice(double val){
        Slice s = new Slice(8);
        s.output().writeDouble(val);
        return s;
    }

    protected Slice str2Slice(String val){
        Slice s = new Slice(val.length());
        s.output().writeBytes(val);
        return s;
    }
//
//    public static ValueGroupingMap getInstance(int id){
//
//    }


    public static class Empty extends ValueGroupingMap{

        @Override
        public void encode(SliceOutput out) {
            out.writeInt(-1);
        }

        @Override
        public int group(Slice v) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object groupStartVal(int grp) { throw new UnsupportedOperationException(); }

        @Override
        public String toString() {
            return "Empty";
        }
    }

    public static class IntValueGroupMap extends ValueGroupingMap{
        private static final int ID = 0;

        public int group(Slice v) {
            if(v==null || v.length()<4) {
                return UN_GROUPED_GID;
            } else {
                return v.getInt(0);
            }
        }

        @Override
        public Object groupStartVal(int grp) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void encode(SliceOutput out) {
            out.writeInt(ID);
        }

        @Override
        public String toString() {
            return "IntValueAsGroupId";
        }
    }

    public static class IntRange extends ValueGroupingMap{
        private static final int ID = 1;
        private final TreeMap<Slice, Integer> groupMap;

        public IntRange(List<Integer> groups) {
            groupMap = new TreeMap<>(INT_CMP);
            for(int i=0; i<groups.size(); i++){
                groupMap.put(int2Slice(groups.get(i)), i);
            }
        }

        public IntRange(SliceInput in) {
            groupMap = new TreeMap<>(INT_CMP);
            int cnt = in.readInt();
            for(int i=0; i<cnt; i++){
                int val = in.readInt();
                groupMap.put(int2Slice(val), i);
            }
        }

        public int group(Slice v) {
            if(v==null) return UN_GROUPED_GID;
            Entry<Slice, Integer> e = groupMap.floorEntry(v);
            if (e == null) {
                return UN_GROUPED_GID;
            } else {
                return e.getValue();
            }
        }

        @Override
        public Object groupStartVal(int grp) {
            Slice key = groupMap.navigableKeySet().toArray(new Slice[]{})[grp];
            return key.getInt(0);
        }

        @Override
        public void encode(SliceOutput out) {
            out.writeInt(ID);
            out.writeInt(groupMap.size());
            groupMap.keySet().forEach(k-> out.writeInt(k.getInt(0)));
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("IntRange{");
            groupMap.forEach((s, i)-> sb.append(s.getInt(0)).append("->").append(i).append(','));
            sb.append('}');
            return sb.toString();
        }
    }
//
//    public static class LongValueGroupMap extends ValueGroupingMap{
//        public LongValueGroupMap() {
//            super(Comparator.naturalOrder(), IndexValueType.LONG);
//        }
//    }
//

    public static class FloatRangeGroupMap extends ValueGroupingMap{
        private static final int ID = 2;
        private final TreeMap<Slice, Integer> groupMap;

        public FloatRangeGroupMap(List<Float> groups) {
            groupMap = new TreeMap<>(FLOAT_CMP);
            for(int i=0; i<groups.size(); i++){
                groupMap.put(float2Slice(groups.get(i)), i);
            }
        }

        public FloatRangeGroupMap(SliceInput in) {
            groupMap = new TreeMap<>(FLOAT_CMP);
            int cnt = in.readInt();
            for(int i=0; i<cnt; i++){
                float val = in.readFloat();
                groupMap.put(float2Slice(val), i);
            }
        }

        public int group(Slice v) {
            if(v==null) return UN_GROUPED_GID;
            Entry<Slice, Integer> e = groupMap.floorEntry(v);
            if (e == null) {
                return UN_GROUPED_GID;
            } else {
                return e.getValue();
            }
        }

        @Override
        public Object groupStartVal(int grp) {
            Slice key = groupMap.navigableKeySet().toArray(new Slice[]{})[grp];
            return key.input().readFloat();
        }

        @Override
        public void encode(SliceOutput out) {
            out.writeInt(ID);
            out.writeInt(groupMap.size());
            groupMap.keySet().forEach(k-> out.writeFloat(k.input().readFloat()));
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("FloatRange{");
            groupMap.forEach((s, i)-> sb.append(s.input().readFloat()).append("->").append(i).append(','));
            sb.append('}');
            return sb.toString();
        }
    }
//
//    public static class DoubleValueGroupMap extends ValueGroupingMap{
//        public DoubleValueGroupMap() {
//            super(Comparator.naturalOrder(), IndexValueType.DOUBLE);
//        }
//    }
//
//    public static class StringValueGroupMap extends ValueGroupingMap{
//        public StringValueGroupMap() {
//            super(Comparator.naturalOrder(), IndexValueType.STRING);
//        }
//    }
}

//    private Slice toSlice(Slice key) {
//        switch(valueType){
//            case INT:
//                return int2Slice((Integer) key);
//            case LONG:
//                return long2Slice((Long) key);
//            case FLOAT:
//                return float2Slice((Float) key);
//            case DOUBLE:
//                return double2Slice((Double) key);
//            case STRING:
//                return str2Slice((String) key);
//            default:
//                throw new TPSNHException("invalid value type");
//        }
//    }


//
//
