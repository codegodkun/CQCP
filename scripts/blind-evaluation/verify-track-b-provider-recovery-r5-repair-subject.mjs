import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { pathToFileURL } from "node:url";

import {
  CODEX_REVALIDATION_ROOT,
  validateCodexRevalidationEvidence
} from "./track-b-provider-recovery-codex-revalidation-contract.mjs";
import { parseJsonBytesRejectDuplicateKeys } from "./strict-json.mjs";

const OUTPUT_ROOT = "outputs/task-eval-006/track-b-recovery-v1";
const RUN_ROOT = `${OUTPUT_ROOT}/run-v1`;
const VERIFY_ROOT = `${OUTPUT_ROOT}/verification-v2`;
const OLD_FREEZE =
  `${OUTPUT_ROOT}/verification/audit/` +
  "freeze-9dddbe43748c4fe8ff957f2a97f91ab27466432110ed9f3ef6cc8374c748258d/manifest.json";
const R5_FAILED_AUDIT_ROOT =
  `${VERIFY_ROOT}/audit/` +
  "freeze-e77a86358d56d98e77c08cd5b20fc7dfe1a65c9a67a509cee2c35ac0350b54db";
const R5_FAILED_MANIFEST = `${R5_FAILED_AUDIT_ROOT}/manifest.json`;
const R5_FAILED_CODE_AUDIT =
  `${R5_FAILED_AUDIT_ROOT}/codex-code-architecture-audit-no-go.md`;
const R5_FAILED_CC_STATUS =
  `${R5_FAILED_AUDIT_ROOT}/cc-audit-transport-incomplete.md`;
const R5_FAILED_TEST_AUDIT =
  `${R5_FAILED_AUDIT_ROOT}/codex-test-security-audit-interrupted.md`;
const R6_FAILED_AUDIT_ROOT =
  `${VERIFY_ROOT}/audit/` +
  "freeze-a9daf62f4644dadd3834a3ef1e92408bc96c4d974009e1f6020b3f3f24e397ad";
const R6_FAILED_MANIFEST = `${R6_FAILED_AUDIT_ROOT}/manifest.json`;
const R6_CODE_AUDIT =
  `${R6_FAILED_AUDIT_ROOT}/codex-code-architecture-audit-go.md`;
const R6_TEST_AUDIT =
  `${R6_FAILED_AUDIT_ROOT}/codex-test-security-audit-go.md`;
const R6_CC_AUDIT = `${R6_FAILED_AUDIT_ROOT}/cc-audit-no-go.md`;
const R6_CC_RAW_AUDIT = `${R6_FAILED_AUDIT_ROOT}/cc-audit-raw.console.log`;
const R6_CC_STATUS = `${R6_FAILED_AUDIT_ROOT}/cc-audit-status.json`;
const R7_FAILED_AUDIT_ROOT =
  `${VERIFY_ROOT}/audit/` +
  "freeze-47d84299f9f5002cc80c5aa20481452cdf6f27f83699fce2793c836aaf4852d3";
const R7_FAILED_MANIFEST = `${R7_FAILED_AUDIT_ROOT}/manifest.json`;
const R7_CC_AUDIT = `${R7_FAILED_AUDIT_ROOT}/cc-audit-go.md`;
const R7_CODE_AUDIT =
  `${R7_FAILED_AUDIT_ROOT}/codex-code-architecture-audit-interrupted.md`;
const R7_TEST_AUDIT =
  `${R7_FAILED_AUDIT_ROOT}/codex-test-security-audit-no-go.md`;
const R7_AUDIT_STATUS = `${R7_FAILED_AUDIT_ROOT}/audit-round-status.json`;
const R8_FAILED_AUDIT_ROOT =
  `${VERIFY_ROOT}/audit/` +
  "freeze-a85e96f58532a8a9b807e72cb3ca0666e011e72321cf87a6685d28c59db1b50d";
const R8_FAILED_MANIFEST = `${R8_FAILED_AUDIT_ROOT}/manifest.json`;
const R8_CC_AUDIT = `${R8_FAILED_AUDIT_ROOT}/cc-audit-no-go.md`;
const R8_CC_STATUS = `${R8_FAILED_AUDIT_ROOT}/cc-audit-status.json`;
const R8_CODE_AUDIT =
  `${R8_FAILED_AUDIT_ROOT}/codex-code-architecture-audit-interrupted.md`;
