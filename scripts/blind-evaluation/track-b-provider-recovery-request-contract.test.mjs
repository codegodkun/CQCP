import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

import {
  TRACK_B_PROVIDER_RECOVERY_MAX_OUTPUT_TOKENS,
  TRACK_B_PROVIDER_RECOVERY_MODEL,
  TRACK_B_PROVIDER_RECOVERY_REQUEST_BUILDER_VERSION,
  buildTrackBProviderRecoveryCall
} from "./track-b-provider-recovery-request-contract.mjs";

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), "../..");

async function loadFixture() {
  const input = JSON.parse(
    await readFile(
      resolve(
        repoRoot,
        "outputs/task-eval-005/track-b-fifth-v1/run-v1/model-input.json"
      ),
      "utf8"
    )
  );
  return {
    packet: input.packets.find((item) => item.sampleId === "TB5-CON-001"),
    promptBytes: await readFile(
      resolve(
        repoRoot,
        "scripts/blind-evaluation/track-b-provider-recovery-prompt-v3.txt"
      )
    )
  };
}

test("builds one strict v3 request whose user content is only the semantic projection", async () => {
  const { packet, promptBytes } = await loadFixture();
  const call = buildTrackBProviderRecoveryCall(packet, promptBytes);

  assert.equal(
    TRACK_B_PROVIDER_RECOVERY_REQUEST_BUILDER_VERSION,
    "track-b-provider-recovery-request-builder-v3"
  );
  assert.equal(call.input.packetId, packet.packetId);
  assert.equal(call.outboundRequest.model, TRACK_B_PROVIDER_RECOVERY_MODEL);
  assert.deepEqual(call.outboundRequest.thinking, { type: "disabled" });
  assert.equal(call.outboundRequest.stream, false);
  assert.deepEqual(call.outboundRequest.response_format, {
    type: "json_object"
  });
  assert.equal(
    call.outboundRequest.max_tokens,
    TRACK_B_PROVIDER_RECOVERY_MAX_OUTPUT_TOKENS
  );
  assert.equal(
    call.outboundRequest.messages[1].content,
    JSON.stringify(call.input)
  );

  const serialized = call.outboundRequest.messages[1].content;
  for (const forbidden of [
    "taskId",
    "executionId",
    "sampleId",
    "coverageStatus",
    "diagnosticCode",
    "reasonCodes",
    "modelCallAllowed",
    "SYS_ROLE_CONFLICT",
    "ELIGIBLE_CONFLICT_LOCAL_CONTEXT"
  ]) {
    assert.equal(serialized.includes(forbidden), false, forbidden);
  }
});

test("rejects prompt drift before constructing a provider request", async () => {
  const { packet, promptBytes } = await loadFixture();
  assert.throws(
    () =>
      buildTrackBProviderRecoveryCall(
        packet,
        Buffer.concat([promptBytes, Buffer.from("tampered")])
      ),
    /TRACK_B_RECOVERY_PROMPT_HASH_MISMATCH/
  );
});
