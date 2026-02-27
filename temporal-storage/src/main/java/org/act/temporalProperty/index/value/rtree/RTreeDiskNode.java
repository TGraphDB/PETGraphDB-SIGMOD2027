package org.act.temporalProperty.index.value.rtree;

import org.act.temporalProperty.util.SliceOutput;

import java.util.List;

/**
 * Created by song on 2018-01-21.
 *
 * as a placeholder when read from disk
 */
public class RTreeDiskNode extends RTreeNode {

    public RTreeDiskNode(int pos, RTreeRange range) {
        this.pos = pos;
        this.bound = range;
    }

    @Override
    public boolean isLeaf() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void encode(SliceOutput out) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<RTreeNode> getChildren() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<IndexEntry> getEntries() {
        throw new UnsupportedOperationException();
    }
}
