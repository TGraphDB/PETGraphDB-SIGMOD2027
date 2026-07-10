package edu.buaa.client;

import com.google.common.util.concurrent.ListenableFuture;
import edu.buaa.common.client.TDengineExecutorClient;
import edu.buaa.common.transaction.*;
import edu.buaa.common.utils.PFieldList;
import edu.buaa.common.utils.PVal;
import edu.buaa.utils.Triple;

import java.sql.*;
import java.util.*;

public class TDengineClient extends TDengineExecutorClient {

    @Override
    protected List<String> createTables() {
        String nst = contentQuoted(schema.nodeStatic);
        String rst = contentQuoted(schema.relStatic);
        String ntp = contentQuoted(schema.nodeTemporal);
        String rtp = contentQuoted(schema.relTemporal);
        return Arrays.asList(
                "CREATE TABLE IF NOT EXISTS `node` (`ts` TIMESTAMP, `id` BIGINT" + (nst.isEmpty() ? "" : ", " + nst) + ")",
                "CREATE TABLE IF NOT EXISTS `rel` (`ts` TIMESTAMP, `id` BIGINT, `r_from` BIGINT, `r_to` BIGINT" + (rst.isEmpty() ? "" : ", " + rst) + ")",
                "CREATE STABLE IF NOT EXISTS `node_tp` (`ts` TIMESTAMP, `end_time` TIMESTAMP" + (ntp.isEmpty() ? "" : ", " + ntp) + ") TAGS (`entity` BIGINT)",
                "CREATE STABLE IF NOT EXISTS `rel_tp` (`ts` TIMESTAMP, `end_time` TIMESTAMP" + (rtp.isEmpty() ? "" : ", " + rtp) + ") TAGS (`entity` BIGINT)"
        );
    }

    @Override
    public ListenableFuture<ServerResponse> execute(AbstractTransaction tx) throws InterruptedException {
        switch (tx.getTxType()) {
            case tx_import_temporal_data:
                return this.submit(execute((ImportTemporalDataTx) tx), tx.getSection());
            case tx_query_entity_history:
                return this.submit(execute((EntityHistoryTx) tx));
            default:
                throw new UnsupportedOperationException("TDengineClient does not support: " + tx.getTxType());
        }
    }

    // ==================== Import Temporal Data ====================

    private Req execute(ImportTemporalDataTx tx) {
        return conn -> {
            String tableName = tx.isNode() ? "node" : "rel";
            String stableName = tx.isNode() ? "node_tp" : "rel_tp";
            PFieldList data = tx.getData();
            List<String> props = new ArrayList<>(data.keysWithout("u_sid", "t"));
            int tSize = data.size();

            // 1. 收集所有 u_sid 并查询对应的 entity 内部 id
            Map<String, Long> uidToId = new LinkedHashMap<>();
            for (int i = 0; i < tSize; i++) {
                String uid = data.get("u_sid", i).s();
                uidToId.putIfAbsent(uid, null);
            }

            try (Statement stmt = conn.createStatement()) {
                for (String uid : uidToId.keySet()) {
                    ResultSet rs = stmt.executeQuery(
                            "SELECT `id` FROM `" + tableName + "` WHERE `u_sid`='" + escapeSql(uid) + "' LIMIT 1");
                    if (rs.next()) {
                        uidToId.put(uid, rs.getLong("id"));
                    }
                }
            }

            // 2. 按 entity id 分组
            Map<Long, List<Integer>> entityRows = new LinkedHashMap<>();
            for (int i = 0; i < tSize; i++) {
                String uid = data.get("u_sid", i).s();
                Long eid = uidToId.get(uid);
                if (eid != null) {
                    entityRows.computeIfAbsent(eid, k -> new ArrayList<>()).add(i);
                }
            }

            // 3. 使用 TDengine 多表 INSERT 语法批量写入
            StringBuilder sql = new StringBuilder("INSERT INTO ");
            for (Map.Entry<Long, List<Integer>> entry : entityRows.entrySet()) {
                long entityId = entry.getKey();
                String childTable = stableName + "_e_" + entityId;
                sql.append('`').append(childTable).append("` USING `").append(stableName)
                        .append("` TAGS(").append(entityId).append(") VALUES ");
                for (int idx : entry.getValue()) {
                    int t = data.get("t", idx).i();
                    long tsMs = (long) t * 1000L;
                    sql.append('(').append(tsMs).append(',').append(tsMs);
                    for (String prop : props) {
                        PVal val = data.get(prop, idx);
                        sql.append(',').append(formatValue(val));
                    }
                    sql.append(") ");
                }
            }

            try (Statement stmt = conn.createStatement()) {
                stmt.execute(sql.toString());
            } catch (SQLException e) {
                throw new TransactionFailedException(e, tx);
            } finally {
                conn.commit();
            }
            return new AbstractTransaction.Result();
        };
    }

