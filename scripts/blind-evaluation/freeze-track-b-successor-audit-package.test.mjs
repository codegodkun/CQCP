import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { createHash } from "node:crypto";
import { mkdir, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { resolve } from "node:path";
import test from "node:test";

import {
  freezeTrackBSuccessorAuditPackage,
  verifyTrackBSuccessorAuditFreeze
} from "./freeze-track-b-successor-audit-package.mjs";

const sha256 = (bytes) =>
  createHash("sha256").update(bytes).digest("hex");
const jsonBytes = (value) =>
  Buffer.from(`${JSON.stringify(value, null, 2)}\n`, "utf8");

test("successor freeze binds clean HEAD, full diff, console and immutable evidence", async () => {
  const root = await mkdtemp(resolve(tmpdir(), "cqcp-tbs-freeze-"));
  try {
    git(root, "init", "-q");
    git(root, "config", "user.email", "freeze-test@example.invalid");
    git(root, "config", "user.name", "Freeze Test");
    git(root, "config", "core.autocrlf", "false");
    await write(root, "README.md", "base\n");
    git(root, "add", ".");
    git(root, "commit", "-q", "-m", "base");
    const baseCommit = git(root, "rev-parse", "HEAD");

    const governance = [
      "CURRENT_CONTEXT.md",
      "changelog/2026-08.md",
      "decisions/ADR-022-mvp002-standing-egress-grant-and-derived-receipt.md",
      "decisions/ADR-023-track-b-successor-independent-admission.md",
      "docs/ai-review.md",
      "tasks/MVP_TASK_MAP.md",
      "tasks/active/TASK-EVAL-003-track-b-successor-admission.md"
    ];
    for (const path of governance) await write(root, path, `${path}\n`);
    const artifactPath =
      "outputs/task-eval-003/track-b-successor-v1/run-v1/" +
      "unblind/admission-seal.json";
    const artifactBytes = jsonBytes({ status: "SEALED_GO" });
    await write(root, artifactPath, artifactBytes);
    const logPath =
      "outputs/task-eval-003/track-b-successor-v1/verification/node.log";
    const logBytes = Buffer.from("43 tests / 43 pass\n", "utf8");
    await write(root, logPath, logBytes);
    const verificationPath =
      "outputs/task-eval-003/track-b-successor-v1/verification/result.json";
    const verificationBytes = jsonBytes({
      schemaVersion:
        "task-eval-003-track-b-successor-verification-result-v1",
      status: "PASS",
      providerAdmission: "ESTABLISHED_FOR_EVALUATION_SHADOW_GATE",
      networkCallPerformedByVerification: false,
      chronology: {
        admissionSealedAt: "2026-08-03T12:00:00.000Z"
      },
      evidence: [
        {
          path: artifactPath,
          size: artifactBytes.length,
          sha256: sha256(artifactBytes)
        }
      ]
    });
    await write(root, verificationPath, verificationBytes);
    const consoleManifestPath =
      "outputs/task-eval-003/track-b-successor-v1/verification/" +
      "console-manifest.json";
    await write(
      root,
      consoleManifestPath,
      jsonBytes({
        schemaVersion:
          "task-eval-003-track-b-successor-console-manifest-v1",
        status: "PASS",
        runs: [
          {
            name: "node-contracts",
            command: "node --test successor tests",
            exitCode: 0,
            logPath,
            logSize: logBytes.length,
            logSha256: sha256(logBytes)
          }
        ]
      })
    );
    git(root, "add", ".");
    git(root, "commit", "-q", "-m", "successor subject");

    const frozen = await freezeTrackBSuccessorAuditPackage({
      repoRoot: root,
      verificationResultPath: verificationPath,
      consoleManifestPath,
      createdAt: "2026-08-03T12:01:00.000Z",
      baseCommit
    });
    assert.equal(
      frozen.status,
      "FROZEN_READY_FOR_THREE_PARTY_READ_ONLY_AUDIT"
    );
    assert.match(frozen.subjectIdentity, /^[a-f0-9]{64}$/);
    const verified = await verifyTrackBSuccessorAuditFreeze({
      repoRoot: root,
      manifestPath: frozen.manifestPath
    });
    assert.equal(verified.status, "FREEZE_VERIFIED");
    assert.equal(verified.subjectIdentity, frozen.subjectIdentity);
    const manifest = JSON.parse(
      await readFile(resolve(root, frozen.manifestPath), "utf8")
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
    await write(root, artifactPath, jsonBytes({ status: "TAMPERED" }));
    await assert.rejects(
      () =>
        verifyTrackBSuccessorAuditFreeze({
          repoRoot: root,
          manifestPath: frozen.manifestPath
        }),
      /Size changed|Hash changed/
    );
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});

async function write(root, relativePath, contents) {
  const absolute = resolve(root, relativePath);
  await mkdir(resolve(absolute, ".."), { recursive: true });
  await writeFile(absolute, contents);
}

function git(root, ...args) {
  return execFileSync("git", args, {
    cwd: root,
    encoding: "utf8"
  }).trim();
}
