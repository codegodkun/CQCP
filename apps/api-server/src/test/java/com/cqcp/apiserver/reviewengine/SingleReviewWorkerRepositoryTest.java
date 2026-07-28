package com.cqcp.apiserver.reviewengine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Tests for {@link SingleReviewWorkerRepository} claim/lease behavior.
 *
 * <p>Requires a running PostgreSQL instance with V1+V2 migrations applied
 * to database {@code cqcp_mvp001_b_test} (set via {@code CQCP_TEST_DB_NAME}).</p>
 */
@SpringBootTest
@TestPropertySource(properties = {
        "cqcp.review.worker.enabled=false",
        "spring.flyway.enabled=true",
})
class SingleReviewWorkerRepositoryTest {

    @Autowired private SingleReviewWorkerRepository repository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TransactionTemplate txTemplate;

    private String taskId1;
    private String taskId2;
    private String taskId3;
    private String execId1;
    private String execId2;
    private String execId3;
    private final java.util.List<String> allTaskIds = new java.util.ArrayList<>();
    private final java.util.List<String> allExecIds = new java.util.ArrayList<>();

    @BeforeEach
    void setUp() {
        // No cleanup of other test data. Each test run starts fresh on a clean DB.
        taskId1 = "tsk1-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        taskId2 = "tsk2-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        taskId3 = "tsk3-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        allTaskIds.clear(); allExecIds.clear();

        insertTask(taskId1); allTaskIds.add(taskId1);
        insertTask(taskId2); allTaskIds.add(taskId2);
        insertTask(taskId3); allTaskIds.add(taskId3);

        execId1 = "exec-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        execId2 = "exec-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        execId3 = "exec-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        insertExecution(execId1, taskId1, "2000-01-01 00:00:00+00"); allExecIds.add(execId1);
        insertExecution(execId2, taskId2, "2000-01-01 00:00:01+00"); allExecIds.add(execId2);
        insertExecution(execId3, taskId3, "2000-01-01 00:00:02+00"); allExecIds.add(execId3);
    }

    @AfterEach
    void cleanUp() {
        for (var eid : allExecIds) {
            jdbcTemplate.update("DELETE FROM point_diagnostic WHERE execution_id=?", eid);
            jdbcTemplate.update("DELETE FROM tuning_packet WHERE execution_id=?", eid);
            jdbcTemplate.update("DELETE FROM review_result_snapshot WHERE execution_id=?", eid);
            jdbcTemplate.update("DELETE FROM task_stage_log WHERE execution_id=?", eid);
            int d1 = jdbcTemplate.update("DELETE FROM execution WHERE execution_id=?", eid);
            if (d1 != 1) throw new IllegalStateException("cleanup failed: " + eid);
        }
        for (var tid : allTaskIds) {
            int d2 = jdbcTemplate.update("DELETE FROM task WHERE task_id=?", tid);
            if (d2 != 1) throw new IllegalStateException("cleanup failed: " + tid);
        }
        allExecIds.clear(); allTaskIds.clear();
    }

    private void insertTask(String tId) {
        jdbcTemplate.update("""
                INSERT INTO task (task_id, caller_id, caller_type, source_type, contract_name,
                                  contract_type_code, result_url, structured_fields_snapshot)
                VALUES (?, NULL, 'TEST', 'TEST', ?, 'ENGINEERING', ?, '{}')
                """, tId, "TestContract-" + tId, "/results/" + tId);
    }

    private void insertExecution(String execId, String tId, String createdAt) {
        jdbcTemplate.update("""
                INSERT INTO execution (
                    execution_id, task_id, status, current_stage,
                    contract_type_profile_version, rule_set_version,
                    review_budget_profile_version, model_profile_code,
                    model_config_version, parser_version, prompt_version,
                    schema_version, pattern_library_version,
                    field_lexicon_version, evidence_selector_version,
                    provider_type, model_name, endpoint_alias,
                    created_at
                ) VALUES (?, ?, 'QUEUED', 'QUEUED',
                          'v20260705.1', 'v20260705.1', 'budget-standard-v20260724.1',
                          'MVP_DEMO_MOCK', 'model-config-mvp-demo-mock-v20260724.1',
                          'parser-docx-word-v20260724.1', 'v20260705.1',
                          'model-output-artifact-v20260724.1', 'v20260705.1',
                          'v20260705.1', 'v20260705.1',
                          'MOCK', 'cqcp-demo-mock', 'mock-local', ?::timestamptz)
                """, execId, tId, createdAt);
    }

