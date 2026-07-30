package com.cqcp.apiserver.reviewengine;

import static org.assertj.core.api.Assertions.assertThat;

import com.cqcp.apiserver.reviewengine.ModelAssistEligibilityEvaluator.ConflictDimension;
import com.cqcp.apiserver.reviewengine.ModelAssistEligibilityEvaluator.EligibilityReason;
import com.cqcp.apiserver.reviewengine.ModelAssistEligibilityEvaluator.ExecutionStrategy;
import com.cqcp.apiserver.reviewengine.ModelAssistEligibilityEvaluator.LocalContextRelation;
import com.cqcp.apiserver.reviewengine.ModelAssistEligibilityEvaluator.ModelAssistCandidate;
import com.cqcp.apiserver.reviewengine.ModelAssistEligibilityEvaluator.ModelAssistMode;
import com.cqcp.apiserver.reviewengine.ModelAssistEligibilityEvaluator.ResolverPolicy;
import com.cqcp.apiserver.reviewengine.ModelAssistEligibilityEvaluator.RoleContext;
import com.cqcp.apiserver.reviewengine.ModelAssistEligibilityEvaluator.SlotPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModelAssistRuntimeSeamTest {

    private final ModelAssistEligibilityEvaluator evaluator =
            new ModelAssistEligibilityEvaluator();
    private final RuntimeEvidencePacketBuilder packetBuilder =
            new RuntimeEvidencePacketBuilder();

    @Test
    void deterministicHighIsAlwaysZeroCallByDefault() {
        var decision = evaluator.evaluate(context(
                EvidenceConfidenceLevel.HIGH,
                List.of(candidate("a", "70", true)),
                ResolverPolicy.DETERMINISTIC_ONLY,
                ModelAssistMode.NONE,
                ExecutionStrategy.DETERMINISTIC,
                true,
                true));

        assertThat(decision.eligible()).isFalse();
        assertThat(decision.reasonCodes())
                .containsExactly(EligibilityReason.DETERMINISTIC_HIGH_ZERO_CALL);
    }

    @Test
    void highRequiresExplicitSemanticGuard() {
        var decision = evaluator.evaluate(context(
                EvidenceConfidenceLevel.HIGH,
                List.of(candidate("a", "条款含义", true)),
                ResolverPolicy.DETERMINISTIC_ONLY,
                ModelAssistMode.SEMANTIC_INTERPRETATION,
                ExecutionStrategy.LLM_SEMANTIC_WITH_GUARD,
                true,
                true));

        assertThat(decision.eligible()).isTrue();
        assertThat(decision.reasonCodes())
                .containsExactly(EligibilityReason.ELIGIBLE_HIGH_SEMANTIC_GUARD);
    }

    @Test
    void unknownOrNoCandidateIsZeroCall() {
        var decision = evaluator.evaluate(context(
                EvidenceConfidenceLevel.UNKNOWN,
                List.of(),
                ResolverPolicy.GEMMA_IF_AMBIGUOUS,
                ModelAssistMode.AMBIGUITY_RESOLUTION,
                ExecutionStrategy.LLM_EXTRACT_THEN_RULE,
                true,
                true));
        assertThat(decision.eligible()).isFalse();
        assertThat(decision.reasonCodes()).containsExactly(EligibilityReason.NO_CANDIDATE);
    }

    @Test
    void invalidBundleFailsBeforeConfidenceRules() {
        var decision = evaluator.evaluate(context(
                EvidenceConfidenceLevel.MEDIUM,
                List.of(candidate("a", "70", true)),
                ResolverPolicy.GEMMA_IF_AMBIGUOUS,
                ModelAssistMode.AMBIGUITY_RESOLUTION,
                ExecutionStrategy.LLM_EXTRACT_THEN_RULE,
                false,
                true));
        assertThat(decision.reasonCodes()).containsExactly(EligibilityReason.BUNDLE_INVALID);
    }

    @Test
    void incompleteBudgetFailsBeforeConfidenceRules() {
        var decision = evaluator.evaluate(context(
                EvidenceConfidenceLevel.MEDIUM,
                List.of(candidate("a", "70", true)),
                ResolverPolicy.GEMMA_IF_AMBIGUOUS,
                ModelAssistMode.AMBIGUITY_RESOLUTION,
                ExecutionStrategy.LLM_EXTRACT_THEN_RULE,
                true,
                false));
        assertThat(decision.reasonCodes()).containsExactly(EligibilityReason.BUDGET_INCOMPLETE);
    }

    @Test
    void nonRequiredSlotCannotEnterModelBudget() {
        var context = new RoleContext(
                ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY,
                "PAYMENT_TERMS",
                "PROGRESS_PAYMENT_RATIO",
                EvidenceConfidenceLevel.MEDIUM,
                List.of(candidate("a", "70", true)),
                new SlotPolicy(false, false),
                ResolverPolicy.GEMMA_IF_AMBIGUOUS,
                ModelAssistMode.AMBIGUITY_RESOLUTION,
                ExecutionStrategy.LLM_EXTRACT_THEN_RULE,
                true,
                true);
        assertThat(evaluator.evaluate(context).reasonCodes())
                .containsExactly(EligibilityReason.REQUIRED_SLOT_UNAVAILABLE);
    }

    @Test
    void mediumWithRequiredSlotResolverAndAnchorIsEligible() {
        var decision = evaluator.evaluate(context(
                EvidenceConfidenceLevel.MEDIUM,
                List.of(candidate("a", "70", true)),
                ResolverPolicy.GEMMA_IF_AMBIGUOUS,
                ModelAssistMode.AMBIGUITY_RESOLUTION,
                ExecutionStrategy.LLM_EXTRACT_THEN_RULE,
                true,
                true));
        assertThat(decision.eligible()).isTrue();
        assertThat(decision.requiredBlockIds()).containsExactly("a");
    }

    @Test
    void mediumWithoutResolverPolicyIsZeroCall() {
        var decision = evaluator.evaluate(context(
                EvidenceConfidenceLevel.MEDIUM,
                List.of(candidate("a", "70", true)),
                ResolverPolicy.DETERMINISTIC_ONLY,
                ModelAssistMode.AMBIGUITY_RESOLUTION,
                ExecutionStrategy.LLM_EXTRACT_THEN_RULE,
                true,
                true));
        assertThat(decision.reasonCodes())
                .containsExactly(EligibilityReason.RESOLVER_POLICY_DISALLOWS_MODEL);
    }

    @Test
    void mediumWithoutReliableAnchorIsZeroCall() {
        var decision = evaluator.evaluate(context(
                EvidenceConfidenceLevel.MEDIUM,
                List.of(candidate("a", "70", false)),
                ResolverPolicy.GEMMA_IF_AMBIGUOUS,
                ModelAssistMode.AMBIGUITY_RESOLUTION,
                ExecutionStrategy.LLM_EXTRACT_THEN_RULE,
                true,
                true));
        assertThat(decision.reasonCodes())
                .containsExactly(EligibilityReason.RELIABLE_ANCHOR_MISSING);
    }

    @Test
    void lowNeedsRoleValueContextAndAnchorMinimum() {
        var decision = evaluator.evaluate(context(
                EvidenceConfidenceLevel.LOW,
                List.of(candidate("a", "70", true)),
                ResolverPolicy.GEMMA_IF_AMBIGUOUS,
                ModelAssistMode.AMBIGUITY_RESOLUTION,
                ExecutionStrategy.LLM_EXTRACT_THEN_RULE,
                true,
                true));
        assertThat(decision.eligible()).isTrue();
        assertThat(decision.reasonCodes())
                .containsExactly(EligibilityReason.ELIGIBLE_LOW_MINIMUM_CANDIDATE);
    }

    @Test
    void lowCandidateWithoutRoleSignalIsRejected() {
        var candidate = new ModelAssistCandidate(
                "70", "PERCENTAGE_POINT", "a", "进度款70%",
                "BLOCK_LEVEL", null, "NORMAL", "BODY",
                LocalContextRelation.SAME_SENTENCE, ConflictDimension.NONE,
                false, true, false);
        var decision = evaluator.evaluate(context(
                EvidenceConfidenceLevel.LOW,
                List.of(candidate),
                ResolverPolicy.GEMMA_IF_AMBIGUOUS,
                ModelAssistMode.AMBIGUITY_RESOLUTION,
                ExecutionStrategy.LLM_EXTRACT_THEN_RULE,
                true,
                true));
        assertThat(decision.reasonCodes())
                .containsExactly(EligibilityReason.LOW_CANDIDATE_THRESHOLD_NOT_MET);
    }

    @Test
    void comparableConflictWithTwoAnchorsIsEligible() {
        var decision = evaluator.evaluate(context(
                EvidenceConfidenceLevel.CONFLICTED,
                List.of(
                        conflictCandidate("a", "70", "NORMAL"),
                        conflictCandidate("b", "75", "NORMAL")),
                ResolverPolicy.GEMMA_IF_AMBIGUOUS,
                ModelAssistMode.AMBIGUITY_RESOLUTION,
                ExecutionStrategy.LLM_EXTRACT_THEN_RULE,
                true,
                true));
        assertThat(decision.eligible()).isTrue();
        assertThat(decision.requiredBlockIds()).containsExactly("a", "b");
    }

    @Test
    void conflictWithOneCandidateFailsClosed() {
        var decision = evaluator.evaluate(context(
                EvidenceConfidenceLevel.CONFLICTED,
                List.of(conflictCandidate("a", "70", "NORMAL")),
                ResolverPolicy.GEMMA_IF_AMBIGUOUS,
                ModelAssistMode.AMBIGUITY_RESOLUTION,
                ExecutionStrategy.LLM_EXTRACT_THEN_RULE,
                true,
                true));
        assertThat(decision.reasonCodes())
                .containsExactly(EligibilityReason.CONFLICT_NOT_LOCALLY_COMPARABLE);
    }

    @Test
    void conflictInDeletedContextFailsClosed() {
        var decision = evaluator.evaluate(context(
                EvidenceConfidenceLevel.CONFLICTED,
                List.of(
                        conflictCandidate("a", "70", "DELETED"),
                        conflictCandidate("b", "75", "NORMAL")),
                ResolverPolicy.GEMMA_IF_AMBIGUOUS,
                ModelAssistMode.AMBIGUITY_RESOLUTION,
                ExecutionStrategy.LLM_EXTRACT_THEN_RULE,
                true,
                true));
        assertThat(decision.reasonCodes())
                .containsExactly(EligibilityReason.CONFLICT_NOT_LOCALLY_COMPARABLE);
    }

    @Test
    void deterministicPacketIsStableAndZeroCall() throws Exception {
        PointEvidence evidence = highEvidence();
        var identity = identity();
        RuntimeEvidencePacket first = packetBuilder.build(
                identity, evidence, RuntimeEvidencePacketBuilder.AssistPolicy.deterministicNoAssist(), 4096);
        RuntimeEvidencePacket second = packetBuilder.build(
                identity, evidence, RuntimeEvidencePacketBuilder.AssistPolicy.deterministicNoAssist(), 4096);

        var mapper = new ObjectMapper().findAndRegisterModules();
        assertThat(mapper.writeValueAsBytes(first)).isEqualTo(mapper.writeValueAsBytes(second));
        assertThat(first.packetId()).isEqualTo(second.packetId());
        assertThat(first.admission().modelCallAllowed()).isFalse();
        assertThat(first.admission().status()).isEqualTo("ZERO_CALL_REQUIRED");
    }

    @Test
    void packetContainsOnlyLocalCandidateEvidenceAndAnchors() throws Exception {
        RuntimeEvidencePacket packet = packetBuilder.build(
                identity(),
                highEvidence(),
                RuntimeEvidencePacketBuilder.AssistPolicy.deterministicNoAssist(),
                4096);
        String serialized = new ObjectMapper().writeValueAsString(packet);

        assertThat(packet.candidateOccurrences()).hasSize(2);
        assertThat(packet.candidateOccurrences())
                .allSatisfy(occurrence -> assertThat(occurrence.sourceAnchor().reliable()).isTrue());
        assertThat(serialized.toLowerCase())
                .doesNotContain("expected", "groundtruth", "humananchor", "verdict", "finding");
        assertThat(serialized).doesNotContain("合同全文");
    }

    @Test
    void packetBudgetTruncationForcesZeroCall() {
        RuntimeEvidencePacket packet = packetBuilder.build(
                identity(),
                highEvidence(),
                new RuntimeEvidencePacketBuilder.AssistPolicy(
                        ResolverPolicy.GEMMA_IF_AMBIGUOUS,
                        ModelAssistMode.AMBIGUITY_RESOLUTION,
                        ExecutionStrategy.LLM_EXTRACT_THEN_RULE),
                1);
        assertThat(packet.budget().complete()).isFalse();
        assertThat(packet.budget().truncated()).isTrue();
        assertThat(packet.admission().modelCallAllowed()).isFalse();
        assertThat(packet.admission().reasonCodes())
                .containsExactly(EligibilityReason.BUDGET_INCOMPLETE);
    }

    @Test
    void familyPlanWithNoEligibleRolesRequiresNoCall() {
        RuntimeEvidencePacket packet = packetBuilder.build(
                identity(),
                highEvidence(),
                RuntimeEvidencePacketBuilder.AssistPolicy.deterministicNoAssist(),
                4096);
        var request = packetBuilder.toRoleRequest("shard-1", packet, 100);
        FamilyModelCallPlan plan =
                new FamilyModelCallPlanner().plan(packet.family(), List.of(request), 4096);

        assertThat(plan.modelCallAllowed()).isFalse();
        assertThat(plan.requestedRoles()).isEmpty();
        assertThat(plan.priorityReason()).isEqualTo("ZERO_ELIGIBLE_ROLES");
        assertThat(plan.ineligibleRoles()).singleElement()
                .satisfies(role -> assertThat(role.reasonCodes())
                        .containsExactly(EligibilityReason.DETERMINISTIC_HIGH_ZERO_CALL));
    }

    @Test
    void familyPlanDeduplicatesBlocksAcrossShards() {
        var eligible = new ModelAssistEligibilityEvaluator.EligibilityDecision(
                true, List.of(EligibilityReason.ELIGIBLE_MEDIUM_AMBIGUITY), List.of("shared"));
        var candidate = candidate("shared", "70", true);
        var requests = List.of(
                new FamilyModelCallPlanner.RoleRequest(
                        "shard-1", "PAYMENT_TERMS", "ROLE_A", 100, eligible, List.of(candidate)),
                new FamilyModelCallPlanner.RoleRequest(
                        "shard-2", "PAYMENT_TERMS", "ROLE_B", 90, eligible, List.of(candidate)));

        FamilyModelCallPlan plan =
                new FamilyModelCallPlanner().plan("PAYMENT_TERMS", requests, 4096);

        assertThat(plan.sourceShardIds()).containsExactly("shard-1", "shard-2");
        assertThat(plan.requestedRoles()).containsExactly("ROLE_A", "ROLE_B");
        assertThat(plan.selectedBlockIds()).containsExactly("shared");
        assertThat(plan.usedEvidenceChars()).isEqualTo("进度款70%".length());
    }

    @Test
    void familyPlanHardBudgetProducesUncoveredRoleWithoutPartialBlocks() {
        var eligibleA = new ModelAssistEligibilityEvaluator.EligibilityDecision(
                true, List.of(EligibilityReason.ELIGIBLE_MEDIUM_AMBIGUITY), List.of("a"));
        var eligibleB = new ModelAssistEligibilityEvaluator.EligibilityDecision(
                true, List.of(EligibilityReason.ELIGIBLE_MEDIUM_AMBIGUITY), List.of("b"));
        var requests = List.of(
                new FamilyModelCallPlanner.RoleRequest(
                        "shard-1", "PAYMENT_TERMS", "CORE_ROLE", 100,
                        eligibleA, List.of(candidate("a", "70", true))),
                new FamilyModelCallPlanner.RoleRequest(
                        "shard-2", "PAYMENT_TERMS", "LOW_ROLE", 10,
                        eligibleB, List.of(candidate("b", "75", true))));

        FamilyModelCallPlan plan = new FamilyModelCallPlanner().plan(
                "PAYMENT_TERMS", requests, "进度款70%".length());

        assertThat(plan.requestedRoles()).containsExactly("CORE_ROLE");
        assertThat(plan.uncoveredRoles()).containsExactly("LOW_ROLE");
        assertThat(plan.selectedBlockIds()).containsExactly("a");
        assertThat(plan.priorityReason()).isEqualTo("HARD_BUDGET_CORE_CRITICAL_PRIORITY");
    }

    private static RoleContext context(
            EvidenceConfidenceLevel confidence,
            List<ModelAssistCandidate> candidates,
            ResolverPolicy resolverPolicy,
            ModelAssistMode mode,
            ExecutionStrategy strategy,
            boolean bundleValid,
            boolean budgetComplete) {
        return new RoleContext(
                ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY,
                "PAYMENT_TERMS",
                "PROGRESS_PAYMENT_RATIO",
                confidence,
                candidates,
                new SlotPolicy(true, true),
                resolverPolicy,
                mode,
                strategy,
                bundleValid,
                budgetComplete);
    }

    private static ModelAssistCandidate candidate(String blockId, String value, boolean reliable) {
        return new ModelAssistCandidate(
                value,
                "PERCENTAGE_POINT",
                blockId,
                "进度款" + value + "%",
                reliable ? "BLOCK_LEVEL" : "TABLE_CELL",
                null,
                "NORMAL",
                "BODY",
                LocalContextRelation.SAME_SENTENCE,
                ConflictDimension.NONE,
                true,
                true,
                false);
    }

    private static ModelAssistCandidate conflictCandidate(
            String blockId,
            String value,
            String contextType) {
        return new ModelAssistCandidate(
                value,
                "PERCENTAGE_POINT",
                blockId,
                "进度款" + value + "%",
                "BLOCK_LEVEL",
                null,
                contextType,
                "BODY",
                LocalContextRelation.SAME_SENTENCE,
                ConflictDimension.VALUE,
                true,
                true,
                false);
    }

    private static PointEvidence highEvidence() {
        return new PointEvidence(
                ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY,
                "PROGRESS_PAYMENT_RATIO",
                "70",
                EvidenceStatus.CONFIRMED,
                "NATIVE_WORD",
                "STRUCTURED",
                "NORMAL",
                "block-a",
                EvidenceConfidenceLevel.HIGH.name(),
                "一致性扫描 READY",
                null,
                null,
                List.of(new EvidenceSlotCoverage(
                        "progressPaymentRatio",
                        true,
                        true,
                        EvidenceSlotCoverageStatus.SATISFIED,
                        null,
                        true)),
                List.of("付款条款"),
                "BODY",
                "BLOCK_LEVEL",
                null,
                List.of(
                        new PointEvidenceOccurrence(
                                "70",
                                "block-a",
                                "A模式按月支付至70%",
                                List.of("付款条款"),
                                "BODY",
                                "HIGH",
                                "BLOCK_LEVEL",
                                null),
                        new PointEvidenceOccurrence(
                                "70",
                                "block-b",
                                "进度款支付至70%",
                                List.of("付款条款"),
                                "BODY",
                                "HIGH",
                                "BLOCK_LEVEL",
                                null)));
    }

    private static RuntimeEvidencePacketBuilder.PacketIdentity identity() {
        return new RuntimeEvidencePacketBuilder.PacketIdentity(
                "task-1", "execution-1", "sample-1", "v20260729.1");
    }
}
