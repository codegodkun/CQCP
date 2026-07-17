#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";

const REQUIRED_FIELDS = ["assetId", "assetType", "version", "status", "source", "changeReason"];

const REQUIRED_MODULE_KEYS = [
  "reviewPointDefinitions",
  "patternLibrary",
  "fieldLexicon",
  "promptTemplates",
  "contractTypeProfiles",
  "evidenceSelectors",
];

const EXPECTED_REVIEW_POINT_CODES = [
  "PARTY_A_NAME_CONSISTENCY",
  "PARTY_B_NAME_CONSISTENCY",
  "CONTRACT_TOTAL_AMOUNT_CONSISTENCY",
  "TAX_AMOUNT_FORMULA_CONSISTENCY",
  "PREPAYMENT_RATIO_CONSISTENCY",
  "PROGRESS_PAYMENT_RATIO_CONSISTENCY",
  "COMPLETION_PAYMENT_RATIO_CONSISTENCY",
  "SETTLEMENT_PAYMENT_RATIO_CONSISTENCY",
  "WARRANTY_RETENTION_RATIO_CONSISTENCY",
];

const FROZEN_CANONICAL = {
  "PARTY_A_NAME_CONSISTENCY":           { valueType: "TEXT",    unit: "NONE" },
  "PARTY_B_NAME_CONSISTENCY":           { valueType: "TEXT",    unit: "NONE" },
  "CONTRACT_TOTAL_AMOUNT_CONSISTENCY":  { valueType: "DECIMAL", unit: "CNY" },
  "TAX_AMOUNT_FORMULA_CONSISTENCY":     { valueType: "DECIMAL", unit: "CNY" },
  "PREPAYMENT_RATIO_CONSISTENCY":       { valueType: "DECIMAL", unit: "PERCENT" },
  "PROGRESS_PAYMENT_RATIO_CONSISTENCY": { valueType: "DECIMAL", unit: "PERCENT" },
  "COMPLETION_PAYMENT_RATIO_CONSISTENCY":{ valueType: "DECIMAL", unit: "PERCENT" },
  "SETTLEMENT_PAYMENT_RATIO_CONSISTENCY":{ valueType: "DECIMAL", unit: "PERCENT" },
  "WARRANTY_RETENTION_RATIO_CONSISTENCY":{ valueType: "DECIMAL", unit: "PERCENT" },
};

const EXPECTED_INCLUDED_REGION_TYPES = ["BODY", "APPENDIX"];
const EXPECTED_STRONG_EXCLUDED_CONTEXT_TYPES = ["TOC", "HEADER_FOOTER", "DELETED", "VOIDED"];
const EXPECTED_REQUIRED_ATTRIBUTION_SIGNALS = ["SOURCE_CONFIDENCE", "PARSE_CONFIDENCE", "VALUE_GRAMMAR", "ROLE_LABEL", "REGION_CONTEXT", "ANCHOR_IDENTITY"];
const EXPECTED_BLOCK_IDENTITY = ["reviewPointCode", "blockId"];
const EXPECTED_TABLE_CELL_IDENTITY = ["reviewPointCode", "blockId", "previewElementRef"];
const PARTY_A_SEMANTIC_EXCLUSIONS = ["CONTRACT_TITLE_NAME_MENTION", "AGREEMENT_PREAMBLE_CONTRACT_NAME_MENTION"];

const FORBIDDEN_SUBSTRINGS = [
  "CQCP-MVP-DOCX",
  "occurrenceNo",
  "includedInConsistencyEvaluation",
  "human-anchors",
  "fixtures",
];

const FORBIDDEN_EXTENSION_PATTERNS = [/\.docx\b/i, /\.xlsx\b/i];

function collectJsonFiles(dir) {
  const results = [];
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  for (const entry of entries) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      results.push(...collectJsonFiles(full));
    } else if (entry.isFile() && entry.name.endsWith(".json")) {
      results.push(full);
    }
  }
  return results;
}

