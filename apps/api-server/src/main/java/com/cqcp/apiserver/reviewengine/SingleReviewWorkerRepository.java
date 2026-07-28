package com.cqcp.apiserver.reviewengine;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
/**
 * Repository for atomically claiming a QUEUED execution from PostgreSQL
 * using {@code FOR UPDATE SKIP LOCKED}.
 *
 * <p>Created as a {@code @Bean} in {@link SingleReviewWorkerConfiguration}
 * rather than via component scanning, to avoid duplicate bean definitions.</p>
 */
class SingleReviewWorkerRepository {

    private static final String CLAIM_SQL = """
            WITH candidate AS (
                SELECT execution_id
                FROM execution
                WHERE status = 'QUEUED'
                  AND (stage_lease_owner IS NULL
                       OR stage_lease_expires_at IS NULL
                       OR stage_lease_expires_at < NOW())
                ORDER BY created_at ASC, execution_id ASC
                FOR UPDATE SKIP LOCKED
                LIMIT 1
            )
            UPDATE execution e
            SET stage_lease_owner = :owner,
                stage_lease_acquired_at = NOW(),
                stage_lease_expires_at = NOW() + (:leaseDuration || ' seconds')::INTERVAL,
                heartbeat_at = NOW(),
                updated_at = NOW()
            FROM candidate
            WHERE e.execution_id = candidate.execution_id
              AND e.status = 'QUEUED'
            RETURNING e.execution_id
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    SingleReviewWorkerRepository(JdbcTemplate jdbcTemplate) {
        this(new NamedParameterJdbcTemplate(Objects.requireNonNull(jdbcTemplate, "jdbcTemplate")));
    }

    /** Package-private for testing — allows mock/template injection. */
    SingleReviewWorkerRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    /**
     * Atomically claim one QUEUED execution.
     *
     * @param owner              unique worker identifier
     * @param leaseDurationSeconds  lease validity period in seconds
     * @return claimed execution_id, or empty if none available
     */
    Optional<String> claimOne(String owner, long leaseDurationSeconds) {
        Objects.requireNonNull(owner, "owner");
        var params = new MapSqlParameterSource()
                .addValue("owner", owner)
                .addValue("leaseDuration", leaseDurationSeconds);
        var ids = jdbcTemplate.query(CLAIM_SQL, params,
                (rs, rowNum) -> rs.getString("execution_id"));
        return ids.isEmpty() ? Optional.empty() : Optional.of(ids.getFirst());
    }
}
