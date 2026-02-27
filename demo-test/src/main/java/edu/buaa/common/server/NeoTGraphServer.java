package edu.buaa.common.server;

import com.google.common.base.Preconditions;
import edu.buaa.common.RuntimeEnv;
import edu.buaa.common.server.DBSocketServer.DBKernelProxy;
import edu.buaa.utils.Helper;

import java.io.File;

public class NeoTGraphServer {
    public static void main(String[] args){
        RuntimeEnv env = RuntimeEnv.getCurrentEnv();
        String serverCodeVersion = env.name() + "." + Helper.codeGitVersion();
        System.out.println("server code version: "+ serverCodeVersion);
        try {
            DBSocketServer server = initServer(
                    Helper.mustEnv("CLASS_SERVER"),
                    Integer.parseInt(Helper.mustEnv("DB_PORT")));
            server.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static DBSocketServer initServer(String clzServer, int port) throws Exception {
        Class<?> cls = Class.forName(clzServer);
        Object obj = cls.newInstance();
        DBKernelProxy server = (DBKernelProxy) obj;
        return new DBSocketServer( dbDir(), server, port );
    }

    private static File dbDir(){
        String path = Helper.mustEnv("DB_PATH");
        Preconditions.checkNotNull(path, "need arg: DB_PATH");
        File dbDir = new File(path);
        if( !dbDir.exists()){
            if(dbDir.mkdirs()) return dbDir;
            else throw new IllegalArgumentException("invalid dbDir");
        }else if( !dbDir.isDirectory()){
            throw new IllegalArgumentException("invalid dbDir");
        }
        return dbDir;
    }
}
