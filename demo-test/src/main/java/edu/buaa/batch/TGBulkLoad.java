package edu.buaa.batch;

import com.google.common.collect.PeekingIterator;
import edu.buaa.common.benchmark.MilestoneBuilder;
import edu.buaa.common.transaction.ImportStaticDataTx;
import edu.buaa.common.transaction.ImportTemporalDataTx;
import edu.buaa.common.utils.PFieldList;
import edu.buaa.common.utils.PVal;
import edu.buaa.utils.Helper;
import org.act.temporalProperty.TemporalPropertyStore;
import org.act.temporalProperty.impl.CompressionType;
import org.act.temporalProperty.impl.Options;
import org.act.temporalProperty.impl.ValueType;
import org.act.temporalProperty.meta.ValueContentType;
import org.act.temporalProperty.query.TimeIntervalKey;
import org.act.temporalProperty.util.TemporalPropertyValueConvertor;
import org.act.temporalProperty.vo.EntityPropertyId;
import org.neo4j.batchinsert.BatchInserter;
import org.neo4j.batchinsert.BatchInserters;
import org.neo4j.configuration.Config;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.schema.Schema;
import org.neo4j.graphdb.temporal.TimePoint;
import org.neo4j.io.layout.DatabaseLayout;
import org.neo4j.io.layout.Neo4jLayout;
import org.neo4j.kernel.api.KernelTransaction;
import org.neo4j.kernel.impl.coreapi.InternalTransaction;

import java.io.File;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static edu.buaa.server.system.TGraphKernelServer.LABEL;
import static edu.buaa.server.system.TGraphKernelServer.RELATIONSHIP_TYPE;
import static org.act.temporalProperty.TemporalPropertyStoreFactory.bulkPropertyStore;

/**
 * 只是从dev-sjh的代码抄过来的，没测试过，理解也有限，但可以编译和运行
 */
public class TGBulkLoad extends MilestoneBuilder{
    protected final File dbDir;
    private DatabaseLayout databaseLayout = null;
    private final Map<String, Integer> tpMap = new HashMap<>();
    protected Map<String, Long> nMap = new HashMap<>();
    protected Map<String, Long> rMap = new HashMap<>();

    public TGBulkLoad() throws Exception {
        super();
        this.dbDir = new File(Helper.mustEnv("DB_PATH"));
        System.out.println("DB dir: "+dbDir);
        Options.setGlobalCompressionType(CompressionType.NONE);
        dataGen.setSectionEnable(false);
    }

    @Override
    public void close() throws Exception { }

    private DatabaseLayout getDefaultDatabaseLayout() {
        if (databaseLayout == null) {
            Config.Builder configBuilder = Config.newBuilder();
            configBuilder.set(GraphDatabaseSettings.neo4j_home, dbDir.toPath().toAbsolutePath());
            Neo4jLayout layout = Neo4jLayout.of(configBuilder.build());
            databaseLayout = layout.databaseLayout("neo4j");
        }
        return databaseLayout;
    }

    @Override
    public void importStatic() throws Exception{
        BatchInserter bulkDB = BatchInserters.inserter(getDefaultDatabaseLayout());
        Iterator<ImportStaticDataTx> it = dataGen.readNetwork(8000);
        while(it.hasNext()) {
            ImportStaticDataTx tx = it.next();
            PFieldList nodesData = tx.getNodes();
            int nSize = nodesData.size();
            for (int i = 0; i < nSize; i++) {
                String sid = nodesData.get("u_sid", i).s();
                long id = bulkDB.createNode(props(nodesData, i), LABEL);
                nMap.put(sid, id);
            }
            PFieldList relData = tx.getRels();
            int rSize = relData.size();
            for (int i = 0; i < rSize; i++) {
                String sid = relData.get("u_sid", i).s();
                String fromId = relData.get("r_from", i).s();
                String toId = relData.get("r_to", i).s();
                Long s = nMap.get(fromId);
                Long e = nMap.get(toId);
                long id = bulkDB.createRelationship(s, e, RELATIONSHIP_TYPE, props(relData, i));
                rMap.put(sid, id);
            }
        }
        bulkDB.shutdown();
        DatabaseManagementService dbms = new DatabaseManagementServiceBuilder(dbDir.toPath()).build();
        GraphDatabaseService db = dbms.database("neo4j");
        try (Transaction tx = db.beginTx()) {
            tx.schema().indexFor(LABEL).on("u_sid").create();
            tx.schema().indexFor(RELATIONSHIP_TYPE).on("u_sid").create();
            tx.commit();
        }
        dbms.shutdown();
    }

    private Map<String, Object> props(PFieldList data, int i) {
        Map<String, Object> p = new HashMap<>();
        for (String key : data.keys()) {
            p.put(key, data.get(key, i).getVal());
        }
        return p;
    }

