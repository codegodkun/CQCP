import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

export const CORE_BASE_COMMIT =
  "1035739b751386176e47c6871738a62bff86de02";
export const CORE_SCOPE_VERSION = "mvp002-core-subject-v1";

const CORE_BLIND_SCRIPT_NAMES = new Set([
  "blind-evidence-chain.mjs",
  "blind-evidence-chain.test.mjs",
  "blind-opinion-prompt.txt",
  "deepseek-evidence-chain.mjs",
  "deepseek-evidence-chain.test.mjs",
  "deepseek-runner.mjs",
  "deepseek-secure-transport.mjs",
  "deepseek-secure-transport.test.mjs",
  "opinion-contract.mjs",
  "opinion-contract.test.mjs",
  "prepare-blind-dispatch.mjs",
  "prepare-track-b-admission-corpus.mjs",
  "prepare-track-b-admission-corpus.test.mjs",
  "prepare-track-b-admission-dispatch.mjs",
  "prepare-track-b-admission-dispatch.test.mjs",
  "prepare-track-b-dispatch.mjs",
  "prepare-track-b-egress-challenge.mjs",
  "prepare-track-b-human-challenge.mjs",
  "prepare-track-b-human-challenge.test.mjs",
  "prepare-track-b-identity-migration-challenge.mjs",
  "prepare-track-b-identity-migration-challenge.test.mjs",
  "provider-gate-decision.mjs",
  "provider-gate-decision.test.mjs",
  "seal-and-evaluate-track-b-admission.mjs",
  "seal-and-evaluate-track-b-admission.test.mjs",
  "seal-deepseek-opinions.mjs",
  "seal-opinions.mjs",
  "seal-track-b-admission-codex-opinion.mjs",
  "seal-track-b-human-ground-truth.mjs",
  "seal-track-b-human-ground-truth.test.mjs",
  "seal-track-b-identity-migration.mjs",
  "seal-track-b-identity-migration.test.mjs",
  "seam-mainline-gate.mjs",
  "seam-mainline-gate.test.mjs",
  "stable-capability-create.mjs",
  "stable-capability-create.test.mjs",
  "stable-capability-file.mjs",
  "stable-capability-file.test.mjs",
  "stable-create-posix.py",
  "stable-create-windows.ps1",
  "stable-file-posix.py",
  "stable-file-windows.ps1",
  "stable-interpreter-attestation.mjs",
  "stable-interpreter-attestation.test.mjs",
  "strict-json.mjs",
  "strict-json.test.mjs",
  "track-b-admission-authorization.mjs",
  "track-b-admission-authorization.test.mjs",
  "track-b-admission-constants.mjs",
  "track-b-admission-deepseek-runner.mjs",
  "track-b-admission-deepseek-runner.test.mjs",
  "track-b-admission-opinion-contract.mjs",
  "track-b-admission-opinion-contract.test.mjs",
  "track-b-admission-opinion-prompt.txt",
  "track-b-deepseek-execution-claim-contract.mjs",
  "track-b-deepseek-execution-claim-contract.test.mjs",
  "track-b-opinion-contract.mjs",
  "track-b-opinion-contract.test.mjs",
  "track-b-opinion-prompt.txt",
  "track-b-provider-request-contract.mjs",
  "track-b-provider-request-contract.test.mjs",
  "unblind.mjs",
  "verify-deepseek-seal.mjs",
  "verify-freeze.mjs",
]);

const CORE_MVP002_SCRIPT_NAMES = new Set([
  "Dockerfile.api-runtime",
  "capture-browser-upload-evidence.ps1",
  "capture-runtime-provenance.mjs",
  "core-subject.mjs",
  "core-subject.test.mjs",
  "create-malicious-preview-docx.ps1",
  "freeze-core-audit-package.mjs",
  "freeze-core-audit-package.test.mjs",
  "run-compose-acceptance.ps1",
  "run-core-verification.ps1",
  "run-formal-r7.ps1",
  "verify-browser-evidence.mjs",
  "verify-openapi.mjs",
  "verify-r7-evidence.mjs",
]);

