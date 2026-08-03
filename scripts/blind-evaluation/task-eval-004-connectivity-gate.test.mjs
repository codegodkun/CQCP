import assert from "node:assert/strict";
import { mkdtemp, readFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { resolve } from "node:path";
import test from "node:test";

import {
  TASK_EVAL_004_CONNECTIVITY_PATH,
  runTaskEval004ConnectivityGate,
  validateTaskEval004ConnectivityEvidence,
} from "./task-eval-004-connectivity-gate.mjs";

const environment = Object.freeze({
  DEEPSEEK_API_KEY: "TEST_ONLY_SECRET",
  DEEPSEEK_SECRET_REFERENCE_ALIAS: "DEEPSEEK_OFFICIAL_EVAL",
});
const addresses = Object.freeze([{ address: "8.8.8.8", family: 4 }]);
const fixedNow = () => new Date("2026-08-03T03:00:00.000Z");

test("connectivity gate creates minimal immutable evidence and verifies it", async () => {
  const root = await tempRoot();
  let modelsCalls = 0;
  let chatCalls = 0;
  const dependencies = {
    now: fixedNow,
    resolveAddresses: async () => addresses,
    getModels: async () => {
      modelsCalls += 1;
      return jsonResponse({
        object: "list",
        data: [
          { id: "deepseek-v4-pro", object: "model" },
          { id: "deepseek-v4-flash", object: "model" },
        ],
      });
    },
    postChat: async ({ body }) => {
      chatCalls += 1;
      const request = JSON.parse(body.toString("utf8"));
      assert.equal(request.model, "deepseek-v4-pro");
      assert.deepEqual(request.thinking, { type: "disabled" });
      assert.deepEqual(request.response_format, { type: "json_object" });
      assert.equal(request.stream, false);
      return jsonResponse({
        id: "probe-1",
        model: "deepseek-v4-pro",
        created: 1,
        choices: [
          {
            finish_reason: "stop",
            message: { content: '{"status":"PONG"}' },
          },
        ],
      });
    },
  };
  const result = await runTaskEval004ConnectivityGate({
    argv: [root, "run"],
    environment,
    dependencies,
  });
  assert.equal(result.status, "CONNECTIVITY_GATE_GO");
  assert.equal(result.finishReason, "stop");
  assert.equal(modelsCalls, 1);
  assert.equal(chatCalls, 1);
  const bytes = await readFile(
    resolve(root, ...TASK_EVAL_004_CONNECTIVITY_PATH.split("/")),
  );
  const evidence = validateTaskEval004ConnectivityEvidence(
    JSON.parse(bytes.toString("utf8")),
  );
  assert.equal(evidence.formalAdmissionAffected, false);
  assert.equal(JSON.stringify(evidence).includes("TEST_ONLY_SECRET"), false);
  assert.equal("rawResponse" in evidence, false);
  const verified = await runTaskEval004ConnectivityGate({
    argv: [root, "verify"],
  });
  assert.equal(verified.status, "CONNECTIVITY_GATE_VERIFIED");
  assert.equal(verified.evidenceSha256, result.evidenceSha256);
  await assert.rejects(
    runTaskEval004ConnectivityGate({
      argv: [root, "run"],
      environment,
      dependencies,
    }),
    (error) => error.code === "EEXIST",
  );
});

test("missing or incorrect explicit Secret Reference fails before network", async () => {
  for (const invalidEnvironment of [
    { DEEPSEEK_SECRET_REFERENCE_ALIAS: "DEEPSEEK_OFFICIAL_EVAL" },
    { DEEPSEEK_API_KEY: "TEST_ONLY_SECRET" },
    {
      DEEPSEEK_API_KEY: "TEST_ONLY_SECRET",
      DEEPSEEK_SECRET_REFERENCE_ALIAS: "AUTO_DISCOVERY",
    },
  ]) {
    let networkCalled = false;
    await assert.rejects(
      runTaskEval004ConnectivityGate({
        argv: [await tempRoot(), "run"],
        environment: invalidEnvironment,
        dependencies: {
          resolveAddresses: async () => {
            networkCalled = true;
            return addresses;
          },
        },
      }),
      /TASK_EVAL_004_SECRET_(MISSING|ALIAS_INVALID)/,
    );
    assert.equal(networkCalled, false);
  }
});

test("missing target model blocks before synthetic chat and writes no evidence", async () => {
  const root = await tempRoot();
  let chatCalled = false;
  await assert.rejects(
    runTaskEval004ConnectivityGate({
      argv: [root, "run"],
      environment,
      dependencies: {
        now: fixedNow,
        resolveAddresses: async () => addresses,
        getModels: async () =>
          jsonResponse({ data: [{ id: "deepseek-v4-flash" }] }),
        postChat: async () => {
          chatCalled = true;
          throw new Error("must not run");
        },
      },
    }),
    /CONNECTIVITY_TARGET_MODEL_UNAVAILABLE/,
  );
  assert.equal(chatCalled, false);
  await assert.rejects(
    readFile(resolve(root, ...TASK_EVAL_004_CONNECTIVITY_PATH.split("/"))),
    (error) => error.code === "ENOENT",
  );
});

test("non-stop or malformed synthetic opinion fails closed without evidence", async () => {
  for (const choice of [
    {
      finish_reason: "length",
      message: { content: '{"status":"PONG"}' },
    },
    {
      finish_reason: "stop",
      message: { content: '{"status":"NOPE"}' },
    },
    {
      finish_reason: "stop",
      message: { content: '{"status":"PONG","extra":true}' },
    },
  ]) {
    const root = await tempRoot();
    await assert.rejects(
      runTaskEval004ConnectivityGate({
        argv: [root, "run"],
        environment,
        dependencies: {
          now: fixedNow,
          resolveAddresses: async () => addresses,
          getModels: async () =>
            jsonResponse({ data: [{ id: "deepseek-v4-pro" }] }),
          postChat: async () =>
            jsonResponse({
              model: "deepseek-v4-pro",
              choices: [choice],
            }),
        },
      }),
    );
    await assert.rejects(
      readFile(resolve(root, ...TASK_EVAL_004_CONNECTIVITY_PATH.split("/"))),
      (error) => error.code === "ENOENT",
    );
  }
});

async function tempRoot() {
  return mkdtemp(resolve(tmpdir(), "task-eval-004-connectivity-"));
}

function jsonResponse(value) {
  return {
    status: 200,
    contentTypeClass: "APPLICATION_JSON",
    body: Buffer.from(JSON.stringify(value), "utf8"),
  };
}
