package edu.buaa.batch;

import com.google.common.collect.PeekingIterator;
import edu.buaa.common.benchmark.MilestoneBuilder;
import edu.buaa.common.transaction.ImportStaticDataTx;
import edu.buaa.common.transaction.ImportTemporalDataTx;
import edu.buaa.common.utils.PFieldList;
import edu.buaa.common.utils.PVal;
import edu.buaa.utils.Helper;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.iotdb.isession.ITableSession;
import org.apache.iotdb.isession.SessionDataSet;
import org.apache.iotdb.isession.pool.ITableSessionPool;
import org.apache.iotdb.rpc.IoTDBConnectionException;
import org.apache.iotdb.rpc.StatementExecutionException;
import org.apache.iotdb.session.pool.TableSessionPoolBuilder;
import org.apache.tsfile.enums.ColumnCategory;
import org.apache.tsfile.enums.TSDataType;
import org.apache.tsfile.write.record.Tablet;

import java.util.*;

public class IoTDBBulkLoad extends MilestoneBuilder {
    private static final int DEFAULT_BATCH_SIZE = 2000;

    private final String serverHost = Helper.mustEnv("DB_HOST");
    private final String serverPort = Helper.mustEnv("DB_PORT");
    private final ITableSessionPool tableSessionPool =
            new TableSessionPoolBuilder()
                    .nodeUrls(Collections.singletonList(serverHost + ":" + serverPort))
                    .user("root")
                    .password("root")
                    .maxSize(1)
                    .build();
    private final ITableSession session = tableSessionPool.getSession();
    private final Tablet nodeStaticTablet, nodeTemporalTablet, relStaticTablet, relTemporalTablet;
    private final int batchSize = Integer.parseInt(Helper.envOrDefault("BATCH_SIZE", Integer.toString(DEFAULT_BATCH_SIZE)));

    public IoTDBBulkLoad() throws Exception {
        super();
        // IoTDB java api支持无表结构写入，仅需指定Tablet即可
        session.executeNonQueryStatement("CREATE DATABASE IF NOT EXISTS db");
        session.executeNonQueryStatement("USE db");
        nodeStaticTablet = initTableAndReturnTablet(true, true, schema.nodeStatic);
        nodeTemporalTablet = initTableAndReturnTablet(false, true, schema.nodeTemporal);
        relStaticTablet = initTableAndReturnTablet(true, false, schema.relStatic);
        relTemporalTablet = initTableAndReturnTablet(false, false, schema.relTemporal);
        dataGen.setSectionEnable(false);
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

    private Tablet initTableAndReturnTablet(boolean isStatic, boolean isNode, Map<String, PVal.Type> columns) throws IoTDBConnectionException, StatementExecutionException {
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

    private int fullTabletCnt = 0;
    private int halfFullTabletRecordNum = 0;

    private void insertData(PFieldList data, Set<String> props, Tablet tablet, boolean isStatic) throws IoTDBConnectionException, StatementExecutionException {
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
                fullTabletCnt++;
            }
        }
        if (tablet.getRowSize() != 0) {
            session.insert(tablet);
            halfFullTabletRecordNum += tablet.getRowSize();
            tablet.reset();
        }
    }

    @Override
    public void importStatic() throws Exception {
        Iterator<ImportStaticDataTx> it = dataGen.readNetwork(8000);
        while (it.hasNext()) {
            ImportStaticDataTx tx = it.next();
            insertData(tx.getNodes(), schema.nodeStatic.keySet(), nodeStaticTablet, true);
            insertData(tx.getRels(), schema.relStatic.keySet(), relStaticTablet, true);
        }
    }

    private long totalDataPoints = 0;

    private void insertTemporalDataTx(ImportTemporalDataTx tx, Tablet tablet, int st, int et) throws IoTDBConnectionException, StatementExecutionException {
        PFieldList data = tx.getData();
        Set<String> props = data.keysWithout("t", "st", "et");
        insertData(data, props, tablet, false);
        totalDataPoints += (long) data.size() * data.keysWithout("u_sid", "t").size();
        if(ticker.shouldTick(0)) {
            int curT = data.get("t", 0).i();
            log.debug("loading tp: {}%, total points: {}", (curT - st) * 100f / (et - st), totalDataPoints);
            log.debug("full tablets inserted: {}, half full tablet records inserted: {}", fullTabletCnt, halfFullTabletRecordNum);
            fullTabletCnt = 0;
            halfFullTabletRecordNum = 0;
        }
    }

    @Override
    public void importTemporal() throws Exception {
        int et = dataGen.parseTime(endTime);
        int st = dataGen.parseTime(startTime);
        PeekingIterator<ImportTemporalDataTx> nodeIter = dataGen.readNodeTemporal(startTime, endTime, 10000);
        while (nodeIter.hasNext()) {
            insertTemporalDataTx(nodeIter.next(), nodeTemporalTablet, st, et);
        }
        PeekingIterator<ImportTemporalDataTx> edgeIter = dataGen.readRelTemporal(startTime, endTime, 10000);
        while (edgeIter.hasNext()) {
            insertTemporalDataTx(edgeIter.next(), relTemporalTablet, st, et);
        }
    }

    @Override
    public void close() throws Exception {
        SessionDataSet nodeTpDataSet = session.executeQueryStatement("SELECT count(*) FROM node_tp");
        SessionDataSet relTpDataSet = session.executeQueryStatement("SELECT count(*) FROM rel_tp");
        printDataSet(nodeTpDataSet);
        printDataSet(relTpDataSet);
        session.close();
        tableSessionPool.close();
    }

    public static void printDataSet(SessionDataSet dataSet)
            throws IoTDBConnectionException, StatementExecutionException {
        SessionDataSet.DataIterator iterator = dataSet.iterator();
        System.out.println(dataSet.getColumnNames());
        System.out.println(dataSet.getColumnTypes());
        int columnCount = dataSet.getColumnNames().size();
        while (iterator.next()) {
            StringBuilder builder = new StringBuilder();
            for (int i = 1; i <= columnCount; i++) {
                if (!iterator.isNull(i)) {
                    builder.append(iterator.getString(i)).append(",");
                } else {
                    builder.append("null").append(",");
                }
            }
            System.out.println(builder);
        }
    }
}
