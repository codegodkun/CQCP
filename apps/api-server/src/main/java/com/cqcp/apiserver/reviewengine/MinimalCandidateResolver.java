package com.cqcp.apiserver.reviewengine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

final class MinimalCandidateResolver {

    CandidateResolutionResult resolve(
            ReviewPointCode reviewPointCode,
            String candidateRole,
            List<EvidenceCandidate> rawCandidates) {
        Objects.requireNonNull(reviewPointCode, "reviewPointCode");
        Objects.requireNonNull(candidateRole, "candidateRole");
        Objects.requireNonNull(rawCandidates, "rawCandidates");

        var retainedOccurrences = List.copyOf(rawCandidates);
        var candidates = deduplicate(rawCandidates);
        if (candidates.isEmpty()) {
            return resolutionResult(
                    EvidenceConfidenceLevel.UNKNOWN,
                    Optional.empty(),
                    "SYS_INDEX_INCOMPLETE",
                    NotConcludedReasonCode.EVIDENCE_NOT_FOUND,
                    retainedOccurrences);
        }

        if (hasSameBlockRoleConflict(candidates)) {
            return resolutionResult(
                    EvidenceConfidenceLevel.CONFLICTED,
                    Optional.empty(),
                    "SYS_ROLE_CONFLICT",
                    NotConcludedReasonCode.EVIDENCE_AMBIGUOUS,
                    retainedOccurrences);
        }

        var fullyAttributed = candidates.stream()
                .filter(EvidenceCandidate::roleLabelSignal)
                .filter(EvidenceCandidate::valueFormatSignal)
                .filter(EvidenceCandidate::blockAttributionSignal)
                .toList();
        var fullyAttributedDistinctValues = fullyAttributed.stream()
                .map(EvidenceCandidate::candidateValue)
                .distinct()
                .toList();
        if (fullyAttributedDistinctValues.size() == 1) {
            return resolutionResult(
                    EvidenceConfidenceLevel.HIGH,
                    Optional.of(fullyAttributed.getFirst()),
                    null,
                    null,
                    retainedOccurrences);
        }
        if (fullyAttributedDistinctValues.size() > 1) {
            return resolutionResult(
                    EvidenceConfidenceLevel.CONFLICTED,
                    Optional.empty(),
                    "SYS_ROLE_CONFLICT",
                    NotConcludedReasonCode.EVIDENCE_AMBIGUOUS,
                    retainedOccurrences);
        }

        var roleLabeled = candidates.stream()
                .filter(EvidenceCandidate::roleLabelSignal)
                .sorted(Comparator.comparing(EvidenceCandidate::candidateValue))
                .toList();
        var roleLabeledDistinctValues = roleLabeled.stream()
                .map(EvidenceCandidate::candidateValue)
                .distinct()
                .toList();
        if (roleLabeledDistinctValues.size() == 1) {
            return resolutionResult(
                    EvidenceConfidenceLevel.MEDIUM,
                    Optional.of(roleLabeled.getFirst()),
                    "SYS_EVIDENCE_MEDIUM_CONFIDENCE",
                    NotConcludedReasonCode.EVIDENCE_AMBIGUOUS,
                    retainedOccurrences);
        }
        if (roleLabeledDistinctValues.size() > 1) {
            return resolutionResult(
                    EvidenceConfidenceLevel.CONFLICTED,
                    Optional.empty(),
                    "SYS_ROLE_CONFLICT",
                    NotConcludedReasonCode.EVIDENCE_AMBIGUOUS,
                    retainedOccurrences);
        }

        return resolutionResult(
                EvidenceConfidenceLevel.LOW,
                Optional.of(candidates.getFirst()),
                "SYS_EVIDENCE_LOW_CONFIDENCE",
                NotConcludedReasonCode.EVIDENCE_AMBIGUOUS,
                retainedOccurrences);
    }

