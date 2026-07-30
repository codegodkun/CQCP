export type ProfileReadiness =
  | "READY"
  | "SECRET_MISSING"
  | "NOT_TESTED"
  | "CONNECTIVITY_FAILED"
  | "READY_FOR_EVALUATION_CONFIG";

export type ConnectivityStatus =
  | "SUCCEEDED"
  | "SECRET_MISSING"
  | "AUTHENTICATION_FAILED"
  | "RATE_LIMITED"
  | "UPSTREAM_5XX"
  | "TIMEOUT"
  | "REDIRECT_REJECTED"
  | "MODEL_NOT_FOUND"
  | "MALFORMED_RESPONSE"
  | "ENDPOINT_NOT_ALLOWED"
  | "NETWORK_ERROR";

export interface ConnectivityView {
  connectivityTestId: string;
  status: ConnectivityStatus;
  httpStatusClass: string | null;
  modelAvailable: boolean;
  durationMs: number;
  testedAt: string;
}

export interface ModelProfileView {
  profileCode: string;
  configVersion: string;
  displayName: string;
  providerType: string;
  endpointAlias: string;
  modelName: string;
  usageScope: string;
  enabled: boolean;
  defaultForNewTask: boolean;
  secretConfigured: boolean;
  readiness: ProfileReadiness;
  timeoutSeconds: number;
  retryCount: number;
  latestConnectivityTest: ConnectivityView | null;
}

export interface ModelProfileListResponse {
  items: ModelProfileView[];
}

export interface ModelProfileWriteInput {
  profileCode: string;
  displayName: string;
  providerType: "PUBLIC_OPENAI_COMPATIBLE";
  endpointAlias: "deepseek-official";
  modelName: "deepseek-v4-pro" | "deepseek-v4-flash";
  usageScope: "EVALUATION";
  secretRef: string;
  timeoutSeconds: number;
  retryCount: number;
}
