import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";

import {
  assertBrowserTransportEvidence,
  parseBrowserEventStream,
  parseNginxBrowserAccessLog
} from "./browser-evidence-contract.mjs";
import { assertRedactedResolvedComposeConfig } from "./runtime-provenance-contract.mjs";

const repoRoot = path.resolve(process.argv[2] ?? ".");
const evidenceRoot = path.join(
  repoRoot,
  "outputs/task-mvp-002/browser-evidence-current"
);
const sha256 = (bytes) =>
  crypto.createHash("sha256").update(bytes).digest("hex");
const filePath = (relativePath) =>
  path.join(repoRoot, ...relativePath.split("/"));
const fileBytes = (relativePath) => fs.readFileSync(filePath(relativePath));
const readJson = (relativePath) =>
  JSON.parse(fs.readFileSync(filePath(relativePath), "utf8"));
const assertFileReference = (reference, prefix = "outputs/task-mvp-002/") => {
  assert.equal(reference.path.startsWith(prefix), true, "evidence path escaped task root");
  const bytes = fileBytes(reference.path);
  if (reference.size !== undefined) assert.equal(bytes.length, reference.size);
  assert.equal(sha256(bytes), reference.sha256, `evidence changed: ${reference.path}`);
  return bytes;
};

const assertionsPath = path.join(evidenceRoot, "browser-assertions.json");
const evidence = JSON.parse(fs.readFileSync(assertionsPath, "utf8"));
assert.equal(evidence.schemaVersion, "task-mvp-002-browser-evidence-v5");
assert.equal(evidence.status, "PASS");

const captureManifestBytes = assertFileReference(evidence.captureManifest);
const capture = JSON.parse(captureManifestBytes.toString("utf8"));
assert.equal(capture.schemaVersion, "task-mvp-002-browser-capture-v1");
assert.equal(capture.status, "COMPLETE");
assert.equal(capture.browser.controlSurface, "DIRECT_CHROME_DEVTOOLS_PROTOCOL");
const eventStreamBytes = assertFileReference(capture.eventStream);
const accessLogBytes = assertFileReference(capture.serverAccessLog);
const events = parseBrowserEventStream(eventStreamBytes);
const accessEntries = parseNginxBrowserAccessLog(accessLogBytes);

const uploadEvidenceBytes = assertFileReference(evidence.browserUploadEvidence);
const upload = JSON.parse(uploadEvidenceBytes.toString("utf8"));
assert.equal(upload.schemaVersion, "task-mvp-002-browser-upload-v2");
assert.equal(upload.status, "PASS");
assert.equal(upload.uploadChannel, "CHROME_CDP_NATIVE_FILE_CHOOSER");
assert.deepEqual(upload.eventStream, capture.eventStream);
assert.deepEqual(upload.serverAccessLog, capture.serverAccessLog);

const transport = assertBrowserTransportEvidence(events, accessEntries, {
  uploadTaskId: upload.taskId,
  uploadExecutionId: upload.executionId,
  primaryTaskId: evidence.primaryExecution.taskId,
  primaryExecutionId: evidence.primaryExecution.executionId,
  maliciousTaskId: evidence.maliciousBodyExecution.taskId,
  maliciousExecutionId: evidence.maliciousBodyExecution.executionId
});
assert.deepEqual(capture.transport, transport);
assert.deepEqual(capture.identities.upload, {
  taskId: upload.taskId,
  executionId: upload.executionId
});

const runtimeProvenanceBytes = assertFileReference(evidence.runtimeProvenance);
const runtimeProvenance = JSON.parse(runtimeProvenanceBytes.toString("utf8"));
assert.equal(
  runtimeProvenance.subject.sourceStateSha256,
  evidence.runtimeProvenance.sourceStateSha256
);
assert.equal(
  runtimeProvenance.compose.services["api-server"].imageId,
  evidence.runtimeProvenance.apiImageId
);
assert.equal(
  runtimeProvenance.compose.services["admin-web"].imageId,
  evidence.runtimeProvenance.adminWebImageId
);
const resolvedComposeBytes = fileBytes(runtimeProvenance.compose.resolvedConfig.path);
assert.equal(resolvedComposeBytes.length, runtimeProvenance.compose.resolvedConfig.size);
assert.equal(sha256(resolvedComposeBytes), runtimeProvenance.compose.resolvedConfig.sha256);
assertRedactedResolvedComposeConfig(resolvedComposeBytes);
const provenanceVerification = spawnSync(
  process.execPath,
  [
    path.join(repoRoot, "scripts/mvp002/capture-runtime-provenance.mjs"),
    repoRoot,
    "verify",
    path.dirname(filePath(evidence.runtimeProvenance.path)),
    "cqcp-mvp002-acceptance",
    "deploy/compose/compose.yml",
    "scripts/mvp002/compose.acceptance.override.yml"
  ],
  { cwd: repoRoot, encoding: "utf8", windowsHide: true }
);
assert.equal(
  provenanceVerification.status,
  0,
  `runtime provenance verification failed: ${provenanceVerification.stderr}`
);

