import assert from "node:assert/strict";

const ACCESS_LINE = /"(?<method>GET|HEAD|POST|PUT|DELETE|PATCH) (?<path>[^ ]+) HTTP\/[0-9.]+" (?<status>\d{3}) (?<bytes>\d+) "(?<referer>[^"]*)" "(?<userAgent>[^"]*)"/;

export function parseBrowserEventStream(text) {
  const lines = String(text).split(/\r?\n/).filter(Boolean);
  assert.ok(lines.length > 0, "browser event stream is empty");
  return lines.map((line, index) => {
    const event = JSON.parse(line);
    assert.equal(event.sequence, index + 1, "browser event sequence is not contiguous");
    assert.match(event.capturedAt, /^\d{4}-\d{2}-\d{2}T/);
    assert.equal(typeof event.type, "string");
    assert.notEqual(event.type.length, 0);
    return event;
  });
}

export function parseNginxBrowserAccessLog(text) {
  const entries = [];
  for (const line of String(text).split(/\r?\n/)) {
    const match = ACCESS_LINE.exec(line);
    if (!match?.groups) continue;
    entries.push({
      method: match.groups.method,
      path: match.groups.path,
      status: Number(match.groups.status),
      bytes: Number(match.groups.bytes),
      referer: match.groups.referer,
      userAgent: match.groups.userAgent
    });
  }
  assert.ok(entries.length > 0, "browser access log has no parseable request");
  assert.equal(
    entries.every((entry) => /(?:Chrome|HeadlessChrome)\//.test(entry.userAgent)),
    true,
    "browser access log contains a non-Chrome client"
  );
  return entries;
}

function hasAccess(entries, method, requestPath, status) {
  return entries.some(
    (entry) =>
      entry.method === method &&
      entry.path === requestPath &&
      entry.status === status
  );
}

export function assertBrowserTransportEvidence(events, accessEntries, expected) {
  const chooser = events.filter((event) => event.type === "Page.fileChooserOpened");
  assert.equal(chooser.length, 1, "expected exactly one native file chooser event");
  assert.equal(chooser[0].payload.mode, "selectSingle");

  const dialogEvents = events.filter(
    (event) => event.type === "Page.javascriptDialogOpening"
  );
  assert.equal(dialogEvents.length, 0, "browser opened a JavaScript dialog");
  const consoleFailures = events.filter(
    (event) =>
      event.type === "Runtime.consoleAPICalled" &&
      ["error", "warning"].includes(event.payload.type)
  );
  assert.equal(consoleFailures.length, 0, "browser console contains error/warning");

  const completedDownloads = events.filter(
    (event) =>
      event.type === "Browser.downloadProgress" &&
      event.payload.state === "completed"
  );
  assert.equal(completedDownloads.length, 1, "native browser download did not complete");
  assert.equal(
    events.some((event) => event.type === "Browser.downloadWillBegin"),
    true,
    "native browser download start event is missing"
  );

  assert.equal(
    hasAccess(accessEntries, "POST", "/api/review/tasks", 202),
    true,
    "server log does not prove browser upload HTTP 202"
  );
  assert.equal(
    hasAccess(
      accessEntries,
      "GET",
      `/api/review/tasks/${expected.uploadTaskId}/executions/${expected.uploadExecutionId}`,
      200
    ),
    true,
    "server log does not bind the uploaded execution"
  );
  assert.equal(
    hasAccess(
      accessEntries,
      "GET",
      `/api/v1/tasks/${expected.uploadTaskId}/result?executionId=${expected.uploadExecutionId}`,
      200
    ),
    true,
    "server log does not bind the uploaded result"
  );
  assert.equal(
    hasAccess(
      accessEntries,
      "GET",
      `/api/review/tasks/${expected.primaryTaskId}/executions/${expected.primaryExecutionId}/document`,
      200
    ),
    true,
    "server log does not prove the primary document download"
  );
  assert.equal(
    hasAccess(
      accessEntries,
      "GET",
      `/api/review/tasks/${expected.maliciousTaskId}/executions/${expected.maliciousExecutionId}/document-preview`,
      200
    ),
    true,
    "server log does not bind the malicious-body preview"
  );
  return {
    fileChooserEvents: chooser.length,
    completedDownloads: completedDownloads.length,
    accessEntries: accessEntries.length
  };
}
