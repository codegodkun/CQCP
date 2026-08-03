import { spawnSync } from "node:child_process";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const [repoRoot = ".", createdAt = new Date().toISOString()] =
  process.argv.slice(2);
const delegate = resolve(
  dirname(fileURLToPath(import.meta.url)),
  "prepare-track-b-holdout-dispatch.mjs"
);
const result = spawnSync(
  process.execPath,
  [delegate, repoRoot, createdAt, "final"],
  { stdio: "inherit" }
);
if (result.error) throw result.error;
if (result.status !== 0) process.exitCode = result.status ?? 1;
