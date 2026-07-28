import type {
  CreateReviewTaskMetadata,
  CreateReviewTaskResponse,
  FormFields,
  ValidationErrorResponse,
} from "./types";

/** Build structured fields object from form state: convert string → number for numeric fields. */
function buildStructuredFields(fields: FormFields): CreateReviewTaskMetadata["structuredFields"] {
  const pm = fields.paymentMethod as "MONTHLY" | "MILESTONE";
  const base = {
    contractName: fields.contractName.trim(),
    partyAName: fields.partyAName.trim(),
    partyBName: fields.partyBName.trim(),
    projectName: fields.projectName.trim(),
    contractTotalAmount: Number(fields.contractTotalAmount),
    taxExcludedAmount: Number(fields.taxExcludedAmount),
    taxAmount: Number(fields.taxAmount),
    taxRate: Number(fields.taxRate),
    pricingMode: fields.pricingMode as "FIXED_TOTAL_PRICE" | "PROVISIONAL_TOTAL_PRICE",
    paymentMethod: pm,
    invoiceType: fields.invoiceType as "VAT_GENERAL" | "VAT_SPECIAL",
    currency: "CNY" as const,
    prepaymentRatio: Number(fields.prepaymentRatio),
  };

  if (pm === "MONTHLY") {
    return {
      ...base,
      progressPaymentRatio: Number(fields.progressPaymentRatio),
      completionPaymentRatio: Number(fields.completionPaymentRatio),
      settlementPaymentRatio: Number(fields.settlementPaymentRatio),
      warrantyRetentionRatio: Number(fields.warrantyRetentionRatio),
      // Ensure milestonePaymentTerms is never sent in MONTHLY payload
    };
  }

  // MILESTONE — ensure monthly ratios are never sent
  return {
    ...base,
    milestonePaymentTerms: fields.milestonePaymentTerms.trim(),
  };
}

/** Build the full request metadata. */
function buildMetadata(fields: FormFields): CreateReviewTaskMetadata {
  const meta: CreateReviewTaskMetadata = {
    contractType: "ENGINEERING",
    structuredFields: buildStructuredFields(fields),
  };
  const bd = fields.businessDocumentId.trim();
  if (bd) {
    meta.businessDocumentId = bd;
  }
  return meta;
}

/**
 * Submit a review task.
 * Accepts the FormFields and a File, constructs FormData, and POSTs.
 */
export async function submitReviewTask(
  file: File,
  fields: FormFields,
  signal?: AbortSignal,
): Promise<CreateReviewTaskResponse> {
  const metadata = buildMetadata(fields);
  const formData = new FormData();
  formData.append("file", file);
  formData.append("metadata", new Blob([JSON.stringify(metadata)], { type: "application/json" }));

  const response = await fetch("/api/review/tasks", {
    method: "POST",
    body: formData,
    signal,
  });

  // Only HTTP 202 with valid JSON is accepted as success
  if (response.status !== 202) {
    // Attempt to parse business error or validation error
    const text = await response.text().catch(() => "");
    throw new SubmitError(response.status, text, response.status === 400 || response.status === 409 || response.status === 429 || response.status === 503);
  }

  let json: unknown;
  try {
    json = await response.json();
  } catch {
    throw new SubmitError(response.status, "", false);
  }

  // Validate required fields and QUEUED status
  if (!isValidSuccessResponse(json)) {
    throw new SubmitError(response.status, "", false);
  }

  // AC18: return only frozen fields — never pass through raw json reference
  const safe = json as unknown as Record<string, unknown>;
  return {
    taskId: safe.taskId as string,
    executionId: safe.executionId as string,
    status: "QUEUED" as const,
    resultUrl: safe.resultUrl as string,
  };
}

function isValidSuccessResponse(json: unknown): json is CreateReviewTaskResponse {
  if (typeof json !== "object" || json === null) return false;
  const r = json as Record<string, unknown>;
  if (!isCanonicalNonEmptyString(r.taskId)) return false;
  if (!isCanonicalNonEmptyString(r.executionId)) return false;
  if (r.status !== "QUEUED") return false;
  if (typeof r.resultUrl !== "string" || r.resultUrl.trim().length === 0) {
    return false;
  }
  return true;
}

function isCanonicalNonEmptyString(value: unknown): value is string {
  return (
    typeof value === "string" &&
    value.length > 0 &&
    value === value.trim()
  );
}

