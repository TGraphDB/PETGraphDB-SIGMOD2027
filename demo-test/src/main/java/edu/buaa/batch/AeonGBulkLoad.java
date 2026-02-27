package edu.buaa.batch;

import com.alibaba.fastjson.JSON;
import com.google.common.collect.PeekingIterator;
import edu.buaa.common.benchmark.MilestoneBuilder;
import edu.buaa.common.client.DBClientProxy;
import edu.buaa.common.transaction.ImportStaticDataTx;
import edu.buaa.common.transaction.ImportTemporalDataTx;
import edu.buaa.common.utils.PFieldList;
import edu.buaa.common.utils.PVal;
import edu.buaa.utils.Helper;
import org.neo4j.driver.*;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Relationship;
import org.neo4j.graphdb.temporal.TimePoint;

import java.io.*;
import java.util.*;

public class AeonGBulkLoad  extends MilestoneBuilder implements AutoCloseable{
    protected final String serverHost = Helper.mustEnv("DB_HOST"); // hostname of server.
    protected final int serverPort = Integer.parseInt(Helper.mustEnv("DB_PORT")); // hostname of server.
    protected final String dbName = Helper.mustEnv("MILESTONE_NAME");
    private Driver driver;
    private String dataset;
//    private String PATH_STATIC_V;
//    private String PATH_TEMPORAL_V;
//    private String PATH_STATIC_E;
//    private String PATH_TEMPORAL_E;
//    private List<String[]> VERTEX_SCHEMA;
//    private List<String[]> EDGE_SCHEMA;
    private String EdgeLabel;
    private String NodeLabel;
    private String EdgeNodeLabel="EdgeNode";
    private Session session;
    private final int BATCH_SIZE = 100;
    private final int STATIC_BATCH_SIZE = 8000;

    private Map<String, String> edgeIdMap;
//    private String NODE_MAP_FILE_PATH;
//    private String EDGE_MAP_FILE_PATH;

    private int transaction_t_min=Integer.MAX_VALUE;
    private int transaction_t_max=Integer.MIN_VALUE;
    private int valid_t_min=Integer.MAX_VALUE;
    private int valid_t_max=Integer.MIN_VALUE;

    public AeonGBulkLoad() throws Exception {
        String connect="bolt://"+serverHost+":"+serverPort;
//        String connect="bolt://localhost:7687";
        System.out.println(connect);
        driver = GraphDatabase.driver(connect, AuthTokens.basic("", ""));
        dbDir();
        dataset = Helper.mustEnv("DATASET");
//        dataset="energy";
//        NODE_MAP_FILE_PATH="/database/nodeid_tx_mapping.csv";
//        EDGE_MAP_FILE_PATH="/database/edgeid_tx_mapping.csv";
//        System.out.println(NODE_MAP_FILE_PATH);
        edgeIdMap = new HashMap<>();
        this.dataGen.setSectionEnable(false);
    }
    static File dbDir() {
        String path = Helper.mustEnv("DB_PATH");
//        String path = "D:\\Graph_DMS_buaa\\AeonG实验\\AeonGClient\\AeonG_test\\database\\AeonG_energy_all";
        File dbDir = new File(path);
        if (!dbDir.exists()) {
            if (dbDir.mkdirs()) return dbDir;
            else throw new IllegalArgumentException("invalid dbDir");
        } else if (!dbDir.isDirectory()) {
            throw new IllegalArgumentException("invalid dbDir");
        }
        return dbDir;
    }
//    private void CheckInputFile()
//    {
//        File file=new File(NODE_MAP_FILE_PATH);
//        if(!file.getParentFile().exists())
//        {
//            System.out.println("Dir Not Found");
//            File parentDir = file.getParentFile();
//            parentDir.mkdirs();
//            System.out.println(parentDir.getAbsolutePath());
//        }
//    }

    private void connect() throws Exception {
        session = driver.session(SessionConfig.forDatabase("memgraph"));
        var result = session.run("SHOW STORAGE INFO;");
        while (result.hasNext()) {
            var record = result.next();
            System.out.println(record);
        }
    //        CheckInputFile();
        getDatasetSchemeAndLabel(dataset);
    }

    @Override
    public void close() throws RuntimeException {
        String cypher ="MATCH (n:TEST_META) RETURN n;";
        var res=session.run(cypher);
        while (res.hasNext())
        {
            var record=res.next();
            System.out.println(record.asMap());
        }
        session.close();
        driver.close();
    }


