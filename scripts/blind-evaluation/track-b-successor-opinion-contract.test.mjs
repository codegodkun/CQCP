import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

import {
  TRACK_B_SUCCESSOR_TRACK
} from "./track-b-holdout-constants.mjs";
import {
  jsonBytes,
  sha256
} from "./track-b-holdout-contract.mjs";
import {
  TRACK_B_SUCCESSOR_MODEL_INPUT_SCHEMA,
  validateTrackBHoldoutModelInput,
  validateTrackBSuccessorCorpusForModel,
  validateTrackBSuccessorDeepSeekEnvelope,
  validateTrackBSuccessorModelInput,
  validateTrackBSuccessorOpinionPayload
} from "./track-b-holdout-opinion-contract.mjs";
import {
  buildTrackBSuccessorProviderCalls,
  buildTrackBSuccessorProviderCallSetArtifact
} from "./track-b-holdout-provider-request-contract.mjs";

const corpusBytes = await readFile(
  new URL(
    "../../outputs/task-eval-003/track-b-successor-v1/corpus.json",
    import.meta.url
  )
);
const corpus = JSON.parse(corpusBytes.toString("utf8"));
const promptBytes = await readFile(
  new URL("./track-b-holdout-opinion-prompt.txt", import.meta.url)
);

function buildModelInput() {
  const { eligiblePackets, zeroCallPackets } =
    validateTrackBSuccessorCorpusForModel(corpus, sha256(corpusBytes));
  assert.equal(zeroCallPackets.length, 3);
  return validateTrackBSuccessorModelInput({
    schemaVersion: TRACK_B_SUCCESSOR_MODEL_INPUT_SCHEMA,
    track: TRACK_B_SUCCESSOR_TRACK,
    sourceCorpusSha256: sha256(corpusBytes),
    groundTruthIncluded: false,
    packetCount: eligiblePackets.length,
    packets: eligiblePackets
  });
}

function acceptedPayload(input) {
  return {
    opinions: input.packets.map((packet) => {
      const occurrence = packet.candidateOccurrences[0];
      return {
        packetId: packet.packetId,
        suggestedRole: packet.candidateRole,
        selectedOccurrenceIds: [occurrence.occurrenceId],
        selectedAnchorBlockIds: [occurrence.sourceAnchor.blockId],
        abstain: false,
        abstentionReason: null
      };
    })
  };
}

test("successor opinion contract admits only the successor identity", () => {
  const input = buildModelInput();
  assert.equal(input.packetCount, 9);
  assert.ok(input.packets.every((packet) => packet.sampleId.startsWith("TBS-")));
  assert.throws(
    () => validateTrackBHoldoutModelInput(input),
    /MODEL_INPUT_IDENTITY_INVALID/
  );
  assert.deepEqual(
    validateTrackBSuccessorOpinionPayload(input, acceptedPayload(input)),
    acceptedPayload(input)
  );
});

test("successor provider plan is nine immutable single-packet calls", () => {
  const input = buildModelInput();
  const calls = buildTrackBSuccessorProviderCalls(input, promptBytes);
  assert.equal(calls.length, 9);
  assert.ok(calls.every((call) => call.callId.startsWith("TBS-MC-")));
  assert.ok(calls.every((call) => call.input.packetCount === 1));
  assert.equal(
    new Set(calls.map((call) => call.input.packets[0].packetId)).size,
    9
  );
  const callSet = buildTrackBSuccessorProviderCallSetArtifact(
    input,
    promptBytes
  );
  assert.equal(
    callSet.schemaVersion,
    "task-eval-003-track-b-successor-provider-call-set-v1"
  );
  assert.equal(callSet.callCount, 9);
  assert.equal(callSet.masterModelInputSha256, sha256(jsonBytes(input)));
});

test("successor DeepSeek envelope remains strict and fail closed", () => {
  const input = buildModelInput();
  const payload = acceptedPayload(input);
  assert.deepEqual(
    validateTrackBSuccessorDeepSeekEnvelope(input, {
      choices: [
        {
          finish_reason: "stop",
          message: { content: JSON.stringify(payload) }
        }
      ]
    }),
    payload
  );
  assert.throws(
    () =>
      validateTrackBSuccessorDeepSeekEnvelope(input, {
        choices: [
          {
            finish_reason: "length",
            message: { content: JSON.stringify(payload) }
          }
        ]
      }),
    (error) => error.admissionCode === "FINISH_REASON_NOT_STOP"
  );
  assert.throws(
    () =>
      validateTrackBSuccessorOpinionPayload(input, {
        ...payload,
        verdict: "PASS"
      }),
    /fields invalid/
  );
});
