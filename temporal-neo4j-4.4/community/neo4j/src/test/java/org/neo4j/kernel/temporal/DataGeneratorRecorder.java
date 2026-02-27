package org.neo4j.kernel.temporal;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;
import java.util.Scanner;

/**
 * 用于在提供数据记录文件时复现提供的数据，在没有提供数据记录文件时随机产生并记录数据。
 * 由于测试程序运行过程中可能存在随机输入产生到一半就产生异常的情况，因此记录文件中的内容可能并不完全。
 * 此时在修复bug后的下一次测试中剩余部分将继续以随机方式产生
 */
public class DataGeneratorRecorder {
    private final Scanner scanner;
    private final Random random = new Random();
    private final StringBuilder builder = new StringBuilder();
    private final File dataDir;
    private boolean newDataGenerated = false;
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789./*-+~!@#$%^&*()_`{}[]\\ \"'|;:,";

    public DataGeneratorRecorder(File dataDir) {
        this.scanner = null;
        assert dataDir.exists() && dataDir.isDirectory();
        this.dataDir = dataDir;
        System.out.println("本次测试的数据来源为随机数");
    }

    public DataGeneratorRecorder(File dataDir, File dataFile) {
        try {
            this.scanner = new Scanner(dataFile);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        assert dataDir.exists() && dataDir.isDirectory();
        this.dataDir = dataDir;
        System.out.println("本次测试的数据来源为过往失败的测试");
    }

    public int nextInt(int bound) {
        if (scanner != null) {
            if (scanner.hasNext()) {
                int ret = scanner.nextInt();
                builder.append(ret).append("\n");
                return ret;
            }
        }
        newDataGenerated = true;
        int result = random.nextInt(bound);
        builder.append(result).append("\n");
        return result;
    }

    public int nextInt() {
        if (scanner != null) {
            if (scanner.hasNext()) {
                int ret = scanner.nextInt();
                builder.append(ret).append("\n");
                return ret;
            }
        }
        newDataGenerated = true;
        int result = random.nextInt();
        builder.append(result).append("\n");
        return result;
    }

    public boolean nextBoolean() {
        if (scanner != null) {
            if (scanner.hasNext()) {
                int ret = scanner.nextInt();
                builder.append(ret).append("\n");
                return ret == 1;
            }
        }
        newDataGenerated = true;
        boolean result = random.nextBoolean();
        builder.append(result ? 1 : 0).append("\n");
        return result;
    }

    public String nextString(int maxSize) {
        if (scanner != null) {
            if (scanner.hasNext()) {
                int length = scanner.nextInt();
                builder.append(length).append("\n");
                StringBuilder result = new StringBuilder();
                for (int i = 0; i < length; i++) {
                    int index = scanner.nextInt();
                    builder.append(index).append("\n");
                    result.append(ALPHABET.charAt(index));
                }
                return result.toString();
            }
        }
        newDataGenerated = true;
        int length = random.nextInt(maxSize) + 1;
        builder.append(length).append("\n");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < length; i++) {
            int index = random.nextInt(ALPHABET.length());
            builder.append(index).append("\n");
            result.append(ALPHABET.charAt(index));
        }
        return result.toString();
    }

    public double nextDouble() {
        if (scanner != null) {
            if (scanner.hasNext()) {
                int a = scanner.nextInt(), b = scanner.nextInt();
                builder.append(a).append("\n").append(b).append("\n");
                return (double) a / (double) b;
            }
        }
        int a = random.nextInt(), b = random.nextInt();
        builder.append(a).append("\n").append(b).append("\n");
        return (double) a / (double) b;
    }

    public void flush() {
        if (!newDataGenerated) return;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        String fileName = sdf.format(new Date()) + ".txt";
        File fileToStore = new File(dataDir.toString(), fileName);
        try (FileWriter writer = new FileWriter(fileToStore)) {
            writer.write(builder.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void close() throws Exception {
        if (scanner != null) {
            scanner.close();
        }
    }
}
