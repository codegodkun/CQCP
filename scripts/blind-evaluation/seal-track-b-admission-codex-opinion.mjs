import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, isAbsolute, relative, resolve } from "node:path";

import {
  validateTrackBAdmissionModelInput,
  validateTrackBAdmissionOpinionPayload
} from "./track-b-admission-opinion-contract.mjs";

const [
  repoArg,
  rawOutputArg,
  agentTask,
  agentId,
  startedAtArg,
  completedAtArg
] = process.argv.slice(2);
if (
  !repoArg ||
  !rawOutputArg ||
  !/^\/root\/[a-z0-9_]+$/.test(agentTask ?? "") ||
  !agentId?.trim() ||
  !isCanonicalIso(startedAtArg) ||
  !isCanonicalIso(completedAtArg)
) {
  throw new Error(
    "Usage: node seal-track-b-admission-codex-opinion.mjs " +
      "<repo-root> <raw-agent-output> </root/agent-task> <agent-id> " +
      "<started-at> <completed-at>"
  );
}

const repoRoot = resolve(repoArg);
const rawOutputPath = resolve(rawOutputArg);
const runRoot = "outputs/task-eval-002/track-b-admission-run-v3";
const inputRelativePath = `${runRoot}/model-input.json`;
const dispatchRelativePath = `${runRoot}/dispatch.json`;
const receiptRelativePath = `${runRoot}/codex-execution-receipt.json`;
const opinionRelativePath = `${runRoot}/codex-opinion.json`;
const sha256 = (bytes) => createHash("sha256").update(bytes).digest("hex");
const readRelative = (relativePath) =>
  readFile(resolve(repoRoot, ...relativePath.split("/")));

const inputBytes = await readRelative(inputRelativePath);
const input = validateTrackBAdmissionModelInput(
  JSON.parse(inputBytes.toString("utf8"))
);
const dispatchBytes = await readRelative(dispatchRelativePath);
const dispatch = JSON.parse(dispatchBytes.toString("utf8"));
const dispatchSha256 = sha256(dispatchBytes);
assert.equal(dispatch.modelInputPath, inputRelativePath);
assert.equal(dispatch.modelInputSha256, sha256(inputBytes));
assert.equal(dispatch.codexAgentTask, agentTask);
assert.equal(dispatch.forkTurns, "none");
assert.equal(dispatch.historicalContextIncluded, false);
assert.equal(dispatch.unblindProhibitedUntilBothModelOpinionsSealed, true);

const startedAt = new Date(startedAtArg).toISOString();
const completedAt = new Date(completedAtArg).toISOString();
assert.ok(Date.parse(startedAt) >= Date.parse(dispatch.createdAt));
assert.ok(Date.parse(completedAt) >= Date.parse(startedAt));
assert.ok(Date.parse(completedAt) <= Date.now() + 300_000);

const rawOutputBytes = await readFile(rawOutputPath);
const payload = JSON.parse(rawOutputBytes.toString("utf8"));
validateTrackBAdmissionOpinionPayload(input, payload);
const relativeRawPath = relativePathWithinRepo(repoRoot, rawOutputPath);
const receipt = {
  schemaVersion:
    "task-eval-002-track-b-admission-codex-execution-receipt-v1",
  source: "CODEX_COLLABORATION_TOOL",
  forkTurns: "none",
  agentTask,
  agentId,
  modelInputSha256: sha256(inputBytes),
  dispatchSha256,
  rawAgentOutputPath: relativeRawPath,
  rawAgentOutputSha256: sha256(rawOutputBytes),
  startedAt,
  completedAt,
  transcriptCrossCheckRequired: true
};
const receiptBytes = Buffer.from(
  `${JSON.stringify(receipt, null, 2)}\n`,
  "utf8"
);
const opinion = {
  schemaVersion: "task-eval-002-track-b-admission-model-opinion-v1",
  status: "ACCEPTED",
  track: input.track,
  evaluator: "codex-blind-agent",
  agentTask,
  agentId,
  inputSha256: sha256(inputBytes),
  dispatchSha256,
  executionReceiptSha256: sha256(receiptBytes),
  startedAt,
  completedAt,
  opinions: payload.opinions
};
const receiptPath = resolve(repoRoot, ...receiptRelativePath.split("/"));
const opinionPath = resolve(repoRoot, ...opinionRelativePath.split("/"));
await mkdir(dirname(receiptPath), { recursive: true });
await writeFile(receiptPath, receiptBytes, { flag: "wx" });
await writeFile(
  opinionPath,
  `${JSON.stringify(opinion, null, 2)}\n`,
  { encoding: "utf8", flag: "wx" }
);
process.stdout.write(
  `${JSON.stringify({
    status: opinion.status,
    evaluator: opinion.evaluator,
    opinionCount: opinion.opinions.length,
    receiptSha256: opinion.executionReceiptSha256,
    opinionSha256: sha256(await readFile(opinionPath))
  })}\n`
);

function isCanonicalIso(value) {
  if (
    typeof value !== "string" ||
    !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/.test(value)
  ) {
    return false;
  }
  const parsed = Date.parse(value);
  return Number.isFinite(parsed) && new Date(parsed).toISOString() === value;
}

function relativePathWithinRepo(root, target) {
  const candidate = relative(resolve(root), resolve(target));
  assert.ok(
    candidate &&
      !isAbsolute(candidate) &&
      candidate !== ".." &&
      !candidate.startsWith(`..\\`) &&
      !candidate.startsWith("../"),
    "Raw Codex output must be inside the repository"
  );
  return candidate.replaceAll("\\", "/");
}
