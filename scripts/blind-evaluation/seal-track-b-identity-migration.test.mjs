import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { readFile } from "node:fs/promises";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

const scriptRoot = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptRoot, "../..");

test("accepted identity-only confirmation verifies the immutable v2 human seal", async () => {
  const confirmation =
    "outputs/task-eval-002/track-b-admission-corpus-v2/" +
    "identity-migration-confirmation.json";
  const result = spawnSync(
    process.execPath,
    [
      path.join(scriptRoot, "seal-track-b-identity-migration.mjs"),
      repoRoot,
      confirmation,
      "verify"
    ],
    { encoding: "utf8" }
  );
  assert.equal(result.status, 0, result.stderr);
  const summary = JSON.parse(result.stdout);
  assert.equal(summary.entryCount, 18);
  assert.equal(summary.externalEgressAuthorized, false);
  const seal = JSON.parse(
    await readFile(
      path.join(
        repoRoot,
        "outputs/task-eval-002/track-b-admission-corpus-v2/" +
          "human-ground-truth.json"
      ),
      "utf8"
    )
  );
  assert.equal(seal.confirmationKind, "IDENTITY_ONLY_CARRY_FORWARD");
  assert.equal(seal.externalEgressAuthorized, false);
});
