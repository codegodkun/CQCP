package com.cqcp.apiserver.reviewengine;

import java.util.List;

final class ReviewDocumentModels {

    private ReviewDocumentModels() {
        throw new AssertionError("no instances");
    }

    record DocumentMetadata(
            String taskId,
            String executionId,
            String contractName,
            String parserVersion,
            String originalFileName,
            String documentReference,
            long sizeBytes,
            String sha256) {
    }

    record PreviewCell(
            String previewElementRef,
            int cellIndex,
            String text) {
    }

    record PreviewBlock(
            String blockId,
            String previewElementRef,
            String type,
            String text,
            List<String> sectionPath,
            String tableId,
            Integer rowIndex,
            List<PreviewCell> cells) {

        PreviewBlock {
            sectionPath = sectionPath == null ? List.of() : List.copyOf(sectionPath);
            cells = cells == null ? List.of() : List.copyOf(cells);
        }
    }

    record DocumentPreview(
            String taskId,
            String executionId,
            String contractName,
            String originalFileName,
            String parserVersion,
            String sha256,
            List<PreviewBlock> blocks) {

        DocumentPreview {
            blocks = List.copyOf(blocks);
        }
    }

    record VerifiedDocument(
            String originalFileName,
            String sha256,
            long sizeBytes,
            byte[] content) {

        VerifiedDocument {
            content = content.clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }

    static final class DocumentNotFoundException extends RuntimeException {
        DocumentNotFoundException() {
            super("未找到该 task/execution 的合同文档");
        }
    }

    static final class DocumentConflictException extends RuntimeException {
        private final String code;

        DocumentConflictException(String code, String message) {
            super(message);
            this.code = code;
        }

        String code() {
            return code;
        }
    }

    static final class DocumentUnavailableException extends RuntimeException {
        DocumentUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
