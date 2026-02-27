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
import org.neo4j.graphdb.schema.IndexDefinition;
import org.neo4j.graphdb.schema.Schema;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class Neo4jServer1 implements DBSocketServer.DBKernelProxy {
    public static void main(String[] args) {
        DBSocketServer server = new DBSocketServer(dbDir(), new Neo4jServer1(), Integer.parseInt(Helper.mustEnv("DB_PORT")), "true".equals(System.getenv("ALLOW_TX_FAILURE")));
        RuntimeEnv env = RuntimeEnv.getCurrentEnv();
        String serverCodeVersion = env.name() + "." + Helper.codeGitVersion();
        System.out.println("server code version:" + serverCodeVersion);
        try {
            server.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
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

    public enum Edge implements RelationshipType {
        TOPO_N_E, TOPO_E_N, V_TPROP, E_TPROP
    }

    public enum NodeType {
        VERTEX, EDGE, TIME_INTERVAL
    }

    DatabaseManagementService dbms;

    public static Label label(NodeType type){
        return Label.label(type.name());
    }

    @Override
    public void start(File path) {
        dbms = new DatabaseManagementServiceBuilder(path.toPath()).build();
        GraphDatabaseService db = dbms.database("neo4j");
        try (Transaction tx = db.beginTx()) {
            Schema schema = tx.schema();
            boolean hasTimeIndex = false;
            try {
                for(IndexDefinition timeIndex : schema.getIndexes()){
                    System.out.println(timeIndex.getLabels()+":"+timeIndex.getPropertyKeys());
                    hasTimeIndex = true;
                }
            } catch (IllegalArgumentException e) {
                // There is no this index
            }
            if (hasTimeIndex) {
                System.out.println("Has the time index already.");
            }else{
                schema.indexFor(label(NodeType.VERTEX)).on("u_sid").create();
                schema.indexFor(label(NodeType.EDGE)).on("u_sid").create();
                System.out.println("Build the NODE index.");
                schema.indexFor(label(NodeType.TIME_INTERVAL)).on("st").create(); //.on("et")
                System.out.println("Build the TIME index.");
            }
            tx.commit();
        }
        try(Transaction tx = db.beginTx()){
            awaitIndexes(tx.schema());
            tx.commit();
        }
    }

    @Override
    public void shutdown() {
        dbms.shutdown();
    }

    public void awaitIndexes(Schema schema){
        boolean shouldWait = true;
        int cnt = 0;
        while(shouldWait) try{
            schema.awaitIndexesOnline(10, TimeUnit.SECONDS);
            shouldWait = false;
        } catch (IllegalArgumentException | IllegalStateException e) {
            cnt++;
            System.out.println("indexes not ready after "+cnt*10+" seconds.");
        }
        System.out.println("indexes all online.");
    }


    @Override
    public AbstractTransaction.Result execute(String line) throws RuntimeException {
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
                    return execute((ReachableAreaQueryTx) tx);
                default:
                    throw new UnsupportedOperationException();
            }
        }catch (Exception e){
            if(e instanceof org.neo4j.kernel.DeadlockDetectedException) throw e;
            else throw new TransactionFailedException(e);
        }
    }

    protected AbstractTransaction.Result execute(ImportStaticDataTx tx) {
        GraphDatabaseService db = dbms.database("neo4j");
        Label dataNode = label(NodeType.VERTEX);
        try (Transaction t = db.beginTx()) {
            PFieldList nodesData = tx.getNodes();
            int nSize = nodesData.size();
            for (int i=0; i<nSize; i++) {
                Node n = t.createNode(dataNode);
                String id = nodesData.get("u_sid", i).s();
                n.setProperty("u_sid", id);
                for(String key : nodesData.keysWithout("u_sid")){
                    n.setProperty(key, nodesData.get(key, i).getVal());
                }
            }
            PFieldList relsData = tx.getRels();
            int rSize = relsData.size();
            for (int i=0; i<rSize; i++) {
                String fromId = relsData.get("r_from", i).s();
                String toId = relsData.get("r_to", i).s();
                Node s = getEntityNodeByRawId(fromId, t);
                Node e = getEntityNodeByRawId(toId, t);
                Node edge = t.createNode(label(NodeType.EDGE));
                s.createRelationshipTo(edge, Edge.TOPO_N_E);
                edge.createRelationshipTo(e, Edge.TOPO_E_N);
                for(String key : relsData.keysWithout("r_from", "r_to")){
                    edge.setProperty(key, relsData.get(key, i).getVal());
                }
            }
            t.commit();
        }
        return new AbstractTransaction.Result();
    }

    protected void setProperties(Entity e, PFieldList data, int index, Set<String> keys ){
        for(String propName : keys) {
            e.setProperty(propName, data.get(propName, index).getVal());
        }
    }

    // Assume that data comes order by time. Thus, multithreading would not work.
    protected AbstractTransaction.Result execute(ImportTemporalDataTx tx) {
        GraphDatabaseService db = dbms.database("neo4j");
        try (Transaction ttx = db.beginTx()) {
            PFieldList data = tx.getData();
            int pSize = data.size();
            Set<String> props = data.keysWithout("u_sid", "t");
            for (int i=0; i<pSize; i++) {
                //find node
                String entityRawId = data.get("u_sid", i).s();
                int time = data.get("t", i).i();
                try {
                    update(ttx, tx.isNode(), entityRawId, time, Integer.MAX_VALUE, data, i, props);
                }catch (NotFoundException e){
                    if(!e.getMessage().contains("u_sid")) throw e;
                }
            }
            ttx.commit();
        }
        return new AbstractTransaction.Result();
    }

    protected Node getEntityNodeByRawId(String entityRawId, Transaction tx) {
        ResourceIterator<Node> entities = tx.findNodes(label(NodeType.VERTEX), "u_sid", entityRawId);
        if(entities.hasNext()){
            return entities.next();
        }
        return null;
    }

    protected Node getTimeNode(Node entity, RelationshipType t, int startTime, int endTime, Transaction tx) {
        Node ret;
        ResourceIterator<Node> nodes = tx.findNodes(label(NodeType.TIME_INTERVAL), "st", startTime);
        while (nodes.hasNext()) {
            ret = nodes.next();
            if((Integer) ret.getProperty("et")==endTime) return ret;
        }
        ret = tx.createNode(label(NodeType.TIME_INTERVAL));
        ret.setProperty("st", startTime);
        ret.setProperty("et", endTime);
        return ret;
    }

    protected void copyProperty(Relationship from, Relationship to, Set<String> keys){
        for(String propName : from.getPropertyKeys()) {
            to.setProperty(propName, from.getProperty(propName));
        }
    }

    protected void update(Transaction tx, boolean isNode, String entityRawId, int beginT, int endT, PFieldList data, int index, Set<String> keys){
        Node node = tx.findNode(label(isNode ? NodeType.VERTEX : NodeType.EDGE), "u_sid", entityRawId);
        assert node!=null : new NotFoundException("Entity not found: u_sid= "+entityRawId);
        tx.acquireWriteLock(node);
        // delete the old node and relationships.
        RelationshipType rType = isNode ? Edge.V_TPROP : Edge.E_TPROP;
        ArrayList<Relationship> willBeDeleted = new ArrayList<>();
        int mn = Integer.MAX_VALUE, mx = Integer.MIN_VALUE;
        Relationship mnRel = null, mxRel = null;
        for (Relationship r : node.getRelationships(rType)) {
            Node timeNode = r.getOtherNode(node);
            int st = (int) timeNode.getProperty("st");
            int en = (int) timeNode.getProperty("et");
            if (endT >= st && beginT <= en) {
                willBeDeleted.add(r);
                if (st < beginT && endT < en) {
                    mn = st;
                    mx = en;
                    mnRel = mxRel = r;
                    break;
                }
                if (st < beginT) {
                    mnRel = r;
                    mn = st;
                }
                if (en > endT) {
                    mxRel = r;
                    mx = en;
                }
            }
        }
        // update the first and last range.
        if(mnRel!=null){
            Node t1 = getTimeNode(node, rType, mn, beginT - 1, tx);
            Relationship r = node.createRelationshipTo(t1, rType);
            copyProperty(mnRel, r, keys);
        }
        if(mxRel!=null){
            Node t2 = getTimeNode(node, rType, endT + 1, mx, tx);
            Relationship r = node.createRelationshipTo(t2, rType);
            copyProperty(mxRel, r, keys);
        }
        // delete the old value.
        for (Relationship d : willBeDeleted) {
            Node time = d.getOtherNode(node);
            d.delete();
            if (time.getDegree() == 0) time.delete();
        }
        // build the new node.
        Node timeNode = getTimeNode(node, rType, beginT, endT, tx);
        Relationship rel = node.createRelationshipTo(timeNode, rType);
        setProperties(rel, data, index, keys);
    }

    protected AbstractTransaction.Result execute(UpdateTemporalDataTx tx) {
        GraphDatabaseService db = dbms.database("neo4j");
        PFieldList data = tx.getData();
        Set<String> props = data.keysWithout("u_sid", "st", "et");
        int tSize = data.size();
        try (Transaction t = db.beginTx()) {
            for(int i=0; i<tSize; i++){
                String entityRawId = data.get("u_sid", i).s();
                int st = data.get("st", i).i();
                int et = data.get("et", i).i();
                try {
                    update(t, tx.isNode(), entityRawId, st, et, data, i, props);
                }catch (NotFoundException e){
                    if(!e.getMessage().contains("u_sid")) throw e;
                }
            }
            t.commit();
        }
        return new AbstractTransaction.Result();
    }

    protected AbstractTransaction.Result execute(SnapshotQueryTx tx) {
        GraphDatabaseService db = dbms.database("neo4j");
        try (Transaction t = db.beginTx()) {
            List<Pair<String, PVal>> answers = new ArrayList<>();
            int time = tx.getTimestamp();
            RelationshipType rType = tx.isNode() ? Edge.V_TPROP : Edge.E_TPROP;
            ResourceIterator<Node> timeNodes = t.findNodes(label(NodeType.TIME_INTERVAL));
            while (timeNodes.hasNext()) {
                Node node = timeNodes.next();
                int st = (int) node.getProperty("st");
                int en = (int) node.getProperty("et");
                if (time >= st && time <= en) {
                    for (Relationship rel : node.getRelationships(rType)) {
                        Node entity = rel.getOtherNode(node);
                        answers.add(Pair.of((String) entity.getProperty("u_sid"), PVal.v(rel.getProperty(tx.getPropertyName()))));
                    }
                }
            }
            t.commit();
            SnapshotQueryTx.Result result = new SnapshotQueryTx.Result();
            result.answer(answers);
            return result;
        }
    }

    protected AbstractTransaction.Result execute(SnapshotAggrMaxTx tx) {
        GraphDatabaseService db = dbms.database("neo4j");
        try (Transaction t = db.beginTx()) {
            List<Pair<String, PVal>> answers = new ArrayList<>();
            Map<String, PVal> ans = new HashMap<>();
            int stt = tx.getT0(), ett = tx.getT1();
            RelationshipType rType = tx.isNode() ? Edge.V_TPROP : Edge.E_TPROP;
            ResourceIterator<Node> timeNodes = t.findNodes(label(NodeType.TIME_INTERVAL));
            while (timeNodes.hasNext()) {
                Node node = timeNodes.next();
                int st = (int) node.getProperty("st");
                int en = (int) node.getProperty("et");
                if (stt <= en && st <= ett) { // overlap
                    for (Relationship rel : node.getRelationships(rType)) {
                        Node entity = rel.getOtherNode(node);
                        ans.merge((String) entity.getProperty("u_sid"), PVal.v(rel.getProperty(tx.getP())), (val, val2) -> {
                            if(val.compareTo(val2)>0) return val;
                            else return val2;
                        });
                    }
                }
            }
            for (Map.Entry<String, PVal> entry : ans.entrySet()) {
                answers.add(Pair.of(entry.getKey(), entry.getValue()));
            }
            t.commit();
            SnapshotAggrMaxTx.Result result = new SnapshotAggrMaxTx.Result();
            result.setPropMaxValue(answers);
            return result;
        }
    }

    protected AbstractTransaction.Result execute(SnapshotAggrDurationTx tx) {
        GraphDatabaseService db = dbms.database("neo4j");
        TreeSet<PVal> intStarts = tx.getIntStartTreeSet();
        try (Transaction t = db.beginTx()) {
            List<Triple<String, PVal, Integer>> res = new ArrayList<>();
            int t0 = tx.getT0(), t1 = tx.getT1();
            ResourceIterator<Node> timeNodes = t.findNodes(label(NodeType.TIME_INTERVAL));
            HashMap<Pair<String, PVal>, Integer> ans = new HashMap<>();
            while (timeNodes.hasNext()) {
                Node node = timeNodes.next();
                int st = (int) node.getProperty("st");
                int en = (int) node.getProperty("et");
                if (t1 >= st && t0 <= en) {
                    int left = Math.max(t0, st);
                    int right = Math.min(t1, en);
                    int duration = right - left + 1;

                    for (Relationship rel : node.getRelationships(tx.isNode()?Edge.V_TPROP:Edge.E_TPROP)) {
                        String id = (String) rel.getOtherNode(node).getProperty("u_sid");
                        PVal property = PVal.v(rel.getProperty(tx.getP()));
                        PVal grp = intStarts.floor(property);
                        if(grp!=null) {
                            ans.merge(Pair.of(id, grp), duration, Integer::sum);
                        }
                    }
                }
            }
            for (Map.Entry<Pair<String, PVal>, Integer> entry : ans.entrySet()) {
                res.add(Triple.of(entry.getKey().getKey(), entry.getKey().getValue(), entry.getValue()));
            }
            t.commit();
            SnapshotAggrDurationTx.Result result = new SnapshotAggrDurationTx.Result();
            result.setStatusDuration(res);
            return result;
        }
    }

    protected AbstractTransaction.Result execute(EntityHistoryTx tx) {
        GraphDatabaseService db = dbms.database("neo4j");
        try (Transaction t = db.beginTx()) {
            RelationshipType rType = tx.isNode() ? Edge.V_TPROP : Edge.E_TPROP;
            List<Triple<Integer, Integer, PVal>> res = new ArrayList<>();
            int t0 = tx.getBeginTime(), t1 = tx.getEndTime();
            ResourceIterator<Node> timeNodes = t.findNodes(label(NodeType.TIME_INTERVAL));
            while (timeNodes.hasNext()) {
                Node node = timeNodes.next();
                int st = (int) node.getProperty("st");
                int en = (int) node.getProperty("et");
                if (t0 <= en && st <= t1) { // overlap
                    for (Relationship rel : node.getRelationships(rType)) {
                        Node entity = rel.getOtherNode(node);
                        String id = (String) entity.getProperty("u_sid");
                        if(tx.getEntity().equals(id)) {
                            int left = Math.max(t0, st);
                            int right = Math.min(t1, en);
                            res.add(Triple.of(left, right, PVal.v(rel.getProperty(tx.getProp()))));
                        }
                    }
                }
            }
            t.commit();
            EntityHistoryTx.Result result = new EntityHistoryTx.Result();
            result.setHistory(res);
            return result;
        }
    }

    protected AbstractTransaction.Result execute(EntityTemporalConditionTx tx) {
        GraphDatabaseService db = dbms.database("neo4j");
        try (Transaction t = db.beginTx()) {
            int t0 = tx.getT0(), t1 = tx.getT1();
            PVal vMin = tx.getVMin(), vMax = tx.getVMax();
            RelationshipType rType = tx.isNode() ? Edge.V_TPROP : Edge.E_TPROP;
            HashSet<String> res = new HashSet<>();
            ResourceIterator<Node> timeNodes = t.findNodes(label(NodeType.TIME_INTERVAL));
            while (timeNodes.hasNext()) {
                Node node = timeNodes.next();
                int st = (int) node.getProperty("st");
                int en = (int) node.getProperty("et");
                if (t1 >= st && t0 <= en) {
                    for (Relationship rel : node.getRelationships(rType)) {
                        PVal property = PVal.v(rel.getProperty(tx.getP()));
                        String entity = (String) rel.getOtherNode(node).getProperty("u_sid");
                        if (vMin.compareTo(property)<=0 && property.compareTo(vMax)<=0) {
                            res.add(entity);
                        }
                    }
                }
            }
            t.commit();
            EntityTemporalConditionTx.Result result = new EntityTemporalConditionTx.Result();
            result.setEntities(new ArrayList<>(res));
            return result;
        }
    }

    protected AbstractTransaction.Result execute(ReachableAreaQueryTx tx) {
        GraphDatabaseService db = dbms.database("neo4j");
        try(Transaction t = db.beginTx()) {
            Node start = getEntityNodeByRawId(tx.getStartNode(), t);
            ReachableAreaNeo4j algo = new ReachableAreaNeo4j(tx.getProp(), start.getId(), tx.getDepartureTime(), tx.getTravelTime(), t);
            List<Pair<Integer, String>> answers = new ArrayList<>();
            for(ReachableAreaQueryTx.TemporalDijkstraAlgo.NodeCross nodeCross : algo.run()){
                String u_sid = (String) t.getNodeById(nodeCross.getId()).getProperty("u_sid");
                answers.add(Pair.of(nodeCross.getArriveTime(), u_sid));
            }
            ReachableAreaQueryTx.Result result = new ReachableAreaQueryTx.Result();
//            result.setNodeArriveTime(answers);
//            result.setInnerResults(algo.getInnerResults());
            result.setStatResult(algo.statResult);
            t.commit();
            return result;
        }
    }

    public static class ReachableAreaNeo4j extends ReachableAreaQueryTx.TemporalDijkstraAlgo {
        private final String travelTimePropertyKey;
        private final Transaction transaction;

        public ReachableAreaNeo4j(String travelTimePropertyKey, long startId, int startTime, int travelTime, Transaction transaction){
            super(startId, startTime, travelTime, true);
            this.travelTimePropertyKey = travelTimePropertyKey;
            this.transaction = transaction;
        }

        protected int getEarliestArriveTime(Long roadId, int departureTime) throws UnsupportedOperationException {
            Node edgeNode = transaction.getNodeById(roadId);
            final int[] arriveTime = {Integer.MAX_VALUE};
            TreeMap<Integer, Integer> result = new TreeMap<>(); // dep time, arr time;
            for(Relationship tpr: edgeNode.getRelationships(Edge.E_TPROP)){
                Node timeNode = tpr.getOtherNode(edgeNode);
                int st = (int) timeNode.getProperty("st");
                int en = (int) timeNode.getProperty("et");
                if(st<=this.endTime && en>=departureTime){
                    int travel_t = (int) tpr.getProperty(travelTimePropertyKey);
                    int left = Math.max(departureTime, st);
                    int arrT = left+travel_t;
                    result.put(left, arrT);
                }
            }
            if(result.isEmpty() || result.firstKey()>departureTime) throw new UnsupportedOperationException();
            result.forEach((k,v)-> arriveTime[0] = Math.min(v, arriveTime[0]));
            if(arriveTime[0] <Integer.MAX_VALUE) return arriveTime[0];
            else throw new UnsupportedOperationException();
        }

        @Override
        protected Iterable<Long> getAllOutRoads(long nodeId) {
            Node node = transaction.getNodeById(nodeId);
            List<Long> result = new ArrayList<>();
            for(Relationship r : node.getRelationships(Edge.TOPO_N_E)){
                Node edgeNode = r.getOtherNode(node);
                result.add(edgeNode.getId());
            }
            return result;
        }

        @Override
        protected long getEndNodeId(long roadId) {
            Node edgeNode = transaction.getNodeById(roadId);
            for(Relationship r : edgeNode.getRelationships(Edge.TOPO_E_N)){
                Node endNode = r.getOtherNode(edgeNode);
                return endNode.getId();
            }
            throw new IllegalStateException("SNH: edge end node not found!");
        }
    }

}
