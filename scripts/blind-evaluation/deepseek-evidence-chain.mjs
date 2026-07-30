import { readdir, readFile } from "node:fs/promises";
import { resolve } from "node:path";

import {
  sha256File,
  validateOpinionPayload
} from "./opinion-contract.mjs";
import {
  parseJsonRejectDuplicateKeys
} from "./strict-json.mjs";

export const DEEPSEEK_OPINION_SEAL_V2_PATH =
  "outputs/task-eval-002/deepseek-opinion-seal.json";
export const DEEPSEEK_OPINION_REVALIDATION_SEAL_V3_PATH =
  "outputs/task-eval-002/deepseek-opinion-revalidation-seal-v3.json";

const ACCEPTED_OUTPUT_FIELDS = new Set([
  "schemaVersion",
  "status",
  "track",
  "sampleId",
  "evaluator",
  "inputSha256",
  "networkAttempted",
  "finishReason",
  "opinions"
]);
const FORBIDDEN_PERSISTED_KEYS = new Set([
  "apiKey",
  "authorizationHeader",
  "prompt",
  "rawResponse",
  "reasoning_content",
  "reasoningContent"
]);

export async function validateDeepSeekExecutionRecords(repoRoot) {
  const outputRoot = resolve(repoRoot, "outputs/task-eval-002");
  const manifestPath = resolve(outputRoot, "freeze-manifest.json");
  const manifest = parseJsonRejectDuplicateKeys(
    await readFile(manifestPath, "utf8")
  );
  if (
    manifest.schemaVersion !== "task-eval-002-freeze-v1" ||
    manifest.externalEgressAuthorized !== false ||
    Object.hasOwn(manifest, "externalEgressAuthorizationPaths") ||
    manifest.samples?.length !== 3
  ) {
    throw new Error(
      "The immutable blind freeze must remain authorization-neutral"
    );
  }

  const runnerPath = resolve(
    repoRoot,
    "scripts/blind-evaluation/deepseek-runner.mjs"
  );
  const contractPath = resolve(
    repoRoot,
    "scripts/blind-evaluation/opinion-contract.mjs"
  );
  const opinionDirectory = resolve(outputRoot, "deepseek-opinions");
  const opinionFileNames = (await readdir(opinionDirectory))
    .filter((name) => name.endsWith(".json"))
    .sort();
  const records = [];

  for (const sample of manifest.samples) {
    const inputPath = resolve(repoRoot, sample.blindInputPath);
    if ((await sha256File(inputPath)) !== sample.blindInputSha256) {
      throw new Error(`DeepSeek blind input changed: ${sample.sampleId}`);
    }
    const blindInput = parseJsonRejectDuplicateKeys(
      await readFile(inputPath, "utf8")
    );
    const authorizationRelativePath =
      `outputs/task-eval-002/egress-authorizations/` +
      `${sample.sampleId}.deepseek.json`;
    const authorizationPath = resolve(repoRoot, authorizationRelativePath);
    const authorization = parseJsonRejectDuplicateKeys(
      await readFile(authorizationPath, "utf8")
    );
    if (
      authorization.schemaVersion !==
        "task-eval-002-egress-authorization-v1" ||
      authorization.authorized !== true ||
      authorization.scope !== "DEEPSEEK_EVALUATION_EGRESS" ||
      authorization.sampleId !== sample.sampleId ||
      authorization.inputSha256 !== sample.blindInputSha256 ||
      authorization.endpointHost !== "api.deepseek.com" ||
      authorization.model !== "deepseek-v4-pro" ||
      typeof authorization.purpose !== "string" ||
      !authorization.purpose.includes("TASK-EVAL-002") ||
      !Number.isFinite(Date.parse(authorization.approvedAt)) ||
      !Number.isFinite(Date.parse(authorization.expiresAt)) ||
      Date.parse(authorization.approvedAt) >=
        Date.parse(authorization.expiresAt)
    ) {
      throw new Error(`DeepSeek authorization mismatch: ${sample.sampleId}`);
    }

    const opinionRelativePath =
      `outputs/task-eval-002/deepseek-opinions/` +
      `${sample.sampleId}.deepseek.final-retry.json`;
    const opinionPath = resolve(repoRoot, opinionRelativePath);
    const opinion = parseJsonRejectDuplicateKeys(
      await readFile(opinionPath, "utf8")
    );
    requireExactKeys(opinion, ACCEPTED_OUTPUT_FIELDS, sample.sampleId);
    rejectForbiddenPersistedKeys(opinion, sample.sampleId);
    if (
      opinion.schemaVersion !== "task-eval-002-model-opinion-v1" ||
      opinion.status !== "ACCEPTED" ||
      opinion.track !== "TRACK_A_FULL_DOCUMENT_SEMANTIC_OPINION" ||
      opinion.sampleId !== sample.sampleId ||
      opinion.evaluator !== "deepseek-v4-pro" ||
      opinion.inputSha256 !== sample.blindInputSha256 ||
      opinion.networkAttempted !== true ||
      opinion.finishReason !== "stop"
    ) {
      throw new Error(`DeepSeek accepted output mismatch: ${sample.sampleId}`);
    }
    validateOpinionPayload(blindInput, { opinions: opinion.opinions });

    const attemptFileNames = opinionFileNames.filter((name) =>
      name.startsWith(`${sample.sampleId}.deepseek`)
    );
    const attempts = [];
    for (const fileName of attemptFileNames) {
      const attemptPath = resolve(opinionDirectory, fileName);
      const attempt = parseJsonRejectDuplicateKeys(
        await readFile(attemptPath, "utf8")
      );
      rejectForbiddenPersistedKeys(attempt, `${sample.sampleId}/${fileName}`);
      if (attempt.inputSha256 !== sample.blindInputSha256) {
        throw new Error(`DeepSeek attempt identity mismatch: ${fileName}`);
      }
      attempts.push({
        path: `outputs/task-eval-002/deepseek-opinions/${fileName}`,
        sha256: await sha256File(attemptPath),
        status: attempt.status,
        code: attempt.code ?? null,
        detailCode: attempt.detailCode ?? null,
        networkAttempted: attempt.networkAttempted
      });
    }

    records.push({
      sampleId: sample.sampleId,
      inputPath: sample.blindInputPath,
      inputSha256: sample.blindInputSha256,
      authorizationPath: authorizationRelativePath,
      authorizationSha256: await sha256File(authorizationPath),
      authorizationApprovedAt: new Date(
        authorization.approvedAt
      ).toISOString(),
      authorizationExpiresAt: new Date(
        authorization.expiresAt
      ).toISOString(),
      opinionPath: opinionRelativePath,
      opinionSha256: await sha256File(opinionPath),
      opinionCount: opinion.opinions.length,
      attempts
    });
  }

  return {
    manifestPath: "outputs/task-eval-002/freeze-manifest.json",
    manifestSha256: await sha256File(manifestPath),
    runnerPath: "scripts/blind-evaluation/deepseek-runner.mjs",
    runnerSha256: await sha256File(runnerPath),
    contractPath: "scripts/blind-evaluation/opinion-contract.mjs",
    contractSha256: await sha256File(contractPath),
    records
  };
}

