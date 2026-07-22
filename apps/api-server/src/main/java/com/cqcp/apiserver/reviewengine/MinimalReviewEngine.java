package com.cqcp.apiserver.reviewengine;

import com.cqcp.apiserver.tuning.PointDiagnostic;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

public class MinimalReviewEngine {

    private static final BigDecimal AMOUNT_TOLERANCE = new BigDecimal("0.01");
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final Pattern TABLE_CELL_REF = Pattern.compile("^table:[^/]+/row:[0-9]+/cell:[0-9]+$");
    private static final List<ReviewPointCode> MONTHLY_ONLY_POINTS = List.of(
            ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY,
            ReviewPointCode.COMPLETION_PAYMENT_RATIO_CONSISTENCY,
            ReviewPointCode.SETTLEMENT_PAYMENT_RATIO_CONSISTENCY,
            ReviewPointCode.WARRANTY_RETENTION_RATIO_CONSISTENCY);

    public ReviewEngineResult review(ReviewEngineInput input) {
        var pointResults = new ArrayList<PointReviewResult>();
        var diagnostics = new ArrayList<PointDiagnostic>();

        // Validate runtime snapshot if present
        if (input.runtimeRuleSetSnapshot() != null) {
            validateConsistencySnapshot(input.runtimeRuleSetSnapshot());
        }

        for (ReviewPointCode reviewPointCode : ReviewPointCode.values()) {
            pointResults.add(reviewPoint(input, reviewPointCode, diagnostics));
        }

        var summary = summarize(pointResults);
        var completeness = buildCompleteness(summary);
        var snapshotStatus = summary.notConcludedCount() > 0 ? "PARTIAL_SUCCESS" : "SUCCESS";
        var snapshotDraft = new ReviewResultSnapshotDraft(
                input.taskId(),
                input.executionId(),
                input.sampleId(),
                snapshotStatus,
                summary,
                completeness,
                List.copyOf(pointResults),
                List.copyOf(diagnostics));

        return new ReviewEngineResult(
                List.copyOf(pointResults),
                summary,
                completeness,
                snapshotDraft,
                List.copyOf(diagnostics));
    }

    private void validateConsistencySnapshot(RuntimeRuleSetSnapshot snapshot) {
        if (!"v20260715.1".equals(snapshot.version())) {
            throw new IllegalStateException(
                    "Unsupported consistency snapshot version: " + snapshot.version());
        }
        if (snapshot.policyMap().size() != 9) {
            throw new IllegalStateException(
                    "Consistency snapshot must cover all 9 review points");
        }
        for (ReviewPointCode code : ReviewPointCode.values()) {
            var policy = snapshot.policyMap().get(code);
            if (policy == null) {
                throw new IllegalStateException(
                        "Missing consistency policy for: " + code);
            }
            if (!"CONSISTENCY_SET".equals(policy.cardinalityMode())) {
                throw new IllegalStateException(
                        "Non-CONSISTENCY_SET policy for: " + code);
            }
        }
    }

    private PointReviewResult reviewPoint(
            ReviewEngineInput input,
            ReviewPointCode reviewPointCode,
            List<PointDiagnostic> diagnostics) {
        if (isSkippedForPaymentMethod(input, reviewPointCode)) {
            return new PointReviewResult(
                    reviewPointCode,
                    PointStatus.SKIPPED,
                    skippedMessage(reviewPointCode),
                    null,
                    List.of(),
                    null,
                    SkippedReason.NOT_APPLICABLE_FOR_PAYMENT_METHOD,
                    null,
                    null,
                    List.of());
        }

        var evidence = input.pointEvidences().get(reviewPointCode);
        var preflight = preflight(reviewPointCode, evidence);
        if (!preflight.canConclude()) {
            return notConcludedFromPreflight(reviewPointCode, preflight, evidence, diagnostics);
        }

        // Consistency multi-value conflict: distinct canonical values > 1 → ERROR
        // Only activated when runtimeRuleSetSnapshot is present
        if (input.runtimeRuleSetSnapshot() != null && hasConsistencyMultiValueConflict(evidence)) {
            return consistencyMultiValueError(reviewPointCode, evidence);
        }

        return switch (reviewPointCode) {
            case PARTY_A_NAME_CONSISTENCY -> compareText(
                    reviewPointCode,
                    input.structuredFields().getRequired("partyAName"),
                    evidence,
                    "甲方名称一致。",
                    "甲方名称与合同证据不一致。");
            case PARTY_B_NAME_CONSISTENCY -> compareText(
                    reviewPointCode,
                    input.structuredFields().getRequired("partyBName"),
                    evidence,
                    "乙方名称一致。",
                    "乙方名称与合同证据不一致。");
            case CONTRACT_TOTAL_AMOUNT_CONSISTENCY -> compareDecimal(
                    reviewPointCode,
                    input.structuredFields().getRequired("contractTotalAmount"),
                    evidence,
                    "合同含税总金额一致。",
                    "合同含税总金额与合同证据不一致。");
            case TAX_AMOUNT_FORMULA_CONSISTENCY -> reviewTaxFormula(input, reviewPointCode, evidence);
            case PREPAYMENT_RATIO_CONSISTENCY -> compareDecimal(
                    reviewPointCode,
                    input.structuredFields().getRequired("prepaymentRatio"),
                    evidence,
                    "预付款比例一致。",
                    "预付款比例与合同证据不一致。");
            case PROGRESS_PAYMENT_RATIO_CONSISTENCY -> compareDecimal(
                    reviewPointCode,
                    input.structuredFields().getRequired("progressPaymentRatio"),
                    evidence,
                    "进度款比例一致。",
                    "进度款比例与合同证据不一致。");
            case COMPLETION_PAYMENT_RATIO_CONSISTENCY -> compareDecimal(
                    reviewPointCode,
                    input.structuredFields().getRequired("completionPaymentRatio"),
                    evidence,
                    "竣工款比例一致。",
                    "竣工款比例与合同证据不一致。");
            case SETTLEMENT_PAYMENT_RATIO_CONSISTENCY -> compareDecimal(
                    reviewPointCode,
                    input.structuredFields().getRequired("settlementPaymentRatio"),
                    evidence,
                    "结算款比例一致。",
                    "结算款比例与合同证据不一致。");
            case WARRANTY_RETENTION_RATIO_CONSISTENCY -> compareDecimal(
                    reviewPointCode,
                    input.structuredFields().getRequired("warrantyRetentionRatio"),
                    evidence,
                    "质保款比例一致。",
                    "质保款比例与合同证据不一致。");
        };
    }

