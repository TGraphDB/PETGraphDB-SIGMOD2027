package edu.buaa.batch;

import edu.buaa.common.benchmark.MilestoneBuilder;
import edu.buaa.utils.Helper;


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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.*;
import java.util.concurrent.TimeUnit;


public class TGQLBulkLoad extends MilestoneBuilder {
	private static final Label OBJECT_LABEL = Label.label("Object");
	private static final Label ATTRIBUTE_LABEL = Label.label("Attribute");
	private static final Label VALUE_LABEL = Label.label("Value");
    private static final RelationshipType RELATIONSHIP_TYPE_STATIC = RelationshipType.withName("static");
    private static final RelationshipType RELATIONSHIP_TYPE_TEMPORAL = RelationshipType.withName("temporal");

    private static final RelationshipType HAS_ATTRIBUTE = RelationshipType.withName("HAS_ATTRIBUTE"); // 对象与属性用此类型关系连接
    private static final RelationshipType HAS_VALUE = RelationshipType.withName("HAS_VALUE"); // 属性与属性值用此类型关系连接
	
	protected final File dbDir;
	private DatabaseLayout databaseLayout = null;
	protected final Map<String, Long> nMap = new HashMap<>(); // 原始节点ID(String) -> Neo4j生成的内部节点ID(Long)
	// protected final Map<String, Long> rMap = new HashMap<>(); // 原始关系ID(String) -> Neo4j生成的内部关系ID(Long)
    protected final Map<String, Long> rFromMap = new HashMap<>(); // 原始关系ID(String) -> 起点内部节点ID(Long)
    protected final Map<String, Long> rToMap = new HashMap<>(); // 原始关系ID(String) -> 终点内部节点ID(Long)
	protected final Map<String, Long> nAttrMap = new HashMap<>(); // 节点外部ID + 属性名 → 属性节点ID
    protected final Map<Integer, String> eidToUsidMap = new HashMap<>(); // 边ID(int) → u_sid(String)
    // protected final Map<String, String> nidToUsidMap = new HashMap<>(); // 点ID → u_sid


	public TGQLBulkLoad() throws Exception {
		super();
		this.dbDir = new File(Helper.mustEnv("DB_PATH"));
		System.out.println("DB dir: " + dbDir);
	}

	@Override
	public void close() throws Exception {
        // 创建索引
        DatabaseManagementService dbms = new DatabaseManagementServiceBuilder(dbDir.toPath()).build();
        GraphDatabaseService db = dbms.database("neo4j");
        try (Transaction tx = db.beginTx()) {
            // 为节点和关系的 u_sid 创建索引
            tx.schema().indexFor(OBJECT_LABEL).on("u_sid").create();
            tx.schema().indexFor(RELATIONSHIP_TYPE_STATIC).on("u_sid").create();
            tx.schema().indexFor(ATTRIBUTE_LABEL).on("title").create();
            tx.schema().indexFor(RELATIONSHIP_TYPE_TEMPORAL).on("u_sid").create();
            tx.commit();
        }
        try (Transaction tx = db.beginTx()) {
            awaitIndexes(tx.schema());
            tx.commit();
        }
        dbms.shutdown();
	}

	private DatabaseLayout getDefaultDatabaseLayout() {
		if (databaseLayout == null) {
			Config.Builder configBuilder = Config.newBuilder();
			configBuilder.set(GraphDatabaseSettings.neo4j_home, dbDir.toPath().toAbsolutePath());
			Neo4jLayout layout = Neo4jLayout.of(configBuilder.build());
			databaseLayout = layout.databaseLayout("neo4j");
		}
		return databaseLayout;
	}

	@Override
	public void importStatic() throws Exception{
        BatchInserter bulkDB = BatchInserters.inserter(getDefaultDatabaseLayout());
        File node = dataGen.prepareStaticCSV(true);
        File edge = dataGen.prepareStaticCSV(false);

        loadStaticNodesFromCsv(node, bulkDB);
        loadStaticEdgesFromCsv(edge, bulkDB);
        // 关闭 BatchInserter，数据落盘
        bulkDB.shutdown();
    }

    private void awaitIndexes(Schema schema) {
        boolean shouldWait = true;
        int cnt = 0;
        while (shouldWait) {
            try {
                schema.awaitIndexesOnline(10, TimeUnit.SECONDS);
                shouldWait = false;
            } catch (IllegalArgumentException | IllegalStateException e) {
                cnt++;
                System.out.println("indexes not ready after " + cnt * 10 + " seconds.");
            }
        }
        System.out.println("indexes all online.");
    }

