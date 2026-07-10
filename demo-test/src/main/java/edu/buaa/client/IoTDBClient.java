package edu.buaa.client;

import com.google.common.util.concurrent.ListenableFuture;
import edu.buaa.common.client.DBClientProxy;
import edu.buaa.common.client.RequestDispatcher;
import edu.buaa.common.transaction.*;
import edu.buaa.common.utils.PFieldList;
import edu.buaa.common.utils.PVal;
import edu.buaa.common.utils.TemporalGraphPropertySchema;
import edu.buaa.utils.Helper;
import edu.buaa.utils.TimeMonitor;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.iotdb.isession.ITableSession;
import org.apache.iotdb.isession.SessionDataSet;
import org.apache.iotdb.isession.pool.ITableSessionPool;
import org.apache.iotdb.rpc.IoTDBConnectionException;
import org.apache.iotdb.rpc.StatementExecutionException;
import org.apache.iotdb.session.pool.TableSessionPoolBuilder;
import org.apache.tsfile.enums.ColumnCategory;
import org.apache.tsfile.enums.TSDataType;
import org.apache.tsfile.read.common.Field;
import org.apache.tsfile.read.common.RowRecord;
import org.apache.tsfile.write.record.Tablet;

import java.io.IOException;
import java.util.*;

public class IoTDBClient implements DBClientProxy {
    private static final int DEFAULT_BATCH_SIZE = 2000;

    private ITableSessionPool tableSessionPool;
    private Tablet nodeStaticTablet, nodeTemporalTablet, relStaticTablet, relTemporalTablet;
    private RequestDispatcher service;

    private final int batchSize = Integer.parseInt(Helper.envOrDefault("BATCH_SIZE", Integer.toString(DEFAULT_BATCH_SIZE)));

    @Override
    public DBClientProxy init(String serverHost, int port, String dbName, int parallelCnt, TemporalGraphPropertySchema schema) throws Exception {
        tableSessionPool = new TableSessionPoolBuilder()
                .nodeUrls(Collections.singletonList(serverHost + ":" + port))
                .user("root")
                .password("root")
                .maxSize(parallelCnt)
                .build();
        try (ITableSession session = tableSessionPool.getSession()) {
            session.executeNonQueryStatement("CREATE DATABASE IF NOT EXISTS db");
            session.executeNonQueryStatement("USE db");
            nodeStaticTablet = initTableAndReturnTablet(true, true, schema.nodeStatic, session);
            nodeTemporalTablet = initTableAndReturnTablet(false, true, schema.nodeTemporal, session);
            relStaticTablet = initTableAndReturnTablet(true, false, schema.relStatic, session);
            relTemporalTablet = initTableAndReturnTablet(false, false, schema.relTemporal, session);
        }
        service = new RequestDispatcher(parallelCnt);
        return this;
    }

    private String occupier = "occupier";

    private Triple<ArrayList<String>, ArrayList<TSDataType>, ArrayList<ColumnCategory>> getSchema(boolean isStatic, Map<String, PVal.Type> columns) {
        ArrayList<String> names = new ArrayList<>();
        ArrayList<TSDataType> dataTypes = new ArrayList<>();
        ArrayList<ColumnCategory> categories = new ArrayList<>();
        for (Map.Entry<String, PVal.Type> entry : columns.entrySet()) {
            String name = entry.getKey();
            PVal.Type type = entry.getValue();
            names.add(name);
            if (!isStatic) {
                if (type == PVal.Type.INT) {
                    dataTypes.add(TSDataType.INT32);
                } else if (type == PVal.Type.STRING) {
                    dataTypes.add(TSDataType.STRING);
                } else if (type == PVal.Type.FLOAT) {
                    dataTypes.add(TSDataType.FLOAT);
                } else {
                    dataTypes.add(null);
                }
            }
            else {
                dataTypes.add(TSDataType.STRING);
            }
            if (name.compareTo("u_sid") == 0) {
                categories.add(ColumnCategory.TAG);
            }
            else {
                categories.add(isStatic ? ColumnCategory.ATTRIBUTE : ColumnCategory.FIELD);
            }
        }
        if (isStatic) {
            while (names.contains(occupier)) {
                occupier += "r";
            }
            names.add(occupier);
            dataTypes.add(TSDataType.INT32);
            categories.add(ColumnCategory.FIELD);
        }
        if (!names.contains("u_sid")) {
            names.add("u_sid");
            dataTypes.add(TSDataType.STRING);
            categories.add(ColumnCategory.TAG);
        }
        return Triple.of(names, dataTypes, categories);
    }

