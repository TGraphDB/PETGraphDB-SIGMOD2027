package edu.buaa;

import org.junit.Test;
import org.neo4j.driver.*;
import org.neo4j.driver.types.Node;

public class SimpleTest {
    @Test
    public void run(){
        Driver driver = GraphDatabase.driver("bolt://client:7654", AuthTokens.basic("", ""));
        try(Session session = driver.session()) {
            session.run("CREATE (n:Node {row_number: 0}) RETURN n;").single();
            Record res = session.run("MATCH (n:Node {row_number:0}) SET n.age = 42 RETURN n;").single();
            Node n = res.get("n").asNode();
            System.out.println(n.asMap());
            var nodes = session.run(
                            String.format("CALL time.getTemporalGraphNodes(%d, %d) YIELD *", 0, Integer.MAX_VALUE))
                    .list();
            for(var v: nodes){
                System.out.println(v.asMap());
            }
        }
    }
}
