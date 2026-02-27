package edu.buaa.server.store;

import com.alibaba.fastjson.JSON;
import edu.buaa.common.RuntimeEnv;
import edu.buaa.common.server.DBSocketServer;
import edu.buaa.common.transaction.*;
import edu.buaa.common.utils.PFieldList;
import edu.buaa.utils.Helper;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Set;

/**
 * empty意味着不做任何时态属性上的操作，仅执行静态操作和在时态操作中根据静态管理器获取相应的id（baseline）
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

    private StaticDataManager nodeStaticManager, relStaticManager;

    @Override
    public void start(File path) throws Exception {
        File staticDir = new File(path, "static");
        createDirIfNotExists(staticDir);
        nodeStaticManager = new StaticDataManager(new File(staticDir, "node"));
        relStaticManager = new StaticDataManager(new File(staticDir, "relationship"));
    }

    private static void createDirIfNotExists(File dir) {
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw new IllegalArgumentException("invalid dbDir");
            }
        } else if (!dir.isDirectory()) {
            throw new IllegalArgumentException("invalid dbDir");
        }
    }

    @Override
    public void shutdown() {
        nodeStaticManager.shutdown();
        relStaticManager.shutdown();
    }

    @Override
    public AbstractTransaction.Result execute(String line) throws TransactionFailedException {
        return new AbstractTransaction.Result();
    }

}
