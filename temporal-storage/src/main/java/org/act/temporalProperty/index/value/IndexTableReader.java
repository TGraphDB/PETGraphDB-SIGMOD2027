package org.act.temporalProperty.index.value;

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.PeekingIterator;
import org.act.temporalProperty.index.value.rtree.IndexEntry;
import org.act.temporalProperty.index.value.rtree.IndexEntryOperator;
import org.act.temporalProperty.index.value.rtree.RTreeNode;
import org.act.temporalProperty.index.value.rtree.RTreeNodeBlock;
import org.act.temporalProperty.index.value.rtree.RTreeRange;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by song on 2018-01-19.
 */
public class IndexTableReader extends AbstractIterator<IndexEntry> implements PeekingIterator<IndexEntry> {

    private final IndexEntryOperator op;
    private final LinkedList<RTreeNode> nodeStack = new LinkedList<>();
    private final LinkedList<Integer> indexStack = new LinkedList<>(); // next index to access
    private final MappedByteBuffer map;
    private final int rootPos;
    private final RTreeRange rootBound;

    public IndexTableReader( FileChannel channel, IndexEntryOperator indexEntryOperator ) throws IOException {
        this.map = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
        this.map.order(ByteOrder.LITTLE_ENDIAN);
//        this.map.flip();
        this.op = indexEntryOperator;

//        System.out.println(map.limit()+" "+map.position()+" "+map.remaining());
        this.rootPos = map.getInt();
//        System.out.println(rootPos);
        this.rootBound = RTreeRange.decode(map, op);

        this.nodeStack.push(getNode(rootPos, rootBound));
        this.indexStack.push(0);
    }

    private RTreeNode getNode(int pos, RTreeRange bound) {
        map.position(pos);
        RTreeNodeBlock block = new RTreeNodeBlock(map, op);
        RTreeNode node = block.getNode();
        node.setBound(bound);
        return node;
    }

    @Override
    protected IndexEntry computeNext() {
        while(nodeStack.peek()!=null){
            RTreeNode cur = nodeStack.peek();
            int curIndex = indexStack.peek();
            if(!cur.isLeaf()){
                List<RTreeNode> children=cur.getChildren();
                if(curIndex<children.size()) {
                    RTreeNode diskNode = children.get(curIndex);
                    RTreeNode node = getNode(diskNode.getPos(), diskNode.getBound());
                    nodeStack.push(node);
                    indexStack.push(0);
                }else{
                    nodeStack.pop();
                    indexStack.pop();
                    if(!indexStack.isEmpty()) incLast(indexStack);
                }
            }else{
                List<IndexEntry> data = cur.getEntries();
                if(curIndex<data.size()){
                    IndexEntry entry = data.get(curIndex);
                    incLast(indexStack);
                    return entry;
                }else{
                    nodeStack.pop();
                    indexStack.pop();
                    if(!indexStack.isEmpty()) incLast(indexStack);
                }
            }
        }
        return endOfData();
    }

    private void incLast(LinkedList<Integer> indexStack) {
        int i = indexStack.pop();
        indexStack.push(i+1);
    }
}
