package com.cqcp.apiserver.reviewengine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "cqcp.review.worker.enabled=false",
        "spring.flyway.enabled=true",
})
class ReviewExecutionStatusRepositoryTest {

    @Autowired private ReviewExecutionStatusRepository repository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @SpyBean private JdbcTemplate jdbcTemplateSpy;

    private String taskId;
    private String executionId;

    @BeforeEach
    void setUp() {
        taskId = "crt-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        executionId = "cre-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        jdbcTemplate.update("INSERT INTO task (task_id, caller_type, source_type, contract_name, " +
                "contract_type_code, result_url, structured_fields_snapshot) " +
                "VALUES (?, 'TEST', 'TEST', ?, 'ENGINEERING', ?, '{}')",
                taskId, "TestC-" + taskId, "/results/" + taskId);

        jdbcTemplate.update("INSERT INTO execution (execution_id, task_id, status, current_stage, " +
                "contract_type_profile_version, rule_set_version, review_budget_profile_version, " +
                "model_profile_code, model_config_version, parser_version, prompt_version, " +
                "schema_version, pattern_library_version, field_lexicon_version, evidence_selector_version, " +
                "provider_type, model_name, endpoint_alias) " +
                "VALUES (?, ?, 'QUEUED', 'QUEUED', " +
                "'v20260705.1', 'v20260705.1', 'budget-standard-v20260724.1', " +
                "'MVP_DEMO_MOCK', 'model-config-mvp-demo-mock-v20260724.1', " +
                "'parser-docx-word-v20260724.1', 'v20260705.1', 'model-output-artifact-v20260724.1', " +
                "'v20260705.1', 'v20260705.1', 'v20260705.1', " +
                "'MOCK', 'cqcp-demo-mock', 'mock-local')",
                executionId, taskId);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM point_diagnostic WHERE execution_id=?", executionId);
        jdbcTemplate.update("DELETE FROM tuning_packet WHERE execution_id=?", executionId);
        jdbcTemplate.update("DELETE FROM review_result_snapshot WHERE execution_id=?", executionId);
        jdbcTemplate.update("DELETE FROM task_stage_log WHERE execution_id=?", executionId);
        jdbcTemplate.update("DELETE FROM execution WHERE execution_id=?", executionId);
        jdbcTemplate.update("DELETE FROM execution WHERE task_id=?", taskId);
        jdbcTemplate.update("DELETE FROM task WHERE task_id=?", taskId);
    }

    // ── AC6: SQL constrains both IDs ──

    @Test
    void ac6_findStatus_returnsRow() {
        var result = repository.findStatus(taskId, executionId);
        assertThat(result).isPresent();
        var row = result.orElseThrow();
        assertThat(row.taskId()).isEqualTo(taskId);
        assertThat(row.executionId()).isEqualTo(executionId);
    }

    @Test
    void ac6_crossTaskExecution_returnsEmpty() {
        var otherTask = "other-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        var result = repository.findStatus(otherTask, executionId);
        assertThat(result).isEmpty();
    }

    @Test
    void ac6_nonexistentTask_returnsEmpty() {
        assertThat(repository.findStatus("no-such-task", executionId)).isEmpty();
    }

    @Test
    void ac6_nonexistentExecution_returnsEmpty() {
        assertThat(repository.findStatus(taskId, "no-such-exec")).isEmpty();
    }

    // ── AC8: snapshotAvailable ──

    @Test
    void ac8_snapshotWithoutSnapshot_returnsAvailableFalse() {
        var row = repository.findStatus(taskId, executionId).orElseThrow();
        assertThat(row.snapshotExecutionId()).isNull();
    }

    @Test
    void ac8_snapshotWithSnapshot_returnsAvailableTrue() {
        jdbcTemplate.update("UPDATE execution SET status='COMPOSING', current_stage='COMPOSING' WHERE execution_id=?", executionId);
        jdbcTemplate.update("INSERT INTO review_result_snapshot (task_id, execution_id, " +
                "status, summary, review_completeness, point_results, findings, diagnostics, " +
                "source_anchors, structured_fields_snapshot, " +
                "enabled_review_points_snapshot, disabled_review_points_snapshot, " +
                "contract_type_profile_version, rule_set_version, review_budget_profile_version, " +
                "model_profile_version, parser_version, prompt_version, schema_version, " +
                "pattern_library_version, field_lexicon_version, evidence_selector_version, created_at) " +
                "VALUES (?, ?, 'SUCCESS', '{}'::jsonb, '{}'::jsonb, '[]'::jsonb, '[]'::jsonb, " +
                "'[]'::jsonb, '[]'::jsonb, '{}'::jsonb, '[]'::jsonb, '[]'::jsonb, " +
                "'v20260705.1','v20260705.1','budget-standard-v20260724.1'," +
                "'model-config-mvp-demo-mock-v20260724.1'," +
                "'parser-docx-word-v20260724.1','v20260705.1'," +
                "'model-output-artifact-v20260724.1','v20260705.1'," +
                "'v20260705.1','v20260705.1', NOW())",
                taskId, executionId);

        var row = repository.findStatus(taskId, executionId).orElseThrow();
        assertThat(row.snapshotExecutionId()).isEqualTo(executionId);
    }

