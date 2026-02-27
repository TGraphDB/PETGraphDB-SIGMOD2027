package org.neo4j.kernel.temporal;

import org.act.temporalProperty.exception.TPSNHException;
import org.junit.jupiter.api.*;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.temporal.TimePoint;

import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Consumer;

public class TransactionWritePhaseOneTest extends TestBase {
    private static final Class<? extends TestBase> thisClass = TransactionWritePhaseOneTest.class;
    private static DatabaseManagementService dbms;
    private Transaction transaction;

    @BeforeAll
    public static void prepare() throws NoSuchMethodException {
        prepareDir(thisClass);
        clearDatabase(thisClass, thisClass.getMethod("testWrite"));
        dbms = new DatabaseManagementServiceBuilder(Objects.requireNonNull(getDatabaseHome(thisClass, thisClass.getMethod("testWrite"))).toPath()).build();
    }

    @AfterAll
    public static void clear() throws NoSuchMethodException {
        dbms.shutdown();
    }

    @BeforeEach
    public void startTx() {
        transaction = dbms.database("neo4j").beginTx();
    }

    @AfterEach
    public void endTx() {
        transaction.close();
    }

    @RepeatedTest(200)
    public void testWrite() throws Exception {
        Method thisMethod = thisClass.getMethod(Thread.currentThread().getStackTrace()[1].getMethodName());
        DataGeneratorRecorder dataGeneratorRecorder = getNextDataGenerator(thisClass, thisMethod);
        try {
            normalTest(dataGeneratorRecorder, false);
        }
        catch (Throwable e) {
            dataGeneratorRecorder.flush();
            throw new RuntimeException(e);
        }
        finally {
            dataGeneratorRecorder.close();
        }
    }

    @RepeatedTest(10)
    public void testWriteOneNode() throws Exception {
        Method thisMethod = thisClass.getMethod(Thread.currentThread().getStackTrace()[1].getMethodName());
        DataGeneratorRecorder dataGeneratorRecorder = getNextDataGenerator(thisClass, thisMethod);
        try {
            normalTest(dataGeneratorRecorder, true);
        }
        catch (Throwable e) {
            dataGeneratorRecorder.flush();
            throw new RuntimeException(e);
        }
        finally {
            dataGeneratorRecorder.close();
        }
    }

    private void normalTest(DataGeneratorRecorder dataGeneratorRecorder, boolean isOneNodeTest) {
        // 1、设置好随机的初始状态
        GraphForTestValidation graph = new GraphForTestValidation(transaction, dataGeneratorRecorder, isOneNodeTest);
        // 2、进行100000次操作，中间穿插一些验证
        for (int i = 0; i < 100000; i++) {
            /*
             * 按如下概率操作：
             * 0.1%，删除一个点及其关系
             * 0.9%，删除一个关系
             * 0.1%，增加一个点
             * 0.9%，增加一个关系
             * 98%，按一定概率随机选择点或边，设置一次时态属性
             */
            int judge = dataGeneratorRecorder.nextInt(1000);
            if (judge == 0) {
                graph.removeNode(transaction, dataGeneratorRecorder);
            }
            else if (judge < 10) {
                graph.removeRelationship(transaction, dataGeneratorRecorder);
            }
            else if (judge == 10) {
                graph.addNode(transaction, dataGeneratorRecorder);
            }
            else if (judge < 20) {
                graph.addRelationship(transaction, dataGeneratorRecorder);
            }
            else {
                graph.setTemporalProperty(transaction, dataGeneratorRecorder);
            }
        }
        // 3、操作完成后，验证所有结果
        graph.validateAll(transaction);
    }

    @Test
    public void timeRevertTest() {
        Node node = transaction.createNode(GraphForTestValidation.nodeLabel);
        try {
            node.setTemporalProperty("a", new TimePoint(4), new TimePoint(2), 1);
            throw new RuntimeException();
        }
        catch (IllegalArgumentException ignored) {}
    }

