package edu.buaa.common.client;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.parser.Feature;
import com.google.common.util.concurrent.ListenableFuture;
import edu.buaa.common.server.DBSocketServer;
import edu.buaa.common.transaction.AbstractTransaction;
import edu.buaa.common.utils.TemporalGraphPropertySchema;
import edu.buaa.utils.Helper;
import edu.buaa.utils.TimeMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;

public abstract class AbstractNeoClient implements DBClientProxy{
    private final BlockingQueue<Connection> connectionPool = new LinkedBlockingQueue<>();
    private RequestDispatcher service;
    private static final Logger log = LoggerFactory.getLogger(AbstractNeoClient.class);

    @Override
    public AbstractNeoClient init(String serverHost, int port, String dbName, int parallelCnt, TemporalGraphPropertySchema schema) throws IOException {
        this.service = new RequestDispatcher(parallelCnt);
        for (int i = 0; i < parallelCnt; i++) {
            this.connectionPool.offer(new Connection(serverHost, port));
        }
        return this;
    }

    protected ListenableFuture<DBProxy.ServerResponse> addQuery(String query, int section) throws InterruptedException {
        return service.submit(new Req(query), section);
    }

    protected ListenableFuture<DBProxy.ServerResponse> addQuery(String query) throws InterruptedException {
        return service.submit(new Req(query), -1);
    }

    @Override
    public String testServerClientCompatibility() throws UnsupportedOperationException{
        try {
            Future<DBProxy.ServerResponse> response = addQuery("VERSION");
            DBSocketServer.ServerVersionResult result = (DBSocketServer.ServerVersionResult) response.get().getResult();
            String clientVersion = Helper.codeGitVersion();
            if (!clientVersion.equals(result.getVersion())) {
                log.error("server({}) client({}) version not match!", result.getVersion(), clientVersion);
            }
            return result.getVersion();
        } catch (Exception e) {
            throw new UnsupportedOperationException(e);
        }
    }

    public void awaitTermination() throws InterruptedException, IOException {
        int remains = service.getQueueSize();
        log.debug("Client closed but will send remaining {} requests", remains);
        long mark = System.currentTimeMillis();
        while (remains>0) {
            long completeCnt = service.getCompletedTaskCount();
            remains = service.getQueueSize();
            log.debug(completeCnt + " / " + (completeCnt + remains) + " query completed.");
            Thread.sleep(20_000);
        }
        this.addQuery("EXIT");
        service.awaitClose();
        while (!connectionPool.isEmpty()) {
            Connection conn = connectionPool.take();
            conn.close();
        }
        log.debug("Client exit after wait {} seconds. send {} lines.", (System.currentTimeMillis()-mark)/1000, service.getCompletedTaskCount());
    }

    public void close() throws IOException, InterruptedException {
        this.awaitTermination();
    }

    public class Req implements RequestDispatcher.RequestWs {
        private final TimeMonitor timeMonitor = new TimeMonitor();
        private final String query;

        Req(String query) {
            this.query = query;
            timeMonitor.begin("Wait in queue");
        }

        @Override
        public DBProxy.ServerResponse call() throws Exception {
            Connection conn = connectionPool.take();
            String response = null;
            try {
                timeMonitor.mark("Wait in queue", "Send query");
                conn.out.println(query);
                timeMonitor.mark("Send query", "Wait result");
                response = conn.in.readLine();
                timeMonitor.end("Wait result");
                if("EXIT".equals(query)) return new DBProxy.ServerResponse();
                if (response == null) throw new RuntimeException("[Got null. Server close connection]");
                if (query.equals("VERSION")) return JSON.parseObject(response, DBProxy.ServerResponse.class);
                else return onResponse(query, response, timeMonitor, Thread.currentThread());
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println(query);
                System.out.println(response);
                throw e;
            } finally {
                connectionPool.put(conn);
            }
        }
    }

    protected ServerResponse onResponse(String query, String response, TimeMonitor timeMonitor, Thread thread) throws Exception {
        ServerResponse res = JSON.parseObject(response, ServerResponse.class, Feature.SupportAutoType);
        AbstractTransaction.Metrics metrics = res.getMetrics();
        metrics.setConnId(Math.toIntExact(Thread.currentThread().getId()));
        metrics.setExeTime(Math.toIntExact(timeMonitor.duration("Send query") + timeMonitor.duration("Wait result")));
        metrics.setWaitTime(Math.toIntExact(timeMonitor.duration("Wait in queue")));
        metrics.setSendTime(timeMonitor.beginT("Send query"));
        metrics.setReqSize(query.length());
        metrics.setReturnSize(response.length());
        return res;
    }

    public static class Connection {
        private final Socket client;
        private final BufferedReader in;
        private final PrintWriter out;

        Connection(String host, int port) throws IOException {
            this.client = retryCon(host, port, 10, 60);
//            client.setSoTimeout(8000);
//            this.client.setTcpNoDelay(true);
            this.in = new BufferedReader(new InputStreamReader(client.getInputStream()));
            this.out = new PrintWriter(client.getOutputStream(), true);
        }

        private Socket retryCon(String host, int port, int times, int waitSeconds) throws IOException{
            Socket cli = null;
            ConnectException err = null;
            try {
                for (int i = 0; i < times && cli == null; i++) {
                    try {
                        cli = new Socket(host, port);
                    } catch (ConnectException e) {
                        if (e.getMessage().contains("timed out")) {
                            err = e;
                            Thread.sleep(waitSeconds * 1000L);
                            log.warn("connection time out {}. wait {} seconds retry", i, waitSeconds);
                        }else{
                            throw e;
                        }
                    }
                }
                if(cli==null) throw err;
            }catch (InterruptedException e){
                throw new IOException(e);
            }
            return cli;
        }

        public void close() throws IOException {
            in.close();
            out.close();
            client.close();
        }
    }
}
