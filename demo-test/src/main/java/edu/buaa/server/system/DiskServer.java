package edu.buaa.server.system;

import edu.buaa.common.RuntimeEnv;
import edu.buaa.common.server.DBSocketServer;
import edu.buaa.common.transaction.AbstractTransaction;
import edu.buaa.common.transaction.TransactionFailedException;
import edu.buaa.utils.Helper;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * 在一个文件中反复将事务内容直接追加写入，引入网络延迟和I/O
 */
public class DiskServer implements DBSocketServer.DBKernelProxy {
    File dataFile;
    BufferedWriter writer;

    public static void main(String[] args) {
        DBSocketServer server = new DBSocketServer(dbDir(), new DiskServer(), Integer.parseInt(Helper.mustEnv("DB_PORT")), true);
        RuntimeEnv env = RuntimeEnv.getCurrentEnv();
        String serverCodeVersion = env.name() + "." + Helper.codeGitVersion();
        System.out.println("server code version:" + serverCodeVersion);
        try {
            server.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static File dbDir() {
        String path = Helper.mustEnv("DB_PATH");
        File dbDir = new File(path);
        if (!dbDir.exists()) {
            if (dbDir.mkdirs()) return dbDir;
            else throw new IllegalArgumentException("invalid dbDir");
        } else if (!dbDir.isDirectory()) {
            throw new IllegalArgumentException("invalid dbDir");
        }
        return dbDir;
    }

    @Override
    public void start(File path) throws Exception {
        dataFile = new File(path, "data.txt");
        if (!dataFile.exists()) {
            writer = new BufferedWriter(new FileWriter(dataFile));
            writer.flush();
        }
    }

    @Override
    public void shutdown() throws IOException {
        writer.close();
    }

    @Override
    public AbstractTransaction.Result execute(String line) throws TransactionFailedException {
        try {
            writer.write(line);
            writer.newLine();
            writer.flush();
            return new AbstractTransaction.Result();
        } catch (IOException e) {
            throw new TransactionFailedException(e);
        }
    }
}
