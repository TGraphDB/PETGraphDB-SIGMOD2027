package edu.buaa.test.management;


import com.alibaba.fastjson.JSON;
import edu.buaa.common.transaction.AbstractTransaction;

import java.io.IOException;
import java.sql.*;

public class TestManager {

    private static final String serverHost = "master";
    String user = "postgres";
    String password = "langduhua";

    private final String db;
    private final String codeVersion;
    private final String device;
    private final int trialId;
    private final Integer jenkinsId;

    Connection conn;
    PreparedStatement addCase, addTrial, addCpResult;

    public TestManager(String dbName, String codeVersion, String device, int trialId, Integer jenkinsId) throws Exception {
        db = dbName;
        this.codeVersion = codeVersion;
        this.device = device;
        this.trialId = trialId;
        this.jenkinsId = jenkinsId;
        Class.forName("org.postgresql.Driver");
        this.conn = DriverManager.getConnection("jdbc:postgresql://" + serverHost + ":5432/test_case", user, password);
        conn.setAutoCommit(true);
        Statement stmt = conn.createStatement();
        stmt.execute("CREATE TABLE IF NOT EXISTS test_case(" +
                "id BIGSERIAL PRIMARY KEY," +
                "dataset char(16)," + // --哪个数据集的请求
                "gen_code_version char(16)," + // --哪个版本代码生成的请求
                "rq_type char(16)," + // --请求类型：ehistory, snapshot, etpc, aggmax, aggdur, reachable\n
                "st integer," + // --开始时间（如果snapshot则为时间戳）\n
                "et integer," + // --结束时间（如果snapshot则忽略）\n
                "rq_content text," + // --请求内容\n
                "success_cnt integer default 0," + // --累计历史测试中正确性一致的次数\n
                "failure_cnt integer default 0," + // --累计历史测试中正确性不一致的次数\n
                "last_status char(16)," + //最后一次测试状态
                "last_success_version char(16)," + // --最后一次成功是哪个版本？\n
                "last_failure_version char(16)," + // --最后一次失败是哪个版本？\n
                "last_update_t timestamp default CURRENT_TIMESTAMP)" // --更新时间
        );
        stmt.execute("CREATE TABLE IF NOT EXISTS test_trial (" +
                "trial_jenkins_id integer," +
                "dataset char(16)," +
                "sample_mode char(16)," +
                "rq_type char(16)," +
                "test_code_version char(16)," + // --哪个版本代码上跑的测试、运行的校验\n
                "test_dbs text[]," + // --测试了哪些数据库\n
                "requests bigint[]," +
                "common_size integer[]," +
                "common_rate integer[]," +
                "update_t timestamp default CURRENT_TIMESTAMP)"
        );
        stmt.execute("CREATE TABLE IF NOT EXISTS test_detail(" +
                "trial_jenkins_id integer," +
                "test_jenkins_id integer," +
                "case_id BIGINT," +
                "db char(16)," + // --在哪个数据库测试的
                "create_t timestamp default CURRENT_TIMESTAMP, " + // --更新时间
                "code_version char(16)," + // --哪个版本代码
                "device char(16),"+
                "status char(16)," + //测试状态
                "execute_time integer default -1," + // --执行时间\n
                "tx_time integer default -1," + // --server测量的执行时间\n
                "wait_time integer default -1," + // --等待时间\n
                "result_size integer default -1,"+
                "result text," +
                "err_msg text)"// --结果\n
        );
        addTrial = conn.prepareStatement(
                "INSERT INTO test_case (dataset, gen_code_version, rq_type, st, et, rq_content) values(?,?,?,?,?,?)");
        addCase = conn.prepareStatement(
                "INSERT INTO verified_case values(?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
        );
        addCpResult = conn.prepareStatement(
                "INSERT INTO compare_result values(?,?,?,?,?,?,?,?,?,?)"
        );
        TestManager tm = this;
        Thread bgCommitThread = new Thread(() -> {
            try {
                while(shouldRun){
                    Thread.sleep(1000);
                    tm.commit();
                }
            } catch (SQLException | InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("bgCommit thread exit.");
        });
        bgCommitThread.setName("TestManager BG commit");
        bgCommitThread.setDaemon(true);
        bgCommitThread.start();
    }
    private volatile boolean shouldRun = true;
    private volatile boolean needCommit = false;
    private int cnt = 0;
    public synchronized void addCase(AbstractTransaction tx, AbstractTransaction.Result result, AbstractTransaction.Metrics metrics) throws SQLException {
        addCase.setLong(1, tx.getId());
        addCase.setString(2, db);
        addCase.setTimestamp(3, new Timestamp(metrics.getSendTime()));
        addCase.setString(4, codeVersion);
        addCase.setInt(5, trialId);
        addCase.setInt(6, jenkinsId);
        addCase.setString(7, device);
        addCase.setString(8, metrics.isTxSuccess()?"ok":"failed");
        addCase.setInt(9, metrics.getExeTime());
        addCase.setInt(10, -1);
        addCase.setInt(11, metrics.getWaitTime());
        addCase.setInt(12, metrics.getReturnSize());
        addCase.setString(13, JSON.toJSONString(result));
        addCase.setString(14, metrics.getErrMsg());
        addCase.addBatch();
        cnt++;
        needCommit = true;
        if(cnt % 9==0) {
            addCase.executeBatch();
            needCommit = false;
        }
    }

    public void addCpResult(int id, String db1, long t1, String v1, String db2, long t2, String v2, int common, int only1, int only2 ) throws SQLException {
        addCpResult.setLong(1, id);
        addCpResult.setString(2, db1);
        addCpResult.setTimestamp(3, new Timestamp(t1));
        addCpResult.setString(4, v1);
//        addCpResult;
    }

    public void addTGraphIndexDetail(String detail) throws SQLException {
        PreparedStatement sql = conn.prepareStatement("UPDATE \"Jenkins-Build\" SET index_detail=? WHERE \"ID\"=?");
        sql.setString(1, detail);
        sql.setInt(2, jenkinsId);
        System.out.println("META DB BUILD/INDEX: "+detail);
        sql.executeUpdate();
    }

    public void addSpaceDetail(String detail) throws SQLException {
        PreparedStatement sql = conn.prepareStatement("UPDATE \"Jenkins-Build\" SET space_detail=? WHERE \"ID\"=?");
        sql.setString(1, detail);
        sql.setInt(2, jenkinsId);
        System.out.println("META DB BUILD/SPACE: "+detail);
        sql.executeUpdate();
    }

    private synchronized void commit() throws SQLException {
        try {
            if(needCommit && (cnt % 9 > 0)) {
                addCase.executeBatch();
                needCommit = false;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw e;
        }
    }

    public void close() throws IOException {
        try {
            this.commit();
            shouldRun = false;
            conn.close();
            System.out.println("test manager closed. add "+cnt+" cases.");
        } catch (SQLException e) {
            e.printStackTrace();
            throw new IOException(e);
        }
    }

    public void updateDuration(String tableName, long beginTime) throws SQLException {
        int dur = (int) ((System.currentTimeMillis() - beginTime) / 1000);
        PreparedStatement sql = conn.prepareStatement("UPDATE \"Jenkins-"+tableName+"\" SET duration=? WHERE \"ID\"=?");
        sql.setInt(1, dur);
        sql.setInt(2, jenkinsId);
        System.out.println("META DB DURATION: "+dur+" SECONDS");
        sql.executeUpdate();
    }
}
