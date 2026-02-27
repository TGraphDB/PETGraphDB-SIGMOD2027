package edu.buaa.batch;

import com.google.common.collect.PeekingIterator;
import com.google.common.util.concurrent.ListenableFuture;
import edu.buaa.client.AionClient;
import edu.buaa.common.benchmark.MilestoneBuilder;
import edu.buaa.common.client.DBProxy;
import edu.buaa.common.transaction.ImportStaticDataTx;
import edu.buaa.common.transaction.ImportTemporalDataTx;
import edu.buaa.server.store.RocksDBServer1;
import edu.buaa.utils.Helper;

import java.io.File;
import java.util.Iterator;

public class AionBulkLoad extends MilestoneBuilder {
    private final File dbDir;
    private final AionClient client;
    protected final String serverHost = Helper.mustEnv("DB_HOST"); // hostname of server.
    protected final int serverPort = Integer.parseInt(Helper.mustEnv("DB_PORT")); // hostname of server.
    protected final String dbName = Helper.mustEnv("MILESTONE_NAME");
    private final int STATIC_BATCH_SIZE = 8000;
    private final int BATCH_SIZE = 100;

    public AionBulkLoad() throws Exception {
        super();
        this.dbDir = new File(Helper.mustEnv("DB_PATH"));
        this.client = new AionClient();
        this.client.init(serverHost, serverPort, dbName, 1, schema);
        System.out.println("DB dir: " + dbDir);
        this.dataGen.setSectionEnable(false);
    }

    @Override
    public void importStatic() throws Exception {
        client.createNodeIndex();
        client.createEdgeIndex();
        client.showIndex();
        Iterator<ImportStaticDataTx> it = dataGen.readNetwork(STATIC_BATCH_SIZE);
        while (it.hasNext()) {
            client.execute(it.next());
        }
        System.out.println("static import done");

    }

    @Override
    public void importTemporal() throws Exception {
        int et = dataGen.parseTime(endTime);
        int st = dataGen.parseTime(startTime);

        PeekingIterator<ImportTemporalDataTx> nodeIter = dataGen.readNodeTemporal(startTime, endTime, BATCH_SIZE);
        while (nodeIter.hasNext()) {
            ImportTemporalDataTx tx = nodeIter.next();
            client.execute(tx);
            if (ticker.shouldTick(0)) {
                int curT = tx.getData().get("t", 0).i();
                log.debug("load node temporal: {}%", (curT - st) * 100f / (et - st));
            }
        }

        PeekingIterator<ImportTemporalDataTx> relIter = dataGen.readRelTemporal(startTime, endTime, BATCH_SIZE);
        while (relIter.hasNext()) {
            ImportTemporalDataTx tx = relIter.next();
            client.execute(tx);
            if (ticker.shouldTick(0)) {
                int curT = tx.getData().get("t", 0).i();
                log.debug("load rel temporal: {}%", (curT - st) * 100f / (et - st));
            }
        }
    }

    @Override
    public void close() throws Exception {
        client.close();
        client.cutconnect();
    }
}
