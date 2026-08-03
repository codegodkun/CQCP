import assert from "node:assert/strict";
import { readFile, writeFile } from "node:fs/promises";
import { resolve } from "node:path";

import {
  jsonBytes,
  sha256,
  TRACK_B_SCHEMA_DIAGNOSTIC_MAX_CALLS
} from "./track-b-schema-diagnostic-contract.mjs";
import { parseJsonBytesRejectDuplicateKeys } from "./strict-json.mjs";

const repoRoot = resolve(process.argv[2] ?? ".");
const frozenAt = canonicalIso(process.argv[3]);
const root = "outputs/task-eval-005/schema-diagnostic-v1";
const phases = [
  "baseline",
  "candidate-1-qualification",
  "candidate-1-confirmation"
];
const evidence = await Promise.all(phases.map(readPhase));
const [baseline, qualification, confirmation] = evidence;

assert.equal(baseline.result.status, "PASS_STRICT_SCHEMA_12_OF_12");
assert.equal(qualification.result.status, "PASS_STRICT_SCHEMA_12_OF_12");
assert.equal(confirmation.result.status, "PASS_STRICT_SCHEMA_12_OF_12");
assert.equal(qualification.result.promptSha256, confirmation.result.promptSha256);
assert.notEqual(baseline.result.promptSha256, qualification.result.promptSha256);
assert.equal(qualification.result.acceptedCount + confirmation.result.acceptedCount, 24);
assert.equal(
  Object.entries(qualification.result.categoryCounts)
    .filter(([key]) => key !== "ACCEPTED")
    .reduce((sum, [, value]) => sum + value, 0),
  0
);
assert.equal(
  Object.entries(confirmation.result.categoryCounts)
    .filter(([key]) => key !== "ACCEPTED")
    .reduce((sum, [, value]) => sum + value, 0),
  0
);
const diagnosticCallCount = evidence.reduce(
  (sum, item) => sum + item.result.completedCallCount,
  0
);
assert.equal(diagnosticCallCount, 36);
assert.ok(diagnosticCallCount <= TRACK_B_SCHEMA_DIAGNOSTIC_MAX_CALLS);

const promptPath =
  "scripts/blind-evaluation/track-b-schema-diagnostic-prompt-v2.txt";
const promptBytes = await readFile(relative(promptPath));
assert.equal(sha256(promptBytes), qualification.result.promptSha256);

const freeze = {
  schemaVersion: "task-eval-005-track-b-schema-stability-freeze-v1",
  status: "GO_24_OF_24_STRICT_SCHEMA",
  frozenAt,
  diagnosisOnly: true,
  formalAdmissionAffected: false,
  baselinePromptVersion: "track-b-holdout-opinion-prompt-v1",
  baselinePromptSha256: baseline.result.promptSha256,
  selectedPromptVersion: "track-b-single-packet-opinion-prompt-v2",
  selectedPromptPath: promptPath,
  selectedPromptSha256: sha256(promptBytes),
  selectedSchemaVersion: "track-b-role-candidate-anchor-abstention-v2",
  selectedRequestBuilderVersion:
    "track-b-single-packet-provider-request-builder-v2",
  model: "deepseek-v4-pro",
  thinking: "disabled",
  stream: false,
  responseFormat: "json_object",
  maxOutputTokens: 1500,
  qualificationAccepted: qualification.result.acceptedCount,
  confirmationAccepted: confirmation.result.acceptedCount,
  selectedVersionAccepted: 24,
  selectedVersionRejected: 0,
  diagnosticCallCount,
  diagnosticCallLimit: TRACK_B_SCHEMA_DIAGNOSTIC_MAX_CALLS,
  candidate2Created: false,
  rawRequestPersisted: false,
  rawResponsePersisted: false,
  responseContentPersisted: false,
  reasoningContentPersisted: false,
  secretPersisted: false,
  evidence: evidence.map((item) => ({
    phase: item.result.phase,
    resultPath: item.resultPath,
    resultSha256: item.resultSha256,
    claimPath: item.claimPath,
    claimSha256: item.claimSha256,
    dispatchSha256: item.result.dispatchSha256,
    providerCallSetSha256: item.result.providerCallSetSha256,
    completedCallCount: item.result.completedCallCount,
    acceptedCount: item.result.acceptedCount,
    terminalCode: item.result.terminalCode
  })),
  fifthCorpusCreationAllowed: true,
  taskEval004RetryAllowed: false,
  sixthCorpusAllowed: false,
  providerA0A1A2Allowed: false
};
const bytes = jsonBytes(freeze);
const outputPath = relative(`${root}/stability-freeze.json`);
await writeFile(outputPath, bytes, { flag: "wx" });
process.stdout.write(`${JSON.stringify({
  status: freeze.status,
  selectedPromptSha256: freeze.selectedPromptSha256,
  selectedSchemaVersion: freeze.selectedSchemaVersion,
  selectedVersionAccepted: freeze.selectedVersionAccepted,
  selectedVersionRejected: freeze.selectedVersionRejected,
  diagnosticCallCount,
  freezeSha256: sha256(bytes),
  fifthCorpusCreationAllowed: true
})}\n`);

async function readPhase(phase) {
  const resultPath = `${root}/${phase}/diagnostic-result.json`;
  const claimPath = `${root}/${phase}/execution-claim.json`;
  const [resultBytes, claimBytes] = await Promise.all([
    readFile(relative(resultPath)),
    readFile(relative(claimPath))
  ]);
  return {
    resultPath,
    resultSha256: sha256(resultBytes),
    result: parseJsonBytesRejectDuplicateKeys(resultBytes),
    claimPath,
    claimSha256: sha256(claimBytes)
  };
}

function relative(path) {
  return resolve(repoRoot, ...path.split("/"));
}

function canonicalIso(value) {
  assert.equal(typeof value, "string");
  assert.equal(new Date(value).toISOString(), value);
  return value;
}
