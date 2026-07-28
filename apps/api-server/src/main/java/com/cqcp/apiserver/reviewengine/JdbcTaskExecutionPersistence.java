package com.cqcp.apiserver.reviewengine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

public class JdbcTaskExecutionPersistence implements TaskExecutionPersistence {

    private static final Logger log = LoggerFactory.getLogger(JdbcTaskExecutionPersistence.class);

    /** ThreadLocal to carry the current lifecycle owner for guarded writes.
     *  Set by the worker before calling execute(), cleared in finally. */
    private static final ThreadLocal<String> CURRENT_OWNER = new ThreadLocal<>();

    public static void setCurrentOwner(String owner) { CURRENT_OWNER.set(owner); }
    public static void clearCurrentOwner() { CURRENT_OWNER.remove(); }

    private static final String UPDATE_EXECUTION_SET = """
            UPDATE execution
            SET status = :status, current_stage = :currentStage,
                started_at = CASE WHEN started_at IS NULL THEN :startedAt ELSE started_at END,
                finished_at = :finishedAt,
                stage_lease_owner = CASE WHEN :clearLease THEN NULL ELSE stage_lease_owner END,
                stage_lease_acquired_at = CASE WHEN :clearLease THEN NULL ELSE stage_lease_acquired_at END,
                stage_lease_expires_at = CASE WHEN :clearLease THEN NULL
                    ELSE NOW() + (stage_lease_expires_at - COALESCE(heartbeat_at, stage_lease_acquired_at)) END,
                heartbeat_at = CASE WHEN :clearLease THEN NULL ELSE NOW() END,
                updated_at = NOW()
            WHERE execution_id = :executionId AND stage_lease_owner = :owner
            """;

    /** Additional WHERE clause: non-terminal refresh requires valid baseline. */
    private static final String NON_TERMINAL_BASELINE = " (stage_lease_expires_at IS NOT NULL"
            + " AND COALESCE(heartbeat_at, stage_lease_acquired_at) IS NOT NULL)";

    /** Frozen state-machine predecessor map for saveExecution guard.
     *  Non-terminal terminal targets also guard on current_stage = :currentStage. */
    private static String transitionGuard(ExecutionStatus toStatus) {
        if (toStatus == ExecutionStatus.FAILED || toStatus == ExecutionStatus.CANCELLED) {
            return " AND status NOT IN ('SUCCESS','PARTIAL_SUCCESS','FAILED','CANCELLED')"
                    + " AND current_stage = :currentStage";
        }
        if (toStatus == ExecutionStatus.REVIEWING_RULES) {
            return " AND ((status = 'QUEUED' AND current_stage = 'QUEUED')"
                    + " OR (status = 'BUILDING_EVIDENCE' AND current_stage = 'BUILDING_EVIDENCE'))";
        }
        return switch (toStatus) {
            case PARSING -> " AND status = 'QUEUED' AND current_stage = 'QUEUED'";
            case INDEXING -> " AND status = 'PARSING' AND current_stage = 'PARSING'";
            case PLANNING -> " AND status = 'INDEXING' AND current_stage = 'INDEXING'";
            case BUILDING_EVIDENCE -> " AND status = 'PLANNING' AND current_stage = 'PLANNING'";
            case COMPOSING -> " AND status = 'REVIEWING_RULES' AND current_stage = 'REVIEWING_RULES'";
            case SUCCESS, PARTIAL_SUCCESS -> " AND status = 'COMPOSING' AND current_stage = 'COMPOSING'";
            default -> throw new IllegalArgumentException("Unsupported status: " + toStatus);
        };
    }

    /** Frozen event-type → (guardStatus, guardCurrentStage) for stage log INSERT...SELECT.
     *  Only STARTED, COMPLETED, and FAILED are accepted.  stageName must be a valid non-terminal
     *  {@link ExecutionStatus}. */
    private static void stageLogGuard(String eventType, String stageName,
                                       MapSqlParameterSource params) {
        if (!List.of("STARTED", "COMPLETED", "FAILED").contains(eventType)) {
            throw new IllegalArgumentException("Unsupported stage log eventType: " + eventType);
        }
        if (!"FAILED".equals(eventType)) {
            // Validate stageName is a non-terminal ExecutionStatus
            var parsed = ExecutionStatus.valueOf(stageName);
            if (parsed.isTerminal()) {
                throw new IllegalArgumentException("Stage name must not be terminal: " + stageName);
            }
            params.addValue("guardStatus", stageName);
            params.addValue("guardCurrentStage", stageName);
        } else {
            params.addValue("guardStatus", "FAILED");
            params.addValue("guardCurrentStage", stageName);
        }
    }

