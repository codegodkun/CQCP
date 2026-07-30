import { createHash } from "node:crypto";
import { readFile } from "node:fs/promises";
import {
  parseJsonRejectDuplicateKeys,
} from "./strict-json.mjs";

const OPINIONS = new Set([
  "CONSISTENT",
  "INCONSISTENT",
  "NOT_ENOUGH_EVIDENCE",
  "NOT_APPLICABLE"
]);
const OPINION_FIELDS = new Set([
  "reviewPointCode",
  "opinion",
  "confidence",
  "candidateValues",
  "evidenceQuotes",
  "opaqueLocations",
  "insufficiencyReason"
]);

export async function sha256File(path) {
  const bytes = await readFile(path);
  return createHash("sha256").update(bytes).digest("hex");
}

export function validateOpinionPayload(blindInput, payload) {
  requireObject(payload, "payload");
  requireExactKeys(payload, new Set(["opinions"]), "payload");
  if (!Array.isArray(payload.opinions)) {
    throw new Error("opinions must be an array");
  }
  const reviewPointCodes = blindInput.reviewPoints.map(
    (point) => point.reviewPointCode
  );
  if (payload.opinions.length !== reviewPointCodes.length) {
    throw new Error("opinions must cover every review point exactly once");
  }
  const locationTexts = new Map();
  for (const location of blindInput.documentProjection.locations) {
    const texts = [];
    if (typeof location.text === "string") texts.push(location.text);
    const cellTexts = (location.cells ?? [])
      .map((cell) => cell.text)
      .filter((text) => typeof text === "string");
    texts.push(...cellTexts);
    if (cellTexts.length > 0) texts.push(cellTexts.join(" | "));
    locationTexts.set(location.locationId, texts);
    for (const cell of location.cells ?? []) {
      locationTexts.set(
        cell.locationId,
        typeof cell.text === "string" ? [cell.text] : []
      );
    }
  }
  const knownLocations = new Set(locationTexts.keys());
  const seen = new Set();
  for (const [index, opinion] of payload.opinions.entries()) {
    const path = `opinions[${index}]`;
    requireObject(opinion, path);
    requireExactKeys(opinion, OPINION_FIELDS, path);
    if (!reviewPointCodes.includes(opinion.reviewPointCode)) {
      throw new Error(`${path}.reviewPointCode is unknown`);
    }
    if (seen.has(opinion.reviewPointCode)) {
      throw new Error(`${path}.reviewPointCode is duplicated`);
    }
    seen.add(opinion.reviewPointCode);
    if (!OPINIONS.has(opinion.opinion)) {
      throw new Error(`${path}.opinion is invalid`);
    }
    if (
      typeof opinion.confidence !== "number" ||
      !Number.isFinite(opinion.confidence) ||
      opinion.confidence < 0 ||
      opinion.confidence > 1
    ) {
      throw new Error(`${path}.confidence must be between 0 and 1`);
    }
    requireStringArray(opinion.candidateValues, `${path}.candidateValues`);
    requireStringArray(opinion.evidenceQuotes, `${path}.evidenceQuotes`);
    requireStringArray(opinion.opaqueLocations, `${path}.opaqueLocations`);
    for (const location of opinion.opaqueLocations) {
      if (!knownLocations.has(location)) {
        throw new Error(`${path}.opaqueLocations contains an unknown location`);
      }
    }
    if (
      opinion.opinion !== "NOT_ENOUGH_EVIDENCE" &&
      (opinion.evidenceQuotes.length === 0 ||
        opinion.opaqueLocations.length === 0)
    ) {
      throw new Error(`${path} requires evidence quotes and locations`);
    }
    const claimedTexts = opinion.opaqueLocations.flatMap(
      (location) => locationTexts.get(location) ?? []
    );
    for (const quote of opinion.evidenceQuotes) {
      if (!quote.trim() || !claimedTexts.some((text) => text.includes(quote))) {
        throw new Error(
          `${path}.evidenceQuotes must be verbatim text from a claimed location`
        );
      }
    }
    if (
      opinion.insufficiencyReason !== null &&
      typeof opinion.insufficiencyReason !== "string"
    ) {
      throw new Error(`${path}.insufficiencyReason must be string or null`);
    }
    if (
      opinion.opinion === "NOT_ENOUGH_EVIDENCE" &&
      !opinion.insufficiencyReason?.trim()
    ) {
      throw new Error(`${path}.insufficiencyReason is required for abstention`);
    }
  }
  return payload;
}

