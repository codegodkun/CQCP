import { spawnSync } from "node:child_process";
import { readFile, writeFile, mkdir } from "node:fs/promises";
import { dirname, isAbsolute, relative, resolve } from "node:path";

import {
  sha256File,
  validateOpinionPayload
} from "./opinion-contract.mjs";
import { validateSealedChain } from "./blind-evidence-chain.mjs";
import { validateDeepSeekSeal } from "./deepseek-evidence-chain.mjs";

const repoRoot = resolve(process.argv[2] ?? ".");
const outputRoot = resolve(repoRoot, "outputs/task-eval-002");
const manifest = JSON.parse(
  await readFile(resolve(outputRoot, "freeze-manifest.json"), "utf8")
);
const sealedChain = await validateSealedChain(repoRoot);
const deepSeekSealedChain = await validateDeepSeekSeal(repoRoot);
const trackBAdmissionPath = resolve(outputRoot, "track-b-admission.json");
const trackBSealPath = resolve(outputRoot, "track-b-codex-seal.json");
const trackBAdmission = JSON.parse(
  await readFile(trackBAdmissionPath, "utf8")
);
const trackBSeal = JSON.parse(await readFile(trackBSealPath, "utf8"));
const trackBSealSha256 = await sha256File(trackBSealPath);
if (
  trackBSeal.schemaVersion !== "task-eval-002-track-b-codex-seal-v2" ||
  trackBSeal.status !== "CODEX_TRACK_B_COMPLETE_ZERO_CALL_ABSTENTION" ||
  trackBSeal.evaluationCount !== 27 ||
  trackBSeal.abstentionCount !== 27 ||
  trackBSeal.providerAdmission !== "NOT_ESTABLISHED_ZERO_ELIGIBLE_SAMPLE" ||
  trackBSeal.providerAdmission !== "NOT_ESTABLISHED_ZERO_ELIGIBLE_SAMPLE"
) {
  throw new Error("Track B evidence chain is invalid");
}
let trackBAdmissionReport = null;
if (trackBAdmission.schemaVersion === "task-eval-002-track-b-admission-v2") {
  if (
    trackBAdmission.evidence?.sealSha256 !== trackBSealSha256 ||
    trackBAdmission.modelCallsAllowed !== false ||
    trackBAdmission.providerAdmission !==
      "NOT_ESTABLISHED_ZERO_ELIGIBLE_SAMPLE" ||
    trackBAdmission.codexBlindEvaluation?.evaluationCount !== 27 ||
    trackBAdmission.codexBlindEvaluation?.abstentionCount !== 27
  ) {
    throw new Error("Track B v2 admission evidence chain is invalid");
  }
} else if (
  trackBAdmission.schemaVersion ===
    "task-eval-002-track-b-admission-v3"
) {
  const reportPath = resolveRelativeEvidence(
    trackBAdmission.admissionReportPath
  );
  const reportBytes = await readFile(reportPath);
  if (
    (await sha256File(reportPath)) !==
      trackBAdmission.admissionReportSha256
  ) {
    throw new Error("Track B v3 admission report hash changed");
  }
  trackBAdmissionReport = JSON.parse(reportBytes.toString("utf8"));
  const verification = spawnSync(
    process.execPath,
    [
      resolve(
        repoRoot,
        "scripts/blind-evaluation/seal-and-evaluate-track-b-admission.mjs"
      ),
      repoRoot,
      "verify"
    ],
    { cwd: repoRoot, encoding: "utf8" }
  );
  if (
    verification.status !== 0 ||
    trackBAdmissionReport.status !== trackBAdmission.status ||
    trackBAdmissionReport.providerAdmission !==
      trackBAdmission.providerAdmission ||
    trackBAdmission.modelCallsAllowed !==
      (trackBAdmission.status === "ADMITTED")
  ) {
    throw new Error("Track B v3 admission evidence chain is invalid");
  }
} else {
  throw new Error("Unknown Track B admission schema");
}
const opinionBySample = new Map(
  sealedChain.records.map((record) => [
    record.assignment.sampleId,
    record.document
  ])
);
const deepSeekOpinionBySample = new Map();
for (const record of deepSeekSealedChain.records) {
  deepSeekOpinionBySample.set(
    record.sampleId,
    JSON.parse(await readFile(resolve(repoRoot, record.opinionPath), "utf8"))
  );
}
const comparisons = [];