const R8_TEST_AUDIT =
  `${R8_FAILED_AUDIT_ROOT}/codex-test-security-audit-interrupted.md`;
const R9_FAILED_AUDIT_ROOT =
  `${VERIFY_ROOT}/audit/` +
  "freeze-e566c8d2e987b2508c24083f1307765c3150722e9a39f66cdb5c7c08f14433a2";
const R9_FAILED_MANIFEST = `${R9_FAILED_AUDIT_ROOT}/manifest.json`;
const R9_AUDIT_STATUS = `${R9_FAILED_AUDIT_ROOT}/audit-round-status.json`;
const R9_CC_AUDIT = `${R9_FAILED_AUDIT_ROOT}/cc-audit-go.md`;
const R9_CODE_AUDIT =
  `${R9_FAILED_AUDIT_ROOT}/codex-code-architecture-audit-no-go.md`;
const R9_TEST_AUDIT =
  `${R9_FAILED_AUDIT_ROOT}/codex-test-security-audit-interrupted.md`;
const TASK_EVAL_006 =
  "tasks/active/TASK-EVAL-006-track-b-provider-conversation-recovery.md";
const PASS_LOGS = Object.freeze([
  ["node-phase-appropriate", `${VERIFY_ROOT}/node-tests-phase-appropriate.console.log`],
  ["java-recovery-runtime", `${VERIFY_ROOT}/java-track-b-recovery-runtime.console.log`],
  ["java-recovery-junit", `${VERIFY_ROOT}/java-track-b-recovery-runtime.junit.xml`],
  ["admission-revalidation-seal", `${VERIFY_ROOT}/admission-revalidation-seal-verify.console.log`],
  ["secret-provider-leak-scan", `${VERIFY_ROOT}/secret-and-provider-leak-scan.console.log`],
  ["line-endings", `${VERIFY_ROOT}/line-endings.console.log`],
  ["git-diff-check", `${VERIFY_ROOT}/git-diff-check.console.log`]
]);
const FAILED_HARNESS_LOGS = Object.freeze([
  {
    path: `${VERIFY_ROOT}/node-tests-phase-appropriate.failed-preseal-harness.console.log`,
    classification: "PHASE_INAPPROPRIATE_PRESEAL_TEST_INCLUDED"
  },
  {
    path: `${VERIFY_ROOT}/admission-revalidation-seal-verify.failed-timestamp-harness.console.log`,
    classification: "POWERSHELL_LOCALIZED_TIMESTAMP_ARGUMENT"
  },
  {
    path: `${VERIFY_ROOT}/secret-and-provider-leak-scan.failed-overbroad-pattern.console.log`,
    classification: "OVERBROAD_DS_SYNTHETIC_IDENTIFIER_PATTERN"
  }
]);
const EVIDENCE_PATHS = Object.freeze([
  `${OUTPUT_ROOT}/human-ground-truth.json`,
  `${RUN_ROOT}/model-input.json`,
  `${RUN_ROOT}/provider-call-set.json`,
  `${RUN_ROOT}/dispatch.json`,
  `${RUN_ROOT}/derived-egress-receipt.json`,
  `${RUN_ROOT}/deepseek-execution-claim.json`,
  `${RUN_ROOT}/deepseek-opinion.json`,
  `${RUN_ROOT}/admission-report.json`,
  `${RUN_ROOT}/admission-seal.json`,
  `${CODEX_REVALIDATION_ROOT}/readiness-instruction.txt`,
  `${CODEX_REVALIDATION_ROOT}/readiness-response.json`,
  `${CODEX_REVALIDATION_ROOT}/evaluation-instruction.txt`,
  `${CODEX_REVALIDATION_ROOT}/execution-claim.json`,
  `${CODEX_REVALIDATION_ROOT}/launch-receipt.json`,
  `${CODEX_REVALIDATION_ROOT}/codex-opinion.json`,
  `${CODEX_REVALIDATION_ROOT}/completion-receipt.json`,
  `${CODEX_REVALIDATION_ROOT}/admission-report-v2.json`,
  `${CODEX_REVALIDATION_ROOT}/admission-seal-v2.json`,
  "apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/TrackBProviderRecoveryRuntimeContractTest.java",
  "scripts/blind-evaluation/track-b-provider-recovery-codex-revalidation-contract.mjs",
  "scripts/blind-evaluation/track-b-provider-recovery-codex-revalidation-contract.test.mjs",
  "scripts/blind-evaluation/prepare-track-b-provider-recovery-codex-revalidation.mjs",
  "scripts/blind-evaluation/seal-track-b-provider-recovery-admission-revalidation.mjs",
  "scripts/blind-evaluation/seal-track-b-provider-recovery-admission-revalidation.test.mjs",
  "scripts/blind-evaluation/run-track-b-provider-recovery-r5-repair-verification.ps1",
  OLD_FREEZE,
  R5_FAILED_MANIFEST,
  R5_FAILED_CODE_AUDIT,
  R5_FAILED_CC_STATUS,
  R5_FAILED_TEST_AUDIT,
  R6_FAILED_MANIFEST,
  R6_CODE_AUDIT,
  R6_TEST_AUDIT,
  R6_CC_AUDIT,
  R6_CC_RAW_AUDIT,
  R6_CC_STATUS,
  R7_FAILED_MANIFEST,
  R7_CC_AUDIT,
  R7_CODE_AUDIT,
  R7_TEST_AUDIT,
  R7_AUDIT_STATUS,
  R8_FAILED_MANIFEST,
  R8_CC_AUDIT,
  R8_CC_STATUS,
  R8_CODE_AUDIT,
  R8_TEST_AUDIT,
  R9_FAILED_MANIFEST,
  R9_AUDIT_STATUS,
  R9_CC_AUDIT,
  R9_CODE_AUDIT,
  R9_TEST_AUDIT,
  TASK_EVAL_006,
  `${OUTPUT_ROOT}/verification/secret-and-provider-leak-scan.console.log`
]);

