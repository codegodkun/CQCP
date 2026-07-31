import assert from "node:assert/strict";
import { execFileSync, spawnSync } from "node:child_process";
import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

import {
  assertCoreChangedPaths,
  assertCoreContentBoundary,
  assertCoreImportClosure,
  CORE_BASE_COMMIT,
  CORE_SCOPE_VERSION,
  normalizeRepoPath,
} from "./core-subject.mjs";

const sha256 = (bytes) =>
  crypto.createHash("sha256").update(bytes).digest("hex");
const canonicalJson = (value) => `${JSON.stringify(value, null, 2)}\n`;
const GIT_DIFF_MAX_BUFFER_BYTES = 64 * 1024 * 1024;

function gitText(repoRoot, ...args) {
  return execFileSync("git", args, {
    cwd: repoRoot,
    encoding: "utf8",
  }).trim();
}

export function gitBytes(repoRoot, ...args) {
  return execFileSync("git", args, {
    cwd: repoRoot,
    encoding: null,
    maxBuffer: GIT_DIFF_MAX_BUFFER_BYTES,
  });
}

function parseStatus(repoRoot) {
  const records = execFileSync(
    "git",
    ["status", "--porcelain=v1", "-z", "--untracked-files=all"],
    { cwd: repoRoot, encoding: "utf8" },
  )
    .split("\0")
    .filter(Boolean)
    .map((record) => {
      const status = record.slice(0, 2);
      assert.equal(
        status.includes("R") || status.includes("C"),
        false,
        `Rename/copy status is unsupported: ${record}`,
      );
      return {
        status,
        path: normalizeRepoPath(record.slice(3)),
      };
    });
  return records;
}

function changedPaths(repoRoot, headCommit) {
  const fields = execFileSync(
    "git",
    [
      "diff",
      "--name-status",
      "-z",
      "--no-renames",
      CORE_BASE_COMMIT,
      headCommit,
      "--",
    ],
    { cwd: repoRoot, encoding: "utf8" },
  )
    .split("\0")
    .filter(Boolean);
  assert.equal(
    fields.length % 2,
    0,
    "Git name-status output must contain status/path pairs",
  );
  const records = [];
  for (let index = 0; index < fields.length; index += 2) {
    const status = fields[index];
    assert.ok(["A", "M", "D"].includes(status), `Unsupported status: ${status}`);
    records.push({
      status,
      path: normalizeRepoPath(fields[index + 1]),
    });
  }
  return records.sort((left, right) =>
    left.path.localeCompare(right.path, "en"),
  );
}

function blobAt(repoRoot, revision, filePath) {
  const result = spawnSync(
    "git",
    ["show", `${revision}:${filePath}`],
    { cwd: repoRoot, encoding: null },
  );
  if (result.status !== 0) return null;
  return result.stdout;
}

function fileRecord(repoRoot, headCommit, record) {
  const base = blobAt(repoRoot, CORE_BASE_COMMIT, record.path);
  const current = record.status === "D"
    ? null
    : blobAt(repoRoot, headCommit, record.path);
  if (record.status !== "D") {
    assert.notEqual(current, null, `Current blob is missing: ${record.path}`);
  }
  return {
    status: record.status,
    path: record.path,
    baseSize: base?.length ?? null,
    baseSha256: base === null ? null : sha256(base),
    currentSize: current?.length ?? null,
    currentSha256: current === null ? null : sha256(current),
  };
}