function arraysEqual(a, b) {
  if (!Array.isArray(a) || !Array.isArray(b)) return false;
  if (a.length !== b.length) return false;
  for (let i = 0; i < a.length; i++) {
    if (a[i] !== b[i]) return false;
  }
  return true;
}

// ── Main ──

const assetsRoot = process.argv[2] || "packages/review-assets";

if (!fs.existsSync(assetsRoot)) {
  console.error(`Assets root not found: ${assetsRoot}`);
  process.exit(1);
}

const jsonFiles = collectJsonFiles(assetsRoot);

if (jsonFiles.length === 0) {
  console.error(`No .json files found under ${assetsRoot}`);
  process.exit(1);
}

const errors = [];
const assetIds = new Set();
const parsedAssets = [];

// ═══════════════════════════════════════════════
// Phase 1: parse JSON and check per-file fields
// ═══════════════════════════════════════════════

for (const filePath of jsonFiles) {
  let data;
  try {
    const raw = fs.readFileSync(filePath, "utf8");
    data = JSON.parse(raw);
  } catch (e) {
    errors.push(`${filePath}: JSON parse failed — ${e.message}`);
    continue;
  }

  parsedAssets.push({ filePath, data });

  for (const field of REQUIRED_FIELDS) {
    if (!Object.prototype.hasOwnProperty.call(data, field)) {
      errors.push(`${filePath}: missing required field "${field}"`);
    }
  }

  if (typeof data.assetId === "string") {
    if (assetIds.has(data.assetId)) {
      errors.push(`${filePath}: duplicate assetId "${data.assetId}"`);
    } else {
      assetIds.add(data.assetId);
    }
  }

  const sourceOk =
    data.source !== null &&
    typeof data.source === "object" &&
    !Array.isArray(data.source);

  if (!sourceOk) {
    const got =
      data.source === null ? "null" :
      Array.isArray(data.source) ? "array" :
      typeof data.source;
    errors.push(`${filePath}: source must be a non-null object, got ${got}`);
  }
}

// ═══════════════════════════════════════════════
// Phase 2: classify assets by content + reference
// ═══════════════════════════════════════════════

// Build lookup: resolved absolute path → parsed asset
const assetByPath = new Map();
for (const a of parsedAssets) {
  assetByPath.set(path.resolve(a.filePath), a);
}

// First, determine which REVIEW_POINT_DEFINITION assets are policy
// (has any reviewPoint with consistencyPolicy)
const policyReviewPointAssetIds = new Set();

for (const a of parsedAssets) {
  if (a.data.assetType === "REVIEW_POINT_DEFINITION") {
    const rps = a.data.reviewPoints;
    if (Array.isArray(rps)) {
      for (const rp of rps) {
        if (rp && typeof rp === "object" && rp.consistencyPolicy) {
          policyReviewPointAssetIds.add(a.data.assetId);
          break;
        }
      }
    }
  }
}

// Classify each asset
function classifyAsset(asset) {
  const { filePath, data } = asset;

  if (data.assetType === "REVIEW_POINT_DEFINITION") {
    return policyReviewPointAssetIds.has(data.assetId) ? "policy" : "legacy";
  }

  if (data.assetType === "RULE_SET") {
    const mv = data.moduleVersions;
    if (mv && typeof mv === "object" && mv.reviewPointDefinitions && typeof mv.reviewPointDefinitions === "object") {
      const ref = mv.reviewPointDefinitions;
      if (typeof ref.path === "string") {
        const rulesetDir = path.dirname(filePath);
        const resolvedPath = path.resolve(rulesetDir, ref.path);
        const targetAsset = assetByPath.get(resolvedPath);
        if (targetAsset && targetAsset.data.assetType === "REVIEW_POINT_DEFINITION" && policyReviewPointAssetIds.has(targetAsset.data.assetId)) {
          return "policy";
        }
      }
    }
    return "legacy";
  }

  return "legacy";
}