export function validateDeepSeekCompletionEnvelope(blindInput, envelope) {
  const choice = envelope?.choices?.[0];
  if (envelope?.choices?.length !== 1) {
    throw completionAdmissionError(
      "CHOICE_COUNT_INVALID",
      "response is incomplete"
    );
  }
  if (choice?.finish_reason !== "stop") {
    throw completionAdmissionError(
      "FINISH_REASON_NOT_STOP",
      "response is incomplete"
    );
  }
  if (
    typeof choice?.message?.content !== "string" ||
    !choice.message.content.trim()
  ) {
    throw completionAdmissionError("CONTENT_EMPTY", "response is incomplete");
  }
  let payload;
  try {
    payload = parseJsonRejectDuplicateKeys(choice.message.content);
  } catch {
    throw completionAdmissionError(
      "CONTENT_NOT_JSON",
      "response content is not valid JSON"
    );
  }
  try {
    return validateOpinionPayload(blindInput, payload);
  } catch (error) {
    throw completionAdmissionError(
      classifyOpinionSchemaViolation(error),
      "response opinion schema is invalid"
    );
  }
}

function completionAdmissionError(admissionCode, message) {
  const error = new Error(message);
  error.admissionCode = admissionCode;
  return error;
}

function classifyOpinionSchemaViolation(error) {
  const message = error instanceof Error ? error.message : "";
  if (/payload must be an object/.test(message)) return "PAYLOAD_NOT_OBJECT";
  if (/opinions must be an array/.test(message)) return "OPINIONS_NOT_ARRAY";
  if (/opinions\[\d+\] must be an object/.test(message)) {
    return "OPINION_ITEM_NOT_OBJECT";
  }
  if (/not allowed|required/.test(message)) return "FIELD_SET_INVALID";
  if (/cover every review point/.test(message)) {
    return "REVIEW_POINT_COVERAGE_INVALID";
  }
  if (/reviewPointCode is unknown|duplicated/.test(message)) {
    return "REVIEW_POINT_IDENTITY_INVALID";
  }
  if (/opinion is invalid/.test(message)) return "OPINION_ENUM_INVALID";
  if (/confidence must be/.test(message)) return "CONFIDENCE_INVALID";
  if (/must be a string array/.test(message)) return "ARRAY_TYPE_INVALID";
  if (/unknown location/.test(message)) return "LOCATION_UNKNOWN";
  if (/requires evidence quotes and locations/.test(message)) {
    return "EVIDENCE_REQUIRED";
  }
  if (/must be verbatim text/.test(message)) return "QUOTE_NOT_VERBATIM";
  if (/insufficiencyReason/.test(message)) {
    return "INSUFFICIENCY_REASON_INVALID";
  }
  return "OPINION_SCHEMA_INVALID";
}

function requireObject(value, path) {
  if (value === null || typeof value !== "object" || Array.isArray(value)) {
    throw new Error(`${path} must be an object`);
  }
}

function requireExactKeys(value, allowed, path) {
  for (const key of Object.keys(value)) {
    if (!allowed.has(key)) throw new Error(`${path}.${key} is not allowed`);
  }
  for (const key of allowed) {
    if (!(key in value)) throw new Error(`${path}.${key} is required`);
  }
}

function requireStringArray(value, path) {
  if (!Array.isArray(value) || value.some((item) => typeof item !== "string")) {
    throw new Error(`${path} must be a string array`);
  }
}