    private Tablet initTableAndReturnTablet(boolean isStatic, boolean isNode, Map<String, PVal.Type> columns, ITableSession session) throws IoTDBConnectionException, StatementExecutionException {
        String tableName = (isNode ? "node" : "rel") + (isStatic ? "" : "_tp");
        Triple<ArrayList<String>, ArrayList<TSDataType>, ArrayList<ColumnCategory>> temp = getSchema(isStatic, columns);
        ArrayList<String> names = temp.getLeft();
        ArrayList<TSDataType> types = temp.getMiddle();
        ArrayList<ColumnCategory> categories = temp.getRight();
        StringBuilder builder = new StringBuilder();
        builder.append("CREATE TABLE IF NOT EXISTS ").append(tableName).append(" (");
        StringJoiner joiner = new StringJoiner(", ");
        for (int i = 0; i < names.size(); i++) {
            joiner.add(names.get(i) + " " + types.get(i) + " " + categories.get(i));
        }
        builder.append(joiner).append(")");
        session.executeNonQueryStatement(builder.toString());
        return new Tablet(tableName, names, types, categories, batchSize);
    }

    @Override
    public String testServerClientCompatibility() throws UnsupportedOperationException {
        return "";
    }

    private void insertData(PFieldList data, Set<String> props, Tablet tablet, boolean isStatic, ITableSession session) throws IoTDBConnectionException, StatementExecutionException {
        for (int i = 0; i < data.size(); i++) {
            int row = tablet.getRowSize();
            long timestamp = isStatic ? 0 : data.get("t", i).i();
            tablet.addTimestamp(row, timestamp);
            for (String prop : props) {
                Object o = data.get(prop, i).getVal();
                tablet.addValue(prop, row, isStatic ? o.toString() : o);
            }
            if (isStatic) {
                tablet.addValue(occupier, row, i);
            }
            if (tablet.getRowSize() == tablet.getMaxRowNumber()) {
                session.insert(tablet);
                tablet.reset();
            }
        }
        if (tablet.getRowSize() != 0) {
            session.insert(tablet);
            tablet.reset();
        }
    }

    @Override
    public ListenableFuture<ServerResponse> execute(AbstractTransaction tx) throws Exception {
        switch (tx.getTxType()) {
            case tx_import_static_data:
                return this.submit(execute((ImportStaticDataTx) tx));
            case tx_import_temporal_data:
                return this.submit(execute((ImportTemporalDataTx) tx), tx.getSection());
            case tx_query_entity_history:
                return this.submit(execute((EntityHistoryTx) tx));
            case tx_query_snapshot:
            case tx_query_snapshot_aggr_max:
            case tx_query_road_by_temporal_condition:
            case tx_query_reachable_area:
            case tx_update_temporal_data:
                return this.submit(s->{
                    throw new TransactionFailedException();
                });
            case tx_query_snapshot_aggr_duration:
            default:
                throw new UnsupportedOperationException();
        }
    }

    private Req execute(ImportStaticDataTx tx) {
        return session -> {
            PFieldList nodeData = tx.getNodes(), relData = tx.getRels();
            insertData(nodeData, nodeData.keys(), nodeStaticTablet, true, session);
            insertData(relData, relData.keys(), relStaticTablet, true, session);
            return new AbstractTransaction.Result();
        };
    }