    /**
     * Check if evidence has multiple distinct canonical values via occurrences,
     * not via evidence.candidateValue (which is a first-occurrence projection).
     */
    private boolean hasConsistencyMultiValueConflict(PointEvidence evidence) {
        if (evidence == null || evidence.occurrences().isEmpty()) {
            return false;
        }
        if (evidence.status() != EvidenceStatus.CONFIRMED) {
            return false;
        }
        var distinctValues = evidence.occurrences().stream()
                .map(PointEvidenceOccurrence::candidateValue)
                .filter(v -> v != null && !v.isBlank())
                .distinct()
                .toList();
        return distinctValues.size() > 1;
    }

    private PointReviewResult consistencyMultiValueError(
            ReviewPointCode reviewPointCode,
            PointEvidence evidence) {
        var summary = "一致性扫描发现 " + evidence.occurrences().size()
                + " 处 occurrence，存在多个不同值。";
        return new PointReviewResult(
                reviewPointCode,
                PointStatus.ERROR,
                summary,
                FindingSeverity.ERROR,
                anchorsFor(evidence),
                null,
                null,
                PointCoverageStatus.COMPLETE,
                null,
                List.of());
    }

    private PointPreflight preflight(ReviewPointCode reviewPointCode, PointEvidence evidence) {
        if (hasUnreliableExplicitOccurrence(evidence)) {
            var slotCoverages = List.of(new EvidenceSlotCoverage(
                    slotKeyOf(reviewPointCode),
                    true,
                    true,
                    EvidenceSlotCoverageStatus.PARTIAL,
                    "SYS_EVIDENCE_BUNDLE_INVALID",
                    false));
            return failurePreflight(
                    PointCoverageStatus.PARTIAL,
                    "SYS_EVIDENCE_BUNDLE_INVALID",
                    NotConcludedReasonCode.INTERNAL_RULE_ERROR,
                    null,
                    "关键证据缺少可靠原文定位，当前无法形成正式结论。",
                    slotCoverages);
        }
        var slotCoverages = evidence == null
                ? List.of(missingRequiredSlot(reviewPointCode))
                : !evidence.slotCoverages().isEmpty()
                        ? evidence.slotCoverages()
                        : List.of(defaultSlotCoverage(reviewPointCode, evidence));
        var pointCoverageStatus = evaluatePointCoverageStatus(slotCoverages);

        for (EvidenceSlotCoverage slotCoverage : slotCoverages) {
            if (!slotCoverage.required()) {
                continue;
            }

            return switch (slotCoverage.coverageStatus()) {
                case SATISFIED -> slotCoverage.reliableAnchor()
                        ? new PointPreflight(true, pointCoverageStatus, null, null, null, null, slotCoverages)
                        : failurePreflight(
                                pointCoverageStatus,
                                "SYS_EVIDENCE_BUNDLE_INVALID",
                                NotConcludedReasonCode.INTERNAL_RULE_ERROR,
                                null,
                                "关键证据缺少可靠原文定位，当前无法形成正式结论。",
                                slotCoverages);
                case LOW_CONFIDENCE -> failurePreflight(
                        PointCoverageStatus.LOW_CONFIDENCE,
                        defaultIfBlank(slotCoverage.diagnosticCode(), "SYS_PARSE_LOW_CONFIDENCE"),
                        NotConcludedReasonCode.PARSE_LOW_CONFIDENCE,
                        "PARSE_LOW_CONFIDENCE",
                        "required slot 仅达到低置信覆盖，当前无法形成正式结论。",
                        slotCoverages);
                case BUDGET_TRUNCATED -> failurePreflight(
                        PointCoverageStatus.PARTIAL,
                        "SYS_EVIDENCE_BUDGET_EXCEEDED",
                        NotConcludedReasonCode.MODEL_BUDGET_EXCEEDED,
                        "BUDGET_TRUNCATED",
                        "required slot 因预算截断未完整保留，当前无法形成正式结论。",
                        slotCoverages);
                case MISSING, PARTIAL -> {
                    var diagnosticCode = defaultIfBlank(slotCoverage.diagnosticCode(), "SYS_INDEX_INCOMPLETE");
                    if ("SYS_EVIDENCE_BUNDLE_INVALID".equals(diagnosticCode)) {
                        yield failurePreflight(
                                PointCoverageStatus.PARTIAL,
                                diagnosticCode,
                                NotConcludedReasonCode.INTERNAL_RULE_ERROR,
                                null,
                                "关键证据缺少可靠原文定位，当前无法形成正式结论。",
                                slotCoverages);
                    }
                    yield failurePreflight(
                            PointCoverageStatus.PARTIAL,
                            diagnosticCode,
                            NotConcludedReasonCode.EVIDENCE_NOT_FOUND,
                            "INDEX_MISSING",
                            "缺少 required slot，当前无法形成正式结论。",
                            slotCoverages);
                }
                case AMBIGUOUS -> ambiguousFailure(slotCoverage.diagnosticCode(), slotCoverages);
            };
        }

        return failurePreflight(
                PointCoverageStatus.PARTIAL,
                evidence == null ? "SYS_INDEX_INCOMPLETE" : defaultIfBlank(evidence.diagnosticCode(), "SYS_RULE_ERROR"),
                evidence == null ? NotConcludedReasonCode.EVIDENCE_NOT_FOUND : defaultReason(evidence.notConcludedReason(), NotConcludedReasonCode.INTERNAL_RULE_ERROR),
                mapNotConcludedDetail(evidence == null ? "SYS_INDEX_INCOMPLETE" : evidence.diagnosticCode()),
                evidence == null ? "缺少 required slot，当前无法形成正式结论。" : "当前未形成正式结论。",
                slotCoverages);
    }

