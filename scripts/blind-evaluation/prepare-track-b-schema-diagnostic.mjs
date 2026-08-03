import assert from "node:assert/strict";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { resolve } from "node:path";

import {
  buildTrackBSchemaDiagnosticCalls,
  buildTrackBSchemaDiagnosticInput,
  collectTrackBSchemaDiagnosticDisjointness,
  jsonBytes,
  sha256,
  TRACK_B_SCHEMA_DIAGNOSTIC_CALLS_PER_PASS,
  TRACK_B_SCHEMA_DIAGNOSTIC_MODEL
} from "./track-b-schema-diagnostic-contract.mjs";
import {
  loadAndValidateMvp002StandingEgressGrant
} from "./mvp002-standing-egress-grant.mjs";

const repoRoot = resolve(process.argv[2] ?? ".");
const createdAt = canonicalIso(process.argv[3], "createdAt");
const phase = process.argv[4] ?? "baseline";
const promptRelativePath = process.argv[5] ??
  "scripts/blind-evaluation/track-b-holdout-opinion-prompt.txt";

const allowedPhases = new Set([
  "baseline",
  "candidate-1-qualification",
  "candidate-1-confirmation",
  "candidate-2-qualification",
  "candidate-2-confirmation"
]);
assert.ok(allowedPhases.has(phase), "phase is not allowed");
if (phase === "baseline") {
  assert.equal(
    promptRelativePath,
    "scripts/blind-evaluation/track-b-holdout-opinion-prompt.txt"
  );
}

const templatePath =
  "outputs/task-eval-004/track-b-final-v1/run-v1/model-input.json";
const priorCorpusPaths = [
  "apps/api-server/src/test/resources/track-b-admission-corpus-v2/corpus.json",
  "outputs/task-eval-002/track-b-holdout-v1/corpus.json",
  "outputs/task-eval-003/track-b-successor-v1/corpus.json",
  "outputs/task-eval-004/track-b-final-v1/corpus.json"
];
const outputRootRelative =
  `outputs/task-eval-005/schema-diagnostic-v1/${phase}`;
const outputRoot = relative(outputRootRelative);

const templateBytes = await readFile(relative(templatePath));
const input = buildTrackBSchemaDiagnosticInput(
  JSON.parse(templateBytes.toString("utf8"))
);
const priorCorpora = await Promise.all(
  priorCorpusPaths.map(async (path) =>
    JSON.parse((await readFile(relative(path))).toString("utf8"))
  )
);
const disjointness = collectTrackBSchemaDiagnosticDisjointness(
  input,
  priorCorpora
);
assert.deepEqual(disjointness, {
  priorCorpusCount: 4,
  identityOverlapCount: 0,
  candidateValueOverlapCount: 0,
  evidenceTextOverlapCount: 0
});

const promptBytes = await readFile(relative(promptRelativePath));
const calls = buildTrackBSchemaDiagnosticCalls(input, promptBytes);
const inputBytes = jsonBytes(input);
const callSet = {
  schemaVersion: "task-eval-005-track-b-schema-diagnostic-call-set-v1",
  status: "FROZEN_BEFORE_NETWORK",
  phase,
  createdAt,
  purpose: "PROVIDER_VALIDATION",
  dataClass: "SYNTHETIC_CORPUS",
  endpointOrigin: "https://api.deepseek.com:443",
  model: TRACK_B_SCHEMA_DIAGNOSTIC_MODEL,
  promptPath: promptRelativePath,
  promptSha256: sha256(promptBytes),
  actualInputSha256: sha256(inputBytes),
  callCount: calls.length,
  inputCount: calls.length,
  calls: calls.map((call) => ({
    callIndex: call.callIndex,
    callId: call.callId,
    packetId: call.packetId,
    modelInputSha256: call.modelInputSha256,
    outboundRequestSha256: call.outboundRequestSha256
  }))
};
assert.equal(callSet.callCount, TRACK_B_SCHEMA_DIAGNOSTIC_CALLS_PER_PASS);
const callSetBytes = jsonBytes(callSet);
const dispatch = {
  schemaVersion: "task-eval-005-track-b-schema-diagnostic-dispatch-v1",
  status: "FROZEN_BEFORE_NETWORK",
  phase,
  createdAt,
  taskId: "TASK-EVAL-005",
  integrationUnit: "MILESTONE-MVP-002-TRACK-B-SCHEMA-STABILITY",
  promptPath: promptRelativePath,
  promptSha256: sha256(promptBytes),
  inputPath: `${outputRootRelative}/diagnostic-input.json`,
  actualInputSha256: sha256(inputBytes),
  callSetPath: `${outputRootRelative}/provider-call-set.json`,
  providerCallSetSha256: sha256(callSetBytes),
  endpointOrigin: "https://api.deepseek.com:443",
  model: TRACK_B_SCHEMA_DIAGNOSTIC_MODEL,
  callCount: calls.length,
  rawRequestPersistenceAllowed: false,
  rawResponsePersistenceAllowed: false,
  reasoningPersistenceAllowed: false,
  automaticRetryAllowedAfterHttpStart: false,
  formalAdmissionAffected: false
};
const dispatchBytes = jsonBytes(dispatch);
const { bytes: grantBytes, grant } =
  await loadAndValidateMvp002StandingEgressGrant(
    relative("scripts/blind-evaluation/mvp002-standing-egress-grant.json")
  );