    @Override
    public void importTemporal() throws Exception {
        initEntityTp();
        Thread.interrupted();
        System.out.println("DB shutdown.");
        Thread.sleep(20);

        File dir = getDefaultDatabaseLayout().databaseDirectory().toFile();
        PeekingIterator<ImportTemporalDataTx> nodeIter = dataGen.readNodeTemporal(startTime, endTime, 10000);
        TemporalPropertyStore ntpStore = bulkPropertyStore(
                new File(dir, "temporal.node.properties"), new Options().memTableSize(256).fBufferSize(128));
        loadTimePoint(nodeIter, ntpStore);
        ntpStore.shutDown();

        PeekingIterator<ImportTemporalDataTx> edgeIter = dataGen.readRelTemporal(startTime, endTime, 10000);
        TemporalPropertyStore rtpStore = bulkPropertyStore(
                new File(dir, "temporal.relationship.properties"), new Options().memTableSize(256).fBufferSize(128));
        loadTimePoint(edgeIter, rtpStore);
        rtpStore.shutDown();
    }

    protected void initEntityTp() {
        System.out.println("init tp");
        DatabaseManagementService dbms = new DatabaseManagementServiceBuilder(dbDir.toPath()).build();
        GraphDatabaseService dbService = dbms.database("neo4j");
        LinkedList<Long> entities = new LinkedList<>();
        try (Transaction transaction = dbService.beginTx()) {
            for (Node n : transaction.getAllNodes()) {
                entities.add(n.getId());
            }
            transaction.commit();
        }
        while(!entities.isEmpty()){
            try (Transaction transaction = dbService.beginTx()) {
                for(int i=0; i<10000 && !entities.isEmpty();i++) {
                    long eid = entities.poll();
                    schema.nodeTemporal.forEach((k, v) -> initEntityTp(true, transaction, eid, k, v));
                }
                transaction.commit();
            }
        }
        try (Transaction transaction = dbService.beginTx()) {
            for (Relationship rel : transaction.getAllRelationships()) {
                entities.add(rel.getId());
            }
            transaction.commit();
        }
        while(!entities.isEmpty()){
            try (Transaction transaction = dbService.beginTx()) {
                for (int i = 0; i < 10000 && !entities.isEmpty(); i++) {
                    long eid = entities.poll();
                    schema.relTemporal.forEach((k, v) -> initEntityTp(false, transaction, eid, k, v));
                }
                transaction.commit();
            }
        }
        Set<String> keys = new HashSet<>();
        keys.addAll(schema.nodeTemporal.keySet());
        keys.addAll(schema.relTemporal.keySet());
        try (Transaction transaction = dbService.beginTx()) {
            KernelTransaction kernelTransaction = ((InternalTransaction) transaction).kernelTransaction();
            for(String prop: keys){
                int pid = kernelTransaction.tokenRead().propertyKey(prop);
                tpMap.put(prop, pid);
            }
            transaction.commit();
        }
        System.out.println("await index...");
        awaitIndex(dbService);
        dbms.shutdown();
    }

    protected void initEntityTp(boolean isNode, Transaction tx, long eid, String prop, PVal.Type type){
        if(isNode){
            tx.getNodeById(eid).setProperty(prop, tPMeta(type));
        }else{
            tx.getRelationshipById(eid).setProperty(prop, tPMeta(type));
        }
    }

    protected String tPMeta(PVal.Type type) {
        ValueContentType valueType = type== PVal.Type.INT?ValueContentType.INT:ValueContentType.FLOAT;
        return "tp." + valueType.name().toLowerCase() + ":unknown";
    }

    private void loadTimePoint(PeekingIterator<ImportTemporalDataTx> it, TemporalPropertyStore tpStore) {
        int et = dataGen.parseTime(endTime);
        int st = dataGen.parseTime(startTime);
        while(it.hasNext()){
            ImportTemporalDataTx tx = it.next();
            this.importTemporal(tx, tpStore);
            if(ticker.shouldTick(0)) {
                int curT = tx.getData().get("t", 0).i();
                log.debug("load tp: {}%", (curT - st) * 100f / (et - st));
            }
        }
    }

    public void importTemporal(ImportTemporalDataTx tx, TemporalPropertyStore tpStore) {
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

    public void awaitIndex(GraphDatabaseService db){
        boolean shouldWait = true;
        int cnt = 0;
        while(shouldWait) try(Transaction tx = db.beginTx()){
            Schema schema = tx.schema();
            schema.awaitIndexesOnline(10, TimeUnit.SECONDS);
            shouldWait = false;
        } catch (IllegalArgumentException | IllegalStateException e) {
            cnt++;
            System.out.println("indexes not ready after "+cnt*10+" seconds.");
        }
        System.out.println("indexes all online.");
    }
}
