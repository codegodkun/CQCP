package com.cqcp.apiserver.wordparser;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class DocxWordParserSpikeTest {

    private static final Path FIXTURE_ROOT = Path.of("..", "..", "packages", "test-fixtures").normalize();
    private final DocxWordParserSpike parser = new DocxWordParserSpike();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesAllExpectedFixturesIntoStructuredArtifacts() throws IOException {
        var fixturePaths = expectedFixturePaths();

        assertThat(fixturePaths).hasSize(4);

        for (Path expectedPath : fixturePaths) {
            JsonNode expected = objectMapper.readTree(Files.readString(expectedPath));
            Path docxPath = FIXTURE_ROOT.resolve(expected.path("sourceDocx").asText()).normalize();

            assertThat(Files.exists(docxPath))
                    .as("docx fixture exists for %s", expectedPath.getFileName())
                    .isTrue();

            var parsed = parser.parse(docxPath);
            var report = parsed.parseQualityReport();
            var joinedText = parsed.blocks().stream()
                    .map(WordParserSpikeDocument.DocumentBlock::normalizedText)
                    .reduce("", (left, right) -> left + "\n" + right)
                    .toLowerCase(Locale.ROOT);
            var canonicalJoinedText = canonicalize(joinedText);

            assertThat(report.fileType()).isEqualTo("DOCX");
            assertThat(report.parser()).contains("Apache POI");
            assertThat(report.parseStatus()).isNotEqualTo(WordParserSpikeDocument.ParseStatus.FAILED);
            assertThat(report.blockCount()).isGreaterThan(0);
            assertThat(parsed.blocks()).allSatisfy(block -> {
                assertThat(block.contextType()).isNotNull();
                assertThat(block.sourceOrigin()).isNotNull();
                assertThat(block.sourceExtractionMode()).isNotNull();
                assertThat(block.blockConfidence()).isNotNull();
                assertThat(block.previewAnchorLevel()).isNotNull();
            });

            var projectName = normalize(expected.at("/goldenExpected/displayValues/projectName").asText());
            var partyAName = normalize(expected.at("/goldenExpected/displayValues/partyAName").asText());

            assertThat(canonicalJoinedText).contains(canonicalize(projectName.toLowerCase(Locale.ROOT)));
            assertThat(canonicalJoinedText).contains(canonicalize(partyAName.toLowerCase(Locale.ROOT)));
        }
    }

    @Test
    void preservesAggregateCoverageForTask016ParserSpikeGate() throws IOException {
        int totalAppendixBlocks = 0;
        int totalControls = 0;
        int totalHeadings = 0;
        int totalTableRows = 0;

        for (Path expectedPath : expectedFixturePaths()) {
            JsonNode expected = objectMapper.readTree(Files.readString(expectedPath));
            Path docxPath = FIXTURE_ROOT.resolve(expected.path("sourceDocx").asText()).normalize();
            var parsed = parser.parse(docxPath);

            totalAppendixBlocks += parsed.parseQualityReport().appendixRegionCount();
            totalControls += parsed.parseQualityReport().formControlCount();
            totalHeadings += parsed.parseQualityReport().headingCount();
            totalTableRows += (int) parsed.blocks().stream()
                    .filter(block -> block.type() == WordParserSpikeDocument.BlockType.TABLE_ROW)
                    .count();
        }

        assertThat(totalHeadings).isGreaterThan(0);
        assertThat(totalTableRows).isGreaterThan(0);
        assertThat(totalAppendixBlocks).isGreaterThan(0);
        assertThat(totalControls).isGreaterThan(0);
    }

    @Test
    void tableRowsPreserveStableCellIndexesAndJoinedTextRanges() throws IOException {
        var expected = objectMapper.readTree(Files.readString(
                FIXTURE_ROOT.resolve("expected").resolve("CQCP-MVP-DOCX-001.json")));
        var parsed = parser.parse(FIXTURE_ROOT.resolve(expected.path("sourceDocx").asText()).normalize());
        var tableRows = parsed.blocks().stream()
                .filter(block -> block.type() == WordParserSpikeDocument.BlockType.TABLE_ROW)
                .toList();

        assertThat(tableRows).isNotEmpty();
        assertThat(tableRows)
                .filteredOn(block -> block.tableCells().size() > 1)
                .isNotEmpty()
                .allSatisfy(block -> {
                    assertThat(block.tableId()).isNotBlank();
                    assertThat(block.rowIndex()).isNotNull();
                    assertThat(block.tableCells())
                            .extracting(WordParserSpikeDocument.TableCellSpan::cellIndex)
                            .containsExactlyElementsOf(
                                    java.util.stream.IntStream.range(0, block.tableCells().size())
                                            .boxed()
                                            .toList());
                    assertThat(block.tableCells()).allSatisfy(cell -> {
                        assertThat(cell.startOffset()).isGreaterThanOrEqualTo(0);
                        assertThat(cell.endOffset()).isGreaterThanOrEqualTo(cell.startOffset());
                        assertThat(block.text().substring(cell.startOffset(), cell.endOffset()))
                                .isEqualTo(cell.text());
                    });
                });
    }

    private List<Path> expectedFixturePaths() throws IOException {
        try (Stream<Path> stream = Files.list(FIXTURE_ROOT.resolve("expected"))) {
            return stream
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
    }

    private String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }

    private String canonicalize(String text) {
        return normalize(text).replaceAll("[\\p{Punct}\\p{IsPunctuation}（）【】《》、]", "");
    }
}
