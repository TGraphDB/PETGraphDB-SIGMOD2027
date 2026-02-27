package edu.buaa.server.system;

import com.alibaba.fastjson.JSON;
import edu.buaa.common.RuntimeEnv;
import edu.buaa.common.server.DBSocketServer;
import edu.buaa.common.transaction.*;
import edu.buaa.common.utils.PFieldList;
import edu.buaa.common.utils.PVal;
import edu.buaa.utils.Helper;
import edu.buaa.utils.Triple;
import org.act.temporalProperty.impl.CompressionType;
import org.act.temporalProperty.impl.Options;
import org.act.temporalProperty.query.TimePointL;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.temporal.TimeIntervalRangeQuery;
import org.neo4j.graphdb.temporal.TimePoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

/**
 * 基本照抄的TGraph2.3的代码，没有测试过正确性，但可以编译和运行
 * 经过多轮测试没发现问题
 */
public class WorkloadStatisticServer implements DBSocketServer.DBKernelProxy {
    public static final Label LABEL = Label.label("test");
    public static final RelationshipType RELATIONSHIP_TYPE = RelationshipType.withName("test");

    protected DatabaseManagementService dbms;

    protected static DBSocketServer.DBKernelProxy dbKernelProxy;
    protected static Logger log;

    private static void setDefaultArgs() {
        dbKernelProxy = new WorkloadStatisticServer();
        log = LoggerFactory.getLogger(WorkloadStatisticServer.class);
        Options.setCTP(CompressionType.SNAPPY);
    }

