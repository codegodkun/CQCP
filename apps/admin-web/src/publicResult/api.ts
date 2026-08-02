import type { DocumentPreview, ReviewResultSnapshot } from "./types";

export class TaskResultApiError extends Error {
  readonly status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = "TaskResultApiError";
    this.status = status;
  }
}

async function readJsonOrThrow<T>(response: Response): Promise<T> {
  if (response.ok) {
    return (await response.json()) as T;
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

export async function fetchTaskResult(
  taskId: string,
  executionId?: string | null
): Promise<ReviewResultSnapshot> {
  const query = executionId
    ? `?executionId=${encodeURIComponent(executionId)}`
    : "";
  const response = await fetch(
    `/api/v1/tasks/${encodeURIComponent(taskId)}/result${query}`
  );
  return readJsonOrThrow<ReviewResultSnapshot>(response);
}

export async function fetchDocumentPreview(
  taskId: string,
  executionId: string,
  accessToken: string
): Promise<DocumentPreview> {
  const response = await fetch(
    `/api/review/tasks/${encodeURIComponent(taskId)}/executions/${encodeURIComponent(executionId)}/document-preview`,
    {
      headers: {
        Accept: "application/json",
        Authorization: `Bearer ${accessToken}`
      }
    }
  );
  return readJsonOrThrow<DocumentPreview>(response);
}

export async function downloadDocument(
  taskId: string,
  executionId: string,
  accessToken: string
): Promise<Blob> {
  const response = await fetch(
    `/api/review/tasks/${encodeURIComponent(taskId)}/executions/${encodeURIComponent(executionId)}/document`,
    {
      headers: {
        Accept:
          "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        Authorization: `Bearer ${accessToken}`
      }
    }
  );
  if (!response.ok) {
    await readJsonOrThrow<never>(response);
  }
  return response.blob();
}
