import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { randomBytes } from "node:crypto";
import { cp, mkdir, mkdtemp, readFile, readdir, rm, stat, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { dirname, resolve } from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

import {
  TrackBHoldoutRunnerExit,
  runTrackBSuccessorDeepSeek
} from "./track-b-holdout-deepseek-runner.mjs";
import { sha256 } from "./track-b-holdout-contract.mjs";

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const script = (name) => resolve(repoRoot, "scripts/blind-evaluation", name);
const outputRootRelative = "outputs/task-eval-003/track-b-successor-v1";

test("successor permits one proven pre-send DNS retry and then executes exact 9x1 calls", async () => {
  const root = await prepareRun();
  try {
    const preflight = await runTrackBSuccessorDeepSeek({
      argv: [root, "preflight"],
      environment: {}
    });
    assert.equal(preflight.status, "PREFLIGHT_VALIDATED_NO_NETWORK");
    let resolverAttempts = 0;
    let transportCalls = 0;
    const result = await runTrackBSuccessorDeepSeek({
      argv: [root, "run"],
      environment: { DEEPSEEK_API_KEY: "sentinel-successor-key" },
      dependencies: {
        now: advancingClock(),
        resolveAddresses: async () => {
          resolverAttempts += 1;
          if (resolverAttempts === 1) {
            const error = new Error("pre-send DNS failure");
            error.code = "ENOTFOUND";
            throw error;
          }
          return [{ address: "8.8.8.8", family: 4 }];
        },
        transport: async ({ body, secret }) => {
          transportCalls += 1;
          assert.equal(secret, "sentinel-successor-key");
          return acceptedResponse(body, transportCalls);
        }
      }
    });
    assert.equal(resolverAttempts, 2);
    assert.equal(transportCalls, 9);
    assert.equal(result.status, "ACCEPTED");
    assert.equal(result.automaticRetryPerformed, true);
    const runRoot = resolve(root, outputRootRelative, "run-v1");
    const outputBytes = await readFile(resolve(runRoot, "deepseek-opinion.json"));
    const output = JSON.parse(outputBytes.toString("utf8"));
    assert.equal(
      output.schemaVersion,
      "task-eval-003-track-b-successor-model-opinion-v1"
    );
    assert.equal(output.resolverAttemptCount, 2);
    assert.equal(output.safePreSendRetryCount, 1);
    assert.equal(output.automaticRetryPerformed, true);
    assert.equal(output.providerCallCount, 9);
    assert.equal(output.opinions.length, 9);
    const claim = JSON.parse(
      await readFile(resolve(runRoot, "deepseek-execution-claim.json"), "utf8")
    );
    assert.equal(
      claim.schemaVersion,
      "task-eval-003-track-b-successor-deepseek-execution-claim-v1"
    );
    const persisted = outputBytes.toString("utf8").toLowerCase();
    for (const forbidden of [
      "sentinel-successor-key",
      '"rawresponse":',
      '"reasoning_content":',
      "provider-response-1"
    ]) {
      assert.ok(!persisted.includes(forbidden));
    }
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});

test("successor exhausts only the bounded DNS retry before claim or network", async () => {
  const root = await prepareRun();
  try {
    let resolverAttempts = 0;
    let transportCalls = 0;
    await assert.rejects(
      () =>
        runTrackBSuccessorDeepSeek({
          argv: [root, "run"],
          environment: { DEEPSEEK_API_KEY: "sentinel-successor-key" },
          dependencies: {
            now: advancingClock(),
            resolveAddresses: async () => {
              resolverAttempts += 1;
              throw new Error("DNS rejected before send");
            },
            transport: async () => {
              transportCalls += 1;
              throw new Error("must not send");
            }
          }
        }),
      (error) =>
        error instanceof TrackBHoldoutRunnerExit && error.exitCode === 4
    );
    assert.equal(resolverAttempts, 2);
    assert.equal(transportCalls, 0);
    const runRoot = resolve(root, outputRootRelative, "run-v1");
    await assert.rejects(
      () => stat(resolve(runRoot, "deepseek-execution-claim.json")),
      /ENOENT/
    );
    const blocked = await readOnlyBlocked(runRoot);
    assert.equal(blocked.code, "ENDPOINT_DNS_REJECTED");
    assert.equal(blocked.detailCode, "SAFE_PRE_SEND_DNS_RETRY_EXHAUSTED");
    assert.equal(blocked.networkAttempted, false);
    assert.equal(blocked.automaticRetryPerformed, true);
    assert.equal(blocked.resolverAttemptCount, 2);
    assert.equal(blocked.safePreSendRetryCount, 1);
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});

test("successor never retries an HTTP-started schema failure", async () => {
  const root = await prepareRun();
  try {
    let transportCalls = 0;
    await assert.rejects(
      () =>
        runTrackBSuccessorDeepSeek({
          argv: [root, "run"],
          environment: { DEEPSEEK_API_KEY: "sentinel-successor-key" },
          dependencies: {
            now: advancingClock(),
            resolveAddresses: async () => [
              { address: "8.8.8.8", family: 4 }
            ],
            transport: async () => {
              transportCalls += 1;
              return {
                status: 200,
                contentTypeClass: "APPLICATION_JSON",
                body: Buffer.from(
                  JSON.stringify({
                    id: "provider-schema-invalid-1",
                    model: "deepseek-v4-pro",
                    created: 1_800_000_001,
                    choices: [
                      {
                        finish_reason: "stop",
                        message: { content: "{}" }
                      }
                    ]
                  }),
                  "utf8"
                )
              };
            }
          }
        }),
      (error) =>
        error instanceof TrackBHoldoutRunnerExit && error.exitCode === 7
    );
    assert.equal(transportCalls, 1);
    const blocked = await readOnlyBlocked(
      resolve(root, outputRootRelative, "run-v1")
    );
    assert.equal(blocked.code, "SCHEMA_INVALID_OR_EMPTY");
    assert.equal(blocked.detailCode, "OPINION_SCHEMA_INVALID");
    assert.equal(blocked.networkAttempted, true);
    assert.equal(blocked.automaticRetryPerformed, false);
    assert.equal(blocked.completedCallCount, 0);
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});

test("successor rejects an evaluator clock that precedes frozen dispatch", async () => {
  const root = await prepareRun();
  try {
    const runRoot = resolve(root, outputRootRelative, "run-v1");
    const dispatch = JSON.parse(
      await readFile(resolve(runRoot, "dispatch.json"), "utf8")
    );
    let transportCalls = 0;
    await assert.rejects(
      () =>
        runTrackBSuccessorDeepSeek({
          argv: [root, "run"],
          environment: { DEEPSEEK_API_KEY: "sentinel-successor-key" },
          dependencies: {
            now: () => new Date(Date.parse(dispatch.createdAt) - 1),
            resolveAddresses: async () => [
              { address: "8.8.8.8", family: 4 }
            ],
            transport: async () => {
              transportCalls += 1;
              throw new Error("must not send");
            }
          }
        }),
      /RUN_PRECEDES_DISPATCH/
    );
    assert.equal(transportCalls, 0);
    await assert.rejects(
      () => stat(resolve(runRoot, "deepseek-execution-claim.json")),
      /ENOENT/
    );
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});

function acceptedResponse(body, callNumber) {
  const request = JSON.parse(body.toString("utf8"));
  const input = JSON.parse(request.messages[1].content);
  const packet = input.packets[0];
  const occurrence = packet.candidateOccurrences.find((candidate) =>
    packet.admission.requiredBlockIds.includes(candidate.sourceAnchor.blockId)
  );
  return {
    status: 200,
    contentTypeClass: "APPLICATION_JSON",
    body: Buffer.from(
      JSON.stringify({
        id: `provider-response-${callNumber}`,
        model: "deepseek-v4-pro",
        created: 1_800_000_000 + callNumber,
        choices: [
          {
            finish_reason: "stop",
            message: {
              content: JSON.stringify({
                opinions: [
                  {
                    packetId: packet.packetId,
                    suggestedRole: packet.candidateRole,
                    selectedOccurrenceIds: [occurrence.occurrenceId],
                    selectedAnchorBlockIds: [occurrence.sourceAnchor.blockId],
                    abstain: false,
                    abstentionReason: null
                  }
                ]
              })
            }
          }
        ]
      }),
      "utf8"
    )
  };
}

async function prepareRun() {
  const root = await mkdtemp(resolve(tmpdir(), "cqcp-tbs-runner-"));
  await copyFixture(root);
  const now = new Date(Date.now() - 60_000);
  const createdAt = now.toISOString();
  const dispatchAt = new Date(now.getTime() + 60_000).toISOString();
  const expiresAt = new Date(now.getTime() + 3_600_000).toISOString();
  const nonce = `TBS1-${randomBytes(16).toString("hex")}`;
  assertRun(script("prepare-track-b-successor-corpus.mjs"), [root, createdAt]);
  assertRun(script("prepare-track-b-successor-human-challenge.mjs"), [
    root,
    createdAt,
    expiresAt,
    nonce
  ]);
  const outputRoot = resolve(root, outputRootRelative);
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
        "task-eval-003-track-b-successor-human-confirmation-v1",
      confirmed: true,
      challengeNonce: challenge.nonce,
      challengeSha256: sha256(challengeBytes),
      corpusSha256: challenge.corpusSha256,
      humanReviewDraftSha256: challenge.humanReviewDraftSha256,
      humanReviewDocumentSha256:
        challenge.humanReviewDocumentSha256,
      presealManifestSha256: challenge.presealManifestSha256,
      decisionsSha256: sha256(Buffer.from(JSON.stringify(decisions), "utf8")),
      decisions,
      confirmedBy: "Project Owner Test",
      confirmedAt: new Date(now.getTime() + 30_000).toISOString(),
      confirmationSource: "CODEX_THREAD_USER_CONFIRMATION",
      confirmationStatement:
        `Test confirmation ${challenge.nonce} ${challenge.corpusSha256}`
    }, null, 2)}\n`,
    "utf8"
  );
  assertRun(script("seal-track-b-successor-human-ground-truth.mjs"), [
    root,
    "confirmation.json",
    "create"
  ]);
  assertRun(script("prepare-track-b-successor-dispatch.mjs"), [root, dispatchAt]);
  return root;
}

async function copyFixture(root) {
  const copies = [
    [
      "apps/api-server/src/test/resources/track-b-successor-v1",
      ["source-signals.json", "proposed-decisions.json"]
    ],
    ["apps/api-server/src/test/resources/track-b-admission-corpus-v2", ["corpus.json"]],
    ["outputs/task-eval-002/track-b-holdout-v1", ["corpus.json"]],
    [
      "scripts/blind-evaluation",
      ["track-b-holdout-opinion-prompt.txt", "mvp002-standing-egress-grant.json"]
    ]
  ];
  for (const [relativeRoot, files] of copies) {
    const targetRoot = resolve(root, relativeRoot);
    await mkdir(targetRoot, { recursive: true });
    for (const file of files) {
      await cp(resolve(repoRoot, relativeRoot, file), resolve(targetRoot, file));
    }
  }
}

async function readOnlyBlocked(runRoot) {
  const blockedRoot = resolve(runRoot, "blocked");
  const names = await readdir(blockedRoot);
  assert.equal(names.length, 1);
  return JSON.parse(await readFile(resolve(blockedRoot, names[0]), "utf8"));
}

function assertRun(command, args) {
  const result = spawnSync(process.execPath, [command, ...args], {
    cwd: repoRoot,
    encoding: "utf8"
  });
  assert.equal(result.status, 0, result.stderr);
}

function advancingClock() {
  let value = Date.now() + 60_000;
  return () => {
    const result = new Date(value);
    value += 1_000;
    return result;
  };
}
