import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

import {
  validateTrackBProviderRecoveryDeepSeekEnvelope
} from "./track-b-provider-recovery-opinion-contract.mjs";
import {
  projectRuntimeEvidencePacketForModel
} from "./track-b-provider-recovery-projection.mjs";

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), "../..");

async function loadProjectedPacket() {
  const input = JSON.parse(
    await readFile(
      resolve(
        repoRoot,
        "outputs/task-eval-005/track-b-fifth-v1/run-v1/model-input.json"
      ),
      "utf8"
    )
  );
  return projectRuntimeEvidencePacketForModel(
    input.packets.find((item) => item.sampleId === "TB5-CON-001")
  );
}

test("accepts one strict positive role/candidate/anchor opinion for the projected packet", async () => {
  const input = await loadProjectedPacket();
  const payload = {
    opinions: [
      {
        packetId: input.packetId,
        suggestedRole: input.requestedRole,
        selectedOccurrenceIds: ["OCC-002"],
        selectedAnchorBlockIds: ["tb5-con-001-b01"],
        abstain: false,
        abstentionReason: null
      }
    ]
  };
  const envelope = {
    choices: [
      {
        finish_reason: "stop",
        message: { content: JSON.stringify(payload) }
      }
    ]
  };

  assert.deepEqual(
    validateTrackBProviderRecoveryDeepSeekEnvelope(input, envelope),
    payload
  );
});

test("accepts the exact opinion field set regardless of JSON property order", async () => {
  const input = await loadProjectedPacket();
  const reordered = {
    opinions: [
      {
        abstentionReason: null,
        abstain: false,
        selectedAnchorBlockIds: ["tb5-con-001-b01"],
        selectedOccurrenceIds: ["OCC-002"],
        suggestedRole: input.requestedRole,
        packetId: input.packetId
      }
    ]
  };

  assert.deepEqual(
    validateTrackBProviderRecoveryDeepSeekEnvelope(input, {
      choices: [
        {
          finish_reason: "stop",
          message: { content: JSON.stringify(reordered) }
        }
      ]
    }),
    reordered
  );
});

test("fails closed on duplicate JSON keys, non-stop completion, or untrusted anchor selection", async () => {
  const input = await loadProjectedPacket();
  assert.throws(
    () =>
      validateTrackBProviderRecoveryDeepSeekEnvelope(input, {
        choices: [
          {
            finish_reason: "stop",
            message: {
              content:
                '{"opinions":[],"opinions":[]}'
            }
          }
        ]
      }),
    /TRACK_B_RECOVERY_CONTENT_NOT_STRICT_JSON/
  );
  assert.throws(
    () =>
      validateTrackBProviderRecoveryDeepSeekEnvelope(input, {
        choices: [
          { finish_reason: "length", message: { content: "{}" } }
        ]
      }),
    /TRACK_B_RECOVERY_FINISH_REASON_NOT_STOP/
  );
  assert.throws(
    () =>
      validateTrackBProviderRecoveryDeepSeekEnvelope(input, {
        choices: [
          {
            finish_reason: "stop",
            message: {
              content: JSON.stringify({
                opinions: [
                  {
                    packetId: input.packetId,
                    suggestedRole: input.requestedRole,
                    selectedOccurrenceIds: ["OCC-002"],
                    selectedAnchorBlockIds: ["invented-block"],
                    abstain: false,
                    abstentionReason: null
                  }
                ]
              })
            }
          }
        ]
      }),
    /TRACK_B_RECOVERY_ANCHOR_SELECTION_INVALID/
  );
});
