import assert from "node:assert/strict";
import { readFile, writeFile } from "node:fs/promises";
import { resolve } from "node:path";
import { pathToFileURL } from "node:url";

import {
  assertDeepSeekTlsEnvironment,
  postDeepSeekJson,
  resolveAndValidateDeepSeekAddresses
} from "./deepseek-secure-transport.mjs";
import {
  loadAndValidateMvp002StandingEgressGrant
} from "./mvp002-standing-egress-grant.mjs";
import { parseJsonBytesRejectDuplicateKeys } from "./strict-json.mjs";
import {
  buildTrackBSchemaDiagnosticCalls,
  classifyTrackBSchemaDiagnosticEnvelope,
  jsonBytes,
  sha256,
  TRACK_B_SCHEMA_DIAGNOSTIC_CALLS_PER_PASS,
  TRACK_B_SCHEMA_DIAGNOSTIC_CLASSES,
  TRACK_B_SCHEMA_DIAGNOSTIC_MODEL,
  validateTrackBSchemaDiagnosticInput
} from "./track-b-schema-diagnostic-contract.mjs";

export async function runTrackBSchemaDiagnostic({
  argv = process.argv.slice(2),
  environment = process.env,
  dependencies = {}
} = {}) {
  const [repoArg, phase = "baseline"] = argv;
  assert.ok(repoArg, "repo root is required");
  const repoRoot = resolve(repoArg);
  const now = dependencies.now ?? (() => new Date());
  const resolveAddresses = dependencies.resolveAddresses ??
    resolveAndValidateDeepSeekAddresses;
  const transport = dependencies.transport ?? postDeepSeekJson;
  const startedAt = now().toISOString();
  const deadlineAt = new Date(
    Date.parse(startedAt) + 20 * 60_000
  ).toISOString();
  const rootRelative =
    `outputs/task-eval-005/schema-diagnostic-v1/${phase}`;
  const at = (name) =>
    resolve(repoRoot, ...`${rootRelative}/${name}`.split("/"));

  assertDeepSeekTlsEnvironment(environment);
  const secret = environment.DEEPSEEK_API_KEY;
  assert.ok(secret?.trim(), "SECRET_MISSING");

  const [
    inputEvidence,
    callSetEvidence,
    dispatchEvidence,
    receiptEvidence,
    manifestEvidence,
    grantEvidence
  ] = await Promise.all([
    readJson(at("diagnostic-input.json")),
    readJson(at("provider-call-set.json")),
    readJson(at("dispatch.json")),
    readJson(at("derived-egress-receipt.json")),
    readJson(at("preflight-manifest.json")),
    loadAndValidateMvp002StandingEgressGrant(
      resolve(
        repoRoot,
        "scripts/blind-evaluation/mvp002-standing-egress-grant.json"
      )
    )
  ]);
  const input = validateTrackBSchemaDiagnosticInput(inputEvidence.value);
  const dispatch = dispatchEvidence.value;
  const receipt = receiptEvidence.value;
  const manifest = manifestEvidence.value;
  assert.equal(dispatch.phase, phase);
  assert.equal(receipt.phase, phase);
  assert.equal(manifest.phase, phase);
  assert.equal(dispatch.actualInputSha256, sha256(inputEvidence.bytes));
  assert.equal(receipt.actualInputSha256, sha256(inputEvidence.bytes));
  assert.equal(receipt.dispatchSha256, sha256(dispatchEvidence.bytes));
  assert.equal(
    receipt.providerCallSetSha256,
    sha256(callSetEvidence.bytes)
  );
  assert.equal(
    receipt.standingGrantSha256,
    sha256(grantEvidence.bytes)
  );
  assert.equal(receipt.endpointOrigin, "https://api.deepseek.com:443");
  assert.equal(receipt.model, TRACK_B_SCHEMA_DIAGNOSTIC_MODEL);
  assert.equal(receipt.callCount, TRACK_B_SCHEMA_DIAGNOSTIC_CALLS_PER_PASS);
  assert.equal(receipt.formalAdmissionAffected, false);

  const promptPath = resolve(repoRoot, ...dispatch.promptPath.split("/"));
  const promptBytes = await readFile(promptPath);
  assert.equal(sha256(promptBytes), dispatch.promptSha256);
  const calls = buildTrackBSchemaDiagnosticCalls(input, promptBytes);
  assert.deepEqual(
    calls.map((call) => ({
      callIndex: call.callIndex,
      callId: call.callId,
      packetId: call.packetId,
      modelInputSha256: call.modelInputSha256,
      outboundRequestSha256: call.outboundRequestSha256
    })),
    callSetEvidence.value.calls
  );
  assert.deepEqual(
    calls.map((call) => call.outboundRequestSha256),
    receipt.outboundRequestSha256s
  );

  const addresses = await resolveAddresses({
    deadlineAt,
    timeoutMs: 60_000
  });
  const pinnedAddressSetSha256 = sha256(
    Buffer.from(JSON.stringify(addresses), "utf8")
  );
  const claim = {
    schemaVersion: "task-eval-005-track-b-schema-diagnostic-claim-v1",
    status: "CLAIMED_ONCE",
    phase,
    claimedAt: now().toISOString(),
    actualInputSha256: sha256(inputEvidence.bytes),
    dispatchSha256: sha256(dispatchEvidence.bytes),
    providerCallSetSha256: sha256(callSetEvidence.bytes),
    derivedReceiptSha256: sha256(receiptEvidence.bytes),
    model: TRACK_B_SCHEMA_DIAGNOSTIC_MODEL,
    callCount: calls.length,
    pinnedAddressSetSha256,
    automaticRetryAllowed: false,
    formalAdmissionAffected: false
  };
  const claimBytes = jsonBytes(claim);
  await writeFile(at("execution-claim.json"), claimBytes, { flag: "wx" });

  const results = [];
  let terminalCode = null;
  for (const call of calls) {
    let response;
    try {
      response = await transport({
        body: call.outboundRequestBytes,
        secret,
        addresses,
        timeoutMs: 60_000,
        deadlineAt,
        environment
      });
    } catch (error) {
      terminalCode = error?.code === "RESPONSE_TOO_LARGE"
        ? "RESPONSE_TOO_LARGE"
        : "NETWORK_OR_UNKNOWN_SIDE_EFFECT";
      results.push(callResult(call, terminalCode, now));
      break;
    }
    if (response.status < 200 || response.status >= 300) {
      terminalCode =
        response.status === 401 || response.status === 403
          ? "AUTHENTICATION_FAILED"
          : response.status === 429
            ? "RATE_LIMITED"
            : response.status >= 500
              ? "UPSTREAM_5XX"
              : "UPSTREAM_REJECTED";
      results.push(callResult(call, terminalCode, now));
      break;
    }
    let classification;
    try {
      if (response.contentTypeClass !== "APPLICATION_JSON") {
        classification = "ENVELOPE_INVALID";
      } else {
        const envelope = parseJsonBytesRejectDuplicateKeys(response.body);
        classification = classifyTrackBSchemaDiagnosticEnvelope(
          call.input,
          envelope
        );
      }
    } catch {
      classification = "ENVELOPE_INVALID";
    }
    results.push(callResult(call, classification, now));
  }

  const categoryCounts = Object.fromEntries(
    TRACK_B_SCHEMA_DIAGNOSTIC_CLASSES.map((key) => [key, 0])
  );
  for (const item of results) {
    if (Object.hasOwn(categoryCounts, item.classification)) {
      categoryCounts[item.classification] += 1;
    }
  }
  const acceptedCount = categoryCounts.ACCEPTED;
  const passComplete = results.length === calls.length && terminalCode === null;
  const strictAccepted =
    passComplete && acceptedCount === TRACK_B_SCHEMA_DIAGNOSTIC_CALLS_PER_PASS;
  const result = {
    schemaVersion: "task-eval-005-track-b-schema-diagnostic-result-v1",
    status: terminalCode
      ? "TERMINAL_BLOCKED"
      : strictAccepted
        ? "PASS_STRICT_SCHEMA_12_OF_12"
        : "PASS_COMPLETE_WITH_SCHEMA_REJECTIONS",
    phase,
    startedAt,
    completedAt: now().toISOString(),
    model: TRACK_B_SCHEMA_DIAGNOSTIC_MODEL,
    endpointOrigin: "https://api.deepseek.com:443",
    actualInputSha256: sha256(inputEvidence.bytes),
    promptSha256: sha256(promptBytes),
    dispatchSha256: sha256(dispatchEvidence.bytes),
    providerCallSetSha256: sha256(callSetEvidence.bytes),
    derivedReceiptSha256: sha256(receiptEvidence.bytes),
    executionClaimSha256: sha256(claimBytes),
    pinnedAddressSetSha256,
    plannedCallCount: calls.length,
    completedCallCount: results.length,
    acceptedCount,
    terminalCode,
    categoryCounts,
    calls: results,
    rawRequestPersisted: false,
    rawResponsePersisted: false,
    responseContentPersisted: false,
    reasoningContentPersisted: false,
    secretPersisted: false,
    automaticRetryPerformed: false,
    formalAdmissionAffected: false
  };
  const resultBytes = jsonBytes(result);
  await writeFile(at("diagnostic-result.json"), resultBytes, { flag: "wx" });
  return {
    status: result.status,
    phase,
    completedCallCount: result.completedCallCount,
    acceptedCount,
    terminalCode,
    categoryCounts,
    resultSha256: sha256(resultBytes)
  };
}

function callResult(call, classification, now) {
  return {
    callIndex: call.callIndex,
    callId: call.callId,
    packetId: call.packetId,
    modelInputSha256: call.modelInputSha256,
    outboundRequestSha256: call.outboundRequestSha256,
    classification,
    accepted: classification === "ACCEPTED",
    completedAt: now().toISOString()
  };
}

async function readJson(path) {
  const bytes = await readFile(path);
  return { bytes, value: parseJsonBytesRejectDuplicateKeys(bytes) };
}

if (
  process.argv[1] &&
  import.meta.url === pathToFileURL(resolve(process.argv[1])).href
) {
  const result = await runTrackBSchemaDiagnostic();
  process.stdout.write(`${JSON.stringify(result)}\n`);
}
