import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";

export const CORE_BASE_COMMIT =
  "1035739b751386176e47c6871738a62bff86de02";
export const CORE_SCOPE_VERSION = "mvp002-core-subject-v3";

const FORBIDDEN_CORE_CONTENT = new Map([
  [
    "docs/ARCHITECTURE.md",
    [
      "provider-attempt-outcome-contract-v1.json",
      "task-model-002-provider-attempt-outcome-contract-v1",
    ],
  ],
]);

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
  "browser-evidence-contract.mjs",
  "browser-evidence-contract.test.mjs",
  "Dockerfile.api-runtime",
  "capture-browser-evidence.mjs",
  "capture-runtime-provenance.mjs",
  "compose.acceptance.override.yml",
  "core-subject.mjs",
  "core-subject.test.mjs",
  "create-malicious-preview-docx.ps1",
  "derive-core-boundary.mjs",
  "freeze-core-audit-package.mjs",
  "freeze-core-audit-package.test.mjs",
  "nginx.acceptance.conf.template",
  "run-compose-acceptance.ps1",
  "run-core-verification.ps1",
  "run-formal-r7.ps1",
  "runtime-provenance-contract.mjs",
  "runtime-provenance-contract.test.mjs",
  "verify-browser-evidence.mjs",
  "verify-openapi.mjs",
  "verify-r7-evidence.mjs",
]);

