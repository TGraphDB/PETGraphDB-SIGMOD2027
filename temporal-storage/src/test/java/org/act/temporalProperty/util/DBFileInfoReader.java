package org.act.temporalProperty.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.google.common.collect.PeekingIterator;
import org.act.temporalProperty.helper.StoreInitial;
import org.act.temporalProperty.impl.*;
import org.act.temporalProperty.index.IndexValueType;
import org.act.temporalProperty.index.value.rtree.*;
import org.act.temporalProperty.meta.SystemMeta;
import org.act.temporalProperty.meta.SystemMetaController;
import org.act.temporalProperty.meta.SystemMetaFile;
import org.act.temporalProperty.query.TimeInterval;
import org.act.temporalProperty.query.TimeIntervalKey;
import org.act.temporalProperty.query.TimePointL;
import org.act.temporalProperty.query.aggr.AggregationIndexKey;
import org.act.temporalProperty.table.*;
import org.apache.commons.lang3.SystemUtils;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.*;

/**
 * Created by song on 16-9-3.
 */
public class DBFileInfoReader
{
    private String dbDir(){
        if(SystemUtils.IS_OS_WINDOWS){
            return "G:\\jenkins-workspace\\m_traffic_tgk_01\\temporal.relationship.properties";
        }else{
            return "/media/song/test/db-network-only/temporal.relationship.properties";
        }
    }

    @Test
    public void metaFileInfo() throws IOException {
        readMetaFileContent("meta.info");
//        readMetaFileContent("meta.info.new");
    }

    @Test
    public void indexFileInfo() throws Exception{
        readIndexFile();
    }

    private void readIndexFile() throws IOException {
        FileChannel channel = new FileInputStream(new File(new File(this.dbDir(), "index"), "index")).getChannel();
        List<IndexValueType> types = new ArrayList<>();
        types.add(IndexValueType.INT);
        IndexFileIterator reader = new IndexFileIterator(channel, new IndexEntryOperator(types, 4096));//blocksize is not used when reading index files.
        reader.printIndexBlocks(4);
        reader.printDataBlocks(0, 3);
        channel.close();
    }


    private void readMetaFileContent(String fileName) throws IOException {
        System.out.println("################## "+fileName+" #################");
        SystemMetaFile file = SystemMetaController.readFromDisk(new File(dbDir(), fileName));
        if(file!=null && file.isValid()){
            SystemMeta meta = SystemMetaController.decode(file.getMeta(), file.getVersion());
            System.out.println(JSON.toJSONString(meta, SerializerFeature.PrettyFormat));
        }else{
            System.out.println("Format Error: not an valid meta file! Unexpected file end.");
        }
    }

    @Test
    public void dbTmpFileInfo() throws IOException {
        StoreInitial store = new StoreInitial(new File(this.dbDir()));
        MemTable table = store.getMemTable();
        int i=0;
        for (SearchableIterator it = table.iterator(); it.hasNext() && i<10; i++) {
            InternalEntry entry = it.next();
            System.out.println(entry.getKey()+" "+entry.getValue());
        }
        i=0;
        for (PeekingIterator<Map.Entry<TimeIntervalKey, Slice>> it = table.intervalEntryIterator(); it.hasNext() && i<10; i++) {
            Map.Entry<TimeIntervalKey, Slice> entry = it.next();
            System.out.println(entry.getKey()+" "+entry.getValue());
        }
    }



