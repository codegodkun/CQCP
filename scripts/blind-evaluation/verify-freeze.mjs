import { readFile } from "node:fs/promises";
import { resolve } from "node:path";

import { sha256File } from "./opinion-contract.mjs";
import { validateSealedChain } from "./blind-evidence-chain.mjs";

const repoRoot = resolve(process.argv[2] ?? ".");
const outputRoot = resolve(repoRoot, "outputs/task-eval-002");
const manifest = JSON.parse(
  await readFile(resolve(outputRoot, "freeze-manifest.json"), "utf8")
);
const forbiddenKeys = [
  "expected",
  "actual",
  "groundtruth",
  "humananchor",
  "blockid",
  "previewelementref",
  "pointstatus",
  "comparisonresult"
];

for (const sample of manifest.samples) {
  const inputPath = resolve(repoRoot, sample.blindInputPath);
  const actualHash = await sha256File(inputPath);
  if (actualHash !== sample.blindInputSha256) {
    throw new Error(`Hash mismatch: ${sample.sampleId}`);
  }
  const input = JSON.parse(await readFile(inputPath, "utf8"));
  scan(input, "$");
  const serialized = JSON.stringify(input);
  if (/(block-[0-9]+|table:[^\s"']+)/i.test(serialized)) {
    throw new Error(`Runtime identity leaked: ${sample.sampleId}`);
  }
}
const sealedChain = await validateSealedChain(repoRoot);

process.stdout.write(
  `${JSON.stringify({
    status: "PASS",
    sampleCount: manifest.samples.length,
    externalEgressAuthorized: manifest.externalEgressAuthorized,
    dispatchSha256: sealedChain.dispatchRecord.sha256,
    opinionSealSha256: sealedChain.sealSha256
  })}\n`
);

function scan(value, path) {
  if (Array.isArray(value)) {
    value.forEach((item, index) => scan(item, `${path}[${index}]`));
    return;
  }
  if (value === null || typeof value !== "object") return;
  for (const [key, child] of Object.entries(value)) {
    const normalized = key.toLowerCase().replaceAll("_", "");
    if (forbiddenKeys.some((token) => normalized.includes(token))) {
      throw new Error(`Forbidden blind key at ${path}.${key}`);
    }
    scan(child, `${path}.${key}`);
  }
}
