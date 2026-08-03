import assert from "node:assert/strict";
import { access, readFile } from "node:fs/promises";
import { constants } from "node:fs";
import { resolve } from "node:path";
import test from "node:test";

import {
  sha256,
  validateTrackBFifthCorpus
} from "./track-b-holdout-contract.mjs";
import {
  validateTrackBFifthIndependence
} from "./track-b-fifth-contract.mjs";

const root = resolve(process.cwd());
const at = (path) => resolve(root, ...path.split("/"));
const output = "outputs/task-eval-005/track-b-fifth-v1";

test("fifth preseal is disjoint and model-dark", async () => {
  const [corpusBytes, manifestBytes, challengeBytes, diagnosticBytes] =
    await Promise.all([
      readFile(at(`${output}/corpus.json`)),
      readFile(at(`${output}/preseal-manifest.json`)),
      readFile(at(`${output}/human-confirmation-challenge.json`)),
      readFile(at(
        "outputs/task-eval-005/schema-diagnostic-v1/baseline/diagnostic-input.json"
      ))
    ]);
  const corpus = validateTrackBFifthCorpus(
    JSON.parse(corpusBytes.toString("utf8"))
  );
  const manifest = JSON.parse(manifestBytes.toString("utf8"));
  const challenge = JSON.parse(challengeBytes.toString("utf8"));
  const priorCorpora = await Promise.all([
    "apps/api-server/src/test/resources/track-b-admission-corpus-v2/corpus.json",
    "outputs/task-eval-002/track-b-holdout-v1/corpus.json",
    "outputs/task-eval-003/track-b-successor-v1/corpus.json",
    "outputs/task-eval-004/track-b-final-v1/corpus.json"
  ].map(async (path) => JSON.parse((await readFile(at(path))).toString("utf8"))));
  assert.deepEqual(validateTrackBFifthIndependence({
    fifthCorpus: corpus,
    priorCorpora,
    diagnosticInput: JSON.parse(diagnosticBytes.toString("utf8"))
  }), manifest.disjointness);
  assert.equal(challenge.corpusSha256, sha256(corpusBytes));
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
    "run-v1/deepseek-execution-claim.json"
  ]) {
    await assert.rejects(access(at(`${output}/${forbidden}`), constants.F_OK));
  }
});
