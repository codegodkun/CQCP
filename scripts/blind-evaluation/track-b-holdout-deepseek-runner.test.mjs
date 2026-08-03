import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { randomBytes } from "node:crypto";
import {
  cp,
  mkdir,
  mkdtemp,
  readFile,
  readdir,
  rm,
  writeFile
} from "node:fs/promises";
import { tmpdir } from "node:os";
import { dirname, resolve } from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

import {
  TrackBHoldoutRunnerExit,
  runTrackBHoldoutDeepSeek
} from "./track-b-holdout-deepseek-runner.mjs";
import { sha256 } from "./track-b-holdout-contract.mjs";

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const scripts = Object.freeze({
  prepare: resolve(
    repoRoot,
    "scripts/blind-evaluation/prepare-track-b-holdout-corpus.mjs"
  ),
  challenge: resolve(
    repoRoot,
    "scripts/blind-evaluation/prepare-track-b-holdout-human-challenge.mjs"
  ),
  seal: resolve(
    repoRoot,
    "scripts/blind-evaluation/seal-track-b-holdout-human-ground-truth.mjs"
  ),
  dispatch: resolve(
    repoRoot,
    "scripts/blind-evaluation/prepare-track-b-holdout-dispatch.mjs"
  )
});

test("preflights without network, executes 9 calls once, and persists no secret/raw response", async () => {
  const root = await prepareRun();
  try {
    let transportCalls = 0;
    const preflight = await runTrackBHoldoutDeepSeek({
      argv: [root, "preflight"],
      environment: {}
    });
    assert.equal(preflight.status, "PREFLIGHT_VALIDATED_NO_NETWORK");
    assert.equal(preflight.providerCallCount, 9);
    assert.equal(preflight.networkAttempted, false);

    const now = advancingClock();
    const result = await runTrackBHoldoutDeepSeek({
      argv: [root, "run"],
      environment: { DEEPSEEK_API_KEY: "sentinel-holdout-key" },
      dependencies: {
        now,
        resolveAddresses: async () => [
          { address: "8.8.8.8", family: 4 }
        ],
        transport: async ({ body, secret, addresses }) => {
          transportCalls += 1;
          assert.equal(secret, "sentinel-holdout-key");
          assert.deepEqual(addresses, [{ address: "8.8.8.8", family: 4 }]);
          const request = JSON.parse(body.toString("utf8"));
          const input = JSON.parse(request.messages[1].content);
          const packet = input.packets[0];
          const selected = packet.candidateOccurrences.filter((candidate) =>
            packet.admission.requiredBlockIds.includes(
              candidate.sourceAnchor.blockId
            )
          );
          const payload = {
            opinions: [
              {
                packetId: packet.packetId,
                suggestedRole: packet.candidateRole,
                selectedOccurrenceIds: selected.map(
                  (candidate) => candidate.occurrenceId
                ),
                selectedAnchorBlockIds: [
                  ...new Set(
                    selected.map(
                      (candidate) => candidate.sourceAnchor.blockId
                    )
                  )
                ],
                abstain: false,
                abstentionReason: null
              }
            ]
          };
          return {
            status: 200,
            contentTypeClass: "APPLICATION_JSON",
            body: Buffer.from(
              JSON.stringify({
                id: `response-${transportCalls}`,
                model: "deepseek-v4-pro",
                created: 1_800_000_000 + transportCalls,
                choices: [
                  {
                    finish_reason: "stop",
                    message: { content: JSON.stringify(payload) }
                  }
                ]
              }),
              "utf8"
            )
          };
        }
      }
    });
    assert.equal(transportCalls, 9);
    assert.equal(result.status, "ACCEPTED");
    assert.equal(result.providerCallCount, 9);
    assert.equal(result.opinionCount, 9);
    assert.equal(result.automaticRetryPerformed, false);

    const runRoot = resolve(
      root,
      "outputs/task-eval-002/track-b-holdout-v1/run-v1"
    );
    const outputBytes = await readFile(resolve(runRoot, "deepseek-opinion.json"));
    const output = JSON.parse(outputBytes.toString("utf8"));
    assert.equal(output.providerResponses.length, 9);
    assert.equal(output.opinions.length, 9);
    assert.equal(output.rawResponsePersisted, false);
    assert.equal(output.reasoningContentPersisted, false);
    assert.ok(
      output.providerResponses.every(
        (receipt) =>
          receipt.receiptStatus === "STRICT_SCHEMA_ACCEPTED" &&
          /^[a-f0-9]{64}$/.test(receipt.modelInputSha256) &&
          /^[a-f0-9]{64}$/.test(receipt.outboundRequestSha256)
      )
    );
    const persisted = outputBytes.toString("utf8").toLowerCase();
    for (const forbidden of [
      "sentinel-holdout-key",
      '"rawresponse":',
      '"rawproviderresponse":',
      '"reasoning_content":',
      "response-1"
    ]) {
      assert.ok(!persisted.includes(forbidden));
    }

    const claimBytes = await readFile(
      resolve(runRoot, "deepseek-execution-claim.json")
    );
    const claim = JSON.parse(claimBytes.toString("utf8"));
    assert.equal(
      claim.egressAuthorizationSha256,
      sha256(
        await readFile(
          resolve(root, "scripts/blind-evaluation/mvp002-standing-egress-grant.json")
        )
      )
    );
    assert.equal(
      claim.egressChallengeSha256,
      sha256(
        await readFile(
          resolve(runRoot, "derived-egress-authorization-receipt.json")
        )
      )
    );

    await assert.rejects(
      () =>
        runTrackBHoldoutDeepSeek({
          argv: [root, "run"],
          environment: { DEEPSEEK_API_KEY: "sentinel-holdout-key" },
          dependencies: {
            now,
            resolveAddresses: async () => [
              { address: "8.8.8.8", family: 4 }
            ],
            transport: async () => {
              transportCalls += 1;
              throw new Error("must not replay");
            }
          }
        }),
      /already claimed/
    );
    assert.equal(transportCalls, 9);
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});

test("unknown side effect stops after one attempt and writes a terminal blocked receipt", async () => {
  const root = await prepareRun();
  try {
    let attempts = 0;
    await assert.rejects(
      () =>
        runTrackBHoldoutDeepSeek({
          argv: [root, "run"],
          environment: { DEEPSEEK_API_KEY: "sentinel-holdout-key" },
          dependencies: {
            now: advancingClock(),
            resolveAddresses: async () => [
              { address: "8.8.8.8", family: 4 }
            ],
            transport: async () => {
              attempts += 1;
              const error = new Error("ambiguous transport outcome");
              error.code = "ETIMEDOUT";
              throw error;
            }
          }
        }),
      (error) =>
        error instanceof TrackBHoldoutRunnerExit && error.exitCode === 5
    );
    assert.equal(attempts, 1);
    const blockedRoot = resolve(
      root,
      "outputs/task-eval-002/track-b-holdout-v1/run-v1/blocked"
    );
    const names = await readdir(blockedRoot);
    assert.equal(names.length, 1);
    const blockedBytes = await readFile(resolve(blockedRoot, names[0]));
    const blocked = JSON.parse(blockedBytes.toString("utf8"));
    assert.equal(blocked.status, "BLOCKED");
    assert.equal(blocked.code, "NETWORK_OR_TIMEOUT");
    assert.equal(blocked.detailCode, "UNKNOWN_SIDE_EFFECT_NO_RETRY");
    assert.equal(blocked.networkAttempted, true);
    assert.equal(blocked.automaticRetryPerformed, false);
    assert.equal(blocked.completedCallCount, 0);
    assert.ok(!blockedBytes.toString("utf8").includes("sentinel-holdout-key"));
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});

async function prepareRun() {
  const root = await mkdtemp(resolve(tmpdir(), "cqcp-tbh-runner-"));
  const fixtureRoot = resolve(
    root,
    "apps/api-server/src/test/resources/track-b-holdout-v1"
  );
  const scriptRoot = resolve(root, "scripts/blind-evaluation");
  await mkdir(fixtureRoot, { recursive: true });
  await mkdir(scriptRoot, { recursive: true });
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
    await cp(
      resolve(repoRoot, "scripts/blind-evaluation", name),
      resolve(scriptRoot, name)
    );
  }
  const now = new Date(Date.now() - 60_000);
  const createdAt = now.toISOString();
  const dispatchAt = new Date(now.getTime() + 60_000).toISOString();
  const expiresAt = new Date(now.getTime() + 3_600_000).toISOString();
  const nonce = `TBH2-${randomBytes(16).toString("hex")}`;
  assertRun(scripts.prepare, [root, createdAt]);
  assertRun(scripts.challenge, [root, createdAt, expiresAt, nonce]);
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
  assertRun(scripts.seal, [root, "confirmation.json", "create"]);
  assertRun(scripts.dispatch, [root, dispatchAt]);
  return root;
}

function assertRun(script, args) {
  const result = spawnSync(process.execPath, [script, ...args], {
    cwd: repoRoot,
    encoding: "utf8"
  });
  assert.equal(result.status, 0, result.stderr);
  return result;
}

function advancingClock() {
  let value = Date.now() + 60_000;
  return () => {
    const result = new Date(value);
    value += 1_000;
    return result;
  };
}
