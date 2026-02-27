package edu.buaa.common.transaction;

import edu.buaa.utils.Helper;
import edu.buaa.utils.Pair;

import java.util.*;
import java.util.stream.Collectors;

public class ReachableAreaQueryTx extends AbstractTransaction {
    public static boolean DEBUG = false;

    private String startNode;
    private String prop;
    private int departureTime;
    private int travelTime;

    public ReachableAreaQueryTx(){this.setTxType(TxType.tx_query_reachable_area);}

    public String getStartNode() {
        return startNode;
    }

    public int getDepartureTime() {
        return departureTime;
    }

    public int getTravelTime() {
        return travelTime;
    }

    public void setStartNode(String startNode) {
        this.startNode = startNode;
    }

    public void setDepartureTime(int departureTime) {
        this.departureTime = departureTime;
    }

    public void setTravelTime(int travelTime) {
        this.travelTime = travelTime;
    }

    public String getProp() {
        return prop;
    }

    public void setProp(String prop) {
        this.prop = prop;
    }

    @Override
    public boolean validateResult(AbstractTransaction.Result result){
        Helper.validateResult(((Result) this.getResult()).getNodeArriveTime(),
                ((Result) result).getNodeArriveTime());
//        List<InnerResult> my = ((Result) this.getResult()).getInnerResults().stream().filter(i ->
//                        i instanceof EarliestArrTime
////                        i instanceof Node2Rel
////                        i instanceof RelEndNode
//        ).collect(Collectors.toList());
//        List<InnerResult> other = ((Result) result).getInnerResults().stream().filter(i->
//                        i instanceof EarliestArrTime
////                        i instanceof Node2Rel
////                        i instanceof RelEndNode
//        ).collect(Collectors.toList());
//        try {
//            Helper.validateResult(my, other);
//        }catch (Helper.SetNotMath e){
//            System.out.println("not match");
//            Helper.validateResult(toMap((List<EarliestArrTime>) e.a), toMap((List<EarliestArrTime>) e.b));
////            Helper.validateResult(to2Map((List<Node2Rel>) e.a), to2Map((List<Node2Rel>) e.b));
////            Helper.validateResult(to22Map((List<RelEndNode>) e.a), to22Map((List<RelEndNode>) e.b));
//        }
//        int cnt = Math.min(my.size(), other.size());
//        StringBuilder sb = new StringBuilder();
//        if(my.size()!=other.size()) sb.append("size not eq <").append(my.size()).append(" vs ").append(other.size()).append(">");
//        for(int i=0; i<cnt; i++){
//            if(!Objects.equals(my.get(i), other.get(i))) {
//                // search backward.
//                InnerResult curMy = my.get(i), curOther = other.get(i);
//                int occurMy=-1, occurOther=-1;
//                if(curMy instanceof Node2Rel && curOther instanceof Node2Rel){
//                    occurMy = searchBackEndNode(((Node2Rel) curMy).node, my, i);
//                    occurOther = searchBackEndNode(((Node2Rel) curOther).node, other, i);
//                }
//                throw new Helper.SetNotMath("not eq at step "+i+": \n"+
//                        my.get(i)+" occur at "+my.get(occurMy)+"(step "+occurMy+")\n"+
//                        other.get(i)+" occur at "+other.get(occurOther)+"(step "+occurOther+")", my, other, i);
////                System.out.println("not eq at step "+i+": "+my.get(i)+" vs "+other.get(i));
////                return false;
//            }
//        }
        return true;
    }

    @Override
    protected boolean infoIsNode() {
        return false;
    }

    private HashMap<String, ArrayList<org.apache.commons.lang3.tuple.Pair<Integer, Integer>>> cacheForGetFineGrainedInfo = null;

