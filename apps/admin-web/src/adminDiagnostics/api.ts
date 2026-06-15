import { TaskResultApiError } from "../publicResult/api";

import type { AdminDiagnosticSnapshot } from "./types";

export { TaskResultApiError };

export async function fetchAdminDiagnosticResult(
  taskId: string
): Promise<AdminDiagnosticSnapshot> {
  const response = await fetch(`/api/v1/tasks/${encodeURIComponent(taskId)}/result`);

  if (response.ok) {
    return (await response.json()) as AdminDiagnosticSnapshot;
  }

  let detail = "Request failed";
  try {
    const problem = (await response.json()) as { detail?: string };
    if (problem.detail) {
      detail = problem.detail;
    }
  } catch {
    detail = response.statusText || detail;
  }

  throw new TaskResultApiError(detail, response.status);
}
