package com.cqcp.apiserver.reviewengine;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = "cqcp.review.worker.enabled=false")
class ReviewTaskListRepositoryTest {

    @Autowired
    private ReviewTaskListRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String taskId;
    private String completedExecutionId;
    private String processingExecutionId;

    @BeforeEach
    void setUp() {
        var suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        taskId = "list-task-" + suffix;
        completedExecutionId = "list-done-" + suffix;
        processingExecutionId = "list-work-" + suffix;

        jdbcTemplate.update("""
                INSERT INTO task (
                    task_id, caller_type, source_type, contract_name,
                    contract_type_code, result_url, structured_fields_snapshot
                ) VALUES (?, 'TEST', 'TEST', ?, 'ENGINEERING', ?, '{}'::jsonb)
                """, taskId, "分页合同-" + suffix, "/review/results/" + taskId);

        insertExecution(
                completedExecutionId,
                "PARTIAL_SUCCESS",
                "PARTIAL_SUCCESS",
                Instant.parse("2026-07-28T01:00:00Z"));
        insertExecution(
                processingExecutionId,
                "REVIEWING_RULES",
                "REVIEWING_RULES",
                Instant.parse("2026-07-28T02:00:00Z"));

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
                    ?, ?, 'PARTIAL_SUCCESS',
                    '{"plannedPointCount":9,"passCount":3,"errorCount":1,"warningCount":2,"notConcludedCount":2,"skippedCount":1}'::jsonb,
                    '{}'::jsonb, '[]'::jsonb, '[]'::jsonb, '[]'::jsonb,
                    '[]'::jsonb, '{}'::jsonb, '[]'::jsonb, '[]'::jsonb,
                    'v20260705.1', 'v20260705.1', 'budget-standard-v20260724.1',
                    'model-config-mvp-demo-mock-v20260724.1',
                    'parser-docx-word-v20260724.1', 'v20260705.1',
                    'model-output-artifact-v20260724.1', 'v20260705.1',
                    'v20260705.1', 'v20260705.1', NOW()
                )
                """, taskId, completedExecutionId);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM review_result_snapshot WHERE task_id = ?", taskId);
        jdbcTemplate.update("DELETE FROM execution WHERE task_id = ?", taskId);
        jdbcTemplate.update("DELETE FROM task WHERE task_id = ?", taskId);
    }

    @Test
    void statusGroupsAndStatisticsRemainBoundToTheSameExecution() {
        var completed = repository.findPage(
                0,
                20,
                ReviewTaskListModels.StatusGroup.COMPLETED,
                null);
        var processing = repository.findPage(
                0,
                20,
                ReviewTaskListModels.StatusGroup.PROCESSING,
                null);

        assertThat(completed.items())
                .filteredOn(item -> item.taskId().equals(taskId))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.executionId()).isEqualTo(completedExecutionId);
                    assertThat(item.resultUrl()).isEqualTo(
                            "/review/results/" + taskId + "?executionId=" + completedExecutionId);
                    assertThat(item.status()).isEqualTo("PARTIAL_SUCCESS");
                    assertThat(item.resultStatistics()).isNotNull();
                    assertThat(item.resultStatistics().plannedPointCount()).isEqualTo(9);
                    assertThat(item.resultStatistics().passCount()).isEqualTo(3);
                    assertThat(item.resultStatistics().notConcludedCount()).isEqualTo(2);
                    assertThat(item.modelProfile().profileCode()).isEqualTo("MVP_DEMO_MOCK");
                    assertThat(item.modelProfile().displayName()).isEqualTo("MVP Demo Mock");
                });
        assertThat(processing.items())
                .filteredOn(item -> item.taskId().equals(taskId))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.executionId()).isEqualTo(processingExecutionId);
                    assertThat(item.resultUrl()).isEqualTo(
                            "/review/results/" + taskId + "?executionId=" + processingExecutionId);
                    assertThat(item.resultStatistics()).isNull();
                });
    }

    @Test
    void searchIsParameterizedAndPaginationMetadataIsStable() {
        var match = repository.findPage(0, 1, null, "%分页合同%");

        assertThat(match.items()).hasSize(1);
        assertThat(match.items().getFirst().executionId()).isEqualTo(processingExecutionId);
        assertThat(match.totalElements()).isEqualTo(2);
        assertThat(match.totalPages()).isEqualTo(2);
        assertThat(match.page()).isZero();
        assertThat(match.size()).isEqualTo(1);
    }

    private void insertExecution(
            String executionId,
            String status,
            String stage,
            Instant createdAt) {
        jdbcTemplate.update("""
                INSERT INTO execution (
                    execution_id, task_id, status, current_stage,
                    contract_type_profile_version, rule_set_version,
                    review_budget_profile_version, model_profile_code,
                    model_config_version, parser_version, prompt_version,
                    schema_version, pattern_library_version,
                    field_lexicon_version, evidence_selector_version,
                    provider_type, model_name, endpoint_alias,
                    created_at, updated_at
                ) VALUES (
                    ?, ?, ?, ?,
                    'v20260705.1', 'v20260705.1',
                    'budget-standard-v20260724.1', 'MVP_DEMO_MOCK',
                    'model-config-mvp-demo-mock-v20260724.1',
                    'parser-docx-word-v20260724.1', 'v20260705.1',
                    'model-output-artifact-v20260724.1', 'v20260705.1',
                    'v20260705.1', 'v20260705.1',
                    'MOCK', 'cqcp-demo-mock', 'mock-local',
                    ?, ?
                )
                """,
                executionId,
                taskId,
                status,
                stage,
                java.sql.Timestamp.from(createdAt),
                java.sql.Timestamp.from(createdAt));
    }
}
