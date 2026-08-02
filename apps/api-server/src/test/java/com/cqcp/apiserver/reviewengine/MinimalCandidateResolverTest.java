package com.cqcp.apiserver.reviewengine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class MinimalCandidateResolverTest {

    private final MinimalCandidateResolver resolver = new MinimalCandidateResolver();

    @Test
    void returnsUnknownWhenCandidateListIsEmpty() {
        var result = resolver.resolve(
                ReviewPointCode.CONTRACT_TOTAL_AMOUNT_CONSISTENCY,
                "CONTRACT_TOTAL_AMOUNT",
                List.of());

        assertThat(result.confidenceLevel()).isEqualTo(EvidenceConfidenceLevel.UNKNOWN);
        assertThat(result.selectedCandidate()).isEmpty();
        assertThat(result.diagnosticCode()).isEqualTo("SYS_INDEX_INCOMPLETE");
        assertThat(result.notConcludedReason()).isEqualTo(NotConcludedReasonCode.EVIDENCE_NOT_FOUND);
    }

    @Test
    void returnsHighForSingleFullyAttributedCandidate() {
        var candidate = new EvidenceCandidate(
                ReviewPointCode.CONTRACT_TOTAL_AMOUNT_CONSISTENCY,
                "CONTRACT_TOTAL_AMOUNT",
                "884800",
                "block-total",
                "\u542b\u7a0e\u5408\u540c\u603b\u4ef7 884800 \u5143",
                true,
                true,
                true);

        var result = resolver.resolve(
                ReviewPointCode.CONTRACT_TOTAL_AMOUNT_CONSISTENCY,
                "CONTRACT_TOTAL_AMOUNT",
                List.of(candidate));

        assertThat(result.confidenceLevel()).isEqualTo(EvidenceConfidenceLevel.HIGH);
        assertThat(result.selectedCandidate()).contains(candidate);
        assertThat(result.diagnosticCode()).isNull();
        assertThat(result.notConcludedReason()).isNull();
    }

    @Test
    void returnsMediumForSingleCandidateWithRoleLabelButMissingOneDeterministicSignal() {
        var candidate = new EvidenceCandidate(
                ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY,
                "PROGRESS_PAYMENT_RATIO",
                "70",
                "block-progress",
                "\u8fdb\u5ea6\u6b3e 70%",
                true,
                true,
                false);

        var result = resolver.resolve(
                ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY,
                "PROGRESS_PAYMENT_RATIO",
                List.of(candidate));

        assertThat(result.confidenceLevel()).isEqualTo(EvidenceConfidenceLevel.MEDIUM);
        assertThat(result.selectedCandidate()).contains(candidate);
        assertThat(result.diagnosticCode()).isEqualTo("SYS_EVIDENCE_MEDIUM_CONFIDENCE");
        assertThat(result.notConcludedReason()).isEqualTo(NotConcludedReasonCode.EVIDENCE_AMBIGUOUS);
    }

    @Test
    void returnsLowForCandidateWithoutSufficientAttributionSignals() {
        var candidate = new EvidenceCandidate(
                ReviewPointCode.WARRANTY_RETENTION_RATIO_CONSISTENCY,
                "WARRANTY_RETENTION_RATIO",
                "5",
                "block-warranty",
                "5%",
                false,
                true,
                false);

        var result = resolver.resolve(
                ReviewPointCode.WARRANTY_RETENTION_RATIO_CONSISTENCY,
                "WARRANTY_RETENTION_RATIO",
                List.of(candidate));

        assertThat(result.confidenceLevel()).isEqualTo(EvidenceConfidenceLevel.LOW);
        assertThat(result.selectedCandidate()).contains(candidate);
        assertThat(result.diagnosticCode()).isEqualTo("SYS_EVIDENCE_LOW_CONFIDENCE");
        assertThat(result.notConcludedReason()).isEqualTo(NotConcludedReasonCode.EVIDENCE_AMBIGUOUS);
    }

    @Test
    void returnsConflictedWhenMultipleCandidatesCompeteWithinSameBlockAndRole() {
        var left = new EvidenceCandidate(
                ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY,
                "PROGRESS_PAYMENT_RATIO",
                "70",
                "block-progress",
                "\u5f62\u8c61\u8fdb\u5ea6\u4ea7\u503c\u768470%",
                true,
                true,
                true);
        var right = new EvidenceCandidate(
                ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY,
                "PROGRESS_PAYMENT_RATIO",
                "100",
                "block-progress",
                "\u5f00\u7968\u91d1\u989d100%",
                true,
                true,
                true);

        var result = resolver.resolve(
                ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY,
                "PROGRESS_PAYMENT_RATIO",
                List.of(left, right));

        assertThat(result.confidenceLevel()).isEqualTo(EvidenceConfidenceLevel.CONFLICTED);
        assertThat(result.selectedCandidate()).isEmpty();
        assertThat(result.diagnosticCode()).isEqualTo("SYS_ROLE_CONFLICT");
        assertThat(result.notConcludedReason()).isEqualTo(NotConcludedReasonCode.EVIDENCE_AMBIGUOUS);
    }

    @Test
    void sameExactValueRetainsDistinctBlockAndCellOccurrences() {
        var firstCell = candidate("block-table", "table:table-1/row:2/cell:0", "含税总价 884800 元");
        var secondCell = candidate("block-table", "table:table-1/row:2/cell:1", "含税总价 884800 元");
        var rowFallback = candidate("block-table", "table:table-1/row:2", "含税总价 884800 元");
        var otherBlock = candidate("block-summary", null, "合同总金额为 884800 元");
        var rawCandidates = new java.util.ArrayList<>(List.of(firstCell, secondCell, rowFallback, otherBlock));

        var result = resolver.resolve(
                ReviewPointCode.CONTRACT_TOTAL_AMOUNT_CONSISTENCY,
                "CONTRACT_TOTAL_AMOUNT",
                rawCandidates);

        assertThat(result.confidenceLevel()).isEqualTo(EvidenceConfidenceLevel.HIGH);
        assertThat(result.selectedCandidate()).contains(firstCell);
        assertThat(result.retainedOccurrences())
                .containsExactly(firstCell, secondCell, rowFallback, otherBlock);
        assertThat(result.selectedValueOccurrences())
                .containsExactly(firstCell, secondCell, rowFallback, otherBlock);

        rawCandidates.clear();
        assertThat(result.retainedOccurrences()).hasSize(4);
        assertThatThrownBy(() -> result.retainedOccurrences().add(firstCell))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.selectedValueOccurrences().add(firstCell))
                .isInstanceOf(UnsupportedOperationException.class);

        var firstBlock = candidate("block-first", null, "第一处合同总金额为 884800 元");
        var secondBlock = candidate("block-second", null, "第二处合同总金额为 884800 元");
        var distinctBlockResult = resolver.resolve(
                ReviewPointCode.CONTRACT_TOTAL_AMOUNT_CONSISTENCY,
                "CONTRACT_TOTAL_AMOUNT",
                List.of(firstBlock, secondBlock));

        assertThat(distinctBlockResult.confidenceLevel()).isEqualTo(EvidenceConfidenceLevel.HIGH);
        assertThat(distinctBlockResult.selectedValueOccurrences())
                .containsExactly(firstBlock, secondBlock);
    }

    @Test
    void distinctValuesRemainConflictedWithoutSelectedValueOccurrences() {
        var left = candidate("block-left", null, "合同总金额为 884800 元");
        var right = new EvidenceCandidate(
                ReviewPointCode.CONTRACT_TOTAL_AMOUNT_CONSISTENCY,
                "CONTRACT_TOTAL_AMOUNT",
                "990000",
                "block-right",
                "合同总金额为 990000 元",
                true,
                true,
                true);

        var result = resolver.resolve(
                ReviewPointCode.CONTRACT_TOTAL_AMOUNT_CONSISTENCY,
                "CONTRACT_TOTAL_AMOUNT",
                List.of(left, right));

        assertThat(result.confidenceLevel()).isEqualTo(EvidenceConfidenceLevel.CONFLICTED);
        assertThat(result.selectedCandidate()).isEmpty();
        assertThat(result.retainedOccurrences()).containsExactly(left, right);
        assertThat(result.selectedValueOccurrences()).isEmpty();
        assertThat(result.diagnosticCode()).isEqualTo("SYS_ROLE_CONFLICT");
        assertThat(result.notConcludedReason()).isEqualTo(NotConcludedReasonCode.EVIDENCE_AMBIGUOUS);
    }

    private EvidenceCandidate candidate(String blockId, String previewElementRef, String blockText) {
        return new EvidenceCandidate(
                ReviewPointCode.CONTRACT_TOTAL_AMOUNT_CONSISTENCY,
                "CONTRACT_TOTAL_AMOUNT",
                "884800",
                blockId,
                blockText,
                true,
                true,
                true,
                List.of("合同价款"),
                "BODY",
                "table-1",
                2,
                previewElementRef != null && previewElementRef.contains("/cell:") ? 1 : null,
                previewElementRef);
    }

    @Test
    void twentyParamConstructorPreservesNewFields() {
        var candidate = new EvidenceCandidate(
                ReviewPointCode.PARTY_A_NAME_CONSISTENCY,
                "PARTY_A",
                "测试公司",
                "block-1",
                "甲方：测试公司",
                true,
                true,
                true,
                List.of("合同主体"),
                "BODY",
                null,
                null,
                null,
                null,
                "NORMAL",
                "NATIVE_WORD",
                "STRUCTURED",
                "HIGH",
                "BLOCK_LEVEL",
                List.of());

        assertThat(candidate.contextType()).isEqualTo("NORMAL");
        assertThat(candidate.sourceOrigin()).isEqualTo("NATIVE_WORD");
        assertThat(candidate.sourceExtractionMode()).isEqualTo("STRUCTURED");
        assertThat(candidate.blockConfidence()).isEqualTo("HIGH");
        assertThat(candidate.previewAnchorLevel()).isEqualTo("BLOCK_LEVEL");
        assertThat(candidate.semanticContextTypes()).isEmpty();
    }

    @Test
    void legacyFourteenParamConstructorSentinelsAreNull() {
        var candidate = new EvidenceCandidate(
                ReviewPointCode.PARTY_A_NAME_CONSISTENCY,
                "PARTY_A",
                "测试公司",
                "block-1",
                "甲方：测试公司",
                true,
                true,
                true);

        assertThat(candidate.contextType()).isNull();
        assertThat(candidate.sourceOrigin()).isNull();
        assertThat(candidate.sourceExtractionMode()).isNull();
        assertThat(candidate.blockConfidence()).isNull();
        assertThat(candidate.previewAnchorLevel()).isNull();
        assertThat(candidate.semanticContextTypes()).isEmpty();
    }
}