const assetClassifications = new Map();
for (const a of parsedAssets) {
  assetClassifications.set(a.filePath, classifyAsset(a));
}

// Validate source.type and source.runtimeBinding per classification
for (const a of parsedAssets) {
  const classification = assetClassifications.get(a.filePath);
  const src = a.data.source;

  if (src && typeof src === "object" && !Array.isArray(src)) {
    const expectedType = classification === "policy" ? "architecture-approved-policy" : "code-current-mapping";

    if (!Object.prototype.hasOwnProperty.call(src, "type")) {
      errors.push(`${a.filePath}: source.type is missing (expected "${expectedType}" for ${classification} asset)`);
    } else if (src.type !== expectedType) {
      errors.push(`${a.filePath}: source.type must be "${expectedType}" for ${classification} asset, got "${src.type}"`);
    }

    if (src.runtimeBinding !== "NOT_BOUND") {
      errors.push(`${a.filePath}: source.runtimeBinding must be "NOT_BOUND", got "${src.runtimeBinding}"`);
    }
  }
}

// ═══════════════════════════════════════════════
// Phase 3: validate consistencyPolicy on policy REVIEW_POINT_DEFINITION
// ═══════════════════════════════════════════════

for (const a of parsedAssets) {
  if (a.data.assetType !== "REVIEW_POINT_DEFINITION") continue;
  if (assetClassifications.get(a.filePath) !== "policy") continue;

  const rps = a.data.reviewPoints;
  if (!Array.isArray(rps)) {
    errors.push(`${a.filePath}: reviewPoints must be an array`);
    continue;
  }

  // Nine-point integrity: exact count
  if (rps.length !== 9) {
    errors.push(`${a.filePath}: reviewPoints must have exactly 9 elements, got ${rps.length}`);
  }

  const codes = [];
  for (let i = 0; i < rps.length; i++) {
    const rp = rps[i];
    if (rp === null || typeof rp !== "object" || Array.isArray(rp)) {
      errors.push(`${a.filePath}: reviewPoints[${i}] must be a non-null, non-array object, got ${Array.isArray(rp) ? "array" : rp === null ? "null" : typeof rp}`);
      continue;
    }
    if (!Object.prototype.hasOwnProperty.call(rp, "reviewPointCode") || typeof rp.reviewPointCode !== "string" || rp.reviewPointCode === "") {
      errors.push(`${a.filePath}: reviewPoints[${i}] must have non-empty string reviewPointCode`);
      continue;
    }
    codes.push(rp.reviewPointCode);
  }

  const codeSet = new Set();
  for (const c of codes) {
    if (codeSet.has(c)) {
      errors.push(`${a.filePath}: duplicate reviewPointCode "${c}"`);
    }
    codeSet.add(c);
  }

  for (const expected of EXPECTED_REVIEW_POINT_CODES) {
    if (!codeSet.has(expected)) {
      errors.push(`${a.filePath}: missing review point "${expected}"`);
    }
  }

  // Per-point consistency policy validation
  for (const rp of rps) {
    if (!rp || typeof rp !== "object") continue;
    const code = rp.reviewPointCode;
    if (!code) continue;

    const cp = rp.consistencyPolicy;
    if (!cp || typeof cp !== "object") {
      errors.push(`${a.filePath}: ${code}: missing consistencyPolicy`);
      continue;
    }

    // cardinalityMode
    if (cp.cardinalityMode !== "CONSISTENCY_SET") {
      errors.push(`${a.filePath}: ${code}: cardinalityMode must be "CONSISTENCY_SET", got ${JSON.stringify(cp.cardinalityMode)}`);
    }

    // minCandidates
    if (cp.minCandidates !== 1) {
      errors.push(`${a.filePath}: ${code}: minCandidates must be 1, got ${JSON.stringify(cp.minCandidates)}`);
    }

    // maxCandidates
    if (!Number.isInteger(cp.maxCandidates) || cp.maxCandidates < 2) {
      errors.push(`${a.filePath}: ${code}: maxCandidates must be integer >= 2, got ${JSON.stringify(cp.maxCandidates)}`);
    }

    // occurrenceBudget
    if (!Object.prototype.hasOwnProperty.call(cp, "occurrenceBudget")) {
      errors.push(`${a.filePath}: ${code}: missing occurrenceBudget`);
    } else if (!Number.isInteger(cp.occurrenceBudget) || cp.occurrenceBudget < 1) {
      errors.push(`${a.filePath}: ${code}: occurrenceBudget must be positive integer, got ${JSON.stringify(cp.occurrenceBudget)}`);
    } else if (Number.isInteger(cp.maxCandidates) && cp.maxCandidates >= 2 && cp.occurrenceBudget < cp.maxCandidates) {
      errors.push(`${a.filePath}: ${code}: occurrenceBudget (${cp.occurrenceBudget}) < maxCandidates (${cp.maxCandidates})`);
    }

    // scopePolicy
    const sp = cp.scopePolicy;
    if (!sp || typeof sp !== "object") {
      errors.push(`${a.filePath}: ${code}: missing scopePolicy`);
    } else {
      if (typeof sp.version !== "string" || sp.version === "") {
        errors.push(`${a.filePath}: ${code}: scopePolicy.version must be non-empty string`);
      }
      if (!arraysEqual(sp.includedRegionTypes, EXPECTED_INCLUDED_REGION_TYPES)) {
        errors.push(`${a.filePath}: ${code}: scopePolicy.includedRegionTypes must be ${JSON.stringify(EXPECTED_INCLUDED_REGION_TYPES)}, got ${JSON.stringify(sp.includedRegionTypes)}`);
      }
      if (!arraysEqual(sp.strongExcludedContextTypes, EXPECTED_STRONG_EXCLUDED_CONTEXT_TYPES)) {
        errors.push(`${a.filePath}: ${code}: scopePolicy.strongExcludedContextTypes must be ${JSON.stringify(EXPECTED_STRONG_EXCLUDED_CONTEXT_TYPES)}, got ${JSON.stringify(sp.strongExcludedContextTypes)}`);
      }
      if (!arraysEqual(sp.requiredAttributionSignals, EXPECTED_REQUIRED_ATTRIBUTION_SIGNALS)) {
        errors.push(`${a.filePath}: ${code}: scopePolicy.requiredAttributionSignals must be ${JSON.stringify(EXPECTED_REQUIRED_ATTRIBUTION_SIGNALS)}, got ${JSON.stringify(sp.requiredAttributionSignals)}`);
      }

      // Per-point semantic exclusion
      if (code === "PARTY_A_NAME_CONSISTENCY") {
        if (!arraysEqual(sp.strongExcludedSemanticContexts, PARTY_A_SEMANTIC_EXCLUSIONS)) {
          errors.push(`${a.filePath}: ${code}: scopePolicy.strongExcludedSemanticContexts must be ${JSON.stringify(PARTY_A_SEMANTIC_EXCLUSIONS)}, got ${JSON.stringify(sp.strongExcludedSemanticContexts)}`);
        }
      } else {
        const semCtx = sp.strongExcludedSemanticContexts;
        if (!Array.isArray(semCtx) || semCtx.length !== 0) {
          errors.push(`${a.filePath}: ${code}: scopePolicy.strongExcludedSemanticContexts must be empty array for non-PARTY_A points, got ${JSON.stringify(semCtx)}`);
        }
      }
    }

    // canonicalizationPolicy
    const canon = cp.canonicalizationPolicy;
    if (!canon || typeof canon !== "object") {
      errors.push(`${a.filePath}: ${code}: missing canonicalizationPolicy`);
    } else {
      if (typeof canon.version !== "string" || canon.version === "") {
        errors.push(`${a.filePath}: ${code}: canonicalizationPolicy.version must be non-empty string`);
      }

      const vt = canon.valueType;
      if (vt !== "TEXT" && vt !== "DECIMAL") {
        errors.push(`${a.filePath}: ${code}: canonicalizationPolicy.valueType must be TEXT or DECIMAL, got ${JSON.stringify(vt)}`);
      }

      const u = canon.unit;
      if (u !== "NONE" && u !== "CNY" && u !== "PERCENT") {
        errors.push(`${a.filePath}: ${code}: canonicalizationPolicy.unit must be NONE/CNY/PERCENT, got ${JSON.stringify(u)}`);
      }

      // Per-point frozen canonical pair
      const expected = FROZEN_CANONICAL[code];
      if (expected) {
        if (vt !== expected.valueType || u !== expected.unit) {
          errors.push(`${a.filePath}: ${code}: canonicalization mismatch, expected ${expected.valueType}/${expected.unit}, got ${vt}/${u}`);
        }
      }
    }

    // anchorIdentityPolicy
    const aip = cp.anchorIdentityPolicy;
    if (!aip || typeof aip !== "object") {
      errors.push(`${a.filePath}: ${code}: missing anchorIdentityPolicy`);
    } else {
      if (typeof aip.version !== "string" || aip.version === "") {
        errors.push(`${a.filePath}: ${code}: anchorIdentityPolicy.version must be non-empty string`);
      }
      if (!arraysEqual(aip.blockIdentity, EXPECTED_BLOCK_IDENTITY)) {
        errors.push(`${a.filePath}: ${code}: anchorIdentityPolicy.blockIdentity must be ${JSON.stringify(EXPECTED_BLOCK_IDENTITY)}, got ${JSON.stringify(aip.blockIdentity)}`);
      }
      if (!arraysEqual(aip.tableCellIdentity, EXPECTED_TABLE_CELL_IDENTITY)) {
        errors.push(`${a.filePath}: ${code}: anchorIdentityPolicy.tableCellIdentity must be ${JSON.stringify(EXPECTED_TABLE_CELL_IDENTITY)}, got ${JSON.stringify(aip.tableCellIdentity)}`);
      }
    }

    // Forbidden patterns in consistencyPolicy only
    const cpSerialized = JSON.stringify(cp);
    for (const pattern of FORBIDDEN_SUBSTRINGS) {
      if (cpSerialized.includes(pattern)) {
        errors.push(`${a.filePath}: ${code}: forbidden pattern "${pattern}" found in consistencyPolicy`);
      }
    }
    for (const regex of FORBIDDEN_EXTENSION_PATTERNS) {
      if (regex.test(cpSerialized)) {
        errors.push(`${a.filePath}: ${code}: forbidden file extension pattern ${regex} found in consistencyPolicy`);
      }
    }
  }
}

