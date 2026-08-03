import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { randomBytes } from "node:crypto";
import { cp, mkdir, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { dirname, resolve } from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

import {
  sealAndEvaluateTrackBSuccessor
} from "./seal-and-evaluate-track-b-holdout.mjs";
import { sha256 } from "./track-b-holdout-contract.mjs";
import {
  runTrackBSuccessorDeepSeek
} from "./track-b-holdout-deepseek-runner.mjs";
import {
  verifyTrackBSuccessorAdmission
} from "./verify-track-b-successor-admission.mjs";

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const scriptPath = (name) =>
  resolve(repoRoot, "scripts/blind-evaluation", name);
const outputRootRelative = "outputs/task-eval-003/track-b-successor-v1";

test("successor verifies both blind opinions before unblind and seals exact GO", async () => {
  const root = await prepareRun();
  try {
    const runRoot = resolve(root, outputRootRelative, "run-v1");
    const input = JSON.parse(
      await readFile(resolve(runRoot, "model-input.json"), "utf8")
    );
    const dispatch = JSON.parse(
      await readFile(resolve(runRoot, "dispatch.json"), "utf8")
    );
    const proposals = JSON.parse(
      await readFile(
        resolve(
          root,
          "apps/api-server/src/test/resources/track-b-successor-v1/proposed-decisions.json"
        ),
        "utf8"
      )
    );
    const expectedByCase = new Map(
      proposals.entries.map((entry) => [entry.caseId, entry.proposedExpected])
    );
    const payload = {
      opinions: input.packets.map((packet) => ({
        packetId: packet.packetId,
        ...expectedByCase.get(packet.sampleId)
      }))
    };
    const rawRelativePath = `${outputRootRelative}/run-v1/codex-agent-raw.json`;
    await writeFile(
      resolve(root, ...rawRelativePath.split("/")),
      `${JSON.stringify(payload)}\n`,
      "utf8"
    );
    const dispatchTime = Date.parse(dispatch.createdAt);
    assertRun("seal-track-b-successor-codex-opinion.mjs", [
      root,
      rawRelativePath,
      "/root/track_b_successor_blind_eval",
      "agent-successor-test-001",
      new Date(dispatchTime + 1_000).toISOString(),
      new Date(dispatchTime + 61_000).toISOString()
    ]);

    let resolverAttempts = 0;
    let transportCalls = 0;
    await runTrackBSuccessorDeepSeek({
      argv: [root, "run"],
      environment: { DEEPSEEK_API_KEY: "sentinel-successor-unblind-key" },
      dependencies: {
        now: advancingClock(dispatchTime + 120_000),
        resolveAddresses: async () => {
          resolverAttempts += 1;
          if (resolverAttempts === 1) throw new Error("safe DNS miss");
          return [{ address: "8.8.4.4", family: 4 }];
        },
        transport: async ({ body }) => {
          transportCalls += 1;
          const request = JSON.parse(body.toString("utf8"));
          const providerInput = JSON.parse(request.messages[1].content);
          const packet = providerInput.packets[0];
          return {
            status: 200,
            contentTypeClass: "APPLICATION_JSON",
            body: Buffer.from(
              JSON.stringify({
                id: `successor-unblind-${transportCalls}`,
                model: "deepseek-v4-pro",
                created: 1_800_200_000 + transportCalls,
                choices: [
                  {
                    finish_reason: "stop",
                    message: {
                      content: JSON.stringify({
                        opinions: [
                          {
                            packetId: packet.packetId,
                            ...expectedByCase.get(packet.sampleId)
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
      }
    });
    assert.equal(resolverAttempts, 2);
    assert.equal(transportCalls, 9);
    const result = await sealAndEvaluateTrackBSuccessor({
      repoRoot: root,
      evaluatedAt: new Date(dispatchTime + 600_000).toISOString()
    });
    assert.equal(
      result.status,
      "SEALED_GO_ALL_ADMISSION_DIMENSIONS_100_PERCENT"
    );
    assert.equal(
      result.providerAdmission,
      "ESTABLISHED_FOR_EVALUATION_SHADOW_GATE"
    );
    assert.equal(result.sameHoldoutRetryAllowed, false);
    const reportBytes = await readFile(
      resolve(runRoot, "unblind/admission-evaluation.json")
    );
    const sealBytes = await readFile(
      resolve(runRoot, "unblind/admission-seal.json")
    );
    const report = JSON.parse(reportBytes.toString("utf8"));
    const seal = JSON.parse(sealBytes.toString("utf8"));
    assert.equal(
      report.schemaVersion,
      "task-eval-003-track-b-successor-unblind-report-v1"
    );
    assert.equal(report.blindOpinionsVerifiedBeforeGroundTruthRead, true);
    assert.equal(report.evaluation.allGatesPassed, true);
    assert.equal(
      seal.schemaVersion,
      "task-eval-003-track-b-successor-admission-seal-v1"
    );
    assert.equal(seal.reportSha256, sha256(reportBytes));
    assert.equal(seal.sameHoldoutRetryAllowed, false);
    assert.equal(seal.independentThreePartyAuditRequired, true);
    const codexOpinion = JSON.parse(
      await readFile(resolve(runRoot, "codex-opinion.json"), "utf8")
    );
    assert.equal(
      codexOpinion.schemaVersion,
      "task-eval-003-track-b-successor-model-opinion-v1"
    );
    const persisted = Buffer.concat([reportBytes, sealBytes])
      .toString("utf8")
      .toLowerCase();
    assert.ok(!persisted.includes("sentinel-successor-unblind-key"));
    assert.ok(!persisted.includes("proposedexpected"));

    const verified = await sealAndEvaluateTrackBSuccessor({
      repoRoot: root,
      evaluatedAt: "ignored-in-verify-mode",
      mode: "verify"
    });
    assert.equal(verified.status, "ADMISSION_SEAL_VERIFIED");
    assert.equal(
      verified.providerAdmission,
      "ESTABLISHED_FOR_EVALUATION_SHADOW_GATE"
    );
    const verification = await verifyTrackBSuccessorAdmission({
      repoRoot: root,
      environment: {}
    });
    assert.equal(verification.status, "PASS");
    assert.equal(verification.evidenceCount, 18);
    assert.equal(verification.providerCallCount, 9);
    assert.equal(verification.zeroCallControlCount, 3);
    assert.equal(verification.secretScanPerformed, false);
    assert.equal(verification.networkCallPerformedByVerification, false);
    await writeFile(
      resolve(runRoot, "unblind/admission-evaluation.json"),
      Buffer.concat([reportBytes, Buffer.from("\n", "utf8")])
    );
    await assert.rejects(
      () =>
        sealAndEvaluateTrackBSuccessor({
          repoRoot: root,
          evaluatedAt: "ignored-in-verify-mode",
          mode: "verify"
        }),
      /UNBLIND_REPORT_CHANGED/
    );
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});

async function prepareRun() {
  const root = await mkdtemp(resolve(tmpdir(), "cqcp-tbs-unblind-"));
  await copyFixture(root);
  const now = new Date(Date.now() - 60_000);
  const createdAt = now.toISOString();
  const dispatchAt = new Date(now.getTime() + 60_000).toISOString();
  const expiresAt = new Date(now.getTime() + 3_600_000).toISOString();
  const nonce = `TBS1-${randomBytes(16).toString("hex")}`;
  assertRun("prepare-track-b-successor-corpus.mjs", [root, createdAt]);
  assertRun("prepare-track-b-successor-human-challenge.mjs", [
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
  assertRun("seal-track-b-successor-human-ground-truth.mjs", [
    root,
    "confirmation.json",
    "create"
  ]);
  assertRun("prepare-track-b-successor-dispatch.mjs", [root, dispatchAt]);
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

function assertRun(name, args) {
  const result = spawnSync(process.execPath, [scriptPath(name), ...args], {
    cwd: repoRoot,
    encoding: "utf8"
  });
  assert.equal(result.status, 0, result.stderr);
}

function advancingClock(start) {
  let value = start;
  return () => {
    const result = new Date(value);
    value += 1_000;
    return result;
  };
}