    private PointPreflight ambiguousFailure(String diagnosticCode, List<EvidenceSlotCoverage> slotCoverages) {
        var resolvedDiagnostic = defaultIfBlank(diagnosticCode, "SYS_EVIDENCE_AMBIGUOUS");
        var detail = "SYS_ROLE_CONFLICT".equals(resolvedDiagnostic) ? "ROLE_CONFLICT" : null;
        var message = "SYS_ROLE_CONFLICT".equals(resolvedDiagnostic)
                ? "required slot 存在角色冲突，当前无法形成正式结论。"
                : "required slot 存在证据歧义，当前无法形成正式结论。";
        return failurePreflight(
                PointCoverageStatus.PARTIAL,
                resolvedDiagnostic,
                NotConcludedReasonCode.EVIDENCE_AMBIGUOUS,
                detail,
                message,
                slotCoverages);
    }

    private PointPreflight failurePreflight(
            PointCoverageStatus pointCoverageStatus,
            String diagnosticCode,
            NotConcludedReasonCode reason,
            String notConcludedDetail,
            String message,
            List<EvidenceSlotCoverage> slotCoverages) {
        return new PointPreflight(false, pointCoverageStatus, diagnosticCode, reason, notConcludedDetail, message, slotCoverages);
    }

    private PointReviewResult compareText(
            ReviewPointCode reviewPointCode,
            String actualValue,
            PointEvidence evidence,
            String passMessage,
            String errorMessage) {
        return normalizeText(actualValue).equals(normalizeText(evidence.candidateValue()))
                ? pass(reviewPointCode, passMessage, evidence)
                : error(reviewPointCode, errorMessage, evidence);
    }

    private PointReviewResult compareDecimal(
            ReviewPointCode reviewPointCode,
            String actualValue,
            PointEvidence evidence,
            String passMessage,
            String errorMessage) {
        return decimalsEqual(actualValue, evidence.candidateValue())
                ? pass(reviewPointCode, passMessage, evidence)
                : error(reviewPointCode, errorMessage, evidence);
    }

    private PointReviewResult reviewTaxFormula(
            ReviewEngineInput input,
            ReviewPointCode reviewPointCode,
            PointEvidence evidence) {
        // Evidence tax consistency check: only when runtimeRuleSetSnapshot present
        if (input.runtimeRuleSetSnapshot() != null
                && evidence.candidateValue() != null && !evidence.candidateValue().isBlank()) {
            var evidenceTax = new BigDecimal(evidence.candidateValue());
            var structuredTax = input.structuredFields().getDecimal("taxAmount");
            if (!amountsClose(evidenceTax, structuredTax)) {
                return error(reviewPointCode, "税额证据值与结构化字段不一致。", evidence);
            }
        }

        var total = input.structuredFields().getDecimal("contractTotalAmount");
        var excluded = input.structuredFields().getDecimal("taxExcludedAmount");
        var tax = input.structuredFields().getDecimal("taxAmount");

        var strongExpected = excluded.add(tax).setScale(2, RoundingMode.HALF_UP);
        if (!amountsClose(total, strongExpected)) {
            return error(reviewPointCode, "税额强校验不一致。", evidence);
        }

        var taxRate = input.structuredFields().getOptionalDecimal("taxRate");
        if (taxRate.isEmpty()) {
            return pass(reviewPointCode, "税额强校验通过，未执行税额弱校验。", evidence);
        }

        var weakExpected = excluded.multiply(taxRate.orElseThrow())
                .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);

