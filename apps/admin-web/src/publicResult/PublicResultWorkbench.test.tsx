import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { ConfigProvider } from "antd";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";

import { App } from "../App";

const fetchMock = vi.fn();
const createObjectUrlMock = vi.fn(() => "blob:cqcp-download");
const revokeObjectUrlMock = vi.fn();
let clickedDownloadLink: HTMLAnchorElement | undefined;

function recordClickedDownloadLink(link: HTMLAnchorElement) {
  clickedDownloadLink = link;
}

vi.stubGlobal("fetch", fetchMock);

function renderWorkbench() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } }
  });
  return render(
    <ConfigProvider>
      <QueryClientProvider client={queryClient}>
        <MemoryRouter
          initialEntries={["/review/results/task-safe?executionId=exec-safe"]}
        >
          <App initialManagementAccessToken="review-test-token" />
        </MemoryRouter>
      </QueryClientProvider>
    </ConfigProvider>
  );
}

const maliciousText = '<img src=x onerror="window.__cqcpOwned=true">';

const resultSnapshot = {
  taskId: "task-safe",
  executionId: "exec-safe",
  status: "SUCCESS",
  summary: {
    plannedPointCount: 1,
    passCount: 0,
    errorCount: 0,
    warningCount: 1,
    notConcludedCount: 0,
    skippedCount: 0
  },
  reviewCompleteness: {
    reviewCoverageStatus: "FULL_REVIEWED",
    executablePointCount: 1,
    concludedPointCount: 1,
    notConcludedPointCount: 0,
    concludedCoverageRate: 1,
    confidenceLevel: "HIGH"
  },
  pointResults: [
    {
      reviewPointCode: "POINT_SAFE",
      pointStatus: "WARNING",
      businessMessage: "请人工核对。",
      findingSeverity: "WARNING",
      notConcludedReason: null,
      skippedReason: null,
      sourceAnchors: [
        {
          blockId: "block-table",
          previewElementRef: "table:T1/row:0",
          sourceOrigin: "BODY",
          sourceExtractionMode: "TABLE",
          contextType: "BODY",
          evidenceSummary: "表格行证据",
          locationLevel: "EXACT_TEXT_RANGE"
        },
        {
          blockId: "block-table",
          previewElementRef: "table:T1/row:0/cell:1",
          sourceOrigin: "BODY",
          sourceExtractionMode: "TABLE",
          contextType: "BODY",
          evidenceSummary: "单元格证据",
          locationLevel: "EXACT_TEXT_RANGE"
        },
        {
          blockId: "block-malicious",
          previewElementRef: null,
          sourceOrigin: "BODY",
          sourceExtractionMode: "PARAGRAPH",
          contextType: "BODY",
          evidenceSummary: "块级证据",
          locationLevel: "BLOCK_LEVEL"
        },
        {
          blockId: "block-unavailable",
          previewElementRef: null,
          sourceOrigin: "BODY",
          sourceExtractionMode: "PARAGRAPH",
          contextType: "BODY",
          evidenceSummary: "不可定位证据",
          locationLevel: "UNAVAILABLE"
        }
      ]
    }
  ],
  findings: [],
  diagnostics: [],
  sourceAnchors: [],
  structuredFieldsSnapshot: { contractName: "安全渲染合同" },
  enabledReviewPointsSnapshot: [
    {
      reviewPointCode: "POINT_SAFE",
      displayCode: "P-SAFE",
      displayName: "安全定位测试",
      reviewPointFamily: "IDENTITY_FIELDS",
      contractType: "ENGINEERING_PROCUREMENT",
      defaultSeverity: "WARNING",
      displayOrder: 1
    }
  ],
  disabledReviewPointsSnapshot: []
};

const preview = {
  taskId: "task-safe",
  executionId: "exec-safe",
  contractName: "安全渲染合同",
  originalFileName: "safe.docx",
  parserVersion: "parser-docx-word-v20260724.1",
  sha256: "a".repeat(64),
  blocks: [
    {
      blockId: "block-malicious",
      previewElementRef: null,
      type: "PARAGRAPH",
      text: maliciousText,
      sectionPath: [],
      tableId: null,
      rowIndex: null,
      cells: []
    },
    {
      blockId: "block-table",
      previewElementRef: "table:T1/row:0",
      type: "TABLE_ROW",
      text: "字段值",
      sectionPath: [],
      tableId: "T1",
      rowIndex: 0,
      cells: [
        {
          previewElementRef: "table:T1/row:0/cell:0",
          cellIndex: 0,
          text: "字段"
        },
        {
          previewElementRef: "table:T1/row:0/cell:1",
          cellIndex: 1,
          text: "值"
        }
      ]
    }
  ]
};

