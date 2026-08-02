package com.cqcp.apiserver.modelgateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ModelSecretResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void resolvesOnlyValidEnvironmentReferences() {
        var values = Map.of("CQCP_MODEL_DEEPSEEK_API_KEY", "sentinel-secret");
        var resolver = new ModelSecretResolver(tempDir, values::get);

        assertThat(resolver.resolve("env:CQCP_MODEL_DEEPSEEK_API_KEY"))
                .contains("sentinel-secret");
        assertThat(resolver.resolve("env:bad")).isEmpty();
        assertThat(resolver.resolve("sentinel-secret")).isEmpty();
        assertThat(resolver.isValidReference("env:CQCP_MODEL_DEEPSEEK_API_KEY"))
                .isTrue();
        assertThat(resolver.isValidReference("sentinel-secret")).isFalse();
    }

    @Test
    void resolvesRegularFileOnlyInsideConfiguredRoot() throws Exception {
        var secretFile = tempDir.resolve("deepseek");
        Files.writeString(secretFile, " file-sentinel \n");
        var outside = Files.createTempFile("cqcp-outside-secret", ".txt");
        Files.writeString(outside, "outside");
        var resolver = new ModelSecretResolver(tempDir, ignored -> null);

        assertThat(resolver.resolve("file:" + secretFile)).contains("file-sentinel");
        assertThat(resolver.resolve("file:" + outside)).isEmpty();
    }

    @Test
    void rejectsOversizedAndCrLfReferences() throws Exception {
        var oversized = tempDir.resolve("oversized");
        Files.write(oversized, new byte[16 * 1024 + 1]);
        var resolver = new ModelSecretResolver(tempDir, ignored -> null);

        assertThat(resolver.resolve("file:" + oversized)).isEmpty();
        assertThat(resolver.resolve("env:CQCP_MODEL_DEEPSEEK_API_KEY\r\nX")).isEmpty();
    }

    @Test
    void acceptsExactLimitAndRejectsMalformedUtf8() throws Exception {
        var exact = tempDir.resolve("exact");
        var malformed = tempDir.resolve("malformed");
        Files.write(exact, "a".repeat(16 * 1024).getBytes(StandardCharsets.UTF_8));
        Files.write(malformed, new byte[] {(byte) 0xc3, 0x28});
        var resolver = new ModelSecretResolver(tempDir, ignored -> null);

        assertThat(resolver.resolve("file:" + exact))
                .contains("a".repeat(16 * 1024));
        assertThat(resolver.resolve("file:" + malformed)).isEmpty();
        assertThat(resolver.isValidReference(
                        "file:" + tempDir.resolve("nested").resolve("secret")))
                .isFalse();
    }

    @Test
    void rootReplacementAfterStableOpenFailsClosed() throws Exception {
        var configuredRoot = tempDir.resolve("configured-root");
        var movedRoot = tempDir.resolve("moved-root");
        var outsideRoot = tempDir.resolve("outside-root");
        Files.createDirectories(configuredRoot);
        Files.createDirectories(outsideRoot);
        var secretName = "deepseek";
        Files.writeString(configuredRoot.resolve(secretName), "inside-secret");
        Files.writeString(outsideRoot.resolve(secretName), "outside-secret");
        var observerInvoked = new AtomicBoolean();
        var resolver = new ModelSecretResolver(
                configuredRoot,
                ignored -> null,
                () -> {
                    observerInvoked.set(true);
                    Files.move(configuredRoot, movedRoot);
                    createDirectoryAlias(configuredRoot, outsideRoot);
                });

        try {
            assertThat(resolver.resolve("file:" + configuredRoot.resolve(secretName)))
                    .isEmpty();
            assertThat(observerInvoked).isTrue();
        } finally {
            deleteDirectoryAliasIfPresent(configuredRoot);
            if (Files.exists(movedRoot) && !Files.exists(configuredRoot)) {
                Files.move(movedRoot, configuredRoot);
            }
        }
    }

    @Test
    void rejectsSymlinkedSecretFile() throws Exception {
        var realSecret = tempDir.resolve("real-secret");
        var link = tempDir.resolve("linked-secret");
        Files.writeString(realSecret, "symlink-sentinel");
        try {
            Files.createSymbolicLink(link, realSecret);
        } catch (Exception exception) {
            assumeTrue(false, "Cannot create symlink on this platform: " + exception.getMessage());
            return;
        }
        var resolver = new ModelSecretResolver(tempDir, ignored -> null);

        assertThat(resolver.resolve("file:" + link)).isEmpty();
    }

    @Test
    void rejectsWindowsJunctionInsideConfiguredSecretRoot() throws Exception {
        assumeTrue(
                System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win"),
                "Windows junction coverage only");
        var outside = Files.createTempDirectory("cqcp-outside-secret-junction");
        var secret = outside.resolve("deepseek");
        Files.writeString(secret, "junction-secret-sentinel");
        var junction = tempDir.resolve("provider");
        var process = new ProcessBuilder(
                "cmd.exe",
                "/d",
                "/c",
                "mklink",
                "/J",
                junction.toString(),
                outside.toString())
                .redirectErrorStream(true)
                .start();
        var output = new String(
                process.getInputStream().readAllBytes(),
                java.nio.charset.Charset.defaultCharset());
        assertThat(process.waitFor())
                .as("mklink /J must create the test reparse point: %s", output)
                .isZero();
        try {
            var resolver = new ModelSecretResolver(tempDir, ignored -> null);
            assertThat(resolver.resolve("file:" + junction.resolve("deepseek"))).isEmpty();
        } finally {
            Files.deleteIfExists(junction);
            Files.deleteIfExists(secret);
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void rejectsConfiguredSecretRootWhenRootItselfIsWindowsJunction() throws Exception {
        assumeTrue(
                System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win"),
                "Windows junction coverage only");
        var targetRoot = Files.createTempDirectory("cqcp-secret-root-target");
        var secret = targetRoot.resolve("deepseek");
        Files.writeString(secret, "configured-root-junction-sentinel");
        var junctionRoot = tempDir.resolve("configured-root-junction");
        createDirectoryAlias(junctionRoot, targetRoot);

        try {
            var resolver = new ModelSecretResolver(junctionRoot, ignored -> null);
            var reference = "file:" + junctionRoot.resolve("deepseek");

            assertThat(resolver.isValidReference(reference)).isTrue();
            assertThat(resolver.resolve(reference)).isEmpty();
        } finally {
            deleteDirectoryAliasIfPresent(junctionRoot);
            Files.deleteIfExists(secret);
            Files.deleteIfExists(targetRoot);
        }
    }

    private static void createDirectoryAlias(Path alias, Path target)
            throws java.io.IOException {
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
                    throw new java.io.IOException("mklink /J failed: " + output);
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new java.io.IOException("mklink /J interrupted", exception);
            }
            return;
        }
        Files.createSymbolicLink(alias, target);
    }

    private static void deleteDirectoryAliasIfPresent(Path alias) throws Exception {
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
