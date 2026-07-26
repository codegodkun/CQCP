package com.cqcp.apiserver.reviewengine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A → B integration tests using real MockMvc POST + worker.runOnce().
 * Each test cleans only its own created rows in @AfterEach.
 * ALL success paths use worker.runOnce() — no claimOne+process bypass.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "cqcp.review.worker.enabled=false",
        "spring.flyway.enabled=true",
})
class SingleReviewWorkerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private SingleReviewWorker worker;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PersistentTaskResultStore resultStore;
    private final List<String> createdTaskIds = new ArrayList<>();
    private final List<String> createdExecutionIds = new ArrayList<>();
    private final List<java.nio.file.Path> createdFiles = new ArrayList<>();
    private final List<String> capturedWorkerLogs = new ArrayList<>();
    private ch.qos.logback.core.AppenderBase<ch.qos.logback.classic.spi.ILoggingEvent> captureAppender;

    @BeforeEach
    void setUpLogCapture() {
        capturedWorkerLogs.clear();
        var ctx = (ch.qos.logback.classic.LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory();
        var workerLogger = ctx.getLogger(SingleReviewWorker.class);
        captureAppender = new ch.qos.logback.core.AppenderBase<ch.qos.logback.classic.spi.ILoggingEvent>() {
            @Override protected void append(ch.qos.logback.classic.spi.ILoggingEvent event) {
                capturedWorkerLogs.add(event.getFormattedMessage());
            }
        };
        captureAppender.setContext(ctx);
        captureAppender.start();
        workerLogger.addAppender(captureAppender);
    }

    @AfterEach
    void cleanUp() {
        // Delete DB rows by exact ID — FK-safe order (leaf first)
        for (var eid : createdExecutionIds) {
            jdbcTemplate.update("DELETE FROM point_diagnostic WHERE execution_id=?", eid);
            jdbcTemplate.update("DELETE FROM tuning_packet WHERE execution_id=?", eid);
            jdbcTemplate.update("DELETE FROM review_result_snapshot WHERE execution_id=?", eid);
            jdbcTemplate.update("DELETE FROM task_stage_log WHERE execution_id=?", eid);
            int d1 = jdbcTemplate.update("DELETE FROM execution WHERE execution_id=?", eid);
            if (d1 != 1) throw new IllegalStateException("cleanup failed execution: " + eid);
        }
        for (var tid : createdTaskIds) {
            int d2 = jdbcTemplate.update("DELETE FROM task WHERE task_id=?", tid);
            if (d2 != 1) throw new IllegalStateException("cleanup failed task: " + tid);
        }
        // Delete filesystem artefacts in reverse-add order. Walk directories before deleting them.
        for (int i = createdFiles.size() - 1; i >= 0; i--) {
            var p = createdFiles.get(i);
            if (java.nio.file.Files.isDirectory(p)) {
                try (var walk = java.nio.file.Files.walk(p)) {
                    var sorted = walk.sorted((a, b) -> b.compareTo(a));
                    sorted.forEach(f -> { try { java.nio.file.Files.deleteIfExists(f); } catch (java.io.IOException e) { throw new RuntimeException("cleanup failed: " + f, e); } });
                } catch (java.io.IOException e) { throw new RuntimeException("cleanup walk failed: " + p, e); }
            } else {
                try { java.nio.file.Files.deleteIfExists(p); } catch (java.io.IOException e) { throw new RuntimeException("cleanup failed: " + p, e); }
            }
        }
        createdTaskIds.clear();
        createdExecutionIds.clear();
        createdFiles.clear();
        // Remove log capture appender
        if (captureAppender != null) {
            captureAppender.stop();
            var ctx = (ch.qos.logback.classic.LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory();
            ctx.getLogger(SingleReviewWorker.class).detachAppender(captureAppender);
            captureAppender = null;
        }
    }

    // ── MONTHLY success path (AC5) ──

    @Test
    void aToBMonthlyWorkerProducesTerminalExecution() throws Exception {
        var meta = monthlyMeta();
        var result = mockMvc.perform(multipart("/api/review/tasks")
                        .file(docxFile("m.docx")).file(metaPart(meta)))
                .andExpect(status().isAccepted()).andReturn();
        var taskId = extractStr(result.getResponse().getContentAsString(), "taskId");
        var executionId = extractStr(result.getResponse().getContentAsString(), "executionId");
        createdTaskIds.add(taskId);
        createdExecutionIds.add(executionId);

        // Set early created_at so runOnce FIFO picks our execution before any stale data
        jdbcTemplate.update("UPDATE execution SET created_at='2000-01-01T00:00:00Z'::timestamptz WHERE execution_id=?", executionId);

        boolean claimed = worker.runOnce();
        assertThat(claimed).isTrue();

        // Terminal SUCCESS/PARTIAL_SUCCESS
        var status = jdbcTemplate.queryForObject(
                "SELECT status FROM execution WHERE execution_id=?", String.class, executionId);
        assertThat(status).isIn("SUCCESS", "PARTIAL_SUCCESS");

        // Lease owner fully cleared
        var leaseFields = jdbcTemplate.queryForMap(
                "SELECT stage_lease_owner, stage_lease_acquired_at, stage_lease_expires_at, heartbeat_at FROM execution WHERE execution_id=?",
                executionId);
        assertThat(leaseFields.get("stage_lease_owner")).isNull();
        assertThat(leaseFields.get("stage_lease_acquired_at")).isNull();
        assertThat(leaseFields.get("stage_lease_expires_at")).isNull();
        assertThat(leaseFields.get("heartbeat_at")).isNull();

        // 6-stage logs
        var logs = jdbcTemplate.query(
                "SELECT stage_name, event_type FROM task_stage_log WHERE execution_id=? ORDER BY task_stage_log_id ASC",
                (rs, n) -> java.util.Map.entry(rs.getString("stage_name"), rs.getString("event_type")),
                executionId);
        assertThat(logs).hasSize(12);
        assertThat(logs).containsExactly(
                entry("PARSING","STARTED"), entry("PARSING","COMPLETED"),
                entry("INDEXING","STARTED"), entry("INDEXING","COMPLETED"),
                entry("PLANNING","STARTED"), entry("PLANNING","COMPLETED"),
                entry("BUILDING_EVIDENCE","STARTED"), entry("BUILDING_EVIDENCE","COMPLETED"),
                entry("REVIEWING_RULES","STARTED"), entry("REVIEWING_RULES","COMPLETED"),
                entry("COMPOSING","STARTED"), entry("COMPOSING","COMPLETED"));

        // Snapshot
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM review_result_snapshot WHERE execution_id=?", Integer.class, executionId))
                .isOne();

        // PersistentTaskResultStore readback
        var snapOpt = resultStore.findLatestSnapshot(taskId);
        assertThat(snapOpt).isPresent();
        var snap = snapOpt.orElseThrow();
        assertThat(snap.taskId()).isEqualTo(taskId);
        assertThat(snap.executionId()).isEqualTo(executionId);
        assertThat(snap.pointResults()).hasSize(9);
        assertThat(snap.enabledReviewPointsSnapshot()).hasSize(9);
        assertThat(snap.disabledReviewPointsSnapshot()).isEmpty();

        // 10 version fields all match execution
        assertThat(snap.ruleSetVersion()).isEqualTo("v20260705.1");
        assertThat(snap.contractTypeProfileVersion()).isEqualTo("v20260705.1");
        assertThat(snap.reviewBudgetProfileVersion()).isEqualTo("budget-standard-v20260724.1");
        assertThat(snap.modelProfileVersion()).isEqualTo("model-config-mvp-demo-mock-v20260724.1");
        assertThat(snap.parserVersion()).isEqualTo("parser-docx-word-v20260724.1");
        assertThat(snap.promptVersion()).isEqualTo("v20260705.1");
        assertThat(snap.schemaVersion()).isEqualTo("model-output-artifact-v20260724.1");
        assertThat(snap.patternLibraryVersion()).isEqualTo("v20260705.1");
        assertThat(snap.fieldLexiconVersion()).isEqualTo("v20260705.1");
        assertThat(snap.evidenceSelectorVersion()).isEqualTo("v20260705.1");

        // Structured fields
        assertThat(snap.structuredFieldsSnapshot().get("contractName")).isEqualTo("测试合同");
        assertThat(snap.structuredFieldsSnapshot().get("contractTotalAmount")).isEqualTo("1130");
        assertThat(snap.structuredFieldsSnapshot().get("prepaymentRatio")).isEqualTo("20");
        assertThat(snap.structuredFieldsSnapshot().get("currency")).isEqualTo("CNY");
    }

    // ── MILESTONE → 4 SKIPPED points (AC6) ──

    @Test
    void milestoneWorkerSkipsFourMonthlyPoints() throws Exception {
        var meta = milMeta();
        var result = mockMvc.perform(multipart("/api/review/tasks")
                        .file(docxFile("m.docx")).file(metaPart(meta)))
                .andExpect(status().isAccepted()).andReturn();
        var taskId = extractStr(result.getResponse().getContentAsString(), "taskId");
        var executionId = extractStr(result.getResponse().getContentAsString(), "executionId");
        createdTaskIds.add(taskId);
        createdExecutionIds.add(executionId);

        jdbcTemplate.update("UPDATE execution SET created_at='2000-01-01T00:00:00Z'::timestamptz WHERE execution_id=?", executionId);

        assertThat(worker.runOnce()).isTrue();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM execution WHERE execution_id=?", String.class, executionId))
                .isIn("SUCCESS", "PARTIAL_SUCCESS");

        var snap = resultStore.findLatestSnapshot(taskId).orElseThrow();
        var skipped = snap.pointResults().stream()
                .filter(pr -> pr.pointStatus() == PointStatus.SKIPPED).toList();
        assertThat(skipped).hasSize(4);
        assertThat(skipped.stream().map(PointReviewResult::reviewPointCode)).contains(
                ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY,
                ReviewPointCode.COMPLETION_PAYMENT_RATIO_CONSISTENCY,
                ReviewPointCode.SETTLEMENT_PAYMENT_RATIO_CONSISTENCY,
                ReviewPointCode.WARRANTY_RETENTION_RATIO_CONSISTENCY);
        assertThat(snap.findings()).isEmpty();
    }

    // ── AC18: enabled=false; scheduled no-op, manual runOnce works ──

    @Test
    void ac18_enabledFalse_scheduledNoOp_runOnceWorks() throws Exception {
        var meta = monthlyMeta();
        var result = mockMvc.perform(multipart("/api/review/tasks")
                        .file(docxFile("ac18.docx")).file(metaPart(meta)))
                .andExpect(status().isAccepted()).andReturn();
        var taskId = extractStr(result.getResponse().getContentAsString(), "taskId");
        var executionId = extractStr(result.getResponse().getContentAsString(), "executionId");
        createdTaskIds.add(taskId);
        createdExecutionIds.add(executionId);

        // scheduledTrigger with enabled=false — must not claim
        worker.scheduledTrigger();

        var statusAfterScheduled = jdbcTemplate.queryForObject(
                "SELECT status FROM execution WHERE execution_id=?", String.class, executionId);
        assertThat(statusAfterScheduled).isEqualTo("QUEUED");

        var lease = jdbcTemplate.queryForMap(
                "SELECT stage_lease_owner, stage_lease_acquired_at, stage_lease_expires_at FROM execution WHERE execution_id=?",
                executionId);
        assertThat(lease.get("stage_lease_owner")).isNull();
        assertThat(lease.get("stage_lease_acquired_at")).isNull();
        assertThat(lease.get("stage_lease_expires_at")).isNull();

        // Set early created_at so runOnce FIFO picks our execution before any stale data
        jdbcTemplate.update("UPDATE execution SET created_at='2000-01-01T00:00:00Z'::timestamptz WHERE execution_id=?", executionId);

        // Now manual runOnce — must claim and succeed
        boolean claimed = worker.runOnce();
        assertThat(claimed).isTrue();

        var finalStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM execution WHERE execution_id=?", String.class, executionId);
        assertThat(finalStatus).isIn("SUCCESS", "PARTIAL_SUCCESS");

        // Snapshot exists
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM review_result_snapshot WHERE execution_id=?", Integer.class, executionId))
                .isOne();
    }

    // ── AC13 failure paths — 8 scenarios ──

    @Test
    void ac13_unsupportedRuleSet_failsClosed() {
        var tId = "b13-" + uuid8(); var eId = "b13-" + uuid8();
        jdbcTemplate.update("INSERT INTO task (task_id, caller_type, source_type, contract_name, " +
                "contract_type_code, result_url, structured_fields_snapshot) " +
                "VALUES (?,'TEST','TEST',?,'ENGINEERING',?,'{}')", tId, tId, "/r/"+tId);
        jdbcTemplate.update("INSERT INTO execution (execution_id, task_id, status, current_stage, " +
                "contract_type_profile_version, rule_set_version, review_budget_profile_version, " +
                "model_profile_code, model_config_version, parser_version, prompt_version, " +
                "schema_version, pattern_library_version, field_lexicon_version, evidence_selector_version, " +
                "provider_type, model_name, endpoint_alias) " +
                "VALUES (?,?,'QUEUED','QUEUED','v20260705.1','unsupported-v999'," +
                "'budget-standard-v20260724.1','MVP_DEMO_MOCK','model-config-mvp-demo-mock-v20260724.1'," +
                "'parser-docx-word-v20260724.1','v20260705.1','model-output-artifact-v20260724.1'," +
                "'v20260705.1','v20260705.1','v20260705.1','MOCK','cqcp-demo-mock','mock-local')",
                eId, tId);
        createdTaskIds.add(tId); createdExecutionIds.add(eId);
        jdbcTemplate.update("UPDATE execution SET created_at='2000-01-01T00:00:00Z'::timestamptz WHERE execution_id=?", eId);
        assertThat(worker.runOnce()).as("runOnce must claim our AC13 execution").isTrue();
        assertAc13(eId, eId);
    }

    @Test
    void ac13_malformedStructuredFields_failsClosed() {
        var tId = "b13-" + uuid8(); var eId = "b13-" + uuid8();
        jdbcTemplate.update("INSERT INTO task (task_id, caller_type, source_type, contract_name, " +
                "contract_type_code, result_url, structured_fields_snapshot) " +
                "VALUES (?,'TEST','TEST',?,'ENGINEERING',?,'\"not-an-object\"'::jsonb)",
                tId, tId, "/r/"+tId);
        jdbcTemplate.update("INSERT INTO execution (execution_id, task_id, status, current_stage, " +
                "contract_type_profile_version, rule_set_version, review_budget_profile_version, " +
                "model_profile_code, model_config_version, parser_version, prompt_version, " +
                "schema_version, pattern_library_version, field_lexicon_version, evidence_selector_version, " +
                "provider_type, model_name, endpoint_alias) " +
                "VALUES (?,?,'QUEUED','QUEUED','v20260705.1','v20260705.1'," +
                "'budget-standard-v20260724.1','MVP_DEMO_MOCK','model-config-mvp-demo-mock-v20260724.1'," +
                "'parser-docx-word-v20260724.1','v20260705.1','model-output-artifact-v20260724.1'," +
                "'v20260705.1','v20260705.1','v20260705.1','MOCK','cqcp-demo-mock','mock-local')",
                eId, tId);
        createdTaskIds.add(tId); createdExecutionIds.add(eId);
        jdbcTemplate.update("UPDATE execution SET created_at='2000-01-01T00:00:00Z'::timestamptz WHERE execution_id=?", eId);

        assertThat(worker.runOnce()).isTrue();
        assertAc13(eId, eId);
    }

    @Test
    void ac13_parserFailure_failsClosed() {
        var tId = "b13-" + uuid8(); var eId = "b13-" + uuid8();
        var docRef = tId + "/" + uuid32() + ".docx";
        // Create a 0-byte file at the expected path — passes readDocument (regular file)
        // but DocxWordParserSpike.parse throws (not a valid OPC package)
        var fullPath = uploadRoot().resolve(docRef);
        writeFile(fullPath, new byte[0]);
        insertTaskExec(tId, eId, "v20260705.1", "{}", docRef);
        boolean c = worker.runOnce(); assertThat(c).isTrue();
        assertAc13(eId, eId);
    }

    @Test
    void ac13_docxMissing_failsClosed() {
        var tId = "b13-" + uuid8(); var eId = "b13-" + uuid8();
        var docRef = tId + "/" + uuid32() + ".docx";
        insertTaskExec(tId, eId, "v20260705.1", "{}", docRef);
        // No file created at docRef path — readDocument returns empty
        boolean c = worker.runOnce(); assertThat(c).isTrue();
        assertAc13(eId, eId);
    }

    @Test
    void ac13_crossTaskReference_failsClosed() {
        var tId = "b13-" + uuid8(); var eId = "b13-" + uuid8();
        // documentReference starts with a different taskId = cross-task
        insertTaskExec(tId, eId, "v20260705.1", "{}", "OTHER_TASK/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.docx");
        boolean c = worker.runOnce(); assertThat(c).isTrue();
        assertAc13(eId, eId);
    }

    @Test
    void ac13_escapeReference_failsClosed() {
        var tId = "b13-" + uuid8(); var eId = "b13-" + uuid8();
        // documentReference with parent-dir traversal
        insertTaskExec(tId, eId, "v20260705.1", "{}", tId + "/../escape.docx");
        boolean c = worker.runOnce(); assertThat(c).isTrue();
        assertAc13(eId, eId);
    }

    @Test
    void ac13_parentDirSymlink_failsClosed() {
        var tId = "b13-" + uuid8(); var eId = "b13-" + uuid8();
        var docRef = tId + "/" + uuid32() + ".docx";
        symlinkDirSetup(tId, docRef);
        insertTaskExec(tId, eId, "v20260705.1", "{}", docRef);
        boolean c = worker.runOnce(); assertThat(c).isTrue();
        assertAc13(eId, eId);
    }

    /** Create a dir that looks like the task dir but is a symlink. */
    private void symlinkDirSetup(String tId, String docRef) {
        try {
            var path = uploadRoot().resolve(docRef);
            writeFile(path, MINI_DOCX); // real file at real path first
            var taskDir = path.getParent();
            var realDir = taskDir.resolveSibling(tId + "-real");
            java.nio.file.Files.move(taskDir, realDir);
            java.nio.file.Files.createSymbolicLink(taskDir, realDir);
            createdFiles.add(taskDir);
            createdFiles.add(realDir);
        } catch (java.io.IOException e) { throw new RuntimeException(e); }
    }

    @Test
    void ac13_finalSymlink_failsClosed() {
        var tId = "b13-" + uuid8(); var eId = "b13-" + uuid8();
        var docRef = tId + "/" + uuid32() + ".docx";
        symlinkFileSetup(docRef);
        insertTaskExec(tId, eId, "v20260705.1", "{}", docRef);
        boolean c = worker.runOnce(); assertThat(c).isTrue();
        assertAc13(eId, eId);
    }

    /** Create a symlink that looks like a hex-named .docx but points elsewhere. */
    private void symlinkFileSetup(String docRef) {
        try {
            var path = uploadRoot().resolve(docRef);
            var realContent = java.nio.file.Files.createTempFile(uploadRoot(), "real-", ".dat");
            writeFile(realContent, MINI_DOCX);
            java.nio.file.Files.createDirectories(path.getParent());
            java.nio.file.Files.createSymbolicLink(path, realContent);
            createdFiles.add(path);
        } catch (java.io.IOException e) { throw new RuntimeException(e); }
    }

    // ── AC13 helpers ──

    private java.nio.file.Path uploadRoot() {
        return java.nio.file.Path.of(System.getenv().getOrDefault("CQCP_UPLOAD_ROOT", "/data/cqcp/uploads"));
    }
    private void writeFile(java.nio.file.Path p, byte[] data) {
        try { java.nio.file.Files.createDirectories(p.getParent()); java.nio.file.Files.write(p, data); createdFiles.add(p); }
        catch (java.io.IOException e) { throw new RuntimeException(e); }
    }
    private void insertTaskExec(String tId, String eId, String ruleSet, String sfJson, String docRef) {
        var sf = sfJson == null ? "'{}'::jsonb" : "'" + sfJson.replace("'","''") + "'::jsonb";
        jdbcTemplate.update("INSERT INTO task (task_id, caller_type, source_type, contract_name, " +
                "contract_type_code, result_url, structured_fields_snapshot, contract_metadata) " +
                "VALUES (?,'TEST','TEST',?,'ENGINEERING',?," + sf +
                ",('{\"documentReference\":\"' || ? || '\"}')::jsonb)",
                tId, tId, "/r/"+tId, docRef);
        jdbcTemplate.update("INSERT INTO execution (execution_id, task_id, status, current_stage, " +
                "contract_type_profile_version, rule_set_version, review_budget_profile_version, " +
                "model_profile_code, model_config_version, parser_version, prompt_version, " +
                "schema_version, pattern_library_version, field_lexicon_version, evidence_selector_version, " +
                "provider_type, model_name, endpoint_alias, created_at) " +
                "VALUES (?,?,'QUEUED','QUEUED','v20260705.1',?," +
                "'budget-standard-v20260724.1','MVP_DEMO_MOCK','model-config-mvp-demo-mock-v20260724.1'," +
                "'parser-docx-word-v20260724.1','v20260705.1','model-output-artifact-v20260724.1'," +
                "'v20260705.1','v20260705.1','v20260705.1','MOCK','cqcp-demo-mock','mock-local'," +
                "'2000-01-01T00:00:00Z'::timestamptz)",
                eId, tId, ruleSet);
        createdTaskIds.add(tId);
        createdExecutionIds.add(eId);
    }
    private void assertAc13(String eId, String executionId) {
        // FAILED
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM execution WHERE execution_id=?", String.class, eId))
                .isEqualTo("FAILED");
        // All 4 lease fields null
        var lease = jdbcTemplate.queryForMap("SELECT stage_lease_owner, stage_lease_acquired_at, stage_lease_expires_at, heartbeat_at FROM execution WHERE execution_id=?", eId);
        assertThat(lease.get("stage_lease_owner")).isNull();
        assertThat(lease.get("stage_lease_acquired_at")).isNull();
        assertThat(lease.get("stage_lease_expires_at")).isNull();
        assertThat(lease.get("heartbeat_at")).isNull();
        // Exactly 1 FAILED log with fixed message
        var reasons = jdbcTemplate.query(
                "SELECT business_reason FROM task_stage_log WHERE execution_id=? AND event_type='FAILED'",
                (rs,n) -> rs.getString("business_reason"), eId);
        assertThat(reasons).hasSize(1);
        assertThat(reasons.getFirst()).isEqualTo("执行阶段失败");
        // No snapshot
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM review_result_snapshot WHERE execution_id=?", Integer.class, eId)).isZero();
        // Must have at least one controlled log with executionId and errorType=
        assertThat(capturedWorkerLogs).anyMatch(msg -> msg.contains(executionId) && msg.contains("errorType="));
        // Must NOT contain path, documentReference, or contract content
        assertThat(capturedWorkerLogs).noneMatch(msg -> msg.contains("C:") || msg.contains("/data/") || msg.contains("\\\\"));
        assertThat(capturedWorkerLogs).noneMatch(msg -> msg.contains("甲方") || msg.contains("乙方"));
    }
    private static String uuid32() { return java.util.UUID.randomUUID().toString().replace("-", ""); }

    // ── Helpers ──

    // ── Helpers ──

    private MockMultipartFile docxFile(String name) {
        return new MockMultipartFile("file", name,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", MINI_DOCX);
    }
    private MockMultipartFile metaPart(String json) {
        return new MockMultipartFile("metadata", null, "application/json", json.getBytes(StandardCharsets.UTF_8));
    }

    private static final byte[] MINI_DOCX = createMinimalDocx();
    private static byte[] createMinimalDocx() {
        try (var bos = new ByteArrayOutputStream(); var zos = new ZipOutputStream(bos)) {
            zos.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zos.write(("<?xml version=\"1.0\"?>"
                    + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                    + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                    + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                    + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
                    + "</Types>").getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("_rels/.rels"));
            zos.write(("<?xml version=\"1.0\"?>"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>"
                    + "</Relationships>").getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("word/document.xml"));
            zos.write(("<?xml version=\"1.0\"?>"
                    + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
                    + "<w:body><w:p><w:r><w:t>甲方：甲方公司 乙方：乙方公司 合同总价：1130元 预付款：10% 进度款：60% 竣工款：80% 结算款：95% 质保金：5%</w:t></w:r></w:p></w:body></w:document>")
                    .getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            return bos.toByteArray();
        } catch (IOException e) { throw new RuntimeException(e); }
    }

    private static String monthlyMeta() {
        return "{\"contractType\":\"ENGINEERING\",\"structuredFields\":{"
                + "\"contractName\":\"测试合同\",\"partyAName\":\"甲方公司\",\"partyBName\":\"乙方公司\","
                + "\"projectName\":\"测试项目\",\"contractTotalAmount\":1130,\"taxExcludedAmount\":1000,"
                + "\"taxAmount\":130,\"taxRate\":13,\"pricingMode\":\"FIXED_TOTAL_PRICE\","
                + "\"paymentMethod\":\"MONTHLY\",\"invoiceType\":\"VAT_SPECIAL\","
                + "\"prepaymentRatio\":20,\"progressPaymentRatio\":60,\"completionPaymentRatio\":80,"
                + "\"settlementPaymentRatio\":95,\"warrantyRetentionRatio\":5}}";
    }
    private static String milMeta() {
        return "{\"contractType\":\"ENGINEERING\",\"structuredFields\":{"
                + "\"contractName\":\"M合同\",\"partyAName\":\"甲方公司\",\"partyBName\":\"乙方公司\","
                + "\"projectName\":\"M项目\",\"contractTotalAmount\":1130,\"taxExcludedAmount\":1000,"
                + "\"taxAmount\":130,\"taxRate\":13,\"pricingMode\":\"FIXED_TOTAL_PRICE\","
                + "\"paymentMethod\":\"MILESTONE\",\"invoiceType\":\"VAT_SPECIAL\","
                + "\"prepaymentRatio\":10,\"milestonePaymentTerms\":\"按节点付款\"}}";
    }

    private static String extractStr(String json, String key) {
        var search = "\"" + key + "\":\"";
        int s = json.indexOf(search);
        if (s < 0) return "";
        s += search.length();
        int e = json.indexOf("\"", s);
        return e < 0 ? "" : json.substring(s, e);
    }
    private static <K,V> java.util.Map.Entry<K,V> entry(K k, V v) {
        return new java.util.AbstractMap.SimpleImmutableEntry<>(k, v);
    }
    private static String uuid8() {
        return java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
