import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import os from "node:os";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(__dirname, "..");
const VALIDATOR = path.resolve(__dirname, "validate-review-assets.mjs");
const ASSETS_SRC = path.resolve(REPO_ROOT, "packages", "review-assets");

// ── helpers ──

function collectAssetFiles(root) {
  const results = [];
  const entries = fs.readdirSync(root, { withFileTypes: true });
  for (const e of entries) {
    const full = path.join(root, e.name);
    if (e.isDirectory()) {
      results.push(...collectAssetFiles(full));
    } else if (e.name.endsWith(".json")) {
      results.push(full);
    }
  }
  return results;
}

function copyAllAssets(tmpRoot) {
  for (const src of collectAssetFiles(ASSETS_SRC)) {
    const rel = path.relative(ASSETS_SRC, src);
    const dest = path.join(tmpRoot, rel);
    fs.mkdirSync(path.dirname(dest), { recursive: true });
    fs.copyFileSync(src, dest);
  }
}

function copyAssetsSubset(tmpRoot, names) {
  const nameSet = new Set(names);
  for (const src of collectAssetFiles(ASSETS_SRC)) {
    const rel = path.relative(ASSETS_SRC, src);
    const fname = path.basename(src);
    if (nameSet.has(fname)) {
      const dest = path.join(tmpRoot, rel);
      fs.mkdirSync(path.dirname(dest), { recursive: true });
      fs.copyFileSync(src, dest);
    }
  }
}

function validate(tmpRoot) {
  const result = spawnSync(process.execPath, [VALIDATOR, tmpRoot], {
    encoding: "utf8",
    stdio: ["pipe", "pipe", "pipe"],
    timeout: 10000,
  });
  return {
    ok: result.status === 0,
    status: result.status,
    stdout: result.stdout || "",
    stderr: result.stderr || "",
    error: result.error,
  };
}

function readJson(filePath) {
  return JSON.parse(fs.readFileSync(filePath, "utf8"));
}

function writeJson(filePath, data) {
  fs.writeFileSync(filePath, JSON.stringify(data, null, 2), "utf8");
}

function findFile(tmpRoot, basename) {
  for (const f of collectAssetFiles(tmpRoot)) {
    if (path.basename(f) === basename) return f;
  }
  return null;
}

const ASSET_NAMES = [
  "ruleset-v20260705.1.json",
  "ruleset-v20260715.1.json",
  "review-points-v20260705.1.json",
  "review-points-v20260715.1.json",
  "pattern-library-v20260705.1.json",
  "field-lexicon-v20260705.1.json",
  "prompts-v20260705.1.json",
  "contract-type-profile-v20260705.1.json",
  "evidence-selector-v20260705.1.json",
];

const LEGACY_NAMES = [
  "ruleset-v20260705.1.json",
  "review-points-v20260705.1.json",
  "pattern-library-v20260705.1.json",
  "field-lexicon-v20260705.1.json",
  "prompts-v20260705.1.json",
  "contract-type-profile-v20260705.1.json",
  "evidence-selector-v20260705.1.json",
];

// ── positive tests ──

