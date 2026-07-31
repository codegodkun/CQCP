import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";

const root = path.resolve(process.argv[2] ?? ".");
const mode = process.argv[3] ?? "create";
const outputRoot = path.join(root, "outputs/task-eval-002");
const dispatchPath = path.join(outputRoot, "track-b-dispatch.json");
const dispatchHashPath = path.join(outputRoot, "track-b-dispatch.sha256");
const opinionRoot = path.join(outputRoot, "track-b-codex-opinions");
const sealPath = path.join(outputRoot, "track-b-codex-seal.json");
const admissionRelativePath =
  process.argv[4] ?? "outputs/task-eval-002/track-b-admission.json";
const packetManifestOverrideRelativePath = process.argv[5];
const sourceR7ManifestOverrideRelativePath = process.argv[6];
assert.equal(
  packetManifestOverrideRelativePath !== undefined,
  sourceR7ManifestOverrideRelativePath !== undefined,
  "TRACK_B_HISTORICAL_OVERRIDE_PAIR_REQUIRED",
);
assert.ok(
  packetManifestOverrideRelativePath === undefined || mode === "verify",
  "TRACK_B_HISTORICAL_OVERRIDE_VERIFY_ONLY",
);
assert.match(
  admissionRelativePath,
  /^[A-Za-z0-9._/-]+$/,
  "TRACK_B_ADMISSION_PATH_INVALID",
);
assert.ok(
  !admissionRelativePath.includes(".."),
  "TRACK_B_ADMISSION_PATH_TRAVERSAL",
);
const admissionPath = path.resolve(
  root,
  ...admissionRelativePath.split("/"),
);
assert.ok(
  admissionPath.startsWith(`${root}${path.sep}`),
  "TRACK_B_ADMISSION_PATH_OUTSIDE_ROOT",
);
assert.ok(
  process.argv[4] === undefined || mode === "verify",
  "TRACK_B_ADMISSION_OVERRIDE_VERIFY_ONLY",
);
const sha256 = (bytes) => createHash("sha256").update(bytes).digest("hex");
const posixRelative = (absolutePath) =>
  path.relative(root, absolutePath).split(path.sep).join("/");
const exactKeys = (value, expected, label) => {
  assert.ok(
    value !== null && typeof value === "object" && !Array.isArray(value),
    `${label}_MUST_BE_OBJECT`,
  );
  const actual = Object.keys(value).sort();
  const wanted = [...expected].sort();
  assert.deepEqual(actual, wanted, `${label}_KEYS_INVALID`);
};
const requireString = (value, label, pattern = null) => {
  assert.equal(typeof value, "string", `${label}_MUST_BE_STRING`);
  assert.ok(value.length > 0, `${label}_MUST_NOT_BE_EMPTY`);
  if (pattern) assert.match(value, pattern, `${label}_FORMAT_INVALID`);
  return value;
};
const parseIso = (value, label) => {
  requireString(value, label);
  const millis = Date.parse(value);
  assert.ok(Number.isFinite(millis), `${label}_ISO_INVALID`);
  return millis;
};
const readRelative = async (relativePath, label) => {
  requireString(relativePath, label, /^[A-Za-z0-9._/-]+$/);
  assert.ok(!relativePath.includes(".."), `${label}_TRAVERSAL`);
  const absolutePath = path.resolve(root, ...relativePath.split("/"));
  assert.ok(
    absolutePath.startsWith(`${root}${path.sep}`),
    `${label}_OUTSIDE_ROOT`,
  );
  return { absolutePath, bytes: await readFile(absolutePath) };
};

const dispatchBytes = await readFile(dispatchPath);
const dispatchSha256 = sha256(dispatchBytes);
const expectedDispatchHash = (await readFile(dispatchHashPath, "utf8"))
  .trim()
  .split(/\s+/)[0];
