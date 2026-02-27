package edu.buaa.utils;

//import com.google.common.cache.CacheBuilder;
//import net.spy.memcached.MemcachedClient;
//import org.mapdb.DB;
//import org.mapdb.DBMaker;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class KVMem implements AutoCloseable{
//    private MemcachedClient mcc;
//    public KVMem(String ip, int port) throws IOException {
//        mcc = new MemcachedClient(new InetSocketAddress(ip, port));
//        System.out.println("Connection to memcached server successful.");
//    }
//    public KVMem(){}
//
//    public void set(String key, String value){
//        try {
//            mcc.set(key, 0, value).get();
//        } catch (InterruptedException | ExecutionException e) {
//            throw new IllegalStateException("fail to set memcached. "+key+":"+value);
//        }
//    }
//
//    public Object get(String key){
//        return mcc.get(key);
//    }
//
    @Override
    public void close() throws Exception {
//        mcc.shutdown();
    }

    public static class TiCache extends LinkedHashMap<byte[], Long> {
        private final int maxCnt;
        public TiCache(int maxCnt) {
            this.maxCnt = maxCnt;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<byte[], Long> eldest) {
            return size() > maxCnt;
        }
    }
}
