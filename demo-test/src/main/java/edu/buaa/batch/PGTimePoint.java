package edu.buaa.batch;

import edu.buaa.client.PostgreSQLTimePointClient;
import edu.buaa.common.benchmark.SQLMilestoneBuilder;
import edu.buaa.utils.Sync2Async;

import java.io.File;
import java.sql.Connection;
import java.sql.Statement;

public class PGTimePoint extends SQLMilestoneBuilder {

    public PGTimePoint() throws Exception {
        super(new PostgreSQLTimePointClient());
    }

    @Override
    public void importStatic() throws Exception {
        File node = dataGen.prepareStaticCSV(true);
        bulkLoad(node.getAbsolutePath(), "node", false);
        File edge = dataGen.prepareStaticCSV(false);
        bulkLoad(edge.getAbsolutePath(), "rel", false);
    }

    public void bulkLoad(String csvPath, String tableName, boolean isTp) throws Exception {
        String cmd = isTp ? "mlr --csv cut -x -f et" : "TYPE";
        String sql = String.format("COPY %s FROM PROGRAM 'CMD /c \"%s %s\"' WITH (FORMAT CSV, HEADER TRUE)", tableName, cmd, csvPath);
        System.out.println(sql);
        try(Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    @Override
    public void importTemporal() throws Exception {
        File ntp = dataGen.prepareTPCSV(dataSize, startTime, endTime, true, true, false);
        Sync2Async<Void> f = Sync2Async.run("import ntp", () -> bulkLoad(ntp.getAbsolutePath(), "node_tp", true));
        File rtp = dataGen.prepareTPCSV(dataSize, startTime, endTime, false, true, false);
        f.join();
        bulkLoad(rtp.getAbsolutePath(), "rel_tp", true);
    }
}