for (const sample of manifest.samples) {
  const blindPath = resolve(repoRoot, sample.blindInputPath);
  const groundTruthPath = resolve(
    repoRoot,
    "packages/test-fixtures/human-anchors",
    `${sample.sampleId}.json`
  );
  const actualPath = resolve(
    repoRoot,
    "outputs/task-034-mvp-e2e-acceptance/sample-results",
    `${sample.sampleId}.json`
  );
  if (
    (await sha256File(blindPath)) !== sample.blindInputSha256 ||
    (await sha256File(groundTruthPath)) !== sample.groundTruthPackageSha256 ||
    (await sha256File(actualPath)) !== sample.cqcpActualPackageSha256
  ) {
    throw new Error(`Sealed package changed: ${sample.sampleId}`);
  }

  const blind = JSON.parse(await readFile(blindPath, "utf8"));
  const opinionDocument = opinionBySample.get(sample.sampleId);
  const deepSeekOpinionDocument = deepSeekOpinionBySample.get(sample.sampleId);
  if (
    !opinionDocument ||
    opinionDocument.status !== "ACCEPTED" ||
    opinionDocument.inputSha256 !== sample.blindInputSha256 ||
    !deepSeekOpinionDocument ||
    deepSeekOpinionDocument.status !== "ACCEPTED" ||
    deepSeekOpinionDocument.inputSha256 !== sample.blindInputSha256
  ) {
    throw new Error(`Opinion identity mismatch: ${sample.sampleId}`);
  }
  const payload = validateOpinionPayload(blind, {
    opinions: opinionDocument.opinions
  });
  const groundTruth = JSON.parse(await readFile(groundTruthPath, "utf8"));
  const actual = JSON.parse(await readFile(actualPath, "utf8"));
  const deepSeekPayload = validateOpinionPayload(blind, {
    opinions: deepSeekOpinionDocument.opinions
  });
  const deepSeekOpinionByPoint = new Map(
    deepSeekPayload.opinions.map((opinion) => [
      opinion.reviewPointCode,
      opinion
    ])
  );

  const truthByPoint = new Map();
  for (const occurrence of groundTruth.occurrences) {
    if (!occurrence.includedInConsistencyEvaluation) continue;
    const values = truthByPoint.get(occurrence.reviewPointCode) ?? [];
    values.push(occurrence.comparisonResult);
    truthByPoint.set(occurrence.reviewPointCode, values);
  }
  const actualByPoint = new Map(
    actual.points.map((point) => [
      point.reviewPointCode,
      point.candidateComparison === "MATCH" ? "CONSISTENT" : "INCONSISTENT"
    ])
  );

  for (const opinion of payload.opinions) {
    const truthValues = truthByPoint.get(opinion.reviewPointCode) ?? [];
    const humanOpinion =
      truthValues.length === 0
        ? "NOT_ENOUGH_EVIDENCE"
        : truthValues.every((value) => value === "MATCH")
          ? "CONSISTENT"
          : "INCONSISTENT";
    const cqcpOpinion =
      actualByPoint.get(opinion.reviewPointCode) ?? "NOT_ENOUGH_EVIDENCE";
    const deepSeekOpinion = deepSeekOpinionByPoint.get(
      opinion.reviewPointCode
    );
    if (!deepSeekOpinion) {
      throw new Error(
        `DeepSeek opinion missing: ${sample.sampleId}/${opinion.reviewPointCode}`
      );
    }
    comparisons.push({
      sampleId: sample.sampleId,
      reviewPointCode: opinion.reviewPointCode,
      humanGroundTruth: humanOpinion,
      cqcpDeterministicOutput: cqcpOpinion,
      codexModelOpinion: opinion.opinion,
      deepSeekModelOpinion: deepSeekOpinion.opinion,
      codexMatchesHuman: opinion.opinion === humanOpinion,
      deepSeekMatchesHuman: deepSeekOpinion.opinion === humanOpinion,
      cqcpMatchesHuman: cqcpOpinion === humanOpinion,
      rootCause: "UNRESOLVED",
      rootCauseReason:
        "Current frozen evidence does not uniquely distinguish parser, extraction, resolver, SourceAnchor, or adjudication causes."
    });
  }
}

const report = {
  schemaVersion: "task-eval-002-unblind-report-v1",
  status:
    "TRACK_A_CODEX_AND_DEEPSEEK_COMPLETE_TRACK_B_ZERO_ELIGIBLE_SAMPLE",
  opinionSealSha256: sealedChain.sealSha256,
  deepSeekOpinionSealSha256: deepSeekSealedChain.sealSha256,
  modelOutputTerminology: "模型意见",
  trackA: {
    comparisons,
    codexMatchesHuman: comparisons.filter((item) => item.codexMatchesHuman).length,
    cqcpMatchesHuman: comparisons.filter((item) => item.cqcpMatchesHuman).length,
    total: comparisons.length
  },
  trackB: {
    status: trackBAdmission.status,
    codexOpinionSealSha256: trackBSealSha256,
    evaluationCount: trackBSeal.evaluationCount,
    abstentionCount: trackBSeal.abstentionCount,
    modelCallsAllowed: trackBAdmission.modelCallsAllowed,
    providerAdmission: trackBAdmission.providerAdmission,
    reason: trackBAdmission.blockingReasons.join(" "),
    admissionReportSha256:
      trackBAdmission.admissionReportSha256 ?? null,
    admissionMetrics: trackBAdmissionReport?.metrics ?? null
  },
  deepSeek: {
    status: "ACCEPTED_FAIL_CLOSED_RETRIES_RECORDED",
    evaluator: "deepseek-v4-pro",
    endpointHost: "api.deepseek.com",
    matchesHuman: comparisons.filter((item) => item.deepSeekMatchesHuman)
      .length,
    total: comparisons.length,
    opinionSealSha256: deepSeekSealedChain.sealSha256,
    reason:
      "Three input-hash-bound egress authorizations were recorded; only strict-schema, finish_reason=stop outputs were accepted."
  },
  causalAttribution: {
    status: "INSUFFICIENT_EVIDENCE",
    allowedFallback: "UNRESOLVED"
  }
};
const reportPath = resolve(outputRoot, "unblind-report.json");
await mkdir(dirname(reportPath), { recursive: true });
await writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");
process.stdout.write(
  `${JSON.stringify({
    status: report.status,
    total: comparisons.length,
    codexMatchesHuman: report.trackA.codexMatchesHuman,
    cqcpMatchesHuman: report.trackA.cqcpMatchesHuman
  })}\n`
);

function resolveRelativeEvidence(relativePath) {
  if (
    typeof relativePath !== "string" ||
    !relativePath ||
    isAbsolute(relativePath)
  ) {
    throw new Error("Track B evidence path is invalid");
  }
  const candidate = resolve(repoRoot, ...relativePath.split("/"));
  const rel = relative(repoRoot, candidate);
  if (
    !rel ||
    rel === ".." ||
    rel.startsWith(`..\\`) ||
    rel.startsWith("../")
  ) {
    throw new Error("Track B evidence path escapes repository");
  }
  return candidate;
}
