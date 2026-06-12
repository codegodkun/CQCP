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
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

@Component
public class DocxWordParserSpike {

    private static final Pattern CHINESE_HEADING =
            Pattern.compile("^第[一二三四五六七八九十0-9]+[章节条部款项].*");
    private static final Pattern APPENDIX_HEADING =
            Pattern.compile("^(附件|附录|补充条款).*");
    private static final Pattern CHECK_SYMBOL =
            Pattern.compile("[☑☒✓✔√□■]");

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
                blocks.add(new WordParserSpikeDocument.DocumentBlock(
                        "block-" + blockSequence.getAndIncrement(),
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

                    var rowText = normalizeWhitespace(String.join(" | ", cells));
                    rowBlocks.add(new WordParserSpikeDocument.TableRowBlock(rowIndex, List.copyOf(cells), normalizeForSearch(rowText)));
                    if (!rowText.isBlank()) {
                        blocks.add(new WordParserSpikeDocument.DocumentBlock(
                                "block-" + blockSequence.getAndIncrement(),
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

        return new WordParserSpikeDocument(
                metadata,
                List.copyOf(blocks),
                List.copyOf(tables),
                List.copyOf(controls),
                report);
    }

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

    private String normalizeWhitespace(String text) {
        return text == null ? "" : text.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    private String normalizeForSearch(String text) {
        return Normalizer.normalize(normalizeWhitespace(text), Normalizer.Form.NFKC);
    }
}
