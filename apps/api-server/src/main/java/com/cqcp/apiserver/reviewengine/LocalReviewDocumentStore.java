package com.cqcp.apiserver.reviewengine;

import static com.cqcp.apiserver.reviewengine.ReviewTaskCreationModels.DocumentStorageException;
import static com.cqcp.apiserver.reviewengine.ReviewTaskCreationModels.InvalidDocumentContentException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.zip.ZipException;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.exceptions.NotOfficeXmlFileException;
import org.apache.poi.openxml4j.exceptions.OpenXML4JRuntimeException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Demo-level file store for review documents.
 *
 * <p>Writes files to a configured upload root with task-scoped random relative paths.
 * Performs minimal OPC (OOXML Package) validation to reject non-DOCX content
 * without invoking the business parser.
 *
 * <p><strong>TOCTOU</strong>: symlink checks and file operations are not atomic;
 * a race window exists.  Acceptable for Demo; production should use OS-level
 * {@code openat2(RESOLVE_NO_SYMLINKS)} or equivalent.
 */
class LocalReviewDocumentStore {

    private static final Logger log = LoggerFactory.getLogger(LocalReviewDocumentStore.class);

    private final Path realRoot;

    LocalReviewDocumentStore(Path uploadRoot) {
        Objects.requireNonNull(uploadRoot, "uploadRoot");
        try {
            var abs = uploadRoot.toAbsolutePath().normalize();
            Files.createDirectories(abs);
            this.realRoot = abs.toRealPath();
        } catch (IOException e) {
            throw new DocumentStorageException("Cannot create upload root", e);
        }
    }

    /**
     * Persist the given bytes to a task-scoped random path under the upload root.
     *
     * @param documentReference  forward-slash relative path, e.g. {@code "taskId/uuid.docx"}
     * @param data               input stream
     * @return number of bytes written
     * @throws InvalidDocumentContentException if content is not a valid DOCX (HTTP 400)
     * @throws DocumentStorageException        if file I/O fails (HTTP 503)
     * @throws SecurityException               if path traversal or symlink detected
     */
    long save(String documentReference, InputStream data) {
        Objects.requireNonNull(documentReference, "documentReference");
        Objects.requireNonNull(data, "data");

        var fullPath = resolve(documentReference);
        var parent = fullPath.getParent();
        var uuid = basename(fullPath);
        var tempPath = parent.resolve(uuid + ".tmp");
        var targetPath = parent.resolve(uuid + ".docx");

        try {
            ensureParentSecure(parent);

            // CREATE_NEW — fail if already exists
            Files.createFile(tempPath);
            try (OutputStream out = Files.newOutputStream(tempPath, StandardOpenOption.WRITE)) {
                data.transferTo(out);
            }

            // Minimal OPC validation (READ only)
            validateDocx(tempPath.toFile());

            // Before atomic move, confirm target does not already exist (no symlink follow)
            if (Files.exists(targetPath, LinkOption.NOFOLLOW_LINKS)) {
                throw new FileAlreadyExistsException(targetPath.toString());
            }
            atomicMove(tempPath, targetPath);

            return Files.size(targetPath);
        } catch (InvalidDocumentContentException | SecurityException e) {
            throw e;
        } catch (FileAlreadyExistsException e) {
            // Collision — respond with a storage failure that does NOT leak the absolute path.
            // The caller's handler translates this to 503 without exposing server-internal paths.
            throw new DocumentStorageException("File collision", e);
        } catch (IOException e) {
            throw new DocumentStorageException("File I/O error", e);
        } finally {
            cleanupTemp(tempPath, documentReference);
        }
    }

