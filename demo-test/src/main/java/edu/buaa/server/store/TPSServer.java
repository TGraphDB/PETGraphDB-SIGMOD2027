package edu.buaa.server.store;

import com.alibaba.fastjson.JSON;
import edu.buaa.common.RuntimeEnv;
import edu.buaa.common.server.DBSocketServer;
import edu.buaa.common.transaction.*;
import edu.buaa.common.utils.PFieldList;
import edu.buaa.common.utils.PVal;
import edu.buaa.utils.Helper;
import edu.buaa.utils.Pair;
import edu.buaa.utils.Triple;
import org.act.temporalProperty.TemporalPropertyStore;
import org.act.temporalProperty.TemporalPropertyStoreFactory;
import org.act.temporalProperty.impl.*;
import org.act.temporalProperty.meta.ValueContentType;
import org.act.temporalProperty.query.TimeIntervalKey;
import org.act.temporalProperty.query.TimePointL;
import org.act.temporalProperty.util.Slice;
import org.act.temporalProperty.util.TemporalPropertyValueConvertor;
import org.act.temporalProperty.vo.EntityPropertyId;
import org.neo4j.graphdb.temporal.TemporalRangeQuery;
import org.neo4j.graphdb.temporal.TimeIntervalRangeQuery;
import org.neo4j.graphdb.temporal.TimePoint;

import java.io.*;
import java.util.*;

import static org.act.temporalProperty.util.TemporalPropertyValueConvertor.toSlice;

