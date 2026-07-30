import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { readFile, writeFile } from "node:fs/promises";
import path from "node:path";

const root = path.resolve(process.argv[2] ?? ".");
const mode = process.argv[3] ?? "create";
const outputRoot = path.join(root, "outputs/task-eval-002");
const packetRoot = path.join(outputRoot, "track-b-inputs-v1");
const manifestPath = path.join(packetRoot, "manifest.json");
const promptPath = path.join(
  root,
  "scripts/blind-evaluation/track-b-opinion-prompt.txt",
);
const dispatchPath = path.join(outputRoot, "track-b-dispatch.json");
const dispatchHashPath = path.join(outputRoot, "track-b-dispatch.sha256");
const sourceR7ManifestPath = path.join(
  root,
  "outputs/task-034-mvp-e2e-acceptance-v3/run-manifest.json",
);
const sha256 = (bytes) => createHash("sha256").update(bytes).digest("hex");
const posixRelative = (absolutePath) =>
  path.relative(root, absolutePath).split(path.sep).join("/");
const parseIso = (value, label) => {
  assert.equal(typeof value, "string", `${label} must be a string`);
  const millis = Date.parse(value);
  assert.ok(Number.isFinite(millis), `${label} must be ISO-8601`);
  return millis;
};

const manifestBytes = await readFile(manifestPath);
const manifest = JSON.parse(manifestBytes.toString("utf8"));
const promptBytes = await readFile(promptPath);
const sourceR7ManifestBytes = await readFile(sourceR7ManifestPath);
if (
  manifest.schemaVersion !== "task-eval-002-track-b-manifest-v1" ||
  manifest.status !== "PACKETS_READY_ZERO_ELIGIBLE_CALLS" ||
  manifest.modelCallsAllowed !== false ||
  manifest.packetCount !== 27 ||
  manifest.familyPlanCount !== 9 ||
  manifest.candidateOccurrenceCount !== 57 ||
  manifest.samples?.length !== 3 ||
  sha256(sourceR7ManifestBytes) !== manifest.sourceR7ManifestSha256
) {
  throw new Error("TRACK_B_MANIFEST_INVALID");
}

const forbidden = [
  "expectedcandidate",
  "humananchor",
  "groundtruth",
  "candidatecomparison",
  "pointstatus",
  "\"findings\"",
  "\"verdict\"",
];
const agentTasks = [
  "/root/track_b_blind_v6_001",
  "/root/track_b_blind_v6_002",
  "/root/track_b_blind_v6_003",
];
const assignedAt = new Date().toISOString();
const assignments = [];
for (const [index, sample] of manifest.samples.entries()) {
  const packetPath = path.join(packetRoot, sample.path);
  const packetBytes = await readFile(packetPath);
  const packetHash = sha256(packetBytes);
  if (packetHash !== sample.sha256) {
    throw new Error(`TRACK_B_HASH_MISMATCH:${sample.sampleId}`);
  }
  const packetText = packetBytes.toString("utf8");
  const serialized = packetText.toLowerCase();
  for (const token of forbidden) {
    if (serialized.includes(token)) {
      throw new Error(`TRACK_B_LEAK:${sample.sampleId}:${token}`);
    }
  }
  const packet = JSON.parse(packetText);
  if (
    packet.packetCount !== 9 ||
    packet.modelCallsAllowed !== false ||
    packet.packets?.length !== 9 ||
    packet.familyPlans?.length !== 3 ||
    packet.packets.some(
      (item) =>
        item.admission?.modelCallAllowed !== false ||
        item.admission?.status !== "ZERO_CALL_REQUIRED" ||
        item.admission?.reasonCodes?.[0] !==
          "DETERMINISTIC_HIGH_ZERO_CALL" ||
        item.candidateOccurrences?.length < 1 ||
        item.candidateOccurrences.some(
          (occurrence) => occurrence.sourceAnchor?.reliable !== true,
        ),
    )
  ) {
    throw new Error(`TRACK_B_PACKET_CONTRACT_INVALID:${sample.sampleId}`);
  }
  assignments.push({
    assignmentId: `TRACK-B-CODEX-${String(index + 1).padStart(3, "0")}`,
    sampleId: sample.sampleId,
    agentTask: agentTasks[index],
    forkTurns: "none",
    assignedAt,
    packetPath: posixRelative(packetPath),
    packetSetSha256: packetHash,
  });
}

