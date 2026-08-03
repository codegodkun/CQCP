import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { resolve } from "node:path";
import test from "node:test";

import {
  buildTrackBSchemaDiagnosticCalls,
  buildTrackBSchemaDiagnosticInput,
  classifyTrackBSchemaDiagnosticEnvelope,
  collectTrackBSchemaDiagnosticDisjointness,
  TRACK_B_SCHEMA_DIAGNOSTIC_MAX_CALLS
} from "./track-b-schema-diagnostic-contract.mjs";

const repoRoot = resolve(process.cwd());

test("diagnostic input is 12 synthetic packets disjoint from all four corpora", async () => {
  const template = await json(
    "outputs/task-eval-004/track-b-final-v1/run-v1/model-input.json"
  );
  const input = buildTrackBSchemaDiagnosticInput(template);
  const prior = await Promise.all([
    "apps/api-server/src/test/resources/track-b-admission-corpus-v2/corpus.json",
    "outputs/task-eval-002/track-b-holdout-v1/corpus.json",
    "outputs/task-eval-003/track-b-successor-v1/corpus.json",
    "outputs/task-eval-004/track-b-final-v1/corpus.json"
  ].map(json));
  assert.equal(input.packetCount, 12);
  assert.equal(input.formalAdmissionAffected, false);
  assert.deepEqual(collectTrackBSchemaDiagnosticDisjointness(input, prior), {
    priorCorpusCount: 4,
    identityOverlapCount: 0,
    candidateValueOverlapCount: 0,
    evidenceTextOverlapCount: 0
  });
});

test("request builder emits 12 one-packet strict JSON calls without ground truth", async () => {
  const template = await json(
    "outputs/task-eval-004/track-b-final-v1/run-v1/model-input.json"
  );
  const input = buildTrackBSchemaDiagnosticInput(template);
  const prompt = await readFile(resolve(
    repoRoot,
    "scripts/blind-evaluation/track-b-holdout-opinion-prompt.txt"
  ));
  const calls = buildTrackBSchemaDiagnosticCalls(input, prompt);
  assert.equal(calls.length, 12);
  assert.equal(new Set(calls.map((item) => item.packetId)).size, 12);
  for (const call of calls) {
    assert.equal(call.input.packetCount, 1);
    assert.equal(call.outboundRequestBytes.includes(Buffer.from("reasoning_content")), false);
    assert.equal(call.outboundRequestBytes.includes(Buffer.from("human-ground-truth")), false);
    const body = JSON.parse(call.outboundRequestBytes.toString("utf8"));
    assert.equal(body.model, "deepseek-v4-pro");
    assert.deepEqual(body.thinking, { type: "disabled" });
    assert.deepEqual(body.response_format, { type: "json_object" });
    assert.equal(body.stream, false);
    assert.equal(body.max_tokens, 1500);
  }
  assert.equal(TRACK_B_SCHEMA_DIAGNOSTIC_MAX_CALLS, 60);
});

test("validator classifies strict output dimensions without returning content", async () => {
  const template = await json(
    "outputs/task-eval-004/track-b-final-v1/run-v1/model-input.json"
  );
  const input = buildTrackBSchemaDiagnosticInput(template);
  const packet = input.packets[0];
  const providerInput = {
    schemaVersion: "task-eval-005-track-b-schema-diagnostic-provider-input-v1",
    track: input.track,
    purpose: input.purpose,
    packetCount: 1,
    packets: [packet]
  };
  const valid = envelope({
    opinions: [{
      packetId: packet.packetId,
      suggestedRole: packet.candidateRole,
      selectedOccurrenceIds: [packet.candidateOccurrences[0].occurrenceId],
      selectedAnchorBlockIds: [
        packet.candidateOccurrences[0].sourceAnchor.blockId
      ],
      abstain: false,
      abstentionReason: null
    }]
  });
  assert.equal(
    classifyTrackBSchemaDiagnosticEnvelope(providerInput, valid),
    "ACCEPTED"
  );
  assert.equal(
    classifyTrackBSchemaDiagnosticEnvelope(providerInput, envelope({ extra: [] })),
    "ROOT_FIELDS_INVALID"
  );
  const wrongPacket = structuredClone(valid);
  wrongPacket.choices[0].message.content = JSON.stringify({
    opinions: [{
      ...JSON.parse(valid.choices[0].message.content).opinions[0],
      packetId: "EP-wrong"
    }]
  });
  assert.equal(
    classifyTrackBSchemaDiagnosticEnvelope(providerInput, wrongPacket),
    "PACKET_COVERAGE_INVALID"
  );
  const badAnchor = structuredClone(valid);
  badAnchor.choices[0].message.content = JSON.stringify({
    opinions: [{
      ...JSON.parse(valid.choices[0].message.content).opinions[0],
      selectedAnchorBlockIds: ["wrong-block"]
    }]
  });
  assert.equal(
    classifyTrackBSchemaDiagnosticEnvelope(providerInput, badAnchor),
    "OCCURRENCE_OR_ANCHOR_INVALID"
  );
});

function envelope(payload) {
  return {
    id: "diag-test",
    created: 1,
    model: "deepseek-v4-pro",
    choices: [{
      index: 0,
      finish_reason: "stop",
      message: { role: "assistant", content: JSON.stringify(payload) }
    }]
  };
}

async function json(relativePath) {
  return JSON.parse((await readFile(resolve(
    repoRoot,
    ...relativePath.split("/")
  ))).toString("utf8"));
}