export function validateVerificationSummary(
  repoRoot,
  summaryPath,
  expectedHeadCommit = null,
) {
  assert.equal(
    fs.existsSync(summaryPath) && fs.statSync(summaryPath).isFile(),
    true,
    `Core verification summary is missing: ${summaryPath}`,
  );
  const bytes = fs.readFileSync(summaryPath);
  const summary = JSON.parse(bytes.toString("utf8"));
  assert.equal(
    summary.schemaVersion,
    "task-mvp-002-core-verification-v1",
  );
  assert.equal(summary.status, "PASS");
  assert.equal(summary.networkModelCalls, 0);
  assert.equal(summary.providerA0Included, false);
  assert.equal(
    summary.subject?.baseCommit,
    CORE_BASE_COMMIT,
    "Verification summary base does not match the Core subject",
  );
  assert.match(
    summary.subject?.headCommit ?? "",
    /^[a-f0-9]{40}$/,
    "Verification summary HEAD is invalid",
  );
  assert.match(
    summary.subject?.tree ?? "",
    /^[a-f0-9]{40}$/,
    "Verification summary tree is invalid",
  );
  if (expectedHeadCommit !== null) {
    assert.equal(
      summary.subject.headCommit,
      expectedHeadCommit,
      "Verification summary belongs to a different HEAD",
    );
    assert.equal(
      summary.subject.tree,
      gitText(repoRoot, "rev-parse", `${expectedHeadCommit}^{tree}`),
      "Verification summary belongs to a different source tree",
    );
  }
  assert.ok(Array.isArray(summary.runs) && summary.runs.length > 0);
  assert.ok(
    Array.isArray(summary.evidenceReferences) &&
      summary.evidenceReferences.length > 0,
  );

  for (const run of summary.runs) {
    assert.equal(run.exitCode, 0, `Verification run failed: ${run.name}`);
    const absolute = path.resolve(repoRoot, run.logPath);
    assert.equal(
      fs.existsSync(absolute) && fs.statSync(absolute).isFile(),
      true,
      `Missing log: ${run.logPath}`,
    );
    assert.equal(
      sha256(fs.readFileSync(absolute)),
      run.logSha256,
      `Log hash mismatch: ${run.logPath}`,
    );
  }
  for (const evidence of summary.evidenceReferences) {
    const absolute = path.resolve(repoRoot, evidence.path);
    assert.equal(
      fs.existsSync(absolute) && fs.statSync(absolute).isFile(),
      true,
      `Missing evidence: ${evidence.path}`,
    );
    const evidenceBytes = fs.readFileSync(absolute);
    assert.equal(evidenceBytes.length, evidence.size);
    assert.equal(
      sha256(evidenceBytes),
      evidence.sha256,
      `Evidence hash mismatch: ${evidence.path}`,
    );
  }
  return {
    path: normalizeRepoPath(path.relative(repoRoot, summaryPath)),
    size: bytes.length,
    sha256: sha256(bytes),
    summary,
  };
}

export function buildCoreFreeze(repoRoot, summaryPath) {
  const absoluteRoot = path.resolve(repoRoot);
  const headCommit = gitText(absoluteRoot, "rev-parse", "HEAD");
  const ancestry = spawnSync(
    "git",
    ["merge-base", "--is-ancestor", CORE_BASE_COMMIT, headCommit],
    { cwd: absoluteRoot, encoding: "utf8" },
  );
  assert.equal(
    ancestry.status,
    0,
    "Core candidate HEAD must descend from the MVP-002 base",
  );

  const dirtySource = parseStatus(absoluteRoot).filter(
    (record) => !record.path.startsWith("outputs/"),
  );
  assert.deepEqual(
    dirtySource,
    [],
    "Core freeze requires a clean source worktree; only outputs/** may be untracked",
  );

  const statusRecords = changedPaths(absoluteRoot, headCommit);
  assert.ok(statusRecords.length > 0, "Core subject cannot be empty");
  const subjectPaths = assertCoreChangedPaths(
    statusRecords.map((record) => record.path),
  );
  const contentBoundary = assertCoreContentBoundary(
    absoluteRoot,
    subjectPaths,
  );
  const importClosure = assertCoreImportClosure(absoluteRoot, subjectPaths);
  const sourceFiles = statusRecords.map((record) =>
    fileRecord(absoluteRoot, headCommit, record),
  );
  const fullDiff = gitBytes(
    absoluteRoot,
    "diff",
    "--binary",
    "--full-index",
    "--no-renames",
    CORE_BASE_COMMIT,
    headCommit,
    "--",
  );
  const verification = validateVerificationSummary(
    absoluteRoot,
    path.resolve(summaryPath),
    headCommit,
  );
  const tree = gitText(absoluteRoot, "rev-parse", `${headCommit}^{tree}`);
  const sourceStatusSha256 = sha256(Buffer.from(canonicalJson(statusRecords)));
  const fullDiffSha256 = sha256(fullDiff);
  const identityInput = {
    schemaVersion: "task-mvp-002-core-subject-identity-v1",
    scopeVersion: CORE_SCOPE_VERSION,
    baseCommit: CORE_BASE_COMMIT,
    headCommit,
    tree,
    sourceStatusSha256,
    fullDiffSha256,
    sourceFiles,
    contentBoundary,
    importClosure,
    verificationSummarySha256: verification.sha256,
    evidenceReferences: verification.summary.evidenceReferences,
  };
  const subjectIdentity = sha256(Buffer.from(canonicalJson(identityInput)));
  return {
    manifest: {
      schemaVersion: "task-mvp-002-core-freeze-v1",
      generatedAt: new Date().toISOString(),
      scopeVersion: CORE_SCOPE_VERSION,
      baseCommit: CORE_BASE_COMMIT,
      headCommit,
      tree,
      branch: gitText(absoluteRoot, "branch", "--show-current"),
      subjectIdentity,
      subjectPathCount: subjectPaths.length,
      subjectPaths,
      sourceStatusSha256,
      sourceFiles,
      contentBoundary,
      importClosure,
      fullDiffSha256,
      verificationSummary: {
        path: verification.path,
        size: verification.size,
        sha256: verification.sha256,
      },
      evidenceReferences: verification.summary.evidenceReferences,
      exclusions: {
        providerA0: true,
        providerAuditTransport: true,
        outputsAsSource: true,
        modelNetworkCallsDuringVerification: true,
      },
      requiredAuditOutcome: {
        auditors: [
          "CODEX_CODE_ARCHITECTURE",
          "CODEX_TEST_SECURITY",
          "CC_AUDIT",
        ],
        decision: "GO",
        p0: 0,
        p1: 0,
        p2: 0,
        blockingFindings: 0,
        sameSubjectIdentityRequired: true,
      },
    },
    fullDiff,
  };
}

