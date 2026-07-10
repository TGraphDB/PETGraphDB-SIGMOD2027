package edu.buaa.client;

import com.google.common.util.concurrent.ListenableFuture;
import edu.buaa.common.client.AbstractBoltClient;
import edu.buaa.common.transaction.*;
import edu.buaa.common.utils.PFieldList;
import edu.buaa.common.utils.PVal;
import edu.buaa.utils.Triple;
import org.neo4j.driver.*;

import java.sql.SQLException;
import java.util.*;

public class TCypherBoltClient extends AbstractBoltClient {
    private static final String NODE_LABEL = "test";
    private static final String REL_TYPE = "test";
    private Driver driver;

//    @Override
//    protected ServerResponse onResponse(String query, String response, TimeMonitor timeMonitor, Thread thread) throws Exception {
//        return null;
//    }
    @Override
    public ListenableFuture<ServerResponse> execute(AbstractTransaction tx) throws InterruptedException {
        switch (tx.getTxType()) {
            case tx_import_static_data:
                return this.submit(execute((ImportStaticDataTx) tx));
            case tx_import_temporal_data:
                return this.submit(execute((ImportTemporalDataTx) tx), tx.getSection());
//            case tx_query_snapshot:
//                return this.submit(execute((SnapshotQueryTx) tx));
//            case tx_query_snapshot_aggr_max:
//                return this.submit(execute((SnapshotAggrMaxTx) tx));
            case tx_query_entity_history:
                return this.submit(execute((EntityHistoryTx) tx));
//            case tx_query_road_by_temporal_condition:
//                return this.submit(execute((EntityTemporalConditionTx) tx));
//            case tx_query_reachable_area:
//                return this.submit(execute((ReachableAreaQueryTx) tx));
            case tx_update_temporal_data:
                return this.submit(s->{
                    throw new TransactionFailedException();
                });
            case tx_query_snapshot_aggr_duration:
            default:
                throw new UnsupportedOperationException();
        }
    }

    private Req execute(ImportStaticDataTx tx) {
        return session -> {
            PFieldList nodesData = tx.getNodes();
            List<String> nProps = new ArrayList<>(nodesData.keys());
            int nSize = nodesData.size();
            PFieldList relData = tx.getRels();
            int rSize = relData.size();
            List<String> rProps = new ArrayList<>(relData.keysWithout("r_from", "r_to", "u_sid"));
            List<Map<String, Object>> nodeBatch = new ArrayList<>(nSize);
            for(int i=0; i<nSize; i++){
                Map<String, Object> node = new HashMap<>();
                for(String key:nProps){
                    node.put(key, nodesData.get(key, i).getVal());
                }
                nodeBatch.add(node);
            }
            String nodeCypher = String.format(
                    "UNWIND $batch AS row " +
                            "CREATE (n:Node) SET n += row "
            );
            session.run(nodeCypher, Collections.singletonMap("batch", nodeBatch)).consume();

            List<Map<String, Object>> relBatch = new ArrayList<>(rSize);
            for (int i = 0; i < rSize; i++) {
                Map<String, Object> row = new HashMap<>();
                row.put("u_sid", relData.get("u_sid", i));
                row.put("from", relData.get("r_from", i));
                row.put("to", relData.get("r_to", i));
                Map<String, Object> props = new HashMap<>();
                for (String key : rProps) {
                    props.put(key, relData.get(key, i).getVal());
                }
                row.put("props", props);

                relBatch.add(row);
            }
            String relCypher = String.format(
                    "UNWIND $batch AS row " +
                            "MATCH (a:Node {id: row.from}), (b:Node {id: row.to}) " +
                            "CREATE (a)-[r:Edge {u_sid: row.u_sid}]->(b) " +
                            "SET r += row.props"
            );
            session.run(relCypher, Collections.singletonMap("batch", relBatch)).consume();
            return new AbstractTransaction.Result();
        };
    }

