package edu.buaa.batch;

import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Longs;
import edu.buaa.common.benchmark.NeoMilestoneBuilder;
import edu.buaa.common.utils.PVal;
import edu.buaa.server.system.Neo4jServer1;
import edu.buaa.utils.KVMem;
import org.neo4j.batchinsert.BatchInserter;
import org.neo4j.batchinsert.BatchInserters;
import org.neo4j.configuration.Config;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.schema.Schema;
import org.neo4j.io.layout.DatabaseLayout;
import org.neo4j.io.layout.Neo4jLayout;

import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class N1BulkLoad extends NeoMilestoneBuilder {
    protected BatchInserter bulkDB;
    protected File dbDir;
    private File rtiTmp;
    private File ntiTmp;
    protected KVMem.TiCache tiMap;

    public N1BulkLoad() throws Exception { }

    public void init(File graphDb) throws IOException{
        System.out.println("DB dir: "+graphDb);
        this.dbDir = graphDb;
        File parent = dbDir.getParentFile();
        this.rtiTmp = new File(parent, dbDir.getName()+".rti.csv");
        this.ntiTmp = new File(parent, dbDir.getName()+".nti.csv");
        Map<String, String> config = ImmutableMap.of("dbms.pagecache.memory", "40g" );
        Config.Builder configBuilder = Config.newBuilder();
        configBuilder.set(GraphDatabaseSettings.neo4j_home, dbDir.toPath().toAbsolutePath());
        configBuilder.setRaw(config);
        Neo4jLayout layout = Neo4jLayout.of(configBuilder.build());
        DatabaseLayout databaseLayout = layout.databaseLayout("neo4j");
        bulkDB = BatchInserters.inserter(databaseLayout);
        this.tiMap = new KVMem.TiCache(10_000_000);
    }

    @Override
    public void close() throws Exception {
        System.out.println("importing relationships...");
        importRelationship();
        System.out.println(ntiTmp+" length: "+(ntiTmp.length()/1024f/1024f/1024f) +" GB");
        System.out.println(rtiTmp+" length: "+(rtiTmp.length()/1024f/1024f/1024f) +" GB");
//        Files.deleteIfExists(ntiTmp.toPath());
//        Files.deleteIfExists(rtiTmp.toPath());
//make the changes visible for reading, use this sparsely, requires IO!
        bulkDB.shutdown();
        createIndexAndWait();
    }

    private final Map<String, Object> NO_PROP = Collections.emptyMap();
    protected Map<Long, Long> relMap = new HashMap<>();

    public void importStatic() throws Exception {
        Label vertexLabel = Neo4jServer1.label(Neo4jServer1.NodeType.VERTEX);
        System.out.print("import static data...");
        File node = dataGen.prepareStaticCSV(true);

        try(BufferedReader reader = new BufferedReader(new FileReader(node))){
            PropHandler vp = new PropHandler(schema.nodeStatic, reader.readLine().split(","));
            String line;
            while((line= reader.readLine())!=null){
                LineObj obj = vp.processNode(line);
                bulkDB.createNode(obj.id, obj.prop, vertexLabel);
            }
        }

        Label edgeLabel = Neo4jServer1.label(Neo4jServer1.NodeType.EDGE);
        File edge = dataGen.prepareStaticCSV(false);
        try(BufferedReader reader = new BufferedReader(new FileReader(edge))){
            PropHandler vp = new PropHandler(schema.relStatic, reader.readLine().split(","));
            String line;
            while((line= reader.readLine())!=null){
                LineObj obj = vp.processEdge4Node(line);
                long id = bulkDB.createNode(obj.prop, edgeLabel);
                relMap.put(obj.id, id);
            }
            System.out.println("static import done.");
        }
    }

    // import time node only
    public void importTemporal() throws Exception {
        long progress = 0;
        Label timeLabel = Neo4jServer1.label(Neo4jServer1.NodeType.TIME_INTERVAL);
        File ntp = dataGen.prepareTPCSV(dataSize, startTime, endTime, true, true, false);
        long totalSize = ntp.length();
        try(BufferedReader reader = new BufferedReader(new FileReader(ntp));
            BufferedWriter w = new BufferedWriter(new FileWriter(ntiTmp))) {
            w.write("time_node_id");
            w.write('\n');
            PropHandler vp = new PropHandler(schema.nodeTemporal, reader.readLine().split(","));
            String line;
            while ((line = reader.readLine()) != null) {
                progress += line.length() + 1;
                LineObj obj = vp.processTp4Time(line);
                byte[] tiKey = timeNodeKey(obj.id, obj.st, obj.et);
                Long tid = tiMap.get(tiKey);
                if ( tid == null) {
                    tid = bulkDB.createNode(obj.prop, timeLabel);
                    tiMap.put(tiKey, tid);
                }
                w.write(Long.toString(tid));
                w.write('\n');
                if (ticker.shouldTick(0)) {
                    log.debug("import time node of ntp: {}%", progress * 100d / totalSize);
                }
            }
        }
        tiMap.clear();
        File rtp = dataGen.prepareTPCSV(dataSize, startTime, endTime, false, true, false);
        totalSize = rtp.length();
        progress = 0;
        try(BufferedReader reader = new BufferedReader(new FileReader(rtp));
            BufferedWriter w = new BufferedWriter(new FileWriter(rtiTmp))){
            w.write("time_node_id");
            w.write('\n');
            PropHandler vp = new PropHandler(schema.relTemporal, reader.readLine().split(","));
            String line;
            while ((line = reader.readLine()) != null) {
                progress += line.length() + 1;
                LineObj obj = vp.processTp4Time(line);
                long rId = relMap.get(obj.id);
                byte[] tiKey = timeNodeKey(rId, obj.st, obj.et);
                Long tid = tiMap.get(tiKey);
                if ( tid == null) {
                    tid = bulkDB.createNode(obj.prop, timeLabel);
                    tiMap.put(tiKey, tid);
                }
                w.write(Long.toString(tid));
                w.write('\n');
                if (ticker.shouldTick(0)) {
                    log.debug("import time node of rtp: {}%", progress * 100d / totalSize);
                }
            }
        }
        tiMap = null;
    }

    protected byte[] timeNodeKey(long eid, int st, int et) {
        return Longs.toByteArray((((long)st)<<32)+et);
    }

    private void importRelationship() throws Exception {
        long totalRelCnt = 0;
        File edge = dataGen.prepareStaticCSV(false);
        try(BufferedReader reader = new BufferedReader(new FileReader(edge))){
            PropHandler vp = new PropHandler(schema.relStatic, reader.readLine().split(","));
            String line;
            while((line= reader.readLine())!=null){
                LineObj obj = vp.processEdge4Rel(line);
                long eid = relMap.get(obj.id);
                bulkDB.createRelationship(obj.from, eid, Neo4jServer1.Edge.TOPO_N_E, NO_PROP);
                bulkDB.createRelationship(eid, obj.to, Neo4jServer1.Edge.TOPO_E_N, NO_PROP);
                totalRelCnt+=2;
            }
        }
        System.out.println("create "+totalRelCnt+" topo relationships");
        if(this.staticOnly) return;
        File ntp = dataGen.prepareTPCSV(dataSize, startTime, endTime, true, true, false);
        long totalSize = ntp.length();
        long progress = 0;
        totalRelCnt = 0;
        try(BufferedReader reader = new BufferedReader(new FileReader(ntp));
            BufferedReader ti = new BufferedReader(new FileReader(ntiTmp))){
            ti.readLine();
            PropHandler vp = new PropHandler(schema.nodeTemporal, reader.readLine().split(","));
            String line;
            while((line= reader.readLine())!=null){
                progress += line.length()+1;
                LineObj obj = vp.processTp(line);
                String tiKey = ti.readLine();
                long tid = Long.parseLong(tiKey);
                bulkDB.createRelationship(obj.id, tid, Neo4jServer1.Edge.V_TPROP, obj.prop);
                totalRelCnt++;
                if (ticker.shouldTick(0)) log.debug("import rel of ntp: {}%", progress * 100d / totalSize);
            }
        }
        System.out.println("create "+totalRelCnt+" nti relationships");
        File rtp = dataGen.prepareTPCSV(dataSize, startTime, endTime, false, true, false);
        totalSize = rtp.length();
        progress = 0;
        totalRelCnt = 0;
        try(BufferedReader reader = new BufferedReader(new FileReader(rtp));
            BufferedReader ti = new BufferedReader(new FileReader(rtiTmp))){
            ti.readLine();
            PropHandler vp = new PropHandler(schema.relTemporal, reader.readLine().split(","));
            String line;
            while((line= reader.readLine())!=null){
                progress += line.length()+1;
                LineObj obj = vp.processTp(line);
                long rId = relMap.get(obj.id);
                String tiKey = ti.readLine();
                long tid = Long.parseLong(tiKey);
                bulkDB.createRelationship(rId, tid, Neo4jServer1.Edge.E_TPROP, obj.prop);
                totalRelCnt++;
                if(ticker.shouldTick(0)) log.debug("import rel of rtp: {}%", progress*100d/totalSize);
            }
        }
        System.out.println("create "+totalRelCnt+" rti relationships");
    }

    public void createIndexAndWait() {
        DatabaseManagementService dbms = new DatabaseManagementServiceBuilder(dbDir.toPath()).build();
        GraphDatabaseService db = dbms.database("neo4j");
        try (Transaction tx = db.beginTx()) {
            tx.schema().indexFor(Neo4jServer1.label(Neo4jServer1.NodeType.VERTEX)).on("u_sid").create();
            tx.schema().indexFor(Neo4jServer1.label(Neo4jServer1.NodeType.EDGE)).on("u_sid").create();
            tx.commit();
        }
        awaitIndex(db);
        dbms.shutdown();
    }

    protected static class LineObj{
        long id;
        int st, et;
        long from, to;
        Map<String, Object> prop;
    }

    protected static class PropHandler{
        private final List<String> prop;
        private final Map<String, PVal.Type> schema;

        public PropHandler(Map<String, PVal.Type> schema, String... prop) {
            this.schema = schema;
            this.prop = Arrays.asList(prop);
        }

        public LineObj processNode(String line){
            String[] arr = line.split(",");
            LineObj obj = new LineObj();
            obj.id = Long.parseLong(arr[0]);
            obj.prop = new HashMap<>();
            for(int i=1; i<prop.size(); i++){
                String field = prop.get(i);
                String vStr = arr[i];
                PVal.Type type = schema.get(field);
                Object val;
                if(type== PVal.Type.INT) val = Integer.parseInt(vStr);
                else if(type== PVal.Type.FLOAT) val = Float.parseFloat(vStr);
                else if(type == PVal.Type.STRING) val = vStr;
                else throw new IllegalStateException("got type: "+type+" on "+field);
                obj.prop.put(field, val);
            }
            return obj;
        }

        public LineObj processEdge4Node(String line){
            String[] arr = line.split(",");
            LineObj obj = new LineObj();
            obj.id = Long.parseLong(arr[0]);
            obj.prop = new HashMap<>();
            for(int i=3; i<prop.size(); i++){
                String field = prop.get(i);
                String vStr = arr[i];
                PVal.Type type = schema.get(field);
                Object val;
                if(type== PVal.Type.INT) val = Integer.parseInt(vStr);
                else if(type== PVal.Type.FLOAT) val = Float.parseFloat(vStr);
                else if(type == PVal.Type.STRING) val = vStr;
                else throw new IllegalStateException("got type: "+type);
                obj.prop.put(field, val);
            }
            return obj;
        }

        public LineObj processEdge4Rel(String line){
            String[] arr = line.split(",");
            LineObj obj = new LineObj();
            obj.id = Long.parseLong(arr[0]);
            obj.from = Long.parseLong(arr[1]);
            obj.to = Long.parseLong(arr[2]);
            return obj;
        }

        public LineObj processTp(String line) {
            String[] arr = line.split(",");
            LineObj obj = new LineObj();
            obj.st = Integer.parseInt(arr[0]);
            obj.et = Integer.parseInt(arr[1]);
            obj.id = Long.parseLong(arr[2]);
            obj.prop = new HashMap<>();
            for(int i=3; i<prop.size(); i++){
                String field = prop.get(i);
                String vStr = arr[i];
                PVal.Type type = schema.get(field);
                Object val;
                if(type== PVal.Type.INT) val = Integer.parseInt(vStr);
                else if(type== PVal.Type.FLOAT) val = Float.parseFloat(vStr);
                else if(type == PVal.Type.STRING) val = vStr;
                else throw new IllegalStateException("got type: "+type);
                obj.prop.put(field, val);
            }
            return obj;
        }

        public LineObj processTp4Time(String line) {
            String[] arr = line.split(",");
            LineObj obj = new LineObj();
            obj.st = Integer.parseInt(arr[0]);
            obj.et = Integer.parseInt(arr[1]);
            obj.id = Long.parseLong(arr[2]);
            obj.prop = new HashMap<>();
            obj.prop.put("st", obj.st);
            obj.prop.put("et", obj.et);
            return obj;
        }
    }

    public void awaitIndex(GraphDatabaseService db){
        boolean shouldWait = true;
        int cnt = 0;
        while(shouldWait) try(Transaction tx = db.beginTx()){
            Schema schema = tx.schema();
            schema.awaitIndexesOnline(10, TimeUnit.SECONDS);
            shouldWait = false;
        } catch (IllegalArgumentException | IllegalStateException e) {
            cnt++;
            System.out.println("indexes not ready after "+cnt*10+" seconds.");
        }
        System.out.println("indexes all online.");
    }
}
