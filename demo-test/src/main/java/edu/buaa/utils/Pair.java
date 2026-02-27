package edu.buaa.utils;

import com.alibaba.fastjson.annotation.JSONType;
import com.alibaba.fastjson.parser.Feature;
import com.alibaba.fastjson.serializer.SerializerFeature;

import java.io.Serializable;
import java.util.Objects;

@JSONType(serialzeFeatures= SerializerFeature.BeanToArray, parseFeatures= Feature.SupportArrayToBean)
public class Pair<L extends Comparable<L>, R extends Comparable<R>> implements Serializable, Comparable<Pair<L, R>> {
    private L key;
    private R value;
    public Pair() {}

    public static <L extends Comparable<L>, R extends Comparable<R>> Pair<L, R> of(L left, R right) {
        Pair<L, R> p = new Pair<>();
        p.setKey(left);
        p.setValue(right);
        return p;
    }

    public L getKey() {
        return key;
    }

    public void setKey(L key) {
        this.key = key;
    }

    public R getValue() {
        return value;
    }

    public void setValue(R value) {
        this.value = value;
    }

    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        } else if (!(obj instanceof Pair)) {
            return false;
        } else {
            Pair<?, ?> other = (Pair)obj;
            return Objects.equals(this.getKey(), other.getKey()) && Objects.equals(this.getValue(), other.getValue());
        }
    }

    public int hashCode() {
        return Objects.hash(this.getKey(), this.getValue());
    }

    public String toString() { return "" + '(' + this.key + ',' + this.value + ')'; }

    @Override
    public int compareTo(Pair<L, R> o) {
        int r = this.key.compareTo(o.key);
        if(r==0) return this.value.compareTo(o.value);
        else return r;
    }
}