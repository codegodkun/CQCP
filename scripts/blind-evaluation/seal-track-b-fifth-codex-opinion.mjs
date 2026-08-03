import { spawnSync } from "node:child_process";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const args = process.argv.slice(2);
if (args.length !== 6) {
  throw new Error(
    "Usage: node seal-track-b-fifth-codex-opinion.mjs " +
      "<repo-root> <raw-output-path> <agent-task> <agent-id> " +
      "<started-at> <completed-at>"
  );
}
const delegate = resolve(
  dirname(fileURLToPath(import.meta.url)),
  "seal-track-b-holdout-codex-opinion.mjs"
);
const result = spawnSync(process.execPath, [delegate, ...args, "fifth"], {
  stdio: "inherit"
});
if (result.error) throw result.error;
if (result.status !== 0) process.exitCode = result.status ?? 1;
