export type TaskStatusGroup = "PROCESSING" | "COMPLETED" | "FAILED";

export interface TaskModelBinding {
  profileCode: string;
  displayName: string;
  providerType: string;
  modelName: string;
  endpointAlias: string;
  configVersion: string;
}

export interface TaskResultStatistics {
  plannedPointCount: number;
  passCount: number;
  errorCount: number;
  warningCount: number;
  notConcludedCount: number;
  skippedCount: number;
}

export interface TaskExecutionItem {
  taskId: string;
  executionId: string;
  contractName: string;
  status: string;
  currentStage: string;
  resultUrl: string;
  createdAt: string;
  updatedAt: string;
  finishedAt: string | null;
  modelProfile: TaskModelBinding;
  resultStatistics: TaskResultStatistics | null;
}

export interface TaskExecutionPage {
  items: TaskExecutionItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
