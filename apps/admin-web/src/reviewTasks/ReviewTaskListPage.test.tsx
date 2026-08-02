import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor
} from "@testing-library/react";
import { ConfigProvider } from "antd";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";

import { App } from "../App";

const fetchMock = vi.fn();

vi.stubGlobal("fetch", fetchMock);

function renderPage(initialToken = "review-test-token") {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } }
  });
  return render(
    <ConfigProvider>
      <QueryClientProvider client={queryClient}>
        <MemoryRouter
          initialEntries={["/review/tasks?page=1&statusGroup=COMPLETED&q=%E5%90%88%E5%90%8C"]}
        >
          <App initialManagementAccessToken={initialToken} />
        </MemoryRouter>
      </QueryClientProvider>
    </ConfigProvider>
  );
}

describe("MVP-002 execution 任务清单", () => {
  afterEach(() => {
    cleanup();
  });

  beforeEach(() => {
    fetchMock.mockReset();
    window.localStorage.clear();
    fetchMock.mockResolvedValue(
      new Response(
        JSON.stringify({
          items: [
            {
              taskId: "TASK_1",
              executionId: "EXEC_2",
              contractName: "合同 A",
              status: "PARTIAL_SUCCESS",
              currentStage: "PARTIAL_SUCCESS",
              resultUrl: "/review/results/TASK_1?executionId=EXEC_2",
              createdAt: "2026-07-28T02:00:00Z",
              updatedAt: "2026-07-28T02:01:00Z",
              finishedAt: "2026-07-28T02:01:00Z",
              modelProfile: {
                profileCode: "MVP_DEMO_MOCK",
                displayName: "MVP Demo Mock",
                providerType: "MOCK",
                modelName: "cqcp-demo-mock",
                endpointAlias: "mock-local",
                configVersion: "model-config-mvp-demo-mock-v20260724.1"
              },
              resultStatistics: {
                plannedPointCount: 9,
                passCount: 3,
                errorCount: 1,
                warningCount: 2,
                notConcludedCount: 2,
                skippedCount: 1
              }
            }
          ],
          page: 1,
          size: 20,
          totalElements: 21,
          totalPages: 2
        }),
        { status: 200, headers: { "Content-Type": "application/json" } }
      )
    );
  });

  it("按 execution 展示真实模型与同一 execution 统计并生成精确结果链接", async () => {
    renderPage();

    expect(await screen.findByText("合同 A")).toBeInTheDocument();
    expect(screen.getByText("MVP Demo Mock")).toBeInTheDocument();
    expect(screen.getByText("MOCK")).toBeInTheDocument();
    expect(screen.getByText("PASS 3")).toBeInTheDocument();
    expect(screen.getByText("NOT_CONCLUDED 2")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "查看 execution" })).toHaveAttribute(
      "href",
      "/review/results/TASK_1?executionId=EXEC_2"
    );
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/review/tasks?page=1&size=20&statusGroup=COMPLETED&q=%E5%90%88%E5%90%8C",
      {
        headers: {
          Accept: "application/json",
          Authorization: "Bearer review-test-token"
        }
      }
    );
  });

  it("匿名状态不请求清单，验证后只通过 Authorization header 查询", async () => {
    renderPage("");

    expect(fetchMock).not.toHaveBeenCalled();
    fireEvent.change(screen.getByLabelText("Management Access Token"), {
      target: { value: "readonly-review-token" }
    });
    fireEvent.click(screen.getByRole("button", { name: "验证并进入" }));

    expect(await screen.findByText("合同 A")).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/review/tasks?page=1&size=20&statusGroup=COMPLETED&q=%E5%90%88%E5%90%8C",
      {
        headers: {
          Accept: "application/json",
          Authorization: "Bearer readonly-review-token"
        }
      }
    );
    expect(window.location.href).not.toContain("readonly-review-token");
    expect(window.localStorage.length).toBe(0);

    fireEvent.click(screen.getByRole("button", { name: "退出受控访问" }));
    await waitFor(() => {
      expect(screen.queryByText("合同 A")).not.toBeInTheDocument();
    });
  });
});
