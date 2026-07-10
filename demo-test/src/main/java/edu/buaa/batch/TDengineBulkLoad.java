package edu.buaa.batch;

import edu.buaa.common.benchmark.MilestoneBuilder;
import edu.buaa.common.utils.PVal;
import edu.buaa.utils.Helper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

public class TDengineBulkLoad extends MilestoneBuilder {
	private static final String NODE_TABLE = "node";
	private static final String REL_TABLE = "rel";
	private static final String NODE_TP_TABLE = "node_tp";
	private static final String REL_TP_TABLE = "rel_tp";
	private static final int DEFAULT_BATCH_SIZE = 10000;
	private static final int PROGRESS_LOG_INTERVAL = 10000;
	private static final String DEFAULT_HTTP_TIMEOUT = "300000"; // 5 minutes in ms
	private static final String DEFAULT_KEEP_DAYS = "365000";
	private static final long DEFAULT_RPC_QUEUE_MEMORY_ALLOWED = 160L * 1024 * 1024 * 1024;
	private static final String RS_JDBC_PREFIX = "jdbc:TAOS-RS://";
	private static final String WS_JDBC_PREFIX = "jdbc:TAOS-WS://";

	private final String serverHost = Helper.mustEnv("DB_HOST");
	private final int serverPort = Integer.parseInt(Helper.envOrDefault("TDENGINE_PORT",
		Helper.envOrDefault("TDENGINE_WS_PORT", "6041")));
	private final String dbName = Helper.mustEnv("MILESTONE_NAME");
	private final String user = Helper.envOrDefault("DB_USER", "root");
	private final String password = Helper.envOrDefault("DB_PASSWORD", "taosdata");
	private final int batchSize = Integer.parseInt(Helper.envOrDefault("TDENGINE_BATCH_SIZE", Integer.toString(DEFAULT_BATCH_SIZE)));
	private final String rpcQueueMemoryAllowed = Helper.envOrDefault("TDENGINE_RPC_QUEUE_MEMORY_ALLOWED",
		Long.toString(DEFAULT_RPC_QUEUE_MEMORY_ALLOWED));

	private final Connection conn;

	public TDengineBulkLoad() throws Exception {
		super();
		loadDriver(); // 加载 TDengine JDBC 驱动
		createDatabaseIfAbsent();
		this.conn = DriverManager.getConnection(databaseJdbcUrl(), user, password); // 连接到数据库
		this.conn.setAutoCommit(false); // 关闭自动提交，使用批量提交提高性能
		createTablesIfAbsent(); // 可以延迟到导入时创建表
		dataGen.setSectionEnable(false);
	}

	@Override
	public void importStatic() throws Exception {
		File node = dataGen.prepareStaticCSV(true);
		loadStaticCsv(node, true);
		File edge = dataGen.prepareStaticCSV(false);
		loadStaticCsv(edge, false);
	}

	@Override
	public void importTemporal() throws Exception {
		File ntp = dataGen.prepareTPCSV(dataSize, startTime, endTime, true, true, false);
		loadTemporalCsv(ntp, true);
		File rtp = dataGen.prepareTPCSV(dataSize, startTime, endTime, false, true, false);
		loadTemporalCsv(rtp, false);
	}

	@Override
	public void close() throws Exception {
		conn.commit();
		conn.close();
	}

	private void loadDriver() throws ClassNotFoundException {
		if (usesWebSocket()) {
			Class.forName("com.taosdata.jdbc.ws.WebSocketDriver");
		} else {
			Class.forName("com.taosdata.jdbc.rs.RestfulDriver");
		}
	}

	private void createDatabaseIfAbsent() throws SQLException {
		try (Connection adminConn = DriverManager.getConnection(adminJdbcUrl(), user, password);
			 Statement stmt = adminConn.createStatement()) {
			configureDnodeMemory(stmt);
			String keepDays = Helper.envOrDefault("TDENGINE_KEEP", DEFAULT_KEEP_DAYS);
			stmt.execute("CREATE DATABASE IF NOT EXISTS " + quoted(dbName) + " PRECISION 'ms' KEEP " + keepDays);
			stmt.execute("ALTER DATABASE " + quoted(dbName) + " KEEP " + keepDays);
		}
	}

