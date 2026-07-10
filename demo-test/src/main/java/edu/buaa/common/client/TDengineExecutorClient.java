package edu.buaa.common.client;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.ImmutableMap;
import edu.buaa.common.utils.PVal;
import edu.buaa.utils.Helper;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

public abstract class TDengineExecutorClient extends AbstractSQLClient {
    private final String user = Helper.envOrDefault("DB_USER", "root");
    private final String password = Helper.envOrDefault("DB_PASSWORD", "taosdata");
    private final int port = Integer.parseInt(Helper.envOrDefault("TDENGINE_PORT", "6041"));

    @Override
    protected void createDbIfNotExist(String serverHost, String dbName) throws SQLException, ClassNotFoundException {
        Class.forName("com.taosdata.jdbc.rs.RestfulDriver");
        String url = "jdbc:TAOS-RS://" + serverHost + ":" + port;
        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE DATABASE IF NOT EXISTS `" + dbName + "` PRECISION 'ms' KEEP 365000");
        }
    }

    @Override
    protected Connection createNormalConnection(String serverHost, String dbName) throws SQLException {
        Connection con = DriverManager.getConnection(
                "jdbc:TAOS-RS://" + serverHost + ":" + port + "/" + dbName, user, password);
        con.setAutoCommit(false);
        return con;
    }

    @Override
    protected void connected(Connection conn) throws Exception {
        try (Statement stmt = conn.createStatement()) {
            for (String q : createTables()) {
                System.out.println(q);
                stmt.execute(q);
            }
        }
        conn.commit();
    }

    @Override
    public String currentStorageStatus() throws InterruptedException, SQLException {
        Connection conn = connectionPool.take();
        JSONArray arr = new JSONArray();
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SHOW TABLES");
            while (rs.next()) {
                JSONObject obj = new JSONObject();
                obj.put("table_name", rs.getString(1));
                arr.add(obj);
            }
        }
        connectionPool.put(conn);
        return arr.toJSONString();
    }

    private final Map<PVal.Type, String> type2SQLMap = ImmutableMap.of(
            PVal.Type.INT, "INT",
            PVal.Type.FLOAT, "FLOAT",
            PVal.Type.STRING, "BINARY(256)");

    @Override
    protected String type2SQL(PVal.Type type) {
        return type2SQLMap.get(type);
    }

    /**
     * 生成带反引号的列定义，用于 TDengine 建表语句。
     */
    protected String contentQuoted(Map<String, PVal.Type> props) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, PVal.Type> t : props.entrySet()) {
            sb.append('`').append(t.getKey()).append("` ").append(type2SQL(t.getValue())).append(", ");
        }
        if (sb.length() > 1) sb.setLength(sb.length() - 2);
        return sb.toString();
    }
}
