package edu.buaa.server.system;

import com.alibaba.fastjson.JSON;
import com.google.common.base.Preconditions;
import edu.buaa.common.RuntimeEnv;
import edu.buaa.common.server.DBSocketServer;
import edu.buaa.common.transaction.*;
import edu.buaa.common.utils.PFieldList;
import edu.buaa.common.utils.PVal;
import edu.buaa.utils.Helper;
import edu.buaa.utils.Pair;
import edu.buaa.utils.Triple;
import edu.buaa.utils.TxStatisticSaver;
import org.act.temporalProperty.query.TimePointL;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.temporal.TemporalRangeQuery;
import org.neo4j.graphdb.temporal.TimeIntervalRangeQuery;
import org.neo4j.graphdb.temporal.TimePoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 基本照抄的TGraph2.3的代码，没有测试过正确性，但可以编译和运行
 * 经过多轮测试没发现问题
 */
public class TGraphKernelServer implements DBSocketServer.DBKernelProxy {
    public static final Label LABEL = Label.label("test");
    public static final RelationshipType RELATIONSHIP_TYPE = RelationshipType.withName("test");

    protected DatabaseManagementService dbms;
    private static TxStatisticSaver statisticSaver;

    protected static DBSocketServer.DBKernelProxy dbKernelProxy;
    protected static Logger log;
    protected static boolean bigLock, apReadLock, tpReadLock;

    public static DBSocketServer.DBKernelProxy getDbKernelProxy() {
        return dbKernelProxy;
    }

    public static TxStatisticSaver getStatisticSaver() {
        return statisticSaver;
    }

    private static void setDefaultArgs() {
        dbKernelProxy = new TGraphKernelServer();
        log = LoggerFactory.getLogger(TGraphKernelServer.class);
        // 默认
        bigLock = false;
        apReadLock = false;
        tpReadLock = false;
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
    }

    @Override
    public void shutdown() {
        dbms.shutdown();
    }

    @Override
    public AbstractTransaction.Result execute(String line) throws TransactionFailedException {
        throw new UnsupportedOperationException();
    }

    @Override
    public AbstractTransaction.Result execute(String line, AbstractTransaction.Metrics metrics) throws TransactionFailedException {
        try {
            AbstractTransaction tx = JSON.parseObject(line, AbstractTransaction.class);
            switch (tx.getTxType()) {
                case tx_import_static_data:
                    return execute((ImportStaticDataTx) tx, metrics);
                case tx_import_temporal_data:
                    return execute((ImportTemporalDataTx) tx, metrics);
                case tx_update_temporal_data:
                    return execute((UpdateTemporalDataTx) tx, metrics);
                case tx_query_snapshot:
                    return execute((SnapshotQueryTx) tx, metrics);
                case tx_query_snapshot_aggr_max:
                    return execute((SnapshotAggrMaxTx) tx, metrics);
                case tx_query_snapshot_aggr_duration:
                    return execute((SnapshotAggrDurationTx) tx, metrics);
                case tx_query_entity_history:
                    return execute((EntityHistoryTx) tx, metrics);
                case tx_query_road_by_temporal_condition:
                    return execute((EntityTemporalConditionTx) tx, metrics);
                case tx_query_reachable_area:
                    return execute((ReachableAreaQueryTx) tx, metrics);
                default:
                    throw new UnsupportedOperationException();
            }
        }catch (Exception e){
            if(e instanceof org.neo4j.kernel.DeadlockDetectedException) throw e;
            int end = Math.min(60, line.length());
            log.error("ERROR processing TX: "+line.substring(0, end), e);
            throw new TransactionFailedException(e);
        }
    }

    private static void startAcquireLock(AbstractTransaction.Metrics metrics) {
        if (metrics != null) metrics.startAcquireLock();
    }

