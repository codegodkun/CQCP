import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";

import {
  CORE_BASE_COMMIT,
  deriveCoreBoundaryEvidence,
  normalizeRepoPath,
} from "./core-subject.mjs";

const repoRoot = path.resolve(process.argv[2] ?? ".");
const outputPath = path.resolve(
  process.argv[3] ??
    path.join(
      repoRoot,
      "outputs/task-mvp-002/core-audit/verification/core-boundary.json",
    ),
);
const allowedRoot = `${path.join(
  repoRoot,
  "outputs/task-mvp-002",
)}${path.sep}`;
assert.ok(
  `${outputPath}${path.sep}`.startsWith(allowedRoot),
  "Core boundary evidence must remain under outputs/task-mvp-002",
);

const result = spawnSync(
  "git",
  [
    "diff",
    "--name-only",
    "-z",
    "--no-renames",
    CORE_BASE_COMMIT,
    "HEAD",
    "--",
  ],
  { cwd: repoRoot, encoding: "utf8", windowsHide: true },
);
assert.equal(result.status, 0, `git diff failed: ${result.stderr ?? ""}`);
const subjectPaths = result.stdout
  .split("\0")
  .filter(Boolean)
  .map(normalizeRepoPath)
  .sort((left, right) => left.localeCompare(right, "en"));
const evidence = deriveCoreBoundaryEvidence(repoRoot, subjectPaths);
assert.equal(
  evidence.providerA0Included,
  false,
  `Provider A0 signals entered Core: ${JSON.stringify(
    evidence.providerSignals,
  )}`,
);
assert.equal(evidence.status, "PASS");
fs.mkdirSync(path.dirname(outputPath), { recursive: true });
fs.writeFileSync(
  outputPath,
  `${JSON.stringify(evidence, null, 2)}\n`,
  "utf8",
);
process.stdout.write(
  `${JSON.stringify({
    status: evidence.status,
    scopeVersion: evidence.scopeVersion,
    subjectPathCount: evidence.subjectPathCount,
    providerA0Included: evidence.providerA0Included,
    importClosureCount: evidence.importClosure.length,
  })}\n`,
);
