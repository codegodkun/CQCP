import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import { mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { fileURLToPath, pathToFileURL } from "node:url";

import {
  validateDeepSeekCompletionEnvelope,
  validateOpinionPayload
} from "./opinion-contract.mjs";

const scriptRoot = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptRoot, "../..");
const inputPath = path.resolve(
  repoRoot,
  "outputs/task-eval-002/blind-inputs/CQCP-MVP-DOCX-001.track-a.blind.json"
);
const opinionPath = path.resolve(
  repoRoot,
  "outputs/task-eval-002/codex-opinions/CQCP-MVP-DOCX-001.codex.json"
);
const runnerPath = path.resolve(scriptRoot, "deepseek-runner.mjs");
const blindInput = JSON.parse(await readFile(inputPath, "utf8"));
const acceptedOpinion = JSON.parse(await readFile(opinionPath, "utf8"));
const validPayload = { opinions: acceptedOpinion.opinions };

function captureThrown(operation) {
  try {
    operation();
  } catch (error) {
    return error;
  }
  assert.fail("expected operation to throw");
}

test("strict opinion schema accepts the sealed Codex opinion", () => {
  assert.equal(validateOpinionPayload(blindInput, validPayload), validPayload);
});

test("strict opinion schema rejects extra fields and unknown locations", () => {
  const extraField = structuredClone(validPayload);
  extraField.opinions[0].reasoning = "must never be persisted";
  assert.throws(
    () => validateOpinionPayload(blindInput, extraField),
    /not allowed/
  );

  const unknownLocation = structuredClone(validPayload);
  unknownLocation.opinions[0].opaqueLocations = ["LOC-UNKNOWN"];
  assert.throws(
    () => validateOpinionPayload(blindInput, unknownLocation),
    /unknown location/
  );
});

test("completion admission requires stop, non-empty content, and strict schema", () => {
  const validEnvelope = {
    choices: [
      {
        finish_reason: "stop",
        message: {
          content: JSON.stringify(validPayload),
          reasoning_content: "must be ignored"
        }
      }
    ]
  };
  assert.deepEqual(
    validateDeepSeekCompletionEnvelope(blindInput, validEnvelope),
    validPayload
  );

  const finishReasonError = captureThrown(() =>
    validateDeepSeekCompletionEnvelope(blindInput, {
      choices: [
        {
          finish_reason: "length",
          message: { content: JSON.stringify(validPayload) }
        }
      ]
    })
  );
  assert.match(finishReasonError.message, /incomplete/);
  assert.equal(finishReasonError.admissionCode, "FINISH_REASON_NOT_STOP");

  const emptyContentError = captureThrown(() =>
    validateDeepSeekCompletionEnvelope(blindInput, {
      choices: [
        {
          finish_reason: "stop",
          message: { content: "", reasoning_content: "reasoning only" }
        }
      ]
    })
  );
  assert.match(emptyContentError.message, /incomplete/);
  assert.equal(emptyContentError.admissionCode, "CONTENT_EMPTY");

  const strictSchemaError = captureThrown(() =>
    validateDeepSeekCompletionEnvelope(blindInput, {
      choices: [
        {
          finish_reason: "stop",
          message: { content: "{\"opinions\":[],\"extra\":true}" }
        }
      ]
    })
  );
  assert.match(strictSchemaError.message, /schema is invalid/);
  assert.equal(strictSchemaError.admissionCode, "FIELD_SET_INVALID");

  const duplicateKeyError = captureThrown(() =>
    validateDeepSeekCompletionEnvelope(blindInput, {
      choices: [
        {
          finish_reason: "stop",
          message: {
            content:
              `{"opinions":${JSON.stringify(validPayload.opinions)},` +
              `"opin\\u0069ons":[]}`
          }
        }
      ]
    })
  );
  assert.equal(duplicateKeyError.admissionCode, "CONTENT_NOT_JSON");
});

test("missing egress authorization fails before network access", async () => {
  const tempRoot = await mkdtemp(path.join(os.tmpdir(), "cqcp-eval-test-"));
  try {
    const outputPath = path.join(tempRoot, "blocked.json");
    const missingAuthorization = path.join(tempRoot, "missing.json");
    const result = spawnSync(
      process.execPath,
      [runnerPath, inputPath, outputPath, missingAuthorization, "preflight"],
      { cwd: repoRoot, encoding: "utf8" }
    );
    assert.equal(result.status, 0, result.stderr);
    const output = JSON.parse(await readFile(outputPath, "utf8"));
    assert.equal(output.code, "EXTERNAL_EGRESS_NOT_AUTHORIZED");
    assert.equal(output.networkAttempted, false);
  } finally {
    await rm(tempRoot, { recursive: true, force: true });
  }
});

