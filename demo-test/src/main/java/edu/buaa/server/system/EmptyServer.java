package edu.buaa.server.system;

import edu.buaa.common.RuntimeEnv;
import edu.buaa.common.server.DBSocketServer;
import edu.buaa.common.transaction.AbstractTransaction;
import edu.buaa.common.transaction.TransactionFailedException;
import edu.buaa.utils.Helper;

import java.io.File;
import java.io.IOException;

/**
 * 什么都不做，仅用于引入网络延迟，是一个真正的空server
 */
public class EmptyServer implements DBSocketServer.DBKernelProxy {
    public static void main(String[] args) {
        DBSocketServer server = new DBSocketServer(dbDir(), new EmptyServer(), Integer.parseInt(Helper.mustEnv("DB_PORT")), true);
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

    }

    @Override
    public void shutdown() {

    }

    @Override
    public AbstractTransaction.Result execute(String line) throws TransactionFailedException {
        return new AbstractTransaction.Result();
    }
}