    private CandidateResolutionResult resolutionResult(
            EvidenceConfidenceLevel confidenceLevel,
            Optional<EvidenceCandidate> selectedCandidate,
            String diagnosticCode,
            NotConcludedReasonCode notConcludedReason,
            List<EvidenceCandidate> retainedOccurrences) {
        List<EvidenceCandidate> selectedValueOccurrences = List.of();
        if (confidenceLevel == EvidenceConfidenceLevel.HIGH && selectedCandidate.isPresent()) {
            var selected = selectedCandidate.orElseThrow();
            selectedValueOccurrences = retainedOccurrences.stream()
                    .filter(candidate -> candidate.reviewPointCode() == selected.reviewPointCode())
                    .filter(candidate -> Objects.equals(candidate.candidateRole(), selected.candidateRole()))
                    .filter(candidate -> Objects.equals(candidate.candidateValue(), selected.candidateValue()))
                    .filter(EvidenceCandidate::roleLabelSignal)
                    .filter(EvidenceCandidate::valueFormatSignal)
                    .filter(EvidenceCandidate::blockAttributionSignal)
                    .toList();
        }
        return new CandidateResolutionResult(
                confidenceLevel,
                selectedCandidate,
                retainedOccurrences,
                selectedValueOccurrences,
                diagnosticCode,
                notConcludedReason);
    }

    private List<EvidenceCandidate> deduplicate(List<EvidenceCandidate> rawCandidates) {
        Map<String, EvidenceCandidate> deduplicated = new LinkedHashMap<>();
        for (EvidenceCandidate candidate : rawCandidates) {
            var key = candidate.reviewPointCode().name()
                    + "|"
                    + candidate.candidateRole()
                    + "|"
                    + candidate.blockId()
                    + "|"
                    + candidate.candidateValue();
            deduplicated.putIfAbsent(key, candidate);
        }
        return new ArrayList<>(deduplicated.values());
    }

    private boolean hasSameBlockRoleConflict(List<EvidenceCandidate> candidates) {
        Map<String, String> seenValueByBlockRole = new LinkedHashMap<>();
        for (EvidenceCandidate candidate : candidates) {
            var key = candidate.blockId() + "|" + candidate.candidateRole();
            var previousValue = seenValueByBlockRole.putIfAbsent(key, candidate.candidateValue());
            if (previousValue != null && !previousValue.equals(candidate.candidateValue())) {
                return true;
            }
        }
        return false;
    }
}

enum EvidenceConfidenceLevel {
    HIGH,
    MEDIUM,
    LOW,
    CONFLICTED,
    UNKNOWN
}

record EvidenceCandidate(
        ReviewPointCode reviewPointCode,
        String candidateRole,
        String candidateValue,
        String blockId,
        String blockText,
        boolean roleLabelSignal,
        boolean valueFormatSignal,
        boolean blockAttributionSignal,
        List<String> sectionPath,
        String regionType,
        String tableId,
        Integer rowIndex,
        Integer cellIndex,
        String previewElementRef) {

    EvidenceCandidate(
            ReviewPointCode reviewPointCode,
            String candidateRole,
            String candidateValue,
            String blockId,
            String blockText,
            boolean roleLabelSignal,
            boolean valueFormatSignal,
            boolean blockAttributionSignal) {
        this(
                reviewPointCode,
                candidateRole,
                candidateValue,
                blockId,
                blockText,
                roleLabelSignal,
                valueFormatSignal,
                blockAttributionSignal,
                List.of(),
                null,
                null,
                null,
                null,
                null);
    }

    EvidenceCandidate {
        sectionPath = sectionPath == null ? List.of() : List.copyOf(sectionPath);
    }
}

record CandidateResolutionResult(
        EvidenceConfidenceLevel confidenceLevel,
        Optional<EvidenceCandidate> selectedCandidate,
        List<EvidenceCandidate> retainedOccurrences,
        List<EvidenceCandidate> selectedValueOccurrences,
        String diagnosticCode,
        NotConcludedReasonCode notConcludedReason) {

    CandidateResolutionResult(
            EvidenceConfidenceLevel confidenceLevel,
            Optional<EvidenceCandidate> selectedCandidate,
            String diagnosticCode,
            NotConcludedReasonCode notConcludedReason) {
        this(confidenceLevel, selectedCandidate, List.of(), List.of(), diagnosticCode, notConcludedReason);
    }

    CandidateResolutionResult {
        selectedCandidate = selectedCandidate == null ? Optional.empty() : selectedCandidate;
        retainedOccurrences = retainedOccurrences == null ? List.of() : List.copyOf(retainedOccurrences);
        selectedValueOccurrences = selectedValueOccurrences == null ? List.of() : List.copyOf(selectedValueOccurrences);
    }
}
