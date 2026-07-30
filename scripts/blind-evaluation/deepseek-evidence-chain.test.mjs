import assert from "node:assert/strict";
import { cp, mkdir, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

import { validateDeepSeekSeal } from "./deepseek-evidence-chain.mjs";

const scriptRoot = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptRoot, "../..");

test("DeepSeek seal binds all accepted opinions and fails after opinion tampering", async () => {
  const validated = await validateDeepSeekSeal(repoRoot);
  assert.equal(validated.records.length, 3);
  assert.equal(
    validated.records.reduce(
      (total, record) => total + record.opinionCount,
      0
    ),
    27
  );
  assert.ok(
    validated.records.every((record) =>
      record.attempts.some(
        (attempt) =>
          attempt.status === "BLOCKED" &&
          attempt.code === "SCHEMA_INVALID_OR_EMPTY"
      )
    )
  );

  const tempRoot = await mkdtemp(
    path.join(os.tmpdir(), "cqcp-deepseek-seal-")
  );
  try {
    const copies = [
      "outputs/task-eval-002/freeze-manifest.json",
      "outputs/task-eval-002/blind-inputs",
      "outputs/task-eval-002/egress-authorizations",
      "outputs/task-eval-002/deepseek-opinions",
      "outputs/task-eval-002/deepseek-opinion-seal.json",
      "outputs/task-eval-002/deepseek-opinion-revalidation-seal-v3.json",
      "scripts/blind-evaluation/deepseek-runner.mjs",
      "scripts/blind-evaluation/opinion-contract.mjs",
      "scripts/blind-evaluation/strict-json.mjs"
    ];
    for (const relativePath of copies) {
      const source = path.resolve(repoRoot, relativePath);
      const destination = path.resolve(tempRoot, relativePath);
      await mkdir(path.dirname(destination), { recursive: true });
      await cp(source, destination, { recursive: true });
    }
    await validateDeepSeekSeal(tempRoot);

    const manifestPath = path.resolve(
      tempRoot,
      "outputs/task-eval-002/freeze-manifest.json"
    );
    const manifest = JSON.parse(await readFile(manifestPath, "utf8"));
    manifest.externalEgressAuthorized = true;
    await writeFile(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`);
    await assert.rejects(
      () => validateDeepSeekSeal(tempRoot),
      /authorization-neutral/
    );
    await cp(
      path.resolve(
        repoRoot,
        "outputs/task-eval-002/freeze-manifest.json"
      ),
      manifestPath,
      { force: true }
    );

    const authorizationPath = path.resolve(
      tempRoot,
      "outputs/task-eval-002/egress-authorizations/" +
        "CQCP-MVP-DOCX-001.deepseek.json"
    );
    const authorization = JSON.parse(
      await readFile(authorizationPath, "utf8")
    );
    authorization.sampleId = "CQCP-MVP-DOCX-999";
    await writeFile(
      authorizationPath,
      `${JSON.stringify(authorization, null, 2)}\n`
    );
    await assert.rejects(
      () => validateDeepSeekSeal(tempRoot),
      /authorization mismatch/
    );
    await cp(
      path.resolve(
        repoRoot,
        "outputs/task-eval-002/egress-authorizations/" +
          "CQCP-MVP-DOCX-001.deepseek.json"
      ),
      authorizationPath,
      { force: true }
    );

    const opinionPath = path.resolve(
      tempRoot,
      "outputs/task-eval-002/deepseek-opinions/" +
        "CQCP-MVP-DOCX-001.deepseek.final-retry.json"
    );
    const opinion = JSON.parse(await readFile(opinionPath, "utf8"));
    opinion.opinions[0].confidence = 0.01;
    await writeFile(opinionPath, `${JSON.stringify(opinion, null, 2)}\n`);
    await assert.rejects(
      () => validateDeepSeekSeal(tempRoot),
      /DeepSeek opinion seal is invalid/
    );
  } finally {
    await rm(tempRoot, { recursive: true, force: true });
  }
});