        if (!amountsClose(tax, weakExpected)) {
            return warning(reviewPointCode, "税额弱校验存在偏差，请人工复核。", evidence);
        }

        return pass(reviewPointCode, "税额强校验与弱校验均通过。", evidence);
    }

    private boolean isSkippedForPaymentMethod(ReviewEngineInput input, ReviewPointCode reviewPointCode) {
        return "MILESTONE".equals(input.structuredFields().getOptional("paymentMethod").orElse(null))
                && MONTHLY_ONLY_POINTS.contains(reviewPointCode);
    }

    private PointReviewResult pass(ReviewPointCode reviewPointCode, String message, PointEvidence evidence) {
        return new PointReviewResult(
                reviewPointCode,
                PointStatus.PASS,
                message,
                null,
                anchorsFor(evidence),
                null,
                null,
                PointCoverageStatus.COMPLETE,
                null,
                List.of());
    }

    private PointReviewResult warning(ReviewPointCode reviewPointCode, String message, PointEvidence evidence) {
        return new PointReviewResult(
                reviewPointCode,
                PointStatus.WARNING,
                message,
                FindingSeverity.WARNING,
                anchorsFor(evidence),
                null,
                null,
                PointCoverageStatus.COMPLETE,
                null,
                List.of());
    }

    private PointReviewResult error(ReviewPointCode reviewPointCode, String message, PointEvidence evidence) {
        return new PointReviewResult(
                reviewPointCode,
                PointStatus.ERROR,
                message,
                FindingSeverity.ERROR,
                anchorsFor(evidence),
                null,
                null,
                PointCoverageStatus.COMPLETE,
                null,
                List.of());
    }

    private PointReviewResult notConcludedFromPreflight(
            ReviewPointCode reviewPointCode,
            PointPreflight preflight,
            PointEvidence evidence,
            List<PointDiagnostic> diagnostics) {
        if (preflight.diagnosticCode() != null) {
            diagnostics.add(new PointDiagnostic(
                    reviewPointCode.name(),
                    PointStatus.NOT_CONCLUDED.name(),
                    preflight.diagnosticCode(),
                    preflight.message(),
                    evidence == null || evidence.blockId() == null ? List.of() : List.of(evidence.blockId()),
                    evidence == null ? "无证据摘要" : evidence.evidenceSummary(),
                    false));
        }

        return new PointReviewResult(
                reviewPointCode,
                PointStatus.NOT_CONCLUDED,
                preflight.message(),
                null,
                evidence == null ? List.of() : anchorsFor(evidence),
                preflight.reason(),
                null,
                preflight.pointCoverageStatus(),
                preflight.notConcludedDetail(),
                List.of());
    }

    private List<SourceAnchorSummary> anchorsFor(PointEvidence evidence) {
        if (evidence == null) {
            return List.of();
        }
        if (!evidence.occurrences().isEmpty()) {
            Map<String, SourceAnchorSummary> anchors = new LinkedHashMap<>();
            for (PointEvidenceOccurrence occurrence : evidence.occurrences()) {
                if (occurrence.blockId() == null || occurrence.blockId().isBlank()) {
                    continue;
                }
                var identityRef = isExactTableCellRef(occurrence.previewElementRef())
                        ? occurrence.previewElementRef()
                        : "";
                var identity = occurrence.blockId() + "|" + identityRef;
                anchors.putIfAbsent(identity, new SourceAnchorSummary(
                        occurrence.blockId(),
                        evidence.sourceOrigin(),
                        evidence.sourceExtractionMode(),
                        evidence.contextType(),
                        occurrence.evidenceSummary(),
                        occurrence.sectionPath(),
                        occurrence.regionType(),
                        occurrence.confidence(),
                        occurrence.locationLevel(),
                        occurrence.previewElementRef()));
            }
            return List.copyOf(anchors.values());
        }
        if (evidence.blockId() == null || evidence.blockId().isBlank()) {
            return List.of();
        }
        return List.of(new SourceAnchorSummary(
                evidence.blockId(),
                evidence.sourceOrigin(),
                evidence.sourceExtractionMode(),
                evidence.contextType(),
                evidence.evidenceSummary(),
                evidence.sectionPath(),
                evidence.regionType(),
                evidence.confidence(),
                evidence.locationLevel(),
                evidence.previewElementRef()));
    }

    private ReviewSummary summarize(List<PointReviewResult> pointResults) {
        int passCount = 0;
        int errorCount = 0;
        int warningCount = 0;
        int notConcludedCount = 0;
        int skippedCount = 0;

        for (PointReviewResult pointResult : pointResults) {
            switch (pointResult.pointStatus()) {
                case PASS -> passCount++;
                case ERROR -> errorCount++;
                case WARNING -> warningCount++;
                case NOT_CONCLUDED -> notConcludedCount++;
                case SKIPPED -> skippedCount++;
            }
        }

        return new ReviewSummary(
                pointResults.size(),
                passCount,
                errorCount,
                warningCount,
                notConcludedCount,
                skippedCount);
    }

    private ReviewCompleteness buildCompleteness(ReviewSummary summary) {
        int executablePointCount = summary.plannedPointCount() - summary.skippedCount();
        int concludedPointCount = summary.passCount() + summary.errorCount() + summary.warningCount();
        BigDecimal coverageRate = executablePointCount == 0
                ? BigDecimal.ONE
                : BigDecimal.valueOf(concludedPointCount)
                        .divide(BigDecimal.valueOf(executablePointCount), 4, RoundingMode.HALF_UP);

        ReviewCoverageStatus coverageStatus;
        ConfidenceLevel confidenceLevel;
        if (summary.notConcludedCount() == 0) {
            coverageStatus = ReviewCoverageStatus.FULL_REVIEWED;
            confidenceLevel = ConfidenceLevel.HIGH;
        } else if (concludedPointCount == 0) {
            coverageStatus = ReviewCoverageStatus.LOW_CONFIDENCE_REVIEW;
            confidenceLevel = ConfidenceLevel.LOW;
        } else {
            coverageStatus = ReviewCoverageStatus.PARTIAL_REVIEWED;
            confidenceLevel = ConfidenceLevel.MEDIUM;
        }

        return new ReviewCompleteness(
                coverageStatus,
                executablePointCount,
                concludedPointCount,
                summary.notConcludedCount(),
                coverageRate,
                confidenceLevel);
    }

    private boolean decimalsEqual(String left, String right) {
        return amountsClose(new BigDecimal(left), new BigDecimal(right));
    }

    private boolean amountsClose(BigDecimal left, BigDecimal right) {
        return left.subtract(right).abs().compareTo(AMOUNT_TOLERANCE) <= 0;
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private NotConcludedReasonCode defaultReason(
            NotConcludedReasonCode value,
            NotConcludedReasonCode fallback) {
        return value == null ? fallback : value;
    }

    private String skippedMessage(ReviewPointCode reviewPointCode) {
        return switch (reviewPointCode) {
            case PROGRESS_PAYMENT_RATIO_CONSISTENCY -> "按节点付款场景下不适用进度款比例审核。";
            case COMPLETION_PAYMENT_RATIO_CONSISTENCY -> "按节点付款场景下不适用竣工款比例审核。";
            case SETTLEMENT_PAYMENT_RATIO_CONSISTENCY -> "按节点付款场景下不适用结算款比例审核。";
            case WARRANTY_RETENTION_RATIO_CONSISTENCY -> "按节点付款场景下不适用质保款比例审核。";
            default -> "当前审核点在该场景下不适用。";
        };
    }

    private EvidenceSlotCoverage missingRequiredSlot(ReviewPointCode reviewPointCode) {
        return new EvidenceSlotCoverage(slotKeyOf(reviewPointCode), true, true, EvidenceSlotCoverageStatus.MISSING, "SYS_INDEX_INCOMPLETE", false);
    }

    private EvidenceSlotCoverage defaultSlotCoverage(ReviewPointCode reviewPointCode, PointEvidence evidence) {
        return switch (evidence.status()) {
            case CONFIRMED -> new EvidenceSlotCoverage(
                    slotKeyOf(reviewPointCode),
                    true,
                    true,
                    hasReliableAnchor(evidence) ? EvidenceSlotCoverageStatus.SATISFIED : EvidenceSlotCoverageStatus.PARTIAL,
                    hasReliableAnchor(evidence) ? null : "SYS_EVIDENCE_BUNDLE_INVALID",
                    hasReliableAnchor(evidence));
            case MISSING -> new EvidenceSlotCoverage(
                    slotKeyOf(reviewPointCode),
                    true,
                    true,
                    EvidenceSlotCoverageStatus.MISSING,
                    defaultIfBlank(evidence.diagnosticCode(), "SYS_INDEX_INCOMPLETE"),
                    false);
            case AMBIGUOUS -> new EvidenceSlotCoverage(
                    slotKeyOf(reviewPointCode),
                    true,
                    true,
                    lowConfidenceDiagnostic(evidence.diagnosticCode())
                            ? EvidenceSlotCoverageStatus.LOW_CONFIDENCE
                            : EvidenceSlotCoverageStatus.AMBIGUOUS,
                    defaultIfBlank(
                            evidence.diagnosticCode(),
                            lowConfidenceDiagnostic(evidence.diagnosticCode()) ? "SYS_PARSE_LOW_CONFIDENCE" : "SYS_EVIDENCE_AMBIGUOUS"),
                    hasReliableAnchor(evidence));
            case SYSTEM_FAILURE -> new EvidenceSlotCoverage(
                    slotKeyOf(reviewPointCode),
                    true,
                    true,
                    "SYS_EVIDENCE_BUDGET_EXCEEDED".equals(evidence.diagnosticCode())
                            ? EvidenceSlotCoverageStatus.BUDGET_TRUNCATED
                            : EvidenceSlotCoverageStatus.PARTIAL,
                    defaultIfBlank(evidence.diagnosticCode(), "SYS_RULE_ERROR"),
                    hasReliableAnchor(evidence));
        };
    }

    private PointCoverageStatus evaluatePointCoverageStatus(List<EvidenceSlotCoverage> slotCoverages) {
        var requiredSlots = slotCoverages.stream()
                .filter(EvidenceSlotCoverage::required)
                .toList();
        if (requiredSlots.stream().anyMatch(slot -> slot.coverageStatus() == EvidenceSlotCoverageStatus.LOW_CONFIDENCE)) {
            return PointCoverageStatus.LOW_CONFIDENCE;
        }
        if (requiredSlots.stream().allMatch(slot -> slot.coverageStatus() == EvidenceSlotCoverageStatus.SATISFIED)) {
            return PointCoverageStatus.COMPLETE;
        }
        return PointCoverageStatus.PARTIAL;
    }

    private boolean lowConfidenceDiagnostic(String diagnosticCode) {
        return "SYS_EVIDENCE_LOW_CONFIDENCE".equals(diagnosticCode)
                || "SYS_EVIDENCE_MEDIUM_CONFIDENCE".equals(diagnosticCode)
                || "SYS_PARSE_LOW_CONFIDENCE".equals(diagnosticCode);
    }

    private boolean hasReliableAnchor(PointEvidence evidence) {
        if (evidence == null) {
            return false;
        }
        if (!evidence.occurrences().isEmpty()) {
            return !hasUnreliableExplicitOccurrence(evidence);
        }
        return evidence.blockId() != null && !evidence.blockId().isBlank();
    }

    private boolean hasUnreliableExplicitOccurrence(PointEvidence evidence) {
        return evidence != null
                && !evidence.occurrences().isEmpty()
                && evidence.occurrences().stream()
                        .anyMatch(occurrence -> occurrence.blockId() == null || occurrence.blockId().isBlank());
    }

    private boolean isExactTableCellRef(String previewElementRef) {
        return previewElementRef != null && TABLE_CELL_REF.matcher(previewElementRef).matches();
    }

    private String slotKeyOf(ReviewPointCode reviewPointCode) {
        return switch (reviewPointCode) {
            case PARTY_A_NAME_CONSISTENCY -> "party_a";
            case PARTY_B_NAME_CONSISTENCY -> "party_b";
            case CONTRACT_TOTAL_AMOUNT_CONSISTENCY -> "contract_total_amount";
            case TAX_AMOUNT_FORMULA_CONSISTENCY -> "tax_amount";
            case PREPAYMENT_RATIO_CONSISTENCY -> "prepayment_ratio";
            case PROGRESS_PAYMENT_RATIO_CONSISTENCY -> "progress_payment_ratio";
            case COMPLETION_PAYMENT_RATIO_CONSISTENCY -> "completion_payment_ratio";
            case SETTLEMENT_PAYMENT_RATIO_CONSISTENCY -> "settlement_payment_ratio";
            case WARRANTY_RETENTION_RATIO_CONSISTENCY -> "warranty_retention_ratio";
        };
    }

    private String mapNotConcludedDetail(String diagnosticCode) {
        if (diagnosticCode == null) {
            return null;
        }
        return switch (diagnosticCode) {
            case "SYS_INDEX_INCOMPLETE" -> "INDEX_MISSING";
            case "SYS_ROLE_CONFLICT" -> "ROLE_CONFLICT";
            case "SYS_PARSE_LOW_CONFIDENCE", "SYS_EVIDENCE_LOW_CONFIDENCE", "SYS_EVIDENCE_MEDIUM_CONFIDENCE" -> "PARSE_LOW_CONFIDENCE";
            case "SYS_EVIDENCE_BUDGET_EXCEEDED" -> "BUDGET_TRUNCATED";
            default -> null;
        };
    }

    private record PointPreflight(
            boolean canConclude,
            PointCoverageStatus pointCoverageStatus,
            String diagnosticCode,
            NotConcludedReasonCode reason,
            String notConcludedDetail,
            String message,
            List<EvidenceSlotCoverage> slotCoverages) {

        PointPreflight {
            slotCoverages = slotCoverages == null ? List.of() : List.copyOf(slotCoverages);
        }
    }
}

