import assert from "node:assert/strict";
import test from "node:test";

import {
  buildTrackBProviderRecoveryFreezeManifest
} from "./freeze-track-b-provider-recovery-audit-package.mjs";

const hash = (character) => character.repeat(64);

test("freeze manifest binds one immutable subject and three fresh zero-finding audits", () => {
  const result = buildTrackBProviderRecoveryFreezeManifest({
    baseCommit: "1".repeat(40),
    headCommit: "2".repeat(40),
    tree: "3".repeat(40),
    createdAt: "2026-08-03T14:30:00.000Z",
    changedPathRecords: [{ status: "A", path: "example.json" }],
    fullDiffSha256: hash("a"),
    verificationResult: {
      path: "verification-result.json",
      size: 10,
      sha256: hash("b")
    },
    consoleManifest: {
      path: "console-manifest.json",
      size: 11,
      sha256: hash("c")
    },
    evidence: [{ path: "evidence.json", size: 12, sha256: hash("d") }]
  });

  assert.match(result.subjectIdentity, /^[a-f0-9]{64}$/);
  assert.equal(result.manifest.subject.headCommit, "2".repeat(40));
  assert.equal(result.manifest.subject.fullDiffSha256, hash("a"));
  assert.deepEqual(
    result.manifest.requiredAudits.map((audit) => audit.auditor),
    [
      "CC_AUDIT",
      "CODEX_CODE_API_ARCHITECTURE",
      "CODEX_TEST_SECURITY_EVIDENCE"
    ]
  );
  assert.ok(
    result.manifest.requiredAudits
      .filter((audit) => audit.auditor.startsWith("CODEX_"))
      .every(
        (audit) =>
          audit.model === "gpt-5.6-sol" &&
          audit.reasoningEffort === "xhigh" &&
          audit.forkTurns === "none"
      )
  );
  assert.deepEqual(result.manifest.passCondition, {
    decision: "GO",
    p0: 0,
    p1: 0,
    p2: 0,
    blocking: 0,
    allReportsMustBindSameSubjectIdentity: true
  });
});
