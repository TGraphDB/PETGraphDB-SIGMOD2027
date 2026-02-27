package edu.buaa.client;

import com.google.common.util.concurrent.ListenableFuture;
import edu.buaa.common.client.MariaDbExecutorClient;
import edu.buaa.common.transaction.AbstractTransaction;
import edu.buaa.common.utils.PFieldList;
import edu.buaa.common.transaction.*;
import edu.buaa.common.utils.PVal;
import edu.buaa.utils.Helper;
import edu.buaa.utils.Pair;
import edu.buaa.utils.Triple;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;

/**
 * Created by sjh. 2021.11.18
 */
public class MariaDBTemporalTableClient extends MariaDbExecutorClient {

    @Override
    protected List<String> createTables() {
        String ntp = content(schema.nodeTemporal);
        String rtp = content(schema.relTemporal);
        return Arrays.asList(
                "CREATE TABLE IF NOT EXISTS node(id BIGINT PRIMARY KEY AUTO_INCREMENT, "+content(schema.nodeStatic)+")",
                "CREATE TABLE IF NOT EXISTS rel(id BIGINT PRIMARY KEY AUTO_INCREMENT, r_from BIGINT, r_to BIGINT, "+content(schema.relStatic)+")",
                "CREATE TABLE IF NOT EXISTS node_tp(st_time TIMESTAMP(0), en_time TIMESTAMP(0), entity BIGINT, " + //note: st is closed [, en is not closed ) because mariadb's restrict.
                        (ntp.length()==0?"":ntp+",")+" PERIOD FOR time_period(st_time, en_time))",
                // InnoDB默认对主键建立聚簇索引。如果不指定主键，InnoDB会用一个具有唯一且非空值的索引来代替。如果不存在这样的索引，InnoDB会定义一个隐藏的主键，然后对其建立聚簇索引。
                "CREATE TABLE IF NOT EXISTS rel_tp(st_time TIMESTAMP(0), en_time TIMESTAMP(0), entity BIGINT, " + //note: st is closed [, en is not closed ) because mariadb's restrict.
                        (rtp.length()==0?"":rtp+",")+" PERIOD FOR time_period(st_time, en_time))"
        );
    }

    @Override
    public List<String> createIndexes(){
        return Arrays.asList(
                "CREATE UNIQUE INDEX IF NOT EXISTS n_u_sid on node(u_sid)",
                "CREATE UNIQUE INDEX IF NOT EXISTS r_u_sid on rel(u_sid)",
                "CREATE INDEX IF NOT EXISTS r_f on rel(r_from)",
                "CREATE UNIQUE INDEX IF NOT EXISTS ntp_e_st ON node_tp(entity, st_time)",
                "CREATE UNIQUE INDEX IF NOT EXISTS rtp_e_st ON rel_tp(entity, st_time)",
                "CREATE INDEX IF NOT EXISTS ntp_st_en on node_tp(st_time, en_time)",
                "CREATE INDEX IF NOT EXISTS rtp_st_en on rel_tp(st_time, en_time)"
        );
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

    private Req execute(ImportStaticDataTx tx) {
        return conn -> {
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
//                    System.out.println(stat1);
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
            }
            conn.commit();
            return new AbstractTransaction.Result();
        };
    }

