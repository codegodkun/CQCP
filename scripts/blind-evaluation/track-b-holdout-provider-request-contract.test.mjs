import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { readFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

import {
  TRACK_B_HOLDOUT_MODEL,
  TRACK_B_HOLDOUT_TRACK
} from "./track-b-holdout-constants.mjs";
import {
  TRACK_B_HOLDOUT_MODEL_INPUT_SCHEMA,
  validateTrackBHoldoutCorpusForModel
} from "./track-b-holdout-opinion-contract.mjs";
import {
  TRACK_B_HOLDOUT_PROVIDER_CALL_COUNT,
  TRACK_B_HOLDOUT_PROVIDER_MAX_OUTPUT_TOKENS,
  buildTrackBHoldoutProviderCalls,
  buildTrackBHoldoutProviderCallSetArtifact
} from "./track-b-holdout-provider-request-contract.mjs";

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const corpusPath = resolve(
  repoRoot,
  "outputs/task-eval-002/track-b-holdout-v1/corpus.json"
);
const promptPath = resolve(
  repoRoot,
  "scripts/blind-evaluation/track-b-holdout-opinion-prompt.txt"
);
const sha256 = (bytes) =>
  createHash("sha256").update(bytes).digest("hex");

async function loadFixture() {
  const corpusBytes = await readFile(corpusPath);
  const corpus = JSON.parse(corpusBytes.toString("utf8"));
  const { eligiblePackets } = validateTrackBHoldoutCorpusForModel(
    corpus,
    sha256(corpusBytes)
  );
  return {
    input: {
      schemaVersion: TRACK_B_HOLDOUT_MODEL_INPUT_SCHEMA,
      track: TRACK_B_HOLDOUT_TRACK,
      sourceCorpusSha256: sha256(corpusBytes),
      groundTruthIncluded: false,
      packetCount: eligiblePackets.length,
      packets: eligiblePackets
    },
    promptBytes: await readFile(promptPath)
  };
}

test("builds nine strict non-streaming single-packet calls", async () => {
  const { input, promptBytes } = await loadFixture();
  const calls = buildTrackBHoldoutProviderCalls(input, promptBytes);
  assert.equal(calls.length, TRACK_B_HOLDOUT_PROVIDER_CALL_COUNT);
  assert.equal(TRACK_B_HOLDOUT_PROVIDER_CALL_COUNT, 9);
  assert.equal(
    new Set(calls.map((call) => call.input.packets[0].packetId)).size,
    9
  );
  const counts = new Map();
  for (const call of calls) {
    const identity = `${call.input.taskId}\0${call.input.executionId}`;
    counts.set(identity, (counts.get(identity) ?? 0) + 1);
    assert.equal(call.input.packetCount, 1);
    assert.equal(call.input.packets.length, 1);
    assert.equal(call.input.requestedRoles.length, 1);
    assert.equal(
      call.input.requestedRoles[0],
      call.input.packets[0].candidateRole
    );
    assert.deepEqual(Object.keys(call.outboundRequest), [
      "model",
      "thinking",
      "messages",
      "response_format",
      "stream",
      "max_tokens"
    ]);
    assert.equal(call.outboundRequest.model, TRACK_B_HOLDOUT_MODEL);
    assert.deepEqual(call.outboundRequest.thinking, {
      type: "disabled"
    });
    assert.deepEqual(call.outboundRequest.response_format, {
      type: "json_object"
    });
    assert.equal(call.outboundRequest.stream, false);
    assert.equal(
      call.outboundRequest.max_tokens,
      TRACK_B_HOLDOUT_PROVIDER_MAX_OUTPUT_TOKENS
    );
    assert.equal(
      call.outboundRequest.messages[1].content,
      call.modelInputBytes.toString("utf8")
    );
    const serialized = call.modelInputBytes
      .toString("utf8")
      .toLowerCase();
    for (const forbidden of [
      "humandecision",
      "proposedexpected",
      "cqcpactual",
      "cqcpexpected",
      "finding",
      "verdict"
    ]) {
      assert.ok(!serialized.includes(forbidden));
    }
  }
  assert.deepEqual([...counts.values()].sort((a, b) => a - b), [4, 5]);
});

test("call-set artifact is deterministic and binds every request hash", async () => {
  const { input, promptBytes } = await loadFixture();
  const first = buildTrackBHoldoutProviderCallSetArtifact(
    input,
    promptBytes
  );
  const second = buildTrackBHoldoutProviderCallSetArtifact(
    structuredClone(input),
    Buffer.from(promptBytes)
  );
  assert.deepEqual(first, second);
  assert.equal(first.callCount, 9);
  assert.equal(first.eligiblePacketCount, 9);
  assert.equal(new Set(first.calls.map((call) => call.callId)).size, 9);
  assert.ok(
    first.calls.every(
      (call) =>
        /^[a-f0-9]{64}$/.test(call.modelInputSha256) &&
        /^[a-f0-9]{64}$/.test(call.outboundRequestSha256)
    )
  );
});

test("rejects prompt drift before constructing any request", async () => {
  const { input, promptBytes } = await loadFixture();
  assert.throws(
    () =>
      buildTrackBHoldoutProviderCalls(
        input,
        Buffer.concat([promptBytes, Buffer.from("tampered")])
      ),
    /TRACK_B_HOLDOUT_PROMPT_HASH_MISMATCH/
  );
});
