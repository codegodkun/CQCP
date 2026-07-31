package com.cqcp.apiserver.reviewengine;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

// Audit closure: ReviewPointCode and evidence semantic types are declared in
// MinimalReviewEngine.java; both files must be frozen and reviewed together.
record RuntimeEvidencePacket(
        String schemaVersion,
        String packetId,
        String taskId,
        String executionId,
        String sampleId,
        String ruleSetVersion,
        String family,
        ReviewPointCode reviewPointCode,
        String candidateRole,
        List<PacketCandidateOccurrence> candidateOccurrences,
        List<PacketCoverageSignal> coverageSignals,
        PacketBudget budget,
        ModelAssistAdmission admission,
        RequiredModelOutput requiredOutput) {

    RuntimeEvidencePacket {
        schemaVersion = requireText(schemaVersion, "schemaVersion");
        packetId = requireText(packetId, "packetId");
        taskId = requireText(taskId, "taskId");
        executionId = requireText(executionId, "executionId");
        sampleId = requireText(sampleId, "sampleId");
        ruleSetVersion = requireText(ruleSetVersion, "ruleSetVersion");
        family = requireText(family, "family");
        Objects.requireNonNull(reviewPointCode, "reviewPointCode");
        candidateRole = requireText(candidateRole, "candidateRole");
        candidateOccurrences = List.copyOf(candidateOccurrences);
        coverageSignals = List.copyOf(coverageSignals);
        Objects.requireNonNull(budget, "budget");
        Objects.requireNonNull(admission, "admission");
        Objects.requireNonNull(requiredOutput, "requiredOutput");
    }

    record PacketCandidateOccurrence(
            String occurrenceId,
            String candidateValue,
            String evidenceText,
            RuntimeSourceAnchor sourceAnchor) {
        PacketCandidateOccurrence {
            occurrenceId = requireText(occurrenceId, "occurrenceId");
            candidateValue = requireText(candidateValue, "candidateValue");
            evidenceText = requireText(evidenceText, "evidenceText");
            Objects.requireNonNull(sourceAnchor, "sourceAnchor");
        }
    }

    record RuntimeSourceAnchor(
            String blockId,
            String locationLevel,
            String previewElementRef,
            List<String> sectionPath,
            String regionType,
            String contextType,
            boolean reliable) {
        RuntimeSourceAnchor {
            blockId = requireText(blockId, "blockId");
            locationLevel = requireText(locationLevel, "locationLevel");
            sectionPath = sectionPath == null ? List.of() : List.copyOf(sectionPath);
        }
    }

    record PacketCoverageSignal(
            String slotKey,
            boolean required,
            boolean critical,
            String coverageStatus,
            String diagnosticCode,
            boolean reliableAnchor) {
        PacketCoverageSignal {
            slotKey = requireText(slotKey, "slotKey");
            coverageStatus = requireText(coverageStatus, "coverageStatus");
        }
    }

    record PacketBudget(
            int maxEvidenceChars,
            int usedEvidenceChars,
            boolean complete,
            boolean truncated) {
        PacketBudget {
            if (maxEvidenceChars <= 0
                    || usedEvidenceChars < 0
                    || (!truncated && usedEvidenceChars > maxEvidenceChars)) {
                throw new IllegalArgumentException("packet budget is invalid");
            }
            if (complete == truncated) {
                throw new IllegalArgumentException("complete and truncated must be opposites");
            }
        }
    }

    record ModelAssistAdmission(
            boolean modelCallAllowed,
            String status,
            List<ModelAssistEligibilityEvaluator.EligibilityReason> reasonCodes) {
        ModelAssistAdmission {
            status = requireText(status, "status");
            reasonCodes = List.copyOf(reasonCodes);
            if (reasonCodes.isEmpty()) {
                throw new IllegalArgumentException("admission reasonCodes must not be empty");
            }
            if (modelCallAllowed != "ELIGIBLE".equals(status)) {
                throw new IllegalArgumentException("admission status mismatch");
            }
        }
    }

    record RequiredModelOutput(
            String contract,
            List<String> fields,
            String instruction) {
        RequiredModelOutput {
            contract = requireText(contract, "contract");
            fields = List.copyOf(fields);
            instruction = requireText(instruction, "instruction");
            if (fields.isEmpty()) {
                throw new IllegalArgumentException("required output fields must not be empty");
            }
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}

final class RuntimeEvidencePacketBuilder {

    private static final String SCHEMA_VERSION = "task036-runtime-evidence-packet-v1";

    private final ModelAssistEligibilityEvaluator evaluator =
            new ModelAssistEligibilityEvaluator();

    RuntimeEvidencePacket build(
            PacketIdentity identity,
            PointEvidence evidence,
            AssistPolicy assistPolicy,
            int maxEvidenceChars) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(evidence, "evidence");
        Objects.requireNonNull(assistPolicy, "assistPolicy");
        if (maxEvidenceChars <= 0) {
            throw new IllegalArgumentException("maxEvidenceChars must be positive");
        }

        List<RuntimeEvidencePacket.PacketCandidateOccurrence> packetCandidates =
                packetCandidates(evidence);
        int usedEvidenceChars = packetCandidates.stream()
                .map(RuntimeEvidencePacket.PacketCandidateOccurrence::evidenceText)
                .mapToInt(text -> text.codePointCount(0, text.length()))
                .sum();
        boolean slotBudgetTruncated = evidence.slotCoverages().stream()
                .anyMatch(slot -> slot.coverageStatus() == EvidenceSlotCoverageStatus.BUDGET_TRUNCATED);
        boolean budgetComplete = usedEvidenceChars <= maxEvidenceChars && !slotBudgetTruncated;
        boolean bundleValid = evidence.status() != EvidenceStatus.SYSTEM_FAILURE
                && !evidence.slotCoverages().isEmpty()
                && packetCandidates.stream()
                        .allMatch(candidate -> candidate.sourceAnchor().reliable());

        List<ModelAssistEligibilityEvaluator.ModelAssistCandidate> eligibilityCandidates =
                eligibilityCandidates(evidence, packetCandidates);
        boolean required = evidence.slotCoverages().stream().anyMatch(EvidenceSlotCoverage::required);
        boolean critical = evidence.slotCoverages().stream().anyMatch(EvidenceSlotCoverage::critical);
        var context = new ModelAssistEligibilityEvaluator.RoleContext(
                evidence.reviewPointCode(),
                familyOf(evidence.reviewPointCode()),
                evidence.candidateRole(),
                confidenceOf(evidence),
                eligibilityCandidates,
                new ModelAssistEligibilityEvaluator.SlotPolicy(required, critical),
                assistPolicy.resolverPolicy(),
                assistPolicy.modelAssistMode(),
                assistPolicy.executionStrategy(),
                bundleValid,
                budgetComplete);
        ModelAssistEligibilityEvaluator.EligibilityDecision decision = evaluator.evaluate(context);

        var admission = new RuntimeEvidencePacket.ModelAssistAdmission(
                decision.eligible(),
                decision.eligible() ? "ELIGIBLE" : "ZERO_CALL_REQUIRED",
                decision.reasonCodes());
        var budget = new RuntimeEvidencePacket.PacketBudget(
                maxEvidenceChars,
                usedEvidenceChars,
                budgetComplete,
                !budgetComplete);
        var coverage = evidence.slotCoverages().stream()
                .map(slot -> new RuntimeEvidencePacket.PacketCoverageSignal(
                        slot.slotKey(),
                        slot.required(),
                        slot.critical(),
                        slot.coverageStatus().name(),
                        slot.diagnosticCode(),
                        slot.reliableAnchor()))
                .toList();
        var output = new RuntimeEvidencePacket.RequiredModelOutput(
                "ROLE_CANDIDATE_ANCHOR_ABSTENTION_V1",
                List.of(
                        "suggestedRole",
                        "selectedOccurrenceIds",
                        "selectedAnchorBlockIds",
                        "abstain",
                        "abstentionReason"),
                "Only assess supplied role/candidate/anchor evidence. Do not produce final business adjudication.");
        String packetId = packetId(identity, evidence, packetCandidates);
        return new RuntimeEvidencePacket(
                SCHEMA_VERSION,
                packetId,
                identity.taskId(),
                identity.executionId(),
                identity.sampleId(),
                identity.ruleSetVersion(),
                familyOf(evidence.reviewPointCode()),
                evidence.reviewPointCode(),
                evidence.candidateRole(),
                packetCandidates,
                coverage,
                budget,
                admission,
                output);
    }

    FamilyModelCallPlanner.RoleRequest toRoleRequest(
            String sourceShardId,
            RuntimeEvidencePacket packet,
            int priority) {
        var candidates = packet.candidateOccurrences().stream()
                .map(candidate -> new ModelAssistEligibilityEvaluator.ModelAssistCandidate(
                        candidate.candidateValue(),
                        valueTypeOf(packet.reviewPointCode()),
                        candidate.sourceAnchor().blockId(),
                        candidate.evidenceText(),
                        candidate.sourceAnchor().locationLevel(),
                        candidate.sourceAnchor().previewElementRef(),
                        candidate.sourceAnchor().contextType(),
                        candidate.sourceAnchor().regionType(),
                        localRelation(candidate),
                        conflictDimension(packet),
                        true,
                        true,
                        !candidate.sourceAnchor().reliable()))
                .toList();
        var decision = new ModelAssistEligibilityEvaluator.EligibilityDecision(
                packet.admission().modelCallAllowed(),
                packet.admission().reasonCodes(),
                packet.admission().modelCallAllowed()
                        ? candidates.stream()
                                .map(ModelAssistEligibilityEvaluator.ModelAssistCandidate::blockId)
                                .distinct()
                                .toList()
                        : List.of());
        return new FamilyModelCallPlanner.RoleRequest(
                sourceShardId,
                packet.family(),
                packet.candidateRole(),
                priority,
                decision,
                candidates);
    }

    private static List<RuntimeEvidencePacket.PacketCandidateOccurrence> packetCandidates(
            PointEvidence evidence) {
        var source = new ArrayList<PointEvidenceOccurrence>(evidence.occurrences());
        if (source.isEmpty()
                && evidence.candidateValue() != null
                && !evidence.candidateValue().isBlank()
                && evidence.blockId() != null
                && !evidence.blockId().isBlank()) {
            source.add(new PointEvidenceOccurrence(
                    evidence.candidateValue(),
                    evidence.blockId(),
                    evidence.evidenceSummary(),
                    evidence.sectionPath(),
                    evidence.regionType(),
                    evidence.contextType(),
                    evidence.confidence(),
                    evidence.locationLevel(),
                    evidence.previewElementRef()));
        }
        var result = new ArrayList<RuntimeEvidencePacket.PacketCandidateOccurrence>();
        for (int index = 0; index < source.size(); index++) {
            PointEvidenceOccurrence occurrence = source.get(index);
            boolean reliable = reliableAnchor(occurrence);
            result.add(new RuntimeEvidencePacket.PacketCandidateOccurrence(
                    "OCC-" + String.format(java.util.Locale.ROOT, "%03d", index + 1),
                    occurrence.candidateValue(),
                    occurrence.evidenceSummary(),
                    new RuntimeEvidencePacket.RuntimeSourceAnchor(
                            occurrence.blockId(),
                            occurrence.locationLevel(),
                            occurrence.previewElementRef(),
                            occurrence.sectionPath(),
                            occurrence.regionType(),
                            occurrence.contextType(),
                            reliable)));
        }
        return List.copyOf(result);
    }

    private static List<ModelAssistEligibilityEvaluator.ModelAssistCandidate> eligibilityCandidates(
            PointEvidence evidence,
            List<RuntimeEvidencePacket.PacketCandidateOccurrence> candidates) {
        long distinctValues = candidates.stream()
                .map(RuntimeEvidencePacket.PacketCandidateOccurrence::candidateValue)
                .distinct()
                .count();
        return candidates.stream()
                .map(candidate -> new ModelAssistEligibilityEvaluator.ModelAssistCandidate(
                        candidate.candidateValue(),
                        valueTypeOf(evidence.reviewPointCode()),
                        candidate.sourceAnchor().blockId(),
                        candidate.evidenceText(),
                        candidate.sourceAnchor().locationLevel(),
                        candidate.sourceAnchor().previewElementRef(),
                        candidate.sourceAnchor().contextType(),
                        candidate.sourceAnchor().regionType(),
                        localRelation(candidate),
                        distinctValues > 1
                                ? ModelAssistEligibilityEvaluator.ConflictDimension.VALUE
                                : ModelAssistEligibilityEvaluator.ConflictDimension.NONE,
                        true,
                        true,
                        !candidate.sourceAnchor().reliable()))
                .toList();
    }

    private static ModelAssistEligibilityEvaluator.LocalContextRelation localRelation(
            RuntimeEvidencePacket.PacketCandidateOccurrence candidate) {
        return "TABLE_CELL".equals(candidate.sourceAnchor().locationLevel())
                ? ModelAssistEligibilityEvaluator.LocalContextRelation.SAME_TABLE_ROW
                : candidate.evidenceText().contains(candidate.candidateValue())
                        ? ModelAssistEligibilityEvaluator.LocalContextRelation.SAME_SENTENCE
                        : ModelAssistEligibilityEvaluator.LocalContextRelation.NONE;
    }

    private static ModelAssistEligibilityEvaluator.ConflictDimension conflictDimension(
            RuntimeEvidencePacket packet) {
        return packet.candidateOccurrences().stream()
                        .map(RuntimeEvidencePacket.PacketCandidateOccurrence::candidateValue)
                        .distinct()
                        .count() > 1
                ? ModelAssistEligibilityEvaluator.ConflictDimension.VALUE
                : ModelAssistEligibilityEvaluator.ConflictDimension.NONE;
    }

    private static boolean reliableAnchor(PointEvidenceOccurrence occurrence) {
        if (occurrence.blockId() == null || occurrence.blockId().isBlank()) return false;
        if ("TABLE_CELL".equals(occurrence.locationLevel())) {
            return occurrence.previewElementRef() != null
                    && occurrence.previewElementRef()
                            .matches("^table:[^/]+/row:[0-9]+/cell:[0-9]+$");
        }
        return "BLOCK_LEVEL".equals(occurrence.locationLevel())
                && (occurrence.previewElementRef() == null
                        || occurrence.previewElementRef().isBlank()
                        || occurrence.previewElementRef().equals("block:" + occurrence.blockId()));
    }

    private static EvidenceConfidenceLevel confidenceOf(PointEvidence evidence) {
        try {
            return EvidenceConfidenceLevel.valueOf(evidence.confidence());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "unsupported evidence confidence: " + evidence.confidence(), exception);
        }
    }

    static String familyOf(ReviewPointCode code) {
        return switch (code) {
            case PARTY_A_NAME_CONSISTENCY, PARTY_B_NAME_CONSISTENCY -> "PARTY_FIELDS";
            case CONTRACT_TOTAL_AMOUNT_CONSISTENCY, TAX_AMOUNT_FORMULA_CONSISTENCY -> "AMOUNT_TAX";
            case PREPAYMENT_RATIO_CONSISTENCY,
                 PROGRESS_PAYMENT_RATIO_CONSISTENCY,
                 COMPLETION_PAYMENT_RATIO_CONSISTENCY,
                 SETTLEMENT_PAYMENT_RATIO_CONSISTENCY,
                 WARRANTY_RETENTION_RATIO_CONSISTENCY -> "PAYMENT_TERMS";
        };
    }

    private static String valueTypeOf(ReviewPointCode code) {
        return switch (code) {
            case PARTY_A_NAME_CONSISTENCY, PARTY_B_NAME_CONSISTENCY -> "TEXT";
            case CONTRACT_TOTAL_AMOUNT_CONSISTENCY, TAX_AMOUNT_FORMULA_CONSISTENCY ->
                    "CNY_DECIMAL";
            case PREPAYMENT_RATIO_CONSISTENCY,
                 PROGRESS_PAYMENT_RATIO_CONSISTENCY,
                 COMPLETION_PAYMENT_RATIO_CONSISTENCY,
                 SETTLEMENT_PAYMENT_RATIO_CONSISTENCY,
                 WARRANTY_RETENTION_RATIO_CONSISTENCY -> "PERCENTAGE_POINT";
        };
    }

    private static String packetId(
            PacketIdentity identity,
            PointEvidence evidence,
            List<RuntimeEvidencePacket.PacketCandidateOccurrence> candidates) {
        var canonical = new StringBuilder()
                .append(SCHEMA_VERSION).append('|')
                .append(identity.taskId()).append('|')
                .append(identity.executionId()).append('|')
                .append(identity.sampleId()).append('|')
                .append(identity.ruleSetVersion()).append('|')
                .append(evidence.reviewPointCode()).append('|')
                .append(evidence.candidateRole());
        for (var candidate : candidates) {
            canonical.append('|')
                    .append(candidate.candidateValue()).append('|')
                    .append(candidate.sourceAnchor().blockId()).append('|')
                    .append(Objects.requireNonNullElse(
                            candidate.sourceAnchor().previewElementRef(), ""));
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return "EP-" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    record PacketIdentity(
            String taskId,
            String executionId,
            String sampleId,
            String ruleSetVersion) {
        PacketIdentity {
            taskId = requireText(taskId, "taskId");
            executionId = requireText(executionId, "executionId");
            sampleId = requireText(sampleId, "sampleId");
            ruleSetVersion = requireText(ruleSetVersion, "ruleSetVersion");
        }
    }

    record AssistPolicy(
            ModelAssistEligibilityEvaluator.ResolverPolicy resolverPolicy,
            ModelAssistEligibilityEvaluator.ModelAssistMode modelAssistMode,
            ModelAssistEligibilityEvaluator.ExecutionStrategy executionStrategy) {
        AssistPolicy {
            Objects.requireNonNull(resolverPolicy, "resolverPolicy");
            Objects.requireNonNull(modelAssistMode, "modelAssistMode");
            Objects.requireNonNull(executionStrategy, "executionStrategy");
        }

        static AssistPolicy deterministicNoAssist() {
            return new AssistPolicy(
                    ModelAssistEligibilityEvaluator.ResolverPolicy.DETERMINISTIC_ONLY,
                    ModelAssistEligibilityEvaluator.ModelAssistMode.NONE,
                    ModelAssistEligibilityEvaluator.ExecutionStrategy.DETERMINISTIC);
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
