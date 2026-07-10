package edu.buaa.server.system;

import org.act.temporalProperty.impl.CompressionType;
import org.act.temporalProperty.impl.Options;
import org.neo4j.kernel.impl.api.InternalTransactionCommitProcess;
import org.slf4j.LoggerFactory;

public class TGBNoLogServer extends TGraphKernelSnappyServer{

    public static void main(String[] args) {

        Options.setGlobalCompressionType(CompressionType.SNAPPY);
        InternalTransactionCommitProcess.Debug.mockLog = true;

        dbKernelProxy = new TGraphKernelServer();
        log = LoggerFactory.getLogger(TGraphKernelServer.class);
        // 默认
        bigLock = true;
        apReadLock = false;
        tpReadLock = false;

        startServer();
    }

}
