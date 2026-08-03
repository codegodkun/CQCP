import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const genericSealSource = await readFile(
  new URL("./seal-track-b-holdout-human-ground-truth.mjs", import.meta.url),
  "utf8"
);
const confirmationSource = await readFile(
  new URL(
    "./prepare-track-b-provider-recovery-human-confirmation.mjs",
    import.meta.url
  ),
  "utf8"
);
const wrapperSource = await readFile(
  new URL(
    "./seal-track-b-provider-recovery-human-ground-truth.mjs",
    import.meta.url
  ),
  "utf8"
);

test("recovery human seal uses the frozen TASK-EVAL-006 profile", () => {
  assert.match(genericSealSource, /validateTrackBRecoveryCorpus/);
  assert.match(
    genericSealSource,
    /task-eval-006-track-b-recovery-human-ground-truth-v1/
  );
  assert.match(
    confirmationSource,
    /task-eval-006-track-b-recovery-human-confirmation-v1/
  );
  assert.match(wrapperSource, /"recovery"/);
});

test("confirmation remains hash-bound and model-dark", () => {
  assert.match(confirmationSource, /expectedChallengeSha256/);
  assert.match(confirmationSource, /expectedCorpusSha256/);
  assert.match(confirmationSource, /expectedReviewDraftSha256/);
  assert.doesNotMatch(confirmationSource, /model-input|dispatch|provider/i);
  assert.match(genericSealSource, /modelInputCreated: false/);
  assert.match(genericSealSource, /networkCallAllowedByThisSeal: false/);
});
