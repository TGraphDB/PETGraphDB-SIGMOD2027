package edu.buaa.server.system;

import com.alibaba.fastjson.JSON;
import edu.buaa.common.RuntimeEnv;
import edu.buaa.common.server.DBSocketServer;
import edu.buaa.common.transaction.*;
import edu.buaa.common.utils.PFieldList;
import edu.buaa.common.utils.PVal;
import edu.buaa.utils.Helper;
import edu.buaa.utils.Pair;
import edu.buaa.utils.Triple;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.schema.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class TGQLServer implements DBSocketServer.DBKernelProxy {
    private static final Logger log = LoggerFactory.getLogger(TGQLServer.class);

    private static final Label OBJECT_LABEL = Label.label("Object");
    private static final Label ATTRIBUTE_LABEL = Label.label("Attribute");
    private static final Label VALUE_LABEL = Label.label("Value");
    private static final RelationshipType RELATIONSHIP_TYPE_STATIC = RelationshipType.withName("static");
    private static final RelationshipType RELATIONSHIP_TYPE_TEMPORAL = RelationshipType.withName("temporal");
    private static final RelationshipType HAS_ATTRIBUTE = RelationshipType.withName("HAS_ATTRIBUTE");
    private static final RelationshipType HAS_VALUE = RelationshipType.withName("HAS_VALUE");

    private static final int DEFAULT_END_TIME = Integer.MAX_VALUE;

    private DatabaseManagementService dbms;

    /**
     * 输入：命令行参数（未使用）。
     * 返回：无。
     * 作用：启动 TGQL 服务器并监听端口。
     */
    public static void main(String[] args) {
        DBSocketServer server = new DBSocketServer(dbDir(), new TGQLServer(), Integer.parseInt(Helper.mustEnv("DB_PORT")), true);
        RuntimeEnv env = RuntimeEnv.getCurrentEnv();
        String serverCodeVersion = env.name() + "." + Helper.codeGitVersion();
        System.out.println("server code version:" + serverCodeVersion);
        try {
            server.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 输入：无（从环境变量 DB_PATH 读取路径）。
     * 返回：数据库目录 File。
     * 作用：校验并创建数据库目录。
     */
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
    /**
     * 输入：数据库路径。
     * 返回：无。
     * 作用：启动并初始化数据库服务与索引。
     */
    public void start(File path) {
        dbms = new DatabaseManagementServiceBuilder(path.toPath()).build();
        GraphDatabaseService db = dbms.database("neo4j");
        try (Transaction tx = db.beginTx()) {
            awaitIndexes(tx.schema());
            tx.commit();
        }
    }

    @Override
    /**
     * 输入：无。
     * 返回：无。
     * 作用：关闭数据库服务。
     */
    public void shutdown() {
        if (dbms != null) {
            dbms.shutdown();
        }
    }

    /**
     * 输入：Schema 对象。
     * 返回：无。
     * 作用：等待所有索引在线。
     */
    private void awaitIndexes(Schema schema) {
        boolean shouldWait = true;
        int cnt = 0;
        while (shouldWait) {
            try {
                schema.awaitIndexesOnline(10, TimeUnit.SECONDS);
                shouldWait = false;
            } catch (IllegalArgumentException | IllegalStateException e) {
                cnt++;
                System.out.println("indexes not ready after " + cnt * 10 + " seconds.");
            }
        }
        System.out.println("indexes all online.");
    }

    @Override
    /**
     * 输入：序列化事务字符串。
     * 返回：事务执行结果。
     * 作用：默认不带 metrics 的执行入口。
     */
    public AbstractTransaction.Result execute(String line) throws TransactionFailedException {
        return execute(line, null);
    }

    @Override
    /**
     * 输入：序列化事务字符串、可选 metrics。
     * 返回：事务执行结果。
     * 作用：解析事务并分发到具体执行逻辑。
     */
    public AbstractTransaction.Result execute(String line, AbstractTransaction.Metrics metrics) throws TransactionFailedException {
        try {
            AbstractTransaction tx = JSON.parseObject(line, AbstractTransaction.class);
            switch (tx.getTxType()) {
                case tx_import_static_data:
                    return execute((ImportStaticDataTx) tx); // 基本上不用
                case tx_import_temporal_data:
                    return execute((ImportTemporalDataTx) tx); // 常用3 全都有
                case tx_update_temporal_data:
                    return execute((UpdateTemporalDataTx) tx); // 常用3 可能只有一个属性
                case tx_query_snapshot:
                    return execute((SnapshotQueryTx) tx); // 常用1
                case tx_query_snapshot_aggr_max:
                    return execute((SnapshotAggrMaxTx) tx); // 常用2
                case tx_query_snapshot_aggr_duration:
                    return execute((SnapshotAggrDurationTx) tx); // 不用
                case tx_query_entity_history:
                    return execute((EntityHistoryTx) tx); // 常用1
                case tx_query_road_by_temporal_condition:
                    return execute((EntityTemporalConditionTx) tx); // 常用2
                case tx_query_reachable_area:
                    return execute((ReachableAreaQueryTx) tx);  // 常用2
                default:
                    throw new UnsupportedOperationException();
            }
        } catch (Exception e) {
            if (e instanceof org.neo4j.kernel.DeadlockDetectedException) throw e;
            int end = Math.min(80, line.length());
            log.error("ERROR processing TX: " + line.substring(0, end), e);
            throw new TransactionFailedException(e);
        }
    }

    /**
     * 输入：静态数据导入事务。
     * 返回：空结果。
     * 作用：导入静态节点与静态关系。
     */
    protected AbstractTransaction.Result execute(ImportStaticDataTx tx) {
        GraphDatabaseService db = dbms.database("neo4j");
        try (Transaction t = db.beginTx()) {
            PFieldList nodesData = tx.getNodes();
            int nSize = nodesData.size();
            for (int i = 0; i < nSize; i++) {
                Node n = t.createNode(OBJECT_LABEL);
                for (String key : nodesData.keys()) {
                    n.setProperty(key, nodesData.get(key, i).getVal());
                }
            }
            PFieldList relData = tx.getRels();
            int rSize = relData.size();
            for (int i = 0; i < rSize; i++) {
                String fromId = relData.get("r_from", i).s();
                String toId = relData.get("r_to", i).s();
                Node s = findObjectBySid(t, fromId);
                Node e = findObjectBySid(t, toId);
                if (s == null || e == null) {
                    continue;
                }
                Relationship rel = s.createRelationshipTo(e, RELATIONSHIP_TYPE_STATIC);
                for (String key : relData.keysWithout("r_from", "r_to")) {
                    rel.setProperty(key, relData.get(key, i).getVal());
                }
            }
            t.commit();
        }
        return new AbstractTransaction.Result();
    }

    /**
     * 输入：时态导入事务（节点或关系，默认开放区间终点为 MAX）。
     * 返回：空结果。
     * 作用：写入时态属性或时态关系。
     */
    protected AbstractTransaction.Result execute(ImportTemporalDataTx tx) {
        GraphDatabaseService db = dbms.database("neo4j");
        try (Transaction t = db.beginTx()) {
            PFieldList data = tx.getData();
            int size = data.size();
            Set<String> props = data.keysWithout("u_sid", "t");
            for (int i = 0; i < size; i++) {
                String sid = data.get("u_sid", i).s();
                int st = data.get("t", i).i();
                int et = DEFAULT_END_TIME;
                if (tx.isNode()) {
                    Node obj = findObjectBySid(t, sid);
                    if (obj == null) {
                        continue;
                    }
                    for (String prop : props) {
                        Node attr = findOrCreateAttribute(t, obj, prop);
                        Object newValue = data.get(prop, i).getVal();
                        upsertTemporalValue(t, attr, newValue, st, et);
                    }
                } else {
                    Relationship staticRel = findStaticRelationshipBySid(t, sid);
                    if (staticRel == null) {
                        continue;
                    }
                    Map<String, Object> newProps = new HashMap<>();
                    for (String prop : props) {
                        newProps.put(prop, data.get(prop, i).getVal());
                    }
                    upsertTemporalRelationship(t, staticRel, sid, newProps, st, et);
                }
            }
            t.commit();
        }
        return new AbstractTransaction.Result();
    }

    /**
     * 输入：时态更新事务（带区间）。
     * 返回：空结果。
     * 作用：在指定区间内更新时态值或时态关系。
     */
    protected AbstractTransaction.Result execute(UpdateTemporalDataTx tx) {
        GraphDatabaseService db = dbms.database("neo4j");
        try (Transaction t = db.beginTx()) {
            PFieldList data = tx.getData();
            int size = data.size();
            Set<String> props = data.keysWithout("u_sid", "st", "et");
            for (int i = 0; i < size; i++) {
                String sid = data.get("u_sid", i).s();
                int st = data.get("st", i).i();
                int et = data.get("et", i).i();
                if (tx.isNode()) {
                    Node obj = findObjectBySid(t, sid);
                    if (obj == null) {
                        continue;
                    }
                    for (String prop : props) {
                        Node attr = findOrCreateAttribute(t, obj, prop);
                        Object newValue = data.get(prop, i).getVal();
                        upsertTemporalValue(t, attr, newValue, st, et);
                    }
                } else {
                    Relationship staticRel = findStaticRelationshipBySid(t, sid);
                    if (staticRel == null) {
                        continue;
                    }
                    Map<String, Object> newProps = new HashMap<>();
                    for (String prop : props) {
                        newProps.put(prop, data.get(prop, i).getVal());
                    }
                    upsertTemporalRelationship(t, staticRel, sid, newProps, st, et);
                }
            }
            t.commit();
        }
        return new AbstractTransaction.Result();
    }

    /**
     * 输入：快照查询事务（时间点 + 属性名）。
     * 返回：包含匹配结果的快照查询结果。
     * 作用：在给定时间点查询节点/关系的属性值。
     */
    protected AbstractTransaction.Result execute(SnapshotQueryTx tx) {
        SnapshotQueryTx.Result result = new SnapshotQueryTx.Result();
        List<Pair<String, PVal>> status = new ArrayList<>();
        GraphDatabaseService db = dbms.database("neo4j");
        try (Transaction t = db.beginTx()) {
            if (tx.isNode()) {
                try (ResourceIterator<Node> nodes = t.findNodes(OBJECT_LABEL)) {
                    while (nodes.hasNext()) {
                        Node obj = nodes.next(); // 返回从 t.findNodes(OBJECT_LABEL) 查询结果里取出的下一个节点
                        Node attr = findAttribute(obj, tx.getPropertyName());
                        if (attr == null) {
                            continue;
                        }
                        PVal val = valueAtTime(attr, tx.getTimestamp());
                        if (val != null) {
                            status.add(Pair.of(obj.getProperty("u_sid").toString(), val));
                        }
                    }
                }
            } else {
                try (ResourceIterator<Relationship> rels = t.findRelationships(RELATIONSHIP_TYPE_TEMPORAL)) {
                    while (rels.hasNext()) {
                        Relationship rel = rels.next();
                        if (!rel.hasProperty("start_time") || !rel.hasProperty("end_time")) {
                            continue;
                        }
                        Interval itv = parseInterval(rel.getProperty("start_time"), rel.getProperty("end_time"));
                        if (itv == null || !itv.contains(tx.getTimestamp())) {
                            continue;
                        }
                        if (!rel.hasProperty(tx.getPropertyName())) {
                            continue;
                        }
                        PVal v = toPVal(rel.getProperty(tx.getPropertyName()));
                        String id = rel.getProperty("u_sid").toString();
                        status.add(Pair.of(id, v));
                    }
                }
            }
            t.commit();
        }
        result.answer(status);
        return result;
    }

    /**
     * 输入：区间最大值查询事务。
     * 返回：最大值聚合结果。
     * 作用：在时间区间内获取属性最大值（按实体）。
     */
    protected AbstractTransaction.Result execute(SnapshotAggrMaxTx tx) {
        SnapshotAggrMaxTx.Result result = new SnapshotAggrMaxTx.Result();
        List<Pair<String, PVal>> rows = new ArrayList<>();
        GraphDatabaseService db = dbms.database("neo4j");
        try (Transaction t = db.beginTx()) {
            if (tx.isNode()) {
                try (ResourceIterator<Node> nodes = t.findNodes(OBJECT_LABEL)) {
                    while (nodes.hasNext()) {
                        Node obj = nodes.next();
                        Node attr = findAttribute(obj, tx.getP());
                        if (attr == null) {
                            continue;
                        }
                        PVal maxVal = maxValueInRange(attr, tx.getT0(), tx.getT1());
                        if (maxVal != null) {
                            rows.add(Pair.of((String) obj.getProperty("u_sid"), maxVal));
                        }
                    }
                }
            } else {
                Map<String, PVal> maxByRel = new HashMap<>();
                try (ResourceIterator<Relationship> rels = t.findRelationships(RELATIONSHIP_TYPE_TEMPORAL)) {
                    while (rels.hasNext()) {
                        Relationship rel = rels.next();
                        Interval itv = parseInterval(rel.getProperty("start_time"), rel.getProperty("end_time"));
                        if (itv == null || !itv.overlaps(tx.getT0(), tx.getT1())) {
                            continue;
                        }
                        if (!rel.hasProperty(tx.getP())) {
                            continue;
                        }
                        String id = (String) rel.getProperty("u_sid");
                        PVal v = toPVal(rel.getProperty(tx.getP()));
                        if (v == null) {
                            continue;
                        }
                        maxByRel.merge(id, v, (cur, next) -> cur.compareTo(next) >= 0 ? cur : next);
                    }
                }
                for (Map.Entry<String, PVal> entry : maxByRel.entrySet()) {
                    rows.add(Pair.of(entry.getKey(), entry.getValue()));
                }
            }
            t.commit();
        }
        result.setPropMaxValue(rows);
        return result;
    }

    /**
     * 输入：区间时长聚合事务。
     * 返回：属性取值在区间内的持续时间结果。
     * 作用：统计属性值在区间内持续的时长（按实体）。
     */
    protected AbstractTransaction.Result execute(SnapshotAggrDurationTx tx) {
        SnapshotAggrDurationTx.Result result = new SnapshotAggrDurationTx.Result();
        List<Triple<String, PVal, Integer>> rows = new ArrayList<>();
        GraphDatabaseService db = dbms.database("neo4j");
        try (Transaction t = db.beginTx()) {
            if (tx.isNode()) {
                try (ResourceIterator<Node> nodes = t.findNodes(OBJECT_LABEL)) {
                    while (nodes.hasNext()) {
                        Node obj = nodes.next();
                        Node attr = findAttribute(obj, tx.getP());
                        if (attr == null) {
                            continue;
                        }
                        List<Triple<String, PVal, Integer>> durations = durationsInRange(attr, (String) obj.getProperty("u_sid", ""), tx.getT0(), tx.getT1());
                        rows.addAll(durations);
                    }
                }
            } else {
                try (ResourceIterator<Relationship> rels = t.findRelationships(RELATIONSHIP_TYPE_TEMPORAL)) {
                    while (rels.hasNext()) {
                        Relationship rel = rels.next();
                        Interval itv = parseInterval(rel.getProperty("start_time", null), rel.getProperty("end_time", null));
                        if (itv == null || !itv.overlaps(tx.getT0(), tx.getT1())) {
                            continue;
                        }
                        if (!rel.hasProperty(tx.getP())) {
                            continue;
                        }
                        int duration = itv.overlapLength(tx.getT0(), tx.getT1());
                        if (duration > 0) {
                            rows.add(Triple.of((String) rel.getProperty("u_sid", ""), toPVal(rel.getProperty(tx.getP())), duration));
                        }
                    }
                }
            }
            t.commit();
        }
        result.setStatusDuration(rows);
        return result;
    }

    /**
     * 输入：实体历史查询事务。
     * 返回：实体历史结果列表（时间区间 + 值）。
     * 作用：查询实体在时间范围内的属性变化历史。
     */
    protected AbstractTransaction.Result execute(EntityHistoryTx tx) {
        EntityHistoryTx.Result result = new EntityHistoryTx.Result();
        List<Triple<Integer, Integer, PVal>> rows = new ArrayList<>();
        GraphDatabaseService db = dbms.database("neo4j");
        try (Transaction t = db.beginTx()) {
            if (tx.isNode()) {
                Node obj = findObjectBySid(t, tx.getEntity());
                if (obj != null) {
                    Node attr = findAttribute(obj, tx.getProp());
                    if (attr != null) {
                        for (Relationship r : attr.getRelationships(Direction.OUTGOING, HAS_VALUE)) {
                            Node val = r.getEndNode();
                            Interval itv = parseInterval(val.getProperty("start_time"), val.getProperty("end_time"));
                            if (itv == null || !itv.overlaps(tx.getBeginTime(), tx.getEndTime())) {
                                continue;
                            }
                            int st = Math.max(itv.start, tx.getBeginTime());
                            int et = Math.min(itv.end, tx.getEndTime());
                            rows.add(Triple.of(st, et, toPVal(val.getProperty("value"))));
                        }
                    }
                }
            } else {
                try (ResourceIterator<Relationship> rels = t.findRelationships(RELATIONSHIP_TYPE_TEMPORAL, "u_sid", tx.getEntity())) {
                    while (rels.hasNext()) {
                        Relationship rel = rels.next();
                        Interval itv = parseInterval(rel.getProperty("start_time"), rel.getProperty("end_time"));
                        if (itv == null || !itv.overlaps(tx.getBeginTime(), tx.getEndTime())) {
                            continue;
                        }
                        if (!rel.hasProperty(tx.getProp())) {
                            continue;
                        }
                        int st = Math.max(itv.start, tx.getBeginTime());
                        int et = Math.min(itv.end, tx.getEndTime());
                        rows.add(Triple.of(st, et, toPVal(rel.getProperty(tx.getProp()))));
                    }
                }
            }
            t.commit();
        }
        result.setHistory(rows);
        return result;
    }

    /**
     * 输入：时态条件查询事务（属性值区间 + 时间区间）。
     * 返回：满足条件的实体列表。
     * 作用：筛选在时间区间内满足属性值条件的实体。
     */
    protected AbstractTransaction.Result execute(EntityTemporalConditionTx tx) {
        EntityTemporalConditionTx.Result result = new EntityTemporalConditionTx.Result();
        List<String> entities = new ArrayList<>();
        GraphDatabaseService db = dbms.database("neo4j");
        try (Transaction t = db.beginTx()) {
            if (tx.isNode()) {
                try (ResourceIterator<Node> nodes = t.findNodes(OBJECT_LABEL)) {
                    while (nodes.hasNext()) {
                        Node obj = nodes.next();
                        Node attr = findAttribute(obj, tx.getP());
                        if (attr == null) {
                            continue;
                        }
                        if (existsValueWithin(attr, tx.getT0(), tx.getT1(), tx.getVMin(), tx.getVMax())) {
                            entities.add((String) obj.getProperty("u_sid"));
                        }
                    }
                }
            } else {
                try (ResourceIterator<Relationship> rels = t.findRelationships(RELATIONSHIP_TYPE_TEMPORAL)) {
                    while (rels.hasNext()) {
                        Relationship rel = rels.next();
                        Interval itv = parseInterval(rel.getProperty("start_time"), rel.getProperty("end_time"));
                        if (itv == null || !itv.overlaps(tx.getT0(), tx.getT1())) {
                            continue;
                        }
                        if (!rel.hasProperty(tx.getP())) {
                            continue;
                        }
                        PVal v = toPVal(rel.getProperty(tx.getP()));
                        if (within(tx.getVMin(), v, tx.getVMax())) {
                            entities.add((String) rel.getProperty("u_sid"));
                        }
                    }
                }
            }
            t.commit();
        }
        result.setEntities(entities);
        return result;
    }

    /**
     * 输入：可达区域查询事务。
     * 返回：可达区域结果。
     * 作用：基于时态最短路计算在给定时间窗口内的可达节点及其到达时间。
     */
    protected AbstractTransaction.Result execute(ReachableAreaQueryTx tx) {
        GraphDatabaseService db = dbms.database("neo4j");
        try (Transaction t = db.beginTx()) {
            Node start = findObjectBySid(t, tx.getStartNode());
            ReachableAreaTGQL algo = new ReachableAreaTGQL(t, tx.getProp(), start.getId(), tx.getDepartureTime(), tx.getTravelTime());
            List<Pair<Integer, String>> answers = new ArrayList<>();
            for (ReachableAreaQueryTx.TemporalDijkstraAlgo.NodeCross nodeCross : algo.run()) {
                String u_sid = (String) t.getNodeById(nodeCross.getId()).getProperty("u_sid");
                answers.add(Pair.of(nodeCross.getArriveTime(), u_sid));
            }
            ReachableAreaQueryTx.Result result = new ReachableAreaQueryTx.Result();
            // result.setNodeArriveTime(answers);
            // result.setInnerResults(algo.getInnerResults());
            result.setStatResult(algo.statResult);
            t.commit();
            return result;
        }
    }

    private class ReachableAreaTGQL extends ReachableAreaQueryTx.TemporalDijkstraAlgo {
        private final Transaction transaction;
        private final String travelTimePropertyKey;

        private ReachableAreaTGQL(Transaction transaction, String travelTimePropertyKey, long startId, int startTime, int travelTime) {
            super(startId, startTime, travelTime, true);
            this.transaction = transaction;
            this.travelTimePropertyKey = travelTimePropertyKey;
        }

        @Override
        protected int getEarliestArriveTime(Long roadId, int departureTime) throws UnsupportedOperationException {
            Relationship staticRel = transaction.getRelationshipById(roadId);
            String relSid = (String) staticRel.getProperty("u_sid");
            int earliest = Integer.MAX_VALUE;
            try (ResourceIterator<Relationship> rels = transaction.findRelationships(RELATIONSHIP_TYPE_TEMPORAL, "u_sid", relSid)) {
                while (rels.hasNext()) {
                    Relationship rel = rels.next();
                    Interval itv = parseInterval(rel.getProperty("start_time"), rel.getProperty("end_time"));
                    // 如果区间为空，或者出发时间晚于区间结束，或者区间早于查询截止时间，则跳过
                    if (itv == null || itv.end < departureTime || itv.start > endTime) {
                        continue;
                    }
                    // 读取该时间段的通行耗时 (travelT)
                    Integer travelT = toInt(rel.getProperty(travelTimePropertyKey));
                    // 确定实际出发时间（取 departureTime 和 区间开始时间 的最大值）
                    // 意味着如果你到早了，可能得等到区间开始才能走
                    int depart = Math.max(departureTime, itv.start);
                    if (depart > itv.end) {
                        continue;
                    }
                    // 计算到达时间，并更新最小值
                    int arrT = depart + travelT;
                    if (arrT < earliest) {
                        earliest = arrT;
                    }
                }
            }
            if (earliest < Integer.MAX_VALUE) {
                return earliest;
            }
            throw new UnsupportedOperationException();
        }

        @Override
        protected Iterable<Long> getAllOutRoads(long nodeId) {
            Node node = transaction.getNodeById(nodeId);
            List<Long> result = new ArrayList<>();
            for (Relationship r : node.getRelationships(Direction.OUTGOING, RELATIONSHIP_TYPE_STATIC)) {
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

    /**
     * 输入：事务、实体 u_sid。
     * 返回：匹配的对象节点。
     * 作用：按 u_sid 查找 Object 节点。
     */
    private Node findObjectBySid(Transaction tx, String sid) {
        try (ResourceIterator<Node> it = tx.findNodes(OBJECT_LABEL, "u_sid", sid)) {
            if (!it.hasNext()) {
                throw new IllegalStateException("Object not found for u_sid: " + sid);
            }
            return it.next();
        }
    }

    /**
     * 输入：事务、关系 u_sid。
     * 返回：匹配的静态关系或 null。
     * 作用：按 u_sid 查找静态关系。
     */
    private Relationship findStaticRelationshipBySid(Transaction tx, String sid) {
        try (ResourceIterator<Relationship> it = tx.findRelationships(RELATIONSHIP_TYPE_STATIC, "u_sid", sid)) {
            return it.hasNext() ? it.next() : null;
        }
    }

    /**
     * 输入：事务、对象节点、属性名。
     * 返回：属性节点（不存在则创建）。
     * 作用：获取或创建属性节点，并与对象建立关系。
     */
    private Node findOrCreateAttribute(Transaction tx, Node obj, String prop) {
        Node attr = findAttribute(obj, prop);
        if (attr != null) {
            return attr;
        }
        Node newAttr = tx.createNode(ATTRIBUTE_LABEL);
        newAttr.setProperty("title", prop);
        obj.createRelationshipTo(newAttr, HAS_ATTRIBUTE);
        return newAttr;
    }

    /**
     * 输入：对象节点、属性名。
     * 返回：属性节点或 null。
     * 作用：在对象的属性关系中查找指定属性。
     */
    private Node findAttribute(Node obj, String prop) {
        for (Relationship r : obj.getRelationships(Direction.OUTGOING, HAS_ATTRIBUTE)) {
            Node attr = r.getEndNode();
            if (prop.equals(attr.getProperty("title"))) {
                return attr;
            }
        }
        return null;
    }

    /**
     * 输入：属性节点、时间点。
     * 返回：该时间点的属性值（PVal）或 null。
     * 作用：在属性值区间中选择包含时间点且起点最大的值。
     */
    private PVal valueAtTime(Node attr, int timestamp) {
        for (Relationship r : attr.getRelationships(Direction.OUTGOING, HAS_VALUE)) {
            Node val = r.getEndNode();
            Interval itv = parseInterval(val.getProperty("start_time"), val.getProperty("end_time"));
            if (itv == null || !itv.contains(timestamp)) {
                continue;
            }
            return toPVal(val.getProperty("value"));
        }
        return null;
    }

    /**
     * 输入：属性节点、时间区间。
     * 返回：区间内最大属性值（PVal）或 null。
     * 作用：求时间区间内的最大值。
     */
    private PVal maxValueInRange(Node attr, int t0, int t1) {
        PVal best = null;
        for (Relationship r : attr.getRelationships(Direction.OUTGOING, HAS_VALUE)) {
            Node val = r.getEndNode();
            Interval itv = parseInterval(val.getProperty("start_time"), val.getProperty("end_time"));
            if (itv == null || !itv.overlaps(t0, t1)) {
                continue;
            }
            PVal cur = toPVal(val.getProperty("value"));
            if (cur == null) {
                continue;
            }
            if (best == null || best.compareTo(cur) < 0) {
                best = cur;
            }
        }
        return best;
    }

    /**
     * 输入：属性节点、实体标识、时间区间。
     * 返回：区间内各值的持续时长列表。
     * 作用：计算每个值在区间内的持续时长。
     */
    private List<Triple<String, PVal, Integer>> durationsInRange(Node attr, String entity, int t0, int t1) {
        List<Triple<String, PVal, Integer>> rows = new ArrayList<>();
        for (Relationship r : attr.getRelationships(Direction.OUTGOING, HAS_VALUE)) {
            Node val = r.getEndNode();
            Interval itv = parseInterval(val.getProperty("start_time", null), val.getProperty("end_time", null));
            if (itv == null || !itv.overlaps(t0, t1)) {
                continue;
            }
            int duration = itv.overlapLength(t0, t1);
            if (duration > 0) {
                rows.add(Triple.of(entity, toPVal(val.getProperty("value", "")), duration));
            }
        }
        return rows;
    }

    /**
     * 输入：属性节点、时间区间、值区间。
     * 返回：是否存在满足条件的值。
     * 作用：判断区间内是否出现目标值范围。
     */
    private boolean existsValueWithin(Node attr, int t0, int t1, PVal vMin, PVal vMax) {
        for (Relationship r : attr.getRelationships(Direction.OUTGOING, HAS_VALUE)) {
            Node val = r.getEndNode();
            Interval itv = parseInterval(val.getProperty("start_time"), val.getProperty("end_time"));
            if (itv == null || !itv.overlaps(t0, t1)) {
                continue;
            }
            PVal v = toPVal(val.getProperty("value"));
            if (within(vMin, v, vMax)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 输入：事务、属性节点、新值、起止时间。
     * 返回：无。
     * 作用：在时间区间内插入或更新属性值，处理重叠区间并合并相邻值。
     * 把这个时间区间所有值节点覆盖，再重新创建值节点，再合并
     */
    private void upsertTemporalValue(Transaction t, Node attr, Object newValue, int st, int et) {
        List<Node> valueNodes = new ArrayList<>();
        for (Relationship r : attr.getRelationships(Direction.OUTGOING, HAS_VALUE)) {
            valueNodes.add(r.getEndNode());
        }

        for (Node val : valueNodes) {
            Interval itv = parseInterval(val.getProperty("start_time"), val.getProperty("end_time"));
            if (itv == null || !itv.overlaps(st, et)) {
                continue;
            }

            if (st <= itv.start && et >= itv.end) {
                deleteValueNode(val);
                continue;
            }

            if (st <= itv.start && et < itv.end) {
                int newStart = safeAddOne(et);
                if (newStart > itv.end) {
                    deleteValueNode(val);
                } else {
                    val.setProperty("start_time", newStart);
                    val.setProperty("end_time", itv.end);
                }
                continue;
            }

            if (st > itv.start && et >= itv.end) {
                int newEnd = safeSubOne(st);
                if (newEnd < itv.start) {
                    deleteValueNode(val);
                } else {
                    val.setProperty("start_time", itv.start);
                    val.setProperty("end_time", newEnd);
                }
                continue;
            }

            if (st > itv.start && et < itv.end) {
                int leftEnd = safeSubOne(st);
                int rightStart = safeAddOne(et);
                Object oldValue = val.getProperty("value");
                val.setProperty("start_time", itv.start);
                val.setProperty("end_time", leftEnd);

                if (rightStart <= itv.end) {
                    Node rightVal = t.createNode(VALUE_LABEL);
                    if (oldValue != null) {
                        rightVal.setProperty("value", oldValue);
                    }
                    rightVal.setProperty("start_time", rightStart);
                    rightVal.setProperty("end_time", itv.end);
                    attr.createRelationshipTo(rightVal, HAS_VALUE);
                }
            }
        }

        Node newValNode = t.createNode(VALUE_LABEL);
        if (newValue != null) {
            newValNode.setProperty("value", newValue);
        }
        newValNode.setProperty("start_time", st);
        newValNode.setProperty("end_time", et);
        attr.createRelationshipTo(newValNode, HAS_VALUE);

        mergeAttributeValues(attr);
    }

    /**
     * 输入：事务、静态关系、新属性集合、起止时间。
     * 返回：无。
     * 作用：在时间区间内插入或更新时态关系，处理重叠区间并合并相邻值。
     */
    private void upsertTemporalRelationship(Transaction t, Relationship staticRel, String sid, Map<String, Object> newProps, int st, int et) {
        List<Relationship> willBeDeleted = new ArrayList<>();
        int mn = Integer.MAX_VALUE, mx = Integer.MIN_VALUE;
        Relationship mnRel = null, mxRel = null;
        Map<String, Object> props = null;

        try (ResourceIterator<Relationship> it = t.findRelationships(RELATIONSHIP_TYPE_TEMPORAL, "u_sid", sid)) {
            while (it.hasNext()) {
                Relationship r = it.next();
                Integer rSt = toInt(r.getProperty("start_time"));
                Integer rEt = toInt(r.getProperty("end_time"));
                props = getRelationshipProps(r);

                if (et >= rSt && st <= rEt) {
                    willBeDeleted.add(r);
                    if (rSt < st && et < rEt) {
                        mn = rSt;
                        mx = rEt;
                        mnRel = mxRel = r;
                        break;
                    }
                    if (rSt < st) {
                        mnRel = r;
                        mn = rSt;
                    }
                    if (rEt > et) {
                        mxRel = r;
                        mx = rEt;
                    }
                }
            }
        }

        // update the first and last range.
        if (mnRel != null) {
            createTemporalRelationship(staticRel, sid, mn, safeSubOne(st), props);
        }
        if (mxRel != null) {
            createTemporalRelationship(staticRel, sid, safeAddOne(et), mx, props);
        }

        // delete the old value.
        for (Relationship d : willBeDeleted) {
            deleteRelationship(d);
        }

        // build the new relationship.
        Relationship rNew = createTemporalRelationship(staticRel, sid, st, et, props);
        for (Map.Entry<String, Object> entry : newProps.entrySet()) {
            rNew.setProperty(entry.getKey(), entry.getValue());
        }
    }

    /**
     * 输入：静态关系、u_sid、起止时间、属性集合。
     * 返回：创建的时态关系。
     * 作用：创建一个新的时态关系并设置属性。
     */
    private Relationship createTemporalRelationship(Relationship staticRel, String sid, int st, int et, Map<String, Object> props) {
        Relationship rel = staticRel.getStartNode().createRelationshipTo(staticRel.getEndNode(), RELATIONSHIP_TYPE_TEMPORAL);
        rel.setProperty("u_sid", sid);
        rel.setProperty("start_time", st);
        rel.setProperty("end_time", et);
        if (props != null) {
            for (Map.Entry<String, Object> entry : props.entrySet()) {
                rel.setProperty(entry.getKey(), entry.getValue());
            }
        }
        return rel;
    }

    /**
     * 输入：时态关系。
     * 返回：属性集合（不含 start_time/end_time/u_sid）。
     * 作用：提取关系属性用于比较/复制。
     */
    private Map<String, Object> getRelationshipProps(Relationship rel) {
        Map<String, Object> props = new HashMap<>();
        for (String key : rel.getPropertyKeys()) {
            if ("start_time".equals(key) || "end_time".equals(key) || "u_sid".equals(key)) {
                continue;
            }
            props.put(key, rel.getProperty(key));
        }
        return props;
    }

    /**
     * 输入：属性节点。
     * 返回：无。
     * 作用：合并相邻或重叠且值相同的区间。
     */
    private void mergeAttributeValues(Node attr) {
        List<ValueInterval> intervals = new ArrayList<>();
        for (Relationship r : attr.getRelationships(Direction.OUTGOING, HAS_VALUE)) {
            Node val = r.getEndNode();
            Interval itv = parseInterval(val.getProperty("start_time"), val.getProperty("end_time"));
            intervals.add(new ValueInterval(val, itv.start, itv.end, val.getProperty("value")));
        }
        intervals.sort((a, b) -> {
            if (a.start != b.start) {
                return Integer.compare(a.start, b.start);
            }
            return Integer.compare(a.end, b.end);
        });

        ValueInterval current = null;
        for (ValueInterval next : intervals) {
            if (current == null) {
                current = next;
                continue;
            }
            if (Objects.equals(current.value, next.value) && isAdjacentOrOverlapping(current.end, next.start)) {
                int mergedEnd = Math.max(current.end, next.end);
                current.end = mergedEnd;
                current.node.setProperty("end_time", mergedEnd);
                deleteValueNode(next.node);
            } else {
                current = next;
            }
        }
    }

    /**
     * 输入：值节点。
     * 返回：无。
     * 作用：删除值节点及其所有关系。
     */
    private void deleteValueNode(Node val) {
        for (Relationship r : val.getRelationships()) {
            r.delete();
        }
        val.delete();
    }

    /**
     * 输入：关系。
     * 返回：无。
     * 作用：删除关系。
     */
    private void deleteRelationship(Relationship rel) {
        rel.delete();
    }

    /**
     * 输入：当前区间结束、下一区间开始。
     * 返回：是否相邻或重叠。
     * 作用：判断两个区间是否可合并。
     */
    private boolean isAdjacentOrOverlapping(int end, int nextStart) {
        if (nextStart <= end) {
            return true;
        }
        return end != Integer.MAX_VALUE && nextStart == end + 1;
    }

    /**
     * 输入：整数。
     * 返回：加一后的整数（防止溢出）。
     * 作用：安全自增。
     */
    private int safeAddOne(int value) {
        return value == Integer.MAX_VALUE ? Integer.MAX_VALUE : value + 1;
    }

    /**
     * 输入：整数。
     * 返回：减一后的整数（防止溢出）。
     * 作用：安全自减。
     */
    private int safeSubOne(int value) {
        return value == Integer.MIN_VALUE ? Integer.MIN_VALUE : value - 1;
    }

    private static class ValueInterval {
        final Node node;
        final Object value;
        int start;
        int end;

        ValueInterval(Node node, int start, int end, Object value) {
            this.node = node;
            this.start = start;
            this.end = end;
            this.value = value;
        }
    }

    /**
     * 输入：最小值、当前值、最大值。
     * 返回：是否在区间内。
     * 作用：类型一致校验后做区间判断。
     */
    private boolean within(PVal vMin, PVal v, PVal vMax) {
        if (v == null || vMin == null || vMax == null) {
            return false;
        }
        if (v.getType() != vMin.getType() || v.getType() != vMax.getType()) {
            return false;
        }
        return PVal.within(vMin, true, v, vMax, true);
    }

    /**
     * 输入：任意类型值。
     * 返回：对应的 PVal。
     * 作用：将基础类型转换为 PVal 统一表示。
     */
    private PVal toPVal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Integer) {
            return PVal.i((Integer) value);
        }
        if (value instanceof Long) {
            return PVal.i(((Long) value).intValue());
        }
        if (value instanceof Float) {
            return PVal.f((Float) value);
        }
        if (value instanceof Double) {
            return PVal.f(((Double) value).floatValue());
        }
        return PVal.s(value.toString());
    }

    /**
     * 输入：起止对象。
     * 返回：Interval 或 null。
     * 作用：解析起止时间为区间对象。
     */
    private Interval parseInterval(Object startObj, Object endObj) {
        Integer start = toInt(startObj);
        Integer end = toInt(endObj);
        if (start == null || end == null) {
            return null;
        }
        return new Interval(start, end);
    }

    /**
     * 输入：任意类型值。
     * 返回：整数或 null。
     * 作用：将不同类型值安全转换为 int。
     */
    private Integer toInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Long) {
            return ((Long) value).intValue();
        }
        if (value instanceof Float) {
            return ((Float) value).intValue();
        }
        if (value instanceof Double) {
            return ((Double) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static class Interval {
        final int start;
        final int end;

        Interval(int start, int end) {
            this.start = start;
            this.end = end;
        }

        // 点 t 是否落在区间 [start, end] 内（含边界）
        boolean contains(int t) {
            return t >= start && t <= end;
        }

        // 区间 [start, end] 与 [t0, t1] 是否有交集（含边界）
        boolean overlaps(int t0, int t1) {
            return end >= t0 && start <= t1;
        }

        // 计算两区间的重叠长度，返回重叠部分的长度（若无重叠返回 0）
        int overlapLength(int t0, int t1) {
            int s = Math.max(start, t0);
            int e = Math.min(end, t1);
            return Math.max(0, e - s);
        }
    }
}
