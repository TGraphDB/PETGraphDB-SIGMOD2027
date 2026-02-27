package org.act.temporalProperty.index.value.rtree;

import org.act.temporalProperty.exception.TPSNHException;
import org.act.temporalProperty.util.SliceInput;
import org.act.temporalProperty.util.SliceOutput;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by song on 2018-01-19.
 */
public class RTreeIndexNode extends RTreeNode {

    private List<RTreeNode> children = new ArrayList<>();

    public RTreeIndexNode(List<RTreeNode> children, IndexEntryOperator op) {
        this.children = children;
        this.updateBound(op);
        this.updateCardinality();
    }

    private void updateCardinality() {
        for(RTreeNode node : children){
            this.c.addAll(node.c);
        }
    }

    public RTreeIndexNode(List<RTreeNode> data) {
        this.children = data;
    }

    @Override
    public List<RTreeNode> getChildren() {
        return children;
    }

    @Override
    public void encode(SliceOutput out) {
        List<RTreeNode> data = this.getChildren();
        out.writeInt(data.size());
        for (RTreeNode entry : data) {
            out.writeInt(entry.getPos());
            entry.getBound().encode(out);
        }
    }

    public static RTreeNode decode(SliceInput in, IndexEntryOperator op) {
        int count = in.readInt();
        List<RTreeNode> data = new ArrayList<>();
        for(int i=0; i<count; i++){
            int pos = in.readInt();
            RTreeRange range = RTreeRange.decode(in, op);
            data.add(new RTreeDiskNode(pos, range));
        }
        return new RTreeIndexNode(data);
    }

    @Override
    public boolean isLeaf() {
        return false;
    }

    private void updateBound(IndexEntryOperator op){
        if(this.getChildren().size()>0) {
            List<IndexEntry> bounds = new ArrayList<>();
            for(RTreeNode node : this.getChildren()){
                bounds.add(node.getBound().getMin());
                bounds.add(node.getBound().getMax());
            }
            this.bound = new RTreeRange(bounds, op);
        }else{
            throw new TPSNHException("no children node!");
        }
    }

    @Override
    public List<IndexEntry> getEntries() {
        throw new RuntimeException("SNH: unsupported method");
    }

}
