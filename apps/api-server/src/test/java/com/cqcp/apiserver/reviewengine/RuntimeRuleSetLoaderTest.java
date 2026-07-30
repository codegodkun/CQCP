package com.cqcp.apiserver.reviewengine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class RuntimeRuleSetLoaderTest {

    static final String BINDING_NOTE_RULESET =
        "B1 \\u9759\\u6001 policy \\u6e90\\uff1b\\u672a\\u7ed1\\u5b9a runtime loader\\uff0c\\u4e0d\\u5bf9\\u4efb\\u4f55\\u4efb\\u52a1\\u751f\\u6548\\u3002";
    static final String BINDING_NOTE_RP =
        "B1 \\u9759\\u6001 consistency policy \\u6e90\\uff1b\\u672a\\u7ed1\\u5b9a runtime\\uff0c\\u4e0d\\u5bf9\\u4efb\\u4f55\\u4efb\\u52a1\\u751f\\u6548\\u3002\\u5ba1\\u6838\\u70b9\\u5b9a\\u4e49\\u6765\\u81ea ADR-016 \\u51bb\\u7ed3\\u7684\\u4e5d\\u70b9\\u591a\\u51fa\\u5904\\u4e00\\u81f4\\u6027\\u8bed\\u4e49\\u3002";

    static final String MANIFEST_JSON = "{"
        + "\"assetId\":\"cqcp.ruleset.mvp.consistency-set.v20260715.1\","
        + "\"assetType\":\"RULE_SET\","
        + "\"version\":\"v20260715.1\","
        + "\"status\":\"DRAFT\","
        + "\"source\":{"
        + "\"type\":\"architecture-approved-policy\","
        + "\"paths\":[\"decisions/ADR-016-multi-occurrence-consistency-evidence-preservation.md\",\"docs/ARCHITECTURE.md\",\"tasks/active/TASK_SPEC-036-B1-versioned-consistency-policy.md\"],"
        + "\"runtimeBinding\":\"NOT_BOUND\","
        + "\"bindingNote\":\"" + BINDING_NOTE_RULESET + "\""
        + "},"
        + "\"changeReason\":\"test\","
        + "\"createdAt\":\"2026-07-15\","
        + "\"moduleVersions\":{"
        + "\"reviewPointDefinitions\":{\"assetId\":\"cqcp.review-points.mvp.consistency-set.v20260715.1\",\"version\":\"v20260715.1\",\"path\":\"../review-point-definitions/review-points-v20260715.1.json\"},"
        + "\"patternLibrary\":{\"assetId\":\"cqcp.pattern-library.mvp.code-current.v20260705.1\",\"version\":\"v20260705.1\",\"path\":\"../pattern-libraries/pattern-library-v20260705.1.json\"},"
        + "\"fieldLexicon\":{\"assetId\":\"cqcp.field-lexicon.mvp.code-current.v20260705.1\",\"version\":\"v20260705.1\",\"path\":\"../field-lexicons/field-lexicon-v20260705.1.json\"},"
        + "\"promptTemplates\":{\"assetId\":\"cqcp.prompts.mvp.code-current.v20260705.1\",\"version\":\"v20260705.1\",\"path\":\"../prompts/prompts-v20260705.1.json\"},"
        + "\"contractTypeProfiles\":{\"assetId\":\"cqcp.contract-type-profile.mvp.code-current.v20260705.1\",\"version\":\"v20260705.1\",\"path\":\"../contract-type-profiles/contract-type-profile-v20260705.1.json\"},"
        + "\"evidenceSelectors\":{\"assetId\":\"cqcp.evidence-selector.mvp.code-current.v20260705.1\",\"version\":\"v20260705.1\",\"path\":\"../evidence-selectors/evidence-selector-v20260705.1.json\"}"
        + "},"
        + "\"runtimePolicy\":{\"loaderEnabled\":false,\"databasePersistence\":false,\"productionEffect\":\"NONE\",\"notes\":[\"test\"]}"
        + "}";

    static final String PARTY_A_SEMANTIC = "[\"CONTRACT_TITLE_NAME_MENTION\",\"AGREEMENT_PREAMBLE_CONTRACT_NAME_MENTION\"]";

    static String pointJson(String code, String valueType, String unit, String semanticExclusionsJson) {
        return "{\"reviewPointCode\":\"" + code + "\",\"name\":\"test\",\"family\":\"PARTY_FIELDS\","
            + "\"candidateRole\":\"TEST\",\"requiredStructuredFields\":[\"testField\"],"
            + "\"executionStrategy\":\"PATTERN_EXTRACT_THEN_COMPARE\","
            + "\"deterministicRule\":\"test\",\"currentRuntimeSource\":\"test\","
            + "\"consistencyPolicy\":{"
            + "\"cardinalityMode\":\"CONSISTENCY_SET\",\"minCandidates\":1,\"maxCandidates\":8,\"occurrenceBudget\":64,"
            + "\"scopePolicy\":{"
            + "\"version\":\"consistency-scope-v20260715.1\","
            + "\"includedRegionTypes\":[\"BODY\",\"APPENDIX\"],"
            + "\"strongExcludedContextTypes\":[\"TOC\",\"HEADER_FOOTER\",\"DELETED\",\"VOIDED\"],"
            + "\"requiredAttributionSignals\":[\"SOURCE_CONFIDENCE\",\"PARSE_CONFIDENCE\",\"VALUE_GRAMMAR\",\"ROLE_LABEL\",\"REGION_CONTEXT\",\"ANCHOR_IDENTITY\"],"
            + "\"strongExcludedSemanticContexts\":" + semanticExclusionsJson
            + "},"
            + "\"canonicalizationPolicy\":{\"version\":\"consistency-canonicalization-v20260715.1\",\"valueType\":\"" + valueType + "\",\"unit\":\"" + unit + "\"},"
            + "\"anchorIdentityPolicy\":{\"version\":\"mvp-occurrence-identity-v1\",\"blockIdentity\":[\"reviewPointCode\",\"blockId\"],\"tableCellIdentity\":[\"reviewPointCode\",\"blockId\",\"previewElementRef\"]}"
            + "}}";
    }

    static String reviewPointsJson(String points) {
        return "{\"assetId\":\"cqcp.review-points.mvp.consistency-set.v20260715.1\","
            + "\"assetType\":\"REVIEW_POINT_DEFINITION\","
            + "\"version\":\"v20260715.1\","
            + "\"status\":\"DRAFT\","
            + "\"source\":{"
            + "\"type\":\"architecture-approved-policy\","
            + "\"paths\":[\"decisions/ADR-016-multi-occurrence-consistency-evidence-preservation.md\",\"docs/ARCHITECTURE.md\",\"tasks/active/TASK_SPEC-036-B1-versioned-consistency-policy.md\"],"
            + "\"runtimeBinding\":\"NOT_BOUND\","
            + "\"bindingNote\":\"" + BINDING_NOTE_RP + "\""
            + "},"
            + "\"changeReason\":\"test\",\"createdAt\":\"2026-07-15\","
            + "\"contractTypes\":[\"ENGINEERING_PROCUREMENT\"],"
            + "\"reviewPoints\":[" + points + "]}";
    }

    static ClassLoader buildClassLoader(Path tempDir, String manifestContent, String reviewPointsContent) {
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

    static String allNinePoints() {
        return String.join(",",
            pointJson("PARTY_A_NAME_CONSISTENCY", "TEXT", "NONE", PARTY_A_SEMANTIC),
            pointJson("PARTY_B_NAME_CONSISTENCY", "TEXT", "NONE", "[]"),
            pointJson("CONTRACT_TOTAL_AMOUNT_CONSISTENCY", "DECIMAL", "CNY", "[]"),
            pointJson("TAX_AMOUNT_FORMULA_CONSISTENCY", "DECIMAL", "CNY", "[]"),
            pointJson("PREPAYMENT_RATIO_CONSISTENCY", "DECIMAL", "PERCENT", "[]"),
            pointJson("PROGRESS_PAYMENT_RATIO_CONSISTENCY", "DECIMAL", "PERCENT", "[]"),
            pointJson("COMPLETION_PAYMENT_RATIO_CONSISTENCY", "DECIMAL", "PERCENT", "[]"),
            pointJson("SETTLEMENT_PAYMENT_RATIO_CONSISTENCY", "DECIMAL", "PERCENT", "[]"),
            pointJson("WARRANTY_RETENTION_RATIO_CONSISTENCY", "DECIMAL", "PERCENT", "[]")
        );
    }

    static String withCode(Vcn vcn) {
        return vcn.valueType + ":" + vcn.unit;
    }

    static final String[] CODE_NAMES = {
        "PARTY_A_NAME_CONSISTENCY", "PARTY_B_NAME_CONSISTENCY",
        "CONTRACT_TOTAL_AMOUNT_CONSISTENCY", "TAX_AMOUNT_FORMULA_CONSISTENCY",
        "PREPAYMENT_RATIO_CONSISTENCY", "PROGRESS_PAYMENT_RATIO_CONSISTENCY",
        "COMPLETION_PAYMENT_RATIO_CONSISTENCY", "SETTLEMENT_PAYMENT_RATIO_CONSISTENCY",
        "WARRANTY_RETENTION_RATIO_CONSISTENCY"
    };

    static final Map<String, Vcn> CANONICAL = Map.of(
        "PARTY_A_NAME_CONSISTENCY", new Vcn("TEXT", "NONE"),
        "PARTY_B_NAME_CONSISTENCY", new Vcn("TEXT", "NONE"),
        "CONTRACT_TOTAL_AMOUNT_CONSISTENCY", new Vcn("DECIMAL", "CNY"),
        "TAX_AMOUNT_FORMULA_CONSISTENCY", new Vcn("DECIMAL", "CNY"),
        "PREPAYMENT_RATIO_CONSISTENCY", new Vcn("DECIMAL", "PERCENT"),
        "PROGRESS_PAYMENT_RATIO_CONSISTENCY", new Vcn("DECIMAL", "PERCENT"),
        "COMPLETION_PAYMENT_RATIO_CONSISTENCY", new Vcn("DECIMAL", "PERCENT"),
        "SETTLEMENT_PAYMENT_RATIO_CONSISTENCY", new Vcn("DECIMAL", "PERCENT"),
        "WARRANTY_RETENTION_RATIO_CONSISTENCY", new Vcn("DECIMAL", "PERCENT")
    );

    record Vcn(String valueType, String unit) {}

    record ModuleRefEntry(String assetId, String version, String path) {}

    enum RefFieldVariant { MISSING, NON_TEXT }

    private static final Map<String, ModuleRefEntry> MODULE_REF_ENTRIES = Map.of(
        "reviewPointDefinitions", new ModuleRefEntry("cqcp.review-points.mvp.consistency-set.v20260715.1", "v20260715.1",
            "../review-point-definitions/review-points-v20260715.1.json"),
        "patternLibrary", new ModuleRefEntry("cqcp.pattern-library.mvp.code-current.v20260705.1", "v20260705.1",
            "../pattern-libraries/pattern-library-v20260705.1.json"),
        "fieldLexicon", new ModuleRefEntry("cqcp.field-lexicon.mvp.code-current.v20260705.1", "v20260705.1",
            "../field-lexicons/field-lexicon-v20260705.1.json"),
        "promptTemplates", new ModuleRefEntry("cqcp.prompts.mvp.code-current.v20260705.1", "v20260705.1",
            "../prompts/prompts-v20260705.1.json"),
        "contractTypeProfiles", new ModuleRefEntry("cqcp.contract-type-profile.mvp.code-current.v20260705.1", "v20260705.1",
            "../contract-type-profiles/contract-type-profile-v20260705.1.json"),
        "evidenceSelectors", new ModuleRefEntry("cqcp.evidence-selector.mvp.code-current.v20260705.1", "v20260705.1",
            "../evidence-selectors/evidence-selector-v20260705.1.json"));

    static final String VALID_MANIFEST = MANIFEST_JSON;
    static final String VALID_RP = reviewPointsJson(allNinePoints());

    // ── 1. real classpath ──

    @Test
    void loadsV20260715ManifestFromRealClasspath() {
        var snapshot = new RuntimeRuleSetLoader().load("v20260715.1");
        assertThat(snapshot.policyMap()).hasSize(9);
        assertThat(snapshot.version()).isEqualTo("v20260715.1");
    }

    @Test
    void unknownVersionReturnsAssetIdentityInvalid(@TempDir Path tempDir) {
        var cl = buildClassLoader(tempDir, VALID_MANIFEST, VALID_RP);
        assertThatThrownBy(() -> new RuntimeRuleSetLoader(cl).load("v2099.1"))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class)
            .extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.ASSET_IDENTITY_INVALID);
    }

    @Test
    void nullVersionReturnsAssetIdentityInvalid(@TempDir Path tempDir) {
        var cl = buildClassLoader(tempDir, VALID_MANIFEST, VALID_RP);
        assertThatThrownBy(() -> new RuntimeRuleSetLoader(cl).load(null))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class)
            .extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.ASSET_IDENTITY_INVALID);
    }

    // ── 2. immutability ──

    @Test
    void policyMapIsUnmodifiable(@TempDir Path tempDir) {
        var snapshot = loadValid(tempDir);
        var map = snapshot.policyMap();
        assertThatThrownBy(() -> map.put(ReviewPointCode.PARTY_A_NAME_CONSISTENCY, null))
            .isInstanceOf(UnsupportedOperationException.class);
        assertThat(snapshot.policyMap()).containsOnlyKeys(ReviewPointCode.values());
    }

    @Test
    void nestedListsAreUnmodifiable(@TempDir Path tempDir) {
        var policy = loadValid(tempDir).policyMap().get(ReviewPointCode.PARTY_A_NAME_CONSISTENCY);
        assertThatThrownBy(() -> policy.includedRegionTypes().add("EXTRA"))
            .isInstanceOf(UnsupportedOperationException.class);
        assertThat(policy.includedRegionTypes()).containsExactly("BODY", "APPENDIX");
        assertThatThrownBy(() -> policy.strongExcludedContextTypes().add("EXTRA"))
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> policy.requiredAttributionSignals().add("EXTRA"))
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> policy.blockIdentity().add("EXTRA"))
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> policy.tableCellIdentity().add("EXTRA"))
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> policy.strongExcludedSemanticContexts().add("EXTRA"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    // ── 3. path resolution ──

    @Test
    void absolutePathReturnsRefPathInvalid(@TempDir Path tempDir) {
        String manifest = VALID_MANIFEST.replace(
            "\"../review-point-definitions/review-points-v20260715.1.json\"",
            "\"/absolute/path/foo.json\"");
        assertThatThrownBy(() -> load(manifest, VALID_RP, tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class)
            .extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.REFERENCE_PATH_INVALID);
    }

    @Test
    void urlSchemeReturnsRefPathInvalid(@TempDir Path tempDir) {
        String manifest = VALID_MANIFEST.replace(
            "\"../review-point-definitions/review-points-v20260715.1.json\"",
            "\"file:///etc/passwd\"");
        assertThatThrownBy(() -> load(manifest, VALID_RP, tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class).extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.REFERENCE_PATH_INVALID);
    }

    @Test
    void escapeOutsidePrefixReturnsRefPathInvalid(@TempDir Path tempDir) {
        String manifest = VALID_MANIFEST.replace(
            "\"../review-point-definitions/review-points-v20260715.1.json\"",
            "\"../../outside/escape.json\"");
        assertThatThrownBy(() -> load(manifest, VALID_RP, tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class).extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.REFERENCE_PATH_INVALID);
    }

    @Test
    void mismatchedTargetPathReturnsRefTargetMismatch(@TempDir Path tempDir) {
        String manifest = VALID_MANIFEST.replace(
            "\"../review-point-definitions/review-points-v20260715.1.json\"",
            "\"../pattern-libraries/pattern-library-v20260705.1.json\"");
        assertThatThrownBy(() -> load(manifest, VALID_RP, tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class).extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.REFERENCE_TARGET_MISMATCH);
    }

    @Test
    void pathEscapeCheckedBeforeIdentity(@TempDir Path tempDir) {
        String manifest = VALID_MANIFEST.replace(
            "\"cqcp.ruleset.mvp.consistency-set.v20260715.1\"", "\"wrong.id\"")
            .replace("\"../review-point-definitions/review-points-v20260715.1.json\"",
                "\"../../../outside/nonexistent.json\"");
        assertThatThrownBy(() -> load(manifest, VALID_RP, tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class).extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.REFERENCE_PATH_INVALID);
    }

    @Test
    void manifestNotFoundReturnsResourceNotFound(@TempDir Path tempDir) {
        try {
            var cl = new URLClassLoader(new URL[]{tempDir.toUri().toURL()}, null);
            assertThatThrownBy(() -> new RuntimeRuleSetLoader(cl).load("v20260715.1"))
                .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class).extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.RESOURCE_NOT_FOUND);
        } catch (MalformedURLException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ── 4. asset identity & module references ──

    @Test
    void wrongAssetIdReturnsAssetIdentityInvalid(@TempDir Path tempDir) {
        String manifest = VALID_MANIFEST.replace(
            "\"cqcp.ruleset.mvp.consistency-set.v20260715.1\"", "\"wrong.asset.id\"");
        assertThatThrownBy(() -> load(manifest, VALID_RP, tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class).extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.ASSET_IDENTITY_INVALID);
    }

    @Test
    void moduleRefMissingReturnsAssetIdentityInvalid(@TempDir Path tempDir) {
        String manifest = VALID_MANIFEST.replace(
            "\"contractTypeProfiles\":{", "\"contractTypeProfilesX\":{");
        assertThatThrownBy(() -> load(manifest, VALID_RP, tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class).extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.ASSET_IDENTITY_INVALID);
    }

    @Test
    void moduleRefWrongAssetIdReturnsAssetIdentityInvalid(@TempDir Path tempDir) {
        String manifest = VALID_MANIFEST.replace(
            "\"cqcp.pattern-library.mvp.code-current.v20260705.1\"", "\"wrong.id\"");
        assertThatThrownBy(() -> load(manifest, VALID_RP, tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class).extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.ASSET_IDENTITY_INVALID);
    }

    @Test
    void moduleRefWrongVersionReturnsAssetIdentityInvalid(@TempDir Path tempDir) {
        String manifest = VALID_MANIFEST.replace(
            "\"cqcp.pattern-library.mvp.code-current.v20260705.1\",\"version\":\"v20260705.1\"",
            "\"cqcp.pattern-library.mvp.code-current.v20260705.1\",\"version\":\"v2099.1\"");
        assertThatThrownBy(() -> load(manifest, VALID_RP, tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class).extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.ASSET_IDENTITY_INVALID);
    }

    @Test
    void rpDefIdentityWrongReturnsAssetIdentityInvalid(@TempDir Path tempDir) {
        String rp = VALID_RP.replace(
            "\"cqcp.review-points.mvp.consistency-set.v20260715.1\"", "\"wrong.id\"");
        assertThatThrownBy(() -> load(VALID_MANIFEST, rp, tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class).extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.ASSET_IDENTITY_INVALID);
    }

    // ── 5. release state ──

    @Test
    void statusActivatedReturnsAssetReleaseStateInvalid(@TempDir Path tempDir) {
        String manifest = VALID_MANIFEST.replace("\"status\":\"DRAFT\"", "\"status\":\"ACTIVE\"");
        assertThatThrownBy(() -> load(manifest, VALID_RP, tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class).extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.ASSET_RELEASE_STATE_INVALID);
    }

    @Test
    void sourceTypeWrongReturnsAssetReleaseStateInvalid(@TempDir Path tempDir) {
        String manifest = VALID_MANIFEST.replace(
            "\"type\":\"architecture-approved-policy\"", "\"type\":\"code-current-mapping\"");
        assertThatThrownBy(() -> load(manifest, VALID_RP, tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class).extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.ASSET_RELEASE_STATE_INVALID);
    }

    @Test
    void runtimeBindingBoundReturnsAssetReleaseStateInvalid(@TempDir Path tempDir) {
        String manifest = VALID_MANIFEST.replace("\"runtimeBinding\":\"NOT_BOUND\"", "\"runtimeBinding\":\"BOUND\"");
        assertThatThrownBy(() -> load(manifest, VALID_RP, tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class).extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.ASSET_RELEASE_STATE_INVALID);
    }

    @Test
    void rulesetBindingNoteChangedReturnsAssetReleaseStateInvalid(@TempDir Path tempDir) {
        String manifest = VALID_MANIFEST.replace(
            "\"bindingNote\":\"" + BINDING_NOTE_RULESET + "\"", "\"bindingNote\":\"changed\"");
        assertThatThrownBy(() -> load(manifest, VALID_RP, tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class).extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.ASSET_RELEASE_STATE_INVALID);
    }

    @Test
    void rpBindingNoteChangedReturnsAssetReleaseStateInvalid(@TempDir Path tempDir) {
        String rp = VALID_RP.replace(
            "\"bindingNote\":\"" + BINDING_NOTE_RP + "\"", "\"bindingNote\":\"changed\"");
        assertThatThrownBy(() -> load(VALID_MANIFEST, rp, tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class).extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.ASSET_RELEASE_STATE_INVALID);
    }

    @Test
    void rulesetSourcePathsMissingReturnsAssetReleaseStateInvalid(@TempDir Path tempDir) {
        String manifest = VALID_MANIFEST.replace(
            "\"docs/ARCHITECTURE.md\",\"tasks/active/TASK_SPEC-036-B1-versioned-consistency-policy.md\"",
            "\"tasks/active/TASK_SPEC-036-B1-versioned-consistency-policy.md\"");
        assertThatThrownBy(() -> load(manifest, VALID_RP, tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class).extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.ASSET_RELEASE_STATE_INVALID);
    }

    @Test
    void rulesetSourcePathsExtraReturnsAssetReleaseStateInvalid(@TempDir Path tempDir) {
        String manifest = VALID_MANIFEST.replace(
            "\"tasks/active/TASK_SPEC-036-B1-versioned-consistency-policy.md\"",
            "\"tasks/active/TASK_SPEC-036-B1-versioned-consistency-policy.md\",\"extra.md\"");
        assertThatThrownBy(() -> load(manifest, VALID_RP, tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class).extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.ASSET_RELEASE_STATE_INVALID);
    }

    @Test
    void rpSourcePathsReorderedReturnsAssetReleaseStateInvalid(@TempDir Path tempDir) {
        String rp = VALID_RP.replace(
            "\"decisions/ADR-016-multi-occurrence-consistency-evidence-preservation.md\",\"docs/ARCHITECTURE.md\"",
            "\"docs/ARCHITECTURE.md\",\"decisions/ADR-016-multi-occurrence-consistency-evidence-preservation.md\"");
        assertThatThrownBy(() -> load(VALID_MANIFEST, rp, tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class).extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.ASSET_RELEASE_STATE_INVALID);
    }

    @Test
    void loaderEnabledTrueReturnsAssetReleaseStateInvalid(@TempDir Path tempDir) {
        String manifest = VALID_MANIFEST.replace("\"loaderEnabled\":false", "\"loaderEnabled\":true");
        assertThatThrownBy(() -> load(manifest, VALID_RP, tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class).extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.ASSET_RELEASE_STATE_INVALID);
    }

    @Test
    void databasePersistenceTrueReturnsAssetReleaseStateInvalid(@TempDir Path tempDir) {
        String manifest = VALID_MANIFEST.replace("\"databasePersistence\":false", "\"databasePersistence\":true");
        assertThatThrownBy(() -> load(manifest, VALID_RP, tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class).extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.ASSET_RELEASE_STATE_INVALID);
    }

    @Test
    void productionEffectActiveReturnsAssetReleaseStateInvalid(@TempDir Path tempDir) {
        String manifest = VALID_MANIFEST.replace("\"productionEffect\":\"NONE\"", "\"productionEffect\":\"ACTIVE\"");
        assertThatThrownBy(() -> load(manifest, VALID_RP, tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class).extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.ASSET_RELEASE_STATE_INVALID);
    }

    // ── 6. review point set ──

    @Test
    void extraReviewPointReturnsReviewPointSetInvalid(@TempDir Path tempDir) {
        String tenPoints = allNinePoints() + ","
            + pointJson("PARTY_A_NAME_CONSISTENCY", "TEXT", "NONE", PARTY_A_SEMANTIC);
        assertThatThrownBy(() -> load(VALID_MANIFEST, reviewPointsJson(tenPoints), tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class).extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.REVIEW_POINT_SET_INVALID);
    }

    @Test
    void emptyReviewPointObjectReturnsReviewPointSetInvalid(@TempDir Path tempDir) {
        String points = allNinePoints() + ",{}";
        assertThatThrownBy(() -> load(VALID_MANIFEST, reviewPointsJson(points), tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class).extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.REVIEW_POINT_SET_INVALID);
    }

    @Test
    void illegalReviewPointCodeReturnsReviewPointSetInvalid(@TempDir Path tempDir) {
        String points = allNinePoints().replace(
            "\"reviewPointCode\":\"WARRANTY_RETENTION_RATIO_CONSISTENCY\"",
            "\"reviewPointCode\":\"NONEXISTENT_CODE\"");
        assertThatThrownBy(() -> load(VALID_MANIFEST, reviewPointsJson(points), tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class).extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.REVIEW_POINT_SET_INVALID);
    }

    @Test
    void missingReviewPointReturnsReviewPointSetInvalid(@TempDir Path tempDir) {
        String eight = String.join(",",
            pointJson("PARTY_A_NAME_CONSISTENCY", "TEXT", "NONE", PARTY_A_SEMANTIC),
            pointJson("PARTY_B_NAME_CONSISTENCY", "TEXT", "NONE", "[]"),
            pointJson("CONTRACT_TOTAL_AMOUNT_CONSISTENCY", "DECIMAL", "CNY", "[]"),
            pointJson("TAX_AMOUNT_FORMULA_CONSISTENCY", "DECIMAL", "CNY", "[]"),
            pointJson("PREPAYMENT_RATIO_CONSISTENCY", "DECIMAL", "PERCENT", "[]"),
            pointJson("PROGRESS_PAYMENT_RATIO_CONSISTENCY", "DECIMAL", "PERCENT", "[]"),
            pointJson("COMPLETION_PAYMENT_RATIO_CONSISTENCY", "DECIMAL", "PERCENT", "[]"),
            pointJson("SETTLEMENT_PAYMENT_RATIO_CONSISTENCY", "DECIMAL", "PERCENT", "[]"));
        assertThatThrownBy(() -> load(VALID_MANIFEST, reviewPointsJson(eight), tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class).extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.REVIEW_POINT_SET_INVALID);
    }

    @Test
    void duplicateReviewPointReturnsReviewPointSetInvalid(@TempDir Path tempDir) {
        String points = allNinePoints().replace(
            "\"reviewPointCode\":\"WARRANTY_RETENTION_RATIO_CONSISTENCY\"",
            "\"reviewPointCode\":\"PARTY_B_NAME_CONSISTENCY\"");
        assertThatThrownBy(() -> load(VALID_MANIFEST, reviewPointsJson(points), tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class).extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.REVIEW_POINT_SET_INVALID);
    }

    // ── 7. budget — 9 points × 4 fields, each mutation only on one point ──

    enum BudgetField { CARDINALITY_MODE, MIN_CANDIDATES, MAX_CANDIDATES, OCCURRENCE_BUDGET }

    static Stream<Arguments> budgetProvider() {
        return Arrays.stream(CODE_NAMES).flatMap(code ->
            Arrays.stream(BudgetField.values()).map(f -> Arguments.of(code, f)));
    }

    @ParameterizedTest
    @MethodSource("budgetProvider")
    void singlePointBudgetMutationReturnsConsistencyBudgetInvalid(String targetCode, BudgetField field,
                                                                   @TempDir Path tempDir) {
        String mutated = mutateBudgetForCode(targetCode, field);
        assertThatThrownBy(() -> load(VALID_MANIFEST, reviewPointsJson(mutated), tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class).extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.CONSISTENCY_BUDGET_INVALID);
    }

    private String mutateBudgetForCode(String code, BudgetField field) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < CODE_NAMES.length; i++) {
            if (i > 0) sb.append(",");
            String c = CODE_NAMES[i];
            String sem = "PARTY_A_NAME_CONSISTENCY".equals(c) ? PARTY_A_SEMANTIC : "[]";
            Vcn frozen = CANONICAL.get(c);
            if (c.equals(code)) {
                sb.append(switch (field) {
                    case CARDINALITY_MODE -> pointJsonBudget(c, frozen.valueType, frozen.unit, sem, "SINGLE", 1, 8, 64);
                    case MIN_CANDIDATES -> pointJsonBudget(c, frozen.valueType, frozen.unit, sem, "CONSISTENCY_SET", 2, 8, 64);
                    case MAX_CANDIDATES -> pointJsonBudget(c, frozen.valueType, frozen.unit, sem, "CONSISTENCY_SET", 1, 7, 64);
                    case OCCURRENCE_BUDGET -> pointJsonBudget(c, frozen.valueType, frozen.unit, sem, "CONSISTENCY_SET", 1, 8, 63);
                });
            } else {
                sb.append(pointJson(c, frozen.valueType, frozen.unit, sem));
            }
        }
        return sb.toString();
    }

    static String pointJsonBudget(String code, String valueType, String unit, String sem,
                                   String cm, int min, int max, int budget) {
        return "{\"reviewPointCode\":\"" + code + "\",\"name\":\"test\",\"family\":\"PARTY_FIELDS\","
            + "\"candidateRole\":\"TEST\",\"requiredStructuredFields\":[\"testField\"],"
            + "\"executionStrategy\":\"PATTERN_EXTRACT_THEN_COMPARE\","
            + "\"deterministicRule\":\"test\",\"currentRuntimeSource\":\"test\","
            + "\"consistencyPolicy\":{"
            + "\"cardinalityMode\":\"" + cm + "\",\"minCandidates\":" + min + ",\"maxCandidates\":" + max + ",\"occurrenceBudget\":" + budget + ","
            + "\"scopePolicy\":{"
            + "\"version\":\"consistency-scope-v20260715.1\","
            + "\"includedRegionTypes\":[\"BODY\",\"APPENDIX\"],"
            + "\"strongExcludedContextTypes\":[\"TOC\",\"HEADER_FOOTER\",\"DELETED\",\"VOIDED\"],"
            + "\"requiredAttributionSignals\":[\"SOURCE_CONFIDENCE\",\"PARSE_CONFIDENCE\",\"VALUE_GRAMMAR\",\"ROLE_LABEL\",\"REGION_CONTEXT\",\"ANCHOR_IDENTITY\"],"
            + "\"strongExcludedSemanticContexts\":" + sem
            + "},"
            + "\"canonicalizationPolicy\":{\"version\":\"consistency-canonicalization-v20260715.1\",\"valueType\":\"" + valueType + "\",\"unit\":\"" + unit + "\"},"
            + "\"anchorIdentityPolicy\":{\"version\":\"mvp-occurrence-identity-v1\",\"blockIdentity\":[\"reviewPointCode\",\"blockId\"],\"tableCellIdentity\":[\"reviewPointCode\",\"blockId\",\"previewElementRef\"]}"
            + "}}";
    }

    // ── 8. canonical — 9 points × 2 fields, each mutates only one point ──

    enum CanonField { VALUE_TYPE, UNIT }

    static Stream<Arguments> canonProvider() {
        return Arrays.stream(CODE_NAMES).flatMap(code ->
            Arrays.stream(CanonField.values()).map(f -> Arguments.of(code, f)));
    }

    @ParameterizedTest
    @MethodSource("canonProvider")
    void singlePointCanonicalMutationReturnsCanonicalizationPolicyInvalid(String targetCode, CanonField field,
                                                                           @TempDir Path tempDir) {
        Vcn frozen = CANONICAL.get(targetCode);
        String wrong = (field == CanonField.VALUE_TYPE) ? flippedVt(frozen.valueType) : flippedUnit(frozen.unit);
        String mutated = mutateCanonForCode(allNinePoints(), targetCode, field, wrong);
        assertThatThrownBy(() -> load(VALID_MANIFEST, reviewPointsJson(mutated), tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class).extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.CANONICALIZATION_POLICY_INVALID);
    }

    private String flippedVt(String vt) { return "TEXT".equals(vt) ? "DECIMAL" : "TEXT"; }
    private String flippedUnit(String u) {
        return switch (u) { case "NONE" -> "CNY"; case "CNY" -> "PERCENT"; case "PERCENT" -> "NONE"; default -> "XXX"; };
    }

    private String mutateCanonForCode(String points, String code, CanonField field, String wrongValue) {
        // Rebuild: for each code, emit pointJson with correct or flipped canonical
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < CODE_NAMES.length; i++) {
            if (i > 0) sb.append(",");
            String c = CODE_NAMES[i];
            Vcn frozen = CANONICAL.get(c);
            String vt = frozen.valueType;
            String u = frozen.unit;
            if (c.equals(code)) {
                if (field == CanonField.VALUE_TYPE) vt = wrongValue;
                else u = wrongValue;
            }
            String sem = "PARTY_A_NAME_CONSISTENCY".equals(c) ? PARTY_A_SEMANTIC : "[]";
            sb.append(pointJson(c, vt, u, sem));
        }
        return sb.toString();
    }

    // ── 9. scope policy ──

    @Test
    void includedRegionMissingReturnsScopePolicyInvalid(@TempDir Path tempDir) {
        String points = allNinePoints().replace(
            "\"includedRegionTypes\":[\"BODY\",\"APPENDIX\"]",
            "\"includedRegionTypes\":[\"BODY\"]");
        assertThatThrownBy(() -> load(VALID_MANIFEST, reviewPointsJson(points), tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class).extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.SCOPE_POLICY_INVALID);
    }

    @Test
    void excludedContextMissingReturnsScopePolicyInvalid(@TempDir Path tempDir) {
        String points = allNinePoints().replace(
            "\"strongExcludedContextTypes\":[\"TOC\",\"HEADER_FOOTER\",\"DELETED\",\"VOIDED\"]",
            "\"strongExcludedContextTypes\":[\"TOC\",\"HEADER_FOOTER\",\"DELETED\"]");
        assertThatThrownBy(() -> load(VALID_MANIFEST, reviewPointsJson(points), tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class).extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.SCOPE_POLICY_INVALID);
    }

    @Test
    void excludedContextExtraReturnsScopePolicyInvalid(@TempDir Path tempDir) {
        String points = allNinePoints().replace(
            "\"strongExcludedContextTypes\":[\"TOC\",\"HEADER_FOOTER\",\"DELETED\",\"VOIDED\"]",
            "\"strongExcludedContextTypes\":[\"TOC\",\"HEADER_FOOTER\",\"DELETED\",\"VOIDED\",\"EXTRA\"]");
        assertThatThrownBy(() -> load(VALID_MANIFEST, reviewPointsJson(points), tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class).extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.SCOPE_POLICY_INVALID);
    }

    @Test
    void excludedContextReorderedReturnsScopePolicyInvalid(@TempDir Path tempDir) {
        String points = allNinePoints().replace(
            "\"strongExcludedContextTypes\":[\"TOC\",\"HEADER_FOOTER\",\"DELETED\",\"VOIDED\"]",
            "\"strongExcludedContextTypes\":[\"HEADER_FOOTER\",\"TOC\",\"DELETED\",\"VOIDED\"]");
        assertThatThrownBy(() -> load(VALID_MANIFEST, reviewPointsJson(points), tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class).extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.SCOPE_POLICY_INVALID);
    }

    @Test
    void attributionSignalsMissingReturnsScopePolicyInvalid(@TempDir Path tempDir) {
        String points = allNinePoints().replace(
            "\"requiredAttributionSignals\":[\"SOURCE_CONFIDENCE\",\"PARSE_CONFIDENCE\",\"VALUE_GRAMMAR\",\"ROLE_LABEL\",\"REGION_CONTEXT\",\"ANCHOR_IDENTITY\"]",
            "\"requiredAttributionSignals\":[\"SOURCE_CONFIDENCE\",\"PARSE_CONFIDENCE\"]");
        assertThatThrownBy(() -> load(VALID_MANIFEST, reviewPointsJson(points), tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class).extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.SCOPE_POLICY_INVALID);
    }

    @Test
    void partyBSemanticExclusionNotEmptyReturnsScopePolicyInvalid(@TempDir Path tempDir) {
        String points = allNinePoints().replace(
            "\"strongExcludedSemanticContexts\":[]",
            "\"strongExcludedSemanticContexts\":[\"CONTRACT_TITLE_NAME_MENTION\"]");
        assertThatThrownBy(() -> load(VALID_MANIFEST, reviewPointsJson(points), tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class).extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.SCOPE_POLICY_INVALID);
    }

    @Test
    void partyASemanticExclusionMissingReturnsScopePolicyInvalid(@TempDir Path tempDir) {
        String points = allNinePoints().replace(
            "\"strongExcludedSemanticContexts\":[" + PARTY_A_SEMANTIC.substring(1, PARTY_A_SEMANTIC.length() - 1) + "]",
            "\"strongExcludedSemanticContexts\":[\"CONTRACT_TITLE_NAME_MENTION\"]");
        assertThatThrownBy(() -> load(VALID_MANIFEST, reviewPointsJson(points), tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class).extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.SCOPE_POLICY_INVALID);
    }

    // ── 10. anchor identity ──

    @Test
    void blockIdentityMissingReturnsAnchorIdentityPolicyInvalid(@TempDir Path tempDir) {
        String points = allNinePoints().replace(
            "\"blockIdentity\":[\"reviewPointCode\",\"blockId\"]",
            "\"blockIdentity\":[\"reviewPointCode\"]");
        assertThatThrownBy(() -> load(VALID_MANIFEST, reviewPointsJson(points), tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class).extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.ANCHOR_IDENTITY_POLICY_INVALID);
    }

    @Test
    void blockIdentityExtraReturnsAnchorIdentityPolicyInvalid(@TempDir Path tempDir) {
        String points = allNinePoints().replace(
            "\"blockIdentity\":[\"reviewPointCode\",\"blockId\"]",
            "\"blockIdentity\":[\"reviewPointCode\",\"blockId\",\"extra\"]");
        assertThatThrownBy(() -> load(VALID_MANIFEST, reviewPointsJson(points), tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class).extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.ANCHOR_IDENTITY_POLICY_INVALID);
    }

    @Test
    void blockIdentityReorderedReturnsAnchorIdentityPolicyInvalid(@TempDir Path tempDir) {
        String points = allNinePoints().replace(
            "\"blockIdentity\":[\"reviewPointCode\",\"blockId\"]",
            "\"blockIdentity\":[\"blockId\",\"reviewPointCode\"]");
        assertThatThrownBy(() -> load(VALID_MANIFEST, reviewPointsJson(points), tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class).extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.ANCHOR_IDENTITY_POLICY_INVALID);
    }

    @Test
    void tableCellIdentityReorderedReturnsAnchorIdentityPolicyInvalid(@TempDir Path tempDir) {
        String points = allNinePoints().replace(
            "\"tableCellIdentity\":[\"reviewPointCode\",\"blockId\",\"previewElementRef\"]",
            "\"tableCellIdentity\":[\"blockId\",\"reviewPointCode\",\"previewElementRef\"]");
        assertThatThrownBy(() -> load(VALID_MANIFEST, reviewPointsJson(points), tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class).extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.ANCHOR_IDENTITY_POLICY_INVALID);
    }

    // ── 11. missing / wrong-type safety fields ──

    @Test
    void missingAssetIdFieldReturnsResourceJsonInvalid(@TempDir Path tempDir) {
        String manifest = VALID_MANIFEST.replace("\"assetId\":", "\"assetIdX\":");
        assertThatThrownBy(() -> load(manifest, VALID_RP, tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class).extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.RESOURCE_JSON_INVALID);
    }

    @Test
    void nonTextualStatusReturnsResourceJsonInvalid(@TempDir Path tempDir) {
        String manifest = VALID_MANIFEST.replace("\"status\":\"DRAFT\"", "\"status\":123");
        assertThatThrownBy(() -> load(manifest, VALID_RP, tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class).extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.RESOURCE_JSON_INVALID);
    }

    @Test
    void nonBooleanLoaderEnabledReturnsResourceJsonInvalid(@TempDir Path tempDir) {
        String manifest = VALID_MANIFEST.replace("\"loaderEnabled\":false", "\"loaderEnabled\":\"not-a-bool\"");
        assertThatThrownBy(() -> load(manifest, VALID_RP, tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class).extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.RESOURCE_JSON_INVALID);
    }

    @Test
    void nonIntMinCandidatesReturnsResourceJsonInvalid(@TempDir Path tempDir) {
        String points = allNinePoints().replace("\"minCandidates\":1", "\"minCandidates\":\"one\"");
        assertThatThrownBy(() -> load(VALID_MANIFEST, reviewPointsJson(points), tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class).extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.RESOURCE_JSON_INVALID);
    }

    @Test
    void nonTextualBudgetFieldReturnsResourceJsonInvalid(@TempDir Path tempDir) {
        String points = allNinePoints().replace("\"cardinalityMode\":\"CONSISTENCY_SET\"",
            "\"cardinalityMode\":true");
        assertThatThrownBy(() -> load(VALID_MANIFEST, reviewPointsJson(points), tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class).extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.RESOURCE_JSON_INVALID);
    }

    // ── 12. multi-error priority — forbidden content checked LAST ──

    @Test
    void budgetInvalidTakesPriorityOverForbiddenContent(@TempDir Path tempDir) {
        // budget broken (maxCandidates=7) + forbidden content in review point name → budget wins
        String budgetBroken = mutateBudgetForCode("CONTRACT_TOTAL_AMOUNT_CONSISTENCY", BudgetField.MAX_CANDIDATES)
            .replace(
                "\"reviewPointCode\":\"CONTRACT_TOTAL_AMOUNT_CONSISTENCY\",\"name\":\"test\"",
                "\"reviewPointCode\":\"CONTRACT_TOTAL_AMOUNT_CONSISTENCY\",\"name\":\"test-CQCP-MVP-DOCX\"");
        assertThatThrownBy(() -> load(VALID_MANIFEST, reviewPointsJson(budgetBroken), tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class)
            .extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.CONSISTENCY_BUDGET_INVALID);
    }

    @Test
    void scopeInvalidTakesPriorityOverForbiddenContent(@TempDir Path tempDir) {
        String busy = allNinePoints().replace(
            "\"strongExcludedContextTypes\":[\"TOC\",\"HEADER_FOOTER\",\"DELETED\",\"VOIDED\"]",
            "\"strongExcludedContextTypes\":[\"TOC\",\"HEADER_FOOTER\",\"DELETED\"]")
            .replace("\"reviewPointCode\":\"PARTY_B_NAME_CONSISTENCY\",\"name\":\"test\"",
            "\"reviewPointCode\":\"PARTY_B_NAME_CONSISTENCY\",\"name\":\"test-CQCP-MVP-DOCX\"");
        assertThatThrownBy(() -> load(VALID_MANIFEST, reviewPointsJson(busy), tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class).extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.SCOPE_POLICY_INVALID);
    }

    @Test
    void pathInvalidTakesPriorityOverIdentityAndForbidden(@TempDir Path tempDir) {
        String manifest = VALID_MANIFEST
            .replace("\"cqcp.ruleset.mvp.consistency-set.v20260715.1\"", "\"wrong.id\"")
            .replace("\"../review-point-definitions/review-points-v20260715.1.json\"",
                "\"../../../outside/escape.json\"");
        assertThatThrownBy(() -> load(manifest, VALID_RP, tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class).extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.REFERENCE_PATH_INVALID);
    }

    // ── 13. forbidden content — must be checked LAST ──

    static Stream<String> forbiddenContentProvider() {
        return Stream.of(
            "CQCP-MVP-DOCX", "occurrenceNo", "includedInConsistencyEvaluation",
            "human-anchors", "human anchor", "humanAnchor", "fixture",
            "expected", ".docx", ".xlsx"
        );
    }

    @ParameterizedTest
    @MethodSource("forbiddenContentProvider")
    void forbiddenContentInReviewPointsReturnsForbiddenPolicyContent(String pattern, @TempDir Path tempDir) {
        String poisoned = allNinePoints().replace(
            "\"reviewPointCode\":\"CONTRACT_TOTAL_AMOUNT_CONSISTENCY\",\"name\":\"test\"",
            "\"reviewPointCode\":\"CONTRACT_TOTAL_AMOUNT_CONSISTENCY\",\"name\":\"test-" + pattern + "\"");
        assertThatThrownBy(() -> load(VALID_MANIFEST, reviewPointsJson(poisoned), tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class).extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.FORBIDDEN_POLICY_CONTENT);
    }

    @ParameterizedTest
    @MethodSource("forbiddenContentProvider")
    void forbiddenContentInManifestReturnsForbiddenPolicyContent(String pattern, @TempDir Path tempDir) {
        String manifest = VALID_MANIFEST.replace("\"test\"", "\"test-" + pattern + "\"");
        assertThatThrownBy(() -> load(manifest, VALID_RP, tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class).extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.FORBIDDEN_POLICY_CONTENT);
    }

    // ── 14. duplicate JSON keys ──

    @Test
    void duplicateManifestKeyReturnsResourceJsonInvalid(@TempDir Path tempDir) {
        String dup = "{ \"status\":\"DRAFT\" , \"status\":\"ACTIVE\" }";
        try {
            Path assetsDir = tempDir.resolve("cqcp/review-assets/rule-sets");
            Files.createDirectories(assetsDir);
            Files.writeString(assetsDir.resolve("ruleset-v20260715.1.json"), dup, StandardCharsets.UTF_8);
            Path rpDir = tempDir.resolve("cqcp/review-assets/review-point-definitions");
            Files.createDirectories(rpDir);
            Files.writeString(rpDir.resolve("review-points-v20260715.1.json"), VALID_RP, StandardCharsets.UTF_8);
            var cl = new URLClassLoader(new URL[]{tempDir.toUri().toURL()}, null);
            assertThatThrownBy(() -> new RuntimeRuleSetLoader(cl).load("v20260715.1"))
                .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class).extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.RESOURCE_JSON_INVALID);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ── 14. cross-asset multi-error priority ──

    @Test
    void rpJsonInvalidTrumpsManifestReleaseError(@TempDir Path tempDir) {
        // manifest has valid identity but broken release state
        String badManifest = VALID_MANIFEST.replace("\"status\":\"DRAFT\"", "\"status\":\"ACTIVE\"");
        // rpDef is NOT valid JSON → Phase 1 fires during rpDef parse, before Phase 3
        String badRp = "{ not json }";
        assertThatThrownBy(() -> load(badManifest, badRp, tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class)
            .extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.RESOURCE_JSON_INVALID);
    }

    @Test
    void rpAssetIdentityInvalidTrumpsRpBudgetError(@TempDir Path tempDir) {
        // rp has wrong assetId (Phase 2) AND broken maxCandidates (Phase 5) → Phase 2 wins
        String badRp = reviewPointsJson(allNinePoints().replace(
            "\"maxCandidates\":8", "\"maxCandidates\":7"))
            .replace("\"assetId\":\"cqcp.review-points.mvp.consistency-set.v20260715.1\"",
                "\"assetId\":\"wrong-rp-id\"");
        assertThatThrownBy(() -> load(VALID_MANIFEST, badRp, tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class)
            .extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.ASSET_IDENTITY_INVALID);
    }

    @Test
    void rpScopeInvalidTrumpsRpForbiddenContent(@TempDir Path tempDir) {
        // rp has scope error (Phase 5) AND forbidden content (Phase 6) → Phase 5 wins
        String badRp = reviewPointsJson(allNinePoints()
            .replace("\"strongExcludedContextTypes\":[\"TOC\",\"HEADER_FOOTER\",\"DELETED\",\"VOIDED\"]",
                "\"strongExcludedContextTypes\":[\"TOC\",\"HEADER_FOOTER\",\"DELETED\"]")
            .replace("\"reviewPointCode\":\"PARTY_B_NAME_CONSISTENCY\",\"name\":\"test\"",
                "\"reviewPointCode\":\"PARTY_B_NAME_CONSISTENCY\",\"name\":\"test-CQCP-MVP-DOCX\""));
        assertThatThrownBy(() -> load(VALID_MANIFEST, badRp, tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class)
            .extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.SCOPE_POLICY_INVALID);
    }

    @Test
    void manifestIdentityInvalidTrumpsRpForbiddenContent(@TempDir Path tempDir) {
        // manifest has wrong assetId (Phase 2) and rp has forbidden content (Phase 6) → Phase 2 wins
        String badManifest = VALID_MANIFEST.replace(
            "\"assetId\":\"cqcp.ruleset.mvp.consistency-set.v20260715.1\"", "\"assetId\":\"wrong-manifest-id\"");
        String poisonedRp = reviewPointsJson(allNinePoints().replace(
            "\"reviewPointCode\":\"CONTRACT_TOTAL_AMOUNT_CONSISTENCY\",\"name\":\"test\"",
            "\"reviewPointCode\":\"CONTRACT_TOTAL_AMOUNT_CONSISTENCY\",\"name\":\"test-CQCP-MVP-DOCX\""));
        assertThatThrownBy(() -> load(badManifest, poisonedRp, tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class)
            .extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.ASSET_IDENTITY_INVALID);
    }

    // ── 15. cross-asset multi-error: identity trumps release state ──

    @Test
    void rpIdentityInvalidTrumpsManifestReleaseError(@TempDir Path tempDir) {
        // manifest has ACTIVE status (Phase 3 release error), rpDef has wrong assetId (Phase 2 identity)
        String badManifest = VALID_MANIFEST.replace("\"status\":\"DRAFT\"", "\"status\":\"ACTIVE\"");
        String badRp = VALID_RP.replace(
            "\"cqcp.review-points.mvp.consistency-set.v20260715.1\"", "\"wrong-rp-id\"");
        assertThatThrownBy(() -> load(badManifest, badRp, tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class)
            .extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.ASSET_IDENTITY_INVALID);
    }

    // ── 16. empty / null root ──

    @Test
    void emptyManifestReturnsResourceJsonInvalid(@TempDir Path tempDir) {
        try {
            Path assetsDir = tempDir.resolve("cqcp/review-assets/rule-sets");
            Files.createDirectories(assetsDir);
            Files.writeString(assetsDir.resolve("ruleset-v20260715.1.json"), "", StandardCharsets.UTF_8);
            Path rpDir = tempDir.resolve("cqcp/review-assets/review-point-definitions");
            Files.createDirectories(rpDir);
            Files.writeString(rpDir.resolve("review-points-v20260715.1.json"), VALID_RP, StandardCharsets.UTF_8);
            var cl = new URLClassLoader(new URL[]{tempDir.toUri().toURL()}, null);
            assertThatThrownBy(() -> new RuntimeRuleSetLoader(cl).load("v20260715.1"))
                .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class)
                .extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.RESOURCE_JSON_INVALID);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void nullRootManifestReturnsResourceJsonInvalid(@TempDir Path tempDir) {
        try {
            Path assetsDir = tempDir.resolve("cqcp/review-assets/rule-sets");
            Files.createDirectories(assetsDir);
            Files.writeString(assetsDir.resolve("ruleset-v20260715.1.json"), "null", StandardCharsets.UTF_8);
            Path rpDir = tempDir.resolve("cqcp/review-assets/review-point-definitions");
            Files.createDirectories(rpDir);
            Files.writeString(rpDir.resolve("review-points-v20260715.1.json"), VALID_RP, StandardCharsets.UTF_8);
            var cl = new URLClassLoader(new URL[]{tempDir.toUri().toURL()}, null);
            assertThatThrownBy(() -> new RuntimeRuleSetLoader(cl).load("v20260715.1"))
                .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class)
                .extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.RESOURCE_JSON_INVALID);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ── 17. cross-point priority: empty first point does not block type error in later point ──

    @Test
    void emptyFirstPointDoesNotBlockConsistencyTypeErrorInLaterPoint(@TempDir Path tempDir) {
        // Point 0 is empty object (→ REVIEW_POINT_SET_INVALID in Phase 4)
        // Point 1 has non-int minCandidates (→ RESOURCE_JSON_INVALID in Phase 1b)
        // Phase 1b scans all points → RESOURCE_JSON_INVALID fires first
        String[] pts = new String[9];
        pts[0] = "{}";
        Vcn frozen1 = CANONICAL.get(CODE_NAMES[0]);
        pts[1] = pointJson(CODE_NAMES[0], frozen1.valueType, frozen1.unit,
            "PARTY_A_NAME_CONSISTENCY".equals(CODE_NAMES[0]) ? PARTY_A_SEMANTIC : "[]")
            .replace("\"minCandidates\":1", "\"minCandidates\":\"bad\"");
        for (int i = 2; i < 9; i++) {
            String code = CODE_NAMES[i];
            Vcn frozen = CANONICAL.get(code);
            String sem = "PARTY_A_NAME_CONSISTENCY".equals(code) ? PARTY_A_SEMANTIC : "[]";
            pts[i] = pointJson(code, frozen.valueType, frozen.unit, sem);
        }
        String badRp = reviewPointsJson(String.join(",", pts));
        assertThatThrownBy(() -> load(VALID_MANIFEST, badRp, tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class)
            .extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.RESOURCE_JSON_INVALID);
    }

    // ── 18. position independence: same type error in different positions → same reason ──

    @Test
    void sameTypeErrorInDifferentPointPositionsReturnsSameReason(@TempDir Path tempDir) {
        // Inject non-int minCandidates in first, middle, and last point positions.
        // Each must return RESOURCE_JSON_INVALID regardless of position.
        for (int pos : new int[]{0, 4, 8}) {
            String[] pts = new String[9];
            for (int i = 0; i < 9; i++) {
                String code = CODE_NAMES[i];
                Vcn frozen = CANONICAL.get(code);
                String sem = "PARTY_A_NAME_CONSISTENCY".equals(code) ? PARTY_A_SEMANTIC : "[]";
                String json = pointJson(code, frozen.valueType, frozen.unit, sem);
                pts[i] = (i == pos)
                    ? json.replace("\"minCandidates\":1", "\"minCandidates\":\"bad\"")
                    : json;
            }
            String badRp = reviewPointsJson(String.join(",", pts));
            assertThatThrownBy(() -> load(VALID_MANIFEST, badRp, tempDir))
                .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class)
                .extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.RESOURCE_JSON_INVALID);
        }
    }

    // ── 19. Phase 4 struct error trumps Phase 5 value error ──

    @Test
    void earlyBudgetErrorAndLateDuplicateCodeReturnsReviewPointSetInvalid(@TempDir Path tempDir) {
        // Point 0 has budget VALUE error (maxCandidates=7, type-valid)
        // Point 8 has duplicate reviewPointCode (PARTY_B_NAME_CONSISTENCY already used at index 1)
        // Phase 4 catches duplicate code before Phase 5a budget check runs
        String points = allNinePoints()
            .replaceFirst("\"maxCandidates\":8", "\"maxCandidates\":7")
            .replace("\"reviewPointCode\":\"WARRANTY_RETENTION_RATIO_CONSISTENCY\"",
                "\"reviewPointCode\":\"PARTY_B_NAME_CONSISTENCY\"");
        assertThatThrownBy(() -> load(VALID_MANIFEST, reviewPointsJson(points), tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class)
            .extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.REVIEW_POINT_SET_INVALID);
    }

    // ── 20. Phase 5a budget error in late point trumps Phase 5b scope error in early point ──

    @Test
    void earlyScopeErrorAndLateBudgetErrorReturnsConsistencyBudgetInvalid(@TempDir Path tempDir) {
        // Point 0 has scope version error, point 8 has maxCandidates=7 (budget error)
        // Phase 5a (budget, runs first): finds point 8 budget error → CONSISTENCY_BUDGET_INVALID
        String[] pts = new String[9];
        Vcn frozen0 = CANONICAL.get(CODE_NAMES[0]);
        pts[0] = pointJson(CODE_NAMES[0], frozen0.valueType, frozen0.unit,
            "PARTY_A_NAME_CONSISTENCY".equals(CODE_NAMES[0]) ? PARTY_A_SEMANTIC : "[]")
            .replace("\"version\":\"consistency-scope-v20260715.1\"", "\"version\":\"wrong-scope-v1\"");
        for (int i = 1; i < 8; i++) {
            String code = CODE_NAMES[i];
            Vcn fr = CANONICAL.get(code);
            String sem = "PARTY_A_NAME_CONSISTENCY".equals(code) ? PARTY_A_SEMANTIC : "[]";
            pts[i] = pointJson(code, fr.valueType, fr.unit, sem);
        }
        Vcn frozen8 = CANONICAL.get(CODE_NAMES[8]);
        pts[8] = pointJsonBudget(CODE_NAMES[8], frozen8.valueType, frozen8.unit,
            "[]", "CONSISTENCY_SET", 1, 7, 64);
        String badRp = reviewPointsJson(String.join(",", pts));
        assertThatThrownBy(() -> load(VALID_MANIFEST, badRp, tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class)
            .extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.CONSISTENCY_BUDGET_INVALID);
    }

    @Test
    void earlyBudgetErrorAndLateScopeErrorReturnsConsistencyBudgetInvalid(@TempDir Path tempDir) {
        // Swapped positions: point 0 has budget error, point 8 has scope error
        // Phase 5a (budget, runs first): finds point 0 budget error → same reason
        String[] pts = new String[9];
        Vcn frozen0 = CANONICAL.get(CODE_NAMES[0]);
        pts[0] = pointJsonBudget(CODE_NAMES[0], frozen0.valueType, frozen0.unit,
            PARTY_A_SEMANTIC, "CONSISTENCY_SET", 1, 7, 64);
        for (int i = 1; i < 8; i++) {
            String code = CODE_NAMES[i];
            Vcn fr = CANONICAL.get(code);
            String sem = "PARTY_A_NAME_CONSISTENCY".equals(code) ? PARTY_A_SEMANTIC : "[]";
            pts[i] = pointJson(code, fr.valueType, fr.unit, sem);
        }
        Vcn frozen8 = CANONICAL.get(CODE_NAMES[8]);
        pts[8] = pointJson(CODE_NAMES[8], frozen8.valueType, frozen8.unit, "[]")
            .replace("\"version\":\"consistency-scope-v20260715.1\"", "\"version\":\"wrong-scope-v1\"");
        String badRp = reviewPointsJson(String.join(",", pts));
        assertThatThrownBy(() -> load(VALID_MANIFEST, badRp, tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class)
            .extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.CONSISTENCY_BUDGET_INVALID);
    }

    // ── 21. Phase 5b scope error in early point trumps Phase 5c canonical error in late point ──

    @Test
    void earlyCanonicalErrorAndLateScopeErrorReturnsScopePolicyInvalid(@TempDir Path tempDir) {
        // Point 0 has canonical value error (wrong valueType), point 8 has scope version error
        // Phase 5a (budget): passes. Phase 5b (scope): finds point 8 scope error → SCOPE_POLICY_INVALID
        String[] pts = new String[9];
        Vcn frozen0 = CANONICAL.get(CODE_NAMES[0]);
        pts[0] = pointJson(CODE_NAMES[0], "WRONG", frozen0.unit,
            PARTY_A_SEMANTIC);
        for (int i = 1; i < 8; i++) {
            String code = CODE_NAMES[i];
            Vcn fr = CANONICAL.get(code);
            String sem = "PARTY_A_NAME_CONSISTENCY".equals(code) ? PARTY_A_SEMANTIC : "[]";
            pts[i] = pointJson(code, fr.valueType, fr.unit, sem);
        }
        Vcn frozen8 = CANONICAL.get(CODE_NAMES[8]);
        pts[8] = pointJson(CODE_NAMES[8], frozen8.valueType, frozen8.unit, "[]")
            .replace("\"version\":\"consistency-scope-v20260715.1\"", "\"version\":\"wrong-scope-v1\"");
        String badRp = reviewPointsJson(String.join(",", pts));
        assertThatThrownBy(() -> load(VALID_MANIFEST, badRp, tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class)
            .extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.SCOPE_POLICY_INVALID);
    }

    @Test
    void earlyScopeErrorAndLateCanonicalErrorReturnsScopePolicyInvalid(@TempDir Path tempDir) {
        // Swapped: point 0 has scope error, point 8 has canonical value error
        // Phase 5a (budget): passes. Phase 5b (scope): finds point 0 scope error → same reason
        String[] pts = new String[9];
        Vcn frozen0 = CANONICAL.get(CODE_NAMES[0]);
        pts[0] = pointJson(CODE_NAMES[0], frozen0.valueType, frozen0.unit, PARTY_A_SEMANTIC)
            .replace("\"version\":\"consistency-scope-v20260715.1\"", "\"version\":\"wrong-scope-v1\"");
        for (int i = 1; i < 8; i++) {
            String code = CODE_NAMES[i];
            Vcn fr = CANONICAL.get(code);
            String sem = "PARTY_A_NAME_CONSISTENCY".equals(code) ? PARTY_A_SEMANTIC : "[]";
            pts[i] = pointJson(code, fr.valueType, fr.unit, sem);
        }
        Vcn frozen8 = CANONICAL.get(CODE_NAMES[8]);
        pts[8] = pointJson(CODE_NAMES[8], "WRONG", frozen8.unit, "[]");
        String badRp = reviewPointsJson(String.join(",", pts));
        assertThatThrownBy(() -> load(VALID_MANIFEST, badRp, tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class)
            .extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.SCOPE_POLICY_INVALID);
    }

    // ── 22. Phase 5c canonical error trumps Phase 5d anchor error ──

    @Test
    void earlyAnchorErrorAndLateCanonicalErrorReturnsCanonicalizationPolicyInvalid(@TempDir Path tempDir) {
        // Point 0 has anchor error (blockIdentity shortened), point 8 has canonical value error
        // Phase 5a/b pass. Phase 5c (canonical): finds point 8 canonical error → CANONICALIZATION_POLICY_INVALID
        String[] pts = new String[9];
        Vcn frozen0 = CANONICAL.get(CODE_NAMES[0]);
        pts[0] = pointJson(CODE_NAMES[0], frozen0.valueType, frozen0.unit, PARTY_A_SEMANTIC)
            .replace("\"blockIdentity\":[\"reviewPointCode\",\"blockId\"]",
                "\"blockIdentity\":[\"reviewPointCode\"]");
        for (int i = 1; i < 8; i++) {
            String code = CODE_NAMES[i];
            Vcn fr = CANONICAL.get(code);
            String sem = "PARTY_A_NAME_CONSISTENCY".equals(code) ? PARTY_A_SEMANTIC : "[]";
            pts[i] = pointJson(code, fr.valueType, fr.unit, sem);
        }
        Vcn frozen8 = CANONICAL.get(CODE_NAMES[8]);
        pts[8] = pointJson(CODE_NAMES[8], "WRONG", frozen8.unit, "[]");
        String badRp = reviewPointsJson(String.join(",", pts));
        assertThatThrownBy(() -> load(VALID_MANIFEST, badRp, tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class)
            .extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.CANONICALIZATION_POLICY_INVALID);
    }

    @Test
    void earlyCanonicalErrorAndLateAnchorErrorReturnsCanonicalizationPolicyInvalid(@TempDir Path tempDir) {
        // Swapped: point 0 has canonical error, point 8 has anchor error
        // Phase 5a/b pass. Phase 5c (canonical): finds point 0 canonical error → same reason
        String[] pts = new String[9];
        Vcn frozen0 = CANONICAL.get(CODE_NAMES[0]);
        pts[0] = pointJson(CODE_NAMES[0], "WRONG", frozen0.unit, PARTY_A_SEMANTIC);
        for (int i = 1; i < 8; i++) {
            String code = CODE_NAMES[i];
            Vcn fr = CANONICAL.get(code);
            String sem = "PARTY_A_NAME_CONSISTENCY".equals(code) ? PARTY_A_SEMANTIC : "[]";
            pts[i] = pointJson(code, fr.valueType, fr.unit, sem);
        }
        Vcn frozen8 = CANONICAL.get(CODE_NAMES[8]);
        pts[8] = pointJson(CODE_NAMES[8], frozen8.valueType, frozen8.unit, "[]")
            .replace("\"blockIdentity\":[\"reviewPointCode\",\"blockId\"]",
                "\"blockIdentity\":[\"reviewPointCode\"]");
        String badRp = reviewPointsJson(String.join(",", pts));
        assertThatThrownBy(() -> load(VALID_MANIFEST, badRp, tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class)
            .extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.CANONICALIZATION_POLICY_INVALID);
    }

    // ── 23. Phase 1b type error trumps Phase 2 identity error ──

    @Test
    void manifestIdentityErrorAndRpDefStatusTypeErrorReturnsResourceJsonInvalid(@TempDir Path tempDir) {
        // manifest has wrong assetId (text type, wrong value → ASSET_IDENTITY_INVALID in Phase 2)
        // rpDef has status=123 (non-text type → RESOURCE_JSON_INVALID in Phase 1b)
        // Phase 1b scans both assets' text fields first: finds rpDef.status type error → RESOURCE_JSON_INVALID
        String badManifest = VALID_MANIFEST.replace(
            "\"cqcp.ruleset.mvp.consistency-set.v20260715.1\"", "\"wrong-manifest-id\"");
        String badRp = VALID_RP.replace("\"status\":\"DRAFT\"", "\"status\":123");
        assertThatThrownBy(() -> load(badManifest, badRp, tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class)
            .extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.RESOURCE_JSON_INVALID);
    }

    // ── 24. Schema phase object/array type checks (Phase 1b) ──

    @Test
    void missingSourceReturnsResourceJsonInvalid(@TempDir Path tempDir) {
        String manifest = VALID_MANIFEST.replace("\"source\":{", "\"sourceX\":{");
        assertThatThrownBy(() -> load(manifest, VALID_RP, tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class)
            .extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.RESOURCE_JSON_INVALID);
    }

    @Test
    void nonObjectSourceReturnsResourceJsonInvalid(@TempDir Path tempDir) {
        String manifest = VALID_MANIFEST.replaceFirst("\"source\":\\{[^}]+\\}", "\"source\":123");
        assertThatThrownBy(() -> load(manifest, VALID_RP, tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class)
            .extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.RESOURCE_JSON_INVALID);
    }

    @Test
    void missingRuntimePolicyReturnsResourceJsonInvalid(@TempDir Path tempDir) {
        String manifest = VALID_MANIFEST.replace(
            "\"runtimePolicy\":{\"loaderEnabled\":false,\"databasePersistence\":false,\"productionEffect\":\"NONE\",\"notes\":[\"test\"]}",
            "\"runtimePolicyX\":{\"loaderEnabled\":false,\"databasePersistence\":false,\"productionEffect\":\"NONE\",\"notes\":[\"test\"]}");
        assertThatThrownBy(() -> load(manifest, VALID_RP, tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class)
            .extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.RESOURCE_JSON_INVALID);
    }

    @Test
    void nonObjectRuntimePolicyReturnsResourceJsonInvalid(@TempDir Path tempDir) {
        String manifest = VALID_MANIFEST.replace(
            "\"runtimePolicy\":{\"loaderEnabled\":false,\"databasePersistence\":false,\"productionEffect\":\"NONE\",\"notes\":[\"test\"]}",
            "\"runtimePolicy\":\"not-an-object\"");
        assertThatThrownBy(() -> load(manifest, VALID_RP, tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class)
            .extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.RESOURCE_JSON_INVALID);
    }

    @Test
    void missingScopePolicyReturnsResourceJsonInvalid(@TempDir Path tempDir) {
        String badPoints = allNinePoints().replaceFirst("\"scopePolicy\":\\{[^}]+\\},", "");
        assertThatThrownBy(() -> load(VALID_MANIFEST, reviewPointsJson(badPoints), tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class)
            .extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.RESOURCE_JSON_INVALID);
    }

    @Test
    void nonObjectCanonicalizationPolicyReturnsResourceJsonInvalid(@TempDir Path tempDir) {
        String badPoints = allNinePoints().replaceFirst(
            "\"canonicalizationPolicy\":\\{[^}]+\\}",
            "\"canonicalizationPolicy\":\"bad\"");
        assertThatThrownBy(() -> load(VALID_MANIFEST, reviewPointsJson(badPoints), tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class)
            .extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.RESOURCE_JSON_INVALID);
    }

    @Test
    void missingAnchorIdentityPolicyReturnsResourceJsonInvalid(@TempDir Path tempDir) {
        String badPoints = allNinePoints().replaceFirst(",\"anchorIdentityPolicy\":\\{[^}]+\\}", "");
        assertThatThrownBy(() -> load(VALID_MANIFEST, reviewPointsJson(badPoints), tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class)
            .extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.RESOURCE_JSON_INVALID);
    }

    @Test
    void missingScopeArrayReturnsResourceJsonInvalid(@TempDir Path tempDir) {
        String badPoints = allNinePoints().replaceFirst(
            "\"includedRegionTypes\":\\[\"BODY\",\"APPENDIX\"\\]",
            "\"includedRegionTypesX\":[\"BODY\",\"APPENDIX\"]");
        assertThatThrownBy(() -> load(VALID_MANIFEST, reviewPointsJson(badPoints), tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class)
            .extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.RESOURCE_JSON_INVALID);
    }

    @Test
    void nonArrayAnchorIdentityReturnsResourceJsonInvalid(@TempDir Path tempDir) {
        String badPoints = allNinePoints().replaceFirst(
            "\"blockIdentity\":\\[\"reviewPointCode\",\"blockId\"\\]",
            "\"blockIdentity\":\"not-an-array\"");
        assertThatThrownBy(() -> load(VALID_MANIFEST, reviewPointsJson(badPoints), tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class)
            .extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.RESOURCE_JSON_INVALID);
    }

    @Test
    void missingReviewPointCodeReturnsReviewPointSetInvalid(@TempDir Path tempDir) {
        String badPoints = allNinePoints().replaceFirst(
            "\"reviewPointCode\":\"PARTY_A_NAME_CONSISTENCY\"",
            "\"reviewPointCodeX\":\"PARTY_A_NAME_CONSISTENCY\"");
        assertThatThrownBy(() -> load(VALID_MANIFEST, reviewPointsJson(badPoints), tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class)
            .extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.REVIEW_POINT_SET_INVALID);
    }

    @Test
    void nonTextualReviewPointCodeReturnsReviewPointSetInvalid(@TempDir Path tempDir) {
        String badPoints = allNinePoints().replaceFirst(
            "\"reviewPointCode\":\"PARTY_A_NAME_CONSISTENCY\"",
            "\"reviewPointCode\":123");
        assertThatThrownBy(() -> load(VALID_MANIFEST, reviewPointsJson(badPoints), tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class)
            .extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.REVIEW_POINT_SET_INVALID);
    }

    // ── 25. B2 correction: module reference reason ──

    @Test
    void moduleVersionsMissingReturnsResourceJsonInvalid(@TempDir Path tempDir) {
        String manifest = VALID_MANIFEST.replace("\"moduleVersions\":{", "\"moduleVersionsX\":{");
        assertThatThrownBy(() -> load(manifest, VALID_RP, tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class)
            .extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.RESOURCE_JSON_INVALID);
    }

    @Test
    void moduleVersionsNonObjectReturnsResourceJsonInvalid(@TempDir Path tempDir) {
        int mvStart = VALID_MANIFEST.indexOf("\"moduleVersions\":{");
        int mvEnd = VALID_MANIFEST.indexOf(",\"runtimePolicy\"", mvStart);
        String before = VALID_MANIFEST.substring(0, mvStart);
        String after = VALID_MANIFEST.substring(mvEnd);
        String manifest = before + "\"moduleVersions\":\"not-an-object\"" + after;
        assertThatThrownBy(() -> load(manifest, VALID_RP, tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class)
            .extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.RESOURCE_JSON_INVALID);
    }

    @Test
    void reviewPointDefinitionsMissingReturnsAssetIdentityInvalid(@TempDir Path tempDir) {
        String manifest = VALID_MANIFEST.replace(
            "\"reviewPointDefinitions\":{", "\"reviewPointDefinitionsX\":{");
        assertThatThrownBy(() -> load(manifest, VALID_RP, tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class)
            .extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.ASSET_IDENTITY_INVALID);
    }

    @Test
    void reviewPointDefinitionsNonObjectReturnsAssetIdentityInvalid(@TempDir Path tempDir) {
        ModuleRefEntry entry = MODULE_REF_ENTRIES.get("reviewPointDefinitions");
        String search = "\"reviewPointDefinitions\":{\"assetId\":\"" + entry.assetId
            + "\",\"version\":\"" + entry.version + "\",\"path\":\"" + entry.path + "\"}";
        String manifest = VALID_MANIFEST.replace(search, "\"reviewPointDefinitions\":\"not-an-object\"");
        assertThatThrownBy(() -> load(manifest, VALID_RP, tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class)
            .extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.ASSET_IDENTITY_INVALID);
    }

    static Stream<String> otherModuleRefKeys() {
        return Stream.of("patternLibrary", "fieldLexicon", "promptTemplates",
            "contractTypeProfiles", "evidenceSelectors");
    }

    @ParameterizedTest
    @MethodSource("otherModuleRefKeys")
    void otherModuleRefNonObjectReturnsAssetIdentityInvalid(String refKey, @TempDir Path tempDir) {
        ModuleRefEntry entry = MODULE_REF_ENTRIES.get(refKey);
        String search = "\"" + refKey + "\":{\"assetId\":\"" + entry.assetId
            + "\",\"version\":\"" + entry.version + "\",\"path\":\"" + entry.path + "\"}";
        String manifest = VALID_MANIFEST.replace(search, "\"" + refKey + "\":\"not-an-object\"");
        assertThatThrownBy(() -> load(manifest, VALID_RP, tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class)
            .extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.ASSET_IDENTITY_INVALID);
    }

    @ParameterizedTest
    @MethodSource("refInternalFieldProvider")
    void refInternalFieldTypeErrorReturnsResourceJsonInvalid(String refKey, String field, RefFieldVariant variant,
                                                              @TempDir Path tempDir) {
        ModuleRefEntry entry = MODULE_REF_ENTRIES.get(refKey);
        String manifest = surgicallyMutateRefField(VALID_MANIFEST, refKey, field, entry, variant);
        assertThatThrownBy(() -> load(manifest, VALID_RP, tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class)
            .extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.RESOURCE_JSON_INVALID);
    }

    @Test
    void normalRefPathValueMismatchReturnsAssetIdentityInvalid(@TempDir Path tempDir) {
        String manifest = VALID_MANIFEST.replace(
            "\"path\":\"../pattern-libraries/pattern-library-v20260705.1.json\"",
            "\"path\":\"../pattern-libraries/wrong-path.json\"");
        assertThatThrownBy(() -> load(manifest, VALID_RP, tempDir))
            .isInstanceOf(RuntimeRuleSetLoader.RuleSetLoadException.class)
            .extracting("reason").isEqualTo(RuntimeRuleSetLoader.RuleSetLoadException.RuleSetLoadReason.ASSET_IDENTITY_INVALID);
    }

    static Stream<Arguments> refInternalFieldProvider() {
        return MODULE_REF_ENTRIES.keySet().stream()
            .flatMap(refKey -> Stream.of("assetId", "version", "path")
                .flatMap(field -> Stream.of(RefFieldVariant.MISSING, RefFieldVariant.NON_TEXT)
                    .map(variant -> Arguments.of(refKey, field, variant))));
    }

    /**
     * Surgical mutation of a single field within one module reference in the manifest JSON.
     * MISSING renames the field key; NON_TEXT changes the value to a boolean (true).
     * Uniqueness is guaranteed by each ref's unique assetId.
     */
    private String surgicallyMutateRefField(String manifest, String refKey, String field,
                                             ModuleRefEntry entry, RefFieldVariant variant) {
        String searchStr;
        String replaceStr;

        switch (field) {
            case "assetId" -> {
                searchStr = "\"assetId\":\"" + entry.assetId + "\"";
                if (variant == RefFieldVariant.MISSING) {
                    replaceStr = "\"assetIdX\":\"" + entry.assetId + "\"";
                } else {
                    replaceStr = "\"assetId\":true";
                }
            }
            case "version" -> {
                searchStr = "\"assetId\":\"" + entry.assetId + "\",\"version\":\"" + entry.version + "\"";
                if (variant == RefFieldVariant.MISSING) {
                    replaceStr = "\"assetId\":\"" + entry.assetId + "\",\"versionX\":\"" + entry.version + "\"";
                } else {
                    replaceStr = "\"assetId\":\"" + entry.assetId + "\",\"version\":true";
                }
            }
            case "path" -> {
                searchStr = "\"assetId\":\"" + entry.assetId + "\",\"version\":\"" + entry.version + "\",\"path\":\"" + entry.path + "\"";
                if (variant == RefFieldVariant.MISSING) {
                    replaceStr = "\"assetId\":\"" + entry.assetId + "\",\"version\":\"" + entry.version + "\",\"pathX\":\"" + entry.path + "\"";
                } else {
                    replaceStr = "\"assetId\":\"" + entry.assetId + "\",\"version\":\"" + entry.version + "\",\"path\":true";
                }
            }
            default -> throw new AssertionError("unknown field: " + field);
        }

        String result = manifest.replace(searchStr, replaceStr);
        if (result.equals(manifest)) {
            throw new AssertionError("surgicallyMutateRefField: no match for "
                + refKey + "." + field + " " + variant);
        }
        return result;
    }

    // ── helpers ──

    private RuntimeRuleSetSnapshot loadValid(Path tempDir) {
        return load(VALID_MANIFEST, VALID_RP, tempDir);
    }

    private RuntimeRuleSetSnapshot load(String manifest, String rp, Path tempDir) {
        var cl = buildClassLoader(tempDir, manifest, rp);
        return new RuntimeRuleSetLoader(cl).load("v20260715.1");
    }

}