    @Test
    public void diskFilesInfo() throws IOException {
        String propertyId = "1/un.000002.table";
        File metaFile = new File( this.dbDir() + "/" + propertyId );
        if(!metaFile.exists()){
            System.out.println("##### Warning: file not exist: "+ metaFile.getAbsolutePath());
            return;
        }
        System.out.println("################## "+propertyId+" #################");
        FileInputStream inputStream = new FileInputStream( new File( this.dbDir() + "/" + propertyId ) );
        FileChannel channel = inputStream.getChannel();
        Table table = new MMapTable( propertyId, channel, TableComparator.instance(), false );
        TableIterator iterator = table.iterator();
        if( !iterator.hasNext() )
        {
            System.out.println("Empty 000000.dbtmp file.");
            return;
        }
        TimePointL maxTime = TimePointL.Init;
        TimePointL minTime = TimePointL.Now;
        long size = 0;
        long recordCount = 0;
        int cnt=0;
        while( iterator.hasNext() )
        {
            Map.Entry<Slice,Slice> entry = iterator.next();
            Slice key = entry.getKey();
            Slice value = entry.getValue();
            InternalKey internalKey = InternalKey.decode( key );
            if(internalKey.getEntityId()==49822){
                System.out.println(internalKey+" "+value.getInt(0));
                cnt++;
            }
            TimePointL time = internalKey.getStartTime();
            minTime = TimeIntervalUtil.min(minTime, time);
            maxTime = TimeIntervalUtil.max(maxTime, time);
            size += (key.length() + value.length());
            recordCount++;
        }
        inputStream.close();
        channel.close();
        System.out.println("Size: "+ humanReadableFileSize(size)+" minTime:"+ minTime +" maxTime:"+maxTime +" record count:"+recordCount);
    }

    @Test
    public void bufferFilesInfo() throws IOException {
        String fileName = "1/st.000004.buffer";
//        File file = new File( this.dbDir() + "/" + fileName );
        File file = new File( "D:\\tgraph\\test\\energy\\st.000061.buffer" );
        if(!file.exists()){
            System.out.println("##### Warning: file not exist: "+ file.getAbsolutePath());
            return;
        }
        System.out.println("################## "+fileName+" #################");
        FileBuffer fb = new FileBuffer(0);
        fb.init(file);
        SearchableIterator iterator = fb.iterator();
        if( !iterator.hasNext() )
        {
            System.out.println("Empty file.");
            return;
        }
        TimePointL maxTime = TimePointL.Init;
        TimePointL minTime = TimePointL.Now;
        long size = 0;
        long recordCount = 0;
        int cnt=0;
        Set<Long> eIds = new HashSet<>();
        while( iterator.hasNext() )
        {
            InternalEntry entry = iterator.next();
            InternalKey key = entry.getKey();
            Slice value = entry.getValue();
            if(key.getEntityId()==49822){
                System.out.println(key+" "+value.getInt(0));
                cnt++;
            }
            eIds.add(key.getEntityId());
            TimePointL time = key.getStartTime();
            minTime = TimeIntervalUtil.min(minTime, time);
            maxTime = TimeIntervalUtil.max(maxTime, time);
            recordCount++;
        }
        System.out.println("Size: "+ humanReadableFileSize(size)+" minTime:"+ minTime +" maxTime:"+maxTime +" record count:"+recordCount+" entity id cnt:"+eIds.size());
    }

    @Test
    public void dbAggrIndexFileInfo() throws IOException {
        String fileName = "index/aggr.000015.index";
        System.out.println("################## "+fileName+" #################");
        FileInputStream inputStream = new FileInputStream( new File( this.dbDir() + "/" + fileName ) );
        FileChannel channel = inputStream.getChannel();
        Table table = new MMapTable( fileName, channel, AggregationIndexKey.sliceComparator, false );
        TableIterator iterator = table.iterator();
        if( !iterator.hasNext() ) {
            System.out.println("Empty file.");
            return;
        }
        TimePointL maxTime = TimePointL.Init;
        TimePointL minTime = TimePointL.Now;
        long size = 0;
        long recordCount = 0;
        while( iterator.hasNext() )
        {
            Map.Entry<Slice,Slice> entry = iterator.next();
            Slice key = entry.getKey();
            Slice value = entry.getValue();
            AggregationIndexKey indexKey = new AggregationIndexKey(key);
            if(indexKey.getEntityId()==40){
                System.out.println(indexKey+" "+value.getInt(0));
            }
            TimePointL time = indexKey.getTimeGroupId();
            minTime = TimeIntervalUtil.min(minTime, time);
            maxTime = TimeIntervalUtil.max(maxTime, time);
            size += (key.length() + value.length());
            recordCount++;
        }
        inputStream.close();
        channel.close();
        System.out.println("Size: "+ humanReadableFileSize(size)+" minTime:"+ minTime +" maxTime:"+maxTime +" record count:"+recordCount);
    }