    private void createIndex() {
        var sql = "CREATE INDEX ON :" + NodeLabel + "(u_sid);";
        var sql2 = "CREATE INDEX ON :" + EdgeNodeLabel + "(u_sid);";
        System.out.println(sql);
        System.out.println(sql2);
        try {
            session.run(sql).consume();
            session.run(sql2).consume();
            var result=session.run("SHOW INDEX INFO;");
            while(result.hasNext())
            {
                Record record= result.next();
                System.out.println(record.asMap());
            }
            System.out.println("Index created.");
        } catch (Exception e) {
            System.out.println("Index creation warning: " + e.getMessage());
        }
    }

    @Override
    public void importStatic() throws Exception
    {
        connect();
        createIndex();
        Iterator<ImportStaticDataTx> it = dataGen.readNetwork(STATIC_BATCH_SIZE);
        String newNode = String.format("UNWIND $batch AS row CREATE (n:%s) SET n = row", NodeLabel); // RETURN id(n) AS db_id, row.u_sid AS u_sid
        String newEdge = String.format(
                "UNWIND $batch AS row " +
                        "MATCH (a:%s {u_sid: row.r_from}), (b:%s {u_sid: row.r_to}) " +
                        "CREATE (a)-[r:%s]->(b) SET r = row RETURN row.r_from AS from, row.u_sid AS u_sid", // id(r) AS db_id
                NodeLabel, NodeLabel, EdgeLabel);
        String newEdgeNode = String.format(
                "UNWIND $batch AS row CREATE (n:%s) SET n = row", EdgeNodeLabel);
        List<Map<String, Object>> batchNode = new ArrayList<>();
        List<Map<String, Object>> batchEdge = new ArrayList<>();

        while(it.hasNext()) {
            ImportStaticDataTx tx = it.next();

            PFieldList nodesData = tx.getNodes();
            int nSize = nodesData.size();
            for (int i = 0; i < nSize; i++) {
                Map<String, Object> typedRow = props(nodesData, i);
                batchNode.add(typedRow);
            }
            runBatch(newNode, batchNode, true);

            PFieldList relData = tx.getRels();
            int rSize = relData.size();
            for (int i = 0; i < rSize; i++) {
                Map<String, Object> typedRow = props(relData, i);
                batchEdge.add(typedRow);
            }
            runBatch(newEdgeNode, batchEdge, true);
//            var result = session.run(newEdge, Collections.singletonMap("batch", batchEdge));
            session.run(newEdge, Collections.singletonMap("batch", batchEdge)).consume();
//            while (result.hasNext()) {
//                var record = result.next();
//                String fromStr = record.get("from").asString();
//                String u_sid = record.get("u_sid").asString();
//                edgeIdMap.put(u_sid, fromStr);
//            }

            batchNode = new ArrayList<>();
            batchEdge = new ArrayList<>();
        }
    }

