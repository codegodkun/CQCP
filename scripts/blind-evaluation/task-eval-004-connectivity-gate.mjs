import assert from "node:assert/strict";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { pathToFileURL } from "node:url";

import {
  assertDeepSeekTlsEnvironment,
  getDeepSeekModelsJson,
  postDeepSeekJson,
  resolveAndValidateDeepSeekAddresses,
} from "./deepseek-secure-transport.mjs";
import { jsonBytes, sha256 } from "./track-b-holdout-contract.mjs";
import { parseJsonBytesRejectDuplicateKeys } from "./strict-json.mjs";

export const TASK_EVAL_004_CONNECTIVITY_SCHEMA =
  "task-eval-004-deepseek-connectivity-evidence-v1";
export const TASK_EVAL_004_SECRET_ALIAS = "DEEPSEEK_OFFICIAL_EVAL";
export const TASK_EVAL_004_MODEL = "deepseek-v4-pro";
export const TASK_EVAL_004_ENDPOINT = "https://api.deepseek.com:443";
export const TASK_EVAL_004_CONNECTIVITY_PATH =
  "outputs/task-eval-004/track-b-final-v1/connectivity/" +
  "connectivity-evidence.json";

const SYNTHETIC_REQUEST = Object.freeze({
  model: TASK_EVAL_004_MODEL,
  messages: Object.freeze([
    Object.freeze({
      role: "user",
      content:
        "This is a synthetic connectivity probe. Return only JSON: " +
        '{"status":"PONG"}',
    }),
  ]),
  thinking: Object.freeze({ type: "disabled" }),
  response_format: Object.freeze({ type: "json_object" }),
  stream: false,
  temperature: 0,
  max_tokens: 128,
});

const EVIDENCE_KEYS = Object.freeze([
  "schemaVersion",
  "status",
  "taskId",
  "milestoneId",
  "createdAt",
  "endpointOrigin",
  "secretReferenceAlias",
  "secretConfigured",
  "modelsProbe",
  "syntheticProbe",
  "transport",
  "networkCallCount",
  "formalAdmissionAffected",
  "rawKeyPersisted",
  "rawRequestPersisted",
  "rawResponsePersisted",
  "reasoningContentPersisted",
]);

