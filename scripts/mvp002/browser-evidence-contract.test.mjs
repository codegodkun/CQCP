import assert from "node:assert/strict";
import test from "node:test";

import {
  assertBrowserTransportEvidence,
  parseBrowserEventStream,
  parseNginxBrowserAccessLog
} from "./browser-evidence-contract.mjs";

const identities = {
  uploadTaskId: `TASK_${"a".repeat(32)}`,
  uploadExecutionId: `EXEC_${"b".repeat(32)}`,
  primaryTaskId: `TASK_${"c".repeat(32)}`,
  primaryExecutionId: `EXEC_${"d".repeat(32)}`,
  maliciousTaskId: `TASK_${"e".repeat(32)}`,
  maliciousExecutionId: `EXEC_${"f".repeat(32)}`
};

function eventStream() {
  return [
    { sequence: 1, capturedAt: "2026-07-31T00:00:00.000Z", type: "Page.fileChooserOpened", payload: { mode: "selectSingle" } },
    { sequence: 2, capturedAt: "2026-07-31T00:00:01.000Z", type: "Browser.downloadWillBegin", payload: { guid: "g" } },
    { sequence: 3, capturedAt: "2026-07-31T00:00:02.000Z", type: "Browser.downloadProgress", payload: { guid: "g", state: "completed" } }
  ].map((event) => JSON.stringify(event)).join("\n");
}

function accessLog() {
  const ua = "Mozilla/5.0 HeadlessChrome/150.0.0.0";
  return [
    `127.0.0.1 - - [31/Jul/2026:00:00:00 +0000] "POST /api/review/tasks HTTP/1.1" 202 1 "http://localhost/review/new" "${ua}" "-"`,
    `127.0.0.1 - - [31/Jul/2026:00:00:01 +0000] "GET /api/review/tasks/${identities.uploadTaskId}/executions/${identities.uploadExecutionId} HTTP/1.1" 200 1 "-" "${ua}" "-"`,
    `127.0.0.1 - - [31/Jul/2026:00:00:02 +0000] "GET /api/v1/tasks/${identities.uploadTaskId}/result?executionId=${identities.uploadExecutionId} HTTP/1.1" 200 1 "-" "${ua}" "-"`,
    `127.0.0.1 - - [31/Jul/2026:00:00:03 +0000] "GET /api/review/tasks/${identities.primaryTaskId}/executions/${identities.primaryExecutionId}/document HTTP/1.1" 200 1 "-" "${ua}" "-"`,
    `127.0.0.1 - - [31/Jul/2026:00:00:04 +0000] "GET /api/review/tasks/${identities.maliciousTaskId}/executions/${identities.maliciousExecutionId}/document-preview HTTP/1.1" 200 1 "-" "${ua}" "-"`
  ].join("\n");
}

test("browser transport contract rebuilds upload/download proof from raw events and server log", () => {
  const result = assertBrowserTransportEvidence(
    parseBrowserEventStream(eventStream()),
    parseNginxBrowserAccessLog(accessLog()),
    identities
  );
  assert.deepEqual(result, {
    fileChooserEvents: 1,
    completedDownloads: 1,
    accessEntries: 5
  });
});

test("browser transport contract rejects a swapped or incomplete server log", () => {
  assert.throws(
    () =>
      assertBrowserTransportEvidence(
        parseBrowserEventStream(eventStream()),
        parseNginxBrowserAccessLog(accessLog().replace("/document HTTP", "/wrong HTTP")),
        identities
      ),
    /primary document download/
  );
});

test("browser transport contract rejects a self-assertion without native file chooser event", () => {
  const withoutChooser = eventStream()
    .split("\n")
    .slice(1)
    .map((line, index) => JSON.stringify({ ...JSON.parse(line), sequence: index + 1 }))
    .join("\n");
  assert.throws(
    () =>
      assertBrowserTransportEvidence(
        parseBrowserEventStream(withoutChooser),
        parseNginxBrowserAccessLog(accessLog()),
        identities
      ),
    /native file chooser/
  );
});
