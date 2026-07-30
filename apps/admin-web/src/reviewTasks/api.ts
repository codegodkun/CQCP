import type { TaskExecutionPage, TaskStatusGroup } from "./types";

export class TaskListApiError extends Error {
  readonly status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = "TaskListApiError";
    this.status = status;
  }
}

export async function fetchReviewTasks(input: {
  page: number;
  size: number;
  statusGroup?: TaskStatusGroup;
  q?: string;
}, accessToken: string): Promise<TaskExecutionPage> {
  const params = new URLSearchParams({
    page: String(input.page),
    size: String(input.size)
  });
  if (input.statusGroup) params.set("statusGroup", input.statusGroup);
  if (input.q?.trim()) params.set("q", input.q.trim());

  const response = await fetch(`/api/review/tasks?${params.toString()}`, {
    headers: {
      Accept: "application/json",
      Authorization: `Bearer ${accessToken}`
    }
  });
  if (response.ok) return (await response.json()) as TaskExecutionPage;

  let detail = "任务列表查询失败";
  try {
    const problem = (await response.json()) as { detail?: string };
    detail = problem.detail ?? detail;
  } catch {
    // The stable user-facing fallback intentionally ignores an upstream body.
  }
  throw new TaskListApiError(detail, response.status);
}
