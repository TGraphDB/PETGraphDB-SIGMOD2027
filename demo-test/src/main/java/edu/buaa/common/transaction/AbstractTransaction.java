package edu.buaa.common.transaction;

import org.apache.commons.lang3.tuple.Pair;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

public abstract class AbstractTransaction {
    public enum TxType{
        tx_index_tgraph_aggr_max(false),
        tx_index_tgraph_aggr_duration(false),
        tx_index_tgraph_temporal_condition(false),
        tx_import_static_data(false),
        tx_import_temporal_data(false),
        tx_update_temporal_data(false),
        tx_query_reachable_area(true),
        tx_query_node_neighbor_road(true),
        tx_query_road_earliest_arrive_time_aggr(true),
        tx_query_snapshot(true),
        tx_query_snapshot_aggr_max(true),
        tx_query_snapshot_aggr_duration(true),
        tx_query_road_by_temporal_condition(true),
        tx_query_entity_history(true);
        private boolean isReadTx;
        TxType(boolean isReadTx){
            this.isReadTx = isReadTx;
        }
        public boolean isReadTx() {
            return isReadTx;
        }
    }

    private static int idSeq = 0;
    public int id;
    private TxType txType;
    private Metrics metrics;
    private Result result;
    private int section = -1; // used when append tp. to ensure time asc for each entity.

