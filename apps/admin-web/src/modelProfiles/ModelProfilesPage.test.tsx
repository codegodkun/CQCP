import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { ConfigProvider } from "antd";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";

import { App } from "../App";

const fetchMock = vi.fn();

vi.stubGlobal("fetch", fetchMock);

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } }
  });
  return render(
    <ConfigProvider>
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={["/admin/model-profiles"]}>
          <App />
        </MemoryRouter>
      </QueryClientProvider>
    </ConfigProvider>
  );
}

const profiles = {
  items: [
    {
      profileCode: "DEEPSEEK_EVAL",
      configVersion: "model-config-deepseek-eval-v1",
      displayName: "DeepSeek Evaluation",
      providerType: "PUBLIC_OPENAI_COMPATIBLE",
      endpointAlias: "deepseek-official",
      modelName: "deepseek-v4-pro",
      usageScope: "EVALUATION",
      enabled: false,
      defaultForNewTask: false,
      secretConfigured: true,
      readiness: "READY_FOR_EVALUATION_CONFIG",
      timeoutSeconds: 30,
      retryCount: 0,
      latestConnectivityTest: {
        connectivityTestId: "MCT_1",
        status: "SUCCEEDED",
        httpStatusClass: "2XX",
        modelAvailable: true,
        durationMs: 18,
        testedAt: "2026-07-28T01:00:00Z"
      }
    }
  ]
};

describe("MVP-002 Model Profile 管理页", () => {
  afterEach(() => {
    cleanup();
  });

  beforeEach(() => {
    fetchMock.mockReset();
    fetchMock.mockImplementation((input: RequestInfo | URL, init?: RequestInit) => {
      if (init?.method === "POST") {
        return Promise.resolve(
          new Response(JSON.stringify(profiles.items[0].latestConnectivityTest), {
            status: 200,
            headers: { "Content-Type": "application/json" }
          })
        );
      }
      return Promise.resolve(
        new Response(JSON.stringify(profiles), {
          status: 200,
          headers: { "Content-Type": "application/json" }
        })
      );
    });
  });

  it("未认证时禁用配置写入且不发送管理请求", () => {
    renderPage();

    expect(
      screen.getByRole("button", { name: "保存为新 config version" })
    ).toBeDisabled();
    expect(
      screen.getByText("请先验证 Admin Access Token，再执行管理操作。")
    ).toBeInTheDocument();
    fireEvent.click(
      screen.getByRole("button", { name: "保存为新 config version" })
    );
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("无效 token 的 401 不得解锁表单或触发写请求", async () => {
    fetchMock.mockResolvedValue(
      new Response(
        JSON.stringify({
          title: "UNAUTHORIZED",
          detail: "管理访问凭据无效"
        }),
        {
          status: 401,
          headers: { "Content-Type": "application/problem+json" }
        }
      )
    );
    renderPage();

    fireEvent.change(screen.getByLabelText("Admin Access Token"), {
      target: { value: "invalid-admin-token" }
    });
    fireEvent.click(screen.getByRole("button", { name: "验证并进入" }));

    expect(await screen.findByText("Model Profile 查询失败。")).toBeInTheDocument();
    await waitFor(() => {
      expect(
        screen.getByRole("button", { name: "保存为新 config version" })
      ).toBeDisabled();
    });
    fireEvent.click(
      screen.getByRole("button", { name: "保存为新 config version" })
    );
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0]?.[1]).not.toMatchObject({ method: "POST" });
    expect(document.body.textContent).not.toContain("invalid-admin-token");
  });

  it("只显示 Secret 状态并以无请求体方式执行已保存配置的连通测试", async () => {
    renderPage();

    expect(fetchMock).not.toHaveBeenCalled();
    fireEvent.change(screen.getByLabelText("Admin Access Token"), {
      target: { value: "admin-test-token" }
    });
    fireEvent.click(screen.getByRole("button", { name: "验证并进入" }));

    expect(await screen.findByText("DeepSeek Evaluation")).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith("/api/admin/model-profiles", {
      headers: {
        Accept: "application/json",
        Authorization: "Bearer admin-test-token"
      }
    });
    expect(screen.getByText("Secret 已配置")).toBeInTheDocument();
    expect(screen.getByText("评测配置就绪（未发布）")).toBeInTheDocument();
    expect(screen.getByText("disabled")).toBeInTheDocument();
    expect(document.body.textContent).not.toContain("apiKey");
    expect(document.body.textContent).not.toContain("Authorization");
    expect(document.body.textContent).not.toContain("admin-test-token");
    expect(document.body.textContent).not.toContain("CQCP_DB_PASSWORD");

    fireEvent.click(screen.getByRole("button", { name: "连通测试" }));

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith(
        "/api/admin/model-profiles/DEEPSEEK_EVAL/connectivity-tests",
        {
          method: "POST",
          headers: {
            Accept: "application/json",
            Authorization: "Bearer admin-test-token"
          }
        }
      );
    });
  });
});
