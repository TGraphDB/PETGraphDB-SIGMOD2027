package org.act.temporalProperty.index.value;

import org.act.temporalProperty.index.value.cardinality.RTreeCardinality;
import org.act.temporalProperty.index.value.rtree.*;
import org.act.temporalProperty.util.DynamicSliceOutput;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by song on 2018-01-19.
 *
 */
public class IndexTableWriter {

    private final IndexEntryOperator op;
    private final FileChannel channel;
    private final List<IndexEntry> dataEntries;
    private boolean hasHeader = false;

    public IndexTableWriter(FileChannel channel, IndexEntryOperator op){
        this.dataEntries = new ArrayList<>();
        this.channel = channel;
        this.op = op;
    }

    public void add(IndexEntry entry){
        if(!hasHeader){
            addHeader();
        }
        dataEntries.add(entry);
    }

    private void addHeader() {

    }

    public void finish() throws IOException {
        if(dataEntries.isEmpty()) return;

        RTree tree = new RTree(dataEntries, op);

        ByteBuffer startPos = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        startPos.putInt(Integer.MAX_VALUE);
        startPos.flip();
        channel.write(startPos);

        channel.write(rootBound(tree.getRoot()));

        List<List<RTreeNode>> levels = tree.getLevels();
        for(int i=0; i<levels.size(); i++){
            List<RTreeNode> list = levels.get(i);
            for (RTreeNode node : list) {
                RTreeNodeBlock block = new RTreeNodeBlock(node, channel.position());
                channel.write(block.toByteBuffer());
            }
        }

        RTreeNodeBlock rootBlock = new RTreeNodeBlock(tree.getRoot(), channel.position());
        channel.write(rootBlock.toByteBuffer());

        // write cardinality parts
//        System.out.println(channel.position());
        ByteBuffer cTree = new RTreeCardinality(tree).encode();
        channel.write(cTree);

//        System.out.println(tree.getRoot().getPos());
        startPos.clear();
        startPos.putInt(tree.getRoot().getPos()).flip();
        channel.write(startPos, 0);

        channel.force(true);
    }

    private ByteBuffer rootBound(RTreeNode root) {
        DynamicSliceOutput out = new DynamicSliceOutput(64);
        root.getBound().encode(out);
        return out.toByteBuffer();
    }

}
