package org.neo4j.kernel.temporal;

import org.act.temporalProperty.util.Slice;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.temporal.TimePoint;
import org.neo4j.kernel.impl.api.InternalTransactionCommitProcess;

import java.io.File;

public class ProcessOfTransactionLogAndRecoveryPhaseOneTest {
    private static int count = 0;
    private static boolean initializing = true;
    private static final boolean SEND_DETAIL_COMMANDS = false;
    public static void main(String[] args) {
        Command.CommandTransporter transporter = new Command.CommandTransporter(System.in);
        Thread guard = new Thread(() -> {
            try {
                Thread.sleep(1000 * 60 * 20);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            System.err.println("time out!");
            System.exit(1);
        });
        guard.start();
        System.out.println("starting crash database...");
        try {
            int transactionSize = Integer.parseInt(args[0]);
            int transactionNum = Integer.parseInt(args[1]);
            boolean crashDatabase = Boolean.parseBoolean(args[2]);
            File file = new File(args[3]);
            if (!file.exists()) {
                throw new RuntimeException("database dir not exist");
            }
            file = new File(file.getParent(), "db_crash");
            if (!file.exists()) {
                if (!file.mkdir()) {
                    throw new RuntimeException("fail to make database for crash!");
                }
            }
            DatabaseManagementService dbms = new DatabaseManagementServiceBuilder(file.toPath()).build();
            System.out.println("crash database started!");
            System.out.println("transaction size: " + transactionSize);
            System.out.println("transaction num: " + transactionNum);
            System.out.println("crash database: " + crashDatabase);
            GraphDatabaseService db = dbms.database("neo4j");
            InternalTransactionCommitProcess.Debug.debugging = true;
            try (Transaction transaction = db.beginTx()) {
                while (true) {
                    Command command = transporter.get();
                    assert command != null;
                    boolean isStart = executeCommand(command, transaction);
                    if (isStart) {
                        break;
                    }
                }
                transaction.commit();
            }
            initializing = false;
            for (int transactionIndex = 0; transactionIndex < transactionNum; transactionIndex++) {
                try (Transaction transaction = db.beginTx()) {
                    for (int commandIndex = 0; commandIndex < transactionSize; commandIndex++) {
                        Command command = transporter.get();
                        assert command != null;
                        executeCommand(command, transaction);
                    }
                    if (transactionIndex == transactionNum - 1) {
                        if (crashDatabase) {
                            InternalTransactionCommitProcess.Debug.crash = true;
                        }
                    }
                    transaction.commit();
                }
                int finishedNum = (transactionIndex + 1) * transactionSize;
                if (finishedNum % 100 == 0) {
                    System.out.println(finishedNum + " transactions in this round have finished!");
                }
            }
            dbms.shutdown();
        }
        catch (Throwable e) {
            e.printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }

    private static boolean executeCommand(Command command, Transaction transaction) {
        if (SEND_DETAIL_COMMANDS) {
            if (initializing) {
                System.out.println("executing command " + "init" + ", command: " + command);
            } else {
                System.out.println("executing command " + count + ", command: " + command);
                count++;
            }
        }
        int op = command.getOperation(), type = command.getEntityType();
        if (op == Command.CREATE) {
            if (type == Command.NODE) {
                Node node = transaction.createNode(GraphForTestValidation.nodeLabel);
                node.setProperty("id", command.getNodeId());
            }
            else if (type == Command.RELATIONSHIP) {
                Node start = transaction.findNode(GraphForTestValidation.nodeLabel, "id", command.getStartId());
                Node end = transaction.findNode(GraphForTestValidation.nodeLabel, "id", command.getEndId());
                start.createRelationshipTo(end, GraphForTestValidation.relationshipType);
            }
            else {
                throw new RuntimeException("wrong command!");
            }
        }
        else if (op == Command.DELETE) {
            if (type == Command.NODE) {
                Node node = transaction.findNode(GraphForTestValidation.nodeLabel, "id", command.getNodeId());
                Iterable<Relationship> relationshipsOfNode = node.getRelationships();
                relationshipsOfNode.forEach(Relationship::delete);
                node.delete();
            }
            else if (type == Command.RELATIONSHIP) {
                findRelationship(transaction, command.getStartId(), command.getEndId()).delete();
            }
            else {
                throw new RuntimeException("wrong command!");
            }
        }
        else if (op == Command.SET_TEMPORAL_PROPERTY) {
            Entity entity;
            if (type == Command.NODE) {
                entity = transaction.findNode(GraphForTestValidation.nodeLabel, "id", command.getNodeId());
            }
            else if (type == Command.RELATIONSHIP) {
                entity = findRelationship(transaction, command.getStartId(), command.getEndId());
            }
            else {
                throw new RuntimeException("wrong command!");
            }
            Object value = command.getValue();
            if (value instanceof String) {
                value = new Slice(((String) value).getBytes());
            }
            entity.setTemporalProperty(command.getKey(), new TimePoint(command.getStart()), new TimePoint(command.getEnd()), value);
        }
        else if (op == Command.START) {
            System.out.println("init commands executed.");
            return true;
        }
        else if (op == Command.EMPTY) {
            return false;
        }
        else {
            throw new RuntimeException("wrong command!");
        }
        return false;
    }

    private static Relationship findRelationship(Transaction transaction, int startId, int endId) {
        Node start = transaction.findNode(GraphForTestValidation.nodeLabel, "id", startId);
        Iterable<Relationship> relationships = start.getRelationships(Direction.OUTGOING);
        final Relationship[] relationship = {null};
        relationships.forEach(relationship1 -> {
            int endNodeId = (int) relationship1.getEndNode().getProperty("id");
            if (endNodeId == endId) {
                if (relationship[0] == null) {
                    relationship[0] = relationship1;
                }
                else {
                    throw new RuntimeException("multi relationships between same two nodes!");
                }
            }
        });
        if (relationship[0] == null) {
            throw new RuntimeException("can not find corresponding relationship!");
        }
        return relationship[0];
    }
}
