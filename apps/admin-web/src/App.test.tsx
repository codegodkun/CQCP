import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { ConfigProvider } from "antd";
import { afterEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";

import { App } from "./App";

const fetchMock = vi.fn();

vi.stubGlobal("fetch", fetchMock);

function renderApp(initialEntry = "/") {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false
      }
    }
  });

  return render(
    <ConfigProvider>
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={[initialEntry]}>
          <App />
        </MemoryRouter>
      </QueryClientProvider>
    </ConfigProvider>
  );
}

const adminSnapshot = {
  taskId: "task-024",
  executionId: "execution-024",
  status: "PARTIAL_SUCCESS",
  summary: {
    plannedPointCount: 4,
    passCount: 1,
    errorCount: 1,
    warningCount: 1,
    notConcludedCount: 1,
    skippedCount: 0
  },
  reviewCompleteness: {
    reviewCoverageStatus: "PARTIAL_REVIEWED",
    executablePointCount: 4,
    concludedPointCount: 3,
    notConcludedPointCount: 1,
    concludedCoverageRate: 0.75,
    confidenceLevel: "MEDIUM"
  },
  pointResults: [
    {
      reviewPointCode: "PARTY_A_NAME_CONSISTENCY",
      pointStatus: "PASS",
      businessMessage: "甲方名称一致。"
    },
    {
      reviewPointCode: "PARTY_B_NAME_CONSISTENCY",
      pointStatus: "ERROR",
      businessMessage: "乙方名称与合同证据不一致。"
    },
    {
      reviewPointCode: "PREPAYMENT_RATIO_CONSISTENCY",
      pointStatus: "NOT_CONCLUDED",
      businessMessage: "未找到可靠合同证据，无法形成正式结论。"
    },
    {
      reviewPointCode: "TAX_AMOUNT_FORMULA_CONSISTENCY",
      pointStatus: "WARNING",
      businessMessage: "税额弱校验存在偏差，请人工复核。"
    }
  ],
  diagnostics: [
    {
      reviewPointCode: "PREPAYMENT_RATIO_CONSISTENCY",
      pointStatus: "NOT_CONCLUDED",
      diagnosticCode: "SYS_MODEL_TIMEOUT",
      businessReason: "模型超时，当前点位未形成可靠结论。",
      evidenceSummary: "模型调用阶段未返回可用输出。",
      evidenceBlockIds: ["block-009", "block-010"],
      containsSensitivePayload: false
    }
  ],
  enabledReviewPointsSnapshot: [
    {
      reviewPointCode: "PARTY_A_NAME_CONSISTENCY",
      displayCode: "P001",
      displayName: "甲方名称一致性",
      reviewPointFamily: "PARTY_FIELDS",
      contractType: "ENGINEERING_PROCUREMENT",
      defaultSeverity: "ERROR",
      displayOrder: 1
    },
    {
      reviewPointCode: "PARTY_B_NAME_CONSISTENCY",
      displayCode: "P002",
      displayName: "乙方名称一致性",
      reviewPointFamily: "PARTY_FIELDS",
      contractType: "ENGINEERING_PROCUREMENT",
      defaultSeverity: "ERROR",
      displayOrder: 2
    },
    {
      reviewPointCode: "PREPAYMENT_RATIO_CONSISTENCY",
      displayCode: "P003",
      displayName: "预付款比例一致性",
      reviewPointFamily: "PAYMENT_FIELDS",
      contractType: "ENGINEERING_PROCUREMENT",
      defaultSeverity: "ERROR",
      displayOrder: 3
    },
    {
      reviewPointCode: "TAX_AMOUNT_FORMULA_CONSISTENCY",
      displayCode: "P004",
      displayName: "税额公式一致性",
      reviewPointFamily: "AMOUNT_FIELDS",
      contractType: "ENGINEERING_PROCUREMENT",
      defaultSeverity: "WARNING",
      displayOrder: 4
    }
  ]
};

