import type { ReviewResultSnapshot } from "./types";

export class TaskResultApiError extends Error {
  readonly status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = "TaskResultApiError";
    this.status = status;
  }
}

export async function fetchTaskResult(taskId: string): Promise<ReviewResultSnapshot> {
  const response = await fetch(`/api/v1/tasks/${encodeURIComponent(taskId)}/result`);

  if (response.ok) {
    return (await response.json()) as ReviewResultSnapshot;
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