    private static void finishAcquireLock(AbstractTransaction.Metrics metrics) {
        if (metrics != null) metrics.finishAcquireLock();
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

    protected AbstractTransaction.Result execute(ImportStaticDataTx tx, AbstractTransaction.Metrics metrics) {
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

    protected AbstractTransaction.Result execute(ImportTemporalDataTx tx, AbstractTransaction.Metrics metrics) {
        GraphDatabaseService db = dbms.database("neo4j");
        try(Transaction transaction = db.beginTx()) {
            PFieldList data = tx.getData();
            Set<String> props = data.keysWithout("u_sid", "t");
            int tSize = data.size();
            for (int i=0; i<tSize; i++) {
                try {
                    String id = data.get("u_sid", i).s();
                    TimePoint time = Helper.time(data.get("t", i).i());
                    Entity entity = tx.isNode() ? transaction.findNode(LABEL, "u_sid", id) :
                            transaction.findRelationship(RELATIONSHIP_TYPE, "u_sid", id);
                    for (String prop : props) {
                        startAcquireLock(metrics);
                        if (bigLock) {
                            transaction.acquireWriteLock(entity);
                        }
                        else {
                            entity.acquireTemporalExclusiveLock(prop, time, TimePoint.NOW);
                        }
                        finishAcquireLock(metrics);
                        entity.setTemporalProperty(prop, time, data.get(prop, i).getVal());
                    }
                }
                catch (IllegalStateException e) {
                    if (!e.getMessage().contains("not found")) throw e;
                }
            }
            transaction.commit();
        }
        return new AbstractTransaction.Result();
    }

    protected AbstractTransaction.Result execute(UpdateTemporalDataTx tx, AbstractTransaction.Metrics metrics) {
        GraphDatabaseService db = dbms.database("neo4j");
        try(Transaction transaction = db.beginTx()) {
            PFieldList data = tx.getData();
            Set<String> props = data.keysWithout("u_sid", "st", "et");
            int tSize = data.size();
            for (int i=0; i<tSize; i++) {
                try {
                    String id = data.get("u_sid", i).s();
                    TimePoint start = Helper.time(data.get("st", i).i());
                    TimePoint end = Helper.time(data.get("et", i).i());
                    Entity entity = tx.isNode() ? transaction.findNode(LABEL, "u_sid", id) :
                            transaction.findRelationship(RELATIONSHIP_TYPE, "u_sid", id);
                    for (String prop : props) {
                        startAcquireLock(metrics);
                        if (bigLock) {
                            transaction.acquireWriteLock(entity);
                        }
                        else {
                            entity.acquireTemporalExclusiveLock(prop, start, end);
                        }
                        finishAcquireLock(metrics);
                        entity.setTemporalProperty(prop, start, end, data.get(prop, i).getVal());
                    }
                }
                catch (IllegalStateException e) {
                    if (!e.getMessage().contains("not found")) throw e;
                }
            }
            transaction.commit();
        }
        return new AbstractTransaction.Result();
    }

    protected AbstractTransaction.Result execute(SnapshotQueryTx tx, AbstractTransaction.Metrics metrics) {
        GraphDatabaseService db = dbms.database("neo4j");
        try(Transaction transaction = db.beginTx()) {
            List<Pair<String, PVal>> answers = new ArrayList<>();
            all(tx.isNode(), transaction, entity -> {
                String id = (String) entity.getProperty("u_sid");
                String key = tx.getPropertyName();
                TimePoint timePoint = Helper.time(tx.getTimestamp());
                if (apReadLock) {
                    startAcquireLock(metrics);
                    if (bigLock) {
                        transaction.acquireReadLock(entity);
                    }
                    else {
                        entity.acquireTemporalSharedLock(key, timePoint, timePoint);
                    }
                    finishAcquireLock(metrics);
                }
                Object v = entity.getTemporalProperty(key, timePoint);
                if (v!=null) {
                    answers.add(Pair.of(id, PVal.v(v)));
                }
            });
            SnapshotQueryTx.Result result = new SnapshotQueryTx.Result();
            result.answer(answers);
            return result;
        }
    }

    protected AbstractTransaction.Result execute(SnapshotAggrMaxTx tx, AbstractTransaction.Metrics metrics) {
        GraphDatabaseService db = dbms.database("neo4j");
        try(Transaction transaction = db.beginTx()){
            List<Pair<String, PVal>> answers = new ArrayList<>();
            all(tx.isNode(), transaction, entity -> {
                String id = (String) entity.getProperty("u_sid");
                String key = tx.getP();
                TimePoint startTime = Helper.time(tx.getT0());
                TimePoint endTime = Helper.time(tx.getT1());
                if (apReadLock) {
                    startAcquireLock(metrics);
                    if (bigLock) {
                        transaction.acquireReadLock(entity);
                    }
                    else {
                        entity.acquireTemporalSharedLock(key, startTime, endTime);
                    }
                    finishAcquireLock(metrics);
                }
                Object v = entity.getTemporalProperty(key, startTime, endTime, new TemporalRangeQuery.MaxValue());
                if(v!=null){
                    answers.add(Pair.of(id, PVal.v(v))); //放入answers中
                }
            });
            SnapshotAggrMaxTx.Result result = new SnapshotAggrMaxTx.Result();
            result.setPropMaxValue(answers);
            return result;
        }
    }

    @SuppressWarnings("unchecked")
    protected AbstractTransaction.Result execute(SnapshotAggrDurationTx tx, AbstractTransaction.Metrics metrics) {
        GraphDatabaseService db = dbms.database("neo4j");
        TimePoint begin = Helper.time(tx.getT0()), end = Helper.time(tx.getT1());
        try (Transaction transaction = db.beginTx()) {
            List<Triple<String, PVal, Integer>> answers = new ArrayList<>();
            all(tx.isNode(), transaction, entity -> {
                String id = (String) entity.getProperty("u_sid");
                if (apReadLock) {
                    startAcquireLock(metrics);
                    if (bigLock) {
                        transaction.acquireReadLock(entity);
                    }
                    else {
                        entity.acquireTemporalSharedLock(tx.getP(), begin, end);
                    }
                    finishAcquireLock(metrics);
                }
                Object v = entity.getTemporalProperty(tx.getP(), begin, end,
                        new TimeIntervalRangeQuery.Duration<>(begin, end, tx.getIntStartTreeSet(), PVal::v));
                if(v instanceof HashMap){
                    HashMap<PVal, Integer> m = (HashMap<PVal, Integer>) v;
                    m.forEach((k, val) -> answers.add(Triple.of(id, k, val)));
                }
            });
            SnapshotAggrDurationTx.Result result = new SnapshotAggrDurationTx.Result();
            result.setStatusDuration(answers);
            return result;
        }
    }

    protected AbstractTransaction.Result execute(EntityHistoryTx tx, AbstractTransaction.Metrics metrics) {
        GraphDatabaseService db = dbms.database("neo4j");
        TimePoint begin = Helper.time(tx.getBeginTime()), end = Helper.time(tx.getEndTime());
        try (Transaction transaction = db.beginTx()) {
            List<Triple<Integer, Integer, PVal>> answers = new ArrayList<>();
            Entity entity;
            if(tx.isNode()){
                entity = transaction.findNode(LABEL, "u_sid", tx.getEntity());
            }else{
                entity = transaction.findRelationship(RELATIONSHIP_TYPE, "u_sid", tx.getEntity());
            }
            if (tpReadLock) {
                startAcquireLock(metrics);
                if (bigLock) {
                    transaction.acquireReadLock(entity);
                }
                else {
                    entity.acquireTemporalSharedLock(tx.getProp(), begin, end);
                }
                finishAcquireLock(metrics);
            }
            entity.getTemporalProperty(tx.getProp(), begin, end, new TimeIntervalRangeQuery(begin, end){
                @Override
                public void onEntry(TimePointL beginTime, TimePointL endTime, Object val) {
                    if(val!=null) {
                        answers.add(Triple.of(beginTime.valInt(), endTime.valInt(), PVal.v(val)));
                    }
                }
            });
            EntityHistoryTx.Result result = new EntityHistoryTx.Result();
            result.setHistory(answers);
            return result;
        }
    }

    protected AbstractTransaction.Result execute(EntityTemporalConditionTx tx, AbstractTransaction.Metrics metrics) {
        GraphDatabaseService db = dbms.database("neo4j");
        try (Transaction transaction = db.beginTx()) {
            List<String> res = new ArrayList<>();
            all(tx.isNode(), transaction, entity -> {
                String id = (String) entity.getProperty("u_sid");
                String key = tx.getP();
                TimePoint startTime = Helper.time(tx.getT0());
                TimePoint endTime = Helper.time(tx.getT1());
                if (apReadLock) {
                    startAcquireLock(metrics);
                    if (bigLock) {
                        transaction.acquireReadLock(entity);
                    }
                    else {
                        entity.acquireTemporalSharedLock(key, startTime, endTime);
                    }
                    finishAcquireLock(metrics);
                }
                entity.getTemporalProperty(key, startTime, endTime, new TemporalRangeQuery() {
                    @Override
                    public boolean onNewEntry(long entityId, int propertyId, TimePointL time, Object val) {
                        if (val instanceof Comparable && PVal.within(tx.getVMin(), true, PVal.v(val), tx.getVMax(), true)) {
                            res.add(id);
                            return false;
                        }
                        return true;
                    }
                });
            });
            EntityTemporalConditionTx.Result result = new EntityTemporalConditionTx.Result();
            result.setEntities(res);
            return result;
        }
    }

    protected AbstractTransaction.Result execute(ReachableAreaQueryTx tx, AbstractTransaction.Metrics metrics) {
        GraphDatabaseService db = dbms.database("neo4j");
        try(Transaction transaction = db.beginTx()) {
            ReachableAreaTGraphKernel algo = new ReachableAreaTGraphKernel(transaction, "travel_time",
                    transaction.findNode(LABEL, "u_sid", tx.getStartNode()).getId(),
                    tx.getDepartureTime(), tx.getTravelTime(), bigLock, apReadLock, metrics);
            List<Pair<Integer, String>> answers = new ArrayList<>();
            for(ReachableAreaQueryTx.TemporalDijkstraAlgo.NodeCross nodeCross : algo.run()){
                String u_sid = (String) transaction.getNodeById(nodeCross.getId()).getProperty("u_sid");
                answers.add(Pair.of(nodeCross.getArriveTime(), u_sid));
            }
            ReachableAreaQueryTx.Result result = new ReachableAreaQueryTx.Result();
//            result.setNodeArriveTime(answers);
//            result.setInnerResults(algo.getInnerResults());
            result.setStatResult(algo.statResult);
            transaction.commit();
            return result;
        }
    }

    public static class ReachableAreaTGraphKernel extends ReachableAreaQueryTx.TemporalDijkstraAlgo {
        private final String travelTimePropertyKey;
        private final Transaction transaction;

        private final boolean bigLock;
        private final boolean readLock;
        private final AbstractTransaction.Metrics metrics;

        public ReachableAreaTGraphKernel(Transaction transaction, String travelTimePropertyKey, long startId, int startTime, int travelTime, boolean bigLock, boolean readLock, AbstractTransaction.Metrics metrics) {
            super(startId, startTime, travelTime, true);
            this.transaction = transaction;
            this.travelTimePropertyKey = travelTimePropertyKey;
            this.bigLock = bigLock;
            this.readLock = readLock;
            this.metrics = metrics;
        }

        protected int getEarliestArriveTime(Long roadId, int departureTime) throws UnsupportedOperationException {
            Relationship r = transaction.getRelationshipById(roadId);
            if( !r.hasProperty( travelTimePropertyKey )) throw new UnsupportedOperationException();
            if (readLock) {
                startAcquireLock(metrics);
                if (bigLock) {
                    transaction.acquireReadLock(r);
                }
                else {
                    r.acquireTemporalSharedLock(travelTimePropertyKey, Helper.time(departureTime), Helper.time(this.endTime));
                }
                finishAcquireLock(metrics);
            }
            Object tObj = r.getTemporalProperty(travelTimePropertyKey, Helper.time(departureTime), Helper.time(this.endTime), new TemporalRangeQuery() {
                private int minArriveT = Integer.MAX_VALUE;
                private boolean firstEntry = true;
                @Override
                public boolean onNewEntry(long entityId, int propertyId, TimePointL time, Object val) {
                    if (! (val instanceof Integer)) throw new UnsupportedOperationException();
                    int curT = time.valInt();
                    if(firstEntry && curT>departureTime){
                        throw new UnsupportedOperationException();
                    }
                    firstEntry=false;
                    int travelT = (int) val;
                    if(curT<departureTime) curT = departureTime;
                    if(curT +travelT<minArriveT) minArriveT = curT +travelT;
                    return curT<minArriveT;
                }
                @Override
                public Object onReturn() {
                    if(minArriveT<Integer.MAX_VALUE){
                        return minArriveT;
                    }else{
                        return null;
                    }
                }
            });
            if (tObj == null) {
                throw new UnsupportedOperationException();
            }else{
                return (Integer) tObj;
            }
        }

        @Override
        protected Iterable<Long> getAllOutRoads(long nodeId) {
            Node node = transaction.getNodeById(nodeId);
            List<Long> result = new ArrayList<>();
            for(Relationship r : node.getRelationships(Direction.OUTGOING)){
                result.add(r.getId());
            }
            return result;
        }

        @Override
        protected long getEndNodeId(long roadId) {
            return transaction.getRelationshipById(roadId).getEndNode().getId();
        }

        @Override
        protected String nodeId2Str(long nodeId) {
            Node n = transaction.getNodeById(nodeId);
            return (String) n.getProperty("u_sid");
        }

        @Override
        protected String relId2Str(long relId) {
            Relationship r = transaction.getRelationshipById(relId);
            return (String) r.getProperty("u_sid");
        }
    }
}