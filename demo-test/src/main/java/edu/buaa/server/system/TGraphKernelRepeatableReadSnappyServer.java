package edu.buaa.server.system;

import org.act.temporalProperty.impl.CompressionType;
import org.act.temporalProperty.impl.Options;
import org.slf4j.LoggerFactory;

public class TGraphKernelRepeatableReadSnappyServer extends TGraphKernelServer {
    private static void setDefaultArgs() {
        dbKernelProxy = new TGraphKernelRepeatableReadSnappyServer();
        log = LoggerFactory.getLogger(TGraphKernelRepeatableReadSnappyServer.class);
        // 可重复读，细粒度锁
        bigLock = false;
        apReadLock = true;
        tpReadLock = true;
        // 开启snappy压缩
        Options.setGlobalCompressionType(CompressionType.SNAPPY);
    }

    public static void main(String[] args) {
        setDefaultArgs();
        startServer();
    }
}
