package com.cqcp.apiserver.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BlindEvaluationProjectionGeneratorTest {

    private static final List<String> GENERATED_FILES = List.of(
            "blind-inputs/CQCP-MVP-DOCX-001.track-a.blind.json",
            "blind-inputs/CQCP-MVP-DOCX-002.track-a.blind.json",
            "blind-inputs/CQCP-MVP-DOCX-003.track-a.blind.json",
            "freeze-manifest.json",
            "track-b-admission.json");

    private final ObjectMapper mapper = JsonMapper.builder().build();

    @Test
    void identicalInputs_generateByteIdenticalBlindPackages(@TempDir Path tempDir) throws Exception {
        var repoRoot = Path.of("../..").toAbsolutePath().normalize();
        var trustedEvidenceRoot = resourcePath("/blind-evaluation-source");
        var output = tempDir.resolve("output");
        var generator = new BlindEvaluationProjectionGenerator();

        generator.generate(repoRoot, trustedEvidenceRoot, output);
        var firstRun = new LinkedHashMap<String, byte[]>();
        for (var relativePath : GENERATED_FILES) {
            var generatedBytes = Files.readAllBytes(output.resolve(relativePath));
            assertThat(new String(generatedBytes, StandardCharsets.UTF_8))
                    .as(relativePath + " uses canonical LF line endings")
                    .doesNotContain("\r");
            firstRun.put(relativePath, generatedBytes);
        }

        generator.generate(repoRoot, trustedEvidenceRoot, output);

        for (var relativePath : GENERATED_FILES) {
            assertThat(Files.readAllBytes(output.resolve(relativePath)))
                    .as(relativePath)
                    .isEqualTo(firstRun.get(relativePath));
        }

        var manifest = mapper.readTree(output.resolve("freeze-manifest.json").toFile());
        assertThat(manifest.has("generatedAt")).isFalse();
        assertThat(manifest.path("externalEgressAuthorized").asBoolean()).isFalse();
        assertThat(manifest.path("sampleCount").asInt()).isEqualTo(3);
        assertThat(Files.readAllBytes(output.resolve("freeze-manifest.json")))
                .as("checked-in immutable freeze manifest")
                .isEqualTo(Files.readAllBytes(
                        resourcePath("/blind-evaluation-expected/freeze-manifest.json")));
        for (var sampleId : List.of(
                "CQCP-MVP-DOCX-001",
                "CQCP-MVP-DOCX-002",
                "CQCP-MVP-DOCX-003")) {
            var relativePath = Path.of(
                    "blind-inputs", sampleId + ".track-a.blind.json");
            assertThat(Files.readAllBytes(output.resolve(relativePath)))
                    .as("checked-in immutable blind input " + sampleId)
                    .isEqualTo(Files.readAllBytes(
                            resourcePath("/blind-evaluation-expected").resolve(relativePath)));
        }

        var trackB = mapper.readTree(output.resolve("track-b-admission.json").toFile());
        assertThat(trackB.path("status").asText())
                .isEqualTo("PACKETS_READY_ZERO_ELIGIBLE_CALLS_CODEX_PENDING");
        assertThat(trackB.path("modelCallsAllowed").asBoolean()).isFalse();
        assertThat(trackB.path("packetCount").asInt()).isEqualTo(27);
        assertThat(trackB.path("candidateOccurrenceCount").asInt()).isEqualTo(57);
        assertThat(trackB.path("admissionConclusion").asText())
                .isEqualTo("CURRENT_R7_ZERO_ELIGIBLE_ROLES_DOES_NOT_ADMIT_PROVIDER");
    }

    private static Path resourcePath(String path) {
        try {
            return Path.of(Objects.requireNonNull(
                    BlindEvaluationProjectionGeneratorTest.class.getResource(path)).toURI());
        } catch (Exception error) {
            throw new IllegalStateException("Unable to resolve test resource " + path, error);
        }
    }
}
