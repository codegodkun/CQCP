package com.cqcp.apiserver.reviewengine;

import static org.assertj.core.api.Assertions.assertThat;

import com.cqcp.apiserver.reviewengine.ModelAssistEligibilityEvaluator.ConflictDimension;
import com.cqcp.apiserver.reviewengine.ModelAssistEligibilityEvaluator.ExecutionStrategy;
import com.cqcp.apiserver.reviewengine.ModelAssistEligibilityEvaluator.LocalContextRelation;
import com.cqcp.apiserver.reviewengine.ModelAssistEligibilityEvaluator.ModelAssistCandidate;
import com.cqcp.apiserver.reviewengine.ModelAssistEligibilityEvaluator.ModelAssistMode;
import com.cqcp.apiserver.reviewengine.ModelAssistEligibilityEvaluator.ResolverPolicy;
import com.cqcp.apiserver.reviewengine.ModelAssistEligibilityEvaluator.RoleContext;
import com.cqcp.apiserver.reviewengine.ModelAssistEligibilityEvaluator.SlotPolicy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrackBAdmissionCorpusRuntimeContractTest {

    private static final String CORPUS_PATH =
            "/track-b-admission-corpus-v2/corpus.json";
    private static final String ELIGIBILITY_SOURCE_SIGNALS_PATH =
            "/track-b-admission-corpus-v2/"
                    + "eligibility-source-signals.json";
    private static final String OUTPUT_CONTRACT =
            "Only assess supplied role/candidate/anchor evidence. "
                    + "Do not produce final business adjudication.";

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final ModelAssistEligibilityEvaluator evaluator =
            new ModelAssistEligibilityEvaluator();
    private final RuntimeEvidencePacketBuilder packetBuilder =
            new RuntimeEvidencePacketBuilder();

    @Test
    void admissionCorpusDeserializesIntoRuntimePacketAndRecomputesEveryAdmission() throws Exception {
        JsonNode corpus = mapper.readTree(resourceBytes(CORPUS_PATH));
        String sourceSignalsText = new String(
                resourceBytes(ELIGIBILITY_SOURCE_SIGNALS_PATH), StandardCharsets.UTF_8);
        JsonNode sourceSignals = mapper.readTree(sourceSignalsText);
        assertThat(corpus.path("groundTruthIncluded").asBoolean()).isFalse();
        assertThat(corpus.path("packetCount").asInt()).isEqualTo(18);
        assertThat(corpus.path("eligiblePacketCount").asInt()).isEqualTo(15);
        assertThat(corpus.path("mediumEligibleCount").asInt()).isEqualTo(8);
        assertThat(corpus.path("conflictedEligibleCount").asInt()).isEqualTo(7);
        assertThat(corpus.path("zeroCallControlCount").asInt()).isEqualTo(3);
        assertThat(sourceSignals.path("schemaVersion").asText())
                .isEqualTo("task-eval-002-track-b-eligibility-source-signals-v1");
        assertThat(sourceSignals.path("source").asText())
                .isEqualTo("INDEPENDENT_PRE_ADMISSION_RUNTIME_SIGNALS");
        assertThat(sourceSignals.path("admissionFieldsIncluded").asBoolean()).isFalse();
        assertThat(sourceSignals.path("inputCount").asInt()).isEqualTo(18);
        assertThat(sourceSignalsText)
                .doesNotContain(
                        "admissionReason",
                        "reasonCodes",
                        "modelCallAllowed",
                        "proposedExpected",
                        "humanDecision");
        var sourceBySampleId = new LinkedHashMap<String, JsonNode>();
        for (JsonNode source : sourceSignals.path("inputs")) {
            assertThat(sourceBySampleId.put(source.path("sampleId").asText(), source))
                    .isNull();
        }
        assertThat(sourceBySampleId).hasSize(18);

        var packets = new ArrayList<RuntimeEvidencePacket>();
        var recomputedDecisions =
                new ArrayList<ModelAssistEligibilityEvaluator.EligibilityDecision>();
        for (JsonNode packetNode : corpus.path("packets")) {
            RuntimeEvidencePacket packet =
                    mapper.treeToValue(packetNode, RuntimeEvidencePacket.class);
            packets.add(packet);
            JsonNode source = sourceBySampleId.remove(packet.sampleId());
            assertThat(source).as(packet.sampleId()).isNotNull();
            assertThat(source.path("packetId").asText()).isEqualTo(packet.packetId());
            assertThat(packet.schemaVersion())
                    .isEqualTo("task036-runtime-evidence-packet-v1");
            assertThat(packet.requiredOutput().contract())
                    .isEqualTo("ROLE_CANDIDATE_ANCHOR_ABSTENTION_V1");
            assertThat(packet.requiredOutput().fields())
                    .containsExactly(
                            "suggestedRole",
                            "selectedOccurrenceIds",
                            "selectedAnchorBlockIds",
                            "abstain",
                            "abstentionReason");
            assertThat(packet.requiredOutput().instruction()).isEqualTo(OUTPUT_CONTRACT);
            assertThat(mapper.writeValueAsString(packet).toLowerCase())
                    .doesNotContain(
                            "groundtruth",
                            "humananchor",
                            "proposedexpected",
                            "\"finding\"",
                            "\"verdict\"");
            var diagnostic = packet.coverageSignals().getFirst().diagnosticCode();
            var confidence =
                    EvidenceConfidenceLevel.valueOf(source.path("confidenceLevel").asText());
            switch (confidence) {
                case CONFLICTED ->
                    assertThat(diagnostic).isEqualTo("SYS_ROLE_CONFLICT");
                case UNKNOWN ->
                    assertThat(diagnostic).isEqualTo("SYS_INDEX_INCOMPLETE");
                case HIGH ->
                    assertThat(diagnostic).isNull();
                case MEDIUM, LOW ->
                    assertThat(diagnostic).isEqualTo("SYS_EVIDENCE_MEDIUM_CONFIDENCE");
            }

            var recomputed = evaluator.evaluate(runtimeContext(packet, source));
            recomputedDecisions.add(recomputed);
            assertThat(recomputed.eligible())
                    .as(packet.sampleId())
                    .isEqualTo(packet.admission().modelCallAllowed());
            assertThat(recomputed.reasonCodes())
                    .as(packet.sampleId())
                    .containsExactlyElementsOf(packet.admission().reasonCodes());

            if (packet.admission().modelCallAllowed()) {
                assertThat(packet.candidateOccurrences()).isNotEmpty();
                assertThat(packet.candidateOccurrences())
                        .allSatisfy(candidate ->
                                assertThat(candidate.sourceAnchor().reliable()).isTrue());
            }
        }

        assertThat(sourceBySampleId).isEmpty();
        assertThat(packets).hasSize(18);
        assertThat(packets)
                .filteredOn(packet -> packet.admission().modelCallAllowed())
                .hasSize(15);
        assertThat(packets)
                .filteredOn(packet -> packet.admission().reasonCodes().contains(
                        ModelAssistEligibilityEvaluator.EligibilityReason
                                .DETERMINISTIC_HIGH_ZERO_CALL))
                .singleElement()
                .satisfies(packet -> assertThat(packet.admission().modelCallAllowed()).isFalse());
        assertThat(recomputedDecisions).filteredOn(
                        ModelAssistEligibilityEvaluator.EligibilityDecision::eligible)
                .hasSize(15);

        var packetsByRuntimeCall = new LinkedHashMap<String, List<RuntimeEvidencePacket>>();
        for (RuntimeEvidencePacket packet : packets) {
            String callIdentity =
                    packet.taskId() + "\u0000" + packet.executionId() + "\u0000" + packet.family();
            packetsByRuntimeCall
                    .computeIfAbsent(callIdentity, ignored -> new ArrayList<>())
                    .add(packet);
        }
        assertThat(packetsByRuntimeCall).hasSize(8);

        var callsPerEligibleTask = new LinkedHashMap<String, Integer>();
        int eligibleCallCount = 0;
        int zeroCallControlCount = 0;
        for (List<RuntimeEvidencePacket> callPackets : packetsByRuntimeCall.values()) {
            RuntimeEvidencePacket first = callPackets.getFirst();
            assertThat(callPackets)
                    .allSatisfy(packet -> {
                        assertThat(packet.taskId()).isEqualTo(first.taskId());
                        assertThat(packet.executionId()).isEqualTo(first.executionId());
                        assertThat(packet.family()).isEqualTo(first.family());
                    });
            var requests = callPackets.stream()
                    .map(packet -> packetBuilder.toRoleRequest(
                            "admission-corpus-" + packet.sampleId(), packet, 100))
                    .toList();
            var plan = new FamilyModelCallPlanner().plan(first.family(), requests, 4096);
            boolean modelCallExpected = callPackets.stream()
                    .allMatch(packet -> packet.admission().modelCallAllowed());
            assertThat(plan.modelCallAllowed()).isEqualTo(modelCallExpected);
            if (modelCallExpected) {
                eligibleCallCount += 1;
                String taskIdentity = first.taskId() + "\u0000" + first.executionId();
                callsPerEligibleTask.merge(taskIdentity, 1, Integer::sum);
                assertThat(plan.requestedRoles())
                        .containsExactlyInAnyOrderElementsOf(callPackets.stream()
                                .map(RuntimeEvidencePacket::candidateRole)
                                .toList());
                assertThat(plan.requestedRoles()).doesNotHaveDuplicates();
                assertThat(plan.selectedBlockIds()).isNotEmpty();
            } else {
                zeroCallControlCount += callPackets.size();
                assertThat(plan.requestedRoles()).isEmpty();
            }
        }
        assertThat(eligibleCallCount).isEqualTo(6);
        assertThat(zeroCallControlCount).isEqualTo(3);
        assertThat(callsPerEligibleTask).hasSize(2);
        assertThat(callsPerEligibleTask.values()).containsOnly(3);
        assertThat(callsPerEligibleTask.values()).allMatch(count -> count <= 4);
    }

    private RoleContext runtimeContext(RuntimeEvidencePacket packet, JsonNode source) {
        var signalByOccurrenceId = new LinkedHashMap<String, JsonNode>();
        for (JsonNode candidateSignal : source.path("candidateSignals")) {
            assertThat(signalByOccurrenceId.put(
                            candidateSignal.path("occurrenceId").asText(), candidateSignal))
                    .isNull();
        }
        List<ModelAssistCandidate> candidates = packet.candidateOccurrences().stream()
                .map(candidate -> {
                    JsonNode signal = signalByOccurrenceId.remove(candidate.occurrenceId());
                    assertThat(signal).as(candidate.occurrenceId()).isNotNull();
                    assertThat(signal.path("valueType").asText())
                            .isEqualTo(valueType(packet.reviewPointCode()));
                    return new ModelAssistCandidate(
                            candidate.candidateValue(),
                            signal.path("valueType").asText(),
                            candidate.sourceAnchor().blockId(),
                            candidate.evidenceText(),
                            candidate.sourceAnchor().locationLevel(),
                            candidate.sourceAnchor().previewElementRef(),
                            candidate.sourceAnchor().contextType(),
                            candidate.sourceAnchor().regionType(),
                            LocalContextRelation.valueOf(
                                    signal.path("localContextRelation").asText()),
                            ConflictDimension.valueOf(
                                    signal.path("conflictDimension").asText()),
                            signal.path("roleLabelSignal").asBoolean(),
                            signal.path("valueFormatSignal").asBoolean(),
                            signal.path("strongExcluded").asBoolean());
                })
                .toList();
        assertThat(signalByOccurrenceId).isEmpty();
        JsonNode slotPolicy = source.path("slotPolicy");
        assertThat(packet.coverageSignals().stream().anyMatch(
                        RuntimeEvidencePacket.PacketCoverageSignal::required))
                .isEqualTo(slotPolicy.path("required").asBoolean());
        assertThat(packet.coverageSignals().stream().anyMatch(
                        RuntimeEvidencePacket.PacketCoverageSignal::critical))
                .isEqualTo(slotPolicy.path("critical").asBoolean());
        boolean packetBundleValid = !packet.coverageSignals().isEmpty()
                && packet.candidateOccurrences().stream()
                        .allMatch(candidate -> candidate.sourceAnchor().reliable());
        assertThat(packetBundleValid)
                .isEqualTo(source.path("bundleValid").asBoolean());
        assertThat(packet.budget().complete())
                .isEqualTo(source.path("budgetComplete").asBoolean());
        return new RoleContext(
                packet.reviewPointCode(),
                packet.family(),
                packet.candidateRole(),
                EvidenceConfidenceLevel.valueOf(source.path("confidenceLevel").asText()),
                candidates,
                new SlotPolicy(
                        slotPolicy.path("required").asBoolean(),
                        slotPolicy.path("critical").asBoolean()),
                ResolverPolicy.valueOf(source.path("resolverPolicy").asText()),
                ModelAssistMode.valueOf(source.path("modelAssistMode").asText()),
                ExecutionStrategy.valueOf(source.path("executionStrategy").asText()),
                source.path("bundleValid").asBoolean(),
                source.path("budgetComplete").asBoolean());
    }

    private static String valueType(ReviewPointCode code) {
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

    private static byte[] resourceBytes(String path) throws Exception {
        try (var input = TrackBAdmissionCorpusRuntimeContractTest.class
                .getResourceAsStream(path)) {
            assertThat(input).as(path).isNotNull();
            return input.readAllBytes();
        }
    }

}
