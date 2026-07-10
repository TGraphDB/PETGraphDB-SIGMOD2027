package edu.buaa.server.system;

import org.act.temporalProperty.impl.CompressionType;
import org.act.temporalProperty.impl.Options;
import org.slf4j.LoggerFactory;

public class TGraphKernelBigLockSnappyServer extends TGraphKernelServer {
    private static void setDefaultArgs() {
        dbKernelProxy = new TGraphKernelBigLockSnappyServer();
        log = LoggerFactory.getLogger(TGraphKernelBigLockSnappyServer.class);
        // 粗粒度锁，默认读已提交
        bigLock = true;
        apReadLock = false;
        tpReadLock = false;
        // 开启snappy压缩
        Options.setGlobalCompressionType(CompressionType.SNAPPY);
    }

    public static void main(String[] args) {
        setDefaultArgs();
        startServer();
    }
}
