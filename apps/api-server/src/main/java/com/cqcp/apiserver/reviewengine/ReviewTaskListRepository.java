package com.cqcp.apiserver.reviewengine;

import static com.cqcp.apiserver.reviewengine.ReviewTaskListModels.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class ReviewTaskListRepository {

    private static final String SELECT_COLUMNS = """
            SELECT
                t.task_id,
                t.contract_name,
                '/review/results/' || t.task_id || '?executionId=' || e.execution_id AS result_url,
                e.execution_id,
                e.status,
                e.current_stage,
                e.created_at,
                e.updated_at,
                e.finished_at,
                e.model_profile_code,
                COALESCE(mp.display_name, e.model_profile_code) AS model_display_name,
                e.provider_type,
                e.model_name,
                e.endpoint_alias,
                e.model_config_version,
                s.summary::text AS summary_json
            FROM task t
            JOIN execution e ON e.task_id = t.task_id
            LEFT JOIN review_result_snapshot s
                ON s.task_id = e.task_id AND s.execution_id = e.execution_id
            LEFT JOIN model_profile_config_version mp
                ON mp.profile_code = e.model_profile_code
               AND mp.config_version = e.model_config_version
            """;

    private static final String COUNT_PREFIX = """
            SELECT COUNT(*)
            FROM task t
            JOIN execution e ON e.task_id = t.task_id
            """;

    private static final String PROCESSING_PREDICATE = """
            e.status NOT IN ('SUCCESS', 'PARTIAL_SUCCESS', 'FAILED', 'CANCELLED')
            """;
    private static final String COMPLETED_PREDICATE = """
            e.status IN ('SUCCESS', 'PARTIAL_SUCCESS')
            """;
    private static final String FAILED_PREDICATE = """
            e.status IN ('FAILED', 'CANCELLED')
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    ReviewTaskListRepository(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    TaskExecutionPage findPage(int page, int size, StatusGroup statusGroup, String queryPattern) {
        var params = new MapSqlParameterSource()
                .addValue("limit", size)
                .addValue("offset", Math.multiplyExact(page, size));
        var where = whereClause(statusGroup, queryPattern, params);
        var rows = jdbcTemplate.queryForList(
                SELECT_COLUMNS + where + """
                        ORDER BY e.created_at DESC, e.execution_id DESC
                        LIMIT :limit OFFSET :offset
                        """,
                params);
        var total = Objects.requireNonNull(
                jdbcTemplate.queryForObject(COUNT_PREFIX + where, params, Long.class));
        var items = rows.stream().map(this::mapItem).toList();
        var totalPages = total == 0 ? 0 : Math.toIntExact((total + size - 1) / size);
        return new TaskExecutionPage(items, page, size, total, totalPages);
    }

    private String whereClause(
            StatusGroup statusGroup,
            String queryPattern,
            MapSqlParameterSource params) {
        var predicates = new java.util.ArrayList<String>();
        if (statusGroup != null) {
            predicates.add(switch (statusGroup) {
                case PROCESSING -> PROCESSING_PREDICATE;
                case COMPLETED -> COMPLETED_PREDICATE;
                case FAILED -> FAILED_PREDICATE;
            });
        }
        if (queryPattern != null) {
            params.addValue("queryPattern", queryPattern);
            predicates.add("""
                    (
                        LOWER(t.contract_name) LIKE :queryPattern ESCAPE '\\'
                        OR LOWER(t.task_id) LIKE :queryPattern ESCAPE '\\'
                        OR LOWER(e.execution_id) LIKE :queryPattern ESCAPE '\\'
                    )
                    """);
        }
        return predicates.isEmpty() ? "" : " WHERE " + String.join(" AND ", predicates);
    }

    private TaskExecutionItem mapItem(Map<String, Object> row) {
        return new TaskExecutionItem(
                requiredString(row, "task_id"),
                requiredString(row, "execution_id"),
                requiredString(row, "contract_name"),
                requiredString(row, "status"),
                requiredString(row, "current_stage"),
                requiredString(row, "result_url"),
                toInstant(row.get("created_at")),
                toInstant(row.get("updated_at")),
                toInstant(row.get("finished_at")),
                new ModelBinding(
                        requiredString(row, "model_profile_code"),
                        requiredString(row, "model_display_name"),
                        requiredString(row, "provider_type"),
                        requiredString(row, "model_name"),
                        requiredString(row, "endpoint_alias"),
                        requiredString(row, "model_config_version")),
                parseStatistics((String) row.get("summary_json")));
    }

    private ResultStatistics parseStatistics(String summaryJson) {
        if (summaryJson == null) {
            return null;
        }
        try {
            var summary = objectMapper.readValue(summaryJson, ReviewSummary.class);
            return new ResultStatistics(
                    summary.plannedPointCount(),
                    summary.passCount(),
                    summary.errorCount(),
                    summary.warningCount(),
                    summary.notConcludedCount(),
                    summary.skippedCount());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot deserialize execution result statistics", exception);
        }
    }

    private static String requiredString(Map<String, Object> row, String key) {
        var value = row.get(key);
        if (value == null) {
            throw new IllegalStateException("Missing task list column: " + key);
        }
        return value.toString();
    }

    private static Instant toInstant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        throw new IllegalStateException("Unsupported timestamp value");
    }
}
