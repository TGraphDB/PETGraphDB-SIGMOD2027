package edu.buaa.server.system;

import org.act.temporalProperty.impl.CompressionType;
import org.act.temporalProperty.impl.Options;
import org.slf4j.LoggerFactory;

public class TGraphKernelSnappyServer extends TGraphKernelServer {
    private static void setDefaultArgs() {
        dbKernelProxy = new TGraphKernelSnappyServer();
        log = LoggerFactory.getLogger(TGraphKernelSnappyServer.class);
        // 默认
        bigLock = false;
        apReadLock = false;
        tpReadLock = false;
        // 开启snappy压缩
        Options.setCTP(CompressionType.SNAPPY);
    }
    public static void main(String[] args) {
        setDefaultArgs();
        startServer();
    }
}