describe("positive validation", () => {
  it("all nine assets pass", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      const r = validate(tmp);
      assert.equal(r.ok, true, `expected pass, got: ${r.stderr}`);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("legacy seven assets alone pass", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAssetsSubset(tmp, LEGACY_NAMES);
      const r = validate(tmp);
      assert.equal(r.ok, true, `expected pass, got: ${r.stderr}`);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("legacy review points have no consistencyPolicy", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      const legacyRp = findFile(tmp, "review-points-v20260705.1.json");
      const data = readJson(legacyRp);
      for (const rp of data.reviewPoints) {
        assert.equal(Object.prototype.hasOwnProperty.call(rp, "consistencyPolicy"), false,
          `${rp.reviewPointCode}: legacy review point must not have consistencyPolicy`);
      }
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });
});

// ── asset identity assertions ──

describe("asset identity", () => {
  function assertIdentity(filePath, expected) {
    const data = readJson(filePath);
    assert.equal(data.assetId, expected.assetId);
    assert.equal(data.version, expected.version);
    assert.equal(data.status, expected.status);
    assert.equal(data.source.type, expected.sourceType);
    assert.equal(data.source.runtimeBinding, expected.runtimeBinding);
  }

  it("new RuleSet has correct frozen identity", () => {
    const f = path.resolve(ASSETS_SRC, "rule-sets", "ruleset-v20260715.1.json");
    assertIdentity(f, {
      assetId: "cqcp.ruleset.mvp.consistency-set.v20260715.1",
      version: "v20260715.1",
      status: "DRAFT",
      sourceType: "architecture-approved-policy",
      runtimeBinding: "NOT_BOUND",
    });
  });

  it("new review points have correct frozen identity", () => {
    const f = path.resolve(ASSETS_SRC, "review-point-definitions", "review-points-v20260715.1.json");
    assertIdentity(f, {
      assetId: "cqcp.review-points.mvp.consistency-set.v20260715.1",
      version: "v20260715.1",
      status: "DRAFT",
      sourceType: "architecture-approved-policy",
      runtimeBinding: "NOT_BOUND",
    });
  });

  it("new RuleSet runtimePolicy has all false/NONE", () => {
    const f = path.resolve(ASSETS_SRC, "rule-sets", "ruleset-v20260715.1.json");
    const data = readJson(f);
    assert.equal(data.runtimePolicy.loaderEnabled, false);
    assert.equal(data.runtimePolicy.databasePersistence, false);
    assert.equal(data.runtimePolicy.productionEffect, "NONE");
  });

  it("new RuleSet references new review-points only, other modules v20260705.1", () => {
    const f = path.resolve(ASSETS_SRC, "rule-sets", "ruleset-v20260715.1.json");
    const data = readJson(f);
    const mv = data.moduleVersions;
    assert.equal(mv.reviewPointDefinitions.assetId, "cqcp.review-points.mvp.consistency-set.v20260715.1");
    assert.equal(mv.reviewPointDefinitions.version, "v20260715.1");
    assert.equal(mv.patternLibrary.version, "v20260705.1");
    assert.equal(mv.fieldLexicon.version, "v20260705.1");
    assert.equal(mv.promptTemplates.version, "v20260705.1");
    assert.equal(mv.contractTypeProfiles.version, "v20260705.1");
    assert.equal(mv.evidenceSelectors.version, "v20260705.1");
  });
});

// ── new RuleSet six-module complete fixed mapping ──

describe("new RuleSet six-module fixed mapping", () => {
  it("all six module refs have exact frozen path, assetId, version", () => {
    const f = path.resolve(ASSETS_SRC, "rule-sets", "ruleset-v20260715.1.json");
    const data = readJson(f);
    const mv = data.moduleVersions;

    const expected = {
      reviewPointDefinitions: {
        path: "../review-point-definitions/review-points-v20260715.1.json",
        assetId: "cqcp.review-points.mvp.consistency-set.v20260715.1",
        version: "v20260715.1",
      },
      patternLibrary: {
        path: "../pattern-libraries/pattern-library-v20260705.1.json",
        assetId: "cqcp.pattern-library.mvp.code-current.v20260705.1",
        version: "v20260705.1",
      },
      fieldLexicon: {
        path: "../field-lexicons/field-lexicon-v20260705.1.json",
        assetId: "cqcp.field-lexicon.mvp.code-current.v20260705.1",
        version: "v20260705.1",
      },
      promptTemplates: {
        path: "../prompts/prompts-v20260705.1.json",
        assetId: "cqcp.prompts.mvp.code-current.v20260705.1",
        version: "v20260705.1",
      },
      contractTypeProfiles: {
        path: "../contract-type-profiles/contract-type-profile-v20260705.1.json",
        assetId: "cqcp.contract-type-profile.mvp.code-current.v20260705.1",
        version: "v20260705.1",
      },
      evidenceSelectors: {
        path: "../evidence-selectors/evidence-selector-v20260705.1.json",
        assetId: "cqcp.evidence-selector.mvp.code-current.v20260705.1",
        version: "v20260705.1",
      },
    };

    const keys = Object.keys(expected);
    assert.equal(keys.length, 6, "must have exactly 6 module keys");

    for (const key of keys) {
      const ref = mv[key];
      assert.ok(ref, `moduleVersions.${key} must exist`);
      const exp = expected[key];
      assert.equal(ref.path, exp.path,
        `moduleVersions.${key}.path: expected ${exp.path}, got ${ref.path}`);
      assert.equal(ref.assetId, exp.assetId,
        `moduleVersions.${key}.assetId: expected ${exp.assetId}, got ${ref.assetId}`);
      assert.equal(ref.version, exp.version,
        `moduleVersions.${key}.version: expected ${exp.version}, got ${ref.version}`);
    }
  });

  it("swapping patternLibrary and fieldLexicon ref objects would be caught", () => {
    // Prove that if someone accidentally swaps the entire ref objects,
    // the hardcoded per-key assertions above would fail.
    const f = path.resolve(ASSETS_SRC, "rule-sets", "ruleset-v20260715.1.json");
    const data = readJson(f);

    // patternLibrary and fieldLexicon have different path/assetId/version
    const pl = data.moduleVersions.patternLibrary;
    const fl = data.moduleVersions.fieldLexicon;
    assert.notEqual(pl.path, fl.path, "patternLibrary and fieldLexicon paths must differ");
    assert.notEqual(pl.assetId, fl.assetId, "patternLibrary and fieldLexicon assetIds must differ");
  });
});

// ── frozen nine-point policy asset contract ──

describe("frozen nine-point policy asset contract", () => {
  const NINE_POINT_FROZEN = {
    "PARTY_A_NAME_CONSISTENCY": {
      cardinalityMode: "CONSISTENCY_SET",
      minCandidates: 1,
      maxCandidates: 8,
      occurrenceBudget: 64,
      scopePolicyVersion: "consistency-scope-v20260715.1",
      canonicalizationPolicyVersion: "consistency-canonicalization-v20260715.1",
      anchorIdentityPolicyVersion: "mvp-occurrence-identity-v1",
      valueType: "TEXT",
      unit: "NONE",
    },
    "PARTY_B_NAME_CONSISTENCY": {
      cardinalityMode: "CONSISTENCY_SET",
      minCandidates: 1,
      maxCandidates: 8,
      occurrenceBudget: 64,
      scopePolicyVersion: "consistency-scope-v20260715.1",
      canonicalizationPolicyVersion: "consistency-canonicalization-v20260715.1",
      anchorIdentityPolicyVersion: "mvp-occurrence-identity-v1",
      valueType: "TEXT",
      unit: "NONE",
    },
    "CONTRACT_TOTAL_AMOUNT_CONSISTENCY": {
      cardinalityMode: "CONSISTENCY_SET",
      minCandidates: 1,
      maxCandidates: 8,
      occurrenceBudget: 64,
      scopePolicyVersion: "consistency-scope-v20260715.1",
      canonicalizationPolicyVersion: "consistency-canonicalization-v20260715.1",
      anchorIdentityPolicyVersion: "mvp-occurrence-identity-v1",
      valueType: "DECIMAL",
      unit: "CNY",
    },
    "TAX_AMOUNT_FORMULA_CONSISTENCY": {
      cardinalityMode: "CONSISTENCY_SET",
      minCandidates: 1,
      maxCandidates: 8,
      occurrenceBudget: 64,
      scopePolicyVersion: "consistency-scope-v20260715.1",
      canonicalizationPolicyVersion: "consistency-canonicalization-v20260715.1",
      anchorIdentityPolicyVersion: "mvp-occurrence-identity-v1",
      valueType: "DECIMAL",
      unit: "CNY",
    },
    "PREPAYMENT_RATIO_CONSISTENCY": {
      cardinalityMode: "CONSISTENCY_SET",
      minCandidates: 1,
      maxCandidates: 8,
      occurrenceBudget: 64,
      scopePolicyVersion: "consistency-scope-v20260715.1",
      canonicalizationPolicyVersion: "consistency-canonicalization-v20260715.1",
      anchorIdentityPolicyVersion: "mvp-occurrence-identity-v1",
      valueType: "DECIMAL",
      unit: "PERCENT",
    },
    "PROGRESS_PAYMENT_RATIO_CONSISTENCY": {
      cardinalityMode: "CONSISTENCY_SET",
      minCandidates: 1,
      maxCandidates: 8,
      occurrenceBudget: 64,
      scopePolicyVersion: "consistency-scope-v20260715.1",
      canonicalizationPolicyVersion: "consistency-canonicalization-v20260715.1",
      anchorIdentityPolicyVersion: "mvp-occurrence-identity-v1",
      valueType: "DECIMAL",
      unit: "PERCENT",
    },
    "COMPLETION_PAYMENT_RATIO_CONSISTENCY": {
      cardinalityMode: "CONSISTENCY_SET",
      minCandidates: 1,
      maxCandidates: 8,
      occurrenceBudget: 64,
      scopePolicyVersion: "consistency-scope-v20260715.1",
      canonicalizationPolicyVersion: "consistency-canonicalization-v20260715.1",
      anchorIdentityPolicyVersion: "mvp-occurrence-identity-v1",
      valueType: "DECIMAL",
      unit: "PERCENT",
    },
    "SETTLEMENT_PAYMENT_RATIO_CONSISTENCY": {
      cardinalityMode: "CONSISTENCY_SET",
      minCandidates: 1,
      maxCandidates: 8,
      occurrenceBudget: 64,
      scopePolicyVersion: "consistency-scope-v20260715.1",
      canonicalizationPolicyVersion: "consistency-canonicalization-v20260715.1",
      anchorIdentityPolicyVersion: "mvp-occurrence-identity-v1",
      valueType: "DECIMAL",
      unit: "PERCENT",
    },
    "WARRANTY_RETENTION_RATIO_CONSISTENCY": {
      cardinalityMode: "CONSISTENCY_SET",
      minCandidates: 1,
      maxCandidates: 8,
      occurrenceBudget: 64,
      scopePolicyVersion: "consistency-scope-v20260715.1",
      canonicalizationPolicyVersion: "consistency-canonicalization-v20260715.1",
      anchorIdentityPolicyVersion: "mvp-occurrence-identity-v1",
      valueType: "DECIMAL",
      unit: "PERCENT",
    },
  };

  for (const [code, expected] of Object.entries(NINE_POINT_FROZEN)) {
    it(`${code}: all frozen fields match asset`, () => {
      const f = path.resolve(ASSETS_SRC, "review-point-definitions", "review-points-v20260715.1.json");
      const data = readJson(f);
      const rp = data.reviewPoints.find((p) => p.reviewPointCode === code);
      assert.ok(rp, `review point ${code} must exist`);

      const cp = rp.consistencyPolicy;
      assert.ok(cp, `${code}: consistencyPolicy must exist`);

      assert.equal(cp.cardinalityMode, expected.cardinalityMode,
        `${code}: cardinalityMode`);
      assert.equal(cp.minCandidates, expected.minCandidates,
        `${code}: minCandidates`);
      assert.equal(cp.maxCandidates, expected.maxCandidates,
        `${code}: maxCandidates`);
      assert.equal(cp.occurrenceBudget, expected.occurrenceBudget,
        `${code}: occurrenceBudget`);

      assert.equal(cp.scopePolicy.version, expected.scopePolicyVersion,
        `${code}: scopePolicy.version`);
      assert.equal(cp.canonicalizationPolicy.version, expected.canonicalizationPolicyVersion,
        `${code}: canonicalizationPolicy.version`);
      assert.equal(cp.anchorIdentityPolicy.version, expected.anchorIdentityPolicyVersion,
        `${code}: anchorIdentityPolicy.version`);

      assert.equal(cp.canonicalizationPolicy.valueType, expected.valueType,
        `${code}: canonicalizationPolicy.valueType`);
      assert.equal(cp.canonicalizationPolicy.unit, expected.unit,
        `${code}: canonicalizationPolicy.unit`);
    });
  }
});

// ── source.type classification ──

describe("source.type classification", () => {
  it("policy RuleSet with code-current-mapping fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      const f = findFile(tmp, "ruleset-v20260715.1.json");
      const data = readJson(f);
      data.source.type = "code-current-mapping";
      writeJson(f, data);
      const r = validate(tmp);
      assert.equal(r.ok, false, "expected failure");
      assert.match(r.stderr, /ruleset-v20260715\.1\.json/);
      assert.match(r.stderr, /code-current-mapping/);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("policy review points with code-current-mapping fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      const f = findFile(tmp, "review-points-v20260715.1.json");
      const data = readJson(f);
      data.source.type = "code-current-mapping";
      writeJson(f, data);
      const r = validate(tmp);
      assert.equal(r.ok, false, "expected failure");
      assert.match(r.stderr, /review-points-v20260715\.1\.json/);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("policy asset with missing source.type fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      const f = findFile(tmp, "ruleset-v20260715.1.json");
      const data = readJson(f);
      delete data.source.type;
      writeJson(f, data);
      const r = validate(tmp);
      assert.equal(r.ok, false, "expected failure");
      assert.match(r.stderr, /source\.type is missing/);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("policy asset with illegal source.type value fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      const f = findFile(tmp, "ruleset-v20260715.1.json");
      const data = readJson(f);
      data.source.type = "self-asserted";
      writeJson(f, data);
      const r = validate(tmp);
      assert.equal(r.ok, false, "expected failure");
      assert.match(r.stderr, /self-asserted/);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("legacy RuleSet with architecture-approved-policy fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      const f = findFile(tmp, "ruleset-v20260705.1.json");
      const data = readJson(f);
      data.source.type = "architecture-approved-policy";
      writeJson(f, data);
      const r = validate(tmp);
      assert.equal(r.ok, false, "expected failure");
      assert.match(r.stderr, /ruleset-v20260705\.1\.json/);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("legacy review points with architecture-approved-policy fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      const f = findFile(tmp, "review-points-v20260705.1.json");
      const data = readJson(f);
      data.source.type = "architecture-approved-policy";
      writeJson(f, data);
      const r = validate(tmp);
      assert.equal(r.ok, false, "expected failure");
      assert.match(r.stderr, /review-points-v20260705\.1\.json/);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("legacy asset with missing source.type fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      const f = findFile(tmp, "ruleset-v20260705.1.json");
      const data = readJson(f);
      delete data.source.type;
      writeJson(f, data);
      const r = validate(tmp);
      assert.equal(r.ok, false, "expected failure");
      assert.match(r.stderr, /source\.type is missing/);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("legacy asset with illegal source.type value fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      const f = findFile(tmp, "ruleset-v20260705.1.json");
      const data = readJson(f);
      data.source.type = "self-asserted-legacy";
      writeJson(f, data);
      const r = validate(tmp);
      assert.equal(r.ok, false, "expected failure");
      assert.match(r.stderr, /self-asserted-legacy/);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("source.type classification is content-based, not self-declared", () => {
    // Even if a policy REVIEW_POINT_DEFINITION self-declares as code-current-mapping,
    // it must fail because the content (consistencyPolicy presence) makes it policy.
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      const f = findFile(tmp, "review-points-v20260715.1.json");
      const data = readJson(f);
      data.source.type = "code-current-mapping";
      writeJson(f, data);
      const r = validate(tmp);
      assert.equal(r.ok, false, "self-declared code-current-mapping on policy content must fail");
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });
});

// ── manifest breakage: legacy vs new ──

describe("manifest module reference breakage", () => {
  it("broken legacy manifest module path fails, locating legacy", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      const f = findFile(tmp, "ruleset-v20260705.1.json");
      const data = readJson(f);
      data.moduleVersions.patternLibrary.path = "../nonexistent/file.json";
      writeJson(f, data);
      const r = validate(tmp);
      assert.equal(r.ok, false);
      assert.match(r.stderr, /ruleset-v20260705\.1\.json/);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("broken legacy manifest assetId mismatch fails, locating legacy", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      const f = findFile(tmp, "ruleset-v20260705.1.json");
      const data = readJson(f);
      data.moduleVersions.fieldLexicon.assetId = "wrong.asset.id";
      writeJson(f, data);
      const r = validate(tmp);
      assert.equal(r.ok, false);
      assert.match(r.stderr, /ruleset-v20260705\.1\.json/);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("broken legacy manifest version mismatch fails, locating legacy", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      const f = findFile(tmp, "ruleset-v20260705.1.json");
      const data = readJson(f);
      data.moduleVersions.promptTemplates.version = "v9999.1";
      writeJson(f, data);
      const r = validate(tmp);
      assert.equal(r.ok, false);
      assert.match(r.stderr, /ruleset-v20260705\.1\.json/);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("broken new manifest module path fails, locating new", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      const f = findFile(tmp, "ruleset-v20260715.1.json");
      const data = readJson(f);
      data.moduleVersions.contractTypeProfiles.path = "../nonexistent/file.json";
      writeJson(f, data);
      const r = validate(tmp);
      assert.equal(r.ok, false);
      assert.match(r.stderr, /ruleset-v20260715\.1\.json/);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("broken new manifest assetId mismatch fails, locating new", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      const f = findFile(tmp, "ruleset-v20260715.1.json");
      const data = readJson(f);
      data.moduleVersions.evidenceSelectors.assetId = "wrong.asset.id";
      writeJson(f, data);
      const r = validate(tmp);
      assert.equal(r.ok, false);
      assert.match(r.stderr, /ruleset-v20260715\.1\.json/);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("broken new manifest version mismatch fails, locating new", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      const f = findFile(tmp, "ruleset-v20260715.1.json");
      const data = readJson(f);
      data.moduleVersions.patternLibrary.version = "v9999.1";
      writeJson(f, data);
      const r = validate(tmp);
      assert.equal(r.ok, false);
      assert.match(r.stderr, /ruleset-v20260715\.1\.json/);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("both manifests broken independently report both errors", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      const legacy = findFile(tmp, "ruleset-v20260705.1.json");
      const ld = readJson(legacy);
      ld.moduleVersions.patternLibrary.path = "../nonexistent/legacy.json";
      writeJson(legacy, ld);

      const newRs = findFile(tmp, "ruleset-v20260715.1.json");
      const nd = readJson(newRs);
      nd.moduleVersions.fieldLexicon.assetId = "wrong.asset.id";
      writeJson(newRs, nd);

      const r = validate(tmp);
      assert.equal(r.ok, false);
      assert.match(r.stderr, /ruleset-v20260705\.1\.json/);
      assert.match(r.stderr, /ruleset-v20260715\.1\.json/);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("breaking new manifest keeps legacy intact in error output", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      const newRs = findFile(tmp, "ruleset-v20260715.1.json");
      const nd = readJson(newRs);
      nd.moduleVersions.promptTemplates.version = "v9999.1";
      writeJson(newRs, nd);

      const r = validate(tmp);
      assert.equal(r.ok, false);
      // Error should mention new manifest, not legacy
      assert.match(r.stderr, /ruleset-v20260715\.1\.json/);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });
});

// ── budget errors ──

describe("budget validation", () => {
  function mutateOnePoint(tmpRoot, code, mutator) {
    const f = findFile(tmpRoot, "review-points-v20260715.1.json");
    const data = readJson(f);
    const rp = data.reviewPoints.find((p) => p.reviewPointCode === code);
    mutator(rp.consistencyPolicy);
    writeJson(f, data);
  }

  it("missing occurrenceBudget fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      mutateOnePoint(tmp, "PARTY_A_NAME_CONSISTENCY", (cp) => { delete cp.occurrenceBudget; });
      const r = validate(tmp);
      assert.equal(r.ok, false);
      assert.match(r.stderr, /occurrenceBudget/);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("maxCandidates = 1 fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      mutateOnePoint(tmp, "PARTY_B_NAME_CONSISTENCY", (cp) => { cp.maxCandidates = 1; });
      const r = validate(tmp);
      assert.equal(r.ok, false);
      assert.match(r.stderr, /maxCandidates/);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("maxCandidates = 0 fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      mutateOnePoint(tmp, "PARTY_A_NAME_CONSISTENCY", (cp) => { cp.maxCandidates = 0; });
      const r = validate(tmp);
      assert.equal(r.ok, false);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("occurrenceBudget < maxCandidates fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      mutateOnePoint(tmp, "CONTRACT_TOTAL_AMOUNT_CONSISTENCY", (cp) => {
        cp.maxCandidates = 8;
        cp.occurrenceBudget = 4;
      });
      const r = validate(tmp);
      assert.equal(r.ok, false);
      assert.match(r.stderr, /occurrenceBudget/);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("occurrenceBudget == maxCandidates passes (legal boundary)", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      mutateOnePoint(tmp, "CONTRACT_TOTAL_AMOUNT_CONSISTENCY", (cp) => {
        cp.maxCandidates = 8;
        cp.occurrenceBudget = 8;
      });
      const r = validate(tmp);
      assert.equal(r.ok, true, `expected pass, got: ${r.stderr}`);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("maxCandidates is float fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      mutateOnePoint(tmp, "TAX_AMOUNT_FORMULA_CONSISTENCY", (cp) => { cp.maxCandidates = 8.5; });
      const r = validate(tmp);
      assert.equal(r.ok, false);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("occurrenceBudget is float fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      mutateOnePoint(tmp, "PREPAYMENT_RATIO_CONSISTENCY", (cp) => { cp.occurrenceBudget = 64.5; });
      const r = validate(tmp);
      assert.equal(r.ok, false);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("minCandidates = 2 fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      mutateOnePoint(tmp, "PROGRESS_PAYMENT_RATIO_CONSISTENCY", (cp) => { cp.minCandidates = 2; });
      const r = validate(tmp);
      assert.equal(r.ok, false);
      assert.match(r.stderr, /minCandidates/);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("minCandidates = 0 fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      mutateOnePoint(tmp, "SETTLEMENT_PAYMENT_RATIO_CONSISTENCY", (cp) => { cp.minCandidates = 0; });
      const r = validate(tmp);
      assert.equal(r.ok, false);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("cardinalityMode not CONSISTENCY_SET fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      mutateOnePoint(tmp, "WARRANTY_RETENTION_RATIO_CONSISTENCY", (cp) => { cp.cardinalityMode = "MULTI"; });
      const r = validate(tmp);
      assert.equal(r.ok, false);
      assert.match(r.stderr, /cardinalityMode/);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("missing cardinalityMode fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      mutateOnePoint(tmp, "PARTY_A_NAME_CONSISTENCY", (cp) => { delete cp.cardinalityMode; });
      const r = validate(tmp);
      assert.equal(r.ok, false);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });
});

// ── nine-point integrity ──

describe("nine-point integrity", () => {
  it("missing a review point fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      const f = findFile(tmp, "review-points-v20260715.1.json");
      const data = readJson(f);
      data.reviewPoints = data.reviewPoints.filter((p) => p.reviewPointCode !== "PARTY_A_NAME_CONSISTENCY");
      writeJson(f, data);
      const r = validate(tmp);
      assert.equal(r.ok, false);
      assert.match(r.stderr, /missing review point "PARTY_A_NAME_CONSISTENCY"/);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("duplicate reviewPointCode fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      const f = findFile(tmp, "review-points-v20260715.1.json");
      const data = readJson(f);
      const dup = JSON.parse(JSON.stringify(data.reviewPoints[0]));
      data.reviewPoints.push(dup);
      writeJson(f, data);
      const r = validate(tmp);
      assert.equal(r.ok, false);
      assert.match(r.stderr, /duplicate reviewPointCode/);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("point missing consistencyPolicy fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      const f = findFile(tmp, "review-points-v20260715.1.json");
      const data = readJson(f);
      delete data.reviewPoints[3].consistencyPolicy;
      writeJson(f, data);
      const r = validate(tmp);
      assert.equal(r.ok, false);
      assert.match(r.stderr, /consistencyPolicy/);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("pushing empty object to reviewPoints fails with locatable error", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      const f = findFile(tmp, "review-points-v20260715.1.json");
      const data = readJson(f);
      data.reviewPoints.push({});
      writeJson(f, data);
      const r = validate(tmp);
      assert.equal(r.ok, false);
      // Must locate the faulty index
      assert.match(r.stderr, /reviewPoints\[9\]/);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("too many review points fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      const f = findFile(tmp, "review-points-v20260715.1.json");
      const data = readJson(f);
      const extra = JSON.parse(JSON.stringify(data.reviewPoints[0]));
      extra.reviewPointCode = "EXTRA_POINT";
      data.reviewPoints.push(extra);
      writeJson(f, data);
      const r = validate(tmp);
      assert.equal(r.ok, false);
      assert.match(r.stderr, /reviewPoints must have exactly 9/);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });
});

// ── scope/exclusion/attribution array precision ──

describe("scope exclusion attribution arrays", () => {
  function mutateScope(tmpRoot, code, mutator) {
    const f = findFile(tmpRoot, "review-points-v20260715.1.json");
    const data = readJson(f);
    const rp = data.reviewPoints.find((p) => p.reviewPointCode === code);
    mutator(rp.consistencyPolicy.scopePolicy);
    writeJson(f, data);
  }

  it("includedRegionTypes missing BODY fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      mutateScope(tmp, "PARTY_A_NAME_CONSISTENCY", (sp) => { sp.includedRegionTypes = ["APPENDIX"]; });
      const r = validate(tmp);
      assert.equal(r.ok, false);
      assert.match(r.stderr, /includedRegionTypes/);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("includedRegionTypes extra item fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      mutateScope(tmp, "PARTY_B_NAME_CONSISTENCY", (sp) => { sp.includedRegionTypes = ["BODY", "APPENDIX", "TABLE"]; });
      const r = validate(tmp);
      assert.equal(r.ok, false);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("includedRegionTypes wrong order fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      mutateScope(tmp, "CONTRACT_TOTAL_AMOUNT_CONSISTENCY", (sp) => { sp.includedRegionTypes = ["APPENDIX", "BODY"]; });
      const r = validate(tmp);
      assert.equal(r.ok, false);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("strongExcludedContextTypes missing TOC fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      mutateScope(tmp, "TAX_AMOUNT_FORMULA_CONSISTENCY", (sp) => { sp.strongExcludedContextTypes = ["HEADER_FOOTER", "DELETED", "VOIDED"]; });
      const r = validate(tmp);
      assert.equal(r.ok, false);
      assert.match(r.stderr, /strongExcludedContextTypes/);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("strongExcludedContextTypes extra item fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      mutateScope(tmp, "PREPAYMENT_RATIO_CONSISTENCY", (sp) => { sp.strongExcludedContextTypes.push("EXTRA"); });
      const r = validate(tmp);
      assert.equal(r.ok, false);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("strongExcludedContextTypes wrong order fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      mutateScope(tmp, "PROGRESS_PAYMENT_RATIO_CONSISTENCY", (sp) => {
        sp.strongExcludedContextTypes = ["VOIDED", "DELETED", "HEADER_FOOTER", "TOC"];
      });
      const r = validate(tmp);
      assert.equal(r.ok, false);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("requiredAttributionSignals missing one fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      mutateScope(tmp, "COMPLETION_PAYMENT_RATIO_CONSISTENCY", (sp) => {
        sp.requiredAttributionSignals = ["SOURCE_CONFIDENCE", "PARSE_CONFIDENCE", "VALUE_GRAMMAR", "ROLE_LABEL", "REGION_CONTEXT"];
      });
      const r = validate(tmp);
      assert.equal(r.ok, false);
      assert.match(r.stderr, /requiredAttributionSignals/);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("requiredAttributionSignals extra item fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      mutateScope(tmp, "SETTLEMENT_PAYMENT_RATIO_CONSISTENCY", (sp) => { sp.requiredAttributionSignals.push("EXTRA"); });
      const r = validate(tmp);
      assert.equal(r.ok, false);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("requiredAttributionSignals wrong order fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      mutateScope(tmp, "WARRANTY_RETENTION_RATIO_CONSISTENCY", (sp) => {
        sp.requiredAttributionSignals = ["ANCHOR_IDENTITY", "REGION_CONTEXT", "ROLE_LABEL", "VALUE_GRAMMAR", "PARSE_CONFIDENCE", "SOURCE_CONFIDENCE"];
      });
      const r = validate(tmp);
      assert.equal(r.ok, false);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("includedRegionTypes empty array fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      mutateScope(tmp, "PARTY_A_NAME_CONSISTENCY", (sp) => { sp.includedRegionTypes = []; });
      const r = validate(tmp);
      assert.equal(r.ok, false);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("scopePolicy.version empty string fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      mutateScope(tmp, "PARTY_A_NAME_CONSISTENCY", (sp) => { sp.version = ""; });
      const r = validate(tmp);
      assert.equal(r.ok, false);
      assert.match(r.stderr, /scopePolicy\.version/);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });
});

// ── PARTY_A semantic exclusion ──

describe("PARTY_A semantic exclusion", () => {
  function mutatePartyA(tmpRoot, mutator) {
    const f = findFile(tmpRoot, "review-points-v20260715.1.json");
    const data = readJson(f);
    const rp = data.reviewPoints.find((p) => p.reviewPointCode === "PARTY_A_NAME_CONSISTENCY");
    mutator(rp.consistencyPolicy.scopePolicy);
    writeJson(f, data);
  }

  function mutateOtherPoint(tmpRoot, code, mutator) {
    const f = findFile(tmpRoot, "review-points-v20260715.1.json");
    const data = readJson(f);
    const rp = data.reviewPoints.find((p) => p.reviewPointCode === code);
    mutator(rp.consistencyPolicy.scopePolicy);
    writeJson(f, data);
  }

  it("PARTY_A missing semantic exclusion fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      mutatePartyA(tmp, (sp) => { sp.strongExcludedSemanticContexts = []; });
      const r = validate(tmp);
      assert.equal(r.ok, false);
      assert.match(r.stderr, /strongExcludedSemanticContexts/);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("PARTY_A extra semantic exclusion fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      mutatePartyA(tmp, (sp) => { sp.strongExcludedSemanticContexts.push("EXTRA_CONTEXT"); });
      const r = validate(tmp);
      assert.equal(r.ok, false);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("PARTY_A wrong order fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      mutatePartyA(tmp, (sp) => {
        sp.strongExcludedSemanticContexts = ["AGREEMENT_PREAMBLE_CONTRACT_NAME_MENTION", "CONTRACT_TITLE_NAME_MENTION"];
      });
      const r = validate(tmp);
      assert.equal(r.ok, false);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("PARTY_B with non-empty semantic exclusion fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      mutateOtherPoint(tmp, "PARTY_B_NAME_CONSISTENCY", (sp) => {
        sp.strongExcludedSemanticContexts = ["CONTRACT_TITLE_NAME_MENTION"];
      });
      const r = validate(tmp);
      assert.equal(r.ok, false);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("CONTRACT_TOTAL_AMOUNT with non-empty semantic exclusion fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      mutateOtherPoint(tmp, "CONTRACT_TOTAL_AMOUNT_CONSISTENCY", (sp) => {
        sp.strongExcludedSemanticContexts = ["SOME_CONTEXT"];
      });
      const r = validate(tmp);
      assert.equal(r.ok, false);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });
});

// ── anchor identity ──

describe("anchor identity", () => {
  function mutateAnchor(tmpRoot, code, mutator) {
    const f = findFile(tmpRoot, "review-points-v20260715.1.json");
    const data = readJson(f);
    const rp = data.reviewPoints.find((p) => p.reviewPointCode === code);
    mutator(rp.consistencyPolicy.anchorIdentityPolicy);
    writeJson(f, data);
  }

  it("blockIdentity missing reviewPointCode fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      mutateAnchor(tmp, "PARTY_A_NAME_CONSISTENCY", (aip) => { aip.blockIdentity = ["blockId"]; });
      const r = validate(tmp);
      assert.equal(r.ok, false);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("blockIdentity extra field fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      mutateAnchor(tmp, "PARTY_B_NAME_CONSISTENCY", (aip) => { aip.blockIdentity = ["reviewPointCode", "blockId", "extra"]; });
      const r = validate(tmp);
      assert.equal(r.ok, false);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("blockIdentity wrong order fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      mutateAnchor(tmp, "CONTRACT_TOTAL_AMOUNT_CONSISTENCY", (aip) => { aip.blockIdentity = ["blockId", "reviewPointCode"]; });
      const r = validate(tmp);
      assert.equal(r.ok, false);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("tableCellIdentity missing previewElementRef fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      mutateAnchor(tmp, "TAX_AMOUNT_FORMULA_CONSISTENCY", (aip) => { aip.tableCellIdentity = ["reviewPointCode", "blockId"]; });
      const r = validate(tmp);
      assert.equal(r.ok, false);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("tableCellIdentity wrong order fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      mutateAnchor(tmp, "COMPLETION_PAYMENT_RATIO_CONSISTENCY", (aip) => {
        aip.tableCellIdentity = ["previewElementRef", "blockId", "reviewPointCode"];
      });
      const r = validate(tmp);
      assert.equal(r.ok, false);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("tableCellIdentity extra field fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      mutateAnchor(tmp, "PREPAYMENT_RATIO_CONSISTENCY", (aip) => { aip.tableCellIdentity = ["reviewPointCode", "blockId", "previewElementRef", "extra"]; });
      const r = validate(tmp);
      assert.equal(r.ok, false);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("anchorIdentityPolicy version empty fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      mutateAnchor(tmp, "PROGRESS_PAYMENT_RATIO_CONSISTENCY", (aip) => { aip.version = ""; });
      const r = validate(tmp);
      assert.equal(r.ok, false);
      assert.match(r.stderr, /anchorIdentityPolicy\.version/);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });
});

// ── canonicalization per-point ──

describe("canonicalization per-point", () => {
  function mutateCanon(tmpRoot, code, mutator) {
    const f = findFile(tmpRoot, "review-points-v20260715.1.json");
    const data = readJson(f);
    const rp = data.reviewPoints.find((p) => p.reviewPointCode === code);
    mutator(rp.consistencyPolicy.canonicalizationPolicy);
    writeJson(f, data);
  }

  it("PARTY_A wrong valueType fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      mutateCanon(tmp, "PARTY_A_NAME_CONSISTENCY", (c) => { c.valueType = "DECIMAL"; });
      const r = validate(tmp);
      assert.equal(r.ok, false);
      assert.match(r.stderr, /canonicalization mismatch/);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("PARTY_B wrong unit fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      mutateCanon(tmp, "PARTY_B_NAME_CONSISTENCY", (c) => { c.unit = "CNY"; });
      const r = validate(tmp);
      assert.equal(r.ok, false);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("CONTRACT_TOTAL_AMOUNT wrong valueType fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      mutateCanon(tmp, "CONTRACT_TOTAL_AMOUNT_CONSISTENCY", (c) => { c.valueType = "TEXT"; });
      const r = validate(tmp);
      assert.equal(r.ok, false);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("TAX_AMOUNT_FORMULA wrong unit fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      mutateCanon(tmp, "TAX_AMOUNT_FORMULA_CONSISTENCY", (c) => { c.unit = "PERCENT"; });
      const r = validate(tmp);
      assert.equal(r.ok, false);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("ratio point wrong valueType fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      mutateCanon(tmp, "PREPAYMENT_RATIO_CONSISTENCY", (c) => { c.valueType = "TEXT"; });
      const r = validate(tmp);
      assert.equal(r.ok, false);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("valueType not in enum fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      mutateCanon(tmp, "SETTLEMENT_PAYMENT_RATIO_CONSISTENCY", (c) => { c.valueType = "INTEGER"; });
      const r = validate(tmp);
      assert.equal(r.ok, false);
      assert.match(r.stderr, /valueType/);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("unit not in enum fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      mutateCanon(tmp, "WARRANTY_RETENTION_RATIO_CONSISTENCY", (c) => { c.unit = "USD"; });
      const r = validate(tmp);
      assert.equal(r.ok, false);
      assert.match(r.stderr, /unit/);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("canonicalizationPolicy version empty fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      mutateCanon(tmp, "PARTY_A_NAME_CONSISTENCY", (c) => { c.version = ""; });
      const r = validate(tmp);
      assert.equal(r.ok, false);
      assert.match(r.stderr, /canonicalizationPolicy\.version/);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });
});

// ── runtime activation ──

describe("runtime activation", () => {
  it("new RuleSet loaderEnabled=true fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      const f = findFile(tmp, "ruleset-v20260715.1.json");
      const data = readJson(f);
      data.runtimePolicy.loaderEnabled = true;
      writeJson(f, data);
      const r = validate(tmp);
      assert.equal(r.ok, false);
      assert.match(r.stderr, /loaderEnabled/);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("new RuleSet databasePersistence=true fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      const f = findFile(tmp, "ruleset-v20260715.1.json");
      const data = readJson(f);
      data.runtimePolicy.databasePersistence = true;
      writeJson(f, data);
      const r = validate(tmp);
      assert.equal(r.ok, false);
      assert.match(r.stderr, /databasePersistence/);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("new RuleSet productionEffect=ACTIVE fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      const f = findFile(tmp, "ruleset-v20260715.1.json");
      const data = readJson(f);
      data.runtimePolicy.productionEffect = "ACTIVE";
      writeJson(f, data);
      const r = validate(tmp);
      assert.equal(r.ok, false);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("new RuleSet productionEffect=PARTIAL fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      const f = findFile(tmp, "ruleset-v20260715.1.json");
      const data = readJson(f);
      data.runtimePolicy.productionEffect = "PARTIAL";
      writeJson(f, data);
      const r = validate(tmp);
      assert.equal(r.ok, false);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("new RuleSet missing runtimePolicy fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      const f = findFile(tmp, "ruleset-v20260715.1.json");
      const data = readJson(f);
      delete data.runtimePolicy;
      writeJson(f, data);
      const r = validate(tmp);
      assert.equal(r.ok, false);
      assert.match(r.stderr, /runtimePolicy/);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("new RuleSet runtimeBinding=BOUND fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      const f = findFile(tmp, "ruleset-v20260715.1.json");
      const data = readJson(f);
      data.source.runtimeBinding = "BOUND";
      writeJson(f, data);
      const r = validate(tmp);
      assert.equal(r.ok, false);
      assert.match(r.stderr, /runtimeBinding/);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("legacy RuleSet loaderEnabled=true fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      const f = findFile(tmp, "ruleset-v20260705.1.json");
      const data = readJson(f);
      data.runtimePolicy.loaderEnabled = true;
      writeJson(f, data);
      const r = validate(tmp);
      assert.equal(r.ok, false);
      assert.match(r.stderr, /loaderEnabled/);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });
});

// ── forbidden patterns ──

describe("forbidden patterns", () => {
  function mutateCp(tmpRoot, code, mutator) {
    const f = findFile(tmpRoot, "review-points-v20260715.1.json");
    const data = readJson(f);
    const rp = data.reviewPoints.find((p) => p.reviewPointCode === code);
    mutator(rp.consistencyPolicy);
    writeJson(f, data);
  }

  it("CQCP-MVP-DOCX in consistencyPolicy fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      mutateCp(tmp, "PARTY_A_NAME_CONSISTENCY", (cp) => { cp.scopePolicy.version = "scope-CQCP-MVP-DOCX-001"; });
      const r = validate(tmp);
      assert.equal(r.ok, false);
      assert.match(r.stderr, /CQCP-MVP-DOCX/);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("occurrenceNo in consistencyPolicy fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      mutateCp(tmp, "PARTY_B_NAME_CONSISTENCY", (cp) => { cp.scopePolicy.occurrenceNo = 1; });
      const r = validate(tmp);
      assert.equal(r.ok, false);
      assert.match(r.stderr, /occurrenceNo/);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("includedInConsistencyEvaluation in consistencyPolicy fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      mutateCp(tmp, "CONTRACT_TOTAL_AMOUNT_CONSISTENCY", (cp) => { cp.includedInConsistencyEvaluation = true; });
      const r = validate(tmp);
      assert.equal(r.ok, false);
      assert.match(r.stderr, /includedInConsistencyEvaluation/);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("human-anchors in consistencyPolicy fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      mutateCp(tmp, "TAX_AMOUNT_FORMULA_CONSISTENCY", (cp) => { cp.scopePolicy.version = "human-anchors-v1"; });
      const r = validate(tmp);
      assert.equal(r.ok, false);
      assert.match(r.stderr, /human-anchors/);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("fixtures in consistencyPolicy fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      mutateCp(tmp, "PREPAYMENT_RATIO_CONSISTENCY", (cp) => { cp.scopePolicy.version = "fixtures/v1"; });
      const r = validate(tmp);
      assert.equal(r.ok, false);
      assert.match(r.stderr, /fixtures/);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it(".docx extension in consistencyPolicy fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      mutateCp(tmp, "PROGRESS_PAYMENT_RATIO_CONSISTENCY", (cp) => { cp.scopePolicy.version = "ref/sample.docx"; });
      const r = validate(tmp);
      assert.equal(r.ok, false);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it(".xlsx extension in consistencyPolicy fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      mutateCp(tmp, "SETTLEMENT_PAYMENT_RATIO_CONSISTENCY", (cp) => { cp.scopePolicy.version = "ref/matrix.xlsx"; });
      const r = validate(tmp);
      assert.equal(r.ok, false);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("forbidden patterns only scanned in consistencyPolicy, not whole asset", () => {
    // The review-points-v20260715.1.json source has paths that include
    // "TASK_SPEC-036-B1-versioned-consistency-policy.md" — this contains
    // "fixtures" as part of the TASK_SPEC name. But wait, it doesn't actually.
    // Let me verify: the source.paths in the asset are generic references.
    // The point is that forbidden tokens appearing ONLY in source/changeReason
    // (outside consistencyPolicy) must NOT cause failure.
    // We already tested the positive case: all 9 assets pass despite having
    // normal metadata. The forbidden check is scoped to each
    // consistencyPolicy's JSON serialization only.
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      // Add "fixtures" to source description — must NOT fail
      const f = findFile(tmp, "review-points-v20260715.1.json");
      const data = readJson(f);
      data.source.bindingNote = "This references fixtures and human-anchors in source metadata only.";
      writeJson(f, data);
      const r = validate(tmp);
      assert.equal(r.ok, true, `expected pass when forbidden tokens only in source metadata: ${r.stderr}`);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });
});

// ── empty policy versions ──

describe("empty policy versions", () => {
  function mutateOnePoint(tmpRoot, code, mutator) {
    const f = findFile(tmpRoot, "review-points-v20260715.1.json");
    const data = readJson(f);
    const rp = data.reviewPoints.find((p) => p.reviewPointCode === code);
    mutator(rp.consistencyPolicy);
    writeJson(f, data);
  }

  it("scopePolicy version empty fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      mutateOnePoint(tmp, "COMPLETION_PAYMENT_RATIO_CONSISTENCY", (cp) => { cp.scopePolicy.version = ""; });
      const r = validate(tmp);
      assert.equal(r.ok, false);
      assert.match(r.stderr, /scopePolicy\.version/);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("canonicalizationPolicy version empty fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      mutateOnePoint(tmp, "WARRANTY_RETENTION_RATIO_CONSISTENCY", (cp) => { cp.canonicalizationPolicy.version = ""; });
      const r = validate(tmp);
      assert.equal(r.ok, false);
      assert.match(r.stderr, /canonicalizationPolicy\.version/);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("anchorIdentityPolicy version empty fails", () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-test-"));
    try {
      copyAllAssets(tmp);
      mutateOnePoint(tmp, "PARTY_B_NAME_CONSISTENCY", (cp) => { cp.anchorIdentityPolicy.version = ""; });
      const r = validate(tmp);
      assert.equal(r.ok, false);
      assert.match(r.stderr, /anchorIdentityPolicy\.version/);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });
});
