package com.cqcp.apiserver.reviewengine;

import static com.cqcp.apiserver.reviewengine.ReviewTaskCreationModels.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Core service for {@code POST /api/review/tasks}.
 *
 * <p>The {@code createTask} method is {@link Transactional @Transactional} so that
 * Task and Execution inserts share a single PostgreSQL transaction and
 * {@link FileCleanupSynchronization} fires on commit / rollback.
 */
public class ReviewTaskCreationService {

    private static final Logger log = LoggerFactory.getLogger(ReviewTaskCreationService.class);
    private static final String TASK_PREFIX = "TASK_";
    private static final String EXEC_PREFIX = "EXEC_";

    // Known metadata top-level fields
    private static final Set<String> KNOWN_METADATA_FIELDS = Set.of(
            "businessDocumentId", "contractType", "structuredFields");

    // Known structured-fields names (plus the five ratios only used by MONTHLY)
    private static final Set<String> KNOWN_STRUCTURED_FIELDS = Set.of(
            "contractName", "partyAName", "partyBName", "projectName",
            "contractTotalAmount", "taxExcludedAmount", "taxAmount", "taxRate",
            "pricingMode", "paymentMethod", "invoiceType", "currency",
            "milestonePaymentTerms",
            "prepaymentRatio", "progressPaymentRatio", "completionPaymentRatio",
            "settlementPaymentRatio", "warrantyRetentionRatio");

    private static final Set<String> STRING_FIELDS = Set.of(
            "contractName", "partyAName", "partyBName", "projectName",
            "pricingMode", "paymentMethod", "invoiceType", "milestonePaymentTerms");

    private static final Set<String> NUMERIC_FIELDS = Set.of(
            "contractTotalAmount", "taxExcludedAmount", "taxAmount", "taxRate",
            "prepaymentRatio", "progressPaymentRatio", "completionPaymentRatio",
            "settlementPaymentRatio", "warrantyRetentionRatio");

    private static final Set<String> AMOUNT_FIELDS = Set.of(
            "contractTotalAmount", "taxExcludedAmount", "taxAmount");

    private static final Set<String> RATIO_FIELDS = Set.of(
            "prepaymentRatio", "progressPaymentRatio", "completionPaymentRatio",
            "settlementPaymentRatio", "warrantyRetentionRatio");

    // Fields that belong to MONTHLY only (not in MILESTONE schema)
    private static final Set<String> MONTHLY_ONLY_FIELDS = Set.of(
            "progressPaymentRatio", "completionPaymentRatio",
            "settlementPaymentRatio", "warrantyRetentionRatio");

    // Dedicated mapper preserving BigDecimal scale
    private static final ObjectMapper BIG_DECIMAL_MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .nodeFactory(JsonNodeFactory.withExactBigDecimals(true))
            .build();

    private static final ObjectMapper PLAIN_MAPPER = new ObjectMapper();

    private final ReviewTaskCreationRepository repository;
    private final ExecutionBindingCatalog catalog;
    private final LocalReviewDocumentStore documentStore;
    private final long maxUploadBytes;