public class TPSServer implements DBSocketServer.DBKernelProxy {
    public static void main(String[] args) {
        Options.setGlobalCompressionType(CompressionType.SNAPPY);

        DBSocketServer server = new DBSocketServer(dbDir(), new TPSServer(), Integer.parseInt(Helper.mustEnv("DB_PORT")), true);
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

    private StaticDataManager nodeStaticManager, relStaticManager;
    private TemporalPropertyStore nodeTemporalStore, relTemporalStore;

    @Override
    public void start(File path) throws Exception {
        File staticDir = new File(path, "static");
        File temporalDir = new File(path, "temporal");
        createDirIfNotExists(staticDir);
        createDirIfNotExists(temporalDir);
        nodeStaticManager = new StaticDataManager(new File(staticDir, "node"));
        relStaticManager = new StaticDataManager(new File(staticDir, "relationship"));
        Options op = new Options().memTableSize(getMemTableSize());
        System.out.println("TPS memtable size set to " + op.memTableSize() + "MB");
        nodeTemporalStore = TemporalPropertyStoreFactory.newPropertyStore(new File(temporalDir, "node"));
        relTemporalStore = TemporalPropertyStoreFactory.newPropertyStore(new File(temporalDir, "relationship"));
    }


    protected int getMemTableSize() {
        return 64;
    }

    private static void createDirIfNotExists(File dir) {
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw new IllegalArgumentException("invalid dbDir");
            }
        } else if (!dir.isDirectory()) {
            throw new IllegalArgumentException("invalid dbDir");
        }
    }

    @Override
    public void shutdown() {
        nodeStaticManager.shutdown();
        relStaticManager.shutdown();
        try {
            nodeTemporalStore.shutDown();
            relTemporalStore.shutDown();
        } catch (Exception e) {
            e.printStackTrace();
        }
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
                case tx_query_entity_history:
                    return execute((EntityHistoryTx) tx);
                case tx_query_road_by_temporal_condition:
                    return execute((EntityTemporalConditionTx) tx);
                case tx_query_snapshot_aggr_duration:
                case tx_query_reachable_area:
                default:
                    throw new UnsupportedOperationException();
            }
        }catch (Exception e){
            throw new TransactionFailedException(e);
        }
    }

    protected AbstractTransaction.Result execute(ImportStaticDataTx tx) {
        PFieldList nodesData = tx.getNodes();
        Set<String> props = nodesData.keysWithout("u_sid");
        int nSize = nodesData.size();
        for (int i=0; i<nSize; i++) {
            String id = nodesData.get("u_sid", i).s();
            nodeStaticManager.createEntityId(id);
            HashMap<String, Object> map = new HashMap<>();
            for(String key : props){
                map.put(key, nodesData.get(key, i).getVal());
            }
            nodeStaticManager.setStaticProperties(id, map);
        }
        nodeStaticManager.flush();
        PFieldList relData = tx.getRels();
        props = relData.keysWithout("u_sid");
        int rSize = relData.size();
        for (int i=0; i<rSize; i++) {
            String id = relData.get("u_sid", i).s();
            HashMap<String, Object> map = new HashMap<>();
            relStaticManager.createEntityId(id);
            for(String key : props){
                map.put(key, relData.get(key, i).getVal());
            }
            relStaticManager.setStaticProperties(id, map);
        }
        relStaticManager.flush();
        return new AbstractTransaction.Result();
    }

    protected AbstractTransaction.Result execute(ImportTemporalDataTx tx) {
        PFieldList data = tx.getData();
        TemporalPropertyStore store = tx.isNode() ? nodeTemporalStore : relTemporalStore;
        StaticDataManager staticDataManager = tx.isNode() ? nodeStaticManager : relStaticManager;
        Set<String> props = data.keysWithout("u_sid", "t");
        int tSize = data.size();
        for (int i=0; i<tSize; i++) {
            String id = data.get("u_sid", i).s();
            long entityId = staticDataManager.getEntityId(id);
            long timestamp = (long) data.get("t", i).i();
            for(String key : props){
                Object v = data.get(key, i).getVal();
                int propertyId = staticDataManager.getOrCreatePropertyId(key);
                TimeIntervalKey timeIntervalKey = new TimeIntervalKey(new EntityPropertyId(entityId, propertyId),
                        new TimePointL(timestamp), TimePointL.Now, ValueType.fromValue(v));
                Slice value = toSlice(v);
                store.setProperty(timeIntervalKey, value);
            }
        }
        return new AbstractTransaction.Result();
    }

    protected AbstractTransaction.Result execute(UpdateTemporalDataTx tx) {
        PFieldList data = tx.getData();
        TemporalPropertyStore store = tx.isNode() ? nodeTemporalStore : relTemporalStore;
        StaticDataManager staticDataManager = tx.isNode() ? nodeStaticManager : relStaticManager;
        Set<String> props = data.keysWithout("u_sid", "st", "et");
        int tSize = data.size();
        for (int i=0; i<tSize; i++) {
            String id = data.get("u_sid", i).s();
            long entityId = staticDataManager.getEntityId(id);
            long t0 = (long) data.get("st", i).i();
            long t1 = (long) data.get("et", i).i();
            for(String key : props){
                Object v = data.get(key, i).getVal();
                int propertyId = staticDataManager.getOrCreatePropertyId(key);
                TimeIntervalKey timeIntervalKey = new TimeIntervalKey(new EntityPropertyId(entityId, propertyId),
                        new TimePointL(t0), new TimePointL(t1), ValueType.fromValue(v));
                Slice value = toSlice(v);
                store.setProperty(timeIntervalKey, value);
            }
        }
        return new AbstractTransaction.Result();
    }

    protected AbstractTransaction.Result execute(SnapshotQueryTx tx) {
        TemporalPropertyStore store = tx.isNode() ? nodeTemporalStore : relTemporalStore;
        StaticDataManager staticDataManager = tx.isNode() ? nodeStaticManager : relStaticManager;
        List<Pair<String, PVal>> answers = new ArrayList<>();
        TimePoint t = Helper.time(tx.getTimestamp());
        int propId = staticDataManager.getOrCreatePropertyId(tx.getPropertyName());
        ValueContentType vType = store.getPropertyValueType(propId);
        staticDataManager.getEntityIdMap().forEach((idStr, idLong) -> {
            Slice v = store.getPointValue(idLong, propId, t);
            if(v!=null){
                Object val = TemporalPropertyValueConvertor.fromSlice(vType, v);
                answers.add(Pair.of(idStr, PVal.v(val))); //放入answers中
            }
        });
        SnapshotQueryTx.Result result = new SnapshotQueryTx.Result();
        result.answer(answers);
        return result;
    }

    protected AbstractTransaction.Result execute(SnapshotAggrMaxTx tx) {
        TemporalPropertyStore store = tx.isNode() ? nodeTemporalStore : relTemporalStore;
        StaticDataManager staticDataManager = tx.isNode() ? nodeStaticManager : relStaticManager;
        List<Pair<String, PVal>> answers = new ArrayList<>();
        TimePoint t0 = Helper.time(tx.getT0());
        TimePoint t1 = Helper.time(tx.getT1());
        int propId = staticDataManager.getOrCreatePropertyId(tx.getP());
        staticDataManager.getEntityIdMap().forEach((idStr, idLong) -> {
            Object v = store.getRangeValue(idLong, propId, t0, t1, new TemporalRangeQuery.MaxValue());
            if(v!=null){
                answers.add(Pair.of(idStr, PVal.v(v))); //放入answers中
            }
        });
        SnapshotAggrMaxTx.Result result = new SnapshotAggrMaxTx.Result();
        result.setPropMaxValue(answers);
        return result;
    }

    protected AbstractTransaction.Result execute(EntityTemporalConditionTx tx) {
        TemporalPropertyStore store = tx.isNode() ? nodeTemporalStore : relTemporalStore;
        StaticDataManager staticDataManager = tx.isNode() ? nodeStaticManager : relStaticManager;
        List<String> answers = new ArrayList<>();
        TimePoint t0 = Helper.time(tx.getT0());
        TimePoint t1 = Helper.time(tx.getT1());
        int propId = staticDataManager.getOrCreatePropertyId(tx.getP());
        staticDataManager.getEntityIdMap().forEach((idStr, idLong) -> {
            Object v = store.getRangeValue(idLong, propId, t0, t1, new TemporalRangeQuery() {
                @Override
                public boolean onNewEntry(long entityId, int propertyId, TimePointL time, Object val) {
                    if (val instanceof Comparable && PVal.within(tx.getVMin(), true, PVal.v(val), tx.getVMax(), true)) {
                        answers.add(idStr);
                        return false;
                    }
                    return true;
                }
            });
        });
        EntityTemporalConditionTx.Result result = new EntityTemporalConditionTx.Result();
        result.setEntities(answers);
        return result;
    }

    protected AbstractTransaction.Result execute(EntityHistoryTx tx) {
        TemporalPropertyStore store = tx.isNode() ? nodeTemporalStore : relTemporalStore;
        StaticDataManager staticDataManager = tx.isNode() ? nodeStaticManager : relStaticManager;
        TimePointL t0 = new TimePointL(tx.getBeginTime());
        TimePointL t1 = new TimePointL(tx.getEndTime());
        long entityId = staticDataManager.getEntityId(tx.getEntity());
        int propertyId = staticDataManager.getOrCreatePropertyId(tx.getProp());
        List<Triple<Integer, Integer, PVal>> answers = new ArrayList<>();
        store.getRangeValue(entityId, propertyId, t0, t1, new TimeIntervalRangeQuery(t0, t1){
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

    public static class TPSServerMem1 extends TPSServer {
        public static void main(String[] args) {
            Options.setGlobalCompressionType(CompressionType.SNAPPY);

            DBSocketServer server = new DBSocketServer(dbDir(), new TPSServerMem1(), Integer.parseInt(Helper.mustEnv("DB_PORT")), true);
            RuntimeEnv env = RuntimeEnv.getCurrentEnv();
            String serverCodeVersion = env.name() + "." + Helper.codeGitVersion();
            System.out.println("server code version:" + serverCodeVersion);
            try {
                server.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        protected int getMemTableSize() {
            return 1;
        }
    }

    public static class TPSServerMem4 extends TPSServer {
        public static void main(String[] args) {
            Options.setGlobalCompressionType(CompressionType.SNAPPY);

            DBSocketServer server = new DBSocketServer(dbDir(), new TPSServerMem4(), Integer.parseInt(Helper.mustEnv("DB_PORT")), true);
            RuntimeEnv env = RuntimeEnv.getCurrentEnv();
            String serverCodeVersion = env.name() + "." + Helper.codeGitVersion();
            System.out.println("server code version:" + serverCodeVersion);
            try {
                server.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        protected int getMemTableSize() {
            return 4;
        }
    }

    public static class TPSServerMem16 extends TPSServer {
        public static void main(String[] args) {
            Options.setGlobalCompressionType(CompressionType.SNAPPY);

            DBSocketServer server = new DBSocketServer(dbDir(), new TPSServerMem16(), Integer.parseInt(Helper.mustEnv("DB_PORT")), true);
            RuntimeEnv env = RuntimeEnv.getCurrentEnv();
            String serverCodeVersion = env.name() + "." + Helper.codeGitVersion();
            System.out.println("server code version:" + serverCodeVersion);
            try {
                server.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        protected int getMemTableSize() {
            return 16;
        }
    }
}