    private static final String INSERT_STAGE_LOG_GUARDED_SQL = """
            INSERT INTO task_stage_log (task_id, execution_id, stage_name, attempt,
                event_type, summary_status, business_reason,
                diagnostic_code, duration_ms, detail_payload, created_at)
            SELECT t.task_id, e.execution_id, :stageName, :attempt,
                   :eventType, :summaryStatus, :businessReason,
                   :diagnosticCode, :durationMs, :detailPayload::jsonb, :createdAt
            FROM execution e JOIN task t ON t.task_id = e.task_id
            WHERE e.execution_id = :executionId
              AND e.stage_lease_owner = :owner
              AND e.status = :guardStatus
              AND e.current_stage = :guardCurrentStage
            """;

    private static final String INSERT_SNAPSHOT_SQL = """
            INSERT INTO review_result_snapshot (task_id, execution_id, status, summary,
                review_completeness, point_results, findings, diagnostics, source_anchors,
                structured_fields_snapshot, enabled_review_points_snapshot, disabled_review_points_snapshot,
                contract_type_profile_version, rule_set_version, review_budget_profile_version,
                model_profile_version, parser_version, prompt_version, schema_version,
                pattern_library_version, field_lexicon_version, evidence_selector_version, created_at)
            SELECT t.task_id, e.execution_id, :status, :summary::jsonb, :reviewCompleteness::jsonb,
                   :pointResults::jsonb, :findings::jsonb, :diagnostics::jsonb, :sourceAnchors::jsonb,
                   :structuredFields::jsonb, :enabledPoints::jsonb, :disabledPoints::jsonb,
                   :contractTypeProfileVersion, :ruleSetVersion, :reviewBudgetProfileVersion,
                   :modelProfileVersion, :parserVersion, :promptVersion, :schemaVersion,
                   :patternLibraryVersion, :fieldLexiconVersion, :evidenceSelectorVersion, :createdAt
            FROM execution e JOIN task t ON t.task_id = e.task_id
            WHERE e.execution_id = :executionId
              AND e.stage_lease_owner = :owner
              AND e.status = 'COMPOSING' AND e.current_stage = 'COMPOSING'
            """;

    private static final String LOAD_EXECUTION_TASK_SQL = """
            SELECT t.task_id, t.contract_name, t.structured_fields_snapshot::text AS sf_snapshot,
                   t.contract_metadata ->> 'documentReference' AS doc_ref,
                   e.execution_id, e.status, e.current_stage,
                   e.contract_type_profile_version, e.rule_set_version,
                   e.review_budget_profile_version, e.model_profile_code,
                   e.model_config_version, e.parser_version, e.prompt_version,
                   e.schema_version, e.pattern_library_version,
                   e.field_lexicon_version, e.evidence_selector_version,
                   e.provider_type, e.model_name, e.endpoint_alias,
                   e.started_at, e.finished_at
            FROM execution e JOIN task t ON t.task_id = e.task_id
            WHERE e.execution_id = :executionId AND e.stage_lease_owner = :owner
            """;

    private static final String LOAD_STATUS_SQL = """
            SELECT status, current_stage, task_id
            FROM execution WHERE execution_id = :executionId AND stage_lease_owner = :owner
            """;

    private static final String CURRENT_OWNER_MUST_BE_SET = "saveExecution requires CURRENT_OWNER via setCurrentOwner()";

    /** Lifecycle UPDATE suffix: exact status+current_stage guard. */
    private static final String LIFECYCLE_WHERE = " AND status = :whereStatus AND current_stage = :whereCurrentStage";

