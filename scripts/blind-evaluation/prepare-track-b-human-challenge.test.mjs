import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import {
  mkdtemp,
  readFile,
  rm
} from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

const scriptRoot = path.dirname(fileURLToPath(import.meta.url));
const generatorPath = path.join(
  scriptRoot,
  "prepare-track-b-admission-corpus.mjs"
);
const challengePath = path.join(
  scriptRoot,
  "prepare-track-b-human-challenge.mjs"
);

test("human challenge is hash-bound, time-bounded and immutable", async () => {
  const root = await mkdtemp(path.join(os.tmpdir(), "cqcp-track-b-challenge-"));
  try {
    let result = spawnSync(
      process.execPath,
      [generatorPath, root, "2026-07-29T07:00:00.000Z"],
      { encoding: "utf8" }
    );
    assert.equal(result.status, 0, result.stderr);
    const args = [
      challengePath,
      root,
      "2026-07-29T07:05:00.000Z",
      "2026-07-30T07:05:00.000Z",
      "TBH-0123456789abcdef0123456789abcdef"
    ];
    result = spawnSync(process.execPath, args, { encoding: "utf8" });
    assert.equal(result.status, 0, result.stderr);
    const challenge = JSON.parse(
      await readFile(
        path.join(
          root,
          "outputs/task-eval-002/track-b-admission-corpus-v2/" +
            "human-confirmation-challenge.json"
        ),
        "utf8"
      )
    );
    assert.equal(challenge.requiredDecisionCount, 18);
    assert.equal(challenge.proposedAnswersAreNonAuthoritative, true);
    assert.match(challenge.corpusSha256, /^[a-f0-9]{64}$/);
    assert.match(challenge.humanReviewDocumentSha256, /^[a-f0-9]{64}$/);
    const second = spawnSync(process.execPath, args, { encoding: "utf8" });
    assert.notEqual(second.status, 0);
    assert.match(second.stderr, /EEXIST/);
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});
