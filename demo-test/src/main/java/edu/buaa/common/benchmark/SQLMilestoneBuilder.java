package edu.buaa.common.benchmark;

import edu.buaa.common.client.AbstractSQLClient;
import edu.buaa.common.utils.TemporalGraphPropertySchema;
import edu.buaa.utils.Helper;
import edu.buaa.utils.TimeTicker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public abstract class SQLMilestoneBuilder extends MilestoneBuilder{

    protected final String serverHost = Helper.mustEnv("DB_HOST"); // hostname of server.
    protected final int serverPort = Integer.parseInt(Helper.mustEnv("DB_PORT")); // hostname of server.
    protected final String dbName = Helper.mustEnv("MILESTONE_NAME");

    protected final Connection conn;
    protected final AbstractSQLClient client;

    public SQLMilestoneBuilder(AbstractSQLClient client) throws Exception {
        super();
        this.client = client;
        this.client.init(serverHost, serverPort, dbName, 1, schema);
        conn = client.connectionPool.take();
        conn.setAutoCommit(true);
    }

    private void createIndexes() throws Exception{
        try (Statement stmt = conn.createStatement()) {
            for(String q : client.createIndexes()){
                System.out.println(q);
                stmt.execute(q);
            }
        }
    }

    @Override
    public void close() throws Exception {
        createIndexes();
        beforeClose();
        client.connectionPool.put(conn);
        client.close();
    }

    protected void beforeClose() throws SQLException {

    }

    /**
     * CSV Format
     * node: id, u_sid, p1, p2, ... (id is generated primary key, type long)
     * edge: id, u_sid, r_from, r_to, p1, p2, ... (r_from, r_to is id in node)
     * tp:   t, entity, p1, p2, ... (if time point. entity is id in node/edge)
     * ti:   st, et, entity, p1, p2, ... (if time interval. entity is id in node/edge)
     */
    public interface CSVExport { // for dataset
        File prepareStaticCSV(boolean isNode) throws Exception;
        File prepareTPCSV(String dataSize, String beginT, String endT, boolean isNode, boolean isTimeInterval, boolean isTimestamp) throws Exception;
    }

    public interface CSVImport{ // for DB client
        void bulkLoad(File csv, boolean isNode, boolean isStatic) throws Exception;
    }
}
