package edu.buaa.common.benchmark;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.Feature;
import com.alibaba.fastjson.parser.ParserConfig;
import edu.buaa.common.transaction.*;
import edu.buaa.common.utils.PVal;
import edu.buaa.utils.Helper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.*;
import java.util.Arrays;
import java.util.List;

public class BenchmarkImporter {
    private static final String serverHost = "localhost";

    public static void main(String[] args) throws Exception {
        ParserConfig.getGlobalInstance().putDeserializer(PVal.class, new PVal.PValCodec());
        String user = "postgres";
        String password = "langduhua";
        String dbName = "test_case";
        Class.forName("org.postgresql.Driver");
        try (Connection conn = DriverManager.getConnection("jdbc:postgresql://" + serverHost + ":5432/"+dbName, user, password)) {
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
                    "test_code_version char(16)," + // --哪个版本代码上跑的测试、运行的校验\n
                    "test_dbs text[]," + // --测试了哪些数据库\n
                    "test_results text," + // --结果哪里不一致\n
                    "success_cnt integer default 0," + // --累计历史测试中正确性一致的次数\n
                    "failure_cnt integer default 0," + // --累计历史测试中正确性不一致的次数\n
                    "last_status char(16)," + //最后一次测试状态
                    "last_success_version char(16)," + // --最后一次成功是哪个版本？\n
                    "last_failure_version char(16)," + // --最后一次失败是哪个版本？\n
                    "last_update_t timestamp default CURRENT_TIMESTAMP)" // --更新时间
            );
            stmt.execute("CREATE TABLE IF NOT EXISTS test_trial (" +
                    "jenkins_id integer," +
                    "dataset char(16)," +
                    "sample_mode char(16)," +
                    "rq_type char(16)," +
                    "requests bigint[]," +
                    "success_rate integer," +
                    "update_t timestamp default CURRENT_TIMESTAMP)"
            );
            conn.setAutoCommit(false);
            try(PreparedStatement pstmt = conn.prepareStatement(
                    "INSERT INTO test_case (dataset, gen_code_version, rq_type, st, et, rq_content) values(?,?,?,?,?,?)")){
                String codeVersion = Helper.codeGitVersion();
                importBenchmarks(pstmt, 9999, codeVersion.equals("NoGit")? "init": codeVersion);
            }
            conn.commit();
        }
    }

    private static void importBenchmarks(PreparedStatement pstmt, int bulkSize, String gitVersion) throws IOException, SQLException {
        File bFolder = new File("D:\\tgraph\\jenkins");
        List<String> dataset = Arrays.asList("syn"); // "energy", "traffic"
        List<String> rq = Arrays.asList("ehistory", "snapshot", "aggmax"); //, "update",,"aggdur", "etpc", "reachable"
        int batchCnt = 0;
        for(String req : rq) {
            for (String data : dataset) {
                File in = new File(bFolder, "b_" + data + "_T.all_" + req + "_10000\\benchmark.json");
                System.out.println(in);
                try(BufferedReader reader = new BufferedReader(new FileReader(in))){
                    String line;
                    while((line= reader.readLine())!=null){
                        AbstractTransaction tx = JSONObject.parseObject(line, AbstractTransaction.class, Feature.SupportAutoType);
                        int st = -1;
                        int et = Integer.MAX_VALUE;
                        if(tx instanceof EntityHistoryTx){
                            st = ((EntityHistoryTx) tx).getBeginTime();
                            et = ((EntityHistoryTx) tx).getEndTime();
                        }else if(tx instanceof SnapshotQueryTx){
                            st = ((SnapshotQueryTx) tx).getTimestamp();
                            et = ((SnapshotQueryTx) tx).getTimestamp();
                        }else if(tx instanceof SnapshotAggrDurationTx){
                            st = ((SnapshotAggrDurationTx) tx).getT0();
                            et = ((SnapshotAggrDurationTx) tx).getT1();
                        }else if(tx instanceof SnapshotAggrMaxTx){
                            st = ((SnapshotAggrMaxTx) tx).getT0();
                            et = ((SnapshotAggrMaxTx) tx).getT1();
                        }else if(tx instanceof EntityTemporalConditionTx){
                            st = ((EntityTemporalConditionTx) tx).getT0();
                            et = ((EntityTemporalConditionTx) tx).getT1();
                        }else if(tx instanceof ReachableAreaQueryTx){
                            st = ((ReachableAreaQueryTx) tx).getDepartureTime();
                            et = ((ReachableAreaQueryTx) tx).getDepartureTime() + ((ReachableAreaQueryTx) tx).getTravelTime();
                        }else{
                            System.out.println(tx.getTxType());
                        }
//                        System.out.println(line);
                        pstmt.setString(1, data);
                        pstmt.setString(2, gitVersion);
                        pstmt.setString(3, req);
                        pstmt.setInt(4, st);
                        pstmt.setInt(5, et);
                        pstmt.setString(6, line);
                        pstmt.addBatch();
                        batchCnt++;
                        if(batchCnt>=bulkSize){
                            pstmt.executeBatch();
                            System.out.print(".");
                            batchCnt = 0;
                        }
                    }
                }
                System.out.println();
            }
        }
        pstmt.executeBatch();
    }

}
