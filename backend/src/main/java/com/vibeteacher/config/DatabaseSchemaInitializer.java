package com.vibeteacher.config;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
@DependsOn("flyway")
public class DatabaseSchemaInitializer {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseSchemaInitializer.class);

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public DatabaseSchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @PostConstruct
    public void initialize() {
        logDatabaseInfoAndValidateConnection();
        ensureSchemaCompatibility();
    }

    private void logDatabaseInfoAndValidateConnection() {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            logger.info("Database connected. product={}, version={}, url={}",
                meta.getDatabaseProductName(),
                meta.getDatabaseProductVersion(),
                maskUrl(meta.getURL()));
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        } catch (Exception ex) {
            logger.error("Database connection failed at startup.", ex);
            throw new IllegalStateException("数据库连接失败，请检查配置与网络连通性", ex);
        }
    }

    private void ensureSchemaCompatibility() {
        runSql("CREATE TABLE IF NOT EXISTS user_progress ("
            + "user_id VARCHAR(128) PRIMARY KEY,"
            + "total_submissions INT NOT NULL DEFAULT 0,"
            + "current_streak INT NOT NULL DEFAULT 0,"
            + "longest_streak INT NOT NULL DEFAULT 0,"
            + "suggested_vocabulary_usage_count INT NOT NULL DEFAULT 0,"
            + "last_submission_date DATE NULL)");

        runSql("CREATE TABLE IF NOT EXISTS user_vocabulary ("
            + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
            + "user_id VARCHAR(128) NOT NULL,"
            + "word VARCHAR(128) NOT NULL,"
            + "meaning VARCHAR(1024) NULL,"
            + "example VARCHAR(2048) NULL)");

        runSql("CREATE TABLE IF NOT EXISTS user_achievement ("
            + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
            + "user_id VARCHAR(128) NOT NULL,"
            + "achievement_id VARCHAR(128) NOT NULL,"
            + "earned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");

        runSql("CREATE TABLE IF NOT EXISTS revision_record ("
            + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
            + "user_id VARCHAR(128) NOT NULL,"
            + "level VARCHAR(64) NULL,"
            + "model VARCHAR(128) NULL,"
            + "requirements LONGTEXT NULL,"
            + "expand BOOLEAN NOT NULL DEFAULT FALSE,"
            + "source_text LONGTEXT NOT NULL,"
            + "corrected_text LONGTEXT NULL,"
            + "summary LONGTEXT NULL,"
            + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB ROW_FORMAT=DYNAMIC");

        runSql("CREATE TABLE IF NOT EXISTS error_occurrence ("
            + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
            + "revision_id BIGINT NOT NULL,"
            + "user_id VARCHAR(128) NOT NULL,"
            + "error_type VARCHAR(128) NULL,"
            + "original_text LONGTEXT NULL,"
            + "suggested_text LONGTEXT NULL,"
            + "reason LONGTEXT NULL) ENGINE=InnoDB ROW_FORMAT=DYNAMIC");

        // Column forward-compatibility: if new columns are missing in old schema, add them quietly.
        addColumnIfMissing("user_progress", "total_submissions", "INT NOT NULL DEFAULT 0");
        addColumnIfMissing("user_progress", "current_streak", "INT NOT NULL DEFAULT 0");
        addColumnIfMissing("user_progress", "longest_streak", "INT NOT NULL DEFAULT 0");
        addColumnIfMissing("user_progress", "suggested_vocabulary_usage_count", "INT NOT NULL DEFAULT 0");
        addColumnIfMissing("user_progress", "last_submission_date", "DATE NULL");

        addColumnIfMissing("user_vocabulary", "user_id", "VARCHAR(128) NOT NULL");
        addColumnIfMissing("user_vocabulary", "word", "VARCHAR(128) NOT NULL");
        addColumnIfMissing("user_vocabulary", "meaning", "VARCHAR(1024) NULL");
        addColumnIfMissing("user_vocabulary", "example", "VARCHAR(2048) NULL");

        addColumnIfMissing("user_achievement", "user_id", "VARCHAR(128) NOT NULL");
        addColumnIfMissing("user_achievement", "achievement_id", "VARCHAR(128) NOT NULL");
        addColumnIfMissing("user_achievement", "earned_at", "TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP");

        addColumnIfMissing("revision_record", "level", "VARCHAR(64) NULL");
        addColumnIfMissing("revision_record", "model", "VARCHAR(128) NULL");
        addColumnIfMissing("revision_record", "requirements", "LONGTEXT NULL");
        addColumnIfMissing("revision_record", "expand", "BOOLEAN NOT NULL DEFAULT FALSE");
        addColumnIfMissing("revision_record", "source_text", "LONGTEXT NOT NULL");
        addColumnIfMissing("revision_record", "corrected_text", "LONGTEXT NULL");
        addColumnIfMissing("revision_record", "summary", "LONGTEXT NULL");
        addColumnIfMissing("revision_record", "created_at", "TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP");

        addColumnIfMissing("error_occurrence", "revision_id", "BIGINT NOT NULL");
        addColumnIfMissing("error_occurrence", "user_id", "VARCHAR(128) NOT NULL");
        addColumnIfMissing("error_occurrence", "error_type", "VARCHAR(128) NULL");
        addColumnIfMissing("error_occurrence", "original_text", "LONGTEXT NULL");
        addColumnIfMissing("error_occurrence", "suggested_text", "LONGTEXT NULL");
        addColumnIfMissing("error_occurrence", "reason", "LONGTEXT NULL");

        addIndexIfMissing("user_vocabulary", "uk_user_word", true, "(user_id, word)");
        addIndexIfMissing("user_achievement", "uk_user_achievement", true, "(user_id, achievement_id)");
        addIndexIfMissing("revision_record", "idx_revision_record_user_created", false, "(user_id, created_at DESC)");
        addIndexIfMissing("error_occurrence", "idx_error_occurrence_user", false, "(user_id)");
        addIndexIfMissing("error_occurrence", "idx_error_occurrence_type", false, "(error_type)");
    }

    private void addColumnIfMissing(String tableName, String columnName, String definition) {
        if (hasColumn(tableName, columnName)) {
            return;
        }
        runSql("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + definition);
    }

    private boolean hasColumn(String tableName, String columnName) {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            if (hasColumn(meta, conn.getCatalog(), tableName, columnName)) {
                return true;
            }
            String schema = conn.getSchema();
            return hasColumn(meta, schema, tableName, columnName);
        } catch (Exception ex) {
            logger.warn("Failed to inspect column metadata. table={}, column={}", tableName, columnName, ex);
            return false;
        }
    }

    private boolean hasColumn(DatabaseMetaData meta, String schemaPattern, String tableName, String columnName) {
        try (ResultSet rs = meta.getColumns(null, schemaPattern, tableName, null)) {
            while (rs.next()) {
                String existing = rs.getString("COLUMN_NAME");
                if (existing != null && existing.equalsIgnoreCase(columnName)) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            // Fall through and let caller try another metadata scope.
        }
        return false;
    }

    private void addIndexIfMissing(String tableName, String indexName, boolean unique, String columnsClause) {
        if (hasIndex(tableName, indexName)) {
            return;
        }
        String uniquePrefix = unique ? "UNIQUE " : "";
        runSql("CREATE " + uniquePrefix + "INDEX " + indexName + " ON " + tableName + columnsClause);
    }

    private boolean hasIndex(String tableName, String indexName) {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String catalog = conn.getCatalog();
            String schema = conn.getSchema();
            if (hasIndex(meta, catalog, schema, tableName, indexName)) {
                return true;
            }
            return hasIndex(meta, null, null, tableName, indexName);
        } catch (Exception ex) {
            logger.warn("Failed to inspect index metadata. table={}, index={}", tableName, indexName, ex);
            return false;
        }
    }

    private boolean hasIndex(DatabaseMetaData meta, String catalog, String schema, String tableName, String indexName) {
        try (ResultSet rs = meta.getIndexInfo(catalog, schema, tableName, false, false)) {
            while (rs.next()) {
                String existing = rs.getString("INDEX_NAME");
                if (existing != null && existing.equalsIgnoreCase(indexName)) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            // Fall through and let caller try another metadata scope.
        }
        return false;
    }

    private void runSql(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception ex) {
            logger.warn("Schema compatibility SQL skipped due to error. sql={}", sql, ex);
        }
    }

    private String maskUrl(String url) {
        if (url == null) {
            return "null";
        }
        int q = url.indexOf('?');
        return q < 0 ? url : url.substring(0, q) + "?...";
    }
}