export async function validateDeepSeekSeal(repoRoot) {
  const evidence = await validateDeepSeekExecutionRecords(repoRoot);
  const priorSealPath = resolve(repoRoot, DEEPSEEK_OPINION_SEAL_V2_PATH);
  const priorSeal = parseJsonRejectDuplicateKeys(
    await readFile(priorSealPath, "utf8")
  );
  if (
    priorSeal.schemaVersion !== "task-eval-002-deepseek-opinion-seal-v2" ||
    priorSeal.status !== "SEALED_AUTHORIZED_BLIND_MODEL_OPINIONS" ||
    priorSeal.model !== "deepseek-v4-pro" ||
    priorSeal.endpointHost !== "api.deepseek.com" ||
    priorSeal.externalModelReceivedGroundTruth !== false ||
    priorSeal.externalModelReceivedCqcpActual !== false ||
    priorSeal.manifestSha256 !== evidence.manifestSha256 ||
    JSON.stringify(priorSeal.records) !== JSON.stringify(evidence.records)
  ) {
    throw new Error("DeepSeek opinion seal is invalid");
  }
  const sealPath = resolve(
    repoRoot,
    DEEPSEEK_OPINION_REVALIDATION_SEAL_V3_PATH
  );
  const seal = parseJsonRejectDuplicateKeys(
    await readFile(sealPath, "utf8")
  );
  const sealedAtMillis = Date.parse(seal.sealedAt);
  const strictJsonParserPath =
    "scripts/blind-evaluation/strict-json.mjs";
  if (
    seal.schemaVersion !==
      "task-eval-002-deepseek-opinion-revalidation-seal-v3" ||
    seal.status !== "SEALED_STRICT_JSON_REVALIDATION" ||
    seal.authorizationOverlay !==
      "PER_SAMPLE_HASH_BOUND_AUTHORIZATION_SEPARATE_FROM_IMMUTABLE_FREEZE" ||
    seal.model !== "deepseek-v4-pro" ||
    seal.endpointHost !== "api.deepseek.com" ||
    seal.modelOutputTerminology !== "模型意见" ||
    seal.externalModelReceivedGroundTruth !== false ||
    seal.externalModelReceivedCqcpActual !== false ||
    seal.externalNetworkCallPerformed !== false ||
    seal.rawProviderResponseRead !== false ||
    seal.humanGroundTruthRead !== false ||
    seal.priorSealPath !== DEEPSEEK_OPINION_SEAL_V2_PATH ||
    seal.priorSealSha256 !== await sha256File(priorSealPath) ||
    seal.manifestSha256 !== evidence.manifestSha256 ||
    seal.runnerSha256 !== evidence.runnerSha256 ||
    seal.contractSha256 !== evidence.contractSha256 ||
    seal.strictJsonParserPath !== strictJsonParserPath ||
    seal.strictJsonParserSha256 !==
      await sha256File(resolve(repoRoot, strictJsonParserPath)) ||
    JSON.stringify(seal.records) !== JSON.stringify(evidence.records) ||
    !Number.isFinite(sealedAtMillis) ||
    evidence.records.some(
      (record) =>
        Date.parse(record.authorizationApprovedAt) > sealedAtMillis ||
        Date.parse(record.authorizationExpiresAt) < sealedAtMillis
    )
  ) {
    throw new Error("DeepSeek opinion seal is invalid");
  }
  return {
    seal,
    sealSha256: await sha256File(sealPath),
    records: evidence.records
  };
}

function requireExactKeys(value, allowed, path) {
  const actual = Object.keys(value);
  if (
    actual.length !== allowed.size ||
    actual.some((key) => !allowed.has(key))
  ) {
    throw new Error(`Unexpected persisted DeepSeek fields: ${path}`);
  }
}

function rejectForbiddenPersistedKeys(value, path) {
  if (Array.isArray(value)) {
    value.forEach((item, index) =>
      rejectForbiddenPersistedKeys(item, `${path}[${index}]`)
    );
    return;
  }
  if (value === null || typeof value !== "object") return;
  for (const [key, child] of Object.entries(value)) {
    if (FORBIDDEN_PERSISTED_KEYS.has(key)) {
      throw new Error(`Forbidden persisted DeepSeek field: ${path}.${key}`);
    }
    rejectForbiddenPersistedKeys(child, `${path}.${key}`);
  }
}