	private void configureDnodeMemory(Statement stmt) throws SQLException {
		// TDengine defaults rpcQueueMemoryAllowed to 1/10 of server memory, which lands near 20 GiB on a 190 GiB host.
		stmt.execute("ALTER ALL DNODES 'rpcQueueMemoryAllowed' '" + rpcQueueMemoryAllowed + "'");
		System.out.println("Configured TDengine rpcQueueMemoryAllowed=" + rpcQueueMemoryAllowed + " bytes.");
	}

	private void createTablesIfAbsent() throws SQLException {
		try (Statement stmt = conn.createStatement()) {
			stmt.execute(createStaticTableSql(NODE_TABLE, schema.nodeStatic, false));
			stmt.execute(createStaticTableSql(REL_TABLE, schema.relStatic, true));
			stmt.execute(createTemporalStableSql(NODE_TP_TABLE, schema.nodeTemporal));
			stmt.execute(createTemporalStableSql(REL_TP_TABLE, schema.relTemporal));
		}
		conn.commit();
	}

	private void loadStaticCsv(File csv, boolean isNode) throws Exception {
		String tableName = isNode ? NODE_TABLE : REL_TABLE;
		String dataLabel = isNode ? "static nodes" : "static edges";
		try (BufferedReader reader = new BufferedReader(new FileReader(csv))) {
			String headerLine = reader.readLine();
			if (headerLine == null) {
				return;
			}
			String[] headers = headerLine.split(",");
			List<String> columns = new ArrayList<>();
			columns.add("ts");
			for (String header : headers) {
				columns.add(header);
			}
			String sql = insertSql(tableName, columns.toArray(new String[0]));
			try (PreparedStatement stmt = conn.prepareStatement(sql)) { // stmt可以复用，提升性能；index从1开始
				String line;
				int pending = 0;
				long imported = 0L;
				while ((line = reader.readLine()) != null) {
					if (line.isEmpty()) {
						continue;
					}
					String[] values = line.split(",", -1);
					stmt.setLong(1, staticTimestampMillis());
					for (int i = 0; i < headers.length; i++) {
						bindStaticValue(stmt, i + 2, headers[i], values[i], isNode); // 数据填充
					}
					stmt.addBatch();
					pending++;
					imported++;
					//logProgress(dataLabel, imported);
					if (pending >= batchSize) {
						flush(stmt, dataLabel, pending, imported);
						pending = 0;
					}
				}
				if (pending > 0) {
					flush(stmt, dataLabel, pending, imported);
				}
			}
		}
	}

	/**
	 * 使用 TDengine 多表 INSERT 语法批量导入时序数据。
	 * 每个 batch 内的数据按 entityId 分组，拼接成一条 INSERT SQL 一次性发送，
	 * 避免了为每个 entity 维护独立的 PreparedStatement 导致的性能下降。
	 */
	private void loadTemporalCsv(File csv, boolean isNode) throws Exception {
		String stableName = isNode ? NODE_TP_TABLE : REL_TP_TABLE;
		String dataLabel = isNode ? "temporal nodes" : "temporal edges";
		try (BufferedReader reader = new BufferedReader(new FileReader(csv))) {
			String headerLine = reader.readLine();
			if (headerLine == null) {
				return;
			}
			String[] headers = headerLine.split(",");
			List<String[]> buffer = new ArrayList<>(batchSize);
			String line;
			int pending = 0;
			long imported = 0L;
			while ((line = reader.readLine()) != null) {
				if (line.isEmpty()) {
					continue;
				}
				String[] values = line.split(",", -1);
				if (values.length < 3) {
					continue;
				}
				buffer.add(values);
				pending++;
				imported++;
				//logProgress(dataLabel, imported);
				if (pending >= batchSize) {
					flushTemporalBatch(buffer, stableName, headers, isNode, dataLabel, pending, imported);
					buffer.clear();
					pending = 0;
				}
			}
			if (pending > 0) {
				flushTemporalBatch(buffer, stableName, headers, isNode, dataLabel, pending, imported);
			}
		}
	}

	private void bindStaticValue(PreparedStatement stmt, int index, String column, String raw, boolean isNode) throws SQLException {
		if ("id".equals(column) || "r_from".equals(column) || "r_to".equals(column)) {
			stmt.setLong(index, Long.parseLong(raw));
			return;
		}
		PVal.Type type = schema.getType(isNode, true, column);
		setTypedValue(stmt, index, type, raw);
	}

