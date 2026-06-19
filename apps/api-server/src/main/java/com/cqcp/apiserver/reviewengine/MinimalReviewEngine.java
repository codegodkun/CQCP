package com.cqcp.apiserver.reviewengine;

import com.cqcp.apiserver.tuning.PointDiagnostic;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class MinimalReviewEngine {

    private static final BigDecimal AMOUNT_TOLERANCE = new BigDecimal("0.01");
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final List<ReviewPointCode> MONTHLY_ONLY_POINTS = List.of(
            ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY,
            ReviewPointCode.COMPLETION_PAYMENT_RATIO_CONSISTENCY,
            ReviewPointCode.SETTLEMENT_PAYMENT_RATIO_CONSISTENCY,
            ReviewPointCode.WARRANTY_RETENTION_RATIO_CONSISTENCY);

    public ReviewEngineResult review(ReviewEngineInput input) {
        var pointResults = new ArrayList<PointReviewResult>();
        var diagnostics = new ArrayList<PointDiagnostic>();

        for (ReviewPointCode reviewPointCode : ReviewPointCode.values()) {
            pointResults.add(reviewPoint(input, reviewPointCode, diagnostics));
        }

        var summary = summarize(pointResults);
        var completeness = buildCompleteness(pointResults, summary);
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
                    SkippedReason.NOT_APPLICABLE_FOR_PAYMENT_METHOD);
        }

        var evidence = input.pointEvidences().get(reviewPointCode);
        if (evidence == null) {
            return notConcluded(
                    reviewPointCode,
                    "缺少最小合同侧证据，无法形成正式结论。",
                    "SYS_INDEX_INCOMPLETE",
                    NotConcludedReasonCode.EVIDENCE_NOT_FOUND,
                    null,
                    diagnostics);
        }

        if (evidence.status() != EvidenceStatus.CONFIRMED) {
            return switch (evidence.status()) {
                case MISSING -> notConcluded(
                        reviewPointCode,
                        "未找到可靠合同证据，无法形成正式结论。",
                        defaultIfBlank(evidence.diagnosticCode(), "SYS_INDEX_INCOMPLETE"),
                        defaultReason(evidence.notConcludedReason(), NotConcludedReasonCode.EVIDENCE_NOT_FOUND),
                        evidence,
                        diagnostics);
                case AMBIGUOUS -> notConcluded(
                        reviewPointCode,
                        "候选证据存在歧义，无法可靠归属到当前审核点。",
                        defaultIfBlank(evidence.diagnosticCode(), "SYS_EVIDENCE_AMBIGUOUS"),
                        defaultReason(evidence.notConcludedReason(), NotConcludedReasonCode.EVIDENCE_AMBIGUOUS),
                        evidence,
                        diagnostics);
                case SYSTEM_FAILURE -> notConcluded(
                        reviewPointCode,
                        "系统侧能力暂不可用，当前未形成正式结论。",
                        defaultIfBlank(evidence.diagnosticCode(), "SYS_RULE_ERROR"),
                        defaultReason(evidence.notConcludedReason(), NotConcludedReasonCode.INTERNAL_RULE_ERROR),
                        evidence,
                        diagnostics);
                case CONFIRMED -> throw new IllegalStateException("confirmed evidence should be handled earlier");
            };
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
                null);
    }

    private PointReviewResult warning(ReviewPointCode reviewPointCode, String message, PointEvidence evidence) {
        return new PointReviewResult(
                reviewPointCode,
                PointStatus.WARNING,
                message,
                FindingSeverity.WARNING,
                anchorsFor(evidence),
                null,
                null);
    }

    private PointReviewResult error(ReviewPointCode reviewPointCode, String message, PointEvidence evidence) {
        return new PointReviewResult(
                reviewPointCode,
                PointStatus.ERROR,
                message,
                FindingSeverity.ERROR,
                anchorsFor(evidence),
                null,
                null);
    }

    private PointReviewResult notConcluded(
            ReviewPointCode reviewPointCode,
            String message,
            String diagnosticCode,
            NotConcludedReasonCode reason,
            PointEvidence evidence,
            List<PointDiagnostic> diagnostics) {
        if (diagnosticCode != null) {
            diagnostics.add(new PointDiagnostic(
                    reviewPointCode.name(),
                    PointStatus.NOT_CONCLUDED.name(),
                    diagnosticCode,
                    message,
                    evidence == null || evidence.blockId() == null ? List.of() : List.of(evidence.blockId()),
                    evidence == null ? "无证据摘要" : evidence.evidenceSummary(),
                    false));
        }

        return new PointReviewResult(
                reviewPointCode,
                PointStatus.NOT_CONCLUDED,
                message,
                null,
                evidence == null ? List.of() : anchorsFor(evidence),
                reason,
                null);
    }

    private List<SourceAnchorSummary> anchorsFor(PointEvidence evidence) {
        if (evidence == null || evidence.blockId() == null) {
            return List.of();
        }
        return List.of(new SourceAnchorSummary(
                evidence.blockId(),
                evidence.sourceOrigin(),
                evidence.sourceExtractionMode(),
                evidence.contextType(),
                evidence.evidenceSummary()));
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

    private ReviewCompleteness buildCompleteness(List<PointReviewResult> pointResults, ReviewSummary summary) {
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

record ReviewEngineInput(
        String taskId,
        String executionId,
        String sampleId,
        StructuredFieldSet structuredFields,
        Map<ReviewPointCode, PointEvidence> pointEvidences) {

    ReviewEngineInput {
        pointEvidences = Map.copyOf(pointEvidences);
    }

    ReviewEngineInput withEvidenceOverride(ReviewPointCode reviewPointCode, PointEvidence evidence) {
        var updated = new EnumMap<ReviewPointCode, PointEvidence>(ReviewPointCode.class);
        updated.putAll(pointEvidences);
        updated.put(reviewPointCode, evidence);
        return new ReviewEngineInput(taskId, executionId, sampleId, structuredFields, updated);
    }
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
        NotConcludedReasonCode notConcludedReason) {
}

record SourceAnchorSummary(
        String blockId,
        String sourceOrigin,
        String sourceExtractionMode,
        String contextType,
        String evidenceSummary) {
}

record PointReviewResult(
        ReviewPointCode reviewPointCode,
        PointStatus pointStatus,
        String businessMessage,
        FindingSeverity findingSeverity,
        List<SourceAnchorSummary> sourceAnchors,
        NotConcludedReasonCode notConcludedReason,
        SkippedReason skippedReason) {

    PointReviewResult {
        sourceAnchors = List.copyOf(sourceAnchors);
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
