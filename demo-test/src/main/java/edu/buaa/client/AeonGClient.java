package edu.buaa.client;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.google.common.util.concurrent.ListenableFuture;
import edu.buaa.common.client.AbstractBoltClient;
import edu.buaa.common.transaction.*;
import edu.buaa.common.utils.PFieldList;
import edu.buaa.common.utils.PVal;
import edu.buaa.utils.Helper;
import edu.buaa.utils.Pair;
import edu.buaa.utils.Triple;
import org.neo4j.cypher.internal.expressions.In;
import org.neo4j.driver.*;
import org.neo4j.driver.types.Entity;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Relationship;
import org.neo4j.driver.types.Type;
import scala.reflect.internal.Trees;

import java.math.BigDecimal;
import java.sql.SQLException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AeonGClient extends AbstractBoltClient {
    private Driver driver;
    protected final int serverPort = Integer.parseInt(Helper.mustEnv("DB_PORT")); // hostname of server.
//    protected final int serverPort = 45242;
    private String dataset = Helper.mustEnv("DATASET");
//    private String dataset = "traffic";
    private String EdgeLabel;
    private String NodeLabel;
    private String EdgeNodeLabel="EdgeNode";

    private int vtmin;
    private int vtmax;
    private int ttmin;
    private int ttmax;
    private double k;
    private double b;

    @Override
    public ListenableFuture<ServerResponse> execute(AbstractTransaction tx) throws InterruptedException {
        switch (tx.getTxType()) {
            case tx_import_static_data:
                return this.submit(execute((ImportStaticDataTx) tx));
            case tx_import_temporal_data:
                return this.submit(execute((ImportTemporalDataTx) tx), tx.getSection());
            case tx_query_snapshot:
                return this.submit(execute((SnapshotQueryTx) tx));
            case tx_query_snapshot_aggr_max:
                return this.submit(execute((SnapshotAggrMaxTx) tx));
            case tx_query_entity_history:
                return this.submit(execute((EntityHistoryTx) tx));
            case tx_query_road_by_temporal_condition:
                return this.submit(execute((EntityTemporalConditionTx) tx));
            case tx_query_reachable_area:
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

    @Override
    protected void connected(Session session) throws Exception {
        switch (dataset) {
            case "energy": {
                EdgeLabel = "TransmissionLine";
                NodeLabel = "Station";
                break;
            }
            case "traffic": {
                EdgeLabel = "Road";
                NodeLabel = "RoadNode";
                break;
            }
            case "syn": {
                EdgeLabel = "Edge";
                NodeLabel = "Node";
                break;
            }
            default:
                throw new IllegalArgumentException("Unknown dataset: " + dataset);
        }
        readVandT(session);
        System.out.println(curStoreInfo(session));
        System.out.println("client sessions prepared: all connected to AeonG. ready for execute queries.");
    }

    @Override
    protected String type2SQL(PVal.Type type) {
        switch (type) {
            case STRING:
                return "String";
            case INT:
                return "Integer";
            case FLOAT:
                return "Float";
            default:
                return "String";
        }
    }
    private  Object tranTypeV(Object v,String prop)
    {
        Object legalV=null;
        if (v != null) {
            if (v instanceof String) {
                legalV = v;
            }else if (v instanceof BigDecimal) {
                legalV = ((BigDecimal) v).floatValue();
            }
            else if (v instanceof Float || v instanceof Double) {
                legalV = ((Number) v).floatValue();
            } else if (v instanceof Integer || v instanceof Long) {
                long longVal = ((Number) v).longValue();
                if (longVal > Integer.MAX_VALUE || longVal < Integer.MIN_VALUE) {
                    throw new IllegalArgumentException("数值超出Integer范围：" + longVal);
                }
                legalV = ((Number) v).intValue();
            } else {
                throw new IllegalArgumentException(
                        "属性" + prop + "类型非法，当前类型：" + v.getClass().getName()
                );
            }
        }
        return legalV;
    }
    private Triple<Integer, Integer, PVal> findDelAndNow(Entity entity,String prop,boolean hascheck)
    {

        Value del = entity.get("delete_info");
        Value trans_t=entity.get("transaction_ts");
        Value trans_e=entity.get("transaction_te");
        if(!trans_t.isNull()&&!trans_e.isNull()&&!hascheck)
        {
            hascheck=true;
            Object v= tranTypeV(entity.get(prop).asObject(),prop);
            Long st=trans_t.asLong();
            Long et = trans_e.asLong();
            int stt=st.intValue();
            int ett=et.intValue();
            if(et.equals(Long.MAX_VALUE))
            {
                ett= Integer.MAX_VALUE;
            }
            if(v==null)
            {
                return Triple.of(TtoV(stt), TtoV(ett), null);
            }
            else
                return Triple.of(TtoV(stt), TtoV(ett), PVal.v(v));
        }
        if(!del.isNull())
        {
            String jsonString=del.asString();
            JSONObject jsonObject = JSON.parseObject(jsonString);
            Long st= jsonObject.getLong("TT_TS");
            Long et = jsonObject.getLong("TT_TE");
            JSONObject SP = jsonObject.getJSONObject("SP");
            Object v=tranTypeV(SP.get(prop),prop);
            int stt=st.intValue();
            int ett=et.intValue();
            if(et.equals(Long.MAX_VALUE))
            {
                ett= Integer.MAX_VALUE;
            }
            if(v==null)
            {
                return Triple.of(TtoV(stt), TtoV(ett), null);
            }
            else
                return Triple.of(TtoV(stt), TtoV(ett), PVal.v(v));
        }
        return new Triple<>();
    }
    private String curStoreInfo(Session session){
        System.out.println("SHOW STORAGE INFO...");
        var result = session.run("SHOW STORAGE INFO;");
        JSONObject obj = new JSONObject();
        while (result.hasNext()) {
            var record = result.next();
            String key = record.get("storage info").asString();
            Object val;
            if (key.equals("vertex_count") || key.equals("edge_count") || key.equals("memory_usage") || key.equals("disk_usage")) {
                val = record.get("value").asLong();
            } else continue;
            obj.put(key, val);
            System.out.println(key+": "+val);
        }
        System.out.println("SHOW STORAGE INFO: "+obj.toJSONString());
        return obj.toJSONString();
    }

    @Override
    public String currentStorageStatus() throws SQLException, InterruptedException {
        Session session = connectionPool.take();
        var res = curStoreInfo(session);
        connectionPool.put(session);
        return res;
    }

    @Override
    protected void createDbIfNotExist(String serverHost, String dbName) throws SQLException, ClassNotFoundException {}

    @Override
    protected Session createNormalConnection(String serverHost, String dbName) throws SQLException {
        String connect = "bolt://" + this.serverHost + ":" + serverPort;
        driver = GraphDatabase.driver(connect, AuthTokens.basic("", ""));
        return driver.session(SessionConfig.forDatabase("memgraph"));
    }

    @Override
    protected List<String> createTables() {
        return new ArrayList<>();
    }

    private void readVandT(Session session)
    {
        System.out.println("Reading TEST_META...");
        var result =session.run("MATCH (n:TEST_META {uuid: 0}) RETURN n;");
        while (result.hasNext())
        {
            Record record= result.next();
            Node node=record.get("n").asNode();
            int vtmax=node.get("vtmax").asInt();
            int vtmin=node.get("vtmin").asInt();
            int ttmax=node.get("ttmax").asInt();
            int ttmin=node.get("ttmin").asInt();
//        int vtmax=1291132679;
//        int vtmin=1272643500 ;
//        int ttmax=1395516609;
//        int ttmin=6;
            double down=vtmax*1.0-vtmin*1.0;
            double up=ttmax*1.0-ttmin*1.0;
            k=up/down;
            b=ttmin-vtmin*k;
            System.out.printf("vt: [%s ~ %s], tt: [%s ~ %s], k: %s, b: %s%n", vtmin, vtmax, ttmin, ttmax, k, b);
        }
        System.out.println("Reading TEST_META done");
    }
    private int VtoT(int vt)
    {
        double res=k*vt+b;
        return (int)Math.floor(res);
    }
    private int TtoV(int tt)
    {
        double res=(tt-b)/k;
        return (int)Math.floor(res);
    }

    private Req execute(ImportTemporalDataTx tx) {
        return session -> {
            boolean isNode=tx.isNode();
            PFieldList data = tx.getData();
            List<String> props = new ArrayList<>(data.keysWithout("u_sid","t"));
            int tSize = data.size();
            List<Map<String,Object>> batch = new ArrayList<>(tSize);
//            for(int i=0;i<tSize;i++)
//            {
//                Map<String,Object> row = new HashMap<>();
//                Map<String,Object>allprops=new HashMap<>();
//                row.put("u_sid", data.get("u_sid", i).getVal());
//                for(String key:props){
//                    allprops.put(key, data.get(key, i).getVal());
//                }
//                row.put("props", allprops);
//                batch.add(row);
//            }
            if(isNode)
            {
                for(int i=0;i<tSize;i++)
                {
                    Map<String,Object> row = new HashMap<>();
                    Map<String,Object>allprops=new HashMap<>();
                    row.put("u_sid", data.get("u_sid", i).getVal());
                    for(String key:props){
                        allprops.put(key, data.get(key, i).getVal());
                    }
                    row.put("props", allprops);
                    String nodeCypher = String.format(
                            "MATCH (n:%s {u_sid: $u_sid}) " +
                                    "SET n += $props",
                            NodeLabel
                    );
                    session.run(nodeCypher, row).consume();
                }
//                String nodeCypher = String.format(
//                        "UNWIND $batch AS row " +
//                                "MATCH (n:%s {u_sid: row.u_sid}) " +
//                                "SET n += row.props",
//                        NodeLabel
//                );
//                session.run(nodeCypher, Collections.singletonMap("batch", batch)).consume();
            }
            else
            {
                for(int i=0;i<tSize;i++)
                {
                    Map<String,Object> row = new HashMap<>();
                    Map<String,Object>allprops=new HashMap<>();
                    row.put("u_sid", data.get("u_sid", i).getVal());
                    for(String key:props){
                        allprops.put(key, data.get(key, i).getVal());
                    }
                    row.put("props", allprops);
                    String nodeCypher = String.format(
                            "MATCH (n:%s {u_sid: $u_sid}) " +
                                    "SET n += $props",
                            EdgeNodeLabel
                    );
                    session.run(nodeCypher, row).consume();
                }
//                String edgeCypher = String.format(
//                        "UNWIND $batch AS row " +
//                                "MATCH (a:%s)-[r:%s{u_sid: row.u_sid}]->(b:%s) " +
//                                "SET r += row.props",
//                        NodeLabel, EdgeLabel, NodeLabel
//                );
//                String edgeCypher = String.format(
//                        "UNWIND $batch AS row " +
//                                "MATCH (n:%s {u_sid: row.u_sid}) " +
//                                "SET n += row.props RETURN n",
//                        EdgeNodeLabel
//                );
//                session.run(edgeCypher, Collections.singletonMap("batch", batch)).consume();
            }
            return new AbstractTransaction.Result();
        };
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
                            "CREATE (n:%s) SET n += row ",
                    NodeLabel
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
                            "MATCH (a:%s {id: row.from}), (b:%s {id: row.to}) " +
                            "CREATE (a)-[r:%s {u_sid: row.u_sid}]->(b) " +
                            "SET r += row.props",
                    NodeLabel, NodeLabel, EdgeLabel
            );
            session.run(relCypher, Collections.singletonMap("batch", relBatch)).consume();
            return new AbstractTransaction.Result();
        };
    }

    private Req execute(EntityTemporalConditionTx tx) {
        return session -> {
            boolean isNode=tx.isNode();
            String prop=tx.getP();
            int begin=VtoT(tx.getT0());
            int end=VtoT(tx.getT1());
            PVal minValue=tx.getVMin();
            PVal maxValue=tx.getVMax();
            List<String> res = new ArrayList<>();
            boolean hascheck=false;
            if(isNode)
            {
                String cypher = String.format(
                        "MATCH (n:%s) " +
                                "TT FROM "+begin+" TO "+end+" " +
                                "  RETURN n.u_sid AS id, COLLECT(n) AS nodeList",NodeLabel);
                var result = session.run(cypher);
                while (result.hasNext()) {
                    var record = result.next();
                    List<Object> nodeList=record.get("nodeList").asList();
                    String id=record.get("id").toString();
                    for(Object n:nodeList)
                    {
                        Node node=(Node)n;
                        Triple<Integer, Integer, PVal> triple=findDelAndNow(node,prop,hascheck);
                        PVal val=triple.getRight();
                        if(val!=null&&val.compareTo(minValue)>=0&&val.compareTo(maxValue)<=0)
                        {
                            res.add(id);
                            break;
                        }
                    }
                }
            }
            else
            {
//                String cypher = String.format(
//                        "MATCH ()-[r:%s]->() " +
//                                "TT FROM "+begin+" TO "+end+" " +
//                                "RETURN r.u_sid AS id, COLLECT(r) AS edgeList",EdgeLabel);
//                var result = session.run(cypher);
//                while (result.hasNext()) {
//                    var record = result.next();
//                    List<Object> edgeList=record.get("edgeList").asList();
//                    String id=record.get("id").toString();
//                    for(Object n:edgeList)
//                    {
//                        Relationship rel=(Relationship) n;
//                        Triple<Integer, Integer, PVal> triple=findDelAndNow(rel,prop,hascheck);
//                        PVal val=triple.getRight();
//                        if(val!=null&&val.compareTo(minValue)>=0&&val.compareTo(maxValue)<=0)
//                        {
//                            res.add(id);
//                            break;
//                        }
//                    }
//                }
                String cypher = String.format(
                        "MATCH (n:%s) " +
                                "TT FROM "+begin+" TO "+end+" " +
                                "RETURN n.u_sid AS id, COLLECT(n) AS nodeList",EdgeNodeLabel);
                var result = session.run(cypher);
                while (result.hasNext()) {
                    var record = result.next();
                    List<Object> nodeList=record.get("nodeList").asList();
                    String id=record.get("id").toString();
                    for(Object n:nodeList)
                    {
                        Node node=(Node)n;
                        Triple<Integer, Integer, PVal> triple=findDelAndNow(node,prop,hascheck);
                        PVal val=triple.getRight();
                        if(val!=null&&val.compareTo(minValue)>=0&&val.compareTo(maxValue)<=0)
                        {
                            res.add(id);
                            break;
                        }
                    }
                }
            }
            EntityTemporalConditionTx.Result result = new EntityTemporalConditionTx.Result();
            result.setEntities(res);
            return result;
        };
    }

    private Req execute(SnapshotAggrMaxTx tx) {
        return session -> {
            boolean isNode=tx.isNode();
            String prop=tx.getP();
            int begin=VtoT(tx.getT0());
            int end=VtoT(tx.getT1());
            List<Pair<String, PVal>> res = new ArrayList<>();
            boolean hascheck=false;
            if(isNode)
            {
                String cypher = String.format(
                        "MATCH (n:%s) " +
                                "TT FROM "+begin+" TO "+end+" " +
                                "RETURN n.u_sid AS id, COLLECT(n) AS nodeList",NodeLabel);
                var result=session.run(cypher);
                while (result.hasNext()) {
                    var record = result.next();
                    List<Object> nodeList=record.get("nodeList").asList();
                    String id=record.get("id").toString();
                    PVal maxPval=null;
                    for(Object n:nodeList)
                    {
                        Node node=(Node)n;
                        Triple<Integer, Integer, PVal> triple=findDelAndNow(node,prop,hascheck);
                        if(maxPval==null)
                        {
                            maxPval=triple.getRight();
                        }
                        else
                        {
                            if(triple.getRight()!=null&&maxPval.compareTo(triple.getRight())<0)
                            {
                                maxPval=triple.getRight();
                            }
                        }
                    }
                    if(maxPval!=null)
                    {
                        res.add(Pair.of(id,maxPval));
                    }
                }
            }
            else
            {
//                String cypher = String.format(
//                        "MATCH ()-[r:%s]->() " +
//                                "TT FROM "+begin+" TO "+end+" " +
//                                "RETURN r.u_sid AS id, COLLECT(r) AS edgeList",EdgeLabel);
//                var result=session.run(cypher);
//                while (result.hasNext()) {
//                    var record = result.next();
//                    List<Object> edgeList=record.get("edgeList").asList();
//                    String id=record.get("id").toString();
//                    PVal maxPval=null;
//                    for(Object n:edgeList)
//                    {
//                        Relationship rel=(Relationship) n;
//                        Triple<Integer, Integer, PVal> triple=findDelAndNow(rel,prop,hascheck);
//                        if(maxPval==null)
//                        {
//                            maxPval=triple.getRight();
//                        }
//                        else
//                        {
//                            if(triple.getRight()!=null&&maxPval.compareTo(triple.getRight())<0)
//                            {
//                                maxPval=triple.getRight();
//                            }
//                        }
//                    }
//                    if(maxPval!=null)
//                    {
//                        res.add(Pair.of(id,maxPval));
//                    }
//                }
                String cypher = String.format(
                        "MATCH (n:%s) " +
                                "TT FROM "+begin+" TO "+end+" " +
                                "RETURN n.u_sid AS id, COLLECT(n) AS nodeList",EdgeNodeLabel);
                var result=session.run(cypher);
                while (result.hasNext()) {
                    var record = result.next();
                    List<Object> nodeList=record.get("nodeList").asList();
                    String id=record.get("id").toString();
                    PVal maxPval=null;
                    for(Object n:nodeList)
                    {
                        Node node=(Node)n;
                        Triple<Integer, Integer, PVal> triple=findDelAndNow(node,prop,hascheck);
                        if(maxPval==null)
                        {
                            maxPval=triple.getRight();
                        }
                        else
                        {
                            if(triple.getRight()!=null&&maxPval.compareTo(triple.getRight())<0)
                            {
                                maxPval=triple.getRight();
                            }
                        }
                    }
                    if(maxPval!=null)
                    {
                        res.add(Pair.of(id,maxPval));
                    }
                }
            }
            SnapshotAggrMaxTx.Result ans = new SnapshotAggrMaxTx.Result();
            ans.setPropMaxValue(res);
            return ans;
        };
    }

    private Req execute(EntityHistoryTx tx) {
        return session -> {
            List<Triple<Integer, Integer, PVal>> res = new ArrayList<>();
            String id=tx.getEntity();
            String prop=tx.getProp();
            int begin=VtoT(tx.getBeginTime());
            int end=VtoT(tx.getEndTime());
            boolean isNode=tx.isNode();
            Map<String, Object> params = new HashMap<>();
            params.put("id", id);
            boolean hascheck=false;  //用来过滤多余的最后一条当前信息
//            params.put("begin", begin);
//            params.put("end", end);
            if(isNode)
            {
                String cypher = String.format(
                        "MATCH (n:%s{u_sid: $id}) " +
                                "TT FROM "+begin+" TO "+end+" " +
                                "RETURN n",
                        NodeLabel);
                System.out.println(cypher);
                var result=session.run(cypher,params);
                while (result.hasNext()) {
                    var record = result.next();
                    Node node = record.get("n").asNode();
                    Triple<Integer, Integer, PVal> triple = findDelAndNow(node, prop, hascheck);
                    if(triple.getRight()!=null) res.add(triple);
//                    Long st=node.get("transaction_ts").asLong();
//                    Long et=node.get("transaction_te").asLong();
//                    int stt=st.intValue();
//                    int ett=et.intValue();
//                    if(et.equals(Long.MAX_VALUE))
//                    {
//                        ett= Integer.MAX_VALUE;
//                    }
//                    res.add(Triple.of(stt, ett, PVal.v(v)));
                }
            }
            else
            {
//                String cypher = String.format(
//                        "MATCH ()-[r:%s{u_sid: $id}]->() " +
//                                "TT FROM "+begin+" TO "+end+" " +
//                                "RETURN r",
//                        EdgeLabel, prop
//                );
//                var result=session.run(cypher,params);
//                while (result.hasNext()) {
//                    var record = result.next();
//                    Relationship rel= record.get("r").asRelationship();
//                    Triple<Integer, Integer, PVal> triple = findDelAndNow(rel, prop, hascheck);
//                    if(triple.getRight()!=null) res.add(triple);
//                }
                String cypher = String.format(
                        "MATCH (n:%s{u_sid: $id}) " +
                                "TT FROM "+begin+" TO "+end+" " +
                                "RETURN n",
                        EdgeNodeLabel);
                var result=session.run(cypher,params);
                while (result.hasNext()) {
                    var record = result.next();
                    Node node = record.get("n").asNode();
                    Triple<Integer, Integer, PVal> triple = findDelAndNow(node, prop, hascheck);
                    if (triple.getRight() != null) res.add(triple);
                }
            }
            EntityHistoryTx.Result ans= new EntityHistoryTx.Result();
            ans.setHistory(res);
            return ans;
        };
    }

    private Req execute(SnapshotQueryTx tx) {
        return session ->
        {
            String propertyName = tx.getPropertyName();
            int timestamp = VtoT(tx.getTimestamp());
//            Map<String, Object> params = new HashMap<>();
//            params.put("t", timestamp);
            boolean hascheck=false;
            if (tx.isNode()) {
                String cypher = String.format(
                        "MATCH (n:%s) " +
                                "TT AS "+timestamp+" " +
                                "RETURN n",
                        NodeLabel);
                var result = session.run(cypher);
                List<Pair<String, PVal>> res = new ArrayList<>();
                while (result.hasNext()) {
                    var record = result.next();
                    Node node = record.get("n").asNode();
                    String id = node.get("u_sid").asString();
                    Triple<Integer,Integer,PVal>triple=findDelAndNow(node,propertyName,hascheck);
                    if(triple.getRight()!=null)
                    res.add(Pair.of(id, triple.getRight()));
                }
                SnapshotQueryTx.Result re = new SnapshotQueryTx.Result();
                re.answer(res);
                return re;
            } else {
//                String cypher = String.format(
//                        "MATCH ()-[r:%s]->() " +
//                                "TT AS "+timestamp+" " +
//                                "RETURN r",
//                        EdgeLabel
//                );
//                var result = session.run(cypher);
//                List<Pair<String, PVal>> res = new ArrayList<>();
//                while (result.hasNext()) {
//                    var record = result.next();
//                    Relationship rel= record.get("n").asRelationship();
//                    String id = rel.get("u_sid").asString();
//                    Triple<Integer,Integer,PVal>triple=findDelAndNow(rel,propertyName,hascheck);
//                    if(triple.getRight()!=null)
//                    res.add(Pair.of(id, triple.getRight()));
//                }
                String cypher = String.format(
                        "MATCH (n:%s) " +
                                "TT AS "+timestamp+" " +
                                "RETURN n",
                        EdgeNodeLabel);
                var result = session.run(cypher);
                List<Pair<String, PVal>> res = new ArrayList<>();
                while (result.hasNext()) {
                    var record = result.next();
                    Node node = record.get("n").asNode();
                    String id = node.get("u_sid").asString();
                    Triple<Integer,Integer,PVal>triple=findDelAndNow(node,propertyName,hascheck);
                    if(triple.getRight()!=null)
                        res.add(Pair.of(id, triple.getRight()));
                }
                SnapshotQueryTx.Result re = new SnapshotQueryTx.Result();
                re.answer(res);
                return re;
            }
        };

    }
    private Req execute(ReachableAreaQueryTx tx){
        ReachableAreaQueryTx.DEBUG = true;
        return session -> {
            List<Pair<Integer, String>> answers = new ArrayList<>();
            String startNodeUid=tx.getStartNode();
            long startNodeId=findNodeIdByUid(startNodeUid,session);
            ReachableAreaAeonG algo = new ReachableAreaAeonG(startNodeId, tx.getDepartureTime(), tx.getTravelTime(),session,startNodeUid);

            for (ReachableAreaQueryTx.TemporalDijkstraAlgo.NodeCross nodeCross : algo.run()) {
                String u_sid = findUidByNodeId(nodeCross.getId(),session);
                answers.add(Pair.of(nodeCross.getArriveTime(), u_sid));
            }
            ReachableAreaQueryTx.Result result = new ReachableAreaQueryTx.Result();
//            result.setNodeArriveTime(answers);
//            result.setInnerResults(algo.getInnerResults());
            result.setStatResult(algo.statResult);
            return result;
        };
    }
    /**
     * 通过u_sid返回点的内部id
     */
    private long findNodeIdByUid(String uid,Session session)
    {
        long res=-1;
        Map<String, Object> params = new HashMap<>();
        params.put("uid",uid);
        String cypher=String.format("MATCH (n:%s) WHERE n.u_sid=$uid RETURN id(n) AS id",NodeLabel);
        var result=session.run(cypher,params);
        while (result.hasNext())
        {
            var record = result.next();
            res=record.get("id").asLong();
        }
        if(res==-1)
        {
            String errorMsg = "\n未找到u_sid为[" + uid + "]的内部ID。"
                    + "\n执行的Cypher语句：" + cypher;
            throw new RuntimeException(errorMsg);
        }
        return res;
    }

    /**
     * 通过内部id返回点的u_sid
     */
    private String findUidByNodeId(long nodeId,Session session)
    {
        String res=null;
        Map<String, Object> params = new HashMap<>();
        params.put("nodeId",nodeId);
        String cypher=String.format("MATCH (n:%s) WHERE id(n)=$nodeId RETURN n.u_sid AS uid",NodeLabel);
        var result=session.run(cypher,params);
        while (result.hasNext())
        {
            var record = result.next();
            res=record.get("uid").asString();
        }
        if(res==null)
        {
            String errorMsg = "\n未找到nodeId为[" + nodeId + "]的u_sid。"
                    + "\n执行的Cypher语句：" + cypher;
            throw new RuntimeException(errorMsg);
        }
        return res;
    }
    /**
     * 通过内部id返回边的u_sid
     */
    private String findUidByRelId(long RelId,Session session)
    {
        String res=null;
        Map<String, Object> params = new HashMap<>();
        params.put("RelId",RelId);
        String cypher=String.format("MATCH (n:%s) WHERE id(n)=$RelId RETURN n.u_sid AS uid",EdgeNodeLabel);
        var result=session.run(cypher,params);
        while (result.hasNext())
        {
            var record = result.next();
            res=record.get("uid").asString();
        }
        if(res==null)
        {
            String errorMsg = "\n未找到RelId为[" + RelId + "]的u_sid。"
                    + "\n执行的Cypher语句：" + cypher;
            throw new TransactionFailedException(new RuntimeException(errorMsg));
        }
        return res;
    }
    public class ReachableAreaAeonG extends ReachableAreaQueryTx.TemporalDijkstraAlgo
    {
        private  Session session;
        private String startNodeUid;
        public ReachableAreaAeonG(long startId, int startTime, int travelTime,Session session,String startNodeUid) {
            super(startId, startTime, travelTime, true);
            this.session=session;
            this.startNodeUid=startNodeUid;
        }
        @Override
        protected int getEarliestArriveTime(Long roadId, int departureTime) throws UnsupportedOperationException {
//            System.out.println("getEarliestArriveTime !");
            int earT = Integer.MAX_VALUE;
            int depatureTimeT=VtoT(departureTime);
            int end=VtoT(this.endTime);
            Map<String, Object> params = new HashMap<>();
            String roadUid=findUidByRelId(roadId,session);
            params.put("roadUid", roadUid);
            String cypher=String.format("MATCH (n:%s{u_sid:$roadUid}) TT FROM "+depatureTimeT+" TO " +end+" RETURN n ",EdgeNodeLabel);
//            System.out.println(cypher+"  running ");
            var result=session.run(cypher,params);
            while (result.hasNext()) {
                var record = result.next();
                Node node = record.get("n").asNode();
                Triple<Integer, Integer, PVal> triple = findDelAndNow(node, "travel_time", false);
                if(triple==null||triple.getRight()==null)
                {
                    continue;
                }
                int st=Math.max(depatureTimeT,triple.getLeft());
                int travel_time=(int)triple.getRight().getVal();
                int arr_t=st+travel_time*50;
//                System.out.println("arr_t: "+arr_t);
                if(arr_t<earT) earT=arr_t;
            }
            if(earT<Integer.MAX_VALUE)
            {
                int res=TtoV(earT);
//                System.out.println(this.startNodeUid+" Road: "+roadUid+" earT: "+res+"   depature: "+depatureTimeT+"  endTime: "+end);
                return res;
            }
            else throw new UnsupportedOperationException();
        }

        @Override
        protected Iterable<Long> getAllOutRoads(long nodeId) {
            List<Long> OutRoadIdList = new ArrayList<>();
            Map<String, Object> params = new HashMap<>();
            String nodeuid=findUidByNodeId(nodeId,session);
            params.put("nodeuid", nodeuid);

            String cypher=String.format("MATCH (n:%s) WHERE n.r_from=$nodeuid RETURN id(n) AS rid",EdgeNodeLabel);
            var result=session.run(cypher,params);
            while (result.hasNext()) {
                var record = result.next();
                OutRoadIdList.add(record.get("rid").asLong());
            }
            return OutRoadIdList;
        }

        @Override
        protected long getEndNodeId(long roadId) {
            long res=-1;
            Map<String, Object> params = new HashMap<>();
            params.put("roadId", roadId);
            String cypher=String.format("MATCH (n:%s) WHERE id(n)=$roadId RETURN n.r_to AS Euid",EdgeNodeLabel);
            var result=session.run(cypher,params);
            while (result.hasNext()) {
                var record = result.next();
                String Euid=record.get("Euid").asString();
                res=findNodeIdByUid(Euid,session);
            }
            if(res==-1)
            {
                String errorMsg = "\n未找到关系ID为[" + roadId + "]的出边对应的目标节点ID。"
                        + "\n执行的Cypher语句：" + cypher
                        + "\n传入的roadId参数：" + roadId;
                throw new RuntimeException(errorMsg);
            }
            return res;
        }


        @Override
        protected String nodeId2Str(long nodeId) {
            String res=findUidByNodeId(nodeId,session);
            if(res==null)
            {
                String errorMsg = "\n未找节点ID为[" + nodeId + "]的u_sid。";
                throw new RuntimeException(errorMsg);
            }
            return res;
        }

        @Override
        protected String relId2Str(long relId) {
            String res=null;
            Map<String, Object> params = new HashMap<>();
            params.put("relId", relId);
            String cypher=String.format("MATCH (n:%s) WHERE id(n)=$relId RETURN n.u_sid AS uid",EdgeNodeLabel);
            var result=session.run(cypher,params);
            while (result.hasNext()) {
                var record = result.next();
                res=record.get("uid").asString();
            }
            if(res==null)
            {
                String errorMsg = "\n未找关系ID为[" + relId + "]的u_sid。";
                throw new RuntimeException(errorMsg);
            }
            return res;
        }

    }
}
