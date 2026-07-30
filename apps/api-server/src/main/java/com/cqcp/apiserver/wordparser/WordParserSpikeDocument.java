package com.cqcp.apiserver.wordparser;

import java.util.List;

public record WordParserSpikeDocument(
        Metadata metadata,
        List<DocumentBlock> blocks,
        List<TableBlock> tables,
        List<FormControlBlock> controls,
        ParseQualityReport parseQualityReport,
        ScopeCoverageReport scopeCoverageReport) {

    public WordParserSpikeDocument(
            Metadata metadata,
            List<DocumentBlock> blocks,
            List<TableBlock> tables,
            List<FormControlBlock> controls,
            ParseQualityReport parseQualityReport) {
        this(metadata, blocks, tables, controls, parseQualityReport, ScopeCoverageReport.unverified());
    }

    public record Metadata(String sourceFileId, String sourceFileName) {
    }

    public record DocumentBlock(
            String blockId,
            BlockType type,
            String text,
            String normalizedText,
            List<String> sectionPath,
            RegionType regionType,
            ContextType contextType,
            SourceOrigin sourceOrigin,
            SourceExtractionMode sourceExtractionMode,
            String sourceFileId,
            String tableId,
            Integer rowIndex,
            List<TableCellSpan> tableCells,
            ConfidenceLevel blockConfidence,
            PreviewAnchorLevel previewAnchorLevel) {

        public DocumentBlock {
            tableCells = tableCells == null ? List.of() : List.copyOf(tableCells);
        }
    }

    public record TableBlock(
            String tableId,
            String sourceFileId,
            List<String> sectionPath,
            RegionType regionType,
            List<TableRowBlock> rows,
            boolean hasMergedCells,
            boolean hasNestedTable,
            ConfidenceLevel tableConfidence,
            List<String> warnings) {
    }

    public record TableRowBlock(int rowIndex, List<String> cells, String normalizedText) {
    }

    public record TableCellSpan(
            int cellIndex,
            String text,
            int startOffset,
            int endOffset) {
    }

    public record FormControlBlock(
            String blockId,
            ControlType controlType,
            String label,
            String value,
            String nearbyText,
            String tableId,
            Integer rowIndex,
            List<String> sectionPath,
            ConfidenceLevel controlConfidence,
            List<String> warnings) {
    }

    public record ParseQualityReport(
            String fileType,
            String parser,
            String language,
            int textLength,
            int blockCount,
            int headingCount,
            int tableCount,
            int formControlCount,
            int appendixRegionCount,
            boolean tocDetected,
            ParseStatus parseStatus,
            String confidenceSummary,
            int lowConfidenceRegionCount,
            int lowConfidenceBlockCount,
            int lowConfidenceTableCount,
            List<String> warnings) {
    }

    public record ScopeCoverageReport(
            boolean verified,
            List<String> handledStrongContextTypes,
            List<ExcludedSourceRegion> excludedSourceRegions,
            List<String> unresolvedSignals) {

        public static ScopeCoverageReport unverified() {
            return new ScopeCoverageReport(false, List.of(), List.of(), List.of());
        }

        public ScopeCoverageReport {
            handledStrongContextTypes = List.copyOf(handledStrongContextTypes);
            excludedSourceRegions = List.copyOf(excludedSourceRegions);
            unresolvedSignals = List.copyOf(unresolvedSignals);
        }
    }

    public record ExcludedSourceRegion(
            String contextType,
            String blockId,
            String sourcePart,
            String reason) {
    }

    public enum BlockType {
        HEADING,
        PARAGRAPH,
        TABLE_ROW,
        APPENDIX_TITLE,
        TOC_ITEM
    }

    public enum RegionType {
        BODY,
        APPENDIX
    }

    public enum ContextType {
        NORMAL,
        TOC
    }

    public enum SourceOrigin {
        NATIVE_WORD
    }

    public enum SourceExtractionMode {
        STRUCTURED
    }

    public enum ConfidenceLevel {
        HIGH,
        MEDIUM,
        LOW
    }

    public enum PreviewAnchorLevel {
        BLOCK_LEVEL,
        TABLE_CELL
    }

    public enum ControlType {
        SYMBOL_CHECK
    }

    public enum ParseStatus {
        GOOD,
        PARTIAL,
        LOW_CONFIDENCE,
        FAILED
    }
}