enum ReviewPointCode {
    PARTY_A_NAME_CONSISTENCY,
    PARTY_B_NAME_CONSISTENCY,
    CONTRACT_TOTAL_AMOUNT_CONSISTENCY,
    TAX_AMOUNT_FORMULA_CONSISTENCY,
    PREPAYMENT_RATIO_CONSISTENCY,
    PROGRESS_PAYMENT_RATIO_CONSISTENCY,
    COMPLETION_PAYMENT_RATIO_CONSISTENCY,
    SETTLEMENT_PAYMENT_RATIO_CONSISTENCY,
    WARRANTY_RETENTION_RATIO_CONSISTENCY
}

enum PointStatus {
    PASS,
    WARNING,
    ERROR,
    NOT_CONCLUDED,
    SKIPPED
}

enum FindingSeverity {
    WARNING,
    ERROR
}

enum ReviewCoverageStatus {
    FULL_REVIEWED,
    PARTIAL_REVIEWED,
    LOW_CONFIDENCE_REVIEW
}

enum ConfidenceLevel {
    HIGH,
    MEDIUM,
    LOW
}

enum NotConcludedReasonCode {
    PARSE_LOW_CONFIDENCE,
    EVIDENCE_NOT_FOUND,
    EVIDENCE_AMBIGUOUS,
    MODEL_UNAVAILABLE,
    MODEL_BUDGET_EXCEEDED,
    INTERNAL_RULE_ERROR
}

