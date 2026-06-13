package com.cqcp.apiserver.reviewengine;

import static org.assertj.core.api.Assertions.assertThat;

import com.cqcp.apiserver.tuning.PointDiagnostic;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResultComposerTest {

    private final ResultComposer composer = new ResultComposer();

    @Test
    void buildsSuccessSnapshotAndCreatesFindingsOnlyForWarningAndError() {
        var sharedAnchor = new SourceAnchorSummary("block-1", "NATIVE_WORD", "STRUCTURED", "NORMAL", "证据 1");
        var secondaryAnchor = new SourceAnchorSummary("block-2", "NATIVE_WORD", "STRUCTURED", "NORMAL", "证据 2");
        var pointResults = List.of(
                new PointReviewResult(
                        ReviewPointCode.PARTY_A_NAME_CONSISTENCY,
                        PointStatus.ERROR,
                        "甲方名称与合同证据不一致。",
                        FindingSeverity.ERROR,
                        List.of(sharedAnchor),
                        null,
                        null),
                new PointReviewResult(
                        ReviewPointCode.TAX_AMOUNT_FORMULA_CONSISTENCY,
                        PointStatus.WARNING,
                        "税额弱校验存在偏差，请人工复核。",
                        FindingSeverity.WARNING,
                        List.of(secondaryAnchor),
                        null,
                        null),
                new PointReviewResult(
                        ReviewPointCode.PARTY_B_NAME_CONSISTENCY,
                        PointStatus.PASS,
                        "乙方名称一致。",
                        null,
                        List.of(sharedAnchor),
                        null,
                        null));
        var reviewEngineResult = new ReviewEngineResult(
                pointResults,
                new ReviewSummary(3, 1, 1, 1, 0, 0),
                new ReviewCompleteness(
                        ReviewCoverageStatus.FULL_REVIEWED,
                        3,
                        3,
                        0,
                        new BigDecimal("1.0000"),
                        ConfidenceLevel.HIGH),
                new ReviewResultSnapshotDraft(
                        "task-001",
                        "execution-001",
                        "sample-001",
                        "SUCCESS",
                        new ReviewSummary(3, 1, 1, 1, 0, 0),
                        new ReviewCompleteness(
                                ReviewCoverageStatus.FULL_REVIEWED,
                                3,
                                3,
                                0,
                                new BigDecimal("1.0000"),
                                ConfidenceLevel.HIGH),
                        pointResults,
                        List.of()),
                List.of());

        var snapshot = composer.compose(defaultInput(), reviewEngineResult);

        assertThat(snapshot.status()).isEqualTo(SnapshotStatus.SUCCESS);
        assertThat(snapshot.findings()).hasSize(2);
        assertThat(snapshot.findings())
                .extracting(ReviewFinding::reviewPointCode)
                .containsExactly(
                        ReviewPointCode.PARTY_A_NAME_CONSISTENCY,
                        ReviewPointCode.TAX_AMOUNT_FORMULA_CONSISTENCY);
        assertThat(snapshot.findings())
                .extracting(ReviewFinding::severity)
                .containsExactly(FindingSeverity.ERROR, FindingSeverity.WARNING);
        assertThat(snapshot.sourceAnchors()).hasSize(2);
        assertThat(snapshot.structuredFieldsSnapshot()).containsEntry("partyAName", "甲方公司");
        assertThat(snapshot.ruleSetVersion()).isEqualTo("ruleset-v1");
    }

    @Test
    void buildsPartialSuccessSnapshotWhenNotConcludedExistsAndKeepsDiagnosticsSeparated() {
        var sharedAnchor = new SourceAnchorSummary("block-1", "NATIVE_WORD", "STRUCTURED", "NORMAL", "证据 1");
        var pointResults = List.of(
                new PointReviewResult(
                        ReviewPointCode.PARTY_A_NAME_CONSISTENCY,
                        PointStatus.PASS,
                        "甲方名称一致。",
                        null,
                        List.of(sharedAnchor),
                        null,
                        null),
                new PointReviewResult(
                        ReviewPointCode.PARTY_B_NAME_CONSISTENCY,
                        PointStatus.NOT_CONCLUDED,
                        "系统侧能力暂不可用，当前未形成正式结论。",
                        null,
                        List.of(),
                        NotConcludedReasonCode.MODEL_UNAVAILABLE,
                        null));
        var diagnostics = List.of(new PointDiagnostic(
                ReviewPointCode.PARTY_B_NAME_CONSISTENCY.name(),
                PointStatus.NOT_CONCLUDED.name(),
                "SYS_MODEL_TIMEOUT",
                "系统侧能力暂不可用，当前未形成正式结论。",
                List.of("block-1"),
                "模型超时。",
                false));
        var reviewEngineResult = new ReviewEngineResult(
                pointResults,
                new ReviewSummary(2, 1, 0, 0, 1, 0),
                new ReviewCompleteness(
                        ReviewCoverageStatus.PARTIAL_REVIEWED,
                        2,
                        1,
                        1,
                        new BigDecimal("0.5000"),
                        ConfidenceLevel.MEDIUM),
                new ReviewResultSnapshotDraft(
                        "task-001",
                        "execution-001",
                        "sample-001",
                        "PARTIAL_SUCCESS",
                        new ReviewSummary(2, 1, 0, 0, 1, 0),
                        new ReviewCompleteness(
                                ReviewCoverageStatus.PARTIAL_REVIEWED,
                                2,
                                1,
                                1,
                                new BigDecimal("0.5000"),
                                ConfidenceLevel.MEDIUM),
                        pointResults,
                        diagnostics),
                diagnostics);

        var snapshot = composer.compose(defaultInput(), reviewEngineResult);

        assertThat(snapshot.status()).isEqualTo(SnapshotStatus.PARTIAL_SUCCESS);
        assertThat(snapshot.findings()).isEmpty();
        assertThat(snapshot.diagnostics())
                .extracting(PointDiagnostic::diagnosticCode)
                .containsExactly("SYS_MODEL_TIMEOUT");
        assertThat(snapshot.pointResults())
                .extracting(PointReviewResult::pointStatus)
                .containsExactly(PointStatus.PASS, PointStatus.NOT_CONCLUDED);
    }

    private ResultComposerInput defaultInput() {
        return new ResultComposerInput(
                "task-001",
                "execution-001",
                Map.of("partyAName", "甲方公司", "partyBName", "乙方公司"),
                List.of(
                        new ReviewPointSnapshot(
                                ReviewPointCode.PARTY_A_NAME_CONSISTENCY,
                                "P001",
                                "甲方名称一致性",
                                "PARTY_FIELDS",
                                "ENGINEERING_PROCUREMENT",
                                FindingSeverity.ERROR,
                                1),
                        new ReviewPointSnapshot(
                                ReviewPointCode.PARTY_B_NAME_CONSISTENCY,
                                "P002",
                                "乙方名称一致性",
                                "PARTY_FIELDS",
                                "ENGINEERING_PROCUREMENT",
                                FindingSeverity.ERROR,
                                2)),
                List.of(),
                new VersionReferences(
                        "contract-type-v1",
                        "ruleset-v1",
                        "budget-v1",
                        "model-v1",
                        "parser-v1",
                        "prompt-v1",
                        "schema-v1",
                        "pattern-v1",
                        "lexicon-v1",
                        "selector-v1"),
                Instant.parse("2026-06-13T00:00:00Z"));
    }
}