    public ReviewTaskCreationService(
            ReviewTaskCreationRepository repository,
            ExecutionBindingCatalog catalog,
            LocalReviewDocumentStore documentStore,
            long maxUploadBytes) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.documentStore = Objects.requireNonNull(documentStore, "documentStore");
        this.maxUploadBytes = maxUploadBytes;
    }

    // ==================== Public entry point ====================

    @Transactional
    public CreateReviewTaskResponse createTask(
            String originalFilename, InputStream fileData, long fileSize,
            String metadataJson) {

        var errors = new ArrayList<FieldError>();
        var alreadyErred = new LinkedHashSet<String>();  // track fields with type errors

        // — file checks —
        if (originalFilename == null || originalFilename.isBlank()) {
            errors.add(new FieldError("file", "EMPTY_FILE", "file 不能为空"));
        } else if (originalFilename.indexOf('\r') >= 0 || originalFilename.indexOf('\n') >= 0) {
            errors.add(new FieldError("file", "INVALID_FILE_NAME", "file 名称包含非法字符"));
        } else if (!originalFilename.toLowerCase(Locale.ROOT).endsWith(".docx")) {
            errors.add(new FieldError("file", "UNSUPPORTED_FILE_TYPE", "仅支持 DOCX 文件"));
        }
        if (fileSize == 0) {
            errors.add(new FieldError("file", "EMPTY_FILE", "file 不能为空"));
        }
        if (fileSize > maxUploadBytes) {
            errors.add(new FieldError("file", "FILE_TOO_LARGE", "file 超过 25 MiB 上限"));
        }

        // — metadata JSON —
        JsonNode root;
        if (metadataJson == null || metadataJson.isBlank()) {
            errors.add(new FieldError("metadata", "INVALID_JSON", "metadata JSON 无法解析"));
            throw new ValidationException(errors);
        }
        try {
            root = BIG_DECIMAL_MAPPER.readTree(metadataJson);
            if (root == null || root.isNull()) {
                errors.add(new FieldError("metadata", "INVALID_JSON", "metadata JSON 无法解析"));
                throw new ValidationException(errors);
            }
        } catch (JsonProcessingException e) {
            errors.add(new FieldError("metadata", "INVALID_JSON", "metadata JSON 无法解析"));
            throw new ValidationException(errors);
        }

        // — metadata must be object —
        if (!root.isObject()) {
            errors.add(new FieldError("metadata", "INVALID_FIELD_TYPE", "metadata 类型无效"));
            throw new ValidationException(errors);
        }

        // — metadata unknown fields —
        for (Iterator<String> it = root.fieldNames(); it.hasNext(); ) {
            var fn = it.next();
            if (!KNOWN_METADATA_FIELDS.contains(fn)) {
                errors.add(new FieldError(fn, "UNKNOWN_FIELD", fn + " 为未声明字段"));
            }
        }

        // — contractType —
        if (!root.has("contractType")) {
            errors.add(new FieldError("contractType", "REQUIRED_FIELD_MISSING", "contractType 为必填项"));
        } else if (root.get("contractType").isNull() || !root.get("contractType").isTextual()) {
            errors.add(new FieldError("contractType", "INVALID_FIELD_TYPE", "contractType 类型无效"));
            alreadyErred.add("contractType");
        } else if (!"ENGINEERING".equals(root.get("contractType").asText())) {
            errors.add(new FieldError("contractType", "INVALID_ENUM_VALUE", "contractType 必须为 ENGINEERING"));
        }

        // — businessDocumentId (optional) —
        if (root.has("businessDocumentId")) {
            var bd = root.get("businessDocumentId");
            if (bd.isNull() || !bd.isTextual()) {
                errors.add(new FieldError("businessDocumentId", "INVALID_FIELD_TYPE",
                        "businessDocumentId 类型无效"));
            }
        }

        // — structuredFields presence & type —
        JsonNode sf;
        if (!root.has("structuredFields")) {
            errors.add(new FieldError("structuredFields", "REQUIRED_FIELD_MISSING",
                    "structuredFields 为必填项"));
            throw new ValidationException(errors);
        }
        sf = root.get("structuredFields");
        if (sf.isNull() || !sf.isObject()) {
            errors.add(new FieldError("structuredFields", "INVALID_FIELD_TYPE",
                    "structuredFields 类型无效"));
            throw new ValidationException(errors);
        }

        // — structuredFields unknown fields —
        for (Iterator<String> it = sf.fieldNames(); it.hasNext(); ) {
            var fn = it.next();
            if (!KNOWN_STRUCTURED_FIELDS.contains(fn)) {
                errors.add(new FieldError("structuredFields." + fn, "UNKNOWN_FIELD",
                        "structuredFields." + fn + " 为未声明字段"));
            }
        }

        // — type checks (STRING_FIELDS) — null → INVALID_FIELD_TYPE, not REQUIRED
        for (var f : STRING_FIELDS) {
            if (!sf.has(f)) continue;
            if (sf.get(f).isNull() || !sf.get(f).isTextual()) {
                errors.add(new FieldError("structuredFields." + f, "INVALID_FIELD_TYPE",
                        "structuredFields." + f + " 类型无效"));
                alreadyErred.add("sf:" + f);
            }
        }

        // — type checks (NUMERIC_FIELDS) — null → INVALID_FIELD_TYPE, not REQUIRED
        for (var f : NUMERIC_FIELDS) {
            if (!sf.has(f)) continue;
            if (sf.get(f).isNull() || !sf.get(f).isNumber()) {
                errors.add(new FieldError("structuredFields." + f, "INVALID_FIELD_TYPE",
                        "structuredFields." + f + " 类型无效"));
                alreadyErred.add("sf:" + f);
            }
        }

        // — currency type check —
        if (sf.has("currency") && !sf.get("currency").isNull()) {
            if (!sf.get("currency").isTextual()) {
                errors.add(new FieldError("structuredFields.currency", "INVALID_FIELD_TYPE",
                        "structuredFields.currency 类型无效"));
                alreadyErred.add("sf:currency");
            }
        } else if (sf.has("currency") && sf.get("currency").isNull()) {
            errors.add(new FieldError("structuredFields.currency", "INVALID_FIELD_TYPE",
                    "structuredFields.currency 类型无效"));
            alreadyErred.add("sf:currency");
        }

        // — common required (only if not already type-erred) —
        for (var f : List.of("contractName", "partyAName", "partyBName", "projectName")) {
            if (alreadyErred.contains("sf:" + f)) continue;
            if (!sf.has(f) || sf.get(f).isNull() || !sf.get(f).isTextual()) {
                errors.add(new FieldError("structuredFields." + f, "REQUIRED_FIELD_MISSING",
                        f + " 为必填项"));
            }
        }

        for (var f : List.of("contractTotalAmount", "taxExcludedAmount", "taxAmount", "taxRate")) {
            if (alreadyErred.contains("sf:" + f)) continue;
            if (!sf.has(f) || sf.get(f).isNull() || !sf.get(f).isNumber()) {
                errors.add(new FieldError("structuredFields." + f, "REQUIRED_FIELD_MISSING",
                        f + " 为必填项"));
            }
        }

        // — enums: require absent, type-error already handled, wrong-value gets INVALID_ENUM_VALUE —
        var enumFields = List.of("pricingMode", "paymentMethod", "invoiceType");
        for (var ef : enumFields) {
            if (alreadyErred.contains("sf:" + ef)) continue;
            if (!sf.has(ef) || sf.get(ef).isNull() || !sf.get(ef).isTextual()) {
                errors.add(new FieldError("structuredFields." + ef, "REQUIRED_FIELD_MISSING",
                        ef + " 为必填项"));
                continue;
            }
            // field is present, textual: check enum values
            var val = sf.get(ef).asText();
            java.util.Set<String> allowed = switch (ef) {
                case "pricingMode" -> java.util.Set.of("FIXED_TOTAL_PRICE", "PROVISIONAL_TOTAL_PRICE");
                case "paymentMethod" -> java.util.Set.of("MONTHLY", "MILESTONE");
                case "invoiceType" -> java.util.Set.of("VAT_GENERAL", "VAT_SPECIAL");
                default -> java.util.Set.of();
            };
            if (!allowed.contains(val)) {
                errors.add(new FieldError("structuredFields." + ef, "INVALID_ENUM_VALUE",
                        ef + " 值无效"));
            }
        }

        // — non-blank checks —
        checkNonBlank(sf, "contractName", errors, alreadyErred);
        checkNonBlank(sf, "partyAName", errors, alreadyErred);
        checkNonBlank(sf, "partyBName", errors, alreadyErred);
        checkNonBlank(sf, "projectName", errors, alreadyErred);

        // — maxLength —
        checkMaxLength(sf, "contractName", 255, errors, alreadyErred);

        // — decimal scale & range —
        for (var f : AMOUNT_FIELDS) {
            checkDecimalScale(sf, f, 2, errors, alreadyErred);
        }
        checkDecimalScale(sf, "taxRate", 4, errors, alreadyErred);
        checkPercentRange(sf, "taxRate", errors, alreadyErred);

        for (var f : RATIO_FIELDS) {
            checkDecimalScale(sf, f, 2, errors, alreadyErred);
            checkPercentRange(sf, f, errors, alreadyErred);
        }

        // — currency value —
        if (!alreadyErred.contains("sf:currency") && sf.has("currency")
                && sf.get("currency").isTextual()) {
            var cv = sf.get("currency").asText();
            if (cv.isEmpty()) {
                errors.add(new FieldError("structuredFields.currency", "UNSUPPORTED_CURRENCY",
                        "仅支持 CNY"));
            } else if (!"CNY".equals(cv)) {
                errors.add(new FieldError("structuredFields.currency", "UNSUPPORTED_CURRENCY",
                        "仅支持 CNY"));
            }
        }

        // — payment-method conditional —
        String pm = hasText(sf, "paymentMethod") ? sf.get("paymentMethod").asText() : "";

        if ("MONTHLY".equals(pm)) {
            // MONTHLY requires five ratios
            for (var f : RATIO_FIELDS) {
                if (!alreadyErred.contains("sf:" + f)
                        && (!sf.has(f) || sf.get(f).isNull() || !sf.get(f).isNumber())) {
                    errors.add(new FieldError("structuredFields." + f, "CONDITIONAL_FIELD_MISSING",
                            f + " 为必填项"));
                }
            }
            // MILESTONE-only field in MONTHLY → unknown field
            if (sf.has("milestonePaymentTerms")) {
                errors.add(new FieldError("structuredFields.milestonePaymentTerms", "UNKNOWN_FIELD",
                        "structuredFields.milestonePaymentTerms 为未声明字段"));
            }
        } else if ("MILESTONE".equals(pm)) {
            if (!alreadyErred.contains("sf:prepaymentRatio")
                    && (!sf.has("prepaymentRatio") || sf.get("prepaymentRatio").isNull()
                    || !sf.get("prepaymentRatio").isNumber())) {
                errors.add(new FieldError("structuredFields.prepaymentRatio", "CONDITIONAL_FIELD_MISSING",
                        "prepaymentRatio 为必填项"));
            }
            if (!alreadyErred.contains("sf:milestonePaymentTerms")) {
                if (!sf.has("milestonePaymentTerms") || sf.get("milestonePaymentTerms").isNull()
                        || !sf.get("milestonePaymentTerms").isTextual()) {
                    errors.add(new FieldError("structuredFields.milestonePaymentTerms",
                            "CONDITIONAL_FIELD_MISSING", "milestonePaymentTerms 为必填项"));
                } else {
                    // non-blank for milestonePaymentTerms
                    checkNonBlank(sf, "milestonePaymentTerms", errors, alreadyErred);
                }
            }
            // MONTHLY-only fields in MILESTONE → unknown field
            for (var f : MONTHLY_ONLY_FIELDS) {
                if (sf.has(f)) {
                    errors.add(new FieldError("structuredFields." + f, "UNKNOWN_FIELD",
                            "structuredFields." + f + " 为未声明字段"));
                }
            }
        }

        // ===== Throw if any errors =====
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        // ===== Phase 2: IDs =====
        var taskId = TASK_PREFIX + uuidHex();
        var executionId = EXEC_PREFIX + uuidHex();
        var docRef = taskId + "/" + uuidHex() + ".docx";
        var resultUrl = "/review/results/" + taskId + "?executionId=" + executionId;

        // ===== Phase 3: Document persistence =====
        long sizeBytes;
        String sha256;
        try {
            sizeBytes = documentStore.save(docRef, fileData);
            sha256 = documentStore.sha256(taskId, docRef);
        } catch (Exception e) {
            throw e;
        }

        // ===== Phase 4: Register rollback cleanup =====
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new FileCleanupSynchronization(documentStore, docRef));
        } else {
            documentStore.deleteIfExists(docRef);
            throw new IllegalStateException("No active transaction for file cleanup registration");
        }

        // ===== Phase 5: Binding resolution =====
        ExecutionBindingRelease binding;
        try {
            binding = catalog.resolveDefault("MVP_DEMO", "DEMO", "ENGINEERING");
        } catch (ExecutionBindingResolutionException e) {
            documentStore.deleteIfExists(docRef);
            throw e;
        }

        // ===== Phase 6: DB inserts =====
        var contractMetadata = buildContractMetadata(root, originalFilename, docRef, sizeBytes, sha256);
        var normalizedSnapshot = normalizeSnapshot(sf);

        repository.insertTask(
                taskId, null, "ADMIN", "DOCX_UPLOAD",
                sf.get("contractName").asText(), "ENGINEERING", resultUrl,
                normalizedSnapshot.get("currency").asText(),
                contractMetadata.toString(),
                normalizedSnapshot.toString());

        repository.insertExecution(taskId, new ExecutionInsert(
                executionId, taskId,
                binding.contractTypeProfileVersion(), binding.ruleSetVersion(),
                binding.reviewBudgetProfileVersion(), binding.modelProfileCode(),
                binding.modelConfigVersion(), binding.parserVersion(),
                binding.promptVersion(), binding.schemaVersion(),
                binding.patternLibraryVersion(), binding.fieldLexiconVersion(),
                binding.evidenceSelectorVersion(), binding.providerType(),
                binding.modelName(), binding.endpointAlias()));

        return new CreateReviewTaskResponse(taskId, executionId, "QUEUED", resultUrl);
    }

    // ==================== Normalization ====================

    /** Build contract_metadata as an ObjectNode. businessDocumentId is from metadata root. */
    private static ObjectNode buildContractMetadata(
            JsonNode root,
            String originalFileName,
            String documentReference,
            long sizeBytes,
            String sha256) {
        var meta = PLAIN_MAPPER.createObjectNode();
        meta.put("originalFileName", originalFileName);
        meta.put("documentReference", documentReference);
        meta.put("sizeBytes", sizeBytes);
        meta.put("sha256", sha256);
        if (root.has("businessDocumentId")) {
            var bd = root.get("businessDocumentId");
            if (bd.isNull() || !bd.isTextual()) {
                // type error already reported; store empty string as metadata
                // (null is not preserved — it was already rejected by validation)
            } else if (bd.asText().isEmpty()) {
                // explicit empty string is a legitimate value; store it
                meta.put("businessDocumentId", "");
            } else {
                meta.put("businessDocumentId", bd.asText());
            }
        }
        // (absent → omit from JSON; NON_NULL handles serialization)
        return meta;
    }

    /** Normalized structured_fields_snapshot — injects currency only when absent. */
    private static ObjectNode normalizeSnapshot(JsonNode sf) {
        var snap = ((ObjectNode) sf).deepCopy();
        if (!sf.has("currency")) {
            snap.put("currency", "CNY");
        }
        return snap;
    }

    // ==================== Validation helpers ====================

    private static boolean hasText(JsonNode sf, String field) {
        return sf.has(field) && sf.get(field).isTextual();
    }

    private static void checkNonBlank(JsonNode sf, String field, List<FieldError> errors,
                                       LinkedHashSet<String> already) {
        if (already.contains("sf:" + field)) return;
        if (!sf.has(field) || !sf.get(field).isTextual()) return;
        if (sf.get(field).asText().isBlank()) {
            errors.add(new FieldError("structuredFields." + field, "REQUIRED_FIELD_MISSING",
                    field + " 为必填项"));
        }
    }

    private static void checkMaxLength(JsonNode sf, String field, int max,
                                       List<FieldError> errors, LinkedHashSet<String> already) {
        if (already.contains("sf:" + field)) return;
        if (!sf.has(field) || !sf.get(field).isTextual()) return;
        if (sf.get(field).asText().length() > max) {
            errors.add(new FieldError("structuredFields." + field, "INVALID_STRING_LENGTH",
                    field + " 不能超过 " + max + " 字符"));
        }
    }

    private static void checkDecimalScale(JsonNode sf, String field, int maxScale,
                                           List<FieldError> errors, LinkedHashSet<String> already) {
        if (already.contains("sf:" + field)) return;
        if (!sf.has(field) || !sf.get(field).isNumber()) return;
        var bd = sf.get(field).decimalValue();
        if (bd.scale() > maxScale) {
            errors.add(new FieldError("structuredFields." + field, "INVALID_DECIMAL_SCALE",
                    field + " 最多保留 " + maxScale + " 位小数"));
        }
    }

    private static void checkPercentRange(JsonNode sf, String field, List<FieldError> errors,
                                           LinkedHashSet<String> already) {
        if (already.contains("sf:" + field)) return;
        if (!sf.has(field) || !sf.get(field).isNumber()) return;
        var bd = sf.get(field).decimalValue();
        if (bd.compareTo(BigDecimal.ZERO) < 0 || bd.compareTo(BigDecimal.valueOf(100)) > 0) {
            errors.add(new FieldError("structuredFields." + field, "INVALID_PERCENT_RANGE",
                    field + " 必须在 0-100 范围内"));
        }
    }

    private static String uuidHex() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    // ==================== FileCleanupSynchronization ====================

    static final class FileCleanupSynchronization implements TransactionSynchronization {
        private final LocalReviewDocumentStore documentStore;
        private final String documentReference;

        FileCleanupSynchronization(LocalReviewDocumentStore store, String ref) {
            this.documentStore = Objects.requireNonNull(store, "store");
            this.documentReference = Objects.requireNonNull(ref, "ref");
        }

        @Override
        public void afterCompletion(int status) {
            if (status != TransactionSynchronization.STATUS_COMMITTED) {
                documentStore.deleteIfExists(documentReference);
            }
        }
    }
}