enum SkippedReason {
    NOT_APPLICABLE_FOR_PAYMENT_METHOD
}

enum EvidenceStatus {
    CONFIRMED,
    MISSING,
    AMBIGUOUS,
    SYSTEM_FAILURE
}

enum EvidenceSlotCoverageStatus {
    SATISFIED,
    PARTIAL,
    MISSING,
    AMBIGUOUS,
    LOW_CONFIDENCE,
    BUDGET_TRUNCATED
}

enum PointCoverageStatus {
    COMPLETE,
    PARTIAL,
    LOW_CONFIDENCE
}

record ReviewEngineInput(
        String taskId,
        String executionId,
        String sampleId,
        StructuredFieldSet structuredFields,
        Map<ReviewPointCode, PointEvidence> pointEvidences,
        RuntimeRuleSetSnapshot runtimeRuleSetSnapshot) {

    ReviewEngineInput(
            String taskId,
            String executionId,
            String sampleId,
            StructuredFieldSet structuredFields,
            Map<ReviewPointCode, PointEvidence> pointEvidences) {
        this(taskId, executionId, sampleId, structuredFields, pointEvidences, null);
    }

    ReviewEngineInput {
        pointEvidences = Map.copyOf(pointEvidences);
    }

    ReviewEngineInput withEvidenceOverride(ReviewPointCode reviewPointCode, PointEvidence evidence) {
        var updated = new EnumMap<ReviewPointCode, PointEvidence>(ReviewPointCode.class);
        updated.putAll(pointEvidences);
        updated.put(reviewPointCode, evidence);
        return new ReviewEngineInput(taskId, executionId, sampleId, structuredFields, updated, runtimeRuleSetSnapshot);
    }
}

