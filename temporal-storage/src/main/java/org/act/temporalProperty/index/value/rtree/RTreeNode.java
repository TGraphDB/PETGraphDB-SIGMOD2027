package org.act.temporalProperty.index.value.rtree;

import org.act.temporalProperty.index.value.cardinality.HyperLogLog;
import org.act.temporalProperty.util.SliceOutput;

import java.util.List;

/**
 * Created by song on 2018-01-19.
 *
 */
public abstract class RTreeNode
{
    protected int pos;
    protected RTreeRange bound;
    protected HyperLogLog c = HyperLogLog.defaultBuilder();

    public long getCardinality(){
        return c.cardinality();
    }

    public HyperLogLog getCardinalityEstimator(){return c;}

    // common methods ========================
    public abstract boolean isLeaf();
    public abstract void encode(SliceOutput out);

    public void setPos(long position) {
        if(position<Integer.MAX_VALUE) this.pos = (int) position;
        else throw new RuntimeException("pos large than 2GB");
    }

    public int getPos() {
        return pos;
    }

    public RTreeRange getBound() {
        return bound;
    }

    public void setBound(RTreeRange range) {
        this.bound = range;
    }

    public String toString(){
        return "RTreeNode[bound="+(bound==null?"null":bound.toString())+"]";
    }

    // method only for index nodes ================
    public abstract List<RTreeNode> getChildren();

    // method only for leaf nodes ================
    public abstract List<IndexEntry> getEntries();
}
