package edu.buaa.common.benchmark;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.Feature;
import com.alibaba.fastjson.parser.ParserConfig;
import edu.buaa.common.transaction.AbstractTransaction;
import edu.buaa.common.transaction.EntityHistoryTx;
import edu.buaa.common.transaction.ImportTemporalDataTx;
import edu.buaa.common.transaction.UpdateTemporalDataTx;
import edu.buaa.common.utils.PFieldList;
import edu.buaa.common.utils.PVal;
import edu.buaa.common.utils.TemporalGraphPropertySchema;
import edu.buaa.dataset.EnergyWriteTxGenerator;
import edu.buaa.dataset.SynWriteTxGenerator;
import edu.buaa.dataset.TrafficWriteTxGenerator;
import edu.buaa.utils.*;


import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class BenchmarkSampler {
    public static void main(String[] args) throws Exception {
        ParserConfig.getGlobalInstance().putDeserializer(PVal.class, new PVal.PValCodec());
        String mode = Helper.mustEnv("SAMPLE_MODE");
        int jenkinsId = Integer.parseInt(Helper.mustEnv("JENKINS_ID"));
        String filePath = Helper.mustEnv("BENCHMARK_FULL_PATH");
        String dataset = Helper.mustEnv("DATASET");
        String reqType = Helper.mustEnv("REQ_TYPE");
        String reqCnt = Helper.mustEnv("REQ_CNT");
        String dataSize = Helper.mustEnv("DATA_SIZE");
        String tpRange = Helper.mustEnv(dataSize);
        String startTime = tpRange.split("~")[0];
        String endTime = tpRange.split("~")[1];
        System.out.println("build time set to "+ startTime +" ~ "+ endTime+" ("+dataSize+")");
        TemporalGraphDataGenerator dataGen = initDataset(dataset);
        int st = dataGen.parseTime(startTime);
        int et = dataGen.parseTime(endTime);
        String user = "postgres";
        String password = "langduhua";
        String dbName = "test_case";
        Class.forName("org.postgresql.Driver");
        File fPath = new File(filePath);
        System.out.println("benchmark file: "+fPath.getAbsolutePath());
        if(fPath.exists()){
            File bak = new File(fPath.getParentFile(),
                    fPath.getName()+"_"+Helper.timeStamp2String((int) (fPath.lastModified()/1000)).
                            replace(":","").replace(" ","_")+".bak");
            if(fPath.renameTo(bak)){
                System.out.println("backup file: "+bak.getAbsolutePath());
            }else{
                System.out.println("backup failed, file override.");
            }
        }
        fPath.getParentFile().mkdirs();
        BenchmarkWriter writer = new BenchmarkWriter(fPath, false);
        try (Connection conn = DriverManager.getConnection("jdbc:postgresql://master:5432/"+dbName, user, password)) {
            Set<String> singleReq = new HashSet<>(Arrays.asList("ehistory","etpc", "aggmax","snapshot", "reachable"));
            if(singleReq.contains(reqType)){
                singleRequest(conn, writer, mode, st, et, dataset, reqType, Integer.parseInt(reqCnt), jenkinsId);
            }else{
                OLTPGen sampler = new OLTPGen(conn, writer, startTime, endTime, dataGen, dataset, Integer.parseInt(reqCnt), jenkinsId);
                sampler.run();
            }
        }finally {
            writer.close();
        }
    }

    private static void singleRequest(Connection conn, BenchmarkWriter writer, String mode, int st, int et, String dataset, String reqType, int reqCnt, int jenkinsId) throws SQLException, IOException {
        conn.setAutoCommit(false);
        PreparedStatement stmt;
        if("debug".equalsIgnoreCase(mode)){
            stmt = debug(conn);
        }else if("extend".equalsIgnoreCase(mode)){
            stmt = extend(conn);
        }else if("regression".equalsIgnoreCase(mode)){
            stmt = regression(conn);
        }else{
            throw new IllegalArgumentException(mode);
        }
        stmt.setInt(1, st);
        stmt.setInt(2, et);
        stmt.setString(3, dataset);
        stmt.setString(4, reqType);
        stmt.setInt(5, reqCnt);
        ResultSet rs = stmt.executeQuery();
        List<Integer> ids = new ArrayList<>();
        while(rs.next()){
            int id = Math.toIntExact(rs.getLong("id"));
            writer.write(txIdConv(id, rs.getString("rq_content")));
            ids.add(id);
        }
        stmt.close();
        stmt = conn.prepareStatement("INSERT INTO test_trial(jenkins_id, sample_mode, requests, dataset, rq_type) VALUES(?,?,?,?,?)");
        stmt.setInt(1, jenkinsId);
        stmt.setString(2, mode);
        stmt.setObject(3, idConv(ids));
        stmt.setString(4, dataset);
        stmt.setString(5, reqType);
        stmt.addBatch();
        stmt.executeBatch();
        stmt.close();
        conn.commit();
    }

    private static int[] idConv(List<Integer> ids) {
        int[] result = new int[ids.size()];
        for(int i=0; i<ids.size(); i++) result[i] = ids.get(i);
        return result;
    }

    private static TemporalGraphDataGenerator initDataset(String dataset) {
        TemporalGraphDataGenerator dataGen;
        if(dataset.equalsIgnoreCase("energy")){
            dataGen = new EnergyWriteTxGenerator();
        }else if(dataset.equalsIgnoreCase("traffic")){
            dataGen = new TrafficWriteTxGenerator();
        }else{
            dataGen = new SynWriteTxGenerator();
        }
        dataGen.init(new File(Helper.mustEnv("DIR_DATA"), dataset), TemporalGraphPropertySchema.load(dataset));
        return dataGen;
    }

    private static AbstractTransaction txIdConv(int id, String rq_content) {
        AbstractTransaction tx = JSONObject.parseObject(rq_content, AbstractTransaction.class, Feature.SupportAutoType);
        tx.setId(id);
        return tx;
    }

    static PreparedStatement extend(Connection con) throws SQLException {
        return con.prepareStatement("SELECT id, rq_content FROM test_case WHERE success_cnt=0 AND failure_cnt=0 AND " +
                "st>=? AND et<=? AND dataset=? AND rq_type=? LIMIT ?");
    }

    static PreparedStatement regression(Connection con) throws SQLException {
        return con.prepareStatement("SELECT id, rq_content FROM test_case WHERE last_status='success' AND " +
                "st>=? AND et<=? AND dataset=? AND rq_type=? LIMIT ?");
    }

    static PreparedStatement debug(Connection con) throws SQLException {
        return con.prepareStatement("SELECT id, rq_content FROM test_case WHERE last_status='failure' AND " +
                "st>=? AND et<=? AND dataset=? AND rq_type=? LIMIT ?");
    }

    static class OLTPGen{
        private final Connection conn;
        private final BenchmarkWriter writer;
        private final String beginT;
        private final String endT;
        private final int st;
        private final int et;
        private final TemporalGraphDataGenerator dataGen;
        private final String dataset;
        private final int reqCnt;
        private final int jenkinsId;
        private final List<AbstractTransaction> txList = new ArrayList<>();
        private final List<Integer> arr;
        private final Map<String, TemporalValue<Integer>> tg = new HashMap<>();
        private final BenchmarkBuilder.BenchmarkRandomGen dataRand;

        public OLTPGen(Connection conn, BenchmarkWriter writer, String beginT, String endT, TemporalGraphDataGenerator dataGen, String dataset, int reqCnt, int jenkinsId) throws IOException {
            this.conn = conn;
            this.writer = writer;
            this.beginT = beginT;
            this.endT = endT;
            this.st = dataGen.parseTime(beginT);
            this.et = dataGen.parseTime(endT);
            this.dataGen = dataGen;
            this.dataset = dataset;
            this.reqCnt = reqCnt;
            this.jenkinsId = jenkinsId;
            arr = JSON.parseArray("["+Helper.mustEnv("rq_distribution".toUpperCase())+"]", Integer.class);
            dataRand = dataGen.randomGen();
        }

        public void run() throws IOException, SQLException {
            txList.addAll(appendTx(arr.get(0), 100));
            float entityCnt = 0.1f;
            List<AbstractTransaction> eh = ehistory(arr.get(1), (int) (arr.get(1)*entityCnt));
            txList.addAll(eh);
            txList.addAll(update(arr.get(2), eh));
            Collections.shuffle(txList);
            writer.write(txList);
        }

        private List<AbstractTransaction> appendTx(int reqCnt, int WRITE_TX_SIZE) throws IOException {
            List<AbstractTransaction> r = new ArrayList<>();
            final Iterator<ImportTemporalDataTx> nit = dataGen.readNodeTemporal(endT, WRITE_TX_SIZE);
            final Iterator<ImportTemporalDataTx> eit = dataGen.readRelTemporal(endT, WRITE_TX_SIZE);
            for(int i=0; i<reqCnt && (nit.hasNext() || eit.hasNext()); i++){
                ImportTemporalDataTx tx;
                if(nit.hasNext() && eit.hasNext()) {
                    tx = ThreadLocalRandom.current().nextBoolean() ? nit.next() : eit.next();
                }else if(nit.hasNext()){
                    tx = nit.next();
                }else { //eit.hasNext()){
                    tx = eit.next();
                }
                r.add(tx);
            }
            return r;
        }

        private List<AbstractTransaction> ehistory(int reqCnt, int entityCnt) throws IOException, SQLException {
            List<AbstractTransaction> r = new ArrayList<>();
            PreparedStatement stmt = conn.prepareStatement("SELECT id, rq_content FROM test_case WHERE " +
                    "st>=? AND et<=? AND dataset=? AND rq_type=? LIMIT ?");
            stmt.setInt(1, st);
            stmt.setInt(2, et);
            stmt.setString(3, dataset);
            stmt.setString(4, "ehistory");
            stmt.setInt(5, reqCnt);
            ResultSet rs = stmt.executeQuery();
            int eCnt = 0;
            Random rand = ThreadLocalRandom.current();
            while(rs.next()){
                int id = Math.toIntExact(rs.getLong("id"));
                EntityHistoryTx tx = (EntityHistoryTx) txIdConv(id, rs.getString("rq_content"));
                if(eCnt<entityCnt){
                    eCnt++;
                }else{
                    EntityHistoryTx tt = (EntityHistoryTx) r.get(rand.nextInt(r.size()));
                    tx.setEntity(tt.getEntity());
                    tx.setNode(tt.isNode());
                    tx.setProp(tt.getProp());
                    tx.setId(-tx.getId());
                }
                r.add(tx);
            }
            stmt.close();
            return r;
        }

        private List<AbstractTransaction> update(int cnt, List<AbstractTransaction> eh) {
            List<AbstractTransaction> r = new ArrayList<>();
            Random rand = ThreadLocalRandom.current();
            for(int i=0; i<cnt; i++){
                EntityHistoryTx tt = (EntityHistoryTx) eh.get(rand.nextInt(eh.size()));
                PVal v = dataRand.valueRange(tt.isNode(), tt.getProp()).getKey();
                Pair<Integer, Integer> ti = calcTimeInterval(st, et, rand.nextFloat(), rand.nextInt(3), dataRand.snapshotInterval(tt.isNode()));
                r.add(update(tt.getEntity(), tt.getProp(), ti.getKey(), ti.getValue(), v.getVal()));
            }
            return r;
        }

        private UpdateTemporalDataTx update(String entity, String prop, int st, int et, Object val) {
            UpdateTemporalDataTx tx = new UpdateTemporalDataTx();
            PFieldList data = new PFieldList();
            data.add("u_sid", entity);
            data.add("st", st);
            data.add("et", et);
            data.add(prop, val);
            return tx;
        }

        private Pair<Integer, Integer> calcTimeInterval(int timeBegin, int timeEnd, float p, int length, int snapshotInterval) {
            int expectMinLen = (length + 1) * snapshotInterval;
            if(timeEnd - timeBegin > expectMinLen) {
                timeEnd -= expectMinLen;
            }else {
                return Pair.of(timeBegin, timeEnd);
            }
            int st = (int) (timeBegin + (timeEnd-timeBegin)*p);
            int et = st + length * snapshotInterval;
            return Pair.of(st, et);
        }
    }


}
