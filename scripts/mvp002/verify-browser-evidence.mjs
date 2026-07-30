import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";

const repoRoot = path.resolve(process.argv[2] ?? ".");
const evidenceRoot = path.join(
  repoRoot,
  "outputs/task-mvp-002/browser-evidence-current"
);
const assertionsPath = path.join(evidenceRoot, "browser-assertions.json");
const browserUploadPath = path.join(
  evidenceRoot,
  "browser-upload-result.json"
);
const evidence = JSON.parse(fs.readFileSync(assertionsPath, "utf8"));
const browserUpload = JSON.parse(fs.readFileSync(browserUploadPath, "utf8"));
const sha256 = (bytes) =>
  crypto.createHash("sha256").update(bytes).digest("hex");
const fileHash = (relativePath) =>
  sha256(fs.readFileSync(path.join(repoRoot, ...relativePath.split("/"))));

assert.equal(evidence.schemaVersion, "task-mvp-002-browser-evidence-v3");
assert.equal(evidence.status, "PASS");
assert.equal(
  fileHash(evidence.runtimeProvenance.path),
  evidence.runtimeProvenance.sha256,
  "bound runtime provenance changed"
);
const runtimeProvenance = JSON.parse(
  fs.readFileSync(
    path.join(repoRoot, ...evidence.runtimeProvenance.path.split("/")),
    "utf8"
  )
);
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
const provenanceVerification = spawnSync(
  process.execPath,
  [
    path.join(
      repoRoot,
      "scripts/mvp002/capture-runtime-provenance.mjs"
    ),
    repoRoot,
    "verify",
    path.dirname(
      path.join(
        repoRoot,
        ...evidence.runtimeProvenance.path.split("/")
      )
    )
  ],
  {
    cwd: repoRoot,
    encoding: "utf8",
    windowsHide: true
  }
);
assert.equal(
  provenanceVerification.status,
  0,
  `runtime provenance verification failed: ${provenanceVerification.stderr}`
);
assert.equal(
  fileHash(evidence.composeEvidence.path),
  evidence.composeEvidence.sha256,
  "bound Compose evidence changed"
);
const compose = JSON.parse(
  fs.readFileSync(
    path.join(repoRoot, ...evidence.composeEvidence.path.split("/")),
    "utf8"
  )
);
assert.equal(compose.schemaVersion, "task-mvp-002-compose-acceptance-v2");
assert.equal(compose.status, "PASS");
assert.deepEqual(compose.runtimeProvenance, {
  path: evidence.runtimeProvenance.path,
  sha256: evidence.runtimeProvenance.sha256
});
assert.equal(compose.taskId, evidence.primaryExecution.taskId);
assert.equal(compose.executionId, evidence.primaryExecution.executionId);
assert.equal(compose.resultExecutionId, evidence.primaryExecution.executionId);
assert.equal(
  compose.sourceSha256,
  evidence.primaryExecution.sourceDocumentSha256
);
assert.equal(compose.downloadSha256, evidence.primaryExecution.downloadSha256);
assert.equal(compose.externalModelNetworkAttempted, false);
assert.equal(compose.adminSecurity.taskListUnauthenticatedStatus, 401);
assert.equal(compose.adminSecurity.previewUnauthenticatedStatus, 401);
assert.equal(compose.adminSecurity.readonlyWorkbenchAccess, true);
assert.equal(
  compose.maliciousBodyExecution.taskId,
  evidence.maliciousBodyExecution.taskId
);
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
assert.equal(
  compose.maliciousBodyExecution.sourceSha256,
  evidence.maliciousBodyExecution.sourceDocumentSha256
);

assert.equal(
  fileHash(evidence.browserUploadEvidence.path),
  evidence.browserUploadEvidence.sha256,
  "bound browser upload evidence changed"
);
assert.equal(browserUpload.status, "PASS");
assert.equal(browserUpload.uploadChannel, "BROWSER_FILE_CHOOSER");
assert.equal(
  browserUpload.taskId,
  evidence.assertions.browserFileUpload.taskId
);
assert.equal(
  browserUpload.executionId,
  evidence.assertions.browserFileUpload.executionId
);
assert.equal(browserUpload.executionStatus, "SUCCESS");
assert.equal(
  browserUpload.sourceSha256,
  evidence.primaryExecution.sourceDocumentSha256
);
assert.equal(browserUpload.sourceSha256, browserUpload.downloadSha256);
assert.equal(browserUpload.sourceSha256, browserUpload.storedHeaderSha256);
assert.equal(browserUpload.resultExecutionId, browserUpload.executionId);
assert.equal(browserUpload.taskListExecutionId, browserUpload.executionId);
assert.equal(browserUpload.authorizationPersisted, false);