    /** Idempotent delete — swallows all exceptions, logs only documentReference. */
    void deleteIfExists(String documentReference) {
        try {
            var fullPath = resolve(documentReference);
            boolean failed = false;
            try { Files.deleteIfExists(fullPath); } catch (Exception e) { failed = true; }
            var temp = fullPath.resolveSibling(basename(fullPath) + ".tmp");
            try { Files.deleteIfExists(temp); } catch (Exception e) { failed = true; }
            if (failed) {
                log.warn("Failed to clean up file for docRef={}", documentReference);
            }
        } catch (Exception e) {
            log.warn("Failed to clean up file for docRef={}", documentReference);
        }
    }

    boolean exists(String documentReference) {
        return Files.exists(resolve(documentReference));
    }

    // --------------- internal helpers ---------------

    private Path resolve(String documentReference) {
        var ref = Path.of(documentReference);
        if (ref.isAbsolute()) {
            throw new SecurityException("Absolute document reference");
        }
        var full = realRoot.resolve(ref).normalize();
        if (!full.startsWith(realRoot)) {
            throw new SecurityException("Path traversal detected");
        }
        return full;
    }

    private void ensureParentSecure(Path parent) {
        try {
            Path walk = realRoot;
            for (Path seg : realRoot.relativize(parent)) {
                walk = walk.resolve(seg);
                if (Files.exists(walk) && Files.isSymbolicLink(walk)) {
                    throw new SecurityException("Symbolic link in path");
                }
            }
            Files.createDirectories(parent);
            var realParent = parent.toRealPath();
            if (!realParent.startsWith(realRoot)) {
                throw new SecurityException("Parent escapes upload root");
            }
        } catch (SecurityException e) {
            throw e;
        } catch (IOException e) {
            throw new DocumentStorageException("Cannot create parent directory", e);
        }
    }

    /** OPC validation — separates content errors (400) from file-system IO (503). */
    private void validateDocx(File file) {
        // The file was already created and written by Files.createFile + OutputStream
        // before this method is called, so a genuine file-system IOException at this
        // point is extremely rare.  Nevertheless we distinguish:
        //   ZipException  → content error (400)
        //   IOExceptions  → file-system failure (503)
        //   POI exceptions → content error (400)
        try (OPCPackage pkg = OPCPackage.open(file, PackageAccess.READ)) {
            var hasMainDoc = pkg.getPartsByContentType(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml")
                    .stream()
                    .anyMatch(p -> "/word/document.xml".equals(p.getPartName().getName()));
            if (!hasMainDoc) {
                throw new InvalidDocumentContentException("file 不是有效 DOCX 文档");
            }
        } catch (InvalidDocumentContentException e) {
            throw e;
        } catch (InvalidFormatException e) {
            // POI's checked InvalidFormatException — unparseable OOXML content (400)
            throw new InvalidDocumentContentException("file 不是有效 DOCX 文档");
        } catch (ZipException e) {
            // java.util.zip.ZipException from non-ZIP bytes — content error (400)
            throw new InvalidDocumentContentException("file 不是有效 DOCX 文档");
        } catch (NotOfficeXmlFileException e) {
            // POI's NotOfficeXmlFileException — not an OOXML package (400)
            throw new InvalidDocumentContentException("file 不是有效 DOCX 文档");
        } catch (OpenXML4JRuntimeException e) {
            // Other POI runtime content exceptions (InvalidOperationException etc.) → 400
            throw new InvalidDocumentContentException("file 不是有效 DOCX 文档");
        } catch (IOException e) {
            // Genuine file-system IO (permission, stale handle, etc.) — storage failure (503)
            throw new DocumentStorageException("File IO error during DOCX validation", e);
        }
    }

    private static void atomicMove(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target);    // same FileStore fallback, no REPLACE_EXISTING
        }
    }

    /** Temp cleanup — swallows exceptions, logs only documentReference. */
    private static void cleanupTemp(Path tempPath, String documentReference) {
        try {
            Files.deleteIfExists(tempPath);
        } catch (Exception e) {
            log.warn("Failed to clean up temp file for docRef={}", documentReference);
        }
    }

    private static String basename(Path p) {
        var name = p.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return (dot > 0) ? name.substring(0, dot) : name;
    }
}
