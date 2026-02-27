package edu.buaa.batch;

import com.google.common.collect.PeekingIterator;
import edu.buaa.common.benchmark.MilestoneBuilder;
import edu.buaa.common.transaction.ImportStaticDataTx;
import edu.buaa.common.transaction.ImportTemporalDataTx;
import edu.buaa.common.utils.PFieldList;
import edu.buaa.server.store.StaticDataManager;
import edu.buaa.utils.Helper;
import org.act.temporalProperty.TemporalPropertyStore;
import org.act.temporalProperty.impl.CompressionType;
import org.act.temporalProperty.impl.Options;
import org.act.temporalProperty.impl.TemporalPropertyStoreImpl;
import org.act.temporalProperty.impl.ValueType;
import org.act.temporalProperty.query.TimeIntervalKey;
import org.act.temporalProperty.util.TemporalPropertyValueConvertor;
import org.act.temporalProperty.vo.EntityPropertyId;
import org.neo4j.graphdb.temporal.TimePoint;

import java.io.File;
import java.lang.reflect.Field;
import java.util.*;

import static org.act.temporalProperty.TemporalPropertyStoreFactory.bulkPropertyStore;

public class TPSBulkLoad extends MilestoneBuilder {
    protected final File dbDir, staticDir, temporalDir;
    private final Map<String, Integer> nTpMap = new HashMap<>();
    private final Map<String, Integer> rTpMap = new HashMap<>();
    protected Map<String, Long> nMap = new HashMap<>();
    protected Map<String, Long> rMap = new HashMap<>();
    public TPSBulkLoad() throws Exception {
        super();
        this.dbDir = new File(Helper.mustEnv("DB_PATH"));
        this.dataGen.setSectionEnable(false);
        createDirIfNotExists(dbDir);
        System.out.println("DB dir: "+dbDir);
        // 默认开snappy
        Options.setCTP(CompressionType.SNAPPY);
        this.staticDir = new File(dbDir, "static");
        this.temporalDir = new File(dbDir, "temporal");
        createDirIfNotExists(staticDir);
        createDirIfNotExists(temporalDir);
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

    private Map<String, Object> props(PFieldList data, int i) {
        Map<String, Object> p = new HashMap<>();
        for (String key : data.keys()) {
            p.put(key, data.get(key, i).getVal());
        }
        return p;
    }

    @Override
    public void importStatic() throws Exception {
        StaticDataManager nodeStaticManager = new StaticDataManager(new File(staticDir, "node"));
        StaticDataManager relStaticManager = new StaticDataManager(new File(staticDir, "relationship"));
        Iterator<ImportStaticDataTx> it = dataGen.readNetwork(8000);
        while(it.hasNext()) {
            ImportStaticDataTx tx = it.next();
            PFieldList nodesData = tx.getNodes();
            int nSize = nodesData.size();
            for (int i = 0; i < nSize; i++) {
                String sid = nodesData.get("u_sid", i).s();
                nodeStaticManager.createEntityId(sid);
                long id = nodeStaticManager.getEntityId(sid);
                nodeStaticManager.setStaticProperties(sid, props(nodesData, i));
                nMap.put(sid, id);
            }
            PFieldList relData = tx.getRels();
            int rSize = relData.size();
            for (int i = 0; i < rSize; i++) {
                String sid = relData.get("u_sid", i).s();
                relStaticManager.createEntityId(sid);
                long id = relStaticManager.getEntityId(sid);
                relStaticManager.setStaticProperties(sid, props(relData, i));
                rMap.put(sid, id);
            }
        }
        nodeStaticManager.shutdown();
        relStaticManager.shutdown();
    }

    @Override
    public void importTemporal() throws Exception {
        initEntityTp();
        Thread.interrupted();
        System.out.println("DB shutdown.");
        Thread.sleep(20);

        setMemtableSize();
        System.out.println("TPS bulkload memtable size: " + TemporalPropertyStoreImpl.MEMTABLE_SIZE + "MB");

        PeekingIterator<ImportTemporalDataTx> nodeIter = dataGen.readNodeTemporal(startTime, endTime, 10000);
        TemporalPropertyStore ntpStore = bulkPropertyStore(new File(temporalDir, "node"));
        loadTimePoint(nodeIter, ntpStore, nTpMap);
        ntpStore.shutDown();

        PeekingIterator<ImportTemporalDataTx> edgeIter = dataGen.readRelTemporal(startTime, endTime, 10000);
        TemporalPropertyStore rtpStore = bulkPropertyStore(new File(temporalDir, "relationship"));
        loadTimePoint(edgeIter, rtpStore, rTpMap);
        rtpStore.shutDown();
    }

    public void setMemtableSize() {
        try {
            Class.forName("org.act.temporalProperty.impl.TemporalPropertyStoreImpl");
            java.lang.reflect.Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);

            java.lang.reflect.Field field = TemporalPropertyStoreImpl.class.getDeclaredField("MEMTABLE_SIZE");
            Object base = unsafe.staticFieldBase(field);
            long offset = unsafe.staticFieldOffset(field);

            unsafe.putInt(base, offset, getMemTableSize());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected void initEntityTp() {
        System.out.println("init tp");
        Set<String> nodeKeys = new HashSet<>(schema.nodeTemporal.keySet());
        StaticDataManager nodeStaticManager = new StaticDataManager(new File(staticDir, "node"));
        for (String key : nodeKeys) {
            int pid = nodeStaticManager.getOrCreatePropertyId(key);
            nTpMap.put(key, pid);
        }
        Set<String> relKeys = new HashSet<>(schema.relTemporal.keySet());
        StaticDataManager relStaticManager = new StaticDataManager(new File(staticDir, "relationship"));
        for (String key : relKeys) {
            int pid = relStaticManager.getOrCreatePropertyId(key);
            rTpMap.put(key, pid);
        }
        nodeStaticManager.shutdown();
        relStaticManager.shutdown();
    }

    private long totalDataPoints = 0;

    private void loadTimePoint(PeekingIterator<ImportTemporalDataTx> it, TemporalPropertyStore tpStore, Map<String, Integer> tpMap) {
        int et = dataGen.parseTime(endTime);
        int st = dataGen.parseTime(startTime);
        while(it.hasNext()){
            ImportTemporalDataTx tx = it.next();
            totalDataPoints += (long) tx.getData().size() * tx.getData().keysWithout("u_sid", "t").size();
            this.importTemporal(tx, tpStore, tpMap);
            if(ticker.shouldTick(0)) {
                int curT = tx.getData().get("t", 0).i();
                log.debug("loading tp: {}%, total points: {}", (curT - st) * 100f / (et - st), totalDataPoints);
            }
        }
    }

    public void importTemporal(ImportTemporalDataTx tx, TemporalPropertyStore tpStore, Map<String, Integer> tpMap) {
        PFieldList data = tx.getData();
        Set<String> props = data.keysWithout("u_sid", "st", "et", "t");
        int tSize = data.size();
        TimePoint e = TimePoint.NOW;
        for (int i=0; i<tSize; i++) {
            String id = data.get("u_sid", i).s();
            TimePoint s = Helper.time(data.get("t", i).i());
            for(String prop : props){
                Object val = data.get(prop, i).getVal();
                tpStore.setProperty(
                        new TimeIntervalKey(
                                new EntityPropertyId(entityId(id, tx.isNode()), tpMap.get(prop)),
                                s, e, ValueType.fromValue(val)),
                        TemporalPropertyValueConvertor.toSlice(val));
            }
        }
    }

    protected long entityId(String u_sid, boolean isNode) {
        Map<String, Long> mp = isNode ? nMap : rMap;
        Long id = mp.get(u_sid);
        if (id == null) throw new IllegalStateException("node not found. u_sid: " + u_sid);
        return id;
    }

    @Override
    public void close() throws Exception {

    }

    protected int getMemTableSize() {
        return 64;
    }

    public static class TPSBulkLoadMem1 extends TPSBulkLoad {
        public TPSBulkLoadMem1() throws Exception {
            super();
        }

        @Override
        protected int getMemTableSize() {
            return 1;
        }
    }

    public static class TPSBulkLoadMem4 extends TPSBulkLoad {
        public TPSBulkLoadMem4() throws Exception {
            super();
        }

        @Override
        protected int getMemTableSize() {
            return 4;
        }
    }

    public static class TPSBulkLoadMem16 extends TPSBulkLoad {
        public TPSBulkLoadMem16() throws Exception {
            super();
        }

        @Override
        protected int getMemTableSize() {
            return 16;
        }
    }
}
