import assert from "node:assert/strict";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { pathToFileURL } from "node:url";

import {
  buildCodexRevalidationClaim,
  buildCodexRevalidationCompletionReceipt,
  buildCodexRevalidationLaunchReceipt,
  CODEX_REVALIDATION_ROOT,
  codexRevalidationSha256
} from "./track-b-provider-recovery-codex-revalidation-contract.mjs";

const RUN_ROOT = "outputs/task-eval-006/track-b-recovery-v1/run-v1";
const HUMAN_PATH =
  "outputs/task-eval-006/track-b-recovery-v1/human-ground-truth.json";
const PROMPT_PATH =
  "scripts/blind-evaluation/track-b-provider-recovery-prompt-v3.txt";
const READINESS_INSTRUCTION = `Readiness phase only. Do not read any filesystem file, repository, chat history, or project memory. Do not evaluate any packet. Use PowerShell 7 only to obtain the current UTC time, then reply with exactly one JSON object: {"status":"READY_NO_FILES_READ","readyAt":"<canonical ISO UTC>","filesRead":0}. Do not add Markdown or commentary.\n`;

const jsonBytes = (value) =>
  Buffer.from(`${JSON.stringify(value, null, 2)}\n`, "utf8");
const canonicalIso = (value) => {
  assert.equal(new Date(value).toISOString(), value);
  return value;
};
const fromRepo = (root, relative) =>
  resolve(root, ...relative.split("/"));

function evaluationInstruction(isolatedDirectory) {
  const root = isolatedDirectory.replaceAll("\\", "/");
  return `You are the fresh CQCP Track B blind revalidation evaluator. Your inherited context contains only the prior readiness instruction and response. Before reading any file, use PowerShell 7 to capture a canonical UTC startedAt. Then read exactly these four files under ${root}: execution-claim.json, launch-receipt.json, model-input.json, prompt-v3.txt. Do not read any other filesystem path, repository file, project memory, chat history, prior Codex/DeepSeek opinion, human ground truth, actual/expected, Finding, or verdict. Verify the directory contains exactly those four files and verify their hashes against the claim/launch receipt. Evaluate all 9 packets using prompt-v3.txt. After evaluation capture completedAt in canonical UTC. Reply with exactly one JSON object and no Markdown/commentary. Top-level fields must be: schemaVersion="task-eval-006-track-b-recovery-codex-opinion-v4", status="ACCEPTED_FRESH_BLIND_REVALIDATION_OPINION", evaluator="codex-subagent-gpt-5.6-sol-xhigh", agentId, canonicalTaskName, executionClaimSha256, launchReceiptSha256, evaluationInstructionSha256, modelInputSha256, freshContext=true, forkTurns="none", allowedInputOnly=true, humanGroundTruthRead=false, priorModelOpinionRead=false, projectMemoryOrChatHistoryRead=false, findingOrVerdictProduced=false, startedAt, completedAt, opinions. opinions must contain exactly 9 entries and follow prompt-v3.txt exactly.\n`;
}

