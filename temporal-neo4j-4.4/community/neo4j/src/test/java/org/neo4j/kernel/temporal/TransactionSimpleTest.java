package org.neo4j.kernel.temporal;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.temporal.TimePoint;
import org.neo4j.kernel.DeadlockDetectedException;
import org.neo4j.kernel.impl.coreapi.InternalTransaction;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * 分3类测试：
 * 1、对neo4j原有代码的测试，主要是为了进一步理解原有的锁机制
 * 1.1、刻意制造仅有排他锁的死锁并输出相关信息供判别（了解死锁机制）
 * 1.2、排他锁和共享锁一起上，通过一些输出信息观察并发结果
 * 2、测试外层接口能否正确连接到锁处理类（测试所有接口）
 * 2.1、测试能否通过写入直接拿到排他锁，并通过并发表现观察
 * 2.2、测试能否通过拿锁接口拿锁，并通过并发表现观察
 * 3、测试锁的行为是否正确
 * 3.1、测试普通锁和时态锁是否能正确并发
 * 3.2、测试时态锁之间能否正确并发
 * 3.3、测试死锁检测和打断是否能正确触发，触发后系统能否恢复正常（最好代价更低，不用在一个死锁循环中打断多个事务）
 * 注：在TGraph中破坏2PL（即手动释放获得的锁）是未定义行为。时态锁没有释放的接口，普通锁的释放可能会导致系统出问题。
 */
