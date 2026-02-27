package edu.buaa.server.store;

import com.google.common.base.Preconditions;
import edu.buaa.common.RuntimeEnv;
import edu.buaa.common.server.DBSocketServer;
import edu.buaa.utils.Helper;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * 本server的key组合方式为entityPropertyId+timestamp
 */
public class RocksDBServer1 extends RocksDBServer {
    public static void main(String[] args) {
        DBSocketServer server = new DBSocketServer(dbDir(), new RocksDBServer1(), Integer.parseInt(Helper.mustEnv("DB_PORT")), true);
        RuntimeEnv env = RuntimeEnv.getCurrentEnv();
        String serverCodeVersion = env.name() + "." + Helper.codeGitVersion();
        System.out.println("server code version:" + serverCodeVersion);
        try {
            server.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected byte[] getKeyBytes(TemporalKey key) {
        return ByteBuffer.allocate(Long.BYTES + Integer.BYTES + Long.BYTES).putLong(key.getEntityId())
                .putInt(key.getPropertyId()).putLong(key.getTimestamp()).array();
    }

    @Override
    protected TemporalKey KeyFromBytes(byte[] bytes) {
        Preconditions.checkArgument(bytes.length == Long.BYTES + Integer.BYTES + Long.BYTES);
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        long entityId = buffer.getLong();
        int propertyId = buffer.getInt();
        long timestamp = buffer.getLong();
        return new TemporalKey(entityId, propertyId, timestamp);
    }

    @Override
    protected int getMemTableSize() {
        return 64;
    }

    public static class RocksDBServer1Mem1 extends RocksDBServer1 {
        @Override
        protected int getMemTableSize() {
            return 1;
        }

        public static void main(String[] args) {
            DBSocketServer server = new DBSocketServer(dbDir(), new RocksDBServer1Mem1(), Integer.parseInt(Helper.mustEnv("DB_PORT")), true);
            RuntimeEnv env = RuntimeEnv.getCurrentEnv();
            String serverCodeVersion = env.name() + "." + Helper.codeGitVersion();
            System.out.println("server code version:" + serverCodeVersion);
            try {
                server.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static class RocksDBServer1Mem4 extends RocksDBServer1 {
        @Override
        protected int getMemTableSize() {
            return 4;
        }

        public static void main(String[] args) {
            DBSocketServer server = new DBSocketServer(dbDir(), new RocksDBServer1Mem4(), Integer.parseInt(Helper.mustEnv("DB_PORT")), true);
            RuntimeEnv env = RuntimeEnv.getCurrentEnv();
            String serverCodeVersion = env.name() + "." + Helper.codeGitVersion();
            System.out.println("server code version:" + serverCodeVersion);
            try {
                server.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static class RocksDBServer1Mem16 extends RocksDBServer1 {
        @Override
        protected int getMemTableSize() {
            return 16;
        }

        public static void main(String[] args) {
            DBSocketServer server = new DBSocketServer(dbDir(), new RocksDBServer1Mem16(), Integer.parseInt(Helper.mustEnv("DB_PORT")), true);
            RuntimeEnv env = RuntimeEnv.getCurrentEnv();
            String serverCodeVersion = env.name() + "." + Helper.codeGitVersion();
            System.out.println("server code version:" + serverCodeVersion);
            try {
                server.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