    private void loadStaticNodesFromCsv(File nodeCsv, BatchInserter bulkDB) throws Exception {
        try (BufferedReader reader = new BufferedReader(new FileReader(nodeCsv))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return;
            }
            String[] header = headerLine.split(",");

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                String[] arr = line.split(",", -1);
                String id = arr[0];
                Map<String, Object> props = new HashMap<>();
                props.put("u_sid", arr[1]);
                for (int i = 2; i < header.length; i++) {
                    String key = header[i];
                    props.put(key, parseValue(arr[i]));
                }
                long neo4j_id = bulkDB.createNode(props, OBJECT_LABEL);
                nMap.put(id, neo4j_id);
                // nidToUsidMap.put(id, arr[1]);
            }
        }
    }

    private void loadStaticEdgesFromCsv(File edgeCsv, BatchInserter bulkDB) throws Exception {
        try (BufferedReader reader = new BufferedReader(new FileReader(edgeCsv))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return;
            }
            String[] header = headerLine.split(",");
            // int idIdx = requireHeader(header, "id");
            int idIdx = 0;
            int fromIdx = 1;
            int toIdx = 2;

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                String[] arr = line.split(",", -1);
                long from = nMap.get(arr[fromIdx]);
                long to = nMap.get(arr[toIdx]);

                Map<String, Object> props = new HashMap<>();
                props.put("u_sid", arr[3]);
                for (int i = 4; i < header.length; i++) {
                    String key = header[i];
                    props.put(key, parseValue(arr[i]));
                }

                long relId = bulkDB.createRelationship(from, to, RELATIONSHIP_TYPE_STATIC, props);
                // rMap.put(arr[idIdx], relId);
                rFromMap.put(arr[idIdx], from);
                rToMap.put(arr[idIdx], to);
                eidToUsidMap.put(Integer.parseInt(arr[idIdx]), arr[3]);
            }
        }
    }

	@Override
	public void importTemporal() throws Exception {
        File ntp = dataGen.prepareTPCSV(dataSize, startTime, endTime, true, true, false);
        BatchInserter nodeBulkDB = BatchInserters.inserter(getDefaultDatabaseLayout()); // 以批量插入模式打开数据库
        loadNodeTemporalFromCsv(ntp, nodeBulkDB);
        nodeBulkDB.shutdown();

        File rtp = dataGen.prepareTPCSV(dataSize, startTime, endTime, false, true, false);
        BatchInserter edgeBulkDB = BatchInserters.inserter(getDefaultDatabaseLayout());
        loadEdgeTemporalFromCsv(rtp, edgeBulkDB);
        edgeBulkDB.shutdown();
    }

    private void loadNodeTemporalFromCsv(File ntp, BatchInserter bulkDB) throws Exception {
        long totalSize = ntp.length();
        long progress = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(ntp))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return;
            }
            String[] header = headerLine.split(",");
            int stIdx = 0;
            int etIdx = 1;
            int entityIdx = 2;
            List<String> propNames = new ArrayList<>();
            for (int i = 3; i < header.length; i++) {
                propNames.add(header[i]);
            }

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                progress += line.length() + 1;
                String[] arr = line.split(",", -1);
                int st = Integer.parseInt(arr[stIdx]);
                int et = Integer.parseInt(arr[etIdx]);
                String entitySid = arr[entityIdx];
                Long objId = nMap.get(entitySid);
                if (objId == null) {
                    throw new IllegalStateException("node not found. id: " + entitySid);
                }

                for (int i = 0; i < propNames.size(); i++) {
                    int colIdx = 3 + i;
                    String prop = propNames.get(i);
                    String raw = arr[colIdx];
                    Object value = parseValue(raw);

                    Long attrId = nAttrMap.get(attrKey(entitySid, prop));
                    if (attrId == null) {
                        Map<String, Object> attrProps = new HashMap<>();
                        attrProps.put("title", prop);
                        attrId = bulkDB.createNode(attrProps, ATTRIBUTE_LABEL);
                        bulkDB.createRelationship(objId, attrId, HAS_ATTRIBUTE, Collections.emptyMap());
                        nAttrMap.put(attrKey(entitySid, prop), attrId);
                    }

                    Map<String, Object> valueProps = new HashMap<>();
                    valueProps.put("value", value);
                    valueProps.put("start_time", st);
                    valueProps.put("end_time", et);
                    long valueId = bulkDB.createNode(valueProps, VALUE_LABEL);
                    bulkDB.createRelationship(attrId, valueId, HAS_VALUE, Collections.emptyMap());
                }
                if (ticker.shouldTick(0)) {
                    log.debug("import time node of ntp: {}%", progress * 100d / totalSize);
                }
            }
        }
    }

    private Object parseValue(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
        }
        try {
            return Float.parseFloat(raw);
        } catch (NumberFormatException ignored) {
        }
        return raw;
    }

    private void loadEdgeTemporalFromCsv(File rtp, BatchInserter bulkDB) throws Exception {
        long totalSize = rtp.length();
        long progress = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(rtp))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return;
            }
            String[] header = headerLine.split(",");
            int stIdx = 0;
            int etIdx = 1;
            int entityIdx = 2;
            List<String> propNames = new ArrayList<>();
            for (int i = 3; i < header.length; i++) {
                propNames.add(header[i]);
            }

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                progress += line.length() + 1;
                String[] arr = line.split(",", -1);
                if (arr.length < 3) {
                    continue;
                }
                int st = Integer.parseInt(arr[stIdx]);
                int et = Integer.parseInt(arr[etIdx]);
                String relSid = arr[entityIdx];

                Long from = rFromMap.get(relSid);
                Long to = rToMap.get(relSid);
                if (from == null || to == null) {
                    throw new IllegalStateException("edge endpoints not found. id: " + relSid);
                }

                Map<String, Object> relProps = new HashMap<>();
                relProps.put("u_sid", eidToUsidMap.get(Integer.parseInt(relSid)));
                relProps.put("start_time", st);
                relProps.put("end_time", et);


                for (int i = 0; i < propNames.size(); i++) {
                    int colIdx = 3 + i;
                    if (colIdx >= arr.length) {
                        continue;
                    }
                    String prop = propNames.get(i);
                    String raw = arr[colIdx];
                    Object value = parseValue(raw);
                    relProps.put(prop, value);
                }

                bulkDB.createRelationship(from, to, RELATIONSHIP_TYPE_TEMPORAL, relProps);
                if (ticker.shouldTick(0)) {
                    log.debug("import time rel of rtp: {}%", progress * 100d / totalSize);
                }
            }
        }
    }

    private String attrKey(String uSid, String prop) {
        return uSid + "|" + prop;
    }
}

