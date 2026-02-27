package org.neo4j.kernel.temporal;

import org.junit.jupiter.api.*;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Transaction;

import java.lang.reflect.Method;
import java.util.Objects;

public class TransactionCommitPhaseOneTest extends TestBase {
    private static final Class<? extends TestBase> thisClass = TransactionCommitPhaseOneTest.class;
    private DatabaseManagementService dbms;

    @BeforeAll
    public static void prepare() throws NoSuchMethodException {
        prepareDir(thisClass);
    }

    @RepeatedTest(200)
    public void testCommit() throws Exception {
        Method thisMethod = thisClass.getMethod(Thread.currentThread().getStackTrace()[1].getMethodName());
        DataGeneratorRecorder dataGeneratorRecorder = getNextDataGenerator(thisClass, thisMethod);
        clearDatabase(thisClass, thisMethod);
        dbms = new DatabaseManagementServiceBuilder(Objects.requireNonNull(getDatabaseHome(thisClass, thisMethod)).toPath()).build();
        try {
            normalTest(dataGeneratorRecorder, false);
        }
        catch (Throwable e) {
            dataGeneratorRecorder.flush();
            throw new RuntimeException(e);
        }
        finally {
            dataGeneratorRecorder.close();
            dbms.shutdown();
        }
    }

    @RepeatedTest(10)
    public void testCommitOneNode() throws Exception {
        Method thisMethod = thisClass.getMethod(Thread.currentThread().getStackTrace()[1].getMethodName());
        DataGeneratorRecorder dataGeneratorRecorder = getNextDataGenerator(thisClass, thisMethod);
        clearDatabase(thisClass, thisMethod);
        dbms = new DatabaseManagementServiceBuilder(Objects.requireNonNull(getDatabaseHome(thisClass, thisMethod)).toPath()).build();
        try {
            normalTest(dataGeneratorRecorder, true);
        }
        catch (Throwable e) {
            dataGeneratorRecorder.flush();
            throw new RuntimeException(e);
        }
        finally {
            dataGeneratorRecorder.close();
            dbms.shutdown();
        }
    }

    private void normalTest(DataGeneratorRecorder dataGeneratorRecorder, boolean isOneNode) {
        GraphDatabaseService db = dbms.database("neo4j");
        GraphForTestValidation graph;
        try (Transaction transaction = db.beginTx()) {
            graph = new GraphForTestValidation(transaction, dataGeneratorRecorder, isOneNode);
            transaction.commit();
        }
        // 进行a*b=100000次操作，中间穿插一些验证
        int temp = dataGeneratorRecorder.nextInt(5);
        // transactionSize如果为1就会导致单次测试运行时间过长而被junit掐掉，因此最少是10
        int transactionSize = 10, transactionNum = 10000;
        for (int i = 0; i < temp; i++) {
            transactionSize *= 10;
            transactionNum /= 10;
        }
        System.out.println("transaction size: " + transactionSize);
        for (int i = 0; i < transactionNum; i++) {
            try (Transaction transaction = db.beginTx()) {
                for (int j = 0; j < transactionSize; j++) {
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
                transaction.commit();
            }
            if ((i * transactionSize) % 100 == 0) {
                System.out.println((i * transactionSize) + " operations have finished.");
            }
        }
        try (Transaction transaction = db.beginTx()) {
            graph.validateAll(transaction);
            transaction.commit();
        }
    }
}