assert.equal(dispatchSha256, expectedDispatchHash, "TRACK_B_DISPATCH_CHANGED");
const dispatch = JSON.parse(dispatchBytes.toString("utf8"));
exactKeys(
  dispatch,
  [
    "schemaVersion",
    "track",
    "createdAt",
    "packetManifestPath",
    "packetManifestSha256",
    "sourceR7ManifestPath",
    "sourceR7ManifestSha256",
    "promptPath",
    "promptSha256",
    "sampleCount",
    "assignments",
  ],
  "TRACK_B_DISPATCH_ROOT",
);
assert.equal(dispatch.schemaVersion, "task-eval-002-track-b-dispatch-v2");
assert.equal(
  dispatch.track,
  "TRACK_B_RUNTIME_ISOMORPHIC_ROLE_CANDIDATE_ANCHOR_ABSTENTION",
);
const dispatchCreatedAt = parseIso(dispatch.createdAt, "dispatch.createdAt");
assert.equal(dispatch.sampleCount, 3);
assert.ok(Array.isArray(dispatch.assignments), "assignments must be an array");
assert.equal(dispatch.assignments.length, 3);

const packetManifestFile = await readRelative(
  packetManifestOverrideRelativePath ?? dispatch.packetManifestPath,
  "packetManifestPath",
);
assert.equal(
  sha256(packetManifestFile.bytes),
  dispatch.packetManifestSha256,
  "TRACK_B_PACKET_MANIFEST_CHANGED",
);
const packetManifest = JSON.parse(packetManifestFile.bytes.toString("utf8"));
assert.equal(packetManifest.schemaVersion, "task-eval-002-track-b-manifest-v1");
assert.equal(packetManifest.status, "PACKETS_READY_ZERO_ELIGIBLE_CALLS");
assert.equal(packetManifest.modelCallsAllowed, false);
assert.equal(packetManifest.packetCount, 27);
assert.equal(packetManifest.familyPlanCount, 9);
assert.equal(packetManifest.candidateOccurrenceCount, 57);

const sourceR7ManifestFile = await readRelative(
  sourceR7ManifestOverrideRelativePath ?? dispatch.sourceR7ManifestPath,
  "sourceR7ManifestPath",
);
assert.equal(
  sha256(sourceR7ManifestFile.bytes),
  dispatch.sourceR7ManifestSha256,
  "TRACK_B_SOURCE_R7_CHANGED",
);
assert.equal(
  packetManifest.sourceR7ManifestSha256,
  dispatch.sourceR7ManifestSha256,
  "TRACK_B_SOURCE_R7_IDENTITY_MISMATCH",
);
const sourceR7Manifest = JSON.parse(
  sourceR7ManifestFile.bytes.toString("utf8"),
);
assert.equal(sourceR7Manifest.formalMode, true);
assert.equal(sourceR7Manifest.humanGroundTruthIncludedCount, 57);
assert.equal(sourceR7Manifest.humanGroundTruthExcludedCount, 6);
assert.equal(sourceR7Manifest.productionInactiveBranchLedgerCount, 70);
assert.equal(sourceR7Manifest.samples?.length, 3);
assert.ok(
  sourceR7Manifest.samples.every((sample) => sample.snapshotStatus === "SUCCESS"),
  "TRACK_B_SOURCE_R7_NOT_SUCCESS",
);

const promptFile = await readRelative(dispatch.promptPath, "promptPath");
assert.equal(
  sha256(promptFile.bytes),
  dispatch.promptSha256,
  "TRACK_B_PROMPT_CHANGED",
);

