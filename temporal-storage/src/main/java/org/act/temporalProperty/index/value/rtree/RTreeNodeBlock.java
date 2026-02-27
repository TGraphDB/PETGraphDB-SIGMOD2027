package org.act.temporalProperty.index.value.rtree;

import org.act.temporalProperty.util.DynamicSliceOutput;
import org.act.temporalProperty.util.Slice;
import org.act.temporalProperty.util.SliceInput;

import java.nio.ByteBuffer;

/**
 * Created by song on 2018-01-19.
 */
public class RTreeNodeBlock {

    private RTreeNode node;
    private long pos;

    // for encode
    public RTreeNodeBlock(RTreeNode node, long pos){
        this.node = node;
        this.pos = pos;
    }

    public ByteBuffer toByteBuffer() {
        DynamicSliceOutput out = new DynamicSliceOutput(4096);
        out.writeByte(this.node.isLeaf()?1:0);
        out.writeInt(0);//content length placeholder.
        this.node.setPos(this.pos);
        this.node.encode(out);
        Slice content = out.slice();
        content.setInt(1, content.length()-5);
        return content.toByteBuffer();
    }

    // for decode
    public RTreeNodeBlock(ByteBuffer map, IndexEntryOperator op){
        boolean isLeaf = map.get()==1;
        int len = map.getInt();
        byte[] content = new byte[len];
        map.get(content);
        SliceInput in = new Slice(content).input();
        if(isLeaf){
            this.node = RTreeLeafNode.decode(in);
        }else{
            this.node = RTreeIndexNode.decode(in, op);
        }
    }

    public RTreeNode getNode(){
        return this.node;
    }



}