record EvidenceSlotCoverage(
        String slotKey,
        boolean required,
        boolean critical,
        EvidenceSlotCoverageStatus coverageStatus,
        String diagnosticCode,
        boolean reliableAnchor) {
}

record PointEvidence(
        ReviewPointCode reviewPointCode,
        String candidateRole,
        String candidateValue,
        EvidenceStatus status,
        String sourceOrigin,
        String sourceExtractionMode,
        String contextType,
        String blockId,
        String confidence,
        String evidenceSummary,
        String diagnosticCode,
        NotConcludedReasonCode notConcludedReason,
        List<EvidenceSlotCoverage> slotCoverages,
        List<String> sectionPath,
        String regionType,
        String locationLevel,
        String previewElementRef,
        List<PointEvidenceOccurrence> occurrences) {

    PointEvidence(
            ReviewPointCode reviewPointCode,
            String candidateRole,
            String candidateValue,
            EvidenceStatus status,
            String sourceOrigin,
            String sourceExtractionMode,
            String contextType,
            String blockId,
            String confidence,
            String evidenceSummary,
            String diagnosticCode,
            NotConcludedReasonCode notConcludedReason,
            List<EvidenceSlotCoverage> slotCoverages) {
        this(
                reviewPointCode,
                candidateRole,
                candidateValue,
                status,
                sourceOrigin,
                sourceExtractionMode,
                contextType,
                blockId,
                confidence,
                evidenceSummary,
                diagnosticCode,
                notConcludedReason,
                slotCoverages,
                List.of(),
                null,
                blockId == null || blockId.isBlank() ? null : "BLOCK_LEVEL",
                null,
                List.of());
    }

    PointEvidence(
            ReviewPointCode reviewPointCode,
            String candidateRole,
            String candidateValue,
            EvidenceStatus status,
            String sourceOrigin,
            String sourceExtractionMode,
            String contextType,
            String blockId,
            String confidence,
            String evidenceSummary,
            String diagnosticCode,
            NotConcludedReasonCode notConcludedReason,
            List<EvidenceSlotCoverage> slotCoverages,
            List<String> sectionPath,
            String regionType,
            String locationLevel,
            String previewElementRef) {
        this(
                reviewPointCode,
                candidateRole,
                candidateValue,
                status,
                sourceOrigin,
                sourceExtractionMode,
                contextType,
                blockId,
                confidence,
                evidenceSummary,
                diagnosticCode,
                notConcludedReason,
                slotCoverages,
                sectionPath,
                regionType,
                locationLevel,
                previewElementRef,
                List.of());
    }

    PointEvidence {
        slotCoverages = slotCoverages == null ? List.of() : List.copyOf(slotCoverages);
        sectionPath = sectionPath == null ? List.of() : List.copyOf(sectionPath);
        occurrences = occurrences == null ? List.of() : List.copyOf(occurrences);
    }
}

