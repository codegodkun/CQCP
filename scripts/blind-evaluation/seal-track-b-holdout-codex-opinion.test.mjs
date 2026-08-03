import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { randomBytes } from "node:crypto";
import {
  cp,
  mkdir,
  mkdtemp,
  readFile,
  rm,
  writeFile
} from "node:fs/promises";
import { tmpdir } from "node:os";
import { dirname, resolve } from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

import { sha256 } from "./track-b-holdout-contract.mjs";

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const scriptPath = (name) =>
  resolve(repoRoot, "scripts/blind-evaluation", name);

test("seals strict fork-turns-none Codex opinions without reading ground truth", async () => {
  const root = await prepareRun();
  try {
    const runRoot = resolve(
      root,
      "outputs/task-eval-002/track-b-holdout-v1/run-v1"
    );
    const input = JSON.parse(
      await readFile(resolve(runRoot, "model-input.json"), "utf8")
    );
    const dispatch = JSON.parse(
      await readFile(resolve(runRoot, "dispatch.json"), "utf8")
    );
    const startedAt = new Date(
      Date.parse(dispatch.createdAt) + 1_000
    ).toISOString();
    const completedAt = new Date(
      Date.parse(dispatch.createdAt) + 61_000
    ).toISOString();
    const payload = {
      opinions: input.packets.map((packet) => {
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
      })
    };
    const rawRelativePath =
      "outputs/task-eval-002/track-b-holdout-v1/run-v1/" +
      "codex-agent-raw.json";
    await writeFile(
      resolve(root, ...rawRelativePath.split("/")),
      `${JSON.stringify(payload)}\n`,
      "utf8"
    );
    const sealed = run(scriptPath("seal-track-b-holdout-codex-opinion.mjs"), [
      root,
      rawRelativePath,
      "/root/track_b_holdout_blind_eval",
      "agent-test-001",
      startedAt,
      completedAt
    ]);
    assert.equal(sealed.status, 0, sealed.stderr);
    const outputBytes = await readFile(resolve(runRoot, "codex-opinion.json"));
    const receiptBytes = await readFile(
      resolve(runRoot, "codex-execution-receipt.json")
    );
    const output = JSON.parse(outputBytes.toString("utf8"));
    const receipt = JSON.parse(receiptBytes.toString("utf8"));
    assert.equal(output.status, "ACCEPTED");
    assert.equal(output.forkTurns, "none");
    assert.equal(output.historicalContextIncluded, false);
    assert.equal(output.opinions.length, 9);
    assert.equal(receipt.source, "CODEX_COLLABORATION_TOOL");
    assert.equal(receipt.forkTurns, "none");
    assert.equal(receipt.transcriptCrossCheckRequired, true);
    assert.equal(output.executionReceiptSha256, sha256(receiptBytes));
    const serialized = outputBytes.toString("utf8").toLowerCase();
    for (const forbidden of [
      "project owner test",
      "proposedexpected",
      "human-ground-truth",
      "finding",
      "verdict"
    ]) {
      assert.ok(!serialized.includes(forbidden));
    }
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});

async function prepareRun() {
  const root = await mkdtemp(resolve(tmpdir(), "cqcp-tbh-codex-"));
  const fixtureRoot = resolve(
    root,
    "apps/api-server/src/test/resources/track-b-holdout-v1"
  );
  const blindScriptRoot = resolve(root, "scripts/blind-evaluation");
  await mkdir(fixtureRoot, { recursive: true });
  await mkdir(blindScriptRoot, { recursive: true });
  for (const name of ["source-signals.json", "proposed-decisions.json"]) {
    await cp(
      resolve(
        repoRoot,
        "apps/api-server/src/test/resources/track-b-holdout-v1",
        name
      ),
      resolve(fixtureRoot, name)
    );
  }
  for (const name of [
    "track-b-holdout-opinion-prompt.txt",
    "mvp002-standing-egress-grant.json"
  ]) {
    await cp(scriptPath(name), resolve(blindScriptRoot, name));
  }
  const now = new Date(Date.now() - 60_000);
  const createdAt = now.toISOString();
  const dispatchAt = new Date(now.getTime() + 60_000).toISOString();
  const expiresAt = new Date(now.getTime() + 3_600_000).toISOString();
  const nonce = `TBH2-${randomBytes(16).toString("hex")}`;
  assertRun("prepare-track-b-holdout-corpus.mjs", [root, createdAt]);
  assertRun("prepare-track-b-holdout-human-challenge.mjs", [
    root,
    createdAt,
    expiresAt,
    nonce
  ]);
  const outputRoot = resolve(
    root,
    "outputs/task-eval-002/track-b-holdout-v1"
  );
  const challengeBytes = await readFile(
    resolve(outputRoot, "human-confirmation-challenge.json")
  );
  const challenge = JSON.parse(challengeBytes.toString("utf8"));
  const draft = JSON.parse(
    await readFile(resolve(outputRoot, "human-review-draft.json"), "utf8")
  );
  const decisions = draft.entries.map((entry) => ({
    caseId: entry.caseId,
    packetId: entry.packetId,
    expected: entry.proposedExpected
  }));
  await writeFile(
    resolve(root, "confirmation.json"),
    `${JSON.stringify({
      schemaVersion:
        "task-eval-002-track-b-holdout-human-confirmation-v1",
      confirmed: true,
      challengeNonce: challenge.nonce,
      challengeSha256: sha256(challengeBytes),
      corpusSha256: challenge.corpusSha256,
      humanReviewDraftSha256: challenge.humanReviewDraftSha256,
      humanReviewDocumentSha256:
        challenge.humanReviewDocumentSha256,
      presealManifestSha256: challenge.presealManifestSha256,
      decisionsSha256: sha256(
        Buffer.from(JSON.stringify(decisions), "utf8")
      ),
      decisions,
      confirmedBy: "Project Owner Test",
      confirmedAt: new Date(now.getTime() + 30_000).toISOString(),
      confirmationSource: "CODEX_THREAD_USER_CONFIRMATION",
      confirmationStatement:
        `Test confirmation ${challenge.nonce} ${challenge.corpusSha256}`
    }, null, 2)}\n`,
    "utf8"
  );
  assertRun("seal-track-b-holdout-human-ground-truth.mjs", [
    root,
    "confirmation.json",
    "create"
  ]);
  assertRun("prepare-track-b-holdout-dispatch.mjs", [root, dispatchAt]);
  return root;
}

function assertRun(name, args) {
  const result = run(scriptPath(name), args);
  assert.equal(result.status, 0, result.stderr);
}

function run(script, args) {
  return spawnSync(process.execPath, [script, ...args], {
    cwd: repoRoot,
    encoding: "utf8"
  });
}
