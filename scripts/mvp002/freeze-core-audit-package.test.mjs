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

test("verification summary binds every log and external evidence byte", () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-core-freeze-"));
  try {
    fs.mkdirSync(path.join(root, "logs"), { recursive: true });
    fs.mkdirSync(path.join(root, "evidence"), { recursive: true });
    const logBytes = Buffer.from("PASS\n");
    const evidenceBytes = Buffer.from('{"status":"PASS"}\n');
    fs.writeFileSync(path.join(root, "logs/test.log"), logBytes);
    fs.writeFileSync(path.join(root, "evidence/result.json"), evidenceBytes);
    const summary = {
      schemaVersion: "task-mvp-002-core-verification-v1",
      status: "PASS",
      networkModelCalls: 0,
      providerA0Included: false,
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

test("verification summary rejects network calls and Provider inclusion", () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-core-freeze-"));
  try {
    const summaryPath = path.join(root, "summary.json");
    fs.writeFileSync(
      summaryPath,
      `${JSON.stringify({
        schemaVersion: "task-mvp-002-core-verification-v1",
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
