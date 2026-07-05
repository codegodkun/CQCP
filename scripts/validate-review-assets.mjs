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

function usage() {
  console.error("Usage: node scripts/validate-review-assets.mjs [assets-root]");
}

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

// Phase 1: parse JSON and check per-file fields
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

  // Check required top-level fields
  for (const field of REQUIRED_FIELDS) {
    if (!Object.prototype.hasOwnProperty.call(data, field)) {
      errors.push(`${filePath}: missing required field "${field}"`);
    }
  }

  // Check assetId uniqueness (only if assetId exists)
  if (typeof data.assetId === "string") {
    if (assetIds.has(data.assetId)) {
      errors.push(`${filePath}: duplicate assetId "${data.assetId}"`);
    } else {
      assetIds.add(data.assetId);
    }
  }

  // Check source: must be a non-null, non-array object
  const sourceOk =
    data.source !== null &&
    typeof data.source === "object" &&
    !Array.isArray(data.source);

  if (!sourceOk) {
    const got =
      data.source === null ? "null" :
      Array.isArray(data.source) ? "array" :
      typeof data.source;
    errors.push(
      `${filePath}: source must be a non-null object, got ${got}`
    );
  } else {
    if (data.source.type !== "code-current-mapping") {
      errors.push(
        `${filePath}: source.type must be "code-current-mapping", got "${data.source.type}"`
      );
    }
    if (data.source.runtimeBinding !== "NOT_BOUND") {
      errors.push(
        `${filePath}: source.runtimeBinding must be "NOT_BOUND", got "${data.source.runtimeBinding}"`
      );
    }
  }

  // Check runtimePolicy.loaderEnabled
  if (
    data.runtimePolicy !== null &&
    typeof data.runtimePolicy === "object" &&
    !Array.isArray(data.runtimePolicy) &&
    Object.prototype.hasOwnProperty.call(data.runtimePolicy, "loaderEnabled")
  ) {
    if (data.runtimePolicy.loaderEnabled !== false) {
      errors.push(
        `${filePath}: runtimePolicy.loaderEnabled must be false, got "${data.runtimePolicy.loaderEnabled}"`
      );
    }
  }
}

// Phase 2: RuleSet manifest checks
const ruleSets = parsedAssets.filter((a) => a.data.assetType === "RULE_SET");

if (ruleSets.length === 0) {
  errors.push("No RULE_SET manifest found (expected exactly 1)");
} else if (ruleSets.length > 1) {
  errors.push(
    `Multiple RULE_SET manifests found (${ruleSets.length}); expected exactly 1: ${ruleSets.map((r) => r.filePath).join(", ")}`
  );
} else {
  const ruleSet = ruleSets[0];
  const mv = ruleSet.data.moduleVersions;

  if (!mv || typeof mv !== "object") {
    errors.push(`${ruleSet.filePath}: missing or invalid moduleVersions`);
  } else {
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
        errors.push(
          `${ruleSet.filePath}: moduleVersions.${key}.path "${ref.path}" resolves to non-existent file "${resolvedPath}"`
        );
        continue;
      }

      // Read and validate referenced file
      let refData;
      try {
        refData = JSON.parse(fs.readFileSync(resolvedPath, "utf8"));
      } catch (e) {
        errors.push(
          `${ruleSet.filePath}: moduleVersions.${key}.path "${ref.path}" — target file JSON parse failed: ${e.message}`
        );
        continue;
      }

      if (ref.assetId !== refData.assetId) {
        errors.push(
          `${ruleSet.filePath}: moduleVersions.${key}.assetId "${ref.assetId}" does not match target file assetId "${refData.assetId}"`
        );
      }

      if (ref.version !== refData.version) {
        errors.push(
          `${ruleSet.filePath}: moduleVersions.${key}.version "${ref.version}" does not match target file version "${refData.version}"`
        );
      }
    }
  }
}

// Report
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
