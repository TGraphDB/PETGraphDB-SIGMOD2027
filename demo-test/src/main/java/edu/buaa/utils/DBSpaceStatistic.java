package edu.buaa.utils;

import com.alibaba.fastjson.JSONObject;
import com.google.common.util.concurrent.ListenableFuture;
import edu.buaa.common.RuntimeEnv;
import edu.buaa.common.client.MariaDbExecutorClient;
import edu.buaa.common.client.PostgreSqlExecutorClient;
import edu.buaa.common.transaction.AbstractTransaction;
import edu.buaa.common.utils.TemporalGraphPropertySchema;
import edu.buaa.test.management.TestManager;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class DBSpaceStatistic {
    private static final String dataset = Helper.mustEnv("DATASET");
    private static final String dbName = Helper.mustEnv("DB_NAME").toUpperCase();
    private static final String serverHost = Helper.mustEnv("DB_HOST"); // pg mariadb.
    private static final String milestoneName = Helper.mustEnv("MILESTONE_NAME");
    private static final String serverPath = Helper.mustEnv("DB_PATH"); // tg neo4j

    public static void main(String[] args) throws Exception {
        if(dbName.equalsIgnoreCase("ma")) {
            MariaDb cli = new MariaDb();
            cli.init(serverHost, 3306, milestoneName, 1, TemporalGraphPropertySchema.load(dataset));
            System.out.println(log(cli.currentStorageStatus()));
            cli.close();
        }else if(dbName.equalsIgnoreCase("pg")) {
            PostgreSQL pg = new PostgreSQL();
            pg.init(serverHost, 5432, milestoneName, 1, TemporalGraphPropertySchema.load(dataset));
            System.out.println(log(pg.currentStorageStatus()));
            pg.close();
        }else if(dbName.toUpperCase().startsWith("N14")) {
            System.out.println(log(neo4size(new File(serverPath))));
        }else if(dbName.toUpperCase().startsWith("N1")) {
            System.out.println(log(neo2Size(new File(serverPath))));
        }else if(dbName.toUpperCase().startsWith("TGQL")) {
            System.out.println(log(neo4size(new File(serverPath))));
        }else if(dbName.toUpperCase().startsWith("AEONG")) {
            System.out.println(log(aeon4size(new File(serverPath))));
        }else if(dbName.toUpperCase().startsWith("ROCKSDB")) {
            System.out.println(log(rocksdbSize(new File(serverPath))));
        }else if(dbName.toUpperCase().startsWith("TG4")) {
            System.out.println(log(tg4size(new File(serverPath))));
        }else if(dbName.toUpperCase().startsWith("AION")){
                System.out.println(log(aionsize(new File(serverPath))));
        }else{ //dbName.toUpperCase().startsWith("TG")
            System.out.println(log(tg2size(new File(serverPath))));
        }
    }

    private static String log(String json) throws Exception {
        int ENV_JENKINS_ID = Integer.parseInt(Helper.envOrDefault("JENKINS_ID", "-1"));
        String ENV_DEVICE = Helper.envOrDefault("DEVICE", RuntimeEnv.hostName());
        long beginT = Long.parseLong(Helper.envOrDefault("MARK_TIME", "0"));
        TestManager testM = new TestManager(dbName, Helper.codeGitVersion(), ENV_DEVICE, ENV_JENKINS_ID, ENV_JENKINS_ID);
        testM.addSpaceDetail(json);
        if(beginT>0) testM.updateDuration("Build", beginT);
        testM.close();
        return json;
    }

    private static class MariaDb extends MariaDbExecutorClient{
        @Override
        protected List<String> createTables() {
            return Collections.emptyList();
        }
        @Override
        public ListenableFuture<ServerResponse> execute(AbstractTransaction tx) throws Exception {
            throw new UnsupportedOperationException();
        }
    }

    private static class PostgreSQL extends PostgreSqlExecutorClient{
        @Override
        public ListenableFuture<ServerResponse> execute(AbstractTransaction tx) throws Exception {
            throw new UnsupportedOperationException();
        }
        @Override
        protected List<String> createTables() {
            return Collections.emptyList();
        }
    }

    private static long size(final File file, FilenameFilter filter, int level) {
        if (file.isFile()) return file.length();
        final File[] children = file.listFiles(filter);
        long total = 0;
        if (children != null)
            for (final File child : children){
                StringBuilder sb = new StringBuilder();
                for(int i=0;i<level;i++) sb.append("|--");
                sb.append(child.getName()).append('\t').append(child.isFile()?child.length()+"":"");
                System.out.println(sb);
                total += size(child, filter, level+1);
            }
        return total;
    }

    private static long size(final File file, FilenameFilter filter) {
        System.out.println("------------------"+ file.getAbsolutePath()+"------------------");
        return size(file, filter, 0);
    }

    private static final FilenameFilter noFilter = (d, name) -> true;
    private static final FilenameFilter neoLogFilter = (d, name) -> {
        String[] arr = name.split("\\.");
        if(arr.length > 0 && arr[arr.length-1].matches("\\d+")){
            System.out.println("Log file "+ name + " excluded.");
            return false;
        } else {
            return true;
        }
    };

    private static JSONObject merge(JSONObject o1, JSONObject o2) {
        JSONObject o = new JSONObject();
        for (Map.Entry<String, Object> entry : o1.entrySet()) {
            String key = entry.getKey();
            o.put(key, o1.getLong(key) + o2.getLong(key));
        }
        return o;
    }

    private static String neo4size(File dir) {
        dir = new File(dir, "data");
        dir = new File(dir, "databases");
        JSONObject system = neo4jSizeInner(new File(dir, "system"));
        JSONObject neo4j = neo4jSizeInner(new File(dir, "neo4j"));
        return merge(system, neo4j).toJSONString();
    }
    private static String aionsize(File dir) {
        JSONObject obj = new JSONObject();
        File dir1 = new File("/database", "data");
        dir1 = new File(dir1, "databases");
        File lineageDir = new File("/database", "aion-data/lineage");
        File timeDir = new File("/database", "aion-data/time");
        JSONObject system = neo4jSizeInner(new File(dir1, "system"));
        JSONObject neo4j = neo4jSizeInner(new File(dir1, "neo4j"));
        JSONObject merge = merge(system, neo4j);
        long lineageSize = size(lineageDir, noFilter);
        long timeSize = size(timeDir, noFilter);
        long staticSize=0;
        for(var o: merge.entrySet()){
            staticSize+=(long)o.getValue();
        }
        obj.put("static_data", staticSize);
        obj.put("lineage_data", lineageSize);
        obj.put("time_data", timeSize);
        return obj.toJSONString();
    }
    private static String neo2Size(File dir) {
        return neo4jSizeInner(dir).toJSONString();
    }

    private static JSONObject neo4jSizeInner(File dir){
        JSONObject obj = new JSONObject();
        Function<String, Boolean> isIndex = (name) -> name.equalsIgnoreCase("schema") || name.startsWith("index");
        long dbSize = size(dir, (dir1, name) -> !isIndex.apply(name));
        long indexSize = size(new File(dir,"schema"), noFilter)+size(new File(dir, "index"), noFilter);
        obj.put("data", dbSize);
        obj.put("index", indexSize);
        return obj;
    }
    private static String aeon4size(File dir) {
        return aeonGSizeInner(dir).toJSONString();
    }

    private static String rocksdbSize(File dir) {
        JSONObject obj = new JSONObject();
        long totalSize = size(dir, noFilter);
        obj.put("total_size", totalSize);
        return obj.toJSONString();
    }

    private static JSONObject aeonGSizeInner(File dir) {
        JSONObject obj = new JSONObject();
        File historyDir = new File("/database", "history_deltas");
        File snapshotsDir = new File("/database", "snapshots");
        long historySize = size(historyDir, noFilter);
        long currentSize = size(snapshotsDir, noFilter);
        long totalDataSize = currentSize + historySize;
        long indexSize = 0;
        obj.put("total_data", totalDataSize);
        obj.put("current_data", currentSize);
        obj.put("temporal_data", historySize);
        return obj;
    }

    private static String tg4size(File path) {
        path = new File(path, "data");
        path = new File(path, "databases");
        JSONObject system = tgraphSizeInner(new File(path, "system"));
        JSONObject neo4j = tgraphSizeInner(new File(path, "neo4j"));
        return merge(system, neo4j).toJSONString();
    }

    private static String tg2size(File path) {
        return tgraphSizeInner(path).toJSONString();
    }

    private static JSONObject tgraphSizeInner(File path) {
        long neoData = size(path, (d, name) -> !name.startsWith("index") && !name.equals("schema") && !name.startsWith("temporal"));
        long neoIndex = size(new File(path,"schema"), noFilter)+size(new File(path, "index"), noFilter);
        long tpNodeData = size(new File(path,"temporal.node.properties"), (d, name) -> !name.equals("index"));
        long tpNodeIndex = size(new File(path, "temporal.node.properties/index"), noFilter);
        long tpRelData = size(new File(path,"temporal.relationship.properties"), (d, name) -> !name.equals("index"));
        long tpRelIndex = size(new File(path, "temporal.relationship.properties/index"), noFilter);
        JSONObject obj = new JSONObject();
        obj.put("neo_data", neoData);
        obj.put("neo_index", neoIndex);
        obj.put("tp_node_data", tpNodeData);
        obj.put("tp_node_index", tpNodeIndex);
        obj.put("tp_rel_data", tpRelData);
        obj.put("tp_rel_index", tpRelIndex);
        return obj;
    }
}