    private Map<String, Object> props(PFieldList data, int i) {
        Map<String, Object> p = new HashMap<>();
        for (String key : data.keys()) {
            p.put(key, data.get(key, i).getVal());
        }
        return p;
    }

//    private  void importStaticNode() throws Exception
//    {
//        if (PATH_STATIC_V != null)
//        {
//            List<Map<String, Object>> batch = new ArrayList<>(BATCH_SIZE);
//            String cypher = String.format("UNWIND $batch AS row CREATE (n:%s) SET n += row RETURN n", NodeLabel);
//            BufferedReader reader = new BufferedReader(new FileReader(PATH_STATIC_V));
//            String[] headers = parseHeader(reader.readLine());
//            String line;
//            while ((line = reader.readLine()) != null)
//            {
//                Map<String, Object> temprow = parseLineToMap(line, headers);
//                Map<String, Object> row = parseTypedRow(temprow, VERTEX_SCHEMA);
//                batch.add(row);
//                if (batch.size() >= BATCH_SIZE) {
//                    runBatch(cypher, batch,true);
//                    batch.clear();
//                }
//            }
//            if (!batch.isEmpty()) {
//                runBatch(cypher, batch,true);
//            }
//            System.out.println("Nodes imported successfully.");
//        }
//
//    }
//    private  void importStaticEdge() throws Exception
//    {
//        if (PATH_STATIC_E != null)
//        {
//            List<Map<String, Object>> batch = new ArrayList<>(BATCH_SIZE);
//            String cypher = String.format(
//                    "UNWIND $batch AS row " +
//                            "MATCH (a:%s {id: row.r_from}), (b:%s {id: row.r_to}) " +
//                            "CREATE (a)-[r:%s]->(b) " +
//                            "SET r += row RETURN r",
//                    NodeLabel, NodeLabel, EdgeLabel);
//            BufferedReader reader = new BufferedReader(new FileReader(PATH_STATIC_E));
//            String[] headers = parseHeader(reader.readLine());
//            String line;
//            while ((line = reader.readLine()) != null)
//            {
//                Map<String, Object> temprow = parseLineToMap(line, headers);
//                Map<String, Object> row = parseTypedRow(temprow, EDGE_SCHEMA);
//
//                if (temprow.containsKey("r_from")) {
//                    try {
//                        String val = temprow.get("r_from").toString();
//                        row.put("r_from", Integer.parseInt(val));
//                        edgeIndex.put((Integer)row.get("id"), Integer.parseInt(val));
//                    } catch (Exception e) {
//                        e.printStackTrace();
//                    }
//                }
//                if (temprow.containsKey("r_to")) {
//                    try {
//                        String val = temprow.get("r_to").toString();
//                        row.put("r_to", Integer.parseInt(val));
//                    } catch (Exception e) {
//                        e.printStackTrace();
//                        continue;
//                    }
//                }
//                batch.add(row);
//                if (batch.size() >= BATCH_SIZE) {
//                    runBatch(cypher, batch,false);
//                    batch.clear();
//                }
//            }
//            if (!batch.isEmpty()) {
//                runBatch(cypher, batch,false);
//            }
//            System.out.println("Edges imported successfully.");
//        }
//    }


    @Override
    public void importTemporal() throws Exception
    {
        importNodeTemporalProperty();
        importEdgeTemporalProperty();
        System.out.printf("vt: [%s ~ %s], tt: [%s ~ %s]%n", valid_t_min, valid_t_max, transaction_t_min, transaction_t_max);
        Map<String, Object> params = new HashMap<>();
        params.put("vtmin", valid_t_min);
        params.put("vtmax", valid_t_max);
        params.put("ttmin", transaction_t_min);
        params.put("ttmax", transaction_t_max);
        String cypher = "CREATE (n:TEST_META {uuid: 0, vtmin: $vtmin, vtmax: $vtmax, ttmin: $ttmin, ttmax: $ttmax}) return n";
        session.run(cypher, params).consume();
        System.out.println("TEST_META node created successfully.");
    }

    private void importNodeTemporalProperty() throws IOException {
        PeekingIterator<ImportTemporalDataTx> it = dataGen.readNodeTemporal(startTime, endTime, BATCH_SIZE);
        int et = dataGen.parseTime(endTime);
        int st = dataGen.parseTime(startTime);
        while(it.hasNext()){
            ImportTemporalDataTx tx = it.next();
            PFieldList data = tx.getData();
            Set<String> props = data.keysWithout("u_sid", "st", "et", "t");
            List<Map<String,Object>> batch = new ArrayList<>();
            int tSize = data.size();
            for (int i=0; i<tSize; i++) {
                String u_sid = data.get("u_sid", i).s();
                int vtStart = data.get("t", i).i();
                Map<String, Object> propMap = props(data, i);
                for (String prop : props) {
                    Object val = data.get(prop, i).getVal();
                    propMap.put(prop, val);
                }
                Map<String, Object> row = new HashMap<>();
                row.put("props",propMap);
                row.put("u_sid",u_sid);
                batch.add(row);
                valid_t_min=Math.min( vtStart, valid_t_min );
                valid_t_max=Math.max( vtStart, valid_t_max );
            }
            String updateCypher = String.format(
                    "UNWIND $batch AS row " +
                            "MATCH (n:%s {u_sid: row.u_sid}) " +
                            "SET n += row.props RETURN n",
                    NodeLabel
            );
            runTemporalBatch(updateCypher, batch,true);
            // commit batch
            if(ticker.shouldTick(0)) {
                int curT = tx.getData().get("t", 0).i();
                System.out.printf("load tp: %s %% %n", (curT - st) * 100f / (et - st));
            }
        }
    }