    /** Lifecycle UPDATE suffix with non-terminal lease baseline guard (for clearLease=false paths). */
    private static final String LIFECYCLE_NON_TERMINAL_WHERE = LIFECYCLE_WHERE + " AND" + NON_TERMINAL_BASELINE;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcTaskExecutionPersistence(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbc = new NamedParameterJdbcTemplate(Objects.requireNonNull(jdbcTemplate, "jdbcTemplate"));
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    // ── Interface methods — guarded with explicit predecessor state machine ──

    @Override
    public void saveExecution(TaskExecutionRecord execution) {
        var owner = CURRENT_OWNER.get();
        if (owner == null) throw new IllegalStateException(CURRENT_OWNER_MUST_BE_SET);
        var toStatus = execution.status();
        var sql = UPDATE_EXECUTION_SET + transitionGuard(toStatus);
        // Non-terminal lease refresh requires valid expires + heartbeat/acquired baseline
        if (!toStatus.isTerminal()) {
            sql += " AND" + NON_TERMINAL_BASELINE;
        }
        var params = new MapSqlParameterSource()
                .addValue("executionId", execution.executionId()).addValue("owner", owner)
                .addValue("status", toStatus.name())
                .addValue("currentStage", execution.currentStage())
                .addValue("startedAt", toTimestamp(execution.startedAt()))
                .addValue("finishedAt", toTimestamp(execution.finishedAt()))
                .addValue("clearLease", toStatus.isTerminal());
        int updated = jdbc.update(sql, params);
        if (updated != 1) {
            throw new IllegalStateException(
                    "saveExecution rejected: execution=" + execution.executionId()
                    + " target=(" + toStatus + "/" + execution.currentStage() + "/" + owner + ")");
        }
    }

    @Override
    public void appendStageLog(TaskStageLogEntry entry) {
        var owner = CURRENT_OWNER.get();
        if (owner == null) throw new IllegalStateException(CURRENT_OWNER_MUST_BE_SET);
        var lp = new MapSqlParameterSource()
                .addValue("executionId", entry.executionId()).addValue("owner", owner)
                .addValue("stageName", entry.stageName()).addValue("attempt", entry.attempt())
                .addValue("eventType", entry.eventType())
                .addValue("summaryStatus", entry.summaryStatus())
                .addValue("businessReason", entry.businessReason())
                .addValue("diagnosticCode", entry.diagnosticCode())
                .addValue("durationMs", entry.durationMs())
                .addValue("detailPayload", serialiseDetailPayload(entry.detailPayload()))
                .addValue("createdAt", toTimestamp(entry.createdAt()));
        stageLogGuard(entry.eventType(), entry.stageName(), lp);
        int inserted = jdbc.update(INSERT_STAGE_LOG_GUARDED_SQL, lp);
        if (inserted != 1) {
            throw new IllegalStateException(
                    "appendStageLog guard failed: execution=" + entry.executionId()
                    + " event=" + entry.eventType() + " stage=" + entry.stageName());
        }
    }

    @Override
    public void saveSnapshot(ReviewResultSnapshot snapshot) {
        var owner = CURRENT_OWNER.get();
        if (owner == null) throw new IllegalStateException(CURRENT_OWNER_MUST_BE_SET);
        var params = buildSnapshotParams(snapshot);
        params.addValue("executionId", snapshot.executionId()).addValue("owner", owner);
        int inserted = jdbc.update(INSERT_SNAPSHOT_SQL, params);
        if (inserted != 1) {
            throw new IllegalStateException(
                    "saveSnapshot failed: execution=" + snapshot.executionId()
                    + " owner=" + owner);
        }
    }

    // ── appendCompletedStageLog ──

    @Override
    public void appendCompletedStageLog(TaskStageLogEntry entry, String stageOwner,
                                         ExecutionStatus currentStatus, String currentStage) {
        int inserted = jdbc.update(INSERT_STAGE_LOG_GUARDED_SQL,
                new MapSqlParameterSource()
                    .addValue("executionId", entry.executionId()).addValue("owner", stageOwner)
                    .addValue("stageName", entry.stageName()).addValue("attempt", entry.attempt())
                    .addValue("eventType", entry.eventType())
                    .addValue("summaryStatus", entry.summaryStatus())
                    .addValue("businessReason", entry.businessReason())
                    .addValue("diagnosticCode", entry.diagnosticCode())
                    .addValue("durationMs", entry.durationMs())
                    .addValue("detailPayload", serialiseDetailPayload(entry.detailPayload()))
                    .addValue("createdAt", toTimestamp(entry.createdAt()))
                    .addValue("guardStatus", currentStatus.name())
                    .addValue("guardCurrentStage", currentStage));
        if (inserted != 1) {
            throw new IllegalStateException(
                    "appendCompletedStageLog guard failed: execution=" + entry.executionId()
                    + " guard=(" + currentStatus + "/" + currentStage + "/" + stageOwner + ")");
        }
    }

    private String serialiseDetailPayload(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) return "{}";
        // Fail-closed: let exception propagate → transaction rollback
        try { return objectMapper.writeValueAsString(payload); }
        catch (JsonProcessingException e) { // only catch checked — rethrow unchecked
            throw new IllegalStateException("Failed to serialise detail_payload", e);
        }
    }

