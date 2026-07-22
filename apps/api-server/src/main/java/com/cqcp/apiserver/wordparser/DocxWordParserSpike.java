package com.cqcp.apiserver.wordparser;

import static com.cqcp.apiserver.wordparser.WordParserSpikeDocument.BlockType.APPENDIX_TITLE;
import static com.cqcp.apiserver.wordparser.WordParserSpikeDocument.BlockType.HEADING;
import static com.cqcp.apiserver.wordparser.WordParserSpikeDocument.BlockType.PARAGRAPH;
import static com.cqcp.apiserver.wordparser.WordParserSpikeDocument.BlockType.TABLE_ROW;
import static com.cqcp.apiserver.wordparser.WordParserSpikeDocument.BlockType.TOC_ITEM;
import static com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ConfidenceLevel.HIGH;
import static com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ConfidenceLevel.MEDIUM;
import static com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ContextType.NORMAL;
import static com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ContextType.TOC;
import static com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ControlType.SYMBOL_CHECK;
import static com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ParseStatus.FAILED;
import static com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ParseStatus.GOOD;
import static com.cqcp.apiserver.wordparser.WordParserSpikeDocument.ParseStatus.PARTIAL;
import static com.cqcp.apiserver.wordparser.WordParserSpikeDocument.PreviewAnchorLevel.BLOCK_LEVEL;
import static com.cqcp.apiserver.wordparser.WordParserSpikeDocument.PreviewAnchorLevel.TABLE_CELL;
import static com.cqcp.apiserver.wordparser.WordParserSpikeDocument.RegionType.APPENDIX;
import static com.cqcp.apiserver.wordparser.WordParserSpikeDocument.RegionType.BODY;
import static com.cqcp.apiserver.wordparser.WordParserSpikeDocument.SourceExtractionMode.STRUCTURED;
import static com.cqcp.apiserver.wordparser.WordParserSpikeDocument.SourceOrigin.NATIVE_WORD;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRunTrackChange;
import org.springframework.stereotype.Component;

@Component
public class DocxWordParserSpike {

    private static final Pattern CHINESE_HEADING =
            Pattern.compile("^第[一二三四五六七八九十0-9]+[章节条部款项].*");
    private static final Pattern APPENDIX_HEADING =
            Pattern.compile("^(附件|附录|补充条款).*");
    private static final Pattern CHECK_SYMBOL =
            Pattern.compile("[☑☒✓✔√□■]");

    // ────────── Scanner exception seam (package-private, for testing) ──────────

    @FunctionalInterface
    interface ScannerExceptionSeam {
        void check(String contextType) throws Exception;
        static ScannerExceptionSeam NO_OP = ctx -> {};
    }

    private ScannerExceptionSeam scannerExceptionSeam = ScannerExceptionSeam.NO_OP;

    void setScannerExceptionSeam(ScannerExceptionSeam seam) {
        this.scannerExceptionSeam = seam != null ? seam : ScannerExceptionSeam.NO_OP;
    }

    // ────────── Public parse API ──────────

    public WordParserSpikeDocument parse(Path docxPath) throws IOException {
        try (InputStream inputStream = Files.newInputStream(docxPath);
             XWPFDocument document = new XWPFDocument(inputStream)) {
            return parseDocument(docxPath, document);
        }
    }

