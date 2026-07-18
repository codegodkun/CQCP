package com.cqcp.apiserver.reviewengine;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class RuntimeRuleSetLoader {

    private static final String MANIFEST_PATH = "cqcp/review-assets/rule-sets/ruleset-v20260715.1.json";
    private static final String CLASS_PATH_PREFIX = "cqcp/review-assets/";
    private static final String FROZEN_TARGET = "review-point-definitions/review-points-v20260715.1.json";
    private static final String VERSION = "v20260715.1";

    private static final String FROZEN_RULESET_ASSET_ID = "cqcp.ruleset.mvp.consistency-set.v20260715.1";
    private static final String FROZEN_RP_ASSET_ID = "cqcp.review-points.mvp.consistency-set.v20260715.1";
    private static final String FROZEN_STATUS = "DRAFT";
    private static final String FROZEN_SOURCE_TYPE = "architecture-approved-policy";
    private static final String FROZEN_RUNTIME_BINDING = "NOT_BOUND";
    private static final String FROZEN_RULESET_BINDING_NOTE =
        "B1 静态 policy 源；未绑定 runtime loader，不对任何任务生效。";
    private static final String FROZEN_RP_BINDING_NOTE =
        "B1 静态 consistency policy 源；未绑定 runtime，不对任何任务生效。审核点定义来自 ADR-016 冻结的九点多出处一致性语义。";

    private static final List<String> FROZEN_SOURCE_PATHS = List.of(
        "decisions/ADR-016-multi-occurrence-consistency-evidence-preservation.md",
        "docs/ARCHITECTURE.md",
        "tasks/active/TASK_SPEC-036-B1-versioned-consistency-policy.md"
    );

    private static final int EXPECTED_POINT_COUNT = 9;
    private static final String FROZEN_CARDINALITY_MODE = "CONSISTENCY_SET";
    private static final int FROZEN_MIN_CANDIDATES = 1;
    private static final int FROZEN_MAX_CANDIDATES = 8;
    private static final int FROZEN_OCCURRENCE_BUDGET = 64;

    private static final List<String> FROZEN_INCLUDED_REGIONS = List.of("BODY", "APPENDIX");
    private static final List<String> FROZEN_EXCLUDED_CONTEXTS = List.of("TOC", "HEADER_FOOTER", "DELETED", "VOIDED");
    private static final List<String> FROZEN_ATTRIBUTION_SIGNALS = List.of(
        "SOURCE_CONFIDENCE", "PARSE_CONFIDENCE", "VALUE_GRAMMAR",
        "ROLE_LABEL", "REGION_CONTEXT", "ANCHOR_IDENTITY"
    );
    private static final List<String> FROZEN_PARTY_A_SEMANTIC_EXCLUSIONS = List.of(
        "CONTRACT_TITLE_NAME_MENTION", "AGREEMENT_PREAMBLE_CONTRACT_NAME_MENTION");
    private static final List<String> FROZEN_BLOCK_IDENTITY = List.of("reviewPointCode", "blockId");
    private static final List<String> FROZEN_TABLE_CELL_IDENTITY = List.of("reviewPointCode", "blockId", "previewElementRef");

    private static final String FROZEN_SCOPE_VERSION = "consistency-scope-v20260715.1";
    private static final String FROZEN_CANONICALIZATION_VERSION = "consistency-canonicalization-v20260715.1";
    private static final String FROZEN_ANCHOR_VERSION = "mvp-occurrence-identity-v1";

    private static final Map<ReviewPointCode, FrozenCanonical> FROZEN_CANONICAL_MATRIX = Map.of(
        ReviewPointCode.PARTY_A_NAME_CONSISTENCY, new FrozenCanonical("TEXT", "NONE"),
        ReviewPointCode.PARTY_B_NAME_CONSISTENCY, new FrozenCanonical("TEXT", "NONE"),
        ReviewPointCode.CONTRACT_TOTAL_AMOUNT_CONSISTENCY, new FrozenCanonical("DECIMAL", "CNY"),
        ReviewPointCode.TAX_AMOUNT_FORMULA_CONSISTENCY, new FrozenCanonical("DECIMAL", "CNY"),
        ReviewPointCode.PREPAYMENT_RATIO_CONSISTENCY, new FrozenCanonical("DECIMAL", "PERCENT"),
        ReviewPointCode.PROGRESS_PAYMENT_RATIO_CONSISTENCY, new FrozenCanonical("DECIMAL", "PERCENT"),
        ReviewPointCode.COMPLETION_PAYMENT_RATIO_CONSISTENCY, new FrozenCanonical("DECIMAL", "PERCENT"),
        ReviewPointCode.SETTLEMENT_PAYMENT_RATIO_CONSISTENCY, new FrozenCanonical("DECIMAL", "PERCENT"),
        ReviewPointCode.WARRANTY_RETENTION_RATIO_CONSISTENCY, new FrozenCanonical("DECIMAL", "PERCENT"));

    private static final Map<String, ModuleRef> FROZEN_MODULE_REFS = Map.of(
        "reviewPointDefinitions", new ModuleRef("cqcp.review-points.mvp.consistency-set.v20260715.1", "v20260715.1",
            "../review-point-definitions/review-points-v20260715.1.json"),
        "patternLibrary", new ModuleRef("cqcp.pattern-library.mvp.code-current.v20260705.1", "v20260705.1",
            "../pattern-libraries/pattern-library-v20260705.1.json"),
        "fieldLexicon", new ModuleRef("cqcp.field-lexicon.mvp.code-current.v20260705.1", "v20260705.1",
            "../field-lexicons/field-lexicon-v20260705.1.json"),
        "promptTemplates", new ModuleRef("cqcp.prompts.mvp.code-current.v20260705.1", "v20260705.1",
            "../prompts/prompts-v20260705.1.json"),
        "contractTypeProfiles", new ModuleRef("cqcp.contract-type-profile.mvp.code-current.v20260705.1", "v20260705.1",
            "../contract-type-profiles/contract-type-profile-v20260705.1.json"),
        "evidenceSelectors", new ModuleRef("cqcp.evidence-selector.mvp.code-current.v20260705.1", "v20260705.1",
            "../evidence-selectors/evidence-selector-v20260705.1.json"));

    private static final List<String> FORBIDDEN_CONTENT_PATTERNS = List.of(
        "CQCP-MVP-DOCX", "occurrenceNo", "includedInConsistencyEvaluation",
        "human-anchors", "human anchor", "humanAnchor", "fixture",
        "expected", ".docx", ".xlsx");

    private final ClassLoader classLoader;
    private final ObjectMapper mapper;

    RuntimeRuleSetLoader() {
        this(RuntimeRuleSetLoader.class.getClassLoader());
    }

    RuntimeRuleSetLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
        this.mapper = new ObjectMapper();
        this.mapper.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }

    RuntimeRuleSetSnapshot load(String version) {
        if (!VERSION.equals(version)) {
            throw new RuleSetLoadException(RuleSetLoadException.RuleSetLoadReason.ASSET_IDENTITY_INVALID,
                version != null ? version : "null", MANIFEST_PATH);
        }
        return loadManifest();
    }

    // ════════════════════════════════════════════════════════════════
    // Global phased validation: 12 reasons enforce priority across assets.
    // Phase 1: read + path     → RESOURCE_NOT_FOUND / RESOURCE_JSON_INVALID / REFERENCE_PATH_INVALID / REFERENCE_TARGET_MISMATCH
    // Phase 2: asset identity  → ASSET_IDENTITY_INVALID (both assets)
    // Phase 3: release state   → ASSET_RELEASE_STATE_INVALID (both assets)
    // Phase 4: review point set → REVIEW_POINT_SET_INVALID
    // Phase 5: per-point policy → CONSISTENCY_BUDGET / SCOPE / CANONICALIZATION / ANCHOR_IDENTITY
    // Phase 6: forbidden content → FORBIDDEN_POLICY_CONTENT (both assets)
    // ════════════════════════════════════════════════════════════════

    private RuntimeRuleSetSnapshot loadManifest() {

        // ===== Phase 1: read both JSONs + resolve path =====
        final JsonNode manifest = readJsonResource(MANIFEST_PATH);

        JsonNode mv = manifest.get("moduleVersions");
        if (mv == null || !mv.isObject()) {
            throw new RuleSetLoadException(RuleSetLoadException.RuleSetLoadReason.RESOURCE_JSON_INVALID,
                VERSION, MANIFEST_PATH);
        }
        JsonNode rpRefNode = mv.get("reviewPointDefinitions");
        if (rpRefNode == null || !rpRefNode.isObject()) {
            throw new RuleSetLoadException(RuleSetLoadException.RuleSetLoadReason.ASSET_IDENTITY_INVALID,
                VERSION, MANIFEST_PATH);
        }
        JsonNode pathNode = rpRefNode.get("path");
        if (pathNode == null || !pathNode.isTextual()) {
            throw new RuleSetLoadException(RuleSetLoadException.RuleSetLoadReason.RESOURCE_JSON_INVALID,
                VERSION, MANIFEST_PATH);
        }
        String rpRef = pathNode.textValue();
        String resolvedRpPath = resolveAndValidatePath(rpRef);
        final JsonNode rpDef = readJsonResource(resolvedRpPath);

        // ===== Phase 1b: full schema/type scan across both assets =====
        phase1SchemaScan(manifest, rpDef, resolvedRpPath);

        // ===== Phase 2: asset identity (both assets) =====
        validateModuleReferences(mv);
        validateAssetIdentity(manifest, "RULE_SET", FROZEN_RULESET_ASSET_ID, MANIFEST_PATH);
        validateAssetIdentity(rpDef, "REVIEW_POINT_DEFINITION", FROZEN_RP_ASSET_ID, resolvedRpPath);

        // ===== Phase 3: release state (both assets) =====
        validateReleaseState(manifest, FROZEN_RULESET_BINDING_NOTE, MANIFEST_PATH);
        validateRuntimePolicy(manifest);
        validateReleaseState(rpDef, FROZEN_RP_BINDING_NOTE, resolvedRpPath);

        // ===== Phase 4: review point set — build validated consistencyPolicy map =====
        JsonNode points = rpDef.get("reviewPoints");
        if (points == null || !points.isArray() || points.size() != EXPECTED_POINT_COUNT) {
            throw new RuleSetLoadException(RuleSetLoadException.RuleSetLoadReason.REVIEW_POINT_SET_INVALID,
                VERSION, resolvedRpPath);
        }
        Map<ReviewPointCode, JsonNode> cpMap = new LinkedHashMap<>();
        for (int i = 0; i < points.size(); i++) {
            JsonNode point = points.get(i);
            if (point == null || !point.isObject() || point.isEmpty()) {
                throw new RuleSetLoadException(RuleSetLoadException.RuleSetLoadReason.REVIEW_POINT_SET_INVALID,
                    VERSION, resolvedRpPath);
            }
            ReviewPointCode code = extractAndValidateCode(point, resolvedRpPath);
            if (cpMap.containsKey(code)) {
                throw new RuleSetLoadException(RuleSetLoadException.RuleSetLoadReason.REVIEW_POINT_SET_INVALID,
                    VERSION, resolvedRpPath);
            }
            JsonNode cpNode = point.get("consistencyPolicy");
            if (cpNode == null || !cpNode.isObject()) {
                throw new RuleSetLoadException(RuleSetLoadException.RuleSetLoadReason.REVIEW_POINT_SET_INVALID,
                    VERSION, resolvedRpPath);
            }
            cpMap.put(code, cpNode);
        }

        // ===== Phase 5: per-dimension policy validation (each traverses all 9 points) =====
        validateAllBudgets(cpMap, resolvedRpPath);
        validateAllScopes(cpMap, resolvedRpPath);
        validateAllCanonicalizationPolicies(cpMap, resolvedRpPath);
        validateAllAnchorIdentityPolicies(cpMap, resolvedRpPath);

        // ===== Build immutable snapshot =====
        Map<ReviewPointCode, ConsistencyPolicySnapshot> policyMap = new LinkedHashMap<>();
        for (var entry : cpMap.entrySet()) {
            policyMap.put(entry.getKey(),
                buildConsistencyPolicySnapshot(entry.getKey(), entry.getValue(), resolvedRpPath));
        }

        // ===== Phase 6: forbidden content (both assets, last) =====
        scanForbidden(manifest.toString(), MANIFEST_PATH);
        scanForbidden(rpDef.toString(), resolvedRpPath);

        String rpAssetId = requiredString(rpDef, "assetId", resolvedRpPath);
        String rpVersion = requiredString(rpDef, "version", resolvedRpPath);
        return new RuntimeRuleSetSnapshot(FROZEN_RULESET_ASSET_ID, VERSION, rpAssetId, rpVersion, policyMap);
    }

    // ──────────────────────────── Phase 1 ────────────────────────────

    private JsonNode readJsonResource(String resourcePath) {
        try (InputStream in = classLoader.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new RuleSetLoadException(RuleSetLoadException.RuleSetLoadReason.RESOURCE_NOT_FOUND,
                    VERSION, resourcePath);
            }
            JsonNode root = mapper.readTree(in);
            if (root == null || !root.isObject()) {
                throw new RuleSetLoadException(RuleSetLoadException.RuleSetLoadReason.RESOURCE_JSON_INVALID,
                    VERSION, resourcePath);
            }
            return root;
        } catch (RuleSetLoadException e) {
            throw e;
        } catch (Exception e) {
            throw new RuleSetLoadException(RuleSetLoadException.RuleSetLoadReason.RESOURCE_JSON_INVALID,
                VERSION, resourcePath);
        }
    }

    // ──────────────────────────── Phase 2 ────────────────────────────

    private void validateModuleReferences(JsonNode mv) {
        if (mv.size() != FROZEN_MODULE_REFS.size()) {
            throw new RuleSetLoadException(RuleSetLoadException.RuleSetLoadReason.ASSET_IDENTITY_INVALID,
                VERSION, MANIFEST_PATH);
        }
        for (var entry : FROZEN_MODULE_REFS.entrySet()) {
            String key = entry.getKey();
            ModuleRef expected = entry.getValue();
            JsonNode refNode = mv.get(key);
            if (refNode == null || !refNode.isObject()) {
                throw new RuleSetLoadException(RuleSetLoadException.RuleSetLoadReason.ASSET_IDENTITY_INVALID,
                    VERSION, MANIFEST_PATH);
            }
            String actualId = requiredString(refNode, "assetId", MANIFEST_PATH);
            String actualVersion = requiredString(refNode, "version", MANIFEST_PATH);
            String actualPath = requiredString(refNode, "path", MANIFEST_PATH);
            if (!expected.assetId.equals(actualId) || !expected.version.equals(actualVersion)
                || !expected.path.equals(actualPath)) {
                throw new RuleSetLoadException(RuleSetLoadException.RuleSetLoadReason.ASSET_IDENTITY_INVALID,
                    VERSION, MANIFEST_PATH);
            }
        }
    }

    private void validateAssetIdentity(JsonNode node, String expectedType, String expectedAssetId, String path) {
        String assetId = requiredString(node, "assetId", path);
        String assetType = requiredString(node, "assetType", path);
        String version = requiredString(node, "version", path);
        if (!expectedAssetId.equals(assetId) || !expectedType.equals(assetType) || !VERSION.equals(version)) {
            throw new RuleSetLoadException(RuleSetLoadException.RuleSetLoadReason.ASSET_IDENTITY_INVALID,
                VERSION, path);
        }
    }

    // ──────────────────────────── Phase 3 ────────────────────────────

    private void validateReleaseState(JsonNode node, String expectedBindingNote, String path) {
        String status = requiredString(node, "status", path);
        JsonNode source = node.get("source");
        if (source == null || !source.isObject()) {
            throw new RuleSetLoadException(RuleSetLoadException.RuleSetLoadReason.ASSET_RELEASE_STATE_INVALID,
                VERSION, path);
        }
        String sourceType = requiredString(source, "type", path);
        String runtimeBinding = requiredString(source, "runtimeBinding", path);
        String bindingNote = requiredString(source, "bindingNote", path);
        if (!FROZEN_STATUS.equals(status) || !FROZEN_SOURCE_TYPE.equals(sourceType)
            || !FROZEN_RUNTIME_BINDING.equals(runtimeBinding) || !expectedBindingNote.equals(bindingNote)) {
            throw new RuleSetLoadException(RuleSetLoadException.RuleSetLoadReason.ASSET_RELEASE_STATE_INVALID,
                VERSION, path);
        }

        JsonNode paths = source.get("paths");
        if (paths == null || !paths.isArray() || paths.size() != FROZEN_SOURCE_PATHS.size()) {
            throw new RuleSetLoadException(RuleSetLoadException.RuleSetLoadReason.ASSET_RELEASE_STATE_INVALID,
                VERSION, path);
        }
        for (int i = 0; i < paths.size(); i++) {
            JsonNode p = paths.get(i);
            if (p == null || !p.isTextual() || !FROZEN_SOURCE_PATHS.get(i).equals(p.textValue())) {
                throw new RuleSetLoadException(RuleSetLoadException.RuleSetLoadReason.ASSET_RELEASE_STATE_INVALID,
                    VERSION, path);
            }
        }
    }

    private void validateRuntimePolicy(JsonNode manifest) {
        JsonNode rp = manifest.get("runtimePolicy");
        if (rp == null || !rp.isObject()) {
            throw new RuleSetLoadException(RuleSetLoadException.RuleSetLoadReason.ASSET_RELEASE_STATE_INVALID,
                VERSION, MANIFEST_PATH);
        }
        JsonNode leNode = rp.get("loaderEnabled");
        if (leNode == null || !leNode.isBoolean()) {
            throw new RuleSetLoadException(RuleSetLoadException.RuleSetLoadReason.ASSET_RELEASE_STATE_INVALID,
                VERSION, MANIFEST_PATH);
        }
        JsonNode dpNode = rp.get("databasePersistence");
        if (dpNode == null || !dpNode.isBoolean()) {
            throw new RuleSetLoadException(RuleSetLoadException.RuleSetLoadReason.ASSET_RELEASE_STATE_INVALID,
                VERSION, MANIFEST_PATH);
        }
        String productionEffect = requiredString(rp, "productionEffect", MANIFEST_PATH);
        if (leNode.booleanValue() || dpNode.booleanValue() || !"NONE".equals(productionEffect)) {
            throw new RuleSetLoadException(RuleSetLoadException.RuleSetLoadReason.ASSET_RELEASE_STATE_INVALID,
                VERSION, MANIFEST_PATH);
        }
    }

    // ──────────────────────────── Phase 4 ────────────────────────────

    private ReviewPointCode extractAndValidateCode(JsonNode point, String path) {
        JsonNode codeNode = point.get("reviewPointCode");
        if (codeNode == null || !codeNode.isTextual()) {
            throw new RuleSetLoadException(RuleSetLoadException.RuleSetLoadReason.REVIEW_POINT_SET_INVALID,
                VERSION, path);
        }
        try {
            return ReviewPointCode.valueOf(codeNode.textValue());
        } catch (IllegalArgumentException e) {
            throw new RuleSetLoadException(RuleSetLoadException.RuleSetLoadReason.REVIEW_POINT_SET_INVALID,
                VERSION, path);
        }
    }

    // ──────────────────────────── Phase 5 ────────────────────────────

    // ── Phase 5a: budget (all 9 points) ──

    private void validateAllBudgets(Map<ReviewPointCode, JsonNode> cpMap, String path) {
        for (var entry : cpMap.entrySet()) {
            JsonNode cp = entry.getValue();
            String cardinalityMode = cp.get("cardinalityMode").textValue();
            int minCandidates = cp.get("minCandidates").intValue();
            int maxCandidates = cp.get("maxCandidates").intValue();
            int occurrenceBudget = cp.get("occurrenceBudget").intValue();
            if (!FROZEN_CARDINALITY_MODE.equals(cardinalityMode)
                || minCandidates != FROZEN_MIN_CANDIDATES
                || maxCandidates != FROZEN_MAX_CANDIDATES
                || occurrenceBudget != FROZEN_OCCURRENCE_BUDGET) {
                throw new RuleSetLoadException(RuleSetLoadException.RuleSetLoadReason.CONSISTENCY_BUDGET_INVALID,
                    VERSION, path);
            }
        }
    }

    // ── Phase 5b: scope (all 9 points) ──

    private void validateAllScopes(Map<ReviewPointCode, JsonNode> cpMap, String path) {
        for (var entry : cpMap.entrySet()) {
            ReviewPointCode code = entry.getKey();
            JsonNode cp = entry.getValue();
            JsonNode scope = cp.get("scopePolicy");
            String scopeVersion = scope.get("version").textValue();
            if (!FROZEN_SCOPE_VERSION.equals(scopeVersion)) {
                throw new RuleSetLoadException(RuleSetLoadException.RuleSetLoadReason.SCOPE_POLICY_INVALID,
                    VERSION, path);
            }
            List<String> includedRegions = textListFromArray(scope.get("includedRegionTypes"));
            List<String> excludedContexts = textListFromArray(scope.get("strongExcludedContextTypes"));
            List<String> attributionSignals = textListFromArray(scope.get("requiredAttributionSignals"));
            List<String> semanticExclusions = textListFromArray(scope.get("strongExcludedSemanticContexts"));
            if (!FROZEN_INCLUDED_REGIONS.equals(includedRegions)
                || !FROZEN_EXCLUDED_CONTEXTS.equals(excludedContexts)
                || !FROZEN_ATTRIBUTION_SIGNALS.equals(attributionSignals)) {
                throw new RuleSetLoadException(RuleSetLoadException.RuleSetLoadReason.SCOPE_POLICY_INVALID,
                    VERSION, path);
            }
            List<String> expectedSemantic = code == ReviewPointCode.PARTY_A_NAME_CONSISTENCY
                ? FROZEN_PARTY_A_SEMANTIC_EXCLUSIONS : List.of();
            if (!expectedSemantic.equals(semanticExclusions)) {
                throw new RuleSetLoadException(RuleSetLoadException.RuleSetLoadReason.SCOPE_POLICY_INVALID,
                    VERSION, path);
            }
        }
    }

    // ── Phase 5c: canonicalization (all 9 points) ──

    private void validateAllCanonicalizationPolicies(Map<ReviewPointCode, JsonNode> cpMap, String path) {
        for (var entry : cpMap.entrySet()) {
            ReviewPointCode code = entry.getKey();
            JsonNode cp = entry.getValue();
            JsonNode canon = cp.get("canonicalizationPolicy");
            String canonVersion = canon.get("version").textValue();
            if (!FROZEN_CANONICALIZATION_VERSION.equals(canonVersion)) {
                throw new RuleSetLoadException(RuleSetLoadException.RuleSetLoadReason.CANONICALIZATION_POLICY_INVALID,
                    VERSION, path);
            }
            String valueType = canon.get("valueType").textValue();
            String unit = canon.get("unit").textValue();
            FrozenCanonical expectedCanonical = FROZEN_CANONICAL_MATRIX.get(code);
            if (!expectedCanonical.valueType.equals(valueType) || !expectedCanonical.unit.equals(unit)) {
                throw new RuleSetLoadException(RuleSetLoadException.RuleSetLoadReason.CANONICALIZATION_POLICY_INVALID,
                    VERSION, path);
            }
        }
    }

    // ── Phase 5d: anchor identity (all 9 points) ──

    private void validateAllAnchorIdentityPolicies(Map<ReviewPointCode, JsonNode> cpMap, String path) {
        for (var entry : cpMap.entrySet()) {
            JsonNode cp = entry.getValue();
            JsonNode anchor = cp.get("anchorIdentityPolicy");
            String anchorVersion = anchor.get("version").textValue();
            if (!FROZEN_ANCHOR_VERSION.equals(anchorVersion)) {
                throw new RuleSetLoadException(RuleSetLoadException.RuleSetLoadReason.ANCHOR_IDENTITY_POLICY_INVALID,
                    VERSION, path);
            }
            List<String> blockId = textListFromArray(anchor.get("blockIdentity"));
            List<String> tableCellId = textListFromArray(anchor.get("tableCellIdentity"));
            if (!FROZEN_BLOCK_IDENTITY.equals(blockId) || !FROZEN_TABLE_CELL_IDENTITY.equals(tableCellId)) {
                throw new RuleSetLoadException(RuleSetLoadException.RuleSetLoadReason.ANCHOR_IDENTITY_POLICY_INVALID,
                    VERSION, path);
            }
        }
    }

    // ── Build ConsistencyPolicySnapshot from verified consistencyPolicy node ──

    private ConsistencyPolicySnapshot buildConsistencyPolicySnapshot(ReviewPointCode code, JsonNode cp, String path) {
        String cardinalityMode = cp.get("cardinalityMode").textValue();
        int minCandidates = cp.get("minCandidates").intValue();
        int maxCandidates = cp.get("maxCandidates").intValue();
        int occurrenceBudget = cp.get("occurrenceBudget").intValue();

        JsonNode scope = cp.get("scopePolicy");
        String scopeVersion = scope.get("version").textValue();
        List<String> includedRegions = textListFromArray(scope.get("includedRegionTypes"));
        List<String> excludedContexts = textListFromArray(scope.get("strongExcludedContextTypes"));
        List<String> attributionSignals = textListFromArray(scope.get("requiredAttributionSignals"));
        List<String> semanticExclusions = textListFromArray(scope.get("strongExcludedSemanticContexts"));

        JsonNode canon = cp.get("canonicalizationPolicy");
        String canonVersion = canon.get("version").textValue();
        String valueType = canon.get("valueType").textValue();
        String unit = canon.get("unit").textValue();

        JsonNode anchor = cp.get("anchorIdentityPolicy");
        String anchorVersion = anchor.get("version").textValue();
        List<String> blockId = textListFromArray(anchor.get("blockIdentity"));
        List<String> tableCellId = textListFromArray(anchor.get("tableCellIdentity"));

        return new ConsistencyPolicySnapshot(cardinalityMode, minCandidates, maxCandidates, occurrenceBudget,
            scopeVersion, includedRegions, excludedContexts, attributionSignals, semanticExclusions,
            canonVersion, valueType, unit, anchorVersion, blockId, tableCellId);
    }

    // ──────────────────────────── Phase 6 ────────────────────────────

    private void scanForbidden(String raw, String path) {
        for (String pattern : FORBIDDEN_CONTENT_PATTERNS) {
            if (raw.contains(pattern)) {
                throw new RuleSetLoadException(RuleSetLoadException.RuleSetLoadReason.FORBIDDEN_POLICY_CONTENT,
                    VERSION, path);
            }
        }
    }

    // ════════════════ Phase 1b: schema/type scan ════════════════

    /**
     * Phase 1b: scan ALL safety-critical fields across both assets for
     * missing/wrong-type conditions.  Throws RESOURCE_JSON_INVALID when:
     * - text fields (identity, status, source text, budget text, etc.) are
     *   missing or non-textual
     * - source, runtimePolicy, scopePolicy, canonicalizationPolicy,
     *   anchorIdentityPolicy are missing or non-object
     * - boolean fields (loaderEnabled, databasePersistence) are missing or
     *   non-boolean
     * - int fields (minCandidates, maxCandidates, occurrenceBudget) are
     *   missing or non-int
     * - arrays (source.paths, all policy string arrays) are missing,
     *   non-array, or contain non-text elements
     *
     * Does NOT check reviewPointCode — that stays REVIEW_POINT_SET_INVALID.
     * Does NOT check consistencyPolicy missing/non-object — that stays
     * REVIEW_POINT_SET_INVALID.
     */
    private void phase1SchemaScan(JsonNode manifest, JsonNode rpDef, String rpDefPath) {
        // ===== Both assets: text identity/status fields =====
        requireTextField(manifest, "assetId", MANIFEST_PATH);
        requireTextField(manifest, "assetType", MANIFEST_PATH);
        requireTextField(manifest, "version", MANIFEST_PATH);
        requireTextField(manifest, "status", MANIFEST_PATH);
        requireTextField(rpDef, "assetId", rpDefPath);
        requireTextField(rpDef, "assetType", rpDefPath);
        requireTextField(rpDef, "version", rpDefPath);
        requireTextField(rpDef, "status", rpDefPath);

        // ===== Both assets: source must be object with text fields =====
        JsonNode manifestSrc = requireObjectField(manifest, "source", MANIFEST_PATH);
        requireTextField(manifestSrc, "type", MANIFEST_PATH);
        requireTextField(manifestSrc, "runtimeBinding", MANIFEST_PATH);
        requireTextField(manifestSrc, "bindingNote", MANIFEST_PATH);

        JsonNode rpdefSrc = requireObjectField(rpDef, "source", rpDefPath);
        requireTextField(rpdefSrc, "type", rpDefPath);
        requireTextField(rpdefSrc, "runtimeBinding", rpDefPath);
        requireTextField(rpdefSrc, "bindingNote", rpDefPath);

        // ===== Both assets: source.paths must be array with text elements =====
        requireArrayField(manifestSrc, "paths", MANIFEST_PATH);
        requireTextArrayElements(manifestSrc.get("paths"), MANIFEST_PATH);
        requireArrayField(rpdefSrc, "paths", rpDefPath);
        requireTextArrayElements(rpdefSrc.get("paths"), rpDefPath);

        // ===== Manifest: runtimePolicy must be object =====
        JsonNode rp = requireObjectField(manifest, "runtimePolicy", MANIFEST_PATH);
        requireBooleanField(rp, "loaderEnabled", MANIFEST_PATH);
        requireBooleanField(rp, "databasePersistence", MANIFEST_PATH);
        requireTextField(rp, "productionEffect", MANIFEST_PATH);

        // ===== Manifest: module reference internal fields =====
        JsonNode mv = manifest.get("moduleVersions");
        if (mv != null && mv.isObject()) {
            for (String key : FROZEN_MODULE_REFS.keySet()) {
                JsonNode ref = mv.get(key);
                if (ref != null && ref.isObject()) {
                    requireTextField(ref, "assetId", MANIFEST_PATH);
                    requireTextField(ref, "version", MANIFEST_PATH);
                    requireTextField(ref, "path", MANIFEST_PATH);
                }
            }
        }

        // ===== Review points: consistencyPolicy fields (NOT reviewPointCode — stays REVIEW_POINT_SET_INVALID) =====
        JsonNode points = rpDef.get("reviewPoints");
        if (points != null && points.isArray()) {
            for (int i = 0; i < points.size(); i++) {
                JsonNode point = points.get(i);
                if (point != null && point.isObject() && !point.isEmpty()) {
                    // consistencyPolicy missing/non-object stays REVIEW_POINT_SET_INVALID — skip here
                    JsonNode cp = point.get("consistencyPolicy");
                    if (cp != null && cp.isObject()) {
                        // Budget int + text fields
                        requireTextField(cp, "cardinalityMode", rpDefPath);
                        requireIntField(cp, "minCandidates", rpDefPath);
                        requireIntField(cp, "maxCandidates", rpDefPath);
                        requireIntField(cp, "occurrenceBudget", rpDefPath);

                        // Scope must be object, then check arrays and version
                        JsonNode scope = requireObjectField(cp, "scopePolicy", rpDefPath);
                        requireTextField(scope, "version", rpDefPath);
                        requireTextArray(scope, "includedRegionTypes", rpDefPath);
                        requireTextArray(scope, "strongExcludedContextTypes", rpDefPath);
                        requireTextArray(scope, "requiredAttributionSignals", rpDefPath);
                        requireTextArray(scope, "strongExcludedSemanticContexts", rpDefPath);

                        // Canonical must be object
                        JsonNode canon = requireObjectField(cp, "canonicalizationPolicy", rpDefPath);
                        requireTextField(canon, "version", rpDefPath);
                        requireTextField(canon, "valueType", rpDefPath);
                        requireTextField(canon, "unit", rpDefPath);

                        // Anchor must be object, then check arrays and version
                        JsonNode anchor = requireObjectField(cp, "anchorIdentityPolicy", rpDefPath);
                        requireTextField(anchor, "version", rpDefPath);
                        requireTextArray(anchor, "blockIdentity", rpDefPath);
                        requireTextArray(anchor, "tableCellIdentity", rpDefPath);
                    }
                }
            }
        }
    }

    private void requireBooleanField(JsonNode parent, String field, String path) {
        JsonNode node = parent.get(field);
        if (node == null || !node.isBoolean()) {
            throw new RuleSetLoadException(RuleSetLoadException.RuleSetLoadReason.RESOURCE_JSON_INVALID,
                VERSION, path);
        }
    }

    private void requireIntField(JsonNode parent, String field, String path) {
        JsonNode node = parent.get(field);
        if (node == null || !node.isInt()) {
            throw new RuleSetLoadException(RuleSetLoadException.RuleSetLoadReason.RESOURCE_JSON_INVALID,
                VERSION, path);
        }
    }

    /** Throws RESOURCE_JSON_INVALID if text field is missing or non-textual. */
    private void requireTextField(JsonNode parent, String fieldName, String path) {
        JsonNode node = parent.get(fieldName);
        if (node == null || !node.isTextual()) {
            throw new RuleSetLoadException(RuleSetLoadException.RuleSetLoadReason.RESOURCE_JSON_INVALID,
                VERSION, path);
        }
    }

    /** Throws RESOURCE_JSON_INVALID if missing/non-object; returns the node. */
    private JsonNode requireObjectField(JsonNode parent, String field, String path) {
        JsonNode node = parent.get(field);
        if (node == null || !node.isObject()) {
            throw new RuleSetLoadException(RuleSetLoadException.RuleSetLoadReason.RESOURCE_JSON_INVALID,
                VERSION, path);
        }
        return node;
    }

    /** Throws RESOURCE_JSON_INVALID if missing/non-array. */
    private void requireArrayField(JsonNode parent, String field, String path) {
        JsonNode node = parent.get(field);
        if (node == null || !node.isArray()) {
            throw new RuleSetLoadException(RuleSetLoadException.RuleSetLoadReason.RESOURCE_JSON_INVALID,
                VERSION, path);
        }
    }

    /** Throws RESOURCE_JSON_INVALID if any array element is non-textual. */
    private void requireTextArrayElements(JsonNode arr, String path) {
        for (JsonNode elem : arr) {
            if (!elem.isTextual()) {
                throw new RuleSetLoadException(RuleSetLoadException.RuleSetLoadReason.RESOURCE_JSON_INVALID,
                    VERSION, path);
            }
        }
    }

    /** Require a field to exist as array with all-text elements. */
    private void requireTextArray(JsonNode parent, String field, String path) {
        JsonNode arr = parent.get(field);
        if (arr == null || !arr.isArray()) {
            throw new RuleSetLoadException(RuleSetLoadException.RuleSetLoadReason.RESOURCE_JSON_INVALID,
                VERSION, path);
        }
        for (JsonNode elem : arr) {
            if (!elem.isTextual()) {
                throw new RuleSetLoadException(RuleSetLoadException.RuleSetLoadReason.RESOURCE_JSON_INVALID,
                    VERSION, path);
            }
        }
    }

    /** Convert a validated JsonNode array to unmodifiable List<String>. */
    private List<String> textListFromArray(JsonNode arr) {
        List<String> result = new ArrayList<>();
        for (JsonNode elem : arr) {
            result.add(elem.textValue());
        }
        return List.copyOf(result);
    }

    // ════════════════════════ common helpers ════════════════════════

    private String resolveAndValidatePath(String rawRef) {
        if (rawRef.startsWith("/") || rawRef.startsWith("\\")) {
            throw new RuleSetLoadException(RuleSetLoadException.RuleSetLoadReason.REFERENCE_PATH_INVALID,
                VERSION, MANIFEST_PATH);
        }
        if (rawRef.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*")) {
            throw new RuleSetLoadException(RuleSetLoadException.RuleSetLoadReason.REFERENCE_PATH_INVALID,
                VERSION, MANIFEST_PATH);
        }
        if (rawRef.indexOf('\0') >= 0) {
            throw new RuleSetLoadException(RuleSetLoadException.RuleSetLoadReason.REFERENCE_PATH_INVALID,
                VERSION, MANIFEST_PATH);
        }
        for (int i = 0; i < rawRef.length(); i++) {
            char c = rawRef.charAt(i);
            if (c < 32 && c != '\t' && c != '\n' && c != '\r') {
                throw new RuleSetLoadException(RuleSetLoadException.RuleSetLoadReason.REFERENCE_PATH_INVALID,
                    VERSION, MANIFEST_PATH);
            }
        }

        String manifestDir = "cqcp/review-assets/rule-sets/";
        String combined = manifestDir + rawRef;
        String[] parts = combined.split("/");
        List<String> normalized = new ArrayList<>();
        for (String part : parts) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) {
                if (normalized.isEmpty()) {
                    throw new RuleSetLoadException(RuleSetLoadException.RuleSetLoadReason.REFERENCE_PATH_INVALID,
                        VERSION, MANIFEST_PATH);
                }
                normalized.remove(normalized.size() - 1);
            } else {
                normalized.add(part);
            }
        }
        String normalizedPath = String.join("/", normalized);
        if (!normalizedPath.startsWith(CLASS_PATH_PREFIX)) {
            throw new RuleSetLoadException(RuleSetLoadException.RuleSetLoadReason.REFERENCE_PATH_INVALID,
                VERSION, MANIFEST_PATH);
        }
        String suffix = normalizedPath.substring(CLASS_PATH_PREFIX.length());
        if (!FROZEN_TARGET.equals(suffix)) {
            throw new RuleSetLoadException(RuleSetLoadException.RuleSetLoadReason.REFERENCE_TARGET_MISMATCH,
                VERSION, MANIFEST_PATH);
        }
        return normalizedPath;
    }

    /** Throws RESOURCE_JSON_INVALID if missing or non-textual. */
    private String requiredString(JsonNode parent, String fieldName, String path) {
        JsonNode node = parent.get(fieldName);
        if (node == null || !node.isTextual()) {
            throw new RuleSetLoadException(RuleSetLoadException.RuleSetLoadReason.RESOURCE_JSON_INVALID,
                VERSION, path);
        }
        return node.textValue();
    }

    private record FrozenCanonical(String valueType, String unit) {}
    private record ModuleRef(String assetId, String version, String path) {}

    static final class RuleSetLoadException extends RuntimeException {
        enum RuleSetLoadReason {
            REFERENCE_PATH_INVALID, REFERENCE_TARGET_MISMATCH, RESOURCE_NOT_FOUND, RESOURCE_JSON_INVALID,
            ASSET_IDENTITY_INVALID, ASSET_RELEASE_STATE_INVALID, REVIEW_POINT_SET_INVALID,
            CONSISTENCY_BUDGET_INVALID, SCOPE_POLICY_INVALID, CANONICALIZATION_POLICY_INVALID,
            ANCHOR_IDENTITY_POLICY_INVALID, FORBIDDEN_POLICY_CONTENT
        }

        final RuleSetLoadReason reason;
        final String version;
        final String resourcePath;

        RuleSetLoadException(RuleSetLoadReason reason, String version, String resourcePath) {
            super(version + ": " + reason + " — " + resourcePath);
            this.reason = reason;
            this.version = version;
            this.resourcePath = resourcePath;
        }
    }
}
