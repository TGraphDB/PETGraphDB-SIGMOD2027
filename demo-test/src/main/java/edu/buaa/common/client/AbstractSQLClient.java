package edu.buaa.common.client;

import com.google.common.util.concurrent.ListenableFuture;
import edu.buaa.common.benchmark.SQLMilestoneBuilder;
import edu.buaa.common.transaction.AbstractTransaction;
import edu.buaa.common.transaction.TransactionFailedException;
import edu.buaa.common.utils.PVal;
import edu.buaa.common.utils.TemporalGraphPropertySchema;
import edu.buaa.utils.Helper;
import edu.buaa.utils.TimeMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static edu.buaa.common.utils.PVal.Type.*;

/**
 * Created by crusher. 2020.10.14
 */
public abstract class AbstractSQLClient implements DBClientProxy {
    private static final Logger log = LoggerFactory.getLogger(AbstractSQLClient.class);
    protected String dbName;
    protected String serverHost;
    public final BlockingQueue<Connection> connectionPool = new LinkedBlockingQueue<>();
    protected final Map<Connection, Integer> connIdMap = new HashMap<>();
    protected TemporalGraphPropertySchema schema;
    private RequestDispatcher service;

    @Override
    public AbstractSQLClient init(String serverHost, int port, String dbName, int parallelCnt, TemporalGraphPropertySchema schema) throws Exception{
        System.out.println("connect to "+dbName+"@"+serverHost+":"+port+"("+parallelCnt+")");
        this.serverHost = serverHost;
        this.dbName = dbName;
        this.service = new RequestDispatcher(parallelCnt);
        this.schema = schema;
        createDbIfNotExist(serverHost, dbName);
        for (int i = 0; i < parallelCnt; i++) {
            Connection conn = createNormalConnection(serverHost, dbName);
            this.connectionPool.offer(conn);
            connIdMap.put(conn, i);
            if(i==parallelCnt-1) connected(conn);
        }
        return this;
    }

    protected abstract void connected(Connection conn) throws Exception;

    protected abstract void createDbIfNotExist(String serverHost, String dbName) throws SQLException, ClassNotFoundException;

    protected abstract List<String> createTables();

    public List<String> createIndexes(){ return Collections.emptyList(); }

    protected abstract Connection createNormalConnection(String serverHost, String dbName) throws SQLException;

    public abstract String currentStorageStatus() throws SQLException, InterruptedException;

//    public abstract void onError(AbstractTransaction tx, Exception e);

    @Override
    public String testServerClientCompatibility() throws UnsupportedOperationException {
        return null;
    }

    @Override
    public void close() throws IOException, InterruptedException {
        try {
            int remains = service.getQueueSize();
            log.debug("Client closed but will send remaining {} requests", remains);
            long mark = System.currentTimeMillis();
            while (remains>0) {
                long completeCnt = service.getCompletedTaskCount();
                remains = service.getQueueSize();
                System.out.println(completeCnt + " / " + (completeCnt + remains) + " query completed.");
                Thread.sleep(20_000);
            }
            service.awaitClose();
            while (!connectionPool.isEmpty()) {
                Connection conn = connectionPool.take();
                conn.close();
            }
            log.debug("Client exit after wait {} seconds. send {} lines.", (System.currentTimeMillis()-mark)/1000, service.getCompletedTaskCount());
        } catch (SQLException e) {
            e.printStackTrace();
            throw new IOException(e);
        }
    }

    protected ListenableFuture<ServerResponse> submit(Req request) throws InterruptedException {
        return this.service.submit(new SQLReq(connectionPool, connIdMap, request), -1);
    }

    protected ListenableFuture<ServerResponse> submit(Req request, int section) throws InterruptedException {
        return this.service.submit(new SQLReq(connectionPool, connIdMap, request), section);
    }

    @FunctionalInterface
    protected interface Req{
        AbstractTransaction.Result executeQuery(Connection conn) throws Exception;
    }

    protected static class SQLReq implements RequestDispatcher.RequestWs {
        private final TimeMonitor timeMonitor = new TimeMonitor();
        protected final AbstractTransaction.Metrics metrics = new AbstractTransaction.Metrics();
        protected final BlockingQueue<Connection> connectionPool;
        protected final Map<Connection, Integer> connIdMap;
        private final Req body;

        protected SQLReq(BlockingQueue<Connection> connectionPool, Map<Connection, Integer> connIdMap, Req body) {
            this.connectionPool = connectionPool;
            this.connIdMap = connIdMap;
            this.body = body;
            timeMonitor.begin("Wait in queue");
        }

        @Override
        public ServerResponse call() throws Exception {
            try {
                Connection conn = connectionPool.take();
                timeMonitor.mark("Wait in queue", "query");
                AbstractTransaction.Result result = body.executeQuery(conn);
                timeMonitor.end("query");
                if (result == null) throw new RuntimeException("[Got null. Server close connection]");
                connectionPool.put(conn);
                metrics.setWaitTime(Math.toIntExact(timeMonitor.duration("Wait in queue")));
                metrics.setSendTime(timeMonitor.beginT("query"));
                metrics.setExeTime(Math.toIntExact(timeMonitor.duration("query")));
                metrics.setConnId(connIdMap.get(conn));
                metrics.setTxSuccess(true);
                ServerResponse response = new ServerResponse();
                response.setMetrics(metrics);
                response.setResult(result);
                return response;
            }catch (TransactionFailedException e){
                Helper.trace().notifyError(e);
                ServerResponse response = new ServerResponse();
                metrics.setTxSuccess(false);
                response.setMetrics(metrics);
                response.setResult(null);
                return response;
            } catch (Exception e) {
                e.printStackTrace();
                throw e;
            }
        }
    }

    protected void setProp(PreparedStatement stat, int pos, PVal val) throws SQLException {
        switch (val.getType()){
            case STRING:
                stat.setString(pos, val.s());
                break;
            case INT:
                stat.setInt(pos, val.i());
                break;
            case FLOAT:
                stat.setFloat(pos, val.f());
                break;
        }
    }

    protected PVal getPVal(ResultSet rs, boolean isNode, String pName, String column) throws SQLException {
        PVal.Type type = schema.getType(isNode, false, pName);
        if (type==FLOAT){
            return PVal.f(rs.getFloat(column));
        }else if(type==INT){
            return PVal.i(rs.getInt(column));
        }else if(type==STRING){
            return PVal.s(rs.getString(column));
        }
        throw new IllegalStateException("got type "+type+" on column.");
    }

    protected String content(Map<String, PVal.Type> props){
        StringBuilder sb = new StringBuilder();
        for(Map.Entry<String, PVal.Type> t : props.entrySet()){
            sb.append(t.getKey()).append(' ').append(type2SQL(t.getValue())).append(", ");
        }
        if(sb.length()>1) sb.setLength(sb.length()-2);
        return sb.toString();
    }

    protected abstract String type2SQL(PVal.Type value);

    protected String qMarks(int size){
        String[] arr = new String[size];
        Arrays.fill(arr, "?");
        return String.join(",", arr);
    }
}

