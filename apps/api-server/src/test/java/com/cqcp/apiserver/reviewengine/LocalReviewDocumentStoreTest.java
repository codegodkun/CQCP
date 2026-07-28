package com.cqcp.apiserver.reviewengine;

import static com.cqcp.apiserver.reviewengine.ReviewTaskCreationModels.DocumentStorageException;
import static com.cqcp.apiserver.reviewengine.ReviewTaskCreationModels.InvalidDocumentContentException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalReviewDocumentStoreTest {

    @TempDir
    Path tempDir;

    private LocalReviewDocumentStore store;

    @BeforeEach
    void setUp() {
        store = new LocalReviewDocumentStore(tempDir);
    }

    // ==================== Happy path (valid DOCX) ====================

    @Test
    void savesAndReadsFile_success() {
        var docx = validDocx();
        var ref = "t/u.docx";
        var size = store.save(ref, new ByteArrayInputStream(docx));
        assertThat(size).isEqualTo(docx.length);

        var saved = tempDir.resolve("t/u.docx");
        assertThat(saved).exists().isRegularFile();
        assertThat(readAllBytes(saved)).isEqualTo(docx);
        assertThat(tempDir.resolve("t/u.tmp")).doesNotExist();
    }

    @Test
    void fileIsReadableAfterRequestCompletes() {
        var docx = validDocx();
        store.save("p/u.docx", new ByteArrayInputStream(docx));
        assertThat(store.exists("p/u.docx")).isTrue();
        assertThat(readAllBytes(tempDir.resolve("p/u.docx"))).isEqualTo(docx);
    }

    // ==================== Path traversal ====================

    @Test
    void rejectsPathTraversal() {
        assertThatThrownBy(() -> store.save("../escape.docx", emptyStream()))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void rejectsAbsoluteReference() {
        assertThatThrownBy(() -> store.save("/etc/passwd", emptyStream()))
                .isInstanceOf(SecurityException.class);
    }

    // ==================== Temp file cleanup ====================

    @Test
    void tempFileCleanedOnSuccess() {
        store.save("t/x.docx", new ByteArrayInputStream(validDocx()));
        assertThat(tempDir.resolve("t/x.tmp")).doesNotExist();
    }

    @Test
    void tempFileCleanedOnFailure() {
        assertThatThrownBy(() -> store.save("t/n.docx", null))
                .isInstanceOf(Exception.class);
        assertThat(tempDir.resolve("t/n.tmp")).doesNotExist();
    }

    // ==================== Collision (CREATE_NEW) ====================

    @Test
    void createNewCollision_failsClosed() {
        var ref = "c/u.docx";
        store.save(ref, new ByteArrayInputStream(validDocx()));
        // Second save with same ref → CREATE_NEW → FileAlreadyExistsException → DocumentStorageException
        assertThatThrownBy(() -> store.save(ref, new ByteArrayInputStream(validDocx())))
                .isInstanceOf(DocumentStorageException.class);
        // .tmp must be cleaned
        assertThat(tempDir.resolve("c/u.tmp")).doesNotExist();
        // Original file must NOT be overwritten
        assertThat(readAllBytes(tempDir.resolve("c/u.docx"))).isEqualTo(validDocx());
    }

    // ==================== Symlink rejection ====================

    @Test
    void symlinkInTaskDir_rejected() throws Exception {
        // Create a real target directory
        var realDir = tempDir.resolve("real_target");
        Files.createDirectories(realDir);
        // Create a symlink pointing to it (may fail on Windows without privilege)
        var symDir = tempDir.resolve("symlink-task");
        try {
            Files.createSymbolicLink(symDir, realDir);
        } catch (Exception e) {
            assumeTrue(false, "Cannot create symlink on this platform: " + e.getMessage());
            return;
        }
        var store2 = new LocalReviewDocumentStore(tempDir);
        // Reference is resolved under the symlinked directory name
        assertThatThrownBy(() -> store2.save("symlink-task/u.docx", new ByteArrayInputStream(validDocx())))
                .isInstanceOf(SecurityException.class);
        // No file should appear in the real target directory either
        assertThat(realDir.resolve("u.docx")).doesNotExist();
        assertThat(realDir.resolve("u.tmp")).doesNotExist();
        // Temp file also cleaned
        assertThat(symDir.resolve("u.tmp")).doesNotExist();
    }

    // ==================== DOCX validation ====================

    @Test
    void validDocx_passes() {
        assertDoesNotThrow(() -> store.save("v/u.docx", new ByteArrayInputStream(validDocx())));
    }

    @Test
    void randomBytes_invalid() {
        var random = "not a docx at all".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertThatThrownBy(() -> store.save("r/u.docx", new ByteArrayInputStream(random)))
                .isInstanceOf(InvalidDocumentContentException.class);
        assertThat(tempDir.resolve("r/u.tmp")).doesNotExist();
    }

    @Test
    void zipWithoutMainPart_invalid() throws Exception {
        var baos = new java.io.ByteArrayOutputStream();
        try (var zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zos.write("<?xml version=\"1.0\"?><Types/>".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("word/something.xml"));
            zos.write("<x/>".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        assertThatThrownBy(() -> store.save("z/u.docx", new ByteArrayInputStream(baos.toByteArray())))
                .isInstanceOf(InvalidDocumentContentException.class);
        assertThat(tempDir.resolve("z/u.tmp")).doesNotExist();
    }

    // ==================== Delete ====================

    @Test
    void deleteIfExists_removesFile() {
        var ref = "d/u.docx";
        store.save(ref, new ByteArrayInputStream(validDocx()));
        assertThat(store.exists(ref)).isTrue();
        store.deleteIfExists(ref);
        assertThat(store.exists(ref)).isFalse();
    }

    @Test
    void deleteIfExists_idempotent() {
        store.deleteIfExists("nonexistent/x.docx");
    }

    // ==================== Helpers ====================

    private static byte[] validDocx() {
        try {
            var baos = new java.io.ByteArrayOutputStream();
            try (var zos = new ZipOutputStream(baos)) {
                zos.putNextEntry(new ZipEntry("[Content_Types].xml"));
                zos.write(("<?xml version=\"1.0\"?>\n<Types xmlns=\"http://schemas.openxmlformats"
                        + ".org/package/2006/content-types\">\n<Default Extension=\"rels\" "
                        + "ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>\n"
                        + "<Default Extension=\"xml\" ContentType=\"application/vnd.openxmlformats"
                        + "-officedocument.wordprocessingml.document.main+xml\"/>\n"
                        + "</Types>").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zos.closeEntry();
                zos.putNextEntry(new ZipEntry("word/document.xml"));
                zos.write("<?xml version=\"1.0\"?><w:document xmlns:w=\"http://schemas.openxmlformats"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zos.write("-org/wordprocessingml/2006/main\"><w:body><w:p><w:r><w:t>Test</w:t>"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zos.write("</w:r></w:p></w:body></w:document>".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zos.closeEntry();
                zos.putNextEntry(new ZipEntry("_rels/.rels"));
                zos.write(("<?xml version=\"1.0\"?>\n<Relationships xmlns=\"http://schemas.openxmlformats"
                        + "-org/package/2006/relationships\">\n<Relationship Id=\"rId1\" "
                        + "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships"
                        + "/officeDocument\" Target=\"word/document.xml\"/>\n"
                        + "</Relationships>").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zos.closeEntry();
            }
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static InputStream emptyStream() {
        return new ByteArrayInputStream(new byte[0]);
    }

    private static byte[] readAllBytes(Path p) {
        try { return Files.readAllBytes(p); } catch (Exception e) { throw new RuntimeException(e); }
    }
}
