package com.cqcp.apiserver.reviewengine;

import static com.cqcp.apiserver.reviewengine.ReviewDocumentModels.DocumentConflictException;
import static com.cqcp.apiserver.reviewengine.ReviewDocumentModels.DocumentMetadata;
import static com.cqcp.apiserver.reviewengine.ReviewDocumentModels.DocumentNotFoundException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cqcp.apiserver.wordparser.DocxWordParserSpike;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReviewDocumentServiceTest {

    private static final String TASK_ID = "TASK_0123456789abcdef0123456789abcdef";
    private static final String EXECUTION_ID = "EXEC_0123456789abcdef0123456789abcdef";
    private static final String DOCUMENT_REFERENCE =
            TASK_ID + "/abcdef0123456789abcdef0123456789.docx";
    private static final Path FIXTURE = Path.of(
            "..",
            "..",
            "packages",
            "test-fixtures",
            "docx",
            "CQCP-MVP-DOCX-001-progress-medium.docx").normalize();

    @TempDir
    Path uploadRoot;

    private ReviewDocumentRepository repository;
    private LocalReviewDocumentStore documentStore;
    private ReviewDocumentService service;
    private String sha256;
    private long sizeBytes;

    @BeforeEach
    void setUp() throws IOException {
        repository = mock(ReviewDocumentRepository.class);
        documentStore = new LocalReviewDocumentStore(uploadRoot);
        try (var input = Files.newInputStream(FIXTURE)) {
            sizeBytes = documentStore.save(DOCUMENT_REFERENCE, input);
        }
        sha256 = documentStore.sha256(TASK_ID, DOCUMENT_REFERENCE);
        service = new ReviewDocumentService(repository, documentStore, new DocxWordParserSpike());
    }

    @Test
    void exactExecutionDocumentIsVerifiedByStoredHashAndSize() {
        when(repository.findDocument(TASK_ID, EXECUTION_ID)).thenReturn(Optional.of(metadata(
                RuntimeArtifactVersions.PARSER_VERSION,
                sizeBytes,
                sha256,
                DOCUMENT_REFERENCE)));

        var verified = service.getDocument(TASK_ID, EXECUTION_ID);

        assertThat(verified.sha256()).isEqualTo(sha256);
        assertThat(verified.sizeBytes()).isEqualTo(sizeBytes);
        assertThat(verified.content()).hasSize(Math.toIntExact(sizeBytes));
        assertThat(sha256Of(verified.content())).isEqualTo(sha256);
    }

    @Test
    void previewUsesParserIssuedBlockAndCellIdentities() {
        when(repository.findDocument(TASK_ID, EXECUTION_ID)).thenReturn(Optional.of(metadata(
                RuntimeArtifactVersions.PARSER_VERSION,
                sizeBytes,
                sha256,
                DOCUMENT_REFERENCE)));

        var preview = service.getPreview(TASK_ID, EXECUTION_ID);

        assertThat(preview.blocks()).isNotEmpty();
        assertThat(preview.blocks()).allSatisfy(block -> assertThat(block.blockId()).isNotBlank());
        assertThat(preview.blocks())
                .flatExtracting(ReviewDocumentModels.PreviewBlock::cells)
                .allSatisfy(cell -> assertThat(cell.previewElementRef())
                        .matches("table:.+/row:\\d+/cell:\\d+"));
    }

    @Test
    void parserVersionMismatchFailsBeforeReadingDocument() {
        when(repository.findDocument(TASK_ID, EXECUTION_ID)).thenReturn(Optional.of(metadata(
                "parser-old",
                sizeBytes,
                sha256,
                DOCUMENT_REFERENCE)));

        assertThatThrownBy(() -> service.getPreview(TASK_ID, EXECUTION_ID))
                .isInstanceOf(DocumentConflictException.class)
                .extracting(exception -> ((DocumentConflictException) exception).code())
                .isEqualTo("PARSER_VERSION_MISMATCH");
    }

    @Test
    void checksumOrSizeMismatchFailsClosed() {
        when(repository.findDocument(TASK_ID, EXECUTION_ID)).thenReturn(Optional.of(metadata(
                RuntimeArtifactVersions.PARSER_VERSION,
                sizeBytes,
                "0".repeat(64),
                DOCUMENT_REFERENCE)));

        assertThatThrownBy(() -> service.getDocument(TASK_ID, EXECUTION_ID))
                .isInstanceOf(DocumentConflictException.class)
                .extracting(exception -> ((DocumentConflictException) exception).code())
                .isEqualTo("DOCUMENT_CHECKSUM_MISMATCH");

        when(repository.findDocument(TASK_ID, EXECUTION_ID)).thenReturn(Optional.of(metadata(
                RuntimeArtifactVersions.PARSER_VERSION,
                sizeBytes + 1,
                sha256,
                DOCUMENT_REFERENCE)));

        assertThatThrownBy(() -> service.getDocument(TASK_ID, EXECUTION_ID))
                .isInstanceOf(DocumentConflictException.class)
                .extracting(exception -> ((DocumentConflictException) exception).code())
                .isEqualTo("DOCUMENT_SIZE_MISMATCH");
    }

    @Test
    void historicalDocumentWithoutHashFailsClosed() {
        when(repository.findDocument(TASK_ID, EXECUTION_ID)).thenReturn(Optional.of(metadata(
                RuntimeArtifactVersions.PARSER_VERSION,
                sizeBytes,
                null,
                DOCUMENT_REFERENCE)));

        assertThatThrownBy(() -> service.getDocument(TASK_ID, EXECUTION_ID))
                .isInstanceOf(DocumentConflictException.class)
                .extracting(exception -> ((DocumentConflictException) exception).code())
                .isEqualTo("DOCUMENT_CHECKSUM_UNAVAILABLE");
    }

    @Test
    void crossTaskReferenceAndNonCanonicalIdentityAreRejected() {
        when(repository.findDocument(TASK_ID, EXECUTION_ID)).thenReturn(Optional.of(metadata(
                RuntimeArtifactVersions.PARSER_VERSION,
                sizeBytes,
                sha256,
                "TASK_other/abcdef0123456789abcdef0123456789.docx")));

        assertThatThrownBy(() -> service.getDocument(TASK_ID, EXECUTION_ID))
                .isInstanceOf(DocumentNotFoundException.class);
        assertThatThrownBy(() -> service.getDocument("../" + TASK_ID, EXECUTION_ID))
                .isInstanceOf(DocumentNotFoundException.class);
        assertThatThrownBy(() -> service.getDocument(TASK_ID, EXECUTION_ID + "\r\n"))
                .isInstanceOf(DocumentNotFoundException.class);

        when(repository.findDocument(TASK_ID, EXECUTION_ID)).thenReturn(Optional.of(
                new DocumentMetadata(
                        TASK_ID,
                        EXECUTION_ID,
                        "测试合同",
                        RuntimeArtifactVersions.PARSER_VERSION,
                        "测试合同\r\nContent-Type:text/html.docx",
                        DOCUMENT_REFERENCE,
                        sizeBytes,
                        sha256)));
        assertThatThrownBy(() -> service.getDocument(TASK_ID, EXECUTION_ID))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    @Test
    void invalidIdentityDoesNotQueryRepository() {
        var isolatedRepository = mock(ReviewDocumentRepository.class);
        var isolatedService =
                new ReviewDocumentService(isolatedRepository, documentStore, new DocxWordParserSpike());

        assertThatThrownBy(() -> isolatedService.getDocument(" " + TASK_ID, EXECUTION_ID))
                .isInstanceOf(DocumentNotFoundException.class);
        verifyNoInteractions(isolatedRepository);
    }

    @Test
    void reparseSecurityFailureMapsToNotFoundBeforeParser() {
        var isolatedRepository = mock(ReviewDocumentRepository.class);
        var isolatedStore = mock(LocalReviewDocumentStore.class);
        var isolatedParser = mock(DocxWordParserSpike.class);
        var isolatedService =
                new ReviewDocumentService(isolatedRepository, isolatedStore, isolatedParser);
        when(isolatedRepository.findDocument(TASK_ID, EXECUTION_ID))
                .thenReturn(Optional.of(metadata(
                        RuntimeArtifactVersions.PARSER_VERSION,
                        sizeBytes,
                        sha256,
                        DOCUMENT_REFERENCE)));
        when(isolatedStore.readDocumentSnapshot(TASK_ID, DOCUMENT_REFERENCE))
                .thenThrow(new SecurityException("reparse point"));

        assertThatThrownBy(() -> isolatedService.getDocument(TASK_ID, EXECUTION_ID))
                .isInstanceOf(DocumentNotFoundException.class)
                .hasMessage("未找到该 task/execution 的合同文档");
        verifyNoInteractions(isolatedParser);
    }

    @Test
    void taskDirectoryReplacementAfterStableOpenFailsClosedBeforeParser()
            throws Exception {
        var raceRoot = uploadRoot.resolve("race-root");
        Files.createDirectories(raceRoot);
        var bootstrapStore = new LocalReviewDocumentStore(raceRoot);
        try (var input = Files.newInputStream(FIXTURE)) {
            bootstrapStore.save(DOCUMENT_REFERENCE, input);
        }
        var expectedSize = Files.size(FIXTURE);
        var expectedSha = sha256Of(Files.readAllBytes(FIXTURE));
        var taskDirectory = raceRoot.resolve(TASK_ID);
        var movedTaskDirectory = raceRoot.resolve(TASK_ID + "-moved");
        var outsideDirectory = Files.createTempDirectory(
                uploadRoot.getParent(),
                "cqcp-document-outside-");
        var fileName = Path.of(DOCUMENT_REFERENCE).getFileName();
        Files.copy(FIXTURE, outsideDirectory.resolve(fileName));
        var observerInvoked = new AtomicBoolean();
        var raceStore = new LocalReviewDocumentStore(
                raceRoot,
                new LocalReviewDocumentStore.StableOpenObserver() {
                    @Override
                    public void beforeFileOpen() throws IOException {
                        observerInvoked.set(true);
                        Files.move(taskDirectory, movedTaskDirectory);
                        createDirectoryAlias(taskDirectory, outsideDirectory);
                    }
                });
        var raceRepository = mock(ReviewDocumentRepository.class);
        var raceParser = mock(DocxWordParserSpike.class);
        var raceService = new ReviewDocumentService(
                raceRepository,
                raceStore,
                raceParser);
        when(raceRepository.findDocument(TASK_ID, EXECUTION_ID))
                .thenReturn(Optional.of(metadata(
                        RuntimeArtifactVersions.PARSER_VERSION,
                        expectedSize,
                        expectedSha,
                        DOCUMENT_REFERENCE)));

        try {
            assertThatThrownBy(() -> raceService.getDocument(TASK_ID, EXECUTION_ID))
                    .isInstanceOf(DocumentNotFoundException.class);
            assertThat(observerInvoked).isTrue();
            verifyNoInteractions(raceParser);
        } finally {
            deleteDirectoryAliasIfPresent(taskDirectory);
            if (Files.exists(movedTaskDirectory) && !Files.exists(taskDirectory)) {
                Files.move(movedTaskDirectory, taskDirectory);
            }
            Files.deleteIfExists(outsideDirectory.resolve(fileName));
            Files.deleteIfExists(outsideDirectory);
        }
    }

    private static DocumentMetadata metadata(
            String parserVersion,
            long expectedSize,
            String expectedSha,
            String reference) {
        return new DocumentMetadata(
                TASK_ID,
                EXECUTION_ID,
                "测试合同",
                parserVersion,
                "测试合同.docx",
                reference,
                expectedSize,
                expectedSha);
    }

    private static String sha256Of(byte[] content) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void createDirectoryAlias(Path alias, Path target)
            throws IOException {
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")) {
            var process = new ProcessBuilder(
                    "cmd.exe",
                    "/d",
                    "/c",
                    "mklink",
                    "/J",
                    alias.toString(),
                    target.toString())
                    .redirectErrorStream(true)
                    .start();
            var output = new String(
                    process.getInputStream().readAllBytes(),
                    java.nio.charset.Charset.defaultCharset());
            try {
                if (process.waitFor() != 0) {
                    throw new IOException("mklink /J failed: " + output);
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("mklink /J interrupted", exception);
            }
            return;
        }
        Files.createSymbolicLink(alias, target);
    }

    private static void deleteDirectoryAliasIfPresent(Path alias) throws IOException {
        if (!Files.exists(alias, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return;
        var attributes = Files.readAttributes(
                alias,
                java.nio.file.attribute.BasicFileAttributes.class,
                java.nio.file.LinkOption.NOFOLLOW_LINKS);
        if (attributes.isSymbolicLink() || attributes.isOther()) {
            Files.delete(alias);
        }
    }
}
