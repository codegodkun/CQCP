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
const sealPath = path.resolve(
  scriptRoot,
  "seal-track-b-human-ground-truth.mjs"
);
const dispatchPath = path.resolve(
  scriptRoot,
  "prepare-track-b-admission-dispatch.mjs"
);
const challengeGeneratorPath = path.resolve(
  scriptRoot,
  "prepare-track-b-human-challenge.mjs"
);
const egressChallengeGeneratorPath = path.resolve(
  scriptRoot,
  "prepare-track-b-egress-challenge.mjs"
);
const promptPath = path.resolve(
  scriptRoot,
  "track-b-admission-opinion-prompt.txt"
);
const sha256 = (bytes) => createHash("sha256").update(bytes).digest("hex");

test("Track B admission dispatch is impossible before human seal and excludes ground truth afterward", async () => {
  const root = await mkdtemp(path.join(os.tmpdir(), "cqcp-track-b-dispatch-"));
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
        dispatchPath,
        root,
        "2026-07-29T07:20:00.000Z",
        "/root/test_track_b_agent"
      ],
      { encoding: "utf8" }
    );
    assert.notEqual(result.status, 0);
    assert.match(result.stderr, /HUMAN_GROUND_TRUTH_NOT_SEALED/);
    result = spawnSync(
      process.execPath,
      [
        challengeGeneratorPath,
        root,
        "2026-07-29T07:05:00.000Z",
        "2026-07-30T07:05:00.000Z",
        "TBH-1123456789abcdef0123456789abcdef"
      ],
      { encoding: "utf8" }
    );
    assert.equal(result.status, 0, result.stderr);

    const outputRoot = path.join(
      root,
      "outputs/task-eval-002/track-b-admission-corpus-v2"
    );
    const corpusBytes = await readFile(path.join(outputRoot, "corpus.json"));
    const draftBytes = await readFile(
      path.join(outputRoot, "human-review-draft.json")
    );
    const reviewDocumentBytes = await readFile(
      path.join(outputRoot, "human-ground-truth-review.md")
    );
    const challengeBytes = await readFile(
      path.join(outputRoot, "human-confirmation-challenge.json")
    );
    const challenge = JSON.parse(challengeBytes.toString("utf8"));
    const draft = JSON.parse(draftBytes.toString("utf8"));
    const expected = draft.entries.map((entry) => ({
      caseId: entry.caseId,
      packetId: entry.packetId,
      expected: entry.proposedExpected
    }));
    const confirmationRelativePath =
      "outputs/task-eval-002/track-b-admission-corpus-v2/" +
      "human-confirmation.json";
    await writeFile(
      path.join(root, confirmationRelativePath),
      `${JSON.stringify(
        {
          schemaVersion: "task-eval-002-track-b-human-confirmation-v1",
          confirmed: true,
          challengeNonce: challenge.nonce,
          challengeSha256: sha256(challengeBytes),
          corpusSha256: sha256(corpusBytes),
          humanReviewDraftSha256: sha256(draftBytes),
          humanReviewDocumentSha256: sha256(reviewDocumentBytes),
          decisionsSha256: sha256(
            Buffer.from(JSON.stringify(expected), "utf8")
          ),
          decisions: expected,
          confirmedBy: "TEST_HUMAN_REVIEWER",
          confirmedAt: "2026-07-29T07:10:00.000Z",
          confirmationSource: "CODEX_THREAD_USER_CONFIRMATION",
          confirmationStatement:
            `确认 ${challenge.nonce} / ${challenge.corpusSha256}`
        },
        null,
        2
      )}\n`
    );
    result = spawnSync(
      process.execPath,
      [sealPath, root, confirmationRelativePath, "create"],
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
        dispatchPath,
        root,
        "2026-07-29T07:20:00.000Z",
        "/root/test_track_b_agent"
      ],
      { encoding: "utf8" }
    );
    assert.equal(result.status, 0, result.stderr);
    const runRoot = path.join(
      root,
      "outputs/task-eval-002/track-b-admission-run-v3"
    );
    const modelInputText = await readFile(
      path.join(runRoot, "model-input.json"),
      "utf8"
    );
    const modelInput = JSON.parse(modelInputText);
    const dispatch = JSON.parse(
      await readFile(path.join(runRoot, "dispatch.json"), "utf8")
    );
    const providerCallSetText = await readFile(
      path.join(runRoot, "provider-call-set.json"),
      "utf8"
    );
    const providerCallSet = JSON.parse(providerCallSetText);
    assert.equal(modelInput.packetCount, 15);
    assert.ok(
      modelInput.packets.every(
        (packet) => packet.admission.modelCallAllowed === true
      )
    );
    assert.doesNotMatch(
      modelInputText,
      /proposedExpected|humanDecision|ACCEPTED_HUMAN_GROUND_TRUTH/
    );
    assert.equal(dispatch.humanGroundTruthExcludedFromModelInput, true);
    assert.equal(dispatch.providerCallCount, 6);
    assert.equal(
      dispatch.providerRequestBuilderVersion,
      "track-b-provider-request-builder-v3"
    );
    assert.equal(providerCallSet.callCount, 6);
    assert.equal(providerCallSet.eligiblePacketCount, 15);
    assert.equal(
      dispatch.providerCallSetSha256,
      sha256(Buffer.from(providerCallSetText, "utf8"))
    );
    assert.equal(
      providerCallSet.calls.reduce(
        (total, call) => total + call.packetIds.length,
        0
      ),
      15
    );
    assert.ok(
      providerCallSet.calls.every(
        (call) =>
          new Set(call.requestedRoles).size ===
          call.requestedRoles.length
      )
    );
    assert.doesNotMatch(
      providerCallSetText,
      /proposedExpected|humanDecision|ACCEPTED_HUMAN_GROUND_TRUTH/
    );
    assert.equal(dispatch.egressAuthorizationRequired, true);
    assert.equal(
      dispatch.egressAuthorizationPath,
      "outputs/task-eval-002/track-b-admission-run-v3/" +
        "egress-authorization.json"
    );
    await assert.rejects(
      readFile(path.join(runRoot, "egress-authorization.json")),
      /ENOENT/
    );
    assert.equal(dispatch.eligiblePacketCount, 15);
    assert.equal(dispatch.zeroCallControlCount, 3);
    assert.equal(
      dispatch.egressAuthorizationChallengePath,
      "outputs/task-eval-002/track-b-admission-run-v3/" +
        "egress-authorization-challenge.json"
    );
    result = spawnSync(
      process.execPath,
      [
        egressChallengeGeneratorPath,
        root,
        "2026-07-29T07:20:10.000Z",
        "2026-07-29T08:20:10.000Z",
        "TBE-7123456789abcdef0123456789abcdef"
      ],
      { encoding: "utf8" }
    );
    assert.equal(result.status, 0, result.stderr);
    const egressChallenge = JSON.parse(
      await readFile(
        path.join(
          root,
          ...dispatch.egressAuthorizationChallengePath.split("/")
        ),
        "utf8"
      )
    );
    assert.equal(
      egressChallenge.modelInputSha256,
      dispatch.modelInputSha256
    );
    assert.equal(
      egressChallenge.dispatchSha256,
      sha256(await readFile(path.join(runRoot, "dispatch.json")))
    );
    assert.equal(egressChallenge.authorizedInputCount, 15);
    assert.equal(egressChallenge.excludedZeroCallControlCount, 3);
    assert.equal(egressChallenge.providerCallCount, 6);
    assert.equal(
      egressChallenge.providerCallSetSha256,
      dispatch.providerCallSetSha256
    );
    assert.equal(egressChallenge.outboundRequestSha256s.length, 6);
    const duplicateChallenge = spawnSync(
      process.execPath,
      [
        egressChallengeGeneratorPath,
        root,
        "2026-07-29T07:20:10.000Z",
        "2026-07-29T08:20:10.000Z",
        "TBE-7123456789abcdef0123456789abcdef"
      ],
      { encoding: "utf8" }
    );
    assert.notEqual(duplicateChallenge.status, 0);
    assert.match(duplicateChallenge.stderr, /EEXIST/);

    const secondDispatch = spawnSync(
      process.execPath,
      [
        dispatchPath,
        root,
        "2026-07-29T07:20:00.000Z",
        "/root/test_track_b_agent"
      ],
      { encoding: "utf8" }
    );
    assert.notEqual(secondDispatch.status, 0);
    assert.match(secondDispatch.stderr, /immutable and already exists/);
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});
