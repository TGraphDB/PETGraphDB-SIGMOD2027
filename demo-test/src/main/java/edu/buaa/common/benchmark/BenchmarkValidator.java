package edu.buaa.common.benchmark;

import com.alibaba.fastjson.JSON;
import com.google.common.base.Preconditions;
import edu.buaa.common.transaction.AbstractTransaction;
import edu.buaa.common.transaction.EntityHistoryTx;
import edu.buaa.common.utils.PVal;
import edu.buaa.utils.Helper;
import edu.buaa.utils.Pair;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;

public class BenchmarkValidator {
    private static final String resultPath = Helper.mustEnv("BENCHMARK_DIR");
    private static final int jenkinsId = Integer.parseInt(Helper.envOrDefault("JENKINS_ID", "-1"));
    private final PreparedStatement updateTrial;
    private final File dir;
    private final String gitVersion;
    private final List<BenchmarkReader> files;
    private final Connection conn;

    public static void main(String[] args) throws Exception {
        File dir = new File(resultPath);
        if(!dir.exists()) throw new IllegalArgumentException("benchmark dir not exist.");
        BenchmarkValidator v = new BenchmarkValidator(dir);
        try {
            if (dir.getName().contains("ehistory")) {
                v.historyPrint();
            } else {
                v.validateResult();
            }
        }finally {
            v.addTestTrial();
        }
    }

    private final PreparedStatement updateReqSucc;
    private final PreparedStatement updateReqFail;

    public BenchmarkValidator(File dir) throws Exception {
        this.dir = dir;
        this.gitVersion = Helper.codeGitVersion();
        this.files = this.txResultReaders(Helper.mustEnv("DB_LIST").split(","));
        String user = "postgres";
        String password = "langduhua";
        String dbName = "test_case";
        Class.forName("org.postgresql.Driver");
        conn = DriverManager.getConnection("jdbc:postgresql://master:5432/"+dbName, user, password);
        conn.setAutoCommit(false);
        updateReqSucc = conn.prepareStatement("UPDATE test_case SET test_code_version=?, test_dbs=?, test_results=?, " +
                "success_cnt=success_cnt+1, last_status=?, last_success_version=? WHERE id=?");
        updateReqFail = conn.prepareStatement("UPDATE test_case SET test_code_version=?, test_dbs=?, test_results=?, " +
                "failure_cnt=failure_cnt+1, last_status=?, last_failure_version=? WHERE id=?");
        updateTrial = conn.prepareStatement("UPDATE test_trial SET success_rate=? WHERE jenkins_id=?");
    }

    private void addTestTrial() throws SQLException {
        updateTrial.setInt(1, (int) (cntSucc*100L/(cntSucc+cntFail)));
        updateTrial.setInt(2, jenkinsId);
        updateTrial.addBatch();
        System.out.println(updateTrial);
        updateTrial.executeBatch();
        if(cntSucc%999!=0) updateReqSucc.executeBatch();
        if(cntFail%999!=0) updateReqFail.executeBatch();
        conn.commit();
        conn.close();
    }

    private int cntSucc = 0, cntFail = 0;
    private void updateSuccess(int id, String result) throws Exception {
        updateReqSucc.setString(1, gitVersion);
        updateReqSucc.setObject(2, dbList.toArray(new String[0]));
        updateReqSucc.setString(3, result);
        updateReqSucc.setString(4, "success");
        updateReqSucc.setString(5, gitVersion);
        updateReqSucc.setInt(6, id);
        updateReqSucc.addBatch();
        cntSucc++;
        if(cntSucc%999==0){
            updateReqSucc.executeBatch();
        }
    }

    private void updateFailure(int id, String result) throws Exception {
        updateReqFail.setString(1, gitVersion);
        updateReqFail.setObject(2, dbList.toArray(new String[0]));
        updateReqFail.setString(3, result);
        updateReqFail.setString(4, "failure");
        updateReqFail.setString(5, gitVersion);
        updateReqFail.setInt(6, id);
        updateReqFail.addBatch();
        cntFail++;
        if(cntFail%999==0){
            updateReqFail.executeBatch();
        }
    }

    List<String> dbList = new ArrayList<>();

    private List<BenchmarkReader> txResultReaders(String[] test_dbs) throws IOException {
        List<BenchmarkReader> fileList = new ArrayList<>();
        for(String db : test_dbs){
            File resultFile = new File(dir, db + ".result.json");
            if(resultFile.exists() && resultFile.isFile()) {
                fileList.add(new BenchmarkReader(resultFile, false));
                dbList.add(db);
            }
        }
        return fileList;
    }

    public void validateResult() throws Exception {
        List<AbstractTransaction> tx;
        int cnt = -1, line = 0;
        try{
            do {
                tx = next(this.files);
                line++;
                if (cnt < 0) cnt = tx.size();
                else if (cnt == tx.size()) {
                    validate(tx, line);
                } else if (!tx.isEmpty()) {
                    throw new RuntimeException("size not equal at line " + line + "! expect " + cnt + " but got " + tx.size());
                }
            } while(!tx.isEmpty());
        }catch (RuntimeException | AssertionError e){
            System.out.println("validate line: "+line);
            e.printStackTrace();
        }
    }

    private List<AbstractTransaction> next(List<BenchmarkReader> readers) throws IOException {
        List<AbstractTransaction> result = new ArrayList<>();
        for(BenchmarkReader r : readers){
            if(r.hasNext()) result.add(r.next());
            else{
                System.out.println("EOF "+r.file);
                r.close();
            }
        }
        return result;
    }

