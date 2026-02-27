package edu.buaa.batch;

import com.google.common.collect.PeekingIterator;
import edu.buaa.common.benchmark.MilestoneBuilder;
import edu.buaa.common.transaction.ImportStaticDataTx;
import edu.buaa.common.transaction.ImportTemporalDataTx;

import java.util.Iterator;

public class NOPBulkLoad extends MilestoneBuilder {

    private static final int STATIC_BATCH_SIZE = 8000;
    private static final int TEMPORAL_BATCH_SIZE = 1;

    public NOPBulkLoad() throws Exception {
    }

    @Override
    public void importStatic() throws Exception {
        Iterator<ImportStaticDataTx> it = dataGen.readNetwork(STATIC_BATCH_SIZE);
        while (it.hasNext()) {
            ImportStaticDataTx tx = it.next();
            try {
                System.out.println("NODES: " +
                        tx.getNodes().printByOrder(10, 30, "u_sid"));
                System.out.println("EDGES: " +
                        tx.getRels().printByOrder(10, 30, "u_sid"));
            }catch (RuntimeException e){
                e.printStackTrace();

            }
            System.out.println("===================================================================");
        }
    }

    @Override
    public void importTemporal() throws Exception {
        int et = dataGen.parseTime(endTime);
        int st = dataGen.parseTime(startTime);

        PeekingIterator<ImportTemporalDataTx> nodeIter = dataGen.readNodeTemporal(startTime, endTime, TEMPORAL_BATCH_SIZE);
        while (nodeIter.hasNext()) {
            ImportTemporalDataTx tx = nodeIter.next();
            System.out.println("NODES TEMPORAL DATA: "+
                    tx.getData().printByOrder(10, 30, "t", "u_sid"));
            if (ticker.shouldTick(0)) {
                int curT = tx.getData().get("t", 0).i();
                log.debug("load node temporal: {}%", (curT - st) * 100f / (et - st));
            }
        }

        PeekingIterator<ImportTemporalDataTx> relIter = dataGen.readRelTemporal(startTime, endTime, TEMPORAL_BATCH_SIZE);
        while (relIter.hasNext()) {
            ImportTemporalDataTx tx = relIter.next();
            System.out.println("EDGES TEMPORAL DATA: "+
                    tx.getData().printByOrder(10, 30, "t", "u_sid"));
            if (ticker.shouldTick(0)) {
                int curT = tx.getData().get("t", 0).i();
                log.debug("load rel temporal: {}%", (curT - st) * 100f / (et - st));
            }
        }
    }

    @Override
    public void close() throws Exception {

    }
}