    public AbstractTransaction(){
        this.id = idSeq++;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getSection() {
        return section;
    }

    public void setSection(int section) {
        this.section = section;
    }

    public TxType getTxType() {
        return txType;
    }

    public void setTxType(TxType txType) {
        this.txType = txType;
    }

    public Metrics getMetrics() {
        return metrics;
    }

    public void setMetrics(Metrics metrics) {
        this.metrics = metrics;
    }

    public Result getResult() {
        return result;
    }

    public void setResult(Result result) {
        this.result = result;
    }

    public boolean validateResult(Result result){return false;}

    public static class Metrics{
        private boolean txSuccess;
        private int waitTime; // duration, in milliseconds
        private long sendTime; // timestamp, in milliseconds
        private int exeTime; // duration, in milliseconds
        private int connId;
        private int reqSize; // user defined value, maybe bytes or rows
        private int returnSize; // user defined value, maybe bytes or rows

        public int getRetryCnt() {
            return retryCnt;
        }

        public void setRetryCnt(int retryCnt) {
            this.retryCnt = retryCnt;
        }

        private int retryCnt;
        private long lockWaitTime = 0;
        private transient long startWaitLockTime;
        private String errMsg;

        public String getErrMsg() {
            return errMsg;
        }

        public void setErrMsg(String errMsg){
            this.errMsg = errMsg;
        }

        public void setErrMsg(Throwable err) {
            final StringWriter sw = new StringWriter();
            final PrintWriter pw = new PrintWriter(sw, true);
            err.printStackTrace(pw);
            this.errMsg = sw.getBuffer().toString();
        }

        public void startAcquireLock() {
            startWaitLockTime = System.currentTimeMillis();
        }

        public void finishAcquireLock() {
            lockWaitTime += (System.currentTimeMillis() - startWaitLockTime);
        }

        public long getLockWaitTime() {
            return lockWaitTime;
        }

        public boolean isTxSuccess() {
            return txSuccess;
        }

        public void setTxSuccess(boolean txSuccess) {
            this.txSuccess = txSuccess;
        }

        public int getExeTime() {
            return exeTime;
        }

        public void setExeTime(int exeTime) {
            this.exeTime = exeTime;
        }

        public int getWaitTime() {
            return waitTime;
        }

        public void setWaitTime(int waitTime) {
            this.waitTime = waitTime;
        }

        public long getSendTime() {
            return sendTime;
        }

        public void setSendTime(long sendTime) {
            this.sendTime = sendTime;
        }

        public int getConnId() {
            return connId;
        }

        public void setConnId(int connId) {
            this.connId = connId;
        }

        public int getReqSize() {
            return reqSize;
        }

        public void setReqSize(int reqSize) {
            this.reqSize = reqSize;
        }

        public int getReturnSize() {
            return returnSize;
        }

        public void setReturnSize(int returnSize) {
            this.returnSize = returnSize;
        }
    }

    public static class Result{

    }

    public enum ConflictType {
        UNSUPPORTED,    // 不支持对比
        UNKNOWN,        // 需要同时知道数据库内容才能知道是否冲突
        FALSE,          // 无冲突
        TRUE,           // 有冲突
    }

    private static HashSet<TxType> unsupportedTypes, allEntityTypes, unknownEntityTypes;

    static {
        unsupportedTypes = new HashSet<>();
        unsupportedTypes.add(TxType.tx_index_tgraph_temporal_condition);
        unsupportedTypes.add(TxType.tx_index_tgraph_aggr_duration);
        unsupportedTypes.add(TxType.tx_index_tgraph_aggr_max);
        unsupportedTypes.add(TxType.tx_import_static_data);
        allEntityTypes = new HashSet<>();
        allEntityTypes.add(TxType.tx_query_snapshot);
        allEntityTypes.add(TxType.tx_query_snapshot_aggr_max);
        allEntityTypes.add(TxType.tx_query_snapshot_aggr_duration);
        allEntityTypes.add(TxType.tx_query_road_by_temporal_condition);
        unknownEntityTypes = new HashSet<>();
        unknownEntityTypes.add(TxType.tx_query_reachable_area);
    }

    public static void setUnsupportedTypes(HashSet<TxType> unsupportedTypes) {
        AbstractTransaction.unsupportedTypes = new HashSet<>();
        if (unsupportedTypes != null) {
            AbstractTransaction.unsupportedTypes.addAll(unsupportedTypes);
        }
    }

    public static void setAllEntityTypes(HashSet<TxType> allEntityTypes) {
        AbstractTransaction.allEntityTypes = new HashSet<>();
        AbstractTransaction.allEntityTypes.addAll(allEntityTypes);
    }

    public static void setUnknownEntityTypes(HashSet<TxType> unknownEntityTypes) {
        AbstractTransaction.unknownEntityTypes = new HashSet<>();
        AbstractTransaction.unknownEntityTypes.addAll(unknownEntityTypes);
    }

    private static boolean intersect(Pair<Integer, Integer> interval1, Pair<Integer, Integer> interval2) {
        return interval1.getLeft() <= interval2.getRight() && interval2.getLeft() <= interval1.getRight();
    }

    private static boolean intersect(HashMap<String, ArrayList<Pair<Integer, Integer>>> info1, HashMap<String, ArrayList<Pair<Integer, Integer>>> info2) {
        for (Map.Entry<String, ArrayList<Pair<Integer, Integer>>> entry : info1.entrySet()) {
            ArrayList<Pair<Integer, Integer>> intervals1 = entry.getValue();
            ArrayList<Pair<Integer, Integer>> intervals2 = info2.get(entry.getKey());
            if (intervals2 == null) continue;
            for (Pair<Integer, Integer> interval1 : intervals1) {
                for (Pair<Integer, Integer> interval2 : intervals2) {
                    if (intersect(interval1, interval2)) return true;
                }
            }
        }
        return false;
    }

    private static boolean intersectConsideringEntity(HashMap<String, HashMap<String, ArrayList<Pair<Integer, Integer>>>> infoWithId1, HashMap<String, HashMap<String, ArrayList<Pair<Integer, Integer>>>> infoWithId2) {
        for (Map.Entry<String, HashMap<String, ArrayList<Pair<Integer, Integer>>>> info1Entry : infoWithId1.entrySet()) {
            HashMap<String, ArrayList<Pair<Integer, Integer>>> info1 = info1Entry.getValue();
            HashMap<String, ArrayList<Pair<Integer, Integer>>> info2 = infoWithId2.get(info1Entry.getKey());
            if (info2 == null) continue;
            if (intersect(info1, info2)) {
                return true;
            }
        }
        return false;
    }

    public static ConflictType isConflict(AbstractTransaction transaction1, AbstractTransaction transaction2, boolean bigLock, boolean readLock) {
        // 首先排除不比对的事务
        if (unsupportedTypes.contains(transaction1.getTxType()) || unsupportedTypes.contains(transaction2.getTxType())) {
            return ConflictType.UNSUPPORTED;
        }
        // 如果都是读事务则不冲突
        if (transaction1.getTxType().isReadTx && transaction2.getTxType().isReadTx) {
            return ConflictType.FALSE;
        }
        // 如果不加读锁，则只要有读事务就不冲突
        if (!readLock && (transaction1.getTxType().isReadTx || transaction2.getTxType().isReadTx)) {
            return ConflictType.FALSE;
        }
        // 如果一个涉及点一个涉及边，则显然不会冲突
        if (transaction1.infoIsNode() != transaction2.infoIsNode()) {
            return ConflictType.FALSE;
        }
        // 分类讨论
        if (bigLock) {
            if (allEntityTypes.contains(transaction1.getTxType()) || allEntityTypes.contains(transaction2.getTxType())) {
                return ConflictType.TRUE;
            }
            if (unknownEntityTypes.contains(transaction1.getTxType()) || unknownEntityTypes.contains(transaction2.getTxType())) {
                return ConflictType.UNKNOWN;
            }
            if (Collections.disjoint(transaction1.getEntities(), transaction2.getEntities())) {
                return ConflictType.FALSE;
            }
            else {
                return ConflictType.TRUE;
            }
        }
        else {
            if (allEntityTypes.contains(transaction1.getTxType()) || allEntityTypes.contains(transaction2.getTxType())) {
                if (intersect(transaction1.getFineGrainedInfo(), transaction2.getFineGrainedInfo())) {
                    return ConflictType.TRUE;
                }
                else {
                    return ConflictType.FALSE;
                }
            }
            if (unknownEntityTypes.contains(transaction1.getTxType()) || unknownEntityTypes.contains(transaction2.getTxType())) {
                if (intersect(transaction1.getFineGrainedInfo(), transaction2.getFineGrainedInfo())) {
                    return ConflictType.UNKNOWN;
                }
                else {
                    return ConflictType.FALSE;
                }
            }
            if (intersectConsideringEntity(transaction1.getFineGrainedInfoWithEntity(), transaction2.getFineGrainedInfoWithEntity())) {
                return ConflictType.TRUE;
            }
            else {
                return ConflictType.FALSE;
            }
        }
    }

    protected abstract boolean infoIsNode();
    // 需要返回所有涉及的属性和其上的时间段
    protected abstract HashMap<String, ArrayList<Pair<Integer, Integer>>> getFineGrainedInfo();
    // 对于确定涉及图部分有id的entity的事务（即不在unknownEntityType和allEntityType类的事务），返回如下数据结构
    protected abstract HashMap<String, HashMap<String, ArrayList<Pair<Integer, Integer>>>> getFineGrainedInfoWithEntity();
    protected abstract HashSet<String> getEntities();
}