    private void validate(List<AbstractTransaction> list, int line) throws Exception {
        AbstractTransaction t = list.get(0);
        Preconditions.checkState(list.stream().allMatch(tx -> tx.getTxType().equals(t.getTxType())));
        Preconditions.checkState(list.stream().allMatch(tx -> tx.getId()==t.getId()));
        StringBuilder sb0 = new StringBuilder();
        StringBuilder sbx = new StringBuilder();
        for (int i=0; i<list.size()-1; i++) {
            for(int j=i+1; j<list.size(); j++){
                AbstractTransaction tx0 = list.get(i);
                AbstractTransaction tx1 = list.get(j);
                try {
                    tx0.validateResult(tx1.getResult());
                    sb0.append(dbList.get(i)).append("=").append(dbList.get(j)).append(", ");
                }catch (Helper.SetNotMath e){
                    sbx.append(dbList.get(i)).append("<>").append(dbList.get(j)).append(' ').append(e.getMessage()).append('\n');
                } catch (RuntimeException e){
                    System.out.println("RuntimeException during "+ dbList.get(i)+" vs "+ dbList.get(j)+" on line "+line);
                    StringWriter sw = new StringWriter();
                    e.printStackTrace(new PrintWriter(sw));
                    sbx.append(dbList.get(i)).append(" ? ").append(dbList.get(j)).append(' ').append(e.getMessage()).append('\n').append(sw);
                }
            }
        }
        if(sbx.length()>0){
            String tStr = JSON.toJSONString(t);
            System.out.println("ERR: NOT Match on line "+line+". "+sb0+"  "+ (tStr.length()<360? tStr : tStr.substring(0, 360)));
            System.out.println(sbx);
            updateFailure(t.getId(), sbx.toString());
        }else{
            updateSuccess(t.getId(), sb0.toString());
        }
    }

    public void historyPrint() throws Exception {
        List<List<AbstractTransaction>> sys = toList(this.files);
        int size = -1;
        for(int i=0; i< sys.size(); i++){
            if(i==0) size=sys.get(i).size();
            else if(size!=sys.get(i).size()){
                System.out.println("expect size "+size+" but got "+sys.get(i).size()+" on "+ dbList.get(i));
                size = Math.min(size, sys.get(i).size());
            }
        }
        for(int line=0; line<size; line++){
            int txId = -1;
            List<List<Pair<Integer, PVal>>> res = new ArrayList<>();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < sys.size(); i++) {
                List<AbstractTransaction> sy = sys.get(i);
                AbstractTransaction tx = sy.get(line);
                txId = tx.getId();
                if (tx instanceof EntityHistoryTx) try{
                    EntityHistoryTx t = ((EntityHistoryTx) tx);
                    List<Pair<Integer, PVal>> r;
                    if(t.getResult()==null) r = Collections.emptyList();
                    else r = t.conv(((EntityHistoryTx.Result) t.getResult()).getHistory());
                    res.add(r);
                    if(i==0) sb.append(t.getProp()).append(" ").append(t.getBeginTime()).append(",").append(t.getEndTime()).append(" (").append(t.getEntity()).append(")  ");
                    sb.append(dbList.get(i)).append("(").append(r.size()).append(")    ");
                }catch (Exception e){
                    System.err.println("Error in "+dbList.get(i));
                    e.printStackTrace();
                    throw e;
                }
            }
            OptionalInt tmp = res.stream().mapToInt(List::size).min();
            if(tmp.isPresent()){
                int ssize = tmp.getAsInt();
//                System.out.print("line "+(line + 1));
                boolean alllEq = true;
                if(!res.stream().allMatch(pairs -> pairs.size()==ssize)) {
                    sb.append("  ------ X");
                    alllEq = false;
                }
                sb.append('\n');
//                for(int i=0; i< sys.size(); i++) System.out.print(fileNames.get(i)+"    ");
                for(int k=0; k<ssize;k++){
                    boolean allEq = true;
                    for(int i=0; i< sys.size(); i++){
                        Pair<Integer, PVal> cur = res.get(i).get(k);
                        sb.append(cur.getKey()).append(" ").append(cur.getValue()).append("    ");
                        if(i>0){
                            Pair<Integer, PVal> pre = res.get(i - 1).get(k);
                            if(!(pre.getKey().equals(cur.getKey()) && eqv(pre.getValue(), cur.getValue()))) allEq=false;
                        }
                    }
                    if(!allEq) alllEq = false;
                    sb.append("   ").append(allEq ? "" : "X").append('\n');
                }
                if(!alllEq) {
                    System.out.println(sb);
                    updateFailure(txId, sb.toString());
                }else{
                    updateSuccess(txId, sb.toString());
                }
            }else{
                updateSuccess(txId, "");
            }
        }
    }

    private boolean eqv(Object pre, Object cur) {
        if(pre instanceof Float && cur instanceof Float){
            int a = (Float.floatToRawIntBits((Float) pre) & 0xffff0000)>>16;
            int b = (Float.floatToRawIntBits((Float) cur) & 0xffff0000)>>16;
            if(Math.abs(a-b)<2) return true;
            float diff = Math.abs(((Float)pre) - ((Float)cur));
            return diff < 0.01;
        }
        return Objects.equals(pre, cur);
    }

    private List<List<AbstractTransaction>> toList(List<BenchmarkReader> txResultReaders) throws IOException {
        List<List<AbstractTransaction>> res = new ArrayList<>();
        for(BenchmarkReader reader : txResultReaders) {
            res.add(txList(reader));
            reader.close();
        }
        return res;
    }

    private List<AbstractTransaction> txList(BenchmarkReader reader){
        List<AbstractTransaction> res = new ArrayList<>();
        while(reader.hasNext()) res.add(reader.next());
        return res;
    }

}
