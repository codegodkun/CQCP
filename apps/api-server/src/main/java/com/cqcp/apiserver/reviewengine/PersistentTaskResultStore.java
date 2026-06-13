package com.cqcp.apiserver.reviewengine;

import com.cqcp.apiserver.tuning.PointDiagnostic;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * TASK-022 的最小持久化结果查询适配层。
 * 仅负责从 V1 schema 读取 task 和 review_result_snapshot，
 * 不触发审核、不修改 execution 状态、不写 stage log。
 */
@Primary
@Component
public final class PersistentTaskResultStore implements TaskResultStore {

    private static final String FIND_LATEST_SNAPSHOT_SQL = """
            SELECT
                task_id,
                execution_id,
                superseded_by_execution_id,
                superseded_reason,
                status,
                summary::text AS summary_json,
                review_completeness::text AS review_completeness_json,
                point_results::text AS point_results_json,
                findings::text AS findings_json,
                diagnostics::text AS diagnostics_json,
                source_anchors::text AS source_anchors_json,
                structured_fields_snapshot::text AS structured_fields_snapshot_json,
                enabled_review_points_snapshot::text AS enabled_review_points_snapshot_json,
                disabled_review_points_snapshot::text AS disabled_review_points_snapshot_json,
                contract_type_profile_version,
                rule_set_version,
                review_budget_profile_version,
                model_profile_version,
                parser_version,
                prompt_version,
                schema_version,
                pattern_library_version,
                field_lexicon_version,
                evidence_selector_version,
                created_at
            FROM review_result_snapshot
            WHERE task_id = ?
              AND superseded_by_execution_id IS NULL
            ORDER BY created_at DESC
            LIMIT 1
            """;

    private static final String EXISTS_TASK_SQL = """
            SELECT EXISTS (
                SELECT 1
                FROM task
                WHERE task_id = ?
            )
            """;

    private static final TypeReference<List<PointReviewResult>> POINT_RESULTS_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<ReviewFinding>> FINDINGS_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<PointDiagnostic>> DIAGNOSTICS_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<SourceAnchorSummary>> SOURCE_ANCHORS_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, String>> STRUCTURED_FIELDS_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<ReviewPointSnapshot>> REVIEW_POINT_SNAPSHOTS_TYPE = new TypeReference<>() {};

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public PersistentTaskResultStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public boolean hasTask(String taskId) {
        Objects.requireNonNull(taskId, "taskId");
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(EXISTS_TASK_SQL, Boolean.class, taskId));
    }

    @Override
    public Optional<ReviewResultSnapshot> findLatestSnapshot(String taskId) {
        Objects.requireNonNull(taskId, "taskId");
        var rows = jdbcTemplate.queryForList(FIND_LATEST_SNAPSHOT_SQL, taskId);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(mapSnapshot(rows.getFirst()));
    }

    private ReviewResultSnapshot mapSnapshot(Map<String, Object> row) {
        return new ReviewResultSnapshot(
                requiredString(row, "task_id"),
                requiredString(row, "execution_id"),
                nullableString(row, "superseded_by_execution_id"),
                nullableString(row, "superseded_reason"),
                SnapshotStatus.valueOf(requiredString(row, "status")),
                readJson(requiredString(row, "summary_json"), ReviewSummary.class),
                readJson(requiredString(row, "review_completeness_json"), ReviewCompleteness.class),
                readJson(requiredString(row, "point_results_json"), POINT_RESULTS_TYPE),
                readJson(requiredString(row, "findings_json"), FINDINGS_TYPE),
                readJson(requiredString(row, "diagnostics_json"), DIAGNOSTICS_TYPE),
                readJson(requiredString(row, "source_anchors_json"), SOURCE_ANCHORS_TYPE),
                readJson(requiredString(row, "structured_fields_snapshot_json"), STRUCTURED_FIELDS_TYPE),
                readJson(requiredString(row, "enabled_review_points_snapshot_json"), REVIEW_POINT_SNAPSHOTS_TYPE),
                readJson(requiredString(row, "disabled_review_points_snapshot_json"), REVIEW_POINT_SNAPSHOTS_TYPE),
                requiredString(row, "contract_type_profile_version"),
                requiredString(row, "rule_set_version"),
                requiredString(row, "review_budget_profile_version"),
                requiredString(row, "model_profile_version"),
                requiredString(row, "parser_version"),
                requiredString(row, "prompt_version"),
                requiredString(row, "schema_version"),
                requiredString(row, "pattern_library_version"),
                requiredString(row, "field_lexicon_version"),
                requiredString(row, "evidence_selector_version"),
                requiredInstant(row, "created_at"));
    }

    private String requiredString(Map<String, Object> row, String key) {
        var value = row.get(key);
        if (value == null) {
            throw new IllegalStateException("Missing required column: " + key);
        }
        return value.toString();
    }

    private String nullableString(Map<String, Object> row, String key) {
        var value = row.get(key);
        return value == null ? null : value.toString();
    }

    private Instant requiredInstant(Map<String, Object> row, String key) {
        var value = row.get(key);
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        throw new IllegalStateException("Unsupported timestamp column: " + key + ", value=" + value);
    }

    private <T> T readJson(String rawJson, Class<T> type) {
        try {
            return objectMapper.readValue(rawJson, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize snapshot JSON into " + type.getSimpleName(), exception);
        }
    }

    private <T> T readJson(String rawJson, TypeReference<T> type) {
        try {
            return objectMapper.readValue(rawJson, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize snapshot JSON payload", exception);
        }
    }
}