function main() {
  const repoRoot = path.resolve(process.argv[2] ?? ".");
  const mode = process.argv[3] ?? "create";
  assert.ok(["create", "verify"].includes(mode), "Mode must be create or verify");
  const summaryPath = path.resolve(
    process.argv[4] ??
      path.join(
        repoRoot,
        "outputs/task-mvp-002/core-audit/verification/verification-summary.json",
      ),
  );
  const freezeRoot = path.join(
    repoRoot,
    "outputs/task-mvp-002/core-audit/freeze",
  );
  const manifestPath = path.join(freezeRoot, "freeze-manifest.json");
  const manifestHashPath = path.join(freezeRoot, "freeze-manifest.sha256");
  const fullDiffPath = path.join(freezeRoot, "full-diff.patch");
  const built = buildCoreFreeze(repoRoot, summaryPath);
  const manifestBytes = Buffer.from(canonicalJson(built.manifest));

  if (mode === "create") {
    assert.equal(
      fs.existsSync(manifestPath) ||
        fs.existsSync(manifestHashPath) ||
        fs.existsSync(fullDiffPath),
      false,
      "Core freeze already exists; archive explicitly before creating another generation",
    );
    fs.mkdirSync(freezeRoot, { recursive: true });
    fs.writeFileSync(fullDiffPath, built.fullDiff, { flag: "wx" });
    fs.writeFileSync(manifestPath, manifestBytes, { flag: "wx" });
    fs.writeFileSync(
      manifestHashPath,
      `${sha256(manifestBytes)}  freeze-manifest.json\n`,
      { flag: "wx" },
    );
  } else {
    assert.equal(
      fs.existsSync(manifestPath) && fs.statSync(manifestPath).isFile(),
      true,
      "Freeze manifest is missing",
    );
    assert.equal(
      fs.existsSync(fullDiffPath) && fs.statSync(fullDiffPath).isFile(),
      true,
      "Frozen diff is missing",
    );
    const frozenManifestBytes = fs.readFileSync(manifestPath);
    const frozenManifest = JSON.parse(frozenManifestBytes.toString("utf8"));
    const comparable = structuredClone(built.manifest);
    comparable.generatedAt = frozenManifest.generatedAt;
    assert.deepEqual(frozenManifest, comparable);
    assert.deepEqual(fs.readFileSync(fullDiffPath), built.fullDiff);
    assert.equal(
      fs.readFileSync(manifestHashPath, "utf8"),
      `${sha256(frozenManifestBytes)}  freeze-manifest.json\n`,
    );
  }
  process.stdout.write(
    `${JSON.stringify({
      mode,
      subjectIdentity: built.manifest.subjectIdentity,
      subjectPathCount: built.manifest.subjectPathCount,
      fullDiffSha256: built.manifest.fullDiffSha256,
      verificationSummarySha256:
        built.manifest.verificationSummary.sha256,
    })}\n`,
  );
}

if (path.resolve(process.argv[1] ?? "") === fileURLToPath(import.meta.url)) {
  main();
}
