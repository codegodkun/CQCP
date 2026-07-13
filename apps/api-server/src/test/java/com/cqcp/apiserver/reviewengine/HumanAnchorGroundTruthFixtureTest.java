package com.cqcp.apiserver.reviewengine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class HumanAnchorGroundTruthFixtureTest {

    private static final Path FIXTURE_ROOT =
            Path.of("..", "..", "packages", "test-fixtures").normalize();
    private static final Path XLSX_PATH =
            Path.of("..", "..", "outputs", "task-data-001-anchor-template",
                    "TASK-DATA-001-human-anchor-template.xlsx").normalize();
    private static final Path HUMAN_ANCHORS_DIR = FIXTURE_ROOT.resolve("human-anchors");
    private static final Path EXPECTED_DIR = FIXTURE_ROOT.resolve("expected");

    private static final String[] FROZEN_SAMPLES = {
        "CQCP-MVP-DOCX-001", "CQCP-MVP-DOCX-002", "CQCP-MVP-DOCX-003"
    };
    private static final int[] FROZEN_ACCEPTED = {22, 19, 22};
    private static final int[] FROZEN_INCLUDED = {20, 17, 20};
    private static final int[] FROZEN_EXCLUDED = {2, 2, 2};
    private static final String[] FROZEN_CANONICALS = {
        "TABLE_ROW:block-4:0", "BLOCK:block-38", "TABLE_ROW:block-4:1"
    };
    private static final String[] FROZEN_CASE_IDS = {
        "CQCP-MVP-DOCX-001#PARTY_A_NAME_CONSISTENCY",
        "CQCP-MVP-DOCX-002#CONTRACT_TOTAL_AMOUNT_CONSISTENCY",
        "CQCP-MVP-DOCX-003#PARTY_B_NAME_CONSISTENCY"
    };
    private static final String[] FROZEN_REVIEW_POINT_CODES = {
        "PARTY_A_NAME_CONSISTENCY",
        "CONTRACT_TOTAL_AMOUNT_CONSISTENCY",
        "PARTY_B_NAME_CONSISTENCY"
    };
    private static final String[] FROZEN_EXPECTED_CANDIDATE_VALUES = {
        "奔腾公司", "884800", "远航建筑工程有限公司"
    };

    // §2.2: 16 XLSX direct fields — compared using DataFormatter display values
    private static final String[] DIRECT_FIELDS = {
        "occurrenceNo", "reviewPointCode", "reviewPointName",
        "expectedCandidateValue", "observedCandidateValue", "comparisonResult",
        "anchorGranularity", "humanAnchorText", "humanLocationDescription",
        "tableContext", "rowContext", "cellContext",
        "groundTruthSource", "dataOwner", "independenceStatement", "status"
    };

    // §2.2: 18 frozen occurrence keys — each occurrence must have exactly this set
    private static final Set<String> FROZEN_OCCURRENCE_KEYS = Set.of(
        "occurrenceNo", "reviewPointCode", "reviewPointName",
        "expectedCandidateValue", "observedCandidateValue", "comparisonResult",
        "anchorGranularity", "humanAnchorText", "humanLocationDescription",
        "tableContext", "rowContext", "cellContext",
        "groundTruthSource", "dataOwner", "independenceStatement", "status",
        "includedInConsistencyEvaluation", "exclusionReason"
    );

    private static final Set<String> FORBIDDEN_PARSER_KEYS = Set.of(
        "blockId", "rowIndex", "cellIndex", "previewElementRef",
        "expectedCanonicalAnchors", "canonicalKey", "sourceOrigin",
        "sourceExtractionMode", "contextType", "regionType", "confidence",
        "locationLevel", "evidenceSummary", "sectionPath"
    );

    // All columns that must exist in the XLSX header: 16 direct fields + 3 metadata
    private static final String[] REQUIRED_XLSX_COLUMNS = {
        "sampleId", "occurrenceNo", "reviewPointCode", "reviewPointName",
        "expectedCandidateValue", "observedCandidateValue", "comparisonResult",
        "anchorGranularity", "humanAnchorText", "humanLocationDescription",
        "tableContext", "rowContext", "cellContext",
        "groundTruthSource", "dataOwner", "independenceStatement", "status",
        "notes", "docxPath"
    };

    private final ObjectMapper objectMapper = new ObjectMapper();

    // --- 1. Frozen counts ---

    @Test
    void shouldHaveFrozenOccurrenceCountsPerSample() throws Exception {
        Map<String, List<Map<String, String>>> xlsxData = readXlsx();
        for (int i = 0; i < FROZEN_SAMPLES.length; i++) {
            String sid = FROZEN_SAMPLES[i];
            int xlsxCount = xlsxData.get(sid).size();
            JsonNode fixture = loadFixture(sid);
            int fixtureCount = fixture.get("occurrences").size();
            JsonNode expected = loadExpected(sid);
            JsonNode ref = expected.at("/goldenExpected/humanAnchorGroundTruth");

            assertThat(xlsxCount).as(sid + " XLSX count").isEqualTo(FROZEN_ACCEPTED[i]);
            assertThat(fixtureCount).as(sid + " fixture count").isEqualTo(FROZEN_ACCEPTED[i]);
            assertThat(ref.get("acceptedOccurrenceCount").asInt())
                    .as(sid + " expected acceptedOccurrenceCount")
                    .isEqualTo(FROZEN_ACCEPTED[i]);
        }
    }

    // --- 2. 16 direct fields match XLSX with type + index-order assertions ---

    @Test
    void shouldMatchXlsxDirectFieldsForAll63Occurrences() throws Exception {
        Map<String, List<Map<String, String>>> xlsxData = readXlsx();
        int total = 0;
        for (String sid : FROZEN_SAMPLES) {
            List<Map<String, String>> xlsxRows = xlsxData.get(sid);
            JsonNode fixture = loadFixture(sid);
            JsonNode occurrences = fixture.get("occurrences");
            assertThat(occurrences.size()).isEqualTo(xlsxRows.size());

            for (int j = 0; j < xlsxRows.size(); j++) {
                Map<String, String> xlsxRow = xlsxRows.get(j);
                JsonNode fixtOcc = occurrences.get(j);

                // Index-order assertion: fixture occurrenceNo at index j
                // must match XLSX row at index j.  Reordering the fixture
                // array will cause this to fail.
                String xlsxOccNo = xlsxRow.get("occurrenceNo");
                assertThat(fixtOcc.get("occurrenceNo").asText())
                        .as(sid + " index " + j + " occurrenceNo order mismatch")
                        .isEqualTo(xlsxOccNo);

                for (String field : DIRECT_FIELDS) {
                    JsonNode fieldNode = fixtOcc.get(field);
                    // R1: assert JSON node type is string for all 16 direct fields
                    assertThat(fieldNode.isTextual())
                            .as(sid + " " + xlsxOccNo + " field " + field + " must be JSON string")
                            .isTrue();

                    String xlsxValue = xlsxRow.getOrDefault(field, "");
                    assertThat(fieldNode.asText())
                            .as(sid + " " + xlsxOccNo + " field " + field)
                            .isEqualTo(xlsxValue);
                }

                // R1: includedInConsistencyEvaluation must be JSON boolean
                assertThat(fixtOcc.get("includedInConsistencyEvaluation").isBoolean())
                        .as(sid + " " + xlsxOccNo + " includedInConsistencyEvaluation must be JSON boolean")
                        .isTrue();

                // Verify frozen status values
                assertThat(fixtOcc.get("comparisonResult").asText()).isEqualTo("MATCH");
                assertThat(fixtOcc.get("groundTruthSource").asText()).isEqualTo("MANUAL_DOCX_REVIEW");
                assertThat(fixtOcc.get("status").asText()).isEqualTo("ACCEPTED_HUMAN_GROUND_TRUTH");

                total++;
            }
        }
        assertThat(total).isEqualTo(63);
    }

    // --- 3. Derived fields from notes (with index-order assertion) ---

    @Test
    void shouldDeriveInclusionAndExclusionFromNotes() throws Exception {
        Map<String, List<Map<String, String>>> xlsxData = readXlsx();
        int totalExcluded = 0, totalIncluded = 0;
        int[] excludedPerSample = new int[3];
        int[] includedPerSample = new int[3];

        for (int i = 0; i < FROZEN_SAMPLES.length; i++) {
            String sid = FROZEN_SAMPLES[i];
            List<Map<String, String>> xlsxRows = xlsxData.get(sid);
            JsonNode fixture = loadFixture(sid);
            JsonNode occurrences = fixture.get("occurrences");

            for (int j = 0; j < xlsxRows.size(); j++) {
                Map<String, String> xlsxRow = xlsxRows.get(j);
                JsonNode fixtOcc = occurrences.get(j);

                // Index-order assertion
                assertThat(fixtOcc.get("occurrenceNo").asText())
                        .as(sid + " index " + j + " occurrenceNo order mismatch in derivation test")
                        .isEqualTo(xlsxRow.get("occurrenceNo"));

                String occNo = xlsxRow.get("occurrenceNo");
                String notes = xlsxRow.getOrDefault("notes", "");

                // R1: isBoolean for includedInConsistencyEvaluation
                assertThat(fixtOcc.get("includedInConsistencyEvaluation").isBoolean())
                        .as(sid + " " + occNo + " includedInConsistencyEvaluation must be JSON boolean")
                        .isTrue();

                boolean expectedIncluded = !notes.trim().startsWith("排除：");
                boolean actualIncluded = fixtOcc.get("includedInConsistencyEvaluation").asBoolean();

                assertThat(actualIncluded)
                        .as(sid + " " + occNo + " includedInConsistencyEvaluation")
                        .isEqualTo(expectedIncluded);

                if (!expectedIncluded) {
                    String expectedReason = notes.trim().substring("排除：".length());
                    assertThat(fixtOcc.get("exclusionReason").isNull())
                            .as(sid + " " + occNo + " exclusionReason should not be null")
                            .isFalse();
                    assertThat(fixtOcc.get("exclusionReason").asText())
                            .isEqualTo(expectedReason);
                    totalExcluded++;
                    excludedPerSample[i]++;
                } else {
                    assertThat(fixtOcc.get("exclusionReason").isNull())
                            .as(sid + " " + occNo + " exclusionReason should be null")
                            .isTrue();
                    totalIncluded++;
                    includedPerSample[i]++;
                }
            }

            JsonNode expected = loadExpected(sid);
            JsonNode ref = expected.at("/goldenExpected/humanAnchorGroundTruth");
            assertThat(ref.get("includedOccurrenceCount").asInt())
                    .as(sid + " expected includedOccurrenceCount")
                    .isEqualTo(FROZEN_INCLUDED[i]);
            assertThat(ref.get("excludedOccurrenceCount").asInt())
                    .as(sid + " expected excludedOccurrenceCount")
                    .isEqualTo(FROZEN_EXCLUDED[i]);
        }

        assertThat(totalExcluded).isEqualTo(6);
        assertThat(totalIncluded).isEqualTo(57);
        for (int i = 0; i < 3; i++) {
            assertThat(excludedPerSample[i]).as(FROZEN_SAMPLES[i] + " excluded").isEqualTo(2);
            assertThat(includedPerSample[i]).as(FROZEN_SAMPLES[i] + " included").isEqualTo(FROZEN_INCLUDED[i]);
        }
    }

    // --- 4. Canonical anchors + full positiveCase frozen ---

    @Test
    void parserBackedCanonicalAnchorsRemainFrozen() throws Exception {
        for (int i = 0; i < FROZEN_SAMPLES.length; i++) {
            JsonNode expected = loadExpected(FROZEN_SAMPLES[i]);
            JsonNode positiveCases = expected.at("/goldenExpected/evidenceEvaluation/positiveCases");

            // R4: freeze positiveCases count
            assertThat(positiveCases.size()).as(FROZEN_SAMPLES[i] + " positiveCases count").isEqualTo(1);

            JsonNode positiveCase = positiveCases.get(0);

            // R4: freeze caseId, reviewPointCode, expectedCandidateValue
            assertThat(positiveCase.get("caseId").asText())
                    .as(FROZEN_SAMPLES[i] + " caseId")
                    .isEqualTo(FROZEN_CASE_IDS[i]);
            assertThat(positiveCase.get("reviewPointCode").asText())
                    .as(FROZEN_SAMPLES[i] + " reviewPointCode")
                    .isEqualTo(FROZEN_REVIEW_POINT_CODES[i]);
            assertThat(positiveCase.get("expectedCandidateValue").asText())
                    .as(FROZEN_SAMPLES[i] + " expectedCandidateValue")
                    .isEqualTo(FROZEN_EXPECTED_CANDIDATE_VALUES[i]);

            JsonNode anchors = positiveCase.get("expectedCanonicalAnchors");
            assertThat(anchors.size()).isEqualTo(1);
            assertThat(anchors.get(0).asText()).isEqualTo(FROZEN_CANONICALS[i]);
        }
    }

    // --- 5. Exact 18-key set + no parser-derived fields ---

    @Test
    void shouldNotContainParserDerivedFields() throws Exception {
        for (String sid : FROZEN_SAMPLES) {
            JsonNode fixture = loadFixture(sid);
            for (JsonNode occ : fixture.get("occurrences")) {
                var actualKeys = new LinkedHashSet<String>();
                occ.fieldNames().forEachRemaining(actualKeys::add);

                // R4: exactly 18 keys
                assertThat(actualKeys)
                        .as(sid + " " + occ.get("occurrenceNo").asText()
                                + " key set must be exactly the 18 frozen keys")
                        .isEqualTo(FROZEN_OCCURRENCE_KEYS);

                // No forbidden keys present
                for (String forbidden : FORBIDDEN_PARSER_KEYS) {
                    assertThat(occ.has(forbidden))
                            .as(sid + " should not have " + forbidden)
                            .isFalse();
                }
            }
        }
    }

    // --- 6. TABLE_CELL context required ---

    @Test
    void shouldHaveValidTableContextForTableCellGranularity() throws Exception {
        for (String sid : FROZEN_SAMPLES) {
            JsonNode fixture = loadFixture(sid);
            for (JsonNode occ : fixture.get("occurrences")) {
                String granularity = occ.get("anchorGranularity").asText();
                String occNo = occ.get("occurrenceNo").asText();
                if ("TABLE_CELL".equals(granularity)) {
                    assertThat(occ.get("tableContext").isTextual())
                            .as(sid + " " + occNo + " tableContext must be string").isTrue();
                    assertThat(occ.get("tableContext").asText())
                            .as(sid + " " + occNo + " tableContext")
                            .isNotBlank().isNotEqualTo("不适用");
                    assertThat(occ.get("rowContext").isTextual())
                            .as(sid + " " + occNo + " rowContext must be string").isTrue();
                    assertThat(occ.get("rowContext").asText())
                            .as(sid + " " + occNo + " rowContext")
                            .isNotBlank().isNotEqualTo("不适用");
                    assertThat(occ.get("cellContext").isTextual())
                            .as(sid + " " + occNo + " cellContext must be string").isTrue();
                    assertThat(occ.get("cellContext").asText())
                            .as(sid + " " + occNo + " cellContext")
                            .isNotBlank().isNotEqualTo("不适用");
                }
            }
        }
    }

    // --- 7. Unique occurrenceNo ---

    @Test
    void shouldHaveGloballyUniqueOccurrenceNos() throws Exception {
        Set<String> allNos = new LinkedHashSet<>();
        int total = 0;
        for (String sid : FROZEN_SAMPLES) {
            JsonNode fixture = loadFixture(sid);
            for (JsonNode occ : fixture.get("occurrences")) {
                // R1: occurrenceNo must be a JSON string value
                JsonNode occNoNode = occ.get("occurrenceNo");
                assertThat(occNoNode.isTextual())
                        .as(sid + " occurrenceNo must be JSON string").isTrue();

                allNos.add(occNoNode.asText());
                total++;
            }
        }
        assertThat(allNos).hasSize(total).hasSize(63);
    }

    // --- 8. Traceability chain ---

    @Test
    void shouldHaveValidTraceabilityChain() throws Exception {
        for (int i = 0; i < FROZEN_SAMPLES.length; i++) {
            String sid = FROZEN_SAMPLES[i];
            JsonNode fixture = loadFixture(sid);
            JsonNode expected = loadExpected(sid);

            // Fixture top-level frozen fields
            assertThat(fixture.get("schemaVersion").asText()).isEqualTo("v1");
            assertThat(fixture.get("sampleId").asText()).isEqualTo(sid);
            assertThat(fixture.get("sourceWorkbook").asText())
                    .isEqualTo("outputs/task-data-001-anchor-template/TASK-DATA-001-human-anchor-template.xlsx");
            assertThat(fixture.get("sourceSheet").asText()).isEqualTo("anchor明细待确认");
            assertThat(fixture.get("dataOwner").asText()).isEqualTo("ZK");
            assertThat(fixture.get("groundTruthSource").asText()).isEqualTo("MANUAL_DOCX_REVIEW");
            assertThat(fixture.get("status").asText()).isEqualTo("ACCEPTED_HUMAN_GROUND_TRUTH");

            // Expected reference object
            JsonNode ref = expected.at("/goldenExpected/humanAnchorGroundTruth");
            assertThat(ref.get("schemaVersion").asText()).isEqualTo("v1");
            assertThat(ref.get("fixture").asText())
                    .isEqualTo("human-anchors/" + sid + ".json");
            assertThat(ref.get("sourceWorkbook").asText())
                    .isEqualTo("outputs/task-data-001-anchor-template/TASK-DATA-001-human-anchor-template.xlsx");
            assertThat(ref.get("sourceSheet").asText()).isEqualTo("anchor明细待确认");
            assertThat(ref.get("groundTruthSource").asText()).isEqualTo("MANUAL_DOCX_REVIEW");
            assertThat(ref.get("dataOwner").asText()).isEqualTo("ZK");
            assertThat(ref.get("status").asText()).isEqualTo("ACCEPTED_HUMAN_GROUND_TRUTH");

            assertThat(ref.get("acceptedOccurrenceCount").asInt())
                    .isEqualTo(FROZEN_ACCEPTED[i]);
            assertThat(ref.get("acceptedOccurrenceCount").asInt())
                    .isEqualTo(fixture.get("occurrences").size());

            Path fixturePath = FIXTURE_ROOT.resolve(ref.get("fixture").asText());
            assertThat(Files.exists(fixturePath))
                    .as("fixture path " + fixturePath + " must exist").isTrue();
        }
    }

    // --- 9. sourceDocx consistency ---

    @Test
    void shouldHaveConsistentSourceDocxPerSample() throws Exception {
        Map<String, List<Map<String, String>>> xlsxData = readXlsx();
        for (String sid : FROZEN_SAMPLES) {
            Set<String> docxPaths = new LinkedHashSet<>();
            for (var row : xlsxData.get(sid)) {
                String dp = row.get("docxPath");
                assertThat(dp).as(sid + " docxPath prefix")
                        .startsWith("packages/test-fixtures/docx/");
                docxPaths.add(dp);
            }
            assertThat(docxPaths).as(sid + " docxPath unique").hasSize(1);

            String expectedSourceDocx = docxPaths.iterator().next()
                    .substring("packages/test-fixtures/".length());
            JsonNode fixture = loadFixture(sid);
            assertThat(fixture.get("sourceDocx").asText()).isEqualTo(expectedSourceDocx);

            JsonNode expectedJson = loadExpected(sid);
            assertThat(expectedJson.get("sourceDocx").asText()).isEqualTo(expectedSourceDocx);
        }
    }

    // --- 10. Empty strings preserved ---

    @Test
    void shouldPreserveEmptyStringFromXlsxDirectFields() throws Exception {
        Map<String, List<Map<String, String>>> xlsxData = readXlsx();
        for (String sid : FROZEN_SAMPLES) {
            List<Map<String, String>> xlsxRows = xlsxData.get(sid);
            JsonNode fixture = loadFixture(sid);
            JsonNode occurrences = fixture.get("occurrences");

            for (int j = 0; j < xlsxRows.size(); j++) {
                var xlsxRow = xlsxRows.get(j);
                JsonNode fixtOcc = occurrences.get(j);

                for (String field : DIRECT_FIELDS) {
                    String xlsxValue = xlsxRow.getOrDefault(field, "");
                    if (xlsxValue.isEmpty()) {
                        assertThat(fixtOcc.get(field).isTextual())
                                .as(sid + " " + xlsxRow.get("occurrenceNo")
                                        + " field " + field + " must be JSON string")
                                .isTrue();
                        assertThat(fixtOcc.get(field).asText())
                                .as(sid + " " + xlsxRow.get("occurrenceNo")
                                        + " field " + field + " should be empty string")
                                .isEqualTo("");
                        assertThat(fixtOcc.get(field).isNull())
                                .as(sid + " " + xlsxRow.get("occurrenceNo")
                                        + " field " + field + " should not be null")
                                .isFalse();
                    }
                }
            }
        }
    }

    // --- helpers ---

    /**
     * Reads XLSX sheet "anchor明细待确认" using Apache POI DataFormatter.
     * Returns map of sampleId → list of row data (XLSX column name → display value),
     * in XLSX row order.
     */
    private Map<String, List<Map<String, String>>> readXlsx() throws Exception {
        var samples = new LinkedHashMap<String, List<Map<String, String>>>();
        for (String sid : FROZEN_SAMPLES) {
            samples.put(sid, new ArrayList<>());
        }

        try (var wb = new XSSFWorkbook(new FileInputStream(XLSX_PATH.toFile()))) {
            Sheet sheet = wb.getSheet("anchor明细待确认");
            assertThat(sheet).as("XLSX sheet must exist").isNotNull();

            var fmt = new DataFormatter();
            Row headerRow = sheet.getRow(0);
            Map<String, Integer> colMap = new LinkedHashMap<>();
            for (Cell cell : headerRow) {
                String name = fmt.formatCellValue(cell).trim();
                if (!name.isEmpty()) colMap.put(name, cell.getColumnIndex());
            }

            // R3: all 16 direct fields + sampleId + notes + docxPath must be present
            for (String r : REQUIRED_XLSX_COLUMNS) {
                assertThat(colMap).as("XLSX must contain column: " + r).containsKey(r);
            }

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                String sid = fmt.formatCellValue(row.getCell(colMap.get("sampleId")));
                String status = fmt.formatCellValue(row.getCell(colMap.get("status")));
                if (!samples.containsKey(sid)) continue;
                if (!"ACCEPTED_HUMAN_GROUND_TRUTH".equals(status)) continue;

                Map<String, String> rec = new LinkedHashMap<>();
                for (String col : colMap.keySet()) {
                    Cell cell = row.getCell(colMap.get(col));
                    rec.put(col, fmt.formatCellValue(cell));
                }

                // Verify state invariants
                String cr = rec.get("comparisonResult");
                String gts = rec.get("groundTruthSource");
                if (!"MATCH".equals(cr)) {
                    fail("STOP: " + sid + " " + rec.get("occurrenceNo") + " comparisonResult=" + cr);
                }
                if (!"MANUAL_DOCX_REVIEW".equals(gts)) {
                    fail("STOP: " + sid + " " + rec.get("occurrenceNo") + " groundTruthSource=" + gts);
                }

                // Verify notes isn't "排除：" without reason
                String notes = rec.get("notes");
                if (notes != null && notes.trim().equals("排除：")) {
                    fail("STOP: " + sid + " " + rec.get("occurrenceNo")
                            + " notes is '排除：' without reason text");
                }

                samples.get(sid).add(rec);
            }
        }
        return samples;
    }

    private JsonNode loadFixture(String sampleId) throws Exception {
        Path path = HUMAN_ANCHORS_DIR.resolve(sampleId + ".json");
        assertThat(Files.exists(path))
                .as("fixture file must exist: " + path).isTrue();
        return objectMapper.readTree(Files.readString(path));
    }

    private JsonNode loadExpected(String sampleId) throws Exception {
        Path path = EXPECTED_DIR.resolve(sampleId + ".json");
        assertThat(Files.exists(path))
                .as("expected file must exist: " + path).isTrue();
        return objectMapper.readTree(Files.readString(path));
    }
}
