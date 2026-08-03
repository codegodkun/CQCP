import assert from "node:assert/strict";
import { access, cp, mkdir, mkdtemp, readFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { dirname, resolve } from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

import {
  TrackBProviderRecoveryRunnerExit,
  runTrackBProviderRecoveryDiagnostic
} from "./track-b-provider-recovery-runner.mjs";

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), "../..");

test("preflight recomputes every frozen diagnostic artifact without network", async () => {
  let transportCalled = false;
  const result = await runTrackBProviderRecoveryDiagnostic({
    argv: [repoRoot, "preflight"],
    environment: {},
    dependencies: {
      transport: async () => {
        transportCalled = true;
        throw new Error("network must not run in preflight");
      }
    }
  });

  assert.equal(result.status, "PREFLIGHT_VALIDATED_NO_NETWORK");
  assert.equal(result.providerCallCount, 4);
  assert.equal(result.networkAttempted, false);
  assert.equal(transportCalled, false);
  for (const field of [
    "modelInputSha256",
    "providerCallSetSha256",
    "dispatchSha256",
    "derivedReceiptSha256"
  ]) {
    assert.match(result[field], /^[a-f0-9]{64}$/);
  }
});

test("runs four calls once and persists only strict accepted opinions plus hashes", async (t) => {
  const tempRoot = await mkdtemp(resolve(tmpdir(), "cqcp-recovery-runner-"));
  t.after(() => rm(tempRoot, { recursive: true, force: true }));
  for (const relative of [
    "outputs/task-eval-005/track-b-fifth-v1/run-v1/model-input.json",
    "scripts/blind-evaluation/track-b-provider-recovery-prompt-v3.txt",
    "scripts/blind-evaluation/mvp002-standing-egress-grant.json",
    "outputs/task-eval-006/track-b-provider-recovery-v1/diagnostic-v1/model-input.json",
    "outputs/task-eval-006/track-b-provider-recovery-v1/diagnostic-v1/provider-call-set.json",
    "outputs/task-eval-006/track-b-provider-recovery-v1/diagnostic-v1/dispatch.json",
    "outputs/task-eval-006/track-b-provider-recovery-v1/diagnostic-v1/derived-egress-receipt.json"
  ]) {
    const target = resolve(tempRoot, ...relative.split("/"));
    await mkdir(dirname(target), { recursive: true });
    await cp(resolve(repoRoot, ...relative.split("/")), target);
  }

  const expectedByPacket = new Map([
    ["EP-1c4bd4f0cc79234a8d0938fddaa6652ea36d7c3652b5e1bafaeccf10540b3811", "OCC-002"],
    ["EP-23b06c3d7bd61056f7b5ad6019fcdf8a6291d95cd098f04db917b76023b74a7a", "OCC-001"],
    ["EP-b0e11f150650818fbd48bf8f48c2312e303de96885343acfcd5ed035ea3b78e1", "OCC-002"],
    ["EP-170929d81f5ec1c0f1098331aa74973b7ea563a0dbcffcd8f53c36e4dbae9c51", "OCC-001"]
  ]);
  let transportCalls = 0;
  const result = await runTrackBProviderRecoveryDiagnostic({
    argv: [tempRoot, "run"],
    environment: { DEEPSEEK_API_KEY: "test-only-secret" },
    dependencies: {
      now: () => new Date("2026-08-03T12:30:00.000Z"),
      resolveAddresses: async () => [{ address: "8.8.8.8", family: 4 }],
      transport: async ({ body }) => {
        transportCalls += 1;
        const request = JSON.parse(body.toString("utf8"));
        const input = JSON.parse(request.messages[1].content);
        const occurrenceId = expectedByPacket.get(input.packetId);
        const selected = input.candidateOccurrences.find(
          (item) => item.occurrenceId === occurrenceId
        );
        return {
          status: 200,
          contentTypeClass: "APPLICATION_JSON",
          body: Buffer.from(
            JSON.stringify({
              id: `mock-${transportCalls}`,
              model: "deepseek-v4-pro",
              created: 1,
              choices: [
                {
                  finish_reason: "stop",
                  message: {
                    content: JSON.stringify({
                      opinions: [
                        {
                          packetId: input.packetId,
                          suggestedRole: input.requestedRole,
                          selectedOccurrenceIds: [occurrenceId],
                          selectedAnchorBlockIds: [selected.anchor.blockId],
                          abstain: false,
                          abstentionReason: null
                        }
                      ]
                    }),
                    reasoning_content: "must never be persisted"
                  }
                }
              ]
            }),
            "utf8"
          )
        };
      }
    }
  });

  assert.equal(result.status, "ACCEPTED_NON_ADMISSION_DIAGNOSTIC");
  assert.equal(result.providerCallCount, 4);
  assert.equal(result.opinionCount, 4);
  assert.equal(transportCalls, 4);
  const outputBytes = await readFile(
    resolve(
      tempRoot,
      "outputs/task-eval-006/track-b-provider-recovery-v1/diagnostic-v1/deepseek-opinion.json"
    )
  );
  const output = JSON.parse(outputBytes.toString("utf8"));
  assert.equal(output.rawResponsePersisted, false);
  assert.equal(output.reasoningContentPersisted, false);
  assert.equal(output.automaticRetryPerformed, false);
  assert.equal(output.opinions.length, 4);
  assert.equal(outputBytes.includes(Buffer.from("must never be persisted")), false);
  assert.equal(outputBytes.includes(Buffer.from("test-only-secret")), false);
});

