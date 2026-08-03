package com.cqcp.apiserver.reviewengine;

import static org.assertj.core.api.Assertions.assertThat;

import com.cqcp.apiserver.reviewengine.ModelAssistEligibilityEvaluator.ExecutionStrategy;
import com.cqcp.apiserver.reviewengine.ModelAssistEligibilityEvaluator.ModelAssistMode;
import com.cqcp.apiserver.reviewengine.ModelAssistEligibilityEvaluator.ResolverPolicy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrackBProviderRecoveryRuntimeContractTest {

    private static final String SOURCE_PATH =
            "/track-b-recovery-v1/source-signals.json";
    private static final String PROPOSED_PATH =
            "/track-b-recovery-v1/proposed-decisions.json";
    private static final String CORPUS_PATH =
            "outputs/task-eval-006/track-b-recovery-v1/corpus.json";
    private static final String CORPUS_SHA256 =
            "3a988a9ab09008645dd812b47c1ad3f4c6a9aac699a590a66bd6a17cf1010554";

    private final ObjectMapper mapper =
            new ObjectMapper().findAndRegisterModules();
    private final RuntimeEvidencePacketBuilder packetBuilder =
            new RuntimeEvidencePacketBuilder();

    @Test
    void recoveryCorpusBuildsExactlyThroughTheRuntimePacketSeam()
            throws Exception {
        byte[] sourceBytes = resourceBytes(SOURCE_PATH);
        byte[] proposedBytes = resourceBytes(PROPOSED_PATH);
        byte[] corpusBytes = Files.readAllBytes(repositoryRoot().resolve(CORPUS_PATH));
        String sourceText = new String(sourceBytes, StandardCharsets.UTF_8);
        JsonNode source = mapper.readTree(sourceBytes);
        JsonNode proposed = mapper.readTree(proposedBytes);
        JsonNode corpus = mapper.readTree(corpusBytes);

        assertThat(sha256(corpusBytes)).isEqualTo(CORPUS_SHA256);
        assertThat(source.path("schemaVersion").asText())
                .isEqualTo("task-eval-006-track-b-recovery-source-signals-v1");
        assertThat(source.path("groundTruthIncluded").asBoolean()).isFalse();
        assertThat(source.path("admissionFieldsIncluded").asBoolean()).isFalse();
        assertThat(source.path("caseCount").asInt()).isEqualTo(12);
        assertThat(sourceText)
                .doesNotContain(
                        "\"admission\":",
                        "\"packetId\":",
                        "\"reasonCodes\":",
                        "\"modelCallAllowed\":",
                        "\"proposedExpected\":",
                        "\"humanDecision\":",
                        "\"finding\":",
                        "\"verdict\":");
        assertThat(corpus.path("packets")).hasSize(12);
        assertThat(proposed.path("entries")).hasSize(12);

        var packets = new ArrayList<RuntimeEvidencePacket>();
        for (int index = 0; index < source.path("cases").size(); index += 1) {
            JsonNode item = source.path("cases").get(index);
            JsonNode identity = source.path("identities")
                    .path(item.path("identityClass").asText());
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
            JsonNode actualPacket = mapper.valueToTree(packet);
            assertThat(actualPacket)
                    .as("exact recovery packet %s", item.path("caseId").asText())
                    .isEqualTo(corpus.path("packets").get(index));
            assertThat(proposed.path("entries").get(index).path("caseId").asText())
                    .isEqualTo(packet.sampleId());
            JsonNode proposedExpected = proposed.path("entries")
                    .get(index)
                    .path("proposedExpected");
            assertThat(stringList(proposedExpected.path("selectedOccurrenceIds")))
                    .isSubsetOf(packet.candidateOccurrences().stream()
                            .map(RuntimeEvidencePacket.PacketCandidateOccurrence::occurrenceId)
                            .toList());
            assertThat(stringList(proposedExpected.path("selectedAnchorBlockIds")))
                    .isSubsetOf(packet.candidateOccurrences().stream()
                            .map(candidate -> candidate.sourceAnchor().blockId())
                            .toList());
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
        try (var input = TrackBProviderRecoveryRuntimeContractTest.class
                .getResourceAsStream(path)) {
            assertThat(input).as(path).isNotNull();
            return input.readAllBytes();
        }
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("AGENTS.md"))
                    && Files.isDirectory(current.resolve("outputs"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("CQCP repository root not found");
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
