package edu.buaa.common.benchmark;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SimplePropertyPreFilter;
import com.aliyun.openservices.aliyun.log.producer.Producer;
import com.google.common.util.concurrent.RateLimiter;
import edu.buaa.client.MariaDBTemporalTableClient;
import edu.buaa.client.PostgreSQLTimePointClient;
import edu.buaa.common.RuntimeEnv;
import edu.buaa.common.TxResultProcessor;
import edu.buaa.common.client.AbstractSQLClient;
import edu.buaa.common.client.DBClientProxy;
import edu.buaa.common.client.DBProxy;
import edu.buaa.common.server.SQLSocketServer;
import edu.buaa.common.transaction.AbstractTransaction;
import edu.buaa.common.utils.TemporalGraphPropertySchema;
import edu.buaa.server.system.Neo4jServer1;
import edu.buaa.server.system.TGraphKernelServer;
import edu.buaa.server.system.TGraphKernelSnappyServer;
import edu.buaa.test.management.TestManager;
import edu.buaa.utils.Helper;
import edu.buaa.utils.TimeTicker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Calendar;

public class BenchmarkDebugger {
    private static final Logger log = LoggerFactory.getLogger(BenchmarkDebugger.class);

    private final int serverPort = Integer.parseInt(Helper.mustEnv("DB_PORT"));
    private final String dbName = "m_energy_tg4s_all";
    private final int maxConnCnt = Integer.parseInt(Helper.mustEnv("MAX_CONN_CNT"));
    private final int reqRate = -1;
    private final String clientClz = "edu.buaa.client.NeoTGraphExecutorClient";
    private final String serverClz = "edu.buaa.server.system.TGraphKernelSnappyServer";
    private final String dataset = "energy";
    private final String benchmarkFileName = Helper.mustEnv("BENCHMARK_FILE");
    private final boolean needResult = false;
    private final String resultFile = "";
    private final String coderVersion = Helper.codeGitVersion();
    private final long clipTimeInMilli = Long.parseLong(Helper.envOrDefault("CLIP_TIME_IN_MINUTE", "-1")) * 60 * 1000;

    public static void main(String[] args) throws Exception {
        long maxMemInGB = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getMax() / 1024 / 1024 / 1024;
        System.out.println("max heap mem: " + maxMemInGB + "GB");
        BenchmarkDebugger debugger = new BenchmarkDebugger();
        Thread serverThread = new Thread(debugger::runServer);
        serverThread.start();
        Thread.sleep(12_000);
        Thread clientThread = new Thread(() -> {
            try {
                debugger.runClient();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        clientThread.start();
        if (debugger.clipTimeInMilli > 0) {
            Thread daemon = new Thread(() -> {
                System.out.println("killer started.");
                try {
                    Thread.sleep(debugger.clipTimeInMilli);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                try {
                    TGraphKernelServer.getDbKernelProxy().shutdown();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                System.exit(-1);
            });
            daemon.setDaemon(true);
            daemon.start();
        }
        TGraphKernelServer.getStatisticSaver().starting();
    }

    private void runServer(){
        String db = dbName.split("_")[2];
        String[] args = new String[]{};
        switch (db.toUpperCase()){
            case "TGK":
                TGraphKernelServer.main(args);
                break;
            case "TGS":
            case "TG4S":
                TGraphKernelSnappyServer.main(args);
                break;
            case "N1":
                Neo4jServer1.main(args);
                break;
        }
    }


    private void runClient() throws Exception {
        DBProxy client = null;
        TxResultProcessor post = null;
        BenchmarkReader reader = null;
        TimeTicker ticker = new TimeTicker(30, 2);
        File benchmarkFile = new File(benchmarkFileName);
        String testName = Helper.getTestName(benchmarkFile.getParentFile().getName(), dbName);
        int jenkinsId = Integer.parseInt(Helper.envOrDefault("JENKINS_ID", "0"));
        Helper.trace().setAppVersion(testName);
        System.out.println(testName);
        try {
            client = initClient(clientClz, "localhost", serverPort, dbName, TemporalGraphPropertySchema.load(dataset), maxConnCnt);
            client.testServerClientCompatibility();
            post = new TxResultProcessor(testName, coderVersion);
            if(needResult) {
                post.setResult(new File(resultFile));
            }
            post.setVerifyResult(false);
            reader = new BenchmarkReader(benchmarkFile, true);
            SimplePropertyPreFilter pf = new SimplePropertyPreFilter();
            pf.getExcludes().addAll(Arrays.asList("txType", "metrics", "result", "nodes", "rels",
                    "crosses", "roads", "data" // for traffic dataset
            ));
            final RateLimiter rateLimiter = RateLimiter.create(reqRate>0 ? reqRate : 10_0000_0000); // Unit: permits per second.

            while (reader.hasNext()) {
                if(reqRate>0) rateLimiter.acquire();
                AbstractTransaction tx = reader.next();
//              post.processSync(client.execute(tx), tx);
                post.process(client.execute(tx), tx);
                if(ticker.shouldTick(0)) log.debug(JSON.toJSONString(tx, pf));
            }
        }catch (Exception e) {
            log.error("ERROR sending request", e);
            Helper.trace().notifyError(e);
            throw new RuntimeException(e);
        }finally {
            if(reader!=null) reader.close();
            if(client!=null) client.close();
            if(post!=null) post.close();
        }
    }

    public DBClientProxy initClient(String clsClient, String dbHost, int port, String dbName, TemporalGraphPropertySchema schema, int maxConnCnt) throws Exception {
        Class<?> cls = Class.forName(clsClient);
        Object obj = cls.newInstance();
        DBClientProxy client = (DBClientProxy) obj;
        log.debug("connect to {}:{} with {} connections", dbHost, port, maxConnCnt);
        return client.init(dbHost, port, dbName, maxConnCnt, schema);
    }

    public static String getTestName(String dbType){
        Calendar c = Calendar.getInstance();
        return "M_"+c.get(Calendar.YEAR)+"."+(c.get(Calendar.MONTH)+1)+"."+c.get(Calendar.DAY_OF_MONTH)+"_"+
                c.get(Calendar.HOUR_OF_DAY)+":"+c.get(Calendar.MINUTE)+dbType.toLowerCase();
    }

}