export async function runTaskEval004ConnectivityGate({
  argv = process.argv.slice(2),
  environment = process.env,
  dependencies = {},
} = {}) {
  const [repoArg, mode = "run"] = argv;
  if (!repoArg || !["run", "verify"].includes(mode)) {
    throw new Error(
      "Usage: node task-eval-004-connectivity-gate.mjs " +
        "<repo-root> [run|verify]",
    );
  }
  const repoRoot = resolve(repoArg);
  const evidencePath = resolve(
    repoRoot,
    ...TASK_EVAL_004_CONNECTIVITY_PATH.split("/"),
  );
  if (mode === "verify") {
    const bytes = await readFile(evidencePath);
    const evidence = validateTaskEval004ConnectivityEvidence(
      parseJsonBytesRejectDuplicateKeys(bytes),
    );
    assert.equal(jsonBytes(evidence).equals(bytes), true);
    return {
      status: "CONNECTIVITY_GATE_VERIFIED",
      evidenceSha256: sha256(bytes),
      targetModel: TASK_EVAL_004_MODEL,
      formalAdmissionAffected: false,
    };
  }

  assertDeepSeekTlsEnvironment(environment);
  const secret = environment.DEEPSEEK_API_KEY;
  if (typeof secret !== "string" || !secret.trim()) {
    throw new Error("TASK_EVAL_004_SECRET_MISSING");
  }
  if (
    environment.DEEPSEEK_SECRET_REFERENCE_ALIAS !==
    TASK_EVAL_004_SECRET_ALIAS
  ) {
    throw new Error("TASK_EVAL_004_SECRET_ALIAS_INVALID");
  }

  const now = dependencies.now ?? (() => new Date());
  const resolveAddresses =
    dependencies.resolveAddresses ?? resolveAndValidateDeepSeekAddresses;
  const getModels = dependencies.getModels ?? getDeepSeekModelsJson;
  const postChat = dependencies.postChat ?? postDeepSeekJson;
  const createdAt = now().toISOString();
  const deadlineAt = new Date(
    Date.parse(createdAt) + 2 * 60_000,
  ).toISOString();
  const addresses = await resolveAddresses({
    deadlineAt,
    timeoutMs: 30_000,
  });
  const pinnedAddressSetSha256 = sha256(
    Buffer.from(JSON.stringify(addresses), "utf8"),
  );

  const modelsResponse = await getModels({
    secret,
    addresses,
    deadlineAt,
    timeoutMs: 30_000,
    environment,
  });
  assert.equal(modelsResponse.status, 200, "CONNECTIVITY_MODELS_HTTP_REJECTED");
  assert.equal(
    modelsResponse.contentTypeClass,
    "APPLICATION_JSON",
    "CONNECTIVITY_MODELS_MEDIA_TYPE_INVALID",
  );
  const modelsEnvelope = parseJsonBytesRejectDuplicateKeys(
    modelsResponse.body,
  );
  const availableModelIds = validateModelsEnvelope(modelsEnvelope);
  assert.ok(
    availableModelIds.includes(TASK_EVAL_004_MODEL),
    "CONNECTIVITY_TARGET_MODEL_UNAVAILABLE",
  );

  const requestBytes = Buffer.from(
    JSON.stringify(SYNTHETIC_REQUEST),
    "utf8",
  );
  const chatResponse = await postChat({
    body: requestBytes,
    secret,
    addresses,
    deadlineAt,
    timeoutMs: 60_000,
    environment,
  });
  assert.equal(chatResponse.status, 200, "CONNECTIVITY_CHAT_HTTP_REJECTED");
  assert.equal(
    chatResponse.contentTypeClass,
    "APPLICATION_JSON",
    "CONNECTIVITY_CHAT_MEDIA_TYPE_INVALID",
  );
  const chatEnvelope = parseJsonBytesRejectDuplicateKeys(chatResponse.body);
  const accepted = validateSyntheticChatEnvelope(chatEnvelope);

  const evidence = validateTaskEval004ConnectivityEvidence({
    schemaVersion: TASK_EVAL_004_CONNECTIVITY_SCHEMA,
    status: "CONNECTIVITY_GATE_GO",
    taskId: "TASK-EVAL-004",
    milestoneId: "MILESTONE-MVP-002-TRACK-B-FINAL",
    createdAt,
    endpointOrigin: TASK_EVAL_004_ENDPOINT,
    secretReferenceAlias: TASK_EVAL_004_SECRET_ALIAS,
    secretConfigured: true,
    modelsProbe: {
      httpStatus: modelsResponse.status,
      contentTypeClass: modelsResponse.contentTypeClass,
      availableModelIds,
      targetModelAvailable: true,
    },
    syntheticProbe: {
      requestConfigSha256: sha256(requestBytes),
      httpStatus: chatResponse.status,
      contentTypeClass: chatResponse.contentTypeClass,
      requestedModel: TASK_EVAL_004_MODEL,
      responseModel: accepted.responseModel,
      finishReason: accepted.finishReason,
      strictJsonSchemaValid: true,
      sentinelMatched: true,
      thinkingDisabled: true,
      jsonMode: true,
      streaming: false,
    },
    transport: {
      endpointHost: "api.deepseek.com",
      pinnedAddressSetSha256,
      redirectsAllowed: false,
      systemProxyUsed: false,
      tlsVerificationRequired: true,
    },
    networkCallCount: 2,
    formalAdmissionAffected: false,
    rawKeyPersisted: false,
    rawRequestPersisted: false,
    rawResponsePersisted: false,
    reasoningContentPersisted: false,
  });
  const bytes = jsonBytes(evidence);
  await mkdir(dirname(evidencePath), { recursive: true });
  await writeFile(evidencePath, bytes, { flag: "wx" });
  return {
    status: evidence.status,
    evidenceSha256: sha256(bytes),
    availableModelIds,
    targetModel: TASK_EVAL_004_MODEL,
    finishReason: accepted.finishReason,
    formalAdmissionAffected: false,
  };
}

