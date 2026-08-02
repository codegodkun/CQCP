package com.cqcp.apiserver.reviewengine;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Pure-rule model-assist admission. It never mutates deterministic resolution
 * and never performs a model call.
 */
final class ModelAssistEligibilityEvaluator {

    EligibilityDecision evaluate(RoleContext context) {
        Objects.requireNonNull(context, "context");
        var reasons = new ArrayList<EligibilityReason>();

        if (!context.bundleValid()) {
            return ineligible(EligibilityReason.BUNDLE_INVALID);
        }
        if (!context.budgetComplete()) {
            return ineligible(EligibilityReason.BUDGET_INCOMPLETE);
        }
        if (!context.slotPolicy().required() && !context.slotPolicy().critical()) {
            return ineligible(EligibilityReason.REQUIRED_SLOT_UNAVAILABLE);
        }
        if (context.candidates().isEmpty()
                || context.confidenceLevel() == EvidenceConfidenceLevel.UNKNOWN) {
            return ineligible(EligibilityReason.NO_CANDIDATE);
        }
        if (context.modelAssistMode() == ModelAssistMode.NONE) {
            return ineligible(context.confidenceLevel() == EvidenceConfidenceLevel.HIGH
                    ? EligibilityReason.DETERMINISTIC_HIGH_ZERO_CALL
                    : EligibilityReason.MODEL_ASSIST_POLICY_NONE);
        }

        return switch (context.confidenceLevel()) {
            case HIGH -> evaluateHigh(context);
            case MEDIUM -> evaluateMedium(context);
            case CONFLICTED -> evaluateConflicted(context);
            case LOW -> evaluateLow(context);
            case UNKNOWN -> ineligible(EligibilityReason.NO_CANDIDATE);
        };
    }

    private EligibilityDecision evaluateHigh(RoleContext context) {
        if (context.modelAssistMode() != ModelAssistMode.SEMANTIC_INTERPRETATION
                || context.executionStrategy() != ExecutionStrategy.LLM_SEMANTIC_WITH_GUARD) {
            return ineligible(EligibilityReason.DETERMINISTIC_HIGH_ZERO_CALL);
        }
        if (context.candidates().stream().noneMatch(ModelAssistCandidate::hasReliableAnchor)) {
            return ineligible(EligibilityReason.RELIABLE_ANCHOR_MISSING);
        }
        return eligible(
                EligibilityReason.ELIGIBLE_HIGH_SEMANTIC_GUARD,
                context.candidates());
    }

    private EligibilityDecision evaluateMedium(RoleContext context) {
        if (context.resolverPolicy() != ResolverPolicy.GEMMA_IF_AMBIGUOUS) {
            return ineligible(EligibilityReason.RESOLVER_POLICY_DISALLOWS_MODEL);
        }
        List<ModelAssistCandidate> anchored =
                context.candidates().stream().filter(ModelAssistCandidate::hasReliableAnchor).toList();
        if (anchored.isEmpty()) {
            return ineligible(EligibilityReason.RELIABLE_ANCHOR_MISSING);
        }
        return eligible(EligibilityReason.ELIGIBLE_MEDIUM_AMBIGUITY, anchored);
    }

    private EligibilityDecision evaluateConflicted(RoleContext context) {
        if (context.resolverPolicy() != ResolverPolicy.GEMMA_IF_AMBIGUOUS) {
            return ineligible(EligibilityReason.RESOLVER_POLICY_DISALLOWS_MODEL);
        }
        List<ModelAssistCandidate> comparable = context.candidates().stream()
                .filter(ModelAssistCandidate::locallyComparable)
                .toList();
        long distinctValues = comparable.stream()
                .map(ModelAssistCandidate::candidateValue)
                .distinct()
                .count();
        long distinctValueTypes = comparable.stream()
                .map(ModelAssistCandidate::valueType)
                .distinct()
                .count();
        if (comparable.size() < 2 || distinctValues < 2 || distinctValueTypes != 1) {
            return ineligible(EligibilityReason.CONFLICT_NOT_LOCALLY_COMPARABLE);
        }
        return eligible(EligibilityReason.ELIGIBLE_CONFLICT_LOCAL_CONTEXT, comparable);
    }

    private EligibilityDecision evaluateLow(RoleContext context) {
        if (context.resolverPolicy() != ResolverPolicy.GEMMA_IF_AMBIGUOUS) {
            return ineligible(EligibilityReason.RESOLVER_POLICY_DISALLOWS_MODEL);
        }
        List<ModelAssistCandidate> minimumCandidates = context.candidates().stream()
                .filter(ModelAssistCandidate::meetsLowCandidateThreshold)
                .toList();
        if (minimumCandidates.isEmpty()) {
            boolean anyAnchor =
                    context.candidates().stream().anyMatch(ModelAssistCandidate::hasReliableAnchor);
            return ineligible(anyAnchor
                    ? EligibilityReason.LOW_CANDIDATE_THRESHOLD_NOT_MET
                    : EligibilityReason.RELIABLE_ANCHOR_MISSING);
        }
        return eligible(EligibilityReason.ELIGIBLE_LOW_MINIMUM_CANDIDATE, minimumCandidates);
    }

    private static EligibilityDecision eligible(
            EligibilityReason reason,
            List<ModelAssistCandidate> candidates) {
        Set<String> blockIds = new LinkedHashSet<>();
        for (ModelAssistCandidate candidate : candidates) {
            blockIds.add(candidate.blockId());
        }
        return new EligibilityDecision(true, List.of(reason), List.copyOf(blockIds));
    }

