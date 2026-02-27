package edu.buaa.utils;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSONWriter;
import edu.buaa.common.transaction.AbstractTransaction;
import org.neo4j.graphdb.Transaction;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class TxStatisticSaver {
    private static boolean statisticRecorded;
    private final AtomicInteger receivedCount = new AtomicInteger(0);
    private long startTimeInMilli = -1, endTimeInMilli = -1;
    private final HashMap<String, BufferedWriter> writes = new HashMap<>();
    private static final boolean writeResultEnabled = true;

    public static boolean isStatisticRecorded() {
        return statisticRecorded;
    }

    static {
        try {
            Class.forName("org.neo4j.graphdb.inspect.TransactionTemporalStatisticRecorder");
            statisticRecorded = true;
        } catch (ClassNotFoundException e) {
            statisticRecorded = false;
        }
    }

    public void receiveOneTransaction() {
        receivedCount.incrementAndGet();
    }

    private final File root;

    public TxStatisticSaver(File root) {
        this.root = root;
        if (root == null) return;
        // 确保root指向一个空文件夹
        if (!root.exists()) {
            if (!root.mkdir()) throw new RuntimeException();
        }
        else if (!root.isDirectory()) throw new RuntimeException();
        else if (Objects.requireNonNull(root.listFiles()).length != 0) throw new RuntimeException();
        Thread thread = new Thread(() -> {
            System.out.println("inspect enabled: " + statisticRecorded);
            while (true) {
                if (statisticRecorded) System.out.println("current buffer size: " + getBufferSize());
                System.out.println("current received transaction num: " + receivedCount.get() + "\n\n\n\n");
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    public void setArgs(Transaction transaction, int txId, AbstractTransaction.TxType type) {
        if (!statisticRecorded) return;
        if (root == null) return;
        try {
            ArrayList<Object> args = new ArrayList<>();
            args.add(txId);
            args.add(type);
            Transaction.class.getMethod("setRecorderArgs", ArrayList.class).invoke(transaction, args);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public void save() {
        if (!statisticRecorded) return;
        try {
            Object recorder = Class.forName("org.neo4j.graphdb.inspect.RecorderContainer").getMethod("take").invoke(null);
            if (root == null || !writeResultEnabled) {
                return;
            }
            ArrayList<Object> args = (ArrayList<Object>) Class.forName("org.neo4j.graphdb.inspect.TransactionTemporalStatisticRecorder").getMethod("getArgs").invoke(recorder);
            JSONObject object = (JSONObject) Class.forName("org.neo4j.graphdb.inspect.TransactionTemporalStatisticRecorder").getMethod("toJsonObject").invoke(recorder);
            AbstractTransaction.TxType type = (AbstractTransaction.TxType) args.get(1);
            BufferedWriter writer = writes.computeIfAbsent(type.name(), t -> {
                try {
                    return new BufferedWriter(new FileWriter(new File(root, t + ".jsonl")));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            writer.write(object.toString());
            writer.write("\n");
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException | ClassNotFoundException |
                 IOException e) {
            throw new RuntimeException(e);
        }
    }

    public int getBufferSize() {
        if (!statisticRecorded) return -1;
        try {
             Field field = Class.forName("org.neo4j.graphdb.inspect.RecorderContainer").getDeclaredField("queue");
             field.setAccessible(true);
            LinkedBlockingQueue<?> queue = (LinkedBlockingQueue<?>) field.get(null);
            return queue.size();
        } catch (IllegalAccessException | ClassNotFoundException |
                 NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean finished() {
        if (!statisticRecorded) return true;
        if (root == null) return true;
        try {
            return (boolean) Class.forName("org.neo4j.graphdb.inspect.RecorderContainer").getMethod("isEmpty").invoke(null);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public void starting() {
        if (this.startTimeInMilli == -1) this.startTimeInMilli = System.currentTimeMillis();
    }

    public void finishing() {
        if (this.endTimeInMilli == -1) this.endTimeInMilli = System.currentTimeMillis();
    }

    public void saveTPM() throws IOException {
        if (root == null) return;
        try (FileWriter writer = new FileWriter(new File(root, "tpm.txt"))) {
            long timeUsedInMilli = endTimeInMilli - startTimeInMilli;
            double result = (double) receivedCount.get() / ((double) timeUsedInMilli / 60_000);
            writer.write(String.valueOf(result));
        }
    }

    public void close() throws IOException {
        for (BufferedWriter writer : writes.values()) {
            writer.close();
        }
    }
}