assert.ok(grant.scope.allowedPurposes.includes("PROVIDER_VALIDATION"));
assert.ok(grant.scope.allowedDataClasses.includes("SYNTHETIC_CORPUS"));
const receipt = {
  schemaVersion: "task-eval-005-track-b-schema-diagnostic-derived-receipt-v1",
  status: "DERIVED_BEFORE_NETWORK",
  phase,
  createdAt,
  milestoneId: grant.milestoneId,
  standingGrantSha256: sha256(grantBytes),
  purpose: "PROVIDER_VALIDATION",
  dataClass: "SYNTHETIC_CORPUS",
  actualInputSha256: sha256(inputBytes),
  dispatchSha256: sha256(dispatchBytes),
  providerCallSetSha256: sha256(callSetBytes),
  outboundRequestSha256s: calls.map(
    (call) => call.outboundRequestSha256
  ),
  endpointOrigin: "https://api.deepseek.com:443",
  model: TRACK_B_SCHEMA_DIAGNOSTIC_MODEL,
  callCount: calls.length,
  inputCount: calls.length,
  rawRequestPersisted: false,
  rawResponsePersisted: false,
  reasoningContentPersisted: false,
  formalAdmissionAffected: false
};
const receiptBytes = jsonBytes(receipt);
const manifest = {
  schemaVersion: "task-eval-005-track-b-schema-diagnostic-preflight-v1",
  status: "READY_FOR_ONE_DIAGNOSTIC_PASS",
  phase,
  createdAt,
  templatePath,
  templateSha256: sha256(templateBytes),
  priorCorpusPaths,
  disjointness,
  promptPath: promptRelativePath,
  promptSha256: sha256(promptBytes),
  inputPath: dispatch.inputPath,
  actualInputSha256: dispatch.actualInputSha256,
  dispatchPath: `${outputRootRelative}/dispatch.json`,
  dispatchSha256: receipt.dispatchSha256,
  callSetPath: dispatch.callSetPath,
  providerCallSetSha256: receipt.providerCallSetSha256,
  derivedReceiptPath: `${outputRootRelative}/derived-egress-receipt.json`,
  derivedReceiptSha256: sha256(receiptBytes),
  callCount: calls.length,
  networkAttempted: false,
  formalAdmissionAffected: false
};

await mkdir(outputRoot, { recursive: true });
await writeImmutable("diagnostic-input.json", inputBytes);
await writeImmutable("provider-call-set.json", callSetBytes);
await writeImmutable("dispatch.json", dispatchBytes);
await writeImmutable("derived-egress-receipt.json", receiptBytes);
await writeImmutable("preflight-manifest.json", jsonBytes(manifest));

process.stdout.write(`${JSON.stringify({
  status: manifest.status,
  phase,
  promptSha256: manifest.promptSha256,
  actualInputSha256: manifest.actualInputSha256,
  dispatchSha256: manifest.dispatchSha256,
  providerCallSetSha256: manifest.providerCallSetSha256,
  derivedReceiptSha256: manifest.derivedReceiptSha256,
  callCount: manifest.callCount,
  disjointness,
  networkAttempted: false
})}\n`);

function relative(path) {
  return resolve(repoRoot, ...path.split("/"));
}

function writeImmutable(name, bytes) {
  return writeFile(resolve(outputRoot, name), bytes, { flag: "wx" });
}

function canonicalIso(value, field) {
  assert.equal(typeof value, "string", `${field} must be supplied`);
  assert.equal(new Date(value).toISOString(), value);
  return value;
}
