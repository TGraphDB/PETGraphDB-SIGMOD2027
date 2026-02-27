package edu.buaa.common.client;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.ImmutableMap;
import edu.buaa.common.utils.PVal;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

public abstract class PostgreSqlExecutorClient extends AbstractSQLClient {
    private final String user = "postgres";
    private final String password = "langduhua";

    @Override
    protected void createDbIfNotExist(String serverHost, String dbName) throws SQLException, ClassNotFoundException {
        Class.forName("org.postgresql.Driver");
        try (Connection conn = DriverManager.getConnection("jdbc:postgresql://" + serverHost + ":5432/", user, password);
             Statement stmt = conn.createStatement()) {
            conn.setAutoCommit(true);
            ResultSet rs = stmt.executeQuery("select database.datname from pg_catalog.pg_database database where database.datname = '"+dbName+"'");
            if (!rs.next()) stmt.execute("CREATE database "+dbName);
        }
    }

    @Override
    protected Connection createNormalConnection(String serverHost, String dbName) throws SQLException {
        Connection con = DriverManager.getConnection("jdbc:postgresql://" + serverHost + ":5432/"+dbName, user, password);
        con.setAutoCommit(false);
        con.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        return con;
    }

    @Override
    protected void connected(Connection conn) throws Exception {
        try (Statement stmt = conn.createStatement()) {
            for(String q : createTables()){
                System.out.println(q);
                stmt.execute(q);
            }
        }
        conn.commit();
    }

    @Override
    public String currentStorageStatus() throws InterruptedException, SQLException {
        String sql = "select t.relname as relname,pg_relation_size(t.relid) as tablesize," +
                "sum(pg_relation_size(i.indexrelid)) as indexsize from " +
                "pg_stat_user_tables as t left join pg_stat_user_indexes as i on t.relid=i.relid "+
                "group by t.relname, t.relid";
        Connection conn = connectionPool.take();
        JSONArray arr = new JSONArray();
        try(Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            while(rs.next()) {
                JSONObject obj = new JSONObject();
                obj.put("relname", rs.getString("relname"));
                obj.put("tablesize", rs.getLong("tablesize"));
                obj.put("indexsize", rs.getLong("indexsize"));
                arr.add(obj);
            }
        }
        connectionPool.put(conn);
        return arr.toJSONString();
    }

    private final Map<PVal.Type, String> type2SQLMap = ImmutableMap.of(
            PVal.Type.INT, "INTEGER",
            PVal.Type.FLOAT, "REAL",
            PVal.Type.STRING, "VARCHAR(256)");

    @Override
    protected String type2SQL(PVal.Type type){
        return type2SQLMap.get(type);
    }
}
