package edu.buaa.common.benchmark;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SimplePropertyPreFilter;
import com.aliyun.openservices.aliyun.log.producer.Producer;

import com.google.common.util.concurrent.RateLimiter;
import edu.buaa.common.RuntimeEnv;
import edu.buaa.common.TxResultProcessor;
import edu.buaa.common.client.DBClientProxy;
import edu.buaa.common.client.DBProxy;
import edu.buaa.common.transaction.AbstractTransaction;
import edu.buaa.common.utils.TemporalGraphPropertySchema;
import edu.buaa.test.management.TestManager;
import edu.buaa.utils.Helper;
import edu.buaa.utils.TimeTicker;
import org.apache.commons.lang3.tuple.Triple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.LockSupport;

public class BenchmarkRunner {
    private static final Logger log = LoggerFactory.getLogger(BenchmarkRunner.class);

    private final String serverHost = Helper.mustEnv("DB_HOST");
    private final int serverPort = Integer.parseInt(Helper.mustEnv("DB_PORT"));
    private final String dbName = Helper.mustEnv("DB_NAME");
    private final int maxConnCnt = Integer.parseInt(Helper.mustEnv("MAX_CONNECTION_CNT"));
    private final int reqRate = Integer.parseInt(Helper.envOrDefault("REQ_RATE", "-1"));
    private final String clientClz = Helper.mustEnv("CLASS_CLIENT");
    private final String dataset = Helper.mustEnv("DATASET");
    private final String benchmarkFileName = Helper.mustEnv("BENCHMARK_FULL_PATH");
    private final boolean needResult = Boolean.parseBoolean(Helper.mustEnv("NEED_RESULT"));
    private final String resultFile = needResult ? Helper.mustEnv("BENCHMARK_RESULT_PATH") : "";
    private final String coderVersion = Helper.codeGitVersion();

    public static void main(String[] args) throws Exception {
        new BenchmarkRunner().run();
    }

    private void run() throws Exception {
        long beginTime = System.currentTimeMillis();
        Producer logger = Helper.getLogger();
        DBProxy client = null;
        TxResultProcessor post = null;
        BenchmarkReader reader = null;
        TimeTicker ticker = new TimeTicker(30, 2);
        File benchmarkFile = new File(benchmarkFileName);
        int jenkinsId = Integer.parseInt(Helper.envOrDefault("JENKINS_ID", "0"));
        String testName = Helper.getTestName(jenkinsId, benchmarkFile.getParentFile().getName(), dbName, maxConnCnt, reqRate);
        String ENV_DEVICE = Helper.envOrDefault("DEVICE", RuntimeEnv.hostName());
        Helper.trace().setAppVersion(testName);
        System.out.println(testName);
//        TestManager testM = new TestManager(dbName, coderVersion, ENV_DEVICE, ENV_JENKINS_ID, ENV_JENKINS_ID);
        try {
            client = initClient(clientClz, serverHost, serverPort, dbName, TemporalGraphPropertySchema.load(dataset), maxConnCnt);
            client.testServerClientCompatibility();
            post = new TxResultProcessor(testName, coderVersion, jenkinsId);
            post.setLogger(logger);
//            post.setTestDB(testM);
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
//                if(needResult){
//                    post.processSync(client.execute(tx), tx);
//                }else{
                    post.process(client.execute(tx), tx);
//                }
                if(ticker.shouldTick(0)) log.debug(JSON.toJSONString(tx, pf));
            }
//            System.exit(0);
//        } catch (ConnectException e){
//            System.exit(3);
        }catch (Exception e) {
            log.error("ERROR sending request", e);
            Helper.trace().notifyError(e);
            throw new RuntimeException(e);
        }finally {
            if(reader!=null) reader.close();
            if(client!=null) client.close();
            if(post!=null) post.close();
//            testM.updateDuration("Test", beginTime);
//            testM.close();
            logger.close();
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
