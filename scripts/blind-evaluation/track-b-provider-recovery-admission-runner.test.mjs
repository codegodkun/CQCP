import assert from "node:assert/strict";
import { cp, mkdir, mkdtemp, readFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { dirname, resolve } from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

import {
  TrackBProviderRecoveryAdmissionExit,
  runTrackBProviderRecoveryAdmission
} from "./track-b-provider-recovery-admission-runner.mjs";

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const requiredFiles = [
  "outputs/task-eval-006/track-b-recovery-v1/corpus.json",
  "outputs/task-eval-006/track-b-recovery-v1/human-ground-truth.json",
  "scripts/blind-evaluation/track-b-provider-recovery-prompt-v3.txt",
  "scripts/blind-evaluation/mvp002-standing-egress-grant.json",
  "outputs/task-eval-006/track-b-recovery-v1/run-v1/model-input.json",
  "outputs/task-eval-006/track-b-recovery-v1/run-v1/provider-call-set.json",
  "outputs/task-eval-006/track-b-recovery-v1/run-v1/dispatch.json",
  "outputs/task-eval-006/track-b-recovery-v1/run-v1/derived-egress-receipt.json"
];

async function copyEvidence(t) {
  const tempRoot = await mkdtemp(resolve(tmpdir(), "cqcp-recovery-admission-"));
  t.after(() => rm(tempRoot, { recursive: true, force: true }));
  for (const relative of requiredFiles) {
    const target = resolve(tempRoot, ...relative.split("/"));
    await mkdir(dirname(target), { recursive: true });
    await cp(resolve(repoRoot, ...relative.split("/")), target);
  }
  return tempRoot;
}

test("formal admission preflight validates all frozen bytes without network", async () => {
  let transportCalled = false;
  const result = await runTrackBProviderRecoveryAdmission({
    argv: [repoRoot, "preflight"],
    environment: {},
    dependencies: {
      transport: async () => {
        transportCalled = true;
      }
    }
  });
  assert.equal(
    result.status,
    "FORMAL_ADMISSION_PREFLIGHT_VALIDATED_NO_NETWORK"
  );
  assert.equal(result.providerCallCount, 9);
  assert.equal(result.zeroCallControlCount, 3);
  assert.equal(result.networkAttempted, false);
  assert.equal(transportCalled, false);
});

test("formal runner performs exactly nine calls and never persists raw reasoning", async (t) => {
  const tempRoot = await copyEvidence(t);
  let transportCalls = 0;
  const result = await runTrackBProviderRecoveryAdmission({
    argv: [tempRoot, "run"],
    environment: { DEEPSEEK_API_KEY: "test-only-secret" },
    dependencies: {
      now: () => new Date("2026-08-03T12:30:00.000Z"),
      resolveAddresses: async () => [{ address: "8.8.8.8", family: 4 }],
      transport: async ({ body }) => {
        transportCalls += 1;
        const request = JSON.parse(body.toString("utf8"));
        const input = JSON.parse(request.messages[1].content);
        const selected = input.candidateOccurrences[0];
        return {
          status: 200,
          contentTypeClass: "APPLICATION_JSON",
          body: Buffer.from(JSON.stringify({
            id: `mock-${transportCalls}`,
            model: "deepseek-v4-pro",
            created: 1,
            choices: [{
              finish_reason: "stop",
              message: {
                content: JSON.stringify({ opinions: [{
                  packetId: input.packetId,
                  suggestedRole: input.requestedRole,
                  selectedOccurrenceIds: [selected.occurrenceId],
                  selectedAnchorBlockIds: [selected.anchor.blockId],
                  abstain: false,
                  abstentionReason: null
                }] }),
                reasoning_content: "must never persist"
              }
            }]
          }), "utf8")
        };
      }
    }
  });
  assert.equal(result.providerCallCount, 9);
  assert.equal(result.opinionCount, 9);
  assert.equal(transportCalls, 9);
  const bytes = await readFile(resolve(
    tempRoot,
    "outputs/task-eval-006/track-b-recovery-v1/run-v1/deepseek-opinion.json"
  ));
  assert.equal(bytes.includes(Buffer.from("must never persist")), false);
  assert.equal(bytes.includes(Buffer.from("test-only-secret")), false);
});

test("unknown side effect writes terminal evidence and retries zero times", async (t) => {
  const tempRoot = await copyEvidence(t);
  let calls = 0;
  await assert.rejects(
    () => runTrackBProviderRecoveryAdmission({
      argv: [tempRoot, "run"],
      environment: { DEEPSEEK_API_KEY: "test-only-secret" },
      dependencies: {
        now: () => new Date("2026-08-03T12:31:00.000Z"),
        resolveAddresses: async () => [{ address: "8.8.8.8", family: 4 }],
        transport: async () => {
          calls += 1;
          throw new Error("timeout after request start");
        }
      }
    }),
    (error) =>
      error instanceof TrackBProviderRecoveryAdmissionExit &&
      error.exitCode === 5
  );
  assert.equal(calls, 1);
  const terminal = JSON.parse(await readFile(resolve(
    tempRoot,
    "outputs/task-eval-006/track-b-recovery-v1/run-v1/deepseek-terminal.json"
  ), "utf8"));
  assert.equal(terminal.detailCode, "UNKNOWN_SIDE_EFFECT_NO_RETRY");
  assert.equal(terminal.automaticRetryPerformed, false);
});