    private static EligibilityDecision ineligible(EligibilityReason reason) {
        return new EligibilityDecision(false, List.of(reason), List.of());
    }

    record RoleContext(
            ReviewPointCode reviewPointCode,
            String family,
            String candidateRole,
            EvidenceConfidenceLevel confidenceLevel,
            List<ModelAssistCandidate> candidates,
            SlotPolicy slotPolicy,
            ResolverPolicy resolverPolicy,
            ModelAssistMode modelAssistMode,
            ExecutionStrategy executionStrategy,
            boolean bundleValid,
            boolean budgetComplete) {
        RoleContext {
            Objects.requireNonNull(reviewPointCode, "reviewPointCode");
            family = requireText(family, "family");
            candidateRole = requireText(candidateRole, "candidateRole");
            Objects.requireNonNull(confidenceLevel, "confidenceLevel");
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
            Objects.requireNonNull(slotPolicy, "slotPolicy");
            Objects.requireNonNull(resolverPolicy, "resolverPolicy");
            Objects.requireNonNull(modelAssistMode, "modelAssistMode");
            Objects.requireNonNull(executionStrategy, "executionStrategy");
        }
    }

    record SlotPolicy(boolean required, boolean critical) {
    }

    record ModelAssistCandidate(
            String candidateValue,
            String valueType,
            String blockId,
            String evidenceText,
            String locationLevel,
            String previewElementRef,
            String contextType,
            String regionType,
            LocalContextRelation localContextRelation,
            ConflictDimension conflictDimension,
            boolean roleLabelSignal,
            boolean valueFormatSignal,
            boolean strongExcluded) {
        ModelAssistCandidate {
            candidateValue = requireText(candidateValue, "candidateValue");
            valueType = requireText(valueType, "valueType");
            blockId = requireText(blockId, "blockId");
            evidenceText = requireText(evidenceText, "evidenceText");
            Objects.requireNonNull(localContextRelation, "localContextRelation");
            Objects.requireNonNull(conflictDimension, "conflictDimension");
        }

        boolean hasReliableAnchor() {
            if (strongExcluded || isForbiddenContext(contextType)) return false;
            if ("TABLE_CELL".equals(locationLevel)) {
                return previewElementRef != null
                        && previewElementRef.matches("^table:[^/]+/row:[0-9]+/cell:[0-9]+$");
            }
            return "BLOCK_LEVEL".equals(locationLevel)
                    && (previewElementRef == null
                            || previewElementRef.isBlank()
                            || previewElementRef.equals("block:" + blockId));
        }

        boolean locallyComparable() {
            return hasReliableAnchor()
                    && localContextRelation != LocalContextRelation.NONE
                    && conflictDimension != ConflictDimension.NONE;
        }

        boolean meetsLowCandidateThreshold() {
            return hasReliableAnchor()
                    && roleLabelSignal
                    && valueFormatSignal
                    && localContextRelation != LocalContextRelation.NONE;
        }

        private static boolean isForbiddenContext(String contextType) {
            return Set.of("DELETED", "VOIDED", "TOC", "HEADER_FOOTER")
                    .contains(Objects.requireNonNullElse(contextType, ""));
        }
    }

    record EligibilityDecision(
            boolean eligible,
            List<EligibilityReason> reasonCodes,
            List<String> requiredBlockIds) {
        EligibilityDecision {
            reasonCodes = List.copyOf(reasonCodes);
            requiredBlockIds = List.copyOf(requiredBlockIds);
            if (reasonCodes.isEmpty()) {
                throw new IllegalArgumentException("reasonCodes must not be empty");
            }
            if (!eligible && !requiredBlockIds.isEmpty()) {
                throw new IllegalArgumentException("ineligible decision cannot request blocks");
            }
        }
    }

    enum ResolverPolicy {
        DETERMINISTIC_ONLY,
        GEMMA_IF_AMBIGUOUS
    }

    enum ModelAssistMode {
        NONE,
        AMBIGUITY_RESOLUTION,
        SEMANTIC_INTERPRETATION
    }

    enum ExecutionStrategy {
        DETERMINISTIC,
        LLM_EXTRACT_THEN_RULE,
        LLM_SEMANTIC_WITH_GUARD
    }

    enum LocalContextRelation {
        SAME_SENTENCE,
        SAME_TABLE_ROW,
        ADJACENT_LABEL,
        NONE
    }

    enum ConflictDimension {
        VALUE,
        UNIT,
        SECTION,
        LABEL,
        NONE
    }

    enum EligibilityReason {
        ELIGIBLE_HIGH_SEMANTIC_GUARD,
        ELIGIBLE_MEDIUM_AMBIGUITY,
        ELIGIBLE_CONFLICT_LOCAL_CONTEXT,
        ELIGIBLE_LOW_MINIMUM_CANDIDATE,
        DETERMINISTIC_HIGH_ZERO_CALL,
        MODEL_ASSIST_POLICY_NONE,
        RESOLVER_POLICY_DISALLOWS_MODEL,
        NO_CANDIDATE,
        REQUIRED_SLOT_UNAVAILABLE,
        BUNDLE_INVALID,
        BUDGET_INCOMPLETE,
        RELIABLE_ANCHOR_MISSING,
        CONFLICT_NOT_LOCALLY_COMPARABLE,
        LOW_CANDIDATE_THRESHOLD_NOT_MET
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
