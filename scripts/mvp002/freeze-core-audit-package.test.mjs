import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import crypto from "node:crypto";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";

import {
  gitBytes,
  validateVerificationSummary,
} from "./freeze-core-audit-package.mjs";

const sha256 = (bytes) =>
  crypto.createHash("sha256").update(bytes).digest("hex");

function writeJUnitEvidence(root, runName, tests) {
  const runRoot = path.join(
    root,
    "outputs/task-mvp-002/core-audit/verification/junit",
    runName,
  );
  fs.mkdirSync(runRoot, { recursive: true });
  const xml = Buffer.from(
    `<testsuite name="${runName}" tests="${tests}" failures="0" errors="0" skipped="0"></testsuite>\n`,
  );
  const xmlPath = path.join(runRoot, `TEST-${runName}.xml`);
  fs.writeFileSync(xmlPath, xml);
  const relativeXmlPath = path
    .relative(root, xmlPath)
    .split(path.sep)
    .join("/");
  const counts = {
    suites: 1,
    tests,
    failures: 0,
    errors: 0,
    skipped: 0,
  };
  const manifest = {
    schemaVersion: "task-mvp-002-junit-evidence-v1",
    runName,
    counts,
    files: [{
      path: relativeXmlPath,
      size: xml.length,
      sha256: sha256(xml),
    }],
  };
  const manifestBytes = Buffer.from(`${JSON.stringify(manifest)}\n`);
  const manifestPath = path.join(runRoot, "junit-manifest.json");
  fs.writeFileSync(manifestPath, manifestBytes);
  return {
    runName,
    manifestPath: path
      .relative(root, manifestPath)
      .split(path.sep)
      .join("/"),
    manifestSize: manifestBytes.length,
    manifestSha256: sha256(manifestBytes),
    counts,
  };
}

test("verification summary binds every log and external evidence byte", () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-core-freeze-"));
  try {
    fs.mkdirSync(path.join(root, "logs"), { recursive: true });
    fs.mkdirSync(path.join(root, "evidence"), { recursive: true });
    const logBytes = Buffer.from("PASS\n");
    const evidenceBytes = Buffer.from('{"status":"PASS"}\n');
    fs.writeFileSync(path.join(root, "logs/test.log"), logBytes);
    fs.writeFileSync(path.join(root, "evidence/result.json"), evidenceBytes);
    const boundary = Buffer.from(
      `${JSON.stringify({
        schemaVersion: "task-mvp-002-core-boundary-v1",
        status: "PASS",
        providerA0Included: false,
      })}\n`,
    );
    const boundaryPath = path.join(root, "evidence/core-boundary.json");
    fs.writeFileSync(boundaryPath, boundary);
    const junitEvidence = [
      writeJUnitEvidence(root, "d1-combined", 452),
      writeJUnitEvidence(root, "d2-seam", 22),
      writeJUnitEvidence(root, "backend-first", 891),
      writeJUnitEvidence(root, "backend-repeat", 891),
    ];
    const summary = {
      schemaVersion: "task-mvp-002-core-verification-v2",
      status: "PASS",
      networkModelCalls: 0,
      providerA0Included: false,
      coreBoundary: {
        path: "evidence/core-boundary.json",
        sha256: sha256(boundary),
      },
      subject: {
        baseCommit: "1035739b751386176e47c6871738a62bff86de02",
        headCommit: "a".repeat(40),
        tree: "b".repeat(40),
      },
      runs: [{
        name: "test",
        exitCode: 0,
        logPath: "logs/test.log",
        logSha256: sha256(logBytes),
      }],
      evidenceReferences: [{
        path: "evidence/result.json",
        size: evidenceBytes.length,
        sha256: sha256(evidenceBytes),
      }],
      backend: junitEvidence.at(-1).counts,
      junitEvidence,
    };
    const summaryPath = path.join(root, "summary.json");
    fs.writeFileSync(summaryPath, `${JSON.stringify(summary)}\n`);
    assert.equal(
      validateVerificationSummary(root, summaryPath).summary.status,
      "PASS",
    );
    fs.writeFileSync(path.join(root, "evidence/result.json"), "tampered\n");
    assert.throws(
      () => validateVerificationSummary(root, summaryPath),
      /Evidence hash mismatch|Expected values to be strictly equal/,
    );
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});

test("verification summary rejects another subject HEAD", () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-core-freeze-"));
  try {
    const summaryPath = path.join(root, "summary.json");
    fs.writeFileSync(
      summaryPath,
      `${JSON.stringify({
        schemaVersion: "task-mvp-002-core-verification-v2",
        status: "PASS",
        networkModelCalls: 0,
        providerA0Included: false,
        subject: {
          baseCommit: "1035739b751386176e47c6871738a62bff86de02",
          headCommit: "a".repeat(40),
          tree: "b".repeat(40),
        },
        runs: [{}],
        evidenceReferences: [{}],
      })}\n`,
    );
    assert.throws(
      () =>
        validateVerificationSummary(
          root,
          summaryPath,
          "c".repeat(40),
        ),
      /different HEAD/,
    );
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});

test("verification summary rejects network calls and Provider inclusion", () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-core-freeze-"));
  try {
    const summaryPath = path.join(root, "summary.json");
    fs.writeFileSync(
      summaryPath,
      `${JSON.stringify({
        schemaVersion: "task-mvp-002-core-verification-v2",
        status: "PASS",
        networkModelCalls: 1,
        providerA0Included: true,
        runs: [{}],
        evidenceReferences: [{}],
      })}\n`,
    );
    assert.throws(
      () => validateVerificationSummary(root, summaryPath),
      /Expected values to be strictly equal/,
    );
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});

test("binary freeze diff supports a Core subject larger than Node's default buffer", () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-core-diff-"));
  const git = (...args) =>
    execFileSync("git", args, { cwd: root, encoding: "utf8" });
  try {
    git("init");
    git("config", "user.name", "CQCP Test");
    git("config", "user.email", "cqcp-test@example.invalid");
    const largePath = path.join(root, "large.txt");
    fs.writeFileSync(
      largePath,
      `${Array(80000).fill("before-value").join("\n")}\n`,
    );
    git("add", "large.txt");
    git("commit", "-m", "base");
    fs.writeFileSync(
      largePath,
      `${Array(80000).fill("after-value").join("\n")}\n`,
    );
    git("add", "large.txt");
    git("commit", "-m", "change");

    const diff = gitBytes(root, "diff", "--binary", "HEAD^", "HEAD");
    assert.ok(diff.length > 1024 * 1024);
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});