const sha256 = (bytes) =>
  createHash("sha256").update(bytes).digest("hex");
const jsonBytes = (value) =>
  Buffer.from(`${JSON.stringify(value, null, 2)}\n`, "utf8");
const record = (path, bytes) => ({
  path,
  size: bytes.length,
  sha256: sha256(bytes)
});

export async function buildTrackBProviderRecoveryR5RepairVerification({
  repoRoot: repoArg,
  completedAt
}) {
  assert.equal(new Date(completedAt).toISOString(), completedAt);
  const repoRoot = resolve(repoArg);
  const read = (relative) =>
    readFile(resolve(repoRoot, ...relative.split("/")));
  const passRuns = [];
  const passTexts = new Map();
  for (const [name, path] of PASS_LOGS) {
    const bytes = await read(path);
    passRuns.push({ name, ...record(path, bytes), exitCode: 0 });
    passTexts.set(name, bytes.toString("utf8"));
  }
  assert.match(passTexts.get("node-phase-appropriate"), /ℹ tests 56/);
  assert.match(passTexts.get("node-phase-appropriate"), /ℹ pass 56/);
  assert.match(passTexts.get("node-phase-appropriate"), /ℹ fail 0/);
  assert.match(passTexts.get("java-recovery-runtime"), /BUILD SUCCESSFUL/);
  assert.match(
    passTexts.get("java-recovery-runtime"),
    /5 actionable tasks: 5 executed/
  );
  const junit = passTexts.get("java-recovery-junit");
  assert.match(junit, /tests="1" skipped="0" failures="0" errors="0"/);
  assert.match(
    junit,
    /name="recoveryCorpusBuildsExactlyThroughTheRuntimePacketSeam\(\)"/
  );
  assert.match(
    passTexts.get("admission-revalidation-seal"),
    /SEALED_GO_TRACK_B_RECOVERY_EVALUATION_REVALIDATED_PENDING_AUDIT/
  );
  assert.match(
    passTexts.get("admission-revalidation-seal"),
    /"deepSeekNetworkCallRepeated":false/
  );
  assert.match(passTexts.get("secret-provider-leak-scan"), /secretLikeMatches=0/);
  assert.match(
    passTexts.get("secret-provider-leak-scan"),
    /forbiddenProviderPayloadMatches=0/
  );
  assert.match(passTexts.get("line-endings"), /filesContainingCR=0/);
  assert.match(passTexts.get("git-diff-check"), /gitDiffCheckExitCode=0/);

  const failedHarnessEvidence = [];
  for (const item of FAILED_HARNESS_LOGS) {
    const bytes = await read(item.path);
    failedHarnessEvidence.push({
      ...record(item.path, bytes),
      classification: item.classification,
      productCorrectnessFailure: false
    });
  }
  const evidence = [];
  for (const path of EVIDENCE_PATHS) {
    const bytes = await read(path);
    evidence.push(record(path, bytes));
  }

  const modelInputBytes = await read(`${RUN_ROOT}/model-input.json`);
  const humanBytes = await read(`${OUTPUT_ROOT}/human-ground-truth.json`);
  const claimBytes = await read(`${CODEX_REVALIDATION_ROOT}/execution-claim.json`);
  const launchBytes = await read(`${CODEX_REVALIDATION_ROOT}/launch-receipt.json`);
  const opinionBytes = await read(`${CODEX_REVALIDATION_ROOT}/codex-opinion.json`);
  const completionBytes = await read(`${CODEX_REVALIDATION_ROOT}/completion-receipt.json`);
  const chronology = validateCodexRevalidationEvidence({
    modelInputBytes,
    humanGroundTruthBytes: humanBytes,
    claimBytes,
    launchReceiptBytes: launchBytes,
    opinionBytes,
    completionReceiptBytes: completionBytes
  });
  const reportBytes = await read(`${CODEX_REVALIDATION_ROOT}/admission-report-v2.json`);
  const sealBytes = await read(`${CODEX_REVALIDATION_ROOT}/admission-seal-v2.json`);
  const report = parseJsonBytesRejectDuplicateKeys(reportBytes);
  const seal = parseJsonBytesRejectDuplicateKeys(sealBytes);
  assert.equal(report.status, "GO_9_OF_9_PLUS_3_ZERO_CALL_PENDING_AUDIT");
  assert.equal(
    seal.status,
    "SEALED_GO_TRACK_B_RECOVERY_EVALUATION_REVALIDATED_PENDING_AUDIT"
  );
  assert.equal(seal.reportSha256, sha256(reportBytes));
  assert.equal(report.modelEvaluationPassed, true);
  assert.equal(seal.modelEvaluationPassed, true);
  assert.equal(report.providerAdmissionEstablished, false);
  assert.equal(seal.providerAdmissionEstablished, false);
  assert.equal(report.admissionDecisionPendingAudit, true);
  assert.equal(seal.admissionDecisionPendingAudit, true);
  assert.equal(seal.deepSeekNetworkCallRepeated, false);
  assert.equal(report.deepSeekNetworkCallRepeated, false);
  assert.equal(report.deepSeekClaimRepeated, false);
  assert.equal(
    sha256(await read(`${RUN_ROOT}/deepseek-execution-claim.json`)),
    "600a821a25641b8d875e696d79191b986f77d39571a778c9670a5af9cde5929d"
  );
  assert.equal(
    sha256(await read(`${RUN_ROOT}/deepseek-opinion.json`)),
    "e863596aab1be943bc04c63414bfcb65f825deaf9bf35c5d117c5c58d3437100"
  );
  assert.equal(
    sha256(await read(`${RUN_ROOT}/admission-seal.json`)),
    "2d879b139715ca7646a95e035747891a14b38ab545302a899ef2ca6376651b04"
  );
  const r5FailedManifestBytes = await read(R5_FAILED_MANIFEST);
  const r5FailedManifest = parseJsonBytesRejectDuplicateKeys(
    r5FailedManifestBytes
  );
  assert.equal(
    r5FailedManifest.subjectIdentity,
    "e77a86358d56d98e77c08cd5b20fc7dfe1a65c9a67a509cee2c35ac0350b54db"
  );
  assert.equal(
    r5FailedManifest.subject.headCommit,
    "53766271736d71bedf4b8539d224bd951a2d4bbf"
  );
  assert.equal(
    sha256(r5FailedManifestBytes),
    "a713467b0358fb4ec1d8bcc09a84a26e72e325a224a57e7c36c47330b6471cf3"
  );
  assert.match((await read(R5_FAILED_CODE_AUDIT)).toString("utf8"), /Verdict: `NO_GO`/);
  assert.match((await read(R5_FAILED_CC_STATUS)).toString("utf8"), /NO AUDIT VERDICT/);
  assert.match((await read(R5_FAILED_TEST_AUDIT)).toString("utf8"), /NO AUDIT VERDICT/);
  const r6FailedManifestBytes = await read(R6_FAILED_MANIFEST);
  const r6FailedManifest = parseJsonBytesRejectDuplicateKeys(
    r6FailedManifestBytes
  );
  assert.equal(
    r6FailedManifest.subjectIdentity,
    "a9daf62f4644dadd3834a3ef1e92408bc96c4d974009e1f6020b3f3f24e397ad"
  );
  assert.equal(
    r6FailedManifest.subject.headCommit,
    "dfc24de439ff549db4f48c2407bb127aa5e2ede8"
  );
  assert.equal(
    sha256(r6FailedManifestBytes),
    "5cd7820ea7fa87254e5f08f94aea434cf9d09bb5d60cc1b1f2f3074ca2a5630c"
  );
  assert.match((await read(R6_CODE_AUDIT)).toString("utf8"), /Verdict: `GO`/);
  assert.match((await read(R6_TEST_AUDIT)).toString("utf8"), /Verdict: `GO`/);
  assert.match((await read(R6_CC_AUDIT)).toString("utf8"), /Verdict: `NO_GO`/);
  assert.equal(
    sha256(await read(R6_CC_RAW_AUDIT)),
    "244ae3f8fa9061139060d09747b579f241e0bf0518d9e5f3e05ce4a9b0d77445"
  );
  const r6CcStatus = parseJsonBytesRejectDuplicateKeys(await read(R6_CC_STATUS));
  assert.equal(
    r6CcStatus.subjectSha256,
    "a9daf62f4644dadd3834a3ef1e92408bc96c4d974009e1f6020b3f3f24e397ad"
  );
  assert.equal(r6CcStatus.exitCode, 0);
  assert.equal(
    r6CcStatus.resultSha256,
    "244ae3f8fa9061139060d09747b579f241e0bf0518d9e5f3e05ce4a9b0d77445"
  );
  const r7FailedManifestBytes = await read(R7_FAILED_MANIFEST);
  const r7FailedManifest = parseJsonBytesRejectDuplicateKeys(
    r7FailedManifestBytes
  );
  assert.equal(
    sha256(r7FailedManifestBytes),
    "e83c7be5648c9724b441cf0b77d65906f022791c9f58bec75e95e38ca513517b"
  );
  assert.equal(
    r7FailedManifest.subjectIdentity,
    "47d84299f9f5002cc80c5aa20481452cdf6f27f83699fce2793c836aaf4852d3"
  );
  assert.equal(
    r7FailedManifest.subject.headCommit,
    "b3716385113528df5024316ed233fc11fd146a15"
  );
  assert.equal(
    r7FailedManifest.subject.fullDiffSha256,
    "f981b02dba29d362b51c56a83c6642485a4cd9a4e2aea19b081235a29d7ecab2"
  );
  const r7AuditStatus = parseJsonBytesRejectDuplicateKeys(
    await read(R7_AUDIT_STATUS)
  );
  assert.equal(r7AuditStatus.overallStatus, "NO_GO");
  assert.equal(r7AuditStatus.p0, 1);
  assert.equal(r7AuditStatus.blocking, 1);
  assert.equal(
    r7AuditStatus.failureClassification,
    "CC_AUDIT_PACKAGE_CONTENT_ISOLATION"
  );
  assert.match(
    (await read(R7_CC_AUDIT)).toString("utf8"),
    /INVALIDATED_BY_CODEX_TEST_SECURITY_P0/
  );
  assert.match(
    (await read(R7_CODE_AUDIT)).toString("utf8"),
    /INTERRUPTED_NO_VERDICT/
  );
  assert.match(
    (await read(R7_TEST_AUDIT)).toString("utf8"),
    /Verdict: `NO_GO`/
  );
  const r8FailedManifestBytes = await read(R8_FAILED_MANIFEST);
  const r8FailedManifest = parseJsonBytesRejectDuplicateKeys(
    r8FailedManifestBytes
  );
  assert.equal(
    sha256(r8FailedManifestBytes),
    "08a0bd235b5c0e1cd24d67ea25895f9b7984d424e0a427b9415417d8b5579513"
  );
  assert.equal(
    r8FailedManifest.subjectIdentity,
    "a85e96f58532a8a9b807e72cb3ca0666e011e72321cf87a6685d28c59db1b50d"
  );
  assert.equal(
    r8FailedManifest.subject.headCommit,
    "d08484cc693aee643265f7ddecbf29c21e02b506"
  );
  assert.equal(r8FailedManifest.subject.changedPathCount, 325);
  assert.equal(r8FailedManifest.subject.evidenceCount, 65);
  assert.equal(
    r8FailedManifest.subject.fullDiffSha256,
    "b1e3eefe4b10441f46f55f4238ef689d219205fadb5d2dfb56b2a1432ee6eee1"
  );
  const r8CcStatus = parseJsonBytesRejectDuplicateKeys(
    await read(R8_CC_STATUS)
  );
  assert.equal(r8CcStatus.verdict, "NO_GO");
  assert.equal(r8CcStatus.p0, 0);
  assert.equal(r8CcStatus.p1, 0);
  assert.equal(r8CcStatus.p2, 1);
  assert.equal(r8CcStatus.blocking, 1);
  assert.equal(
    r8CcStatus.failureClassification,
    "GOVERNANCE_DOCUMENT_FRESHNESS"
  );
  assert.match(
    (await read(R8_CC_AUDIT)).toString("utf8"),
    /Verdict: `NO_GO`/
  );
  assert.match(
    (await read(R8_CODE_AUDIT)).toString("utf8"),
    /INTERRUPTED_NO_VERDICT/
  );
  assert.match(
    (await read(R8_TEST_AUDIT)).toString("utf8"),
    /INTERRUPTED_NO_VERDICT/
  );
  const r9FailedManifestBytes = await read(R9_FAILED_MANIFEST);
  const r9FailedManifest = parseJsonBytesRejectDuplicateKeys(
    r9FailedManifestBytes
  );
  assert.equal(
    sha256(r9FailedManifestBytes),
    "d791a0539e9b1bc16d9ab073a55c91e869faddae6aa699b5e9c181e363ee4b45"
  );
  assert.equal(
    r9FailedManifest.subjectIdentity,
    "e566c8d2e987b2508c24083f1307765c3150722e9a39f66cdb5c7c08f14433a2"
  );
  assert.equal(
    r9FailedManifest.subject.headCommit,
    "140b9a3a288e3d48e3e3d635a9d02b7b7e53945c"
  );
  assert.equal(
    r9FailedManifest.subject.tree,
    "224feded66d46cbd816d4c82f0098a6f7826e4b9"
  );
  assert.equal(r9FailedManifest.subject.changedPathCount, 330);
  assert.equal(r9FailedManifest.subject.evidenceCount, 70);
  assert.equal(
    r9FailedManifest.subject.fullDiffSha256,
    "81d48f4e4d0ab7e387fb23bd3b4b01549719cf95285b1d03047eff5e96df44cf"
  );
  const r9AuditStatus = parseJsonBytesRejectDuplicateKeys(
    await read(R9_AUDIT_STATUS)
  );
  assert.equal(r9AuditStatus.overallStatus, "NO_GO");
  assert.equal(r9AuditStatus.p0, 0);
  assert.equal(r9AuditStatus.p1, 0);
  assert.equal(r9AuditStatus.p2, 1);
  assert.equal(r9AuditStatus.blocking, 1);
  assert.equal(
    r9AuditStatus.ccAudit,
    "GO_INVALIDATED_BY_CODE_ARCHITECTURE_P2"
  );
  assert.equal(r9AuditStatus.codexCodeArchitectureAudit, "NO_GO");
  assert.equal(
    r9AuditStatus.codexTestSecurityAudit,
    "INTERRUPTED_NO_VERDICT"
  );
  assert.equal(
    r9AuditStatus.failureClassification,
    "GOVERNANCE_TASK_STATE_CONTRADICTION"
  );
  assert.match(
    (await read(R9_CC_AUDIT)).toString("utf8"),
    /INVALIDATED_BY_CODE_ARCHITECTURE_P2/
  );
  assert.match(
    (await read(R9_CODE_AUDIT)).toString("utf8"),
    /Verdict: `NO_GO`/
  );
  assert.match(
    (await read(R9_TEST_AUDIT)).toString("utf8"),
    /INTERRUPTED_NO_VERDICT/
  );
  const taskEval006 = (await read(TASK_EVAL_006)).toString("utf8");
  assert.doesNotMatch(
    taskEval006,
    /A0\/A1\/A2 仍等待 R6 immutable freeze/
  );
  assert.match(
    taskEval006,
    /A0\/A1\/A2 仍等待修复后唯一新 immutable subject 的三方全零 GO 与 CI 内容一致性/
  );

  const consoleManifest = {
    schemaVersion:
      "task-eval-006-track-b-recovery-r5-repair-console-manifest-v1",
    status: "PASS",
    completedAt,
    runs: passRuns,
    failedHarnessEvidence
  };
  const consoleManifestBytes = jsonBytes(consoleManifest);
  const verification = {
    schemaVersion:
      "task-eval-006-track-b-recovery-r5-repair-verification-result-v1",
    status: "PASS",
    completedAt,
    integrationUnit: "MILESTONE-MVP-002-TRACK-B-PROVIDER-RECOVERY",
    providerAdmission: "PENDING_THREE_PARTY_AUDIT_AND_CI",
    networkCallPerformedByRepair: false,
    deepSeekNetworkCallRepeated: false,
    chronology: {
      humanGroundTruthConfirmedAt: chronology.claim.humanGroundTruthConfirmedAt,
      codexExecutionClaimedAt: chronology.claim.claimedAt,
      freshAgentReadyAt: chronology.launch.readyAt,
      launchReceiptRecordedAt: chronology.launch.recordedAt,
      codexEvaluationStartedAt: chronology.opinion.startedAt,
      codexEvaluationCompletedAt: chronology.opinion.completedAt,
      codexCompletionReceivedAt: chronology.completion.receivedAt,
      admissionResealedAt: seal.sealedAt,
      verificationCompletedAt: completedAt
    },
    metrics: {
      node: "56/56",
      javaRecoveryRuntimeSeam: "1/1",
      codexAdmission: "9/9",
      deepSeekAdmission: "9/9",
      zeroCallControls: "3/3",
      secretLikeMatches: 0,
      forbiddenProviderPayloadMatches: 0,
      filesContainingCR: 0,
      gitDiffCheckExitCode: 0
    },
    consoleManifestPath: `${VERIFY_ROOT}/console-manifest.json`,
    consoleManifestSha256: sha256(consoleManifestBytes),
    evidence,
    previousFailedFreezePreserved: true,
    previousFailedFreezePath: OLD_FREEZE,
    failedAuditRoundsPreserved: [
      {
        subjectIdentity:
          "e77a86358d56d98e77c08cd5b20fc7dfe1a65c9a67a509cee2c35ac0350b54db",
        manifestPath: R5_FAILED_MANIFEST,
        codeArchitectureVerdict: "NO_GO",
        ccAuditStatus: "TRANSPORT_INCOMPLETE_NO_VERDICT",
        testSecurityAuditStatus: "INTERRUPTED_NO_VERDICT"
      },
      {
        subjectIdentity:
          "a9daf62f4644dadd3834a3ef1e92408bc96c4d974009e1f6020b3f3f24e397ad",
        manifestPath: R6_FAILED_MANIFEST,
        codeArchitectureVerdict: "GO",
        ccAuditVerdict: "NO_GO",
        ccAuditP0: 1,
        ccAuditP1: 0,
        ccAuditP2: 1,
        ccAuditBlocking: 2,
        testSecurityAuditVerdict: "GO",
        failureClassification: "AUDIT_PROJECTION_TRANSPORT"
      },
      {
        subjectIdentity:
          "47d84299f9f5002cc80c5aa20481452cdf6f27f83699fce2793c836aaf4852d3",
        manifestPath: R7_FAILED_MANIFEST,
        codeArchitectureAuditStatus: "INTERRUPTED_NO_VERDICT",
        ccAuditVerdict: "GO_INVALIDATED_BY_PACKAGE_ISOLATION_P0",
        testSecurityAuditVerdict: "NO_GO",
        testSecurityAuditP0: 1,
        testSecurityAuditP1: 0,
        testSecurityAuditP2: 0,
        testSecurityAuditBlocking: 1,
        failureClassification: "CC_AUDIT_PACKAGE_CONTENT_ISOLATION"
      },
      {
        subjectIdentity:
          "a85e96f58532a8a9b807e72cb3ca0666e011e72321cf87a6685d28c59db1b50d",
        manifestPath: R8_FAILED_MANIFEST,
        codeArchitectureAuditStatus: "INTERRUPTED_NO_VERDICT",
        ccAuditVerdict: "NO_GO",
        ccAuditP0: 0,
        ccAuditP1: 0,
        ccAuditP2: 1,
        ccAuditBlocking: 1,
        testSecurityAuditStatus: "INTERRUPTED_NO_VERDICT",
        failureClassification: "GOVERNANCE_DOCUMENT_FRESHNESS"
      },
      {
        subjectIdentity:
          "e566c8d2e987b2508c24083f1307765c3150722e9a39f66cdb5c7c08f14433a2",
        manifestPath: R9_FAILED_MANIFEST,
        codeArchitectureAuditVerdict: "NO_GO",
        codeArchitectureAuditP0: 0,
        codeArchitectureAuditP1: 0,
        codeArchitectureAuditP2: 1,
        codeArchitectureAuditBlocking: 1,
        ccAuditVerdict: "GO_INVALIDATED_BY_CODE_ARCHITECTURE_P2",
        testSecurityAuditStatus: "INTERRUPTED_NO_VERDICT",
        failureClassification: "GOVERNANCE_TASK_STATE_CONTRADICTION"
      }
    ],
    publicProfileDisabledUnbound: true,
    modelProducedOrChangedFindingVerdict: false,
    a0A1A2ImplementationAllowed: false,
    productionReadyClaimed: false,
    task028Unlocked: false,
    task031Unlocked: false,
    task032Unlocked: false
  };
  return { consoleManifest, verification };
}

