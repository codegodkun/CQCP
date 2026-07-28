package com.cqcp.apiserver.reviewengine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A → C → B → C integration test for the Execution Status Query API.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "cqcp.review.worker.enabled=false",
        "spring.flyway.enabled=true",
})
// The dynamic upload-root points at a per-run temp dir; drop this context after the
// class so the cached-context pool never reuses a stale path.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReviewExecutionStatusIntegrationTest {

    // Uploaded files must land in a JUnit-managed temp dir (auto-cleaned),
    // never in the repository data/ directory.
    @org.junit.jupiter.api.io.TempDir
    static Path tempUploadDir;

    @DynamicPropertySource
    static void overrideUploadRoot(DynamicPropertyRegistry reg) {
        reg.add("cqcp.review.upload-root", tempUploadDir::toString);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private SingleReviewWorker worker;
    @SpyBean private JdbcTemplate jdbcTemplateSpy;

    private final List<String> createdTaskIds = new ArrayList<>();
    private final List<String> createdExecutionIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (var eid : createdExecutionIds) {
            jdbcTemplate.update("DELETE FROM point_diagnostic WHERE execution_id=?", eid);
            jdbcTemplate.update("DELETE FROM tuning_packet WHERE execution_id=?", eid);
            jdbcTemplate.update("DELETE FROM review_result_snapshot WHERE execution_id=?", eid);
            jdbcTemplate.update("DELETE FROM task_stage_log WHERE execution_id=?", eid);
            jdbcTemplate.update("DELETE FROM execution WHERE execution_id=?", eid);
        }
        for (var tid : createdTaskIds) {
            jdbcTemplate.update("DELETE FROM execution WHERE task_id=?", tid);
            int d = jdbcTemplate.update("DELETE FROM task WHERE task_id=?", tid);
            if (d != 1) throw new IllegalStateException("cleanup failed: " + tid);
        }
        createdTaskIds.clear();
        createdExecutionIds.clear();
    }

    /** Create a task + execution via A POST, return executionId. */
    private String createTaskViaPost() throws Exception {
        var metadata = """
                {"contractType":"ENGINEERING","structuredFields":{
                    "contractName":"测试合同","partyAName":"甲方公司","partyBName":"乙方公司",
                    "projectName":"测试项目","contractTotalAmount":1130,"taxExcludedAmount":1000,
                    "taxAmount":130,"taxRate":13,"pricingMode":"FIXED_TOTAL_PRICE",
                    "paymentMethod":"MONTHLY","invoiceType":"VAT_SPECIAL",
                    "prepaymentRatio":20,"progressPaymentRatio":60,"completionPaymentRatio":80,
                    "settlementPaymentRatio":95,"warrantyRetentionRatio":5}}
                """;
        var result = mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/review/tasks")
                        .file(new MockMultipartFile("file", "test.docx",
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                createMinimalDocx()))
                        .file(new MockMultipartFile("metadata", null, "application/json",
                                metadata.getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isAccepted()).andReturn();

        var body = result.getResponse().getContentAsString();
        var taskId = extractStr(body, "taskId");
        var executionId = extractStr(body, "executionId");
        createdTaskIds.add(taskId);
        createdExecutionIds.add(executionId);
        return taskId;
    }

    /** Create a bare-bones task + execution via SQL for 404 tests. */
    private String seedTask() {
        var tid = "cit-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        jdbcTemplate.update("INSERT INTO task (task_id, caller_type, source_type, contract_name, " +
                "contract_type_code, result_url, structured_fields_snapshot) " +
                "VALUES (?,'TEST','TEST',?,'ENGINEERING',?,'{}')",
                tid, "CIT-" + tid, "/r/" + tid);
        createdTaskIds.add(tid);
        return tid;
    }

    private String seedExecution(String taskId) {
        var eid = "cie-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        jdbcTemplate.update("INSERT INTO execution (execution_id, task_id, status, current_stage, " +
                "contract_type_profile_version, rule_set_version, review_budget_profile_version, " +
                "model_profile_code, model_config_version, parser_version, prompt_version, " +
                "schema_version, pattern_library_version, field_lexicon_version, evidence_selector_version, " +
                "provider_type, model_name, endpoint_alias) " +
                "VALUES (?,?,'QUEUED','QUEUED','v20260705.1','v20260705.1'," +
                "'budget-standard-v20260724.1','MVP_DEMO_MOCK','model-config-mvp-demo-mock-v20260724.1'," +
                "'parser-docx-word-v20260724.1','v20260705.1','model-output-artifact-v20260724.1'," +
                "'v20260705.1','v20260705.1','v20260705.1','MOCK','cqcp-demo-mock','mock-local')",
                eid, taskId);
        createdExecutionIds.add(eid);
        return eid;
    }

    // ── AC5: 3 real 404 paths with spy ──

    @Test
    void ac5_taskNotFound_returns404() throws Exception {
        mockMvc.perform(get("/api/review/tasks/no-such/executions/any-exec"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REVIEW_EXECUTION_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("未找到指定审核执行"))
                .andExpect(jsonPath("$.reason").doesNotExist())
                .andExpect(jsonPath("$.retryable").doesNotExist())
                .andExpect(jsonPath("$.operatorActionRequired").doesNotExist());
    }

    @Test
    void ac5_executionNotFound_returns404() throws Exception {
        var taskId = seedTask();
        assert404(taskId, "no-such-exec");
    }

    @Test
    void ac5_crossTaskExecution_returns404() throws Exception {
        var taskId1 = seedTask();
        var taskId2 = seedTask();
        var execId2 = seedExecution(taskId2);
        assert404(taskId1, execId2);
    }

    private void assert404(String taskId, String executionId) throws Exception {
        mockMvc.perform(get("/api/review/tasks/{tid}/executions/{eid}", taskId, executionId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REVIEW_EXECUTION_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("未找到指定审核执行"))
                .andExpect(jsonPath("$.reason").doesNotExist());
    }

    @Test
    void ac5_onlyOneQuery_noSecondProbe() throws Exception {
        seedTask();
        org.mockito.Mockito.clearInvocations(jdbcTemplateSpy);
        var xTaskId = "xtra-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        mockMvc.perform(get("/api/review/tasks/{tid}/executions/e-none", xTaskId))
                .andExpect(status().isNotFound());
        verify(jdbcTemplateSpy, times(1))
                .query(anyString(), any(RowMapper.class), anyString(), anyString());
    }

    // ── AC13: A POST → QUEUED ──

    @Test
    void ac13_afterACreate_returnsQueuedStatus() throws Exception {
        var body = mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/review/tasks")
                        .file(new MockMultipartFile("file", "t.docx",
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                createMinimalDocx()))
                        .file(new MockMultipartFile("metadata", null, "application/json",
                                ("{\"contractType\":\"ENGINEERING\",\"structuredFields\":{"
                                + "\"contractName\":\"t\",\"partyAName\":\"A\",\"partyBName\":\"B\","
                                + "\"projectName\":\"P\",\"contractTotalAmount\":100,\"taxExcludedAmount\":90,"
                                + "\"taxAmount\":10,\"taxRate\":13,\"pricingMode\":\"FIXED_TOTAL_PRICE\","
                                + "\"paymentMethod\":\"MONTHLY\",\"invoiceType\":\"VAT_SPECIAL\","
                                + "\"prepaymentRatio\":30,\"progressPaymentRatio\":60,"
                                + "\"completionPaymentRatio\":80,\"settlementPaymentRatio\":95,"
                                + "\"warrantyRetentionRatio\":5}}").getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        var taskId = extractStr(body, "taskId");
        var execId = extractStr(body, "executionId");
        createdTaskIds.add(taskId);
        createdExecutionIds.add(execId);

        jdbcTemplate.update("UPDATE execution SET created_at='2000-01-01T00:00:00Z'::timestamptz WHERE execution_id=?", execId);

        // Immediately query C
        mockMvc.perform(get("/api/review/tasks/{tid}/executions/{eid}", taskId, execId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.currentStage").value("QUEUED"))
                .andExpect(jsonPath("$.terminal").value(false))
                .andExpect(jsonPath("$.snapshotAvailable").value(false));
    }

    // ── AC14: A → B runOnce → C terminal ──

    @Test
    void ac14_afterBRunOnce_returnsTerminal() throws Exception {
        var taskId = createTaskViaPost();
        var execId = createdExecutionIds.getLast();

        jdbcTemplate.update("UPDATE execution SET created_at='2000-01-01T00:00:00Z'::timestamptz WHERE execution_id=?", execId);

        // B
        worker.runOnce();

        // C: must be SUCCESS or PARTIAL_SUCCESS
        var response = mockMvc.perform(get("/api/review/tasks/{tid}/executions/{eid}", taskId, execId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(
                        org.hamcrest.Matchers.anyOf(
                                org.hamcrest.Matchers.is("SUCCESS"),
                                org.hamcrest.Matchers.is("PARTIAL_SUCCESS"))))
                .andExpect(jsonPath("$.terminal").value(true))
                .andExpect(jsonPath("$.snapshotAvailable").value(true))
                .andExpect(jsonPath("$.resultUrl").isString())
                .andReturn().getResponse().getContentAsString();
        assertThat(response).contains("currentStage");
    }

    // ── AC15: B failure via unsupported rule_set_version ──

    @Test
    void ac15_failedExecutionViaUnsupportedRuleSet() throws Exception {
        var taskId = createTaskViaPost();
        var execId = createdExecutionIds.getLast();

        jdbcTemplate.update("UPDATE execution SET created_at='2000-01-01T00:00:00Z'::timestamptz "
                + "WHERE execution_id=?", execId);
        // Set unsupported rule_set_version
        jdbcTemplate.update("UPDATE execution SET rule_set_version='unsupported-v999' "
                + "WHERE execution_id=?", execId);

        worker.runOnce();

        // C: must be FAILED
        mockMvc.perform(get("/api/review/tasks/{tid}/executions/{eid}", taskId, execId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.currentStage").value("QUEUED"))
                .andExpect(jsonPath("$.terminal").value(true))
                .andExpect(jsonPath("$.snapshotAvailable").value(false))
                .andExpect(jsonPath("$.resultUrl").isString())
                // Prohibited fields
                .andExpect(jsonPath("$.stageLog").doesNotExist())
                .andExpect(jsonPath("$.businessReason").doesNotExist())
                .andExpect(jsonPath("$.diagnosticCode").doesNotExist())
                .andExpect(jsonPath("$.endpointUrl").doesNotExist());
    }

    // ── AC16: read-only evidence via spy ──

    @Test
    void ac16_happyPath_repositoryOnlySelects() throws Exception {
        var taskId = createTaskViaPost();
        var execId = createdExecutionIds.getLast();
        jdbcTemplate.update("UPDATE execution SET created_at='2000-01-01T00:00:00Z'::timestamptz WHERE execution_id=?", execId);
        org.mockito.Mockito.clearInvocations(jdbcTemplateSpy);

        mockMvc.perform(get("/api/review/tasks/{tid}/executions/{eid}", taskId, execId))
                .andExpect(status().isOk());

        verify(jdbcTemplateSpy, times(1))
                .query(anyString(), any(RowMapper.class), anyString(), anyString());
    }

    // ── resultUrl via integration ──

    @Test
    void ac7_resultUrlDirect() throws Exception {
        var taskId = createTaskViaPost();
        var execId = createdExecutionIds.getLast();
        jdbcTemplate.update("UPDATE execution SET created_at='2000-01-01T00:00:00Z'::timestamptz WHERE execution_id=?", execId);

        mockMvc.perform(get("/api/review/tasks/{tid}/executions/{eid}", taskId, execId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultUrl").value(
                        "/review/results/" + taskId + "?executionId=" + execId));
    }

    // ── Helpers ──

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
                    + "<w:body><w:p><w:r><w:t>甲方：甲方公司 合同总价：1000元</w:t></w:r></w:p></w:body></w:document>")
                    .getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            return bos.toByteArray();
        } catch (IOException e) { throw new RuntimeException(e); }
    }

    private static String extractStr(String json, String key) {
        var search = "\"" + key + "\":\"";
        int s = json.indexOf(search);
        if (s < 0) return "";
        s += search.length();
        int e = json.indexOf("\"", s);
        return e < 0 ? "" : json.substring(s, e);
    }
}