if (mode === "create") {
  const dispatch = {
    schemaVersion: "task-eval-002-track-b-dispatch-v2",
    track: "TRACK_B_RUNTIME_ISOMORPHIC_ROLE_CANDIDATE_ANCHOR_ABSTENTION",
    createdAt: assignedAt,
    packetManifestPath: posixRelative(manifestPath),
    packetManifestSha256: sha256(manifestBytes),
    sourceR7ManifestPath: posixRelative(sourceR7ManifestPath),
    sourceR7ManifestSha256: sha256(sourceR7ManifestBytes),
    promptPath: posixRelative(promptPath),
    promptSha256: sha256(promptBytes),
    sampleCount: assignments.length,
    assignments,
  };
  await writeFile(
    dispatchPath,
    `${JSON.stringify(dispatch, null, 2)}\n`,
    "utf8",
  );
  const dispatchSha256 = sha256(await readFile(dispatchPath));
  await writeFile(
    dispatchHashPath,
    `${dispatchSha256}  track-b-dispatch.json\n`,
    "utf8",
  );
  process.stdout.write(
    `${JSON.stringify({
      status: "TRACK_B_DISPATCH_FROZEN",
      sampleCount: assignments.length,
      dispatchSha256,
      createdAt: assignedAt,
    })}\n`,
  );
} else if (mode === "verify") {
  const dispatchBytes = await readFile(dispatchPath);
  const dispatch = JSON.parse(dispatchBytes.toString("utf8"));
  const expectedDispatchHash = (await readFile(dispatchHashPath, "utf8"))
    .trim()
    .split(/\s+/)[0];
  assert.equal(sha256(dispatchBytes), expectedDispatchHash);
  assert.equal(dispatch.schemaVersion, "task-eval-002-track-b-dispatch-v2");
  assert.equal(dispatch.track, "TRACK_B_RUNTIME_ISOMORPHIC_ROLE_CANDIDATE_ANCHOR_ABSTENTION");
  assert.equal(dispatch.packetManifestPath, posixRelative(manifestPath));
  assert.equal(dispatch.packetManifestSha256, sha256(manifestBytes));
  assert.equal(dispatch.sourceR7ManifestPath, posixRelative(sourceR7ManifestPath));
  assert.equal(dispatch.sourceR7ManifestSha256, sha256(sourceR7ManifestBytes));
  assert.equal(dispatch.promptPath, posixRelative(promptPath));
  assert.equal(dispatch.promptSha256, sha256(promptBytes));
  assert.equal(dispatch.sampleCount, 3);
  assert.equal(dispatch.assignments?.length, 3);
  parseIso(dispatch.createdAt, "dispatch.createdAt");
  for (const [index, expected] of assignments.entries()) {
    const actual = dispatch.assignments[index];
    assert.deepEqual(
      {
        ...actual,
        assignedAt: assignedAt,
      },
      expected,
      `assignment identity mismatch: ${expected.sampleId}`,
    );
    assert.equal(actual.assignedAt, dispatch.createdAt);
    parseIso(actual.assignedAt, "assignment.assignedAt");
  }
  process.stdout.write(
    `${JSON.stringify({
      status: "TRACK_B_DISPATCH_VERIFIED",
      sampleCount: dispatch.sampleCount,
      dispatchSha256: expectedDispatchHash,
    })}\n`,
  );
} else {
  throw new Error(`Unknown mode: ${mode}`);
}
