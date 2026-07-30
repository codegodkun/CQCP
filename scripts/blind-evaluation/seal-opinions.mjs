import { access, writeFile } from "node:fs/promises";
import { resolve } from "node:path";

import {
  validateDispatch,
  validateOpinionDocument
} from "./blind-evidence-chain.mjs";

const repoRoot = resolve(process.argv[2] ?? ".");
const sealedAt = process.argv[3];
if (!sealedAt || Number.isNaN(Date.parse(sealedAt))) {
  throw new Error("A valid ISO seal timestamp is required");
}
const outputRoot = resolve(repoRoot, "outputs/task-eval-002");
try {
  await access(resolve(outputRoot, "unblind-report.json"));
  throw new Error("Remove the old unblind report before sealing opinions");
} catch (error) {
  if (error?.code !== "ENOENT") throw error;
}

const dispatchRecord = await validateDispatch(repoRoot);
const opinions = [];
for (const assignment of dispatchRecord.dispatch.assignments) {
  const record = await validateOpinionDocument(
    repoRoot,
    dispatchRecord,
    assignment
  );
  opinions.push({
    sampleId: assignment.sampleId,
    evaluator: record.document.evaluator,
    agentTaskName: record.document.agentTaskName,
    agentId: record.document.agentId,
    startedAt: record.document.startedAt,
    completedAt: record.document.completedAt,
    path: assignment.opinionOutputPath,
    sha256: record.sha256,
    inputSha256: assignment.blindInputSha256
  });
}
if (
  opinions.some(
    (opinion) => Date.parse(sealedAt) < Date.parse(opinion.completedAt)
  )
) {
  throw new Error("Seal timestamp precedes an opinion completion");
}

const seal = {
  schemaVersion: "task-eval-002-opinion-seal-v1",
  status: "SEALED_BEFORE_UNBLIND",
  sealedAt: new Date(sealedAt).toISOString(),
  dispatchPath: "outputs/task-eval-002/blind-dispatch.json",
  dispatchSha256: dispatchRecord.sha256,
  promptPath: dispatchRecord.dispatch.promptPath,
  promptSha256: dispatchRecord.dispatch.promptSha256,
  unblindReportPresentAtSeal: false,
  opinions
};
await writeFile(
  resolve(outputRoot, "opinion-seal.json"),
  `${JSON.stringify(seal, null, 2)}\n`,
  "utf8"
);
process.stdout.write(
  `${JSON.stringify({
    status: seal.status,
    opinionCount: opinions.length,
    dispatchSha256: seal.dispatchSha256
  })}\n`
);
