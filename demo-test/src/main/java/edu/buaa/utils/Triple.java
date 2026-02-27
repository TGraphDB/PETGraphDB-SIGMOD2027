package edu.buaa.utils;

import com.alibaba.fastjson.annotation.JSONField;
import com.alibaba.fastjson.annotation.JSONType;
import com.alibaba.fastjson.parser.Feature;
import com.alibaba.fastjson.serializer.SerializerFeature;

import java.io.Serializable;
import java.util.Objects;

@JSONType(serialzeFeatures= SerializerFeature.BeanToArray, parseFeatures= Feature.SupportArrayToBean)
public class Triple<L extends Comparable<L>, M extends Comparable<M>, R extends Comparable<R>> implements Serializable,
Comparable<Triple<L, M, R>>{
    @JSONField(ordinal = 1)
    private L left;

    @JSONField(ordinal = 2)
    private M middle;

    @JSONField(ordinal = 3)
    private R right;

    public Triple() {}

    public static <L extends Comparable<L>, M extends Comparable<M>, R extends Comparable<R>> Triple<L, M, R> of(L left, M middle, R right) {
        Triple<L,M,R> t = new Triple<L, M, R>();
        t.left = left;
        t.middle = middle;
        t.right = right;
        return t;
    }

    public L getLeft() { return left; }

    public void setLeft(L left) { this.left = left; }

    public M getMiddle() { return middle; }

    public void setMiddle(M middle) { this.middle = middle; }

    public R getRight() { return right; }

    public void setRight(R right) {this.right = right;}

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        } else if (!(obj instanceof Triple)) {
            return false;
        } else {
            Triple<?, ?, ?> other = (Triple)obj;
            return this.getLeft().equals(other.getLeft()) && this.getMiddle().equals(other.getMiddle()) && this.getRight().equals(other.getRight());
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getLeft(), this.getMiddle(), this.getRight());
    }

    public String toString() {
        return "" + '(' + this.getLeft() + ',' + this.getMiddle() + ',' + this.getRight() + ')';
    }

    public String toString(String format) {
        return String.format(format, this.getLeft(), this.getMiddle(), this.getRight());
    }

    @Override
    public int compareTo(Triple<L, M, R> o) {
        int r = this.left.compareTo(o.left);
        if(r==0){
            r = this.middle.compareTo(o.middle);
            if(r==0) return this.right.compareTo(o.right);
            else return r;
        }else{
            return r;
        }
    }

//    public static class TripleCodec implements ObjectSerializer, ObjectDeserializer{
//
//        @Override
//        public <T> T deserialze(DefaultJSONParser parser, Type type, java.lang.Object fieldName) {
//            return null;
//        }
//
//        @Override
//        public int getFastMatchToken() {
//            return 0;
//        }
//
//        @Override
//        public void write(JSONSerializer serializer, java.lang.Object object, java.lang.Object fieldName, Type fieldType, int features) throws IOException {
//            Triple t = (Triple) object;
//            serializer.out.append("[")
//                    .append(serializer.t.getLeft()).append(t.getMiddle().toString()).append(t.getRight().toString())
//                    .append("]");
//        }
//    }
}