    @Override
    protected HashMap<String, ArrayList<org.apache.commons.lang3.tuple.Pair<Integer, Integer>>> getFineGrainedInfo() {
        if (cacheForGetFineGrainedInfo == null) {
            cacheForGetFineGrainedInfo = new HashMap<>();
            ArrayList<org.apache.commons.lang3.tuple.Pair<Integer, Integer>> temp = new ArrayList<>();
            temp.add(org.apache.commons.lang3.tuple.Pair.of(departureTime, departureTime + travelTime));
            cacheForGetFineGrainedInfo.put(prop, temp);
        }
        return cacheForGetFineGrainedInfo;
    }

    @Override
    protected HashMap<String, HashMap<String, ArrayList<org.apache.commons.lang3.tuple.Pair<Integer, Integer>>>> getFineGrainedInfoWithEntity() {
        return null;
    }

    @Override
    protected HashSet<String> getEntities() {
        return null;
    }

    private Map<String, String> to2Map(List<Node2Rel> lst) {
        return lst.stream().collect(Collectors.toMap(i-> i.node, i->i.relList.toString()));
    }

    private Map<String, String> to22Map(List<RelEndNode> lst) {
        return lst.stream().collect(Collectors.toMap(i-> i.rel, i->i.node));
    }

    private Map<String, Integer> toMap(List<EarliestArrTime> lst) {
        return lst.stream().collect(Collectors.toMap(i-> i.rel+"_"+i.depTime, EarliestArrTime::getArrTime));
    }

    public int searchBackEndNode(String node, List<InnerResult> lst, int cur){
        for(int j=cur-1; j>=0; j--){
            InnerResult item = lst.get(j);
            if(item instanceof RelEndNode && ((RelEndNode) item).node.equals(node)){
                return j;
            }
        }
        return -1;
    }

    public static class Result extends AbstractTransaction.Result{
        List<Pair<Integer, String>> nodeArriveTime;
        List<InnerResult> innerResults;
        StatResult statResult;

        public StatResult getStatResult() {
            return statResult;
        }

        public void setStatResult(StatResult statResult) {
            this.statResult = statResult;
        }

        public List<Pair<Integer, String>> getNodeArriveTime() {
            return nodeArriveTime;
        }

        public void setNodeArriveTime(List<Pair<Integer, String>> nodeArriveTime) {
            this.nodeArriveTime = nodeArriveTime;
        }

        public List<InnerResult> getInnerResults() {
            return innerResults;
        }

        public void setInnerResults(List<InnerResult> innerResults) {
            this.innerResults = innerResults;
        }
    }

    public abstract static class InnerResult implements Comparable<InnerResult>{
        private static int idSeq = 0;
        private int id = idSeq++;
        @Override
        public int compareTo(InnerResult o) {
            return Integer.compare(this.id, o.id);
        }
    }

    public static class Node2Rel extends InnerResult{
        private String node;
        private List<String> relList;

        public Node2Rel(String node, List<String> rrr) {
            this.node = node;
            this.relList = rrr;
            if(DEBUG) System.out.println("Node2Rel("+node+")->"+rrr);
        }

        public Node2Rel(){}

        public String getNode() {
            return node;
        }

        public void setNode(String node) {
            this.node = node;
        }

        public List<String> getRelList() {
            return relList;
        }

        public void setRelList(List<String> relList) {
            this.relList = relList;
        }

        @Override
        public String toString() {
            return "Node2Rel{" + node + "=" + relList + '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Node2Rel node2Rel = (Node2Rel) o;
            return node.equals(node2Rel.node) && relList.equals(node2Rel.relList);
        }

        @Override
        public int hashCode() {
            return Objects.hash(node, relList);
        }
    }

    public static class RelEndNode extends InnerResult{
        private String rel;
        private String node;

        public RelEndNode(String rel, String node) {
            this.rel = rel;
            this.node = node;
            if(DEBUG) System.out.println("RelEndNode("+rel+")->"+node);
        }

        public RelEndNode(){}

        public String getRel() {
            return rel;
        }

        public void setRel(String rel) {
            this.rel = rel;
        }

        public String getNode() {
            return node;
        }

        public void setNode(String node) {
            this.node = node;
        }

