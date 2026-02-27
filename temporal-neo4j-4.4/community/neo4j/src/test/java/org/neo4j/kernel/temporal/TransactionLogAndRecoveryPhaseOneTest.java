package org.neo4j.kernel.temporal;

import org.act.temporalProperty.exception.TPSException;
import org.act.temporalProperty.exception.TPSRuntimeException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.*;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Objects;
import java.util.stream.Stream;

public class TransactionLogAndRecoveryPhaseOneTest extends TestBase {
    private static final Class<? extends TestBase> thisClass = TransactionLogAndRecoveryPhaseOneTest.class;
    private static final boolean DEVELOP_FINISHED = true;
    // 有的时候测试实在太慢了，为了验证没有主要流程上的错误，开启快速模式进行debug。当开发基本完成后，关闭快速模式以尽可能多地发现可能存在的bug
    private static final boolean QUICK_TEST = false;

    private Process runningProcess = null;
    private DatabaseManagementService dbms = null;

    @BeforeAll
    public static void prepare() throws NoSuchMethodException {
        prepareDir(thisClass);
    }

    @RepeatedTest(20)
    public void correctTestBeforeDevelopment() throws Exception {
        Method thisMethod = thisClass.getMethod(Thread.currentThread().getStackTrace()[1].getMethodName());
        DataGeneratorRecorder dataGeneratorRecorder = getNextDataGenerator(thisClass, thisMethod);
        clearDatabase(thisClass, thisMethod);
        try {
            normalTest(dataGeneratorRecorder, false, false, Objects.requireNonNull(getDatabaseHome(thisClass, thisMethod)).getPath());
        }
        catch (Throwable e) {
            dataGeneratorRecorder.flush();
            printChildErrMsg();
            throw new RuntimeException(e);
        }
        finally {
            dataGeneratorRecorder.close();
            dbms.shutdown();
        }
    }

    @RepeatedTest(20)
    public void wrongTestBeforeDevelopment() throws Exception {
        if (DEVELOP_FINISHED) {
            return;
        }
        Method thisMethod = thisClass.getMethod(Thread.currentThread().getStackTrace()[1].getMethodName());
        DataGeneratorRecorder dataGeneratorRecorder = getNextDataGenerator(thisClass, thisMethod);
        clearDatabase(thisClass, thisMethod);
        try {
            normalTest(dataGeneratorRecorder, false, true, Objects.requireNonNull(getDatabaseHome(thisClass, thisMethod)).getPath());
        }
        catch (Throwable e) {
            boolean isExpectingError = false;
            if (e instanceof AssertionError) {
                isExpectingError = true;
            }
            // 不将neo4j提供的异常作为预期异常的原因是neo4j本身数据是持久化的，不存在丢失的问题，如果捕获到了反而说明我们的开发有问题
            else {
                if (e instanceof TPSRuntimeException || e instanceof TPSException) {
                    isExpectingError = true;
                }
                else {
                    for (Throwable ex = e; ex == ex.getCause(); ex = ex.getCause()) {
                        if (ex instanceof TPSRuntimeException || ex instanceof TPSException) {
                            isExpectingError = true;
                            break;
                        }
                    }
                }
            }
            if (!isExpectingError) {
                dataGeneratorRecorder.flush();
                printChildErrMsg();
                throw new RuntimeException(e);
            }
        }
        finally {
            dataGeneratorRecorder.close();
            dbms.shutdown();
        }
    }

    @RepeatedTest(10)
    public void oneNodeTestAfterDevelopment() throws Exception {
        if (!DEVELOP_FINISHED) {
            return;
        }
        Method thisMethod = thisClass.getMethod(Thread.currentThread().getStackTrace()[1].getMethodName());
        DataGeneratorRecorder dataGeneratorRecorder = getNextDataGenerator(thisClass, thisMethod);
        clearDatabase(thisClass, thisMethod);
        try {
            normalTest(dataGeneratorRecorder, true, true, Objects.requireNonNull(getDatabaseHome(thisClass, thisMethod)).getPath());
        }
        catch (Throwable e) {
            dataGeneratorRecorder.flush();
            printChildErrMsg();
            throw new RuntimeException(e);
        }
        finally {
            dataGeneratorRecorder.close();
            dbms.shutdown();
        }
    }

    @RepeatedTest(200)
    public void testAfterDevelopment() throws Exception {
        if (!DEVELOP_FINISHED) {
            return;
        }
        Method thisMethod = thisClass.getMethod(Thread.currentThread().getStackTrace()[1].getMethodName());
        DataGeneratorRecorder dataGeneratorRecorder = getNextDataGenerator(thisClass, thisMethod);
        clearDatabase(thisClass, thisMethod);
        try {
            normalTest(dataGeneratorRecorder, false, true, Objects.requireNonNull(getDatabaseHome(thisClass, thisMethod)).getPath());
        }
        catch (Throwable e) {
            dataGeneratorRecorder.flush();
            printChildErrMsg();
            throw new RuntimeException(e);
        }
        finally {
            dataGeneratorRecorder.close();
            dbms.shutdown();
        }
    }