test("authorized preflight still performs no network access", async () => {
  const tempRoot = await mkdtemp(path.join(os.tmpdir(), "cqcp-eval-test-"));
  try {
    const outputPath = path.join(tempRoot, "preflight.json");
    const authorizationPath = path.join(tempRoot, "authorization.json");
    const bytes = await readFile(inputPath);
    const inputSha256 = createHash("sha256").update(bytes).digest("hex");
    await writeFile(
      authorizationPath,
      JSON.stringify({
        schemaVersion: "task-eval-002-egress-authorization-v1",
        authorized: true,
        scope: "DEEPSEEK_EVALUATION_EGRESS",
        sampleId: blindInput.sampleId,
        inputSha256,
        endpointHost: "api.deepseek.com",
        model: "deepseek-v4-pro",
        purpose: "TEST_ONLY TASK-EVAL-002 DeepSeek Track A",
        approvedBy: "TEST_ONLY",
        approvedAt: new Date(Date.now() - 1000).toISOString(),
        expiresAt: new Date(Date.now() + 60_000).toISOString()
      })
    );
    const result = spawnSync(
      process.execPath,
      [runnerPath, inputPath, outputPath, authorizationPath, "preflight"],
      { cwd: repoRoot, encoding: "utf8" }
    );
    assert.equal(result.status, 0, result.stderr);
    const output = JSON.parse(await readFile(outputPath, "utf8"));
    assert.equal(output.code, "PREFLIGHT_AUTHORIZED_NO_NETWORK");
    assert.equal(output.networkAttempted, false);
  } finally {
    await rm(tempRoot, { recursive: true, force: true });
  }
});

test("every post-fetch failure truthfully records networkAttempted", async () => {
  const tempRoot = await mkdtemp(path.join(os.tmpdir(), "cqcp-eval-network-test-"));
  try {
    const authorizationPath = path.join(tempRoot, "authorization.json");
    const mockModulePath = path.join(tempRoot, "mock-fetch.mjs");
    const bytes = await readFile(inputPath);
    const inputSha256 = createHash("sha256").update(bytes).digest("hex");
    await writeFile(
      authorizationPath,
      JSON.stringify({
        schemaVersion: "task-eval-002-egress-authorization-v1",
        authorized: true,
        scope: "DEEPSEEK_EVALUATION_EGRESS",
        sampleId: blindInput.sampleId,
        inputSha256,
        endpointHost: "api.deepseek.com",
        model: "deepseek-v4-pro",
        purpose: "TEST_ONLY TASK-EVAL-002 DeepSeek Track A",
        approvedBy: "TEST_ONLY",
        approvedAt: new Date(Date.now() - 1000).toISOString(),
        expiresAt: new Date(Date.now() + 60_000).toISOString()
      })
    );
    await writeFile(
      mockModulePath,
      `
        import { appendFileSync } from "node:fs";
        globalThis.fetch = async () => {
          appendFileSync(process.env.MOCK_FETCH_COUNTER, "1");
          const scenario = process.env.MOCK_FETCH_SCENARIO;
          if (scenario === "NETWORK") throw new Error("synthetic network failure");
          if (scenario === "SCHEMA") {
            return {
              ok: true,
              status: 200,
              headers: new Headers({ "content-type": "application/json" }),
              text: async () => JSON.stringify({ choices: [] })
            };
          }
          if (scenario === "BODY") {
            return {
              ok: true,
              status: 200,
              headers: new Headers({ "content-type": "text/html; charset=utf-8" }),
              text: async () => "<html>synthetic</html>"
            };
          }
          const status = Number(scenario);
          return { ok: false, status };
        };
      `,
      "utf8"
    );

    const scenarios = [
      ["NETWORK", 5, "NETWORK_OR_TIMEOUT"],
      ["401", 6, "AUTHENTICATION_FAILED"],
      ["429", 6, "RATE_LIMITED"],
      ["503", 6, "UPSTREAM_5XX"],
      ["SCHEMA", 7, "SCHEMA_INVALID_OR_EMPTY"],
      ["BODY", 7, "SCHEMA_INVALID_OR_EMPTY"]
    ];
    for (const [scenario, exitCode, code] of scenarios) {
      const outputPath = path.join(tempRoot, `${scenario}.json`);
      const counterPath = path.join(tempRoot, `${scenario}.count`);
      const result = spawnSync(
        process.execPath,
        [runnerPath, inputPath, outputPath, authorizationPath],
        {
          cwd: repoRoot,
          encoding: "utf8",
          env: {
            ...process.env,
            DEEPSEEK_API_KEY: "TEST_ONLY_SECRET",
            MOCK_FETCH_SCENARIO: scenario,
            MOCK_FETCH_COUNTER: counterPath,
            NODE_OPTIONS: `--import=${pathToFileURL(mockModulePath).href}`
          }
        }
      );
      assert.equal(result.status, exitCode, result.stderr);
      const output = JSON.parse(await readFile(outputPath, "utf8"));
      assert.equal(output.code, code);
      assert.equal(output.networkAttempted, true);
      if (scenario === "SCHEMA") {
        assert.equal(output.detailCode, "CHOICE_COUNT_INVALID");
      }
      if (scenario === "BODY") {
        assert.equal(output.detailCode, "RESPONSE_BODY_NOT_JSON");
        assert.equal(output.responseMediaType, "text/html");
        assert.equal(output.responseShape, "HTML_LIKE");
        assert.equal(output.responseLength, 22);
      }
      assert.equal(await readFile(counterPath, "utf8"), "1");
    }
  } finally {
    await rm(tempRoot, { recursive: true, force: true });
  }
});
