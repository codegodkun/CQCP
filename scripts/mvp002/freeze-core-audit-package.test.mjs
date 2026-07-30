import assert from "node:assert/strict";
import crypto from "node:crypto";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";

import { validateVerificationSummary } from "./freeze-core-audit-package.mjs";

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