    private Req execute(ImportTemporalDataTx tx) {
        return session -> {
            PFieldList data = tx.getData();
            Set<String> props = data.keysWithout("u_sid", "t");
            int size = data.size();

            for (int i = 0; i < size; i++) {
                String uSid = data.get("u_sid", i).s();
                int timeBegin = data.get("t", i).i();
                for (String prop : props) {
                    Object rawValue = data.get(prop, i).getVal();
                    PVal.Type type = schema.getType(tx.isNode(), false, prop);
                    Object value = normalizeValue(tx.isNode(), prop, rawValue);
                    String cypher = setTPCypher(tx.isNode(), prop);
                    Map<String, Object> params = new HashMap<>();
                    params.put("u_sid", uSid);
                    params.put("property", prop);
                    params.put("timeBegin", timeBegin);
                    params.put("value", value);
                    session.run(cypher, params).consume();
                }
            }
            return new AbstractTransaction.Result();
        };
    }

    private String setTPCypher(boolean isNode, String prop) {
        String prefix = isNode
                ? String.format("MATCH (e:%s {u_sid: $u_sid}) ", NODE_LABEL)
                : String.format("MATCH ()-[e:%s {u_sid: $u_sid}]->() ", REL_TYPE);
//                ? String.format("MATCH (e {u_sid: $u_sid}) ")
//                : String.format("MATCH ()-[e {u_sid: $u_sid}]->() ");
        PVal.Type type = schema.getType(isNode, false, prop);
        String valueExpr;
        if (type == PVal.Type.INT) {
            valueExpr = "toInteger($value)";
        } else if (type == PVal.Type.FLOAT) {
            // Cypher toFloat() yields a double-like numeric, which can break strict FLOAT temporal types.
            // Keep the Java-side normalized Float as-is.
            valueExpr = "$value";
        } else {
            valueExpr = "$value";
        }
        return prefix +
                "WITH e " +
                "CALL tp.set(e, $property, $timeBegin, " + valueExpr + ") YIELD count " +
                "RETURN count";
    }

    private Req execute(EntityHistoryTx tx) {
        return session -> {
            String cypher = tx.isNode()
                    ? String.format("MATCH (e:%s {u_sid: $u_sid}) ", NODE_LABEL) 
                    : String.format("MATCH ()-[e:%s {u_sid: $u_sid}]->() ", REL_TYPE);
            cypher += "WITH e " +
                    "CALL tp.get(e, $property, $beginTime, $endTime) YIELD timeBegin, timeEnd, value " +
                    "RETURN timeBegin, timeEnd, value";

            Map<String, Object> params = new HashMap<>();
            params.put("u_sid", tx.getEntity());
            params.put("property", tx.getProp());
            params.put("beginTime", tx.getBeginTime());
            params.put("endTime", tx.getEndTime());

            List<Triple<Integer, Integer, PVal>> history = new ArrayList<>();
            Result queryResult = session.run(cypher, params);
            while (queryResult.hasNext()) {
                Record record = queryResult.next();
                Value beginValue = record.get("timeBegin");
                Value endValue = record.get("timeEnd");
                Value valueValue = record.get("value");
                if (beginValue.isNull() || endValue.isNull() || valueValue.isNull()) {
                    continue;
                }
                history.add(Triple.of(
                        beginValue.asInt(),
                        endValue.asInt(),
                        PVal.v(normalizeValue(tx.isNode(), tx.getProp(), valueValue.asObject()))
                ));
            }

            EntityHistoryTx.Result result = new EntityHistoryTx.Result();
            result.setHistory(history);
            return result;
        };
    }

    private Object normalizeValue(boolean isNode, String prop, Object value) {
        if (value == null) {
            return null;
        }
        PVal.Type type = schema.getType(isNode, false, prop);
        if (type == null) {
            return value;
        }
        switch (type) {
            case INT:
                if (!(value instanceof Number)) {
                    throw new IllegalArgumentException("expect numeric value for int temporal property: " + prop);
                }
                return ((Number) value).intValue();
            case FLOAT:
                if (!(value instanceof Number)) {
                    throw new IllegalArgumentException("expect numeric value for float temporal property: " + prop);
                }
                return ((Number) value).floatValue();
            case STRING:
                return value.toString();
            default:
                return value;
        }
    }
//
//    private Req execute(SnapshotAggrMaxTx tx) throws InterruptedException {
//        return addQuery(format("MATCH {0} RETURN e.u_sid, tp_max(e.{1}, {2,number,#}, {3,number,#})",
//                tx.isNode()?"(e)":"()-[e:EDGE_TO]->()",
//                tx.getP(), tx.getT0(), tx.getT1()));
//    }
//
//    private Req execute(SnapshotAggrDurationTx tx) throws InterruptedException {
//        return addQuery(format("MATCH {0} RETURN e.u_sid, tp_duration(e.{1}, {2,number,#}, {3,number,#})",
//                tx.isNode()?"(e)":"()-[e:EDGE_TO]->()",
//                tx.getP(), tx.getT0(), tx.getT1()));
//    }
//
//    private Req execute(SnapshotQueryTx tx) throws InterruptedException {
//        return addQuery(format("MATCH {0} RETURN e.u_sid, tp_value_at(e.{1}, {2,number,#})",
//                tx.isNode()?"(e)":"()-[e:EDGE_TO]->()",
//                tx.getPropertyName(), tx.getTimestamp()));
//    }
//
//    private Req execute(EntityTemporalConditionTx tx) throws InterruptedException {
//        return addQuery(format("MATCH {0} WHERE tp_within_exists(e.{1}, {2,number,#}, {3,number,#}, {4}, {5}) RETURN e.u_sid",
//                tx.isNode()?"(e)":"()-[e:EDGE_TO]->()",
//                tx.getP(), tx.getT0(), tx.getT1(), tx.getVMin().getVal().toString(), tx.getVMax().getVal().toString()));
//    }