const composeEvidenceBytes = assertFileReference(evidence.composeEvidence);
const compose = JSON.parse(composeEvidenceBytes.toString("utf8"));
assert.equal(compose.schemaVersion, "task-mvp-002-compose-acceptance-v2");
assert.equal(compose.status, "PASS");
assert.equal(compose.taskId, evidence.primaryExecution.taskId);
assert.equal(compose.executionId, evidence.primaryExecution.executionId);
assert.equal(compose.resultExecutionId, evidence.primaryExecution.executionId);
assert.equal(compose.sourceSha256, evidence.primaryExecution.sourceDocumentSha256);
assert.equal(compose.downloadSha256, evidence.primaryExecution.downloadSha256);
assert.equal(compose.externalModelNetworkAttempted, false);
assert.equal(compose.adminSecurity.taskListUnauthenticatedStatus, 401);
assert.equal(compose.adminSecurity.previewUnauthenticatedStatus, 401);
assert.equal(compose.adminSecurity.readonlyWorkbenchAccess, true);
assert.equal(compose.maliciousBodyExecution.taskId, evidence.maliciousBodyExecution.taskId);
assert.equal(
  compose.maliciousBodyExecution.executionId,
  evidence.maliciousBodyExecution.executionId
);
assert.equal(
  compose.maliciousBodyExecution.previewBlockId,
  evidence.maliciousBodyExecution.previewBlockId
);
assert.equal(
  compose.maliciousBodyExecution.previewText,
  evidence.maliciousBodyExecution.previewText
);

const oneEvent = (type) => {
  const matches = events.filter((event) => event.type === type);
  assert.equal(matches.length, 1, `expected exactly one raw event: ${type}`);
  return matches[0].payload;
};
const taskList = oneEvent("CQCP.taskListDomObservation");
const exactResult = oneEvent("CQCP.exactResultDomObservation");
const sourceLocation = oneEvent("CQCP.sourceLocationDomObservation");
const nativeDownload = oneEvent("CQCP.nativeDownloadFileObservation");
const modelProfile = oneEvent("CQCP.modelProfileDomObservation");
const maliciousBody = oneEvent("CQCP.maliciousBodyDomObservation");
const authenticatedStorage = oneEvent("CQCP.authenticatedStorageObservation");
const reloadedStorage = oneEvent("CQCP.reloadedStorageObservation");
const finalStorage = oneEvent("CQCP.finalStorageObservation");

assert.equal(taskList.visible, true);
assert.equal(
  taskList.resultHref,
  `/review/results/${evidence.primaryExecution.taskId}?executionId=${evidence.primaryExecution.executionId}`
);
assert.equal(exactResult.renderedExecutionId, evidence.primaryExecution.executionId);
assert.equal(exactResult.parserText, "parser parser-docx-word-v20260724.1");
assert.equal(sourceLocation.blockId, evidence.assertions.sourceLocation.blockId);
assert.equal(sourceLocation.className.includes("is-primary-evidence"), true);
assert.equal(nativeDownload.sha256, evidence.primaryExecution.sourceDocumentSha256);
assert.equal(nativeDownload.size, upload.sourceSize);
assert.equal(capture.nativeDownload.sha256, nativeDownload.sha256);
assert.equal(modelProfile.bodyText.includes("DEEPSEEK_EVAL_ACCEPTANCE"), true);
assert.equal(modelProfile.bodyText.includes("disabled"), true);
assert.equal(modelProfile.bodyText.includes("EVALUATION"), true);
assert.equal(modelProfile.bodyText.includes("Secret 未配置"), true);
assert.equal(modelProfile.bodyText.includes("SECRET_MISSING"), true);
assert.equal(modelProfile.bodyText.includes("MVP_DEMO_MOCK"), true);
assert.equal(modelProfile.bodyText.includes("enabled"), true);
assert.equal(maliciousBody.blockId, evidence.maliciousBodyExecution.previewBlockId);
assert.equal(maliciousBody.textContent, evidence.maliciousBodyExecution.previewText);
assert.equal(maliciousBody.imageElementCount, 0);
assert.equal(maliciousBody.inlineHandlerAttributeCount, 0);
assert.equal(/<img\b/i.test(maliciousBody.outerHTML), false);
assert.equal(maliciousBody.outerHTML.includes("&lt;img"), true);
assert.equal(authenticatedStorage.localStorage.length, 0);
assert.equal(authenticatedStorage.sessionStorage.length, 0);
assert.equal(authenticatedStorage.passwordInputs.some(Boolean), false);
assert.equal(reloadedStorage.localStorage.length, 0);
assert.equal(reloadedStorage.sessionStorage.length, 0);
assert.equal(reloadedStorage.passwordInputs.some(Boolean), false);
assert.equal(finalStorage.localStorage.length, 0);
assert.equal(finalStorage.sessionStorage.length, 0);
assert.equal(finalStorage.passwordInputs.some(Boolean), false);

