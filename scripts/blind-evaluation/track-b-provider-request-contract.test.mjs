import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { readFile } from "node:fs/promises";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

import {
  validateTrackBAdmissionCorpus,
  validateTrackBProviderModelInput
} from "./track-b-admission-opinion-contract.mjs";
import {
  TRACK_B_PROVIDER_CALL_COUNT,
  TRACK_B_PROVIDER_MAX_CALLS_PER_TASK,
  TRACK_B_PROVIDER_MAX_OUTPUT_TOKENS,
  TRACK_B_PROVIDER_MODEL,
  buildTrackBProviderCalls
} from "./track-b-provider-request-contract.mjs";

const scriptRoot = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptRoot, "../..");

const loadFixture = async () => {
  const corpusBytes = await readFile(
    path.join(
      repoRoot,
      "outputs/task-eval-002/track-b-admission-corpus-v2/corpus.json"
    )
  );
  const corpus = JSON.parse(corpusBytes.toString("utf8"));
  const corpusSha256 = createHash("sha256")
    .update(corpusBytes)
    .digest("hex");
  const { eligiblePackets } = validateTrackBAdmissionCorpus(
    corpus,
    corpusSha256
  );
  const input = {
    schemaVersion: "task-eval-002-track-b-admission-model-input-v1",
    track:
      "TRACK_B_RUNTIME_ISOMORPHIC_ROLE_CANDIDATE_ANCHOR_ABSTENTION",
    sourceCorpusSha256: corpusSha256,
    groundTruthIncluded: false,
    packetCount: eligiblePackets.length,
    packets: eligiblePackets
  };
  const promptBytes = await readFile(
    path.join(
      repoRoot,
      "scripts/blind-evaluation/track-b-admission-opinion-prompt.txt"
    )
  );
  return { input, promptBytes };
};

test("six admission calls use the exact future runtime request shape", async () => {
  const { input, promptBytes } = await loadFixture();
  const calls = buildTrackBProviderCalls(input, promptBytes);
  assert.equal(calls.length, TRACK_B_PROVIDER_CALL_COUNT);
  assert.equal(
    calls.reduce((total, call) => total + call.input.packetCount, 0),
    15
  );
  const callsPerTask = Map.groupBy(
    calls,
    (call) => `${call.input.taskId}\0${call.input.executionId}`
  );
  assert.equal(callsPerTask.size, 2);
  for (const taskCalls of callsPerTask.values()) {
    assert.equal(taskCalls.length, 3);
    assert.ok(
      taskCalls.length <= TRACK_B_PROVIDER_MAX_CALLS_PER_TASK
    );
    assert.equal(
      new Set(taskCalls.map((call) => call.input.family)).size,
      taskCalls.length
    );
  }
  assert.equal(
    new Set(
      calls.flatMap((call) =>
        call.input.packets.map((packet) => packet.packetId)
      )
    ).size,
    15
  );
  for (const call of calls) {
    assert.equal(validateTrackBProviderModelInput(call.input), call.input);
    assert.equal(
      new Set(call.input.requestedRoles).size,
      call.input.requestedRoles.length
    );
    assert.deepEqual(Object.keys(call.outboundRequest), [
      "model",
      "thinking",
      "messages",
      "response_format",
      "stream",
      "max_tokens"
    ]);
    assert.equal(call.outboundRequest.model, TRACK_B_PROVIDER_MODEL);
    assert.deepEqual(call.outboundRequest.thinking, {
      type: "disabled"
    });
    assert.equal(call.outboundRequest.messages.length, 2);
    assert.deepEqual(
      call.outboundRequest.messages.map((message) => message.role),
      ["system", "user"]
    );
    assert.equal(
      call.outboundRequest.messages[0].content,
      promptBytes.toString("utf8")
    );
    assert.equal(
      call.outboundRequest.messages[1].content,
      call.modelInputBytes.toString("utf8")
    );
    assert.deepEqual(call.outboundRequest.response_format, {
      type: "json_object"
    });
    assert.equal(call.outboundRequest.stream, false);
    assert.equal(
      call.outboundRequest.max_tokens,
      TRACK_B_PROVIDER_MAX_OUTPUT_TOKENS
    );
    assert.equal(TRACK_B_PROVIDER_MAX_OUTPUT_TOKENS, 1500);
    assert.equal(
      call.outboundRequestBytes.toString("utf8"),
      JSON.stringify(call.outboundRequest)
    );
  }
});

test("request builder rejects prompt drift and duplicate runtime roles", async () => {
  const { input, promptBytes } = await loadFixture();
  assert.throws(
    () =>
      buildTrackBProviderCalls(
        input,
        Buffer.from(`${promptBytes.toString("utf8")}tampered`, "utf8")
      ),
    /TRACK_B_PROMPT_HASH_MISMATCH/
  );

  const calls = buildTrackBProviderCalls(input, promptBytes);
  const invalid = structuredClone(calls[0].input);
  invalid.requestedRoles[1] = invalid.requestedRoles[0];
  invalid.packets[1].candidateRole = invalid.packets[0].candidateRole;
  assert.throws(
    () => validateTrackBProviderModelInput(invalid),
    /identity is invalid/
  );
});

test("runtime validator accepts non-evaluation task identities", async () => {
  const { input, promptBytes } = await loadFixture();
  const runtimeInput = structuredClone(
    buildTrackBProviderCalls(input, promptBytes)[0].input
  );
  runtimeInput.taskId = "task-runtime-123";
  runtimeInput.executionId = "execution-runtime-456";
  runtimeInput.packets = runtimeInput.packets.map((packet, index) => ({
    ...packet,
    taskId: runtimeInput.taskId,
    executionId: runtimeInput.executionId,
    sampleId: `runtime-sample-${index + 1}`,
    ruleSetVersion: "ruleset-runtime-v1"
  }));
  assert.equal(
    validateTrackBProviderModelInput(runtimeInput),
    runtimeInput
  );
});