    private void normalTest(DataGeneratorRecorder dataGeneratorRecorder, boolean isOneNode, boolean triggerCrash, String databasePath) throws IOException {
        dbms = new DatabaseManagementServiceBuilder(new File(databasePath).toPath()).build();
        GraphDatabaseService db = dbms.database("neo4j");
        GraphForTestValidation graph;
        try (Transaction transaction = db.beginTx()) {
            graph = new GraphForTestValidation(transaction, dataGeneratorRecorder, isOneNode);
            transaction.commit();
        }
        // 进行a*b*c=100000次操作，中间不再穿插验证
        int temp = dataGeneratorRecorder.nextInt(4);
        // transactionSize如果太小就会导致单次测试运行时间过长而被junit掐掉，因此最少是100
        int transactionSize = 100, transactionNum = 1000;
        for (int i = 0; i < temp; i++) {
            transactionSize *= 10;
            transactionNum /= 10;
        }
        // 如果是粗略验证的快速测试，则扩大事务规模，限制事务数量，事务数量越少执行越快
        if (QUICK_TEST) {
            if (transactionSize < 1000) {
                transactionSize = 1000;
                transactionNum = 100;
            }
        }
        // 如果这部分的功能开发完成，则不做任何修改，如果开发还没有完成，则为了检验数据库确实在崩溃后无法恢复时态数据，
        // 将所有操作都压到一条事务中，保证所有时态数据全部丢失
        if (!DEVELOP_FINISHED) {
            if (triggerCrash) {
                transactionSize = 100000;
                transactionNum = 1;
            }
        }
        System.out.println("transaction size: " + transactionSize);
        int round;
        if (transactionNum == 1) {
            round = 1;
        }
        else {
            round = 10;
            transactionNum /= 10;
        }
        ProcessBuilder processBuilder = getProcessBuilder(transactionSize, transactionNum, triggerCrash, databasePath);
        try (Transaction transaction = db.beginTx()) {
            for (int i = 0; i < round; i++) {
                runningProcess = processBuilder.start();
                try {
                    printChildErrMsg(runningProcess.exitValue());
                    throw new RuntimeException("process is not running!");
                }
                catch (IllegalThreadStateException ignored) {}
                graph.setTransporter(new Command.CommandTransporter(runningProcess.getOutputStream()));
                BufferedReader reader = new BufferedReader(new InputStreamReader(runningProcess.getInputStream()));
                while (true) {
                    String string = reader.readLine();
                    System.out.println("child: " + string);
                    if (string.contains("database started")) {
                        break;
                    }
                }
                int finalI = i;
                Thread outputListener = new Thread(() -> {
                    while (true) {
                        try {
                            String string = reader.readLine();
                            if (string == null) break;
                            String commandPrefix = "executing command ";
                            if (string.startsWith(commandPrefix)) {
                                if (!string.contains(graph.getTransporter().getCommand().toString())) {
                                    System.out.println("pushed command not received:");
                                }
                            }
                            System.out.println("child " + finalI + ": " + string);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
                outputListener.start();
                if (i == 0) {
                    graph.flushInitialCommands();
                }
                graph.getTransporter().set(Command.startCommand());
                for (int j = 0; j < transactionSize * transactionNum; j++) {
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
                int exitCode = runningProcess.waitFor();
                printChildErrMsg(exitCode);
                outputListener.interrupt();
                reader.close();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        dbms.shutdown();
        File crashDatabaseFile = new File(new File(databasePath).getParent(), "db_crash");
        dbms = new DatabaseManagementServiceBuilder(crashDatabaseFile.toPath()).build();
        db = dbms.database("neo4j");
        try (Transaction transaction = db.beginTx()) {
            graph.validateAll(transaction);
            transaction.commit();
        }
        dbms.shutdown();
    }

    private void printChildErrMsg(int exitCode) throws IOException {
        if (exitCode != 0) {
            System.err.println("error message from child:");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(runningProcess.getErrorStream()))) {
                Stream<String> lines = reader.lines();
                lines.forEach(System.err::println);
            }
            throw new RuntimeException("child process failed!");
        }
    }

    private void printChildErrMsg() throws IOException {
        printChildErrMsg(runningProcess.exitValue());
    }

    private ProcessBuilder getProcessBuilder(int batchSize, int round, boolean crashDatabase, String mainDatabasePath) {
        ArrayList<String> cmdArray = new ArrayList<>();
        String javaHome = System.getProperty("java.home");
        String java = javaHome + File.separator + "bin" + File.separator + "java";
        cmdArray.add(java);
        String encoding = "-Dfile.encoding=" + Charset.defaultCharset().name();
        cmdArray.add(encoding);
        cmdArray.add("-cp");
        String sysCp = System.getProperty("java.class.path");
        cmdArray.add(sysCp);
        cmdArray.add(ProcessOfTransactionLogAndRecoveryPhaseOneTest.class.toString().split(" ")[1]);

        cmdArray.add(String.valueOf(batchSize));
        cmdArray.add(String.valueOf(round));
        cmdArray.add(String.valueOf(crashDatabase));
        cmdArray.add(mainDatabasePath);
        String[] result = new String[cmdArray.size()];
        for (int i = 0; i < cmdArray.size(); i++) {
            result[i] = cmdArray.get(i);
        }
        return new ProcessBuilder(result);
    }
}
