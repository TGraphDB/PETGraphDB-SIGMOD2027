package edu.buaa.batch;

import edu.buaa.client.MariaDBTemporalTableClient;
import edu.buaa.common.benchmark.SQLMilestoneBuilder;
import edu.buaa.utils.Sync2Async;

import java.io.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class MATemporalTable extends SQLMilestoneBuilder {

    public MATemporalTable() throws Exception {
        super(new MariaDBTemporalTableClient());
    }

    @Override
    public void importStatic() throws Exception {
        File node = dataGen.prepareStaticCSV(true);
        bulkLoad(node, "node", false);
        File edge = dataGen.prepareStaticCSV(false);
        bulkLoad(edge, "rel", false);
    }

    public void bulkLoad(File csv, String tableName, boolean isTp) throws Exception {
        String csvPath = csv.getAbsolutePath().replace('\\', '/');
        String sql = String.format("LOAD DATA INFILE '%s' REPLACE INTO TABLE %s " +
                "FIELDS TERMINATED BY ',' IGNORE 1 LINES " + setTpFields(isTp, csv), csvPath, tableName);
        System.out.println(sql);
        try(Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    private String setTpFields(boolean isTp, File csv) throws IOException {
        if(!isTp) return "";
        String[] csvHeader = csvHeader(csv);
        assert "st".equals(csvHeader[0]);
        assert "et".equals(csvHeader[1]);
        assert "entity".equals(csvHeader[2]);
        StringBuilder sb = new StringBuilder("(@st,@et,@eid");
        for(int i=3; i<csvHeader.length; i++){
            String field = csvHeader[i];
            sb.append(",@").append(field);
        }
        sb.append(") SET st_time=FROM_UNIXTIME(@st),en_time=FROM_UNIXTIME(IF(@et=2147483647,@et,@et+1)),entity=@eid");
        for(int i=3; i<csvHeader.length; i++){
            String field = csvHeader[i];
            sb.append(",").append(field).append("=@").append(field);
        }
        return sb.toString();
    }

    private String[] csvHeader(File csv) throws IOException {
        try(BufferedReader reader = new BufferedReader(new FileReader(csv))){
            String head = reader.readLine();
            if(head!=null) return head.split(",");
            else throw new IOException("csv is empty.");
        }
    }

    @Override
    public void importTemporal() throws Exception {
        File ntp = dataGen.prepareTPCSV(dataSize, startTime, endTime, true, true, false);
        Sync2Async<Void> f = Sync2Async.run("import ntp", () -> bulkLoad(ntp, "node_tp", true));
        File rtp = dataGen.prepareTPCSV(dataSize, startTime, endTime, false, true, false);
        f.join();
        bulkLoad(rtp, "rel_tp", true);
    }

    @Override
    protected void beforeClose() throws SQLException {
        try(Statement stmt = conn.createStatement()) {
            stmt.execute("FLUSH TABLES");
        }
    }
}