        @Override
        public String toString() {
            return "RelEndNode{" +rel + "=" + node + '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RelEndNode that = (RelEndNode) o;
            return rel.equals(that.rel) && node.equals(that.node);
        }

        @Override
        public int hashCode() {
            return Objects.hash(rel, node);
        }
    }

    public static class EarliestArrTime extends InnerResult{
        private String rel;
        private int depTime;
        private int arrTime;

        public EarliestArrTime(String str, int departureTime, int arrT) {
            this.rel = str;
            this.depTime = departureTime;
            this.arrTime = arrT;
            if(DEBUG) System.out.println("EarliestArrTime("+rel+", "+departureTime+")->"+arrT);
        }

        public EarliestArrTime(){}

        public String getRel() {
            return rel;
        }

        public void setRel(String rel) {
            this.rel = rel;
        }

        public int getDepTime() {
            return depTime;
        }

        public void setDepTime(int depTime) {
            this.depTime = depTime;
        }

        public int getArrTime() {
            return arrTime;
        }

        public void setArrTime(int arrTime) {
            this.arrTime = arrTime;
        }

        @Override
        public String toString() {
            return "EarliestArrTime{"+ rel + "(" + depTime + ")=" + arrTime + '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            EarliestArrTime that = (EarliestArrTime) o;
            return depTime == that.depTime && arrTime == that.arrTime && rel.equals(that.rel);
        }

        @Override
        public int hashCode() {
            return Objects.hash(rel, depTime, arrTime);
        }
    }

    public static class StatResult extends AbstractTransaction.Result{
        private long getAllOutRelCost;
        private int getAllOutRelCnt;

        private long getArrTimeCost;
        private int getArrTimeCnt = 0;

        private long getEndNodeCost;
        private int getEndNodeCnt;

        private int maxPathLength;
        private int nodeCnt;

        public int getNodeCnt() {
            return nodeCnt;
        }

        public void setNodeCnt(int nodeCnt) {
            this.nodeCnt = nodeCnt;
        }

        public int getMaxPathLength() {
            return maxPathLength;
        }

        public void setMaxPathLength(int maxPathLength) {
            this.maxPathLength = maxPathLength;
        }

        public long getGetAllOutRelCost() {
            return getAllOutRelCost;
        }

        public void setGetAllOutRelCost(long getAllOutRelCost) {
            this.getAllOutRelCost = getAllOutRelCost;
        }

        public int getGetAllOutRelCnt() {
            return getAllOutRelCnt;
        }

        public void setGetAllOutRelCnt(int getAllOutRelCnt) {
            this.getAllOutRelCnt = getAllOutRelCnt;
        }

        public long getGetArrTimeCost() {
            return getArrTimeCost;
        }

        public void setGetArrTimeCost(long getArrTimeCost) {
            this.getArrTimeCost = getArrTimeCost;
        }

        public int getGetArrTimeCnt() {
            return getArrTimeCnt;
        }

        public void setGetArrTimeCnt(int getArrTimeCnt) {
            this.getArrTimeCnt = getArrTimeCnt;
        }

        public long getGetEndNodeCost() {
            return getEndNodeCost;
        }

        public void setGetEndNodeCost(long getEndNodeCost) {
            this.getEndNodeCost = getEndNodeCost;
        }

        public int getGetEndNodeCnt() {
            return getEndNodeCnt;
        }

        public void setGetEndNodeCnt(int getEndNodeCnt) {
            this.getEndNodeCnt = getEndNodeCnt;
        }
    }

    /**
     * Generalized Dijkstra Algorithm used for calculation. Dreyfus 1969, page 29
     */
    public static abstract class TemporalDijkstraAlgo{
        private final Map<Long, NodeCross> nodeCrossMap = new HashMap<>();
        private final long start;
        private final int startTime;
        protected final int endTime;
        protected final List<InnerResult> innerResults;
        public final StatResult statResult = new StatResult();