/** Accepted field-error codes (OpenAPI stable enum). */
const VALID_FIELD_ERROR_CODES = new Set([
  "REQUIRED_FIELD_MISSING", "CONDITIONAL_FIELD_MISSING", "INVALID_ENUM_VALUE",
  "INVALID_DECIMAL_SCALE", "INVALID_PERCENT_RANGE", "UNSUPPORTED_CURRENCY",
  "UNSUPPORTED_FILE_TYPE", "INVALID_JSON", "EMPTY_FILE", "INVALID_DOCUMENT_CONTENT",
  "FILE_TOO_LARGE", "UNKNOWN_FIELD", "INVALID_STRING_LENGTH", "INVALID_FIELD_TYPE",
]);

/** Parse a 400 ValidationErrorResponse. Returns null if malformed. */
export function parseValidationError(text: string): ValidationErrorResponse | null {
  try {
    const json = JSON.parse(text);
    if (typeof json !== "object" || json === null) return null;
    if (json.code !== "VALIDATION_ERROR") return null;
    if (typeof json.message !== "string") return null;
    if (!Array.isArray(json.fieldErrors) || json.fieldErrors.length === 0) return null;
    // Every entry must have valid string field/code/message AND code must be in the known enum
    const allValid = json.fieldErrors.every(
      (e: unknown) =>
        typeof e === "object" && e !== null &&
        typeof (e as Record<string, unknown>).field === "string" &&
        typeof (e as Record<string, unknown>).code === "string" &&
        typeof (e as Record<string, unknown>).message === "string" &&
        VALID_FIELD_ERROR_CODES.has((e as Record<string, unknown>).code as string),
    );
    if (!allValid) return null;
    // AC18: map every field error to only {field, code, message} — strip any extra keys
    const safeFieldErrors = json.fieldErrors.map((e: Record<string, unknown>) => ({
      field: e.field as string,
      code: e.code as string,
      message: e.message as string,
    }));
    return {
      code: "VALIDATION_ERROR",
      message: json.message as string,
      fieldErrors: safeFieldErrors,
    };
  } catch {
    return null;
  }
}

/** Safe message for a BusinessErrorResponse — only reads code+message if both are strings. */
export function safeBusinessMessage(text: string): { message: string; reason: string | null } | null {
  try {
    const json = JSON.parse(text);
    if (typeof json !== "object" || json === null) return null;
    const r = json as Record<string, unknown>;
    if (typeof r.code !== "string" || typeof r.message !== "string") return null;
    const reason = typeof r.reason === "string" ? r.reason : null;
    return { message: r.message, reason };
  } catch {
    return null;
  }
}

export class SubmitError extends Error {
  readonly status: number;
  readonly responseText: string;
  readonly isKnownError: boolean;

  constructor(status: number, responseText: string, isKnownError: boolean) {
    super("Submit failed");
    this.name = "SubmitError";
    this.status = status;
    this.responseText = responseText;
    this.isKnownError = isKnownError;
  }
}

/** Determine user-facing message for a SubmitError. */
export function userFacingMessage(status: number, responseText: string): { heading: string; detail: string | null } {
  // Network error (fetch rejection)
  if (status === 0) return { heading: "无法连接审核服务，请检查网络后重试", detail: null };

  // Malformed 202 response (invalid JSON or missing/incorrect fields)
  if (status === 202) return { heading: "任务创建响应无效，请稍后重试", detail: null };

  // 400 with valid fieldErrors → handled separately in the component
  if (status === 400) {
    const parsed = parseValidationError(responseText);
    if (parsed) {
      return { heading: "提交数据校验失败", detail: parsed.message || null };
    }
  }

  // 409/429/503 with valid BusinessErrorResponse
  if (status === 409 || status === 429 || status === 503) {
    const msg = safeBusinessMessage(responseText);
    if (msg) {
      return { heading: msg.message, detail: msg.reason };
    }
  }

  // Default status-based messages
  switch (status) {
    case 409: return { heading: "当前执行配置不可用，请联系管理员", detail: null };
    case 429: return { heading: "当前任务较多，请稍后重试", detail: null };
    case 503: return { heading: "文档存储暂不可用，请稍后重试", detail: null };
    default: return { heading: "任务创建失败，请稍后重试", detail: null };
  }
}
