import { StrictMode } from "react";
import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import { ConfigProvider } from "antd";
import {
  MemoryRouter,
  Route,
  Routes,
  useLocation,
  useNavigate
} from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";

import {
  fetchReviewExecutionStatus,
  parseReviewExecutionStatus,
  safeResultPath
} from "./api";
import {
  ReviewExecutionStatusPage
} from "./ReviewExecutionStatusPage";
import { EXECUTION_STAGES, PUBLIC_TASK_STATUSES } from "./types";

const fetchMock = vi.fn();
vi.stubGlobal("fetch", fetchMock);

const baseStatus = {
  taskId: "task-001",
  executionId: "exec-001",
  status: "QUEUED",
  currentStage: "QUEUED",
  terminal: false,
  snapshotAvailable: false,
  resultUrl: "/review/results/task-001?executionId=exec-001",
  reviewModel: {
    modelProfileCode: "review-default",
    providerType: "LOCAL",
    modelName: "local-model",
    endpointAlias: "local-primary",
    modelConfigVersion: "model-config-v1"
  },
  createdAt: "2026-07-27T08:00:00Z",
  updatedAt: "2026-07-27T08:00:01+08:00",
  superseded: false
};

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" }
  });
}

function LocationView() {
  const location = useLocation();
  return <div data-testid="location">{location.pathname + location.search}</div>;
}

function RouteChangeButton() {
  const navigate = useNavigate();
  return (
    <button
      type="button"
      onClick={() => navigate("/review/tasks/task-002/executions/exec-002")}
    >
      change-route
    </button>
  );
}

function renderStatus(
  initialEntry = "/review/tasks/task-001/executions/exec-001",
  strict = false
) {
  const content = (
    <ConfigProvider>
      <MemoryRouter initialEntries={[initialEntry]}>
        <Routes>
          <Route
            path="/review/tasks/:taskId/executions/:executionId"
            element={
              <>
                <ReviewExecutionStatusPage />
                <RouteChangeButton />
              </>
            }
          />
          <Route path="/review/results/:taskId" element={<LocationView />} />
        </Routes>
      </MemoryRouter>
    </ConfigProvider>
  );
  return render(strict ? <StrictMode>{content}</StrictMode> : content);
}

async function flushPromises() {
  await act(async () => {
    await Promise.resolve();
    await Promise.resolve();
  });
}

