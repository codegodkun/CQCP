import assert from "node:assert/strict";
import { execFileSync, spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { resolve, sep } from "node:path";
import { pathToFileURL } from "node:url";

import { parseJsonBytesRejectDuplicateKeys } from "./strict-json.mjs";

export const TRACK_B_PROVIDER_RECOVERY_BASE_COMMIT =
  "115be530480e2ff9b92a7076b2668c066a44ae5c";
const GOVERNANCE_PATHS = Object.freeze([
  ".gitattributes",
  ".gitignore",
  "CURRENT_CONTEXT.md",
  "changelog/2026-08.md",
  "decisions/ADR-019-blind-evaluation-and-model-activation-boundary.md",
  "decisions/ADR-022-mvp002-standing-egress-grant-and-derived-receipt.md",
  "decisions/ADR-025-track-b-schema-stability-diagnosis-and-fifth-admission.md",
  "decisions/ADR-026-track-b-provider-conversation-recovery.md",
  "docs/ARCHITECTURE.md",
  "docs/ai-review.md",
  "tasks/MVP_TASK_MAP.md",
  "tasks/active/TASK-EVAL-002-blind-semantic-evaluation.md",
  "tasks/active/TASK-EVAL-005-track-b-schema-stability-and-fifth-admission.md",
  "tasks/active/TASK-EVAL-006-track-b-provider-conversation-recovery.md"
]);
const VERIFY_ROOT =
  "outputs/task-eval-006/track-b-recovery-v1/verification-v2";
const sha256 = (bytes) =>
  createHash("sha256").update(bytes).digest("hex");
const jsonBytes = (value) =>
  Buffer.from(`${JSON.stringify(value, null, 2)}\n`, "utf8");

export function buildTrackBProviderRecoveryFreezeManifest({
  baseCommit,
  headCommit,
  tree,
  createdAt,
  changedPathRecords,
  fullDiffSha256,
  verificationResult,
  consoleManifest,
  evidence
}) {
  assert.match(baseCommit, /^[a-f0-9]{40}$/);
  assert.match(headCommit, /^[a-f0-9]{40}$/);
  assert.match(tree, /^[a-f0-9]{40}$/);
  assert.equal(new Date(createdAt).toISOString(), createdAt);
  assert.match(fullDiffSha256, /^[a-f0-9]{64}$/);
  assert.ok(Array.isArray(changedPathRecords) && changedPathRecords.length > 0);
  validateRecord(verificationResult);
  validateRecord(consoleManifest);
  assert.ok(Array.isArray(evidence) && evidence.length > 0);
  evidence.forEach(validateRecord);

  const subject = {
    schemaVersion:
      "task-eval-006-track-b-provider-recovery-r5-repair-audit-subject-v1",
    integrationUnit: "MILESTONE-MVP-002-TRACK-B-PROVIDER-RECOVERY",
    baseCommit,
    headCommit,
    tree,
    statusBeforeFreezeSha256: sha256(Buffer.from("", "utf8")),
    changedPathCount: changedPathRecords.length,
    changedPathRecords,
    fullDiffSha256,
    verificationResultPath: verificationResult.path,
    verificationResultSha256: verificationResult.sha256,
    consoleManifestPath: consoleManifest.path,
    consoleManifestSha256: consoleManifest.sha256,
    evidenceCount: evidence.length,
    evidence
  };
  const subjectIdentity = sha256(jsonBytes(subject));
  const manifest = {
    schemaVersion:
      "task-eval-006-track-b-provider-recovery-r5-repair-audit-freeze-v1",
    status: "FROZEN_READY_FOR_THREE_PARTY_READ_ONLY_AUDIT",
    createdAt,
    subjectIdentity,
    subject,
    requiredAudits: [
      {
        auditor: "CC_AUDIT",
        model: "CC_CURRENT_DEEPSEEK_V4_FLASH",
        readOnly: true,
        fresh: true
      },
      {
        auditor: "CODEX_CODE_API_ARCHITECTURE",
        model: "gpt-5.6-sol",
        reasoningEffort: "xhigh",
        forkTurns: "none",
        readOnly: true,
        fresh: true
      },
      {
        auditor: "CODEX_TEST_SECURITY_EVIDENCE",
        model: "gpt-5.6-sol",
        reasoningEffort: "xhigh",
        forkTurns: "none",
        readOnly: true,
        fresh: true
      }
    ],
    passCondition: {
      decision: "GO",
      p0: 0,
      p1: 0,
      p2: 0,
      blocking: 0,
      allReportsMustBindSameSubjectIdentity: true
    },
    a0A1A2ImplementationAllowedBeforeAllAuditsGo: false,
    productionReadyClaimed: false,
    task028Unlocked: false,
    task031Unlocked: false,
    task032Unlocked: false
  };
  return { subjectIdentity, subject, manifest };
}

export async function freezeTrackBProviderRecoveryAuditPackage({
  repoRoot: repoArg,
  createdAt,
  baseCommit = TRACK_B_PROVIDER_RECOVERY_BASE_COMMIT
}) {
  const repoRoot = resolve(repoArg);
  const fromRepo = pathResolver(repoRoot);
  assert.equal(gitStatus(repoRoot), "", "FREEZE_REQUIRES_CLEAN_WORKTREE");
  const headCommit = gitText(repoRoot, "rev-parse", "HEAD");
  const tree = gitText(repoRoot, "rev-parse", "HEAD^{tree}");
  assert.equal(
    spawnSync("git", ["merge-base", "--is-ancestor", baseCommit, headCommit], {
      cwd: repoRoot
    }).status,
    0,
    "BASE_IS_NOT_ANCESTOR"
  );
  const verificationPath = `${VERIFY_ROOT}/verification-result.json`;
  const consolePath = `${VERIFY_ROOT}/console-manifest.json`;
  const verification = await readJsonRecord(fromRepo, verificationPath);
  const consoleManifest = await readJsonRecord(fromRepo, consolePath);
  assert.equal(
    verification.value.schemaVersion,
    "task-eval-006-track-b-recovery-r5-repair-verification-result-v1"
  );
  assert.equal(verification.value.status, "PASS");
  assert.equal(
    verification.value.providerAdmission,
    "ESTABLISHED_FOR_EVALUATION_SHADOW_GATE"
  );
  assert.equal(
    verification.value.networkCallPerformedByRepair,
    false
  );
  assert.equal(
    consoleManifest.value.schemaVersion,
    "task-eval-006-track-b-recovery-r5-repair-console-manifest-v1"
  );
  assert.equal(consoleManifest.value.status, "PASS");

  const referenced = [
    verification.record,
    consoleManifest.record,
    ...verification.value.evidence,
    ...consoleManifest.value.runs.map((run) => ({
      path: run.path,
      size: run.size,
      sha256: run.sha256
    }))
  ];
  const referencedPaths = new Set(referenced.map((record) => record.path));
  const governanceRecords = GOVERNANCE_PATHS.map((path) =>
    gitRecord(repoRoot, headCommit, path)
  );
  const evidence = deduplicateRecords([
    ...referenced,
    ...governanceRecords
  ]);
  for (const record of evidence) {
    if (referencedPaths.has(record.path)) {
      await validateRecordBytes(record, fromRepo);
    }
    assertTrackedBytes(repoRoot, headCommit, record);
  }

  const changedPathRecords = changedPaths(repoRoot, baseCommit, headCommit);
  const fullDiffSha256 = sha256(
    gitBytes(
      repoRoot,
      "diff",
      "--binary",
      "--full-index",
      "--no-renames",
      baseCommit,
      headCommit,
      "--"
    )
  );
  const result = buildTrackBProviderRecoveryFreezeManifest({
    baseCommit,
    headCommit,
    tree,
    createdAt,
    changedPathRecords,
    fullDiffSha256,
    verificationResult: verification.record,
    consoleManifest: consoleManifest.record,
    evidence
  });
  const freezeRoot = fromRepo(
    `${VERIFY_ROOT}/audit/freeze-${result.subjectIdentity}`
  );
  await mkdir(freezeRoot, { recursive: false });
  const manifestPath = resolve(freezeRoot, "manifest.json");
  const manifestBytes = jsonBytes(result.manifest);
  await writeFile(manifestPath, manifestBytes, { flag: "wx" });
  return {
    status: result.manifest.status,
    subjectIdentity: result.subjectIdentity,
    manifestPath: relativeFromRepo(repoRoot, manifestPath),
    manifestSha256: sha256(manifestBytes),
    headCommit,
    tree,
    fullDiffSha256,
    evidenceCount: evidence.length
  };
}

export async function verifyTrackBProviderRecoveryAuditFreeze({
  repoRoot: repoArg,
  manifestPath
}) {
  const repoRoot = resolve(repoArg);
  const fromRepo = pathResolver(repoRoot);
  const manifestEvidence = await readJsonRecord(fromRepo, manifestPath);
  const manifest = manifestEvidence.value;
  assert.equal(
    manifest.status,
    "FROZEN_READY_FOR_THREE_PARTY_READ_ONLY_AUDIT"
  );
  assert.equal(
    sha256(jsonBytes(manifest.subject)),
    manifest.subjectIdentity
  );
  const subject = manifest.subject;
  assert.equal(gitStatusExcluding(repoRoot, manifestPath), "");
  assert.equal(gitText(repoRoot, "rev-parse", "HEAD"), subject.headCommit);
  assert.equal(gitText(repoRoot, "rev-parse", "HEAD^{tree}"), subject.tree);
  assert.deepEqual(
    changedPaths(repoRoot, subject.baseCommit, subject.headCommit),
    subject.changedPathRecords
  );
  assert.equal(
    sha256(
      gitBytes(
        repoRoot,
        "diff",
        "--binary",
        "--full-index",
        "--no-renames",
        subject.baseCommit,
        subject.headCommit,
        "--"
      )
    ),
    subject.fullDiffSha256
  );
  for (const record of subject.evidence) {
    assertTrackedBytes(repoRoot, subject.headCommit, record);
  }
  return {
    status: "FREEZE_VERIFIED",
    subjectIdentity: manifest.subjectIdentity,
    manifestSha256: manifestEvidence.record.sha256,
    headCommit: subject.headCommit,
    fullDiffSha256: subject.fullDiffSha256,
    evidenceCount: subject.evidenceCount
  };
}

function validateRecord(record) {
  assert.equal(typeof record.path, "string");
  assert.ok(record.path && !record.path.includes("\\"));
  assert.ok(Number.isSafeInteger(record.size) && record.size >= 0);
  assert.match(record.sha256, /^[a-f0-9]{64}$/);
}

async function validateRecordBytes(record, fromRepo) {
  validateRecord(record);
  const bytes = await readFile(fromRepo(record.path));
  assert.equal(bytes.length, record.size, `size changed: ${record.path}`);
  assert.equal(sha256(bytes), record.sha256, `hash changed: ${record.path}`);
}

async function readJsonRecord(fromRepo, path) {
  const bytes = await readFile(fromRepo(path));
  return {
    value: parseJsonBytesRejectDuplicateKeys(bytes),
    record: { path, size: bytes.length, sha256: sha256(bytes) }
  };
}

function deduplicateRecords(records) {
  const byPath = new Map();
  for (const record of records) {
    const existing = byPath.get(record.path);
    if (existing) assert.deepEqual(existing, record);
    else byPath.set(record.path, record);
  }
  return [...byPath.values()].sort((left, right) =>
    left.path.localeCompare(right.path, "en")
  );
}

function assertTrackedBytes(repoRoot, headCommit, record) {
  const result = spawnSync("git", ["show", `${headCommit}:${record.path}`], {
    cwd: repoRoot,
    encoding: null,
    maxBuffer: 64 * 1024 * 1024
  });
  assert.equal(result.status, 0, `not tracked: ${record.path}`);
  assert.equal(result.stdout.length, record.size);
  assert.equal(sha256(result.stdout), record.sha256);
}

function gitRecord(repoRoot, headCommit, path) {
  const result = spawnSync("git", ["show", `${headCommit}:${path}`], {
    cwd: repoRoot,
    encoding: null,
    maxBuffer: 64 * 1024 * 1024
  });
  assert.equal(result.status, 0, `not tracked: ${path}`);
  return {
    path,
    size: result.stdout.length,
    sha256: sha256(result.stdout)
  };
}

function changedPaths(repoRoot, baseCommit, headCommit) {
  const fields = execFileSync(
    "git",
    ["diff", "--name-status", "-z", "--no-renames", baseCommit, headCommit, "--"],
    { cwd: repoRoot, encoding: "utf8" }
  ).split("\0").filter(Boolean);
  assert.equal(fields.length % 2, 0);
  const records = [];
  for (let index = 0; index < fields.length; index += 2) {
    assert.ok(["A", "M", "D"].includes(fields[index]));
    records.push({ status: fields[index], path: fields[index + 1] });
  }
  return records.sort((left, right) => left.path.localeCompare(right.path, "en"));
}

function gitStatus(repoRoot) {
  return execFileSync(
    "git",
    ["status", "--porcelain=v1", "--untracked-files=all"],
    { cwd: repoRoot, encoding: "utf8" }
  );
}

function gitStatusExcluding(repoRoot, manifestPath) {
  return gitStatus(repoRoot)
    .split(/\r?\n/)
    .filter(Boolean)
    .filter((line) => !line.endsWith(manifestPath.replaceAll("/", "\\")))
    .filter((line) => !line.endsWith(manifestPath))
    .join("\n");
}

function gitText(repoRoot, ...args) {
  return execFileSync("git", args, { cwd: repoRoot, encoding: "utf8" }).trim();
}

function gitBytes(repoRoot, ...args) {
  return execFileSync("git", args, {
    cwd: repoRoot,
    encoding: null,
    maxBuffer: 64 * 1024 * 1024
  });
}

function pathResolver(repoRoot) {
  return (relativePath) => {
    const absolute = resolve(repoRoot, ...relativePath.split("/"));
    assert.ok(absolute.startsWith(`${repoRoot}${sep}`));
    return absolute;
  };
}

function relativeFromRepo(repoRoot, absolutePath) {
  return absolutePath.slice(repoRoot.length + 1).replaceAll(sep, "/");
}

if (
  process.argv[1] &&
  import.meta.url === pathToFileURL(resolve(process.argv[1])).href
) {
  const [mode, repoRoot, firstArg] = process.argv.slice(2);
  if (mode === "create") {
    const result = await freezeTrackBProviderRecoveryAuditPackage({
      repoRoot,
      createdAt: firstArg
    });
    process.stdout.write(`${JSON.stringify(result)}\n`);
  } else if (mode === "verify") {
    const result = await verifyTrackBProviderRecoveryAuditFreeze({
      repoRoot,
      manifestPath: firstArg
    });
    process.stdout.write(`${JSON.stringify(result)}\n`);
  } else {
    throw new Error(
      "Usage: freeze-track-b-provider-recovery-audit-package.mjs " +
        "create <repo> <createdAt> | verify <repo> <manifestPath>"
    );
  }
}
