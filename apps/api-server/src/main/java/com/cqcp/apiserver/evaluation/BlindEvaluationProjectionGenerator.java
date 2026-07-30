package com.cqcp.apiserver.evaluation;

import com.cqcp.apiserver.reviewengine.RuntimeArtifactVersions;
import com.cqcp.apiserver.wordparser.DocxWordParserSpike;
import com.cqcp.apiserver.wordparser.WordParserSpikeDocument;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Generates deterministic Track-A blind packages for TASK-EVAL-002.
 *
 * <p>The generator reads source fixtures only in the trusted preparation process.
 * Blind packages contain submitted structured fields, review-point definitions,
 * and an opaque-location document projection. They never contain CQCP actual
 * output, expected/ground-truth labels, parser block IDs, or human anchor metadata.
 */
public final class BlindEvaluationProjectionGenerator {

    private static final List<String> SAMPLE_IDS = List.of(
            "CQCP-MVP-DOCX-001",
            "CQCP-MVP-DOCX-002",
            "CQCP-MVP-DOCX-003");
    private static final Set<String> ALLOWED_POINT_FIELDS = Set.of(
            "reviewPointCode",
            "name",
            "family",
            "candidateRole",
            "requiredStructuredFields",
            "optionalStructuredFields",
            "applicabilityPolicy");
    private static final Pattern FORBIDDEN_RUNTIME_ID =
            Pattern.compile("(?i)(block-[0-9]+|table:[^\\s\"']+)");
    private static final List<String> FORBIDDEN_KEY_FRAGMENTS = List.of(
            "expected",
            "actual",
            "groundtruth",
            "humananchor",
            "blockid",
            "previewelementref",
            "comparisonresult",
            "pointstatus");

    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private final DocxWordParserSpike parser = new DocxWordParserSpike();

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Expected arguments: <repoRoot> <outputRoot>");
        }
        new BlindEvaluationProjectionGenerator().generate(
                Path.of(args[0]).toAbsolutePath().normalize(),
                Path.of(args[1]).toAbsolutePath().normalize());
    }

    void generate(Path repoRoot, Path outputRoot) throws Exception {
        generate(repoRoot, repoRoot.resolve("outputs"), outputRoot);
    }

    void generate(Path repoRoot, Path trustedEvidenceRoot, Path outputRoot) throws Exception {
        var fixturesRoot = repoRoot.resolve("packages/test-fixtures");
        var pointDefinitionPath = repoRoot.resolve(
                "packages/review-assets/review-point-definitions/review-points-v20260705.1.json");
        var pointDefinitionHash = sha256(pointDefinitionPath);
        var reviewPoints = blindReviewPoints(mapper.readTree(pointDefinitionPath.toFile()));

        var blindInputRoot = outputRoot.resolve("blind-inputs");
        Files.createDirectories(blindInputRoot);
        var samples = mapper.createArrayNode();

        for (var sampleId : SAMPLE_IDS) {
            var expectedFixturePath = fixturesRoot.resolve("expected/" + sampleId + ".json");
            var trustedFixture = mapper.readTree(expectedFixturePath.toFile());
            var sourceDocx = fixturesRoot.resolve(requiredText(trustedFixture, "sourceDocx")).normalize();
            requireUnderRoot(sourceDocx, fixturesRoot);
            var groundTruthPath = fixturesRoot.resolve("human-anchors/" + sampleId + ".json");
            var actualPath = trustedEvidenceRoot.resolve(
                    "task-034-mvp-e2e-acceptance/sample-results/" + sampleId + ".json");

            var blind = mapper.createObjectNode();
            blind.put("schemaVersion", "task-eval-002-track-a-v1");
            blind.put("track", "TRACK_A_FULL_DOCUMENT_SEMANTIC_OPINION");
            blind.put("sampleId", sampleId);
            blind.put("sourceDocumentSha256", sha256(sourceDocx));
            blind.put("parserProjectionVersion", RuntimeArtifactVersions.PARSER_VERSION);
            blind.set(
                    "submittedStructuredFields",
                    trustedFixture.path("goldenExpected").path("structuredFields").deepCopy());
            blind.set("reviewPoints", reviewPoints.deepCopy());
            blind.set("documentProjection", projectDocument(parser.parse(sourceDocx)));
            blind.set("requiredOutput", outputContract());
            assertBlind(blind);

            var blindPath = blindInputRoot.resolve(sampleId + ".track-a.blind.json");
            writeJson(blindPath, blind);

            var sample = mapper.createObjectNode();
            sample.put("sampleId", sampleId);
            sample.put("sourceDocumentSha256", sha256(sourceDocx));
            sample.put(
                    "blindInputPath",
                    "outputs/task-eval-002/blind-inputs/"
                            + sampleId
                            + ".track-a.blind.json");
            sample.put("blindInputSha256", sha256(blindPath));
            sample.put("groundTruthPackageSha256", sha256(groundTruthPath));
            sample.put("cqcpActualPackageSha256", sha256(actualPath));
            samples.add(sample);
        }

        var manifest = mapper.createObjectNode();
        manifest.put("schemaVersion", "task-eval-002-freeze-v1");
        manifest.put("generator", BlindEvaluationProjectionGenerator.class.getName());
        manifest.put("reviewPointDefinitionSha256", pointDefinitionHash);
        manifest.put("sampleCount", SAMPLE_IDS.size());
        manifest.set("samples", samples);
        manifest.put("externalEgressAuthorized", false);
        manifest.put(
                "externalEgressGate",
                "No authorization artifact is present; DeepSeek runner must fail before network access.");
        writeJson(outputRoot.resolve("freeze-manifest.json"), manifest);

        writeTrackBAdmission(trustedEvidenceRoot, outputRoot);
    }

    private void writeTrackBAdmission(Path trustedEvidenceRoot, Path outputRoot) throws IOException {
        var trackB = mapper.createObjectNode();
        trackB.put("schemaVersion", "task-eval-002-track-b-admission-v1");
        trackB.put("modelCallsAllowed", false);
        Path packetManifestPath = trustedEvidenceRoot.resolve(
                "task-eval-002/track-b-inputs-v1/manifest.json");
        if (Files.isRegularFile(packetManifestPath)) {
            JsonNode packetManifest = mapper.readTree(packetManifestPath.toFile());
            boolean valid = "task-eval-002-track-b-manifest-v1"
                            .equals(packetManifest.path("schemaVersion").asText())
                    && "PACKETS_READY_ZERO_ELIGIBLE_CALLS"
                            .equals(packetManifest.path("status").asText())
                    && !packetManifest.path("modelCallsAllowed").asBoolean(true)
                    && packetManifest.path("packetCount").asInt() == 27
                    && packetManifest.path("candidateOccurrenceCount").asInt() == 57;
            if (valid) {
                trackB.put("status", "PACKETS_READY_ZERO_ELIGIBLE_CALLS_CODEX_PENDING");
                trackB.put("runtimePacketManifestSha256", sha256(packetManifestPath));
                trackB.put("packetCount", 27);
                trackB.put("candidateOccurrenceCount", 57);
                trackB.put(
                        "admissionConclusion",
                        "CURRENT_R7_ZERO_ELIGIBLE_ROLES_DOES_NOT_ADMIT_PROVIDER");
                trackB.set(
                        "blockingReasons",
                        mapper.valueToTree(List.of(
                                "Current R7 corpus has zero eligible model calls; it proves safe abstention, not real ambiguity handling",
                                "Track B Codex opinions and independent milestone audits are not yet sealed",
                                "DeepSeek sample egress is not authorized")));
                writeJson(outputRoot.resolve("track-b-admission.json"), trackB);
                return;
            }
        }
        trackB.put("status", "BLOCKED");
        trackB.set(
                "blockingReasons",
                mapper.valueToTree(List.of(
                        "TASK-034 R7 runtime packet evidence is absent or invalid",
                        "TASK-036 ModelAssistEligibility/FamilyModelCallPlan/runtime-isomorphic EvidencePacket seam is not proven",
                        "Track A full-document projection cannot substitute for Track B admission evidence")));
        writeJson(outputRoot.resolve("track-b-admission.json"), trackB);
    }

    private ArrayNode blindReviewPoints(JsonNode root) {
        var output = mapper.createArrayNode();
        for (var point : root.path("reviewPoints")) {
            var item = mapper.createObjectNode();
            point.fields().forEachRemaining(field -> {
                if (ALLOWED_POINT_FIELDS.contains(field.getKey())) {
                    item.set(field.getKey(), field.getValue().deepCopy());
                }
            });
            output.add(item);
        }
        if (output.size() != 9) {
            throw new IllegalStateException("Expected exactly 9 review point definitions");
        }
        return output;
    }

    private ObjectNode projectDocument(WordParserSpikeDocument document) {
        var projection = mapper.createObjectNode();
        projection.put("format", "ORDERED_BLOCKS_WITH_TABLE_CELLS");
        var locations = mapper.createArrayNode();
        var locationSequence = 1;
        for (var block : document.blocks()) {
            var locationId = "LOC-" + String.format(Locale.ROOT, "%05d", locationSequence++);
            var item = mapper.createObjectNode();
            item.put("locationId", locationId);
            item.put("kind", block.type().name());
            item.put("text", block.text());
            item.set("sectionPath", mapper.valueToTree(block.sectionPath()));
            if (block.tableId() != null && block.rowIndex() != null) {
                var cells = mapper.createArrayNode();
                for (var cell : block.tableCells()) {
                    var projectedCell = mapper.createObjectNode();
                    projectedCell.put("locationId", locationId + "-C" + (cell.cellIndex() + 1));
                    projectedCell.put("columnOrdinal", cell.cellIndex() + 1);
                    projectedCell.put("text", cell.text());
                    cells.add(projectedCell);
                }
                item.set("cells", cells);
            }
            locations.add(item);
        }
        projection.set("locations", locations);
        return projection;
    }

    private ObjectNode outputContract() {
        var output = mapper.createObjectNode();
        output.put("format", "STRICT_JSON_ONLY");
        output.set(
                "opinionEnum",
                mapper.valueToTree(List.of(
                        "CONSISTENT",
                        "INCONSISTENT",
                        "NOT_ENOUGH_EVIDENCE",
                        "NOT_APPLICABLE")));
        output.set(
                "fields",
                mapper.valueToTree(List.of(
                        "reviewPointCode",
                        "opinion",
                        "confidence",
                        "candidateValues",
                        "evidenceQuotes",
                        "opaqueLocations",
                        "insufficiencyReason")));
        output.put(
                "instruction",
                "Return one opinion for every review point. Cite only supplied opaque location IDs. Do not invent missing evidence.");
        return output;
    }

    private void assertBlind(JsonNode node) {
        scanNode(node, "$");
        var serialized = node.toString();
        if (FORBIDDEN_RUNTIME_ID.matcher(serialized).find()) {
            throw new IllegalStateException("Blind package leaks a runtime location identity");
        }
    }

    private void scanNode(JsonNode node, String path) {
        if (node.isObject()) {
            node.fields().forEachRemaining(field -> {
                var normalized = field.getKey().toLowerCase(Locale.ROOT).replace("_", "");
                if (FORBIDDEN_KEY_FRAGMENTS.stream().anyMatch(normalized::contains)) {
                    throw new IllegalStateException("Blind package leaks forbidden key at " + path);
                }
                scanNode(field.getValue(), path + "." + field.getKey());
            });
        } else if (node.isArray()) {
            for (var index = 0; index < node.size(); index++) {
                scanNode(node.get(index), path + "[" + index + "]");
            }
        }
    }

    private static String requiredText(JsonNode node, String field) {
        var value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalStateException("Missing trusted fixture field: " + field);
        }
        return value.asText();
    }

    private static void requireUnderRoot(Path path, Path root) {
        if (!path.toAbsolutePath().normalize().startsWith(root.toAbsolutePath().normalize())) {
            throw new SecurityException("Fixture path escapes fixture root");
        }
    }

    private void writeJson(Path path, JsonNode node) throws IOException {
        Files.createDirectories(path.getParent());
        var bytes = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(node);
        Files.write(path, bytes);
    }

    private static String sha256(Path path) throws IOException {
        try (var input = Files.newInputStream(path)) {
            var digest = MessageDigest.getInstance("SHA-256");
            var buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    BlindEvaluationProjectionGenerator() {
    }
}
