package edu.buaa.common.client;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.ImmutableMap;
import edu.buaa.common.utils.PVal;
import edu.buaa.common.utils.TemporalGraphPropertySchema;

import java.io.File;
import java.sql.*;
import java.util.Map;

public abstract class MariaDbExecutorClient extends AbstractSQLClient {
    private final String user = "root";
    private final String password = "langduhua";

    @Override
    protected void createDbIfNotExist(String serverHost, String dbName) throws SQLException, ClassNotFoundException {
        Class.forName("org.mariadb.jdbc.Driver");
        try (Connection conn = DriverManager.getConnection("jdbc:mariadb://" + serverHost + ":3306/", user, password);
             Statement stmt = conn.createStatement()) {
            conn.setAutoCommit(true);
            ResultSet rs = stmt.executeQuery("SELECT * FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = '"+ this.dbName +"'");
            if (!rs.next()) {
                stmt.execute("CREATE DATABASE "+ this.dbName);
                stmt.execute("USE "+ this.dbName);
                for(String q : createTables()){
                    System.out.println(q);
                    stmt.execute(q);
                }
            }
        }
    }

    @Override
    protected Connection createNormalConnection(String serverHost, String dbName) throws SQLException {
        String dbURL = "jdbc:mariadb://" + serverHost + ":3306/"+dbName;
        Connection con = DriverManager.getConnection(dbURL, user, password);
        con.setAutoCommit(false);
        con.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        return con;
    }

    @Override
    protected void connected(Connection conn) throws Exception{}

    @Override
    public String currentStorageStatus() throws SQLException, InterruptedException {
        Connection conn = connectionPool.take();
        JSONArray arr = new JSONArray();
        try(Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("select table_name as relname, index_length as indexsize, " +
                    "data_length as tablesize from information_schema.TABLES where table_schema='"+this.dbName+"'");
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
            PVal.Type.FLOAT, "FLOAT",
            PVal.Type.STRING, "VARCHAR(256)");

    @Override
    protected String type2SQL(PVal.Type type){
        return type2SQLMap.get(type);
    }
}