for (const [name, assertion] of Object.entries(evidence.assertions)) {
  assert.equal(assertion.status, "PASS", `browser assertion failed: ${name}`);
}
assert.equal(
  evidence.assertions.taskList.resultHref,
  `/review/results/${evidence.primaryExecution.taskId}?executionId=${evidence.primaryExecution.executionId}`
);
assert.equal(
  evidence.assertions.exactResult.renderedExecutionId,
  evidence.primaryExecution.executionId
);
assert.equal(evidence.assertions.sourceLocation.evidenceTextReverseSearchUsed, false);
assert.equal(evidence.assertions.managementAccess.rowsVisibleBeforeAuthentication, false);
assert.equal(evidence.assertions.managementAccess.tokenPersistedAcrossReload, false);
assert.equal(evidence.assertions.managementAccess.tokenVisibleAfterAuthentication, false);
assert.equal(evidence.assertions.managementAccess.tokenPresentInUrl, false);
assert.equal(evidence.assertions.download.authenticatedFetchCompleted, true);
assert.equal(evidence.assertions.download.blobByteLength, browserUpload.sourceSize);
assert.equal(evidence.assertions.download.browserDispatchTriggered, true);
assert.equal(evidence.assertions.download.tokenPresentInUrl, false);
assert.equal(evidence.assertions.modelStatus.adminTokenVisibleAfterAuthentication, false);
assert.equal(evidence.assertions.modelStatus.rawAuthorizationCaptured, false);
assert.equal(evidence.assertions.browserFileUpload.fileChooserUsed, true);
assert.equal(evidence.assertions.browserFileUpload.resultUrlExact, true);
assert.equal(evidence.assertions.maliciousBodyTextSafety.parserBacked, true);
assert.equal(evidence.assertions.maliciousBodyTextSafety.renderedAsText, true);
assert.equal(evidence.assertions.maliciousBodyTextSafety.maliciousImageCount, 0);
assert.equal(evidence.assertions.maliciousBodyTextSafety.inlineHandlerCount, 0);
assert.equal(evidence.assertions.maliciousBodyTextSafety.javascriptDialogPresent, false);
assert.equal(evidence.assertions.browserConsole.errorCount, 0);
assert.equal(evidence.assertions.browserConsole.warningCount, 0);
assert.equal(evidence.screenshots.length, 7);

for (const screenshot of evidence.screenshots) {
  assert.equal(
    fileHash(screenshot.path),
    screenshot.sha256,
    `browser screenshot changed: ${screenshot.path}`
  );
}
const assertionBytes = fs.readFileSync(assertionsPath);
const browserUploadBytes = fs.readFileSync(browserUploadPath);
const runtimeProvenanceBytes = fs.readFileSync(
  path.join(repoRoot, ...evidence.runtimeProvenance.path.split("/"))
);
const composeBytes = fs.readFileSync(
  path.join(repoRoot, ...evidence.composeEvidence.path.split("/"))
);
const forbiddenCredentialMarkers = [
  "mvp002-acceptance-admin",
  "mvp002-acceptance-readonly",
  "Authorization: Bearer"
];
for (const marker of forbiddenCredentialMarkers) {
  for (const bytes of [
    assertionBytes,
    browserUploadBytes,
    runtimeProvenanceBytes,
    composeBytes
  ]) {
    assert.equal(
      bytes.includes(Buffer.from(marker)),
      false,
      `browser evidence persisted a credential marker: ${marker}`
    );
  }
}

process.stdout.write(
  `${JSON.stringify({
    status: "PASS",
    browser: evidence.browser,
    taskId: evidence.primaryExecution.taskId,
    executionId: evidence.primaryExecution.executionId,
    browserUploadTaskId: browserUpload.taskId,
    maliciousBodyTaskId: evidence.maliciousBodyExecution.taskId,
    screenshotCount: evidence.screenshots.length,
    assertions: Object.keys(evidence.assertions).length
  })}\n`
);