describe("TASK-023 public result page", () => {
  afterEach(() => {
    fetchMock.mockReset();
    cleanup();
  });

  it("renders public snapshot and hides internal diagnostics fields", async () => {
    fetchMock.mockResolvedValue(
      new Response(
        JSON.stringify({
          taskId: "task-001",
          executionId: "execution-001",
          status: "PARTIAL_SUCCESS",
          summary: {
            plannedPointCount: 5,
            passCount: 1,
            errorCount: 1,
            warningCount: 1,
            notConcludedCount: 1,
            skippedCount: 1
          },
          reviewCompleteness: {
            reviewCoverageStatus: "PARTIAL_REVIEWED",
            executablePointCount: 4,
            concludedPointCount: 3,
            notConcludedPointCount: 1,
            concludedCoverageRate: 0.75,
            confidenceLevel: "MEDIUM"
          },
          pointResults: [
            {
              reviewPointCode: "PARTY_A_NAME_CONSISTENCY",
              pointStatus: "PASS",
              businessMessage: "甲方名称一致。",
              findingSeverity: null,
              sourceAnchors: [
                {
                  blockId: "block-001",
                  sourceOrigin: "NATIVE_WORD",
                  sourceExtractionMode: "STRUCTURED",
                  contextType: "NORMAL",
                  evidenceSummary: "甲方：测试建设有限公司"
                }
              ],
              notConcludedReason: null,
              skippedReason: null
            },
            {
              reviewPointCode: "PARTY_B_NAME_CONSISTENCY",
              pointStatus: "ERROR",
              businessMessage: "乙方名称与合同证据不一致。",
              findingSeverity: "ERROR",
              sourceAnchors: [
                {
                  blockId: "block-002",
                  sourceOrigin: "NATIVE_WORD",
                  sourceExtractionMode: "STRUCTURED",
                  contextType: "NORMAL",
                  evidenceSummary: "乙方：示例施工有限公司"
                }
              ],
              notConcludedReason: null,
              skippedReason: null
            },
            {
              reviewPointCode: "TAX_AMOUNT_FORMULA_CONSISTENCY",
              pointStatus: "WARNING",
              businessMessage: "税额弱校验存在偏差，请人工复核。",
              findingSeverity: "WARNING",
              sourceAnchors: [
                {
                  blockId: "block-003",
                  sourceOrigin: "NATIVE_WORD",
                  sourceExtractionMode: "DERIVED",
                  contextType: "NORMAL",
                  evidenceSummary: "税额：32.50，计算结果：32.40"
                }
              ],
              notConcludedReason: null,
              skippedReason: null
            },
            {
              reviewPointCode: "PREPAYMENT_RATIO_CONSISTENCY",
              pointStatus: "NOT_CONCLUDED",
              businessMessage: "未找到可靠合同证据，无法形成正式结论。",
              findingSeverity: null,
              sourceAnchors: [],
              notConcludedReason: "EVIDENCE_NOT_FOUND",
              skippedReason: null
            },
            {
              reviewPointCode: "PROGRESS_PAYMENT_RATIO_CONSISTENCY",
              pointStatus: "SKIPPED",
              businessMessage: "按节点付款场景下不适用进度款比例审核。",
              findingSeverity: null,
              sourceAnchors: [],
              notConcludedReason: null,
              skippedReason: "NOT_APPLICABLE_FOR_PAYMENT_METHOD"
            }
          ],
          findings: [
            {
              reviewPointCode: "PARTY_B_NAME_CONSISTENCY",
              severity: "ERROR",
              businessMessage: "乙方名称与合同证据不一致。",
              sourceAnchors: [
                {
                  blockId: "block-002",
                  sourceOrigin: "NATIVE_WORD",
                  sourceExtractionMode: "STRUCTURED",
                  contextType: "NORMAL",
                  evidenceSummary: "乙方：示例施工有限公司"
                }
              ]
            }
          ],
          diagnostics: [
            {
              pointCode: "PREPAYMENT_RATIO_CONSISTENCY",
              pointStatus: "NOT_CONCLUDED",
              diagnosticCode: "SYS_MODEL_TIMEOUT",
              message: "模型暂时不可用",
              sourceBlockIds: [],
              evidenceSummary: "无证据摘要",
              retryable: false
            }
          ],
          sourceAnchors: [
            {
              blockId: "block-001",
              sourceOrigin: "NATIVE_WORD",
              sourceExtractionMode: "STRUCTURED",
              contextType: "NORMAL",
              evidenceSummary: "甲方：测试建设有限公司"
            },
            {
              blockId: "block-002",
              sourceOrigin: "NATIVE_WORD",
              sourceExtractionMode: "STRUCTURED",
              contextType: "NORMAL",
              evidenceSummary: "乙方：示例施工有限公司"
            },
            {
              blockId: "block-003",
              sourceOrigin: "NATIVE_WORD",
              sourceExtractionMode: "DERIVED",
              contextType: "NORMAL",
              evidenceSummary: "税额：32.50，计算结果：32.40"
            }
          ],
          enabledReviewPointsSnapshot: [
            {
              reviewPointCode: "PARTY_A_NAME_CONSISTENCY",
              displayCode: "P001",
              displayName: "甲方名称一致性",
              reviewPointFamily: "PARTY_FIELDS",
              contractType: "ENGINEERING_PROCUREMENT",
              defaultSeverity: "ERROR",
              displayOrder: 1
            },
            {
              reviewPointCode: "PARTY_B_NAME_CONSISTENCY",
              displayCode: "P002",
              displayName: "乙方名称一致性",
              reviewPointFamily: "PARTY_FIELDS",
              contractType: "ENGINEERING_PROCUREMENT",
              defaultSeverity: "ERROR",
              displayOrder: 2
            },
            {
              reviewPointCode: "TAX_AMOUNT_FORMULA_CONSISTENCY",
              displayCode: "P003",
              displayName: "税额公式一致性",
              reviewPointFamily: "AMOUNT_FIELDS",
              contractType: "ENGINEERING_PROCUREMENT",
              defaultSeverity: "WARNING",
              displayOrder: 3
            },
            {
              reviewPointCode: "PREPAYMENT_RATIO_CONSISTENCY",
              displayCode: "P004",
              displayName: "预付款比例一致性",
              reviewPointFamily: "PAYMENT_FIELDS",
              contractType: "ENGINEERING_PROCUREMENT",
              defaultSeverity: "ERROR",
              displayOrder: 4
            },
            {
              reviewPointCode: "PROGRESS_PAYMENT_RATIO_CONSISTENCY",
              displayCode: "P005",
              displayName: "进度款比例一致性",
              reviewPointFamily: "PAYMENT_FIELDS",
              contractType: "ENGINEERING_PROCUREMENT",
              defaultSeverity: "ERROR",
              displayOrder: 5
            }
          ],
          disabledReviewPointsSnapshot: [],
          promptVersion: "prompt-v1"
        }),
        {
          status: 200,
          headers: {
            "Content-Type": "application/json"
          }
        }
      )
    );

    renderApp("/?taskId=task-001");

    expect(await screen.findByText("甲方名称一致性")).toBeInTheDocument();
    expect(screen.getByText("普通结果页最小展示")).toBeInTheDocument();
    expect(screen.getByText("任务 task-001")).toBeInTheDocument();
    expect(screen.getByText("P001")).toBeInTheDocument();
    expect(screen.getByText("乙方名称与合同证据不一致。")).toBeInTheDocument();
    expect(screen.getAllByText("甲方：测试建设有限公司")).toHaveLength(2);
    expect(screen.getByText("请人工核对相关条款或补充证据后再判断。")).toBeInTheDocument();
    expect(screen.getByText("当前结果仅提供 block 级定位摘要。")).toBeInTheDocument();
    expect(screen.getByText("PASS 1")).toBeInTheDocument();
    expect(screen.getByText("ERROR 1")).toBeInTheDocument();
    expect(screen.getByText("WARNING 1")).toBeInTheDocument();
    expect(screen.getByText("NOT_CONCLUDED 1")).toBeInTheDocument();
    expect(screen.getByText("SKIPPED 1")).toBeInTheDocument();
    expect(screen.queryByText("SYS_MODEL_TIMEOUT")).not.toBeInTheDocument();
    expect(screen.queryByText("prompt-v1")).not.toBeInTheDocument();
  });

  it("shows not found message for 404", async () => {
    fetchMock.mockResolvedValue(
      new Response(
        JSON.stringify({
          title: "TASK_RESULT_NOT_FOUND",
          detail: "Task result not found: missing-task"
        }),
        {
          status: 404,
          headers: {
            "Content-Type": "application/json"
          }
        }
      )
    );

    renderApp("/?taskId=missing-task");

    expect(await screen.findByText("未找到对应任务，请确认 taskId 是否正确。")).toBeInTheDocument();
  });

  it("shows not ready message for 409", async () => {
    fetchMock.mockResolvedValue(
      new Response(
        JSON.stringify({
          title: "TASK_RESULT_NOT_READY",
          detail: "Task result not ready: task-009"
        }),
        {
          status: 409,
          headers: {
            "Content-Type": "application/json"
          }
        }
      )
    );

    renderApp("/?taskId=task-009");

    expect(await screen.findByText("任务已创建，但结果尚未生成。")).toBeInTheDocument();
  });

  it("shows network error message", async () => {
    fetchMock.mockRejectedValue(new Error("network down"));

    renderApp("/?taskId=task-010");

    expect(await screen.findByText("结果查询失败，请稍后重试。")).toBeInTheDocument();
    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledTimes(1);
    });
  });

  it("renders admin diagnostics page with minimal diagnostics fields", async () => {
    fetchMock.mockResolvedValue(
      new Response(JSON.stringify(adminSnapshot), {
        status: 200,
        headers: {
          "Content-Type": "application/json"
        }
      })
    );

    renderApp("/admin/diagnostics?taskId=task-024");

    expect(await screen.findByText("管理台诊断详情最小展示")).toBeInTheDocument();
    expect(await screen.findByText("SYS_MODEL_TIMEOUT")).toBeInTheDocument();
    expect(
      screen.getByText((_, element) => element?.textContent === "任务 task-024")
    ).toBeInTheDocument();
    expect(screen.getByText("审核摘要")).toBeInTheDocument();
    expect(screen.getByText("点级诊断")).toBeInTheDocument();
    expect(screen.getByText("部分已审核")).toBeInTheDocument();
    expect(screen.getByText("已形成结论 3 / 4")).toBeInTheDocument();
    expect(screen.getByText("模型超时，当前点位未形成可靠结论。")).toBeInTheDocument();
    expect(screen.getByText("模型调用阶段未返回可用输出。")).toBeInTheDocument();
    expect(screen.getByText("block-009, block-010")).toBeInTheDocument();
    expect(screen.queryByText("prompt-v1")).not.toBeInTheDocument();
    expect(screen.queryByText(/raw output/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/endpoint/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/stack trace/i)).not.toBeInTheDocument();
  });

  it("shows admin diagnostics error states for 404 409 and network error", async () => {
    fetchMock
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            title: "TASK_RESULT_NOT_FOUND",
            detail: "Task result not found: missing-admin-task"
          }),
          {
            status: 404,
            headers: {
              "Content-Type": "application/json"
            }
          }
        )
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            title: "TASK_RESULT_NOT_READY",
            detail: "Task result not ready: task-024"
          }),
          {
            status: 409,
            headers: {
              "Content-Type": "application/json"
            }
          }
        )
      )
      .mockRejectedValueOnce(new Error("network down"));

    const firstRender = renderApp("/admin/diagnostics?taskId=missing-admin-task");
    expect(await screen.findByText("未找到对应任务，请确认 taskId 是否正确。")).toBeInTheDocument();
    firstRender.unmount();

    const secondRender = renderApp("/admin/diagnostics?taskId=task-024");
    expect(await screen.findByText("任务已创建，但诊断结果尚未生成。")).toBeInTheDocument();
    secondRender.unmount();

    renderApp("/admin/diagnostics?taskId=task-025");
    expect(await screen.findByText("诊断查询失败，请稍后重试。")).toBeInTheDocument();
  });
});