record PointEvidenceOccurrence(
        String candidateValue,
        String blockId,
        String evidenceSummary,
        List<String> sectionPath,
        String regionType,
        String confidence,
        String locationLevel,
        String previewElementRef) {

    static PointEvidenceOccurrence fromSelectedCandidate(EvidenceCandidate candidate) {
        Objects.requireNonNull(candidate, "candidate");
        var blockId = candidate.blockId();
        return new PointEvidenceOccurrence(
                candidate.candidateValue(),
                blockId,
                candidate.blockText(),
                candidate.sectionPath(),
                candidate.regionType(),
                EvidenceConfidenceLevel.HIGH.name(),
                blockId == null || blockId.isBlank() ? null : "BLOCK_LEVEL",
                candidate.previewElementRef());
    }

    PointEvidenceOccurrence {
        sectionPath = sectionPath == null ? List.of() : List.copyOf(sectionPath);
    }
}

record SourceAnchorSummary(
        String blockId,
        String sourceOrigin,
        String sourceExtractionMode,
        String contextType,
        String evidenceSummary,
        List<String> sectionPath,
        String regionType,
        String confidence,
        String locationLevel,
        String previewElementRef) {

    SourceAnchorSummary(
            String blockId,
            String sourceOrigin,
            String sourceExtractionMode,
            String contextType,
            String evidenceSummary) {
        this(
                blockId,
                sourceOrigin,
                sourceExtractionMode,
                contextType,
                evidenceSummary,
                List.of(),
                null,
                null,
                blockId == null || blockId.isBlank() ? null : "BLOCK_LEVEL",
                null);
    }

    SourceAnchorSummary {
        sectionPath = sectionPath == null ? List.of() : List.copyOf(sectionPath);
        if (locationLevel == null && blockId != null && !blockId.isBlank()) {
            locationLevel = "BLOCK_LEVEL";
        }
    }
}

record MissingOptionalSlot(
        String slotKey,
        String cause,
        String businessMessage) {
}

record PointReviewResult(
        ReviewPointCode reviewPointCode,
        PointStatus pointStatus,
        String businessMessage,
        FindingSeverity findingSeverity,
        List<SourceAnchorSummary> sourceAnchors,
        NotConcludedReasonCode notConcludedReason,
        SkippedReason skippedReason,
        PointCoverageStatus pointCoverageStatus,
        String notConcludedDetail,
        List<MissingOptionalSlot> missingOptionalSlots) {

    PointReviewResult {
        sourceAnchors = sourceAnchors == null ? List.of() : List.copyOf(sourceAnchors);
        if (pointCoverageStatus == null) {
            pointCoverageStatus = switch (pointStatus) {
                case PASS, WARNING, ERROR -> PointCoverageStatus.COMPLETE;
                case NOT_CONCLUDED -> PointCoverageStatus.PARTIAL;
                case SKIPPED -> null;
            };
        }
        missingOptionalSlots = missingOptionalSlots == null ? List.of() : List.copyOf(missingOptionalSlots);
    }
}

record ReviewSummary(
        int plannedPointCount,
        int passCount,
        int errorCount,
        int warningCount,
        int notConcludedCount,
        int skippedCount) {
}

record ReviewCompleteness(
        ReviewCoverageStatus reviewCoverageStatus,
        int executablePointCount,
        int concludedPointCount,
        int notConcludedPointCount,
        BigDecimal concludedCoverageRate,
        ConfidenceLevel confidenceLevel) {
}

record ReviewResultSnapshotDraft(
        String taskId,
        String executionId,
        String sampleId,
        String status,
        ReviewSummary summary,
        ReviewCompleteness reviewCompleteness,
        List<PointReviewResult> pointResults,
        List<PointDiagnostic> diagnostics) {

    ReviewResultSnapshotDraft {
        pointResults = List.copyOf(pointResults);
        diagnostics = List.copyOf(diagnostics);
    }
}

record ReviewEngineResult(
        List<PointReviewResult> pointResults,
        ReviewSummary summary,
        ReviewCompleteness reviewCompleteness,
        ReviewResultSnapshotDraft snapshotDraft,
        List<PointDiagnostic> pointDiagnostics) {

    ReviewEngineResult {
        pointResults = List.copyOf(pointResults);
        pointDiagnostics = List.copyOf(pointDiagnostics);
    }
}

final class StructuredFieldSet {

    private final Map<String, String> values;

    private StructuredFieldSet(Map<String, String> values) {
        this.values = Map.copyOf(values);
    }

    static Builder builder() {
        return new Builder();
    }

    static StructuredFieldSet fromMap(Map<String, String> values) {
        return new StructuredFieldSet(values);
    }

    String getRequired(String key) {
        return getOptional(key)
                .orElseThrow(() -> new IllegalArgumentException("Missing structured field: " + key));
    }

    Optional<String> getOptional(String key) {
        return Optional.ofNullable(values.get(key))
                .map(String::trim)
                .filter(value -> !value.isEmpty());
    }

    BigDecimal getDecimal(String key) {
        return new BigDecimal(getRequired(key));
    }

    Optional<BigDecimal> getOptionalDecimal(String key) {
        return getOptional(key).map(BigDecimal::new);
    }

    Map<String, String> asMap() {
        return values;
    }

    static final class Builder {

        private final Map<String, String> values = new java.util.LinkedHashMap<>();

        Builder put(String key, String value) {
            values.put(Objects.requireNonNull(key), value);
            return this;
        }

        StructuredFieldSet build() {
            return new StructuredFieldSet(values);
        }
    }
}