    // ==================== Entity History Query ====================

    private Req execute(EntityHistoryTx tx) {
        return conn -> {
            String tableName = tx.isNode() ? "node" : "rel";
            String stableName = tx.isNode() ? "node_tp" : "rel_tp";
            List<Triple<Integer, Integer, PVal>> res = new ArrayList<>();

            // 1. 根据 u_sid 查找 entity 内部 id
            long entityId = -1;
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery(
                        "SELECT `id` FROM `" + tableName + "` WHERE `u_sid`='" + escapeSql(tx.getEntity()) + "' LIMIT 1");
                if (rs.next()) {
                    entityId = rs.getLong("id");
                }
            }

            if (entityId < 0) {
                EntityHistoryTx.Result result = new EntityHistoryTx.Result();
                result.setHistory(res);
                return result;
            }

            String childTable = stableName + "_e_" + entityId;
            long beginMs = (long) tx.getBeginTime() * 1000L;
            long endMs = (long) tx.getEndTime() * 1000L;

            // 2. 查找 beginTime 时刻生效的记录（max ts <= beginMs）
            long effectiveTs = beginMs;
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery(
                        "SELECT MAX(`ts`) AS max_ts FROM `" + childTable + "` WHERE `ts` <= " + beginMs);
                if (rs.next()) {
                    Timestamp maxTs = rs.getTimestamp("max_ts");
                    if (maxTs != null) {
                        effectiveTs = maxTs.getTime();
                    }
                }
            } catch (SQLException e) {
                // 子表不存在时直接返回空结果
                EntityHistoryTx.Result result = new EntityHistoryTx.Result();
                result.setHistory(res);
                return result;
            }

            // 3. 查询从 effectiveTs 到 endMs 的所有记录
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery(
                        "SELECT `ts`, `" + tx.getProp() + "` FROM `" + childTable +
                                "` WHERE `ts` >= " + effectiveTs + " AND `ts` <= " + endMs + " ORDER BY `ts`");

                int lastTime = -1;
                PVal lastVal = null;
                while (rs.next()) {
                    Timestamp ts = rs.getTimestamp("ts");
                    int t = (int) (ts.getTime() / 1000L);
                    PVal val = getPVal(rs, tx.isNode(), tx.getProp(), tx.getProp());
                    if (lastTime != -1) {
                        res.add(Triple.of(lastTime, t - 1, lastVal));
                        lastTime = t;
                    } else {
                        lastTime = Math.max(t, tx.getBeginTime());
                    }
                    lastVal = val;
                }
                if (lastTime != -1) {
                    res.add(Triple.of(lastTime, tx.getEndTime(), lastVal));
                }
            } catch (SQLException e) {
                throw new TransactionFailedException(e, tx);
            }

            EntityHistoryTx.Result result = new EntityHistoryTx.Result();
            result.setHistory(res);
            return result;
        };
    }

    // ==================== Helper Methods ====================

    private String formatValue(PVal val) {
        switch (val.getType()) {
            case INT:
                return String.valueOf(val.i());
            case FLOAT:
                return String.valueOf(val.f());
            case STRING:
                return "'" + escapeSql(val.s()) + "'";
            default:
                return "NULL";
        }
    }

    private String escapeSql(String s) {
        if (s == null) return "";
        return s.replace("'", "\\'");
    }
}
