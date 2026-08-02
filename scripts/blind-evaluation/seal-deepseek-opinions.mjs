import { readFile } from "node:fs/promises";
import { resolve } from "node:path";

import {
  DEEPSEEK_OPINION_REVALIDATION_SEAL_V3_PATH,
  DEEPSEEK_OPINION_SEAL_V2_PATH,
  validateDeepSeekExecutionRecords
} from "./deepseek-evidence-chain.mjs";
import {
  parseJsonRejectDuplicateKeys
} from "./strict-json.mjs";
import {
  writeStableBytesDirectChildren
} from "./stable-capability-create.mjs";
import {
  sha256File
} from "./opinion-contract.mjs";

const repoRoot = resolve(process.argv[2] ?? ".");
const sealedAt = process.argv[3];
if (!sealedAt || Number.isNaN(Date.parse(sealedAt))) {
  throw new Error("A valid ISO seal timestamp is required");
}

const evidence = await validateDeepSeekExecutionRecords(repoRoot);
const priorSealPath = resolve(repoRoot, DEEPSEEK_OPINION_SEAL_V2_PATH);
const priorSealBytes = await readFile(priorSealPath);
const priorSeal = parseJsonRejectDuplicateKeys(
  priorSealBytes.toString("utf8")
);
if (
  priorSeal.schemaVersion !== "task-eval-002-deepseek-opinion-seal-v2" ||
  priorSeal.status !== "SEALED_AUTHORIZED_BLIND_MODEL_OPINIONS" ||
  priorSeal.manifestSha256 !== evidence.manifestSha256 ||
  JSON.stringify(priorSeal.records) !== JSON.stringify(evidence.records)
) {
  throw new Error("The prior DeepSeek opinion seal is invalid");
}
const strictJsonParserPath = "scripts/blind-evaluation/strict-json.mjs";
const seal = {
  schemaVersion:
    "task-eval-002-deepseek-opinion-revalidation-seal-v3",
  status: "SEALED_STRICT_JSON_REVALIDATION",
  sealedAt: new Date(sealedAt).toISOString(),
  authorizationOverlay:
    "PER_SAMPLE_HASH_BOUND_AUTHORIZATION_SEPARATE_FROM_IMMUTABLE_FREEZE",
  endpointHost: "api.deepseek.com",
  model: "deepseek-v4-pro",
  modelOutputTerminology: "模型意见",
  externalModelReceivedGroundTruth: false,
  externalModelReceivedCqcpActual: false,
  externalNetworkCallPerformed: false,
  rawProviderResponseRead: false,
  humanGroundTruthRead: false,
  priorSealPath: DEEPSEEK_OPINION_SEAL_V2_PATH,
  priorSealSha256: await sha256File(priorSealPath),
  inputIsolation:
    "deepseek-runner.mjs transmits only the frozen blind input projection and schema instructions.",
  priorCodexUnblindReportPresent:
    "YES_BUT_NOT_READ_OR_TRANSMITTED_BY_THE_DEEPSEEK_RUNNER",
  manifestPath: evidence.manifestPath,
  manifestSha256: evidence.manifestSha256,
  runnerPath: evidence.runnerPath,
  runnerSha256: evidence.runnerSha256,
  contractPath: evidence.contractPath,
  contractSha256: evidence.contractSha256,
  strictJsonParserPath,
  strictJsonParserSha256: await sha256File(
    resolve(repoRoot, strictJsonParserPath)
  ),
  records: evidence.records
};
const sealBytes = Buffer.from(`${JSON.stringify(seal, null, 2)}\n`, "utf8");
writeStableBytesDirectChildren({
  repoRoot,
  requiredRoot: "outputs/task-eval-002/",
  files: [
    {
      childName: DEEPSEEK_OPINION_REVALIDATION_SEAL_V3_PATH
        .split("/")
        .at(-1),
      bytes: sealBytes
    }
  ]
});
process.stdout.write(
  `${JSON.stringify({
    status: seal.status,
    sampleCount: seal.records.length,
    opinionCount: seal.records.reduce(
      (total, record) => total + record.opinionCount,
      0
    )
  })}\n`
);