const CORE_APP_PATHS = new Set([
  "apps/admin-web/src/App.test.tsx",
  "apps/admin-web/src/App.tsx",
  "apps/admin-web/src/access/ManagementAccessContext.tsx",
  "apps/admin-web/src/access/managementAccess.ts",
  "apps/admin-web/src/modelProfiles/ModelProfilesPage.test.tsx",
  "apps/admin-web/src/modelProfiles/ModelProfilesPage.tsx",
  "apps/admin-web/src/modelProfiles/api.ts",
  "apps/admin-web/src/modelProfiles/types.ts",
  "apps/admin-web/src/publicResult/PublicResultPage.tsx",
  "apps/admin-web/src/publicResult/PublicResultWorkbench.test.tsx",
  "apps/admin-web/src/publicResult/api.ts",
  "apps/admin-web/src/publicResult/types.ts",
  "apps/admin-web/src/reviewTasks/ReviewTaskListPage.test.tsx",
  "apps/admin-web/src/reviewTasks/ReviewTaskListPage.tsx",
  "apps/admin-web/src/reviewTasks/api.ts",
  "apps/admin-web/src/reviewTasks/types.ts",
  "apps/admin-web/src/styles.css",
  "apps/api-server/.dockerignore",
  "apps/api-server/Dockerfile",
  "apps/api-server/build.gradle.kts",
  "apps/api-server/src/main/java/com/cqcp/apiserver/evaluation/BlindEvaluationProjectionGenerator.java",
  "apps/api-server/src/main/java/com/cqcp/apiserver/modelgateway/AdminApiAuthenticationFilter.java",
  "apps/api-server/src/main/java/com/cqcp/apiserver/modelgateway/ModelConnectivityClient.java",
  "apps/api-server/src/main/java/com/cqcp/apiserver/modelgateway/ModelEndpointAllowlist.java",
  "apps/api-server/src/main/java/com/cqcp/apiserver/modelgateway/ModelProfileAdminController.java",
  "apps/api-server/src/main/java/com/cqcp/apiserver/modelgateway/ModelProfileAdminModels.java",
  "apps/api-server/src/main/java/com/cqcp/apiserver/modelgateway/ModelProfileAdminRepository.java",
  "apps/api-server/src/main/java/com/cqcp/apiserver/modelgateway/ModelProfileAdminService.java",
  "apps/api-server/src/main/java/com/cqcp/apiserver/modelgateway/ModelSecretReferenceAllowlist.java",
  "apps/api-server/src/main/java/com/cqcp/apiserver/modelgateway/ModelSecretResolver.java",
  "apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ConsistencyCandidateCollector.java",
  "apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ConsistencyPolicySnapshot.java",
  "apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ConsistencyRuntimeRelease.java",
  "apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ConsistencySetCollector.java",
  "apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/FamilyModelCallPlan.java",
  "apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/InMemoryTaskResultStore.java",
  "apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/InactivePaymentBranchClassifierV20260729.java",
  "apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/JdbcTaskExecutionPersistence.java",
  "apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/LocalReviewDocumentStore.java",
  "apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/MinimalCandidateResolver.java",
  "apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/MinimalReviewEngine.java",
  "apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ModelAssistEligibilityEvaluator.java",
  "apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ParserBackedReviewInputPreparer.java",
  "apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/PersistentTaskResultStore.java",
  "apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ReviewDocumentController.java",
  "apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ReviewDocumentModels.java",
  "apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ReviewDocumentRepository.java",
  "apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ReviewDocumentService.java",
  "apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ReviewTaskCreationService.java",
  "apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ReviewTaskListController.java",
  "apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ReviewTaskListModels.java",
  "apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ReviewTaskListRepository.java",
  "apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ReviewTaskListService.java",
  "apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/RuleSetActivationGate.java",
  "apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/RuntimeEvidencePacket.java",
  "apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/RuntimeRuleSetLoader.java",
  "apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/RuntimeRuleSetLoaderV20260729.java",
  "apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/RuntimeRuleSetSnapshot.java",
  "apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/SingleReviewWorker.java",
  "apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/TaskExecutionStateMachine.java",
  "apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/TaskResultQueryController.java",
  "apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/TaskResultQueryService.java",
  "apps/api-server/src/main/java/com/cqcp/apiserver/wordparser/DocxWordParserSpike.java",
  "apps/api-server/src/main/java/com/cqcp/apiserver/wordparser/WordParserSpikeDocument.java",
  "apps/api-server/src/main/resources/application.yml",
  "apps/api-server/src/main/resources/db/migration/V3__model_profile_secret_reference.sql",
  "apps/api-server/src/test/java/com/cqcp/apiserver/evaluation/BlindEvaluationProjectionGeneratorTest.java",
  "apps/api-server/src/test/java/com/cqcp/apiserver/modelgateway/AdminApiAuthenticationIntegrationTest.java",
  "apps/api-server/src/test/java/com/cqcp/apiserver/modelgateway/JdkModelConnectivityClientTest.java",
  "apps/api-server/src/test/java/com/cqcp/apiserver/modelgateway/ModelEndpointAllowlistTest.java",
  "apps/api-server/src/test/java/com/cqcp/apiserver/modelgateway/ModelProfileAdminControllerTest.java",
  "apps/api-server/src/test/java/com/cqcp/apiserver/modelgateway/ModelProfileAdminServiceTest.java",
  "apps/api-server/src/test/java/com/cqcp/apiserver/modelgateway/ModelProfilePostgresIntegrationTest.java",
  "apps/api-server/src/test/java/com/cqcp/apiserver/modelgateway/ModelProfileSecretNonDisclosureIntegrationTest.java",
  "apps/api-server/src/test/java/com/cqcp/apiserver/modelgateway/ModelSecretResolverTest.java",
  "apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/ConsistencyCandidateCollectorTest.java",
  "apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/ConsistencyRuntimeExecutionActivationTest.java",
  "apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/ConsistencySetCollectorTest.java",
  "apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/ExecutionBindingMigrationTest.java",
  "apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/HumanAnchorGroundTruthFixtureTest.java",
  "apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/LocalReviewDocumentStoreTest.java",
  "apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/MinimalCandidateResolverTest.java",
  "apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/MinimalReviewEngineTest.java",
  "apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/ModelAssistRuntimeSeamTest.java",
  "apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/ParserBackedEvidenceOverlapBaselineTest.java",
  "apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/ParserBackedReviewInputPreparerEvidenceTest.java",
  "apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/PersistentTaskResultStoreTest.java",
  "apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/ReviewDocumentControllerTest.java",
  "apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/ReviewDocumentServiceTest.java",
  "apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/ReviewExecutionStatusIntegrationTest.java",
  "apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/ReviewTaskListRepositoryTest.java",
  "apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/ReviewTaskListServiceTest.java",
  "apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/ReviewWorkbenchAuthenticationIntegrationTest.java",
  "apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/RuleSetActivationGateTest.java",
  "apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/RuntimeRuleSetLoaderTest.java",
  "apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/SingleReviewWorkerIntegrationTest.java",
  "apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/Task034MvpE2EAcceptanceHarnessTest.java",
  "apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/TaskExecutionStateMachineTest.java",
  "apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/TaskResultExactPostgresIntegrationTest.java",
  "apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/TaskResultQueryControllerTest.java",
  "apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/TaskResultQueryServiceTest.java",
  "apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/TrackBAdmissionCorpusRuntimeContractTest.java",
  "apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/VersionedRatioScopeV20260729Test.java",
  "apps/api-server/src/test/java/com/cqcp/apiserver/wordparser/DocxWordParserSpikeTest.java",
  "apps/api-server/src/test/resources/blind-evaluation-expected/blind-inputs/CQCP-MVP-DOCX-001.track-a.blind.json",
  "apps/api-server/src/test/resources/blind-evaluation-expected/blind-inputs/CQCP-MVP-DOCX-002.track-a.blind.json",
  "apps/api-server/src/test/resources/blind-evaluation-expected/blind-inputs/CQCP-MVP-DOCX-003.track-a.blind.json",
  "apps/api-server/src/test/resources/blind-evaluation-expected/freeze-manifest.json",
  "apps/api-server/src/test/resources/blind-evaluation-source/task-034-mvp-e2e-acceptance-v3/run-manifest.json",
  "apps/api-server/src/test/resources/blind-evaluation-source/task-034-mvp-e2e-acceptance/sample-results/CQCP-MVP-DOCX-001.json",
  "apps/api-server/src/test/resources/blind-evaluation-source/task-034-mvp-e2e-acceptance/sample-results/CQCP-MVP-DOCX-002.json",
  "apps/api-server/src/test/resources/blind-evaluation-source/task-034-mvp-e2e-acceptance/sample-results/CQCP-MVP-DOCX-003.json",
  "apps/api-server/src/test/resources/blind-evaluation-source/task-eval-002/track-b-inputs-v1/CQCP-MVP-DOCX-001.track-b.json",
  "apps/api-server/src/test/resources/blind-evaluation-source/task-eval-002/track-b-inputs-v1/CQCP-MVP-DOCX-002.track-b.json",
  "apps/api-server/src/test/resources/blind-evaluation-source/task-eval-002/track-b-inputs-v1/CQCP-MVP-DOCX-003.track-b.json",
  "apps/api-server/src/test/resources/blind-evaluation-source/task-eval-002/track-b-inputs-v1/manifest.json",
  "apps/api-server/src/test/resources/human-anchor-source/TASK-DATA-001-human-anchor-template.xlsx",
  "apps/api-server/src/test/resources/task-036-d1-evidence/task-034-mvp-e2e-acceptance-v2/console-summary.md",
  "apps/api-server/src/test/resources/task-036-d1-evidence/task-034-mvp-e2e-acceptance-v2/occurrence-comparison.csv",
  "apps/api-server/src/test/resources/task-036-d1-evidence/task-034-mvp-e2e-acceptance-v2/run-manifest.json",
  "apps/api-server/src/test/resources/task-036-d1-evidence/task-034-mvp-e2e-acceptance-v2/sample-results/CQCP-MVP-DOCX-001.json",
  "apps/api-server/src/test/resources/task-036-d1-evidence/task-034-mvp-e2e-acceptance-v2/sample-results/CQCP-MVP-DOCX-002.json",
  "apps/api-server/src/test/resources/task-036-d1-evidence/task-034-mvp-e2e-acceptance-v2/sample-results/CQCP-MVP-DOCX-003.json",
  "apps/api-server/src/test/resources/task-036-d1-evidence/task-034-mvp-e2e-acceptance-v3/production-branch-scope-ledger.json",
  "apps/api-server/src/test/resources/task-036-d1-evidence/task-034-mvp-e2e-acceptance-v3/run-manifest.json",
  "apps/api-server/src/test/resources/track-b-admission-corpus-v2/corpus.json",
  "apps/api-server/src/test/resources/track-b-admission-corpus-v2/eligibility-source-signals.json",
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
  if (CORE_APP_PATHS.has(normalized)) return true;
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

const CODE_EXTENSIONS = new Set([".java", ".js", ".mjs", ".ts", ".tsx"]);
const SCRIPT_RESOLUTION_SUFFIXES = [
  "",
  ".js",
  ".mjs",
  ".ts",
  ".tsx",
  "/index.js",
  "/index.mjs",
  "/index.ts",
  "/index.tsx",
];
const PROVIDER_A0_PATH_PATTERN =
  /(?:deepseek.*(?:adapter|provider)|provider.*adapter|modelcallintent|providerattempt)/i;
const PROVIDER_A0_CONTENT_MARKERS = [
  "ModelCallIntent",
  "ProviderAttemptOutcome",
  "reasoning_content",
  "/chat/completions",
];

function gitText(repoRoot, args) {
  const result = spawnSync("git", args, {
    cwd: repoRoot,
    encoding: "utf8",
    windowsHide: true,
  });
  assert.equal(
    result.status,
    0,
    `git ${args.join(" ")} failed: ${result.stderr ?? ""}`,
  );
  return result.stdout.trim();
}

function existedAtCoreBase(repoRoot, filePath) {
  const result = spawnSync(
    "git",
    ["cat-file", "-e", `${CORE_BASE_COMMIT}:${filePath}`],
    { cwd: repoRoot, encoding: "utf8", windowsHide: true },
  );
  return result.status === 0;
}

function resolveScriptDependency(repoRoot, importerPath, specifier) {
  const importerDirectory = path.dirname(path.resolve(repoRoot, importerPath));
  for (const suffix of SCRIPT_RESOLUTION_SUFFIXES) {
    const absolute = path.resolve(importerDirectory, `${specifier}${suffix}`);
    if (fs.existsSync(absolute) && fs.statSync(absolute).isFile()) {
      return normalizeRepoPath(path.relative(repoRoot, absolute));
    }
  }
  return normalizeRepoPath(
    path.relative(repoRoot, path.resolve(importerDirectory, specifier)),
  );
}

function scriptDependencies(repoRoot, filePath, text) {
  const dependencies = [];
  for (const match of text.matchAll(
    /^\s*(?:import|export)\s+(?:[^"'\n]*?\s+from\s+)?["'](\.[^"']+)["']/gm,
  )) {
    dependencies.push(
      resolveScriptDependency(repoRoot, filePath, match[1]),
    );
  }
  return dependencies;
}

function javaDependencies(repoRoot, filePath, text) {
  const dependencies = [];
  const sourceRoots = filePath.includes("/src/test/")
    ? [
        "apps/api-server/src/test/java",
        "apps/api-server/src/main/java",
      ]
    : ["apps/api-server/src/main/java"];
  for (const match of text.matchAll(
    /^\s*import\s+(?:static\s+)?(com\.cqcp\.[A-Za-z0-9_.*]+)\s*;/gm,
  )) {
    const segments = match[1].replace(/\.\*$/, "").split(".");
    let resolved = null;
    while (segments.length >= 3 && resolved === null) {
      const relativeClassPath = `${segments.join("/")}.java`;
      for (const sourceRoot of sourceRoots) {
        const candidate = `${sourceRoot}/${relativeClassPath}`;
        if (
          fs.existsSync(path.resolve(repoRoot, candidate)) ||
          existedAtCoreBase(repoRoot, candidate)
        ) {
          resolved = candidate;
          break;
        }
      }
      segments.pop();
    }
    if (resolved !== null) dependencies.push(resolved);
  }
  return dependencies;
}

export function assertCoreImportClosure(repoRoot, subjectPaths) {
  const queue = subjectPaths
    .filter((filePath) => CODE_EXTENSIONS.has(path.extname(filePath)))
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
    const dependencies = filePath.endsWith(".java")
      ? javaDependencies(repoRoot, filePath, text)
      : scriptDependencies(repoRoot, filePath, text);
    for (const dependency of dependencies) {
      assert.equal(
        dependency.startsWith("../") || path.isAbsolute(dependency),
        false,
        `Core module imports outside repository: ${filePath} -> ${dependency}`,
      );
      if (!isCoreSourcePath(dependency)) {
        assert.equal(
          existedAtCoreBase(repoRoot, dependency),
          true,
          `Core module imports excluded Provider/audit code: ${filePath} -> ${dependency}`,
        );
        continue;
      }
      assert.equal(
        isCoreSourcePath(dependency),
        true,
        `Core module imports excluded Provider/audit code: ${filePath} -> ${dependency}`,
      );
      if (CODE_EXTENSIONS.has(path.extname(dependency))) {
        queue.push(dependency);
      }
    }
  }
  return [...visited].sort();
}

export function assertCoreContentBoundary(repoRoot, subjectPaths) {
  const checked = [];
  for (const [filePath, forbiddenMarkers] of FORBIDDEN_CORE_CONTENT) {
    if (!subjectPaths.includes(filePath)) continue;
    const absolutePath = path.resolve(repoRoot, filePath);
    assert.equal(
      fs.existsSync(absolutePath) && fs.statSync(absolutePath).isFile(),
      true,
      `Core content-boundary file is missing: ${filePath}`,
    );
    const text = fs.readFileSync(absolutePath, "utf8");
    for (const marker of forbiddenMarkers) {
      assert.equal(
        text.includes(marker),
        false,
        `Core content imports excluded Provider contract marker: ${filePath} -> ${marker}`,
      );
    }
    checked.push(filePath);
  }
  return checked.sort();
}

export function deriveCoreBoundaryEvidence(repoRoot, subjectPaths) {
  const normalizedPaths = assertCoreChangedPaths(subjectPaths);
  const contentBoundary = assertCoreContentBoundary(repoRoot, normalizedPaths);
  const importClosure = assertCoreImportClosure(repoRoot, normalizedPaths);
  const providerSignals = [];
  for (const filePath of normalizedPaths) {
    if (
      filePath.startsWith("apps/") &&
      PROVIDER_A0_PATH_PATTERN.test(filePath)
    ) {
      providerSignals.push({ type: "PATH", path: filePath });
    }
    if (
      !filePath.startsWith("apps/") ||
      !CODE_EXTENSIONS.has(path.extname(filePath))
    ) {
      continue;
    }
    const absolutePath = path.resolve(repoRoot, filePath);
    if (!fs.existsSync(absolutePath) || !fs.statSync(absolutePath).isFile()) {
      continue;
    }
    const text = fs.readFileSync(absolutePath, "utf8");
    for (const marker of PROVIDER_A0_CONTENT_MARKERS) {
      if (text.includes(marker)) {
        providerSignals.push({ type: "CONTENT", path: filePath, marker });
      }
    }
  }
  const headCommit = gitText(repoRoot, ["rev-parse", "HEAD"]);
  const tree = gitText(repoRoot, ["rev-parse", `${headCommit}^{tree}`]);
  return {
    schemaVersion: "task-mvp-002-core-boundary-v1",
    status: providerSignals.length === 0 ? "PASS" : "FAIL",
    scopeVersion: CORE_SCOPE_VERSION,
    baseCommit: CORE_BASE_COMMIT,
    headCommit,
    tree,
    subjectPathCount: normalizedPaths.length,
    subjectPathsSha256: crypto
      .createHash("sha256")
      .update(`${JSON.stringify(normalizedPaths)}\n`)
      .digest("hex"),
    contentBoundary,
    importClosure,
    providerSignals,
    providerA0Included: providerSignals.length > 0,
  };
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