const manifestSampleById = new Map(
  packetManifest.samples.map((sample) => [sample.sampleId, sample]),
);
const seenSamples = new Set();
const seenTasks = new Set();
const opinionEntries = [];
let latestCompletedAt = dispatchCreatedAt;
for (const assignment of dispatch.assignments) {
  exactKeys(
    assignment,
    [
      "assignmentId",
      "sampleId",
      "agentTask",
      "forkTurns",
      "assignedAt",
      "packetPath",
      "packetSetSha256",
    ],
    "TRACK_B_ASSIGNMENT",
  );
  requireString(assignment.assignmentId, "assignmentId", /^TRACK-B-CODEX-\d{3}$/);
  requireString(assignment.sampleId, "sampleId", /^CQCP-MVP-DOCX-00[1-3]$/);
  requireString(
    assignment.agentTask,
    "agentTask",
    /^\/root\/track_b_blind_v6_00[1-3]$/,
  );
  assert.equal(assignment.forkTurns, "none");
  assert.equal(
    parseIso(assignment.assignedAt, "assignment.assignedAt"),
    dispatchCreatedAt,
  );
  assert.ok(!seenSamples.has(assignment.sampleId), "duplicate sample assignment");
  assert.ok(!seenTasks.has(assignment.agentTask), "duplicate agent task");
  seenSamples.add(assignment.sampleId);
  seenTasks.add(assignment.agentTask);

  const manifestSample = manifestSampleById.get(assignment.sampleId);
  assert.ok(manifestSample, `sample missing from manifest: ${assignment.sampleId}`);
  assert.equal(
    assignment.packetPath,
    `outputs/task-eval-002/track-b-inputs-v1/${manifestSample.path}`,
  );
  assert.match(
    manifestSample.path,
    /^[A-Za-z0-9._-]+$/,
    "TRACK_B_PACKET_FILENAME_INVALID",
  );
  assert.equal(assignment.packetSetSha256, manifestSample.sha256);
  const packetFile = packetManifestOverrideRelativePath === undefined
    ? await readRelative(assignment.packetPath, "packetPath")
    : {
        absolutePath: path.join(
          path.dirname(packetManifestFile.absolutePath),
          manifestSample.path,
        ),
        bytes: await readFile(
          path.join(
            path.dirname(packetManifestFile.absolutePath),
            manifestSample.path,
          ),
        ),
      };
  assert.equal(
    sha256(packetFile.bytes),
    assignment.packetSetSha256,
    `TRACK_B_PACKET_CHANGED:${assignment.sampleId}`,
  );
  const packet = JSON.parse(packetFile.bytes.toString("utf8"));

  const opinionPath = path.join(
    opinionRoot,
    `${assignment.sampleId}.codex.json`,
  );
  const opinionBytes = await readFile(opinionPath);
  const opinion = JSON.parse(opinionBytes.toString("utf8"));
  exactKeys(
    opinion,
    [
      "schemaVersion",
      "assignmentId",
      "sampleId",
      "agentTask",
      "forkTurns",
      "dispatchSha256",
      "promptSha256",
      "packetSetSha256",
      "assignedAt",
      "completedAt",
      "evaluations",
    ],
    "TRACK_B_OPINION_ROOT",
  );
  assert.equal(
    opinion.schemaVersion,
    "task-eval-002-track-b-codex-opinion-v2",
  );
  for (const field of [
    "assignmentId",
    "sampleId",
    "agentTask",
    "forkTurns",
    "packetSetSha256",
    "assignedAt",
  ]) {
    assert.equal(
      opinion[field],
      assignment[field],
      `TRACK_B_OPINION_${field.toUpperCase()}_MISMATCH`,
    );
  }
  assert.equal(opinion.dispatchSha256, dispatchSha256);
  assert.equal(opinion.promptSha256, dispatch.promptSha256);
  const completedAt = parseIso(opinion.completedAt, "opinion.completedAt");
  assert.ok(
    completedAt >= dispatchCreatedAt && completedAt <= Date.now() + 300_000,
    "TRACK_B_OPINION_TIME_ORDER_INVALID",
  );
  latestCompletedAt = Math.max(latestCompletedAt, completedAt);
  assert.ok(Array.isArray(opinion.evaluations), "evaluations must be an array");
  assert.equal(opinion.evaluations.length, 9);

  const packetById = new Map(packet.packets.map((item) => [item.packetId, item]));
  const seenPackets = new Set();
  for (const evaluation of opinion.evaluations) {
    exactKeys(
      evaluation,
      [
        "packetId",
        "reviewPointCode",
        "suggestedRole",
        "selectedOccurrenceIds",
        "selectedAnchorBlockIds",
        "abstain",
        "abstentionReason",
      ],
      "TRACK_B_EVALUATION",
    );
    requireString(evaluation.packetId, "packetId", /^EP-[a-f0-9]{64}$/);
    requireString(
      evaluation.reviewPointCode,
      "reviewPointCode",
      /^[A-Z][A-Z0-9_]+$/,
    );
    assert.equal(evaluation.suggestedRole, null);
    assert.ok(
      Array.isArray(evaluation.selectedOccurrenceIds),
      "selectedOccurrenceIds must be an array",
    );
    assert.ok(
      Array.isArray(evaluation.selectedAnchorBlockIds),
      "selectedAnchorBlockIds must be an array",
    );
    assert.equal(evaluation.selectedOccurrenceIds.length, 0);
    assert.equal(evaluation.selectedAnchorBlockIds.length, 0);
    assert.equal(typeof evaluation.abstain, "boolean");
    assert.equal(evaluation.abstain, true);
    requireString(evaluation.abstentionReason, "abstentionReason");
    const source = packetById.get(evaluation.packetId);
    assert.ok(source, `unknown packet: ${evaluation.packetId}`);
    assert.ok(!seenPackets.has(evaluation.packetId), "duplicate packet evaluation");
    assert.equal(evaluation.reviewPointCode, source.reviewPointCode);
    assert.equal(
      evaluation.abstentionReason,
      source.admission.reasonCodes[0],
    );
    seenPackets.add(evaluation.packetId);
  }
  assert.equal(seenPackets.size, 9);
  opinionEntries.push({
    assignmentId: assignment.assignmentId,
    sampleId: assignment.sampleId,
    agentTask: assignment.agentTask,
    forkTurns: assignment.forkTurns,
    assignedAt: assignment.assignedAt,
    completedAt: opinion.completedAt,
    opinionPath: posixRelative(opinionPath),
    opinionSha256: sha256(opinionBytes),
    packetSetSha256: assignment.packetSetSha256,
    evaluationCount: opinion.evaluations.length,
    abstentionCount: opinion.evaluations.filter((item) => item.abstain).length,
  });
}
assert.equal(seenSamples.size, 3);

