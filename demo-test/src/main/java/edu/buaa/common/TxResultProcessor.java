package edu.buaa.common;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SimplePropertyPreFilter;
import com.aliyun.openservices.aliyun.log.producer.Producer;
import com.aliyun.openservices.aliyun.log.producer.errors.ProducerException;
import com.aliyun.openservices.log.common.LogItem;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import edu.buaa.common.benchmark.BenchmarkWriter;
import edu.buaa.common.client.DBProxy;
import edu.buaa.common.transaction.AbstractTransaction;
import edu.buaa.common.transaction.AbstractTransaction.Metrics;
import edu.buaa.test.management.TestManager;
import edu.buaa.utils.Helper;
import edu.buaa.utils.TimeTicker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.*;

public class TxResultProcessor {
    private static final Logger log = LoggerFactory.getLogger(TxResultProcessor.class);
    private final ExecutorService service;
    private final String testName;
    private final String clientVersion;
    private final SimplePropertyPreFilter pf;
    private final ThreadPoolExecutor exe;
    private final TimeTicker ticker;

    private Producer logger;
    private boolean verifyResult;
    private BenchmarkWriter writer;
    private TestManager testDB;


    public TxResultProcessor(String testName, String clientVersion){
        this.testName = testName;
        this.clientVersion = clientVersion;
        pf = new SimplePropertyPreFilter();
        pf.getExcludes().addAll(Arrays.asList("txType", "metrics", "result",
                "crosses", "roads", "data" // for traffic dataset
        ));
        this.exe = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
        this.exe.prestartAllCoreThreads();
        this.service = MoreExecutors.listeningDecorator(this.exe);
        this.ticker = new TimeTicker(60, 2);
    }

    public void setLogger(Producer logger) {
        this.logger = logger;
    }

    public void setVerifyResult(boolean verifyResult) {
        this.verifyResult = verifyResult;
    }

    public void setResult(File resultFile) throws IOException {
        this.writer = new BenchmarkWriter(resultFile, false);
    }

    public void setTestDB(TestManager testDB) {
        this.testDB = testDB;
    }

    public void process(ListenableFuture<DBProxy.ServerResponse> result, AbstractTransaction tx) {
        Futures.addCallback( result, new PostProcessing(tx), this.service);
    }

    public DBProxy.ServerResponse processSync(ListenableFuture<DBProxy.ServerResponse> result, AbstractTransaction tx) {
        try {
            DBProxy.ServerResponse r = result.get();
            new PostProcessing(tx).onSuccess(r);
            return r;
        } catch (InterruptedException | ExecutionException e) {
            Helper.trace().notifyError(e);
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private void logMetrics(AbstractTransaction tx, DBProxy.ServerResponse response) throws ProducerException, InterruptedException, ExecutionException {
        JSONObject mObj = mergeMetrics(response.getMetrics(), tx.getMetrics());
        LogItem log = new LogItem();
        log.PushBack("type", tx.getTxType().name());
        log.PushBack("params", JSON.toJSONString(tx, pf));
        add2LogItem(log, mObj);
        if(logger != null) logger.send("tgraph-demo-test", "tgraph-log", testName, clientVersion, log);
    }

    private JSONObject mergeMetrics(Metrics mFromClient, Metrics mFromTx) {
        if(mFromTx!=null){
            if(mFromTx.getReqSize()>0){
                mFromClient.setReqSize(mFromTx.getReqSize());
            }
            if(mFromTx.getReturnSize()>0){
                mFromClient.setReqSize(mFromTx.getReturnSize());
            }
        }
        return (JSONObject) JSON.toJSON(mFromClient);
    }

    private void add2LogItem(LogItem log, JSONObject metrics) {
        for(Map.Entry<String, Object> e : metrics.entrySet()){
            if(e.getValue() instanceof JSONObject){
                JSONObject v = (JSONObject) e.getValue();
                for(Map.Entry<String, Object> ee : v.entrySet()){
                    log.PushBack(e.getKey()+"_"+ee.getKey(), ee.getValue().toString());
                }
            }else {
                log.PushBack(e.getKey(), String.valueOf(e.getValue()));
            }
        }
    }

    public void close() throws IOException, InterruptedException{
        service.shutdown();
        int remains = exe.getQueue().size();
        System.out.printf("Post processor closed but will carryout remaining %s tasks%n", remains);
        long mark = System.currentTimeMillis();
        while (!service.awaitTermination(10, TimeUnit.SECONDS)) {
            long completeCnt = exe.getCompletedTaskCount();
            remains = exe.getQueue().size();
            System.out.printf("%s/%s task completed.%n", completeCnt, completeCnt + remains);
        }
        System.out.printf("Post processor exit after wait %s seconds. send %s lines.", (System.currentTimeMillis()-mark)/1000, exe.getCompletedTaskCount());
        if(writer!=null) writer.close();
//        if(testDB!=null) testDB.close();
    }

    public void awaitDone(int timeout, TimeUnit timeUnit) throws IOException, InterruptedException{
        close();
    }


    private class PostProcessing implements FutureCallback<DBProxy.ServerResponse>{
        AbstractTransaction tx;
        PostProcessing(AbstractTransaction tx){
            this.tx = tx;
        }

        @Override
        public void onSuccess(DBProxy.ServerResponse result) {
            if(result==null) return;
            try {
                if(logger!=null) logMetrics(tx, result);
                if(verifyResult) tx.validateResult(result.getResult());
                if(writer!=null) {
                    tx.setResult(result.getResult());
//                    log.debug("result: {}", result.getResult());
                    writer.write(tx);
                }
//                if(testDB!=null){
//                    testDB.addCase(tx, writer!=null ? result.getResult() : null, result.getMetrics());
//                }
                if(ticker.shouldTick(0)){
                    long completeCnt = exe.getCompletedTaskCount();
                    int remains = exe.getQueue().size();
                    log.info("Post-process: complete {}/{} task.", completeCnt, completeCnt + remains);
                }
            } catch (ProducerException | InterruptedException | IOException | ExecutionException e) {
                log.error(JSON.toJSONString(tx, pf), e);
                Helper.trace().notifyError(e);
            }
        }

        @Override
        public void onFailure(Throwable t) {
            t.printStackTrace();
            Helper.trace().notifyError(t);
        }
    }

}
