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
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.Entity;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.temporal.TimePoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.locks.LockSupport;

/**
 * 用于内部消融实验，测试多级细粒度锁机制的极限性能。
 */
public class TGLockingServer implements DBSocketServer.DBKernelProxy {
    public static final Label LABEL = Label.label("test");
    public static final RelationshipType RELATIONSHIP_TYPE = RelationshipType.withName("test");
    private final int maxThreadCnt;
    private final AtomicLongArray bucket;

    protected DatabaseManagementService dbms;

    protected static Logger log;

    protected static boolean bigLock = false;


    public static void main(String[] args) {
        Options.setGlobalCompressionType(CompressionType.SNAPPY);
        log = LoggerFactory.getLogger(TGLockingServer.class);
        DBSocketServer server = new DBSocketServer(dbDir(), new TGLockingServer(), Integer.parseInt(Helper.mustEnv("DB_PORT")), true);
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

    TGLockingServer(){
        this.maxThreadCnt = Integer.parseInt(Helper.envOrDefault("MAX_CONNECTION_CNT", "16"));
        this.bucket = new AtomicLongArray(maxThreadCnt);
        for(int i=0; i<maxThreadCnt; i++) this.bucket.set(i, -1);
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
        try {
            AbstractTransaction tx = JSON.parseObject(line, AbstractTransaction.class);
            switch (tx.getTxType()) {
                case tx_import_temporal_data:
                    return execute((ImportTemporalDataTx) tx);
                case tx_query_entity_history:
                    return execute((EntityHistoryTx) tx);
                case tx_update_temporal_data:
                    return execute((UpdateTemporalDataTx) tx);
                default:
                    throw new UnsupportedOperationException();
            }
        }catch (Exception e){
            int end = Math.min(60, line.length());
            log.error("ERROR processing TX: "+line.substring(0, end), e);
            throw new TransactionFailedException(e);
        }
    }

    private Entity getEntity(int section, boolean isNode, String id, Transaction transaction){
        int b = section == -1 ? 0 : section % this.maxThreadCnt;
        long entityId = bucket.get(b);
        Entity entity;
        if(entityId==-1) {
            if (isNode) {
                entity = transaction.findNode(LABEL, "u_sid", id);
            } else {
                entity = transaction.findRelationship(RELATIONSHIP_TYPE, "u_sid", id);
            }
            boolean updated = bucket.compareAndSet(b, -1, entity.getId());
            if(updated) System.out.println("[bucket "+b+"] update entity id "+entity.getId() + " with section "+ section);
        }else{
            entity = isNode ? transaction.getNodeById(entityId) : transaction.getRelationshipById(entityId);
        }
        return entity;
    }


    protected AbstractTransaction.Result execute(EntityHistoryTx tx) {
        GraphDatabaseService db = dbms.database("neo4j");
        TimePoint begin = Helper.time(tx.getBeginTime()), end = Helper.time(tx.getEndTime());

        List<Triple<Integer, Integer, PVal>> answers = new ArrayList<>();
        try (Transaction transaction = db.beginTx()) {
            Entity entity = getEntity(tx.getSection(), tx.isNode(), tx.getEntity(), transaction);
            if (bigLock) {
                transaction.acquireReadLock(entity);
            } else {
                entity.acquireTemporalSharedLock(tx.getProp(), begin, end);
            }
            LockSupport.parkNanos(100_000_000);
//            entity.getTemporalProperty(tx.getProp(), begin, end, new TimeIntervalRangeQuery(begin, end){
//                @Override
//                public void onEntry(TimePointL beginTime, TimePointL endTime, Object val) {
//                    if(val!=null) {
//                        answers.add(Triple.of(beginTime.valInt(), endTime.valInt(), PVal.v(val)));
//                    }
//                }
//            });
            EntityHistoryTx.Result result = new EntityHistoryTx.Result();
            result.setHistory(answers);
            return result;
        }
    }

    protected AbstractTransaction.Result execute(ImportTemporalDataTx tx) {
        GraphDatabaseService db = dbms.database("neo4j");
        try(Transaction transaction = db.beginTx()) {
            PFieldList data = tx.getData();
            Set<String> props = data.keysWithout("u_sid", "t");
            int tSize = data.size();
            for (int i=0; i<tSize; i++) {
                try {
                    String id = data.get("u_sid", i).s();
                    TimePoint time = Helper.time(data.get("t", i).i());
                    Entity entity = getEntity(tx.getSection(), tx.isNode(), id, transaction);
                    for (String prop : props) {
                        if (bigLock) {
                            transaction.acquireWriteLock(entity);
                        } else {
                            entity.acquireTemporalExclusiveLock(prop, time, TimePoint.NOW);
                        }
//                        entity.setTemporalProperty(prop, time, data.get(prop, i).getVal());
                    }
                }
                catch (IllegalStateException e) {
                    if (!e.getMessage().contains("not found")) throw e;
                }
            }
            LockSupport.parkNanos(100_000_000);
//            transaction.commit();
        }
        return new AbstractTransaction.Result();
    }

    protected AbstractTransaction.Result execute(UpdateTemporalDataTx tx) {
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
                    Entity entity = getEntity(tx.getSection(), tx.isNode(), id, transaction);
                    for (String prop : props) {
                        if (bigLock) {
                            transaction.acquireWriteLock(entity);
                        } else {
                            entity.acquireTemporalExclusiveLock(prop, start, end);
                        }
//                        entity.setTemporalProperty(prop, start, end, data.get(prop, i).getVal());
                    }
                }
                catch (IllegalStateException e) {
                    if (!e.getMessage().contains("not found")) throw e;
                }
            }
            LockSupport.parkNanos(100_000_000);
//            transaction.commit();
        }
        return new AbstractTransaction.Result();
    }

}