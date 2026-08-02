import { access, readFile } from "node:fs/promises";
import { resolve } from "node:path";

import {
  sha256File,
  validateOpinionPayload
} from "./opinion-contract.mjs";

const DISPATCH_KEYS = new Set([
  "schemaVersion",
  "status",
  "createdAt",
  "protocol",
  "forkTurns",
  "historicalContextIncluded",
  "promptPath",
  "promptSha256",
  "unblindProhibitedUntilOpinionSeal",
  "assignments"
]);
const ASSIGNMENT_KEYS = new Set([
  "sampleId",
  "evaluator",
  "agentTaskName",
  "blindInputPath",
  "blindInputSha256",
  "opinionOutputPath"
]);
const OPINION_DOCUMENT_KEYS = new Set([
  "schemaVersion",
  "status",
  "track",
  "sampleId",
  "evaluator",
  "agentTaskName",
  "agentId",
  "forkTurns",
  "historicalContextIncluded",
  "dispatchSha256",
  "promptSha256",
  "inputSha256",
  "startedAt",
  "completedAt",
  "opinions"
]);
const SEAL_KEYS = new Set([
  "schemaVersion",
  "status",
  "sealedAt",
  "dispatchPath",
  "dispatchSha256",
  "promptPath",
  "promptSha256",
  "unblindReportPresentAtSeal",
  "opinions"
]);
const SEALED_OPINION_KEYS = new Set([
  "sampleId",
  "evaluator",
  "agentTaskName",
  "agentId",
  "startedAt",
  "completedAt",
  "path",
  "sha256",
  "inputSha256"
]);

export async function validateDispatch(repoRoot) {
  const outputRoot = resolve(repoRoot, "outputs/task-eval-002");
  const path = resolve(outputRoot, "blind-dispatch.json");
  const dispatch = JSON.parse(await readFile(path, "utf8"));
  requireExactKeys(dispatch, DISPATCH_KEYS, "dispatch");
  if (
    dispatch.schemaVersion !== "task-eval-002-blind-dispatch-v1" ||
    dispatch.status !== "FROZEN_BEFORE_AGENT_DISPATCH" ||
    dispatch.protocol !== "TRACK_A_FULL_DOCUMENT_SEMANTIC_OPINION" ||
    dispatch.forkTurns !== "none" ||
    dispatch.historicalContextIncluded !== false ||
    dispatch.unblindProhibitedUntilOpinionSeal !== true ||
    !validIso(dispatch.createdAt) ||
    !Array.isArray(dispatch.assignments) ||
    dispatch.assignments.length !== 3
  ) {
    throw new Error("Blind dispatch metadata is invalid");
  }
  const serialized = JSON.stringify(dispatch);
  if (/(ground.?truth|human.?anchor|cqcp.?actual|task-034|unblind-report)/i.test(serialized)) {
    throw new Error("Blind dispatch contains an unblinded reference");
  }
  const promptSha256 = await sha256File(resolve(repoRoot, dispatch.promptPath));
  if (promptSha256 !== dispatch.promptSha256) {
    throw new Error("Blind prompt hash mismatch");
  }
  const sampleIds = new Set();
  for (const [index, assignment] of dispatch.assignments.entries()) {
    requireExactKeys(assignment, ASSIGNMENT_KEYS, `assignments[${index}]`);
    if (
      !nonEmpty(assignment.sampleId) ||
      !nonEmpty(assignment.evaluator) ||
      !nonEmpty(assignment.agentTaskName) ||
      !assignment.agentTaskName.startsWith("/root/mvp002_blind_r2_") ||
      sampleIds.has(assignment.sampleId)
    ) {
      throw new Error(`Invalid blind assignment identity at index ${index}`);
    }
    sampleIds.add(assignment.sampleId);
    const inputSha256 = await sha256File(
      resolve(repoRoot, assignment.blindInputPath)
    );
    if (inputSha256 !== assignment.blindInputSha256) {
      throw new Error(`Blind assignment input changed: ${assignment.sampleId}`);
    }
  }
  return {
    path,
    sha256: await sha256File(path),
    dispatch
  };
}

