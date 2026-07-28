import {
  EXECUTION_STAGES,
  PROVIDER_TYPES,
  PUBLIC_TASK_STATUSES,
  StatusQueryError,
  SUPERSEDED_REASONS
} from "./types";
import type {
  ExecutionStage,
  ProviderType,
  PublicTaskStatus,
  ReviewExecutionStatus,
  SupersededReason
} from "./types";

const RFC3339 =
  /^\d{4}-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12]\d|3[01])T(?:[01]\d|2[0-3]):[0-5]\d:[0-5]\d(?:\.\d+)?(?:Z|[+-](?:[01]\d|2[0-3]):[0-5]\d)$/;

export interface RouteIdentity {
  taskId: string;
  executionId: string;
}

function decodeCanonicalSegment(raw: string): string | null {
  try {
    const decoded = decodeURIComponent(raw);
    if (decoded.trim().length === 0 || encodeURIComponent(decoded) !== raw) return null;
    return decoded;
  } catch {
    return null;
  }
}

export function parseStatusRoute(pathname: string): RouteIdentity | null {
  const match = /^\/review\/tasks\/([^/]+)\/executions\/([^/]+)$/.exec(pathname);
  if (!match) return null;
  const taskId = decodeCanonicalSegment(match[1]);
  const executionId = decodeCanonicalSegment(match[2]);
  return taskId && executionId ? { taskId, executionId } : null;
}

export function safeResultPath(
  resultUrl: string,
  taskId: string,
  executionId: string
): string | null {
  if (resultUrl.startsWith("//") || resultUrl.includes("\\")) return null;
  try {
    const parsed = new URL(resultUrl, window.location.origin);
    const expectedPath = `/review/results/${encodeURIComponent(taskId)}`;
    if (
      parsed.origin !== window.location.origin ||
      parsed.username !== "" ||
      parsed.password !== "" ||
      parsed.hash !== "" ||
      parsed.pathname !== expectedPath ||
      parsed.searchParams.size !== 1 ||
      parsed.searchParams.getAll("executionId").length !== 1 ||
      parsed.searchParams.get("executionId") !== executionId
    ) {
      return null;
    }
    return `${parsed.pathname}?executionId=${encodeURIComponent(executionId)}`;
  } catch {
    return null;
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function requiredString(record: Record<string, unknown>, key: string): string {
  const value = record[key];
  if (typeof value !== "string" || value.trim().length === 0) {
    throw new StatusQueryError("INVALID_DATA");
  }
  return value;
}

function requiredBoolean(record: Record<string, unknown>, key: string): boolean {
  const value = record[key];
  if (typeof value !== "boolean") {
    throw new StatusQueryError("INVALID_DATA");
  }
  return value;
}

function requiredEnum<T extends string>(
  record: Record<string, unknown>,
  key: string,
  values: readonly T[]
): T {
  const value = requiredString(record, key);
  if (!values.includes(value as T)) {
    throw new StatusQueryError("INVALID_DATA");
  }
  return value as T;
}

function optionalEnum<T extends string>(
  record: Record<string, unknown>,
  key: string,
  values: readonly T[]
): T | undefined {
  if (!(key in record)) return undefined;
  return requiredEnum(record, key, values);
}

function requiredDateTime(record: Record<string, unknown>, key: string): string {
  const value = requiredString(record, key);
  const calendar = /^(\d{4})-(\d{2})-(\d{2})T/.exec(value);
  if (!RFC3339.test(value) || !calendar || !Number.isFinite(Date.parse(value))) {
    throw new StatusQueryError("INVALID_DATA");
  }
  const year = Number(calendar[1]);
  const month = Number(calendar[2]);
  const day = Number(calendar[3]);
  const normalized = new Date(Date.UTC(year, month - 1, day));
  if (
    normalized.getUTCFullYear() !== year ||
    normalized.getUTCMonth() !== month - 1 ||
    normalized.getUTCDate() !== day
  ) {
    throw new StatusQueryError("INVALID_DATA");
  }
  return value;
}

function parseReviewModel(value: unknown) {
  if (!isRecord(value)) {
    throw new StatusQueryError("INVALID_DATA");
  }
  return {
    modelProfileCode: requiredString(value, "modelProfileCode"),
    providerType: requiredEnum<ProviderType>(value, "providerType", PROVIDER_TYPES),
    modelName: requiredString(value, "modelName"),
    endpointAlias: requiredString(value, "endpointAlias"),
    modelConfigVersion: requiredString(value, "modelConfigVersion")
  };
}

export function parseReviewExecutionStatus(value: unknown): ReviewExecutionStatus {
  if (!isRecord(value)) {
    throw new StatusQueryError("INVALID_DATA");
  }

  const status = requiredEnum<PublicTaskStatus>(
    value,
    "status",
    PUBLIC_TASK_STATUSES
  );
  const terminal = requiredBoolean(value, "terminal");
  const shouldBeTerminal =
    status === "SUCCESS" || status === "PARTIAL_SUCCESS" || status === "FAILED";
  if (terminal !== shouldBeTerminal) {
    throw new StatusQueryError("INVALID_DATA");
  }

  const supersededValue = value.superseded;
  if (supersededValue !== undefined && typeof supersededValue !== "boolean") {
    throw new StatusQueryError("INVALID_DATA");
  }

  const currentStage = optionalEnum<ExecutionStage>(
    value,
    "currentStage",
    EXECUTION_STAGES
  );
  const supersededReason = optionalEnum<SupersededReason>(
    value,
    "supersededReason",
    SUPERSEDED_REASONS
  );

  return {
    taskId: requiredString(value, "taskId"),
    executionId: requiredString(value, "executionId"),
    status,
    ...(currentStage === undefined ? {} : { currentStage }),
    terminal,
    snapshotAvailable: requiredBoolean(value, "snapshotAvailable"),
    resultUrl: requiredString(value, "resultUrl"),
    reviewModel: parseReviewModel(value.reviewModel),
    createdAt: requiredDateTime(value, "createdAt"),
    updatedAt: requiredDateTime(value, "updatedAt"),
    superseded: supersededValue ?? false,
    ...(supersededReason === undefined ? {} : { supersededReason })
  };
}

async function isKnownNotFound(response: Response): Promise<boolean> {
  if (response.status !== 404) return false;
  try {
    const body: unknown = await response.json();
    return isRecord(body) && body.code === "REVIEW_EXECUTION_NOT_FOUND";
  } catch {
    return false;
  }
}

export async function fetchReviewExecutionStatus(
  taskId: string,
  executionId: string,
  signal?: AbortSignal
): Promise<ReviewExecutionStatus> {
  let response: Response;
  try {
    response = await fetch(
      `/api/review/tasks/${encodeURIComponent(taskId)}/executions/${encodeURIComponent(executionId)}`,
      {
        headers: { Accept: "application/json" },
        signal
      }
    );
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") throw error;
    throw new StatusQueryError("NETWORK");
  }

  if (!response.ok) {
    if (await isKnownNotFound(response)) {
      throw new StatusQueryError("NOT_FOUND");
    }
    throw new StatusQueryError("HTTP");
  }

  let body: unknown;
  try {
    body = await response.json();
  } catch {
    throw new StatusQueryError("INVALID_DATA");
  }
  return parseReviewExecutionStatus(body);
}
