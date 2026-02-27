package edu.buaa.server;

import edu.buaa.client.PostgreSQLTimePointClient;
import edu.buaa.common.RuntimeEnv;
import edu.buaa.common.client.AbstractSQLClient;
import edu.buaa.common.server.SQLSocketServer;
import edu.buaa.common.utils.TemporalGraphPropertySchema;
import edu.buaa.utils.Helper;

import java.io.IOException;

/**
 * Created by sjh. 2021.11.18
 */
public class PostgreSQLServer {

    private static final String dbName = Helper.mustEnv("DB_NAME");
    private static final int parallelCnt = Integer.parseInt(Helper.mustEnv("MAX_CONNECTION_CNT"));
    private static final String dataset = Helper.mustEnv("DATASET");

    public static void main(String[] args) throws Exception {
        AbstractSQLClient client = new PostgreSQLTimePointClient();
        client.init("localhost", 5432, dbName, parallelCnt, TemporalGraphPropertySchema.load(dataset));
        SQLSocketServer server = new SQLSocketServer(client, 9876);
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