    public Req execute(ImportTemporalDataTx tx) {
        return conn -> {
            String tableName = tx.isNode()?"node":"rel";
            PFieldList data = tx.getData();
            List<String> props = new ArrayList<>(data.keysWithout("u_sid", "t"));
            int tSize = data.size();
            try (PreparedStatement stmt = conn.prepareStatement("SELECT id,entity FROM "+tableName+" left join "+tableName+"_tp on entity=id WHERE u_sid=? LIMIT 1");
                 PreparedStatement stmt0 = conn.prepareStatement("INSERT INTO "+tableName+"_tp(entity,st_time,en_time,"+String.join(",", props)+") " +
                         "SELECT entity,?,?,"+qMarks(props.size())+" FROM " +
                         "(SELECT id AS entity FROM "+tableName+" WHERE u_sid=? LIMIT 1) AS ttt")) {
//                Set<String> hasInsert = new HashSet<>();
                for (int i=0; i<tSize; i++) {
                    String rawId = data.get("u_sid", i).s();
                    int time = data.get("t", i).i();
                    stmt.setString(1, rawId);
                    stmt.addBatch();
//                    System.out.println(stmt);
                    ResultSet rs = stmt.executeQuery();
                    if(rs.next()){
                        long innerId = rs.getLong("id");
                        long entity = rs.getLong("entity");
                        if(entity==0){ // SQL VALUE IS NULL
                            stmt0.setTimestamp(1, new Timestamp(time * 1000L));
                            stmt0.setTimestamp(2, new Timestamp(Integer.MAX_VALUE * 1000L));
                            int j;
                            for(j=0; j<props.size(); j++){
                                String key = props.get(j);
                                setProp(stmt0, j+3, data.get(key, i));
                            }
                            stmt0.setString(j+3, rawId);
                            stmt0.addBatch();
//                            System.out.println(stmt0);
                            stmt0.executeBatch();
                        }else{
                            updateTP(conn, tableName+"_tp", innerId, time, Integer.MAX_VALUE, props, data, i);
                        }
                    }else{
                        throw new IllegalStateException("SNH: no static data for u_sid="+rawId);
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            conn.commit();
            return new AbstractTransaction.Result();
        };
    }

    private String setPropL(List<String> props, PFieldList data, int i){
        StringBuilder sb = new StringBuilder();
        for(String prop : props){
            sb.append(prop).append("=?,");
//            sb.append(prop).append('=').append(data.get(prop, i)).append(',');
        }
        sb.setLength(sb.length()-1);
        return sb.toString();
    }

    private void updateTP(Connection conn, String tableName, long innerId, int tBegin, int tEnd, List<String> props, PFieldList data, int i) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("UPDATE "+tableName+" FOR PORTION OF time_period FROM ? TO ? " +
                "SET "+setPropL(props, data, i)+" WHERE entity=?")){
            stmt.setTimestamp(1, new Timestamp(tBegin * 1000L));
            stmt.setTimestamp(2, new Timestamp(tEnd * 1000L));
            int j;
            for(j=0;j<props.size();j++){
                String prop = props.get(j);
                setProp(stmt, j+3, data.get(prop, i));
            }
            stmt.setLong(j+3, innerId);
            stmt.addBatch();
//            System.out.println(stmt);
            stmt.executeBatch();
//            conn.commit();
        }
    }

    private Req execute(UpdateTemporalDataTx tx) {
        return conn -> {
            String tableName = tx.isNode()?"node":"rel";
            PFieldList data = tx.getData();
            List<String> props = new ArrayList<>(data.keysWithout("u_sid", "st", "et"));
            int tSize = data.size();
            try (PreparedStatement stmt = conn.prepareStatement("SELECT id,entity FROM "+tableName+" left join "+tableName+"_tp on entity=id WHERE u_sid=? LIMIT 1");
                 PreparedStatement stmt0 = conn.prepareStatement("INSERT INTO "+tableName+"_tp(entity,st_time,en_time,"+String.join(",", props)+") " +
                         "SELECT entity,?,?,"+qMarks(props.size())+" FROM " +
                         "(SELECT id AS entity FROM "+tableName+" WHERE u_sid=? LIMIT 1) AS ttt")) {
//                Set<String> hasInsert = new HashSet<>();
                for (int i=0; i<tSize; i++) {
                    String rawId = data.get("u_sid", i).s();
                    stmt.setString(1, rawId);
                    stmt.addBatch();
//                    System.out.println(stmt);
                    ResultSet rs = stmt.executeQuery();
                    if(rs.next()){
                        long innerId = rs.getLong("id");
                        long entity = rs.getLong("entity");
                        int st = data.get("st", i).i();
                        int et = data.get("et", i).i();
                        if(entity==0){ // SQL VALUE IS NULL
                            stmt0.setTimestamp(1, new Timestamp(st * 1000L));
                            stmt0.setTimestamp(2, new Timestamp(et * 1000L));
                            int j;
                            for(j=0; j<props.size(); j++){
                                String key = props.get(j);
                                setProp(stmt0, j+3, data.get(key, i));
                            }
                            stmt0.setString(j+3, rawId);
                            stmt0.addBatch();
//                            System.out.println(stmt0);
                            stmt0.executeBatch();
                        }else{
                            updateTP(conn, tableName+"_tp", innerId, st, et, props, data, i);
                        }
                    }else{
                        throw new IllegalStateException("SNH: no static data for u_sid="+rawId);
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            conn.commit();
            return new AbstractTransaction.Result();
        };
    }

    private Req execute(SnapshotQueryTx tx) {
        return conn -> {
            String tableName = tx.isNode()?"node":"rel";
            List<Pair<String, PVal>> res = new ArrayList<>();
            String sql = "SELECT u_sid, property FROM "+tableName+" as ss, " +
                    "(SELECT " + tx.getPropertyName() + " as property, entity FROM "+tableName+"_tp " +
                    "WHERE st_time<=? AND en_time>? ORDER BY entity, st_time) as tt WHERE entity=id";
            System.out.println(sql);
            try (PreparedStatement stat = conn.prepareStatement(sql)) {
                stat.setTimestamp(1, new Timestamp(tx.getTimestamp() * 1000L));
                stat.setTimestamp(2, new Timestamp(tx.getTimestamp() * 1000L));
                ResultSet rs = stat.executeQuery();
                while (rs.next()) {
                    res.add(Pair.of(rs.getString("u_sid"), PVal.v(rs.getObject("property"))));
                }
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
            String sql = "SELECT u_sid, property FROM "+tableName+" as ss, " +
                    "(SELECT MAX(" + tx.getP() + ") as property, entity FROM "+tableName+"_tp " +
                    "WHERE st_time<=? AND en_time>? GROUP BY entity) as tt WHERE entity=id";
            System.out.println(sql);
            try (PreparedStatement stat = conn.prepareStatement(sql)) {
                stat.setTimestamp(1, new Timestamp(tx.getT1() * 1000L));
                stat.setTimestamp(2, new Timestamp(tx.getT0() * 1000L));
                ResultSet rs = stat.executeQuery();
                while(rs.next()) {
                    res.add(Pair.of(rs.getString("u_sid"), PVal.v(rs.getObject("property"))));
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            SnapshotAggrMaxTx.Result result = new SnapshotAggrMaxTx.Result();
            result.setPropMaxValue(res);
            return result;
        };
    }

    private Req execute(SnapshotAggrDurationTx tx) {
        return conn -> {
            List<Triple<String, PVal, Integer>> res = new ArrayList<>();
            int t0 = tx.getT0(), t1 = tx.getT1();
            String tableName = tx.isNode()?"node":"rel";
            String sql = "SELECT u_sid, "+ tx.getP() + ",st_time,en_time FROM "+tableName+", "+tableName+"_tp " +
                    "WHERE st_time<=? AND en_time>? AND entity=id ORDER BY entity, st_time";
            System.out.println(sql);
            TreeSet<PVal> intervalStarts = tx.getIntStartTreeSet();
            try (PreparedStatement stat = conn.prepareStatement(sql)){
                stat.setTimestamp(1, new Timestamp(t1*1000L));
                stat.setTimestamp(2, new Timestamp(t0*1000L));
                ResultSet rs = stat.executeQuery();
                Map<Pair<String, PVal>, Integer> map = new HashMap<>();
                while (rs.next()) {
                    String id = rs.getString("u_sid");
                    PVal value = getPVal(rs, tx.isNode(), tx.getP(), tx.getP());
                    int st = (int) (rs.getTimestamp("st_time").getTime() / 1000L);
                    int en = (int) (rs.getTimestamp("en_time").getTime() / 1000L) - 1;
                    int left = Math.max(t0, st);
                    int right = Math.min(t1, en);
                    int duration = right - left + 1;
                    PVal valGrp = intervalStarts.floor(value);
                    if (valGrp != null) {
                        map.merge(Pair.of(id, valGrp), duration, Integer::sum);
                    }
                }
                for(Map.Entry<Pair<String, PVal>, Integer> e : map.entrySet()){
                    res.add(Triple.of(e.getKey().getKey(), e.getKey().getValue(), e.getValue()));
                }
            }
            SnapshotAggrDurationTx.Result result = new SnapshotAggrDurationTx.Result();
            result.setStatusDuration(res);
            return result;
        };
    }

    private Req execute(EntityHistoryTx tx) {
        return conn -> {
            List<Triple<Integer, Integer, PVal>> res = new ArrayList<>();
            int t0 = tx.getBeginTime(), t1 = tx.getEndTime();
            String tableName = tx.isNode()?"node":"rel";
            String sql = "SELECT " + tx.getProp() + " as property, st_time, en_time FROM "+tableName+", "+tableName+"_tp " +
                    "WHERE st_time<=? AND en_time>? AND entity=id AND u_sid=? ORDER BY st_time";
            System.out.println(sql);
            try (PreparedStatement stat = conn.prepareStatement(sql)){
                stat.setTimestamp(1, new Timestamp(t1 * 1000L));
                stat.setTimestamp(2, new Timestamp(t0 * 1000L));
                stat.setString(3, tx.getEntity());
                ResultSet rs = stat.executeQuery();
                while (rs.next()) {
                    int st = (int) (rs.getTimestamp("st_time").getTime() / 1000L);
                    int en = (int) (rs.getTimestamp("en_time").getTime() / 1000L) - 1;
                    int beginT = Math.max(st, tx.getBeginTime());
                    int endT = Math.min(en, tx.getEndTime());
                    if(beginT<=endT) res.add(Triple.of(beginT, endT, PVal.v(rs.getObject("property"))));
                }
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
            String sql = "SELECT DISTINCT u_sid FROM "+tableName+","+tableName+"_tp WHERE id=entity AND st_time<=? AND en_time>? AND " +
                    tx.getP() + " BETWEEN ? AND ?";
            System.out.println(sql);
            try (PreparedStatement stat = conn.prepareStatement(sql)) {
                stat.setTimestamp(1, new Timestamp(tx.getT1() * 1000L));
                stat.setTimestamp(2, new Timestamp(tx.getT0() * 1000L));
                setProp(stat, 3, tx.getVMin());
                setProp(stat, 4, tx.getVMax());
                ResultSet rs = stat.executeQuery();
                while (rs.next()) {
                    res.add(rs.getString("u_sid"));
                }
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
                 PreparedStatement stmt0 = conn.prepareStatement("SELECT " + tx.getProp() + " as prop, st_time, en_time FROM rel_tp " +
                         "WHERE st_time<=? AND en_time>? AND entity=? ORDER BY st_time");
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
                stmt0.setTimestamp(1, new Timestamp(this.endTime * 1000L));
                stmt0.setTimestamp(2, new Timestamp(departureTime * 1000L));
                stmt0.setLong(3, roadId);
                ResultSet rs = stmt0.executeQuery();
                int earT = Integer.MAX_VALUE;
                boolean first = true;
                while(rs.next()){
                    int st = (int) (rs.getTimestamp("st_time").getTime() / 1000L);
                    int en = (int) (rs.getTimestamp("en_time").getTime() / 1000L);
                    if(first && st>departureTime) break;
                    first=false;
                    int beginT = Math.max(st, departureTime);
                    int endT = Math.min(en, this.endTime);
                    if(beginT<=endT) {
                        int travel_t = rs.getInt("prop");
                        int arrT = beginT + travel_t;
                        if (arrT < earT) earT = arrT;
                        if (st > earT) break;
                    }
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
                    throw new IllegalStateException("SNH: edge not found!");
                }
            } catch (SQLException e) {
                throw new TransactionFailedException(e);
            }
        }
    }

    public  class Importer{
        void loadRelTp(){
            String path = Helper.fileLinuxPath(new File("d:/tgraph/data/syn/edge_temporal_data_conv.csv"));
//            schema.
            String sql = String.format("LOAD DATA INFILE '%s' REPLACE INTO TABLE %s FIELDS TERMINATED BY ',' (entity,st_time,en_time,);");
        }
    }
}

