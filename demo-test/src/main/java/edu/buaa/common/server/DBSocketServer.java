package edu.buaa.common.server;

import com.alibaba.fastjson.JSON;
import com.sun.management.OperatingSystemMXBean;
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
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.LockSupport;

import static edu.buaa.common.transaction.AbstractTransaction.*;

public class DBSocketServer {
    private static final Logger log = LoggerFactory.getLogger(DBSocketServer.class);
    private final File dbPath;
    private final DBKernelProxy dbKernelProxy;
    private final int port;

    private volatile boolean shouldRun = true;
    private ServerSocket server;
    private final List<Thread> threads = new LinkedList<>();

    public DBSocketServer(File dbPath, DBKernelProxy dbKernelProxy, int port) {
        this(dbPath, dbKernelProxy, port, false);
    }

    public DBSocketServer(File dbPath, DBKernelProxy dbKernelProxy, int port, boolean allowTxFail) {
        this.dbPath = dbPath;
        this.dbKernelProxy = dbKernelProxy;
        this.port = port;
    }

    public void start() throws IOException {
        try {
            dbKernelProxy.start(dbPath);
        } catch (Exception e) {
            throw new IOException(e);
        }
        log.debug("db initialized at "+dbPath+" port: "+port);
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
            dbKernelProxy.shutdown();
            Thread.sleep(60);
        }catch (InterruptedException ignore){
            log.debug("Interrupted...");
        }
        log.debug("main thread exit.");
    }

    public interface DBKernelProxy {
        void start(File path) throws Exception;
        void shutdown() throws IOException;
        Result execute(String line) throws TransactionFailedException;
        default Result execute(String line, AbstractTransaction.Metrics metrics) throws TransactionFailedException {
            return execute(line);
        }
    }

    private class ServerStatusMonitor extends Thread{
        volatile ServerStatus serverStatus;

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
                    ServerStatus s = new ServerStatus();
                    s.activeConn = threads.size();
                    s.curMem = runtime.totalMemory() - runtime.freeMemory();
                    s.processCpuLoad = bean.getProcessCpuLoad();
                    s.systemCpuLoad = bean.getSystemCpuLoad();
                    s.diskWriteSpeed = (curDisksWrite - disksWrite)/(now-lastTime)*1000;
                    s.diskReadSpeed = (curDisksRead - disksRead)/(now-lastTime)*1000;
                    s.diskQueueLength = curDiskQueueLen;
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
                        Metrics metrics = new Metrics();
                        Result exeResult = null;

                        time.mark("Wait", "Transaction");
                        if("VERSION".equals(line)){
                            ServerVersionResult versionResult = new ServerVersionResult();
                            versionResult.setVersion(Helper.codeGitVersion());
                            exeResult = versionResult;
                            log.debug("client ask server version.");
                        }else {
                            int retryCnt = 0;
                            try {
                                while(exeResult==null) try {
                                    exeResult = dbKernelProxy.execute(line, metrics);
                                }catch (org.neo4j.kernel.DeadlockDetectedException e){
                                    retryCnt ++ ;
                                    LockSupport.parkNanos(50_000_000);
                                }
                                metrics.setTxSuccess(true);
                                successReq++;
                            } catch (TransactionFailedException e) {
                                metrics.setTxSuccess(false);
                                log.error("ERROR in loop. line:"+line, e);
                                metrics.setErrMsg(e);
                                Helper.trace().notifyError(e);
                            }
                            metrics.setRetryCnt(retryCnt);
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

    public static class Metrics extends AbstractTransaction.Metrics{
        private int txTime;
//        @JSONField(unwrapped = true)
        private ServerStatus status;

        public int getTxTime() {
            return txTime;
        }

        public void setTxTime(int txTime) {
            this.txTime = txTime;
        }

        public ServerStatus getStatus() {
            return status;
        }

        public void setStatus(ServerStatus status) {
            this.status = status;
        }
    }

    public static class ServerStatus{
        private long time = System.currentTimeMillis();
        private int  activeConn;
        private long curMem;
        private long diskReadSpeed;
        private long diskWriteSpeed;
        private long diskQueueLength;
        private double processCpuLoad;
        private double systemCpuLoad;

        public long getTime() {
            return time;
        }

        public void setTime(long time) {
            this.time = time;
        }

        public int getActiveConn() {
            return activeConn;
        }

        public void setActiveConn(int activeConn) {
            this.activeConn = activeConn;
        }

        public long getCurMem() {
            return curMem;
        }

        public void setCurMem(long curMem) {
            this.curMem = curMem;
        }

        public long getDiskReadSpeed() {
            return diskReadSpeed;
        }

        public void setDiskReadSpeed(long diskReadSpeed) {
            this.diskReadSpeed = diskReadSpeed;
        }

        public long getDiskWriteSpeed() {
            return diskWriteSpeed;
        }

        public void setDiskWriteSpeed(long diskWriteSpeed) {
            this.diskWriteSpeed = diskWriteSpeed;
        }

        public long getDiskQueueLength() {
            return diskQueueLength;
        }

        public void setDiskQueueLength(long diskQueueLength) {
            this.diskQueueLength = diskQueueLength;
        }

        public double getProcessCpuLoad() {
            return processCpuLoad;
        }

        public void setProcessCpuLoad(double processCpuLoad) {
            this.processCpuLoad = processCpuLoad;
        }

        public double getSystemCpuLoad() {
            return systemCpuLoad;
        }

        public void setSystemCpuLoad(double systemCpuLoad) {
            this.systemCpuLoad = systemCpuLoad;
        }
    }

    public static class ServerVersionResult extends Result{
        String version;

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }
    }
}