        public TemporalDijkstraAlgo(long startNodeId, int startTime, int travelTime){
            this.start = startNodeId;
            this.startTime = startTime;
            this.endTime = startTime + travelTime;
            this.innerResults = null;
        }

        public TemporalDijkstraAlgo(long startNodeId, int startTime, int travelTime, boolean debug){
            this.start = startNodeId;
            this.startTime = startTime;
            this.endTime = startTime + travelTime;
            if(DEBUG){
                this.innerResults = new ArrayList<>();
                System.out.println("TemporalDijkstraAlgo("+startNodeId+", "+startTime+", "+travelTime+")===================");
            }else{
                this.innerResults = null;
            }
        }

        public Set<NodeCross> run() {
            NodeCross startNode = getNodeCross(start);
            setStatus(startNode, Status.Calculating);
            startNode.arriveTime = startTime;
            startNode.parent = start;

            Set<NodeCross> result = new HashSet<>();
            NodeCross node;
            while ((node = smallestCalculatingNode())!=null) {
                loopAllNeighborsUpdateArriveTime(node, result);
            }
            statResult.maxPathLength = calcMaxPathLen(result, startNode, nodeCrossMap);
            statResult.nodeCnt = result.size();
            return result;
        }

        private int calcMaxPathLen(Set<NodeCross> result, NodeCross startNode, Map<Long, NodeCross> nodeCrossMap) {
            OptionalInt res = result.parallelStream().mapToInt((node) -> {
                int len = 1;
                NodeCross cursor = node;
                while (cursor.parent != startNode.id) {
                    len++;
                    cursor = nodeCrossMap.get(cursor.parent);
                }
                return len;
            }).max();
            if(res.isPresent()) return res.getAsInt();
            else return 0;
        }

        /**
         * loop through all neighbors of a given node, and for each neighbor node:
         * 1. update its earliest arrive time
         * 2. set parent to source node
         * 3. mark node status to CLOSE
         * after the loop above, mark given node status to FINISH
         */
        private void loopAllNeighborsUpdateArriveTime(NodeCross node, Set<NodeCross> result) {
            setStatus( node, Status.Calculated );
            int curTime = node.arriveTime;
            if( curTime > endTime ) return;
            result.add( node );
            for( Long rId : getOutRoads( node.getId() )){
                NodeCross neighbor = getNodeCross( getEndNode( rId ));
                int arriveTime;
                try {
                    switch (neighbor.status) {
                        case NotCalculate:
                            neighbor.arriveTime = getEarliestArrTime(rId, curTime);
                            neighbor.parent = node.getId();
                            setStatus(neighbor, Status.Calculating);
                            break;
                        case Calculating:
                            arriveTime = getEarliestArrTime(rId, curTime);
                            if (neighbor.arriveTime > arriveTime) {
                                neighbor.arriveTime = arriveTime;
                                neighbor.parent = node.getId();
                            }
                            break;
                    }
                }catch (UnsupportedOperationException ignore){}
            }
        }

        /**
         * Use 'earliest arrive time' rather than simply use 'travel time' property at departureTime
         * Because there exist cases that 'a delay before departureTime decrease the time of
         * arrival'.(eg. wait until road not jammed, See Dreyfus 1969, page 29)
         * This makes the arrive-time-function non-decreasing, thus guarantee FIFO property of this temporal network.
         * This property is the foundational assumption to found earliest arrive time with this algorithm.
         * @param roadId road id.
         * @param departureTime time start from r's start node.
         * @return earliest arrive time to r's end node when departure from r's start node at departureTime.
         */
        protected abstract int getEarliestArriveTime(Long roadId, int departureTime) throws UnsupportedOperationException;
        protected abstract Iterable<Long> getAllOutRoads( long nodeId );
        protected abstract long getEndNodeId( long roadId );

