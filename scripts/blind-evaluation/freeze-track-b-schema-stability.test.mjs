import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { resolve } from "node:path";
import test from "node:test";

import { sha256 } from "./track-b-schema-diagnostic-contract.mjs";

test("stability freeze binds 24/24 selected version and no candidate 2", async () => {
  const root = resolve(
    process.cwd(),
    "outputs/task-eval-005/schema-diagnostic-v1"
  );
  const bytes = await readFile(resolve(root, "stability-freeze.json"));
  const freeze = JSON.parse(bytes.toString("utf8"));
  assert.equal(freeze.status, "GO_24_OF_24_STRICT_SCHEMA");
  assert.equal(freeze.selectedVersionAccepted, 24);
  assert.equal(freeze.selectedVersionRejected, 0);
  assert.equal(freeze.diagnosticCallCount, 36);
  assert.equal(freeze.diagnosticCallLimit, 60);
  assert.equal(freeze.candidate2Created, false);
  assert.equal(freeze.fifthCorpusCreationAllowed, true);
  assert.equal(freeze.taskEval004RetryAllowed, false);
  assert.equal(freeze.sixthCorpusAllowed, false);
  assert.equal(freeze.providerA0A1A2Allowed, false);
  const promptBytes = await readFile(resolve(
    process.cwd(),
    ...freeze.selectedPromptPath.split("/")
  ));
  assert.equal(sha256(promptBytes), freeze.selectedPromptSha256);
  assert.equal(JSON.stringify(freeze).includes("reasoning_content"), false);
});
