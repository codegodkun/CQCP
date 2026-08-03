import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

import {
  TRACK_B_PROVIDER_RECOVERY_PACKET_SCHEMA,
  projectRuntimeEvidencePacketForModel,
  validateModelFacingEvidencePacket
} from "./track-b-provider-recovery-projection.mjs";

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const fifthInputPath = resolve(
  repoRoot,
  "outputs/task-eval-005/track-b-fifth-v1/run-v1/model-input.json"
);

async function loadConflictedPacket() {
  const input = JSON.parse(await readFile(fifthInputPath, "utf8"));
  return input.packets.find((packet) => packet.sampleId === "TB5-CON-001");
}

test("projects an eligible runtime packet to the exact model-facing semantic contract", async () => {
  const runtimePacket = await loadConflictedPacket();
  const projected = projectRuntimeEvidencePacketForModel(runtimePacket);

  assert.deepEqual(Object.keys(projected), [
    "schemaVersion",
    "packetId",
    "family",
    "reviewPointCode",
    "requestedRole",
    "candidateOccurrences",
    "budget",
    "requiredOutput"
  ]);
  assert.equal(projected.schemaVersion, TRACK_B_PROVIDER_RECOVERY_PACKET_SCHEMA);
  assert.equal(projected.requestedRole, runtimePacket.candidateRole);
  assert.deepEqual(Object.keys(projected.candidateOccurrences[0]), [
    "occurrenceId",
    "value",
    "evidence",
    "anchor"
  ]);

  const serialized = JSON.stringify(projected);
  for (const forbidden of [
    "taskId",
    "executionId",
    "sampleId",
    "ruleSetVersion",
    "coverageSignals",
    "coverageStatus",
    "diagnosticCode",
    "admission",
    "reasonCodes",
    "modelCallAllowed",
    "AMBIGUOUS",
    "CONFLICTED",
    "SYS_ROLE_CONFLICT",
    "ELIGIBLE_CONFLICT_LOCAL_CONTEXT"
  ]) {
    assert.equal(serialized.includes(forbidden), false, forbidden);
  }
});

test("fails closed before projection when runtime eligibility or reliable evidence is incomplete", async () => {
  const runtimePacket = await loadConflictedPacket();

  const zeroCall = structuredClone(runtimePacket);
  zeroCall.admission.modelCallAllowed = false;
  assert.throws(
    () => projectRuntimeEvidencePacketForModel(zeroCall),
    /TRACK_B_RECOVERY_PACKET_NOT_ELIGIBLE/
  );

  const unreliable = structuredClone(runtimePacket);
  unreliable.candidateOccurrences[0].sourceAnchor.reliable = false;
  assert.throws(
    () => projectRuntimeEvidencePacketForModel(unreliable),
    /TRACK_B_RECOVERY_ANCHOR_UNRELIABLE/
  );

  const truncated = structuredClone(runtimePacket);
  truncated.budget.truncated = true;
  assert.throws(
    () => projectRuntimeEvidencePacketForModel(truncated),
    /TRACK_B_RECOVERY_BUDGET_INCOMPLETE/
  );
});

test("rejects any model-facing field drift instead of forwarding routing metadata", async () => {
  const projected = projectRuntimeEvidencePacketForModel(
    await loadConflictedPacket()
  );
  const leaked = { ...projected, diagnosticCode: "SYS_ROLE_CONFLICT" };

  assert.throws(
    () => validateModelFacingEvidencePacket(leaked),
    /TRACK_B_RECOVERY_MODEL_INPUT_FIELDS_INVALID/
  );
});
