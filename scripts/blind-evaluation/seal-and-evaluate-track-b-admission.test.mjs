import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import {
  copyFile,
  mkdir,
  mkdtemp,
  readFile,
  rm,
  writeFile
} from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

const scriptRoot = path.dirname(fileURLToPath(import.meta.url));
const generatorPath = path.resolve(
  scriptRoot,
  "prepare-track-b-admission-corpus.mjs"
);
const humanSealPath = path.resolve(
  scriptRoot,
  "seal-track-b-human-ground-truth.mjs"
);
const challengeGeneratorPath = path.resolve(
  scriptRoot,
  "prepare-track-b-human-challenge.mjs"
);
const dispatchGeneratorPath = path.resolve(
  scriptRoot,
  "prepare-track-b-admission-dispatch.mjs"
);
const egressChallengeGeneratorPath = path.resolve(
  scriptRoot,
  "prepare-track-b-egress-challenge.mjs"
);
const evaluatorPath = path.resolve(
  scriptRoot,
  "seal-and-evaluate-track-b-admission.mjs"
);
const codexOpinionSealerPath = path.resolve(
  scriptRoot,
  "seal-track-b-admission-codex-opinion.mjs"
);
const promptPath = path.resolve(
  scriptRoot,
  "track-b-admission-opinion-prompt.txt"
);
const sha256 = (bytes) => createHash("sha256").update(bytes).digest("hex");

