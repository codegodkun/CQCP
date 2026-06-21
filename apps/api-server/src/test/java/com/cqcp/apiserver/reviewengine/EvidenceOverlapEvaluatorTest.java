package com.cqcp.apiserver.reviewengine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EvidenceOverlapEvaluatorTest {

    @Test
    void convertsBlockRowAndCellAnchorsToCanonicalKeys() {
        assertThat(EvidenceOverlapEvaluator.canonicalKey(anchor("block-1", null)))
                .contains("BLOCK:block-1");
        assertThat(EvidenceOverlapEvaluator.canonicalKey(anchor(
                "block-2",
                "table:table-1/row:3")))
                .contains("TABLE_ROW:block-2:3");
        assertThat(EvidenceOverlapEvaluator.canonicalKey(anchor(
                "block-3",
                "table:table-1/row:4/cell:2")))
                .contains("TABLE_CELL:block-3:4:2");
    }

    @Test
    void calculatesPerfectPositiveOverlap() {
        var result = EvidenceOverlapEvaluator.evaluatePositive(
                Set.of("BLOCK:block-1", "TABLE_ROW:block-2:3"),
                List.of(anchor("block-1", null), anchor("block-2", "table:table-1/row:3")));

        assertThat(result.expectedRecall()).isEqualByComparingTo("1.0000");
        assertThat(result.actualPrecision()).isEqualByComparingTo("1.0000");
        assertThat(result.requiredHit()).isEqualTo(1);
        assertThat(result.missingExpectedBlocks()).isEmpty();
        assertThat(result.unexpectedMatchedBlocks()).isEmpty();
        assertThat(result.attributionFailureReason()).isNull();
    }

    @Test
    void candidateValueMatchDoesNotHideWrongBlockAttribution() {
        var result = EvidenceOverlapEvaluator.evaluatePositive(
                Set.of("BLOCK:block-expected"),
                List.of(anchor("block-wrong", null)));

        assertThat(result.expectedRecall()).isEqualByComparingTo("0.0000");
        assertThat(result.actualPrecision()).isEqualByComparingTo("0.0000");
        assertThat(result.requiredHit()).isZero();
        assertThat(result.attributionFailureReason())
                .isEqualTo(AttributionFailureReason.WRONG_BLOCK_ATTRIBUTION);
    }

    @Test
    void sameRowWrongCellIsNotAcceptedAsCellHit() {
        var result = EvidenceOverlapEvaluator.evaluatePositive(
                Set.of("TABLE_CELL:block-1:2:0"),
                List.of(anchor("block-1", "table:table-1/row:2/cell:1")));

        assertThat(result.requiredHit()).isZero();
        assertThat(result.attributionFailureReason())
                .isEqualTo(AttributionFailureReason.WRONG_TABLE_CELL_ATTRIBUTION);
    }

    @Test
    void sameBlockWrongRowIsNotAcceptedAsRowHit() {
        var result = EvidenceOverlapEvaluator.evaluatePositive(
                Set.of("TABLE_ROW:block-1:2"),
                List.of(anchor("block-1", "table:table-1/row:3")));

        assertThat(result.requiredHit()).isZero();
        assertThat(result.attributionFailureReason())
                .isEqualTo(AttributionFailureReason.WRONG_TABLE_ROW_ATTRIBUTION);
    }

    @Test
    void malformedPreviewReferenceIsReportedAsUnavailable() {
        var result = EvidenceOverlapEvaluator.evaluatePositive(
                Set.of("TABLE_CELL:block-1:2:0"),
                List.of(anchor("block-1", "table:table-1/cell:0")));

        assertThat(result.requiredHit()).isZero();
        assertThat(result.attributionFailureReason())
                .isEqualTo(AttributionFailureReason.SOURCE_ANCHOR_UNAVAILABLE);
    }

    @Test
    void unexpectedSupplementaryAnchorReducesPrecision() {
        var result = EvidenceOverlapEvaluator.evaluatePositive(
                Set.of("BLOCK:block-1"),
                List.of(anchor("block-1", null), anchor("block-2", null)));

        assertThat(result.expectedRecall()).isEqualByComparingTo("1.0000");
        assertThat(result.actualPrecision()).isEqualByComparingTo("0.5000");
        assertThat(result.requiredHit()).isEqualTo(1);
        assertThat(result.attributionFailureReason())
                .isEqualTo(AttributionFailureReason.UNEXPECTED_ANCHOR_SELECTED);
    }

    @Test
    void ambiguousCandidateProducesExplicitNegativeAttribution() {
        var result = EvidenceOverlapEvaluator.evaluateNegative(
                EvidenceStatus.AMBIGUOUS,
                Set.of("BLOCK:block-forbidden"),
                List.of());

        assertThat(result.attributionFailureReason())
                .isEqualTo(AttributionFailureReason.ATTRIBUTION_AMBIGUOUS);
        assertThat(result.requiredHit()).isZero();
    }

    private SourceAnchorSummary anchor(String blockId, String previewElementRef) {
        return new SourceAnchorSummary(
                blockId,
                "NATIVE_WORD",
                "STRUCTURED",
                "NORMAL",
                "测试证据",
                List.of(),
                "BODY",
                "HIGH",
                "BLOCK_LEVEL",
                previewElementRef);
    }
}