    private void importEdgeTemporalProperty() throws IOException {
        PeekingIterator<ImportTemporalDataTx> it = dataGen.readRelTemporal(startTime, endTime, BATCH_SIZE);
        int et = dataGen.parseTime(endTime);
        int st = dataGen.parseTime(startTime);
        while(it.hasNext()){
            ImportTemporalDataTx tx = it.next();
            PFieldList data = tx.getData();
            Set<String> props = data.keysWithout("u_sid", "st", "et", "t");
            List<Map<String,Object>>batch=new ArrayList<>();

            int tSize = data.size();
            for (int i=0; i<tSize; i++) {
                String u_sid = data.get("u_sid", i).s();
                int vtStart = data.get("t", i).i();
                Map<String, Object> propMap = new HashMap<>();
                for (String prop : props) {
                    Object val = data.get(prop, i).getVal();
                    propMap.put(prop, val);
                }
                Map<String, Object> row = new HashMap<>();
                row.put("props",propMap);
                row.put("u_sid",u_sid);
//                row.put("r_from", edgeIdMap.get(u_sid));
                batch.add(row);
                valid_t_min=Math.min( vtStart, valid_t_min );
                valid_t_max=Math.max( vtStart, valid_t_max );
            }
            String updateCypher = String.format(
                    "UNWIND $batch AS row " +
                            "MATCH (n:%s {u_sid: row.u_sid}) " +
                            "SET n += row.props RETURN n",
                    EdgeNodeLabel
            );
            runTemporalBatch(updateCypher, batch,true);
            // commit batch
            if(ticker.shouldTick(0)) {
                int curT = tx.getData().get("t", 0).i();
                log.debug("load tp: {}%", (curT - st) * 100f / (et - st));
            }
        }
    }

