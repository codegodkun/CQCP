package com.cqcp.apiserver.reviewengine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

record FamilyModelCallPlan(
        String family,
        List<String> sourceShardIds,
        List<String> requestedRoles,
        List<String> selectedBlockIds,
        List<String> uncoveredRoles,
        List<RoleAbstention> ineligibleRoles,
        String priorityReason,
        boolean modelCallAllowed,
        int maxEvidenceChars,
        int usedEvidenceChars) {

    FamilyModelCallPlan {
        family = requireText(family, "family");
        sourceShardIds = List.copyOf(sourceShardIds);
        requestedRoles = List.copyOf(requestedRoles);
        selectedBlockIds = List.copyOf(selectedBlockIds);
        uncoveredRoles = List.copyOf(uncoveredRoles);
        ineligibleRoles = List.copyOf(ineligibleRoles);
        priorityReason = requireText(priorityReason, "priorityReason");
        if (maxEvidenceChars <= 0 || usedEvidenceChars < 0 || usedEvidenceChars > maxEvidenceChars) {
            throw new IllegalArgumentException("family evidence budget is invalid");
        }
        if (modelCallAllowed != !requestedRoles.isEmpty()) {
            throw new IllegalArgumentException("modelCallAllowed must match requestedRoles");
        }
    }

    record RoleAbstention(
            String candidateRole,
            List<ModelAssistEligibilityEvaluator.EligibilityReason> reasonCodes) {
        RoleAbstention {
            candidateRole = requireText(candidateRole, "candidateRole");
            reasonCodes = List.copyOf(reasonCodes);
            if (reasonCodes.isEmpty()) {
                throw new IllegalArgumentException("abstention reasonCodes must not be empty");
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

final class FamilyModelCallPlanner {

    FamilyModelCallPlan plan(
            String family,
            List<RoleRequest> roleRequests,
            int maxEvidenceChars) {
        if (family == null || family.isBlank()) {
            throw new IllegalArgumentException("family must not be blank");
        }
        if (maxEvidenceChars <= 0) {
            throw new IllegalArgumentException("maxEvidenceChars must be positive");
        }
        List<RoleRequest> requests = roleRequests == null ? List.of() : List.copyOf(roleRequests);
        if (requests.stream().anyMatch(request -> !family.equals(request.family()))) {
            throw new IllegalArgumentException("all role requests must belong to the same family");
        }

        var ordered = requests.stream()
                .sorted(Comparator.comparingInt(RoleRequest::priority).reversed()
                        .thenComparing(RoleRequest::candidateRole))
                .toList();
        Set<String> shardIds = new LinkedHashSet<>();
        Set<String> requestedRoles = new LinkedHashSet<>();
        Set<String> selectedBlocks = new LinkedHashSet<>();
        Set<String> uncoveredRoles = new LinkedHashSet<>();
        var ineligible = new ArrayList<FamilyModelCallPlan.RoleAbstention>();
        int used = 0;

        for (RoleRequest request : ordered) {
            shardIds.add(request.sourceShardId());
            if (!request.eligibilityDecision().eligible()) {
                ineligible.add(new FamilyModelCallPlan.RoleAbstention(
                        request.candidateRole(),
                        request.eligibilityDecision().reasonCodes()));
                continue;
            }

            Map<String, Integer> requestedLengths =
                    evidenceLengths(request.candidates(), request.eligibilityDecision().requiredBlockIds());
            int incremental = requestedLengths.entrySet().stream()
                    .filter(entry -> !selectedBlocks.contains(entry.getKey()))
                    .mapToInt(Map.Entry::getValue)
                    .sum();
            if (used + incremental > maxEvidenceChars) {
                uncoveredRoles.add(request.candidateRole());
                continue;
            }
            requestedRoles.add(request.candidateRole());
            for (var entry : requestedLengths.entrySet()) {
                if (selectedBlocks.add(entry.getKey())) {
                    used += entry.getValue();
                }
            }
        }

        String priorityReason;
        if (requestedRoles.isEmpty() && uncoveredRoles.isEmpty()) {
            priorityReason = "ZERO_ELIGIBLE_ROLES";
        } else if (!uncoveredRoles.isEmpty()) {
            priorityReason = "HARD_BUDGET_CORE_CRITICAL_PRIORITY";
        } else {
            priorityReason = "ALL_ELIGIBLE_ROLES_COVERED";
        }
        return new FamilyModelCallPlan(
                family,
                List.copyOf(shardIds),
                List.copyOf(requestedRoles),
                List.copyOf(selectedBlocks),
                List.copyOf(uncoveredRoles),
                List.copyOf(ineligible),
                priorityReason,
                !requestedRoles.isEmpty(),
                maxEvidenceChars,
                used);
    }

    private static Map<String, Integer> evidenceLengths(
            List<ModelAssistEligibilityEvaluator.ModelAssistCandidate> candidates,
            List<String> requiredBlockIds) {
        var candidatesByBlock = new LinkedHashMap<String, ModelAssistEligibilityEvaluator.ModelAssistCandidate>();
        for (var candidate : candidates) {
            candidatesByBlock.putIfAbsent(candidate.blockId(), candidate);
        }
        var result = new LinkedHashMap<String, Integer>();
        for (String blockId : requiredBlockIds) {
            var candidate = candidatesByBlock.get(blockId);
            if (candidate == null) {
                throw new IllegalArgumentException("eligible block is absent from role candidates: " + blockId);
            }
            result.put(blockId, candidate.evidenceText().codePointCount(
                    0, candidate.evidenceText().length()));
        }
        return result;
    }

    record RoleRequest(
            String sourceShardId,
            String family,
            String candidateRole,
            int priority,
            ModelAssistEligibilityEvaluator.EligibilityDecision eligibilityDecision,
            List<ModelAssistEligibilityEvaluator.ModelAssistCandidate> candidates) {
        RoleRequest {
            sourceShardId = requireText(sourceShardId, "sourceShardId");
            family = requireText(family, "family");
            candidateRole = requireText(candidateRole, "candidateRole");
            if (priority < 0) {
                throw new IllegalArgumentException("priority must not be negative");
            }
            Objects.requireNonNull(eligibilityDecision, "eligibilityDecision");
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
        }

        private static String requireText(String value, String field) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(field + " must not be blank");
            }
            return value;
        }
    }
}
