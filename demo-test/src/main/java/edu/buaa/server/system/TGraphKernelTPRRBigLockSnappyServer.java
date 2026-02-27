package edu.buaa.server.system;

import org.act.temporalProperty.impl.CompressionType;
import org.act.temporalProperty.impl.Options;
import org.slf4j.LoggerFactory;

public class TGraphKernelTPRRBigLockSnappyServer extends TGraphKernelServer {
    private static void setDefaultArgs() {
        dbKernelProxy = new TGraphKernelTPRRBigLockSnappyServer();
        log = LoggerFactory.getLogger(TGraphKernelTPRRBigLockSnappyServer.class);
        // 粗粒度锁，tp可重复读
        bigLock = true;
        apReadLock = false;
        tpReadLock = true;
        // 开启snappy压缩
        Options.setCTP(CompressionType.SNAPPY);
    }

    public static void main(String[] args) {
        setDefaultArgs();
        startServer();
    }
}
