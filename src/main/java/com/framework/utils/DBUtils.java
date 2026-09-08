package com.framework.utils;

import com.framework.config.ConfigManager;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * PostgreSQL backend-validation helper via HikariCP pooling.
 * Every query is parameterized (PreparedStatement) — never build SQL via string concatenation
 * with test data, even in test code, to avoid habits leaking into production-adjacent scripts.
 * All Connections/Statements/ResultSets are opened with try-with-resources so nothing leaks
 * even when a test assertion throws mid-query.
 */
public final class DBUtils {

    private static final Logger LOGGER = LogManager.getLogger(DBUtils.class);
    private static volatile HikariDataSource dataSource;

    private DBUtils() {
    }

    private static HikariDataSource getDataSource() {
        if (dataSource == null) {
            synchronized (DBUtils.class) {
                if (dataSource == null) {
                    ConfigManager config = ConfigManager.getInstance();
                    HikariConfig hikariConfig = new HikariConfig();
                    hikariConfig.setJdbcUrl(String.format("jdbc:postgresql://%s:%s/%s",
                            config.get("db.host"), config.get("db.port", "5432"), config.get("db.name")));
                    hikariConfig.setUsername(config.get("db.user"));
                    hikariConfig.setPassword(config.get("db.password"));
                    hikariConfig.setMaximumPoolSize(config.getInt("db.pool.size", 5));
                    hikariConfig.setPoolName("qa-automation-pool");
                    dataSource = new HikariDataSource(hikariConfig);
                    LOGGER.info("Initialized DB connection pool for {}", config.get("db.name"));
                }
            }
        }
        return dataSource;
    }

    /** Runs a SELECT with parameterized args, returns rows as ordered maps keyed by column name. */
    public static List<Map<String, Object>> query(String sql, Object... params) {
        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            bindParams(stmt, params);

            try (ResultSet rs = stmt.executeQuery()) {
                var meta = rs.getMetaData();
                int columnCount = meta.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        row.put(meta.getColumnLabel(i), rs.getObject(i));
                    }
                    results.add(row);
                }
            }
            return results;

        } catch (SQLException e) {
            throw new IllegalStateException("Query failed: " + sql, e);
        }
    }

    /** Returns a single scalar value (e.g. SELECT status FROM payments WHERE id = ?), or null if no row. */
    public static Object queryScalar(String sql, Object... params) {
        List<Map<String, Object>> rows = query(sql, params);
        if (rows.isEmpty()) {
            return null;
        }
        return rows.get(0).values().iterator().next();
    }

    /** For INSERT/UPDATE/DELETE in test setup/teardown; returns affected row count. */
    public static int execute(String sql, Object... params) {
        try (Connection conn = getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            bindParams(stmt, params);
            return stmt.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Update failed: " + sql, e);
        }
    }

    private static void bindParams(PreparedStatement stmt, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            stmt.setObject(i + 1, params[i]);
        }
    }

    public static void closePool() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
