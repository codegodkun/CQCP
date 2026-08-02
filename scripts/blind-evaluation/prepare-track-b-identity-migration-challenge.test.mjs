import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { cp, mkdir, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

const scriptRoot = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptRoot, "../..");
const generatorPath = path.join(
  scriptRoot,
  "prepare-track-b-admission-corpus.mjs"
);
const migrationPath = path.join(
  scriptRoot,
  "prepare-track-b-identity-migration-challenge.mjs"
);

test("identity migration preserves all 18 semantics and human expected decisions", async () => {
  const root = await mkdtemp(path.join(os.tmpdir(), "cqcp-track-b-migrate-"));
  try {
    const oldRoot = path.join(
      root,
      "outputs/task-eval-002/track-b-admission-corpus-v1"
    );
    await mkdir(oldRoot, { recursive: true });
    for (const name of ["corpus.json", "human-ground-truth.json"]) {
      await cp(
        path.join(
          repoRoot,
          "outputs/task-eval-002/track-b-admission-corpus-v1",
          name
        ),
        path.join(oldRoot, name)
      );
    }

    const generatedAt = "2026-07-29T15:00:00.000Z";
    let result = spawnSync(
      process.execPath,
      [generatorPath, root, generatedAt],
      { encoding: "utf8" }
    );
    assert.equal(result.status, 0, result.stderr);

    const newCorpusPath = path.join(
      root,
      "outputs/task-eval-002/track-b-admission-corpus-v2/corpus.json"
    );
    const newCorpus = JSON.parse(await readFile(newCorpusPath, "utf8"));
    newCorpus.packets[0].candidateOccurrences[0].candidateValue =
      "SEMANTIC_DRIFT";
    await writeFile(
      newCorpusPath,
      `${JSON.stringify(newCorpus, null, 2)}\n`
    );
    result = spawnSync(
      process.execPath,
      [
        migrationPath,
        root,
        "2026-07-29T15:01:00.000Z",
        "2026-07-30T15:01:00.000Z",
        "TBM-0123456789abcdef0123456789abcdef"
      ],
      { encoding: "utf8" }
    );
    assert.notEqual(result.status, 0);
    assert.match(
      result.stderr,
      /TRACK_B_IDENTITY_MIGRATION_SEMANTIC_DRIFT/
    );

    result = spawnSync(
      process.execPath,
      [generatorPath, root, generatedAt],
      { encoding: "utf8" }
    );
    assert.equal(result.status, 0, result.stderr);
    result = spawnSync(
      process.execPath,
      [
        migrationPath,
        root,
        "2026-07-29T15:01:00.000Z",
        "2026-07-30T15:01:00.000Z",
        "TBM-0123456789abcdef0123456789abcdef"
      ],
      { encoding: "utf8" }
    );
    assert.equal(result.status, 0, result.stderr);
    const summary = JSON.parse(result.stdout);
    assert.equal(summary.entryCount, 18);
    assert.equal(summary.semanticMatchCount, 18);
    assert.equal(summary.expectedMatchCount, 18);
    const challenge = JSON.parse(
      await readFile(
        path.join(
          root,
          "outputs/task-eval-002/track-b-admission-corpus-v2/" +
            "identity-migration-challenge.json"
        ),
        "utf8"
      )
    );
    assert.equal(challenge.externalEgressAuthorized, false);
    assert.equal(
      new Set(challenge.mappings.map((entry) => entry.newTaskId)).size,
      3
    );
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});
