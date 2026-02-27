package edu.buaa.batch;

import com.google.common.collect.PeekingIterator;
import edu.buaa.common.benchmark.MilestoneBuilder;
import edu.buaa.common.transaction.ImportStaticDataTx;
import edu.buaa.common.transaction.ImportTemporalDataTx;
import edu.buaa.server.store.RocksDBServer;
import edu.buaa.server.store.RocksDBServer1;
import edu.buaa.utils.Helper;

import java.io.File;
import java.util.Iterator;

public class RocksDBBulkLoad extends MilestoneBuilder {
    private final File dbDir;
    private final RocksDBServer server;

    public RocksDBBulkLoad() throws Exception {
        super();
        this.dbDir = new File(Helper.mustEnv("DB_PATH"));
        this.server = getRocksDBServer();
        System.out.println("DB dir: " + dbDir);
        this.dataGen.setSectionEnable(false);
    }

    @Override
    public void importStatic() throws Exception {
        server.start(dbDir);
        Iterator<ImportStaticDataTx> it = dataGen.readNetwork(8000);
        while (it.hasNext()) {
            server.execute(it.next());
        }
    }

    @Override
    public void importTemporal() throws Exception {
        int et = dataGen.parseTime(endTime);
        int st = dataGen.parseTime(startTime);
        long totalDataPoints = 0;

        PeekingIterator<ImportTemporalDataTx> nodeIter = dataGen.readNodeTemporal(startTime, endTime, 10000);
        while (nodeIter.hasNext()) {
            ImportTemporalDataTx tx = nodeIter.next();
            totalDataPoints += (long) tx.getData().size() * tx.getData().keysWithout("u_sid", "t").size();
            server.execute(tx);
            if (ticker.shouldTick(0)) {
                int curT = tx.getData().get("t", 0).i();
                log.debug("loading node temporal: {}%, total points: {}", (curT - st) * 100f / (et - st), totalDataPoints);
            }
        }

        PeekingIterator<ImportTemporalDataTx> relIter = dataGen.readRelTemporal(startTime, endTime, 10000);
        while (relIter.hasNext()) {
            ImportTemporalDataTx tx = relIter.next();
            totalDataPoints += (long) tx.getData().size() * tx.getData().keysWithout("u_sid", "t").size();
            server.execute(tx);
            if (ticker.shouldTick(0)) {
                int curT = tx.getData().get("t", 0).i();
                log.debug("loading rel temporal: {}%, total points: {}", (curT - st) * 100f / (et - st), totalDataPoints);
            }
        }
    }

    @Override
    public void close() throws Exception {
        server.shutdown();
    }

    protected RocksDBServer getRocksDBServer() {
        return new RocksDBServer1();
    }

    public static class RocksDBBulkLoadMem1 extends RocksDBBulkLoad {

        public RocksDBBulkLoadMem1() throws Exception {
            super();
        }

        @Override
        protected RocksDBServer getRocksDBServer() {
            return new RocksDBServer1.RocksDBServer1Mem1();
        }
    }

    public static class RocksDBBulkLoadMem4 extends RocksDBBulkLoad {

        public RocksDBBulkLoadMem4() throws Exception {
            super();
        }

        @Override
        protected RocksDBServer getRocksDBServer() {
            return new RocksDBServer1.RocksDBServer1Mem4();
        }
    }

    public static class RocksDBBulkLoadMem16 extends RocksDBBulkLoad {

        public RocksDBBulkLoadMem16() throws Exception {
            super();
        }

        @Override
        protected RocksDBServer getRocksDBServer() {
            return new RocksDBServer1.RocksDBServer1Mem16();
        }
    }
}