    private WordParserSpikeDocument parseDocument(Path docxPath, XWPFDocument document) {
        var blocks = new ArrayList<WordParserSpikeDocument.DocumentBlock>();
        var tables = new ArrayList<WordParserSpikeDocument.TableBlock>();
        var controls = new ArrayList<WordParserSpikeDocument.FormControlBlock>();
        var warnings = new ArrayList<String>();
        List<String> sectionPath = new ArrayList<>();
        var inAppendix = false;
        var blockSequence = new AtomicInteger(1);
        var tableSequence = new AtomicInteger(1);
        var sourceFileId = docxPath.getFileName().toString();

        // Identity-based lineage maps for scope coverage report
        var paragraphToBlockId = new IdentityHashMap<XWPFParagraph, String>();
        var tableRowToBlockId = new IdentityHashMap<XWPFTableRow, String>();

        for (IBodyElement bodyElement : document.getBodyElements()) {
            if (bodyElement instanceof XWPFParagraph paragraph) {
                var paragraphText = normalizeWhitespace(paragraph.getText());
                if (paragraphText.isBlank()) {
                    continue;
                }

                var blockType = resolveParagraphType(paragraph, paragraphText);
                if (blockType == HEADING || blockType == APPENDIX_TITLE) {
                    sectionPath = nextSectionPath(sectionPath, paragraphText);
                    inAppendix = blockType == APPENDIX_TITLE || APPENDIX_HEADING.matcher(paragraphText).matches();
                }

                var currentSectionPath = List.copyOf(sectionPath);
                var regionType = inAppendix ? APPENDIX : BODY;
                var blockId = "block-" + blockSequence.getAndIncrement();
                paragraphToBlockId.put(paragraph, blockId);
                blocks.add(new WordParserSpikeDocument.DocumentBlock(
                        blockId,
                        blockType,
                        paragraphText,
                        normalizeForSearch(paragraphText),
                        currentSectionPath,
                        regionType,
                        blockType == TOC_ITEM ? TOC : NORMAL,
                        NATIVE_WORD,
                        STRUCTURED,
                        sourceFileId,
                        null,
                        null,
                        List.of(),
                        confidenceForParagraph(blockType, paragraphText),
                        BLOCK_LEVEL));

                extractControlFromText(paragraphText, null, null, currentSectionPath, controls, blockSequence);
            } else if (bodyElement instanceof XWPFTable table) {
                var tableId = "table-" + tableSequence.getAndIncrement();
                var currentSectionPath = List.copyOf(sectionPath);
                var regionType = inAppendix ? APPENDIX : BODY;
                var rowBlocks = new ArrayList<WordParserSpikeDocument.TableRowBlock>();
                var hasMergedCells = false;
                var hasNestedTable = false;
                var rowIndex = 0;

                for (XWPFTableRow row : table.getRows()) {
                    var cells = new ArrayList<String>();
                    for (XWPFTableCell cell : row.getTableCells()) {
                        var cellText = normalizeWhitespace(cell.getText());
                        cells.add(cellText);
                        hasNestedTable = hasNestedTable || !cell.getTables().isEmpty();
                        extractControlFromText(cellText, tableId, rowIndex, currentSectionPath, controls, blockSequence);
                    }

                    if (cells.size() != row.getTableCells().size()) {
                        hasMergedCells = true;
                    }

                    var rawRowText = String.join(" | ", cells);
                    var leadingWhitespace = leadingWhitespaceLength(rawRowText);
                    var rowText = normalizeWhitespace(rawRowText);
                    var tableCells = tableCellSpans(cells, leadingWhitespace, rowText.length());
                    rowBlocks.add(new WordParserSpikeDocument.TableRowBlock(rowIndex, List.copyOf(cells), normalizeForSearch(rowText)));
                    if (!rowText.isBlank()) {
                        var blockId = "block-" + blockSequence.getAndIncrement();
                        tableRowToBlockId.put(row, blockId);
                        blocks.add(new WordParserSpikeDocument.DocumentBlock(
                                blockId,
                                TABLE_ROW,
                                rowText,
                                normalizeForSearch(rowText),
                                currentSectionPath,
                                regionType,
                                NORMAL,
                                NATIVE_WORD,
                                STRUCTURED,
                                sourceFileId,
                                tableId,
                                rowIndex,
                                tableCells,
                                HIGH,
                                TABLE_CELL));
                    }
                    rowIndex++;
                }

                tables.add(new WordParserSpikeDocument.TableBlock(
                        tableId,
                        sourceFileId,
                        currentSectionPath,
                        regionType,
                        List.copyOf(rowBlocks),
                        hasMergedCells,
                        hasNestedTable,
                        HIGH,
                        List.of()));
            }
        }

        if (tables.isEmpty()) {
            warnings.add("NO_TABLE_DETECTED");
        }
        if (controls.isEmpty()) {
            warnings.add("NO_FORM_CONTROL_DETECTED");
        }

        var headingCount = blocks.stream().filter(block -> block.type() == HEADING || block.type() == APPENDIX_TITLE).count();
        var appendixRegionCount = (int) blocks.stream().filter(block -> block.regionType() == APPENDIX).count();
        var parseStatus = resolveParseStatus(blocks, tables);

        var metadata = new WordParserSpikeDocument.Metadata(sourceFileId, sourceFileId);
        var report = new WordParserSpikeDocument.ParseQualityReport(
                "DOCX",
                "Apache POI XWPF",
                Locale.SIMPLIFIED_CHINESE.toLanguageTag(),
                blocks.stream().mapToInt(block -> block.text().length()).sum(),
                blocks.size(),
                (int) headingCount,
                tables.size(),
                controls.size(),
                appendixRegionCount,
                blocks.stream().anyMatch(block -> block.contextType() == TOC),
                parseStatus,
                parseStatus == GOOD ? "HIGH" : "MEDIUM",
                0,
                0,
                0,
                List.copyOf(warnings));

        var scopeReport = buildScopeCoverageReport(document, blocks, paragraphToBlockId, tableRowToBlockId);

        return new WordParserSpikeDocument(
                metadata,
                List.copyOf(blocks),
                List.copyOf(tables),
                List.copyOf(controls),
                report,
                scopeReport);
    }

