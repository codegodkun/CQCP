package com.cqcp.apiserver.reviewengine;

import static org.assertj.core.api.Assertions.assertThat;

import com.cqcp.apiserver.reviewengine.ModelAssistEligibilityEvaluator.ExecutionStrategy;
import com.cqcp.apiserver.reviewengine.ModelAssistEligibilityEvaluator.ModelAssistMode;
import com.cqcp.apiserver.reviewengine.ModelAssistEligibilityEvaluator.ResolverPolicy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrackBHoldoutRuntimeContractTest {

    private static final String SOURCE_PATH =
            "/track-b-holdout-v1/source-signals.json";

    private final ObjectMapper mapper =
            new ObjectMapper().findAndRegisterModules();
    private final RuntimeEvidencePacketBuilder packetBuilder =
            new RuntimeEvidencePacketBuilder();

    @Test
    void newHoldoutBuildsThroughTheRuntimePacketSeam() throws Exception {
        String sourceText = new String(
                resourceBytes(SOURCE_PATH), StandardCharsets.UTF_8);
        JsonNode source = mapper.readTree(sourceText);

        assertThat(source.path("schemaVersion").asText())
                .isEqualTo(
                        "task-eval-002-track-b-holdout-source-signals-v1");
        assertThat(source.path("containsProductionContractText").asBoolean())
                .isFalse();
        assertThat(source.path("groundTruthIncluded").asBoolean())
                .isFalse();
        assertThat(source.path("admissionFieldsIncluded").asBoolean())
                .isFalse();
        assertThat(source.path("caseCount").asInt()).isEqualTo(12);
        assertThat(sourceText)
                .doesNotContain(
                        "\"admission\":",
                        "\"packetId\":",
                        "\"reasonCodes\":",
                        "\"requiredBlockIds\":",
                        "\"modelCallAllowed\":",
                        "\"proposedExpected\":",
                        "\"humanDecision\":",
                        "\"finding\":",
                        "\"verdict\":");

        var packets = new ArrayList<RuntimeEvidencePacket>();
        for (JsonNode item : source.path("cases")) {
            String identityClass = item.path("identityClass").asText();
            JsonNode identity = source.path("identities").path(identityClass);
            RuntimeEvidencePacket packet = packetBuilder.build(
                    new RuntimeEvidencePacketBuilder.PacketIdentity(
                            identity.path("taskId").asText(),
                            identity.path("executionId").asText(),
                            item.path("caseId").asText(),
                            source.path("ruleSetVersion").asText()),
                    evidence(item),
                    assistPolicy(item.path("assistPolicy")),
                    item.path("maxEvidenceChars").asInt());
            RuntimeEvidencePacket restored = mapper.readValue(
                    mapper.writeValueAsBytes(packet),
                    RuntimeEvidencePacket.class);
            assertThat(restored).isEqualTo(packet);
            assertThat(packet.taskId())
                    .startsWith("task-eval-002-track-b-holdout-")
                    .doesNotContain("admission");
            assertThat(packet.executionId())
                    .startsWith("execution-track-b-holdout-");
            assertThat(packet.packetId()).matches("^EP-[a-f0-9]{64}$");
            assertThat(mapper.writeValueAsString(packet).toLowerCase())
                    .doesNotContain(
                            "proposedexpected",
                            "humandecision",
                            "\"expected\"",
                            "\"actual\"",
                            "\"finding\"",
                            "\"verdict\"");
            packets.add(packet);
        }

        assertThat(packets).hasSize(12);
        assertThat(packets.stream().map(RuntimeEvidencePacket::packetId))
                .doesNotHaveDuplicates();
        assertThat(packets)
                .filteredOn(packet -> packet.admission().modelCallAllowed())
                .hasSize(9);
        assertThat(packets)
                .filteredOn(packet -> packet.admission().reasonCodes()
                        .contains(ModelAssistEligibilityEvaluator.EligibilityReason
                                .ELIGIBLE_MEDIUM_AMBIGUITY))
                .hasSize(5);
        assertThat(packets)
                .filteredOn(packet -> packet.admission().reasonCodes()
                        .contains(ModelAssistEligibilityEvaluator.EligibilityReason
                                .ELIGIBLE_CONFLICT_LOCAL_CONTEXT))
                .hasSize(4);
        assertThat(packets)
                .filteredOn(packet -> !packet.admission().modelCallAllowed())
                .extracting(packet -> packet.admission().reasonCodes().getFirst())
                .containsExactlyInAnyOrder(
                        ModelAssistEligibilityEvaluator.EligibilityReason
                                .DETERMINISTIC_HIGH_ZERO_CALL,
                        ModelAssistEligibilityEvaluator.EligibilityReason.BUNDLE_INVALID,
                        ModelAssistEligibilityEvaluator.EligibilityReason
                                .RELIABLE_ANCHOR_MISSING);

        RuntimeEvidencePacket mixed = packets.stream()
                .filter(packet -> packet.sampleId().equals("TBH-CON-003"))
                .findFirst()
                .orElseThrow();
        assertThat(mixed.admission().requiredBlockIds())
                .containsExactly("tbh-con-003-b01", "tbh-con-003-b02");
        assertThat(mixed.candidateOccurrences())
                .extracting(candidate -> candidate.sourceAnchor().blockId())
                .contains("tbh-con-003-b03");

        assertFamilyPlans(packets);
    }

    private void assertFamilyPlans(List<RuntimeEvidencePacket> packets) {
        var byRuntimeCall =
                new LinkedHashMap<String, List<RuntimeEvidencePacket>>();
        for (RuntimeEvidencePacket packet : packets) {
            String key = packet.taskId()
                    + "\u0000"
                    + packet.executionId()
                    + "\u0000"
                    + packet.family();
            byRuntimeCall
                    .computeIfAbsent(key, ignored -> new ArrayList<>())
                    .add(packet);
        }
        int eligibleCalls = 0;
        int zeroCallPackets = 0;
        for (List<RuntimeEvidencePacket> callPackets : byRuntimeCall.values()) {
            RuntimeEvidencePacket first = callPackets.getFirst();
            var requests = callPackets.stream()
                    .map(packet -> packetBuilder.toRoleRequest(
                            "holdout-" + packet.sampleId(), packet, 100))
                    .toList();
            var plan = new FamilyModelCallPlanner()
                    .plan(first.family(), requests, 4096);
            boolean shouldCall = callPackets.stream()
                    .allMatch(packet -> packet.admission().modelCallAllowed());
            assertThat(plan.modelCallAllowed()).isEqualTo(shouldCall);
            if (shouldCall) {
                eligibleCalls += 1;
                assertThat(plan.requestedRoles()).isNotEmpty();
                assertThat(plan.selectedBlockIds()).isNotEmpty();
            } else {
                zeroCallPackets += callPackets.size();
                assertThat(plan.requestedRoles()).isEmpty();
                assertThat(plan.selectedBlockIds()).isEmpty();
            }
        }
        assertThat(eligibleCalls).isEqualTo(6);
        assertThat(zeroCallPackets).isEqualTo(3);
    }

    private PointEvidence evidence(JsonNode item) {
        var occurrences = new ArrayList<PointEvidenceOccurrence>();
        for (JsonNode occurrence : item.path("occurrences")) {
            occurrences.add(new PointEvidenceOccurrence(
                    occurrence.path("candidateValue").asText(),
                    occurrence.path("blockId").asText(),
                    occurrence.path("evidenceText").asText(),
                    stringList(occurrence.path("sectionPath")),
                    occurrence.path("regionType").asText(),
                    occurrence.path("contextType").asText(),
                    occurrence.path("confidence").asText(),
                    occurrence.path("locationLevel").asText(),
                    nullableText(occurrence.path("previewElementRef"))));
        }
        JsonNode first = item.path("occurrences").get(0);
        JsonNode slot = item.path("slotCoverage");
        return new PointEvidence(
                ReviewPointCode.valueOf(item.path("reviewPointCode").asText()),
                item.path("candidateRole").asText(),
                first.path("candidateValue").asText(),
                EvidenceStatus.valueOf(item.path("evidenceStatus").asText()),
                "NATIVE_WORD",
                "STRUCTURED",
                first.path("contextType").asText(),
                first.path("blockId").asText(),
                item.path("confidence").asText(),
                first.path("evidenceText").asText(),
                nullableText(slot.path("diagnosticCode")),
                null,
                List.of(new EvidenceSlotCoverage(
                        slot.path("slotKey").asText(),
                        slot.path("required").asBoolean(),
                        slot.path("critical").asBoolean(),
                        EvidenceSlotCoverageStatus.valueOf(
                                slot.path("coverageStatus").asText()),
                        nullableText(slot.path("diagnosticCode")),
                        slot.path("reliableAnchor").asBoolean())),
                stringList(first.path("sectionPath")),
                first.path("regionType").asText(),
                first.path("locationLevel").asText(),
                nullableText(first.path("previewElementRef")),
                occurrences);
    }

    private static RuntimeEvidencePacketBuilder.AssistPolicy assistPolicy(
            JsonNode policy) {
        return new RuntimeEvidencePacketBuilder.AssistPolicy(
                ResolverPolicy.valueOf(policy.path("resolverPolicy").asText()),
                ModelAssistMode.valueOf(policy.path("modelAssistMode").asText()),
                ExecutionStrategy.valueOf(
                        policy.path("executionStrategy").asText()));
    }

    private static List<String> stringList(JsonNode node) {
        var values = new ArrayList<String>();
        node.forEach(value -> values.add(value.asText()));
        return List.copyOf(values);
    }

    private static String nullableText(JsonNode node) {
        return node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    private static byte[] resourceBytes(String path) throws Exception {
        try (var input = TrackBHoldoutRuntimeContractTest.class
                .getResourceAsStream(path)) {
            assertThat(input).as(path).isNotNull();
            return input.readAllBytes();
        }
    }
}