const sealCore = {
  schemaVersion: "task-eval-002-track-b-codex-seal-v2",
  status: "CODEX_TRACK_B_COMPLETE_ZERO_CALL_ABSTENTION",
  dispatchPath: posixRelative(dispatchPath),
  dispatchSha256,
  packetManifestPath: dispatch.packetManifestPath,
  packetManifestSha256: dispatch.packetManifestSha256,
  sourceR7ManifestPath: dispatch.sourceR7ManifestPath,
  sourceR7ManifestSha256: dispatch.sourceR7ManifestSha256,
  promptPath: dispatch.promptPath,
  promptSha256: dispatch.promptSha256,
  opinionCount: opinionEntries.length,
  evaluationCount: opinionEntries.reduce(
    (total, entry) => total + entry.evaluationCount,
    0,
  ),
  abstentionCount: opinionEntries.reduce(
    (total, entry) => total + entry.abstentionCount,
    0,
  ),
  providerAdmission: "NOT_ESTABLISHED_ZERO_ELIGIBLE_SAMPLE",
  entries: opinionEntries,
};

if (mode === "create") {
  const sealedAt = new Date().toISOString();
  assert.ok(
    Date.parse(sealedAt) >= latestCompletedAt,
    "TRACK_B_SEAL_TIME_ORDER_INVALID",
  );
  const seal = { ...sealCore, sealedAt };
  await mkdir(opinionRoot, { recursive: true });
  await writeFile(sealPath, `${JSON.stringify(seal, null, 2)}\n`, "utf8");
  const sealSha256 = sha256(await readFile(sealPath));
  const admission = {
    schemaVersion: "task-eval-002-track-b-admission-v2",
    status: "CODEX_COMPLETE_ZERO_ELIGIBLE_SAMPLE",
    modelCallsAllowed: false,
    task034CandidateAnchorGate: "R7_PASS",
    runtimeIsomorphicEvidencePacketSeam: "IMPLEMENTED_AND_TESTED",
    evidence: {
      sourceR7ManifestPath: dispatch.sourceR7ManifestPath,
      sourceR7ManifestSha256: dispatch.sourceR7ManifestSha256,
      packetManifestPath: dispatch.packetManifestPath,
      packetManifestSha256: dispatch.packetManifestSha256,
      dispatchPath: posixRelative(dispatchPath),
      dispatchSha256,
      promptPath: dispatch.promptPath,
      promptSha256: dispatch.promptSha256,
      sealPath: posixRelative(sealPath),
      sealSha256,
    },
    codexBlindEvaluation: {
      status: seal.status,
      sampleCount: 3,
      evaluationCount: seal.evaluationCount,
      abstentionCount: seal.abstentionCount,
    },
    providerAdmission: seal.providerAdmission,
    blockingReasons: [
      "The frozen R7 corpus contains zero model-eligible roles, so it proves required abstention but cannot establish guarded-assist quality.",
      "The independent eligible-ambiguity corpus still requires human-confirmed ground truth and actual Codex plus DeepSeek Track B admission.",
      "The 15 eligible synthetic Track B inputs have not received separate DeepSeek egress authorization.",
      "Track A full-document model opinions and connectivity success cannot substitute for Track B provider admission evidence.",
    ],
  };
  await writeFile(
    admissionPath,
    `${JSON.stringify(admission, null, 2)}\n`,
    "utf8",
  );
  process.stdout.write(
    `${JSON.stringify({
      status: seal.status,
      evaluationCount: seal.evaluationCount,
      abstentionCount: seal.abstentionCount,
      sealSha256,
    })}\n`,
  );
} else if (mode === "verify") {
  const sealBytes = await readFile(sealPath);
  const seal = JSON.parse(sealBytes.toString("utf8"));
  const sealedAt = parseIso(seal.sealedAt, "seal.sealedAt");
  assert.ok(sealedAt >= latestCompletedAt, "TRACK_B_SEAL_TIME_ORDER_INVALID");
  assert.deepEqual(
    { ...seal, sealedAt: undefined },
    { ...sealCore, sealedAt: undefined },
    "TRACK_B_SEAL_CONTENT_INVALID",
  );
  const admission = JSON.parse(await readFile(admissionPath, "utf8"));
  assert.equal(admission.schemaVersion, "task-eval-002-track-b-admission-v2");
  assert.equal(admission.status, "CODEX_COMPLETE_ZERO_ELIGIBLE_SAMPLE");
  assert.equal(admission.modelCallsAllowed, false);
  assert.equal(admission.task034CandidateAnchorGate, "R7_PASS");
  assert.equal(
    admission.runtimeIsomorphicEvidencePacketSeam,
    "IMPLEMENTED_AND_TESTED",
  );
  assert.equal(admission.evidence.sourceR7ManifestSha256, dispatch.sourceR7ManifestSha256);
  assert.equal(admission.evidence.packetManifestSha256, dispatch.packetManifestSha256);
  assert.equal(admission.evidence.dispatchSha256, dispatchSha256);
  assert.equal(admission.evidence.promptSha256, dispatch.promptSha256);
  assert.equal(admission.evidence.sealSha256, sha256(sealBytes));
  assert.equal(
    admission.providerAdmission,
    "NOT_ESTABLISHED_ZERO_ELIGIBLE_SAMPLE",
  );
  process.stdout.write(
    `${JSON.stringify({
      status: "TRACK_B_CHAIN_VERIFIED",
      evaluationCount: seal.evaluationCount,
      abstentionCount: seal.abstentionCount,
      sealSha256: sha256(sealBytes),
    })}\n`,
  );
} else {
  throw new Error(`Unknown mode: ${mode}`);
}
