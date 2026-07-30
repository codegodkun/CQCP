import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import { mkdtemp, readFile, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

import {
  validateTrackBAdmissionCorpus
} from "./track-b-admission-opinion-contract.mjs";

const scriptRoot = path.dirname(fileURLToPath(import.meta.url));
const generatorPath = path.resolve(
  scriptRoot,
  "prepare-track-b-admission-corpus.mjs"
);

test("admission corpus is blind, runtime-isomorphic, and keeps safety controls zero-call", async () => {
  const root = await mkdtemp(path.join(os.tmpdir(), "cqcp-track-b-admission-"));
  try {
    const result = spawnSync(
      process.execPath,
      [generatorPath, root, "2026-07-29T09:00:00.000Z"],
      { encoding: "utf8" }
    );
    assert.equal(result.status, 0, result.stderr);
    const outputRoot = path.join(
      root,
      "outputs/task-eval-002/track-b-admission-corpus-v2"
    );
    const corpusText = await readFile(
      path.join(outputRoot, "corpus.json"),
      "utf8"
    );
    const corpus = JSON.parse(corpusText);
    const corpusSha256 = createHash("sha256")
      .update(Buffer.from(corpusText))
      .digest("hex");
    assert.equal(
      validateTrackBAdmissionCorpus(corpus, corpusSha256).eligiblePackets
        .length,
      15
    );
    assert.equal(corpus.packetCount, 18);
    assert.equal(corpus.eligiblePacketCount, 15);
    assert.equal(corpus.mediumEligibleCount, 8);
    assert.equal(corpus.conflictedEligibleCount, 7);
    assert.equal(corpus.zeroCallControlCount, 3);
    assert.equal(corpus.groundTruthIncluded, false);
    assert.doesNotMatch(
      corpusText,
      /proposedExpected|humanDecision|groundTruthEstablished/
    );

    const allowedPacketKeys = [
      "schemaVersion",
      "packetId",
      "taskId",
      "executionId",
      "sampleId",
      "ruleSetVersion",
      "family",
      "reviewPointCode",
      "candidateRole",
      "candidateOccurrences",
      "coverageSignals",
      "budget",
      "admission",
      "requiredOutput"
    ].sort();
    for (const packet of corpus.packets) {
      assert.deepEqual(Object.keys(packet).sort(), allowedPacketKeys);
      assert.match(packet.packetId, /^EP-[a-f0-9]{64}$/);
      assert.equal(
        packet.requiredOutput.instruction,
        "Only assess supplied role/candidate/anchor evidence. Do not produce final business adjudication."
      );
      if (packet.admission.modelCallAllowed) {
        assert.equal(packet.admission.status, "ELIGIBLE");
        assert.ok(packet.candidateOccurrences.length > 0);
        assert.ok(
          packet.candidateOccurrences.every(
            (candidate) => candidate.sourceAnchor.reliable === true
          )
        );
      } else {
        assert.equal(packet.admission.status, "ZERO_CALL_REQUIRED");
      }
    }
    const highControl = corpus.packets.find(
      (packet) =>
        packet.admission.reasonCodes[0] === "DETERMINISTIC_HIGH_ZERO_CALL"
    );
    assert.ok(highControl);
    assert.equal(highControl.admission.modelCallAllowed, false);
    const tamperedControl = structuredClone(corpus);
    tamperedControl.packets.find(
      (packet) => packet.sampleId === "TB-CTL-HIGH-001"
    ).admission.modelCallAllowed = true;
    assert.throws(
      () => validateTrackBAdmissionCorpus(tamperedControl, corpusSha256),
      /identity|counts|invalid/
    );

    const sourceSignalsText = await readFile(
      path.join(outputRoot, "eligibility-source-signals.json"),
      "utf8"
    );
    const sourceSignals = JSON.parse(sourceSignalsText);
    assert.equal(
      sourceSignals.schemaVersion,
      "task-eval-002-track-b-eligibility-source-signals-v1"
    );
    assert.equal(sourceSignals.source, "INDEPENDENT_PRE_ADMISSION_RUNTIME_SIGNALS");
    assert.equal(sourceSignals.admissionFieldsIncluded, false);
    assert.equal(sourceSignals.inputCount, 18);
    assert.equal(sourceSignals.inputs.length, 18);
    assert.doesNotMatch(
      sourceSignalsText,
      /admissionReason|reasonCodes|modelCallAllowed|proposedExpected|humanDecision/
    );
    assert.deepEqual(
      sourceSignals.inputs.map((input) => input.sampleId),
      corpus.packets.map((packet) => packet.sampleId)
    );
    const highSource = sourceSignals.inputs.find(
      (input) => input.sampleId === "TB-CTL-HIGH-001"
    );
    assert.equal(highSource.confidenceLevel, "HIGH");
    assert.equal(highSource.modelAssistMode, "NONE");
    const noCandidateSource = sourceSignals.inputs.find(
      (input) => input.sampleId === "TB-CTL-NONE-001"
    );
    assert.equal(noCandidateSource.candidateSignals.length, 0);
    const budgetSource = sourceSignals.inputs.find(
      (input) => input.sampleId === "TB-CTL-BUDGET-001"
    );
    assert.equal(budgetSource.budgetComplete, false);

    const review = JSON.parse(
      await readFile(path.join(outputRoot, "human-review-draft.json"), "utf8")
    );
    assert.equal(review.humanGroundTruthEstablished, false);
    assert.equal(review.entries.length, 18);
    assert.ok(review.entries.every((entry) => entry.humanDecision === null));
    const reviewMarkdown = await readFile(
      path.join(outputRoot, "human-ground-truth-review.md"),
      "utf8"
    );
    assert.match(reviewMarkdown, /候选原文与 SourceAnchor 逐项核对/);
    assert.match(reviewMarkdown, /TB-MED-001/);
    assert.match(reviewMarkdown, /tb-med-001-b02/);
    assert.match(reviewMarkdown, /OCC-002/);
    assert.match(reviewMarkdown, /DETERMINISTIC_HIGH_ZERO_CALL/);
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});
