import type {
  PointStatus,
  ReviewCompleteness,
  ReviewPointSnapshot,
  ReviewSummary,
  SnapshotStatus
} from "../publicResult/types";

export interface PointDiagnostic {
  reviewPointCode: string;
  pointStatus: PointStatus;
  diagnosticCode: string;
  businessReason: string;
  evidenceSummary: string;
  evidenceBlockIds: string[];
  containsSensitivePayload: boolean;
}

export interface AdminDiagnosticSnapshot {
  taskId: string;
  executionId: string;
  status: SnapshotStatus;
  summary: ReviewSummary;
  reviewCompleteness: ReviewCompleteness;
  pointResults: {
    reviewPointCode: string;
    pointStatus: PointStatus;
    businessMessage: string;
  }[];
  diagnostics: PointDiagnostic[];
  enabledReviewPointsSnapshot: ReviewPointSnapshot[];
}
