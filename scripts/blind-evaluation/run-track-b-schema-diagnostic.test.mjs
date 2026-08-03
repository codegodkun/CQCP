import assert from "node:assert/strict";
import { mkdtemp, mkdir, cp, readFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { basename, dirname, resolve } from "node:path";
import test from "node:test";

import { runTrackBSchemaDiagnostic } from "./run-track-b-schema-diagnostic.mjs";

test("runner stores classifications and hashes but never raw response or reasoning", async () => {
  const fixture = await fixtureRepo();
  const packet = fixture.input.packets[0];
  let callCount = 0;
  const result = await runTrackBSchemaDiagnostic({
    argv: [fixture.root, "baseline"],
    environment: { DEEPSEEK_API_KEY: "test-only-not-persisted" },
    dependencies: {
      now: monotonicClock(),
      resolveAddresses: async () => ["8.8.8.8"],
      transport: async () => {
        const source = fixture.input.packets[callCount++];
        const payload = callCount === 5
          ? { opinions: [] }
          : {
              opinions: [{
                packetId: source.packetId,
                suggestedRole: source.candidateRole,
                selectedOccurrenceIds: [source.candidateOccurrences[0].occurrenceId],
                selectedAnchorBlockIds: [source.candidateOccurrences[0].sourceAnchor.blockId],
                abstain: false,
                abstentionReason: null
              }]
            };
        return {
          status: 200,
          contentTypeClass: "APPLICATION_JSON",
          body: Buffer.from(JSON.stringify({
            id: `test-${callCount}`,
            created: callCount,
            model: "deepseek-v4-pro",
            choices: [{
              index: 0,
              finish_reason: "stop",
              message: {
                role: "assistant",
                content: JSON.stringify(payload),
                reasoning_content: "must-not-persist"
              }
            }]
          }), "utf8")
        };
      }
    }
  });
  assert.equal(packet.packetId, fixture.input.packets[0].packetId);
  assert.equal(result.completedCallCount, 12);
  assert.equal(result.acceptedCount, 11);
  assert.equal(result.categoryCounts.OPINION_COUNT_INVALID, 1);
  const bytes = await readFile(resolve(
    fixture.root,
    "outputs/task-eval-005/schema-diagnostic-v1/baseline/diagnostic-result.json"
  ));
  const text = bytes.toString("utf8");
  assert.equal(text.includes("must-not-persist"), false);
  assert.equal(text.includes("test-only-not-persisted"), false);
  assert.equal(text.includes("reasoning_content"), false);
  assert.equal(text.includes("\"content\""), false);
});

async function fixtureRepo() {
  const sourceRoot = resolve(process.cwd());
  const root = await mkdtemp(resolve(tmpdir(), "cqcp-tbsd-"));
  const files = [
    "scripts/blind-evaluation/mvp002-standing-egress-grant.json",
    "scripts/blind-evaluation/track-b-holdout-opinion-prompt.txt",
    "outputs/task-eval-005/schema-diagnostic-v1/baseline/diagnostic-input.json",
    "outputs/task-eval-005/schema-diagnostic-v1/baseline/provider-call-set.json",
    "outputs/task-eval-005/schema-diagnostic-v1/baseline/dispatch.json",
    "outputs/task-eval-005/schema-diagnostic-v1/baseline/derived-egress-receipt.json",
    "outputs/task-eval-005/schema-diagnostic-v1/baseline/preflight-manifest.json"
  ];
  for (const relative of files) {
    const target = resolve(root, ...relative.split("/"));
    await mkdir(dirname(target), { recursive: true });
    await cp(resolve(sourceRoot, ...relative.split("/")), target);
  }
  const input = JSON.parse((await readFile(resolve(
    root,
    "outputs/task-eval-005/schema-diagnostic-v1/baseline/diagnostic-input.json"
  ))).toString("utf8"));
  return { root, input };
}

function monotonicClock() {
  let tick = 0;
  return () => new Date(Date.UTC(2026, 7, 3, 12, 0, tick++));
}