	private void setTypedValue(PreparedStatement stmt, int index, PVal.Type type, String raw) throws SQLException {
		if (raw == null || raw.isEmpty()) {
			if (type == null || type == PVal.Type.STRING) {
				stmt.setString(index, "");
			} else if (type == PVal.Type.INT) {
				stmt.setNull(index, Types.INTEGER);
			} else if (type == PVal.Type.FLOAT) {
				stmt.setNull(index, Types.FLOAT);
			} else {
				stmt.setNull(index, Types.VARCHAR);
			}
			return;
		}
		if (type == null || type == PVal.Type.STRING) {
			stmt.setString(index, raw);
		} else if (type == PVal.Type.INT) {
			stmt.setInt(index, Integer.parseInt(raw));
		} else if (type == PVal.Type.FLOAT) {
			stmt.setFloat(index, Float.parseFloat(raw));
		} else {
			stmt.setString(index, raw);
		}
	}

	private void flush(PreparedStatement stmt, String dataLabel, int batchCount, long imported) throws SQLException {
		long begin = System.currentTimeMillis();
		stmt.executeBatch();
		stmt.clearBatch();
		conn.commit();
		long cost = System.currentTimeMillis() - begin;
		System.out.println("Flushed " + batchCount + ' ' + dataLabel + " into TDengine in " + cost
			+ " ms (total imported: " + imported + ").");
	}

	private int totalDataPoints = 0;
	private int tickBatches = 0;
	private int halfFullBatchRecordNum = 0;

	/**
	 * 将缓冲区中的时序数据按 entityId 分组，使用 TDengine 多表 INSERT 语法一次性写入。
	 * SQL 格式: INSERT INTO child1 USING stable TAGS(id1) VALUES(...) child2 USING stable TAGS(id2) VALUES(...)
	 * 子表不存在时会自动创建（USING ... TAGS 语法）。
	 */
	private void flushTemporalBatch(List<String[]> buffer, String stableName, String[] headers,
			boolean isNode, String dataLabel, int batchCount, long imported) throws SQLException {
		long begin = System.currentTimeMillis();
		// 按 entityId 分组
		Map<Long, List<String[]>> grouped = new LinkedHashMap<>();
		for (String[] values : buffer) {
			long entityId = Long.parseLong(values[2]);
			grouped.computeIfAbsent(entityId, k -> new ArrayList<>()).add(values);
		}
		// 拼接多表 INSERT SQL
		StringBuilder sql = new StringBuilder("INSERT INTO ");
		for (Map.Entry<Long, List<String[]>> entry : grouped.entrySet()) {
			long entityId = entry.getKey();
			String childTable = temporalChildTableName(stableName, entityId);
			sql.append(quoted(childTable)).append(" USING ").append(quoted(stableName))
				.append(" TAGS(").append(entityId).append(") VALUES ");
			for (String[] values : entry.getValue()) {
				sql.append('(');
				sql.append(timestampMillis(values[0]));
				sql.append(',').append(timestampMillis(values[1]));
				for (int i = 3; i < headers.length; i++) {
					sql.append(',').append(formatSqlValue(headers[i], values[i], isNode));
				}
				sql.append(") ");
			}
		}
		try (Statement stmt = conn.createStatement()) {
			stmt.execute(sql.toString());
		}
		conn.commit();
//		long cost = System.currentTimeMillis() - begin;
//		System.out.println("Flushed " + batchCount + ' ' + dataLabel + " into TDengine in " + cost
//			+ " ms (total imported: " + imported + ").");
		if (batchCount == batchSize) {
			tickBatches++;
		}
		else {
			halfFullBatchRecordNum += batchCount;
		}
		totalDataPoints += batchCount;
		if(ticker.shouldTick(0)) {
			log.debug("loading tp: {}%, total points: {}", 0.0, totalDataPoints);
			log.debug("full batches inserted: {}, half full batch records inserted: {}", tickBatches, halfFullBatchRecordNum);
			tickBatches = 0;
			halfFullBatchRecordNum = 0;
		}
	}

	private String formatSqlValue(String column, String raw, boolean isNode) {
		PVal.Type type = schema.getType(isNode, false, column);
		if (raw == null || raw.isEmpty()) {
			if (type == PVal.Type.INT || type == PVal.Type.FLOAT) {
				return "NULL";
			}
			return "''";
		}
		if (type == PVal.Type.INT || type == PVal.Type.FLOAT) {
			return raw;
		}
		return "'" + raw.replace("'", "\\'") + "'";
	}