export function validateTaskEval004ConnectivityEvidence(evidence) {
  assertPlainObject(evidence, "CONNECTIVITY_EVIDENCE_OBJECT_REQUIRED");
  assert.deepEqual(Object.keys(evidence), EVIDENCE_KEYS);
  assert.equal(evidence.schemaVersion, TASK_EVAL_004_CONNECTIVITY_SCHEMA);
  assert.equal(evidence.status, "CONNECTIVITY_GATE_GO");
  assert.equal(evidence.taskId, "TASK-EVAL-004");
  assert.equal(evidence.milestoneId, "MILESTONE-MVP-002-TRACK-B-FINAL");
  assert.ok(isCanonicalIso(evidence.createdAt));
  assert.equal(evidence.endpointOrigin, TASK_EVAL_004_ENDPOINT);
  assert.equal(
    evidence.secretReferenceAlias,
    TASK_EVAL_004_SECRET_ALIAS,
  );
  assert.equal(evidence.secretConfigured, true);
  assertPlainObject(evidence.modelsProbe);
  assert.deepEqual(Object.keys(evidence.modelsProbe), [
    "httpStatus",
    "contentTypeClass",
    "availableModelIds",
    "targetModelAvailable",
  ]);
  assert.equal(evidence.modelsProbe.httpStatus, 200);
  assert.equal(evidence.modelsProbe.contentTypeClass, "APPLICATION_JSON");
  assert.deepEqual(
    evidence.modelsProbe.availableModelIds,
    [...evidence.modelsProbe.availableModelIds].sort(),
  );
  assert.equal(
    new Set(evidence.modelsProbe.availableModelIds).size,
    evidence.modelsProbe.availableModelIds.length,
  );
  assert.ok(evidence.modelsProbe.availableModelIds.includes(TASK_EVAL_004_MODEL));
  assert.equal(evidence.modelsProbe.targetModelAvailable, true);
  assertPlainObject(evidence.syntheticProbe);
  assert.deepEqual(Object.keys(evidence.syntheticProbe), [
    "requestConfigSha256",
    "httpStatus",
    "contentTypeClass",
    "requestedModel",
    "responseModel",
    "finishReason",
    "strictJsonSchemaValid",
    "sentinelMatched",
    "thinkingDisabled",
    "jsonMode",
    "streaming",
  ]);
  assert.match(evidence.syntheticProbe.requestConfigSha256, /^[a-f0-9]{64}$/);
  assert.equal(evidence.syntheticProbe.httpStatus, 200);
  assert.equal(evidence.syntheticProbe.contentTypeClass, "APPLICATION_JSON");
  assert.equal(evidence.syntheticProbe.requestedModel, TASK_EVAL_004_MODEL);
  assert.equal(evidence.syntheticProbe.responseModel, TASK_EVAL_004_MODEL);
  assert.equal(evidence.syntheticProbe.finishReason, "stop");
  assert.equal(evidence.syntheticProbe.strictJsonSchemaValid, true);
  assert.equal(evidence.syntheticProbe.sentinelMatched, true);
  assert.equal(evidence.syntheticProbe.thinkingDisabled, true);
  assert.equal(evidence.syntheticProbe.jsonMode, true);
  assert.equal(evidence.syntheticProbe.streaming, false);
  assertPlainObject(evidence.transport);
  assert.deepEqual(Object.keys(evidence.transport), [
    "endpointHost",
    "pinnedAddressSetSha256",
    "redirectsAllowed",
    "systemProxyUsed",
    "tlsVerificationRequired",
  ]);
  assert.equal(evidence.transport.endpointHost, "api.deepseek.com");
  assert.match(evidence.transport.pinnedAddressSetSha256, /^[a-f0-9]{64}$/);
  assert.equal(evidence.transport.redirectsAllowed, false);
  assert.equal(evidence.transport.systemProxyUsed, false);
  assert.equal(evidence.transport.tlsVerificationRequired, true);
  assert.equal(evidence.networkCallCount, 2);
  assert.equal(evidence.formalAdmissionAffected, false);
  assert.equal(evidence.rawKeyPersisted, false);
  assert.equal(evidence.rawRequestPersisted, false);
  assert.equal(evidence.rawResponsePersisted, false);
  assert.equal(evidence.reasoningContentPersisted, false);
  return evidence;
}

function validateModelsEnvelope(envelope) {
  assertPlainObject(envelope, "CONNECTIVITY_MODELS_ENVELOPE_INVALID");
  assert.ok(Array.isArray(envelope.data));
  const ids = envelope.data.map((entry) => {
    assertPlainObject(entry, "CONNECTIVITY_MODEL_ENTRY_INVALID");
    assert.equal(typeof entry.id, "string");
    assert.match(entry.id, /^[a-z0-9][a-z0-9._-]{0,127}$/);
    return entry.id;
  });
  const sorted = [...new Set(ids)].sort();
  assert.equal(sorted.length, ids.length, "CONNECTIVITY_MODEL_IDS_DUPLICATED");
  return sorted;
}

function validateSyntheticChatEnvelope(envelope) {
  assertPlainObject(envelope, "CONNECTIVITY_CHAT_ENVELOPE_INVALID");
  assert.equal(envelope.model, TASK_EVAL_004_MODEL);
  assert.ok(Array.isArray(envelope.choices));
  assert.equal(envelope.choices.length, 1);
  const choice = envelope.choices[0];
  assertPlainObject(choice, "CONNECTIVITY_CHAT_CHOICE_INVALID");
  assert.equal(choice.finish_reason, "stop");
  assertPlainObject(choice.message, "CONNECTIVITY_CHAT_MESSAGE_INVALID");
  assert.equal(typeof choice.message.content, "string");
  const content = parseJsonBytesRejectDuplicateKeys(
    Buffer.from(choice.message.content, "utf8"),
  );
  assertPlainObject(content, "CONNECTIVITY_CHAT_CONTENT_INVALID");
  assert.deepEqual(Object.keys(content), ["status"]);
  assert.equal(content.status, "PONG");
  return {
    responseModel: envelope.model,
    finishReason: choice.finish_reason,
  };
}

function assertPlainObject(value, message = "PLAIN_OBJECT_REQUIRED") {
  assert.ok(value && typeof value === "object" && !Array.isArray(value), message);
  assert.equal(Object.getPrototypeOf(value), Object.prototype, message);
}

const isCanonicalIso = (value) =>
  typeof value === "string" &&
  !Number.isNaN(Date.parse(value)) &&
  new Date(value).toISOString() === value;

if (
  process.argv[1] &&
  import.meta.url === pathToFileURL(resolve(process.argv[1])).href
) {
  const result = await runTaskEval004ConnectivityGate();
  process.stdout.write(`${JSON.stringify(result)}\n`);
}
