package com.cqcp.apiserver.reviewengine;

import static com.cqcp.apiserver.reviewengine.ReviewDocumentModels.*;

import com.cqcp.apiserver.wordparser.DocxWordParserSpike;
import com.cqcp.apiserver.wordparser.WordParserSpikeDocument;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
final class ReviewDocumentService {

    private static final Pattern SHA_256 = Pattern.compile("^[a-f0-9]{64}$");
    private static final int MAX_DOCUMENT_BYTES = 25 * 1024 * 1024;

    private final ReviewDocumentRepository repository;
    private final LocalReviewDocumentStore documentStore;
    private final DocxWordParserSpike parser;

    ReviewDocumentService(
            ReviewDocumentRepository repository,
            LocalReviewDocumentStore documentStore,
            DocxWordParserSpike parser) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.documentStore = Objects.requireNonNull(documentStore, "documentStore");
        this.parser = Objects.requireNonNull(parser, "parser");
    }

    DocumentPreview getPreview(String taskId, String executionId) {
        var metadata = loadMetadata(taskId, executionId);
        if (!RuntimeArtifactVersions.PARSER_VERSION.equals(metadata.parserVersion())) {
            throw new DocumentConflictException(
                    "PARSER_VERSION_MISMATCH",
                    "该 execution 的 parserVersion 与当前 parser 不一致，禁止历史重解析");
        }
        var verified = verifyDocument(metadata);
        try {
            var parsed = parser.parse(
                    verified.content(),
                    metadata.originalFileName());
            return new DocumentPreview(
                    metadata.taskId(),
                    metadata.executionId(),
                    metadata.contractName(),
                    metadata.originalFileName(),
                    metadata.parserVersion(),
                    verified.sha256(),
                    parsed.blocks().stream().map(ReviewDocumentService::toPreviewBlock).toList());
        } catch (IOException | RuntimeException exception) {
            throw new DocumentUnavailableException("合同预览解析失败", exception);
        }
    }

    VerifiedDocument getDocument(String taskId, String executionId) {
        return verifyDocument(loadMetadata(taskId, executionId));
    }

    private DocumentMetadata loadMetadata(String taskId, String executionId) {
        requireCanonicalIdentity(taskId);
        requireCanonicalIdentity(executionId);
        return repository.findDocument(taskId, executionId)
                .orElseThrow(DocumentNotFoundException::new);
    }

    private VerifiedDocument verifyDocument(DocumentMetadata metadata) {
        if (metadata.originalFileName() == null
                || metadata.originalFileName().isBlank()
                || hasLineBreak(metadata.originalFileName())
                || metadata.documentReference() == null
                || metadata.documentReference().isBlank()) {
            throw new DocumentNotFoundException();
        }
        if (metadata.sha256() == null || !SHA_256.matcher(metadata.sha256()).matches()) {
            throw new DocumentConflictException(
                    "DOCUMENT_CHECKSUM_UNAVAILABLE",
                    "该历史文档没有可核验的上传 SHA-256，禁止读取");
        }
        try {
            var snapshot = documentStore.readDocumentSnapshot(
                            metadata.taskId(),
                            metadata.documentReference())
                    .orElseThrow(DocumentNotFoundException::new);
            if (metadata.sizeBytes() < 0 || metadata.sizeBytes() > MAX_DOCUMENT_BYTES) {
                throw new DocumentConflictException(
                        "DOCUMENT_SIZE_MISMATCH",
                        "合同文档大小校验失败，禁止读取");
            }
            var content = snapshot.content();
            if (content.length > MAX_DOCUMENT_BYTES) {
                throw new DocumentConflictException(
                        "DOCUMENT_SIZE_MISMATCH",
                        "合同文档大小校验失败，禁止读取");
            }
            var actualSha256 = sha256(content);
            if (!MessageDigest.isEqual(
                    metadata.sha256().getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                    actualSha256.getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
                throw new DocumentConflictException(
                        "DOCUMENT_CHECKSUM_MISMATCH",
                        "合同文档校验失败，禁止读取");
            }
            var actualSize = content.length;
            if (metadata.sizeBytes() < 0 || actualSize != metadata.sizeBytes()) {
                throw new DocumentConflictException(
                        "DOCUMENT_SIZE_MISMATCH",
                        "合同文档大小校验失败，禁止读取");
            }
            return new VerifiedDocument(
                    metadata.originalFileName(),
                    actualSha256,
                    actualSize,
                    content);
        } catch (DocumentNotFoundException | DocumentConflictException exception) {
            throw exception;
        } catch (SecurityException exception) {
            throw new DocumentNotFoundException();
        } catch (RuntimeException exception) {
            throw new DocumentUnavailableException("合同文档暂不可用", exception);
        }
    }

    private static PreviewBlock toPreviewBlock(WordParserSpikeDocument.DocumentBlock block) {
        var rowRef = block.tableId() == null || block.rowIndex() == null
                ? null
                : "table:" + block.tableId() + "/row:" + block.rowIndex();
        var cells = block.tableCells().stream()
                .map(cell -> new PreviewCell(
                        rowRef + "/cell:" + cell.cellIndex(),
                        cell.cellIndex(),
                        cell.text()))
                .toList();
        return new PreviewBlock(
                block.blockId(),
                rowRef,
                block.type().name(),
                block.text(),
                block.sectionPath(),
                block.tableId(),
                block.rowIndex(),
                cells);
    }

    private static void requireCanonicalIdentity(String value) {
        if (value == null
                || value.isBlank()
                || !value.equals(value.trim())
                || hasLineBreak(value)
                || value.indexOf('/') >= 0
                || value.indexOf('\\') >= 0) {
            throw new DocumentNotFoundException();
        }
    }

    private static boolean hasLineBreak(String value) {
        return value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0;
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
