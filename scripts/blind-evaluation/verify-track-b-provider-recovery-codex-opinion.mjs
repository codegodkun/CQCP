import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { readFile } from "node:fs/promises";
import { resolve } from "node:path";

import { parseJsonBytesRejectDuplicateKeys } from "./strict-json.mjs";
import {
  validateTrackBProviderRecoveryOpinionPayload
} from "./track-b-provider-recovery-opinion-contract.mjs";

const repoRoot = resolve(process.argv[2] ?? ".");
const root = resolve(
  repoRoot,
  "outputs/task-eval-006/track-b-recovery-v1/run-v1"
);
const inputBytes = await readFile(resolve(root, "model-input.json"));
const callSetBytes = await readFile(resolve(root, "provider-call-set.json"));
const dispatchBytes = await readFile(resolve(root, "dispatch.json"));
const opinionBytes = await readFile(resolve(root, "codex-opinion.json"));
const input = parseJsonBytesRejectDuplicateKeys(inputBytes);
const opinion = parseJsonBytesRejectDuplicateKeys(opinionBytes);
const sha256 = (bytes) =>
  createHash("sha256").update(bytes).digest("hex");

assert.equal(
  opinion.schemaVersion,
  "task-eval-006-track-b-recovery-codex-opinion-v3"
);
assert.equal(opinion.status, "ACCEPTED_BLIND_FORMAL_ADMISSION_OPINION");
assert.equal(opinion.evaluator, "codex-subagent-gpt-5.6-sol-xhigh");
assert.equal(opinion.modelInputSha256, sha256(inputBytes));
assert.equal(opinion.providerCallSetSha256, sha256(callSetBytes));
assert.equal(opinion.dispatchSha256, sha256(dispatchBytes));
assert.equal(opinion.blindInputOnly, true);
assert.equal(opinion.humanGroundTruthRead, false);
assert.equal(opinion.findingOrVerdictProduced, false);
assert.equal(opinion.opinions.length, input.packetCount);
const byPacket = new Map(
  opinion.opinions.map((entry) => [entry.packetId, entry])
);
assert.equal(byPacket.size, input.packetCount);
for (const packet of input.packets) {
  validateTrackBProviderRecoveryOpinionPayload(packet, {
    opinions: [byPacket.get(packet.packetId)]
  });
}
process.stdout.write(`${JSON.stringify({
  status: "CODEX_BLIND_OPINION_VERIFIED",
  opinionCount: opinion.opinions.length,
  modelInputSha256: sha256(inputBytes),
  opinionSha256: sha256(opinionBytes)
})}\n`);
