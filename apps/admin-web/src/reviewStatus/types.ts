export const PUBLIC_TASK_STATUSES = [
  "QUEUED",
  "PROCESSING",
  "SUCCESS",
  "PARTIAL_SUCCESS",
  "FAILED"
] as const;

export type PublicTaskStatus = (typeof PUBLIC_TASK_STATUSES)[number];

export const EXECUTION_STAGES = [
  "CREATED",
  "QUEUED",
  "PARSING",
  "INDEXING",
  "PLANNING",
  "BUILDING_EVIDENCE",
  "REVIEWING_RULES",
  "REVIEWING_MODEL",
  "COMPOSING",
  "SUCCESS",
  "PARTIAL_SUCCESS",
  "FAILED",
  "CANCELLED"
] as const;

export type ExecutionStage = (typeof EXECUTION_STAGES)[number];

export const PROVIDER_TYPES = [
  "LOCAL",
  "PUBLIC_OPENAI_COMPATIBLE",
  "MOCK"
] as const;

export type ProviderType = (typeof PROVIDER_TYPES)[number];

export const SUPERSEDED_REASONS = [
  "TYPE_CORRECTION",
  "BUDGET_UPGRADE",
  "MANUAL_RERUN",
  "RULESET_RERUN",
  "MODEL_UPGRADE",
  "PARSER_UPGRADE",
  "ADMIN_RECOVERY"
] as const;

export type SupersededReason = (typeof SUPERSEDED_REASONS)[number];

export interface ReviewModelSummary {
  modelProfileCode: string;
  providerType: ProviderType;
  modelName: string;
  endpointAlias: string;
  modelConfigVersion: string;
}

export interface ReviewExecutionStatus {
  taskId: string;
  executionId: string;
  status: PublicTaskStatus;
  currentStage?: ExecutionStage;
  terminal: boolean;
  snapshotAvailable: boolean;
  resultUrl: string;
  reviewModel: ReviewModelSummary;
  createdAt: string;
  updatedAt: string;
  superseded: boolean;
  supersededReason?: SupersededReason;
}

export type StatusQueryErrorKind = "NOT_FOUND" | "HTTP" | "NETWORK" | "INVALID_DATA";

export class StatusQueryError extends Error {
  readonly kind: StatusQueryErrorKind;

  constructor(kind: StatusQueryErrorKind) {
    super(kind);
    this.name = "StatusQueryError";
    this.kind = kind;
  }
}
