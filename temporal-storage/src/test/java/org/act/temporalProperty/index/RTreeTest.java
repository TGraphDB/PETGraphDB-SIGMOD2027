//package org.act.temporalProperty.index;
//
//
//import com.google.common.base.Preconditions;
//import com.google.common.collect.Lists;
//import com.google.gson.JsonArray;
//import com.google.gson.JsonObject;
//import org.act.temporalProperty.exception.TPSNHException;
//import org.act.temporalProperty.impl.index.singleval.SourceCompare;
//import org.act.temporalProperty.index.value.rtree.*;
//import org.act.temporalProperty.util.DataFileImporter;
//import org.act.temporalProperty.util.Slice;
//import org.junit.Before;
//import org.junit.Test;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.io.BufferedWriter;
//import java.io.File;
//import java.io.FileWriter;
//import java.io.IOException;
//import java.util.ArrayList;
//import java.util.Comparator;
//import java.util.List;
//
///**
// * Created by song on 2018-03-30.
// */
//public class RTreeTest {
//    private static Logger log = LoggerFactory.getLogger(RTreeTest.class);
//
//    private static DataFileImporter dataFileImporter;
//    private SourceCompare sourceEntry;
//
//    private static String dbDir;
//    private static String dataPath;
//    private static List<File> dataFileList;
//
//    List<Integer> proIds = new ArrayList<>(); // the list of the proIds which will be indexed and queried
//
//    RTree tree;
//
//    @Before
//    public void buildRTree() throws IOException {
//
//        dataFileImporter = new DataFileImporter(280);
//        dbDir = dataFileImporter.getDbDir();
//        dataPath = dataFileImporter.getDataPath();
//        dataFileList = dataFileImporter.getDataFileList();
//        sourceEntry = new SourceCompare(dataPath, dataFileList, 100);
//
//        //List<IndexEntry> tmp = sourceCompare.queryBySource(300, 320, 200, 300);
//        List<IndexEntry> tmp = querySource(30, 3200, 100, 300);
//        log.info("{}", tmp.size());
////        log.info("{}", tmp);
////        IndexEntryOperator op = new IndexEntryOperator(Lists.newArrayList(IndexValueType.INT), 40);
//        IndexEntryOperator op = new IndexEntryOperator(Lists.newArrayList(IndexValueType.INT), 4096);
//        tree = new RTree(tmp, op);
//    }
//
//    private List<IndexEntry> querySource(int timeMin, int timeMax, int valMin, int valMax) {
//        int[][] pValueIntervals = new int[1][2];
//        pValueIntervals[0][0] = valMin;
//        pValueIntervals[0][1] = valMax;
//        proIds.add(0);
//        List<IndexEntry> result = sourceEntry.queryBySource(timeMin, timeMax, proIds, pValueIntervals);
////        List<IndexEntry> result = new ArrayList<>();
//        for(int i=0; i<result.size(); i++){
//            IndexEntry entry = result.get(i);
//            int start,end;
//            boolean needUpdate=false;
//            if(entry.getStart()<timeMin){
//                start = timeMin;
//                needUpdate = true;
//            }else{
//                start = entry.getStart();
//            }
//            if(entry.getEnd()>timeMax){
//                end = timeMax;
//                needUpdate = true;
//            }else{
//                end = entry.getEnd();
//            }
//            if(needUpdate) result.set(i, new IndexEntry(entry.getEntityId(), start, end, new Slice[]{entry.getValue(0)}));
//        }
//        result.sort((o1, o2) -> {
//            int r = Integer.compare(o1.getStart(), o2.getStart());
//            if(r==0){
//                r = Integer.compare(o1.getEnd(), o2.getEnd());
//                if(r==0){
//                    return o1.getValue(0).compareTo(o2.getValue(0));
//                }else{
//                    return r;
//                }
//            }else{
//                return r;
//            }
//        });
//        return result;
//    }
//
//    @Test
//    public void test(){
//        dumpTree(tree);
//        for(List<RTreeNode> level : tree.getLevels()){
//            log.debug("bound overlap count {}", overlap(level));
//        }
//    }
//
//    private int overlap(List<RTreeNode> level){
//        int overlapCount=0;
//        for (int i = 0; i < level.size() - 1; i++) {
//            for (int j = i + 1; j < level.size(); j++) {
//                RTreeRange bound_i = level.get(i).getBound();
//                RTreeRange bound_j = level.get(j).getBound();
//                if (bound_i.overlap(bound_j)) {
//                    if(overlapOnlyOneValue(bound_i, bound_j)){
//                        log.debug("not real overlap, only overlapped one value");
//                    }else{
//                        log.debug("{} {}", bound_i, bound_j);
//                        overlapCount++;
//                    }
////                    throw new RuntimeException("bound overlap " + i + " " + j);
//                }
//            }
//        }
//        return overlapCount;
//    }
//
//    private boolean overlapOnlyOneValue(RTreeRange r0, RTreeRange r1){
//        return (overlappedValCount(r0.getMax(), r1.getMin()) + overlappedValCount(r0.getMin(), r1.getMax()))==1;
//    }
//
//    private int overlappedValCount(IndexEntry r0, IndexEntry r1){
//        int overlappedValCount = 0;
//        if(r0.getStart()==r1.getStart()) overlappedValCount++;
//        if(r0.getEnd()==r1.getEnd()) overlappedValCount++;
//        if(r0.getValue(0).getInt(0)==r1.getValue(0).getInt(0)) overlappedValCount++;
//        return overlappedValCount>0?1:0;
//    }
//
//    private void dumpTree(RTree tree){
////        dfs(tree.getRoot(), 0);
//        str2file("tree", dfs(tree.getRoot()).toString());
//    }
//
//    private void dfs(RTreeNode node, int level){
//        for(int i=0; i<level; i++) System.out.print("|---");
//        System.out.print(node.getBound()+" CHILDREN SIZE ");
//        if(!node.isLeaf()){
//            System.out.println(node.getChildren().size());
//            for(RTreeNode child : node.getChildren()){
//                dfs(child, level+1);
//            }
//        }else{
//            System.out.println(node.getEntries().size());
//            for(IndexEntry entry : node.getEntries()){
//                for(int i=0; i<level+1; i++) System.out.print("|---");
//                System.out.println(entry);
//            }
//        }
//    }
//
//    private JsonObject dfs(RTreeNode node){
//        JsonObject obj = new JsonObject();
//        obj.add("min", entry2jsonArr(node.getBound().getMin()));
//        obj.add("max", entry2jsonArr(node.getBound().getMax()));
//        JsonArray children = new JsonArray();
//        if(!node.isLeaf()){
//            for(RTreeNode child : node.getChildren()){
//                children.add(dfs(child));
//            }
//        }else{
//            for(IndexEntry entry : node.getEntries()){
//                children.add(entry2jsonArr(entry));
//            }
//        }
//        obj.add("children", children);
//        return obj;
//    }
//
//    private JsonArray entry2jsonArr(IndexEntry entry) {
//        JsonArray arr = new JsonArray();
//        arr.add(entry.getStart());
//        arr.add(entry.getEnd());
//        arr.add(entry.getValue(0).getInt(0));
//        return arr;
//    }
//
//    private void str2file(String fileName, String data){
//        try {
//            BufferedWriter w = new BufferedWriter(new FileWriter(new File("/tmp/"+fileName+".json")));
//            w.write(data);
//            w.flush();
//            w.close();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
//
//    public class RTree {
//
//        private final IndexEntryOperator op;
//        private final RTreeNode root;
//        private final int bData;
//        private final int bIndex;
//        private final List<List<RTreeNode>> levels = new ArrayList<>();
//
//        public RTree(List<IndexEntry> data, IndexEntryOperator op){
//            this.op = op;
//            this.bData = op.dataBlockCapacity();
//            this.bIndex = op.indexBlockCapacity();
//            this.root = packNode(packData(data));
//        }
//
//        public RTreeNode getRoot(){
//            return root;
//        }
//
//        public List<List<RTreeNode>> getLevels() {
//            return levels;
//        }
//
//        private List<RTreeNode> packData(List<IndexEntry> data){
//            this.sortData(data);
//            List<RTreeNode> dataLevel = new ArrayList<>();
//            for(int i = 0; i<data.size(); i+= bData){
//                int end = (i+bData>data.size())? data.size() : i+bData;
//                RTreeNode node = new RTreeLeafNode(data.subList(i, end), this.op);
//                dataLevel.add(node);
//            }
//            this.levels.add(dataLevel);
//            return dataLevel;
//        }
//
//        private RTreeNode packNode(List<RTreeNode> nodes){
//            this.sortNodes(nodes);
//            List<RTreeNode> upperLevelNodes = new ArrayList<>();
//            for(int i = 0; i<=nodes.size(); i+= bIndex) {
//                int end = (i+bIndex>nodes.size())? nodes.size() : i+bIndex;
//                RTreeNode node = new RTreeIndexNode(nodes.subList(i, end), this.op);
//                upperLevelNodes.add(node);
//            }
////        RTree.log.info("one level packed, ({}) nodes", upperLevelNodes.size());
//            if(upperLevelNodes.size()>1){
//                this.levels.add(upperLevelNodes);
//                return packNode(upperLevelNodes);
//            }else if(upperLevelNodes.size()==1){
//                return upperLevelNodes.get(0);
//            }else{
//                throw new TPSNHException("nothing to pack");
//            }
//        }
//
//
//        //------ utils for node(RTreeNode)---------
//        private void sortNodes(List<RTreeNode> data) {
//            data.sort(nodeComparator( this.op ));
////            nodeRecursiveSort(data, 0, data.size(), 0, this.op.dimensionCount());
//        }
//
////        private void nodeRecursiveSort(List<RTreeNode> data, int left, int right, int corIndex, int k) {
////            data.subList(left, right).sort(nodeComparator(this.op, corIndex));
////            if(k>1) {
////                int r = right - left; // total entry count
////                int p = r / bIndex + (r % bIndex == 0 ? 0 : 1); // total block count
////                int s = (int) Math.round(Math.ceil(Math.pow(p, 1d / k))); // block count at current dimension (corIndex).
////                int groupLen = s * bIndex; // group
////                for (int i = 0; i < s; i++) {
////                    int start = i*groupLen+left;
////                    int endTmp = (i+1)*groupLen+left;
////                    int end = endTmp>right ? right : endTmp;
////                    if(start<end) nodeRecursiveSort(data, start, end, corIndex + 1, k - 1);
////                }
////            }
////        }
//
//        private Comparator<RTreeNode> nodeComparator(IndexEntryOperator op) {
//            int totalDim = op.dimensionCount();
//            return ( o1, o2 ) ->
//            {
//                int dimIndex = 0;
//                int r = op.compareRange( o1.getBound(), o2.getBound(), dimIndex );
//                for(dimIndex++; r==0 && dimIndex<totalDim; dimIndex++){
//                    r = op.compareRange( o1.getBound(), o2.getBound(),  dimIndex);
//                }
//                return r;
//            };
//        }
//
//        //------ utils for data(IndexEntry)---------
//        private void sortData(List<IndexEntry> data) {
//            data.sort(sliceComparator( this.op ));
////            dataRecursiveSort(data, 0, data.size(), 0, this.op.dimensionCount());
//            JsonArray arr = new JsonArray();
//            for(IndexEntry entry : data){
//                arr.add(entry2jsonArr(entry));
//            }
//            str2file("order", arr.toString());
////            System.out.println("LEAF DATA ORDER: "+arr);
//        }
//
////        private void dataRecursiveSort(List<IndexEntry> data, int left, int right, int corIndex, int k) {
////            data.subList(left, right).sort(sliceComparator(this.op, corIndex));
////            if(k>1) {
////                int r = right - left; // total entry count
////                int p = r / bData + (r % bData == 0 ? 0 : 1); // total block count
////                int s = (int) Math.round(Math.ceil(Math.pow(p, 1d / k))); // block count at current dimension (corIndex).
////                int groupLen = s * bData; // group
////                for (int i = 0; i < s; i++) {
////                    int start = i*groupLen+left;
////                    int endTmp = (i+1)*groupLen+left;
////                    int end = endTmp>right ? right : endTmp;
////                    if(start<end) dataRecursiveSort(data, start, end, corIndex + 1, k - 1);
////                }
////            }
////        }
//
//        private Comparator<IndexEntry> sliceComparator(IndexEntryOperator op) {
//            int totalDim = op.dimensionCount();
//            return ( o1, o2 ) ->
//            {
//                int dimIndex = 0;
//                int r = op.compare( o1, o2, dimIndex );
//                for(dimIndex++; r==0 && dimIndex<totalDim; dimIndex++){
//                    r = op.compare( o1, o2,  dimIndex);
//                }
//                return r;
//            };
//        }
//
//    }
//}
