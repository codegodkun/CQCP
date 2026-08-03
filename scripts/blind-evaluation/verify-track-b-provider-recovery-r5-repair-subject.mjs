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
