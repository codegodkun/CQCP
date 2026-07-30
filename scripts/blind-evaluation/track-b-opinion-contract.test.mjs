import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { cp, mkdtemp, mkdir, readFile, rm, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

const scriptRoot = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptRoot, "../..");
const contractPath = path.join(scriptRoot, "track-b-opinion-contract.mjs");

const makeFixture = async () => {
  const root = await mkdtemp(path.join(os.tmpdir(), "cqcp-track-b-"));
  const outputRoot = path.join(root, "outputs/task-eval-002");
  await mkdir(outputRoot, { recursive: true });
  await mkdir(
    path.join(root, "outputs/task-034-mvp-e2e-acceptance-v3"),
    { recursive: true },
  );
  await mkdir(path.join(root, "scripts/blind-evaluation"), { recursive: true });
  for (const directory of [
    "track-b-inputs-v1",
    "track-b-codex-opinions",
  ]) {
    await cp(
      path.join(repoRoot, "outputs/task-eval-002", directory),
      path.join(outputRoot, directory),
      { recursive: true },
    );
  }
  for (const file of [
    "track-b-dispatch.json",
    "track-b-dispatch.sha256",
  ]) {
    await cp(
      path.join(repoRoot, "outputs/task-eval-002", file),
      path.join(outputRoot, file),
    );
  }
  await cp(
    path.join(
      repoRoot,
      "outputs/task-034-mvp-e2e-acceptance-v3/run-manifest.json",
    ),
    path.join(
      root,
      "outputs/task-034-mvp-e2e-acceptance-v3/run-manifest.json",
    ),
  );
  await cp(
    path.join(repoRoot, "scripts/blind-evaluation/track-b-opinion-prompt.txt"),
    path.join(root, "scripts/blind-evaluation/track-b-opinion-prompt.txt"),
  );
  return root;
};

const runContract = (root, mode = "create") =>
  spawnSync(process.execPath, [contractPath, root, mode], {
    cwd: repoRoot,
    encoding: "utf8",
  });

test("Track B v2 seal binds dispatch, prompt, R7, agents, times, and opinions", async () => {
  const root = await makeFixture();
  try {
    const create = runContract(root);
    assert.equal(create.status, 0, create.stderr);
    const verify = runContract(root, "verify");
    assert.equal(verify.status, 0, verify.stderr);
    const seal = JSON.parse(
      await readFile(
        path.join(root, "outputs/task-eval-002/track-b-codex-seal.json"),
        "utf8",
      ),
    );
    assert.equal(seal.opinionCount, 3);
    assert.equal(seal.evaluationCount, 27);
    assert.equal(seal.abstentionCount, 27);
    assert.equal(
      seal.providerAdmission,
      "NOT_ESTABLISHED_ZERO_ELIGIBLE_SAMPLE",
    );
    assert.deepEqual(
      seal.entries.map((entry) => entry.forkTurns),
      ["none", "none", "none"],
    );
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});

test("Track B strict schema rejects a string disguised as an empty array", async () => {
  const root = await makeFixture();
  try {
    const opinionPath = path.join(
      root,
      "outputs/task-eval-002/track-b-codex-opinions/CQCP-MVP-DOCX-001.codex.json",
    );
    const opinion = JSON.parse(await readFile(opinionPath, "utf8"));
    opinion.evaluations[0].selectedOccurrenceIds = "";
    await writeFile(opinionPath, JSON.stringify(opinion), "utf8");
    const result = runContract(root);
    assert.notEqual(result.status, 0);
    assert.match(result.stderr, /selectedOccurrenceIds must be an array/);
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});

test("Track B seal rejects packet, manifest, or prompt changes after dispatch", async () => {
  for (const relativePath of [
    "outputs/task-eval-002/track-b-inputs-v1/CQCP-MVP-DOCX-001.track-b.json",
    "outputs/task-eval-002/track-b-inputs-v1/manifest.json",
    "scripts/blind-evaluation/track-b-opinion-prompt.txt",
  ]) {
    const root = await makeFixture();
    try {
      const target = path.join(root, ...relativePath.split("/"));
      await writeFile(target, `${await readFile(target, "utf8")}\n`, "utf8");
      const result = runContract(root);
      assert.notEqual(result.status, 0, relativePath);
      assert.match(
        result.stderr,
        /TRACK_B_(PACKET_CHANGED|PACKET_MANIFEST_CHANGED|PROMPT_CHANGED)/,
      );
    } finally {
      await rm(root, { recursive: true, force: true });
    }
  }
});

test("Track B seal rejects a dispatch changed after its pre-agent hash freeze", async () => {
  const root = await makeFixture();
  try {
    const dispatchPath = path.join(
      root,
      "outputs/task-eval-002/track-b-dispatch.json",
    );
    await writeFile(
      dispatchPath,
      `${await readFile(dispatchPath, "utf8")}\n`,
      "utf8",
    );
    const result = runContract(root);
    assert.notEqual(result.status, 0);
    assert.match(result.stderr, /TRACK_B_DISPATCH_CHANGED/);
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});

test("Track B seal rejects an opinion completed before dispatch", async () => {
  const root = await makeFixture();
  try {
    const opinionPath = path.join(
      root,
      "outputs/task-eval-002/track-b-codex-opinions/CQCP-MVP-DOCX-001.codex.json",
    );
    const opinion = JSON.parse(await readFile(opinionPath, "utf8"));
    opinion.completedAt = "2026-07-29T05:00:00.000Z";
    await writeFile(opinionPath, JSON.stringify(opinion), "utf8");
    const result = runContract(root);
    assert.notEqual(result.status, 0);
    assert.match(result.stderr, /TRACK_B_OPINION_TIME_ORDER_INVALID/);
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});
