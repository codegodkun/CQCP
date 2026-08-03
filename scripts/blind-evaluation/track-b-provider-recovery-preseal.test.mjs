import assert from "node:assert/strict";
import { constants } from "node:fs";
import { access, readFile } from "node:fs/promises";
import { resolve } from "node:path";
import test from "node:test";

import {
  sha256,
  validateTrackBRecoveryCorpus
} from "./track-b-holdout-contract.mjs";
import {
  validateTrackBProviderRecoveryIndependence
} from "./track-b-provider-recovery-corpus-contract.mjs";

const root = resolve(process.cwd());
const at = (path) => resolve(root, ...path.split("/"));
const output = "outputs/task-eval-006/track-b-recovery-v1";

test("recovery corpus is disjoint, human-pending, and model-dark", async () => {
  const [corpusBytes, draftBytes, manifestBytes, challengeBytes] =
    await Promise.all([
      readFile(at(`${output}/corpus.json`)),
      readFile(at(`${output}/human-review-draft.json`)),
      readFile(at(`${output}/preseal-manifest.json`)),
      readFile(at(`${output}/human-confirmation-challenge.json`))
    ]);
  const corpus = validateTrackBRecoveryCorpus(
    JSON.parse(corpusBytes.toString("utf8"))
  );
  const draft = JSON.parse(draftBytes.toString("utf8"));
  const manifest = JSON.parse(manifestBytes.toString("utf8"));
  const challenge = JSON.parse(challengeBytes.toString("utf8"));
  const readJson = async (path) =>
    JSON.parse((await readFile(at(path))).toString("utf8"));
  const priorCorpora = await Promise.all(
    manifest.priorCorpusPaths.map(readJson)
  );
  const diagnosticInputs = await Promise.all(
    manifest.diagnosticInputPaths.map(readJson)
  );

  assert.deepEqual(
    validateTrackBProviderRecoveryIndependence({
      recoveryCorpus: corpus,
      priorCorpora,
      diagnosticInputs
    }),
    manifest.disjointness
  );
  assert.equal(draft.humanGroundTruthEstablished, false);
  assert.ok(draft.entries.every((entry) => entry.humanDecision === null));
  assert.equal(challenge.corpusSha256, sha256(corpusBytes));
  assert.equal(challenge.humanReviewDraftSha256, sha256(draftBytes));
  assert.equal(challenge.presealManifestSha256, sha256(manifestBytes));
  assert.equal(challenge.requiredDecisionCount, 12);
  assert.equal(challenge.modelInputCreated, false);
  assert.equal(challenge.evaluatorAccessAllowed, false);
  assert.equal(challenge.admissionNetworkCallAllowed, false);

  for (const forbidden of [
    "human-confirmation.json",
    "human-ground-truth.json",
    "run-v1/model-input.json",
    "run-v1/dispatch.json",
    "run-v1/codex-opinion.json",
    "run-v1/deepseek-execution-claim.json"
  ]) {
    await assert.rejects(access(at(`${output}/${forbidden}`), constants.F_OK));
  }
});