    private String humanReadableFileSize(long size)
    {
        float oneMB = 1024*1024;
        float oneKB = 1024;
        if( size > oneMB )
        {
            return ( size / oneMB ) + "MB";
        }else if ( size > oneKB )
        {
            return ( size / oneKB ) + "KB";
        }else{
            return size + "Byte";
        }

    }

    private class IndexFileIterator{
        private final IndexEntryOperator op;
        private final ByteBuffer map;
        private final int rootPos;
        private final RTreeRange rootBound;

        public IndexFileIterator(FileChannel channel, IndexEntryOperator indexEntryOperator) throws IOException {
            this.map = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
            this.map.order(ByteOrder.LITTLE_ENDIAN);
            this.op = indexEntryOperator;
            this.rootPos = map.getInt();
            this.rootBound = RTreeRange.decode(map, op);
        }

        private RTreeNode getNode(int pos, RTreeRange bound) {
            map.position(pos);
            RTreeNodeBlock block = new RTreeNodeBlock(map, op);
            RTreeNode node = block.getNode();
            node.setBound(bound);
            return node;
        }

        public void printIndexBlocks(int level) {
            boolean isIndexLevel = true;
            int curLevel = 0;
            List<RTreeNode> curLevelNodes = new ArrayList<>();
            curLevelNodes.add(getNode(rootPos, rootBound));
            Map<Integer, List<RTreeNode>> result = new HashMap<>();
            result.put(curLevel, curLevelNodes);
            while(isIndexLevel){
                curLevel++;
                List<RTreeNode> nextLevelNodes = bfs(curLevelNodes);
                isIndexLevel = !nextLevelNodes.isEmpty();
                result.put(curLevel, nextLevelNodes);
                curLevelNodes = nextLevelNodes;
            }
            List<RTreeNode> leafNodes = bfs(curLevelNodes);
            System.out.println("leaf level contains "+leafNodes.size()+" nodes.");
            for(Map.Entry<Integer, List<RTreeNode>> e : result.entrySet()){
                System.out.println("level "+e.getKey()+" contains "+e.getValue().size()+" nodes.");
                if(e.getKey()<level){
                    for(RTreeNode node : e.getValue()){
                        System.out.println("\t"+node);
                    }
                }
                int overlapCount = 0;
                for (int i = 0; i < e.getValue().size() - 1; i++) {
                    for (int j = i + 1; j < e.getValue().size(); j++) {
                        if (e.getValue().get(i).getBound().overlap(e.getValue().get(j).getBound())) {
                            overlapCount++;
//                            System.err.println("bound overlap " + i + " " + j);
//                            throw new RuntimeException("bound overlap " + i + " " + j);
                        }
                    }
                }
                System.out.println("level "+ e.getKey() +" overlap count "+ overlapCount);
            }
        }

        public void printDataBlocks(int start, int end) {
            dfs(getNode(rootPos, rootBound), 0,  start, end);
        }

        private long dfs(RTreeNode node, long curBlock, int start, int end){
            if(!node.isLeaf()){
                List<RTreeNode> children=node.getChildren();
                for(RTreeNode diskNode : children){
                    RTreeNode child = getNode(diskNode.getPos(), diskNode.getBound());
                    curBlock = dfs(child, curBlock, start, end);
                }
            }else{
                curBlock++;
                if(start<=curBlock && curBlock<=end){
                    System.out.println("========= block("+curBlock+")  bound["+node.getBound()+"] "+node.getEntries().size()+" entries ========");
                    for(IndexEntry entry : node.getEntries()){
                        System.out.println(entry);
                    }
                }
            }
            return curBlock;
        }

        private List<RTreeNode> bfs(List<RTreeNode> nodes) {
            List<RTreeNode> result = new ArrayList<>();
            long entryCount = 0;
            for(RTreeNode cur : nodes){
                if(!cur.isLeaf()){
                    for(RTreeNode diskNode : cur.getChildren()){
                        RTreeNode node = getNode(diskNode.getPos(), diskNode.getBound());
                        result.add(node);
                    }
                }else{
                    entryCount+=cur.getEntries().size();
                }
            }
            if(entryCount>0) System.out.println("level leaf contains "+ entryCount+" entries");
            return result;
        }
    }
}
