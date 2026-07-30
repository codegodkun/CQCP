import assert from "node:assert/strict";
import crypto from "node:crypto";
import test from "node:test";
import {
  SEAM_MAINLINE_CONTRACT_PATH,
  SEAM_MAINLINE_REF,
  SEAM_MAINLINE_RELEASE_LOG_PATH,
  SEAM_SOURCE_PATHS,
  SEAM_VERIFICATION_COMMAND,
  calculateSeamContractIdentity,
  validateSeamMainlineEvidence,
} from "./seam-mainline-gate.mjs";

const sha256 = (value) =>
  crypto.createHash("sha256").update(value).digest("hex");

const fixture = () => {
  const contract = {
    schemaVersion:
      "task-036-model-assist-seam-mainline-contract-v1",
    createdAt: "2026-07-29T12:00:00.000Z",
    baselineHead: "1".repeat(40),
    sourceFiles: SEAM_SOURCE_PATHS.map((path, index) => ({
      path,
      sha256: sha256(`source-${index}`),
    })),
    verificationCommand: SEAM_VERIFICATION_COMMAND,
    contractIdentitySha256: null,
  };
  contract.contractIdentitySha256 =
    calculateSeamContractIdentity(contract);
  const contractSha256 = sha256(
    `${JSON.stringify(contract, null, 2)}\n`,
  );
  const mainlineCommit = "2".repeat(40);
  const releaseLogSha256 = sha256("test passed\n");
  const sourceFiles = contract.sourceFiles.map((file, index) => ({
    path: file.path,
    sha256: file.sha256,
    blobSha1: `${index + 3}`.repeat(40).slice(0, 40),
  }));
  const mainlineFiles = Object.fromEntries(
    sourceFiles.map((file) => [
      file.path,
      { sha256: file.sha256, blobSha1: file.blobSha1 },
    ]),
  );
  const release = {
    schemaVersion:
      "task-036-model-assist-seam-mainline-release-v1",
    verifiedAt: "2026-07-29T12:30:00.000Z",
    evaluatedRef: SEAM_MAINLINE_REF,
    commit: mainlineCommit,
    contractPath: SEAM_MAINLINE_CONTRACT_PATH,
    contractSha256,
    contractIdentitySha256: contract.contractIdentitySha256,
    sourceFiles,
    verificationCommand: SEAM_VERIFICATION_COMMAND,
    exitCode: 0,
    cleanExport: true,
    consoleLogPath: SEAM_MAINLINE_RELEASE_LOG_PATH,
    consoleLogSha256: releaseLogSha256,
  };
  return {
    contract,
    contractSha256,
    release,
    releaseLogSha256,
    mainlineCommit,
    mainlineFiles,
    validationNow: new Date("2026-07-29T13:00:00.000Z"),
  };
};

test("accepts an exact tested seam on the current mainline commit", () => {
  const result = validateSeamMainlineEvidence(fixture());
  assert.deepEqual(result, { valid: true, status: "GO" });
});

test("rejects mere path existence with changed mainline bytes", () => {
  const input = fixture();
  input.mainlineFiles[SEAM_SOURCE_PATHS[0]].sha256 = "f".repeat(64);
  const result = validateSeamMainlineEvidence(input);
  assert.equal(result.valid, false);
  assert.equal(result.status, "SEAM_MAINLINE_FILE_MISMATCH");
});

test("rejects a release receipt for another mainline commit", () => {
  const input = fixture();
  input.release.commit = "9".repeat(40);
  const result = validateSeamMainlineEvidence(input);
  assert.equal(result.valid, false);
  assert.equal(result.status, "SEAM_RELEASE_INVALID");
});

test("rejects a passing claim without an exact console hash", () => {
  const input = fixture();
  input.release.consoleLogSha256 = "e".repeat(64);
  const result = validateSeamMainlineEvidence(input);
  assert.equal(result.valid, false);
  assert.equal(result.status, "SEAM_RELEASE_INVALID");
});

test("rejects source list drift in the frozen contract", () => {
  const input = fixture();
  input.contract.sourceFiles.reverse();
  input.contract.contractIdentitySha256 =
    calculateSeamContractIdentity(input.contract);
  const result = validateSeamMainlineEvidence(input);
  assert.equal(result.valid, false);
  assert.equal(result.status, "SEAM_CONTRACT_FILE_ORDER_INVALID");
});