export async function createClaim({
  repoRoot: repoArg,
  isolatedDirectory,
  claimedAt,
  claimId
}) {
  const repoRoot = resolve(repoArg);
  const isolatedRoot = resolve(isolatedDirectory);
  assert.match(isolatedRoot.replaceAll("\\", "/"), /^C:\/tmp\//i);
  canonicalIso(claimedAt);
  const read = (relative) => readFile(fromRepo(repoRoot, relative));
  const modelInputBytes = await read(`${RUN_ROOT}/model-input.json`);
  const callSetBytes = await read(`${RUN_ROOT}/provider-call-set.json`);
  const dispatchBytes = await read(`${RUN_ROOT}/dispatch.json`);
  const humanBytes = await read(HUMAN_PATH);
  const promptBytes = await read(PROMPT_PATH);
  const readinessBytes = Buffer.from(READINESS_INSTRUCTION, "utf8");
  const evaluationBytes = Buffer.from(
    evaluationInstruction(isolatedRoot),
    "utf8"
  );
  const claim = buildCodexRevalidationClaim({
    claimId,
    claimedAt,
    isolatedDirectory: isolatedRoot,
    humanGroundTruthBytes: humanBytes,
    modelInputBytes,
    providerCallSetBytes: callSetBytes,
    dispatchBytes,
    promptBytes,
    readinessInstructionBytes: readinessBytes,
    evaluationInstructionBytes: evaluationBytes
  });
  const claimBytes = jsonBytes(claim);
  const evidenceRoot = fromRepo(repoRoot, CODEX_REVALIDATION_ROOT);
  await mkdir(evidenceRoot, { recursive: true });
  await mkdir(isolatedRoot, { recursive: false });
  for (const [path, bytes] of [
    [resolve(evidenceRoot, "readiness-instruction.txt"), readinessBytes],
    [resolve(evidenceRoot, "evaluation-instruction.txt"), evaluationBytes],
    [resolve(evidenceRoot, "execution-claim.json"), claimBytes],
    [resolve(isolatedRoot, "model-input.json"), modelInputBytes],
    [resolve(isolatedRoot, "prompt-v3.txt"), promptBytes],
    [resolve(isolatedRoot, "execution-claim.json"), claimBytes]
  ]) {
    await mkdir(dirname(path), { recursive: true });
    await writeFile(path, bytes, { flag: "wx" });
  }
  return {
    status: claim.status,
    claimId,
    claimSha256: codexRevalidationSha256(claimBytes),
    isolatedDirectory: isolatedRoot.replaceAll("\\", "/"),
    readinessInstructionPath: `${CODEX_REVALIDATION_ROOT}/readiness-instruction.txt`,
    readinessInstructionSha256: claim.readinessInstructionSha256,
    evaluationInstructionPath: `${CODEX_REVALIDATION_ROOT}/evaluation-instruction.txt`,
    evaluationInstructionSha256: claim.evaluationInstructionSha256
  };
}

export async function createLaunchReceipt({
  repoRoot: repoArg,
  agentId,
  canonicalTaskName,
  recordedAt
}) {
  const repoRoot = resolve(repoArg);
  const claimBytes = await readFile(
    fromRepo(repoRoot, `${CODEX_REVALIDATION_ROOT}/execution-claim.json`)
  );
  const readinessBytes = await readFile(
    fromRepo(repoRoot, `${CODEX_REVALIDATION_ROOT}/readiness-instruction.txt`)
  );
  const readinessResponseBytes = await readFile(
    fromRepo(repoRoot, `${CODEX_REVALIDATION_ROOT}/readiness-response.json`)
  );
  const claim = JSON.parse(claimBytes);
  const receipt = buildCodexRevalidationLaunchReceipt({
    claimBytes,
    agentId,
    canonicalTaskName,
    readinessInstructionSha256: codexRevalidationSha256(readinessBytes),
    readinessResponseBytes,
    recordedAt: canonicalIso(recordedAt)
  });
  const receiptBytes = jsonBytes(receipt);
  for (const path of [
    fromRepo(repoRoot, `${CODEX_REVALIDATION_ROOT}/launch-receipt.json`),
    resolve(claim.isolatedDirectory, "launch-receipt.json")
  ]) {
    await writeFile(path, receiptBytes, { flag: "wx" });
  }
  return {
    status: receipt.status,
    launchReceiptSha256: codexRevalidationSha256(receiptBytes),
    evaluationInstructionPath:
      `${CODEX_REVALIDATION_ROOT}/evaluation-instruction.txt`
  };
}

export async function createCompletionReceipt({ repoRoot: repoArg, receivedAt }) {
  const repoRoot = resolve(repoArg);
  const read = (relative) => readFile(fromRepo(repoRoot, relative));
  const modelInputBytes = await read(`${RUN_ROOT}/model-input.json`);
  const humanBytes = await read(HUMAN_PATH);
  const claimBytes = await read(
    `${CODEX_REVALIDATION_ROOT}/execution-claim.json`
  );
  const launchBytes = await read(
    `${CODEX_REVALIDATION_ROOT}/launch-receipt.json`
  );
  const opinionBytes = await read(`${CODEX_REVALIDATION_ROOT}/codex-opinion.json`);
  const receipt = buildCodexRevalidationCompletionReceipt({
    modelInputBytes,
    humanGroundTruthBytes: humanBytes,
    claimBytes,
    launchReceiptBytes: launchBytes,
    opinionBytes,
    receivedAt: canonicalIso(receivedAt)
  });
  const receiptBytes = jsonBytes(receipt);
  await writeFile(
    fromRepo(repoRoot, `${CODEX_REVALIDATION_ROOT}/completion-receipt.json`),
    receiptBytes,
    { flag: "wx" }
  );
  return {
    status: receipt.status,
    completionReceiptSha256: codexRevalidationSha256(receiptBytes),
    opinionSha256: receipt.opinionSha256
  };
}

if (
  process.argv[1] &&
  import.meta.url === pathToFileURL(resolve(process.argv[1])).href
) {
  const [mode, repoRoot = ".", ...args] = process.argv.slice(2);
  let result;
  if (mode === "claim") {
    result = await createClaim({
      repoRoot,
      isolatedDirectory: args[0],
      claimedAt: args[1],
      claimId: args[2]
    });
  } else if (mode === "launch") {
    result = await createLaunchReceipt({
      repoRoot,
      agentId: args[0],
      canonicalTaskName: args[1],
      recordedAt: args[2]
    });
  } else if (mode === "complete") {
    result = await createCompletionReceipt({ repoRoot, receivedAt: args[0] });
  } else {
    throw new Error("Usage: prepare-... claim|launch|complete <repo> ...");
  }
  process.stdout.write(`${JSON.stringify(result)}\n`);
}

export { READINESS_INSTRUCTION, evaluationInstruction };
