package edu.buaa.client;

import com.alibaba.fastjson.JSON;
import com.google.common.util.concurrent.ListenableFuture;
import edu.buaa.common.client.AbstractBoltClient;
import edu.buaa.common.client.AbstractSQLClient;
import edu.buaa.common.client.DBClientProxy;
import edu.buaa.common.transaction.*;
import edu.buaa.common.utils.PFieldList;
import edu.buaa.common.utils.PVal;
import edu.buaa.common.utils.TemporalGraphPropertySchema;
import edu.buaa.utils.Helper;
import edu.buaa.utils.Pair;
import edu.buaa.utils.Triple;
import org.neo4j.driver.*;
import org.neo4j.driver.exceptions.ClientException;
import org.neo4j.driver.exceptions.DatabaseException;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Relationship;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class AionClient extends AbstractBoltClient {
    private Driver driver;
    private String EdgeLabel;
    private String NodeLabel;
    protected final int serverPort = Integer.parseInt(Helper.mustEnv("DB_PORT"));
    private long transaction_t_min=Long.MAX_VALUE;
    private long transaction_t_max=Long.MIN_VALUE;
    private long valid_t_min=Long.MAX_VALUE;
    private long valid_t_max=Long.MIN_VALUE;
    private long vtmin;
    private long vtmax;
    private long ttmin;
    private long ttmax;
    private double k;
    private double b;
    private boolean clientok=false;
    private boolean lineok=false;

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
                return this.submit(execute((ReachableAreaQueryTx) tx));
            case tx_update_temporal_data:
                return this.submit(s->{
                    throw new TransactionFailedException();
                });
            case tx_query_snapshot_aggr_duration:
            default:
                throw new UnsupportedOperationException();
        }
    }

    /**
     * 查找最大最小的transaction时间用于线性映射(由于大数据集上超时故弃用)
     */
    private void findminmax(Session session)
    {
        long begin=0L;
        long end=Long.MAX_VALUE-10;
        Map<String, Object> params = new HashMap<>();
        params.put("begin", begin);
        params.put("end", end);
        String cypher=String.format("CALL time.getTemporalGraphNodes($begin, $end) YIELD startTimestamp WITH startTimestamp " +
                " RETURN max(startTimestamp) AS maxtt ,min(startTimestamp) AS mintt");
        var result=session.run(cypher,params);
        while (result.hasNext()) {
            var record = result.next();
            if(record.get("maxtt").isNull()) continue;
            long maxtt=record.get("maxtt").asLong();
            long mintt=record.get("mintt").asLong();
            transaction_t_max=Math.max(transaction_t_max,maxtt);
            transaction_t_min=Math.min(transaction_t_min,mintt);
        }
        String cypher1=String.format("CALL time.getTemporalGraphRelationships($begin, $end) YIELD startTimestamp WITH startTimestamp " +
                " RETURN max(startTimestamp) AS maxtt ,min(startTimestamp) AS mintt");
        var result1=session.run(cypher1,params);
        while (result1.hasNext()) {
            var record = result1.next();
            if(record.get("maxtt").isNull()) continue;
            long maxtt=record.get("maxtt").asLong();
            long mintt=record.get("mintt").asLong();
            transaction_t_max=Math.max(transaction_t_max,maxtt);
            transaction_t_min=Math.min(transaction_t_min,mintt);
        }
    }
    /**
     * valid_time To transaction_time
     */
    private long VtoT(long vt)
    {
        double res=k*vt+b;
        return (long)Math.floor(res);
    }
    /**
     * transaction_time To valid_time
     */
    private long TtoV(long tt)
    {
        double res=(tt-b)/k;
        return (long)Math.floor(res);
    }
    /**
     * 用于bulkLoad结束后查询最大最小的transaction_time并建立一个特殊的点保存valid_time和transaction_time的取值范围
     */
    public void cutconnect() throws Exception
    {
        Session session = driver.session(SessionConfig.forDatabase("neo4j"));
//        findminmax(session);
        System.out.printf("vt: [%s ~ %s], tt: [%s ~ %s]%n", valid_t_min, valid_t_max, transaction_t_min, transaction_t_max);
        Map<String, Object> params = new HashMap<>();
        params.put("vtmin", valid_t_min);
        params.put("vtmax", valid_t_max);
        params.put("ttmin", transaction_t_min);
        params.put("ttmax", transaction_t_max);
        String cypher = "CREATE (n:TEST_META {uuid: 0, vtmin: $vtmin, vtmax: $vtmax, ttmin: $ttmin, ttmax: $ttmax}) return n";
        session.run(cypher, params).consume();
        session.close();
        driver.close();
    }
    /**
     * 用于client启动时读取transaction_time和valid_time的取值范围
     */
    private void readVandT(Session session) throws Exception
    {
        int retrycount=0;
        while(!clientok&&retrycount<20)
        {
            try {
                session.run("MATCH (n:TEST_META {uuid: 0}) RETURN n;").consume();
                clientok=true;
                break;
            }catch (ClientException e){
                retrycount++;
                System.out.println("retry times: "+retrycount);
                Thread.sleep(10000);
                if(retrycount>=20)throw e;
            }
        }
        System.out.println("Reading TEST_META...");
        var result =session.run("MATCH (n:TEST_META {uuid: 0}) RETURN n;");
        while (result.hasNext())
        {
            Record record= result.next();
            Node node=record.get("n").asNode();
            vtmax=node.get("vtmax").asLong();
            vtmin=node.get("vtmin").asLong();
            ttmax=node.get("ttmax").asLong();
            ttmin=node.get("ttmin").asLong();
            double down=vtmax*1.0-vtmin*1.0;
            double up=ttmax*1.0-ttmin*1.0;
            k=up/down;
            b=ttmin-vtmin*k;
            System.out.printf("vt: [%s ~ %s], tt: [%s ~ %s], k: %s, b: %s%n", vtmin, vtmax, ttmin, ttmax, k, b);
        }
        System.out.println("Reading TEST_META done");
    }
    /**
     * 输入: 数据库返回的值
     * 输出: Pval类支持的类型值(Float、Integer、String)
     */
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
    /**
     * next 123用于BulkLoad建立点边索引并返回索引建立信息
     */
    public void createNodeIndex()throws Exception
    {
        Session session = connectionPool.take();
        String cypher = "CREATE INDEX FOR (n:Node) ON (n.u_sid);";
        System.out.println("CREATEING NODE INDEX......");
        session.run(cypher).consume();
        connectionPool.put(session);
    }
    public void createEdgeIndex()throws Exception
    {
        Session session = connectionPool.take();
        String cypher1="CREATE INDEX FOR ()-[r:Edge]->() ON (r.u_sid);";
        System.out.println("CREATEING EDGE INDEX......");
        session.run(cypher1).consume();
        connectionPool.put(session);
    }
    public void showIndex() throws Exception
    {
        Session session = connectionPool.take();
        var res= session.run("SHOW INDEXES");
        System.out.println("INDEX INFO:");
        while (res.hasNext())
        {
            Record record=res.next();
            System.out.println(record);
        }
        connectionPool.put(session);

    }

    @Override
    protected void connected(Session session) throws Exception {

        EdgeLabel="Edge";
        NodeLabel="Node";
        readVandT(session);
        System.out.println("client sessions prepared: all connected to Aion. ready for execute queries.");
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
    @Override
    public String currentStorageStatus() throws SQLException, InterruptedException {
//        Session session = connectionPool.take();
//        var res = curStoreInfo(session);
//        connectionPool.put(session);
//        return res;
        return null;
    }
    @Override
    protected void createDbIfNotExist(String serverHost, String dbName) throws SQLException, ClassNotFoundException {}
    @Override
    public String testServerClientCompatibility() throws UnsupportedOperationException {
        return null;
    }
    @Override
    public Session createNormalConnection(String serverHost, String dbName) throws SQLException {
        String connect = "bolt://" + this.serverHost + ":" + serverPort;
        driver=GraphDatabase.driver(connect, AuthTokens.basic("", ""));
        return driver.session(SessionConfig.forDatabase("neo4j"));
    }

    @Override
    protected List<String> createTables() {
        return new ArrayList<>();
    }
    private Req execute(ImportStaticDataTx tx)
    {
        return session->{
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
                            "CREATE (n:%s) SET n += row",
                    NodeLabel
            );
            try
            {
                session.run(nodeCypher,Collections.singletonMap("batch", nodeBatch)).consume();
            }catch (DatabaseException ignore){}  //防止字符串类型属性设置报错
            List<Map<String, Object>> relBatch = new ArrayList<>(rSize);
            for (int i = 0; i < rSize; i++) {
                Map<String, Object> row = new HashMap<>();
                row.put("u_sid", relData.get("u_sid", i).getVal());
                row.put("from", relData.get("r_from", i).getVal());
                row.put("to", relData.get("r_to", i).getVal());
                Map<String, Object> props = new HashMap<>();
                for (String key : rProps) {
                    props.put(key, relData.get(key, i).getVal());
                }
                row.put("props", props);

                relBatch.add(row);
            }
            String relCypher = String.format(
                    "UNWIND $batch AS row " +
                            "MATCH (a:%s {u_sid: row.from}), (b:%s {u_sid: row.to}) " +
                            "CREATE (a)-[r:%s {u_sid: row.u_sid}]->(b) " +
                            "SET r += row.props",
                    NodeLabel, NodeLabel, EdgeLabel
            );
            try
            {
                session.run(relCypher, Collections.singletonMap("batch", relBatch)).consume();
            }catch (DatabaseException ignore){}  //防止字符串类型属性设置报错
            return new AbstractTransaction.Result();
        };
    }
    private Req execute(ImportTemporalDataTx tx)
    {
        return session -> {
            boolean isNode=tx.isNode();
            PFieldList data = tx.getData();
            List<String> props = new ArrayList<>(data.keysWithout("u_sid","t"));
            int tSize = data.size();
            List<Map<String,Object>> batch = new ArrayList<>(tSize);
            //bulkLoad时批量导入取消注释
//            for(int i=0;i<tSize;i++)
//            {
//                Map<String,Object> row = new HashMap<>();
//                Map<String,Object>allprops=new HashMap<>();
//                long time=(int)data.get("t",i).getVal();
//                valid_t_min=Math.min(valid_t_min,time);
//                valid_t_max=Math.max(valid_t_max,time);
//                row.put("u_sid", data.get("u_sid", i).getVal());
//                for(String key:props){
//                    allprops.put(key, data.get(key, i).getVal());
//                }
//                row.put("props", allprops);
//                batch.add(row);
//            }
            if(isNode)
            {
                //append操作时取消注释
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
                //bulkLoad时批量导入取消注释
//                String nodeCypher = String.format(
//                        "UNWIND $batch AS row " +
//                                "MATCH (n:%s {u_sid: row.u_sid}) " +
//                                "SET n += row.props RETURN DISTINCT timestamp() AS time",
//                        NodeLabel
//                );
////                System.out.println(nodeCypher);
////                System.out.println(JSON.toJSONString(batch));
//                var result=session.run(nodeCypher, Collections.singletonMap("batch", batch));
//                while (result.hasNext()) {
//                    Record record= result.next();
//                    long time=record.get("time").asLong();
//                    transaction_t_max=Math.max(transaction_t_max,time);
//                    transaction_t_min=Math.min(transaction_t_min,time);
//                }
            }
            else
            {
                //append操作时取消注释
                for(int i=0;i<tSize;i++)
                {
                    Map<String,Object> row = new HashMap<>();
                    Map<String,Object>allprops=new HashMap<>();
                    row.put("u_sid", data.get("u_sid", i).getVal());
                    for(String key:props){
                        allprops.put(key, data.get(key, i).getVal());
                    }
                    row.put("props", allprops);
                    String edgeCypher = String.format(
                                "MATCH (a:%s)-[r:%s{u_sid: $u_sid}]->(b:%s) " +
                                "SET r += $props RETURN DISTINCT timestamp() AS time",
                        NodeLabel, EdgeLabel, NodeLabel
                    );
                    session.run(edgeCypher, row).consume();
                }
                //bulkLoad时批量导入取消注释
//                String edgeCypher = String.format(
//                        "UNWIND $batch AS row " +
//                                "MATCH (a:%s)-[r:%s{u_sid: row.u_sid}]->(b:%s) " +
//                                "SET r += row.props RETURN DISTINCT timestamp() AS time",
//                        NodeLabel, EdgeLabel, NodeLabel
//                );
//                var result=session.run(edgeCypher, Collections.singletonMap("batch", batch));
//                while (result.hasNext()) {
//                    Record record= result.next();
//                    long time=record.get("time").asLong();
//                    transaction_t_max=Math.max(transaction_t_max,time);
//                    transaction_t_min=Math.min(transaction_t_min,time);
//                }
            }
            return new AbstractTransaction.Result();
        };
    }
    private Req execute(EntityTemporalConditionTx tx) {
        return session -> {
            boolean isNode=tx.isNode();
            String prop=tx.getP();
            long begin=VtoT(tx.getT0());
            long end=VtoT(tx.getT1());
            Object minVal=tx.getVMin().getVal();
            Object maxVal=tx.getVMax().getVal();
            List<String> res = new ArrayList<>();
            Map<String, Object> params = new HashMap<>();
            params.put("begin", begin);
            params.put("end", end);
            params.put("minVal",minVal);
            params.put("maxVal",maxVal);
            params.put("propName", prop);
            if(isNode)
            {
                String cypher=String.format("CALL time.getTemporalGraphNodes($begin, $end) YIELD nodeId, properties WITH " +
                        " nodeId,properties[$propName] AS targetValue  "+
                        " WHERE targetValue IS NOT NULL AND targetValue >= $minVal AND targetValue <= $maxVal "+
                        " WITH DISTINCT nodeId MATCH (n:%s) WHERE id(n)=nodeId RETURN n.u_sid AS id ",NodeLabel);
                var result=session.run(cypher,params);
                while (result.hasNext()) {
                    var record = result.next();
                    String id=record.get("id").toString();
                    res.add(id);
                }
            }
            else
            {
                String cypher=String.format("CALL time.getTemporalGraphRelationships($begin, $end) YIELD relId, properties WITH " +
                        " relId,properties[$propName] AS targetValue  "+
                        " WHERE targetValue IS NOT NULL AND targetValue >= $minVal AND targetValue <= $maxVal "+
                        " WITH DISTINCT relId MATCH (a:%s)-[r:%s]->(b:%s) WHERE id(r)=relId RETURN r.u_sid AS id ",NodeLabel,EdgeLabel,NodeLabel);
                var result=session.run(cypher,params);
                while (result.hasNext()) {
                    var record = result.next();
                    String id=record.get("id").toString();
                    res.add(id);
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
            long begin=VtoT(tx.getT0());
            long end=VtoT(tx.getT1());
            List<Pair<String, PVal>> res = new ArrayList<>();
            Map<String, Object> params = new HashMap<>();
            params.put("propName", prop);
            params.put("begin", begin);
            params.put("end", end);
            if(isNode)
            {
                String cypher=String.format("CALL time.getTemporalGraphNodes($begin, $end) YIELD nodeId,properties WITH " +
                        " nodeId,properties[$propName] AS targetValue  "+
                        " WHERE targetValue IS NOT NULL "+
                        " WITH nodeId, max(targetValue) AS maxVal "+
                        " MATCH (n:%s) WHERE id(n)=nodeId RETURN n.u_sid AS id ,maxVal",NodeLabel);
                var result=session.run(cypher,params);
                while (result.hasNext()) {
                    Record record=result.next();
                    String id=record.get("id").asString();
                    Object originv=record.get("maxVal").asObject();
                    if(originv==null)continue;
                    Object v=tranTypeV(originv,prop);
                    res.add(Pair.of(id,PVal.v(v)));
                }
            }
            else
            {
                String cypher=String.format("CALL time.getTemporalGraphRelationships($begin, $end) YIELD relId,properties WITH " +
                        " relId,properties[$propName] AS targetValue  "+
                        " WHERE targetValue IS NOT NULL "+
                        " WITH relId, max(targetValue) AS maxVal "+
                        " MATCH (a:%s)-[r:%s]->(b:%s) WHERE id(r)=relId RETURN r.u_sid AS id ,maxVal",NodeLabel,EdgeLabel,NodeLabel);
                var result=session.run(cypher,params);
                while (result.hasNext()) {
                    Record record = result.next();
                    String id = record.get("id").asString();
                    Object originv=record.get("maxVal").asObject();
                    if(originv==null)continue;
                    Object v=tranTypeV(originv,prop);
                    res.add(Pair.of(id, PVal.v(v)));
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
            long begin=VtoT(tx.getBeginTime());
            long end=VtoT(tx.getEndTime());
            boolean isNode=tx.isNode();
            Map<String, Object> params = new HashMap<>();
            params.put("id", id);
            params.put("begin", begin);
            params.put("end", end);
            if(isNode)
            {
                //time for ehistory
//                String cypher=String.format("MATCH (targetNode:%s{u_sid:$id }) WITH id(targetNode) AS targetId "+
//                        "CALL time.getTemporalGraphNodes($begin, $end) YIELD startTimestamp,endTimestamp,nodeId,properties WITH " +
//                        " startTimestamp,endTimestamp,nodeId,properties WHERE nodeId=targetId RETURN startTimestamp,endTimestamp,properties",NodeLabel);
                //lineage for ehistory
                String cypher=String.format("MATCH (targetNode:%s{u_sid:$id }) WITH id(targetNode) AS targetId "+
                        "CALL lineage.getTemporalNodeHistory(targetId, $begin, $end) YIELD startTimestamp,endTimestamp,nodeId,properties WITH " +
                        " startTimestamp,endTimestamp,nodeId,properties WHERE nodeId=targetId RETURN startTimestamp,endTimestamp,properties",NodeLabel);
//                System.out.println(cypher);
                int retrycount=0;
                while(!lineok&&retrycount<20)
                {
                    try {
                        session.run(cypher,params).consume();
                        lineok=true;
                        break;
                    }catch (ClientException e){
                        retrycount++;
                        System.out.println("retry times: "+retrycount);
                        Thread.sleep(10000);
                        if(retrycount>=20)throw e;
                    }
                }

                var result=session.run(cypher,params);
                while (result.hasNext()) {
                    var record = result.next();
                    Map<String,Object>props=record.get("properties").asMap();
                    Object originv=props.get(prop);
                    if(originv==null)continue;
                    Object v=tranTypeV(originv,prop);
                    Long st=record.get("startTimestamp").asLong();
                    Long et=record.get("endTimestamp").asLong();
                    Long stt=TtoV(st);
                    Long ett=TtoV(et);
                   res.add(Triple.of(stt.intValue(),ett.intValue(),PVal.v(v)));
                }
            }
            else
            {
                String cypher=String.format("MATCH (a:%s)-[targetEdge:%s{u_sid:$id }]->(b:%s) WITH id(targetEdge) AS targetId "+
                        "CALL time.getTemporalGraphRelationships($begin, $end) YIELD startTimestamp,endTimestamp,relId,properties WITH " +
                        " startTimestamp,endTimestamp,relId,properties WHERE relId=targetId RETURN startTimestamp,endTimestamp,properties",NodeLabel,EdgeLabel,NodeLabel);
//                System.out.println(cypher);
                var result=session.run(cypher,params);
                while (result.hasNext()) {
                    var record = result.next();
                    Map<String,Object>props=record.get("properties").asMap();
                    Object originv=props.get(prop);
                    if(originv==null)continue;
                    Object v=tranTypeV(originv,prop);
                    Long st=record.get("startTimestamp").asLong();
                    Long et=record.get("endTimestamp").asLong();
                    Long stt=TtoV(st);
                    Long ett=TtoV(et);
                    res.add(Triple.of(stt.intValue(),ett.intValue(),PVal.v(v)));
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
            long timestamp = VtoT(tx.getTimestamp());
            Map<String, Object> params = new HashMap<>();
            params.put("t", timestamp);
            params.put("propName", propertyName);
            List<Pair<String, PVal>> res = new ArrayList<>();
            if (tx.isNode()) {
                String cypher=String.format("CALL time.getTemporalAllNodes($t) YIELD nodeId,properties WITH nodeId,properties " +
                "MATCH (n:%s) WHERE id(n)=nodeId RETURN n.u_sid AS id ,properties[$propName] AS value",NodeLabel);
                var result=session.run(cypher,params);
                while (result.hasNext()) {
                    var record = result.next();
                    Object originv=record.get("value").asObject();
                    if(originv==null) continue;
                    Object v=tranTypeV(originv,propertyName);
                    String id=record.get("id").asString();
                    res.add(Pair.of(id, PVal.v(v)));
                }
                SnapshotQueryTx.Result re = new SnapshotQueryTx.Result();
                re.answer(res);
                return re;
            } else {
                String cypher=String.format("CALL time.getTemporalAllRelationships($t) YIELD relId,properties WITH relId,properties " +
                        "MATCH (a:%s)-[r:%s]->(b:%s) WHERE id(r)=relId RETURN r.u_sid AS id ,properties[$propName] AS value",NodeLabel,EdgeLabel,NodeLabel);
                var result=session.run(cypher,params);
                while (result.hasNext()) {
                    var record = result.next();
                    Object originv=record.get("value").asObject();
                    if(originv==null) continue;
                    Object v=tranTypeV(originv,propertyName);
                    String id=record.get("id").asString();
                    res.add(Pair.of(id, PVal.v(v)));
                }
                SnapshotQueryTx.Result re = new SnapshotQueryTx.Result();
                re.answer(res);
                return re;
            }
        };

    }
    private Req execute(ReachableAreaQueryTx tx){
        return session -> {
            List<Pair<Integer, String>> answers = new ArrayList<>();
            String startNodeUid=tx.getStartNode();
            long startNodeId=findNodeIdByUid(startNodeUid,session);
            ReachableAreaAion algo = new ReachableAreaAion(startNodeId, tx.getDepartureTime(), tx.getTravelTime(),session);
            for (ReachableAreaQueryTx.TemporalDijkstraAlgo.NodeCross nodeCross : algo.run()) {
                String u_sid = findUidByNodeId(nodeCross.getId(),session);
                answers.add(Pair.of(nodeCross.getArriveTime(), u_sid));
            }
            ReachableAreaQueryTx.Result result = new ReachableAreaQueryTx.Result();
            result.setNodeArriveTime(answers);
            result.setInnerResults(algo.getInnerResults());
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
    public class ReachableAreaAion extends ReachableAreaQueryTx.TemporalDijkstraAlgo
    {
        private  Session session;
        public ReachableAreaAion(long startId, int startTime, int travelTime,Session session) {
            super(startId, startTime, travelTime, true);
            this.session=session;
        }
        @Override
        protected int getEarliestArriveTime(Long roadId, int departureTime) throws UnsupportedOperationException {
            long earT = Long.MAX_VALUE;
            long depatureTimeT=VtoT(departureTime);
            Map<String, Object> params = new HashMap<>();
            params.put("begin", depatureTimeT);
            params.put("end", Long.MAX_VALUE-10);
            params.put("roadId", roadId);
            String cypher=String.format("CALL time.getTemporalGraphRelationships($begin, $end) YIELD startTimestamp,endTimestamp,relId,properties " +
                    " WITH startTimestamp,endTimestamp,relId,properties.travel_time AS travel_time" +
                    "  WHERE relId=$roadId RETURN startTimestamp,endTimestamp,travel_time");
            var result=session.run(cypher,params);
            while (result.hasNext())
            {

                var record = result.next();
//                System.out.println(record.asMap());
                long st=record.get("startTimestamp").asLong();
                long et=record.get("endTimestamp").asLong();
                long travel_time=record.get("travel_time").asLong();
                travel_time*=1000;
                st=Math.max(st,depatureTimeT);
                long arr_t=st+travel_time;
                if(arr_t<earT) earT=arr_t;
            }
            if(earT<Long.MAX_VALUE)
            {
               Long res=TtoV(earT);
               return res.intValue();
            }
            else throw new UnsupportedOperationException();
        }

        @Override
        protected Iterable<Long> getAllOutRoads(long nodeId) {
            List<Long> OutRoadIdList = new ArrayList<>();
            Map<String, Object> params = new HashMap<>();
            params.put("nodeId", nodeId);
            String cypher=String.format("MATCH (n:%s)-[r:%s]->(m:%s) WHERE id(n)=$nodeId RETURN id(r) AS rid",NodeLabel,EdgeLabel,NodeLabel);
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
            String cypher=String.format("MATCH (n:%s)-[r:%s]->(m:%s) WHERE id(r)=$roadId RETURN id(m) AS Eid",NodeLabel,EdgeLabel,NodeLabel);
            var result=session.run(cypher,params);
            while (result.hasNext()) {
                var record = result.next();
                res=record.get("Eid").asLong();
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
            String cypher=String.format("MATCH (n:%s)-[r:%s]->(m:%s) WHERE id(r)=$relId RETURN r.u_sid AS uid",NodeLabel,EdgeLabel,NodeLabel);
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
