import assert from "node:assert/strict";
import { readFile, writeFile } from "node:fs/promises";
import { resolve, sep } from "node:path";

import { sha256 } from "./track-b-holdout-contract.mjs";
import {
  validateTrackBFinalModelInput,
  validateTrackBFinalOpinionPayload,
  validateTrackBFifthModelInput,
  validateTrackBFifthOpinionPayload,
  validateTrackBHoldoutModelInput,
  validateTrackBHoldoutOpinionPayload,
  validateTrackBSuccessorModelInput,
  validateTrackBSuccessorOpinionPayload
} from "./track-b-holdout-opinion-contract.mjs";
import {
  parseJsonBytesRejectDuplicateKeys
} from "./strict-json.mjs";

const [
  repoArg,
  rawOutputArg,
  agentTask,
  agentId,
  startedAtArg,
  completedAtArg
] = process.argv.slice(2);
const profileArg = process.argv[8] ?? "holdout";
if (
  !repoArg ||
  !rawOutputArg ||
  !agentTask ||
  !agentId ||
  !startedAtArg ||
  !completedAtArg
) {
  throw new Error(
    "Usage: node seal-track-b-holdout-codex-opinion.mjs " +
      "<repo-root> <raw-output-path> <agent-task> <agent-id> " +
      "<started-at> <completed-at>"
  );
}
const repoRoot = resolve(repoArg);
const profiles = Object.freeze({
  holdout: Object.freeze({
    runRoot: "outputs/task-eval-002/track-b-holdout-v1/run-v1",
    receiptSchema:
      "task-eval-002-track-b-holdout-codex-execution-receipt-v1",
    opinionSchema: "task-eval-002-track-b-holdout-model-opinion-v1",
    validateModelInput: validateTrackBHoldoutModelInput,
    validateOpinionPayload: validateTrackBHoldoutOpinionPayload
  }),
  successor: Object.freeze({
    runRoot: "outputs/task-eval-003/track-b-successor-v1/run-v1",
    receiptSchema:
      "task-eval-003-track-b-successor-codex-execution-receipt-v1",
    opinionSchema: "task-eval-003-track-b-successor-model-opinion-v1",
    validateModelInput: validateTrackBSuccessorModelInput,
    validateOpinionPayload: validateTrackBSuccessorOpinionPayload
  }),
  final: Object.freeze({
    runRoot: "outputs/task-eval-004/track-b-final-v1/run-v1",
    receiptSchema:
      "task-eval-004-track-b-final-codex-execution-receipt-v1",
    opinionSchema: "task-eval-004-track-b-final-model-opinion-v1",
    validateModelInput: validateTrackBFinalModelInput,
    validateOpinionPayload: validateTrackBFinalOpinionPayload
  }),
  fifth: Object.freeze({
    runRoot: "outputs/task-eval-005/track-b-fifth-v1/run-v1",
    receiptSchema:
      "task-eval-005-track-b-fifth-codex-execution-receipt-v2",
    opinionSchema: "task-eval-005-track-b-fifth-model-opinion-v2",
    validateModelInput: validateTrackBFifthModelInput,
    validateOpinionPayload: validateTrackBFifthOpinionPayload
  })
});
const profile = profiles[profileArg];
if (!profile) throw new Error(`Unknown profile: ${profileArg}`);
const runRoot = profile.runRoot;
const inputRelativePath = `${runRoot}/model-input.json`;
const dispatchRelativePath = `${runRoot}/dispatch.json`;
const callSetRelativePath = `${runRoot}/provider-call-set.json`;
const outputRelativePath = `${runRoot}/codex-opinion.json`;
const receiptRelativePath = `${runRoot}/codex-execution-receipt.json`;
const rawOutputPath = resolve(repoRoot, rawOutputArg);
if (!rawOutputPath.startsWith(`${repoRoot}${sep}`)) {
  throw new Error("TRACK_B_HOLDOUT_CODEX_OUTPUT_PATH_ESCAPED");
}
assert.match(agentTask, /^\/root\/[a-z0-9_]+$/);
assert.match(agentId, /^[A-Za-z0-9._:-]{1,128}$/);
const startedAt = canonicalIso(startedAtArg, "startedAt");
const completedAt = canonicalIso(completedAtArg, "completedAt");
assert.ok(Date.parse(completedAt) >= Date.parse(startedAt));