        // method used for debug.
        private int getEarliestArrTime(Long roadId, int departureTime) throws UnsupportedOperationException{
            try {
                long t0 = System.currentTimeMillis();
                int arrT = getEarliestArriveTime(roadId, departureTime);
                statResult.getArrTimeCost += System.currentTimeMillis() - t0;
                statResult.getArrTimeCnt ++;
                if(innerResults!=null) innerResults.add(new EarliestArrTime(relId2Str(roadId), departureTime, arrT));
                return arrT;
            }catch (UnsupportedOperationException e){
                if(innerResults!=null) innerResults.add(new EarliestArrTime(relId2Str(roadId), departureTime, -1));
                throw e;
            }
        }

        private long getEndNode( long roadId ){
            long t0 = System.currentTimeMillis();
            long endNodeId = getEndNodeId( roadId );
            statResult.getEndNodeCost += System.currentTimeMillis() - t0;
            statResult.getEndNodeCnt ++;
            if(innerResults!=null) innerResults.add(new RelEndNode(relId2Str(roadId), nodeId2Str(endNodeId)));
            return endNodeId;
        }

        private Iterable<Long> getOutRoads( long nodeId ){
            long t0 = System.currentTimeMillis();
            Iterable<Long> r = getAllOutRoads(nodeId);
            statResult.getAllOutRelCost += System.currentTimeMillis() - t0;
            statResult.getAllOutRelCnt ++;
            if(innerResults!=null) {
                List<Long> rr = new ArrayList<>();
                List<String> rrr = new ArrayList<>();
                for(Long relId : r) {
                    rr.add(relId);
                }
                rr.sort(Comparator.naturalOrder());
                for(Long relId : rr){
                    rrr.add(relId2Str(relId));
                }
                innerResults.add(new Node2Rel(nodeId2Str(nodeId), rrr));
                return rr;
            }else{
                return r;
            }
        }

        protected String nodeId2Str( long nodeId ){
            return "";
        }

        protected String relId2Str( long relId ){
            return "";
        }

        private final HashSet<NodeCross> calculatingNodes = new HashSet<>();

        private NodeCross smallestCalculatingNode() {
            NodeCross min = null;
            int minTime = Integer.MAX_VALUE;
            for(NodeCross n : calculatingNodes){
                if(n.arriveTime < minTime) {
                    minTime = n.arriveTime;
                    min = n;
                }
            }
            return min;
        }

        private void setStatus(NodeCross node, Status status){
            node.status = status;
            if (status == Status.Calculating) {
                calculatingNodes.add(node);
            }else if( status == Status.Calculated){
                calculatingNodes.remove(node);
            }
        }

        private NodeCross getNodeCross(long id) {
            NodeCross node = nodeCrossMap.get(id);
            if( node == null ){
                node = new NodeCross(id);
                nodeCrossMap.put(id, node);
            }
            return node;
        }

        public List<InnerResult> getInnerResults() {
            return innerResults;
        }

        protected enum Status{ NotCalculate, Calculating, Calculated }

        public static class NodeCross implements Comparable<NodeCross>{
            public long id;
            int arriveTime = Integer.MAX_VALUE;
            long parent;
            private Status status = Status.NotCalculate;
            NodeCross(){}
            NodeCross(long id) {
                this.id = id;
            }
            public NodeCross(long id, int arriveTime) {
                this.id = id;
                this.arriveTime = arriveTime;
            }

            public long getId() {
                return id;
            }

            public void setId(long id) {
                this.id = id;
            }

            public int getArriveTime() {
                return arriveTime;
            }

            public void setArriveTime(int arriveTime) {
                this.arriveTime = arriveTime;
            }

            public long getParent() {
                return parent;
            }

            public void setParent(long parent) {
                this.parent = parent;
            }

            @Override
            public String toString() {
                return "NodeCross{" +
                        "id=" + id +
                        ", arriveTime=" + arriveTime +
                        ", parent=" + parent +
                        '}';
            }

            @Override
            public int compareTo(NodeCross o) {
                int r = Long.compare(this.id, o.id);
                if(r==0) return Integer.compare(this.arriveTime, o.arriveTime);
                else return r;
            }
        }
    }
}
