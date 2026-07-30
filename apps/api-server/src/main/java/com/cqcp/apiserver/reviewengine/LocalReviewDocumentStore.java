package com.cqcp.apiserver.reviewengine;

import static com.cqcp.apiserver.reviewengine.ReviewTaskCreationModels.DocumentStorageException;
import static com.cqcp.apiserver.reviewengine.ReviewTaskCreationModels.InvalidDocumentContentException;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;
import com.sun.nio.file.ExtendedOpenOption;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;
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
 * <p>Reads use a directory-relative {@link SecureDirectoryStream} when the
 * provider supports it. Windows uses a no-share-delete file handle plus
 * before/after path fingerprints so a parent directory cannot be swapped and
 * restored while the verified bytes are read.
 */
class LocalReviewDocumentStore {

    private static final Logger log = LoggerFactory.getLogger(LocalReviewDocumentStore.class);
    private static final int MAX_STORED_DOCUMENT_BYTES = 25 * 1024 * 1024;
    private static final boolean WINDOWS =
            System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win");

    private final Path realRoot;
    private final StableRootIdentity rootIdentity;
    private final WindowsFileIdentity windowsRootIdentity;
    private final StableOpenObserver stableOpenObserver;

    LocalReviewDocumentStore(Path uploadRoot) {
        this(uploadRoot, StableOpenObserver.NOOP);
    }

