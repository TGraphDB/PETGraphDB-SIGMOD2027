package edu.buaa.common.utils;

import com.alibaba.fastjson.annotation.JSONType;
import com.alibaba.fastjson.parser.DefaultJSONParser;
import com.alibaba.fastjson.parser.JSONLexer;
import com.alibaba.fastjson.parser.JSONToken;
import com.alibaba.fastjson.parser.deserializer.ObjectDeserializer;
import com.alibaba.fastjson.serializer.JSONSerializer;
import com.alibaba.fastjson.serializer.ObjectSerializer;

import java.io.IOException;
import java.util.Objects;

//@JSONType(deserializer = PVal.PValCodec.class)
public abstract class PVal implements Comparable<PVal>{
    public static PVal v(Object val) {
        if(val instanceof Integer){
            return PVal.i((Integer) val);
        }else if(val instanceof Float){
            return PVal.f((Float) val);
        }else if(val instanceof String){
            return PVal.s((String) val);
        }else if(val instanceof Double){
            return PVal.f((float) val);
        }
        throw new IllegalArgumentException("expect String|Integer|Float, but got "+val.getClass().getSimpleName());
    }

    public enum Type{ STRING, INT, FLOAT }
//    @JSONField(serialize = false, deserialize = false)
    protected Type type;

    private PVal(Type type){
        this.type = type;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public abstract Object getVal();

    public String s(){
        assert type==Type.STRING;
        return (String) getVal();
    }

    public Integer i(){
        assert type==Type.INT;
        return (Integer) getVal();
    }

    public Float f(){
        assert type==Type.FLOAT;
        return (Float) getVal();
    }

    public static boolean within(PVal vMin, boolean inclusiveBegin, PVal v, PVal vMax, boolean inclusiveEnd){
        int a = vMin.compareTo(v);
        int b = v.compareTo(vMax);
        return ((inclusiveBegin && a<=0) || (!inclusiveBegin && a<0)) &&
                ((inclusiveEnd && b<=0) || (!inclusiveEnd && b<0));
    }

    public static StrVal s(String val){
        StrVal v = new StrVal();
        v.setVal(val);
        return v;
    }

    public static IntVal i(int val){
        IntVal v = new IntVal();
        v.setVal(val);
        return v;
    }

    public static FloatVal f(float val){
        FloatVal v = new FloatVal();
        v.setVal(val);
        return v;
    }

//    @JSONType(serializer = PValCodec.class)
    public static class StrVal extends PVal {
        String val;
        public StrVal(){
            super(Type.STRING);
        }

        @Override
        public String getVal() {
            return val;
        }

        public void setVal(String val) {
            this.val = val;
        }

        @Override
        public int compareTo(PVal o) {
            assert o instanceof StrVal;
            return val.compareTo(((StrVal) o).val);
        }

        @Override
        public String toString() {
            return val;
        }

        @Override
        public int hashCode() {
            return val.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if(obj instanceof StrVal){
                return Objects.equals(this.val, ((StrVal) obj).val);
            }else return false;
        }
    }

//    @JSONType(serializer = PValCodec.class)
    public static class IntVal extends PVal {
        int val;
        public IntVal(){
            super(Type.INT);
        }

        public Integer getVal() {
            return val;
        }

        public void setVal(int val) {
            this.val = val;
        }

        @Override
        public int compareTo(PVal o) {
            assert o instanceof IntVal;
            return Integer.compare(val, ((IntVal) o).val);
        }

        @Override
        public String toString() {
            return String.valueOf(val);
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(val);
        }

        @Override
        public boolean equals(Object obj) {
            if(obj instanceof IntVal){
                return this.val==((IntVal) obj).val;
            }else return false;
        }
    }

//    @JSONType(serializer = PValCodec.class)
    public static class FloatVal extends PVal {
        float val;
        public FloatVal(){
            super(Type.FLOAT);
        }

        public Float getVal() {
            return val;
        }

        public void setVal(float val) {
            this.val = val;
        }

        @Override
        public int compareTo(PVal o) {
            assert o instanceof FloatVal;
            return Float.compare(val, ((FloatVal) o).val);
        }

        @Override
        public String toString() {
            return String.valueOf(val);
        }

        @Override
        public boolean equals(Object obj) {
            if(obj instanceof FloatVal){
                int a = (Float.floatToRawIntBits(val) & 0xffff0000)>>16;
                int b = (Float.floatToRawIntBits(((FloatVal) obj).val) & 0xffff0000)>>16;
                return Math.abs(a-b)<2;
            }
            return false;
        }
    }

    public static class PValCodec implements ObjectSerializer, ObjectDeserializer {
        //反序列化过程
        @SuppressWarnings("unchecked")
        @Override
        public <T> T deserialze(DefaultJSONParser parser, java.lang.reflect.Type type, Object fieldName) {
            final JSONLexer lexer = parser.lexer;
            if (lexer.token() == JSONToken.LITERAL_INT) {
                String val = lexer.numberString();
                lexer.nextToken(JSONToken.COMMA);
                return (T) PVal.i(Integer.parseInt(val));
            }
            if (lexer.token() == JSONToken.LITERAL_FLOAT) {
                float val = lexer.floatValue();
                lexer.nextToken(JSONToken.COMMA);
                return (T) PVal.f(val);
            }
            if (lexer.token() == JSONToken.LITERAL_STRING){
                String val = lexer.stringVal();
                lexer.nextToken(JSONToken.COMMA);
                return (T) PVal.s(val);
            }
            throw new IllegalStateException();
        }

        //暂时还不清楚
        public int getFastMatchToken() {
            return 0;
        }

        //序列化过程
        @Override
        public void write(JSONSerializer serializer, Object object, Object fieldName, java.lang.reflect.Type fieldType, int features) throws IOException {
            serializer.write(((PVal)object).getVal());
        }
    }


}
