import type {
  ConnectivityView,
  ModelProfileListResponse,
  ModelProfileView,
  ModelProfileWriteInput
} from "./types";

export class ModelProfileApiError extends Error {
  readonly status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = "ModelProfileApiError";
    this.status = status;
  }
}

async function read<T>(response: Response): Promise<T> {
  if (response.ok) return (await response.json()) as T;
  let detail = "Model Profile 请求失败";
  try {
    const problem = (await response.json()) as { detail?: string };
    detail = problem.detail ?? detail;
  } catch {
    // Do not expose an arbitrary upstream response.
  }
  throw new ModelProfileApiError(detail, response.status);
}

function adminHeaders(adminToken: string, json = false): Record<string, string> {
  return {
    Authorization: `Bearer ${adminToken}`,
    Accept: "application/json",
    ...(json ? { "Content-Type": "application/json" } : {})
  };
}

export async function fetchModelProfiles(
  adminToken: string
): Promise<ModelProfileListResponse> {
  return read<ModelProfileListResponse>(
    await fetch("/api/admin/model-profiles", {
      headers: adminHeaders(adminToken)
    })
  );
}

export async function createModelProfile(
  input: ModelProfileWriteInput,
  adminToken: string
): Promise<ModelProfileView> {
  return read<ModelProfileView>(
    await fetch("/api/admin/model-profiles", {
      method: "POST",
      headers: adminHeaders(adminToken, true),
      body: JSON.stringify(input)
    })
  );
}

export async function createModelProfileVersion(
  profileCode: string,
  input: ModelProfileWriteInput,
  adminToken: string
): Promise<ModelProfileView> {
  const versionInput = Object.fromEntries(
    Object.entries(input).filter(([key]) => key !== "profileCode")
  );
  return read<ModelProfileView>(
    await fetch(`/api/admin/model-profiles/${encodeURIComponent(profileCode)}`, {
      method: "PUT",
      headers: adminHeaders(adminToken, true),
      body: JSON.stringify(versionInput)
    })
  );
}

export async function testModelProfileConnectivity(
  profileCode: string,
  adminToken: string
): Promise<ConnectivityView> {
  return read<ConnectivityView>(
    await fetch(
      `/api/admin/model-profiles/${encodeURIComponent(profileCode)}/connectivity-tests`,
      {
        method: "POST",
        headers: adminHeaders(adminToken)
      }
    )
  );
}
