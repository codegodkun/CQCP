package com.cqcp.apiserver.reviewengine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "cqcp.review.worker.enabled=false",
        "spring.flyway.enabled=true",
})
class JdbcTaskExecutionPersistenceTest {

    @Autowired private JdbcTaskExecutionPersistence persistence;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-07-01T10:00:00Z"), ZoneOffset.UTC);

    private String taskId;
    private String executionId;
    private String owner;
    private TaskExecutionRecord execRecord;

    @BeforeEach
    void setUp() {
        // No cleanup of other test data. Each test creates fresh IDs and @AfterEach cleans only its own.
        taskId = "lc-t-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        executionId = "lc-e-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        owner = "lc-o-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        jdbcTemplate.update("""
                INSERT INTO task (task_id, caller_id, caller_type, source_type, contract_name,
                                  contract_type_code, result_url, structured_fields_snapshot)
                VALUES (?, NULL, 'TEST', 'TEST', ?, 'ENGINEERING', ?, '{}')
                """, taskId, "Test-" + taskId, "/r/" + taskId);

        jdbcTemplate.update("""
                INSERT INTO execution (execution_id, task_id, status, current_stage,
                    contract_type_profile_version, rule_set_version,
                    review_budget_profile_version, model_profile_code,
                    model_config_version, parser_version, prompt_version,
                    schema_version, pattern_library_version,
                    field_lexicon_version, evidence_selector_version,
                    provider_type, model_name, endpoint_alias,
                    stage_lease_owner, stage_lease_acquired_at, stage_lease_expires_at,
                    started_at, created_at)
                VALUES (?, ?, 'QUEUED', 'QUEUED',
                    'v20260705.1','v20260705.1','budget-standard-v20260724.1',
                    'MVP_DEMO_MOCK','model-config-mvp-demo-mock-v20260724.1',
                    'parser-docx-word-v20260724.1','v20260705.1',
                    'model-output-artifact-v20260724.1','v20260705.1',
                    'v20260705.1','v20260705.1',
                    'MOCK','cqcp-demo-mock','mock-local',
                    ?, NOW(), NOW()+INTERVAL '1 hour', NULL, NOW())
                """, executionId, taskId, owner);

        execRecord = new TaskExecutionRecord(executionId, taskId,
                ExecutionStatus.QUEUED, "QUEUED",
                new VersionReferences("v20260705.1","v20260705.1","budget-standard-v20260724.1",
                        "model-config-mvp-demo-mock-v20260724.1",
                        "parser-docx-word-v20260724.1","v20260705.1",
                        "model-output-artifact-v20260724.1","v20260705.1",
                        "v20260705.1","v20260705.1"),
                "MVP_DEMO_MOCK","MOCK","cqcp-demo-mock","mock-local", null, null);
    }

    @AfterEach
    void cleanUp() {
        // Delete only our own test data by exact ID — no LIKE, no TRUNCATE
        if (executionId != null) {
            jdbcTemplate.update("DELETE FROM point_diagnostic WHERE execution_id=?", executionId);
            jdbcTemplate.update("DELETE FROM tuning_packet WHERE execution_id=?", executionId);
            jdbcTemplate.update("DELETE FROM review_result_snapshot WHERE execution_id=?", executionId);
            jdbcTemplate.update("DELETE FROM task_stage_log WHERE execution_id=?", executionId);
            int del = jdbcTemplate.update("DELETE FROM execution WHERE execution_id=?", executionId);
            if (del != 1) throw new IllegalStateException("cleanup failed: execution " + executionId);
        }
        if (taskId != null) {
            int del = jdbcTemplate.update("DELETE FROM task WHERE task_id=?", taskId);
            if (del != 1) throw new IllegalStateException("cleanup failed: task " + taskId);
        }
    }

    @AfterEach
    void cleanTriggers() {
        for (var f : new String[]{"fail_composing_completed","fail_failed_log","f_ac15_log","f_ac15_term","f_ac15_snap","f_ac16"}) {
            try { jdbcTemplate.execute("DROP FUNCTION IF EXISTS " + f + "() CASCADE"); } catch (Exception ignored) {}
        }
    }

    // ── Basic operations ──

    @Test
    void startStageTransitionsFromQueuedToParsing() {
        var now = Instant.now(FIXED_CLOCK);
        var result = persistence.startStage(
                ExecutionStatus.QUEUED, "QUEUED",
                ExecutionStatus.PARSING, "PARSING", execRecord, owner, now, now);
        assertThat(result.status()).isEqualTo(ExecutionStatus.PARSING);
        var dbStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM execution WHERE execution_id=?", String.class, executionId);
        assertThat(dbStatus).isEqualTo("PARSING");
    }

    @Test
    void failExecutionTransitionsToFailedAndClearsLease() {
        var now = Instant.now(FIXED_CLOCK);
        persistence.failExecution("QUEUED", ExecutionStatus.QUEUED, "QUEUED",
                execRecord, owner, taskId, executionId, "执行阶段失败", now);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM execution WHERE execution_id=?", String.class, executionId))
                .isEqualTo("FAILED");
        var leaseOwner = jdbcTemplate.queryForObject(
                "SELECT stage_lease_owner FROM execution WHERE execution_id=?", String.class, executionId);
        assertThat(leaseOwner).isNull();
    }

    // ── Owner-loss guard tests (AC17) ──

    @Test
    void executionUpdateRejectsWrongOwner() {
        var now = Instant.now(FIXED_CLOCK);
        assertThatThrownBy(() -> persistence.failExecution("QUEUED",
                ExecutionStatus.QUEUED, "QUEUED", execRecord, "wrong-owner",
                taskId, executionId, "执行阶段失败", now))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM execution WHERE execution_id=?", String.class, executionId))
                .isEqualTo("QUEUED");
    }

    @Test
    void completedStageLogRejectsWrongOwner() {
        var now = Instant.now(FIXED_CLOCK);
        persistence.startStage(ExecutionStatus.QUEUED, "QUEUED",
                ExecutionStatus.PARSING, "PARSING", execRecord, owner, now, now);
        // COMPLETED log via appendCompletedStageLog with wrong owner
        var entry = TaskStageLogEntry.completed(taskId, executionId, "PARSING", 1, "SUCCESS", 0, now);
        assertThatThrownBy(() -> persistence.appendCompletedStageLog(
                entry, "wrong-owner", ExecutionStatus.PARSING, "PARSING"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void snapshotRejectsWrongOwner() {
        var now = Instant.now(FIXED_CLOCK);
        var composing = persistence.startStage(ExecutionStatus.QUEUED, "QUEUED",
                ExecutionStatus.COMPOSING, "COMPOSING", execRecord, owner, now, now);
        var summary = new ReviewSummary(9,9,0,0,0,0);
        var completeness = new ReviewCompleteness(ReviewCoverageStatus.FULL_REVIEWED,9,9,0,
                new java.math.BigDecimal("1.0000"), ConfidenceLevel.HIGH);
        var snap = new ReviewResultSnapshot(taskId, executionId, null, null,
                SnapshotStatus.SUCCESS, summary, completeness,
                List.of(), List.of(), List.of(), List.of(),
                Map.of(), List.of(), List.of(),
                "v20260705.1","v20260705.1","budget-standard-v20260724.1",
                "model-config-mvp-demo-mock-v20260724.1",
                "parser-docx-word-v20260724.1","v20260705.1",
                "model-output-artifact-v20260724.1","v20260705.1",
                "v20260705.1","v20260705.1", now);
        assertThatThrownBy(() -> persistence.completeExecution(composing, snap, "wrong-owner", now, now))
                .isInstanceOf(Exception.class);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM review_result_snapshot WHERE execution_id=?", Integer.class, executionId))
                .isZero();
    }

    // ── AC13: FAILED log business_reason is fixed ──

    @Test
    void ac13_failedLogContainsFixedBusinessReason() {
        var now = Instant.now(FIXED_CLOCK);
        persistence.failExecution("QUEUED", ExecutionStatus.QUEUED, "QUEUED",
                execRecord, owner, taskId, executionId, "执行阶段失败", now);
        var reason = jdbcTemplate.queryForObject(
                "SELECT business_reason FROM task_stage_log WHERE execution_id=? AND event_type='FAILED'",
                String.class, executionId);
        assertThat(reason).isEqualTo("执行阶段失败");
    }

    // ── AC15: completeExecution rolls back when any of the 3 steps fails ──
    // Helper: create an AFTER INSERT CONSTRAINT TRIGGER scoped to our execution_id.

    private void scopedTrigger(String triggerName, String funcName, String table, String condition) {
        jdbcTemplate.execute("CREATE OR REPLACE FUNCTION " + funcName + "() RETURNS trigger AS $$"
                + " BEGIN IF NEW.execution_id = '" + executionId + "' AND (" + condition
                + ") THEN RAISE EXCEPTION 'injected " + triggerName + "'; END IF; RETURN NEW; END;"
                + " $$ LANGUAGE plpgsql");
        jdbcTemplate.execute("CREATE CONSTRAINT TRIGGER " + triggerName + " AFTER INSERT ON " + table
                + " FOR EACH ROW EXECUTE FUNCTION " + funcName + "()");
    }

    private ReviewResultSnapshot minimalSnap(Instant now) {
        return new ReviewResultSnapshot(taskId, executionId, null, null, SnapshotStatus.SUCCESS,
                new ReviewSummary(9,9,0,0,0,0),
                new ReviewCompleteness(ReviewCoverageStatus.FULL_REVIEWED,9,9,0,
                        new java.math.BigDecimal("1.0000"), ConfidenceLevel.HIGH),
                List.of(), List.of(), List.of(), List.of(), Map.of(), List.of(), List.of(),
                "v20260705.1","v20260705.1","budget-standard-v20260724.1",
                "model-config-mvp-demo-mock-v20260724.1",
                "parser-docx-word-v20260724.1","v20260705.1",
                "model-output-artifact-v20260724.1","v20260705.1",
                "v20260705.1","v20260705.1", now);
    }

    @Test
    void ac15_snapshotInsertFailure_rollsBack() {
        var now = Instant.now(FIXED_CLOCK);
        var comp = persistence.startStage(ExecutionStatus.QUEUED,"QUEUED",
                ExecutionStatus.COMPOSING,"COMPOSING", execRecord, owner, now, now);
        // Create trigger that rejects snapshot INSERT for this execution
        jdbcTemplate.execute("CREATE OR REPLACE FUNCTION f_ac15_snap() RETURNS trigger AS $$"
                + " BEGIN IF NEW.execution_id = '" + executionId + "'"
                + " THEN RAISE EXCEPTION 'injected snapshot failure'; END IF; RETURN NEW; END;"
                + " $$ LANGUAGE plpgsql");
        jdbcTemplate.execute("CREATE CONSTRAINT TRIGGER tg_ac15_snap AFTER INSERT ON review_result_snapshot"
                + " FOR EACH ROW EXECUTE FUNCTION f_ac15_snap()");
        assertThatThrownBy(() -> persistence.completeExecution(comp, minimalSnap(now), owner, now, now))
                .isInstanceOf(Exception.class);
        // Snapshot rolled back (trigger fired, transaction rolled back by completeExecution)
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM review_result_snapshot WHERE execution_id=?", Integer.class, executionId)).isZero();
        // COMPLETED log also rolled back (same transaction)
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task_stage_log WHERE execution_id=? AND event_type='COMPLETED'", Integer.class, executionId)).isZero();
        // Execution still COMPOSING (rolled back)
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM execution WHERE execution_id=?", String.class, executionId)).isEqualTo("COMPOSING");
        jdbcTemplate.execute("DROP FUNCTION f_ac15_snap() CASCADE");
    }

    @Test
    void ac15_completedLogInsertFailure_rollsBack() {
        var now = Instant.now(FIXED_CLOCK);
        var comp = persistence.startStage(ExecutionStatus.QUEUED,"QUEUED",
                ExecutionStatus.COMPOSING,"COMPOSING", execRecord, owner, now, now);
        scopedTrigger("tg_ac15_log","f_ac15_log","task_stage_log","NEW.event_type='COMPLETED' AND NEW.stage_name='COMPOSING'");
        assertThatThrownBy(() -> persistence.completeExecution(comp, minimalSnap(now), owner, now, now))
                .isInstanceOf(Exception.class);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM review_result_snapshot WHERE execution_id=?", Integer.class, executionId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task_stage_log WHERE execution_id=? AND event_type='COMPLETED'", Integer.class, executionId)).isZero();
        jdbcTemplate.execute("DROP FUNCTION f_ac15_log() CASCADE");
    }

    @Test
    void ac15_terminalUpdateFailure_rollsBack() {
        var now = Instant.now(FIXED_CLOCK);
        var comp = persistence.startStage(ExecutionStatus.QUEUED,"QUEUED",
                ExecutionStatus.COMPOSING,"COMPOSING", execRecord, owner, now, now);
        jdbcTemplate.execute("CREATE OR REPLACE FUNCTION f_ac15_term() RETURNS trigger AS $$"
                + " BEGIN IF NEW.execution_id = '" + executionId + "' AND NEW.status IN ('SUCCESS','PARTIAL_SUCCESS')"
                + " THEN RAISE EXCEPTION 'injected terminal failure'; END IF; RETURN NEW; END;"
                + " $$ LANGUAGE plpgsql");
        jdbcTemplate.execute("CREATE CONSTRAINT TRIGGER tg_ac15_term AFTER UPDATE ON execution"
                + " FOR EACH ROW EXECUTE FUNCTION f_ac15_term()");
        assertThatThrownBy(() -> persistence.completeExecution(comp, minimalSnap(now), owner, now, now))
                .isInstanceOf(Exception.class);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM review_result_snapshot WHERE execution_id=?", Integer.class, executionId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task_stage_log WHERE execution_id=? AND event_type='COMPLETED'", Integer.class, executionId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM execution WHERE execution_id=?", String.class, executionId)).isEqualTo("COMPOSING");
        jdbcTemplate.execute("DROP FUNCTION f_ac15_term() CASCADE");
    }

    // ── AC16: failExecution rolls back when FAILED log fails ──

    @Test
    void ac16_failExecutionRollsBackOnFailedLogFailure() {
        var now = Instant.now(FIXED_CLOCK);
        persistence.startStage(ExecutionStatus.QUEUED, "QUEUED",
                ExecutionStatus.PARSING, "PARSING", execRecord, owner, now, now);
        scopedTrigger("tg_ac16","f_ac16","task_stage_log","NEW.event_type='FAILED'");
        var afterP1 = execRecord.transitionTo(ExecutionStatus.PARSING, "PARSING", now, null);
        assertThatThrownBy(() -> persistence.failExecution("PARSING",
                ExecutionStatus.PARSING, "PARSING", afterP1, owner, taskId, executionId, "执行阶段失败", now))
                .isInstanceOf(Exception.class);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM execution WHERE execution_id=?", String.class, executionId)).isEqualTo("PARSING");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task_stage_log WHERE execution_id=? AND event_type='FAILED'", Integer.class, executionId)).isZero();
        jdbcTemplate.execute("DROP FUNCTION f_ac16() CASCADE");
    }

    // ── AC17: owner mismatch for public interface methods ──
    // These test saveExecution, appendStageLog, saveSnapshot with correct and wrong owner.

    @Test
    void ac17_saveExecution_correctOwner_leaseStability() {
        // Set deterministic baseline: acquired=30min ago, heartbeat=10min ago, expires=50min from now
        // Known lease duration = 60min (expires - heartbeat). Old formula using acquired would
        // give expires=now+80min (expires50m - acquired30minago=80min) which would inflate to 80min.
        jdbcTemplate.update("UPDATE execution SET stage_lease_acquired_at = NOW() - INTERVAL '30 minutes',"
                + " heartbeat_at = NOW() - INTERVAL '10 minutes',"
                + " stage_lease_expires_at = NOW() + INTERVAL '50 minutes'"
                + " WHERE execution_id = ?", executionId);

        var now = Instant.now(FIXED_CLOCK);
        JdbcTaskExecutionPersistence.setCurrentOwner(owner);
        // QUEUED → PARSING via saveExecution (first refresh)
        var p1 = execRecord.transitionTo(ExecutionStatus.PARSING, "PARSING", now, null);
        persistence.saveExecution(p1);
        var hb1 = (java.sql.Timestamp) jdbcTemplate.queryForObject(
                "SELECT heartbeat_at FROM execution WHERE execution_id=?", Object.class, executionId);
        var exp1 = (java.sql.Timestamp) jdbcTemplate.queryForObject(
                "SELECT stage_lease_expires_at FROM execution WHERE execution_id=?", Object.class, executionId);
        assertThat(exp1.after(hb1)).isTrue();
        // PARSING → INDEXING via saveExecution (second refresh)
        var p2 = p1.transitionTo(ExecutionStatus.INDEXING, "INDEXING", now, null);
        persistence.saveExecution(p2);
        var hb2 = (java.sql.Timestamp) jdbcTemplate.queryForObject(
                "SELECT heartbeat_at FROM execution WHERE execution_id=?", Object.class, executionId);
        var exp2 = (java.sql.Timestamp) jdbcTemplate.queryForObject(
                "SELECT stage_lease_expires_at FROM execution WHERE execution_id=?", Object.class, executionId);
        assertThat(exp2.after(hb2)).isTrue();
        var durMs = exp2.getTime() - hb2.getTime();
        // Lease duration must stay at 60min baseline (within 2s tolerance).
        // Old acquired-at formula would produce ~80min and fail this assertion.
        assertThat((double) durMs).as("lease duration should remain stable at 60min baseline; actual=%dms", durMs)
                .isCloseTo(60.0 * 60 * 1000, within(2000.0));
        // Both heartbeat and expiry must advance
        assertThat(hb2.after(hb1)).isTrue();
        assertThat(exp2.after(exp1)).isTrue();
        JdbcTaskExecutionPersistence.clearCurrentOwner();
    }

    @Test
    void ac17_saveExecutionCorrectOwner_terminalClearsLease() {
        var now = Instant.now(FIXED_CLOCK);
        // First go to COMPOSING
        var comp = persistence.startStage(ExecutionStatus.QUEUED,"QUEUED",
                ExecutionStatus.COMPOSING,"COMPOSING", execRecord, owner, now, now);
        assertThat(comp.status()).isEqualTo(ExecutionStatus.COMPOSING);
        // Now write terminal via saveExecution
        JdbcTaskExecutionPersistence.setCurrentOwner(owner);
        var terminal = comp.transitionTo(ExecutionStatus.SUCCESS, "SUCCESS", now, now);
        persistence.saveExecution(terminal);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM execution WHERE execution_id=?", String.class, executionId)).isEqualTo("SUCCESS");
        // All 4 lease fields null (terminal cleared)
        var lease = jdbcTemplate.queryForMap(
                "SELECT stage_lease_owner, stage_lease_acquired_at, stage_lease_expires_at, heartbeat_at FROM execution WHERE execution_id=?", executionId);
        assertThat(lease.get("stage_lease_owner")).isNull();
        assertThat(lease.get("stage_lease_acquired_at")).isNull();
        assertThat(lease.get("stage_lease_expires_at")).isNull();
        assertThat(lease.get("heartbeat_at")).isNull();
        JdbcTaskExecutionPersistence.clearCurrentOwner();
    }

    @Test
    void ac17_saveExecutionRejectsWrongOwner() {
        var now = Instant.now(FIXED_CLOCK);
        JdbcTaskExecutionPersistence.setCurrentOwner("wrong-owner");
        var exec = execRecord.transitionTo(ExecutionStatus.PARSING, "PARSING", now, null);
        assertThatThrownBy(() -> persistence.saveExecution(exec)).isInstanceOf(Exception.class);
        JdbcTaskExecutionPersistence.clearCurrentOwner();
    }

    @Test
    void ac17_appendStageLogRejectsWrongOwner() {
        JdbcTaskExecutionPersistence.setCurrentOwner("wrong-owner");
        assertThatThrownBy(() -> persistence.appendStageLog(
                TaskStageLogEntry.completed(taskId, executionId, "PARSING", 1, "SUCCESS", 0, Instant.now(FIXED_CLOCK))))
                .isInstanceOf(Exception.class);
        JdbcTaskExecutionPersistence.clearCurrentOwner();
    }

    @Test
    void ac17_saveSnapshot_correctOwner_writesOne() {
        var now = Instant.now(FIXED_CLOCK);
        persistence.startStage(ExecutionStatus.QUEUED,"QUEUED",
                ExecutionStatus.COMPOSING,"COMPOSING", execRecord, owner, now, now);
        JdbcTaskExecutionPersistence.setCurrentOwner(owner);
        persistence.saveSnapshot(minimalSnap(now));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM review_result_snapshot WHERE execution_id=?", Integer.class, executionId)).isOne();
        // Second call fails (PK violation or guard) — the INSERT_SNAPSHOT_SQL uses INSERT SELECT
        // which means duplicate execution_id won't fail PK but will hit guard mismatch
        assertThatThrownBy(() -> persistence.saveSnapshot(minimalSnap(now))).isInstanceOf(Exception.class);
        // Still exactly one
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM review_result_snapshot WHERE execution_id=?", Integer.class, executionId)).isOne();
        JdbcTaskExecutionPersistence.clearCurrentOwner();
    }

    @Test
    void ac17_saveSnapshot_wrongOwner_fails() {
        var now = Instant.now(FIXED_CLOCK);
        persistence.startStage(ExecutionStatus.QUEUED,"QUEUED",
                ExecutionStatus.COMPOSING,"COMPOSING", execRecord, owner, now, now);
        JdbcTaskExecutionPersistence.setCurrentOwner("wrong-owner");
        assertThatThrownBy(() -> persistence.saveSnapshot(minimalSnap(now))).isInstanceOf(Exception.class);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM review_result_snapshot WHERE execution_id=?", Integer.class, executionId)).isZero();
        JdbcTaskExecutionPersistence.clearCurrentOwner();
    }

    // ── AC7: Stage log ordering ──

    @Test
    void ac7_stageLogsAreOrderedByLogId() {
        var now = Instant.now(FIXED_CLOCK);
        var exec = execRecord;

        var p1 = persistence.startStage(ExecutionStatus.QUEUED,"QUEUED",
                ExecutionStatus.PARSING,"PARSING", exec, owner, now, now);
        persistence.appendCompletedStageLog(
                TaskStageLogEntry.completed(taskId,executionId,"PARSING",1,"SUCCESS",0,now),
                owner, ExecutionStatus.PARSING, "PARSING");

        var p2 = persistence.startStage(ExecutionStatus.PARSING,"PARSING",
                ExecutionStatus.INDEXING,"INDEXING", p1, owner, now, now);
        persistence.appendCompletedStageLog(
                TaskStageLogEntry.completed(taskId,executionId,"INDEXING",1,"SUCCESS",0,now),
                owner, ExecutionStatus.INDEXING, "INDEXING");

        var p3 = persistence.startStage(ExecutionStatus.INDEXING,"INDEXING",
                ExecutionStatus.PLANNING,"PLANNING", p2, owner, now, now);
        persistence.appendCompletedStageLog(
                TaskStageLogEntry.completed(taskId,executionId,"PLANNING",1,"SUCCESS",0,now),
                owner, ExecutionStatus.PLANNING, "PLANNING");

        var p4 = persistence.startStage(ExecutionStatus.PLANNING,"PLANNING",
                ExecutionStatus.BUILDING_EVIDENCE,"BUILDING_EVIDENCE", p3, owner, now, now);
        persistence.appendCompletedStageLog(
                TaskStageLogEntry.completed(taskId,executionId,"BUILDING_EVIDENCE",1,"SUCCESS",0,now),
                owner, ExecutionStatus.BUILDING_EVIDENCE, "BUILDING_EVIDENCE");

        var p5 = persistence.startStage(ExecutionStatus.BUILDING_EVIDENCE,"BUILDING_EVIDENCE",
                ExecutionStatus.REVIEWING_RULES,"REVIEWING_RULES", p4, owner, now, now);
        persistence.appendCompletedStageLog(
                TaskStageLogEntry.completed(taskId,executionId,"REVIEWING_RULES",1,"SUCCESS",0,now),
                owner, ExecutionStatus.REVIEWING_RULES, "REVIEWING_RULES");

        var comp = persistence.startStage(ExecutionStatus.REVIEWING_RULES,"REVIEWING_RULES",
                ExecutionStatus.COMPOSING,"COMPOSING", p5, owner, now, now);

        var summary = new ReviewSummary(9,9,0,0,0,0);
        var completeness = new ReviewCompleteness(ReviewCoverageStatus.FULL_REVIEWED,9,9,0,
                new java.math.BigDecimal("1.0000"), ConfidenceLevel.HIGH);
        var snapshot = new ReviewResultSnapshot(taskId,executionId,null,null,SnapshotStatus.SUCCESS,
                summary,completeness,List.of(),List.of(),List.of(),List.of(),
                Map.of(),List.of(),List.of(),
                "v20260705.1","v20260705.1","budget-standard-v20260724.1",
                "model-config-mvp-demo-mock-v20260724.1",
                "parser-docx-word-v20260724.1","v20260705.1",
                "model-output-artifact-v20260724.1","v20260705.1",
                "v20260705.1","v20260705.1", now);
        persistence.completeExecution(comp, snapshot, owner, now, now);

        var logs = jdbcTemplate.query(
                "SELECT stage_name,event_type FROM task_stage_log WHERE execution_id=? ORDER BY task_stage_log_id ASC",
                (rs,n)->Map.entry(rs.getString("stage_name"),rs.getString("event_type")), executionId);
        assertThat(logs).hasSize(12);
        assertThat(logs).containsExactly(
                Map.entry("PARSING","STARTED"), Map.entry("PARSING","COMPLETED"),
                Map.entry("INDEXING","STARTED"), Map.entry("INDEXING","COMPLETED"),
                Map.entry("PLANNING","STARTED"), Map.entry("PLANNING","COMPLETED"),
                Map.entry("BUILDING_EVIDENCE","STARTED"), Map.entry("BUILDING_EVIDENCE","COMPLETED"),
                Map.entry("REVIEWING_RULES","STARTED"), Map.entry("REVIEWING_RULES","COMPLETED"),
                Map.entry("COMPOSING","STARTED"), Map.entry("COMPOSING","COMPLETED"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM execution WHERE execution_id=?", String.class, executionId))
                .isEqualTo("SUCCESS");
    }

    // ── completeExecution happy path ──

    @Test
    void completeExecutionPersistsSnapshotAndTransition() {
        var now = Instant.now(FIXED_CLOCK);
        var composing = persistence.startStage(ExecutionStatus.QUEUED,"QUEUED",
                ExecutionStatus.COMPOSING,"COMPOSING", execRecord, owner, now, now);
        var summary = new ReviewSummary(9,9,0,0,0,0);
        var completeness = new ReviewCompleteness(ReviewCoverageStatus.FULL_REVIEWED,9,9,0,
                new java.math.BigDecimal("1.0000"), ConfidenceLevel.HIGH);
        var snapshot = new ReviewResultSnapshot(taskId,executionId,null,null,SnapshotStatus.SUCCESS,
                summary,completeness,List.of(),List.of(),List.of(),List.of(),
                Map.of(),List.of(),List.of(),
                "v20260705.1","v20260705.1","budget-standard-v20260724.1",
                "model-config-mvp-demo-mock-v20260724.1",
                "parser-docx-word-v20260724.1","v20260705.1",
                "model-output-artifact-v20260724.1","v20260705.1",
                "v20260705.1","v20260705.1", now);
        persistence.completeExecution(composing, snapshot, owner, now, now);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM review_result_snapshot WHERE execution_id=?", Integer.class, executionId))
                .isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task_stage_log WHERE execution_id=? AND event_type='COMPLETED'",
                Integer.class, executionId)).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM execution WHERE execution_id=?", String.class, executionId))
                .isEqualTo("SUCCESS");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT stage_lease_owner FROM execution WHERE execution_id=?", String.class, executionId))
                .isNull();
    }

    @Test
    void staleSameOwner_saveExecution_rejected() {
        var now = Instant.now(FIXED_CLOCK);
        JdbcTaskExecutionPersistence.setCurrentOwner(owner);
        var p1 = persistence.startStage(ExecutionStatus.QUEUED,"QUEUED",
                ExecutionStatus.PARSING,"PARSING", execRecord, owner, now, now);
        var staleP1 = p1.transitionTo(ExecutionStatus.PARSING, "PARSING", now, null);
        persistence.startStage(ExecutionStatus.PARSING,"PARSING",
                ExecutionStatus.INDEXING,"INDEXING", p1, owner, now, now);
        JdbcTaskExecutionPersistence.setCurrentOwner(owner);
        assertThatThrownBy(() -> persistence.saveExecution(staleP1)).isInstanceOf(Exception.class);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM execution WHERE execution_id=?", String.class, executionId)).isEqualTo("INDEXING");
        JdbcTaskExecutionPersistence.clearCurrentOwner();
    }

    @Test
    void staleSameOwner_appendStageLog_rejected() {
        var now = Instant.now(FIXED_CLOCK);
        persistence.startStage(ExecutionStatus.QUEUED,"QUEUED",
                ExecutionStatus.PARSING,"PARSING", execRecord, owner, now, now);
        JdbcTaskExecutionPersistence.setCurrentOwner(owner);
        var staleEntry = TaskStageLogEntry.completed(taskId, executionId, "PARSING", 1, "SUCCESS", 0, now);
        persistence.startStage(ExecutionStatus.PARSING,"PARSING",
                ExecutionStatus.INDEXING,"INDEXING", execRecord, owner, now, now);
        assertThatThrownBy(() -> persistence.appendStageLog(staleEntry)).isInstanceOf(Exception.class);
        var count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task_stage_log WHERE execution_id=? AND stage_name='PARSING' AND event_type='COMPLETED'",
                Integer.class, executionId);
        assertThat(count).isZero();
        JdbcTaskExecutionPersistence.clearCurrentOwner();
    }

    @Test
    void staleSameOwner_failWrongStage_rejected() {
        var now = Instant.now(FIXED_CLOCK);
        JdbcTaskExecutionPersistence.setCurrentOwner(owner);
        var p1 = persistence.startStage(ExecutionStatus.QUEUED,"QUEUED",
                ExecutionStatus.PARSING,"PARSING", execRecord, owner, now, now);
        persistence.startStage(ExecutionStatus.PARSING,"PARSING",
                ExecutionStatus.INDEXING,"INDEXING", p1, owner, now, now);
        JdbcTaskExecutionPersistence.setCurrentOwner(owner);
        var wrongFail = execRecord.transitionTo(ExecutionStatus.FAILED, "PARSING", now, now);
        assertThatThrownBy(() -> persistence.saveExecution(wrongFail)).isInstanceOf(Exception.class);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM execution WHERE execution_id=?", String.class, executionId)).isEqualTo("INDEXING");
        JdbcTaskExecutionPersistence.clearCurrentOwner();
    }

    @Test
    void startStage_rejectsMissingLeaseBaseline() {
        var now = Instant.now(FIXED_CLOCK);
        jdbcTemplate.update("UPDATE execution SET heartbeat_at = NULL, stage_lease_acquired_at = NULL,"
                + " stage_lease_expires_at = NOW() + INTERVAL '1 hour' WHERE execution_id = ?", executionId);
        assertThatThrownBy(() -> persistence.startStage(
                ExecutionStatus.QUEUED, "QUEUED",
                ExecutionStatus.PARSING, "PARSING",
                execRecord, owner, now, now))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("startStage failed");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM execution WHERE execution_id=?", String.class, executionId)).isEqualTo("QUEUED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT current_stage FROM execution WHERE execution_id=?", String.class, executionId)).isEqualTo("QUEUED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT stage_lease_owner FROM execution WHERE execution_id=?", String.class, executionId)).isEqualTo(owner);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT stage_lease_expires_at FROM execution WHERE execution_id=?", Object.class, executionId)).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT heartbeat_at FROM execution WHERE execution_id=?", Object.class, executionId)).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT stage_lease_acquired_at FROM execution WHERE execution_id=?", Object.class, executionId)).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task_stage_log WHERE execution_id=?", Integer.class, executionId)).isZero();
    }
}
