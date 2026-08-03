import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

import {
  TRACK_B_HOLDOUT_COUNTS,
  buildTrackBHoldoutArtifacts,
  jsonBytes,
  sha256,
  validateTrackBHoldoutCorpus
} from "./track-b-holdout-contract.mjs";

const repoRoot = resolve(
  dirname(fileURLToPath(import.meta.url)),
  "../.."
);
const sourcePath =
  "apps/api-server/src/test/resources/track-b-holdout-v1/" +
  "source-signals.json";
const proposalsPath =
  "apps/api-server/src/test/resources/track-b-holdout-v1/" +
  "proposed-decisions.json";

test("builds a deterministic 9 eligible plus 3 zero-call holdout", async () => {
  const sourceBytes = await readFile(resolve(repoRoot, sourcePath));
  const proposalsBytes = await readFile(resolve(repoRoot, proposalsPath));
  const input = {
    source: JSON.parse(sourceBytes.toString("utf8")),
    sourcePath,
    sourceSha256: sha256(sourceBytes),
    proposals: JSON.parse(proposalsBytes.toString("utf8")),
    proposalsPath,
    proposalsSha256: sha256(proposalsBytes),
    generatedAt: "2026-08-02T13:00:00.000Z"
  };
  const first = buildTrackBHoldoutArtifacts(input);
  const second = buildTrackBHoldoutArtifacts(input);
  assert.deepEqual(first, second);
  validateTrackBHoldoutCorpus(first.corpus);
  assert.equal(first.corpus.packetCount, TRACK_B_HOLDOUT_COUNTS.total);
  assert.equal(
    first.corpus.eligiblePacketCount,
    TRACK_B_HOLDOUT_COUNTS.eligible
  );
  assert.equal(first.draft.entries.length, TRACK_B_HOLDOUT_COUNTS.total);
  assert.ok(
    first.draft.entries.every(
      (entry) => entry.humanDecision === null
    )
  );
  assert.equal(first.draft.humanGroundTruthEstablished, false);
  const corpusText = jsonBytes(first.corpus).toString("utf8");
  for (const forbidden of [
    '"proposedExpected"',
    '"humanDecision"',
    '"expected"',
    '"actual"',
    '"finding"',
    '"verdict"'
  ]) {
    assert.ok(!corpusText.toLowerCase().includes(forbidden.toLowerCase()));
  }
  assert.match(first.reviewMarkdown, /当前不是 ground truth/);
  assert.match(first.reviewMarkdown, /模型调用/);
});

test("keeps the unrelated conflict block out of requiredBlockIds", async () => {
  const sourceBytes = await readFile(resolve(repoRoot, sourcePath));
  const proposalsBytes = await readFile(resolve(repoRoot, proposalsPath));
  const { corpus } = buildTrackBHoldoutArtifacts({
    source: JSON.parse(sourceBytes.toString("utf8")),
    sourcePath,
    sourceSha256: sha256(sourceBytes),
    proposals: JSON.parse(proposalsBytes.toString("utf8")),
    proposalsPath,
    proposalsSha256: sha256(proposalsBytes),
    generatedAt: "2026-08-02T13:00:00.000Z"
  });
  const packet = corpus.packets.find(
    (candidate) => candidate.sampleId === "TBH-CON-003"
  );
  assert.deepEqual(packet.admission.requiredBlockIds, [
    "tbh-con-003-b01",
    "tbh-con-003-b02"
  ]);
  assert.ok(
    packet.candidateOccurrences.some(
      (candidate) =>
        candidate.sourceAnchor.blockId === "tbh-con-003-b03"
    )
  );
});
