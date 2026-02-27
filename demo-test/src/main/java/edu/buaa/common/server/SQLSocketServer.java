package edu.buaa.common.server;

import com.alibaba.fastjson.JSON;
import com.google.common.util.concurrent.ListenableFuture;
import com.sun.management.OperatingSystemMXBean;
import edu.buaa.common.client.AbstractSQLClient;
import edu.buaa.common.client.DBProxy;
import edu.buaa.common.transaction.AbstractTransaction;
import edu.buaa.common.transaction.TransactionFailedException;
import edu.buaa.utils.Helper;
import edu.buaa.utils.TimeMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import oshi.SystemInfo;
import oshi.hardware.HWDiskStore;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.LinkedList;
import java.util.List;

import static edu.buaa.common.transaction.AbstractTransaction.Result;

public class SQLSocketServer {
    private static final Logger log = LoggerFactory.getLogger(SQLSocketServer.class);
    private final ReqExecutor client;
    private final int port;

    private volatile boolean shouldRun = true;
    private ServerSocket server;
    private final List<Thread> threads = new LinkedList<>();

    public SQLSocketServer(AbstractSQLClient client, int port) {
        this.client = new ReqExecutor(client);
        this.port = port;
    }

    public void start() throws IOException {
        log.debug("db initialized at port: "+port);
        ServerStatusMonitor monitor = new ServerStatusMonitor();
        monitor.setDaemon(true);
        monitor.start();

        server = new ServerSocket(port);
        log.debug("waiting for client to connect.");

        try {
            while (shouldRun) {
                Socket client;
                try {
                    client = server.accept();
                } catch (SocketException ignore) { // closed from another thread.
                    break;
                }
                Thread t = new ServerThread(client, monitor);
                threads.add(t);
                log.debug("GET one more client, currently {} client", threads.size());
                t.setDaemon(true);
                t.start();
            }
            for (Thread t : threads) {
                t.join();
            }
            server.close();
            Thread.sleep(60);
        }catch (InterruptedException ignore){
            log.debug("Interrupted...");
        }
        log.debug("main thread exit.");
    }

    private static class ReqExecutor {
        private final AbstractSQLClient client;

        ReqExecutor(AbstractSQLClient client){
            this.client = client;
        }

        Result execute(String line) throws TransactionFailedException {
            AbstractTransaction tx = JSON.parseObject(line, AbstractTransaction.class);
            try {
                ListenableFuture<DBProxy.ServerResponse> r = this.client.execute(tx);
                return r.get().getResult();
            } catch (TransactionFailedException e) {
                throw e;
            } catch (Exception e) {
                e.printStackTrace();
                throw new TransactionFailedException(e);
            }
        }
    }

    private class ServerThread extends Thread{
        private final ServerStatusMonitor monitor;
        Socket client;
        BufferedReader fromClient;
        PrintStream toClient;
        long reqCnt = 0;
        long successReq = 0;

        ServerThread(Socket client, ServerStatusMonitor monitor) throws IOException {
            this.client = client;
            this.monitor = monitor;
            client.setTcpNoDelay(true);
            this.fromClient = new BufferedReader(new InputStreamReader(client.getInputStream()));
            this.toClient = new PrintStream(client.getOutputStream(), true);
        }

        public void run(){
            long tid = Thread.currentThread().getId();
            Thread.currentThread().setName("Socket con "+tid);
            log.debug(Thread.currentThread().getName()+" started.");
            TimeMonitor time = new TimeMonitor();
            time.begin("Send");
            String line = null;
            try {
                while(shouldRun){
                    time.mark("Send", "Wait");
                    try {
                        line = fromClient.readLine();
                    }catch (SocketException ignore){// client close conn.
                        log.debug("closed by server.");
                        client.close();
                        break;
                    }
                    if(line==null){
                        log.debug("client close connection. read end.");
                        client.close();
                        break;
                    }else if("EXIT".equals(line)){ //client ask server exit;
                        client.close();
                        server.close();
                        shouldRun = false;
                        log.debug("client ask server exit.");
                        break;
                    }else{
                        Result exeResult = null;
                        DBSocketServer.Metrics metrics = new DBSocketServer.Metrics();
                        time.mark("Wait", "Transaction");
                        if("VERSION".equals(line)){
                            DBSocketServer.ServerVersionResult versionResult = new DBSocketServer.ServerVersionResult();
                            versionResult.setVersion(Helper.codeGitVersion());
                            exeResult = versionResult;
                            log.debug("client ask server version.");
                        }else {
                            try {
                                exeResult = SQLSocketServer.this.client.execute(line);
                                metrics.setTxSuccess(true);
                                successReq++;
                            } catch (TransactionFailedException e) {
                                metrics.setTxSuccess(false);
                                metrics.setErrMsg(e);
                                log.error("ERROR in loop. line:"+line, e);
                                Helper.trace().notifyError(e);
                            }
                        }
                        time.mark("Transaction", "Send");

                        metrics.setStatus(monitor.serverStatus);
                        metrics.setTxTime(Math.toIntExact(time.duration("Transaction")));

                        DBProxy.ServerResponse response = new DBProxy.ServerResponse();
                        response.setResult(exeResult);
                        response.setMetrics(metrics);

                        toClient.println(JSON.toJSONString(response, Helper.serializerFeatures));
                        reqCnt++;
                    }
                }
            } catch (Exception e) {
                log.error("ERROR in loop. line:"+line, e);
                Helper.trace().notifyError(e);
            } finally {
                try {
                    fromClient.close();
                    toClient.close();
                    client.close();
                } catch (IOException e) {
                    log.error("ERROR when close client connection.", e);
                }
                log.debug(Thread.currentThread().getName()+" exit. process "+reqCnt+" queries. success rate: "+
                        String.format("%.2f%% (failed: %d)", successReq*100f/reqCnt, reqCnt-successReq));
            }
//            threads.remove(this);// https://stackoverflow.com/questions/25113987/why-is-there-a-concurrentmodificationexception-even-when-list-is-synchronized
        }
    }


    private class ServerStatusMonitor extends Thread{
        volatile DBSocketServer.ServerStatus serverStatus;

        public void run(){
            final OperatingSystemMXBean bean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            Runtime runtime = Runtime.getRuntime();
            try {
                long lastTime = System.currentTimeMillis();
                long disksWrite = 0, disksRead=0;
                while(shouldRun){
                    Thread.sleep(1_000);
                    long curDisksWrite = 0, curDisksRead=0, curDiskQueueLen=0;
                    List<HWDiskStore> disks = new SystemInfo().getHardware().getDiskStores();
                    for(HWDiskStore disk : disks){
                        curDisksWrite += disk.getWriteBytes();
                        curDisksRead += disk.getReadBytes();
                        curDiskQueueLen += disk.getCurrentQueueLength();
                    }
                    long now = System.currentTimeMillis();
                    DBSocketServer.ServerStatus s = new DBSocketServer.ServerStatus();
                    s.setActiveConn(threads.size());
                    s.setCurMem(runtime.totalMemory() - runtime.freeMemory());
                    s.setProcessCpuLoad( bean.getProcessCpuLoad());
                    s.setSystemCpuLoad( bean.getSystemCpuLoad());
                    s.setDiskWriteSpeed((curDisksWrite - disksWrite)/(now-lastTime)*1000);
                    s.setDiskReadSpeed((curDisksRead - disksRead)/(now-lastTime)*1000);
                    s.setDiskQueueLength( curDiskQueueLen );
                    this.serverStatus = s;
                    disksRead = curDisksRead;
                    disksWrite = curDisksWrite;
                    lastTime = now;
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