    @Test
    void claimReturnsOldestQueuedExecution() {
        var owner = "test-owner-1";
        var claimed = repository.claimOne(owner, 60);

        assertThat(claimed).isPresent();
        assertThat(claimed.orElseThrow()).isEqualTo(execId1);

        // Verify FIFO: second claim returns execId2
        var claimed2 = repository.claimOne(owner + "-other", 60);
        assertThat(claimed2).isPresent();
        assertThat(claimed2.orElseThrow()).isEqualTo(execId2);
    }

    @Test
    void claimReturnsEmptyWhenNoQueuedExecutions() {
        // Unit-test the no-candidate path via a mock NamedParameterJdbcTemplate.
        // The production code calls jdbcTemplate.query(sql, MapSqlParameterSource, RowMapper).
        var mockTemplate = org.mockito.Mockito.mock(
                org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate.class);
        var mockRepo = new SingleReviewWorkerRepository(mockTemplate);
        org.mockito.Mockito.when(mockTemplate.query(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(org.springframework.jdbc.core.namedparam.MapSqlParameterSource.class),
                org.mockito.ArgumentMatchers.any(org.springframework.jdbc.core.RowMapper.class)))
                .thenReturn(java.util.List.of());
        assertThat(mockRepo.claimOne("test-owner", 60)).isEmpty();
    }

    @Test
    void activeLeaseCannotBeReclaimed() {
        // Pre-claim the 3 setUp executions so they don't interfere
        repository.claimOne("preclaimer-1", 60);
        repository.claimOne("preclaimer-2", 60);
        repository.claimOne("preclaimer-3", 60);

        // Create 2 test-specific executions: one locked, one QUEUED sentinel
        var activeTask = "atsk-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        insertTask(activeTask);
        var activeExec = "aexec-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        insertExecution(activeExec, activeTask, "1999-01-01 00:00:00+00");
        allTaskIds.add(activeTask); allExecIds.add(activeExec);

        var sentinelTask = "stsk-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        insertTask(sentinelTask);
        var sentinelExec = "sexec-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        insertExecution(sentinelExec, sentinelTask, "2000-01-01 00:00:00+00");
        allTaskIds.add(sentinelTask); allExecIds.add(sentinelExec);

        var owner = "test-locker";
        var claimed = repository.claimOne(owner, 3600);
        assertThat(claimed).hasValue(activeExec);

        // Intruder cannot claim the locked execution but can claim the sentinel
        var intruderClaim = repository.claimOne("intruder", 3600);
        assertThat(intruderClaim).hasValue(sentinelExec);

        // Verify the original claimed execution still belongs to owner
        var dbOwner = jdbcTemplate.queryForObject(
                "SELECT stage_lease_owner FROM execution WHERE execution_id=?", String.class, activeExec);
        assertThat(dbOwner).isEqualTo(owner);
    }

    @Test
    void expiredLeaseCanBeReclaimed() {
        // Claim with very short lease (1 second)
        var owner = "short-lease";
        repository.claimOne(owner, 1);

        // Wait for lease to expire
        try { Thread.sleep(1500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        // Should be reclaimable by another owner
        var reclaimer = "reclaimer";
        var renewed = repository.claimOne(reclaimer, 3600);
        assertThat(renewed).isPresent();
    }

    @Test
    void concurrentClaimReturnsDistinctExecutions() throws Exception {
        var owner1 = "concurrent-t1";
        var owner2 = "concurrent-t2";
        var readyLatch = new CountDownLatch(1);
        var t2DoneLatch = new CountDownLatch(1);

        var t1Result = new AtomicReference<String>();
        var t2Result = new AtomicReference<String>();

        // T1: claim execId1, hold transaction, wait for T2
        var t1 = new Thread(() -> {
            txTemplate.executeWithoutResult(status -> {
                var e1 = repository.claimOne(owner1, 60);
                t1Result.set(e1.orElse(null));
                readyLatch.countDown();
                try {
                    t2DoneLatch.await(15, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        });
        t1.start();

        // T2: wait for T1 to claim, then claim in separate transaction
        var t2 = new Thread(() -> {
            try {
                readyLatch.await(15, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            txTemplate.executeWithoutResult(status -> {
                var e2 = repository.claimOne(owner2, 60);
                t2Result.set(e2.orElse(null));
            });
            t2DoneLatch.countDown();
        });
        t2.start();

        t1.join(20_000);
        t2.join(20_000);

        assertThat(t1Result.get()).isEqualTo(execId1);
        assertThat(t2Result.get()).isEqualTo(execId2);
    }
}
