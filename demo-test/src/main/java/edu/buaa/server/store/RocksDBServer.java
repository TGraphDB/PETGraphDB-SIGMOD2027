package edu.buaa.server.store;

import com.alibaba.fastjson.JSON;
import edu.buaa.common.server.DBSocketServer;
import edu.buaa.common.transaction.*;
import edu.buaa.common.utils.PVal;
import edu.buaa.common.utils.TemporalGraphPropertySchema;
import edu.buaa.common.utils.PFieldList;
import edu.buaa.utils.Helper;
import edu.buaa.utils.Triple;
import org.rocksdb.CompressionType;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.Statistics;
import org.rocksdb.WriteOptions;
import org.rocksdb.util.SizeUnit;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 将RocksDB分为两个server去实现，主要的流程相同，不同点在于时态属性的key的组合方式不同，详见本类的子类的代码与注释
 */
public abstract class RocksDBServer implements DBSocketServer.DBKernelProxy {
    static {
        RocksDB.loadLibrary();
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
    private RocksDB nodeTemporalStore, relTemporalStore;
    private WriteOptions writeOptions;

    @Override
    public void start(File path) throws Exception {
        File staticDir = new File(path, "static");
        File temporalDir = new File(path, "temporal");
        createDirIfNotExists(staticDir);
        createDirIfNotExists(temporalDir);
        writeOptions = new WriteOptions()
                .setDisableWAL(true)
                .setNoSlowdown(false)
                .setSync(false);
        nodeStaticManager = new StaticDataManager(new File(staticDir, "node"));
        relStaticManager = new StaticDataManager(new File(staticDir, "relationship"));
        nodeTemporalStore = openRocksDB(new File(temporalDir, "node").getPath());
        relTemporalStore = openRocksDB(new File(temporalDir, "relationship").getPath());
    }

    private RocksDB openRocksDB(String dbPath) throws RocksDBException {
        final Options options = new Options();
        final Statistics stats = new Statistics();
        int memtableSize = getMemTableSize();
        System.out.println("ROCKSDB memtable size set to " + memtableSize + "MB");
        options.setCreateIfMissing(true)
                .setStatistics(stats)
                .setWriteBufferSize(memtableSize * SizeUnit.MB)
                .setMaxWriteBufferNumber(2)
                .setMaxBackgroundJobs(1)
                .setCompressionType(CompressionType.SNAPPY_COMPRESSION)
                .setBottommostCompressionType(CompressionType.SNAPPY_COMPRESSION);
        return RocksDB.open(options, dbPath);
    }

    protected abstract int getMemTableSize();

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
        nodeTemporalStore.close();
        relTemporalStore.close();
        if (writeOptions != null) {
            writeOptions.close();
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
                case tx_query_entity_history:
                    return execute((EntityHistoryTx) tx);
                case tx_query_road_by_temporal_condition:
                case tx_query_reachable_area:
                case tx_update_temporal_data:
                case tx_query_snapshot:
                case tx_query_snapshot_aggr_max:
                case tx_query_snapshot_aggr_duration:
                default:
                    throw new UnsupportedOperationException();
            }
        }catch (Exception e){
            throw new TransactionFailedException(e);
        }
    }

    protected abstract byte[] getKeyBytes(TemporalKey key);

    protected abstract TemporalKey KeyFromBytes(byte[] bytes);

    public AbstractTransaction.Result execute(ImportStaticDataTx tx) throws RocksDBException {
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
        return new AbstractTransaction.Result();
    }

    public AbstractTransaction.Result execute(ImportTemporalDataTx tx) throws RocksDBException {
        PFieldList data = tx.getData();
        RocksDB temporalStore = tx.isNode() ? nodeTemporalStore : relTemporalStore;
        StaticDataManager staticDataManager = tx.isNode() ? nodeStaticManager : relStaticManager;
        Set<String> props = data.keysWithout("u_sid", "t");
        int tSize = data.size();
        for (int i=0; i<tSize; i++) {
            String id = data.get("u_sid", i).s();
            long entityId = staticDataManager.getEntityId(id);
            long timestamp = (long) data.get("t", i).i();
            for(String key : props){
                int propertyId = staticDataManager.getOrCreatePropertyId(key);
                byte[] keyBytes = getKeyBytes(new TemporalKey(entityId, propertyId, timestamp));
                Object value = data.get(key, i).getVal();
                setTemporalValue(temporalStore, keyBytes, value);
            }
        }
        return new AbstractTransaction.Result();
    }

    private void setTemporalValue(RocksDB store, byte[] key, Object value) throws RocksDBException {
        byte[] valueBytes;
        if (value instanceof Integer) {
            valueBytes = ByteBuffer.allocate(Integer.BYTES).putInt((Integer) value).array();
        }
        else if (value instanceof Float) {
            valueBytes = ByteBuffer.allocate(Float.BYTES).putFloat((Float) value).array();
        }
        else if (value instanceof String) {
            valueBytes = ((String) value).getBytes(StandardCharsets.UTF_8);
        }
        else {
            throw new IllegalArgumentException();
        }
        store.put(writeOptions, key, valueBytes);
    }

    protected AbstractTransaction.Result execute(EntityHistoryTx tx) {
        RocksDB temporalStore = tx.isNode() ? nodeTemporalStore : relTemporalStore;
        StaticDataManager staticDataManager = tx.isNode() ? nodeStaticManager : relStaticManager;
        long entityId = staticDataManager.getEntityId(tx.getEntity());
        int propertyId = staticDataManager.getOrCreatePropertyId(tx.getProp());
        int beginTime = tx.getBeginTime();
        int endTime = tx.getEndTime();

        TreeMap<Long, Object> valuesInRange = new TreeMap<>();
        // Long lastBeforeTime = null;
        Object lastBeforeValue = null;
        PVal.Type valueType = resolveTemporalType(tx.isNode(), tx.getProp());

        byte[] seekKey = getKeyBytes(new TemporalKey(entityId, propertyId, beginTime));
        try (RocksIterator iterator = temporalStore.newIterator()) {
            iterator.seek(seekKey);
            if (!iterator.isValid()) {
                iterator.seekToLast();
            } else {
                iterator.prev();
            }
            if (iterator.isValid()) {
                TemporalKey key = KeyFromBytes(iterator.key());
                if (key.getEntityId() == entityId && key.getPropertyId() == propertyId) {
                    long timestamp = key.getTimestamp();
                    if (timestamp < beginTime) {
                        // lastBeforeTime = timestamp;
                        lastBeforeValue = decodeTemporalValue(iterator.value(), valueType);
                    }
                }
            }

            for (iterator.seek(seekKey); iterator.isValid(); iterator.next()) {
                TemporalKey key = KeyFromBytes(iterator.key());
                if (key.getEntityId() != entityId || key.getPropertyId() != propertyId) {
                    break;
                }
                long timestamp = key.getTimestamp();
                if (timestamp > endTime) {
                    break;
                }
                Object value = decodeTemporalValue(iterator.value(), valueType);
                valuesInRange.put(timestamp, value);
            }
        }

        List<Triple<Integer, Integer, PVal>> answers = new ArrayList<>();
        TreeMap<Long, Object> timeline = new TreeMap<>();
        if (lastBeforeValue != null) {
            timeline.put((long) beginTime, lastBeforeValue);
        }
        timeline.putAll(valuesInRange);

        if (!timeline.isEmpty()) {
            List<Map.Entry<Long, Object>> entries = new ArrayList<>(timeline.entrySet());
            for (int i = 0; i < entries.size(); i++) {
                long start = Math.max(entries.get(i).getKey(), beginTime);
                long nextStart = (i + 1 < entries.size()) ? entries.get(i + 1).getKey() : ((long) endTime + 1);
                long end = Math.min(nextStart - 1, (long) endTime);
                if (start <= end) {
                    Object value = entries.get(i).getValue();
                    if (value != null) {
                        answers.add(Triple.of((int) start, (int) end, PVal.v(value)));
                    }
                }
            }
        }

        EntityHistoryTx.Result result = new EntityHistoryTx.Result();
        result.setHistory(answers);
        return result;
    }

    static class TemporalKey {
        private final long entityId;
        private final int propertyId;
        private final long timestamp;

        public TemporalKey(long entityId, int propertyId, long timestamp) {
            this.entityId = entityId;
            this.propertyId = propertyId;
            this.timestamp = timestamp;
        }

        public long getEntityId() {
            return entityId;
        }

        public int getPropertyId() {
            return propertyId;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }

    private static TemporalGraphPropertySchema schemaCache;

    private static PVal.Type resolveTemporalType(boolean isNode, String prop) {
        TemporalGraphPropertySchema schema = getSchema();
        if (schema == null) {
            return null;
        }
        return schema.getType(isNode, false, prop);
    }

    private static TemporalGraphPropertySchema getSchema() {
        if (schemaCache != null) {
            return schemaCache;
        }
        String dataset = Helper.envOrDefault("DATASET", "");
        if (dataset.isEmpty()) {
            return null;
        }
        try {
            schemaCache = TemporalGraphPropertySchema.load(dataset);
        } catch (RuntimeException e) {
            schemaCache = null;
        }
        return schemaCache;
    }

    private static Object decodeTemporalValue(byte[] valueBytes, PVal.Type type) {
        if (type == PVal.Type.INT) {
            return ByteBuffer.wrap(valueBytes).getInt();
        }
        if (type == PVal.Type.FLOAT) {
            return ByteBuffer.wrap(valueBytes).getFloat();
        }
        if (type == PVal.Type.STRING) {
            return new String(valueBytes, StandardCharsets.UTF_8);
        }
        if (valueBytes.length == Integer.BYTES) {
            return ByteBuffer.wrap(valueBytes).getInt();
        }
        return new String(valueBytes, StandardCharsets.UTF_8);
    }
}