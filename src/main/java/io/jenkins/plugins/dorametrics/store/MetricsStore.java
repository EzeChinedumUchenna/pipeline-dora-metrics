package io.jenkins.plugins.dorametrics.store;

import jenkins.model.Jenkins;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * H2 embedded database for storing build metrics.
 * Located at JENKINS_HOME/pipeline-dora-metrics/metrics
 */
public class MetricsStore {

    private static final Logger LOGGER = Logger.getLogger(MetricsStore.class.getName());
    private static MetricsStore instance;

    private static final Set<String> ALLOWED_ORDER_BY = Set.of(
            "avg_dur DESC", "avg_dur ASC", "failures DESC", "failures ASC",
            "total DESC", "total ASC"
    );

    private final org.h2.jdbcx.JdbcDataSource dataSource;

    private MetricsStore() {
        File jenkinsHome = Jenkins.get().getRootDir();
        File dbDir = new File(jenkinsHome, "pipeline-dora-metrics");
        if (!dbDir.exists()) {
            dbDir.mkdirs();
        }
        String dbUrl = "jdbc:h2:file:" + new File(dbDir, "metrics").getAbsolutePath()
                + ";AUTO_SERVER=TRUE;MODE=MySQL";

        this.dataSource = new org.h2.jdbcx.JdbcDataSource();
        this.dataSource.setURL(dbUrl);
        this.dataSource.setUser("sa");
        this.dataSource.setPassword("");

        initializeSchema();
    }

    /** Package-private for testing. */
    MetricsStore(org.h2.jdbcx.JdbcDataSource ds) {
        this.dataSource = ds;
        initializeSchema();
    }

    public static synchronized MetricsStore getInstance() {
        if (instance == null) {
            instance = new MetricsStore();
        }
        return instance;
    }