export async function validateOpinionDocument(
  repoRoot,
  dispatchRecord,
  assignment
) {
  const path = resolve(repoRoot, assignment.opinionOutputPath);
  const document = JSON.parse(await readFile(path, "utf8"));
  requireExactKeys(document, OPINION_DOCUMENT_KEYS, "opinionDocument");
  if (
    document.schemaVersion !== "task-eval-002-model-opinion-v2" ||
    document.status !== "ACCEPTED" ||
    document.track !== dispatchRecord.dispatch.protocol ||
    document.sampleId !== assignment.sampleId ||
    document.evaluator !== assignment.evaluator ||
    document.agentTaskName !== assignment.agentTaskName ||
    !nonEmpty(document.agentId) ||
    document.forkTurns !== "none" ||
    document.historicalContextIncluded !== false ||
    document.dispatchSha256 !== dispatchRecord.sha256 ||
    document.promptSha256 !== dispatchRecord.dispatch.promptSha256 ||
    document.inputSha256 !== assignment.blindInputSha256 ||
    !validIso(document.startedAt) ||
    !validIso(document.completedAt) ||
    Date.parse(document.startedAt) < Date.parse(dispatchRecord.dispatch.createdAt) ||
    Date.parse(document.completedAt) < Date.parse(document.startedAt)
  ) {
    throw new Error(`Opinion metadata mismatch: ${assignment.sampleId}`);
  }
  const blindInput = JSON.parse(
    await readFile(resolve(repoRoot, assignment.blindInputPath), "utf8")
  );
  validateOpinionPayload(blindInput, { opinions: document.opinions });
  return {
    path,
    sha256: await sha256File(path),
    document
  };
}

export async function validateSealedChain(
  repoRoot,
  { requireUnblindAbsent = false } = {}
) {
  const outputRoot = resolve(repoRoot, "outputs/task-eval-002");
  if (requireUnblindAbsent) {
    try {
      await access(resolve(outputRoot, "unblind-report.json"));
      throw new Error("Unblind report already exists before opinion sealing");
    } catch (error) {
      if (error?.code !== "ENOENT") throw error;
    }
  }
  const dispatchRecord = await validateDispatch(repoRoot);
  const sealPath = resolve(outputRoot, "opinion-seal.json");
  const seal = JSON.parse(await readFile(sealPath, "utf8"));
  requireExactKeys(seal, SEAL_KEYS, "seal");
  if (
    seal.schemaVersion !== "task-eval-002-opinion-seal-v1" ||
    seal.status !== "SEALED_BEFORE_UNBLIND" ||
    seal.dispatchPath !== "outputs/task-eval-002/blind-dispatch.json" ||
    seal.dispatchSha256 !== dispatchRecord.sha256 ||
    seal.promptPath !== dispatchRecord.dispatch.promptPath ||
    seal.promptSha256 !== dispatchRecord.dispatch.promptSha256 ||
    seal.unblindReportPresentAtSeal !== false ||
    !validIso(seal.sealedAt) ||
    !Array.isArray(seal.opinions) ||
    seal.opinions.length !== dispatchRecord.dispatch.assignments.length
  ) {
    throw new Error("Opinion seal metadata is invalid");
  }
  const records = [];
  for (const [index, assignment] of dispatchRecord.dispatch.assignments.entries()) {
    const sealed = seal.opinions[index];
    requireExactKeys(sealed, SEALED_OPINION_KEYS, `seal.opinions[${index}]`);
    const record = await validateOpinionDocument(
      repoRoot,
      dispatchRecord,
      assignment
    );
    if (
      sealed.sampleId !== assignment.sampleId ||
      sealed.evaluator !== record.document.evaluator ||
      sealed.agentTaskName !== record.document.agentTaskName ||
      sealed.agentId !== record.document.agentId ||
      sealed.startedAt !== record.document.startedAt ||
      sealed.completedAt !== record.document.completedAt ||
      sealed.path !== assignment.opinionOutputPath ||
      sealed.sha256 !== record.sha256 ||
      sealed.inputSha256 !== assignment.blindInputSha256 ||
      Date.parse(seal.sealedAt) < Date.parse(record.document.completedAt)
    ) {
      throw new Error(`Sealed opinion mismatch: ${assignment.sampleId}`);
    }
    records.push({ assignment, ...record });
  }
  return {
    dispatchRecord,
    sealPath,
    sealSha256: await sha256File(sealPath),
    seal,
    records
  };
}

function requireExactKeys(value, keys, path) {
  if (value === null || typeof value !== "object" || Array.isArray(value)) {
    throw new Error(`${path} must be an object`);
  }
  for (const key of Object.keys(value)) {
    if (!keys.has(key)) throw new Error(`${path}.${key} is not allowed`);
  }
  for (const key of keys) {
    if (!(key in value)) throw new Error(`${path}.${key} is required`);
  }
}

function validIso(value) {
  return (
    typeof value === "string" &&
    !Number.isNaN(Date.parse(value)) &&
    new Date(value).toISOString() === value
  );
}

function nonEmpty(value) {
  return typeof value === "string" && value.trim().length > 0;
}