// ═══════════════════════════════════════════════
// Phase 4: RuleSet manifest checks (per-RuleSet)
// ═══════════════════════════════════════════════

const ruleSets = parsedAssets.filter((a) => a.data.assetType === "RULE_SET");

if (ruleSets.length === 0) {
  errors.push("No RULE_SET manifest found (expected at least 1)");
}

for (const ruleSet of ruleSets) {
  const mv = ruleSet.data.moduleVersions;

  if (!mv || typeof mv !== "object") {
    errors.push(`${ruleSet.filePath}: missing or invalid moduleVersions`);
    continue;
  }

  const rulesetDir = path.dirname(ruleSet.filePath);

  for (const key of REQUIRED_MODULE_KEYS) {
    if (!Object.prototype.hasOwnProperty.call(mv, key)) {
      errors.push(`${ruleSet.filePath}: moduleVersions missing required key "${key}"`);
      continue;
    }

    const ref = mv[key];
    if (!ref || typeof ref !== "object") {
      errors.push(`${ruleSet.filePath}: moduleVersions.${key} is not an object`);
      continue;
    }

    if (typeof ref.path !== "string") {
      errors.push(`${ruleSet.filePath}: moduleVersions.${key}.path is missing or not a string`);
      continue;
    }

    const resolvedPath = path.resolve(rulesetDir, ref.path);

    if (!fs.existsSync(resolvedPath)) {
      errors.push(`${ruleSet.filePath}: moduleVersions.${key}.path "${ref.path}" resolves to non-existent file "${resolvedPath}"`);
      continue;
    }

    let refData;
    try {
      refData = JSON.parse(fs.readFileSync(resolvedPath, "utf8"));
    } catch (e) {
      errors.push(`${ruleSet.filePath}: moduleVersions.${key}.path "${ref.path}" — target file JSON parse failed: ${e.message}`);
      continue;
    }

    if (ref.assetId !== refData.assetId) {
      errors.push(`${ruleSet.filePath}: moduleVersions.${key}.assetId "${ref.assetId}" does not match target file assetId "${refData.assetId}"`);
    }

    if (ref.version !== refData.version) {
      errors.push(`${ruleSet.filePath}: moduleVersions.${key}.version "${ref.version}" does not match target file version "${refData.version}"`);
    }
  }
}

