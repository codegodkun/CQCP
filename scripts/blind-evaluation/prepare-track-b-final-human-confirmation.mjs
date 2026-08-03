import { spawnSync } from "node:child_process";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const args = process.argv.slice(2);
if (args.length !== 6) {
  throw new Error(
    "Usage: node prepare-track-b-final-human-confirmation.mjs " +
      "<repo-root> <nonce> <challenge-sha256> <corpus-sha256> " +
      "<review-sha256> <confirmed-at>"
  );
}
const delegate = resolve(
  dirname(fileURLToPath(import.meta.url)),
  "prepare-track-b-holdout-human-confirmation.mjs"
);
const result = spawnSync(
  process.execPath,
  [delegate, ...args, "final"],
  { stdio: "inherit" }
);
if (result.error) throw result.error;
if (result.status !== 0) process.exitCode = result.status ?? 1;