    @Test
    public void differentTypeTest() {
        Consumer<Entity> consumer = entity -> {
            String e = entity instanceof Node ? "n" : "r";
            try {
                entity.setTemporalProperty(e + "ba", new TimePoint(1), new TimePoint(3), 1);
                entity.setTemporalProperty(e + "ba", new TimePoint(0), new TimePoint(0), "a");
                throw new RuntimeException();
            }
            catch (TPSNHException ignored) {}
            try {
                entity.setTemporalProperty(e + "bb", new TimePoint(1), new TimePoint(3), 1);
                entity.setTemporalProperty(e + "bb", new TimePoint(0), new TimePoint(0), 1.2);
                throw new RuntimeException();
            }
            catch (TPSNHException ignored) {}
            try {
                entity.setTemporalProperty(e + "bc", new TimePoint(1), new TimePoint(3), 1);
                entity.setTemporalProperty(e + "bc", new TimePoint(0), new TimePoint(0), 1L);
                throw new RuntimeException();
            }
            catch (TPSNHException ignored) {}
            try {
                entity.setTemporalProperty(e + "bd", new TimePoint(1), new TimePoint(3), 1.2d);
                entity.setTemporalProperty(e + "bd", new TimePoint(0), new TimePoint(0), 1.2f);
                throw new RuntimeException();
            }
            catch (TPSNHException ignored) {}
        };
        Node node = transaction.createNode(GraphForTestValidation.nodeLabel);
        consumer.accept(node);
        Relationship relationship = node.createRelationshipTo(node, GraphForTestValidation.relationshipType);
        consumer.accept(relationship);
    }

    @Test
    public void unsupportedTypeTest() {
        Consumer<Entity> consumer = entity -> {
            String e = entity instanceof Node ? "n" : "r";
            try {
                entity.setTemporalProperty(e + "cb", new TimePoint(1), new TimePoint(3), new HashMap<>());
                throw new RuntimeException();
            } catch (TPSNHException ignored) {}
        };
        Node node = transaction.createNode(GraphForTestValidation.nodeLabel);
        consumer.accept(node);
        Relationship relationship = node.createRelationshipTo(node, GraphForTestValidation.relationshipType);
        consumer.accept(relationship);
    }

    @Test
    public void noSuchPropertyTest() {
        Consumer<Entity> consumer = entity -> {
            String e = entity instanceof Node ? "n" : "r";
            try {
                entity.getTemporalProperty(e + "d", new TimePoint(0));
                throw new RuntimeException();
            } catch (NotFoundException ignored) {}
        };
        Node node = transaction.createNode(GraphForTestValidation.nodeLabel);
        consumer.accept(node);
        Relationship relationship = node.createRelationshipTo(node, GraphForTestValidation.relationshipType);
        consumer.accept(relationship);
    }

    @Test
    public void writeNullToEmptyTest() {
        Consumer<Entity> consumer = entity -> {
            String e = entity instanceof Node ? "n" : "r";
            try {
                entity.setTemporalProperty(e + "ea", new TimePoint(0), TimePoint.NOW, null);
                throw new RuntimeException();
            } catch (TPSNHException ignored) {
            }
            entity.setTemporalProperty(e + "eb", new TimePoint(0), new TimePoint(6), 1);
            entity.setTemporalProperty(e + "eb", new TimePoint(0), TimePoint.NOW, null);
        };
        Node node = transaction.createNode(GraphForTestValidation.nodeLabel);
        consumer.accept(node);
        Relationship relationship = node.createRelationshipTo(node, GraphForTestValidation.relationshipType);
        consumer.accept(relationship);
    }

    @Test
    public void removeEmptyNodeTest() {
        Consumer<Entity> consumer = entity -> {
            String e = entity instanceof Node ? "n" : "r";
            entity.setProperty(e + "fa", 1);
            entity.setTemporalProperty(e + "fb", new TimePoint(0), new TimePoint(4), 1);
        };
        Node node = transaction.createNode(GraphForTestValidation.nodeLabel);
        consumer.accept(node);
        Relationship relationship = node.createRelationshipTo(node, GraphForTestValidation.relationshipType);
        consumer.accept(relationship);
        relationship.delete();
        node.delete();
    }
}
