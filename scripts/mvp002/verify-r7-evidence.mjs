import assert from "node:assert/strict";
import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";

const root = path.resolve(process.argv[2] ?? ".");
const mode = process.argv[3] ?? "verify";
const outputRoot = "outputs/task-034-mvp-e2e-acceptance-v3";
const absolute = (relativePath) =>
  path.join(root, ...relativePath.split("/"));
const read = (relativePath) => fs.readFileSync(absolute(relativePath), "utf8");
const readJson = (relativePath) => JSON.parse(read(relativePath));
const sha256 = (bytes) =>
  crypto.createHash("sha256").update(bytes).digest("hex");
const artifact = (relativePath) => {
  const bytes = fs.readFileSync(absolute(relativePath));
  return { path: relativePath, sha256: sha256(bytes), size: bytes.length };
};
const parseCsv = (text) => {
  const rows = [];
  let row = [];
  let field = "";
  let quoted = false;
  for (let index = 0; index < text.length; index += 1) {
    const character = text[index];
    if (quoted) {
      if (character === '"' && text[index + 1] === '"') {
        field += '"';
        index += 1;
      } else if (character === '"') {
        quoted = false;
      } else {
        field += character;
      }
    } else if (character === '"') {
      quoted = true;
    } else if (character === ",") {
      row.push(field);
      field = "";
    } else if (character === "\n") {
      row.push(field.replace(/\r$/, ""));
      rows.push(row);
      row = [];
      field = "";
    } else {
      field += character;
    }
  }
  assert.equal(quoted, false, "R7 CSV has an unterminated quote");
  if (field.length > 0 || row.length > 0) {
    row.push(field.replace(/\r$/, ""));
    rows.push(row);
  }
  return rows;
};

const manifestPath = `${outputRoot}/run-manifest.json`;
const consolePath = `${outputRoot}/console-summary.md`;
const occurrencePath = `${outputRoot}/occurrence-comparison.csv`;
const ledgerPath = `${outputRoot}/production-branch-scope-ledger.json`;
const formalXmlPath = `${outputRoot}/formal-test-result.xml`;
const rawLogPath = "outputs/task-mvp-002/audit/verification/formal-r7.log";
const samplePaths = ["001", "002", "003"].map(
  (suffix) => `${outputRoot}/sample-results/CQCP-MVP-DOCX-${suffix}.json`,
);
const sealPath = `${outputRoot}/r7-evidence-seal.json`;

const manifest = readJson(manifestPath);
const consoleText = read(consolePath);
const ledger = readJson(ledgerPath);
const samples = samplePaths.map(readJson);
const points = samples.flatMap((sample) => sample.points);
const occurrenceRows = parseCsv(read(occurrencePath));
const header = occurrenceRows[0];
const coverageIndex = header.indexOf("coverageResult");
assert.ok(coverageIndex >= 0, "R7 coverageResult column is missing");
const coverageCounts = occurrenceRows.slice(1).reduce((counts, row) => {
  counts[row[coverageIndex]] = (counts[row[coverageIndex]] ?? 0) + 1;
  return counts;
}, {});
const formalXml = read(formalXmlPath);
const rawLog = read(rawLogPath);
const expectedCommit = "WORKTREE-MVP002-FINAL-R8";
const formalSuiteMatch = formalXml.match(
  /<testsuite[^>]*\btests="(\d+)"[^>]*\bskipped="(\d+)"[^>]*\bfailures="(\d+)"[^>]*\berrors="(\d+)"/,
);
assert.ok(formalSuiteMatch, "Formal R7 XML counts are not readable");