    // ── Load helpers ──

    Optional<LoadedExecutionState> loadExecutionAndTask(String executionId, String owner) {
        var params = Map.of("executionId", executionId, "owner", owner);
        var rows = jdbc.query(LOAD_EXECUTION_TASK_SQL, params, (rs, n) -> {
            var sfSnapshot = jsonToStringMap(rs.getString("sf_snapshot"));
            var task = new ReviewTaskRecord(rs.getString("task_id"), rs.getString("contract_name"), sfSnapshot);
            var exec = new TaskExecutionRecord(rs.getString("execution_id"), rs.getString("task_id"),
                    ExecutionStatus.valueOf(rs.getString("status")), rs.getString("current_stage"),
                    extractVersionRefs(rs),
                    rs.getString("model_profile_code"), rs.getString("provider_type"),
                    rs.getString("model_name"), rs.getString("endpoint_alias"),
                    toInstant(rs.getTimestamp("started_at")), toInstant(rs.getTimestamp("finished_at")));
            return new LoadedExecutionState(task, exec, rs.getString("doc_ref"));
        });
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    ExecutionStatusRow loadStatus(String executionId, String owner) {
        var rows = jdbc.query(LOAD_STATUS_SQL, Map.of("executionId", executionId, "owner", owner),
                (rs, n) -> new ExecutionStatusRow(rs.getString("task_id"), rs.getString("status"),
                        rs.getString("current_stage")));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    // ── Lifecycle overrides ──

    @Override @Transactional
    public TaskExecutionRecord startStage(ExecutionStatus fromStatus, String fromCurrentStage,
            ExecutionStatus toStatus, String toStageName,
            TaskExecutionRecord current, String stageOwner,
            Instant executionStartedAt, Instant stageStartedAt) {
        var up = new MapSqlParameterSource().addValue("executionId", current.executionId())
                .addValue("owner", stageOwner).addValue("status", toStatus.name())
                .addValue("currentStage", toStageName).addValue("startedAt", toTimestamp(executionStartedAt))
                .addValue("finishedAt", null).addValue("clearLease", false)
                .addValue("whereStatus", fromStatus.name()).addValue("whereCurrentStage", fromCurrentStage);
        if (jdbc.update(UPDATE_EXECUTION_SET + LIFECYCLE_NON_TERMINAL_WHERE, up) != 1)
            throw new IllegalStateException("startStage failed: execution=" + current.executionId()
                    + " from=" + fromStatus + "/" + fromCurrentStage);
        var lp = new MapSqlParameterSource().addValue("executionId", current.executionId())
                .addValue("owner", stageOwner).addValue("stageName", toStageName).addValue("attempt", 1)
                .addValue("eventType", "STARTED").addValue("summaryStatus", "RUNNING")
                .addValue("businessReason", null).addValue("diagnosticCode", null)
                .addValue("durationMs", null).addValue("detailPayload", "{}")
                .addValue("createdAt", toTimestamp(stageStartedAt))
                .addValue("guardStatus", toStatus.name()).addValue("guardCurrentStage", toStageName);
        if (jdbc.update(INSERT_STAGE_LOG_GUARDED_SQL, lp) != 1)
            throw new IllegalStateException("startStage log failed: execution=" + current.executionId());
        return current.transitionTo(toStatus, toStageName,
                current.startedAt() != null ? current.startedAt() : executionStartedAt, null);
    }

    @Override @Transactional
    public void failExecution(String targetStageName, ExecutionStatus fromStatus, String fromCurrentStage,
            TaskExecutionRecord current, String stageOwner, String taskId, String executionId,
            String businessReason, Instant failedAt) {
        var s1 = new MapSqlParameterSource().addValue("executionId", executionId).addValue("owner", stageOwner)
                .addValue("status", "FAILED").addValue("currentStage", targetStageName)
                .addValue("startedAt", null).addValue("finishedAt", toTimestamp(failedAt))
                .addValue("clearLease", false)
                .addValue("whereStatus", fromStatus.name()).addValue("whereCurrentStage", fromCurrentStage);
        if (jdbc.update(UPDATE_EXECUTION_SET + LIFECYCLE_NON_TERMINAL_WHERE, s1) != 1)
            throw new IllegalStateException("failExecution step1: execution=" + executionId);
        var s2 = new MapSqlParameterSource().addValue("executionId", executionId).addValue("owner", stageOwner)
                .addValue("stageName", targetStageName).addValue("attempt", 1)
                .addValue("eventType", "FAILED").addValue("summaryStatus", "FAILED")
                .addValue("businessReason", businessReason).addValue("diagnosticCode", null)
                .addValue("durationMs", null).addValue("detailPayload", "{}")
                .addValue("createdAt", toTimestamp(failedAt))
                .addValue("guardStatus", "FAILED").addValue("guardCurrentStage", targetStageName);
        if (jdbc.update(INSERT_STAGE_LOG_GUARDED_SQL, s2) != 1)
            throw new IllegalStateException("failExecution step2: execution=" + executionId);
        var s3 = new MapSqlParameterSource().addValue("executionId", executionId).addValue("owner", stageOwner)
                .addValue("status", "FAILED").addValue("currentStage", targetStageName)
                .addValue("startedAt", null).addValue("finishedAt", toTimestamp(failedAt))
                .addValue("clearLease", true)
                .addValue("whereStatus", "FAILED").addValue("whereCurrentStage", targetStageName);
        if (jdbc.update(UPDATE_EXECUTION_SET + LIFECYCLE_WHERE, s3) != 1)
            throw new IllegalStateException("failExecution step3: execution=" + executionId);
    }

    @Transactional
    void failExecutionRaw(String targetStageName, ExecutionStatus fromStatus, String fromCurrentStage,
            String stageOwner, String taskId, String executionId,
            String businessReason, Instant failedAt) {
        var s1 = new MapSqlParameterSource().addValue("executionId", executionId).addValue("owner", stageOwner)
                .addValue("status", "FAILED").addValue("currentStage", targetStageName)
                .addValue("startedAt", null).addValue("finishedAt", toTimestamp(failedAt))
                .addValue("clearLease", false)
                .addValue("whereStatus", fromStatus.name()).addValue("whereCurrentStage", fromCurrentStage);
        if (jdbc.update(UPDATE_EXECUTION_SET + LIFECYCLE_NON_TERMINAL_WHERE, s1) != 1)
            throw new IllegalStateException("failExRaw step1: execution=" + executionId);
        var s2 = new MapSqlParameterSource().addValue("executionId", executionId).addValue("owner", stageOwner)
                .addValue("stageName", targetStageName).addValue("attempt", 1)
                .addValue("eventType", "FAILED").addValue("summaryStatus", "FAILED")
                .addValue("businessReason", businessReason).addValue("diagnosticCode", null)
                .addValue("durationMs", null).addValue("detailPayload", "{}")
                .addValue("createdAt", toTimestamp(failedAt))
                .addValue("guardStatus", "FAILED").addValue("guardCurrentStage", targetStageName);
        if (jdbc.update(INSERT_STAGE_LOG_GUARDED_SQL, s2) != 1)
            throw new IllegalStateException("failExRaw step2: execution=" + executionId);
        var s3 = new MapSqlParameterSource().addValue("executionId", executionId).addValue("owner", stageOwner)
                .addValue("status", "FAILED").addValue("currentStage", targetStageName)
                .addValue("startedAt", null).addValue("finishedAt", toTimestamp(failedAt))
                .addValue("clearLease", true)
                .addValue("whereStatus", "FAILED").addValue("whereCurrentStage", targetStageName);
        if (jdbc.update(UPDATE_EXECUTION_SET + LIFECYCLE_WHERE, s3) != 1)
            throw new IllegalStateException("failExRaw step3: execution=" + executionId);
    }

    @Override @Transactional
    public void completeExecution(TaskExecutionRecord composingExecution, ReviewResultSnapshot snapshot,
            String stageOwner, Instant composingStartedAt, Instant finishedAt) {
        var eid = composingExecution.executionId();
        var term = snapshot.status() == SnapshotStatus.PARTIAL_SUCCESS ? "PARTIAL_SUCCESS" : "SUCCESS";
        // Step 1: snapshot (guard COMPOSING + owner)
        var sp = buildSnapshotParams(snapshot);
        sp.addValue("executionId", eid).addValue("owner", stageOwner);
        if (jdbc.update(INSERT_SNAPSHOT_SQL, sp) != 1)
            throw new IllegalStateException("complete snap: execution=" + eid);
        // Step 2: COMPLETED log
        var lp = new MapSqlParameterSource().addValue("executionId", eid).addValue("owner", stageOwner)
                .addValue("stageName", "COMPOSING").addValue("attempt", 1)
                .addValue("eventType", "COMPLETED").addValue("summaryStatus", term)
                .addValue("businessReason", null).addValue("diagnosticCode", null)
                .addValue("durationMs", Duration.between(composingStartedAt, finishedAt).toMillis())
                .addValue("detailPayload", "{}").addValue("createdAt", toTimestamp(finishedAt))
                .addValue("guardStatus", "COMPOSING").addValue("guardCurrentStage", "COMPOSING");
        if (jdbc.update(INSERT_STAGE_LOG_GUARDED_SQL, lp) != 1)
            throw new IllegalStateException("complete log: execution=" + eid);
        // Step 3: terminal update
        var tp = new MapSqlParameterSource().addValue("executionId", eid).addValue("owner", stageOwner)
                .addValue("status", term).addValue("currentStage", term)
                .addValue("startedAt", null).addValue("finishedAt", toTimestamp(finishedAt))
                .addValue("clearLease", true)
                .addValue("whereStatus", "COMPOSING").addValue("whereCurrentStage", "COMPOSING");
        if (jdbc.update(UPDATE_EXECUTION_SET + LIFECYCLE_WHERE, tp) != 1)
            throw new IllegalStateException("complete term: execution=" + eid);
    }

    // ── Helpers ──

    private MapSqlParameterSource buildSnapshotParams(ReviewResultSnapshot snap) {
        try {
            return new MapSqlParameterSource()
                    .addValue("taskId", snap.taskId()).addValue("executionId", snap.executionId())
                    .addValue("status", snap.status().name())
                    .addValue("summary", objectMapper.writeValueAsString(snap.summary()))
                    .addValue("reviewCompleteness", objectMapper.writeValueAsString(snap.reviewCompleteness()))
                    .addValue("pointResults", objectMapper.writeValueAsString(snap.pointResults()))
                    .addValue("findings", objectMapper.writeValueAsString(snap.findings()))
                    .addValue("diagnostics", objectMapper.writeValueAsString(snap.diagnostics()))
                    .addValue("sourceAnchors", objectMapper.writeValueAsString(snap.sourceAnchors()))
                    .addValue("structuredFields", objectMapper.writeValueAsString(snap.structuredFieldsSnapshot()))
                    .addValue("enabledPoints", objectMapper.writeValueAsString(snap.enabledReviewPointsSnapshot()))
                    .addValue("disabledPoints", objectMapper.writeValueAsString(snap.disabledReviewPointsSnapshot()))
                    .addValue("contractTypeProfileVersion", snap.contractTypeProfileVersion())
                    .addValue("ruleSetVersion", snap.ruleSetVersion())
                    .addValue("reviewBudgetProfileVersion", snap.reviewBudgetProfileVersion())
                    .addValue("modelProfileVersion", snap.modelProfileVersion())
                    .addValue("parserVersion", snap.parserVersion()).addValue("promptVersion", snap.promptVersion())
                    .addValue("schemaVersion", snap.schemaVersion())
                    .addValue("patternLibraryVersion", snap.patternLibraryVersion())
                    .addValue("fieldLexiconVersion", snap.fieldLexiconVersion())
                    .addValue("evidenceSelectorVersion", snap.evidenceSelectorVersion())
                    .addValue("createdAt", toTimestamp(snap.createdAt()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("snapshot JSON: execution=" + snap.executionId(), e);
        }
    }

    private Map<String, String> jsonToStringMap(String json) {
        try { return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {}); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Failed to parse sf_snapshot", e); }
    }

    private VersionReferences extractVersionRefs(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new VersionReferences(rs.getString("contract_type_profile_version"), rs.getString("rule_set_version"),
                rs.getString("review_budget_profile_version"), rs.getString("model_config_version"),
                rs.getString("parser_version"), rs.getString("prompt_version"),
                rs.getString("schema_version"), rs.getString("pattern_library_version"),
                rs.getString("field_lexicon_version"), rs.getString("evidence_selector_version"));
    }

    private static Timestamp toTimestamp(Instant i) { return i == null ? null : Timestamp.from(i); }
    static Instant toInstant(Timestamp ts) { return ts == null ? null : ts.toInstant(); }

    record LoadedExecutionState(ReviewTaskRecord task, TaskExecutionRecord execution, String documentReference) {}
    record ExecutionStatusRow(String taskId, String status, String currentStage) {}
}
