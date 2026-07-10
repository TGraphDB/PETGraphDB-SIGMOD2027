package edu.buaa.server.system;

import edu.buaa.utils.Helper;
import org.neo4j.common.DependencyResolver;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.configuration.connectors.BoltConnector;
import org.neo4j.configuration.helpers.SocketAddress;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.kernel.api.procedure.GlobalProcedures;
import org.neo4j.kernel.internal.GraphDatabaseAPI;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static org.neo4j.configuration.GraphDatabaseSettings.DEFAULT_DATABASE_NAME;

public class TCypherServer {
    private static DatabaseManagementService dbms;
    private static GraphDatabaseService db;

    public static void main(String[] args) {
        dbms = new DatabaseManagementServiceBuilder(dbDir().toPath())
                .setConfig(GraphDatabaseSettings.auth_enabled, false)
                .setConfig(BoltConnector.enabled, true)
                .setConfig(BoltConnector.listen_address, new SocketAddress("0.0.0.0", 7687))
                .build();
        db = dbms.database(DEFAULT_DATABASE_NAME);
        registerProcedures(db);
        System.out.println("server started on port 7687");
        try{
            while (true) {
                Thread.sleep(120_000);
            }
        } catch (InterruptedException e){
            System.out.println("DB Server interrupted, exiting...");
        } finally {
            shutdown();
            System.out.println("DB Server closed. process exit.");
        }
    }

    private static File dbDir(){
        String dbPath = Helper.mustEnv("DB_PATH");
        File dbDir = new File(dbPath);
        if( !dbDir.exists() || !dbDir.isDirectory()){
            throw new IllegalArgumentException("invalid dbDir");
        }
        return dbDir;
    }


    private static void registerProcedures(GraphDatabaseService db) {
        try {
            DependencyResolver resolver = ((GraphDatabaseAPI) db).getDependencyResolver();
            GlobalProcedures procedures = resolver.resolveDependency(GlobalProcedures.class);
            procedures.registerProcedure(TCypherProcedures.class);
//            procedures.registerFunction(TimeStoreProcedures.class);
//            if("true".equalsIgnoreCase(Helper.envOrDefault("APOC_ENABLE", "true"))){
//                registerApocProcedures(procedures);
//            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

//    private static void registerApocProcedures(GlobalProcedures globalProcedures) {
//        List<Class<?>> apocClasses = Arrays.asList(
//                // 1. 核心工具類 (Utils & Help)
//                apoc.help.Help.class,  //提供 apoc.help() 功能。
//                apoc.util.Utils.class,  // 各種通用工具函數。
//                // 2. 圖操作與重構 (Graph & Refactor)
//                apoc.graph.Graphs.class, // 從 Cypher 或數據構建虛擬圖。
//                apoc.nodes.Nodes.class, // 節點操作，如合併、檢查等。
//                apoc.path.PathExplorer.class, // 複雜路徑展開與搜索。
//                // 3. 數據轉換與集合 (Conversion & Collections)
//                apoc.coll.Coll.class, // 集合操作（排序、去重、分割等）。
//                apoc.map.Maps.class, // Map 對象處理（合併、過濾）。
//                apoc.convert.Convert.class, // 類型轉換（JSON 轉 Map、String 轉 Map 等）。
//                apoc.text.Strings.class, // 文本處理與正則表達式。
//                // 4. 數據匯入與匯出 (Import & Export)
//                apoc.export.csv.ExportCSV.class, // 匯出 CSV。
//                apoc.export.json.ExportJson.class, // 匯出 JSON。
//                apoc.load.LoadJson.class, // 從 URL 或檔案加載 JSON。
//                apoc.load.Xml.class, // 加載 XML。
//                // 5. 動態 Cypher 與事務 (Cypher & Transactions)
//                apoc.cypher.Cypher.class, // 執行動態 Cypher 字符串 (apoc.cypher.run)。
//                apoc.periodic.Periodic.class, // 批次處理事務 (apoc.periodic.iterate)。
//                // 6. 數學與數值 (Math & Number)
//                apoc.math.Maths.class, // 進階數學函數。
//                apoc.number.Numbers.class // 數值格式化與轉換。
//        );
//        apocClasses.forEach(cls -> {
//            try {
//                globalProcedures.registerProcedure(cls);
//                globalProcedures.registerFunction(cls);
//            } catch (Exception ignore) {
//                // 部分類可能只有 Procedure 或只有 Function，忽略報錯即可
//            }
//        });
//    }

    public static void shutdown() {
        if (dbms != null) {
            dbms.shutdown();
        }
    }

//    @Override
//    public AbstractTransaction.Result execute(String line) throws TransactionFailedException {
//        PFieldList results = new PFieldList();
//        try(Transaction tx = db.beginTx()){
//            Result r = null;
//            if(line.contains(";")){
//                for(String updateStmt: line.split(";")){
//                    r = tx.execute(updateStmt);
//                }
//            }
//            int debugI = 10;
//            if(!line.contains(" SET ") && r!=null) while(r.hasNext()){
//                Map<String, Object> row = r.next();
//                row.forEach(results::add);
//                if(debugI>0){
//                    System.out.println(row);
//                    debugI--;
//                }
//            }
//            tx.commit();
//        }catch (Throwable e){
//            throw new TransactionFailedException(e);
//        }
//        TCypherExecutorClient.Result rr = new TCypherExecutorClient.Result();
//        rr.setResults(results);
//        return rr;
//    }
}
