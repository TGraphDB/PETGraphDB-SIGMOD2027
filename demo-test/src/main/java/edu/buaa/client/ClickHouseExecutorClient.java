package edu.buaa.client;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListenableFuture;
import edu.buaa.common.client.AbstractSQLClient;
import edu.buaa.common.transaction.*;
import edu.buaa.common.utils.PFieldList;
import edu.buaa.common.utils.PVal;
import edu.buaa.utils.Pair;
import edu.buaa.utils.Triple;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public class ClickHouseExecutorClient extends AbstractSQLClient {
    private final String user = "postgres";
    private final String password = "langduhua";

    @Override
    protected void createDbIfNotExist(String serverHost, String dbName) throws SQLException, ClassNotFoundException {
        Class.forName("ru.yandex.clickhouse.ClickHouseDriver");
        try (Connection conn = DriverManager.getConnection("jdbc:clickhouse://" + serverHost + ":9430/", user, password);
             Statement stmt = conn.createStatement()) {
            conn.setAutoCommit(true);
            stmt.execute("CREATE database IF NOT EXISTS "+dbName);
        }
    }

    @Override
    protected Connection createNormalConnection(String serverHost, String dbName) throws SQLException {
        Connection con = DriverManager.getConnection("jdbc:clickhouse://" + serverHost + ":9430/"+dbName, user, password);
        con.setAutoCommit(false);
        con.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        return con;
    }

    @Override
    protected void connected(Connection conn) throws Exception {
        try (Statement stmt = conn.createStatement()) {
            for(String q : createTables()){
                System.out.println(q);
                stmt.execute(q);
            }
        }
        conn.commit();
    }

    @Override
    public String currentStorageStatus() throws InterruptedException, SQLException {
        String sql = "SELECT " +
                "    database AS dbname," +
                "    table AS relname," +
                "    formatReadableSize(sum(data_compressed_bytes)) AS tablesize," +
                "    formatReadableSize(sum(data_uncompressed_bytes)) AS rawsize" +
                "FROM system.parts" +
                "GROUP BY database, table" +
                "ORDER BY sum(data_compressed_bytes) DESC;";
        Connection conn = connectionPool.take();
        JSONArray arr = new JSONArray();
        try(Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            while(rs.next()) {
                JSONObject obj = new JSONObject();
                obj.put("relname", rs.getString("relname"));
                obj.put("tablesize", rs.getLong("tablesize"));
                obj.put("indexsize", rs.getLong("indexsize"));
                arr.add(obj);
            }
        }
        connectionPool.put(conn);
        return arr.toJSONString();
    }

    private final Map<PVal.Type, String> type2SQLMap = ImmutableMap.of(
            PVal.Type.INT, "INT32",
            PVal.Type.FLOAT, "FLOAT32",
            PVal.Type.STRING, "FixedString(256)");

    @Override
    protected String type2SQL(PVal.Type type){
        return type2SQLMap.get(type);
    }

    protected List<String> createTables() {
        String ntp = content(schema.nodeTemporal);
        String rtp = content(schema.relTemporal);
        return Arrays.asList(
                "CREATE TABLE IF NOT EXISTS node(id Int64,"+content(schema.nodeStatic)+") ORDER BY id",
                "CREATE TABLE IF NOT EXISTS rel (id Int64, r_from Int64, r_to Int64, "+content(schema.relStatic)+") ORDER BY id",
                "CREATE TABLE IF NOT EXISTS node_tp(t DateTime, entity Int64 "+(ntp.length()==0?"":","+ntp)+" )" +
                        "ENGINE = MergeTree()"+
                        "PARTITION BY toYYYYMM(t)" + //按月分区
                        "ORDER BY (entity, t)", //必选排序键
                "CREATE TABLE IF NOT EXISTS rel_tp (t DateTime, entity Int64 "+(rtp.length()==0?"":","+rtp)+" )" +
                        "ENGINE = MergeTree()"+
                        "PARTITION BY toYYYYMM(t)" + //按月分区
                        "ORDER BY (entity, t)" //必选排序键
        );
    }

    @Override
    public List<String> createIndexes() {
        return Arrays.asList();
    }


    @Override
    public ListenableFuture<ServerResponse> execute(AbstractTransaction tx) throws InterruptedException {
        switch (tx.getTxType()) {
            case tx_import_static_data:
                return this.submit(execute((ImportStaticDataTx) tx));
            case tx_import_temporal_data:
                return this.submit(execute((ImportTemporalDataTx) tx), tx.getSection());
            case tx_query_snapshot:
                return this.submit(execute((SnapshotQueryTx) tx));
            case tx_update_temporal_data:
                return this.submit(execute((UpdateTemporalDataTx) tx));
            case tx_query_snapshot_aggr_max:
                return this.submit(execute((SnapshotAggrMaxTx) tx));
            case tx_query_snapshot_aggr_duration:
                return this.submit(execute((SnapshotAggrDurationTx) tx));
            case tx_query_entity_history:
                return this.submit(execute((EntityHistoryTx) tx));
            case tx_query_road_by_temporal_condition:
                return this.submit(execute((EntityTemporalConditionTx) tx));
            case tx_query_reachable_area:
                return this.submit(execute((ReachableAreaQueryTx) tx));
            default:
                throw new UnsupportedOperationException();
        }
    }

    private String insertSQL(String tableName, List<String> props){
        String sql = "INSERT INTO "+tableName+"("+String.join(",", props)+") VALUES ("+qMarks(props.size())+")";
        System.out.println(sql);
        return sql;
    }

    private void lock(Connection con, String tableName) throws SQLException {
//        Statement stmt = con.createStatement();
//        stmt.execute("LOCK TABLE "+tableName+" IN ACCESS EXCLUSIVE MODE;");
//        stmt.closeOnCompletion();
    }

    private Req execute(ImportStaticDataTx tx) {
        return conn -> {
            lock(conn, "rel");
            lock(conn, "node");
            PFieldList nodesData = tx.getNodes();
            List<String> nProps = new ArrayList<>(nodesData.keys());
            int nSize = nodesData.size();
            PFieldList relData = tx.getRels();
            int rSize = relData.size();
            List<String> rProps = new ArrayList<>(relData.keysWithout("r_from", "r_to", "u_sid"));
            String sp = (rProps.size()>0?",":"");
            try (PreparedStatement stat1 = conn.prepareStatement("INSERT INTO node("+String.join(",", nProps)+") VALUES ("+qMarks(nProps.size())+")");
                 PreparedStatement stat2 = conn.prepareStatement(
                         "INSERT INTO rel(r_from,r_to,u_sid"+sp+String.join(",", rProps)+") " +
                                 "SELECT r_from, r_to, ? AS u_sid"+sp+qMarks(rProps.size())+" FROM " +
                                 "(SELECT id AS r_from FROM node WHERE u_sid=? LIMIT 1) AS f, " +
                                 "(SELECT id AS r_to FROM node WHERE u_sid=? LIMIT 1) AS t")) {
                for (int i=0; i<nSize; i++) {
                    for(int j=0; j<nProps.size(); j++){
                        String key = nProps.get(j);
                        setProp(stat1, j+1, nodesData.get(key, i));
                    }
                    stat1.addBatch();
                }
                stat1.executeBatch();
                for (int i=0; i<rSize; i++) {
                    setProp(stat2, 1, relData.get("u_sid", i));
                    int j;
                    for(j=0; j<rProps.size(); j++){
                        String key = rProps.get(j);
                        setProp(stat2, j+2, relData.get(key, i));
                    }
                    setProp(stat2, j+2, relData.get("r_from", i));
                    setProp(stat2, j+3, relData.get("r_to", i));
                    stat2.addBatch();
//                    System.out.println(stat2);
                }
                stat2.executeBatch();
            } catch (SQLException e) {
                throw new TransactionFailedException(e, tx);
            } finally {
                conn.commit();
            }
            return new AbstractTransaction.Result();
        };
    }

    private Req execute(ImportTemporalDataTx tx) {
        return conn -> {
            String tableName = tx.isNode()?"node":"rel";
            lock(conn, tableName);
            lock(conn, tableName+"_tp");
            PFieldList data = tx.getData();
            List<String> props = new ArrayList<>(data.keysWithout("u_sid"));
            int tSize = data.size();
            try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO "+tableName+"_tp(entity,"+String.join(",", props)+") " +
                    "SELECT entity, "+qMarks(props.size())+" FROM " +
                    "(SELECT id AS entity FROM "+tableName+" WHERE u_sid=? LIMIT 1) AS ttt")) {
                for (int i=0; i<tSize; i++) {
                    int j;
                    for(j=0; j<props.size(); j++){
                        String key = props.get(j);
                        setProp(stmt, j+1, data.get(key, i));
                    }
                    setProp(stmt, j+1, data.get("u_sid", i));
                    stmt.addBatch();
//                    System.out.println(stmt);
//                    System.out.println(data.get("u_sid", i).s());
                }
                stmt.executeBatch();
            } catch (SQLException e) {
                throw new TransactionFailedException(e, tx);
            } finally {
                conn.commit();
            }
            return new AbstractTransaction.Result();
        };
    }

    private Req execute(UpdateTemporalDataTx tx) {
        return conn -> {
            String tableName = tx.isNode()?"node_tp":"rel_tp";
            String tsName = tx.isNode()?"node":"rel";
            lock(conn, tableName);
            lock(conn, tsName);
            PFieldList data = tx.getData();
            List<String> props = new ArrayList<>(data.keysWithout("u_sid","st","et"));
            int tSize = data.size();
            String propStr = String.join(",", props);
            try (PreparedStatement stmt1 = conn.prepareStatement("SELECT id, max(t) AS max_t FROM "+tsName+" LEFT JOIN "+tableName+" ON entity=id AND t<=? WHERE u_sid=? GROUP BY id");
                 PreparedStatement stmt2 = conn.prepareStatement("SELECT "+propStr+" FROM "+tableName+" WHERE entity=? and t=?");
                 PreparedStatement stmt3 = conn.prepareStatement("INSERT INTO "+tableName+" (t, entity, "+propStr+") values("+qMarks(props.size()+2)+") " + "on CONFLICT (entity, t) DO NOTHING");
                 PreparedStatement stmt4 = conn.prepareStatement("DELETE FROM "+tableName+" WHERE entity=? AND t>=? AND t<=?")) {
                for (int k=0; k<tSize; k++) {
                    String entityId = data.get("u_sid", k).s();
                    int beginT = data.get("st", k).i();
                    int endT = data.get("et", k).i();

                    stmt1.setString(2, entityId);
                    stmt1.setInt(1, endT);
                    ResultSet rs = stmt1.executeQuery();
                    // insert into table from t1 + 1 with the old value.
                    if (rs.next()) {
                        long eid = rs.getLong("id");
                        stmt2.setLong(1, eid);
                        stmt2.setInt(2, rs.getInt("max_t"));
                        ResultSet r = stmt2.executeQuery();
                        if (r.next()) {
                            stmt3.setInt(1, endT + 1);
                            stmt3.setLong(2, eid);
                            int j = 3;
                            for (String prop : props) {
                                stmt3.setObject(j, r.getObject(prop));
                                j++;
                            }
                            stmt3.execute();
                        }
                        // delete the [t0, t1] rows.
                        stmt4.setLong(1, eid);
                        stmt4.setInt(2, beginT);
                        stmt4.setInt(3, endT);
                        stmt4.execute();
                        // insert into table from t0 with the new value.
                        stmt3.setInt(1, beginT);
                        stmt3.setLong(2, eid);
                        for (int i = 0; i < props.size(); i++) {
                            setProp(stmt3, i+3, data.get(props.get(i), k));
                        }
                        stmt3.execute();
                    }
                }
            } catch (SQLException | NullPointerException e) {
                throw new TransactionFailedException(e, tx);
            } finally {
                conn.commit();
            }
            return new AbstractTransaction.Result();
        };
    }

    private Req execute(SnapshotQueryTx tx) {
        return conn -> {
            String tableName = tx.isNode()?"node_tp":"rel_tp";
            String tsName = tx.isNode()?"node":"rel";
            List<Pair<String, PVal>> res = new ArrayList<>();
            try (PreparedStatement stmt1 = conn.prepareStatement(
                    "SELECT u_sid, " + tx.getPropertyName() + " FROM "+tableName+" AS ts, " +tsName+" AS ss,"+
                            "(SELECT entity, MAX(t) AS max_t FROM "+tableName+" WHERE t<=? GROUP BY entity) AS tmp " +
                            "WHERE ts.entity=tmp.entity AND tmp.entity=ss.id AND ts.t=tmp.max_t")) {
                stmt1.setInt(1, tx.getTimestamp());
                ResultSet rs = stmt1.executeQuery();
                while (rs.next()) {
                    res.add(Pair.of(rs.getString("u_sid"), getPVal(rs, tx.isNode(), tx.getPropertyName(), tx.getPropertyName())));
                }
            } catch (SQLException e) {
                throw new TransactionFailedException(e, tx);
            } finally {
                conn.commit();
            }
            SnapshotQueryTx.Result result = new SnapshotQueryTx.Result();
            result.answer(res);
            return result;
        };
    }


    private Req execute(SnapshotAggrMaxTx tx) {
        return conn -> {
            String tableName = tx.isNode()?"node":"rel";
            List<Pair<String, PVal>> res = new ArrayList<>();
            try (PreparedStatement stmt2 = conn.prepareStatement(
                    "SELECT u_sid, MAX(" + tx.getP() + ") AS max_p FROM "+tableName+" AS ss, "+tableName+"_tp AS ts, "+
                            "(SELECT id, COALESCE(MAX(t),0) AS max_t FROM "+tableName+" LEFT JOIN "+tableName+"_tp ON id=entity AND t<=? GROUP BY id) AS tmp " +
                            "WHERE ts.entity=tmp.id AND ss.id=tmp.id AND ts.t>=tmp.max_t AND ts.t<=? GROUP BY u_sid")) {
                stmt2.setInt(1, tx.getT0());
                stmt2.setInt(2, tx.getT1());
                ResultSet rs = stmt2.executeQuery();
                while (rs.next()) {
                    res.add(Pair.of(rs.getString("u_sid"), getPVal(rs, tx.isNode(), tx.getP(), "max_p")));
                }
            } catch (SQLException e) {
                throw new TransactionFailedException(e, tx);
            } finally {
                conn.commit();
            }
            SnapshotAggrMaxTx.Result result = new SnapshotAggrMaxTx.Result();
            result.setPropMaxValue(res);
            return result;
        };
    }

    private Req execute(SnapshotAggrDurationTx tx) {
        final TreeSet<PVal> intStart = tx.getIntStartTreeSet();
        return conn -> {
            String tableName = tx.isNode()?"node":"rel";
            List<Triple<String, PVal, Integer>> res = new ArrayList<>();
            try (PreparedStatement stmt0 = conn.prepareStatement("SELECT u_sid, t, " + tx.getP() + " FROM "+tableName+" AS ss, "+tableName+"_tp AS ts, "+
                    "(SELECT id, COALESCE(MAX(t),0) AS max_t FROM "+tableName+" LEFT JOIN "+tableName+"_tp ON id=entity AND t<=? GROUP BY id) AS tmp " +
                    "WHERE ts.entity=tmp.id AND ss.id=tmp.id AND ts.t>=tmp.max_t AND ts.t<=? ORDER BY u_sid, t")) {
                stmt0.setInt(1, tx.getT0());
                stmt0.setInt(2, tx.getT1());
                ResultSet rs = stmt0.executeQuery();
                int lastTime = -1;
                String lastId = null;
                PVal lastStatus = null;
                Map<Pair<String, PVal>, Integer> buffer = new HashMap<>();
                while (rs.next()) {
                    String id = rs.getString("u_sid");
                    int t = rs.getInt("t");
                    PVal property = getPVal(rs, tx.isNode(), tx.getP(), tx.getP());
                    if (id.equals(lastId)) {
                        int duration = t - lastTime;
                        PVal statusGrp = intStart.floor( lastStatus);
                        if(statusGrp!=null) buffer.merge(Pair.of(id, statusGrp), duration, Integer::sum);
                        lastTime = t;
                    } else {
                        if(lastId!=null){
                            PVal statusGrp = intStart.floor( lastStatus);
                            buffer.merge(Pair.of(lastId, statusGrp), tx.getT1()-lastTime+1, Integer::sum);
                        }
                        lastTime = tx.getT0();
                    }
                    lastStatus = property;
                    lastId = id;
                }
                if (lastId != null) {
                    PVal statusGrp = intStart.floor(lastStatus);
                    if(statusGrp!=null) buffer.merge(Pair.of(lastId, statusGrp), tx.getT1() - lastTime + 1, Integer::sum);
                }
                for (Map.Entry<Pair<String, PVal>, Integer> e : buffer.entrySet()) {
                    res.add(Triple.of(e.getKey().getKey(), e.getKey().getValue(), e.getValue()));
                }
            } catch (SQLException e) {
                throw new TransactionFailedException(e, tx);
            } finally {
                conn.commit();
            }
            SnapshotAggrDurationTx.Result result = new SnapshotAggrDurationTx.Result();
            result.setStatusDuration(res);
            return result;
        };
    }

    private Req execute(EntityHistoryTx tx) {
        return conn -> {
            String tableName = tx.isNode()?"node":"rel";
            List<Triple<Integer, Integer, PVal>> res = new ArrayList<>();
            try (PreparedStatement stmt1 = conn.prepareStatement("SELECT t, "+tx.getProp()+" FROM "+tableName+"_tp, "+
                    "(SELECT id, COALESCE(MAX(t),0) AS max_t FROM "+tableName+" LEFT JOIN "+tableName+"_tp ON id=entity AND t<=? WHERE u_sid=? GROUP BY id) AS tmp " +
                    "WHERE entity=id AND max_t<=t AND t<=? ORDER BY t")){
                stmt1.setInt(1, tx.getBeginTime());
                stmt1.setString(2, tx.getEntity());
                stmt1.setInt(3, tx.getEndTime());
                System.out.println(stmt1);
                ResultSet r = stmt1.executeQuery();
                int lastTime = -1;
                PVal lastVal = null;
                while (r.next()) {
                    PVal val = getPVal(r, tx.isNode(), tx.getProp(), tx.getProp());
                    int t = r.getInt("t");
                    if (lastTime != -1) {
                        res.add(Triple.of(lastTime, t-1, lastVal));
                        lastTime = t;
                    } else {
                        lastTime = Math.max(t, tx.getBeginTime());
                    }
                    lastVal = val;
                }
                if (lastTime != -1) {
                    res.add(Triple.of(lastTime, tx.getEndTime(), lastVal));
                }
            } catch (SQLException e) {
                throw new TransactionFailedException(e, tx);
            } finally {
                conn.commit();
            }
            EntityHistoryTx.Result result = new EntityHistoryTx.Result();
            result.setHistory(res);
            return result;
        };
    }

    private Req execute(EntityTemporalConditionTx tx) {
        return conn -> {
            String tableName = tx.isNode()?"node":"rel";
            List<String> res = new ArrayList<>();
            try (PreparedStatement stmt0 = conn.prepareStatement("SELECT DISTINCT u_sid FROM "+tableName+" AS ss, " +tableName+"_tp AS ts, "+
                    "(SELECT id, COALESCE(MAX(t),0) AS max_t FROM "+tableName+" LEFT JOIN "+tableName+"_tp ON id=entity AND t<=? GROUP BY id) AS tmp " +
                    "WHERE ss.id=ts.entity AND ss.id=tmp.id AND ts."  + tx.getP() + " >= ? AND ts." + tx.getP() + " <= ? AND "+
                    "tmp.max_t<=ts.t AND ts.t<=?")) {
                stmt0.setInt(1, tx.getT0());
                setProp(stmt0, 2, tx.getVMin());
                setProp(stmt0, 3, tx.getVMax());
                stmt0.setInt(4, tx.getT1());
                System.out.println(stmt0);
                ResultSet rs = stmt0.executeQuery();
                while (rs.next()) {
                    res.add(rs.getString("u_sid"));
                }
            } catch (SQLException e) {
                throw new TransactionFailedException(e, tx);
            } finally {
                conn.commit();
            }
            EntityTemporalConditionTx.Result result = new EntityTemporalConditionTx.Result();
            result.setEntities(res);
            return result;
        };
    }


    protected Req execute(ReachableAreaQueryTx tx) {
        return conn -> {
            List<Pair<Integer, String>> answers = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement("SELECT id, u_sid FROM node");
                 PreparedStatement stmt0 = conn.prepareStatement("SELECT t, "+tx.getProp()+" AS prop FROM rel_tp AS ts, "+
                         "(SELECT id, COALESCE(MAX(t),0) AS max_t FROM rel LEFT JOIN rel_tp ON entity=id AND id=? AND t<=? GROUP BY id) AS tmp " +
                         "WHERE ts.entity=tmp.id AND tmp.max_t<=ts.t AND ts.t<=? ORDER BY t");
                 PreparedStatement stmt1 = conn.prepareStatement("SELECT id FROM rel WHERE r_from=?");
                 PreparedStatement stmt2 = conn.prepareStatement("SELECT r_to FROM rel WHERE id=?");
                 PreparedStatement stmt3 = conn.prepareStatement("SELECT u_sid FROM node WHERE id=?");
                 PreparedStatement stmt4 = conn.prepareStatement("SELECT u_sid FROM rel WHERE id=?")) {
                ResultSet rs = stmt.executeQuery();
                long startNodeId = -1;
                Map<Long, String> map = new HashMap<>();
                while(rs.next()) {
                    long innerId = rs.getLong("id");
                    String u_sid = rs.getString("u_sid");
                    map.put(innerId, u_sid);
                    if(tx.getStartNode().equals(u_sid)) startNodeId = innerId;
                }
                ReachableAreaPG algo = new ReachableAreaPG(startNodeId, tx.getDepartureTime(), tx.getTravelTime(), stmt0, stmt1, stmt2);
                algo.setDebug(stmt3, stmt4);
                for(ReachableAreaQueryTx.TemporalDijkstraAlgo.NodeCross n : algo.run()){
                    answers.add(Pair.of(n.getArriveTime(), map.get(n.getId())));
                }
                ReachableAreaQueryTx.Result result = new ReachableAreaQueryTx.Result();
                result.setNodeArriveTime(answers);
                result.setInnerResults(algo.getInnerResults());
                return result;
            } catch (SQLException e) {
                throw new TransactionFailedException(e, tx);
            } finally {
                conn.commit();
            }
        };
    }

    public static class ReachableAreaPG extends ReachableAreaQueryTx.TemporalDijkstraAlgo {
        private final PreparedStatement stmt0, stmt1, stmt2;

        public ReachableAreaPG(long startId, int startTime, int travelTime, PreparedStatement stmt0, PreparedStatement stmt1, PreparedStatement stmt2){
            super(startId, startTime, travelTime, true);
            this.stmt0 = stmt0;
            this.stmt1 = stmt1;
            this.stmt2 = stmt2;
        }

        protected int getEarliestArriveTime(Long roadId, int departureTime) throws UnsupportedOperationException {
            try {
                stmt0.setLong(1, roadId);
                stmt0.setInt(2, departureTime);
                stmt0.setInt(3, endTime);
                ResultSet rs = stmt0.executeQuery();
                int earT = Integer.MAX_VALUE;
                while(rs.next()){
                    int t = rs.getInt("t");
                    int travel_t = rs.getInt("prop");
                    if(t<departureTime) t = departureTime;
                    int arrT = t+travel_t;
                    if(arrT<earT) earT = arrT;
                    if(t>earT) break;
                }
                if(earT<Integer.MAX_VALUE) return earT;
                else throw new UnsupportedOperationException();
            } catch (SQLException e) {
                throw new TransactionFailedException(e);
            }
        }

        @Override
        protected Iterable<Long> getAllOutRoads(long nodeId) {
            List<Long> lst = new ArrayList<>();
            try {
                stmt1.setLong(1, nodeId);
                ResultSet rs = stmt1.executeQuery();
                while(rs.next()){
                    lst.add(rs.getLong("id"));
                }
            } catch (SQLException e) {
                throw new TransactionFailedException(e);
            }
            return lst;
        }

        @Override
        protected long getEndNodeId(long roadId) {
            try {
                stmt2.setLong(1, roadId);
                ResultSet rs = stmt2.executeQuery();
                if(rs.next()){
                    return rs.getLong("r_to");
                }
            } catch (SQLException e) {
                throw new TransactionFailedException(e);
            }
            throw new IllegalStateException("SNH: edge end node not found!");
        }

        private PreparedStatement nstmt, rstmt;
        public void setDebug(PreparedStatement stmt3, PreparedStatement stmt4) {
            this.nstmt = stmt3;
            this.rstmt = stmt4;
        }

        @Override
        protected String nodeId2Str(long nodeId) {
            try {
                nstmt.setLong(1, nodeId);
                ResultSet rs = nstmt.executeQuery();
                if(rs.next()){
                    return rs.getString("u_sid");
                }else{
                    throw new IllegalStateException("SNH: node not found!");
                }
            } catch (SQLException e) {
                throw new TransactionFailedException(e);
            }
        }

        @Override
        protected String relId2Str(long relId) {
            try {
                rstmt.setLong(1, relId);
                ResultSet rs = rstmt.executeQuery();
                if(rs.next()){
                    return rs.getString("u_sid");
                }else{
                    throw new IllegalStateException("SNH: edge not found: "+relId);
                }
            } catch (SQLException e) {
                throw new TransactionFailedException(e);
            }
        }
    }
}