	private void logProgress(String dataLabel, long imported) {
		if (imported % PROGRESS_LOG_INTERVAL == 0) {
			System.out.println("Imported " + imported + ' ' + dataLabel + " into TDengine.");
		}
	}

	private long timestampMillis(String seconds) {
		return Long.parseLong(seconds) * 1000L;
	}

	private long staticTimestampMillis() {
		if (startTime != null && !startTime.isEmpty()) {
			try {
				// startTime 可能是纯数字的 Unix 时间戳（秒），直接转毫秒
				long seconds = Long.parseLong(startTime);
				return seconds * 1000L;
			} catch (NumberFormatException e) {
				// 非纯数字，按 yyyyMMdd 日期格式解析
				return ((long) Helper.str2UnixTimestamp(startTime)) * 1000L;
			}
		}
		return 0L;
	}

	private String createStaticTableSql(String tableName, Map<String, PVal.Type> props, boolean isRel) {
		StringBuilder sb = new StringBuilder();
		sb.append("CREATE TABLE IF NOT EXISTS ").append(quoted(tableName)).append(" (")
			.append(quoted("ts")).append(" TIMESTAMP, ")
			.append(quoted("id")).append(" BIGINT");
		if (isRel) {
			sb.append(", ").append(quoted("r_from")).append(" BIGINT, ").append(quoted("r_to")).append(" BIGINT");
		}
		appendProps(sb, props);
		sb.append(")");
		return sb.toString();
	}

	private String createTemporalStableSql(String tableName, Map<String, PVal.Type> props) {
		StringBuilder sb = new StringBuilder();
		sb.append("CREATE STABLE IF NOT EXISTS ").append(quoted(tableName))
			.append(" (").append(quoted("ts")).append(" TIMESTAMP, ")
			.append(quoted("end_time")).append(" TIMESTAMP");
		appendProps(sb, props);
		sb.append(") TAGS (").append(quoted("entity")).append(" BIGINT)");
		return sb.toString();
	}

	private void appendProps(StringBuilder sb, Map<String, PVal.Type> props) {
		for (Map.Entry<String, PVal.Type> entry : props.entrySet()) {
			sb.append(", ").append(quoted(entry.getKey())).append(' ').append(typeToSql(entry.getValue()));
		}
	}

	private String typeToSql(PVal.Type type) {
		if (type == PVal.Type.INT) {
			return "INT";
		}
		if (type == PVal.Type.FLOAT) {
			return "FLOAT";
		}
		return "BINARY(256)";
	}

	private String insertSql(String tableName, String[] columns) {
		StringJoiner columnJoiner = new StringJoiner(",");
		StringJoiner placeholderJoiner = new StringJoiner(",");
		for (String column : columns) {
			columnJoiner.add(quoted(column));
			placeholderJoiner.add("?");
		}
		return "INSERT INTO " + quoted(tableName) + "(" + columnJoiner + ") VALUES (" + placeholderJoiner + ")";
	}

	private String quoted(String identifier) {
		return '`' + identifier + '`';
	}

	private String temporalChildTableName(String stableName, long entityId) {
		return stableName + "_e_" + entityId;
	}

	private String adminJdbcUrl() {
		return Helper.envOrDefault("TDENGINE_ADMIN_JDBC_URL", defaultJdbcPrefix() + serverHost + ':' + serverPort + '?' + timeoutParams());
	}

	private String databaseJdbcUrl() {
		return Helper.envOrDefault("TDENGINE_JDBC_URL", defaultJdbcPrefix() + serverHost + ':' + serverPort + '/' + dbName + '?' + timeoutParams());
	}

	private String timeoutParams() {
		String timeout = Helper.envOrDefault("TDENGINE_HTTP_TIMEOUT", DEFAULT_HTTP_TIMEOUT);
		return "httpConnectTimeout=" + timeout + "&httpSocketTimeout=" + timeout;
	}

	private boolean usesWebSocket() {
		String adminUrl = Helper.envOrDefault("TDENGINE_ADMIN_JDBC_URL", "");
		if (adminUrl.startsWith(WS_JDBC_PREFIX)) {
			return true;
		}
		String databaseUrl = Helper.envOrDefault("TDENGINE_JDBC_URL", "");
		return databaseUrl.startsWith(WS_JDBC_PREFIX);
	}

	private String defaultJdbcPrefix() {
		return usesWebSocket() ? WS_JDBC_PREFIX : RS_JDBC_PREFIX;
	}
}
