package com.cqcp.apiserver.reviewengine;

import static com.cqcp.apiserver.reviewengine.ReviewTaskCreationModels.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class ReviewTaskCreationServiceTest {

    private static final long MAX_UPLOAD = 25L * 1024 * 1024;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    // Dedicated mapper preserving BigDecimal lexical scale (same as production)
    private static final ObjectMapper BIG_DECIMAL_MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .nodeFactory(JsonNodeFactory.withExactBigDecimals(true))
            .build();

    @Mock
    private ReviewTaskCreationRepository repository;

    @Mock
    private ExecutionBindingCatalog catalog;

    @Mock
    private LocalReviewDocumentStore documentStore;

    private ReviewTaskCreationService service;

    @BeforeEach
    void setUp() {
        service = new ReviewTaskCreationService(repository, catalog, documentStore, MAX_UPLOAD);
    }

    // ==================== Helpers ====================

    private static CallBuilder validMonthly() {
        return new CallBuilder()
                .put("contractName", "test").put("partyAName", "A").put("partyBName", "B")
                .put("projectName", "P").put("contractTotalAmount", 100)
                .put("taxExcludedAmount", 90).put("taxAmount", 10).put("taxRate", 13)
                .put("pricingMode", "FIXED_TOTAL_PRICE").put("paymentMethod", "MONTHLY")
                .put("invoiceType", "VAT_SPECIAL").put("currency", "CNY")
                .put("prepaymentRatio", 30).put("progressPaymentRatio", 60)
                .put("completionPaymentRatio", 80).put("settlementPaymentRatio", 95)
                .put("warrantyRetentionRatio", 5);
    }

    private static CallBuilder validMilestone() {
        return new CallBuilder()
                .put("contractName", "m").put("partyAName", "A").put("partyBName", "B")
                .put("projectName", "P").put("contractTotalAmount", 200)
                .put("taxExcludedAmount", 180).put("taxAmount", 20).put("taxRate", 13)
                .put("pricingMode", "PROVISIONAL_TOTAL_PRICE").put("paymentMethod", "MILESTONE")
                .put("invoiceType", "VAT_GENERAL").put("currency", "CNY")
                .put("prepaymentRatio", 30)
                .put("milestonePaymentTerms", "里程碑1时付30%");
    }

    /** Build {@code {"contractType":"ENGINEERING","structuredFields":{...}}} */
    static class CallBuilder {
        private final ObjectNode sf = MAPPER.createObjectNode();
        private String contractType = "ENGINEERING";
        private JsonNode contractTypeRaw; // non-null overrides contractType string

        CallBuilder put(String k, String v) { sf.put(k, v); return this; }
        CallBuilder put(String k, int v) { sf.put(k, v); return this; }
        CallBuilder putRaw(String k, String rawJson) {
            // BIG_DECIMAL_MAPPER preserves lexical BigDecimal scale (100.000 → scale 3)
            try { sf.set(k, BIG_DECIMAL_MAPPER.readTree(rawJson)); } catch (Exception e) { throw new RuntimeException(e); }
            return this;
        }
        CallBuilder contractType(String ct) { this.contractType = ct; return this; }
        /** Set contractType to a raw JsonNode (e.g. numeric 123 for type-error tests). */
        CallBuilder contractTypeRaw(String rawJson) {
            try { return contractTypeRaw(BIG_DECIMAL_MAPPER.readTree(rawJson)); } catch (Exception e) { throw new RuntimeException(e); }
        }
        CallBuilder contractTypeRaw(JsonNode raw) {
            // Mark as used for raw build
            this.contractType = null; // null signals raw path
            this.contractTypeRaw = raw;
            return this;
        }
        CallBuilder remove(String field) { sf.remove(field); return this; }

        String build() {
            try {
                var root = MAPPER.createObjectNode();
                if (contractTypeRaw != null) {
                    root.set("contractType", contractTypeRaw);
                } else {
                    root.put("contractType", contractType);
                }
                root.set("structuredFields", sf);
                return MAPPER.writeValueAsString(root);
            } catch (Exception e) { throw new RuntimeException(e); }
        }
    }

    private void call(CallBuilder b) {
        service.createTask("t.docx", streamOf("x"), 10, b.build());
    }

    private ValidationException assertVal(CallBuilder b) {
        return assertThrows(ValidationException.class,
                () -> service.createTask("t.docx", streamOf("x"), 10, b.build()));
    }

    private static InputStream streamOf(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }

    private static void set(String field, String code) {
        // used in validation chain
    }

    // ==================== INVALID_JSON ====================

    @Test
    void rejects_emptyMetadataJson() {
        var ex = assertThrows(ValidationException.class,
                () -> service.createTask("t.docx", streamOf("x"), 10, ""));
        assertThat(ex.getFieldErrors()).anyMatch(e ->
                e.field().equals("metadata") && e.code().equals("INVALID_JSON"));
    }

    @Test
    void rejects_nullMetadataJson() {
        var ex = assertThrows(ValidationException.class,
                () -> service.createTask("t.docx", streamOf("x"), 10, null));
        assertThat(ex.getFieldErrors()).anyMatch(e ->
                e.field().equals("metadata") && e.code().equals("INVALID_JSON"));
    }

    // ==================== INVALID_FIELD_TYPE ====================

    @Test
    void rejects_contractTypeNonString() {
        // contractType as raw number 123 → INVALID_FIELD_TYPE, not enum check
        var ex = assertVal(new CallBuilder().contractTypeRaw("123"));
        assertThat(ex.getFieldErrors()).anyMatch(e ->
                e.field().equals("contractType") && e.code().equals("INVALID_FIELD_TYPE"));
    }

    @Test
    void rejects_structuredFieldsNonObject() {
        var json = "{\"contractType\":\"ENGINEERING\",\"structuredFields\":null}";
        var ex = assertThrows(ValidationException.class,
                () -> service.createTask("t.docx", streamOf("x"), 10, json));
        assertThat(ex.getFieldErrors()).anyMatch(e ->
                e.field().equals("structuredFields") && e.code().equals("INVALID_FIELD_TYPE"));
    }

    @Test
    void rejects_stringFieldAsNumber() {
        var b = validMonthly().putRaw("contractName", "123");
        var ex = assertVal(b);
        assertContains(ex, "structuredFields.contractName", "INVALID_FIELD_TYPE");
    }

    @Test
    void rejects_numberFieldAsString() {
        var b = validMonthly().putRaw("contractTotalAmount", "\"not-a-number\"");
        var ex = assertVal(b);
        assertContains(ex, "structuredFields.contractTotalAmount", "INVALID_FIELD_TYPE");
    }

    @Test
    void rejects_currencyAsNull() {
        var b = validMonthly().putRaw("currency", "null");
        var ex = assertVal(b);
        assertContains(ex, "structuredFields.currency", "INVALID_FIELD_TYPE");
    }

    @Test
    void rejects_businessDocumentIdAsNumber() {
        var b = validMonthly();
        b.sf.put("businessDocumentId", 123);  // metadata root level
        // This gets caught by unknown field check since businessDocumentId is in metadata root
        // Actually, businessDocumentId IS known. But it will fail type check.
        var json = "{\"contractType\":\"ENGINEERING\",\"businessDocumentId\":123,\"structuredFields\":"
                + b.sf.toString() + "}";
        var ex = assertThrows(ValidationException.class,
                () -> service.createTask("t.docx", streamOf("x"), 10, json));
        assertContains(ex, "businessDocumentId", "INVALID_FIELD_TYPE");
    }

    // ==================== UNKNOWN_FIELD ====================

    @Test
    void rejects_unknownMetadataField() {
        var json = "{\"contractType\":\"ENGINEERING\",\"structuredFields\":"
                + validMonthly().sf.toString() + ",\"extra\":\"x\"}";
        var ex = assertThrows(ValidationException.class,
                () -> service.createTask("t.docx", streamOf("x"), 10, json));
        assertContains(ex, "extra", "UNKNOWN_FIELD");
    }

    @Test
    void rejects_unknownStructuredField() {
        var b = validMonthly().put("fooBar", "x");
        var ex = assertVal(b);
        assertContains(ex, "structuredFields.fooBar", "UNKNOWN_FIELD");
    }

    // ==================== REQUIRED_FIELD_MISSING ====================

    @Test
    void rejects_missingContractName() {
        var ex = assertVal(validMonthly().remove("contractName"));
        assertContains(ex, "structuredFields.contractName", "REQUIRED_FIELD_MISSING");
    }

    @Test
    void rejects_contractNameBlank() {
        var ex = assertVal(validMonthly().put("contractName", "  "));
        assertContains(ex, "structuredFields.contractName", "REQUIRED_FIELD_MISSING");
    }

    @Test
    void rejects_missingContractType() {
        var json = "{\"structuredFields\":" + validMonthly().sf.toString() + "}";
        var ex = assertThrows(ValidationException.class,
                () -> service.createTask("t.docx", streamOf("x"), 10, json));
        assertContains(ex, "contractType", "REQUIRED_FIELD_MISSING");
    }

    // ==================== CONDITIONAL_FIELD_MISSING ====================

    @Test
    void rejects_missingMonthlyPrepaymentRatio() {
        var ex = assertVal(validMonthly().remove("prepaymentRatio"));
        assertContains(ex, "structuredFields.prepaymentRatio", "CONDITIONAL_FIELD_MISSING");
    }

    @Test
    void rejects_missingMilestonePaymentTerms() {
        var ex = assertVal(validMilestone().remove("milestonePaymentTerms"));
        assertContains(ex, "structuredFields.milestonePaymentTerms", "CONDITIONAL_FIELD_MISSING");
    }

    // ==================== INVALID_STRING_LENGTH ====================

    @Test
    void rejects_contractNameTooLong() {
        var longName = "A".repeat(256);
        var ex = assertVal(validMonthly().put("contractName", longName));
        assertContains(ex, "structuredFields.contractName", "INVALID_STRING_LENGTH");
    }

    // ==================== INVALID_ENUM_VALUE ====================

    @Test
    void rejects_contractTypeMISC() {
        var b = validMonthly().contractType("MISC");
        var ex = assertVal(b);
        assertContains(ex, "contractType", "INVALID_ENUM_VALUE");
    }

    @Test
    void rejects_invalidPricingMode() {
        var ex = assertVal(validMonthly().put("pricingMode", "INVALID"));
        assertContains(ex, "structuredFields.pricingMode", "INVALID_ENUM_VALUE");
    }

    // ==================== INVALID_DECIMAL_SCALE (exact BigDecimal) ====================

    @Test
    void accepts_amountScale2() {
        var b = validMonthly().putRaw("contractTotalAmount", "100.00");
        when(documentStore.save(anyString(), any())).thenReturn(100L);
        when(catalog.resolveDefault("MVP_DEMO", "DEMO", "ENGINEERING"))
                .thenReturn(validBinding());
        TransactionSynchronizationManager.initSynchronization();
        try {
            call(b);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        // no exception — success
    }

    @Test
    void rejects_amountScale3() {
        var b = validMonthly().putRaw("contractTotalAmount", "100.000");
        var ex = assertVal(b);
        assertContains(ex, "structuredFields.contractTotalAmount", "INVALID_DECIMAL_SCALE");
    }

    @Test
    void rejects_taxRateScale5() {
        var b = validMonthly().putRaw("taxRate", "13.12345");
        var ex = assertVal(b);
        assertContains(ex, "structuredFields.taxRate", "INVALID_DECIMAL_SCALE");
    }

    // ==================== INVALID_PERCENT_RANGE ====================

    @Test
    void rejects_taxRateNegative() {
        var ex = assertVal(validMonthly().put("taxRate", -1));
        assertContains(ex, "structuredFields.taxRate", "INVALID_PERCENT_RANGE");
    }

    @Test
    void rejects_taxRateOver100() {
        var ex = assertVal(validMonthly().put("taxRate", 101));
        assertContains(ex, "structuredFields.taxRate", "INVALID_PERCENT_RANGE");
    }

    // ==================== UNSUPPORTED_CURRENCY ====================

    @Test
    void rejects_usdCurrency() {
        var ex = assertVal(validMonthly().put("currency", "USD"));
        assertContains(ex, "structuredFields.currency", "UNSUPPORTED_CURRENCY");
    }

    @Test
    void rejects_emptyCurrency() {
        var ex = assertVal(validMonthly().put("currency", ""));
        assertContains(ex, "structuredFields.currency", "UNSUPPORTED_CURRENCY");
    }

    // ==================== Currency normalization ====================

    @Test
    void absentCurrency_normalizesToCNY() throws Exception {
        var b = validMonthly().remove("currency");
        when(documentStore.save(anyString(), any())).thenReturn(100L);
        when(catalog.resolveDefault("MVP_DEMO", "DEMO", "ENGINEERING"))
                .thenReturn(validBinding());
        TransactionSynchronizationManager.initSynchronization();
        try {
            call(b);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        // capture the snapshot argument
        var captor = ArgumentCaptor.forClass(String.class);
        verify(repository).insertTask(anyString(), any(), any(), any(), any(), any(), any(),
                anyString(), anyString(), captor.capture());
        var snapshotJson = captor.getValue();
        var snapshot = MAPPER.readTree(snapshotJson);
        assertThat(snapshot.has("currency")).isTrue();
        assertThat(snapshot.get("currency").asText()).isEqualTo("CNY");
    }

    // ==================== Cross-branch unknown fields ====================

    @Test
    void monthly_rejectsMilestoneOnlyFields() {
        var b = validMonthly().put("milestonePaymentTerms", "不应出现");
        var ex = assertVal(b);
        assertContains(ex, "structuredFields.milestonePaymentTerms", "UNKNOWN_FIELD");
    }

    @Test
    void milestone_rejectsMonthlyOnlyRatios() {
        var b = validMilestone()
                .put("progressPaymentRatio", 50)
                .put("completionPaymentRatio", 80)
                .put("settlementPaymentRatio", 95)
                .put("warrantyRetentionRatio", 5);
        var ex = assertVal(b);
        assertContains(ex, "structuredFields.progressPaymentRatio", "UNKNOWN_FIELD");
        assertContains(ex, "structuredFields.completionPaymentRatio", "UNKNOWN_FIELD");
        assertContains(ex, "structuredFields.settlementPaymentRatio", "UNKNOWN_FIELD");
        assertContains(ex, "structuredFields.warrantyRetentionRatio", "UNKNOWN_FIELD");
    }

    // ==================== Milestone blank ====================

    @Test
    void milestone_blankPaymentTerms_rejected() {
        var ex = assertVal(validMilestone().put("milestonePaymentTerms", "  "));
        assertContains(ex, "structuredFields.milestonePaymentTerms", "REQUIRED_FIELD_MISSING");
    }

    // ==================== File checks ====================

    @Test
    void rejects_emptyFile() {
        var ex = assertThrows(ValidationException.class,
                () -> service.createTask("t.docx", streamOf(""), 0,
                        validMonthly().build()));
        assertContains(ex, "file", "EMPTY_FILE");
    }

    @Test
    void rejects_blankFileName() {
        var ex = assertThrows(ValidationException.class,
                () -> service.createTask("  ", streamOf("x"), 10,
                        validMonthly().build()));
        assertContains(ex, "file", "EMPTY_FILE");
    }

    @Test
    void rejects_nonDocxExtension() {
        var ex = assertThrows(ValidationException.class,
                () -> service.createTask("t.pdf", streamOf("x"), 10,
                        validMonthly().build()));
        assertContains(ex, "file", "UNSUPPORTED_FILE_TYPE");
    }

    @Test
    void rejects_fileExceedsMax() {
        var ex = assertThrows(ValidationException.class,
                () -> service.createTask("t.docx", streamOf("x"), MAX_UPLOAD + 1,
                        validMonthly().build()));
        assertContains(ex, "file", "FILE_TOO_LARGE");
    }

    // ==================== No duplicate errors for null ====================

    @Test
    void null_structuredField_getsOnlyTypeError() {
        var json = "{\"contractType\":\"ENGINEERING\",\"structuredFields\":{"
                + "\"contractName\":null,\"partyAName\":\"A\"}}";
        var ex = assertThrows(ValidationException.class,
                () -> service.createTask("t.docx", streamOf("x"), 10, json));
        // contractName is null → INVALID_FIELD_TYPE, NOT additionally REQUIRED
        var contractNameErrors = ex.getFieldErrors().stream()
                .filter(e -> e.field().equals("structuredFields.contractName")).toList();
        assertThat(contractNameErrors).hasSize(1);
        assertThat(contractNameErrors.get(0).code()).isEqualTo("INVALID_FIELD_TYPE");
    }

    // ==================== FileCleanupSynchronization direct tests ====================

    @Test
    void directCallback_committed_retainsFile() {
        var store = org.mockito.Mockito.mock(LocalReviewDocumentStore.class);
        var sync = new ReviewTaskCreationService.FileCleanupSynchronization(store, "r/x.docx");
        sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        verify(store, never()).deleteIfExists(anyString());
    }

    @Test
    void directCallback_rolledBack_deletesFile() {
        var store = org.mockito.Mockito.mock(LocalReviewDocumentStore.class);
        var sync = new ReviewTaskCreationService.FileCleanupSynchronization(store, "r/x.docx");
        sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(store).deleteIfExists("r/x.docx");
    }

    @Test
    void directCallback_unknown_deletesFile() {
        var store = org.mockito.Mockito.mock(LocalReviewDocumentStore.class);
        var sync = new ReviewTaskCreationService.FileCleanupSynchronization(store, "r/x.docx");
        sync.afterCompletion(TransactionSynchronization.STATUS_UNKNOWN);
        verify(store).deleteIfExists("r/x.docx");
    }

    // ==================== Util ====================

    private static void assertContains(ValidationException ex, String field, String code) {
        assertThat(ex.getFieldErrors())
                .as("expect field=%s code=%s", field, code)
                .anyMatch(e -> e.field().equals(field) && e.code().equals(code));
    }

    private static ExecutionBindingRelease validBinding() {
        var raw = new ExecutionBindingRelease(
                "mvp-demo-engineering-v20260724.1",
                "MVP_DEMO", "DEMO", "ENGINEERING", "ENGINEERING_PROCUREMENT",
                true, Instant.parse("2026-07-24T00:00:00Z"), "ph",
                "v20260705.1", "v20260705.1", "budget-standard-v20260724.1",
                "MVP_DEMO_MOCK", "model-config-mvp-demo-mock-v20260724.1",
                "parser-docx-word-v20260724.1", "v20260705.1",
                "model-output-artifact-v20260724.1",
                "v20260705.1", "v20260705.1", "v20260705.1",
                "MOCK", "cqcp-demo-mock", "mock-local");
        var digest = ExecutionBindingCatalog.computeDigest(raw);
        return new ExecutionBindingRelease(raw.bindingVersion(), raw.purpose(),
                raw.deploymentScope(), raw.contractTypeCode(), raw.contractTypeProfileCode(),
                raw.enabled(), raw.effectiveFrom(), digest,
                raw.contractTypeProfileVersion(), raw.ruleSetVersion(),
                raw.reviewBudgetProfileVersion(), raw.modelProfileCode(),
                raw.modelConfigVersion(), raw.parserVersion(), raw.promptVersion(),
                raw.schemaVersion(), raw.patternLibraryVersion(),
                raw.fieldLexiconVersion(), raw.evidenceSelectorVersion(),
                raw.providerType(), raw.modelName(), raw.endpointAlias());
    }
}
