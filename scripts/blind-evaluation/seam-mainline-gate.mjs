import crypto from "node:crypto";

export const SEAM_MAINLINE_CONTRACT_PATH =
  "outputs/task-model-002/seam-mainline-contract.json";
export const SEAM_MAINLINE_RELEASE_EVIDENCE_PATH =
  "outputs/task-model-002/seam-mainline-release-evidence.json";
export const SEAM_MAINLINE_RELEASE_LOG_PATH =
  "outputs/task-model-002/seam-mainline-release-test.log";
export const SEAM_MAINLINE_REF = "origin/master";
export const SEAM_VERIFICATION_COMMAND =
  "./gradlew test --tests com.cqcp.apiserver.reviewengine.ModelAssistRuntimeSeamTest --tests com.cqcp.apiserver.reviewengine.TrackBAdmissionCorpusRuntimeContractTest --no-daemon";
export const SEAM_SOURCE_PATHS = Object.freeze([
  "apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/FamilyModelCallPlan.java",
  "apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/ModelAssistEligibilityEvaluator.java",
  "apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/RuntimeEvidencePacket.java",
  "apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/ModelAssistRuntimeSeamTest.java",
  "apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/TrackBAdmissionCorpusRuntimeContractTest.java",
  "outputs/task-eval-002/track-b-admission-corpus-v2/eligibility-source-signals.json",
]);

const CONTRACT_SCHEMA =
  "task-036-model-assist-seam-mainline-contract-v1";
const RELEASE_SCHEMA =
  "task-036-model-assist-seam-mainline-release-v1";
const SHA256_PATTERN = /^[a-f0-9]{64}$/;
const SHA1_PATTERN = /^[a-f0-9]{40}$/;
const CONTRACT_KEYS = [
  "baselineHead",
  "contractIdentitySha256",
  "createdAt",
  "schemaVersion",
  "sourceFiles",
  "verificationCommand",
];
const RELEASE_KEYS = [
  "cleanExport",
  "commit",
  "consoleLogPath",
  "consoleLogSha256",
  "contractIdentitySha256",
  "contractPath",
  "contractSha256",
  "evaluatedRef",
  "exitCode",
  "schemaVersion",
  "sourceFiles",
  "verificationCommand",
  "verifiedAt",
];
const CONTRACT_FILE_KEYS = ["path", "sha256"];
const RELEASE_FILE_KEYS = ["blobSha1", "path", "sha256"];

const sha256 = (bytes) =>
  crypto.createHash("sha256").update(bytes).digest("hex");
const exactKeys = (value, expected) =>
  value !== null &&
  typeof value === "object" &&
  !Array.isArray(value) &&
  JSON.stringify(Object.keys(value).sort()) ===
    JSON.stringify([...expected].sort());
const isTimestamp = (value) =>
  typeof value === "string" && Number.isFinite(Date.parse(value));

export const calculateSeamContractIdentity = (contract) =>
  sha256(
    Buffer.from(
      JSON.stringify({
        baselineHead: contract.baselineHead,
        sourceFiles: contract.sourceFiles,
        verificationCommand: contract.verificationCommand,
      }),
      "utf8",
    ),
  );

export const validateSeamMainlineEvidence = ({
  contract,
  contractSha256,
  release,
  releaseLogSha256,
  mainlineCommit,
  mainlineFiles,
  validationNow = new Date(),
}) => {
  try {
    const nowMillis =
      validationNow instanceof Date
        ? validationNow.getTime()
        : Date.parse(validationNow);
    if (
      !exactKeys(contract, CONTRACT_KEYS) ||
      contract.schemaVersion !== CONTRACT_SCHEMA ||
      !SHA1_PATTERN.test(contract.baselineHead ?? "") ||
      !isTimestamp(contract.createdAt) ||
      Date.parse(contract.createdAt) > nowMillis + 5 * 60_000 ||
      contract.verificationCommand !== SEAM_VERIFICATION_COMMAND ||
      !Array.isArray(contract.sourceFiles) ||
      contract.sourceFiles.length !== SEAM_SOURCE_PATHS.length ||
      !SHA256_PATTERN.test(contract.contractIdentitySha256 ?? "") ||
      calculateSeamContractIdentity(contract) !==
        contract.contractIdentitySha256 ||
      !SHA256_PATTERN.test(contractSha256 ?? "")
    ) {
      throw new Error("SEAM_CONTRACT_INVALID");
    }
    const contractPaths = [];
    const contractPathSet = new Set();
    for (const file of contract.sourceFiles) {
      if (
        !exactKeys(file, CONTRACT_FILE_KEYS) ||
        !SEAM_SOURCE_PATHS.includes(file.path) ||
        contractPathSet.has(file.path) ||
        !SHA256_PATTERN.test(file.sha256 ?? "")
      ) {
        throw new Error("SEAM_CONTRACT_FILE_INVALID");
      }
      contractPaths.push(file.path);
      contractPathSet.add(file.path);
    }
    if (
      JSON.stringify(contractPaths) !==
      JSON.stringify(SEAM_SOURCE_PATHS)
    ) {
      throw new Error("SEAM_CONTRACT_FILE_ORDER_INVALID");
    }

    if (
      !exactKeys(release, RELEASE_KEYS) ||
      release.schemaVersion !== RELEASE_SCHEMA ||
      release.evaluatedRef !== SEAM_MAINLINE_REF ||
      release.commit !== mainlineCommit ||
      !SHA1_PATTERN.test(mainlineCommit ?? "") ||
      release.contractPath !== SEAM_MAINLINE_CONTRACT_PATH ||
      release.contractSha256 !== contractSha256 ||
      release.contractIdentitySha256 !==
        contract.contractIdentitySha256 ||
      release.verificationCommand !== SEAM_VERIFICATION_COMMAND ||
      release.exitCode !== 0 ||
      release.cleanExport !== true ||
      release.consoleLogPath !== SEAM_MAINLINE_RELEASE_LOG_PATH ||
      release.consoleLogSha256 !== releaseLogSha256 ||
      !SHA256_PATTERN.test(releaseLogSha256 ?? "") ||
      !isTimestamp(release.verifiedAt) ||
      Date.parse(release.verifiedAt) < Date.parse(contract.createdAt) ||
      Date.parse(release.verifiedAt) > nowMillis + 5 * 60_000 ||
      !Array.isArray(release.sourceFiles) ||
      release.sourceFiles.length !== contract.sourceFiles.length
    ) {
      throw new Error("SEAM_RELEASE_INVALID");
    }
    for (let index = 0; index < contract.sourceFiles.length; index += 1) {
      const expected = contract.sourceFiles[index];
      const releaseFile = release.sourceFiles[index];
      const mainlineFile = mainlineFiles?.[expected.path];
      if (
        !exactKeys(releaseFile, RELEASE_FILE_KEYS) ||
        releaseFile.path !== expected.path ||
        releaseFile.sha256 !== expected.sha256 ||
        !SHA1_PATTERN.test(releaseFile.blobSha1 ?? "") ||
        mainlineFile?.sha256 !== expected.sha256 ||
        mainlineFile?.blobSha1 !== releaseFile.blobSha1
      ) {
        throw new Error("SEAM_MAINLINE_FILE_MISMATCH");
      }
    }
    return { valid: true, status: "GO" };
  } catch (error) {
    return {
      valid: false,
      status:
        typeof error?.message === "string" && error.message.length > 0
          ? error.message
          : "INVALID",
    };
  }
};
