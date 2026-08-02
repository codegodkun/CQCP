package com.cqcp.apiserver.modelgateway;

import static com.cqcp.apiserver.modelgateway.ModelProfileAdminModels.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
class ModelProfileAdminRepository {

    private static final String PROFILE_COLUMNS = """
            SELECT
                profile_code,
                config_version,
                display_name,
                provider_type,
                endpoint_alias,
                model_name,
                enabled,
                usage_scope,
                is_default_for_new_task,
                secret_required,
                secret_ref,
                readiness_status,
                timeout_seconds,
                retry_count,
                effective_from,
                created_at
            FROM model_profile_config_version
            """;

    private static final String FIND_LATEST_PROFILES_SQL = """
            SELECT DISTINCT ON (profile_code) *
            FROM (
            """ + PROFILE_COLUMNS + """
            ) profiles
            ORDER BY profile_code, created_at DESC, config_version DESC
            """;

    private static final String FIND_LATEST_PROFILE_SQL = PROFILE_COLUMNS + """
            WHERE profile_code = ?
            ORDER BY created_at DESC, config_version DESC
            LIMIT 1
            """;

    private static final String LOCK_LATEST_PROFILE_SQL = FIND_LATEST_PROFILE_SQL + " FOR UPDATE";
    private static final String LOCK_PROFILE_SCOPE_SQL =
            "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))";

    private static final String INSERT_PROFILE_SQL = """
            INSERT INTO model_profile_config_version (
                profile_code,
                config_version,
                display_name,
                provider_type,
                endpoint_alias,
                model_name,
                enabled,
                usage_scope,
                is_default_for_new_task,
                secret_required,
                secret_ref,
                readiness_status,
                timeout_seconds,
                retry_count,
                effective_from,
                created_at
            ) VALUES (?, ?, ?, ?, ?, ?, false, ?, false, true, ?, 'NOT_READY', ?, ?, ?, ?)
            """;

    private static final String DEACTIVATE_PROFILE_SQL = """
            UPDATE model_profile_config_version
            SET enabled = false,
                is_default_for_new_task = false
            WHERE profile_code = ?
              AND (enabled = true OR is_default_for_new_task = true)
            """;

    private static final String UPDATE_READINESS_SQL = """
            UPDATE model_profile_config_version
            SET readiness_status = ?
            WHERE profile_code = ?
              AND config_version = ?
            """;

    private static final String INSERT_CONNECTIVITY_SQL = """
            INSERT INTO model_profile_connectivity_test (
                connectivity_test_id,
                profile_code,
                config_version,
                status,
                http_status_class,
                model_available,
                duration_ms,
                tested_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String FIND_LATEST_CONNECTIVITY_SQL = """
            SELECT
                connectivity_test_id,
                profile_code,
                config_version,
                status,
                http_status_class,
                model_available,
                duration_ms,
                tested_at
            FROM model_profile_connectivity_test
            WHERE profile_code = ?
              AND config_version = ?
            ORDER BY tested_at DESC, connectivity_test_id DESC
            LIMIT 1
            """;

    private final JdbcTemplate jdbcTemplate;

    ModelProfileAdminRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    List<ModelProfileConfigRow> findLatestProfiles() {
        return jdbcTemplate.query(FIND_LATEST_PROFILES_SQL, PROFILE_MAPPER);
    }

    Optional<ModelProfileConfigRow> findLatestProfile(String profileCode) {
        return one(jdbcTemplate.query(FIND_LATEST_PROFILE_SQL, PROFILE_MAPPER, profileCode));
    }

    Optional<ModelProfileConfigRow> lockLatestProfile(String profileCode) {
        return one(jdbcTemplate.query(LOCK_LATEST_PROFILE_SQL, PROFILE_MAPPER, profileCode));
    }

    void lockProfileScope(String profileCode) {
        jdbcTemplate.queryForList(LOCK_PROFILE_SCOPE_SQL, profileCode);
    }

    void deactivateProfile(String profileCode) {
        jdbcTemplate.update(DEACTIVATE_PROFILE_SQL, profileCode);
    }

    void insertProfile(ModelProfileConfigRow row) {
        jdbcTemplate.update(
                INSERT_PROFILE_SQL,
                row.profileCode(),
                row.configVersion(),
                row.displayName(),
                row.providerType(),
                row.endpointAlias(),
                row.modelName(),
                row.usageScope(),
                row.secretRef(),
                row.timeoutSeconds(),
                row.retryCount(),
                Timestamp.from(row.effectiveFrom()),
                Timestamp.from(row.createdAt()));
    }

    Optional<ConnectivityRow> findLatestConnectivity(String profileCode, String configVersion) {
        return one(jdbcTemplate.query(
                FIND_LATEST_CONNECTIVITY_SQL,
                CONNECTIVITY_MAPPER,
                profileCode,
                configVersion));
    }

    void insertConnectivity(ConnectivityRow row) {
        jdbcTemplate.update(
                INSERT_CONNECTIVITY_SQL,
                row.connectivityTestId(),
                row.profileCode(),
                row.configVersion(),
                row.status().name(),
                row.httpStatusClass(),
                row.modelAvailable(),
                row.durationMs(),
                Timestamp.from(row.testedAt()));
    }

    void updateReadiness(String profileCode, String configVersion, boolean ready) {
        jdbcTemplate.update(
                UPDATE_READINESS_SQL,
                ready ? "READY" : "NOT_READY",
                profileCode,
                configVersion);
    }

    private static <T> Optional<T> one(List<T> rows) {
        if (rows.size() > 1) {
            throw new IllegalStateException("Expected at most one model profile row");
        }
        return rows.stream().findFirst();
    }

    private static final RowMapper<ModelProfileConfigRow> PROFILE_MAPPER =
            (ResultSet rs, int rowNum) -> new ModelProfileConfigRow(
                    rs.getString("profile_code"),
                    rs.getString("config_version"),
                    rs.getString("display_name"),
                    rs.getString("provider_type"),
                    rs.getString("endpoint_alias"),
                    rs.getString("model_name"),
                    rs.getBoolean("enabled"),
                    rs.getString("usage_scope"),
                    rs.getBoolean("is_default_for_new_task"),
                    rs.getBoolean("secret_required"),
                    rs.getString("secret_ref"),
                    rs.getString("readiness_status"),
                    rs.getInt("timeout_seconds"),
                    rs.getInt("retry_count"),
                    instant(rs, "effective_from"),
                    instant(rs, "created_at"));

    private static final RowMapper<ConnectivityRow> CONNECTIVITY_MAPPER =
            (ResultSet rs, int rowNum) -> new ConnectivityRow(
                    rs.getString("connectivity_test_id"),
                    rs.getString("profile_code"),
                    rs.getString("config_version"),
                    ConnectivityStatus.valueOf(rs.getString("status")),
                    rs.getString("http_status_class"),
                    rs.getBoolean("model_available"),
                    rs.getLong("duration_ms"),
                    instant(rs, "tested_at"));

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        var value = resultSet.getObject(column);
        if (value instanceof Instant instant) return instant;
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof OffsetDateTime offsetDateTime) return offsetDateTime.toInstant();
        throw new SQLException("Unsupported timestamp column: " + column);
    }
}
