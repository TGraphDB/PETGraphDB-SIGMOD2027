package edu.buaa.common.benchmark;

import com.google.common.collect.PeekingIterator;
import edu.buaa.common.transaction.ImportStaticDataTx;
import edu.buaa.common.transaction.ImportTemporalDataTx;
import edu.buaa.common.transaction.UpdateTemporalDataTx;
import edu.buaa.common.utils.PFieldList;
import edu.buaa.utils.Helper;
import edu.buaa.utils.TPDataFileFormatConvertor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public abstract class NeoMilestoneBuilder extends MilestoneBuilder{

    public NeoMilestoneBuilder() throws Exception {
        super();
        String dbPath = Helper.mustEnv("DB_PATH");
        this.init(new File(dbPath));
    }

    protected abstract void init(File dbPath) throws IOException;
//    protected abstract void importStatic(ImportStaticDataTx tx) throws IOException;
//    protected abstract void importTemporal(UpdateTemporalDataTx tx) throws IOException;

//    public void importStatic() throws Exception {
//        this.init(new File(dbPath));
//        System.out.print("import static data...");
//        Iterator<ImportStaticDataTx> txStaticGen = dataGen.readNetwork(2000);
//        while(txStaticGen.hasNext()) {
//            ImportStaticDataTx tx = txStaticGen.next();
//            this.importStatic(tx);
//            if(ticker.shouldTick(0)) log.debug("node/edge count: {}/{}",tx.getNodes().size(), tx.getRels().size());
//        }
//    }

//    private volatile boolean productDone = false;
//    public void importTemporal() throws Exception {
////        TPDataFileFormatConvertor.P2IConv conv = new TPDataFileFormatConvertor.P2IConv(
////                dataGen.readNodeTemporal(startTime, endTime, 1000), Integer.MAX_VALUE, 1000, true);
////        while(conv.hasNext()) this.importTemporal(conv.next());
////        conv = new TPDataFileFormatConvertor.P2IConv(dataGen.readRelTemporal(startTime, endTime, 1000), Integer.MAX_VALUE, 1000, false);
////        while(conv.hasNext()) this.importTemporal(conv.next());
//        loadTimeInterval(conv2intAsync(dataGen.readNodeTemporal(startTime, endTime, 1000), true));
//        loadTimeInterval(conv2intAsync(dataGen.readRelTemporal(startTime, endTime, 1000), false));
//    }
//
////    public void importTemporal() throws Exception {
////        PeekingIterator<ImportTemporalDataTx> nodeIter = dataGen.readNodeTemporal(startTime, endTime, 8000);
////        PeekingIterator<ImportTemporalDataTx> edgeIter = dataGen.readRelTemporal(startTime, endTime, 8000);
////        LinkedBlockingQueue<UpdateTemporalDataTx> ni = conv2intAsync(nodeIter, true);
////        LinkedBlockingQueue<UpdateTemporalDataTx> ei = conv2intAsync(edgeIter, false);
////        try {
////            this.importTemporal(ni.take());
////            Thread.interrupted();
////            this.importTemporal(ei.take());
////            Thread.interrupted();
////            loadTimeInterval(ni);
////            loadTimeInterval(ei);
////        } catch (InterruptedException e) {
////            System.out.println("temporal interval imported.");
////        } catch (IOException e) {
////            e.printStackTrace();
////        }
////    }
//
//    private void loadTimeInterval(LinkedBlockingQueue<UpdateTemporalDataTx> pipe) throws InterruptedException, IOException {
//        productDone = false;
//        int et = dataGen.parseTime(endTime);
//        int st = dataGen.parseTime(startTime);
//        while(true){
//            UpdateTemporalDataTx tx = pipe.poll(10, TimeUnit.SECONDS);
//            if(tx==null && productDone) return;
//            if(tx==null && !productDone) continue;
//            this.importTemporal(tx);
//            boolean ignore = Thread.interrupted();
//            if(ticker.shouldTick(0)) {
//                PFieldList data = tx.getData();
//                int curT = data.get("st", 0).i();
//                log.debug("load tp: {} {}%",data.size(), (curT - st) * 100f / (et - st));
//            }
//        }
//    }
//
//    private LinkedBlockingQueue<UpdateTemporalDataTx> conv2intAsync(PeekingIterator<ImportTemporalDataTx> it, boolean isNode) {
//        TPDataFileFormatConvertor.P2IConv conv = new TPDataFileFormatConvertor.P2IConv(it, Integer.MAX_VALUE, 1000, isNode);
//        LinkedBlockingQueue<UpdateTemporalDataTx> pipe = new LinkedBlockingQueue<>(30);
//        Thread t = new Thread(() -> {
//            try {
//                while (conv.hasNext()) pipe.put(conv.next());
//                productDone = true;
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        });
//        t.setDaemon(true);
//        t.start();
//        return pipe;
//    }
}
