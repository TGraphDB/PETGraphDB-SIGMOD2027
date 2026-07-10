package edu.buaa.server.system;

import edu.buaa.common.RuntimeEnv;
import edu.buaa.common.server.DBSocketServer;
import edu.buaa.utils.Helper;
import org.act.temporalProperty.impl.CompressionType;
import org.act.temporalProperty.impl.Options;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 *
 * @author workstation
 */
public class TGBigLockingServer extends TGLockingServer{

    public static void main(String[] args) {

        bigLock = true;

        Options.setGlobalCompressionType(CompressionType.SNAPPY);
        log = LoggerFactory.getLogger(TGBigLockingServer.class);
        DBSocketServer server = new DBSocketServer(dbDir(), new TGBigLockingServer(), Integer.parseInt(Helper.mustEnv("DB_PORT")), true);
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