test("writes a terminal receipt and performs no retry after an unknown HTTP side effect", async (t) => {
  const tempRoot = await mkdtemp(resolve(tmpdir(), "cqcp-recovery-fail-"));
  t.after(() => rm(tempRoot, { recursive: true, force: true }));
  for (const relative of [
    "outputs/task-eval-005/track-b-fifth-v1/run-v1/model-input.json",
    "scripts/blind-evaluation/track-b-provider-recovery-prompt-v3.txt",
    "scripts/blind-evaluation/mvp002-standing-egress-grant.json",
    "outputs/task-eval-006/track-b-provider-recovery-v1/diagnostic-v1/model-input.json",
    "outputs/task-eval-006/track-b-provider-recovery-v1/diagnostic-v1/provider-call-set.json",
    "outputs/task-eval-006/track-b-provider-recovery-v1/diagnostic-v1/dispatch.json",
    "outputs/task-eval-006/track-b-provider-recovery-v1/diagnostic-v1/derived-egress-receipt.json"
  ]) {
    const target = resolve(tempRoot, ...relative.split("/"));
    await mkdir(dirname(target), { recursive: true });
    await cp(resolve(repoRoot, ...relative.split("/")), target);
  }

  let transportCalls = 0;
  await assert.rejects(
    () =>
      runTrackBProviderRecoveryDiagnostic({
        argv: [tempRoot, "run"],
        environment: { DEEPSEEK_API_KEY: "test-only-secret" },
        dependencies: {
          now: () => new Date("2026-08-03T12:31:00.000Z"),
          resolveAddresses: async () => [
            { address: "8.8.8.8", family: 4 }
          ],
          transport: async () => {
            transportCalls += 1;
            const error = new Error("timeout after request started");
            error.code = "MODEL_TIMEOUT";
            throw error;
          }
        }
      }),
    (error) =>
      error instanceof TrackBProviderRecoveryRunnerExit &&
      error.exitCode === 5
  );
  assert.equal(transportCalls, 1);

  const terminal = JSON.parse(
    await readFile(
      resolve(
        tempRoot,
        "outputs/task-eval-006/track-b-provider-recovery-v1/diagnostic-v1/deepseek-terminal.json"
      ),
      "utf8"
    )
  );
  assert.equal(terminal.code, "NETWORK_OR_TIMEOUT");
  assert.equal(terminal.detailCode, "UNKNOWN_SIDE_EFFECT_NO_RETRY");
  assert.equal(terminal.completedCallCount, 0);
  assert.equal(terminal.automaticRetryPerformed, false);
  await assert.rejects(
    () =>
      access(
        resolve(
          tempRoot,
          "outputs/task-eval-006/track-b-provider-recovery-v1/diagnostic-v1/deepseek-opinion.json"
        )
      ),
    /ENOENT/
  );
});