test("Track B admission requires two sealed exact opinions plus three policy zero-call controls", async () => {
  const root = await mkdtemp(path.join(os.tmpdir(), "cqcp-track-b-evaluate-"));
  try {
    let result = spawnSync(
      process.execPath,
      [generatorPath, root, "2026-07-29T07:00:00.000Z"],
      { encoding: "utf8" }
    );
    assert.equal(result.status, 0, result.stderr);
    result = spawnSync(
      process.execPath,
      [
        challengeGeneratorPath,
        root,
        "2026-07-29T07:05:00.000Z",
        "2026-07-30T07:05:00.000Z",
        "TBH-2123456789abcdef0123456789abcdef"
      ],
      { encoding: "utf8" }
    );
    assert.equal(result.status, 0, result.stderr);
    const corpusRoot = path.join(
      root,
      "outputs/task-eval-002/track-b-admission-corpus-v2"
    );
    const corpusBytes = await readFile(path.join(corpusRoot, "corpus.json"));
    const draftBytes = await readFile(
      path.join(corpusRoot, "human-review-draft.json")
    );
    const reviewDocumentBytes = await readFile(
      path.join(corpusRoot, "human-ground-truth-review.md")
    );
    const challengeBytes = await readFile(
      path.join(corpusRoot, "human-confirmation-challenge.json")
    );
    const challenge = JSON.parse(challengeBytes.toString("utf8"));
    const draft = JSON.parse(draftBytes.toString("utf8"));
    const expectedEntries = draft.entries.map((entry) => ({
      caseId: entry.caseId,
      packetId: entry.packetId,
      expected: entry.proposedExpected
    }));
    const confirmationRelativePath =
      "outputs/task-eval-002/track-b-admission-corpus-v2/" +
      "human-confirmation.json";
    await writeFile(
      path.join(root, confirmationRelativePath),
      `${JSON.stringify({
        schemaVersion: "task-eval-002-track-b-human-confirmation-v1",
        confirmed: true,
        challengeNonce: challenge.nonce,
        challengeSha256: sha256(challengeBytes),
        corpusSha256: sha256(corpusBytes),
        humanReviewDraftSha256: sha256(draftBytes),
        humanReviewDocumentSha256: sha256(reviewDocumentBytes),
        decisionsSha256: sha256(
          Buffer.from(JSON.stringify(expectedEntries))
        ),
        decisions: expectedEntries,
        confirmedBy: "TEST_HUMAN_REVIEWER",
        confirmedAt: "2026-07-29T07:10:00.000Z",
        confirmationSource: "CODEX_THREAD_USER_CONFIRMATION",
        confirmationStatement:
          `确认 ${challenge.nonce} / ${challenge.corpusSha256}`
      })}\n`
    );
    result = spawnSync(
      process.execPath,
      [humanSealPath, root, confirmationRelativePath, "create"],
      { encoding: "utf8" }
    );
    assert.equal(result.status, 0, result.stderr);
    const targetPrompt = path.join(
      root,
      "scripts/blind-evaluation/track-b-admission-opinion-prompt.txt"
    );
    await mkdir(path.dirname(targetPrompt), { recursive: true });
    await copyFile(promptPath, targetPrompt);
    result = spawnSync(
      process.execPath,
      [
        dispatchGeneratorPath,
        root,
        "2026-07-29T07:20:00.000Z",
        "/root/test_track_b_admission_agent"
      ],
      { encoding: "utf8" }
    );
    assert.equal(result.status, 0, result.stderr);

    const runRoot = path.join(
      root,
      "outputs/task-eval-002/track-b-admission-run-v3"
    );
    const modelInput = JSON.parse(
      await readFile(path.join(runRoot, "model-input.json"), "utf8")
    );
    const dispatchBytes = await readFile(path.join(runRoot, "dispatch.json"));
    const dispatch = JSON.parse(dispatchBytes.toString("utf8"));
    const providerCallSet = JSON.parse(
      await readFile(
        path.join(root, ...dispatch.providerCallSetPath.split("/")),
        "utf8"
      )
    );
    result = spawnSync(
      process.execPath,
      [
        egressChallengeGeneratorPath,
        root,
        "2026-07-29T07:20:10.000Z",
        "2026-07-29T08:30:00.000Z",
        "TBE-4123456789abcdef0123456789abcdef"
      ],
      { encoding: "utf8" }
    );
    assert.equal(result.status, 0, result.stderr);
    const egressChallengeBytes = await readFile(
      path.join(
        root,
        ...dispatch.egressAuthorizationChallengePath.split("/")
      )
    );
    const egressChallenge = JSON.parse(
      egressChallengeBytes.toString("utf8")
    );
    const authorization = {
      schemaVersion: "task-eval-002-track-b-egress-authorization-v2",
      authorized: true,
      scope: "DEEPSEEK_TRACK_B_ADMISSION_EGRESS",
      purpose: "TASK-EVAL-002_TRACK_B_ADMISSION_ONLY",
      challengePath: dispatch.egressAuthorizationChallengePath,
      challengeSha256: sha256(egressChallengeBytes),
      challengeNonce: egressChallenge.nonce,
      modelInputSha256: dispatch.modelInputSha256,
      providerCallSetSha256: dispatch.providerCallSetSha256,
      providerRequestBuilderVersion:
        dispatch.providerRequestBuilderVersion,
      providerCallCount: dispatch.providerCallCount,
      dispatchSha256: sha256(dispatchBytes),
      endpointHost: "api.deepseek.com",
      model: "deepseek-v4-pro",
      authorizedInputCount: 15,
      excludedZeroCallControlCount: 3,
      zeroCallControlsExcluded: true,
      humanGroundTruthExcludedFromPayload: true,
      findingOrVerdictExcludedFromPayload: true,
      oneTimeExecution: true,
      approvedBy: "TEST_HUMAN_REVIEWER",
      approvedAt: "2026-07-29T07:20:30.000Z",
      expiresAt: "2026-07-29T08:20:30.000Z",
      confirmationSource: "CODEX_THREAD_USER_CONFIRMATION",
      confirmationStatement:
        `确认 ${egressChallenge.nonce} ${sha256(dispatchBytes)} ` +
        `${dispatch.modelInputSha256} ${dispatch.providerCallSetSha256} ` +
        "api.deepseek.com deepseek-v4-pro 6 15 3"
    };
    const authorizationBytes = Buffer.from(
      `${JSON.stringify(authorization, null, 2)}\n`
    );
    await writeFile(
      path.join(root, ...dispatch.egressAuthorizationPath.split("/")),
      authorizationBytes
    );
    const expectedByPacket = new Map(
      expectedEntries.map((entry) => [entry.packetId, entry.expected])
    );
    const opinions = modelInput.packets.map((packet) => ({
      packetId: packet.packetId,
      ...expectedByPacket.get(packet.packetId)
    }));
    const common = {
      schemaVersion: "task-eval-002-track-b-admission-model-opinion-v1",
      status: "ACCEPTED",
      track: modelInput.track,
      inputSha256: sha256(
        await readFile(path.join(runRoot, "model-input.json"))
      ),
      dispatchSha256: sha256(dispatchBytes),
      startedAt: "2026-07-29T07:21:00.000Z",
      completedAt: "2026-07-29T07:22:00.000Z",
      opinions
    };
    const rawCodexOutputPath = path.join(
      runRoot,
      "codex-agent-output.json"
    );
    await writeFile(
      rawCodexOutputPath,
      `${JSON.stringify({ opinions })}\n`
    );
    result = spawnSync(
      process.execPath,
      [
        codexOpinionSealerPath,
        root,
        rawCodexOutputPath,
        "/root/test_track_b_admission_agent",
        "test-agent-id",
        common.startedAt,
        common.completedAt
      ],
      { encoding: "utf8" }
    );
    assert.equal(result.status, 0, result.stderr);
    const deepSeekClaimBytes = Buffer.from(
      `${JSON.stringify({
        schemaVersion:
          "task-eval-002-track-b-deepseek-execution-claim-v2",
        status: "ONE_TIME_EXECUTION_CLAIMED",
        dispatchSha256: sha256(dispatchBytes),
        modelInputSha256: dispatch.modelInputSha256,
        providerCallSetSha256: dispatch.providerCallSetSha256,
        providerCallCount: dispatch.providerCallCount,
        egressAuthorizationSha256: sha256(authorizationBytes),
        egressChallengeSha256: sha256(egressChallengeBytes),
        endpointHost: "api.deepseek.com",
        model: "deepseek-v4-pro",
        resolverVersion: "request-scoped-pinned-dns-v2",
        pinnedAddressSetSha256: "a".repeat(64),
        claimedAt: "2026-07-29T07:21:30.000Z"
      }, null, 2)}\n`
    );
    await writeFile(
      path.join(runRoot, "deepseek-execution-claim.json"),
      deepSeekClaimBytes
    );
    await writeFile(
      path.join(runRoot, "deepseek-opinion.json"),
      `${JSON.stringify({
        ...common,
        schemaVersion:
          "task-eval-002-track-b-admission-model-opinion-v2",
        evaluator: "deepseek-v4-pro",
        authorizationSha256: sha256(authorizationBytes),
        authorization: {
          scope: authorization.scope,
          purpose: authorization.purpose,
          endpointHost: authorization.endpointHost,
          model: authorization.model,
          providerCallSetSha256:
            authorization.providerCallSetSha256,
          providerRequestBuilderVersion:
            authorization.providerRequestBuilderVersion,
          providerCallCount: authorization.providerCallCount,
          authorizedInputCount: authorization.authorizedInputCount,
          excludedZeroCallControlCount:
            authorization.excludedZeroCallControlCount,
          challengePath: authorization.challengePath,
          challengeSha256: authorization.challengeSha256,
          challengeNonce: authorization.challengeNonce,
          oneTimeExecution: authorization.oneTimeExecution,
          approvedBy: authorization.approvedBy,
          approvedAt: authorization.approvedAt,
          expiresAt: authorization.expiresAt,
          confirmationSource: authorization.confirmationSource
        },
        networkAttempted: true,
        executionMode: "run",
        executionClaimSha256: sha256(deepSeekClaimBytes),
        resolverVersion: "request-scoped-pinned-dns-v2",
        pinnedAddressSetSha256: "a".repeat(64),
        providerCallSetSha256: dispatch.providerCallSetSha256,
        providerCallCount: dispatch.providerCallCount,
        providerResponses: providerCallSet.calls.map(
          (call, index) => ({
            callId: call.callId,
            receiptStatus: "STRICT_SCHEMA_ACCEPTED",
            finishReason: "stop"
          })
        ),
        finishReason: "stop"
      })}\n`
    );
    result = spawnSync(
      process.execPath,
      [evaluatorPath, root, "seal", "2026-07-29T07:23:00.000Z"],
      { encoding: "utf8" }
    );
    assert.equal(result.status, 0, result.stderr);
    assert.equal(
      await fileExists(path.join(runRoot, "admission-report.json")),
      false
    );
    result = spawnSync(
      process.execPath,
      [evaluatorPath, root, "seal", "2026-07-29T07:23:00.000Z"],
      { encoding: "utf8" }
    );
    assert.notEqual(result.status, 0);
    assert.match(result.stderr, /EEXIST/);
    result = spawnSync(
      process.execPath,
      [evaluatorPath, root, "verify"],
      { encoding: "utf8" }
    );
    assert.equal(result.status, 0, result.stderr);
    await writeFile(
      path.join(root, ...dispatch.egressAuthorizationPath.split("/")),
      `${JSON.stringify({
        ...authorization,
        purpose: "TAMPERED"
      })}\n`
    );
    result = spawnSync(
      process.execPath,
      [evaluatorPath, root, "verify"],
      { encoding: "utf8" }
    );
    assert.notEqual(result.status, 0);
    assert.match(
      result.stderr,
      /Track B egress authorization changed/
    );
    await writeFile(
      path.join(root, ...dispatch.egressAuthorizationPath.split("/")),
      authorizationBytes
    );
    result = spawnSync(
      process.execPath,
      [evaluatorPath, root, "unblind"],
      { encoding: "utf8" }
    );
    assert.equal(result.status, 0, result.stderr);
    const report = JSON.parse(
      await readFile(path.join(runRoot, "admission-report.json"), "utf8")
    );
    assert.equal(report.status, "ADMITTED");
    assert.equal(report.metrics.codexEligibleMatchesHuman, 15);
    assert.equal(report.metrics.deepSeekEligibleMatchesHuman, 15);
    assert.equal(report.metrics.zeroCallControlsMatchHuman, 3);
    assert.equal(report.metrics.deterministicHighSentToModel, false);
    assert.equal(report.metrics.findingOrVerdictProduced, false);
    const admission = JSON.parse(
      await readFile(
        path.join(root, "outputs/task-eval-002/track-b-admission.json"),
        "utf8"
      )
    );
    assert.equal(admission.status, "ADMITTED");
    assert.equal(admission.providerAdmission, "ESTABLISHED");
    assert.equal(admission.modelCallsAllowed, true);
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});

async function fileExists(filePath) {
  try {
    await readFile(filePath);
    return true;
  } catch {
    return false;
  }
}
