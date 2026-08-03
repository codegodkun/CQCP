import { spawnSync } from "node:child_process";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const [repoRoot, confirmationPath, mode = "create"] =
  process.argv.slice(2);
if (!repoRoot || !confirmationPath) {
  throw new Error(
    "Usage: node seal-track-b-successor-human-ground-truth.mjs " +
      "<repo-root> <confirmation-path> [create|verify]"
  );
}
const delegate = resolve(
  dirname(fileURLToPath(import.meta.url)),
  "seal-track-b-holdout-human-ground-truth.mjs"
);
const result = spawnSync(
  process.execPath,
  [delegate, repoRoot, confirmationPath, mode, "successor"],
  { stdio: "inherit" }
);
if (result.error) throw result.error;
if (result.status !== 0) process.exitCode = result.status ?? 1;