// ═══════════════════════════════════════════════
// Phase 5: runtimePolicy checks
// ═══════════════════════════════════════════════

for (const a of parsedAssets) {
  const classification = assetClassifications.get(a.filePath);
  const rp = a.data.runtimePolicy;

  if (classification === "policy" && a.data.assetType === "RULE_SET") {
    // New policy RuleSet: runtimePolicy is mandatory with exact values
    if (!rp || typeof rp !== "object" || Array.isArray(rp)) {
      errors.push(`${a.filePath}: policy RuleSet must have runtimePolicy object`);
    } else {
      if (!Object.prototype.hasOwnProperty.call(rp, "loaderEnabled")) {
        errors.push(`${a.filePath}: policy RuleSet runtimePolicy.loaderEnabled is missing`);
      } else if (rp.loaderEnabled !== false) {
        errors.push(`${a.filePath}: policy RuleSet runtimePolicy.loaderEnabled must be false, got ${JSON.stringify(rp.loaderEnabled)}`);
      }

      if (!Object.prototype.hasOwnProperty.call(rp, "databasePersistence")) {
        errors.push(`${a.filePath}: policy RuleSet runtimePolicy.databasePersistence is missing`);
      } else if (rp.databasePersistence !== false) {
        errors.push(`${a.filePath}: policy RuleSet runtimePolicy.databasePersistence must be false, got ${JSON.stringify(rp.databasePersistence)}`);
      }

      if (!Object.prototype.hasOwnProperty.call(rp, "productionEffect")) {
        errors.push(`${a.filePath}: policy RuleSet runtimePolicy.productionEffect is missing`);
      } else if (rp.productionEffect !== "NONE") {
        errors.push(`${a.filePath}: policy RuleSet runtimePolicy.productionEffect must be "NONE", got ${JSON.stringify(rp.productionEffect)}`);
      }
    }
  } else {
    // Legacy assets: keep existing loaderEnabled check (optional, but must be false if present)
    if (rp !== null && typeof rp === "object" && !Array.isArray(rp) &&
        Object.prototype.hasOwnProperty.call(rp, "loaderEnabled")) {
      if (rp.loaderEnabled !== false) {
        errors.push(`${a.filePath}: runtimePolicy.loaderEnabled must be false, got ${JSON.stringify(rp.loaderEnabled)}`);
      }
    }
  }
}

// ═══════════════════════════════════════════════
// Report
// ═══════════════════════════════════════════════

if (errors.length > 0) {
  console.error(`Validation failed with ${errors.length} error(s):`);
  for (const err of errors) {
    console.error(`  - ${err}`);
  }
  process.exit(1);
}

console.log(
  `Review assets validation passed. ${parsedAssets.length} file(s) checked, ${jsonFiles.length} JSON file(s) scanned.`
);
