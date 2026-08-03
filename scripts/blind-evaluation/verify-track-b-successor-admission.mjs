import assert from "node:assert/strict";
import { execFileSync, spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import { readFile, stat } from "node:fs/promises";
import { dirname, resolve, sep } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

import {
  sealAndEvaluateTrackBSuccessor
} from "./seal-and-evaluate-track-b-holdout.mjs";
import {
  runTrackBSuccessorDeepSeek
} from "./track-b-holdout-deepseek-runner.mjs";
import {
  parseJsonBytesRejectDuplicateKeys
} from "./strict-json.mjs";

const OUTPUT_ROOT = "outputs/task-eval-003/track-b-successor-v1";
const RUN_ROOT = `${OUTPUT_ROOT}/run-v1`;
const REQUIRED_PATHS = Object.freeze([
  `${OUTPUT_ROOT}/corpus.json`,
  `${OUTPUT_ROOT}/human-review-draft.json`,
  `${OUTPUT_ROOT}/human-ground-truth-review.md`,
  `${OUTPUT_ROOT}/preseal-manifest.json`,
  `${OUTPUT_ROOT}/human-confirmation-challenge.json`,
  `${OUTPUT_ROOT}/human-ground-truth.json`,
  `${RUN_ROOT}/model-input.json`,
  `${RUN_ROOT}/provider-call-set.json`,
  `${RUN_ROOT}/dispatch.json`,
  `${RUN_ROOT}/derived-egress-authorization-receipt.json`,
  `${RUN_ROOT}/codex-agent-raw.json`,
  `${RUN_ROOT}/codex-execution-receipt.json`,
  `${RUN_ROOT}/codex-opinion.json`,
  `${RUN_ROOT}/deepseek-execution-claim.json`,
  `${RUN_ROOT}/deepseek-opinion.json`,
  `${RUN_ROOT}/unblind/admission-evaluation.json`,
  `${RUN_ROOT}/unblind/admission-seal.json`
]);

const sha256 = (bytes) =>
  createHash("sha256").update(bytes).digest("hex");

export async function verifyTrackBSuccessorAdmission({
  repoRoot: repoArg,
  environment = process.env
}) {
  const repoRoot = resolve(repoArg);
  const resolveRelative = (relativePath) => {
    const absolute = resolve(repoRoot, ...relativePath.split("/"));
    if (!absolute.startsWith(`${repoRoot}${sep}`)) {
      throw new Error("TRACK_B_SUCCESSOR_VERIFY_PATH_ESCAPED");
    }
    return absolute;
  };
  const evidence = [];
  const values = new Map();
  for (const relativePath of REQUIRED_PATHS) {
    const absolute = resolveRelative(relativePath);
    const metadata = await stat(absolute);
    assert.equal(metadata.isFile(), true, `Missing evidence: ${relativePath}`);
    const bytes = await readFile(absolute);
    evidence.push({
      path: relativePath,
      size: bytes.length,
      sha256: sha256(bytes)
    });
    if (relativePath.endsWith(".json")) {
      values.set(relativePath, parseJsonBytesRejectDuplicateKeys(bytes));
      rejectForbiddenPersistentKeys(values.get(relativePath), relativePath);
    }
  }

  const groundTruth = values.get(`${OUTPUT_ROOT}/human-ground-truth.json`);
  assert.equal(typeof groundTruth.confirmationPath, "string");
  assert.ok(groundTruth.confirmationPath);
  const confirmationPath = groundTruth.confirmationPath.replaceAll("\\", "/");
  const confirmationAbsolute = resolveRelative(confirmationPath);
  const confirmationBytes = await readFile(confirmationAbsolute);
  assert.equal(sha256(confirmationBytes), groundTruth.confirmationSha256);
  evidence.push({
    path: confirmationPath,
    size: confirmationBytes.length,
    sha256: sha256(confirmationBytes)
  });

  const humanSealVerifier = resolve(
    dirname(fileURLToPath(import.meta.url)),
    "seal-track-b-holdout-human-ground-truth.mjs"
  );
  const humanVerified = spawnSync(
    process.execPath,
    [
      humanSealVerifier,
      repoRoot,
      confirmationPath,
      "verify",
      "successor"
    ],
    { cwd: repoRoot, encoding: "utf8" }
  );
  assert.equal(humanVerified.status, 0, humanVerified.stderr);

  const preflight = await runTrackBSuccessorDeepSeek({
    argv: [repoRoot, "preflight"],
    environment: {}
  });
  assert.equal(preflight.status, "PREFLIGHT_VALIDATED_NO_NETWORK");
  assert.equal(preflight.networkAttempted, false);
  assert.equal(preflight.providerCallCount, 9);

  const sealVerification = await sealAndEvaluateTrackBSuccessor({
    repoRoot,
    evaluatedAt: "ignored-in-verify-mode",
    mode: "verify"
  });
  assert.equal(sealVerification.status, "ADMISSION_SEAL_VERIFIED");
  assert.equal(
    sealVerification.sealedStatus,
    "SEALED_GO_ALL_ADMISSION_DIMENSIONS_100_PERCENT"
  );
  assert.equal(
    sealVerification.providerAdmission,
    "ESTABLISHED_FOR_EVALUATION_SHADOW_GATE"
  );

  const dispatch = values.get(`${RUN_ROOT}/dispatch.json`);
  const codex = values.get(`${RUN_ROOT}/codex-opinion.json`);
  const deepSeek = values.get(`${RUN_ROOT}/deepseek-opinion.json`);
  const claim = values.get(`${RUN_ROOT}/deepseek-execution-claim.json`);
  const admissionSeal = values.get(
    `${RUN_ROOT}/unblind/admission-seal.json`
  );
  assert.ok(
    Date.parse(groundTruth.confirmedAt) <= Date.parse(dispatch.createdAt),
    "Human seal must precede dispatch"
  );
  assert.ok(
    Date.parse(dispatch.createdAt) <= Date.parse(codex.startedAt),
    "Dispatch must precede Codex evaluator"
  );
  assert.ok(
    Date.parse(dispatch.createdAt) <= Date.parse(deepSeek.startedAt),
    "Dispatch must precede DeepSeek evaluator"
  );
  assert.ok(
    Date.parse(deepSeek.startedAt) <= Date.parse(claim.claimedAt) &&
      Date.parse(claim.claimedAt) <= Date.parse(deepSeek.completedAt),
    "DeepSeek claim time is outside the execution"
  );
  assert.ok(
    Date.parse(codex.completedAt) <= Date.parse(admissionSeal.sealedAt) &&
      Date.parse(deepSeek.completedAt) <= Date.parse(admissionSeal.sealedAt),
    "Unblind seal must follow both evaluators"
  );
  assert.equal(deepSeek.providerCallCount, 9);
  assert.equal(deepSeek.providerResponses.length, 9);
  assert.equal(deepSeek.rawResponsePersisted, false);
  assert.equal(deepSeek.reasoningContentPersisted, false);
  assert.ok(deepSeek.resolverAttemptCount >= 1);
  assert.ok(deepSeek.resolverAttemptCount <= 2);
  assert.equal(
    deepSeek.safePreSendRetryCount,
    deepSeek.resolverAttemptCount - 1
  );
  assert.equal(
    deepSeek.automaticRetryPerformed,
    deepSeek.safePreSendRetryCount > 0
  );

  const actualSecret = environment.DEEPSEEK_API_KEY;
  const secretScanPerformed =
    typeof actualSecret === "string" && actualSecret.length > 0;
  if (secretScanPerformed) {
    await assertSecretAbsent(repoRoot, actualSecret);
  }

  return {
    schemaVersion:
      "task-eval-003-track-b-successor-verification-result-v1",
    status: "PASS",
    providerAdmission: sealVerification.providerAdmission,
    evidenceCount: evidence.length,
    evidence,
    chronology: {
      humanConfirmedAt: groundTruth.confirmedAt,
      dispatchCreatedAt: dispatch.createdAt,
      codexStartedAt: codex.startedAt,
      codexCompletedAt: codex.completedAt,
      deepSeekStartedAt: deepSeek.startedAt,
      deepSeekClaimedAt: claim.claimedAt,
      deepSeekCompletedAt: deepSeek.completedAt,
      admissionSealedAt: admissionSeal.sealedAt
    },
    providerCallCount: 9,
    zeroCallControlCount: 3,
    secretScanPerformed,
    rawResponsePersisted: false,
    reasoningContentPersisted: false,
    networkCallPerformedByVerification: false
  };
}

function rejectForbiddenPersistentKeys(value, label, path = "$") {
  if (Array.isArray(value)) {
    value.forEach((item, index) =>
      rejectForbiddenPersistentKeys(item, label, `${path}[${index}]`)
    );
    return;
  }
  if (value === null || typeof value !== "object") return;
  for (const [key, child] of Object.entries(value)) {
    if (/^(rawProviderResponse|rawResponse|reasoning_content)$/i.test(key)) {
      throw new Error(`Forbidden persistent key: ${label}:${path}.${key}`);
    }
    rejectForbiddenPersistentKeys(child, label, `${path}.${key}`);
  }
}

async function assertSecretAbsent(repoRoot, secret) {
  const candidates = execFileSync(
    "git",
    ["ls-files", "--cached", "--others", "--exclude-standard", "-z"],
    { cwd: repoRoot, encoding: "utf8", maxBuffer: 32 * 1024 * 1024 }
  )
    .split("\0")
    .filter(Boolean);
  const needle = Buffer.from(secret, "utf8");
  for (const relativePath of candidates) {
    const absolute = resolve(repoRoot, relativePath);
    let bytes;
    try {
      bytes = await readFile(absolute);
    } catch (error) {
      if (error?.code === "EISDIR" || error?.code === "ENOENT") continue;
      throw error;
    }
    if (bytes.includes(needle)) {
      throw new Error(`TRACK_B_SUCCESSOR_SECRET_PERSISTED:${relativePath}`);
    }
  }
}

if (
  process.argv[1] &&
  import.meta.url === pathToFileURL(resolve(process.argv[1])).href
) {
  const [repoArg = "."] = process.argv.slice(2);
  const result = await verifyTrackBSuccessorAdmission({
    repoRoot: repoArg,
    environment: process.env
  });
  process.stdout.write(`${JSON.stringify(result)}\n`);
}
