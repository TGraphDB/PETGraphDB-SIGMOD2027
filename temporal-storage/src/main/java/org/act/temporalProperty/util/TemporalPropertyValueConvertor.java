package org.act.temporalProperty.util;

import org.act.temporalProperty.meta.ValueContentType;

public class TemporalPropertyValueConvertor
{
    public static final String TemporalPropertyMarker = "TGRAPH_TEMPORAL_PROPERTY";
    public static final String CLASS_NAME_LENGTH_SEPERATOR = "%";
    
    private static final String INT_NAME = "Integer";
    private static final String DOUBLE_NAME = "Double";
    private static final String FLOAT_NAME = "Float";
    private static final String LONG_NAME = "Long";
    private static final String SLICE_NAME = "Slice";

    public static Slice toSlice( Object value )
    {
        SliceOutput o = new DynamicSliceOutput( 8 );
        if(value instanceof Integer) o.writeInt( (Integer) value );
        else if(value instanceof Double) o.writeDouble( (Double) value );
        else if(value instanceof Float) o.writeFloat( (Float) value );
        else if(value instanceof Long) o.writeLong( (Long) value );
        else if(value instanceof Slice) return ((Slice) value).copySlice();
        else throw new UnsupportedOperationException("Unsupported value type");
        return o.slice();
    }

    public static Object fromSlice(String className, Slice value)
    {
        SliceInput in = value.input();
        switch ( className )
        {
        case INT_NAME:
            return in.readInt();
        case DOUBLE_NAME:
            return in.readDouble();
        case FLOAT_NAME:
            return in.readFloat();
        case LONG_NAME:
            return in.readLong();
        case SLICE_NAME:
            return value.copySlice();
        default:
            throw new UnsupportedOperationException("Unsupported value type");
        }
    }

    public static Object fromSlice(ValueContentType valueType, Slice value)
    {
        SliceInput in = value.input();
        switch ( valueType )
        {
        case INT:
            return in.readInt();
        case DOUBLE:
            return in.readDouble();
        case FLOAT:
            return in.readFloat();
        case LONG:
            return in.readLong();
        case SLICE:
            return value.copySlice();
        default:
            throw new UnsupportedOperationException("Unsupported value type");
        }
    }

    public static ValueContentType str2type(String className)
    {
        switch (className) {
        case INT_NAME:
            return ValueContentType.INT;
        case DOUBLE_NAME:
            return ValueContentType.DOUBLE;
        case FLOAT_NAME:
            return ValueContentType.FLOAT;
        case LONG_NAME:
            return ValueContentType.LONG;
        case SLICE_NAME:
            return ValueContentType.SLICE;
        default:
            throw new UnsupportedOperationException("Unsupported value type");
        }
    }
    
}
