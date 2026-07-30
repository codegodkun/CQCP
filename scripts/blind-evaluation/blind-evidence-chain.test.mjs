import assert from "node:assert/strict";
import { mkdtemp, mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import test from "node:test";

import {
  validateDispatch,
  validateSealedChain
} from "./blind-evidence-chain.mjs";
import { sha256File } from "./opinion-contract.mjs";

const roots = [];

test.afterEach(async () => {
  await Promise.all(roots.splice(0).map((root) => rm(root, {
    recursive: true,
    force: true
  })));
});

test("sealed chain binds prompt, input, agent, fork mode, timestamps, and opinion bytes", async () => {
  const root = await makeChain();
  const result = await validateSealedChain(root, {
    requireUnblindAbsent: true
  });
  assert.equal(result.records.length, 3);
  assert.equal(result.seal.status, "SEALED_BEFORE_UNBLIND");
});

test("tampering any opinion after sealing fails closed", async () => {
  const root = await makeChain();
  const path = resolve(
    root,
    "outputs/task-eval-002/codex-opinions/SAMPLE-001.codex.json"
  );
  const opinion = JSON.parse(await readFile(path, "utf8"));
  opinion.opinions[0].confidence = 0.4;
  await writeJson(path, opinion);
  await assert.rejects(
    validateSealedChain(root),
    /Sealed opinion mismatch/
  );
});

test("dispatch and opinion metadata mismatches fail closed", async () => {
  const root = await makeChain();
  const path = resolve(
    root,
    "outputs/task-eval-002/codex-opinions/SAMPLE-002.codex.json"
  );
  const opinion = JSON.parse(await readFile(path, "utf8"));
  opinion.forkTurns = "all";
  await writeJson(path, opinion);
  await assert.rejects(
    validateSealedChain(root),
    /Opinion metadata mismatch/
  );
});

test("sealing precondition rejects a pre-existing unblind report", async () => {
  const root = await makeChain();
  await writeJson(
    resolve(root, "outputs/task-eval-002/unblind-report.json"),
    { status: "STALE" }
  );
  await assert.rejects(
    validateSealedChain(root, { requireUnblindAbsent: true }),
    /Unblind report already exists/
  );
});

test("blind dispatch rejects references to unblinded evidence", async () => {
  const root = await makeChain();
  const path = resolve(root, "outputs/task-eval-002/blind-dispatch.json");
  const dispatch = JSON.parse(await readFile(path, "utf8"));
  dispatch.assignments[0].blindInputPath = "ground-truth/SAMPLE-001.json";
  await writeJson(path, dispatch);
  await assert.rejects(validateDispatch(root), /unblinded reference/);
});

async function makeChain() {
  const root = await mkdtemp(join(tmpdir(), "cqcp-blind-chain-"));
  roots.push(root);
  const promptPath = "scripts/blind-evaluation/blind-opinion-prompt.txt";
  await mkdir(resolve(root, "scripts/blind-evaluation"), { recursive: true });
  await mkdir(resolve(root, "outputs/task-eval-002/blind-inputs"), {
    recursive: true
  });
  await mkdir(resolve(root, "outputs/task-eval-002/codex-opinions"), {
    recursive: true
  });
  await writeFile(resolve(root, promptPath), "strict blind prompt\n", "utf8");
  const promptSha256 = await sha256File(resolve(root, promptPath));
  const assignments = [];
  for (let index = 1; index <= 3; index += 1) {
    const suffix = String(index).padStart(3, "0");
    const sampleId = `SAMPLE-${suffix}`;
    const blindInputPath =
      `outputs/task-eval-002/blind-inputs/${sampleId}.json`;
    await writeJson(resolve(root, blindInputPath), {
      reviewPoints: [{ reviewPointCode: "RP-1" }],
      documentProjection: {
        locations: [{ locationId: "LOC-1", text: "示例合同文本" }]
      }
    });
    assignments.push({
      sampleId,
      evaluator: `codex-blind-agent-r2-${suffix}`,
      agentTaskName: `/root/mvp002_blind_r2_${suffix}`,
      blindInputPath,
      blindInputSha256: await sha256File(resolve(root, blindInputPath)),
      opinionOutputPath:
        `outputs/task-eval-002/codex-opinions/${sampleId}.codex.json`
    });
  }
  const dispatch = {
    schemaVersion: "task-eval-002-blind-dispatch-v1",
    status: "FROZEN_BEFORE_AGENT_DISPATCH",
    createdAt: "2026-07-28T12:00:00.000Z",
    protocol: "TRACK_A_FULL_DOCUMENT_SEMANTIC_OPINION",
    forkTurns: "none",
    historicalContextIncluded: false,
    promptPath,
    promptSha256,
    unblindProhibitedUntilOpinionSeal: true,
    assignments
  };
  const dispatchPath = resolve(
    root,
    "outputs/task-eval-002/blind-dispatch.json"
  );
  await writeJson(dispatchPath, dispatch);
  const dispatchSha256 = await sha256File(dispatchPath);
  const sealedOpinions = [];
  for (const [index, assignment] of assignments.entries()) {
    const opinion = {
      schemaVersion: "task-eval-002-model-opinion-v2",
      status: "ACCEPTED",
      track: dispatch.protocol,
      sampleId: assignment.sampleId,
      evaluator: assignment.evaluator,
      agentTaskName: assignment.agentTaskName,
      agentId: `agent-${index + 1}`,
      forkTurns: "none",
      historicalContextIncluded: false,
      dispatchSha256,
      promptSha256,
      inputSha256: assignment.blindInputSha256,
      startedAt: `2026-07-28T12:0${index + 1}:00.000Z`,
      completedAt: `2026-07-28T12:1${index + 1}:00.000Z`,
      opinions: [{
        reviewPointCode: "RP-1",
        opinion: "CONSISTENT",
        confidence: 0.9,
        candidateValues: ["示例"],
        evidenceQuotes: ["示例合同文本"],
        opaqueLocations: ["LOC-1"],
        insufficiencyReason: null
      }]
    };
    const opinionPath = resolve(root, assignment.opinionOutputPath);
    await writeJson(opinionPath, opinion);
    sealedOpinions.push({
      sampleId: assignment.sampleId,
      evaluator: assignment.evaluator,
      agentTaskName: assignment.agentTaskName,
      agentId: opinion.agentId,
      startedAt: opinion.startedAt,
      completedAt: opinion.completedAt,
      path: assignment.opinionOutputPath,
      sha256: await sha256File(opinionPath),
      inputSha256: assignment.blindInputSha256
    });
  }
  await writeJson(resolve(root, "outputs/task-eval-002/opinion-seal.json"), {
    schemaVersion: "task-eval-002-opinion-seal-v1",
    status: "SEALED_BEFORE_UNBLIND",
    sealedAt: "2026-07-28T12:30:00.000Z",
    dispatchPath: "outputs/task-eval-002/blind-dispatch.json",
    dispatchSha256,
    promptPath,
    promptSha256,
    unblindReportPresentAtSeal: false,
    opinions: sealedOpinions
  });
  return root;
}

async function writeJson(path, value) {
  await writeFile(path, `${JSON.stringify(value, null, 2)}\n`, "utf8");
}
