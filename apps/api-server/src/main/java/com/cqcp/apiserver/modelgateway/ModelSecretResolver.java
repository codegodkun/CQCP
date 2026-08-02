package com.cqcp.apiserver.modelgateway;

import com.sun.nio.file.ExtendedOpenOption;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
final class ModelSecretResolver {

    private static final Pattern ENV_NAME =
            Pattern.compile("^CQCP_MODEL_[A-Z0-9_]{1,52}$");
    private static final long MAX_SECRET_BYTES = 16 * 1024;
    private static final boolean WINDOWS =
            System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win");

    private final Path configuredFileRoot;
    private final Function<String, String> environmentLookup;
    private final StableRootIdentity configuredRootIdentity;
    private final StableOpenObserver stableOpenObserver;

    @Autowired
    ModelSecretResolver(@Value("${cqcp.model-gateway.secret-file-root}") Path fileRoot) {
        this(fileRoot, System::getenv);
    }

    ModelSecretResolver(Path fileRoot, Function<String, String> environmentLookup) {
        this(fileRoot, environmentLookup, StableOpenObserver.NOOP);
    }

    ModelSecretResolver(
            Path fileRoot,
            Function<String, String> environmentLookup,
            StableOpenObserver stableOpenObserver) {
        this.configuredFileRoot = Objects.requireNonNull(fileRoot, "fileRoot")
                .toAbsolutePath()
                .normalize();
        this.environmentLookup = Objects.requireNonNull(environmentLookup, "environmentLookup");
        this.stableOpenObserver =
                Objects.requireNonNull(stableOpenObserver, "stableOpenObserver");
        this.configuredRootIdentity = captureRootIdentity(configuredFileRoot);
    }

    Optional<String> resolve(String secretRef) {
        if (secretRef == null || secretRef.isBlank() || hasLineBreak(secretRef)) {
            return Optional.empty();
        }
        if (secretRef.startsWith("env:")) {
            var name = secretRef.substring("env:".length());
            if (!ENV_NAME.matcher(name).matches()) return Optional.empty();
            return nonBlank(environmentLookup.apply(name));
        }
        if (secretRef.startsWith("file:")) {
            return readSecretFile(secretRef.substring("file:".length()));
        }
        return Optional.empty();
    }

    boolean isValidReference(String secretRef) {
        if (secretRef == null || secretRef.isBlank() || hasLineBreak(secretRef)) return false;
        if (secretRef.startsWith("env:")) {
            return ENV_NAME.matcher(secretRef.substring("env:".length())).matches();
        }
        if (secretRef.startsWith("file:")) {
            try {
                var path = Path.of(secretRef.substring("file:".length()));
                var normalized = path.toAbsolutePath().normalize();
                return path.isAbsolute()
                        && normalized.getParent() != null
                        && normalized.getParent().equals(configuredFileRoot)
                        && normalized.getFileName() != null;
            } catch (RuntimeException exception) {
                return false;
            }
        }
        return false;
    }

    private Optional<String> readSecretFile(String rawPath) {
        try {
            var requested = Path.of(rawPath);
            if (!requested.isAbsolute()) return Optional.empty();
            var normalized = requested.toAbsolutePath().normalize();
            if (normalized.getParent() == null
                    || !normalized.getParent().equals(configuredFileRoot)
                    || normalized.getFileName() == null
                    || configuredRootIdentity == null) {
                return Optional.empty();
            }
            var beforeRoot = readSafeAttributes(configuredFileRoot);
            var beforeFile = readSafeAttributes(normalized);
            if (!configuredRootIdentity.matches(beforeRoot)
                    || !beforeFile.isRegularFile()) {
                return Optional.empty();
            }
            var fileIdentity = StablePathIdentity.of(beforeFile);
            if (WINDOWS) {
                try (var channel = FileChannel.open(
                        normalized,
                        StandardOpenOption.READ,
                        LinkOption.NOFOLLOW_LINKS,
                        ExtendedOpenOption.NOSHARE_DELETE)) {
                    stableOpenObserver.afterOpen();
                    verifyCurrentIdentities(normalized, fileIdentity);
                    return decodeSecret(channel);
                }
            }
            try (DirectoryStream<Path> rootStream =
                    Files.newDirectoryStream(configuredFileRoot)) {
                if (!(rootStream instanceof SecureDirectoryStream<Path> secureRoot)) {
                    return Optional.empty();
                }
                var openedRoot = secureAttributes(secureRoot, Path.of("."));
                if (!configuredRootIdentity.matches(openedRoot)) {
                    return Optional.empty();
                }
                var fileName = normalized.getFileName();
                var openedFile = secureAttributes(secureRoot, fileName);
                if (!fileIdentity.matches(openedFile) || !openedFile.isRegularFile()) {
                    return Optional.empty();
                }
                try (var channel = secureRoot.newByteChannel(
                        fileName,
                        Set.of(
                                StandardOpenOption.READ,
                                LinkOption.NOFOLLOW_LINKS))) {
                    stableOpenObserver.afterOpen();
                    verifyCurrentIdentities(normalized, fileIdentity);
                    return decodeSecret(channel);
                }
            }
        } catch (IOException | RuntimeException exception) {
            return Optional.empty();
        }
    }

    private Optional<String> decodeSecret(SeekableByteChannel channel) throws IOException {
        var bytes = ByteBuffer.allocate(Math.toIntExact(MAX_SECRET_BYTES + 1));
        while (bytes.hasRemaining()) {
            var read = channel.read(bytes);
            if (read < 0) break;
            if (read == 0) {
                Thread.onSpinWait();
            }
        }
        if (bytes.position() > MAX_SECRET_BYTES) return Optional.empty();
        bytes.flip();
        var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        return nonBlank(decoder.decode(bytes).toString());
    }

    private void verifyCurrentIdentities(
            Path filePath,
            StablePathIdentity fileIdentity) throws IOException {
        var currentRoot = readSafeAttributes(configuredFileRoot);
        var currentFile = readSafeAttributes(filePath);
        if (!configuredRootIdentity.matches(currentRoot)
                || !fileIdentity.matches(currentFile)
                || !currentFile.isRegularFile()) {
            throw new SecurityException("Secret path identity changed");
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
        return requireSafeAttributes(view.readAttributes());
    }

    private static BasicFileAttributes readSafeAttributes(Path path) throws IOException {
        return requireSafeAttributes(Files.readAttributes(
                path,
                BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS));
    }

    private static BasicFileAttributes requireSafeAttributes(
            BasicFileAttributes attributes) {
        if (attributes.isSymbolicLink() || attributes.isOther()) {
            throw new SecurityException("Secret reparse path rejected");
        }
        return attributes;
    }

    private static StableRootIdentity captureRootIdentity(Path root) {
        try {
            var attributes = readSafeAttributes(root);
            if (!attributes.isDirectory()) return null;
            return StableRootIdentity.of(attributes);
        } catch (IOException | RuntimeException exception) {
            return null;
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

    @FunctionalInterface
    interface StableOpenObserver {

        StableOpenObserver NOOP = () -> {};

        void afterOpen() throws IOException;
    }

    private static Optional<String> nonBlank(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        return Optional.of(value.trim());
    }

    private static boolean hasLineBreak(String value) {
        return value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0;
    }
}