    /** Package-private for testing. */
    static synchronized void setInstance(MetricsStore store) {
        instance = store;
    }

    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    private void initializeSchema() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS builds ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                    + "job_name VARCHAR(500) NOT NULL,"
                    + "build_number INT NOT NULL,"
                    + "timestamp BIGINT NOT NULL,"
                    + "duration_ms BIGINT NOT NULL,"
                    + "result VARCHAR(20) NOT NULL,"
                    + "trigger_type VARCHAR(50),"
                    + "branch VARCHAR(200),"
                    + "UNIQUE(job_name, build_number)"
                    + ")");

            try {
                stmt.execute("ALTER TABLE builds ADD COLUMN IF NOT EXISTS branch VARCHAR(200)");
            } catch (SQLException ignored) { }

            stmt.execute("CREATE TABLE IF NOT EXISTS stages ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                    + "build_id BIGINT NOT NULL,"
                    + "stage_name VARCHAR(500) NOT NULL,"
                    + "duration_ms BIGINT NOT NULL,"
                    + "result VARCHAR(20) NOT NULL,"
                    + "FOREIGN KEY (build_id) REFERENCES builds(id)"
                    + ")");

            stmt.execute("CREATE TABLE IF NOT EXISTS commits ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                    + "build_id BIGINT NOT NULL,"
                    + "commit_sha VARCHAR(40) NOT NULL,"
                    + "author VARCHAR(200),"
                    + "timestamp BIGINT NOT NULL,"
                    + "FOREIGN KEY (build_id) REFERENCES builds(id)"
                    + ")");

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_builds_job ON builds(job_name)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_builds_timestamp ON builds(timestamp)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_builds_result ON builds(result)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_stages_build ON stages(build_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_commits_build ON commits(build_id)");

            LOGGER.info("Pipeline DORA Metrics: database initialized");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize metrics database", e);
        }
    }

    // === Write operations ===

    public long insertBuild(String jobName, int buildNumber, long timestamp,
                            long durationMs, String result, String triggerType, String branch) {
        String sql = "MERGE INTO builds (job_name, build_number, timestamp, duration_ms, result, trigger_type, branch) "
                + "KEY (job_name, build_number) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, jobName);
            ps.setInt(2, buildNumber);
            ps.setLong(3, timestamp);
            ps.setLong(4, durationMs);
            ps.setString(5, result);
            ps.setString(6, triggerType);
            ps.setString(7, branch);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to insert build: " + jobName + "#" + buildNumber, e);
        }
        return -1;
    }

    public void insertStage(long buildId, String stageName, long durationMs, String result) {
        String sql = "INSERT INTO stages (build_id, stage_name, duration_ms, result) VALUES (?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, buildId);
            ps.setString(2, stageName);
            ps.setLong(3, durationMs);
            ps.setString(4, result);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to insert stage: " + stageName, e);
        }
    }

    public void insertCommit(long buildId, String sha, String author, long timestamp) {
        String sql = "INSERT INTO commits (build_id, commit_sha, author, timestamp) VALUES (?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, buildId);
            ps.setString(2, sha);
            ps.setString(3, author);
            ps.setLong(4, timestamp);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to insert commit: " + sha, e);
        }
    }

    // === Read operations ===

    public List<BuildRecord> getBuilds(String jobName, long fromTimestamp, long toTimestamp) {
        List<BuildRecord> records = new ArrayList<>();
        String sql = "SELECT * FROM builds WHERE job_name = ? AND timestamp BETWEEN ? AND ? ORDER BY timestamp";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, jobName);
            ps.setLong(2, fromTimestamp);
            ps.setLong(3, toTimestamp);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    records.add(BuildRecord.fromResultSet(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to query builds for " + jobName, e);
        }
        return records;
    }

    public List<BuildRecord> getAllBuilds(long fromTimestamp, long toTimestamp) {
        List<BuildRecord> records = new ArrayList<>();
        String sql = "SELECT * FROM builds WHERE timestamp BETWEEN ? AND ? ORDER BY timestamp";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, fromTimestamp);
            ps.setLong(2, toTimestamp);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    records.add(BuildRecord.fromResultSet(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to query all builds", e);
        }
        return records;
    }

    public List<String> getAllJobNames() {
        List<String> names = new ArrayList<>();
        String sql = "SELECT DISTINCT job_name FROM builds ORDER BY job_name";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                names.add(rs.getString("job_name"));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to query job names", e);
        }
        return names;
    }

    public long getEarliestCommitTimestamp(long buildId) {
        String sql = "SELECT MIN(timestamp) FROM commits WHERE build_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, buildId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to query earliest commit", e);
        }
        return 0;
    }

    public List<StageRecord> getStages(long buildId) {
        List<StageRecord> records = new ArrayList<>();
        String sql = "SELECT * FROM stages WHERE build_id = ? ORDER BY id";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, buildId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    records.add(new StageRecord(
                            rs.getLong("id"),
                            rs.getLong("build_id"),
                            rs.getString("stage_name"),
                            rs.getLong("duration_ms"),
                            rs.getString("result")
                    ));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to query stages", e);
        }
        return records;
    }

    // === Optimized aggregate queries ===

    public long countSuccessfulBuilds(long fromMs, long toMs, String jobPattern) {
        if (".*".equals(jobPattern) || jobPattern == null) {
            return executeCount("SELECT COUNT(*) FROM builds WHERE timestamp BETWEEN ? AND ? AND result = 'SUCCESS'",
                    fromMs, toMs);
        }
        return executeCount("SELECT COUNT(*) FROM builds WHERE timestamp BETWEEN ? AND ? AND result = 'SUCCESS' AND job_name REGEXP ?",
                fromMs, toMs, jobPattern);
    }

    public long countTotalBuilds(long fromMs, long toMs, String jobPattern) {
        if (".*".equals(jobPattern) || jobPattern == null) {
            return executeCount("SELECT COUNT(*) FROM builds WHERE timestamp BETWEEN ? AND ?", fromMs, toMs);
        }
        return executeCount("SELECT COUNT(*) FROM builds WHERE timestamp BETWEEN ? AND ? AND job_name REGEXP ?",
                fromMs, toMs, jobPattern);
    }

    public long countFailedBuilds(long fromMs, long toMs, String jobPattern) {
        if (".*".equals(jobPattern) || jobPattern == null) {
            return executeCount("SELECT COUNT(*) FROM builds WHERE timestamp BETWEEN ? AND ? AND result = 'FAILURE'",
                    fromMs, toMs);
        }
        return executeCount("SELECT COUNT(*) FROM builds WHERE timestamp BETWEEN ? AND ? AND result = 'FAILURE' AND job_name REGEXP ?",
                fromMs, toMs, jobPattern);
    }

    public double avgLeadTimeMs(long fromMs, long toMs, String jobPattern) {
        String baseSql = "SELECT AVG((b.timestamp + b.duration_ms) - c.min_commit) FROM builds b "
                + "INNER JOIN (SELECT build_id, MIN(timestamp) as min_commit FROM commits GROUP BY build_id) c "
                + "ON b.id = c.build_id "
                + "WHERE b.timestamp BETWEEN ? AND ? AND b.result = 'SUCCESS' AND c.min_commit > 0";
        String sql = (".*".equals(jobPattern) || jobPattern == null) ? baseSql : baseSql + " AND b.job_name REGEXP ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, fromMs);
            ps.setLong(2, toMs);
            if (!".*".equals(jobPattern) && jobPattern != null) ps.setString(3, jobPattern);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble(1);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.FINE, "avgLeadTimeMs query failed", e);
        }
        return 0;
    }

    public List<JobStats> getJobStats(long fromMs, long toMs, int limit, String orderBy) {
        if (!ALLOWED_ORDER_BY.contains(orderBy)) {
            LOGGER.warning("Rejected invalid orderBy: " + orderBy);
            return Collections.emptyList();
        }
        List<JobStats> results = new ArrayList<>();
        String sql = "SELECT job_name, COUNT(*) as total, "
                + "AVG(duration_ms) as avg_dur, "
                + "SUM(CASE WHEN result = 'FAILURE' THEN 1 ELSE 0 END) as failures "
                + "FROM builds WHERE timestamp BETWEEN ? AND ? "
                + "GROUP BY job_name ORDER BY " + orderBy + " LIMIT ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, fromMs);
            ps.setLong(2, toMs);
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new JobStats(
                            rs.getString("job_name"),
                            rs.getInt("total"),
                            rs.getDouble("avg_dur"),
                            rs.getInt("failures")
                    ));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "getJobStats query failed", e);
        }
        return results;
    }

    public List<StageStats> getStageStats(long fromMs, long toMs, int limit, String orderBy) {
        if (!ALLOWED_ORDER_BY.contains(orderBy)) {
            LOGGER.warning("Rejected invalid orderBy: " + orderBy);
            return Collections.emptyList();
        }
        List<StageStats> results = new ArrayList<>();
        String sql = "SELECT s.stage_name, COUNT(*) as total, "
                + "AVG(s.duration_ms) as avg_dur, "
                + "SUM(CASE WHEN s.result = 'FAILURE' THEN 1 ELSE 0 END) as failures "
                + "FROM stages s INNER JOIN builds b ON s.build_id = b.id "
                + "WHERE b.timestamp BETWEEN ? AND ? "
                + "GROUP BY s.stage_name ORDER BY " + orderBy + " LIMIT ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, fromMs);
            ps.setLong(2, toMs);
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new StageStats(
                            rs.getString("stage_name"),
                            rs.getInt("total"),
                            rs.getDouble("avg_dur"),
                            rs.getInt("failures")
                    ));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "getStageStats query failed", e);
        }
        return results;
    }

    private long executeCount(String sql, long fromMs, long toMs) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, fromMs);
            ps.setLong(2, toMs);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.FINE, "Count query failed", e);
        }
        return 0;
    }

    private long executeCount(String sql, long fromMs, long toMs, String pattern) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, fromMs);
            ps.setLong(2, toMs);
            ps.setString(3, pattern);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.FINE, "Count query failed", e);
        }
        return 0;
    }

    // === Maintenance ===

    public void cleanup(long retainAfterTimestamp) {
        try (Connection conn = getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM commits WHERE build_id IN (SELECT id FROM builds WHERE timestamp < ?)")) {
                ps.setLong(1, retainAfterTimestamp);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM stages WHERE build_id IN (SELECT id FROM builds WHERE timestamp < ?)")) {
                ps.setLong(1, retainAfterTimestamp);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM builds WHERE timestamp < ?")) {
                ps.setLong(1, retainAfterTimestamp);
                ps.executeUpdate();
            }
            LOGGER.info("Pipeline DORA Metrics: old data cleaned up");
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to cleanup old metrics", e);
        }
    }

    // === Record types ===

    public static class BuildRecord {
        public final long id;
        public final String jobName;
        public final int buildNumber;
        public final long timestamp;
        public final long durationMs;
        public final String result;
        public final String triggerType;
        public final String branch;

        public BuildRecord(long id, String jobName, int buildNumber, long timestamp,
                           long durationMs, String result, String triggerType, String branch) {
            this.id = id;
            this.jobName = jobName;
            this.buildNumber = buildNumber;
            this.timestamp = timestamp;
            this.durationMs = durationMs;
            this.result = result;
            this.triggerType = triggerType;
            this.branch = branch;
        }

        public static BuildRecord fromResultSet(ResultSet rs) throws SQLException {
            return new BuildRecord(
                    rs.getLong("id"), rs.getString("job_name"), rs.getInt("build_number"),
                    rs.getLong("timestamp"), rs.getLong("duration_ms"), rs.getString("result"),
                    rs.getString("trigger_type"), rs.getString("branch"));
        }

        public boolean isSuccess() { return "SUCCESS".equals(result); }
        public boolean isFailure() { return "FAILURE".equals(result); }
    }

    public static class StageRecord {
        public final long id;
        public final long buildId;
        public final String stageName;
        public final long durationMs;
        public final String result;

        public StageRecord(long id, long buildId, String stageName, long durationMs, String result) {
            this.id = id;
            this.buildId = buildId;
            this.stageName = stageName;
            this.durationMs = durationMs;
            this.result = result;
        }
    }

    public static class JobStats {
        public final String jobName;
        public final int buildCount;
        public final double avgDurationMs;
        public final int failureCount;

        public JobStats(String jobName, int buildCount, double avgDurationMs, int failureCount) {
            this.jobName = jobName;
            this.buildCount = buildCount;
            this.avgDurationMs = avgDurationMs;
            this.failureCount = failureCount;
        }
    }

    public static class StageStats {
        public final String stageName;
        public final int totalRuns;
        public final double avgDurationMs;
        public final int failureCount;

        public StageStats(String stageName, int totalRuns, double avgDurationMs, int failureCount) {
            this.stageName = stageName;
            this.totalRuns = totalRuns;
            this.avgDurationMs = avgDurationMs;
            this.failureCount = failureCount;
        }
    }
}