    @Override
    protected void connected(Session conn) throws Exception {
        System.out.println("server connected");
    }

    @Override
    protected void createDbIfNotExist(String serverHost, String dbName) throws SQLException, ClassNotFoundException {

    }

    @Override
    protected List<String> createTables() {
        return null;
    }

    @Override
    protected Session createNormalConnection(String serverHost, String dbName) throws SQLException {
        String connect = "bolt://" + this.serverHost + ":7687";
        driver = GraphDatabase.driver(connect, AuthTokens.basic("", ""));
        return driver.session(SessionConfig.forDatabase("neo4j"));
    }

    @Override
    public String currentStorageStatus() throws SQLException, InterruptedException {
        return null;
    }

    @Override
    protected String type2SQL(PVal.Type value) {
        return null;
    }
//
//    public static class Result extends AbstractTransaction.Result{
//        PFieldList results = new PFieldList();
//
//        public PFieldList getResults() {
//            return results;
//        }
//
//        public void setResults(PFieldList results) {
//            this.results = results;
//        }
//    }
//
//
//    private Req execute(ReachableAreaQueryTx tx){
//        ReachableAreaQueryTx.DEBUG = true;
//        return session -> {
//
//            List<Pair<Integer, String>> answers = new ArrayList<>();
//            String startNodeUid=tx.getStartNode();
//            long startNodeId=findNodeIdByUid(startNodeUid,session);
//            ReachableAreaAeonG algo = new ReachableAreaAeonG(startNodeId, tx.getDepartureTime(), tx.getTravelTime(),session,startNodeUid);
//
//            if(tx.getResult() instanceof ReachableAreaQueryTx.Result){
//                ReachableAreaQueryTx.StatResult result = ((ReachableAreaQueryTx.Result) tx.getResult()).getStatResult();
//                if(ReachableAreaQueryTx.DEBUG) algo.setCtrlMeta(result);
//            }
//
//            for (ReachableAreaQueryTx.TemporalDijkstraAlgo.NodeCross nodeCross : algo.run()) {
//                String u_sid = findUidByNodeId(nodeCross.getId(),session);
//                answers.add(Pair.of(nodeCross.getArriveTime(), u_sid));
//            }
//            algo.info();
//            ReachableAreaQueryTx.Result result = new ReachableAreaQueryTx.Result();
////            result.setNodeArriveTime(answers);
////            result.setInnerResults(algo.getInnerResults());
//            result.setStatResult(algo.statResult);
//            return result;
//        };
//    }
//    /**
//     * 通过u_sid返回点的内部id
//     */
//    private long findNodeIdByUid(String uid,Session session)
//    {
//        long res=-1;
//        Map<String, Object> params = new HashMap<>();
//        params.put("uid",uid);
//        String cypher=String.format("MATCH (n:%s) WHERE n.u_sid=$uid RETURN id(n) AS id",NodeLabel);
//        var result=session.run(cypher,params);
//        while (result.hasNext())
//        {
//            var record = result.next();
//            res=record.get("id").asLong();
//        }
//        if(res==-1)
//        {
//            String errorMsg = "\n未找到u_sid为[" + uid + "]的内部ID。"
//                    + "\n执行的Cypher语句：" + cypher;
//            throw new RuntimeException(errorMsg);
//        }
//        return res;
//    }
//
//    /**
//     * 通过内部id返回点的u_sid
//     */
//    private String findUidByNodeId(long nodeId,Session session)
//    {
//        String res=null;
//        Map<String, Object> params = new HashMap<>();
//        params.put("nodeId",nodeId);
//        String cypher=String.format("MATCH (n:%s) WHERE id(n)=$nodeId RETURN n.u_sid AS uid",NodeLabel);
//        var result=session.run(cypher,params);
//        while (result.hasNext())
//        {
//            var record = result.next();
//            res=record.get("uid").asString();
//        }
//        if(res==null)
//        {
//            String errorMsg = "\n未找到nodeId为[" + nodeId + "]的u_sid。"
//                    + "\n执行的Cypher语句：" + cypher;
//            throw new RuntimeException(errorMsg);
//        }
//        return res;
//    }
//    /**
//     * 通过内部id返回边的u_sid
//     */
//    private String findUidByRelId(long RelId,Session session)
//    {
//        String res=null;
//        Map<String, Object> params = new HashMap<>();
//        params.put("RelId",RelId);
//        String cypher=String.format("MATCH (n:%s) WHERE id(n)=$RelId RETURN n.u_sid AS uid",EdgeNodeLabel);
//        var result=session.run(cypher,params);
//        while (result.hasNext())
//        {
//            var record = result.next();
//            res=record.get("uid").asString();
//        }
//        if(res==null)
//        {
//            String errorMsg = "\n未找到RelId为[" + RelId + "]的u_sid。"
//                    + "\n执行的Cypher语句：" + cypher;
//            throw new TransactionFailedException(new RuntimeException(errorMsg));
//        }
//        return res;
//    }
//    public class ReachableAreaAeonG extends ReachableAreaQueryTx.TemporalDijkstraAlgo
//    {
//        private  Session session;
//        private String startNodeUid;
//        private int totalCntNodeOutRel = Integer.MAX_VALUE;
//        private int totalCntArrTime = Integer.MAX_VALUE;
//
//        private int cntNodeOutRel = 0;
//        private int cntArrTime = 0;
//
//
//        public ReachableAreaAeonG(long startId, int startTime, int travelTime,Session session,String startNodeUid) {
//            super(startId, startTime, travelTime, true);
//            this.session=session;
//            this.startNodeUid=startNodeUid;
//        }
//        @Override
//        protected int getEarliestArriveTime(Long roadId, int departureTime) throws UnsupportedOperationException {
//            if(ReachableAreaQueryTx.DEBUG) {
//                if(this.cntArrTime > this.totalCntArrTime) throw new UnsupportedOperationException();
//                this.cntArrTime++;
//            }
//            int earT = Integer.MAX_VALUE;
//            int depatureTimeT=VtoT(departureTime);
//            int end=VtoT(this.endTime);
//            Map<String, Object> params = new HashMap<>();
//            String roadUid=findUidByRelId(roadId,session);
//            params.put("roadUid", roadUid);
//            String cypher=String.format("MATCH (n:%s{u_sid:$roadUid}) TT FROM "+depatureTimeT+" TO " +end+" RETURN n ",EdgeNodeLabel);
////            System.out.println(cypher+"  running ");
//            var result=session.run(cypher,params);
//            int cnt = 0;
//            while (result.hasNext()) {
//                var record = result.next();
//                Node node = record.get("n").asNode();
//                Triple<Integer, Integer, PVal> triple = findDelAndNow(node, "travel_time", false);
//                if(triple==null||triple.getRight()==null)
//                {
//                    continue;
//                }
//                int st=Math.max(depatureTimeT,triple.getLeft());
//                int travel_time=(int)triple.getRight().getVal();
//                double arr_t=st+travel_time*k;
////                int arr_t=st+travel_time*30;
////                System.out.println("arr_t: "+arr_t);
//                if(arr_t<earT) earT= (int) Math.floor(arr_t);
//                cnt++;
//            }
//            if(earT<Integer.MAX_VALUE)
//            {
//                int res=TtoV(earT);
////                System.out.println(this.startNodeUid+" Road: "+roadUid+" earT: "+res+"   depature: "+depatureTimeT+"  endTime: "+end);
//                if(ReachableAreaQueryTx.DEBUG) {
//                    return randomRoll(cnt, departureTime, this.endTime, false);
//                }else{
//                    return res;
//                }
////                System.out.println(this.startNodeUid+" Road: "+roadUid+" earT: "+res+"   depatureT: "+depatureTimeT+" depatureT: "+departureTime+"  endTime: "+end);
////                return res;
////                System.out.println(this.startNodeUid+" Road: "+roadUid+" earT: "+res+"   depatureT: "+depatureTimeT+" depatureT: "+departureTime+"  endTime: "+end);
////                return res;
//            }
//            else throw new UnsupportedOperationException();
//        }
//
//        private int randomRoll(int historyCnt, int departureTime, int endTime, boolean canFinish) {
//            int delta = endTime - departureTime;
//            double r = Math.random();
//            if(canFinish) {
//                if (r > 0.5 && delta < 10) return endTime + 1;
//                else return (int) (r * delta) + departureTime;
//            }else{
//                if(r > 0.5) r *= 0.7;
//                return (int) (r * delta) + departureTime;
//            }
//        }
//
//        @Override
//        protected Iterable<Long> getAllOutRoads(long nodeId) {
//            if(ReachableAreaQueryTx.DEBUG) {
//                if(this.cntNodeOutRel > this.totalCntNodeOutRel) return Collections.emptyList();
//                this.cntNodeOutRel++;
//            }
//            List<Long> OutRoadIdList = new ArrayList<>();
//            Map<String, Object> params = new HashMap<>();
//            String nodeuid=findUidByNodeId(nodeId,session);
//            params.put("nodeuid", nodeuid);
//
//            String cypher=String.format("MATCH (n:%s) WHERE n.r_from=$nodeuid RETURN id(n) AS rid",EdgeNodeLabel);
//            var result=session.run(cypher,params);
//            while (result.hasNext()) {
//                var record = result.next();
//                OutRoadIdList.add(record.get("rid").asLong());
//            }
//            return OutRoadIdList;
//        }
//
//        @Override
//        protected long getEndNodeId(long roadId) {
//            long res=-1;
//            Map<String, Object> params = new HashMap<>();
//            params.put("roadId", roadId);
//            String cypher=String.format("MATCH (n:%s) WHERE id(n)=$roadId RETURN n.r_to AS Euid",EdgeNodeLabel);
//            var result=session.run(cypher,params);
//            while (result.hasNext()) {
//                var record = result.next();
//                String Euid=record.get("Euid").asString();
//                res=findNodeIdByUid(Euid,session);
//            }
//            if(res==-1)
//            {
//                String errorMsg = "\n未找到关系ID为[" + roadId + "]的出边对应的目标节点ID。"
//                        + "\n执行的Cypher语句：" + cypher
//                        + "\n传入的roadId参数：" + roadId;
//                throw new RuntimeException(errorMsg);
//            }
//            return res;
//        }
//
//
//        @Override
//        protected String nodeId2Str(long nodeId) {
//            String res=findUidByNodeId(nodeId,session);
//            if(res==null)
//            {
//                String errorMsg = "\n未找节点ID为[" + nodeId + "]的u_sid。";
//                throw new RuntimeException(errorMsg);
//            }
//            return res;
//        }
//
//        @Override
//        protected String relId2Str(long relId) {
//            String res=null;
//            Map<String, Object> params = new HashMap<>();
//            params.put("relId", relId);
//            String cypher=String.format("MATCH (n:%s) WHERE id(n)=$relId RETURN n.u_sid AS uid",EdgeNodeLabel);
//            var result=session.run(cypher,params);
//            while (result.hasNext()) {
//                var record = result.next();
//                res=record.get("uid").asString();
//            }
//            if(res==null)
//            {
//                String errorMsg = "\n未找关系ID为[" + relId + "]的u_sid。";
//                throw new RuntimeException(errorMsg);
//            }
//            return res;
//        }
//
//        public void setCtrlMeta(ReachableAreaQueryTx.StatResult result) {
//            this.totalCntNodeOutRel = result.getGetAllOutRelCnt();
//            this.totalCntArrTime = result.getGetArrTimeCnt();
//        }
//
//        public void info() {
//            System.out.println("META (actual/ctrl): " +
//                    "CntNodeOutRel("+cntNodeOutRel+"/"+totalCntNodeOutRel+"), " +
//                    "CntArrTime("+cntArrTime+"/"+totalCntArrTime+")");
//        }
//    }
}
