package org.act.temporalProperty.index.value.rtree;

import org.act.temporalProperty.util.SliceInput;
import org.act.temporalProperty.util.SliceOutput;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by song on 2018-01-19.
 *
 * This class is a simple wrapper of Slice
 */
public class RTreeLeafNode extends RTreeNode {
    private List<IndexEntry> entry;

    public RTreeLeafNode(List<IndexEntry> entry, IndexEntryOperator op) {
        this.entry = entry;
        this.updateBound(op);
        this.updateCardinality();
    }

    private void updateCardinality() {
        for(IndexEntry e : entry){
            this.c.offer(e.getEntityId());
        }
    }

    public RTreeLeafNode(List<IndexEntry> data) {
        this.entry = data;
    }

    @Override
    public boolean isLeaf() {
        return true;
    }

    @Override
    public List<IndexEntry> getEntries() {
        return entry;
    }

    @Override
    public void encode(SliceOutput out) {
        List<IndexEntry> data = this.getEntries();
        out.writeInt(data.size());
        for(IndexEntry entry: data){
            entry.encode(out, true);
        }
    }

    public static RTreeLeafNode decode(SliceInput in){
        int count = in.readInt();
        List<IndexEntry> data = new ArrayList<>();
        for(int i=0; i<count; i++){
            data.add(new IndexEntry(in));
        }
        return new RTreeLeafNode(data);
    }

    private void updateBound(IndexEntryOperator op){
        if(this.entry.size()>0) {
            this.bound = new RTreeRange(this.entry, op);
        }
    }

    @Override
    public List<RTreeNode> getChildren() {
        throw new UnsupportedOperationException();
    }
}