describe("MVP-002 审核工作台", () => {
  beforeEach(() => {
    fetchMock.mockReset();
    fetchMock.mockImplementation((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes("/document-preview")) {
        return Promise.resolve(
          new Response(JSON.stringify(preview), {
            status: 200,
            headers: { "Content-Type": "application/json" }
          })
        );
      }
      if (url.endsWith("/document")) {
        return Promise.resolve(
          new Response(new Uint8Array([0x50, 0x4b, 0x03, 0x04]), {
            status: 200,
            headers: {
              "Content-Type":
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            }
          })
        );
      }
      return Promise.resolve(
        new Response(JSON.stringify(resultSnapshot), {
          status: 200,
          headers: { "Content-Type": "application/json" }
        })
      );
    });
    Element.prototype.scrollIntoView = vi.fn();
    Object.defineProperty(URL, "createObjectURL", {
      configurable: true,
      value: createObjectUrlMock
    });
    Object.defineProperty(URL, "revokeObjectURL", {
      configurable: true,
      value: revokeObjectUrlMock
    });
    createObjectUrlMock.mockClear();
    revokeObjectUrlMock.mockClear();
    clickedDownloadLink = undefined;
    vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(function (
      this: HTMLAnchorElement
    ) {
      expect(document.body.contains(this)).toBe(true);
      recordClickedDownloadLink(this);
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
    document.body.innerHTML = "";
  });

  it("只按 parser identity 定位、多 anchor 可切换且合同文本不会执行 HTML", async () => {
    renderWorkbench();

    expect(await screen.findByText("安全定位测试")).toBeInTheDocument();
    expect(await screen.findByText(maliciousText)).toBeInTheDocument();
    expect(document.querySelector('img[src="x"]')).toBeNull();
    expect((window as Window & { __cqcpOwned?: boolean }).__cqcpOwned).toBeUndefined();
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/review/tasks/task-safe/executions/exec-safe/document-preview",
      {
        headers: {
          Accept: "application/json",
          Authorization: "Bearer review-test-token"
        }
      }
    );
    expect(document.body.textContent).not.toContain("review-test-token");
    expect(window.location.href).not.toContain("review-test-token");

    fireEvent.click(screen.getByRole("button", { name: "定位到原文" }));
    await waitFor(() => {
      expect(document.querySelector('[data-block-id="block-table"]')).toHaveClass(
        "is-primary-evidence"
      );
      expect(document.querySelector('[data-block-id="block-malicious"]')).toHaveClass(
        "is-related-evidence"
      );
    });

    fireEvent.click(screen.getByRole("button", { name: "下一处" }));
    expect(await screen.findByText("证据 2 / 4")).toBeInTheDocument();
    expect(
      document.querySelector('[data-preview-element-ref="table:T1/row:0/cell:1"]')
    ).toHaveClass("is-primary-evidence");

    fireEvent.click(screen.getByRole("button", { name: "下一处" }));
    expect(await screen.findByText("证据 3 / 4")).toBeInTheDocument();
    expect(document.querySelector('[data-block-id="block-malicious"]')).toHaveClass(
      "is-primary-evidence"
    );
    expect(screen.getByText("块级定位")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "下一处" }));
    expect(await screen.findByText("证据 4 / 4")).toBeInTheDocument();
    expect(screen.getByText("当前证据没有可靠定位。")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "定位到原文" })).not.toBeInTheDocument();
  });

  it("通过受控 fetch 下载并在浏览器接纳后撤销临时 Blob URL", async () => {
    renderWorkbench();

    expect(await screen.findByText("安全定位测试")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "下载原始 DOCX" }));

    await waitFor(() => expect(clickedDownloadLink).toBeDefined());
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/review/tasks/task-safe/executions/exec-safe/document",
      {
        headers: {
          Accept:
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
          Authorization: "Bearer review-test-token"
        }
      }
    );
    expect(createObjectUrlMock).toHaveBeenCalledTimes(1);
    expect(clickedDownloadLink?.download).toBe("task-safe-exec-safe.docx");
    expect(clickedDownloadLink?.href).toBe("blob:cqcp-download");
    expect(document.body.contains(clickedDownloadLink!)).toBe(false);
    expect(
      await screen.findByText(
        "原始 DOCX 已从受控接口读取（4 bytes）并交给浏览器下载。"
      )
    ).toBeInTheDocument();
    await waitFor(() =>
      expect(revokeObjectUrlMock).toHaveBeenCalledWith("blob:cqcp-download")
    );
  });
});