    LocalReviewDocumentStore(Path uploadRoot, StableOpenObserver stableOpenObserver) {
        Objects.requireNonNull(uploadRoot, "uploadRoot");
        this.stableOpenObserver =
                Objects.requireNonNull(stableOpenObserver, "stableOpenObserver");
        try {
            var abs = uploadRoot.toAbsolutePath().normalize();
            Files.createDirectories(abs);
            this.realRoot = abs.toRealPath();
            var attributes = readSafeAttributes(realRoot);
            if (!attributes.isDirectory()) {
                throw new IOException("Upload root is not a directory");
            }
            this.rootIdentity = StableRootIdentity.of(attributes);
            this.windowsRootIdentity = WINDOWS
                    ? WindowsDirectoryGuard.captureIdentity(realRoot)
                    : null;
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

    /**
     * Read one bounded byte snapshot from a path that remains bound to the
     * validated task directory for the whole operation.
     *
     * @param taskId             loaded task identifier
     * @param documentReference  stored reference, e.g. {@code "TASK_abc/32hexchars.docx"}
     * @return defensive byte snapshot, or empty if the path cannot be proven safe
     */
    java.util.Optional<Path> readDocumentPathForExecution(
            String taskId,
            String documentReference) {
        if (readDocumentSnapshot(taskId, documentReference).isEmpty()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(realRoot.resolve(Path.of(documentReference)).normalize());
    }

    java.util.Optional<StoredDocumentSnapshot> readDocumentSnapshot(
            String taskId,
            String documentReference) {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(documentReference, "documentReference");
        if (hasLineBreak(taskId) || hasLineBreak(documentReference)) {
            throw new SecurityException("Invalid document reference");
        }
        var taskPath = Path.of(taskId);
        if (taskPath.isAbsolute()
                || taskPath.getNameCount() != 1
                || taskId.indexOf('/') >= 0
                || taskId.indexOf('\\') >= 0) {
            throw new SecurityException("Invalid document reference");
        }

        // Pattern: ^{taskId}/[0-9a-f]{32}\.docx$
        var expectedPrefix = taskId + "/";
        if (!documentReference.startsWith(expectedPrefix)) {
            throw new SecurityException("Invalid document reference");
        }
        var suffix = documentReference.substring(expectedPrefix.length());
        if (!suffix.matches("[0-9a-f]{32}\\.docx")) {
            throw new SecurityException("Invalid document reference");
        }

        try {
            var refPath = Path.of(documentReference);
            if (refPath.isAbsolute()) {
                throw new SecurityException("Invalid document reference");
            }
            var resolved = realRoot.resolve(refPath).normalize();
            if (!resolved.startsWith(realRoot)) {
                throw new SecurityException("Invalid document reference");
            }
            var taskDirectory = realRoot.resolve(taskPath);
            var filePath = taskDirectory.resolve(suffix);
            var beforeRoot = readSafeAttributes(realRoot);
            var beforeTask = readSafeAttributes(taskDirectory);
            var beforeFile = readSafeAttributes(filePath);
            if (!rootIdentity.matches(beforeRoot)) {
                throw new SecurityException("Upload root identity changed");
            }
            if (!beforeTask.isDirectory()) {
                throw new SecurityException("Task path is not a directory");
            }
            if (!beforeFile.isRegularFile()) {
                throw new SecurityException("Document path is not a regular file");
            }
            var taskIdentity = StablePathIdentity.of(beforeTask);
            var fileIdentity = StablePathIdentity.of(beforeFile);
            if (WINDOWS) {
                return java.util.Optional.of(readWindowsSnapshot(
                        taskDirectory,
                        filePath,
                        taskIdentity,
                        fileIdentity));
            }
            return java.util.Optional.of(readSecureSnapshot(
                    taskPath,
                    Path.of(suffix),
                    taskDirectory,
                    filePath,
                    taskIdentity,
                    fileIdentity));
        } catch (IOException e) {
            return java.util.Optional.empty();
        } catch (SecurityException e) {
            throw e;
        }
    }

    String sha256(String taskId, String documentReference) {
        var snapshot = readDocumentSnapshot(taskId, documentReference)
                .orElseThrow(() -> new DocumentStorageException("Document is unavailable", null));
        try (var input = new java.io.ByteArrayInputStream(snapshot.content())) {
            var digest = MessageDigest.getInstance("SHA-256");
            var buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new DocumentStorageException("Cannot calculate document checksum", e);
        }
    }

    private StoredDocumentSnapshot readSecureSnapshot(
            Path taskPath,
            Path fileName,
            Path taskDirectory,
            Path filePath,
            StablePathIdentity taskIdentity,
            StablePathIdentity fileIdentity) throws IOException {
        try (DirectoryStream<Path> rootStream = Files.newDirectoryStream(realRoot)) {
            if (!(rootStream instanceof SecureDirectoryStream<Path> secureRoot)) {
                throw new SecurityException(
                        "File system does not support secure relative document reads");
            }
            var openedRootAttributes = secureAttributes(secureRoot, Path.of("."));
            if (!rootIdentity.matches(openedRootAttributes)) {
                throw new SecurityException("Upload root identity changed");
            }
            var openedTaskAttributes = secureAttributes(secureRoot, taskPath);
            if (!taskIdentity.matches(openedTaskAttributes)
                    || !openedTaskAttributes.isDirectory()) {
                throw new SecurityException("Task directory identity changed");
            }
            try (var secureTask = secureRoot.newDirectoryStream(
                    taskPath,
                    LinkOption.NOFOLLOW_LINKS)) {
                var openedTaskHandleAttributes =
                        secureAttributes(secureTask, Path.of("."));
                if (!taskIdentity.matches(openedTaskHandleAttributes)
                        || !openedTaskHandleAttributes.isDirectory()) {
                    throw new SecurityException("Opened task directory identity changed");
                }
                var openedFileAttributes = secureAttributes(secureTask, fileName);
                if (!fileIdentity.matches(openedFileAttributes)
                        || !openedFileAttributes.isRegularFile()) {
                    throw new SecurityException("Document identity changed");
                }
                stableOpenObserver.beforeFileOpen();
                verifyCurrentPathIdentities(
                        taskDirectory,
                        filePath,
                        taskIdentity,
                        fileIdentity);
                try (var channel = secureTask.newByteChannel(
                        fileName,
                        Set.of(
                                StandardOpenOption.READ,
                                LinkOption.NOFOLLOW_LINKS))) {
                    stableOpenObserver.afterFileOpen();
                    verifyCurrentPathIdentities(
                            taskDirectory,
                            filePath,
                            taskIdentity,
                            fileIdentity);
                    return new StoredDocumentSnapshot(readBounded(channel));
                }
            }
        }
    }

    private StoredDocumentSnapshot readWindowsSnapshot(
            Path taskDirectory,
            Path filePath,
            StablePathIdentity taskIdentity,
            StablePathIdentity fileIdentity) throws IOException {
        try (var rootGuard = WindowsDirectoryGuard.open(realRoot);
                var taskGuard = WindowsDirectoryGuard.open(taskDirectory)) {
            if (!windowsRootIdentity.equals(rootGuard.identity())) {
                throw new SecurityException("Upload root identity changed");
            }
            stableOpenObserver.beforeFileOpen();
            verifyCurrentPathIdentities(
                    taskDirectory,
                    filePath,
                    taskIdentity,
                    fileIdentity);
            try (var channel = FileChannel.open(
                    filePath,
                    StandardOpenOption.READ,
                    LinkOption.NOFOLLOW_LINKS,
                    ExtendedOpenOption.NOSHARE_DELETE)) {
                stableOpenObserver.afterFileOpen();
                verifyCurrentPathIdentities(
                        taskDirectory,
                        filePath,
                        taskIdentity,
                        fileIdentity);
                return new StoredDocumentSnapshot(readBounded(channel));
            }
        }
    }

    private void verifyCurrentPathIdentities(
            Path taskDirectory,
            Path filePath,
            StablePathIdentity taskIdentity,
            StablePathIdentity fileIdentity) throws IOException {
        var currentRoot = readSafeAttributes(realRoot);
        var currentTask = readSafeAttributes(taskDirectory);
        var currentFile = readSafeAttributes(filePath);
        if (!rootIdentity.matches(currentRoot)
                || !taskIdentity.matches(currentTask)
                || !currentTask.isDirectory()
                || !fileIdentity.matches(currentFile)
                || !currentFile.isRegularFile()) {
            throw new SecurityException("Document path identity changed");
        }
    }

    private static BasicFileAttributes secureAttributes(
            SecureDirectoryStream<Path> directory,
            Path relativePath) throws IOException {
        var view = directory.getFileAttributeView(
                relativePath,
                BasicFileAttributeView.class,
                LinkOption.NOFOLLOW_LINKS);
        if (view == null) {
            throw new IOException("Basic file attributes unavailable");
        }
        var attributes = view.readAttributes();
        if (attributes.isSymbolicLink() || attributes.isOther()) {
            throw new SecurityException("Reparse path rejected");
        }
        return attributes;
    }

    private static BasicFileAttributes readSafeAttributes(Path path) throws IOException {
        var attributes = Files.readAttributes(
                path,
                BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
        if (attributes.isSymbolicLink() || attributes.isOther()) {
            throw new SecurityException("Reparse path rejected");
        }
        return attributes;
    }

    private static byte[] readBounded(SeekableByteChannel channel) throws IOException {
        try (var input = Channels.newInputStream(channel)) {
            return input.readNBytes(MAX_STORED_DOCUMENT_BYTES + 1);
        }
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

    private static boolean hasLineBreak(String value) {
        return value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0;
    }

    record StoredDocumentSnapshot(byte[] content) {

        StoredDocumentSnapshot {
            content = content.clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }

    private record StableRootIdentity(
            Object fileKey,
            java.nio.file.attribute.FileTime creationTime) {

        static StableRootIdentity of(BasicFileAttributes attributes) {
            return new StableRootIdentity(
                    attributes.fileKey(),
                    attributes.creationTime());
        }

        boolean matches(BasicFileAttributes attributes) {
            if (!attributes.isDirectory()) return false;
            if (fileKey != null && attributes.fileKey() != null) {
                return fileKey.equals(attributes.fileKey());
            }
            return creationTime.equals(attributes.creationTime());
        }
    }

    private record StablePathIdentity(
            Object fileKey,
            java.nio.file.attribute.FileTime creationTime,
            java.nio.file.attribute.FileTime lastModifiedTime,
            long size) {

        static StablePathIdentity of(BasicFileAttributes attributes) {
            return new StablePathIdentity(
                    attributes.fileKey(),
                    attributes.creationTime(),
                    attributes.lastModifiedTime(),
                    attributes.size());
        }

        boolean matches(BasicFileAttributes attributes) {
            if (fileKey != null && attributes.fileKey() != null) {
                return fileKey.equals(attributes.fileKey());
            }
            return creationTime.equals(attributes.creationTime())
                    && lastModifiedTime.equals(attributes.lastModifiedTime())
                    && size == attributes.size();
        }
    }

    private record WindowsFileIdentity(long volumeSerialNumber, String fileId) {}

    private static final class WindowsDirectoryGuard implements AutoCloseable {

        private final WinNT.HANDLE handle;
        private final WindowsFileIdentity identity;

        private WindowsDirectoryGuard(
                WinNT.HANDLE handle,
                WindowsFileIdentity identity) {
            this.handle = handle;
            this.identity = identity;
        }

        static WindowsDirectoryGuard open(Path directory) throws IOException {
            var handle = Kernel32.INSTANCE.CreateFile(
                    directory.toString(),
                    WinNT.FILE_READ_ATTRIBUTES,
                    WinNT.FILE_SHARE_READ | WinNT.FILE_SHARE_WRITE,
                    null,
                    WinNT.OPEN_EXISTING,
                    WinNT.FILE_FLAG_BACKUP_SEMANTICS
                            | WinNT.FILE_FLAG_OPEN_REPARSE_POINT,
                    null);
            if (handle == null || WinBase.INVALID_HANDLE_VALUE.equals(handle)) {
                throw new IOException(
                        "Cannot bind Windows directory handle, error="
                                + Native.getLastError());
            }
            try {
                var tagInfo = new WinBase.FILE_ATTRIBUTE_TAG_INFO();
                tagInfo.write();
                if (!Kernel32.INSTANCE.GetFileInformationByHandleEx(
                        handle,
                        WinBase.FileAttributeTagInfo,
                        tagInfo.getPointer(),
                        new WinDef.DWORD(tagInfo.size()))) {
                    throw new IOException(
                            "Cannot read Windows directory attributes, error="
                                    + Native.getLastError());
                }
                tagInfo.read();
                if ((tagInfo.FileAttributes & WinNT.FILE_ATTRIBUTE_DIRECTORY) == 0
                        || (tagInfo.FileAttributes
                                & WinNT.FILE_ATTRIBUTE_REPARSE_POINT) != 0) {
                    throw new SecurityException("Windows reparse directory rejected");
                }
                var fileIdInfo = new WinBase.FILE_ID_INFO();
                fileIdInfo.write();
                if (!Kernel32.INSTANCE.GetFileInformationByHandleEx(
                        handle,
                        WinBase.FileIdInfo,
                        fileIdInfo.getPointer(),
                        new WinDef.DWORD(fileIdInfo.size()))) {
                    throw new IOException(
                            "Cannot read Windows directory identity, error="
                                    + Native.getLastError());
                }
                fileIdInfo.read();
                var identifier = new byte[fileIdInfo.FileId.Identifier.length];
                for (var index = 0; index < identifier.length; index++) {
                    identifier[index] =
                            fileIdInfo.FileId.Identifier[index].byteValue();
                }
                return new WindowsDirectoryGuard(
                        handle,
                        new WindowsFileIdentity(
                                fileIdInfo.VolumeSerialNumber,
                                HexFormat.of().formatHex(identifier)));
            } catch (IOException | RuntimeException exception) {
                Kernel32.INSTANCE.CloseHandle(handle);
                throw exception;
            }
        }

        static WindowsFileIdentity captureIdentity(Path directory)
                throws IOException {
            try (var guard = open(directory)) {
                return guard.identity();
            }
        }

        WindowsFileIdentity identity() {
            return identity;
        }

        @Override
        public void close() {
            Kernel32.INSTANCE.CloseHandle(handle);
        }
    }

    interface StableOpenObserver {

        StableOpenObserver NOOP = new StableOpenObserver() {};

        default void beforeFileOpen() throws IOException {}

        default void afterFileOpen() throws IOException {}
    }
}
