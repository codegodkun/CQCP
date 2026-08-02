package com.cqcp.apiserver.reviewengine;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuleSetActivationGateTest {

    private static final String BAD_JSON = "{ not valid }";

    static ClassLoader buildFixtureClassLoader(Path tempDir, String manifestContent, String reviewPointsContent) {
        try {
            Path assetsDir = tempDir.resolve("cqcp/review-assets");
            Path ruleSetsDir = assetsDir.resolve("rule-sets");
            Path rpDir = assetsDir.resolve("review-point-definitions");
            Files.createDirectories(ruleSetsDir);
            Files.createDirectories(rpDir);
            Files.writeString(ruleSetsDir.resolve("ruleset-v20260715.1.json"), manifestContent, StandardCharsets.UTF_8);
            Files.writeString(rpDir.resolve("review-points-v20260715.1.json"), reviewPointsContent, StandardCharsets.UTF_8);
            return new URLClassLoader(new URL[]{tempDir.toUri().toURL()}, null);
        } catch (MalformedURLException e) {
            throw new UncheckedIOException(e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void legacyV20260705_1AllowedWithoutPolicyLoad(@TempDir Path tempDir) {
        var cl = buildFixtureClassLoader(tempDir, BAD_JSON, BAD_JSON);
        // even though assets are broken, legacy branch never loads them
        var gate = new RuleSetActivationGate(new RuntimeRuleSetLoader(cl));
        var result = gate.request("v20260705.1", false);
        assertThat(result.status()).isEqualTo(RuleSetActivationGate.LEGACY_ALLOWED);
        assertThat(result.snapshot()).isNull();
    }

    @Test
    void legacyAllowedWithDamagedNewAssets(@TempDir Path tempDir) {
        var cl = buildFixtureClassLoader(tempDir, BAD_JSON, BAD_JSON);
        var gate = new RuleSetActivationGate(new RuntimeRuleSetLoader(cl));
        var result = gate.request("v20260705.1", true);
        assertThat(result.status()).isEqualTo(RuleSetActivationGate.LEGACY_ALLOWED);
        assertThat(result.snapshot()).isNull();
    }

    @Test
    void v20260715_1ReadyFalseReturnsPolicyNotReady(@TempDir Path tempDir) {
        var cl = buildFixtureClassLoader(tempDir, BAD_JSON, BAD_JSON);
        var gate = new RuleSetActivationGate(new RuntimeRuleSetLoader(cl));
        var result = gate.request("v20260715.1", false);
        assertThat(result.status()).isEqualTo(RuleSetActivationGate.POLICY_NOT_READY);
        assertThat(result.snapshot()).isNull();
    }

    @Test
    void v20260715_1ReadyTrueValidAssetsReturnsReadyWithSnapshot(@TempDir Path tempDir) {
        var cl = RuntimeRuleSetLoaderTest.buildClassLoader(tempDir,
            RuntimeRuleSetLoaderTest.MANIFEST_JSON,
            RuntimeRuleSetLoaderTest.reviewPointsJson(RuntimeRuleSetLoaderTest.allNinePoints()));
        var gate = new RuleSetActivationGate(new RuntimeRuleSetLoader(cl));
        var result = gate.request("v20260715.1", true);
        assertThat(result.status()).isEqualTo(RuleSetActivationGate.READY);
        assertThat(result.snapshot()).isNotNull();
        assertThat(result.snapshot().policyMap()).hasSize(9);
    }

    @Test
    void v20260715_1ReadyTrueDamagedAssetsReturnsPolicyAssetInvalid(@TempDir Path tempDir) {
        var cl = buildFixtureClassLoader(tempDir, BAD_JSON, BAD_JSON);
        var gate = new RuleSetActivationGate(new RuntimeRuleSetLoader(cl));
        var result = gate.request("v20260715.1", true);
        assertThat(result.status()).isEqualTo(RuleSetActivationGate.POLICY_ASSET_INVALID);
        assertThat(result.snapshot()).isNull();
    }

    @Test
    void nullVersionReturnsUnknownRuleSetVersion() {
        var gate = new RuleSetActivationGate();
        var result = gate.request(null, true);
        assertThat(result.status()).isEqualTo(RuleSetActivationGate.UNKNOWN_RULE_SET_VERSION);
        assertThat(result.snapshot()).isNull();
    }

    @Test
    void blankVersionReturnsUnknownRuleSetVersion() {
        var gate = new RuleSetActivationGate();
        var result = gate.request("   ", true);
        assertThat(result.status()).isEqualTo(RuleSetActivationGate.UNKNOWN_RULE_SET_VERSION);
        assertThat(result.snapshot()).isNull();
    }

    @Test
    void unknownVersionReturnsUnknownRuleSetVersion() {
        var gate = new RuleSetActivationGate();
        var result = gate.request("v2099.1", true);
        assertThat(result.status()).isEqualTo(RuleSetActivationGate.UNKNOWN_RULE_SET_VERSION);
        assertThat(result.snapshot()).isNull();
    }

    @Test
    void damagedV20260715_1DoesNotFallBackToLegacy(@TempDir Path tempDir) {
        var cl = buildFixtureClassLoader(tempDir, BAD_JSON, BAD_JSON);
        var gate = new RuleSetActivationGate(new RuntimeRuleSetLoader(cl));
        var result = gate.request("v20260715.1", true);
        // must not fall back to LEGACY_ALLOWED
        assertThat(result.status()).isNotEqualTo(RuleSetActivationGate.LEGACY_ALLOWED);
        assertThat(result.status()).isEqualTo(RuleSetActivationGate.POLICY_ASSET_INVALID);
    }
}