describe("review status parser and API", () => {
  afterEach(() => {
    cleanup();
    fetchMock.mockReset();
    vi.useRealTimers();
  });

  it("explicitly accepts all 5 public statuses and all 13 stages", () => {
    for (const stage of EXECUTION_STAGES) {
      const parsed = parseReviewExecutionStatus({ ...baseStatus, currentStage: stage });
      expect(parsed.currentStage).toBe(stage);
    }
    for (const status of PUBLIC_TASK_STATUSES) {
      const terminal = status === "SUCCESS" || status === "PARTIAL_SUCCESS" || status === "FAILED";
      const parsed = parseReviewExecutionStatus({ ...baseStatus, status, terminal });
      expect(parsed.status).toBe(status);
      expect(parsed.terminal).toBe(terminal);
    }
  });

  it("rebuilds only the response whitelist and defaults absent superseded to false", () => {
    const parsed = parseReviewExecutionStatus({
      ...baseStatus,
      superseded: undefined,
      secret: "STATUS_SECRET_SENTINEL",
      stackTrace: "STACK_SENTINEL",
      diagnostics: ["SYS-SECRET"],
      reviewModel: {
        ...baseStatus.reviewModel,
        endpoint: "https://secret.example",
        prompt: "PROMPT_SENTINEL"
      }
    });
    expect(Object.keys(parsed).sort()).toEqual([
      "createdAt",
      "currentStage",
      "executionId",
      "resultUrl",
      "reviewModel",
      "snapshotAvailable",
      "status",
      "superseded",
      "taskId",
      "terminal",
      "updatedAt"
    ]);
    expect(Object.keys(parsed.reviewModel).sort()).toEqual([
      "endpointAlias",
      "modelConfigVersion",
      "modelName",
      "modelProfileCode",
      "providerType"
    ]);
    expect(JSON.stringify(parsed)).not.toContain("SENTINEL");
    expect(parsed.superseded).toBe(false);
  });

  it("accepts an omitted optional currentStage without inventing a stage", () => {
    const withoutStage: Record<string, unknown> = { ...baseStatus };
    delete withoutStage.currentStage;
    const parsed = parseReviewExecutionStatus(withoutStage);
    expect(parsed.currentStage).toBeUndefined();
    expect(Object.keys(parsed)).not.toContain("currentStage");
  });

  it.each([
    [{ ...baseStatus, status: "UNKNOWN" }, "unknown status"],
    [{ ...baseStatus, currentStage: null }, "null stage"],
    [{ ...baseStatus, currentStage: "UNKNOWN" }, "unknown stage"],
    [{ ...baseStatus, terminal: true }, "inconsistent terminal"],
    [{ ...baseStatus, createdAt: "not-a-date" }, "bad date"],
    [{ ...baseStatus, createdAt: "2026-02-30T08:00:00Z" }, "impossible date"],
    [{ ...baseStatus, reviewModel: { ...baseStatus.reviewModel, providerType: "REMOTE" } }, "bad provider"],
    [{ ...baseStatus, taskId: " " }, "blank identity"]
  ] as Array<[unknown, string]>)("fails closed for %s", (body) => {
    expect(() => parseReviewExecutionStatus(body)).toThrow();
  });

  it("requests the exact encoded endpoint with only Accept and returns parsed data", async () => {
    fetchMock.mockResolvedValue(jsonResponse(baseStatus));
    const result = await fetchReviewExecutionStatus("task /甲", "exec /乙");
    expect(result.taskId).toBe("task-001");
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/review/tasks/task%20%2F%E7%94%B2/executions/exec%20%2F%E4%B9%99",
      expect.objectContaining({ headers: { Accept: "application/json" } })
    );
  });
});