    private void runBatch(String cypher, List<Map<String, Object>> batchData, Boolean isNode) {
        try {
            var res= session.run(cypher, Collections.singletonMap("batch", batchData));
            res.consume();
//            System.out.println("Flushed batch of " + batchData.size() + " records.");
        } catch(Exception e)
        {
            e.printStackTrace();
            System.err.println("Failed to import batch.");
        }
    }

//
//    private void importTemporalNode() throws Exception {
//        if (PATH_TEMPORAL_V == null||PATH_TEMPORAL_V.isEmpty()) return;
//        System.out.println("importing temporal node.....");
//        String updateCypher = String.format(
//                "UNWIND $batch AS row " +
//                        "MATCH (n:%s {id: row.id}) " +
//                        "SET n += row.props",
//                NodeLabel
//        );
//
//        List<Map<String, Object>> batch = new ArrayList<>(BATCH_SIZE);
//
//        try (BufferedReader reader = new BufferedReader(new FileReader(PATH_TEMPORAL_V))) {
//            String headerLine = reader.readLine();
//            String[] headers = parseHeader(headerLine);
//            int idIdx = -1, vtStartIdx = -1, vtEndIdx = -1;
//            List<Integer> propIndices = new ArrayList<>();
//
//            for (int i = 0; i < headers.length; i++) {
//                String col = headers[i].toLowerCase();
//                if (col.equals("entity")) {
//                    idIdx = i;
//                } else if (col.equals("st")) {
//                    vtStartIdx = i;
//                } else if (col.equals("et")) {
//                    vtEndIdx = i;
//                } else {
//                    propIndices.add(i);
//                }
//            }
//
//            if (idIdx == -1) throw new RuntimeException("CSV missing 'id' column");
//
//            String line;
//            while ((line = reader.readLine()) != null) {
//                String[] parts = line.split(",");
//                if (parts.length <= idIdx) continue;
//
//                Map<String, Object> row = new HashMap<>();
//                int id = Integer.parseInt(parts[idIdx].trim());
//                row.put("id", id);
//                int vtStart = Integer.parseInt(parts[vtStartIdx].trim());
//                int vtEnd =Integer.parseInt(parts[vtEndIdx].trim());
//                row.put("vt_start", vtStart);
//                row.put("vt_end", vtEnd);
//                Map<String, Object> props = new HashMap<>();
//                for (int idx : propIndices)
//                {
//                    String valStr = parts[idx].trim();
//                    String key = headers[idx];
//                    props.put(key, valStr);
//                }
//                props=parseTypedRow(props,VERTEX_SCHEMA);
//                row.put("props", props);
//                batch.add(row);
//
//                if (batch.size() >= BATCH_SIZE) {
//                    runTemporalBatch(updateCypher, batch,true);
//                    batch.clear();
//                }
//            }
//            if (!batch.isEmpty()) {
//                runTemporalBatch(updateCypher, batch,true);
//            }
//            System.out.println("Temporal node imported successfully.");
//        }
//    }
//    private void importTemporalEdge() throws Exception {
//        if (PATH_TEMPORAL_E == null||PATH_TEMPORAL_E.isEmpty()) return;
//        System.out.println("importing temporal edge.....");
//        String updateCypher = String.format(
//                "UNWIND $batch AS row " +
//                        "MATCH (a:%s{id: row.r_from})-[r:%s{id: row.id}]->(b:%s) " +
//                        "SET r += row.props",
//                NodeLabel, EdgeLabel, NodeLabel
//        );
//        List<Map<String, Object>> batch = new ArrayList<>(BATCH_SIZE);
//
//        try (BufferedReader reader = new BufferedReader(new FileReader(PATH_TEMPORAL_E))) {
//            String headerLine = reader.readLine();
//            String[] headers = parseHeader(headerLine);
//            int idIdx = -1, vtStartIdx = -1, vtEndIdx = -1;
//            List<Integer> propIndices = new ArrayList<>();
//
//            for (int i = 0; i < headers.length; i++) {
//                String col = headers[i].toLowerCase();
//                if (col.equals("entity")) {
//                    idIdx = i;
//                } else if (col.equals("st")) {
//                    vtStartIdx = i;
//                } else if (col.equals("et")) {
//                    vtEndIdx = i;
//                }else {
//                    propIndices.add(i);
//                }
//            }
//
//            if (idIdx == -1) throw new RuntimeException("CSV missing 'id' column");
//
//            String line;
//            while ((line = reader.readLine()) != null) {
//                String[] parts = line.split(",");
//                if (parts.length <= idIdx) continue;
//
//                Map<String, Object> row = new HashMap<>();
//                int id = Integer.parseInt(parts[idIdx].trim());
//                row.put("id", id);
//                row.put("r_from", edgeIndex.get(id));
//                int vtStart = Integer.parseInt(parts[vtStartIdx].trim());
//                int vtEnd =Integer.parseInt(parts[vtEndIdx].trim());
//                row.put("vt_start", vtStart);
//                row.put("vt_end", vtEnd);
//                Map<String, Object> props = new HashMap<>();
//                for (int idx : propIndices)
//                {
//                    String valStr = parts[idx].trim();
//                    String key = headers[idx];
//                    props.put(key, valStr);
//                }
//                props=parseTypedRow(props,EDGE_SCHEMA);
//                row.put("props", props);
//                batch.add(row);
//
//                if (batch.size() >= BATCH_SIZE) {
//                    runTemporalBatch(updateCypher, batch,false);
//                    batch.clear();
//                }
//            }
//            if (!batch.isEmpty()) {
//                runTemporalBatch(updateCypher, batch,false);
//            }
//            System.out.println("Temporal node imported successfully.");
//        }
//    }

