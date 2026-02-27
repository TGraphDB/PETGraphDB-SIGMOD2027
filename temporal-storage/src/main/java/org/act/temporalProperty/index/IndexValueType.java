package org.act.temporalProperty.index;

import com.google.common.base.Preconditions;
import org.act.temporalProperty.meta.ValueContentType;
import org.act.temporalProperty.util.Slice;

/**
 * Created by song on 2018-01-20.
 */
public enum IndexValueType {
    INT(0){
        @Override
        public int compare(Slice entry1, Slice entry2) {
            return Integer.compare(entry1.getInt(0), entry2.getInt(0));
        }

        @Override
        public int compareRange(Slice min1, Slice max1, Slice min2, Slice max2) {
            long tmp1 = min1.getInt(0);
            tmp1 += max1.getInt(0);
            long tmp2 = min2.getInt(0);
            tmp2 += max2.getInt(0);
            return Long.compare(tmp1, tmp2);
        }

        @Override
        public String toString(Slice val) {
            if(val.length()==4) return String.valueOf(val.getInt(0));
            else return null;
        }
    },
    LONG(1){
        @Override
        public int compare(Slice entry1, Slice entry2) {
            return Long.compare(entry1.getLong(0), entry2.getLong(0));
        }

        @Override
        public int compareRange(Slice min1, Slice max1, Slice min2, Slice max2) {
            long tmp1 = min1.getLong(0);
            tmp1 += max1.getLong(0);
            long tmp2 = min2.getLong(0);
            tmp2 += max2.getLong(0);
            return Long.compare(tmp1, tmp2);
        }

        @Override
        public String toString(Slice val) {
            if(val.length()==8) return String.valueOf(val.getLong(0));
            else return null;
        }
    },
    FLOAT(2){
        private float f(Slice s){
            assert s.length()>=4;
            return s.input().readFloat();
        }

        @Override
        public int compare(Slice entry1, Slice entry2) {
            return Float.compare(f(entry1), f(entry2));
        }

        @Override
        public int compareRange(Slice min1, Slice max1, Slice min2, Slice max2) {
            return Float.compare(f(min1)+f(max1), f(min2)+f(max2));
        }

        @Override
        public String toString(Slice val) {
            if(val.length()==4) return String.valueOf(f(val));
            else return null;
        }
    },
    DOUBLE(3){
        private double d(Slice s){
            assert s.length()>=8;
            return s.input().readDouble();
        }
        @Override
        public int compare(Slice entry1, Slice entry2) {
            return Double.compare(d(entry1), d(entry2));
        }

        @Override
        public int compareRange(Slice min1, Slice max1, Slice min2, Slice max2) {
            return Double.compare(d(min1)+d(max1), d(min2)+d(max2));
        }

        @Override
        public String toString(Slice val) {
            if(val.length()==8) return String.valueOf(d(val));
            else return null;
        }
    },
    STRING(4){
        @Override
        public int compare(Slice entry1, Slice entry2) {
            return entry1.compareTo(entry2);

        }

        @Override
        public int compareRange(Slice min1, Slice max1, Slice min2, Slice max2) {
            int i;
            for(i=0; i<min1.length() && i<max1.length() && i<min2.length() && i<max2.length(); i++){
                int result = compareRange(min1.getByte(i), max1.getByte(i), min2.getByte(i), max2.getByte(i));
                if(result!=0) return result;
            }
            int maxLen = Math.max(min1.length(), max1.length());
            maxLen = Math.max(maxLen, min2.length());
            maxLen = Math.max(maxLen, max2.length());
            for(; i<maxLen; i++){
                byte min1Byte, max1Byte, min2Byte, max2Byte;
                if(i>=min1.length()) min1Byte=0; else min1Byte=min1.getByte(i);
                if(i>=max1.length()) max1Byte=0; else max1Byte=max1.getByte(i);
                if(i>=min2.length()) min2Byte=0; else min2Byte=min2.getByte(i);
                if(i>=max2.length()) max2Byte=0; else max2Byte=max2.getByte(i);
                int result = compareRange(min1Byte, max1Byte, min2Byte, max2Byte);
                if(result!=0) return result;
            }
            return 0;
        }

        private int compareRange(byte min1, byte max1, byte min2, byte max2) {
            int tmp1 = min1;
            tmp1+=max1;
            int tmp2 = min2;
            tmp2+=max2;
            return Integer.compare(tmp1, tmp2);
        }

        @Override
        public String toString(Slice val) {
            return val.toString();
        }
    };


    private int id;
    IndexValueType(int id){
        this.id = id;
    }
    public int getId(){
        return id;
    }

    public static IndexValueType decode(int id){
        Preconditions.checkArgument(0<=id && id<=4);
        switch(id){
            case 0: return INT;
            case 1: return LONG;
            case 2: return FLOAT;
            case 3: return DOUBLE;
            default:return STRING;
        }
    }

    public static IndexValueType convertFrom(ValueContentType type){
        switch(type){
            case INT: return IndexValueType.INT;
            case LONG: return IndexValueType.LONG;
            case FLOAT: return IndexValueType.FLOAT;
            case DOUBLE: return IndexValueType.DOUBLE;
            default:return IndexValueType.STRING;
        }
    }

    public static IndexValueType convertFrom(String type){
        switch(type){
            case "INT": return IndexValueType.INT;
            case "LONG": return IndexValueType.LONG;
            case "FLOAT": return IndexValueType.FLOAT;
            case "DOUBLE": return IndexValueType.DOUBLE;
            default:return IndexValueType.STRING;
        }
    }

    public abstract int compare(Slice entry1, Slice entry2);
    public abstract int compareRange(Slice min1, Slice max1, Slice min2, Slice max2);

    public abstract String toString(Slice field);

    public ValueContentType toValueContentType()
    {
        return ValueContentType.decode( id + 1 );
    }
}