describe("ReviewExecutionStatusPage polling and navigation", () => {
  afterEach(() => {
    cleanup();
    fetchMock.mockReset();
    vi.useRealTimers();
  });

  it("rejects invalid route identity without a request", async () => {
    renderStatus("/review/tasks/%20/executions/exec-001");
    expect(await screen.findByText("状态页地址无效")).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("StrictMode immediately issues one request and shows only whitelisted model fields", async () => {
    fetchMock.mockResolvedValue(jsonResponse(baseStatus));
    renderStatus(undefined, true);
    await flushPromises();
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(screen.getAllByText("排队中")).toHaveLength(2);
    expect(screen.getByText(/review-default.*LOCAL.*local-model.*model-config-v1/)).toBeInTheDocument();
    expect(screen.queryByText("local-primary")).not.toBeInTheDocument();
  });

  it("does not overlap a slow request and schedules next request 1000ms after completion", async () => {
    vi.useFakeTimers();
    let resolveFirst!: (response: Response) => void;
    fetchMock
      .mockReturnValueOnce(new Promise<Response>((resolve) => { resolveFirst = resolve; }))
      .mockResolvedValue(jsonResponse(baseStatus));
    renderStatus();
    await flushPromises();
    expect(fetchMock).toHaveBeenCalledTimes(1);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(5000);
    });
    expect(fetchMock).toHaveBeenCalledTimes(1);

    resolveFirst(jsonResponse(baseStatus));
    await flushPromises();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(999);
    });
    expect(fetchMock).toHaveBeenCalledTimes(1);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(1);
    });
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it.each(["SUCCESS", "PARTIAL_SUCCESS"] as const)(
    "%s safely navigates once and stops polling",
    async (status) => {
      vi.useFakeTimers();
      fetchMock.mockResolvedValue(
        jsonResponse({
          ...baseStatus,
          status,
          currentStage: status,
          terminal: true,
          snapshotAvailable: true
        })
      );
      renderStatus(undefined, true);
      await flushPromises();
      expect(screen.getByTestId("location")).toHaveTextContent(
        "/review/results/task-001?executionId=exec-001"
      );
      await act(async () => {
        await vi.advanceTimersByTimeAsync(5000);
      });
      expect(fetchMock).toHaveBeenCalledTimes(1);
    }
  );

  it("FAILED stays, shows fixed message and never discloses injected details", async () => {
    fetchMock.mockResolvedValue(
      jsonResponse({
        ...baseStatus,
        status: "FAILED",
        currentStage: "FAILED",
        terminal: true,
        failureReason: "FAILURE_SECRET_SENTINEL",
        diagnostics: "SYS-SECRET"
      })
    );
    renderStatus();
    expect(
      await screen.findByText("审核执行失败，请返回新建审核页面重新发起。")
    ).toBeInTheDocument();
    expect(screen.getByText("返回新建审核")).toHaveAttribute("href", "/review/new");
    expect(document.body.textContent).not.toContain("SENTINEL");
    expect(document.body.textContent).not.toContain("SYS-");
  });

  it("stops on known 404 and double retry starts only one in-flight request", async () => {
    let resolveRetry!: (response: Response) => void;
    fetchMock
      .mockResolvedValueOnce(
        jsonResponse(
          { code: "REVIEW_EXECUTION_NOT_FOUND", message: "SECRET_SENTINEL" },
          404
        )
      )
      .mockReturnValueOnce(new Promise<Response>((resolve) => { resolveRetry = resolve; }));
    renderStatus();
    expect(
      await screen.findByText("未找到指定审核任务或执行，请确认页面地址。")
    ).toBeInTheDocument();
    const retry = screen.getByText("重新查询");
    fireEvent.click(retry);
    fireEvent.click(retry);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    resolveRetry(jsonResponse(baseStatus));
    await flushPromises();
    expect(document.body.textContent).not.toContain("SECRET_SENTINEL");
  });

  it("resumes 1000ms polling after a manual retry returns a valid nonterminal status", async () => {
    vi.useFakeTimers();
    fetchMock
      .mockResolvedValueOnce(new Response("temporary", { status: 500 }))
      .mockResolvedValueOnce(jsonResponse(baseStatus))
      .mockResolvedValueOnce(
        jsonResponse({
          ...baseStatus,
          status: "FAILED",
          currentStage: "FAILED",
          terminal: true
        })
      );
    renderStatus();
    await flushPromises();
    expect(screen.getByText("状态查询暂不可用，请稍后重新查询。")).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(1);

    fireEvent.click(screen.getByText("重新查询"));
    await flushPromises();
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(screen.getByText("自动更新中")).toBeInTheDocument();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(999);
    });
    expect(fetchMock).toHaveBeenCalledTimes(2);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(1);
    });
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  it.each([
    [
      () => Promise.resolve(new Response("SERVER_SECRET_SENTINEL", { status: 500 })),
      "状态查询暂不可用，请稍后重新查询。"
    ],
    [
      () => Promise.reject(new Error("NETWORK_SECRET_SENTINEL")),
      "无法连接审核服务，请检查网络后重新查询。"
    ],
    [
      () =>
        Promise.resolve(
          new Response("<html>HTML_SECRET_SENTINEL</html>", {
            status: 200,
            headers: { "Content-Type": "text/html" }
          })
        ),
      "状态数据暂不可用，请重新查询。"
    ]
  ])("maps HTTP, network and malformed bodies to safe fixed messages", async (reply, message) => {
    fetchMock.mockImplementationOnce(reply);
    renderStatus();
    expect(await screen.findByText(message)).toBeInTheDocument();
    expect(document.body.textContent).not.toContain("SENTINEL");
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("fails closed when the response identity differs from the route", async () => {
    fetchMock.mockResolvedValue(
      jsonResponse({ ...baseStatus, executionId: "exec-other" })
    );
    renderStatus();
    expect(
      await screen.findByText("状态数据暂不可用，请重新查询。")
    ).toBeInTheDocument();
    expect(screen.queryByText("exec-other")).not.toBeInTheDocument();
  });

  it("stops a successful terminal response when the snapshot is unavailable", async () => {
    fetchMock.mockResolvedValue(
      jsonResponse({
        ...baseStatus,
        status: "SUCCESS",
        currentStage: "SUCCESS",
        terminal: true,
        snapshotAvailable: false
      })
    );
    renderStatus();
    expect(
      await screen.findByText("状态数据暂不可用，请重新查询。")
    ).toBeInTheDocument();
    expect(screen.queryByTestId("location")).not.toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("aborts an in-flight request on unmount without rendering a late failure", async () => {
    let resolvePending!: (response: Response) => void;
    fetchMock.mockReturnValue(
      new Promise<Response>((resolve) => { resolvePending = resolve; })
    );
    const rendered = renderStatus();
    await flushPromises();
    const signal = fetchMock.mock.calls[0][1].signal as AbortSignal;
    rendered.unmount();
    expect(signal.aborted).toBe(true);
    resolvePending(jsonResponse(baseStatus));
    await flushPromises();
    expect(document.body.textContent).not.toContain("无法连接");
  });

  it("shows the business superseded reason without endpoint details", async () => {
    fetchMock.mockResolvedValue(
      jsonResponse({
        ...baseStatus,
        superseded: true,
        supersededReason: "MODEL_UPGRADE"
      })
    );
    renderStatus();
    expect(await screen.findByText("当前执行已被新的审核执行替代。")).toBeInTheDocument();
    expect(screen.getByText("模型已升级并重跑")).toBeInTheDocument();
    expect(screen.queryByText("local-primary")).not.toBeInTheDocument();
  });

  it("ignores a stale response after route identity changes", async () => {
    let resolveOld!: (response: Response) => void;
    fetchMock
      .mockReturnValueOnce(new Promise<Response>((resolve) => { resolveOld = resolve; }))
      .mockResolvedValueOnce(
        jsonResponse({
          ...baseStatus,
          taskId: "task-002",
          executionId: "exec-002"
        })
      );
    renderStatus();
    await flushPromises();
    fireEvent.click(screen.getByText("change-route"));
    await flushPromises();
    expect(await screen.findByText("task-002")).toBeInTheDocument();
    resolveOld(
      jsonResponse({
        ...baseStatus,
        status: "FAILED",
        currentStage: "FAILED",
        terminal: true
      })
    );
    await flushPromises();
    expect(screen.queryByText(/审核执行失败/)).not.toBeInTheDocument();
    expect(screen.getByText("task-002")).toBeInTheDocument();
  });

  it("rejects unsafe result URLs and stays on the status page", async () => {
    fetchMock.mockResolvedValue(
      jsonResponse({
        ...baseStatus,
        status: "SUCCESS",
        currentStage: "SUCCESS",
        terminal: true,
        snapshotAvailable: true,
        resultUrl: "//evil.example/review/results/task-001?executionId=exec-001"
      })
    );
    renderStatus();
    expect(
      await screen.findByText("结果地址校验失败，请稍后重新查询。")
    ).toBeInTheDocument();
    expect(screen.queryByTestId("location")).not.toBeInTheDocument();
  });
});

describe("safeResultPath", () => {
  it("accepts only exact same-origin identity with one executionId", () => {
    expect(
      safeResultPath(
        "/review/results/task-001?executionId=exec-001",
        "task-001",
        "exec-001"
      )
    ).toBe("/review/results/task-001?executionId=exec-001");
    for (const unsafe of [
      "//evil.example/review/results/task-001?executionId=exec-001",
      "https://evil.example/review/results/task-001?executionId=exec-001",
      "/review/results/task-002?executionId=exec-001",
      "/review/results/task-001?executionId=exec-002",
      "/review/results/task-001?executionId=exec-001&extra=1",
      "/review/results/task-001?executionId=exec-001#secret",
      "/review/results/task-001?executionId=exec-001&executionId=exec-001",
      "/review\\results\\task-001?executionId=exec-001"
    ]) {
      expect(safeResultPath(unsafe, "task-001", "exec-001")).toBeNull();
    }
  });
});
