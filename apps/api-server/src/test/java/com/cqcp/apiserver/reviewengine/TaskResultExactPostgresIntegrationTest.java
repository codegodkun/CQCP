package com.cqcp.apiserver.reviewengine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = "cqcp.review.worker.enabled=false")
class TaskResultExactPostgresIntegrationTest {

    @Autowired
    private TaskResultQueryService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String taskId;
    private String firstExecutionId;
    private String secondExecutionId;

    @BeforeEach
    void setUp() {
        var suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        taskId = "exact-task-" + suffix;
        firstExecutionId = "exact-old-" + suffix;
        secondExecutionId = "exact-new-" + suffix;
        jdbcTemplate.update("""
                INSERT INTO task (
                    task_id, caller_type, source_type, contract_name,
                    contract_type_code, result_url, structured_fields_snapshot
                ) VALUES (?, 'TEST', 'TEST', 'Exact Result', 'ENGINEERING', ?, '{}'::jsonb)
                """, taskId, "/review/results/" + taskId);
        insertExecution(firstExecutionId, Instant.parse("2026-07-28T01:00:00Z"));
        insertExecution(secondExecutionId, Instant.parse("2026-07-28T02:00:00Z"));
        insertSnapshot(firstExecutionId, 1, Instant.parse("2026-07-28T01:00:01Z"));
        insertSnapshot(secondExecutionId, 2, Instant.parse("2026-07-28T02:00:01Z"));
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM review_result_snapshot WHERE task_id = ?", taskId);
        jdbcTemplate.update("DELETE FROM execution WHERE task_id = ?", taskId);
        jdbcTemplate.update("DELETE FROM task WHERE task_id = ?", taskId);
    }

    @Test
    void exactHistoricalExecutionNeverFallsBackToLatestSnapshot() {
        var exactOld = service.getResult(taskId, firstExecutionId);
        var exactNew = service.getResult(taskId, secondExecutionId);
        var latest = service.getResult(taskId);

        assertThat(exactOld.executionId()).isEqualTo(firstExecutionId);
        assertThat(exactOld.summary().plannedPointCount()).isEqualTo(1);
        assertThat(exactNew.executionId()).isEqualTo(secondExecutionId);
        assertThat(exactNew.summary().plannedPointCount()).isEqualTo(2);
        assertThat(latest.executionId()).isEqualTo(secondExecutionId);

        assertThatThrownBy(() -> service.getResult(taskId, "missing-execution"))
                .isInstanceOf(TaskResultNotFoundException.class);
    }

    private void insertExecution(String executionId, Instant createdAt) {
        jdbcTemplate.update("""
                INSERT INTO execution (
                    execution_id, task_id, status, current_stage,
                    contract_type_profile_version, rule_set_version,
                    review_budget_profile_version, model_profile_code,
                    model_config_version, parser_version, prompt_version,
                    schema_version, pattern_library_version,
                    field_lexicon_version, evidence_selector_version,
                    provider_type, model_name, endpoint_alias,
                    finished_at, created_at, updated_at
                ) VALUES (
                    ?, ?, 'SUCCESS', 'SUCCESS',
                    'v20260705.1', 'v20260705.1',
                    'budget-standard-v20260724.1', 'MVP_DEMO_MOCK',
                    'model-config-mvp-demo-mock-v20260724.1',
                    'parser-docx-word-v20260724.1', 'v20260705.1',
                    'model-output-artifact-v20260724.1', 'v20260705.1',
                    'v20260705.1', 'v20260705.1',
                    'MOCK', 'cqcp-demo-mock', 'mock-local',
                    ?, ?, ?
                )
                """,
                executionId,
                taskId,
                java.sql.Timestamp.from(createdAt),
                java.sql.Timestamp.from(createdAt),
                java.sql.Timestamp.from(createdAt));
    }

    private void insertSnapshot(String executionId, int plannedPointCount, Instant createdAt) {
        var summary = """
                {"plannedPointCount":%d,"passCount":%d,"errorCount":0,
                 "warningCount":0,"notConcludedCount":0,"skippedCount":0}
                """.formatted(plannedPointCount, plannedPointCount);
        jdbcTemplate.update("""
                INSERT INTO review_result_snapshot (
                    task_id, execution_id, status, summary, review_completeness,
                    point_results, findings, diagnostics, source_anchors,
                    structured_fields_snapshot, enabled_review_points_snapshot,
                    disabled_review_points_snapshot,
                    contract_type_profile_version, rule_set_version,
                    review_budget_profile_version, model_profile_version,
                    parser_version, prompt_version, schema_version,
                    pattern_library_version, field_lexicon_version,
                    evidence_selector_version, created_at
                ) VALUES (
                    ?, ?, 'SUCCESS', ?::jsonb,
                    '{"reviewCoverageStatus":"FULL_REVIEWED","executablePointCount":0,
                      "concludedPointCount":0,"notConcludedPointCount":0,
                      "concludedCoverageRate":1,"confidenceLevel":"HIGH"}'::jsonb,
                    '[]'::jsonb, '[]'::jsonb, '[]'::jsonb, '[]'::jsonb,
                    '{}'::jsonb, '[]'::jsonb, '[]'::jsonb,
                    'v20260705.1', 'v20260705.1',
                    'budget-standard-v20260724.1',
                    'model-config-mvp-demo-mock-v20260724.1',
                    'parser-docx-word-v20260724.1', 'v20260705.1',
                    'model-output-artifact-v20260724.1', 'v20260705.1',
                    'v20260705.1', 'v20260705.1', ?
                )
                """,
                taskId,
                executionId,
                summary,
                java.sql.Timestamp.from(createdAt));
    }
}
