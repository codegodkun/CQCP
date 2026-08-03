import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { access, mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { pathToFileURL } from "node:url";

import { parseJsonBytesRejectDuplicateKeys } from "./strict-json.mjs";

const OUTPUT_ROOT = "outputs/task-eval-006/track-b-recovery-v1";
const RUN_ROOT = `${OUTPUT_ROOT}/run-v1`;
const VERIFY_ROOT = `${OUTPUT_ROOT}/verification`;
const CONSOLES = Object.freeze([
  {
    name: "node-phase-appropriate",
    command: "node --test <25 explicitly listed phase-appropriate files>",
    path: `${VERIFY_ROOT}/node-tests-phase-appropriate.console.log`,
    required: [/ℹ tests 70/, /ℹ pass 70/, /ℹ fail 0/]
  },
  {
    name: "java-track-b-runtime-seam",
    command:
      "gradle test --tests com.cqcp.apiserver.reviewengine.TrackBHoldoutRuntimeContractTest --console=plain",
    path: `${VERIFY_ROOT}/java-track-b-runtime.console.log`,
    required: [/BUILD SUCCESSFUL/]
  },
  {
    name: "admission-seal-verify",
    command:
      "node scripts/blind-evaluation/seal-track-b-provider-recovery-admission.mjs . <sealedAt> verify",
    path: `${VERIFY_ROOT}/admission-seal-verify.console.log`,
    required: [
      /SEALED_GO_TRACK_B_RECOVERY_ADMISSION/,
      /"sealSha256":"2d879b139715ca7646a95e035747891a14b38ab545302a899ef2ca6376651b04"/
    ]
  },
  {
    name: "secret-provider-leak-scan",
    command: "PowerShell in-memory exact Secret and forbidden payload scan",
    path: `${VERIFY_ROOT}/secret-and-provider-leak-scan.console.log`,
    required: [/actualKeyMatches=0/, /forbiddenProviderPayloadMatches=0/]
  },
  {
    name: "line-endings",
    command: "PowerShell LF byte scan for TASK-EVAL-006 hash-bound text",
    path: `${VERIFY_ROOT}/line-endings.console.log`,
    required: [/filesContainingCR=0/]
  },
  {
    name: "git-diff-check",
    command: "git diff --check",
    path: `${VERIFY_ROOT}/git-diff-check.console.log`,
    required: [/gitDiffCheckExitCode=0/]
  }
]);
const EVIDENCE_PATHS = Object.freeze([
  `${OUTPUT_ROOT}/corpus.json`,
  `${OUTPUT_ROOT}/human-confirmation-challenge.json`,
  `${OUTPUT_ROOT}/human-confirmation.json`,
  `${OUTPUT_ROOT}/human-ground-truth.json`,
  `${RUN_ROOT}/model-input.json`,
  `${RUN_ROOT}/provider-call-set.json`,
  `${RUN_ROOT}/dispatch.json`,
  `${RUN_ROOT}/derived-egress-receipt.json`,
  `${RUN_ROOT}/codex-opinion.json`,
  `${RUN_ROOT}/deepseek-execution-claim.json`,
  `${RUN_ROOT}/deepseek-opinion.json`,
  `${RUN_ROOT}/admission-report.json`,
  `${RUN_ROOT}/admission-seal.json`,
  "outputs/task-eval-006/track-b-provider-recovery-v1/diagnostic-v1/diagnostic-seal.json",
  "outputs/task-eval-005/track-b-fifth-v1/run-v1/unblind/admission-seal.json",
  "decisions/ADR-026-track-b-provider-conversation-recovery.md",
  "tasks/active/TASK-EVAL-006-track-b-provider-conversation-recovery.md"
]);

const sha256 = (bytes) =>
  createHash("sha256").update(bytes).digest("hex");
const jsonBytes = (value) =>
  Buffer.from(`${JSON.stringify(value, null, 2)}\n`, "utf8");

export async function buildTrackBProviderRecoveryVerification({
  repoRoot: repoArg,
  completedAt
}) {
  assert.equal(new Date(completedAt).toISOString(), completedAt);
  const repoRoot = resolve(repoArg);
  const read = (relative) => readFile(resolve(repoRoot, ...relative.split("/")));
  const runs = [];
  for (const descriptor of CONSOLES) {
    const bytes = await read(descriptor.path);
    const text = bytes.toString("utf8");
    for (const pattern of descriptor.required) {
      assert.match(text, pattern, `${descriptor.name} missing ${pattern}`);
    }
    runs.push({
      name: descriptor.name,
      command: descriptor.command,
      exitCode: 0,
      logPath: descriptor.path,
      logSize: bytes.length,
      logSha256: sha256(bytes)
    });
  }

  const evidence = [];
  for (const path of EVIDENCE_PATHS) {
    const bytes = await read(path);
    evidence.push({ path, size: bytes.length, sha256: sha256(bytes) });
  }
  const getJson = async (path) =>
    parseJsonBytesRejectDuplicateKeys(await read(path));
  const human = await getJson(`${OUTPUT_ROOT}/human-ground-truth.json`);
  const dispatch = await getJson(`${RUN_ROOT}/dispatch.json`);
  const claim = await getJson(`${RUN_ROOT}/deepseek-execution-claim.json`);
  const deepSeek = await getJson(`${RUN_ROOT}/deepseek-opinion.json`);
  const report = await getJson(`${RUN_ROOT}/admission-report.json`);
  const seal = await getJson(`${RUN_ROOT}/admission-seal.json`);
  const diagnosticSeal = await getJson(
    "outputs/task-eval-006/track-b-provider-recovery-v1/diagnostic-v1/diagnostic-seal.json"
  );
  const fifthSealBytes = await read(
    "outputs/task-eval-005/track-b-fifth-v1/run-v1/unblind/admission-seal.json"
  );
  assert.equal(
    sha256(fifthSealBytes),
    "520d7b38c46b0fc69bf2afb2726f01cadb1991b3b386d481c70b145274d451be",
    "TASK-EVAL-005 terminal seal changed"
  );
  assert.equal(
    diagnosticSeal.status,
    "SEALED_GO_RECOVERY_DIAGNOSTIC"
  );
  assert.equal(
    sha256(jsonBytes(diagnosticSeal)),
    "fedc4e21801f1e5c38f5fc97c0da6db16546fb775ee060991f54e5f41d9404ff"
  );
  assert.equal(report.status, "GO_9_OF_9_PLUS_3_ZERO_CALL");
  assert.equal(seal.status, "SEALED_GO_TRACK_B_RECOVERY_ADMISSION");
  assert.equal(seal.providerAdmissionEstablished, true);
  assert.equal(seal.verificationAndAuditAllowed, true);
  assert.equal(seal.a0A1A2ImplementationAllowed, false);
  assert.equal(deepSeek.automaticRetryPerformed, false);
  assert.equal(deepSeek.rawResponsePersisted, false);
  assert.equal(deepSeek.reasoningContentPersisted, false);
  assert.equal(dispatch.networkCallPerformed, false);
  await assert.rejects(
    () => access(resolve(repoRoot, ...`${RUN_ROOT}/deepseek-terminal.json`.split("/"))),
    /ENOENT/
  );

  const consoleManifest = {
    schemaVersion:
      "task-eval-006-track-b-recovery-console-manifest-v1",
    status: "PASS",
    completedAt,
    phaseAppropriateScope: true,
    allHistorySuiteIsIndependentEntrypoint: false,
    allHistoryFailureLogPath:
      `${VERIFY_ROOT}/node-tests-all-history.failed.console.log`,
    runs
  };
  const consoleManifestBytes = jsonBytes(consoleManifest);
  const verification = {
    schemaVersion:
      "task-eval-006-track-b-recovery-verification-result-v1",
    status: "PASS",
    completedAt,
    integrationUnit: "MILESTONE-MVP-002-TRACK-B-PROVIDER-RECOVERY",
    providerAdmission: "ESTABLISHED_FOR_EVALUATION_SHADOW_GATE",
    networkCallPerformedByVerification: false,
    chronology: {
      humanGroundTruthConfirmedAt: human.confirmedAt,
      dispatchCreatedAt: dispatch.createdAt,
      deepSeekClaimedAt: claim.claimedAt,
      deepSeekCompletedAt: deepSeek.completedAt,
      admissionSealedAt: seal.sealedAt,
      verificationCompletedAt: completedAt
    },
    metrics: {
      node: "70/70",
      javaRuntimeSeam: "1/1",
      codexAdmission: "9/9",
      deepSeekAdmission: "9/9",
      zeroCallControls: "3/3",
      actualSecretLeakFiles: 0,
      forbiddenProviderPayloadFiles: 0,
      filesContainingCR: 0,
      gitDiffCheckExitCode: 0
    },
    consoleManifestPath: `${VERIFY_ROOT}/console-manifest.json`,
    consoleManifestSha256: sha256(consoleManifestBytes),
    evidence,
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

export async function verifyTrackBProviderRecoverySubject({
  repoRoot,
  completedAt,
  mode = "create"
}) {
  const result = await buildTrackBProviderRecoveryVerification({
    repoRoot,
    completedAt
  });
  const root = resolve(repoRoot);
  for (const [relative, value] of [
    [`${VERIFY_ROOT}/console-manifest.json`, result.consoleManifest],
    [`${VERIFY_ROOT}/verification-result.json`, result.verification]
  ]) {
    const path = resolve(root, ...relative.split("/"));
    if (mode === "create") {
      await mkdir(dirname(path), { recursive: true });
      await writeFile(path, jsonBytes(value), { flag: "wx" });
    } else if (mode === "verify") {
      assert.equal(Buffer.compare(await readFile(path), jsonBytes(value)), 0);
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
  const result = await verifyTrackBProviderRecoverySubject({
    repoRoot,
    completedAt,
    mode
  });
  process.stdout.write(`${JSON.stringify(result)}\n`);
}
