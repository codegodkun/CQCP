package com.cqcp.apiserver.wordparser;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class DocxWordParserSpikeTest {

    private static final Path FIXTURE_ROOT = Path.of("..", "..", "packages", "test-fixtures").normalize();
    private final DocxWordParserSpike parser = new DocxWordParserSpike();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ════════════════════════════════════════════════════════════════════
    // 18 invocation matrix: 6 source parts × 3 mutation types
    // ════════════════════════════════════════════════════════════════════

    record ScopeInvocation(String sourcePart, String mutationType, String displayName) {}

    static Stream<ScopeInvocation> scopeInvocations() {
        var parts = List.of("BODY_PARAGRAPH", "BODY_TABLE",
                "HEADER_PARAGRAPH", "HEADER_TABLE",
                "FOOTER_PARAGRAPH", "FOOTER_TABLE");
        var muts = List.of("TRACKED_DELETION", "STRIKE", "DOUBLE_STRIKE");
        return parts.stream()
                .flatMap(p -> muts.stream().map(m -> new ScopeInvocation(p, m, p + "_" + m)));
    }

    @ParameterizedTest(name = "{arguments}")
    @MethodSource("scopeInvocations")
    void testScopeMatrix(ScopeInvocation inv, @TempDir Path tmp) throws IOException {
        var p = tmp.resolve(inv.displayName + ".docx");
        try (var doc = new XWPFDocument()) {
            buildSourcePart(doc, inv.sourcePart, inv.mutationType);
            try (var out = Files.newOutputStream(p)) { doc.write(out); }
        }
        var parsed = parser.parse(p);
        var scope = parsed.scopeCoverageReport();

        boolean isBody = inv.sourcePart.startsWith("BODY");
        boolean isDeleted = inv.mutationType.equals("TRACKED_DELETION");
        String expectedMutationContext = isDeleted ? "DELETED" : "VOIDED";
        String expectedMutationReason = isDeleted
                ? "STRONG_CONTEXT_DELETED_EXCLUDED"
                : "STRONG_CONTEXT_VOIDED_EXCLUDED";
        String expectedSourcePart = isBody ? "BODY"
                : inv.sourcePart.startsWith("HEADER") ? "HEADER" : "FOOTER";

        // 1. All four scanners must be handled (scanner executed successfully)
        assertThat(scope.handledStrongContextTypes())
                .as(inv.displayName + " all four handled")
                .containsExactlyInAnyOrder("TOC", "HEADER_FOOTER", "DELETED", "VOIDED");

        // 2. verified=true when no unresolved signals
        assertThat(scope.verified()).as(inv.displayName + " verified").isTrue();
        assertThat(scope.unresolvedSignals()).as(inv.displayName + " no unresolved").isEmpty();

        // 3. Assert the mutation-specific region exists with exact sourcePart, contextType, reason
        var mutationRegions = scope.excludedSourceRegions().stream()
                .filter(e -> expectedMutationContext.equals(e.contextType())
                        && expectedSourcePart.equals(e.sourcePart())
                        && expectedMutationReason.equals(e.reason()))
                .toList();
        assertThat(mutationRegions)
                .as(inv.displayName + " mutation-specific region for " + expectedMutationContext)
                .hasSize(1);

        if (isBody) {
            // BODY mutation: blockId depends on whether visible DocumentBlock exists
            var firstRegion = mutationRegions.get(0);
            if (isDeleted) {
                // Deletion-only paragraph/row has no visible text → no DocumentBlock → blockId=null
                assertThat(mutationRegions)
                        .as(inv.displayName + " body deletion hasSize 1")
                        .hasSize(1);
                assertThat(firstRegion.blockId())
                        .as(inv.displayName + " body deletion-only blockId is null")
                        .isNull();
                // Verify deleted text does not appear in any legacy blocks
                boolean deletedInLegacy = parsed.blocks().stream()
                        .anyMatch(b -> b.text().contains("del"));
                assertThat(deletedInLegacy)
                        .as(inv.displayName + " deleted text not in legacy blocks")
                        .isFalse();
            } else {
                // Strike/dstrike paragraph/row has visible text → DocumentBlock exists
                // Get the exact expected block by type (PARAGRAPH for body paragraph, TABLE_ROW for body table)
                var expectedType = inv.sourcePart.equals("BODY_PARAGRAPH")
                        ? WordParserSpikeDocument.BlockType.PARAGRAPH
                        : WordParserSpikeDocument.BlockType.TABLE_ROW;
                var matchedBlock = parsed.blocks().stream()
                        .filter(b -> b.type() == expectedType)
                        .findFirst().orElseThrow();
                assertThat(mutationRegions)
                        .as(inv.displayName + " body strike/dstrike hasSize 1")
                        .hasSize(1);
                assertThat(firstRegion.blockId())
                        .as(inv.displayName + " body strike/dstrike blockId equals real block")
                        .isEqualTo(matchedBlock.blockId());
            }
        } else {
            // Header/footer: mutation region must have blockId=null
            var firstRegion = mutationRegions.get(0);
            assertThat(firstRegion.blockId())
                    .as(inv.displayName + " header/footer mutation blockId is null")
                    .isNull();

            // Also verify the part-level HEADER_FOOTER exclusion region exists
            var hfRegions = scope.excludedSourceRegions().stream()
                    .filter(e -> "HEADER_FOOTER".equals(e.contextType())
                            && expectedSourcePart.equals(e.sourcePart())
                            && "STRONG_CONTEXT_HEADER_FOOTER_EXCLUDED".equals(e.reason()))
                    .toList();
            assertThat(hfRegions)
                    .as(inv.displayName + " HEADER_FOOTER part-level region")
                    .hasSize(1);
            assertThat(hfRegions.get(0).blockId())
                    .as(inv.displayName + " HEADER_FOOTER blockId is null")
                    .isNull();

            // Verify deleted/struck text does not appear in any legacy blocks
            boolean hasMutationText = parsed.blocks().stream()
                    .anyMatch(b -> b.text().contains("del") || b.text().equals("s") || b.text().equals("d"));
            assertThat(hasMutationText)
                    .as(inv.displayName + " mutation text not in legacy blocks")
                    .isFalse();
        }
    }

    private void buildSourcePart(XWPFDocument doc, String sourcePart, String mutationType) {
        switch (sourcePart) {
            case "BODY_PARAGRAPH" -> {
                var para = doc.createParagraph();
                addMutation(para, mutationType);
            }
            case "BODY_TABLE" -> {
                var t = doc.createTable(1, 1);
                var cell = t.getRow(0).getCell(0);
                addMutation(cell.addParagraph(), mutationType);
            }
            case "HEADER_PARAGRAPH" -> {
                var header = doc.createHeader(HeaderFooterType.DEFAULT);
                addMutation(header.createParagraph(), mutationType);
            }
            case "HEADER_TABLE" -> {
                var header = doc.createHeader(HeaderFooterType.DEFAULT);
                var ht = header.createTable(1, 1);
                var cell = ht.getRow(0).getCell(0);
                addMutation(cell.addParagraph(), mutationType);
            }
            case "FOOTER_PARAGRAPH" -> {
                var footer = doc.createFooter(HeaderFooterType.DEFAULT);
                addMutation(footer.createParagraph(), mutationType);
            }
            case "FOOTER_TABLE" -> {
                var footer = doc.createFooter(HeaderFooterType.DEFAULT);
                var ft = footer.createTable(1, 1);
                var cell = ft.getRow(0).getCell(0);
                addMutation(cell.addParagraph(), mutationType);
            }
        }
    }

    private void addMutation(XWPFParagraph p, String mutationType) {
        switch (mutationType) {
            case "TRACKED_DELETION" -> {
                p.getCTP().addNewDel().addNewR().addNewDelText().setStringValue("del");
            }
            case "STRIKE" -> {
                var r = p.createRun();
                r.setText("s");
                r.setStrikeThrough(true);
            }
            case "DOUBLE_STRIKE" -> {
                var r = p.createRun();
                r.setText("d");
                r.getCTR().addNewRPr().addNewDstrike();
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // 5 gates
    // ════════════════════════════════════════════════════════════════════

    @Test void gateMixedDeletedLive(@TempDir Path tmp) throws IOException {
        var p = tmp.resolve("md.docx");
        try (var doc = new XWPFDocument()) {
            var para = doc.createParagraph();
            para.getCTP().addNewDel().addNewR().addNewDelText().setStringValue("del");
            para.createRun().setText("live");
            try (var out = Files.newOutputStream(p)) { doc.write(out); }
        }
        var parsed = parser.parse(p);
        var scope = parsed.scopeCoverageReport();
        assertThat(scope.unresolvedSignals()).containsExactly("SCOPE_DELETED_MIXED_LIVE_UNRESOLVED");
        assertThat(scope.verified()).isFalse();
    }

    @Test void gateMixedVoidedLive(@TempDir Path tmp) throws IOException {
        var p = tmp.resolve("mv.docx");
        try (var doc = new XWPFDocument()) {
            var para = doc.createParagraph();
            var r1 = para.createRun(); r1.setText("s"); r1.setStrikeThrough(true);
            para.createRun().setText("l");
            try (var out = Files.newOutputStream(p)) { doc.write(out); }
        }
        var parsed = parser.parse(p);
        var scope = parsed.scopeCoverageReport();
        assertThat(scope.unresolvedSignals()).containsExactly("SCOPE_VOIDED_MIXED_LIVE_UNRESOLVED");
        assertThat(scope.verified()).isFalse();
    }

    @Test void gateDuplicateParagraphText(@TempDir Path tmp) throws IOException {
        var p = tmp.resolve("dp.docx");
        try (var doc = new XWPFDocument()) {
            doc.createParagraph().createRun().setText("相同文本");
            doc.createParagraph().createRun().setText("相同文本");
            try (var out = Files.newOutputStream(p)) { doc.write(out); }
        }
        var parsed = parser.parse(p);
        assertThat(parsed.blocks()).hasSize(2);
        assertThat(parsed.blocks().get(0).blockId()).isNotEqualTo(parsed.blocks().get(1).blockId());
    }

    @Test void gateDuplicateTableText(@TempDir Path tmp) throws IOException {
        var p = tmp.resolve("dt.docx");
        try (var doc = new XWPFDocument()) {
            var t = doc.createTable(2, 1);
            t.getRow(0).getCell(0).setText("相同文本");
            t.getRow(1).getCell(0).setText("相同文本");
            try (var out = Files.newOutputStream(p)) { doc.write(out); }
        }
        var parsed = parser.parse(p);
        var rows = parsed.blocks().stream().filter(b -> b.type() == WordParserSpikeDocument.BlockType.TABLE_ROW).toList();
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).blockId()).isNotEqualTo(rows.get(1).blockId());
    }

    @Test void gateScannerException(@TempDir Path tmp) throws IOException {
        var p = tmp.resolve("ex.docx");
        try (var doc = new XWPFDocument()) {
            doc.createParagraph().createRun().setText("test");
            try (var out = Files.newOutputStream(p)) { doc.write(out); }
        }
        // Inject scanner seam that throws only for VOIDED context
        parser.setScannerExceptionSeam(ctx -> {
            if ("VOIDED".equals(ctx)) throw new RuntimeException("SIMULATED_VOIDED_ERROR");
        });
        var parsed = parser.parse(p);
        var scope = parsed.scopeCoverageReport();
        // VOIDED scanner failed → exact unresolved, no exception message leaking
        assertThat(scope.unresolvedSignals()).containsExactly("SCOPE_SCAN_VOIDED_FAILED");
        assertThat(scope.verified()).isFalse();
        // Other three scanners still handled successfully (containsExactlyInAnyOrder, not just contains)
        assertThat(scope.handledStrongContextTypes()).containsExactlyInAnyOrder("TOC", "HEADER_FOOTER", "DELETED");
        // Reset seam
        parser.setScannerExceptionSeam(DocxWordParserSpike.ScannerExceptionSeam.NO_OP);
    }

    // ════════════════════════════════════════════════════════════════════
    // Empty-cell gate: fully struck table row + default empty cell still excluded
    // ════════════════════════════════════════════════════════════════════

    @Test void gateBodyTableStrikeWithEmptyCell(@TempDir Path tmp) throws IOException {
        var p = tmp.resolve("tse.docx");
        try (var doc = new XWPFDocument()) {
            var t = doc.createTable(1, 2);
            var r = t.getRow(0).getCell(0).addParagraph().createRun();
            r.setText("s"); r.setStrikeThrough(true);
            // Cell 1 has only default empty paragraph — no non-empty unstruck content
            try (var out = Files.newOutputStream(p)) { doc.write(out); }
        }
        var parsed = parser.parse(p);
        var scope = parsed.scopeCoverageReport();
        // All four scanners handled (exact set)
        assertThat(scope.handledStrongContextTypes())
                .containsExactlyInAnyOrder("TOC", "HEADER_FOOTER", "DELETED", "VOIDED");
        assertThat(scope.verified()).isTrue();
        assertThat(scope.unresolvedSignals()).isEmpty();
        // Get the unique TABLE_ROW block from parsed blocks
        var tableRows = parsed.blocks().stream()
                .filter(b -> b.type() == WordParserSpikeDocument.BlockType.TABLE_ROW)
                .toList();
        assertThat(tableRows).hasSize(1);
        var tableRowBlock = tableRows.get(0);
        // Exact mutation region: VOIDED / BODY / STRONG_CONTEXT_VOIDED_EXCLUDED, blockId equals table-row blockId
        var excluded = scope.excludedSourceRegions().stream()
                .filter(e -> "VOIDED".equals(e.contextType())
                        && "BODY".equals(e.sourcePart())
                        && "STRONG_CONTEXT_VOIDED_EXCLUDED".equals(e.reason()))
                .toList();
        assertThat(excluded).hasSize(1);
        assertThat(excluded.get(0).blockId()).isEqualTo(tableRowBlock.blockId());
    }

    // ════════════════════════════════════════════════════════════════════
    // Legacy fixture tests (restored from baseline HEAD 9594344 with scope report compatibility)
    // ════════════════════════════════════════════════════════════════════

    @Test void parsesAllExpectedFixturesIntoStructuredArtifacts() throws IOException {
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

    @Test void preservesAggregateCoverageForTask016ParserSpikeGate() throws IOException {
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

    @Test void tableRowsPreserveStableCellIndexesAndJoinedTextRanges() throws IOException {
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
        try (var stream = Files.list(FIXTURE_ROOT.resolve("expected"))) {
            return stream
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
    }

    // ────────── Legacy test helpers ──────────

    private String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }

    private String canonicalize(String text) {
        return normalize(text).replaceAll("[\\p{Punct}\\p{IsPunctuation}（）【】《》、]", "");
    }
}
