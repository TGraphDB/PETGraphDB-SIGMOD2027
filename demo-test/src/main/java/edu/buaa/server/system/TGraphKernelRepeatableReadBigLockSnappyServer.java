package edu.buaa.server.system;

import org.act.temporalProperty.impl.CompressionType;
import org.act.temporalProperty.impl.Options;
import org.slf4j.LoggerFactory;

public class TGraphKernelRepeatableReadBigLockSnappyServer extends TGraphKernelServer {
    private static void setDefaultArgs() {
        dbKernelProxy = new TGraphKernelRepeatableReadBigLockSnappyServer();
        log = LoggerFactory.getLogger(TGraphKernelRepeatableReadBigLockSnappyServer.class);
        // 可重复读，粗粒度锁
        bigLock = true;
        apReadLock = true;
        tpReadLock = true;
        // 开启snappy压缩
        Options.setCTP(CompressionType.SNAPPY);
    }

    public static void main(String[] args) {
        setDefaultArgs();
        startServer();
    }
}