assert.equal(upload.sourceSha256, evidence.primaryExecution.sourceDocumentSha256);
assert.equal(upload.sourceSha256, upload.downloadSha256);
assert.equal(upload.sourceSha256, upload.storedHeaderSha256);
assert.equal(upload.executionStatus, "SUCCESS");
assert.equal(upload.resultExecutionId, upload.executionId);
assert.equal(upload.taskListExecutionId, upload.executionId);
assert.equal(upload.authorizationPersisted, false);

for (const [name, assertion] of Object.entries(evidence.assertions)) {
  assert.equal(assertion.status, "PASS", `browser assertion failed: ${name}`);
}
assert.equal(evidence.assertions.managementAccess.rowsVisibleBeforeAuthentication, false);
assert.equal(evidence.assertions.managementAccess.rowsVisibleAfterAuthentication, true);
assert.equal(evidence.assertions.managementAccess.tokenPersistedAcrossReload, false);
assert.equal(evidence.assertions.managementAccess.tokenVisibleAfterAuthentication, false);
assert.equal(evidence.assertions.managementAccess.tokenPresentInUrl, false);
assert.equal(evidence.assertions.download.nativeEventCaptured, true);
assert.equal(evidence.assertions.download.nativeFileCaptured, true);
assert.equal(evidence.assertions.download.sha256, nativeDownload.sha256);
assert.equal(evidence.assertions.modelStatus.adminTokenVisibleAfterAuthentication, false);
assert.equal(evidence.assertions.browserFileUpload.fileChooserUsed, true);
assert.equal(evidence.assertions.browserFileUpload.chooserMultiple, false);
assert.equal(evidence.assertions.browserFileUpload.taskId, upload.taskId);
assert.equal(evidence.assertions.browserFileUpload.executionId, upload.executionId);
assert.equal(evidence.assertions.maliciousBodyTextSafety.maliciousImageCount, 0);
assert.equal(evidence.assertions.maliciousBodyTextSafety.inlineHandlerCount, 0);
assert.equal(evidence.assertions.maliciousBodyTextSafety.javascriptDialogPresent, false);
assert.equal(evidence.assertions.browserConsole.errorCount, 0);
assert.equal(evidence.assertions.browserConsole.warningCount, 0);

assert.equal(evidence.screenshots.length, 7);
assert.deepEqual(evidence.screenshots, capture.screenshots);
for (const screenshot of evidence.screenshots) assertFileReference(screenshot);

for (const event of events) {
  if (event.type.startsWith("Network.") && event.payload.url) {
    assert.equal(
      ["localhost", "127.0.0.1"].includes(new URL(event.payload.url).hostname),
      true,
      "browser observed a non-local network request"
    );
  }
}

const evidenceBytes = [
  fs.readFileSync(assertionsPath),
  captureManifestBytes,
  eventStreamBytes,
  accessLogBytes,
  uploadEvidenceBytes,
  runtimeProvenanceBytes,
  resolvedComposeBytes,
  composeEvidenceBytes,
  ...evidence.screenshots.map((screenshot) => fileBytes(screenshot.path))
];
for (const marker of [
  "mvp002-acceptance-admin",
  "mvp002-acceptance-readonly",
  "Authorization: Bearer",
  "admin-sentinel",
  "readonly-sentinel"
]) {
  assert.equal(
    evidenceBytes.some((bytes) => bytes.includes(Buffer.from(marker))),
    false,
    `browser evidence persisted a credential marker: ${marker}`
  );
}

process.stdout.write(`${JSON.stringify({
  status: "PASS",
  browser: evidence.browser,
  taskId: evidence.primaryExecution.taskId,
  executionId: evidence.primaryExecution.executionId,
  browserUploadTaskId: upload.taskId,
  maliciousBodyTaskId: evidence.maliciousBodyExecution.taskId,
  eventStreamSha256: sha256(eventStreamBytes),
  accessLogSha256: sha256(accessLogBytes),
  screenshotCount: evidence.screenshots.length,
  assertions: Object.keys(evidence.assertions).length
})}\n`);