const inputBytes = await readRelative(inputRelativePath);
const dispatchBytes = await readRelative(dispatchRelativePath);
const callSetBytes = await readRelative(callSetRelativePath);
const rawOutputBytes = await readFile(rawOutputPath);
const input = profile.validateModelInput(
  parseJsonBytesRejectDuplicateKeys(inputBytes)
);
const dispatch = parseJsonBytesRejectDuplicateKeys(dispatchBytes);
assert.equal(dispatch.modelInputSha256, sha256(inputBytes));
assert.equal(dispatch.providerCallSetSha256, sha256(callSetBytes));
assert.ok(Date.parse(startedAt) >= Date.parse(dispatch.createdAt));
const payload = profile.validateOpinionPayload(
  input,
  parseJsonBytesRejectDuplicateKeys(rawOutputBytes)
);
const receipt = {
  schemaVersion: profile.receiptSchema,
  source: "CODEX_COLLABORATION_TOOL",
  forkTurns: "none",
  historicalContextIncluded: false,
  agentTask,
  agentId,
  modelInputSha256: sha256(inputBytes),
  dispatchSha256: sha256(dispatchBytes),
  providerCallSetSha256: sha256(callSetBytes),
  rawAgentOutputPath: rawOutputArg.replaceAll("\\", "/"),
  rawAgentOutputSha256: sha256(rawOutputBytes),
  startedAt,
  completedAt,
  transcriptCrossCheckRequired: true
};
const receiptBytes = Buffer.from(
  `${JSON.stringify(receipt, null, 2)}\n`,
  "utf8"
);
const output = {
  schemaVersion: profile.opinionSchema,
  status: "ACCEPTED",
  track: input.track,
  evaluator: "codex-blind-subagent",
  forkTurns: "none",
  historicalContextIncluded: false,
  agentTask,
  agentId,
  modelInputSha256: sha256(inputBytes),
  dispatchSha256: sha256(dispatchBytes),
  providerCallSetSha256: sha256(callSetBytes),
  executionReceiptPath: receiptRelativePath,
  executionReceiptSha256: sha256(receiptBytes),
  startedAt,
  completedAt,
  opinions: payload.opinions
};
const outputBytes = Buffer.from(
  `${JSON.stringify(output, null, 2)}\n`,
  "utf8"
);
await writeFile(resolveRelative(receiptRelativePath), receiptBytes, {
  flag: "wx"
});
try {
  await writeFile(resolveRelative(outputRelativePath), outputBytes, {
    flag: "wx"
  });
} catch (error) {
  throw new Error(
    `TRACK_B_HOLDOUT_CODEX_OPINION_WRITE_FAILED_AFTER_RECEIPT: ${error.code ?? "UNKNOWN"}`
  );
}
process.stdout.write(`${JSON.stringify({
  status: output.status,
  opinionCount: output.opinions.length,
  modelInputSha256: output.modelInputSha256,
  outputSha256: sha256(outputBytes),
  receiptSha256: sha256(receiptBytes)
})}\n`);

function readRelative(relativePath) {
  return readFile(resolveRelative(relativePath));
}

function resolveRelative(relativePath) {
  const resolved = resolve(repoRoot, ...relativePath.split("/"));
  if (!resolved.startsWith(`${repoRoot}${sep}`)) {
    throw new Error("TRACK_B_HOLDOUT_CODEX_PATH_ESCAPED");
  }
  return resolved;
}

function canonicalIso(value, field) {
  assert.equal(typeof value, "string", `${field} must be a string`);
  const parsed = Date.parse(value);
  assert.ok(Number.isFinite(parsed), `${field} is invalid`);
  assert.equal(new Date(parsed).toISOString(), value);
  return value;
}
