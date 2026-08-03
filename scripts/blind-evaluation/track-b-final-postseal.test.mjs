import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { cp, mkdtemp, mkdir, readFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { dirname, resolve } from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

import { sha256 } from "./track-b-holdout-contract.mjs";
import {
  runTrackBFinalDeepSeek
} from "./track-b-holdout-deepseek-runner.mjs";

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const script = (name) =>
  resolve(repoRoot, "scripts/blind-evaluation", name);

test("final profile seals exact human confirmation and freezes 9x1 dispatch", async () => {
  const root = await mkdtemp(resolve(tmpdir(), "cqcp-tbf-postseal-"));
  try {
    const relativeOutput = "outputs/task-eval-004/track-b-final-v1";
    await cp(resolve(repoRoot, relativeOutput), resolve(root, relativeOutput), {
      recursive: true
    });
    const scriptRoot = resolve(root, "scripts/blind-evaluation");
    await mkdir(scriptRoot, { recursive: true });
    for (const name of [
      "track-b-holdout-opinion-prompt.txt",
      "mvp002-standing-egress-grant.json"
    ]) {
      await cp(
        resolve(repoRoot, "scripts/blind-evaluation", name),
        resolve(scriptRoot, name)
      );
    }
    const outputRoot = resolve(root, relativeOutput);
    await rm(resolve(outputRoot, "human-confirmation.json"), { force: true });
    await rm(resolve(outputRoot, "human-ground-truth.json"), { force: true });
    await rm(resolve(outputRoot, "run-v1"), { recursive: true, force: true });
    const challengeBytes = await readFile(
      resolve(outputRoot, "human-confirmation-challenge.json")
    );
    const challenge = JSON.parse(challengeBytes.toString("utf8"));
    const confirmedAt = new Date(
      Date.parse(challenge.createdAt) + 1_000
    ).toISOString();
    assertRun(
      run(script("prepare-track-b-final-human-confirmation.mjs"), [
        root,
        challenge.nonce,
        sha256(challengeBytes),
        challenge.corpusSha256,
        challenge.humanReviewDocumentSha256,
        confirmedAt
      ])
    );
    const confirmationRelative =
      `${relativeOutput}/human-confirmation.json`;
    assertRun(
      run(script("seal-track-b-final-human-ground-truth.mjs"), [
        root,
        confirmationRelative,
        "create"
      ])
    );
    assertRun(
      run(script("seal-track-b-final-human-ground-truth.mjs"), [
        root,
        confirmationRelative,
        "verify"
      ])
    );
    const dispatchAt = new Date(Date.parse(confirmedAt) + 1_000).toISOString();
    assertRun(
      run(script("prepare-track-b-final-dispatch.mjs"), [root, dispatchAt])
    );
    const runRoot = resolve(outputRoot, "run-v1");
    const inputBytes = await readFile(resolve(runRoot, "model-input.json"));
    const callSetBytes = await readFile(
      resolve(runRoot, "provider-call-set.json")
    );
    const dispatchBytes = await readFile(resolve(runRoot, "dispatch.json"));
    const input = JSON.parse(inputBytes.toString("utf8"));
    const callSet = JSON.parse(callSetBytes.toString("utf8"));
    const dispatch = JSON.parse(dispatchBytes.toString("utf8"));
    assert.equal(
      input.schemaVersion,
      "task-eval-004-track-b-final-model-input-v1"
    );
    assert.equal(input.packetCount, 9);
    assert.ok(input.packets.every((packet) => packet.sampleId.startsWith("TBF-")));
    assert.equal(
      callSet.schemaVersion,
      "task-eval-004-track-b-final-provider-call-set-v1"
    );
    assert.equal(callSet.callCount, 9);
    assert.ok(callSet.calls.every((call) => call.callId.startsWith("TBF-MC-")));
    assert.equal(
      dispatch.schemaVersion,
      "task-eval-004-track-b-final-dispatch-v1"
    );
    assert.equal(dispatch.modelInputSha256, sha256(inputBytes));
    assert.equal(dispatch.providerCallSetSha256, sha256(callSetBytes));
    assertRun(
      run(script("track-b-final-deepseek-runner.mjs"), [root, "preflight"])
    );
    let transportCalls = 0;
    const result = await runTrackBFinalDeepSeek({
      argv: [root, "run"],
      environment: { DEEPSEEK_API_KEY: "sentinel-final-test-key" },
      dependencies: {
        now: advancingClock(Date.parse(dispatchAt) + 1_000),
        resolveAddresses: async () => [
          { address: "8.8.8.8", family: 4 }
        ],
        transport: async ({ body, secret }) => {
          assert.equal(secret, "sentinel-final-test-key");
          transportCalls += 1;
          return acceptedResponse(body, transportCalls);
        }
      }
    });
    assert.equal(result.status, "ACCEPTED");
    assert.equal(transportCalls, 9);
    const claim = JSON.parse(
      await readFile(resolve(runRoot, "deepseek-execution-claim.json"), "utf8")
    );
    assert.equal(
      claim.schemaVersion,
      "task-eval-004-track-b-final-deepseek-execution-claim-v1"
    );
    const output = await readFile(resolve(runRoot, "deepseek-opinion.json"), "utf8");
    assert.ok(!output.includes("sentinel-final-test-key"));
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});

function run(command, args) {
  return spawnSync(process.execPath, [command, ...args], {
    cwd: repoRoot,
    encoding: "utf8"
  });
}

function assertRun(result) {
  assert.equal(result.status, 0, result.stderr);
}

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
        created: 1_800_100_000 + callNumber,
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

function advancingClock(initial) {
  let value = initial;
  return () => {
    const result = new Date(value);
    value += 1_000;
    return result;
  };
}