export async function verifyTrackBProviderRecoveryR5RepairSubject({
  repoRoot,
  completedAt,
  mode = "create"
}) {
  const result = await buildTrackBProviderRecoveryR5RepairVerification({
    repoRoot,
    completedAt
  });
  const root = resolve(repoRoot);
  for (const [path, value] of [
    [`${VERIFY_ROOT}/console-manifest.json`, result.consoleManifest],
    [`${VERIFY_ROOT}/verification-result.json`, result.verification]
  ]) {
    const absolute = resolve(root, ...path.split("/"));
    const expected = jsonBytes(value);
    if (mode === "create") {
      await mkdir(dirname(absolute), { recursive: true });
      await writeFile(absolute, expected, { flag: "wx" });
    } else if (mode === "replace") {
      await writeFile(absolute, expected);
    } else if (mode === "verify") {
      assert.equal(Buffer.compare(await readFile(absolute), expected), 0);
    } else {
      throw new Error(`Unknown mode: ${mode}`);
    }
  }
  return {
    status: result.verification.status,
    consoleManifestSha256: sha256(jsonBytes(result.consoleManifest)),
    verificationResultSha256: sha256(jsonBytes(result.verification)),
    evidenceCount: result.verification.evidence.length,
    runCount: result.consoleManifest.runs.length
  };
}

if (
  process.argv[1] &&
  import.meta.url === pathToFileURL(resolve(process.argv[1])).href
) {
  const [repoRoot = ".", completedAt = new Date().toISOString(), mode = "create"] =
    process.argv.slice(2);
  process.stdout.write(`${JSON.stringify(
    await verifyTrackBProviderRecoveryR5RepairSubject({
      repoRoot,
      completedAt,
      mode
    })
  )}\n`);
}
