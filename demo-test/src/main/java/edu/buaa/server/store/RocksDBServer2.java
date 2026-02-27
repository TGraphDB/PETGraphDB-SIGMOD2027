package edu.buaa.server.store;

import com.google.common.base.Preconditions;
import edu.buaa.common.RuntimeEnv;
import edu.buaa.common.server.DBSocketServer;
import edu.buaa.utils.Helper;

import java.io.IOException;
import java.nio.ByteBuffer;

public class RocksDBServer2 extends RocksDBServer {
    public static void main(String[] args) {
        DBSocketServer server = new DBSocketServer(dbDir(), new RocksDBServer2(), Integer.parseInt(Helper.mustEnv("DB_PORT")), true);
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
        return ByteBuffer.allocate(Long.BYTES + Long.BYTES + Integer.BYTES).putLong(key.getTimestamp())
                .putLong(key.getEntityId()).putInt(key.getPropertyId()).array();
    }

    @Override
    protected TemporalKey KeyFromBytes(byte[] bytes) {
        Preconditions.checkArgument(bytes.length == Long.BYTES + Long.BYTES + Integer.BYTES);
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        long timestamp = buffer.getLong();
        long entityId = buffer.getLong();
        int propertyId = buffer.getInt();
        return new TemporalKey(entityId, propertyId, timestamp);
    }

    @Override
    protected int getMemTableSize() {
        return 64;
    }
}
