import assert from "node:assert/strict";
import { execFileSync, spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, resolve, sep } from "node:path";
import { pathToFileURL } from "node:url";

import {
  parseJsonBytesRejectDuplicateKeys
} from "./strict-json.mjs";

export const TRACK_B_SUCCESSOR_BASE_COMMIT =
  "115be530480e2ff9b92a7076b2668c066a44ae5c";

const GOVERNANCE_PATHS = Object.freeze([
  "CURRENT_CONTEXT.md",
  "changelog/2026-08.md",
  "decisions/ADR-022-mvp002-standing-egress-grant-and-derived-receipt.md",
  "decisions/ADR-023-track-b-successor-independent-admission.md",
  "docs/ai-review.md",
  "tasks/MVP_TASK_MAP.md",
  "tasks/active/TASK-EVAL-003-track-b-successor-admission.md"
]);
const sha256 = (bytes) =>
  createHash("sha256").update(bytes).digest("hex");
const jsonBytes = (value) =>
  Buffer.from(`${JSON.stringify(value, null, 2)}\n`, "utf8");

export async function freezeTrackBSuccessorAuditPackage({
  repoRoot: repoArg,
  verificationResultPath,
  consoleManifestPath,
  createdAt,
  baseCommit = TRACK_B_SUCCESSOR_BASE_COMMIT
}) {
  const repoRoot = resolve(repoArg);
  const resolveRelative = pathResolver(repoRoot);
  const canonicalCreatedAt = canonicalIso(createdAt, "createdAt");
  assert.match(baseCommit, /^[a-f0-9]{40}$/);
  const headCommit = gitText(repoRoot, "rev-parse", "HEAD");
  const tree = gitText(repoRoot, "rev-parse", "HEAD^{tree}");
  assert.equal(
    gitStatus(repoRoot),
    "",
    "TRACK_B_SUCCESSOR_FREEZE_REQUIRES_CLEAN_WORKTREE"
  );
  assert.equal(
    spawnSync("git", ["merge-base", "--is-ancestor", baseCommit, headCommit], {
      cwd: repoRoot
    }).status,
    0,
    "TRACK_B_SUCCESSOR_BASE_IS_NOT_ANCESTOR"
  );

  const verification = await readJsonEvidence(
    resolveRelative,
    verificationResultPath
  );
  assert.equal(
    verification.value.schemaVersion,
    "task-eval-003-track-b-successor-verification-result-v1"
  );
  assert.equal(verification.value.status, "PASS");
  assert.equal(
    verification.value.providerAdmission,
    "ESTABLISHED_FOR_EVALUATION_SHADOW_GATE"
  );
  assert.equal(
    verification.value.networkCallPerformedByVerification,
    false
  );
  assert.ok(
    Date.parse(canonicalCreatedAt) >=
      Date.parse(verification.value.chronology.admissionSealedAt)
  );
  const consoleManifest = await readJsonEvidence(
    resolveRelative,
    consoleManifestPath
  );
  await validateConsoleManifest(consoleManifest.value, resolveRelative);

  const referenced = [
    verification.record,
    consoleManifest.record,
    ...verification.value.evidence
  ];
  for (const run of consoleManifest.value.runs) {
    referenced.push({
      path: run.logPath,
      size: run.logSize,
      sha256: run.logSha256
    });
  }
  for (const path of GOVERNANCE_PATHS) {
    const bytes = await readFile(resolveRelative(path));
    referenced.push({ path, size: bytes.length, sha256: sha256(bytes) });
  }
  const evidence = deduplicateRecords(referenced);
  for (const record of evidence) {
    await validateRecord(record, resolveRelative);
    assertTrackedBytes(repoRoot, headCommit, record);
  }

  const changedPathRecords = changedPaths(repoRoot, baseCommit, headCommit);
  assert.ok(changedPathRecords.length > 0);
  const fullDiffBytes = gitBytes(
    repoRoot,
    "diff",
    "--binary",
    "--full-index",
    "--no-renames",
    baseCommit,
    headCommit,
    "--"
  );
  const statusBeforeFreezeBytes = Buffer.from("", "utf8");
  const subject = {
    schemaVersion: "task-eval-003-track-b-successor-audit-subject-v1",
    integrationUnit: "MILESTONE-MVP-002-TRACK-B-SUCCESSOR",
    baseCommit,
    headCommit,
    tree,
    statusBeforeFreezeSha256: sha256(statusBeforeFreezeBytes),
    changedPathCount: changedPathRecords.length,
    changedPathRecords,
    fullDiffSha256: sha256(fullDiffBytes),
    verificationResultPath,
    verificationResultSha256: verification.record.sha256,
    consoleManifestPath,
    consoleManifestSha256: consoleManifest.record.sha256,
    evidenceCount: evidence.length,
    evidence
  };
  const subjectIdentity = sha256(jsonBytes(subject));
  const manifest = {
    schemaVersion:
      "task-eval-003-track-b-successor-audit-freeze-manifest-v1",
    status: "FROZEN_READY_FOR_THREE_PARTY_READ_ONLY_AUDIT",
    createdAt: canonicalCreatedAt,
    subjectIdentity,
    subject,
    requiredAudits: [
      {
        auditor: "CC_AUDIT",
        model: "CC_CURRENT_DEEPSEEK_V4_FLASH",
        readOnly: true
      },
      {
        auditor: "CODEX_CODE_API_ARCHITECTURE",
        model: "gpt-5.6-sol",
        reasoningEffort: "xhigh",
        forkTurns: "none",
        readOnly: true
      },
      {
        auditor: "CODEX_TEST_SECURITY_EVIDENCE",
        model: "gpt-5.6-sol",
        reasoningEffort: "xhigh",
        forkTurns: "none",
        readOnly: true
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
    productionReadyClaimed: false,
    task028Unlocked: false,
    task031Unlocked: false,
    task032Unlocked: false
  };
  const manifestBytes = jsonBytes(manifest);
  const freezeRoot = resolveRelative(
    `outputs/task-eval-003/track-b-successor-v1/audit/` +
      `freeze-${subjectIdentity}`
  );
  await mkdir(dirname(freezeRoot), { recursive: true });
  await mkdir(freezeRoot, { recursive: false });
  const manifestPath = resolve(freezeRoot, "manifest.json");
  await writeFile(manifestPath, manifestBytes, { flag: "wx" });
  return {
    status: manifest.status,
    subjectIdentity,
    manifestPath: relativeFromRepo(repoRoot, manifestPath),
    manifestSha256: sha256(manifestBytes),
    headCommit,
    tree,
    fullDiffSha256: subject.fullDiffSha256,
    evidenceCount: subject.evidenceCount
  };
}

export async function verifyTrackBSuccessorAuditFreeze({
  repoRoot: repoArg,
  manifestPath
}) {
  const repoRoot = resolve(repoArg);
  const resolveRelative = pathResolver(repoRoot);
  const manifestEvidence = await readJsonEvidence(
    resolveRelative,
    manifestPath
  );
  const manifest = manifestEvidence.value;
  assert.equal(
    manifest.schemaVersion,
    "task-eval-003-track-b-successor-audit-freeze-manifest-v1"
  );
  assert.equal(
    manifest.status,
    "FROZEN_READY_FOR_THREE_PARTY_READ_ONLY_AUDIT"
  );
  assert.equal(
    sha256(jsonBytes(manifest.subject)),
    manifest.subjectIdentity,
    "TRACK_B_SUCCESSOR_SUBJECT_IDENTITY_CHANGED"
  );
  const subject = manifest.subject;
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
    await validateRecord(record, resolveRelative);
    assertTrackedBytes(repoRoot, subject.headCommit, record);
  }
  assert.deepEqual(
    manifest.requiredAudits.map((audit) => audit.auditor),
    [
      "CC_AUDIT",
      "CODEX_CODE_API_ARCHITECTURE",
      "CODEX_TEST_SECURITY_EVIDENCE"
    ]
  );
  assert.ok(
    manifest.requiredAudits
      .filter((audit) => audit.auditor.startsWith("CODEX_"))
      .every(
        (audit) =>
          audit.model === "gpt-5.6-sol" &&
          audit.reasoningEffort === "xhigh" &&
          audit.forkTurns === "none"
      )
  );
  assert.deepEqual(manifest.passCondition, {
    decision: "GO",
    p0: 0,
    p1: 0,
    p2: 0,
    blocking: 0,
    allReportsMustBindSameSubjectIdentity: true
  });
  return {
    status: "FREEZE_VERIFIED",
    subjectIdentity: manifest.subjectIdentity,
    manifestSha256: manifestEvidence.record.sha256,
    headCommit: subject.headCommit,
    fullDiffSha256: subject.fullDiffSha256,
    evidenceCount: subject.evidenceCount
  };
}

async function validateConsoleManifest(value, resolveRelative) {
  assert.equal(
    value.schemaVersion,
    "task-eval-003-track-b-successor-console-manifest-v1"
  );
  assert.equal(value.status, "PASS");
  assert.ok(Array.isArray(value.runs) && value.runs.length > 0);
  const names = new Set();
  for (const run of value.runs) {
    assert.equal(names.has(run.name), false);
    names.add(run.name);
    assert.equal(run.exitCode, 0, `Verification failed: ${run.name}`);
    assert.equal(typeof run.command, "string");
    assert.ok(run.command.trim());
    await validateRecord(runRecord(run), resolveRelative);
  }
}

function runRecord(run) {
  return {
    path: run.logPath,
    size: run.logSize,
    sha256: run.logSha256
  };
}

function changedPaths(repoRoot, baseCommit, headCommit) {
  const fields = execFileSync(
    "git",
    [
      "diff",
      "--name-status",
      "-z",
      "--no-renames",
      baseCommit,
      headCommit,
      "--"
    ],
    { cwd: repoRoot, encoding: "utf8" }
  )
    .split("\0")
    .filter(Boolean);
  assert.equal(fields.length % 2, 0);
  const records = [];
  for (let index = 0; index < fields.length; index += 2) {
    assert.ok(["A", "M", "D"].includes(fields[index]));
    records.push({ status: fields[index], path: fields[index + 1] });
  }
  return records.sort((left, right) => left.path.localeCompare(right.path, "en"));
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

async function readJsonEvidence(resolveRelative, relativePath) {
  const bytes = await readFile(resolveRelative(relativePath));
  return {
    bytes,
    value: parseJsonBytesRejectDuplicateKeys(bytes),
    record: { path: relativePath, size: bytes.length, sha256: sha256(bytes) }
  };
}

async function validateRecord(record, resolveRelative) {
  assert.equal(typeof record.path, "string");
  assert.ok(record.path && !record.path.includes("\\"));
  assert.ok(Number.isSafeInteger(record.size) && record.size >= 0);
  assert.match(record.sha256, /^[a-f0-9]{64}$/);
  const bytes = await readFile(resolveRelative(record.path));
  assert.equal(bytes.length, record.size, `Size changed: ${record.path}`);
  assert.equal(sha256(bytes), record.sha256, `Hash changed: ${record.path}`);
}

function assertTrackedBytes(repoRoot, headCommit, record) {
  const result = spawnSync("git", ["show", `${headCommit}:${record.path}`], {
    cwd: repoRoot,
    encoding: null,
    maxBuffer: 32 * 1024 * 1024
  });
  assert.equal(result.status, 0, `Evidence is not tracked: ${record.path}`);
  assert.equal(result.stdout.length, record.size);
  assert.equal(sha256(result.stdout), record.sha256);
}

function pathResolver(repoRoot) {
  return (relativePath) => {
    const absolute = resolve(repoRoot, ...relativePath.split("/"));
    if (!absolute.startsWith(`${repoRoot}${sep}`)) {
      throw new Error("TRACK_B_SUCCESSOR_FREEZE_PATH_ESCAPED");
    }
    return absolute;
  };
}

function gitText(repoRoot, ...args) {
  return execFileSync("git", args, {
    cwd: repoRoot,
    encoding: "utf8"
  }).trim();
}

function gitBytes(repoRoot, ...args) {
  return execFileSync("git", args, {
    cwd: repoRoot,
    encoding: null,
    maxBuffer: 64 * 1024 * 1024
  });
}

function gitStatus(repoRoot) {
  return execFileSync(
    "git",
    ["status", "--porcelain=v1", "--untracked-files=all"],
    { cwd: repoRoot, encoding: "utf8" }
  );
}

function canonicalIso(value, field) {
  assert.equal(typeof value, "string", `${field} must be a string`);
  const parsed = Date.parse(value);
  assert.ok(Number.isFinite(parsed), `${field} is invalid`);
  assert.equal(new Date(parsed).toISOString(), value);
  return value;
}

function relativeFromRepo(repoRoot, absolutePath) {
  return absolutePath
    .slice(repoRoot.length + 1)
    .replaceAll(sep, "/");
}

if (
  process.argv[1] &&
  import.meta.url === pathToFileURL(resolve(process.argv[1])).href
) {
  const [mode, repoArg, firstArg, secondArg, thirdArg] =
    process.argv.slice(2);
  if (mode === "create") {
    const result = await freezeTrackBSuccessorAuditPackage({
      repoRoot: repoArg,
      verificationResultPath: firstArg,
      consoleManifestPath: secondArg,
      createdAt: thirdArg
    });
    process.stdout.write(`${JSON.stringify(result)}\n`);
  } else if (mode === "verify") {
    const result = await verifyTrackBSuccessorAuditFreeze({
      repoRoot: repoArg,
      manifestPath: firstArg
    });
    process.stdout.write(`${JSON.stringify(result)}\n`);
  } else {
    throw new Error(
      "Usage: node freeze-track-b-successor-audit-package.mjs " +
        "create <repo> <verification-result> <console-manifest> <created-at> | " +
        "verify <repo> <manifest>"
    );
  }
}