    private void runTemporalBatch(String updateCypher, List<Map<String, Object>> batchData, boolean isNode)
    {
        try {
//            System.out.println(updateCypher);
//            System.out.println(JSON.toJSONString(batchData));
            var result = session.run(updateCypher, Collections.singletonMap("batch", batchData));
            if(isNode)
            {
                while (result.hasNext()) {
                    var record = result.next();
                    Node node = record.get("n").asNode();
                    int tt = (int) node.get("transaction_ts").asLong();
                    transaction_t_min=Math.min(tt,transaction_t_min);
                    transaction_t_max=Math.max(tt,transaction_t_max);
                }
            }
            else
            {
                while (result.hasNext()) {
                    var record = result.next();
                    Relationship rel = record.get("r").asRelationship();
                    int tt = (int) rel.get("transaction_ts").asLong();
                    transaction_t_min=Math.min(tt,transaction_t_min);
                    transaction_t_max=Math.max(tt,transaction_t_max);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

//    private String[] parseHeader(String headerLine) {
//        if (headerLine == null) return new String[0];
//        String[] raw = headerLine.split(",");
//        String[] parsed = new String[raw.length];
//        for (int i = 0; i < raw.length; i++) {
//            parsed[i] = headerTransfer(raw[i].trim());
//        }
//        return parsed;
//    }
//    private static String headerTransfer(String header) {
//        if (header.toLowerCase().equals("id")) return "id";
//        if (header.equals("fromNode")) return "r_from";
//        if (header.equals("toNode")) return "r_to";
//        return header;
//    }
//    private Map<String, Object> parseLineToMap(String line, String[] headers) {
//        String[] values = line.split(",");
//        Map<String, Object> row = new HashMap<>();
//        for (int i = 0; i < headers.length && i < values.length; i++) {
//            row.put(headers[i], values[i].trim());
//        }
//        return row;
//    }
//    private Map<String, Object> parseTypedRow(Map<String, Object> rawRow, List<String[]> schema) {
//        Map<String, Object> typedRow = new HashMap<>();
//        for (String[] field : schema) {
//            String key = field[0];
//            String type = field[1];
//            Object val = rawRow.get(key);
//            if (val != null && !val.toString().isEmpty()) {
//                try {
//                    switch (type) {
//                        case "int":
//                            typedRow.put(key, Integer.parseInt(val.toString()));
//                            break;
//                        case "float":
//                            typedRow.put(key, Float.parseFloat(val.toString()));
//                            break;
//                        default:
//                            typedRow.put(key, val.toString());
//                            break;
//                    }
//                } catch (NumberFormatException e) {
//                    System.err.println("Type conversion error for key: " + key + ", value: " + val);
//                }
//            }
//        }
//        return typedRow;
//    }
    private void getDatasetSchemeAndLabel(String dataset) throws Exception
    {
//        PATH_STATIC_V=dataGen.prepareStaticCSV(true).getAbsolutePath();
//        PATH_TEMPORAL_V=dataGen.prepareTPCSV(dataSize, startTime, endTime, true, true, false).getAbsolutePath();
//        PATH_STATIC_E=dataGen.prepareStaticCSV(false).getAbsolutePath();
//        PATH_TEMPORAL_E=dataGen.prepareTPCSV(dataSize, startTime, endTime, false, true, false).getAbsolutePath();
//        VERTEX_SCHEMA = new ArrayList<>();
//        System.out.println(PATH_STATIC_V);
//        System.out.println(PATH_TEMPORAL_V);
//        System.out.println(PATH_STATIC_E);
//        System.out.println(PATH_TEMPORAL_E);
//        System.out.println("VERTICES_SCHEMA: ");
//        for (Map.Entry<String, PVal.Type> entry : schema.nodeStatic.entrySet()) {
//            VERTEX_SCHEMA.add(new String[]{entry.getKey(), entry.getValue().toString().toLowerCase()});
//        }
//        for (Map.Entry<String, PVal.Type> entry : schema.nodeTemporal.entrySet()) {
//            VERTEX_SCHEMA.add(new String[]{entry.getKey(), entry.getValue().toString().toLowerCase()});
//        }
//        VERTEX_SCHEMA.add(new String[]{"id", "int"});
//        for(String[] s:VERTEX_SCHEMA)
//        {
//            System.out.println(s[0]+": "+s[1]+" ");
//        }
//        EDGE_SCHEMA = new ArrayList<>();
//        for (Map.Entry<String, PVal.Type> entry : schema.relStatic.entrySet()) {
//            EDGE_SCHEMA.add(new String[]{entry.getKey(), entry.getValue().toString().toLowerCase()});
//        }
//        for (Map.Entry<String, PVal.Type> entry : schema.relTemporal.entrySet()) {
//            EDGE_SCHEMA.add(new String[]{entry.getKey(), entry.getValue().toString().toLowerCase()});
//        }
//        System.out.println("EDGE_SCHEMA: ");
//        for(String[] s:EDGE_SCHEMA)
//        {
//            System.out.println(s[0]+": "+s[1]+" ");
//        }
        switch(dataset){
            case "energy":
            {
                EdgeLabel="TransmissionLine";
                NodeLabel="Station";
                break;
            }
            case "traffic":
            {
                EdgeLabel="Road";
                NodeLabel="RoadNode";
                break;
            }
            case "syn":
            {
                EdgeLabel="Edge";
                NodeLabel="Node";
                break;
            }
            default:
                throw new IllegalArgumentException("Unknown dataset: " + dataset);
        }
    }
}