    // ── AC16: read-only ──

    @Test
    void ac16_repositoryOnlySelects() {
        // Clear invocations from setup
        org.mockito.Mockito.clearInvocations(jdbcTemplateSpy);

        repository.findStatus(taskId, executionId);

        verify(jdbcTemplateSpy, times(1))
                .query(anyString(), any(RowMapper.class), anyString(), anyString());
        verify(jdbcTemplateSpy, never()).update(anyString());
        verify(jdbcTemplateSpy, never()).update(anyString(), (Object[]) any());
        verify(jdbcTemplateSpy, never()).batchUpdate(anyString(), any(List.class));
        verify(jdbcTemplateSpy, never()).execute(anyString());
    }

    // ── resultUrl ──

    @Test
    void resultUrlFromTaskTable() {
        var row = repository.findStatus(taskId, executionId).orElseThrow();
        assertThat(row.resultUrl()).isEqualTo("/results/" + taskId);
    }

    // ── AC9: superseded ──

    @Test
    void ac9_supersededFields() {
        // Need a real execution for the FK reference
        var supersededExec = "crs-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        jdbcTemplate.update("INSERT INTO execution (execution_id, task_id, status, current_stage, " +
                "contract_type_profile_version, rule_set_version, review_budget_profile_version, " +
                "model_profile_code, model_config_version, parser_version, prompt_version, " +
                "schema_version, pattern_library_version, field_lexicon_version, evidence_selector_version, " +
                "provider_type, model_name, endpoint_alias) " +
                "VALUES (?,?,'SUCCESS','SUCCESS','v20260705.1','v20260705.1'," +
                "'budget-standard-v20260724.1','MVP_DEMO_MOCK','model-config-mvp-demo-mock-v20260724.1'," +
                "'parser-docx-word-v20260724.1','v20260705.1','model-output-artifact-v20260724.1'," +
                "'v20260705.1','v20260705.1','v20260705.1','MOCK','cqcp-demo-mock','mock-local')",
                supersededExec, taskId);

        jdbcTemplate.update("UPDATE execution SET status='COMPOSING', current_stage='COMPOSING' WHERE execution_id=?", executionId);
        jdbcTemplate.update("INSERT INTO review_result_snapshot (task_id, execution_id, " +
                "superseded_by_execution_id, superseded_reason, status, summary, review_completeness, " +
                "point_results, findings, diagnostics, source_anchors, structured_fields_snapshot, " +
                "enabled_review_points_snapshot, disabled_review_points_snapshot, " +
                "contract_type_profile_version, rule_set_version, review_budget_profile_version, " +
                "model_profile_version, parser_version, prompt_version, schema_version, " +
                "pattern_library_version, field_lexicon_version, evidence_selector_version, created_at) " +
                "VALUES (?, ?, ?, ?, 'SUCCESS', '{}'::jsonb, '{}'::jsonb, '[]'::jsonb, '[]'::jsonb, " +
                "'[]'::jsonb, '[]'::jsonb, '{}'::jsonb, '[]'::jsonb, '[]'::jsonb, " +
                "'v20260705.1','v20260705.1','budget-standard-v20260724.1'," +
                "'model-config-mvp-demo-mock-v20260724.1'," +
                "'parser-docx-word-v20260724.1','v20260705.1'," +
                "'model-output-artifact-v20260724.1','v20260705.1'," +
                "'v20260705.1','v20260705.1', NOW())",
                taskId, executionId, supersededExec, "MANUAL_RERUN");

        var row = repository.findStatus(taskId, executionId).orElseThrow();
        assertThat(row.supersededByExecutionId()).isEqualTo(supersededExec);
        assertThat(row.supersededReason()).isEqualTo("MANUAL_RERUN");
    }
}
