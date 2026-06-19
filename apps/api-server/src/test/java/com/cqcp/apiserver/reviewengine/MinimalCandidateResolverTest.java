package com.cqcp.apiserver.reviewengine;

import static org.assertj.core.api.Assertions.assertThat;

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
}
