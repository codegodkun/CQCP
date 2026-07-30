import { readFile, writeFile } from "node:fs/promises";
import { resolve } from "node:path";

import { sha256File } from "./opinion-contract.mjs";

const repoRoot = resolve(process.argv[2] ?? ".");
const createdAt = process.argv[3];
if (!createdAt || Number.isNaN(Date.parse(createdAt))) {
  throw new Error("A valid ISO dispatch timestamp is required");
}

const outputRoot = resolve(repoRoot, "outputs/task-eval-002");
const manifest = JSON.parse(
  await readFile(resolve(outputRoot, "freeze-manifest.json"), "utf8")
);
const promptPath = "scripts/blind-evaluation/blind-opinion-prompt.txt";
const promptSha256 = await sha256File(resolve(repoRoot, promptPath));
const assignments = [];

for (const [index, sample] of manifest.samples.entries()) {
  const blindInputSha256 = await sha256File(
    resolve(repoRoot, sample.blindInputPath)
  );
  if (blindInputSha256 !== sample.blindInputSha256) {
    throw new Error(`Blind input hash mismatch: ${sample.sampleId}`);
  }
  const suffix = String(index + 1).padStart(3, "0");
  assignments.push({
    sampleId: sample.sampleId,
    evaluator: `codex-blind-agent-r2-${suffix}`,
    agentTaskName: `/root/mvp002_blind_r2_${suffix}`,
    blindInputPath: sample.blindInputPath,
    blindInputSha256,
    opinionOutputPath:
      `outputs/task-eval-002/codex-opinions/${sample.sampleId}.codex.json`
  });
}

const dispatch = {
  schemaVersion: "task-eval-002-blind-dispatch-v1",
  status: "FROZEN_BEFORE_AGENT_DISPATCH",
  createdAt: new Date(createdAt).toISOString(),
  protocol: "TRACK_A_FULL_DOCUMENT_SEMANTIC_OPINION",
  forkTurns: "none",
  historicalContextIncluded: false,
  promptPath,
  promptSha256,
  unblindProhibitedUntilOpinionSeal: true,
  assignments
};
const serialized = `${JSON.stringify(dispatch, null, 2)}\n`;
if (/(ground.?truth|human.?anchor|cqcp.?actual|task-034|unblind-report)/i.test(serialized)) {
  throw new Error("Dispatch leaks prohibited unblinded references");
}
await writeFile(resolve(outputRoot, "blind-dispatch.json"), serialized, "utf8");
process.stdout.write(
  `${JSON.stringify({
    status: dispatch.status,
    assignmentCount: assignments.length,
    promptSha256
  })}\n`
);
