package com.cqcp.apiserver.reviewengine;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Read-only repository for the Execution Status Query API.
 *
 * <p>Executes a single JOIN query against task + execution + review_result_snapshot.
 * Does not read stage logs, diagnostics, or any sensitive fields.
 */
@Repository
class ReviewExecutionStatusRepository {

    private static final String FIND_STATUS_SQL = """
            SELECT
                t.task_id,
                t.result_url,
                e.execution_id,
                e.status,
                e.current_stage,
                e.model_profile_code,
                e.provider_type,
                e.model_name,
                e.endpoint_alias,
                e.model_config_version,
                e.created_at,
                e.updated_at,
                s.execution_id AS snap_execution_id,
                s.superseded_by_execution_id,
                s.superseded_reason
            FROM task t
            JOIN execution e ON e.task_id = t.task_id
            LEFT JOIN review_result_snapshot s
                ON s.task_id = e.task_id AND s.execution_id = e.execution_id
            WHERE t.task_id = ?
              AND e.execution_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    ReviewExecutionStatusRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    /**
     * Find execution status for the given task and execution IDs.
     *
     * @return a single row, or empty if task/execution does not exist or IDs don't match
     * @throws IllegalStateException if more than one row is returned (data integrity)
     */
    Optional<ReviewExecutionStatusModels.ExecutionStatusRow> findStatus(String taskId, String executionId) {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(executionId, "executionId");

        var rows = jdbcTemplate.query(FIND_STATUS_SQL, STATUS_ROW_MAPPER, taskId, executionId);

        if (rows.isEmpty()) {
            return Optional.empty();
        }
        if (rows.size() > 1) {
            throw new IllegalStateException(
                    "Multiple rows for taskId=" + taskId + " executionId=" + executionId);
        }
        return Optional.of(rows.getFirst());
    }

    private static final RowMapper<ReviewExecutionStatusModels.ExecutionStatusRow> STATUS_ROW_MAPPER =
            (ResultSet rs, int rowNum) -> {
                try {
                    return new ReviewExecutionStatusModels.ExecutionStatusRow(
                            rs.getString("task_id"),
                            rs.getString("result_url"),
                            rs.getString("execution_id"),
                            rs.getString("status"),
                            rs.getString("current_stage"),
                            rs.getString("model_profile_code"),
                            rs.getString("provider_type"),
                            rs.getString("model_name"),
                            rs.getString("endpoint_alias"),
                            rs.getString("model_config_version"),
                            toInstant(rs.getTimestamp("created_at")),
                            toInstant(rs.getTimestamp("updated_at")),
                            rs.getString("snap_execution_id"),
                            rs.getString("superseded_by_execution_id"),
                            rs.getString("superseded_reason"));
                } catch (SQLException e) {
                    throw new RuntimeException("Failed to map ExecutionStatusRow", e);
                }
            };

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