    protected static void startServer() {
        DBSocketServer server = new DBSocketServer(dbDir(), dbKernelProxy, Integer.parseInt(Helper.mustEnv("DB_PORT")), true);
        RuntimeEnv env = RuntimeEnv.getCurrentEnv();
        String serverCodeVersion = env.name() + "." + Helper.codeGitVersion();
        System.out.println("server code version:" + serverCodeVersion);
        try {
            server.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static void main(String[] args) {
        setDefaultArgs();
        startServer();
    }

    static File dbDir() {
        String path = Helper.mustEnv("DB_PATH");
        File dbDir = new File(path);
        if (!dbDir.exists()) {
            if (dbDir.mkdirs()) return dbDir;
            else throw new IllegalArgumentException("invalid dbDir");
        } else if (!dbDir.isDirectory()) {
            throw new IllegalArgumentException("invalid dbDir");
        }
        return dbDir;
    }

    @Override
    public void start(File path) throws Exception {
        dbms = new DatabaseManagementServiceBuilder(path.toPath()).build();
        initStatistic();
    }

    @Override
    public void shutdown() {
        printReport();
        dbms.shutdown();
    }

    public static class Stat {
        private Set<String> properties = new HashSet<>();
        public Map<Long, Integer> entityTimeOverlapCnt = new HashMap<>();
        private long totalEntityCnt = 0;
        private long totalReqCnt = 0;
        private long timeBegin = Integer.MAX_VALUE;
        private long timeEnd = -1;
        private DescriptiveStatistics s;

        public void addT0(long time){
            if( time < timeBegin ) timeBegin = time;
        }

        public void addT1(long time){
            if( time > timeEnd ) timeEnd = time;
        }

        private DescriptiveStatistics postProcess(){
            s = new DescriptiveStatistics();
            for(int v : entityTimeOverlapCnt.values()){
                s.addValue(v);
            }
            return s;
        }

        @Override
        public String toString(){
            if(s==null) postProcess();
            return  "total entity cnt: " + totalEntityCnt + "\n" +
                    "total req cnt: " + totalReqCnt + "\n" +
                    "accessed time range: " + timeBegin + " ~ " + timeEnd + "\n" +
                    "accessed entity cnt:" + entityTimeOverlapCnt.size() + "\n" +
                    "entities that has overlaps: " +
                    "(p0%): " + s.getPercentile(0) + "," +
                    "(p25%): " + s.getPercentile(25) + "," +
                    "(p50%): " + s.getPercentile(50) + "," +
                    "(p75%): " + s.getPercentile(75) + "," +
                    "(p100%): " + s.getPercentile(100) + "" +
                    "";
        }
    }

    private Stat nodeStat = new Stat();
    private Stat relStat = new Stat();


    private void initStatistic() {
        GraphDatabaseService db = dbms.database("neo4j");
        try(Transaction transaction = db.beginTx()){
            all(true, transaction, entity -> {
                nodeStat.totalEntityCnt++;
            });
            all(false, transaction, entity -> {
                relStat.totalEntityCnt++;
            });
        }
    }

    private void printReport() {
        System.out.println("NODE STATISTICS:================\n"+nodeStat);
        System.out.println("REL STATISTICS:================\n"+relStat);
//        GraphDatabaseService db = dbms.database("neo4j");
//        try(Transaction transaction = db.beginTx()){
//            all(true, transaction, entity -> {
//                nodeStat.totalEntityCnt++;
//                TimePoint st = new TimePoint(0), et = TimePoint.NOW;
//                for(String prop: nodeStat.properties){
//                    entity.getTemporalProperty(prop, st, et, new TimeIntervalWithDefaultValue(st, et) {
//                        @Override
//                        public void onTimeInterval(TimePoint tb, TimePoint te, int val) {
//                            if(val==0){
//                                return;
//                            }else if(val==1){
//                                nodeStat.addT0(tb.getTime());
//
//                            }
//                        }
//                    })
//                }
//
//            });
//            all(false, transaction, entity -> {
//                relStat.totalReqCnt++;
//            });
//
//        }
    }

    @Override
    public AbstractTransaction.Result execute(String line) throws TransactionFailedException {
        try {
            AbstractTransaction tx = JSON.parseObject(line, AbstractTransaction.class);
            switch (tx.getTxType()) {
                case tx_import_static_data:
                    return execute((ImportStaticDataTx) tx);
                case tx_import_temporal_data:
                    return execute((ImportTemporalDataTx) tx);
                case tx_update_temporal_data:
                    return execute((UpdateTemporalDataTx) tx);
                case tx_query_snapshot:
                    return execute((SnapshotQueryTx) tx);
                case tx_query_snapshot_aggr_max:
                    return execute((SnapshotAggrMaxTx) tx);
                case tx_query_snapshot_aggr_duration:
                    return execute((SnapshotAggrDurationTx) tx);
                case tx_query_entity_history:
                    return execute((EntityHistoryTx) tx);
                case tx_query_road_by_temporal_condition:
                    return execute((EntityTemporalConditionTx) tx);
                case tx_query_reachable_area:
                default:
                    throw new UnsupportedOperationException();
            }
        }catch (Exception e){
            int end = Math.min(60, line.length());
            log.error("ERROR processing TX: "+line.substring(0, end), e);
            throw new TransactionFailedException(e);
        }
    }

    protected void all(boolean node, Transaction transaction, Consumer<Entity> callBack){
        if (node) {
            for(Node n : transaction.getAllNodes()){
                callBack.accept(n);
            }
        }
        else{
            for(Relationship r : transaction.getAllRelationships()){
                callBack.accept(r);
            }
        }
    }

    protected AbstractTransaction.Result execute(ImportStaticDataTx tx) {
        GraphDatabaseService db = dbms.database("neo4j");
        PFieldList nodesData = tx.getNodes();
        Set<String> props = nodesData.keysWithout("u_sid");
        int nSize = nodesData.size();
        try (Transaction transaction = db.beginTx()) {
            for (int i = 0; i < nSize; i++) {
                Node node = transaction.createNode(LABEL);
                String id = nodesData.get("u_sid", i).s();
                node.setProperty("u_sid", id);
                for (String key : props) {
                    node.setProperty(key, nodesData.get(key, i).getVal());
                }
            }
            transaction.commit();
        }
        PFieldList relData = tx.getRels();
        props = relData.keysWithout("u_sid", "r_from", "r_to");
        int rSize = relData.size();
        try (Transaction transaction = db.beginTx()) {
            for (int i = 0; i < rSize; i++) {
                String fromId = relData.get("r_from", i).s();
                String toId = relData.get("r_to", i).s();
                String id = relData.get("u_sid", i).s();
                Node fromNode = transaction.findNode(LABEL, "u_sid", fromId);
                Node toNode= transaction.findNode(LABEL, "u_sid", toId);
                Relationship relationship = fromNode.createRelationshipTo(toNode, RELATIONSHIP_TYPE);
                relationship.setProperty("u_sid", id);
                for (String key : props) {
                    relationship.setProperty(key, relData.get(key, i).getVal());
                }
            }
            transaction.commit();
        }
        return new AbstractTransaction.Result();
    }

    public static abstract class TimeIntervalWithDefaultValue extends TimeIntervalRangeQuery{

        public TimeIntervalWithDefaultValue(TimePointL beginTime, TimePointL endTime) {
            super(beginTime, endTime);
        }

        @Override
        public void onEntry(TimePointL beginTime, TimePointL endTime, Object val) {
            TimePoint tb = new TimePoint(beginTime.getTime());
            TimePoint te = endTime.isNow() ? TimePoint.NOW : new TimePoint(endTime.getTime());
            if(val==null){
                onTimeInterval(tb, te,0);
            }else if(val instanceof Integer){
                onTimeInterval(tb, te, (int) val);
            }else{
                throw new UnsupportedOperationException("GOT "+val+" expect integer.");
            }
        }

        public abstract void onTimeInterval(TimePoint tb, TimePoint te, int val);
    }

    private long updateTimeIntervalAccessCnt(Entity entity, String prop, TimePoint t0, TimePoint t1, Stat stat) {
        long tt0 = System.nanoTime();
        entity.acquireTemporalExclusiveLock(prop, t0, t1);
        long waitTime = System.nanoTime() - tt0;
        stat.addT0(t0.getTime());
        stat.addT1(t1.getTime());
        int[] minMax = new int[]{Integer.MAX_VALUE, -1};
        entity.getTemporalProperty(prop, t0, t1, new TimeIntervalWithDefaultValue(t0, t1) {
            @Override
            public void onTimeInterval(TimePoint tb, TimePoint te, int val) {
                int timeIntervalOverlapCnt = val + 1;
                entity.setTemporalProperty(prop, tb, te, timeIntervalOverlapCnt);
                if( timeIntervalOverlapCnt < minMax[0] ) minMax[0] = timeIntervalOverlapCnt;
                if( timeIntervalOverlapCnt > minMax[1] ) minMax[1] = timeIntervalOverlapCnt;
            }
        });
        stat.entityTimeOverlapCnt.compute(entity.getId(), (k, old)-> {
            int v = old == null ? 0 : old;
            if(minMax[1] >0) {
                return v + 1;
            }else {
                return v;
            }
        });
        return waitTime;
    }

    protected AbstractTransaction.Result execute(ImportTemporalDataTx tx) {
        GraphDatabaseService db = dbms.database("neo4j");
        RequestProcessResult result = new RequestProcessResult();
        try(Transaction transaction = db.beginTx()) {
            PFieldList data = tx.getData();
            Set<String> props = data.keysWithout("u_sid", "t");
            int tSize = data.size();
            long waitTime = 0;
            for (int i=0; i<tSize; i++) {
                try {
                    String id = data.get("u_sid", i).s();
                    TimePoint time = Helper.time(data.get("t", i).i());
                    Entity entity = tx.isNode() ? transaction.findNode(LABEL, "u_sid", id) :
                            transaction.findRelationship(RELATIONSHIP_TYPE, "u_sid", id);
                    Stat stat = tx.isNode() ? nodeStat : relStat;
                    for (String prop : props) {
                        waitTime += updateTimeIntervalAccessCnt(entity, prop, time, TimePoint.NOW, stat);
                    }
                }
                catch (IllegalStateException e) {
                    if (!e.getMessage().contains("not found")) throw e;
                }
            }
            result.setLockWait(waitTime);
            transaction.commit();
        }
        return result;
    }

    protected AbstractTransaction.Result execute(UpdateTemporalDataTx tx) {
        GraphDatabaseService db = dbms.database("neo4j");
        RequestProcessResult result = new RequestProcessResult();
        try(Transaction transaction = db.beginTx()) {
            PFieldList data = tx.getData();
            Set<String> props = data.keysWithout("u_sid", "st", "et");
            int tSize = data.size();
            long waitTime = 0;
            for (int i=0; i<tSize; i++) {
                try {
                    String id = data.get("u_sid", i).s();
                    TimePoint start = Helper.time(data.get("st", i).i());
                    TimePoint end = Helper.time(data.get("et", i).i());
                    Entity entity = tx.isNode() ? transaction.findNode(LABEL, "u_sid", id) :
                            transaction.findRelationship(RELATIONSHIP_TYPE, "u_sid", id);
                    Stat stat = tx.isNode() ? nodeStat : relStat;
                    for (String prop : props) {
                        waitTime += updateTimeIntervalAccessCnt(entity, prop, start, end, stat);
                    }
                }
                catch (IllegalStateException e) {
                    if (!e.getMessage().contains("not found")) throw e;
                }
            }
            result.setLockWait(waitTime);
            transaction.commit();
        }
        return result;
    }

    protected AbstractTransaction.Result execute(SnapshotQueryTx tx) {
        GraphDatabaseService db = dbms.database("neo4j");
        RequestProcessResult result = new RequestProcessResult();
        Stat stat = tx.isNode() ? nodeStat : relStat;
        try(Transaction transaction = db.beginTx()) {
            final long[] waitTime = {0};
            all(tx.isNode(), transaction, entity -> {
                String id = (String) entity.getProperty("u_sid");
                String key = tx.getPropertyName();
                TimePoint timePoint = Helper.time(tx.getTimestamp());
                waitTime[0] += updateTimeIntervalAccessCnt(entity, key, timePoint, timePoint, stat);
            });
            result.setLockWait(waitTime[0]);
            return result;
        }
    }

    protected AbstractTransaction.Result execute(SnapshotAggrMaxTx tx) {
        GraphDatabaseService db = dbms.database("neo4j");
        RequestProcessResult result = new RequestProcessResult();
        Stat stat = tx.isNode() ? nodeStat : relStat;
        try(Transaction transaction = db.beginTx()){
            final long[] waitTime = {0};
            all(tx.isNode(), transaction, entity -> {
                String id = (String) entity.getProperty("u_sid");
                String key = tx.getP();
                TimePoint startTime = Helper.time(tx.getT0());
                TimePoint endTime = Helper.time(tx.getT1());
                waitTime[0] += updateTimeIntervalAccessCnt(entity, key, startTime, endTime, stat);
            });
            result.setLockWait(waitTime[0]);
            return result;
        }
    }

    @SuppressWarnings("unchecked")
    protected AbstractTransaction.Result execute(SnapshotAggrDurationTx tx) {
        GraphDatabaseService db = dbms.database("neo4j");
        RequestProcessResult result = new RequestProcessResult();
        Stat stat = tx.isNode() ? nodeStat : relStat;
        TimePoint begin = Helper.time(tx.getT0());
        TimePoint end = Helper.time(tx.getT1());
        String prop = tx.getP();
        try (Transaction transaction = db.beginTx()) {
            final long[] waitTime = {0};
            all(tx.isNode(), transaction, entity -> {
                String id = (String) entity.getProperty("u_sid");
                waitTime[0] += updateTimeIntervalAccessCnt(entity, prop, begin, end, stat);
            });
            result.setLockWait(waitTime[0]);
            return result;
        }
    }

    protected AbstractTransaction.Result execute(EntityHistoryTx tx) {
        GraphDatabaseService db = dbms.database("neo4j");
        TimePoint begin = Helper.time(tx.getBeginTime());
        TimePoint end = Helper.time(tx.getEndTime());
        Stat stat = tx.isNode() ? nodeStat : relStat;
        String prop = tx.getProp();
        RequestProcessResult result = new RequestProcessResult();
        try (Transaction transaction = db.beginTx()) {
            List<Triple<Integer, Integer, PVal>> answers = new ArrayList<>();
            Entity entity;
            if(tx.isNode()){
                entity = transaction.findNode(LABEL, "u_sid", tx.getEntity());
            }else{
                entity = transaction.findRelationship(RELATIONSHIP_TYPE, "u_sid", tx.getEntity());
            }
            long waitTime = updateTimeIntervalAccessCnt(entity, prop, begin, end, stat);
            result.setLockWait(waitTime);
            return result;
        }
    }

    protected AbstractTransaction.Result execute(EntityTemporalConditionTx tx) {
        GraphDatabaseService db = dbms.database("neo4j");
        RequestProcessResult result = new RequestProcessResult();
        Stat stat = tx.isNode() ? nodeStat : relStat;
        TimePoint startTime = Helper.time(tx.getT0());
        TimePoint endTime = Helper.time(tx.getT1());
        String key = tx.getP();
        try (Transaction transaction = db.beginTx()) {
            final long[] waitTime = {0};
            all(tx.isNode(), transaction, entity -> {
                String id = (String) entity.getProperty("u_sid");
                waitTime[0] += updateTimeIntervalAccessCnt(entity, key, startTime, endTime, stat);
            });
            result.setLockWait(waitTime[0]);
            return result;
        }
    }


}