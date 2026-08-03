import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { resolve } from "node:path";
import test from "node:test";

import { sha256 } from "./track-b-holdout-contract.mjs";
import {
  TRACK_B_FIFTH_MODEL_INPUT_SCHEMA,
  validateTrackBFifthCorpusForModel,
  validateTrackBFifthDeepSeekEnvelope,
  validateTrackBFifthModelInput
} from "./track-b-holdout-opinion-contract.mjs";
import {
  buildTrackBFifthProviderCalls,
  TRACK_B_FIFTH_TRUSTED_PROMPT_SHA256
} from "./track-b-holdout-provider-request-contract.mjs";

test("fifth v2 builds exact 9x1 calls and accepts strict opinion", async () => {
  const corpusBytes = await readFile(resolve(
    process.cwd(),
    "outputs/task-eval-005/track-b-fifth-v1/corpus.json"
  ));
  const corpus = JSON.parse(corpusBytes.toString("utf8"));
  const { eligiblePackets, zeroCallPackets } =
    validateTrackBFifthCorpusForModel(corpus, sha256(corpusBytes));
  const input = validateTrackBFifthModelInput({
    schemaVersion: TRACK_B_FIFTH_MODEL_INPUT_SCHEMA,
    track: "TRACK_B_FIFTH_RUNTIME_ISOMORPHIC_ROLE_CANDIDATE_ANCHOR_ABSTENTION",
    sourceCorpusSha256: sha256(corpusBytes),
    groundTruthIncluded: false,
    packetCount: eligiblePackets.length,
    packets: eligiblePackets
  });
  const prompt = await readFile(resolve(
    process.cwd(),
    "scripts/blind-evaluation/track-b-schema-diagnostic-prompt-v2.txt"
  ));
  assert.equal(sha256(prompt), TRACK_B_FIFTH_TRUSTED_PROMPT_SHA256);
  const calls = buildTrackBFifthProviderCalls(input, prompt);
  assert.equal(calls.length, 9);
  assert.equal(zeroCallPackets.length, 3);
  assert.ok(calls.every((call) => call.input.packetCount === 1));
  const packet = calls[0].input.packets[0];
  const occurrence = packet.candidateOccurrences[0];
  assert.equal(validateTrackBFifthDeepSeekEnvelope(calls[0].input, {
    model: "deepseek-v4-pro",
    choices: [{
      finish_reason: "stop",
      message: { content: JSON.stringify({
        opinions: [{
          packetId: packet.packetId,
          suggestedRole: packet.candidateRole,
          selectedOccurrenceIds: [occurrence.occurrenceId],
          selectedAnchorBlockIds: [occurrence.sourceAnchor.blockId],
          abstain: false,
          abstentionReason: null
        }]
      }) }
    }]
  }).opinions.length, 1);
});