const assertions = {
  formalTest:
    formalSuiteMatch[1] === "1" &&
    formalSuiteMatch[2] === "0" &&
    formalSuiteMatch[3] === "0" &&
    formalSuiteMatch[4] === "0" &&
    /formalAcceptanceEntryPointIsPropertyAndInputGated/.test(formalXml),
  rawConsole:
    /BUILD SUCCESSFUL/.test(rawLog) &&
    /cqcp\.task034\.formal=true/.test(rawLog) &&
    new RegExp(`cqcp\\.task034\\.commit=${expectedCommit}`).test(rawLog),
  manifest:
    manifest.formalMode === true &&
    manifest.commit === expectedCommit &&
    manifest.branch === "codex/task-mvp-002" &&
    manifest.samples?.length === 3 &&
    manifest.samples.every(
      (sample) =>
        sample.snapshotStatus === "SUCCESS" &&
        sample.versionReferences?.ruleSetVersion === "v20260729.1",
    ) &&
    manifest.humanGroundTruthIncludedCount === 57 &&
    manifest.humanGroundTruthExcludedCount === 6 &&
    manifest.productionInactiveBranchLedgerCount === 70,
  executionScopedQuery:
    manifest.samples.every(
      (sample) =>
        sample.queryPath ===
          `/api/v1/tasks/${sample.taskId}/result?executionId=${sample.executionId}` &&
        sample.queryMode === "EXACT_EXECUTION_ID" &&
        sample.latestSnapshotExecutionIdAtQuery ===
          `${sample.executionId}-latest-decoy` &&
        sample.exactExecutionIsolationVerified === true,
    ) &&
    samples.every(
      (sample) =>
        sample.queryPath ===
          `/api/v1/tasks/${sample.taskId}/result?executionId=${sample.executionId}` &&
        sample.executionMetadata?.sampleId === sample.sampleId &&
        sample.executionMetadata?.taskId === sample.taskId &&
        sample.executionMetadata?.executionId === sample.executionId &&
        sample.executionMetadata?.queryPath === sample.queryPath &&
        sample.executionMetadata?.queryMode === "EXACT_EXECUTION_ID" &&
        sample.executionMetadata?.latestSnapshotExecutionIdAtQuery ===
          `${sample.executionId}-latest-decoy` &&
        sample.executionMetadata?.exactExecutionIsolationVerified === true,
    ),
  console:
    /^- overallVerdict: PASS$/m.test(consoleText) &&
    /^- samples: 3$/m.test(consoleText) &&
    /^- occurrences: 63$/m.test(consoleText) &&
    /^- humanGroundTruthIncluded: 57$/m.test(consoleText) &&
    /^- humanGroundTruthExcluded: 6$/m.test(consoleText) &&
    /^- productionInactiveBranchLedgerEntries: 70$/m.test(consoleText),
  points:
    points.length === 27 &&
    points.every(
      (point) =>
        point.candidateComparison === "MATCH" &&
        point.pointStatus === "PASS" &&
        point.sysDiagnostics?.length === 0,
    ),
  anchors:
    points.reduce(
      (total, point) => total + point.actualAnchors.length,
      0,
    ) === 57,
  occurrences:
    occurrenceRows.length === 64 &&
    coverageCounts.MATCHED === 57 &&
    coverageCounts.EXCLUDED === 6 &&
    Object.keys(coverageCounts).length === 2,
  productionLedger:
    ledger.productionEntryCount === 70 &&
    ledger.humanGroundTruthExcludedCount === 6 &&
    ledger.entries?.length === 70 &&
    ledger.entries.every(
      (entry) =>
        entry.status === "EXCLUDED" &&
        entry.reason === "SEMANTIC_EXCLUDED" &&
        entry.ruleSetVersion === "v20260729.1",
    ),
  noFindingOrDiagnostic: samples.every(
    (sample) =>
      sample.findings?.length === 0 && sample.queryDiagnostics?.length === 0,
  ),
};
assert.ok(
  Object.values(assertions).every(Boolean),
  `Formal R7 assertion failed: ${JSON.stringify(assertions)}`,
);

const artifacts = [
  manifestPath,
  consolePath,
  occurrencePath,
  ledgerPath,
  formalXmlPath,
  rawLogPath,
  ...samplePaths,
].map(artifact);
const sealCore = {
  schemaVersion: "task-034-r7-evidence-seal-v1",
  status: "PASS",
  command:
    `JAVA_TOOL_OPTIONS="-Dcqcp.task034.formal=true -Dcqcp.task034.formalInput=true -Dcqcp.task034.commit=${expectedCommit} -Dcqcp.task034.branch=codex/task-mvp-002 -Dcqcp.task034.gradleVersion=8.10.2" gradle test --no-daemon --rerun-tasks --tests "com.cqcp.apiserver.reviewengine.Task034MvpE2eAcceptanceHarnessTest.formalAcceptanceEntryPointIsPropertyAndInputGated"`,
  assertions,
  artifacts,
};

if (mode === "create") {
  const seal = { ...sealCore, generatedAt: new Date().toISOString() };
  fs.writeFileSync(
    absolute(sealPath),
    `${JSON.stringify(seal, null, 2)}\n`,
    "utf8",
  );
  console.log(
    JSON.stringify({
      status: "R7_EVIDENCE_SEALED",
      sealSha256: artifact(sealPath).sha256,
      assertions,
    }),
  );
} else if (mode === "verify") {
  const seal = readJson(sealPath);
  assert.deepEqual(
    { ...seal, generatedAt: undefined },
    { ...sealCore, generatedAt: undefined },
    "R7 evidence seal changed",
  );
  console.log(
    JSON.stringify({
      status: "R7_EVIDENCE_VERIFIED",
      sealSha256: artifact(sealPath).sha256,
      assertions,
    }),
  );
} else {
  throw new Error(`Unknown mode: ${mode}`);
}