    private Req execute(ImportTemporalDataTx tx) {
        return session -> {
            PFieldList data = tx.getData();
            Tablet tablet = tx.isNode() ? nodeTemporalTablet : relTemporalTablet;
            insertData(data, data.keysWithout("t", "st", "et"), tablet, false, session);
            return new AbstractTransaction.Result();
        };
    }

    private Req execute(EntityHistoryTx tx) {
        return session -> {
            String tableName = tx.isNode() ? "node_tp" : "rel_tp";
            String prop = tx.getProp();
            String u_sid = tx.getEntity();
            int start = tx.getBeginTime();
            int end = tx.getEndTime();
            String sql = String.format("SELECT %s FROM %s WHERE u_sid = '%s' AND time >= %d AND time <= %d ORDER BY time",
                    prop, tableName, u_sid, start, end);
            SessionDataSet dataSet = session.executeQueryStatement(sql);
            int columnIndex;
            for (columnIndex = 0; columnIndex < dataSet.getColumnNames().size(); columnIndex++) {
                if (dataSet.getColumnNames().get(columnIndex).equals(prop)) break;
            }
            List<edu.buaa.utils.Triple<Integer, Integer, PVal>> res = new ArrayList<>();
            edu.buaa.utils.Triple<Integer, Integer, PVal> last = null;
            while (dataSet.hasNext()) {
                RowRecord record = dataSet.next();
                int time = (int) record.getTimestamp();
                Field field = record.getField(columnIndex);
                PVal value = PVal.v(field.getObjectValue(field.getDataType()));
                edu.buaa.utils.Triple<Integer, Integer, PVal> current = edu.buaa.utils.Triple.of(time, -1, value);
                if (last != null) {
                    res.add(edu.buaa.utils.Triple.of(last.getLeft(), time - 1, last.getRight()));
                }
                last = current;
            }
            if (last != null) {
                res.add(edu.buaa.utils.Triple.of(last.getLeft(), end, last.getRight()));
            }
            EntityHistoryTx.Result result = new EntityHistoryTx.Result();
            result.setHistory(res);
            return result;
        };
    }

    @Override
    public void close() throws IOException, InterruptedException {
        service.awaitClose();
        tableSessionPool.close();
    }

    protected ListenableFuture<ServerResponse> submit(IoTDBClient.Req request) throws InterruptedException {
        return this.service.submit(new IoTDBClient.IoTDBReq(tableSessionPool, request), -1);
    }

    protected ListenableFuture<ServerResponse> submit(IoTDBClient.Req request, int section) throws InterruptedException {
        return this.service.submit(new IoTDBClient.IoTDBReq(tableSessionPool, request), section);
    }

    @FunctionalInterface
    protected interface Req{
        AbstractTransaction.Result executeQuery(ITableSession session) throws Exception;
    }

    protected static class IoTDBReq implements RequestDispatcher.RequestWs {
        private final TimeMonitor timeMonitor = new TimeMonitor();
        protected final AbstractTransaction.Metrics metrics = new AbstractTransaction.Metrics();
        protected final ITableSessionPool sessionPool;
        private final IoTDBClient.Req body;

        protected IoTDBReq(ITableSessionPool sessionPool, IoTDBClient.Req body) {
            this.sessionPool = sessionPool;
            this.body = body;
            timeMonitor.begin("Wait in queue");
        }

        @Override
        public ServerResponse call() throws Exception {
            try (ITableSession session = sessionPool.getSession()) {
                timeMonitor.mark("Wait in queue", "query");
                AbstractTransaction.Result result = body.executeQuery(session);
                timeMonitor.end("query");
                if (result == null) throw new RuntimeException("[Got null. Server close connection]");
                metrics.setWaitTime(Math.toIntExact(timeMonitor.duration("Wait in queue")));
                metrics.setSendTime(timeMonitor.beginT("query"));
                metrics.setExeTime(Math.toIntExact(timeMonitor.duration("query")));
                // 这个没法获取，全部定为-1
                metrics.setConnId(-1);
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
}
