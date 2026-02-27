package org.neo4j.kernel.temporal;

import org.act.temporalProperty.util.Slice;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.temporal.TemporalRangeQuery;
import org.neo4j.graphdb.temporal.TimeIntervalRangeQuery;
import org.neo4j.graphdb.temporal.TimePoint;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 所有的用户接口中，新增的set和remove接口都可以直接依赖已有接口实现，只有新增的get接口需要测试。
 * 由于set接口在此前已测试完全，而新get接口由于TGraph默认RC隔离级别和锁无关，因此只需要测试两件事：
 * 1、新get接口可以正确读取本事务和其他已提交事务的综合信息
 * 2、新get接口可以正确处理给出的所有callBack
 */
public class SimpleNewInterfaceTest extends TestBase {
    private static final Class<? extends TestBase> thisClass = SimpleNewInterfaceTest.class;
    private static DatabaseManagementService dbms;
    private static GraphDatabaseService db;
    private static final Label label = Label.label("test");
    private static final RelationshipType relationshipType = RelationshipType.withName("test");

    @BeforeAll
    public static void prepareDir() {
        prepareDir(thisClass);
    }

    @BeforeEach
    @Test
    @Disabled("这个类打上Test只是为了能在TestBase中注册数据库根目录，不是测试程序")
    public void prepareDatabase() throws NoSuchMethodException {
        Method thisMethod = thisClass.getMethod(Thread.currentThread().getStackTrace()[1].getMethodName());
        clearDatabase(thisClass, thisMethod);
        dbms = new DatabaseManagementServiceBuilder(Objects.requireNonNull(getDatabaseHome(thisClass, thisMethod)).toPath()).build();
        db = dbms.database("neo4j");
    }

    @AfterEach
    public void shutdownDatabase() {
        dbms.shutdown();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    // test 1
    public void readTest1(boolean isNode) {
        try (Transaction tx = db.beginTx()) {
            Node node = tx.createNode(label);
            node.setProperty("id", 0);
            Relationship relationship = node.createRelationshipTo(node, relationshipType);
            relationship.setProperty("id", 0);
            tx.commit();
        }
        try (Transaction tx = db.beginTx()) {
            Entity entity = isNode ? tx.findNode(label, "id", 0) : tx.findRelationship(relationshipType, "id", 0);
            entity.setTemporalProperty("temp", new TimePoint(0), new TimePoint(0), 4);
            entity.setTemporalProperty("temp", new TimePoint(1), new TimePoint(1), 5);
            entity.setTemporalProperty("temp", new TimePoint(2), new TimePoint(2), 6);
            Object result = entity.getTemporalProperty("temp", new TimePoint(0), new TimePoint(10), new TemporalRangeQuery.MaxInt());
            assert result instanceof Integer;
            assert ((Integer) result) == 6;
            tx.commit();
        }
        try (Transaction tx = db.beginTx()) {
            Entity entity = isNode ? tx.findNode(label, "id", 0) : tx.findRelationship(relationshipType, "id", 0);
            Object result = entity.getTemporalProperty("temp", new TimePoint(0), new TimePoint(10), new TemporalRangeQuery.MaxInt());
            assert result instanceof Integer;
            assert ((Integer) result) == 6;
        }
        try (Transaction tx = db.beginTx()) {
            Entity entity = isNode ? tx.findNode(label, "id", 0) : tx.findRelationship(relationshipType, "id", 0);
            entity.setTemporalProperty("temp", new TimePoint(3), new TimePoint(3), 198);
            Object result = entity.getTemporalProperty("temp", new TimePoint(0), new TimePoint(10), new TemporalRangeQuery.MaxInt());
            assert result instanceof Integer;
            assert ((Integer) result) == 198;
            tx.commit();
        }
    }

    @Test
    // test 2(case 1)
    public void readTest2_1() {
        try (Transaction tx = db.beginTx()) {
            Node node = tx.createNode(label);
            node.setTemporalProperty("temp", new TimePoint(0), new TimePoint(0), 4.0f);
            node.setTemporalProperty("temp", new TimePoint(1), new TimePoint(1), 5.0f);
            node.setTemporalProperty("temp", new TimePoint(2), new TimePoint(2), 6.0f);
            Object result = node.getTemporalProperty("temp", new TimePoint(0), new TimePoint(10), new TemporalRangeQuery.MaxFloat());
            assert result instanceof Float;
            assert ((Float) result) == 6.0;
        }
    }

    @Test
    public void readTest2_2() {
        try (Transaction tx = db.beginTx()) {
            Node node = tx.createNode(label);
            node.setTemporalProperty("temp", new TimePoint(0), new TimePoint(0), 4.0f);
            node.setTemporalProperty("temp", new TimePoint(1), new TimePoint(1), 5.0f);
            node.setTemporalProperty("temp", new TimePoint(2), new TimePoint(2), 6.0f);
            Object result = node.getTemporalProperty("temp", new TimePoint(0), new TimePoint(10), new TemporalRangeQuery.MaxValue());
            assert result instanceof Comparable;
            assert ((Comparable) result).compareTo(6.0f) == 0;
        }
    }

    @Test
    public void readTest2_3() {
        try (Transaction tx = db.beginTx()) {
            Node node = tx.createNode(label);
            node.setTemporalProperty("temp", new TimePoint(0), new TimePoint(1), new Slice("value1".getBytes(StandardCharsets.UTF_8)));
            node.setTemporalProperty("temp", new TimePoint(2), new TimePoint(2), new Slice("value2".getBytes(StandardCharsets.UTF_8)));
            node.setTemporalProperty("temp", new TimePoint(4), new TimePoint(5), new Slice("value3".getBytes(StandardCharsets.UTF_8)));
            node.setTemporalProperty("temp", new TimePoint(5), new TimePoint(6), new Slice("value3".getBytes(StandardCharsets.UTF_8)));
            Object result = node.getTemporalProperty("temp", new TimePoint(0), new TimePoint(10), new TemporalRangeQuery.ValueChangeCounter());
            assert result instanceof Integer;
            assert (Integer) result == 5;
        }
    }
}