public class TransactionSimpleTest extends TestBase {
    private static final Class<? extends TestBase> thisClass = TransactionSimpleTest.class;
    private DatabaseManagementService dbms;
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
    }

    @AfterEach
    public void shutdownDatabase() {
        dbms.shutdown();
    }

    private static String getTimeMessageInMs(long startTime) {
        return "[" + ((System.nanoTime() - startTime) / 1000 / 1000) + "ms]: ";
    }

    private void executeTransactionAfterSomeTime(long sleepTime, long timerStartTime, Consumer<Transaction> transactionAction) {
        try {
            // 用于确定事务开启顺序的时间，以确定事务id。不同的事务id会参与决定死锁中被杀死的事务
            Thread.sleep(sleepTime);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        long txId = -1;
        try (Transaction tx = dbms.database("neo4j").beginTx()) {
            txId = ((InternalTransaction) tx).kernelTransaction().getUserTransactionId();
            transactionAction.accept(tx);
        }
        catch (DeadlockDetectedException e) {
            System.out.println(getTimeMessageInMs(timerStartTime) + "a deadlock occurred, transaction " + txId + " is terminated: ");
            e.printStackTrace();
        }
    }

    private static void printLockAcquireMessage(long timerStartTime, Transaction transaction, Entity entity, String lockKind) {
        String entityType = entity instanceof Node ? "node" : "relationship";
        Object entityId = entity.getProperty("id");
        long txId = ((InternalTransaction) transaction).kernelTransaction().getUserTransactionId();
        System.out.println(getTimeMessageInMs(timerStartTime) + "transaction " + txId + " acquires " + lockKind +
                " lock on " + entityType + " " + entityId);
    }

    private static void printTemporalLockAcquireMessage(long timerStartTime, Transaction transaction, Entity entity,
                                                        String lockKind, String key, long start, long end) {
        String entityType = entity instanceof Node ? "node" : "relationship";
        Object entityId = entity.getProperty("id");
        long txId = ((InternalTransaction) transaction).kernelTransaction().getUserTransactionId();
        System.out.println(getTimeMessageInMs(timerStartTime) + "transaction " + txId + " acquires temporal " + lockKind +
                " lock on " + entityType + " " + entityId + " property " + key + " range " + start + "~" + end);
    }

    public static Stream<Arguments> normalDeadlockTestDataSource() {
        return Stream.of(
                // 这6个用于说明死锁打断的顺序应该和醒来的顺序无关
                Arguments.of(3, new int[]{1, 2, 3}, new int[]{0, 0, 0}),
                Arguments.of(3, new int[]{1, 3, 2}, new int[]{0, 0, 0}),
                Arguments.of(3, new int[]{2, 1, 3}, new int[]{0, 0, 0}),
                Arguments.of(3, new int[]{2, 3, 1}, new int[]{0, 0, 0}),
                Arguments.of(3, new int[]{3, 1, 2}, new int[]{0, 0, 0}),
                Arguments.of(3, new int[]{3, 2, 1}, new int[]{0, 0, 0}),
                // 这6个和前6个对比，说明持有锁数量是影响因素
                Arguments.of(3, new int[]{1, 2, 3}, new int[]{0, 0, 1}),
                Arguments.of(3, new int[]{1, 3, 2}, new int[]{0, 1, 0}),
                Arguments.of(3, new int[]{2, 1, 3}, new int[]{0, 1, 1}),
                Arguments.of(3, new int[]{2, 3, 1}, new int[]{1, 0, 0}),
                Arguments.of(3, new int[]{3, 1, 2}, new int[]{1, 0, 1}),
                Arguments.of(3, new int[]{3, 2, 1}, new int[]{1, 1, 0})
        );

    }

    @ParameterizedTest
    @MethodSource("normalDeadlockTestDataSource")
    @Disabled("已了解")
    // test 1.1
    public void normalDeadlockTest(int transactionNum, int[] orderToGrabSecond, int[] extraAcquireTimes) throws InterruptedException {
        if (orderToGrabSecond.length != transactionNum) throw new RuntimeException();
        if (extraAcquireTimes.length != transactionNum) throw new RuntimeException();
        long startTime = System.nanoTime();
        long[] transactionStartTimeArray = new long[transactionNum];
        for (int i = 0; i < transactionNum; i++) {
            transactionStartTimeArray[i] = i * 100L;
        }
        long[] transactionGapTimeArray = new long[transactionNum];
        for (int i = 0; i < transactionNum; i++) {
            transactionGapTimeArray[i] = 100L * transactionNum - transactionStartTimeArray[i] + orderToGrabSecond[i] * 100L;
        }
        // 准备死锁循环需要的节点
        GraphDatabaseService db = dbms.database("neo4j");
        try (Transaction tx = db.beginTx()) {
            for (int i = 0; i < transactionNum; i++) {
                Node node = tx.createNode(label);
                node.setProperty("id", i);
            }
            tx.commit();
        }
        // 这个类的方法仅在本方法内部通用
        class InnerClass {
            private long txId;

            public void transactionAction(Transaction transaction, Entity firstToAcquire, Entity secondToAcquire, long gapTime, int extraAcquireTimes) throws InterruptedException {
                long txId = ((InternalTransaction) transaction).kernelTransaction().getUserTransactionId();
                this.txId = txId;
                firstToAcquire.setProperty("tx" + txId, 0);
                printLockAcquireMessage(startTime, transaction, firstToAcquire, "exclusive");
                for (int i = 0; i < extraAcquireTimes; i++) {
                    transaction.acquireWriteLock(firstToAcquire);
                    printLockAcquireMessage(startTime, transaction, firstToAcquire, "exclusive");
                }
                Thread.sleep(gapTime);
                secondToAcquire.setProperty("tx" + txId, 0);
                printLockAcquireMessage(startTime, transaction, secondToAcquire, "exclusive");
            }

            public long getTxId() {
                return txId;
            }
        }
        InnerClass[] innerClasses = new InnerClass[transactionNum];
        for (int i = 0; i < transactionNum; i++) {
            innerClasses[i] = new InnerClass();
        }
        // 构建一个3个事务循环等待的情况
        Thread[] threads = new Thread[transactionNum];
        for (int i = 0; i < transactionNum; i++) {
            long transactionStartTime = transactionStartTimeArray[i];
            int index = i;
            InnerClass innerClass = innerClasses[i];
            long transactionGapTime = transactionGapTimeArray[i];
            int extraAcquireTime = extraAcquireTimes[i];
            threads[i] = new Thread(
                    () -> executeTransactionAfterSomeTime(transactionStartTime, startTime,
                            transaction -> {
                                Entity e1 = transaction.findNode(label, "id", index % transactionNum);
                                Entity e2 = transaction.findNode(label, "id", (index + 1) % transactionNum);
                                try {
                                    innerClass.transactionAction(transaction, e1, e2, transactionGapTime, extraAcquireTime);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                    )
            );
            threads[i].start();
        }
        for (int i = 0; i < 3; i++) {
            threads[i].join();
        }
        // 这里不做程序上的判别了，直接通过输出信息肉眼判断。唯一确定的结论是死锁在产生后会迅速打断，以及所有的并发控制都是正确的。至于死锁打断哪个
        // 事务，甚至打断几个事务，都是有随机因素在里面的，可以多次运行观察结果。前6个case的打断是固定的（多次运行结果相同），后6个相对不固定（有的
        // 运行时会不按预期规则打断，有的甚至会打断2个）
    }

    @Test
    @Disabled("已了解")
    // test 1.2(case 1)
    public void normalAllLockTest1() throws InterruptedException {
        long startTime = System.nanoTime();
        GraphDatabaseService db = dbms.database("neo4j");
        try (Transaction tx = db.beginTx()) {
            Node node = tx.createNode(label);
            node.setProperty("id", 0);
            tx.commit();
        }
        Thread t1 = new Thread(() -> executeTransactionAfterSomeTime(0, startTime, tx -> {
            Node node = tx.findNode(label, "id", 0);
            tx.acquireWriteLock(node);
            printLockAcquireMessage(startTime, tx, node, "exclusive");
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println(getTimeMessageInMs(startTime) + "write transaction finished.");
        }));
        t1.start();
        Thread.sleep(100);
        Thread[] threads = new Thread[10];
        for (int i = 0; i < 10; i++) {
            threads[i] = new Thread(() -> executeTransactionAfterSomeTime(0, startTime, tx -> {
                Node node = tx.findNode(label, "id", 0);
                tx.acquireReadLock(node);
                printLockAcquireMessage(startTime, tx, node, "shared");
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println(getTimeMessageInMs(startTime) + "read transaction finished.");
            }));
            threads[i].start();
        }
        t1.join();
        for (int i = 0; i < 10; i++) {
            threads[i].join();
        }
        // 同样只看输出不做判别
    }

    @Test
    @Disabled("已了解")
    // test 1.2(case 2, deadlock)
    public void normalAllLockTest2() throws InterruptedException {
        long startTime = System.nanoTime();
        GraphDatabaseService db = dbms.database("neo4j");
        try (Transaction tx = db.beginTx()) {
            for (int i = 0; i < 10; i++){
                Node node = tx.createNode(label);
                node.setProperty("id", i);
            }
            tx.commit();
        }
        Thread t1 = new Thread(() -> executeTransactionAfterSomeTime(0, startTime, tx -> {
            Node node = tx.findNode(label, "id", 0);
            tx.acquireWriteLock(node);
            printLockAcquireMessage(startTime, tx, node, "exclusive");
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            node = tx.findNode(label, "id", 1);
            tx.acquireWriteLock(node);
            printLockAcquireMessage(startTime, tx, node, "shared");
            long txId = ((InternalTransaction) tx).kernelTransaction().getUserTransactionId();
            System.out.println(getTimeMessageInMs(startTime) + "transaction " + txId + " finished.");
        }));
        t1.start();
        Thread.sleep(100);
        Thread[] threads = new Thread[10];
        for (int i = 0; i < 10; i++) {
            threads[i] = new Thread(() -> executeTransactionAfterSomeTime(0, startTime, tx -> {
                Node node = tx.findNode(label, "id", 1);
                tx.acquireReadLock(node);
                printLockAcquireMessage(startTime, tx, node, "shared");
                node = tx.findNode(label, "id", 0);
                tx.acquireReadLock(node);
                printLockAcquireMessage(startTime, tx, node, "shared");
                long txId = ((InternalTransaction) tx).kernelTransaction().getUserTransactionId();
                System.out.println(getTimeMessageInMs(startTime) + "transaction " + txId + " finished.");
            }));
            threads[i].start();
        }
        t1.join();
        for (int i = 0; i < 10; i++) {
            threads[i].join();
        }
        // 同样只看输出不做判别
    }

    @Test
    // test 2.1
    // 节点和边的测试用的相同逻辑
    public void temporalLockInterfaceConnectTest1() throws InterruptedException {
        long startTime = System.nanoTime();
        GraphDatabaseService db = dbms.database("neo4j");
        AtomicBoolean nodeTestPassed = new AtomicBoolean(false);
        AtomicBoolean relationshipTestPassed = new AtomicBoolean(false);
        try (Transaction tx = db.beginTx()) {
            Node node = tx.createNode(label);
            node.setProperty("id", 0);
            node.setTemporalProperty("temp", new TimePoint(0), new TimePoint(0), 0);
            Relationship relationship = node.createRelationshipTo(node, relationshipType);
            relationship.setProperty("id", 0);
            relationship.setTemporalProperty("temp", new TimePoint(0), new TimePoint(0), 0);
            tx.commit();
        }
        Thread nodeThread1 = new Thread(() -> executeTransactionAfterSomeTime(0, startTime, tx -> {
            Node node = tx.findNode(label, "id", 0);
            node.setTemporalProperty("temp", new TimePoint(0), new TimePoint(10), 0);
            printTemporalLockAcquireMessage(startTime, tx, node, "exclusive", "temp", 0, 10);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }));
        Thread relationshipThread1 = new Thread(() -> executeTransactionAfterSomeTime(100, startTime, tx -> {
            Relationship relationship = tx.findRelationship(relationshipType, "id", 0);
            relationship.setTemporalProperty("temp", new TimePoint(0), new TimePoint(10), 0);
            printTemporalLockAcquireMessage(startTime, tx, relationship, "exclusive", "temp", 0, 10);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }));
        Thread nodeThread2 = new Thread(() -> executeTransactionAfterSomeTime(100, startTime, tx -> {
            Node node = tx.findNode(label, "id", 0);
            node.setTemporalProperty("temp", new TimePoint(0), new TimePoint(10), 0);
            printTemporalLockAcquireMessage(startTime, tx, node, "exclusive", "temp", 0, 10);
            long sec = (System.nanoTime() - startTime) / 1000 / 1000 / 1000;
            nodeTestPassed.set(sec == 1);
        }));
        Thread relationshipThread2 = new Thread(() -> executeTransactionAfterSomeTime(100, startTime, tx -> {
            Relationship relationship = tx.findRelationship(relationshipType, "id", 0);
            relationship.setTemporalProperty("temp", new TimePoint(0), new TimePoint(10), 0);
            printTemporalLockAcquireMessage(startTime, tx, relationship, "exclusive", "temp", 0, 10);
            long sec = (System.nanoTime() - startTime) / 1000 / 1000 / 1000;
            relationshipTestPassed.set(sec == 1);
        }));
        nodeThread1.start();
        relationshipThread1.start();
        nodeThread2.start();
        relationshipThread2.start();
        nodeThread1.join();
        relationshipThread1.join();
        nodeThread2.join();
        relationshipThread2.join();
        assert nodeTestPassed.get() && relationshipTestPassed.get();
    }

    @Test
    // test 2.2
    // 节点和边的测试用的相同的逻辑
    public void temporalLockInterfaceConnectTest2() throws InterruptedException {
        long startTime = System.nanoTime();
        AtomicBoolean relationshipUpgradeTestPassed = new AtomicBoolean(false);
        AtomicBoolean nodeReadTestPassed = new AtomicBoolean(false);
        AtomicBoolean nodeWriteTestPassed = new AtomicBoolean(false);
        AtomicBoolean nodeUpgradeTestPassed = new AtomicBoolean(false);
        AtomicBoolean relationshipReadTestPassed = new AtomicBoolean(false);
        AtomicBoolean relationshipWriteTestPassed = new AtomicBoolean(false);
        GraphDatabaseService db = dbms.database("neo4j");
        try (Transaction tx = db.beginTx()) {
            Node node = tx.createNode(label);
            node.setProperty("id", 0);
            node.setTemporalProperty("temp", new TimePoint(0), new TimePoint(0), 0);
            Relationship relationship = node.createRelationshipTo(node, relationshipType);
            relationship.setProperty("id", 0);
            relationship.setTemporalProperty("temp", new TimePoint(0), new TimePoint(0), 0);
            tx.commit();
        }
        Thread nodeReadThread1 = new Thread(() -> executeTransactionAfterSomeTime(0, startTime, tx -> {
            Node node = tx.findNode(label, "id", 0);
            node.acquireTemporalSharedLock("temp", new TimePoint(0), new TimePoint(10));
            printTemporalLockAcquireMessage(startTime, tx, node, "shared", "temp", 0, 10);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }));
        Thread nodeReadThread2 = new Thread(() -> executeTransactionAfterSomeTime(100, startTime, tx -> {
            Node node = tx.findNode(label, "id", 0);
            node.acquireTemporalSharedLock("temp", new TimePoint(0), new TimePoint(10));
            printTemporalLockAcquireMessage(startTime, tx, node, "shared", "temp", 0, 10);
            long sec = (System.nanoTime() - startTime) / 1000 / 1000 / 1000;
            nodeReadTestPassed.set(sec == 0);
        }));
        Thread nodeWriteThread = new Thread(() -> executeTransactionAfterSomeTime(200, startTime, tx-> {
            Node node = tx.findNode(label, "id", 0);
            node.acquireTemporalExclusiveLock("temp", new TimePoint(0), new TimePoint(10));
            printTemporalLockAcquireMessage(startTime, tx, node, "exclusive", "temp", 0, 10);
            long sec = (System.nanoTime() - startTime) / 1000 / 1000 / 1000;
            nodeWriteTestPassed.set(sec == 1);
        }));
        Thread nodeReadThread3 = new Thread(() -> executeTransactionAfterSomeTime(300, startTime, tx -> {
            Node node = tx.findNode(label, "id", 0);
            node.acquireTemporalSharedLock("temp", new TimePoint(0), new TimePoint(10));
            printTemporalLockAcquireMessage(startTime, tx, node, "shared", "temp", 0, 10);
            long sec = (System.nanoTime() - startTime) / 1000 / 1000 / 1000;
            nodeUpgradeTestPassed.set(sec == 1);
        }));
        Thread relationshipReadThread1 = new Thread(() -> executeTransactionAfterSomeTime(0, startTime, tx -> {
            Relationship relationship = tx.findRelationship(relationshipType, "id", 0);
            relationship.acquireTemporalSharedLock("temp", new TimePoint(0), new TimePoint(10));
            printTemporalLockAcquireMessage(startTime, tx, relationship, "shared", "temp", 0, 10);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }));
        Thread relationshipReadThread2 = new Thread(() -> executeTransactionAfterSomeTime(100, startTime, tx -> {
            Relationship relationship = tx.findRelationship(relationshipType, "id", 0);
            relationship.acquireTemporalSharedLock("temp", new TimePoint(0), new TimePoint(10));
            printTemporalLockAcquireMessage(startTime, tx, relationship, "shared", "temp", 0, 10);
            long sec = (System.nanoTime() - startTime) / 1000 / 1000 / 1000;
            relationshipReadTestPassed.set(sec == 0);
        }));
        Thread relationshipWriteThread = new Thread(() -> executeTransactionAfterSomeTime(200, startTime, tx-> {
            Relationship relationship = tx.findRelationship(relationshipType, "id", 0);
            relationship.acquireTemporalExclusiveLock("temp", new TimePoint(0), new TimePoint(10));
            printTemporalLockAcquireMessage(startTime, tx, relationship, "exclusive", "temp", 0, 10);
            long sec = (System.nanoTime() - startTime) / 1000 / 1000 / 1000;
            relationshipWriteTestPassed.set(sec == 1);
        }));
        Thread relationshipReadThread3 = new Thread(() -> executeTransactionAfterSomeTime(300, startTime, tx -> {
            Relationship relationship = tx.findRelationship(relationshipType, "id", 0);
            relationship.acquireTemporalSharedLock("temp", new TimePoint(0), new TimePoint(10));
            printTemporalLockAcquireMessage(startTime, tx, relationship, "shared", "temp", 0, 10);
            long sec = (System.nanoTime() - startTime) / 1000 / 1000 / 1000;
            relationshipUpgradeTestPassed.set(sec == 1);
        }));
        nodeReadThread1.start();
        nodeWriteThread.start();
        nodeReadThread2.start();
        nodeReadThread3.start();
        relationshipReadThread1.start();
        relationshipWriteThread.start();
        relationshipReadThread2.start();
        relationshipReadThread3.start();
        nodeReadThread1.join();
        nodeWriteThread.join();
        nodeReadThread2.join();
        nodeReadThread3.join();
        relationshipReadThread1.join();
        relationshipWriteThread.join();
        relationshipReadThread2.join();
        relationshipReadThread3.join();
        assert nodeReadTestPassed.get() && nodeWriteTestPassed.get() && nodeUpgradeTestPassed.get() &&
                relationshipReadTestPassed.get() && relationshipWriteTestPassed.get() && relationshipUpgradeTestPassed.get();
    }

    public static Stream<Arguments> normalTemporalCompatibilityTestDataSource() {
        return Stream.of(
                Arguments.of(true, true, true, new long[]{0, 1, 1}),
                Arguments.of(true, true, false, new long[]{0, 0, 0}),
                Arguments.of(true, false, true, new long[]{0, 1, 1}),
                Arguments.of(true, false, false, new long[]{0, 0, 0}),
                Arguments.of(false, true, true, new long[]{0, 0, 0}),
                Arguments.of(false, true, false, new long[]{0, 0, 0}),
                Arguments.of(false, false, true, new long[]{0, 0, 0}),
                Arguments.of(false, false, false, new long[]{0, 0, 0})
        );
    }

    @ParameterizedTest
    @MethodSource("normalTemporalCompatibilityTestDataSource")
    // test 3.1
    public void normalTemporalCompatibilityTest(boolean isNormalLockExclusive, boolean isTemporalLockExclusive,
                                                boolean lockSameEntity, long[] expectedWaitTime) throws InterruptedException {
        long startTime = System.nanoTime();
        GraphDatabaseService db = dbms.database("neo4j");
        AtomicBoolean testPassed1 = new AtomicBoolean(false);
        AtomicBoolean testPassed2 = new AtomicBoolean(false);
        AtomicBoolean testPassed3 = new AtomicBoolean(false);
        try (Transaction tx = db.beginTx()) {
            for (int i = 0; i < 2; i++) {
                Node node = tx.createNode(label);
                node.setProperty("id", i);
                node.setTemporalProperty("temp", new TimePoint(0), new TimePoint(0), 0);
            }
            tx.commit();
        }
        Thread temporalLockThread1 = new Thread(() -> executeTransactionAfterSomeTime(0, startTime, tx -> {
            Node node = tx.findNode(label, "id", 0);
            long lockTimePoint = 0;
            if (isTemporalLockExclusive) {
                node.acquireTemporalExclusiveLock("temp", new TimePoint(lockTimePoint), new TimePoint(lockTimePoint));
                printTemporalLockAcquireMessage(startTime, tx, node, "exclusive", "temp", 0, 0);
            }
            else {
                node.acquireTemporalSharedLock("temp", new TimePoint(lockTimePoint), new TimePoint(lockTimePoint));
                printTemporalLockAcquireMessage(startTime, tx, node, "shared", "temp", 0, 0);
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }));
        Thread temporalLockThread2 = new Thread(() -> executeTransactionAfterSomeTime(100, startTime, tx -> {
            Node node = tx.findNode(label, "id", 0);
            long lockTimePoint = 1;
            if (isTemporalLockExclusive) {
                node.acquireTemporalExclusiveLock("temp", new TimePoint(lockTimePoint), new TimePoint(lockTimePoint));
                printTemporalLockAcquireMessage(startTime, tx, node, "exclusive", "temp", 0, 0);
            }
            else {
                node.acquireTemporalSharedLock("temp", new TimePoint(lockTimePoint), new TimePoint(lockTimePoint));
                printTemporalLockAcquireMessage(startTime, tx, node, "shared", "temp", 0, 0);
            }
            long sec = (System.nanoTime() - startTime) / 1000 / 1000 / 1000;
            testPassed1.set(sec == expectedWaitTime[0]);
        }));
        Thread normalLockThread = new Thread(() -> executeTransactionAfterSomeTime(200, startTime, tx -> {
            int entityId = lockSameEntity ? 0 : 1;
            Node node = tx.findNode(label, "id", entityId);
            if (isNormalLockExclusive) {
                tx.acquireWriteLock(node);
                printLockAcquireMessage(startTime, tx, node, "exclusive");
            }
            else {
                tx.acquireReadLock(node);
                printLockAcquireMessage(startTime, tx, node, "shared");
            }
            long sec = (System.nanoTime() - startTime) / 1000 / 1000 / 1000;
            testPassed2.set(sec == expectedWaitTime[1]);
        }));
        Thread temporalLockThread3 = new Thread(() -> executeTransactionAfterSomeTime(300, startTime, tx -> {
            Node node = tx.findNode(label, "id", 0);
            long lockTimePoint = 2;
            if (isTemporalLockExclusive) {
                node.acquireTemporalExclusiveLock("temp", new TimePoint(lockTimePoint), new TimePoint(lockTimePoint));
                printTemporalLockAcquireMessage(startTime, tx, node, "exclusive", "temp", 0, 0);
            }
            else {
                node.acquireTemporalSharedLock("temp", new TimePoint(lockTimePoint), new TimePoint(lockTimePoint));
                printTemporalLockAcquireMessage(startTime, tx, node, "shared", "temp", 0, 0);
            }
            long sec = (System.nanoTime() - startTime) / 1000 / 1000 / 1000;
            testPassed3.set(sec == expectedWaitTime[2]);
        }));
        temporalLockThread1.start();
        temporalLockThread2.start();
        normalLockThread.start();
        temporalLockThread3.start();
        temporalLockThread1.join();
        temporalLockThread2.join();
        normalLockThread.join();
        temporalLockThread3.join();
        assert testPassed1.get() && testPassed2.get() && testPassed3.get();
    }

    public static Stream<Arguments> temporalCompatibilityTestDataSource() {
        return Stream.of(
                Arguments.of(true, true, true, new long[]{0, 1, 1}),
                Arguments.of(true, true, false, new long[]{0, 0, 0}),
                Arguments.of(true, false, true, new long[]{0, 0, 0}),
                Arguments.of(true, false, false, new long[]{0, 0, 0}),
                Arguments.of(false, true, true, new long[]{0, 0, 0}),
                Arguments.of(false, true, false, new long[]{0, 0, 0}),
                Arguments.of(false, false, true, new long[]{0, 0, 0}),
                Arguments.of(false, false, false, new long[]{0, 0, 0})
        );
    }

    @ParameterizedTest
    @MethodSource("temporalCompatibilityTestDataSource")
    // test 3.2
    public void temporalCompatibilityTest(boolean sameEntity, boolean sameProperty, boolean intersectTimeRange, long[] expectedWaitTime) throws InterruptedException {
        long startTime = System.nanoTime();
        GraphDatabaseService db = dbms.database("neo4j");
        AtomicBoolean readTestPassed = new AtomicBoolean(false);
        AtomicBoolean writeTestPassed = new AtomicBoolean(false);
        AtomicBoolean upgradeTestPassed = new AtomicBoolean(false);
        try (Transaction tx = db.beginTx()) {
            for (int i = 0; i < 2; i++) {
                Node node = tx.createNode(label);
                node.setProperty("id", i);
                for (int j = 0; j < 2; j++) {
                    node.setTemporalProperty("temp" + j, new TimePoint(0), new TimePoint(0), 0);
                }
            }
            tx.commit();
        }
        Thread readThread1 = new Thread(() -> executeTransactionAfterSomeTime(0, startTime, tx -> {
            Node node = tx.findNode(label, "id", 0);
            long lockTimeStart = 0;
            node.acquireTemporalSharedLock("temp0", new TimePoint(lockTimeStart), new TimePoint(lockTimeStart + 1));
            printTemporalLockAcquireMessage(startTime, tx, node, "shared", "temp0", lockTimeStart, lockTimeStart + 1);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }));
        Thread readThread2 = new Thread(() -> executeTransactionAfterSomeTime(100, startTime, tx -> {
            Node node = tx.findNode(label, "id", 0);
            long lockTimeStart = 0;
            node.acquireTemporalSharedLock("temp0", new TimePoint(lockTimeStart), new TimePoint(lockTimeStart + 1));
            printTemporalLockAcquireMessage(startTime, tx, node, "shared", "temp0", lockTimeStart, lockTimeStart + 1);
            long sec = (System.nanoTime() - startTime) / 1000 / 1000 / 1000;
            readTestPassed.set(sec == expectedWaitTime[0]);
        }));
        Thread writeThread = new Thread(() -> executeTransactionAfterSomeTime(200, startTime, tx -> {
            int nodeId = sameEntity ? 0 : 1;
            String key = sameProperty ? "temp0" : "temp1";
            Node node = tx.findNode(label, "id", nodeId);
            long lockTimeStart = intersectTimeRange ? 1 : 2;
            node.acquireTemporalExclusiveLock(key, new TimePoint(lockTimeStart), new TimePoint(lockTimeStart + 1));
            printTemporalLockAcquireMessage(startTime, tx, node, "shared", key, lockTimeStart, lockTimeStart + 1);
            long sec = (System.nanoTime() - startTime) / 1000 / 1000 / 1000;
            writeTestPassed.set(sec == expectedWaitTime[1]);
        }));
        Thread readThread3 = new Thread(() -> executeTransactionAfterSomeTime(300, startTime, tx -> {
            Node node = tx.findNode(label, "id", 0);
            long lockTimeStart = 0;
            node.acquireTemporalSharedLock("temp0", new TimePoint(lockTimeStart), new TimePoint(lockTimeStart + 1));
            printTemporalLockAcquireMessage(startTime, tx, node, "shared", "temp0", lockTimeStart, lockTimeStart + 1);
            long sec = (System.nanoTime() - startTime) / 1000 / 1000 / 1000;
            upgradeTestPassed.set(sec == expectedWaitTime[2]);
        }));
        readThread1.start();
        readThread2.start();
        writeThread.start();
        readThread3.start();
        readThread1.join();
        readThread2.join();
        writeThread.join();
        readThread3.join();
        assert readTestPassed.get() && writeTestPassed.get() && upgradeTestPassed.get();
    }

    @Test
    // test 3.3(case 1)
    public void temporalDeadlockTest1() throws InterruptedException {
        long startTime = System.nanoTime();
        GraphDatabaseService db = dbms.database("neo4j");
        AtomicInteger finishedTransactionNum = new AtomicInteger();
        try (Transaction tx = db.beginTx()) {
            Node node = tx.createNode(label);
            node.setProperty("id", 0);
            node.setTemporalProperty("temp", new TimePoint(0), new TimePoint(0), 0);
            tx.commit();
        }
        Thread writeThread = new Thread(() -> executeTransactionAfterSomeTime(0, startTime, tx -> {
            Node node = tx.findNode(label, "id", 0);
            node.acquireTemporalExclusiveLock("temp", new TimePoint(0), new TimePoint(10));
            printTemporalLockAcquireMessage(startTime, tx, node, "exclusive", "temp", 0, 10);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            node.acquireTemporalExclusiveLock("temp", new TimePoint(20), new TimePoint(30));
            printTemporalLockAcquireMessage(startTime, tx, node, "exclusive", "temp", 20, 30);
            finishedTransactionNum.incrementAndGet();
        }));
        writeThread.start();
        Thread[] readThreads = new Thread[10];
        for (int i = 0; i < 10; i++) {
            int index = i;
            readThreads[i] = new Thread(() -> executeTransactionAfterSomeTime(100, startTime, tx -> {
                Node node = tx.findNode(label, "id", 0);
                node.acquireTemporalSharedLock("temp", new TimePoint(20 + index), new TimePoint(20 + index));
                printTemporalLockAcquireMessage(startTime, tx, node, "shared", "temp", 20 + index, 20 + index);
                node.acquireTemporalSharedLock("temp", new TimePoint(index), new TimePoint(index));
                printTemporalLockAcquireMessage(startTime, tx, node, "shared", "temp", index, index);
                finishedTransactionNum.incrementAndGet();
            }));
            readThreads[i].start();
        }
        writeThread.join();
        for (int i = 0; i < 10; i++) {
            readThreads[i].join();
        }
        long sec = (System.nanoTime() - startTime) / 1000 / 1000;
        // 死锁打断后快速恢复正常
        assert sec <= 1200;
        // 因为每个死锁循环只有2个事务，因此死锁打断是可以预期的
        assert finishedTransactionNum.get() == 1;
    }

    public static Stream<Arguments> temporalDeadlockTest2DataSource() {
        ArrayList<Arguments> result = new ArrayList<>();
        Random random = new Random();
        for (int transactionNum = 3; transactionNum < 10; transactionNum++) {
            for (int i = 0; i < 10; i++) {
                int[] extraLockTimes = null;
                if (random.nextBoolean()) {
                    extraLockTimes = new int[transactionNum];
                    for (int j = 0; j < transactionNum; j++) {
                        extraLockTimes[j] = random.nextInt(3);
                    }
                }
                result.add(Arguments.of(transactionNum, extraLockTimes));
            }
        }
        return result.stream();
    }

    @ParameterizedTest
    @MethodSource("temporalDeadlockTest2DataSource")
    public void temporalDeadlockTest2(int transactionNum, int[] extraLockTimes) throws InterruptedException {
        if (extraLockTimes != null) {
            if (extraLockTimes.length != transactionNum) throw new RuntimeException();
        }
        long startTime = System.nanoTime();
        GraphDatabaseService db = dbms.database("neo4j");
        AtomicInteger finishedTransactionNum = new AtomicInteger();
        try (Transaction tx = db.beginTx()) {
            Node node = tx.createNode(label);
            node.setProperty("id", 0);
            node.setTemporalProperty("temp", new TimePoint(0), new TimePoint(0), 0);
            tx.commit();
        }
        long[] startSleepTimeArray = new long[transactionNum];
        for (int i = 0; i < transactionNum; i++) {
            startSleepTimeArray[i] = i * 100L;
        }
        long gapTime = transactionNum * 100L;
        Thread[] threads = new Thread[transactionNum];
        for (int i = 0; i < transactionNum; i++) {
            long sleepTime = startSleepTimeArray[i];
            long firstLockStartTime = (i % transactionNum) * 10L;
            long thenLockStartTime = ((i + 1) % transactionNum) * 10L;
            int firstLockTimes = 1 + (extraLockTimes == null ? 0 : extraLockTimes[i]);
            threads[i] = new Thread(() -> executeTransactionAfterSomeTime(sleepTime, startTime, tx -> {
                Node node = tx.findNode(label, "id", 0);
                for (int j = 0; j < firstLockTimes; j++) {
                    node.acquireTemporalExclusiveLock("temp", new TimePoint(firstLockStartTime), new TimePoint(firstLockStartTime + 9));
                    printTemporalLockAcquireMessage(startTime, tx, node, "exclusive", "temp", firstLockStartTime, firstLockStartTime + 9);
                }
                try {
                    Thread.sleep(gapTime);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                node.acquireTemporalExclusiveLock("temp", new TimePoint(thenLockStartTime), new TimePoint(thenLockStartTime + 9));
                printTemporalLockAcquireMessage(startTime, tx, node, "exclusive", "temp", thenLockStartTime, thenLockStartTime + 9);
                finishedTransactionNum.incrementAndGet();
            }));
            threads[i].start();
        }
        for (int i = 0; i < transactionNum; i++) {
            threads[i].join();
        }

        long sec = (System.nanoTime() - startTime) / 1000 / 1000;
        // 死锁打断后快速恢复正常
        double expectedTime = (2 * transactionNum - 1) * 100;
        assert sec <= (long) (expectedTime * 1.1) || sec <= expectedTime + 150;
        // 如果不加多次锁，那么死锁检测还是可以预期的；如果加了多次锁，那么死锁检测就不太能预期了。但应该能能完成9/10的事务（不绝对）
        if (extraLockTimes == null) {
            assert finishedTransactionNum.get() == transactionNum - 1;
        }
        else {
            // 这个的assert不一定会过，但大部分情况下都应该是过的
            assert finishedTransactionNum.get() >= transactionNum * 4 / 5;
        }
    }
}
