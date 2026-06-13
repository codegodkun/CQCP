export type SnapshotStatus = "SUCCESS" | "PARTIAL_SUCCESS";

export type PointStatus =
  | "PASS"
  | "WARNING"
  | "ERROR"
  | "NOT_CONCLUDED"
  | "SKIPPED";

export type ReviewCoverageStatus =
  | "FULL_REVIEWED"
  | "PARTIAL_REVIEWED"
  | "LOW_CONFIDENCE_REVIEW";

export type ConfidenceLevel = "HIGH" | "MEDIUM" | "LOW";

export type NotConcludedReason =
  | "PARSE_LOW_CONFIDENCE"
  | "EVIDENCE_NOT_FOUND"
  | "EVIDENCE_AMBIGUOUS"
  | "MODEL_UNAVAILABLE"
  | "MODEL_BUDGET_EXCEEDED"
  | "INTERNAL_RULE_ERROR";

export interface SourceAnchorSummary {
  blockId: string;
  sourceOrigin: string;
  sourceExtractionMode: string;
  contextType: string;
  evidenceSummary: string;
}

export interface ReviewSummary {
  plannedPointCount: number;
  passCount: number;
  errorCount: number;
  warningCount: number;
  notConcludedCount: number;
  skippedCount: number;
}

export interface ReviewCompleteness {
  reviewCoverageStatus: ReviewCoverageStatus;
  executablePointCount: number;
  concludedPointCount: number;
  notConcludedPointCount: number;
  concludedCoverageRate: number;
  confidenceLevel: ConfidenceLevel;
}

export interface PointReviewResult {
  reviewPointCode: string;
  pointStatus: PointStatus;
  businessMessage: string;
  findingSeverity: "WARNING" | "ERROR" | null;
  sourceAnchors: SourceAnchorSummary[];
  notConcludedReason: NotConcludedReason | null;
  skippedReason: string | null;
}

export interface ReviewPointSnapshot {
  reviewPointCode: string;
  displayCode: string;
  displayName: string;
  reviewPointFamily: string;
  contractType: string;
  defaultSeverity: "WARNING" | "ERROR";
  displayOrder: number;
}

export interface ReviewResultSnapshot {
  taskId: string;
  executionId: string;
  status: SnapshotStatus;
  summary: ReviewSummary;
  reviewCompleteness: ReviewCompleteness;
  pointResults: PointReviewResult[];
  findings: unknown[];
  diagnostics: unknown[];
  sourceAnchors: SourceAnchorSummary[];
  enabledReviewPointsSnapshot: ReviewPointSnapshot[];
  disabledReviewPointsSnapshot: ReviewPointSnapshot[];
}
