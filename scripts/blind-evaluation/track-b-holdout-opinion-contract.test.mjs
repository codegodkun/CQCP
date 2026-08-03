import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { readFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

import {
  TRACK_B_HOLDOUT_MODEL_INPUT_SCHEMA,
  validateTrackBHoldoutCorpusForModel,
  validateTrackBHoldoutDeepSeekEnvelope,
  validateTrackBHoldoutModelInput,
  validateTrackBHoldoutOpinionPayload,
  validateTrackBHoldoutProviderInput
} from "./track-b-holdout-opinion-contract.mjs";
import { TRACK_B_HOLDOUT_TRACK } from "./track-b-holdout-constants.mjs";

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const corpusPath = resolve(
  repoRoot,
  "outputs/task-eval-002/track-b-holdout-v1/corpus.json"
);

const sha256 = (bytes) =>
  createHash("sha256").update(bytes).digest("hex");

async function loadFixture() {
  const corpusBytes = await readFile(corpusPath);
  const corpus = JSON.parse(corpusBytes.toString("utf8"));
  const corpusSha256 = sha256(corpusBytes);
  const { eligiblePackets, zeroCallPackets } =
    validateTrackBHoldoutCorpusForModel(corpus, corpusSha256);
  const input = validateTrackBHoldoutModelInput({
    schemaVersion: TRACK_B_HOLDOUT_MODEL_INPUT_SCHEMA,
    track: TRACK_B_HOLDOUT_TRACK,
    sourceCorpusSha256: corpusSha256,
    groundTruthIncluded: false,
    packetCount: eligiblePackets.length,
    packets: eligiblePackets
  });
  return { input, zeroCallPackets };
}

function opinionFor(packet) {
  const selected = packet.candidateOccurrences.filter((candidate) =>
    packet.admission.requiredBlockIds.includes(
      candidate.sourceAnchor.blockId
    )
  );
  return {
    packetId: packet.packetId,
    suggestedRole: packet.candidateRole,
    selectedOccurrenceIds: selected.map(
      (candidate) => candidate.occurrenceId
    ),
    selectedAnchorBlockIds: [
      ...new Set(
        selected.map((candidate) => candidate.sourceAnchor.blockId)
      )
    ],
    abstain: false,
    abstentionReason: null
  };
}

test("admits exactly nine blind packets and keeps all controls zero-call", async () => {
  const { input, zeroCallPackets } = await loadFixture();
  assert.equal(input.packetCount, 9);
  assert.equal(zeroCallPackets.length, 3);
  assert.deepEqual(
    zeroCallPackets.map((packet) => packet.admission.reasonCodes[0]).sort(),
    [
      "BUNDLE_INVALID",
      "DETERMINISTIC_HIGH_ZERO_CALL",
      "RELIABLE_ANCHOR_MISSING"
    ]
  );
  const serialized = JSON.stringify(input).toLowerCase();
  assert.ok(serialized.includes('"groundtruthincluded":false'));
  assert.ok(!serialized.includes('"groundtruthincluded":true'));
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
});

test("requires one runtime-isomorphic packet per provider call", async () => {
  const { input } = await loadFixture();
  const packet = input.packets[0];
  const providerInput = {
    schemaVersion: "ROLE_CANDIDATE_ANCHOR_ABSTENTION_INPUT_V1",
    track: input.track,
    taskId: packet.taskId,
    executionId: packet.executionId,
    family: packet.family,
    requestedRoles: [packet.candidateRole],
    packetCount: 1,
    packets: [packet]
  };
  assert.equal(
    validateTrackBHoldoutProviderInput(providerInput),
    providerInput
  );
  const grouped = structuredClone(providerInput);
  grouped.requestedRoles.push(input.packets[1].candidateRole);
  grouped.packetCount = 2;
  grouped.packets.push(input.packets[1]);
  assert.throws(
    () => validateTrackBHoldoutProviderInput(grouped),
    /PROVIDER_INPUT_IDENTITY_INVALID/
  );
});

test("accepts strict role-candidate-anchor output and rejects unrelated anchors", async () => {
  const { input } = await loadFixture();
  const payload = { opinions: input.packets.map(opinionFor) };
  assert.equal(
    validateTrackBHoldoutOpinionPayload(input, payload),
    payload
  );

  const forged = structuredClone(payload);
  const packet = input.packets.find(
    (candidate) => candidate.sampleId === "TBH-CON-003"
  );
  const unrelated = packet.candidateOccurrences.find(
    (candidate) =>
      candidate.sourceAnchor.blockId === "tbh-con-003-b03"
  );
  const index = input.packets.findIndex(
    (candidate) => candidate.packetId === packet.packetId
  );
  forged.opinions[index].selectedOccurrenceIds = [
    unrelated.occurrenceId
  ];
  forged.opinions[index].selectedAnchorBlockIds = [
    unrelated.sourceAnchor.blockId
  ];
  assert.throws(
    () => validateTrackBHoldoutOpinionPayload(input, forged),
    /selectedAnchorBlockIds invalid/
  );

  const finalVerdict = structuredClone(payload);
  finalVerdict.opinions[0].verdict = "PASS";
  assert.throws(
    () => validateTrackBHoldoutOpinionPayload(input, finalVerdict),
    /fields invalid/
  );
});

test("admits only non-empty stop completions with duplicate-free strict JSON", async () => {
  const { input } = await loadFixture();
  const payload = { opinions: input.packets.map(opinionFor) };
  const envelope = {
    choices: [
      {
        finish_reason: "stop",
        message: { content: JSON.stringify(payload) }
      }
    ]
  };
  assert.deepEqual(
    validateTrackBHoldoutDeepSeekEnvelope(input, envelope),
    payload
  );
  for (const [candidate, admissionCode] of [
    [
      { choices: [{ finish_reason: "length", message: { content: "{}" } }] },
      "FINISH_REASON_NOT_STOP"
    ],
    [
      { choices: [{ finish_reason: "stop", message: { content: "" } }] },
      "CONTENT_EMPTY"
    ],
    [
      {
        choices: [
          {
            finish_reason: "stop",
            message: { content: '{"opinions":[],"opinions":[]}' }
          }
        ]
      },
      "CONTENT_NOT_JSON"
    ],
    [
      {
        choices: [
          {
            finish_reason: "stop",
            message: { content: '{"opinions":[]}' }
          }
        ]
      },
      "OPINION_SCHEMA_INVALID"
    ]
  ]) {
    assert.throws(
      () => validateTrackBHoldoutDeepSeekEnvelope(input, candidate),
      (error) => error.admissionCode === admissionCode
    );
  }
});
