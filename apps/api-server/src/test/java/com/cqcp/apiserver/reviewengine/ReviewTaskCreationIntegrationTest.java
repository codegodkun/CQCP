package com.cqcp.apiserver.reviewengine;

import static com.cqcp.apiserver.reviewengine.ReviewTaskCreationModels.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.DefaultTransactionStatus;

@SpringBootTest(properties = "cqcp.review.worker.enabled=false")
@AutoConfigureMockMvc
@Import(ReviewTaskCreationIntegrationTest.CommitStageTestConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReviewTaskCreationIntegrationTest {

    // ═══════════════════════════════════════════════════════
    //  Commit-stage injection config
    // ═══════════════════════════════════════════════════════

    @TestConfiguration
    static class CommitStageTestConfig {
        static final AtomicBoolean FAIL_NEXT_COMMIT = new AtomicBoolean(false);

        @Bean
        @Primary
        DataSourceTransactionManager testTxManager(DataSource dataSource) {
            var tm = new DataSourceTransactionManager(dataSource) {
                @Override
                protected void doCommit(DefaultTransactionStatus status) {
                    if (FAIL_NEXT_COMMIT.getAndSet(false)) {
                        throw new TransactionSystemException(
                                "Simulated commit-stage failure for AC7");
                    }
                    super.doCommit(status);
                }
            };
            tm.setRollbackOnCommitFailure(true);
            return tm;
        }
    }

    // ═══════════════════════════════════════════════════════
    //  Upload root override
    // ═══════════════════════════════════════════════════════

    @org.junit.jupiter.api.io.TempDir
    static Path tempUploadDir;

    @DynamicPropertySource
    static void overrideConfig(DynamicPropertyRegistry reg) {
        reg.add("cqcp.review.upload-root", tempUploadDir::toString);
    }

    // ═══════════════════════════════════════════════════════
    //  Injected beans
    // ═══════════════════════════════════════════════════════

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MultipartProperties multipartProperties;

    @SpyBean
    private ReviewTaskCreationRepository repositorySpy;

    // ═══════════════════════════════════════════════════════
    //  Lifecycle
    // ═══════════════════════════════════════════════════════

    @BeforeEach
    void cleanDatabaseAndUploads() {
        jdbcTemplate.update("DELETE FROM point_diagnostic");
        jdbcTemplate.update("DELETE FROM tuning_packet");
        jdbcTemplate.update("DELETE FROM review_result_snapshot");
        jdbcTemplate.update("DELETE FROM task_stage_log");
        jdbcTemplate.update("DELETE FROM execution");
        jdbcTemplate.update("DELETE FROM task");
        // Remove all regular files and empty directories under temp root
        try (var files = Files.walk(tempUploadDir)) {
            files.filter(Files::isRegularFile).forEach(p -> p.toFile().delete());
        } catch (Exception ignored) { }
        // Remove empty dirs
        try (var dirs = Files.walk(tempUploadDir)) {
            dirs.filter(Files::isDirectory)
                    .filter(p -> !p.equals(tempUploadDir))
                    .sorted((a, b) -> b.toString().length() - a.toString().length())
                    .forEach(p -> p.toFile().delete());
        } catch (Exception ignored) { }
        // Reset commit flag + spy
        CommitStageTestConfig.FAIL_NEXT_COMMIT.set(false);
        reset(repositorySpy);
    }

    // ═══════════════════════════════════════════════════════
    //  AC1 — MONTHLY → 202 + 1 Task + 1 QUEUED Execution
    // ═══════════════════════════════════════════════════════

    @Test
    void monthlyRequest_createsOneTaskAndOneExecution() throws Exception {
        var docxBytes = validDocx();

        mockMvc.perform(multipart("/api/review/tasks")
                        .file(docxPart(docxBytes))
                        .file(metadataPart(monthlyJson())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.taskId").isString())
                .andExpect(jsonPath("$.executionId").isString())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.resultUrl").isString());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM execution WHERE status='QUEUED'", Integer.class)).isEqualTo(1);
        // File exists and bytes match input
        var docxFiles = findDocxFiles();
        assertThat(docxFiles).isNotEmpty();
        assertThat(Files.readAllBytes(docxFiles.get(0))).isEqualTo(docxBytes);
    }

    // ═══════════════════════════════════════════════════════
    //  AC2 — MILESTONE
    // ═══════════════════════════════════════════════════════

    @Test
    void milestoneRequest_createsTaskAndExecution() throws Exception {
        mockMvc.perform(multipart("/api/review/tasks")
                        .file(docxPart(validDocx()))
                        .file(metadataPart(milestoneJson())))
                .andExpect(status().isAccepted());
    }

    // ═══════════════════════════════════════════════════════
    //  AC5 — all 14 execution fields
    // ═══════════════════════════════════════════════════════

    @Test
    void execution_14Fields_matchBindingSeed() throws Exception {
        mockMvc.perform(multipart("/api/review/tasks")
                        .file(docxPart(validDocx()))
                        .file(metadataPart(monthlyJson())))
                .andExpect(status().isAccepted());

        var row = jdbcTemplate.queryForMap(
                "SELECT * FROM execution ORDER BY created_at DESC LIMIT 1");
        assertThat(row.get("contract_type_profile_version")).isEqualTo("v20260705.1");
        assertThat(row.get("rule_set_version")).isEqualTo("v20260705.1");
        assertThat(row.get("review_budget_profile_version")).isEqualTo("budget-standard-v20260724.1");
        assertThat(row.get("model_profile_code")).isEqualTo("MVP_DEMO_MOCK");
        assertThat(row.get("model_config_version")).isEqualTo("model-config-mvp-demo-mock-v20260724.1");
        assertThat(row.get("parser_version")).isEqualTo("parser-docx-word-v20260724.1");
        assertThat(row.get("prompt_version")).isEqualTo("v20260705.1");
        assertThat(row.get("schema_version")).isEqualTo("model-output-artifact-v20260724.1");
        assertThat(row.get("pattern_library_version")).isEqualTo("v20260705.1");
        assertThat(row.get("field_lexicon_version")).isEqualTo("v20260705.1");
        assertThat(row.get("evidence_selector_version")).isEqualTo("v20260705.1");
        assertThat(row.get("provider_type")).isEqualTo("MOCK");
        assertThat(row.get("model_name")).isEqualTo("cqcp-demo-mock");
        assertThat(row.get("endpoint_alias")).isEqualTo("mock-local");
    }

    // ═══════════════════════════════════════════════════════
    //  AC4 — task columns
    // ═══════════════════════════════════════════════════════

    @Test
    void task_callerType_isADMIN() throws Exception {
        mockMvc.perform(multipart("/api/review/tasks")
                        .file(docxPart(validDocx()))
                        .file(metadataPart(monthlyJson())))
                .andExpect(status().isAccepted());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT caller_type FROM task LIMIT 1", String.class)).isEqualTo("ADMIN");
    }

    @Test
    void structuredFieldsSnapshot_containsCurrency_whenAbsent() throws Exception {
        var json = monthlyJson().replace(",\"currency\":\"CNY\"", "");
        mockMvc.perform(multipart("/api/review/tasks")
                        .file(docxPart(validDocx()))
                        .file(metadataPart(json)))
                .andExpect(status().isAccepted());
        var snap = jdbcTemplate.queryForObject(
                "SELECT structured_fields_snapshot::text FROM task LIMIT 1", String.class);
        var parsed = objectMapper.readTree(snap);
        assertThat(parsed.has("currency")).isTrue();
        assertThat(parsed.get("currency").asText()).isEqualTo("CNY");
    }

    // ═══════════════════════════════════════════════════════
    //  AC6 — binding unavailable
    // ═══════════════════════════════════════════════════════

    @Test
    void bindingUnavailable_returns409_noRows_noFile() throws Exception {
        jdbcTemplate.update(
                "UPDATE execution_binding_release SET enabled=false "
                        + "WHERE binding_version='mvp-demo-engineering-v20260724.1'");
        try {
            mockMvc.perform(multipart("/api/review/tasks")
                            .file(docxPart(validDocx()))
                            .file(metadataPart(monthlyJson())))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("EXECUTION_BINDING_UNAVAILABLE"))
                    .andExpect(jsonPath("$.retryable").value(false))
                    .andExpect(jsonPath("$.operatorActionRequired").value(true));

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM task", Integer.class)).isZero();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM execution", Integer.class)).isZero();
            assertNoDocxOrTmpFiles();
        } finally {
            jdbcTemplate.update(
                    "UPDATE execution_binding_release SET enabled=true "
                            + "WHERE binding_version='mvp-demo-engineering-v20260724.1'");
        }
    }

    // ═══════════════════════════════════════════════════════
    //  AC7 insert-time — @SpyBean
    // ═══════════════════════════════════════════════════════

    @Test
    void insertTime_executionFailure_rollsBackTaskAndDeletesFile() {
        doThrow(new DataIntegrityViolationException("simulated FK"))
                .when(repositorySpy).insertExecution(any(), any());
        try {
            var ex = assertThrows(ServletException.class,
                    () -> mockMvc.perform(multipart("/api/review/tasks")
                            .file(docxPart(validDocx()))
                            .file(metadataPart(monthlyJson()))));
            // The cause chain should contain DataIntegrityViolationException
            assertThat(findCause(ex, DataIntegrityViolationException.class)).isPresent();

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM task", Integer.class)).isZero();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM execution", Integer.class)).isZero();
            assertNoDocxOrTmpFiles();
        } finally {
            reset(repositorySpy);
        }
    }

    // ═══════════════════════════════════════════════════════
    //  AC7 commit-stage — @Primary failing TM
    // ═══════════════════════════════════════════════════════

    @Test
    void commitStage_failure_triggersSynchronizationCleanup() {
        CommitStageTestConfig.FAIL_NEXT_COMMIT.set(true);
        try {
            var ex = assertThrows(ServletException.class,
                    () -> mockMvc.perform(multipart("/api/review/tasks")
                            .file(docxPart(validDocx()))
                            .file(metadataPart(monthlyJson()))));
            assertThat(findCause(ex, TransactionSystemException.class)).isPresent();

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM task", Integer.class)).isZero();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM execution", Integer.class)).isZero();
            assertNoDocxOrTmpFiles();
        } finally {
            CommitStageTestConfig.FAIL_NEXT_COMMIT.set(false);
        }
    }

    // ═══════════════════════════════════════════════════════
    //  Configuration assertions
    // ═══════════════════════════════════════════════════════

    @Test
    void maxUploadConfiguration_isConsistent() {
        assertThat(multipartProperties.getMaxFileSize().toBytes())
                .isEqualTo(25L * 1024 * 1024)
                .isEqualTo(26_214_400L);
        assertThat(multipartProperties.getMaxRequestSize().toBytes())
                .isEqualTo(26L * 1024 * 1024)
                .isEqualTo(27_262_976L);
    }

    // ═══════════════════════════════════════════════════════
    //  AC3 / AC4 / AC10 / AC11 — ID format, task columns, filename safety, no overwrite
    // ═══════════════════════════════════════════════════════

    @Test
    void ac3_idFormatAndResultUrl() throws Exception {
        var raw = mockMvc.perform(multipart("/api/review/tasks")
                        .file(docxPart(validDocx()))
                        .file(metadataPart(monthlyJson())))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        var res = objectMapper.readValue(raw, CreateReviewTaskResponse.class);

        // taskId = "TASK_" + 32 hex
        assertThat(res.taskId()).matches("TASK_[0-9a-f]{32}");
        assertThat(res.executionId()).matches("EXEC_[0-9a-f]{32}");
        assertThat(res.resultUrl())
                .isEqualTo("/review/results/" + res.taskId() + "?executionId=" + res.executionId());

        // DB IDs match response
        var dbTaskId = jdbcTemplate.queryForObject(
                "SELECT task_id FROM task LIMIT 1", String.class);
        var dbExecId = jdbcTemplate.queryForObject(
                "SELECT execution_id FROM execution LIMIT 1", String.class);
        assertThat(dbTaskId).isEqualTo(res.taskId());
        assertThat(dbExecId).isEqualTo(res.executionId());

        // execution.task_id references the same task
        var execTaskId = jdbcTemplate.queryForObject(
                "SELECT task_id FROM execution LIMIT 1", String.class);
        assertThat(execTaskId).isEqualTo(res.taskId());
    }

    @Test
    void ac4_taskColumns_matchSpec() throws Exception {
        var raw = mockMvc.perform(multipart("/api/review/tasks")
                        .file(docxPart(validDocx()))
                        .file(metadataPart(monthlyJson())))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        var res = objectMapper.readValue(raw, CreateReviewTaskResponse.class);

        var row = jdbcTemplate.queryForMap(
                "SELECT * FROM task ORDER BY created_at DESC LIMIT 1");

        // task_id matches response
        assertThat(row.get("task_id")).isEqualTo(res.taskId());
        assertThat(row.get("caller_id")).isNull();
        assertThat(row.get("caller_type")).isEqualTo("ADMIN");
        assertThat(row.get("source_type")).isEqualTo("DOCX_UPLOAD");
        assertThat(row.get("contract_name")).isEqualTo("t");
        assertThat(row.get("contract_type_code")).isEqualTo("ENGINEERING");
        assertThat(row.get("currency")).isEqualTo("CNY");
        // result_url from DB matches the exact response-sourced URL (not just pattern)
        var actualUrl = (String) row.get("result_url");
        assertThat(actualUrl).isEqualTo(res.resultUrl());
    }

    @Test
    void ac4_contractMetadata_jsonb() throws Exception {
        var docxBytes = validDocx();
        // Use explicit businessDocumentId in metadata
        var metaWithBd = monthlyJsonWithBusinessDocumentId("BD-001");
        mockMvc.perform(multipart("/api/review/tasks")
                        .file(docxPart("custom-file-name.docx", docxBytes))
                        .file(metadataPart(metaWithBd)))
                .andExpect(status().isAccepted());

        var metaJson = jdbcTemplate.queryForObject(
                "SELECT contract_metadata::text FROM task LIMIT 1", String.class);
        var meta = objectMapper.readTree(metaJson);

        assertThat(meta.get("originalFileName").asText()).isEqualTo("custom-file-name.docx");
        assertThat(meta.has("documentReference")).isTrue();
        var docRef = meta.get("documentReference").asText();
        assertThat(docRef).contains("/");
        assertThat(docRef).doesNotContain("..");
        assertThat(docRef).doesNotContain("custom-file-name");
        // sizeBytes must exactly match the input byte count
        assertThat(meta.get("sizeBytes").asLong()).isEqualTo(docxBytes.length);
        // businessDocumentId was sent explicitly
        assertThat(meta.get("businessDocumentId").asText()).isEqualTo("BD-001");
    }

    @Test
    void ac4_structuredFieldsSnapshot_monthly() throws Exception {
        mockMvc.perform(multipart("/api/review/tasks")
                        .file(docxPart(validDocx()))
                        .file(metadataPart(monthlyJson())))
                .andExpect(status().isAccepted());

        var snap = jdbcTemplate.queryForObject(
                "SELECT structured_fields_snapshot::text FROM task LIMIT 1", String.class);
        var parsed = objectMapper.readTree(snap);

        assertThat(parsed.get("contractName").asText()).isEqualTo("t");
        assertThat(parsed.get("paymentMethod").asText()).isEqualTo("MONTHLY");
        assertThat(parsed.get("prepaymentRatio").asInt()).isEqualTo(30);
        assertThat(parsed.get("milestonePaymentTerms")).isNull(); // not in MONTHLY
        assertThat(parsed.get("currency").asText()).isEqualTo("CNY");
    }

    @Test
    void ac4_structuredFieldsSnapshot_milestone() throws Exception {
        mockMvc.perform(multipart("/api/review/tasks")
                        .file(docxPart(validDocx()))
                        .file(metadataPart(milestoneJson())))
                .andExpect(status().isAccepted());

        var snap = jdbcTemplate.queryForObject(
                "SELECT structured_fields_snapshot::text FROM task LIMIT 1", String.class);
        var parsed = objectMapper.readTree(snap);

        assertThat(parsed.get("paymentMethod").asText()).isEqualTo("MILESTONE");
        assertThat(parsed.get("milestonePaymentTerms").asText()).isEqualTo("里程碑1时付30%");
        // MONTHLY-only fields must be absent from snapshot
        assertThat(parsed.has("progressPaymentRatio")).isFalse();
        assertThat(parsed.has("warrantyRetentionRatio")).isFalse();
    }

    @Test
    void ac10_pathTraversalFilenames_allUseSafeRef() throws Exception {
        var dangerousNames = java.util.List.of(
                "../../etc/passwd.docx",
                "..\\..\\Windows\\secret.docx",
                "/etc/passwd.docx",
                "C:\\Windows\\secret.docx");
        for (var traversalName : dangerousNames) {
            // clean DB for each iteration
            jdbcTemplate.update("DELETE FROM execution");
            jdbcTemplate.update("DELETE FROM task");
            // clean upload files from previous iteration
            try (var files = Files.walk(tempUploadDir)) {
                files.filter(Files::isRegularFile).forEach(p -> p.toFile().delete());
            } catch (Exception ignored) { }

            var raw = mockMvc.perform(multipart("/api/review/tasks")
                            .file(new MockMultipartFile("file", traversalName,
                                    "application/vnd.openxmlformats-officedocument" +
                                            ".wordprocessingml.document", validDocx()))
                            .file(metadataPart(monthlyJson())))
                    .andExpect(status().isAccepted())
                    .andReturn().getResponse().getContentAsString();
            var res = objectMapper.readValue(raw, CreateReviewTaskResponse.class);

            // Query by the response taskId — deterministic, no LIMIT 1 ambiguity
            var metaJson = jdbcTemplate.queryForObject(
                    "SELECT contract_metadata::text FROM task WHERE task_id = ?",
                    String.class, res.taskId());
            var meta = objectMapper.readTree(metaJson);

            // Original filename is preserved in metadata
            assertThat(meta.get("originalFileName").asText())
                    .as("originalFileName preserved for: " + traversalName)
                    .isEqualTo(traversalName);

            var docRef = meta.get("documentReference").asText();
            // Reference must be task-scoped random path
            assertThat(docRef).matches("TASK_[0-9a-f]{32}/[0-9a-f]{32}\\.docx");
            // The file on disk must be under upload root
            var resolved = tempUploadDir.resolve(docRef).normalize();
            assertThat(resolved).as("file exists for: " + traversalName).exists();
            assertThat(resolved.toRealPath()).startsWith(tempUploadDir.toRealPath());
        }
    }

    @Test
    void ac11_duplicateFilename_doesNotOverwrite() throws Exception {
        var sameName = "repeat.docx";
        var docx1 = validDocx();
        var docx2 = modifyDocx(docx1, "MODIFIED"); // second file with same name, different content

        // First request
        mockMvc.perform(multipart("/api/review/tasks")
                        .file(new MockMultipartFile("file", sameName,
                                "application/vnd.openxmlformats-officedocument" +
                                        ".wordprocessingml.document", docx1))
                        .file(metadataPart(monthlyJson())))
                .andExpect(status().isAccepted());
        var meta1 = objectMapper.readTree(jdbcTemplate.queryForObject(
                "SELECT contract_metadata::text FROM task ORDER BY created_at DESC LIMIT 1", String.class));
        var ref1 = meta1.get("documentReference").asText();

        // clean DB (but leave files for comparison)
        jdbcTemplate.update("DELETE FROM execution");
        jdbcTemplate.update("DELETE FROM task");

        // Second request, same filename
        mockMvc.perform(multipart("/api/review/tasks")
                        .file(new MockMultipartFile("file", sameName,
                                "application/vnd.openxmlformats-officedocument" +
                                        ".wordprocessingml.document", docx2))
                        .file(metadataPart(monthlyJson())))
                .andExpect(status().isAccepted());
        var meta2 = objectMapper.readTree(jdbcTemplate.queryForObject(
                "SELECT contract_metadata::text FROM task ORDER BY created_at DESC LIMIT 1", String.class));
        var ref2 = meta2.get("documentReference").asText();

        // Different references, both files exist and have original content
        assertThat(ref1).isNotEqualTo(ref2);
        assertThat(tempUploadDir.resolve(ref1)).exists();
        assertThat(tempUploadDir.resolve(ref2)).exists();
        assertThat(readBytes(tempUploadDir.resolve(ref1))).isEqualTo(docx1);
        assertThat(readBytes(tempUploadDir.resolve(ref2))).isEqualTo(docx2);
    }

    // ═══════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════

    private static MockMultipartFile docxPart(byte[] docx) {
        return docxPart("t.docx", docx);
    }

    private static MockMultipartFile docxPart(String filename, byte[] docx) {
        return new MockMultipartFile("file", filename,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docx);
    }

    /** Create a fresh DOCX differing from the first byte of the original content. */
    static byte[] modifyDocx(byte[] original, String marker) {
        try {
            // Rewrite word/document.xml content with marker
            var baos = new java.io.ByteArrayOutputStream();
            try (var zos = new java.util.zip.ZipOutputStream(baos)) {
                // Copy all entries from the original, modifying word/document.xml
                try (var zis = new java.util.zip.ZipInputStream(
                        new java.io.ByteArrayInputStream(original))) {
                    java.util.zip.ZipEntry entry;
                    while ((entry = zis.getNextEntry()) != null) {
                        zos.putNextEntry(new java.util.zip.ZipEntry(entry.getName()));
                        if ("word/document.xml".equals(entry.getName())) {
                            zos.write(("<?xml version=\"1.0\"?><w:document>"
                                    + "<w:body><w:p><w:r><w:t>" + marker
                                    + "</w:t></w:r></w:p></w:body></w:document>")
                                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        } else {
                            zis.transferTo(zos);
                        }
                        zos.closeEntry();
                    }
                }
            }
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] readBytes(Path p) {
        try { return Files.readAllBytes(p); } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static MockMultipartFile metadataPart(String json) {
        return new MockMultipartFile("metadata", null,
                MediaType.APPLICATION_JSON_VALUE,
                json.getBytes(StandardCharsets.UTF_8));
    }

    private static String monthlyJsonWithBusinessDocumentId(String bdId) {
        return "{\"contractType\":\"ENGINEERING\",\"businessDocumentId\":\"" + bdId + "\",\"structuredFields\":{"
                + "\"contractName\":\"t\",\"partyAName\":\"A\",\"partyBName\":\"B\","
                + "\"projectName\":\"P\",\"contractTotalAmount\":100,\"taxExcludedAmount\":90,"
                + "\"taxAmount\":10,\"taxRate\":13,\"pricingMode\":\"FIXED_TOTAL_PRICE\","
                + "\"paymentMethod\":\"MONTHLY\",\"invoiceType\":\"VAT_SPECIAL\","
                + "\"prepaymentRatio\":30,\"progressPaymentRatio\":60,"
                + "\"completionPaymentRatio\":80,\"settlementPaymentRatio\":95,"
                + "\"warrantyRetentionRatio\":5,\"currency\":\"CNY\"}}";
    }

    private static String monthlyJson() {
        return "{\"contractType\":\"ENGINEERING\",\"structuredFields\":{"
                + "\"contractName\":\"t\",\"partyAName\":\"A\",\"partyBName\":\"B\","
                + "\"projectName\":\"P\",\"contractTotalAmount\":100,\"taxExcludedAmount\":90,"
                + "\"taxAmount\":10,\"taxRate\":13,\"pricingMode\":\"FIXED_TOTAL_PRICE\","
                + "\"paymentMethod\":\"MONTHLY\",\"invoiceType\":\"VAT_SPECIAL\","
                + "\"prepaymentRatio\":30,\"progressPaymentRatio\":60,"
                + "\"completionPaymentRatio\":80,\"settlementPaymentRatio\":95,"
                + "\"warrantyRetentionRatio\":5,\"currency\":\"CNY\"}}";
    }

    private static String milestoneJson() {
        return "{\"contractType\":\"ENGINEERING\",\"structuredFields\":{"
                + "\"contractName\":\"m\",\"partyAName\":\"A\",\"partyBName\":\"B\","
                + "\"projectName\":\"P\",\"contractTotalAmount\":200,\"taxExcludedAmount\":180,"
                + "\"taxAmount\":20,\"taxRate\":13,\"pricingMode\":\"PROVISIONAL_TOTAL_PRICE\","
                + "\"paymentMethod\":\"MILESTONE\",\"invoiceType\":\"VAT_GENERAL\","
                + "\"prepaymentRatio\":30,\"milestonePaymentTerms\":\"里程碑1时付30%\","
                + "\"currency\":\"CNY\"}}";
    }

    static byte[] validDocx() {
        try {
            var baos = new ByteArrayOutputStream();
            try (var zos = new ZipOutputStream(baos)) {
                zos.putNextEntry(new ZipEntry("[Content_Types].xml"));
                zos.write(("<?xml version=\"1.0\"?>"
                        + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                        + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                        + "<Default Extension=\"xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
                        + "</Types>").getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
                zos.putNextEntry(new ZipEntry("word/document.xml"));
                zos.write("<?xml version=\"1.0\"?><w:document xmlns:w=\"http://schemas.openxmlformats"
                        .getBytes(StandardCharsets.UTF_8));
                zos.write("-org/wordprocessingml/2006/main\"><w:body><w:p><w:r><w:t>Test</w:t>"
                        .getBytes(StandardCharsets.UTF_8));
                zos.write("</w:r></w:p></w:body></w:document>".getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
                zos.putNextEntry(new ZipEntry("_rels/.rels"));
                zos.write(("<?xml version=\"1.0\"?>"
                        + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                        + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>"
                        + "</Relationships>").getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private java.util.List<Path> findDocxFiles() {
        try (var files = Files.walk(tempUploadDir)) {
            return files.filter(p -> p.toString().endsWith(".docx")).toList();
        } catch (Exception e) { return java.util.List.of(); }
    }

    private void assertNoDocxOrTmpFiles() {
        try (var files = Files.walk(tempUploadDir)) {
            assertThat(
                    files.filter(p -> p.toString().endsWith(".docx")
                            || p.toString().endsWith(".tmp"))
                            .count())
                    .as("no .docx or .tmp files")
                    .isZero();
        } catch (Exception ignored) { }
    }

    private static <T> java.util.Optional<T> findCause(Throwable ex, Class<T> type) {
        for (var c = ex; c != null; c = c.getCause())
            if (type.isInstance(c)) return java.util.Optional.of(type.cast(c));
        return java.util.Optional.empty();
    }
}
