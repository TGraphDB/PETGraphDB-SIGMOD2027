// tested, very slow.
//package edu.buaa.utils;
//
//import com.google.common.collect.AbstractIterator;
//import com.google.common.primitives.Longs;
//import org.rocksdb.Options;
//import org.rocksdb.RocksDB;
//import org.rocksdb.RocksDBException;
//import org.rocksdb.RocksIterator;
//
//import java.io.File;
//import java.io.IOException;
//
//public class KVDisk {
//    private final RocksDB db;
//    private final File dbDir;
//
//    public KVDisk(File dbDir) {
//        this.dbDir = dbDir;
//        Helper.deleteExistDB(dbDir);
//        RocksDB.loadLibrary();
//        Options options = new Options();
//        options.setCreateIfMissing(true);
//        try {
//            db = RocksDB.open(options, dbDir.getAbsolutePath());
//        } catch (RocksDBException e) {
//            throw new RuntimeException(e);
//        }
//    }
//
//    public void add(byte[] key, byte[] val){
//        try {
//            db.put(key, val);
//        } catch (RocksDBException e) {
//            throw new RuntimeException(e);
//        }
//    }
//
//    public byte[] get(byte[] key){
//        try {
//            return db.get(key);
//        } catch (RocksDBException e) {
//            throw new RuntimeException(e);
//        }
//    }
//
//    public RocksIterator getAll(){
//        return db.newIterator();
//    }
//
//    public void close() throws IOException {
//        db.close();
//        Helper.deleteAllFilesOfDir(dbDir);
//    }
//
//    public void add(long key, long val) {
//        this.add(Longs.toByteArray(key), Longs.toByteArray(val));
//    }
//}
