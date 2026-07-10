package edu.buaa.server;

import edu.buaa.utils.Helper;
import org.junit.Test;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;

import java.sql.*;

public class DebugServer {
    @Test
    public void n1(){
        GraphDatabaseService db = new GraphDatabaseFactory().newEmbeddedDatabase(Helper.mustEnv("DB_PATH"));
        try(Transaction tx = db.beginTx()){

        }finally {
            db.shutdown();
        }
    }

    @Test
    public void ma() throws SQLException, ClassNotFoundException {
        Class.forName("org.mariadb.jdbc.Driver");
        try (Connection conn = DriverManager.getConnection("jdbc:mariadb://data:3306/m_traffic_ma_all", "root", "langduhua")) {
            PreparedStatement stmt0 = conn.prepareStatement("SELECT travel_time as prop, st_time, en_time FROM rel_tp " +
                    "WHERE st_time<=? AND en_time>? AND entity=? ORDER BY st_time");
            stmt0.setInt(1, 1272744832+1500);
            stmt0.setInt(2, 1272744832);
            stmt0.setLong(3, 43672);
            ResultSet rs = stmt0.executeQuery();
            int earT = Integer.MAX_VALUE;
            while(rs.next()){
                int st = (int) (rs.getTimestamp("st_time").getTime() / 1000L);
                int en = (int) (rs.getTimestamp("en_time").getTime() / 1000L);
                int beginT = Math.max(st, 1272744832);
                int endT = Math.min(en, 1272744832+1500);
                if(beginT<=endT) {
                    int travel_t = rs.getInt("prop");
                    int arrT = beginT + travel_t;
                    if (arrT < earT) earT = arrT;
                    if (st > earT) break;
                }
            }
            if(earT<Integer.MAX_VALUE) System.out.println(earT);
            else throw new UnsupportedOperationException();
        }

    }
}