const CORE_EXACT_PATHS = new Set([
  ".gitattributes",
  ".dockerignore",
  ".gitignore",
  "CURRENT_CONTEXT.md",
  "changelog/2026-07.md",
  "decisions/ADR-018-model-profile-secret-reference-and-provider-readiness.md",
  "decisions/ADR-019-blind-evaluation-and-model-activation-boundary.md",
  "decisions/ADR-020-review-workbench-management-access-boundary.md",
  "decisions/README.md",
  "deploy/compose/compose.yml",
  "docs/ARCHITECTURE.md",
  "docs/ai-review.md",
  "docs/backend.md",
  "docs/database.md",
  "docs/deployment.md",
  "docs/frontend.md",
  "packages/api-contracts/openapi.json",
  "packages/api-contracts/openapi.yaml",
  "packages/review-assets/README.md",
  "packages/review-assets/review-point-definitions/review-points-v20260715.1.json",
  "packages/review-assets/review-point-definitions/review-points-v20260729.1.json",
  "packages/review-assets/rule-sets/ruleset-v20260715.1.json",
  "packages/review-assets/rule-sets/ruleset-v20260729.1.json",
  "scripts/validate-review-assets.mjs",
  "scripts/validate-review-assets.test.mjs",
  "tasks/MVP_TASK_MAP.md",
  "tasks/active/TASK-034-mvp-e2e-human-anchor-acceptance-execution.md",
  "tasks/active/TASK-036-multi-occurrence-consistency-evidence-architecture-freeze.md",
  "tasks/active/TASK-EVAL-002-blind-semantic-evaluation.md",
  "tasks/active/TASK-MODEL-001-model-profile-secret-readiness.md",
  "tasks/active/TASK-MVP-002-review-workbench.md",
  "tasks/active/TASK_SPEC-034-B-monotonic-occurrence-bridge-v2.md",
  "tasks/active/TASK_SPEC-036-C1-consistency-set-runtime-core.md",
  "tasks/active/TASK_SPEC-036-C2-consistency-set-execution-activation.md",
  "tasks/active/TASK_SPEC-036-D1-versioned-ratio-scope-v20260729.1.md",
  "tasks/active/TASK_SPEC-036-D2-model-assist-evidence-packet-seam.md",
]);

const FORBIDDEN_CORE_PATHS = new Set([
  "apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/ProviderCanonicalIdentityGoldenTest.java",
  "decisions/ADR-021-provider-runtime-identity-recovery-and-capability-boundary.md",
  "decisions/ADR-022-mvp002-standing-egress-grant-and-derived-receipt.md",
  "tasks/active/TASK-MODEL-002-controlled-deepseek-provider.md",
  "tasks/active/TASK_SPEC-MODEL-002-A-provider-contract-shadow.md",
]);

export function normalizeRepoPath(filePath) {
  return filePath.replaceAll("\\", "/");
}

export function isCoreSourcePath(filePath) {
  const normalized = normalizeRepoPath(filePath);
  if (normalized.startsWith("outputs/")) return false;
  if (FORBIDDEN_CORE_PATHS.has(normalized)) return false;
  if (normalized.startsWith("apps/")) return true;
  if (CORE_EXACT_PATHS.has(normalized)) return true;
  if (normalized.startsWith("scripts/blind-evaluation/")) {
    return CORE_BLIND_SCRIPT_NAMES.has(path.posix.basename(normalized));
  }
  if (normalized.startsWith("scripts/mvp002/")) {
    return CORE_MVP002_SCRIPT_NAMES.has(path.posix.basename(normalized));
  }
  return false;
}

export function assertCoreChangedPaths(paths) {
  const normalized = [...new Set(paths.map(normalizeRepoPath))].sort();
  const rejected = normalized.filter((filePath) => !isCoreSourcePath(filePath));
  assert.deepEqual(
    rejected,
    [],
    `Core subject contains non-Core paths:\n${rejected.join("\n")}`,
  );
  return normalized;
}

export function assertCoreImportClosure(repoRoot, subjectPaths) {
  const queue = subjectPaths
    .filter((filePath) => filePath.endsWith(".mjs"))
    .map(normalizeRepoPath);
  const visited = new Set();
  while (queue.length > 0) {
    const filePath = queue.shift();
    if (visited.has(filePath)) continue;
    visited.add(filePath);
    assert.equal(
      isCoreSourcePath(filePath),
      true,
      `Non-Core module entered import closure: ${filePath}`,
    );
    const absolutePath = path.resolve(repoRoot, filePath);
    assert.equal(
      fs.existsSync(absolutePath) && fs.statSync(absolutePath).isFile(),
      true,
      `Core module is missing: ${filePath}`,
    );
    const text = fs.readFileSync(absolutePath, "utf8");
    for (const match of text.matchAll(
      /^\s*(?:import|export)\s+(?:[^"'\n]*?\s+from\s+)?["'](\.[^"']+)["']/gm,
    )) {
      const dependency = normalizeRepoPath(
        path.relative(
          repoRoot,
          path.resolve(path.dirname(absolutePath), match[1]),
        ),
      );
      assert.equal(
        dependency.startsWith("../") || path.isAbsolute(dependency),
        false,
        `Core module imports outside repository: ${filePath} -> ${dependency}`,
      );
      assert.equal(
        isCoreSourcePath(dependency),
        true,
        `Core module imports excluded Provider/audit code: ${filePath} -> ${dependency}`,
      );
      queue.push(dependency);
    }
  }
  return [...visited].sort();
}

export function coreScriptPaths() {
  return [
    ...[...CORE_BLIND_SCRIPT_NAMES].map(
      (name) => `scripts/blind-evaluation/${name}`,
    ),
    ...[...CORE_MVP002_SCRIPT_NAMES].map(
      (name) => `scripts/mvp002/${name}`,
    ),
    "scripts/validate-review-assets.mjs",
    "scripts/validate-review-assets.test.mjs",
  ].sort();
}