    // ────────── Scope coverage report ──────────

    private WordParserSpikeDocument.ScopeCoverageReport buildScopeCoverageReport(
            XWPFDocument document,
            List<WordParserSpikeDocument.DocumentBlock> blocks,
            IdentityHashMap<XWPFParagraph, String> paragraphToBlockId,
            IdentityHashMap<XWPFTableRow, String> tableRowToBlockId) {
        var handledContexts = new LinkedHashSet<String>();
        var excludedRegions = new ArrayList<WordParserSpikeDocument.ExcludedSourceRegion>();
        var unresolved = new ArrayList<String>();

        // TOC scanner: success if blocks with contextType=TOC are detected (via existing block ledger)
        try {
            scannerExceptionSeam.check("TOC");
            boolean hasTocBlocks = blocks.stream()
                    .anyMatch(b -> b.contextType() == TOC);
            // handled = scanner executed successfully, not whether document has TOC
            handledContexts.add("TOC");
            if (hasTocBlocks) {
                // TOC blocks are excluded via ledger contextType, not via ExcludedSourceRegion
            }
        } catch (Exception e) {
            unresolved.add("SCOPE_SCAN_TOC_FAILED");
        }

        // HEADER_FOOTER: enumerate all headers and footers, including deletion/strike/dstrike scanning
        try {
            scannerExceptionSeam.check("HEADER_FOOTER");
            boolean hasHeaderFooterContent = false;
            for (XWPFHeader header : document.getHeaderList()) {
                int headerParagraphCount = header.getParagraphs().size();
                int headerTableCount = header.getTables().size();
                if (headerParagraphCount > 0 || headerTableCount > 0) {
                    hasHeaderFooterContent = true;
                    excludedRegions.add(new WordParserSpikeDocument.ExcludedSourceRegion(
                            "HEADER_FOOTER", null, "HEADER",
                            "STRONG_CONTEXT_HEADER_FOOTER_EXCLUDED"));
                }
                // Scan header paragraphs for deletion and voided, recording mutation regions
                for (XWPFParagraph hp : header.getParagraphs()) {
                    scanHeaderFooterParagraphForDeletion(hp, excludedRegions, "HEADER");
                    scanHeaderFooterParagraphForVoided(hp, excludedRegions, "HEADER");
                }
                // Scan header tables for deletion and voided
                for (XWPFTable ht : header.getTables()) {
                    for (XWPFTableRow htr : ht.getRows()) {
                        for (XWPFTableCell htc : htr.getTableCells()) {
                            for (XWPFParagraph htp : htc.getParagraphs()) {
                                scanHeaderFooterParagraphForDeletion(htp, excludedRegions, "HEADER");
                                scanHeaderFooterParagraphForVoided(htp, excludedRegions, "HEADER");
                            }
                        }
                    }
                }
            }
            for (XWPFFooter footer : document.getFooterList()) {
                int footerParagraphCount = footer.getParagraphs().size();
                int footerTableCount = footer.getTables().size();
                if (footerParagraphCount > 0 || footerTableCount > 0) {
                    hasHeaderFooterContent = true;
                    excludedRegions.add(new WordParserSpikeDocument.ExcludedSourceRegion(
                            "HEADER_FOOTER", null, "FOOTER",
                            "STRONG_CONTEXT_HEADER_FOOTER_EXCLUDED"));
                }
                // Scan footer paragraphs for deletion and voided, recording mutation regions
                for (XWPFParagraph fp : footer.getParagraphs()) {
                    scanHeaderFooterParagraphForDeletion(fp, excludedRegions, "FOOTER");
                    scanHeaderFooterParagraphForVoided(fp, excludedRegions, "FOOTER");
                }
                // Scan footer tables for deletion and voided
                for (XWPFTable ft : footer.getTables()) {
                    for (XWPFTableRow ftr : ft.getRows()) {
                        for (XWPFTableCell ftc : ftr.getTableCells()) {
                            for (XWPFParagraph ftp : ftc.getParagraphs()) {
                                scanHeaderFooterParagraphForDeletion(ftp, excludedRegions, "FOOTER");
                                scanHeaderFooterParagraphForVoided(ftp, excludedRegions, "FOOTER");
                            }
                        }
                    }
                }
            }
            handledContexts.add("HEADER_FOOTER");
        } catch (Exception e) {
            unresolved.add("SCOPE_SCAN_HEADER_FOOTER_FAILED");
        }

        // DELETED: scan each body paragraph and table row for OOXML tracked-deletion runs
        try {
            scannerExceptionSeam.check("DELETED");
            boolean hasDeletion = false;
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    var result = scanParagraphForDeletion(paragraph, paragraphToBlockId, excludedRegions);
                    if (result.scanned) hasDeletion = true;
                    if (result.unresolvedMsg != null) unresolved.add(result.unresolvedMsg);
                }
                if (element instanceof XWPFTable table) {
                    for (XWPFTableRow row : table.getRows()) {
                        var result = scanTableRowForDeletion(row, tableRowToBlockId, excludedRegions);
                        if (result.scanned) hasDeletion = true;
                        if (result.unresolvedMsg != null) unresolved.add(result.unresolvedMsg);
                    }
                }
            }
            // handled = scanner executed successfully
            handledContexts.add("DELETED");
        } catch (Exception e) {
            unresolved.add("SCOPE_SCAN_DELETED_FAILED");
        }

        // VOIDED: scan each body paragraph and table row for strike/double-strike
        try {
            scannerExceptionSeam.check("VOIDED");
            boolean hasVoidedContent = false;
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    var result = scanParagraphForVoided(paragraph, paragraphToBlockId, excludedRegions);
                    if (result.scanned) hasVoidedContent = true;
                    if (result.unresolvedMsg != null) unresolved.add(result.unresolvedMsg);
                }
                if (element instanceof XWPFTable table) {
                    for (XWPFTableRow row : table.getRows()) {
                        var result = scanTableRowForVoided(row, tableRowToBlockId, excludedRegions);
                        if (result.scanned) hasVoidedContent = true;
                        if (result.unresolvedMsg != null) unresolved.add(result.unresolvedMsg);
                    }
                }
            }
            // handled = scanner executed successfully
            handledContexts.add("VOIDED");
        } catch (Exception e) {
            unresolved.add("SCOPE_SCAN_VOIDED_FAILED");
        }

        // verified = all four scanners handled AND no unresolved signals
        boolean verified = handledContexts.containsAll(List.of("TOC", "HEADER_FOOTER", "DELETED", "VOIDED"))
                && unresolved.isEmpty();

        return new WordParserSpikeDocument.ScopeCoverageReport(
                verified,
                List.copyOf(handledContexts),
                List.copyOf(excludedRegions),
                List.copyOf(unresolved));
    }

    // ────────── Paragraph deletion scanner ──────────

    private record ScanResult(boolean scanned, String unresolvedMsg) {
        static ScanResult DONE = new ScanResult(true, null);
        static ScanResult SKIP = new ScanResult(false, null);
        static ScanResult unresolved(String msg) { return new ScanResult(true, msg); }
    }

    private ScanResult scanParagraphForDeletion(
            XWPFParagraph paragraph,
            IdentityHashMap<XWPFParagraph, String> paragraphToBlockId,
            List<WordParserSpikeDocument.ExcludedSourceRegion> excludedRegions) {
        var ctp = paragraph.getCTP();
        var delList = ctp.getDelList();
        if (delList == null || delList.isEmpty()) {
            return ScanResult.SKIP;
        }

        // Check if delList has actual delText content
        boolean hasDelText = false;
        for (CTRunTrackChange del : delList) {
            for (CTR delR : del.getRArray()) {
                if (delR.getDelTextList() != null && !delR.getDelTextList().isEmpty()) {
                    boolean hasNonEmpty = false;
                    for (var dt : delR.getDelTextList()) {
                        if (dt.getStringValue() != null && !dt.getStringValue().isBlank()) {
                            hasNonEmpty = true;
                            break;
                        }
                    }
                    if (hasNonEmpty) { hasDelText = true; break; }
                }
            }
            if (hasDelText) break;
        }
        if (!hasDelText) return ScanResult.SKIP;

        // Check if paragraph has non-empty live text (runs outside w:del)
        boolean hasNonEmptyLive = false;
        for (var run : paragraph.getRuns()) {
            var rt = run.getText(0);
            if (rt != null && !rt.isBlank()) {
                hasNonEmptyLive = true;
                break;
            }
        }

        if (hasNonEmptyLive) {
            // Mixed deleted + live content → unresolved
            return ScanResult.unresolved("SCOPE_DELETED_MIXED_LIVE_UNRESOLVED");
        }

        // Fully deleted: record ExcludedSourceRegion with blockId
        var blockId = paragraphToBlockId.get(paragraph);
        excludedRegions.add(new WordParserSpikeDocument.ExcludedSourceRegion(
                "DELETED", blockId, "BODY",
                "STRONG_CONTEXT_DELETED_EXCLUDED"));
        return ScanResult.DONE;
    }

    private ScanResult scanTableRowForDeletion(
            XWPFTableRow row,
            IdentityHashMap<XWPFTableRow, String> tableRowToBlockId,
            List<WordParserSpikeDocument.ExcludedSourceRegion> excludedRegions) {
        // Check each cell for deletion
        boolean hasAnyDel = false;
        boolean hasAnyLiveCell = false;
        boolean hasMixedCell = false;

        for (XWPFTableCell cell : row.getTableCells()) {
            boolean cellHasDel = false;
            boolean cellHasNonEmptyLive = false;

            for (XWPFParagraph cp : cell.getParagraphs()) {
                var ctp = cp.getCTP();
                if (ctp.getDelList() != null && !ctp.getDelList().isEmpty()) {
                    for (CTRunTrackChange del : ctp.getDelList()) {
                        for (CTR delR : del.getRArray()) {
                            if (delR.getDelTextList() != null && !delR.getDelTextList().isEmpty()) {
                                boolean hasNonEmptyDel = false;
                                for (var dt : delR.getDelTextList()) {
                                    if (dt.getStringValue() != null && !dt.getStringValue().isBlank()) {
                                        hasNonEmptyDel = true;
                                        break;
                                    }
                                }
                                if (hasNonEmptyDel) { cellHasDel = true; break; }
                            }
                        }
                        if (cellHasDel) break;
                    }
                }
                // Check for non-empty live content in this cell
                if (hasReviewableLiveContent(cp)) {
                    cellHasNonEmptyLive = true;
                }
            }

            if (cellHasDel) hasAnyDel = true;
            if (cellHasNonEmptyLive) hasAnyLiveCell = true;
            if (cellHasDel && cellHasNonEmptyLive) hasMixedCell = true;
        }

        if (!hasAnyDel) return ScanResult.SKIP;
        if (hasMixedCell || hasAnyLiveCell) {
            return ScanResult.unresolved("SCOPE_DELETED_MIXED_LIVE_UNRESOLVED");
        }

        // All cells have only deleted content → fully excluded
        var blockId = tableRowToBlockId.get(row);
        excludedRegions.add(new WordParserSpikeDocument.ExcludedSourceRegion(
                "DELETED", blockId, "BODY",
                "STRONG_CONTEXT_DELETED_EXCLUDED"));
        return ScanResult.DONE;
    }

    // ────────── Paragraph voided scanner ──────────

    private ScanResult scanParagraphForVoided(
            XWPFParagraph paragraph,
            IdentityHashMap<XWPFParagraph, String> paragraphToBlockId,
            List<WordParserSpikeDocument.ExcludedSourceRegion> excludedRegions) {
        var runs = paragraph.getRuns();
        if (runs == null || runs.isEmpty()) {
            return ScanResult.SKIP;
        }

        boolean hasStruckNonEmpty = false;
        boolean hasUnstruckNonEmpty = false;

        for (var run : runs) {
            var rt = run.getText(0);
            boolean isNonEmpty = rt != null && !rt.isBlank();
            if (!isNonEmpty) continue;

            boolean isStruck = run.isStrikeThrough() || hasDstrike(run);
            if (isStruck) {
                hasStruckNonEmpty = true;
            } else {
                hasUnstruckNonEmpty = true;
            }
        }

        if (!hasStruckNonEmpty) return ScanResult.SKIP;

        if (hasStruckNonEmpty && hasUnstruckNonEmpty) {
            // Mixed struck + unstruck → unresolved
            return ScanResult.unresolved("SCOPE_VOIDED_MIXED_LIVE_UNRESOLVED");
        }

        // Fully voided
        var blockId = paragraphToBlockId.get(paragraph);
        excludedRegions.add(new WordParserSpikeDocument.ExcludedSourceRegion(
                "VOIDED", blockId, "BODY",
                "STRONG_CONTEXT_VOIDED_EXCLUDED"));
        return ScanResult.DONE;
    }

    private ScanResult scanTableRowForVoided(
            XWPFTableRow row,
            IdentityHashMap<XWPFTableRow, String> tableRowToBlockId,
            List<WordParserSpikeDocument.ExcludedSourceRegion> excludedRegions) {
        boolean hasAnyStruck = false;
        boolean hasAnyUnstruckNonEmpty = false;
        boolean hasMixedCell = false;

        for (XWPFTableCell cell : row.getTableCells()) {
            boolean cellStruck = false;
            boolean cellUnstruck = false;

            for (XWPFParagraph cp : cell.getParagraphs()) {
                for (var run : cp.getRuns()) {
                    var rt = run.getText(0);
                    boolean isNonEmpty = rt != null && !rt.isBlank();
                    if (!isNonEmpty) continue;

                    boolean isStruck = run.isStrikeThrough() || hasDstrike(run);
                    if (isStruck) {
                        cellStruck = true;
                    } else {
                        cellUnstruck = true;
                    }
                }
            }

            if (cellStruck) hasAnyStruck = true;
            if (cellUnstruck) hasAnyUnstruckNonEmpty = true;
            if (cellStruck && cellUnstruck) hasMixedCell = true;
        }

        if (!hasAnyStruck) return ScanResult.SKIP;
        if (hasMixedCell) {
            return ScanResult.unresolved("SCOPE_VOIDED_MIXED_LIVE_UNRESOLVED");
        }
        if (hasAnyUnstruckNonEmpty) {
            return ScanResult.unresolved("SCOPE_VOIDED_MIXED_LIVE_UNRESOLVED");
        }

        // All non-empty content is struck → fully excluded
        var blockId = tableRowToBlockId.get(row);
        excludedRegions.add(new WordParserSpikeDocument.ExcludedSourceRegion(
                "VOIDED", blockId, "BODY",
                "STRONG_CONTEXT_VOIDED_EXCLUDED"));
        return ScanResult.DONE;
    }

    /**
     * Scans a header/footer paragraph for tracked deletion (OOXML w:del).
     * Records a DELETED mutation region when tracked deletion found,
     * in addition to the part-level HEADER_FOOTER exclusion.
     */
    private void scanHeaderFooterParagraphForDeletion(
            XWPFParagraph paragraph,
            List<WordParserSpikeDocument.ExcludedSourceRegion> excludedRegions,
            String sourcePart) {
        var ctp = paragraph.getCTP();
        var delList = ctp.getDelList();
        if (delList != null) {
            for (CTRunTrackChange del : delList) {
                for (CTR delR : del.getRArray()) {
                    if (delR.getDelTextList() != null && !delR.getDelTextList().isEmpty()) {
                        for (var dt : delR.getDelTextList()) {
                            if (dt.getStringValue() != null && !dt.getStringValue().isBlank()) {
                                // Tracked deletion found in header/footer → record DELETED mutation region
                                excludedRegions.add(new WordParserSpikeDocument.ExcludedSourceRegion(
                                        "DELETED", null, sourcePart,
                                        "STRONG_CONTEXT_DELETED_EXCLUDED"));
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Scans a header/footer paragraph for strike/double-strike.
     * Records a VOIDED mutation region when found,
     * in addition to the part-level HEADER_FOOTER exclusion.
     */
    private void scanHeaderFooterParagraphForVoided(
            XWPFParagraph paragraph,
            List<WordParserSpikeDocument.ExcludedSourceRegion> excludedRegions,
            String sourcePart) {
        for (var run : paragraph.getRuns()) {
            if (run.isStrikeThrough() || hasDstrike(run)) {
                var rt = run.getText(0);
                if (rt != null && !rt.isBlank()) {
                    // Strike/dstrike with non-empty text in header/footer → record VOIDED mutation region
                    excludedRegions.add(new WordParserSpikeDocument.ExcludedSourceRegion(
                            "VOIDED", null, sourcePart,
                            "STRONG_CONTEXT_VOIDED_EXCLUDED"));
                    return;
                }
            }
        }
    }

    // ────────── Non-empty reviewable content helpers ──────────

    /**
     * Checks whether a paragraph has non-empty live (non-deleted, non-voided) reviewable content.
     * Used for mixed-content detection in tracked-deletion scanning.
     */
    private static boolean hasReviewableLiveContent(XWPFParagraph paragraph) {
        for (var run : paragraph.getRuns()) {
            var rt = run.getText(0);
            if (rt != null && !rt.isBlank()) {
                // We consider this "live" even if struck (struck is handled by voided scanner)
                return true;
            }
        }
        return false;
    }

    // ────────── dstrike helper ──────────

    private static boolean hasDstrike(XWPFRun run) {
        // Let exceptions propagate to caller (scanner catch block)
        var ctr = run.getCTR();
        if (ctr == null || ctr.getRPr() == null) {
            return false;
        }
        var rprDom = ctr.getRPr().getDomNode();
        var childNodes = rprDom.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            var node = childNodes.item(i);
            if ("dstrike".equals(node.getLocalName())) {
                return true;
            }
        }
        return false;
    }

    // ────────── The rest unchanged from original ──────────

    private void extractControlFromText(
            String text,
            String tableId,
            Integer rowIndex,
            List<String> sectionPath,
            List<WordParserSpikeDocument.FormControlBlock> controls,
            AtomicInteger blockSequence) {
        if (!CHECK_SYMBOL.matcher(text).find()) {
            return;
        }
        controls.add(new WordParserSpikeDocument.FormControlBlock(
                "control-" + blockSequence.getAndIncrement(),
                SYMBOL_CHECK,
                text,
                extractCheckSymbol(text),
                text,
                tableId,
                rowIndex,
                sectionPath,
                MEDIUM,
                List.of()));
    }

    private WordParserSpikeDocument.BlockType resolveParagraphType(XWPFParagraph paragraph, String text) {
        var style = paragraph.getStyle();
        var styleName = paragraph.getStyleID();
        var styleToken = (style == null ? "" : style) + " " + (styleName == null ? "" : styleName);
        var lowercaseStyle = styleToken.toLowerCase(Locale.ROOT);

        if (APPENDIX_HEADING.matcher(text).matches()) {
            return APPENDIX_TITLE;
        }
        if (lowercaseStyle.contains("toc")) {
            return TOC_ITEM;
        }
        if (lowercaseStyle.contains("heading") || CHINESE_HEADING.matcher(text).matches()) {
            return HEADING;
        }
        return PARAGRAPH;
    }

    private WordParserSpikeDocument.ConfidenceLevel confidenceForParagraph(
            WordParserSpikeDocument.BlockType blockType,
            String text) {
        if (blockType == TOC_ITEM) {
            return MEDIUM;
        }
        return text.length() >= 4 ? HIGH : MEDIUM;
    }

    private List<String> nextSectionPath(List<String> currentSectionPath, String headingText) {
        var next = new ArrayList<>(currentSectionPath);
        if (!next.isEmpty()) {
            next.removeLast();
        }
        next.add(headingText);
        return next;
    }

    private WordParserSpikeDocument.ParseStatus resolveParseStatus(
            List<WordParserSpikeDocument.DocumentBlock> blocks,
            List<WordParserSpikeDocument.TableBlock> tables) {
        if (blocks.isEmpty()) {
            return FAILED;
        }
        if (tables.isEmpty()) {
            return PARTIAL;
        }
        return GOOD;
    }

    private String extractCheckSymbol(String text) {
        var matcher = CHECK_SYMBOL.matcher(text);
        return matcher.find() ? matcher.group() : "";
    }

    private List<WordParserSpikeDocument.TableCellSpan> tableCellSpans(
            List<String> cells,
            int leadingWhitespace,
            int rowTextLength) {
        var spans = new ArrayList<WordParserSpikeDocument.TableCellSpan>();
        var offset = 0;
        for (var cellIndex = 0; cellIndex < cells.size(); cellIndex++) {
            var cellText = cells.get(cellIndex);
            var rawStartOffset = offset;
            var rawEndOffset = rawStartOffset + cellText.length();
            var startOffset = Math.max(0, Math.min(rowTextLength, rawStartOffset - leadingWhitespace));
            var endOffset = Math.max(startOffset, Math.min(rowTextLength, rawEndOffset - leadingWhitespace));
            spans.add(new WordParserSpikeDocument.TableCellSpan(
                    cellIndex,
                    rowTextLength == 0 ? "" : cellText,
                    startOffset,
                    endOffset));
            offset = rawEndOffset + (cellIndex + 1 < cells.size() ? " | ".length() : 0);
        }
        return List.copyOf(spans);
    }

    private int leadingWhitespaceLength(String text) {
        var index = 0;
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
        return index;
    }

    private String normalizeWhitespace(String text) {
        return text == null ? "" : text.replace(' ', ' ').replaceAll("\\s+", " ").trim();
    }

    private String normalizeForSearch(String text) {
        return Normalizer.normalize(normalizeWhitespace(text), Normalizer.Form.NFKC);
    }
}
