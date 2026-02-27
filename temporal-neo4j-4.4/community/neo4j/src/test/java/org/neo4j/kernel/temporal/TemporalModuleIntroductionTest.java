package org.neo4j.kernel.temporal;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.*;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;

// 用于测试引入时态模块是否正确
public class TemporalModuleIntroductionTest extends TestBase {
    private static final Class<? extends TestBase> thisClass = TemporalModuleIntroductionTest.class;

    @BeforeAll
    public static void prepareDir() {
        prepareDir(thisClass);
    }

    // 用于测试引入时态模块是否会对原有功能造成破坏
    @RepeatedTest(200)
    public void testNoDamage() throws Exception {
        Method thisMethod = thisClass.getMethod(Thread.currentThread().getStackTrace()[1].getMethodName());
        DataGeneratorRecorder dataGeneratorRecorder = getNextDataGenerator(thisClass, thisMethod);
        try {
            // try块中为真正的测试程序
            File databaseDir = getDatabaseHome(thisClass, thisMethod);
            clearDatabase(thisClass, thisMethod);
            // 准备随机数据，包括写入轮次和图
            int writeRound = dataGeneratorRecorder.nextInt(10) + 1;
            int graphSize = dataGeneratorRecorder.nextInt(writeRound) + writeRound;
            boolean[][] graph = new boolean[graphSize][graphSize];
            ArrayList<Pair<Integer, Integer>> truePos = new ArrayList<>();
            for (int i = 0; i < graphSize; i++) {
                for (int j = 0; j < graphSize; j++) {
                    boolean relExist = dataGeneratorRecorder.nextBoolean();
                    graph[i][j] = relExist;
                    if (relExist) {
                        truePos.add(Pair.of(i, j));
                    }
                }
            }
            Label label = Label.label("node");
            String key = "user_id";
            RelationshipType relationshipType = RelationshipType.withName("rel");
            // 每次写入都新开一次dbms，在一个事务内随机写入一些数据
            for (int i = 0; i < writeRound; i++) {
                assert databaseDir != null;
                DatabaseManagementService dbms = new DatabaseManagementServiceBuilder(databaseDir.toPath()).build();
                GraphDatabaseService db = dbms.database("neo4j");
                try (Transaction transaction = db.beginTx()) {
                    if (truePos.isEmpty()) {
                        continue;
                    }
                    int writeRelCount = dataGeneratorRecorder.nextInt(truePos.size());
                    // 最后一轮写入全部数据
                    if (i == writeRound - 1) {
                        writeRelCount = truePos.size();
                    }
                    for (int j = 0; j < writeRelCount; j++) {
                        int pos = dataGeneratorRecorder.nextInt(truePos.size());
                        Pair<Integer, Integer> pair = truePos.remove(pos);
                        int from = pair.getLeft(), to = pair.getRight();
                        Node fromNode = transaction.findNode(label, key, from);
                        if (fromNode == null) {
                            fromNode = transaction.createNode(label);
                            fromNode.setProperty(key, from);
                        }
                        Node toNode = transaction.findNode(label, key, to);
                        if (toNode == null) {
                            toNode = transaction.createNode(label);
                            toNode.setProperty(key, to);
                        }
                        fromNode.createRelationshipTo(toNode, relationshipType);
                    }
                    transaction.commit();
                } finally {
                    dbms.shutdown();
                }
            }
            boolean[][] graphFromDatabase = new boolean[graphSize][graphSize];
            for (int i = 0; i < graphSize; i++) {
                for (int j = 0; j < graphSize; j++) {
                    graphFromDatabase[i][j] = false;
                }
            }
            {
                assert databaseDir != null;
                DatabaseManagementService dbms = new DatabaseManagementServiceBuilder(databaseDir.toPath()).build();
                GraphDatabaseService db = dbms.database("neo4j");
                try (Transaction transaction = db.beginTx()) {
                    ResourceIterator<Relationship> relationships = transaction.findRelationships(relationshipType);
                    while (relationships.hasNext()) {
                        Relationship relationship = relationships.next();
                        int from = (Integer) relationship.getStartNode().getProperty(key);
                        int to = (Integer) relationship.getEndNode().getProperty(key);
                        graphFromDatabase[from][to] = true;
                    }
                } finally {
                    dbms.shutdown();
                }
            }
            for (int i = 0; i < graphSize; i++) {
                for (int j = 0; j < graphSize; j++) {
                    assert graph[i][j] == graphFromDatabase[i][j];
                }
            }
        }
        catch (Throwable e) {
            dataGeneratorRecorder.flush();
            throw new RuntimeException(e);
        }
        finally {
            dataGeneratorRecorder.close();
        }
    }
}
