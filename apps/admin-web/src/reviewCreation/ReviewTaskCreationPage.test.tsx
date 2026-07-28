import { cleanup, render, screen, fireEvent, waitFor, act } from "@testing-library/react";
import { StrictMode } from "react";
import { ConfigProvider } from "antd";
import { afterEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter, useLocation } from "react-router-dom";

import { ReviewTaskCreationPage } from "./ReviewTaskCreationPage";
import { SubmitError } from "./api";

import { EMPTY_FORM } from "./types";
import type { FormFields } from "./types";
import { DEMO_PRESET } from "./demoPreset";

// ── Mock api module ──

const { submitReviewTaskMock, parseValidationErrorMock, userFacingMessageMock } = vi.hoisted(() => ({
  submitReviewTaskMock: vi.fn(),
  parseValidationErrorMock: vi.fn(),
  userFacingMessageMock: vi.fn(() => ({ heading: "任务创建失败，请稍后重试", detail: null })),
}));

vi.mock("./api", () => ({
  submitReviewTask: submitReviewTaskMock,
  SubmitError: class extends Error {
    status: number; responseText: string; isKnownError: boolean;
    constructor(status: number, responseText: string, isKnownError: boolean) {
      super("Submit failed");
      this.name = "SubmitError";
      this.status = status;
      this.responseText = responseText;
      this.isKnownError = isKnownError;
    }
  },
  parseValidationError: parseValidationErrorMock,
  userFacingMessage: userFacingMessageMock,
}));

// ── Helpers ──

function renderPage() {
  return render(
    <ConfigProvider>
      <MemoryRouter>
        <ReviewTaskCreationPage />
        <LocationView />
      </MemoryRouter>
    </ConfigProvider>,
  );
}

function LocationView() {
  const location = useLocation();
  return <div data-testid="location">{location.pathname + location.search}</div>;
}

function createDocxFile(name: string, size = 1024): File {
  return new File(
    [new ArrayBuffer(size)],
    name,
    { type: "application/vnd.openxmlformats-officedocument.wordprocessingml.document" },
  );
}

function selectFile(input: HTMLElement, file: File | null) {
  fireEvent.change(input, { target: { files: file ? [file] : [] } });
}

async function selectAntdOption(testId: string, optionText: string) {
  const trigger = screen.getByTestId(testId);
  const selector = trigger.querySelector(".ant-select-selector");
  if (!selector) throw new Error(`.ant-select-selector not found in element [data-testid="${testId}"]`);
  fireEvent.mouseDown(selector);
  await waitFor(() => {
    expect(screen.getByText(optionText)).toBeInTheDocument();
  });
  fireEvent.click(screen.getByText(optionText));
}

// ── Tests ──

describe("ReviewTaskCreationPage", () => {
  afterEach(() => {
    cleanup();
    submitReviewTaskMock.mockReset();
    parseValidationErrorMock.mockReset();
    userFacingMessageMock.mockReset();
    userFacingMessageMock.mockReturnValue({ heading: "任务创建失败，请稍后重试", detail: null });
  });

  // ── 1 页面精确渲染 ──

  it("1a: renders DOCX hint, ENGINEERING/CNY, and amount field labels", () => {
    renderPage();
    expect(screen.getByText("当前仅支持 DOCX，DOC 待后续开发。")).toBeInTheDocument();
    expect(screen.getByText("工程采购合同（ENGINEERING）")).toBeInTheDocument();
    expect(screen.getByText("人民币（CNY）")).toBeInTheDocument();
    expect(screen.getByText("合同总金额（含税）")).toBeInTheDocument();
    expect(screen.getByText("不含税金额")).toBeInTheDocument();
    expect(screen.getByText("合同税额")).toBeInTheDocument();
  });

  it("1b: renders all 11 general form controls", () => {
    renderPage();
    const ids = [
      "field-businessDocumentId",
      "field-contractName",
      "field-partyAName",
      "field-partyBName",
      "field-projectName",
      "field-contractTotalAmount",
      "field-taxExcludedAmount",
      "field-taxAmount",
      "field-taxRate",
      "field-pricingMode",
      "field-paymentMethod",
      "field-invoiceType",
    ];
    ids.forEach((id) => {
      expect(screen.getByTestId(id)).toBeInTheDocument();
    });
  });

  it("1c: does not render callerType, model, or version controls", () => {
    renderPage();
    const forbidden = [/caller/i, /model/i, /版本/, /version/i];
    forbidden.forEach((pattern) => {
      expect(screen.queryAllByText(pattern)).toHaveLength(0);
    });
  });

  // ── 2 文件门禁 ──

  describe("file gate", () => {
    it("2a: rejects .doc and .pdf files — error near upload and in summary", async () => {
      renderPage();
      const input = screen.getByTestId("file-input");

      // .doc
      selectFile(input, new File([new ArrayBuffer(100)], "contract.doc"));
      expect(await screen.findByText("仅支持 DOCX 文件")).toBeInTheDocument();
      expect(screen.getByText("文件: 仅支持 DOCX 文件")).toBeInTheDocument();

      // Clear
      selectFile(input, null);
      await waitFor(() => {
        expect(screen.queryByText("仅支持 DOCX 文件")).not.toBeInTheDocument();
      });

      // .pdf
      selectFile(input, new File([new ArrayBuffer(100)], "contract.pdf", { type: "application/pdf" }));
      expect(await screen.findByText("仅支持 DOCX 文件")).toBeInTheDocument();
      expect(screen.getByText("文件: 仅支持 DOCX 文件")).toBeInTheDocument();
    });

    it("2b: rejects empty and >25MiB files", async () => {
      renderPage();
      const input = screen.getByTestId("file-input");

      // Empty
      selectFile(input, createDocxFile("empty.docx", 0));
      expect(await screen.findByText("文件不能为空")).toBeInTheDocument();
      expect(screen.getByText("文件: 文件不能为空")).toBeInTheDocument();

      // Clear
      selectFile(input, null);
      await waitFor(() => {
        expect(screen.queryByText("文件不能为空")).not.toBeInTheDocument();
      });

      // >25MiB
      selectFile(input, createDocxFile("large.docx", 26_214_401));
      expect(await screen.findByText("文件不能超过 25 MiB")).toBeInTheDocument();
      expect(screen.getByText("文件: 文件不能超过 25 MiB")).toBeInTheDocument();
    });

    it("2c: accepts valid .docx — shows filename, enables submit", async () => {
      renderPage();
      const input = screen.getByTestId("file-input");
      const submitBtn = screen.getByTestId("submit-btn");

      expect(submitBtn).toBeDisabled();

      selectFile(input, createDocxFile("my-contract.docx"));
      expect(await screen.findByText("my-contract.docx")).toBeInTheDocument();
      expect(submitBtn).not.toBeDisabled();

      // Remove file → disabled again
      selectFile(input, null);
      await waitFor(() => {
        expect(submitBtn).toBeDisabled();
      });
    });
  });

  // ── 3 Demo ──

  it("3: demo preset fills fields exactly, shows file hint, does not create File", async () => {
    renderPage();

    // File hint
    expect(screen.getByTestId("demo-file-hint")).toHaveTextContent(
      "奔腾公司企鹅岛项目三标段土建总承包工程合同_缩减版.docx",
    );

    // Click demo preset
    fireEvent.click(screen.getByText("填入 Demo 样本字段"));

    // Wait for form to populate
    await waitFor(() => {
      expect(screen.getByTestId("field-contractName")).toHaveValue(
        "奔腾公司企鹅岛项目三标段土建总承包工程合同",
      );
    });
    expect(screen.getByTestId("field-partyAName")).toHaveValue("奔腾公司");
    expect(screen.getByTestId("field-partyBName")).toHaveValue("前水公司");
    expect(screen.getByTestId("field-projectName")).toHaveValue("企鹅岛");
    expect(screen.getByTestId("field-contractTotalAmount")).toHaveValue("8848");
    expect(screen.getByTestId("field-taxExcludedAmount")).toHaveValue("7830.09");
    expect(screen.getByTestId("field-taxAmount")).toHaveValue("1017.91");
    expect(screen.getByTestId("field-taxRate")).toHaveValue("13");

    // MONTHLY ratios visible
    expect(screen.getByTestId("field-prepaymentRatio")).toBeInTheDocument();
    expect(screen.getByTestId("field-progressPaymentRatio")).toBeInTheDocument();

    // File not created or covered
    const fileInput = screen.getByTestId("file-input") as HTMLInputElement;
    expect(fileInput.files?.length ?? 0).toBe(0);
    expect(screen.getByTestId("submit-btn")).toBeDisabled();
  });

  // ── 4 付款方式切换 ──

  describe("payment method switching", () => {
    it("4a: MONTHLY shows 5 ratios; MILESTONE shows prepayment+terms, hides 4 ratios", async () => {
      renderPage();

      // Initially no conditional fields
      expect(screen.queryByTestId("field-prepaymentRatio")).not.toBeInTheDocument();
      expect(screen.queryByTestId("field-milestonePaymentTerms")).not.toBeInTheDocument();

      // → MONTHLY
      await selectAntdOption("field-paymentMethod", "按月度付款");

      expect(screen.getByTestId("field-prepaymentRatio")).toBeInTheDocument();
      expect(screen.getByTestId("field-progressPaymentRatio")).toBeInTheDocument();
      expect(screen.getByTestId("field-completionPaymentRatio")).toBeInTheDocument();
      expect(screen.getByTestId("field-settlementPaymentRatio")).toBeInTheDocument();
      expect(screen.getByTestId("field-warrantyRetentionRatio")).toBeInTheDocument();
      expect(screen.queryByTestId("field-milestonePaymentTerms")).not.toBeInTheDocument();

      // → MILESTONE
      await selectAntdOption("field-paymentMethod", "按节点付款");

      expect(screen.getByTestId("field-prepaymentRatio")).toBeInTheDocument();
      expect(screen.getByTestId("field-milestonePaymentTerms")).toBeInTheDocument();
      expect(screen.queryByTestId("field-progressPaymentRatio")).not.toBeInTheDocument();
      expect(screen.queryByTestId("field-completionPaymentRatio")).not.toBeInTheDocument();
      expect(screen.queryByTestId("field-settlementPaymentRatio")).not.toBeInTheDocument();
      expect(screen.queryByTestId("field-warrantyRetentionRatio")).not.toBeInTheDocument();
    });

    it("4b: switching back preserves previously entered values", async () => {
      renderPage();

      // Fill demo preset (MONTHLY with values)
      fireEvent.click(screen.getByText("填入 Demo 样本字段"));
      await waitFor(() => {
        expect(screen.getByTestId("field-prepaymentRatio")).toHaveValue("0");
      });

      // → MILESTONE
      await selectAntdOption("field-paymentMethod", "按节点付款");
      expect(screen.getByTestId("field-prepaymentRatio")).toHaveValue("0");

      // Fill milestone terms
      fireEvent.change(screen.getByTestId("field-milestonePaymentTerms"), {
        target: { value: "里程碑1: 50%; 里程碑2: 50%" },
      });

      // → back to MONTHLY
      await selectAntdOption("field-paymentMethod", "按月度付款");

      await waitFor(() => {
        expect(screen.getByTestId("field-prepaymentRatio")).toHaveValue("0");
      });
      expect(screen.getByTestId("field-progressPaymentRatio")).toHaveValue("70");
      expect(screen.getByTestId("field-completionPaymentRatio")).toHaveValue("80");
      expect(screen.getByTestId("field-settlementPaymentRatio")).toHaveValue("97");
      expect(screen.getByTestId("field-warrantyRetentionRatio")).toHaveValue("3");

      // Milestone hidden
      expect(screen.queryByTestId("field-milestonePaymentTerms")).not.toBeInTheDocument();
    });
  });

  // ── 5 客户端校验 ──

  describe("client-side validation blocks submit", () => {
    it("5a: required field errors", async () => {
      renderPage();
      const input = screen.getByTestId("file-input");
      const submitBtn = screen.getByTestId("submit-btn");

      selectFile(input, createDocxFile("test.docx"));
      await waitFor(() => {
        expect(submitBtn).not.toBeDisabled();
      });

      // Submit empty form
      fireEvent.click(submitBtn);

      await waitFor(() => {
        expect(screen.getByText("合同名称为必填项")).toBeInTheDocument();
      });
      expect(screen.getByText("甲方名称为必填项")).toBeInTheDocument();
      expect(screen.getByText("乙方名称为必填项")).toBeInTheDocument();
      expect(screen.getByText("项目名称为必填项")).toBeInTheDocument();
      expect(screen.getByText("合同总金额（含税）为必填项")).toBeInTheDocument();
      expect(screen.getByText("不含税金额为必填项")).toBeInTheDocument();
      expect(screen.getByText("合同税额为必填项")).toBeInTheDocument();
      expect(screen.getByText("税率为必填项")).toBeInTheDocument();
      expect(screen.getByText("计价方式为必填项")).toBeInTheDocument();
      expect(screen.getByText("付款方式为必填项")).toBeInTheDocument();
      expect(screen.getByText("发票类型为必填项")).toBeInTheDocument();

      expect(submitReviewTaskMock).not.toHaveBeenCalled();
    });

    it("5b: format (255/2dp/4dp) and range (0~100) errors", async () => {
      renderPage();
      const input = screen.getByTestId("file-input");
      const submitBtn = screen.getByTestId("submit-btn");

      // Select file + fill demo preset
      selectFile(input, createDocxFile("test.docx"));
      fireEvent.click(screen.getByText("填入 Demo 样本字段"));
      await waitFor(() => {
        expect(submitBtn).not.toBeDisabled();
      });

      // Override with invalid values
      fireEvent.change(screen.getByTestId("field-contractName"), {
        target: { value: "a".repeat(256) },
      });
      fireEvent.change(screen.getByTestId("field-contractTotalAmount"), {
        target: { value: "123.456" },
      });
      fireEvent.change(screen.getByTestId("field-taxRate"), {
        target: { value: "12.34567" },
      });
      fireEvent.change(screen.getByTestId("field-progressPaymentRatio"), {
        target: { value: "150" },
      });
      fireEvent.change(screen.getByTestId("field-warrantyRetentionRatio"), {
        target: { value: "101" },
      });

      // Submit
      fireEvent.click(submitBtn);

      // Verify errors
      await waitFor(() => {
        expect(screen.getByText("合同名称不能超过 255 字符")).toBeInTheDocument();
      });
      expect(screen.getByText("合同总金额（含税）格式无效，最多 2 位小数")).toBeInTheDocument();
      expect(screen.getByText("税率格式无效，最多 4 位小数")).toBeInTheDocument();
      expect(screen.getByText("进度款比例必须在 0~100 之间")).toBeInTheDocument();
      expect(screen.getByText("质保款比例必须在 0~100 之间")).toBeInTheDocument();

      expect(submitReviewTaskMock).not.toHaveBeenCalled();
    });
  });

  // ── 6 真实 submitReviewTask FormData 构造 ──

  it("6: real submitReviewTask constructs correct FormData for MONTHLY", async () => {
    const realApi = await vi.importActual<typeof import("./api")>("./api");
    const realSubmit = realApi.submitReviewTask;

    const originalFetch = globalThis.fetch;
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          taskId: "task-001",
          executionId: "exec-001",
          status: "QUEUED",
          resultUrl: "/results/001",
        }),
        { status: 202, headers: { "Content-Type": "application/json" } },
      ),
    );
    globalThis.fetch = fetchMock;

    try {
      const fields: FormFields = {
        ...EMPTY_FORM,
        ...DEMO_PRESET,
        paymentMethod: "MONTHLY" as const,
        businessDocumentId: "BD-001",
      };

      const file = createDocxFile("test.docx");
      const result = await realSubmit(file, fields);

      // URL & method
      expect(fetchMock).toHaveBeenCalledTimes(1);
      const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
      expect(url).toBe("/api/review/tasks");
      expect(init.method).toBe("POST");

      // No custom headers
      expect(init.headers).toBeUndefined();

      // FormData keys exactly file, metadata
      const fd = init.body as FormData;
      const keys: string[] = [];
      fd.forEach((_v, k) => keys.push(k));
      expect(keys).toEqual(["file", "metadata"]);

      // file entry
      expect(fd.get("file")).toBe(file);

      // metadata is Blob with application/json
      const metaBlob = fd.get("metadata") as Blob;
      expect(metaBlob).toBeInstanceOf(Blob);
      expect(metaBlob.type).toBe("application/json");

      const metaText = await new Promise<string>((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(reader.result as string);
        reader.onerror = () => reject(reader.error);
        reader.readAsText(metaBlob);
      });
      const metadata = JSON.parse(metaText);

      expect(metadata).toStrictEqual({
        businessDocumentId: "BD-001",
        contractType: "ENGINEERING",
        structuredFields: {
          completionPaymentRatio: 80,
          contractName: "奔腾公司企鹅岛项目三标段土建总承包工程合同",
          contractTotalAmount: 8848,
          currency: "CNY",
          invoiceType: "VAT_SPECIAL",
          partyAName: "奔腾公司",
          partyBName: "前水公司",
          paymentMethod: "MONTHLY",
          prepaymentRatio: 0,
          pricingMode: "FIXED_TOTAL_PRICE",
          progressPaymentRatio: 70,
          projectName: "企鹅岛",
          settlementPaymentRatio: 97,
          taxAmount: 1017.91,
          taxExcludedAmount: 7830.09,
          taxRate: 13,
          warrantyRetentionRatio: 3,
        },
      });

      // Success result
      expect(result.taskId).toBe("task-001");
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  it("7: real submitReviewTask constructs correct FormData for MILESTONE", async () => {
    const realApi = await vi.importActual<typeof import("./api")>("./api");
    const realSubmit = realApi.submitReviewTask;

    const originalFetch = globalThis.fetch;
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          taskId: "task-002",
          executionId: "exec-002",
          status: "QUEUED",
          resultUrl: "/results/002",
        }),
        { status: 202, headers: { "Content-Type": "application/json" } },
      ),
    );
    globalThis.fetch = fetchMock;

    try {
      const fields: FormFields = {
        ...EMPTY_FORM,
        ...DEMO_PRESET,
        paymentMethod: "MILESTONE" as const,
        milestonePaymentTerms: "节点1: 30%; 节点2: 70%",
      };

      const file = createDocxFile("test-milestone.docx");
      const result = await realSubmit(file, fields);

      // URL & method
      expect(fetchMock).toHaveBeenCalledTimes(1);
      const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
      expect(url).toBe("/api/review/tasks");
      expect(init.method).toBe("POST");

      // No custom headers
      expect(init.headers).toBeUndefined();

      // FormData keys exactly file, metadata
      const fd = init.body as FormData;
      const keys: string[] = [];
      fd.forEach((_v, k) => keys.push(k));
      expect(keys).toEqual(["file", "metadata"]);

      // file entry
      expect(fd.get("file")).toBe(file);

      // metadata is Blob with application/json
      const metaBlob = fd.get("metadata") as Blob;
      expect(metaBlob).toBeInstanceOf(Blob);
      expect(metaBlob.type).toBe("application/json");

      const metaText = await new Promise<string>((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(reader.result as string);
        reader.onerror = () => reject(reader.error);
        reader.readAsText(metaBlob);
      });
      const metadata = JSON.parse(metaText);

      expect(metadata).toStrictEqual({
        contractType: "ENGINEERING",
        structuredFields: {
          contractName: "奔腾公司企鹅岛项目三标段土建总承包工程合同",
          partyAName: "奔腾公司",
          partyBName: "前水公司",
          projectName: "企鹅岛",
          contractTotalAmount: 8848,
          taxExcludedAmount: 7830.09,
          taxAmount: 1017.91,
          taxRate: 13,
          pricingMode: "FIXED_TOTAL_PRICE",
          paymentMethod: "MILESTONE",
          invoiceType: "VAT_SPECIAL",
          currency: "CNY",
          prepaymentRatio: 0,
          milestonePaymentTerms: "节点1: 30%; 节点2: 70%",
        },
      });

      // Success result
      expect(result.taskId).toBe("task-002");
      expect(result.status).toBe("QUEUED");
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  // ── 8 真实 api.ts：202 响应格式校验 ──

  it("8: rejects malformed 202 responses (9 cases) and accepts valid 202", async () => {
    const realApi = await vi.importActual<typeof import("./api")>("./api");
    const realSubmit = realApi.submitReviewTask;
    const RealSubmitError = realApi.SubmitError;

    const originalFetch = globalThis.fetch;

    try {
      const fields: FormFields = {
        ...EMPTY_FORM,
        ...DEMO_PRESET,
        paymentMethod: "MONTHLY" as const,
      };
      const file = createDocxFile("test.docx");

      type MalformedCase = { label: string; body: () => BodyInit };
      const malformedCases: MalformedCase[] = [
        {
          label: "response.json() throws",
          body: () => "{ not valid json",
        },
        {
          label: "missing taskId",
          body: () => JSON.stringify({ executionId: "e-1", status: "QUEUED", resultUrl: "/r/1" }),
        },
        {
          label: "missing executionId",
          body: () => JSON.stringify({ taskId: "t-1", status: "QUEUED", resultUrl: "/r/1" }),
        },
        {
          label: "status not QUEUED",
          body: () => JSON.stringify({ taskId: "t-1", executionId: "e-1", status: "FAILED", resultUrl: "/r/1" }),
        },
        {
          label: "resultUrl not string",
          body: () => JSON.stringify({ taskId: "t-1", executionId: "e-1", status: "QUEUED", resultUrl: 123 }),
        },
        {
          label: "taskId whitespace only",
          body: () => JSON.stringify({ taskId: " ", executionId: "e-1", status: "QUEUED", resultUrl: "/r/1" }),
        },
        {
          label: "executionId whitespace only",
          body: () => JSON.stringify({ taskId: "t-1", executionId: "\t", status: "QUEUED", resultUrl: "/r/1" }),
        },
        {
          label: "resultUrl empty",
          body: () => JSON.stringify({ taskId: "t-1", executionId: "e-1", status: "QUEUED", resultUrl: "" }),
        },
        {
          label: "resultUrl whitespace only",
          body: () => JSON.stringify({ taskId: "t-1", executionId: "e-1", status: "QUEUED", resultUrl: "  " }),
        },
      ];

      for (const { label, body } of malformedCases) {
        const fetchMock = vi.fn().mockResolvedValue(
          new Response(body(), { status: 202, headers: { "Content-Type": "application/json" } }),
        );
        globalThis.fetch = fetchMock;

        let thrown: unknown;
        try {
          await realSubmit(file, fields);
        } catch (e) {
          thrown = e;
        }

        expect(thrown, `case "${label}": should throw`).toBeInstanceOf(RealSubmitError);
        expect((thrown as { status: number }).status, `case "${label}": status`).toBe(202);
      }

      // AC18: Valid 202 with extra sentinel fields — returns exactly four frozen fields, no sentinel leak
      const validFetchMock = vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            taskId: "task-ok",
            executionId: "exec-ok",
            status: "QUEUED",
            resultUrl: "/results/ok",
            secret: "sk-abc123xyz-secret-token",
            stackTrace: "at ContractService.submit (contract.ts:42:15)",
            rawOutput: "---RAW-DUMP---internal-details---",
            endpoint: "https://10.0.0.5:9443/internal/admin/v1/contracts",
          }),
          { status: 202, headers: { "Content-Type": "application/json" } },
        ),
      );
      globalThis.fetch = validFetchMock;

      const result = await realSubmit(file, fields);
      // Exact four fields with precise values
      expect(result).toStrictEqual({
        taskId: "task-ok",
        executionId: "exec-ok",
        status: "QUEUED",
        resultUrl: "/results/ok",
      });
      // Exactly four own keys
      expect(Object.keys(result)).toStrictEqual(["taskId", "executionId", "status", "resultUrl"]);
      // Serialized form must not contain any sentinel
      const serialized = JSON.stringify(result);
      expect(serialized).not.toContain("sk-abc123xyz-secret-token");
      expect(serialized).not.toContain("stackTrace");
      expect(serialized).not.toContain("RAW-DUMP");
      expect(serialized).not.toContain("internal-details");
      expect(serialized).not.toContain("10.0.0.5");
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  it("9: real parseValidationError — valid object equality & table-driven null returns", async () => {
    const realApi = await vi.importActual<typeof import("./api")>("./api");
    const parse = realApi.parseValidationError;

    // AC18: Valid input with extra sentinel keys at both top-level and per fieldError
    const SENTINEL = "RAW_JSON_SECRET_SENTINEL";
    const validInput = JSON.stringify({
      code: "VALIDATION_ERROR",
      message: "校验失败",
      fieldErrors: [
        { field: "contractName", code: "REQUIRED_FIELD_MISSING", message: "合同名称为必填", _secret: SENTINEL, stackTrace: "at validator.ts:42" },
        { field: "taxRate", code: "INVALID_DECIMAL_SCALE", message: "税率格式无效", endpoint: "https://internal:9443/v1" },
      ],
      _secret: SENTINEL,
      stackTrace: "at ContractService.submit (contract.ts:42:15)",
      rawOutput: "---RAW-DUMP---internal-details---",
    });

    const result = parse(validInput);
    expect(result).not.toBeNull();
    // AC18: exact frozen object — no extra keys at either level
    expect(result).toStrictEqual({
      code: "VALIDATION_ERROR",
      message: "校验失败",
      fieldErrors: [
        { field: "contractName", code: "REQUIRED_FIELD_MISSING", message: "合同名称为必填" },
        { field: "taxRate", code: "INVALID_DECIMAL_SCALE", message: "税率格式无效" },
      ],
    });
    // AC18: prove no extra keys at top level
    expect(Object.keys(result!)).toStrictEqual(["code", "message", "fieldErrors"]);
    // AC18: prove no extra keys on each fieldError
    expect(Object.keys(result!.fieldErrors[0])).toStrictEqual(["field", "code", "message"]);
    expect(Object.keys(result!.fieldErrors[1])).toStrictEqual(["field", "code", "message"]);
    // Serialized form must not contain any sentinel
    const serialized = JSON.stringify(result);
    expect(serialized).not.toContain(SENTINEL);
    expect(serialized).not.toContain("stackTrace");
    expect(serialized).not.toContain("RAW-DUMP");
    expect(serialized).not.toContain("internal-details");
    // Both fieldErrors preserved in order
    expect(result!.fieldErrors).toHaveLength(2);
    expect(result!.fieldErrors[0].field).toBe("contractName");
    expect(result!.fieldErrors[1].field).toBe("taxRate");

    // Table-driven: all return null
    const nullCases: [string, string][] = [
      ["empty fieldErrors", JSON.stringify({ code: "VALIDATION_ERROR", message: "msg", fieldErrors: [] })],
      ["unknown code", JSON.stringify({ code: "UNKNOWN_ERROR", message: "msg", fieldErrors: [{ field: "f", code: "REQUIRED_FIELD_MISSING", message: "m" }] })],
      ["field non-string", JSON.stringify({ code: "VALIDATION_ERROR", message: "msg", fieldErrors: [{ field: 123, code: "REQUIRED_FIELD_MISSING", message: "m" }] })],
      ["message non-string", JSON.stringify({ code: "VALIDATION_ERROR", message: 456, fieldErrors: [{ field: "f", code: "REQUIRED_FIELD_MISSING", message: "m" }] })],
      ["non-JSON", "not valid json"],
    ];

    for (const [label, input] of nullCases) {
      expect(parse(input), `case "${label}"`).toBeNull();
    }
  });

  it("10: real safeBusinessMessage + userFacingMessage — sentinel leak prevention across 409/429/503/0/202", async () => {
    const realApi = await vi.importActual<typeof import("./api")>("./api");
    const safeBusinessMessage = realApi.safeBusinessMessage;
    const userFacingMessage = realApi.userFacingMessage;

    const SENTINELS = {
      secret: "sk-abc123xyz-secret-token",
      stackTrace: "at ContractService.submit (contract.ts:42:15)",
      rawOutput: "---RAW-DUMP---internal-details---",
      endpoint: "https://10.0.0.5:9443/internal/admin/v1/contracts",
    };

    // ── Part A: safeBusinessMessage — only message+reason survive; zero sentinel leak ──
    const businessCases = [
      { status: 409, code: "EXECUTION_CONFIG_UNAVAILABLE", message: "执行配置不可用", reason: "No matching config" },
      { status: 429, code: "RATE_LIMITED", message: "请求过于频繁", reason: "Rate limit exceeded" },
      { status: 503, code: "STORAGE_UNAVAILABLE", message: "存储暂不可用", reason: "No healthy storage node" },
    ] as const;

    for (const { status, code, message, reason } of businessCases) {
      const payload = JSON.stringify({ code, message, reason, ...SENTINELS });
      const result = safeBusinessMessage(payload);
      expect(result, `status ${status}: should not be null`).not.toBeNull();
      expect(result!.message, `status ${status}: message`).toBe(message);
      expect(result!.reason, `status ${status}: reason`).toBe(reason);
      expect(Object.keys(result!).sort(), `status ${status}: only message & reason keys`).toEqual(["message", "reason"]);

      const serialized = JSON.stringify(result);
      for (const [sentinelKey, sentinelVal] of Object.entries(SENTINELS)) {
        expect(serialized, `status ${status}: must not leak ${sentinelKey}`).not.toContain(sentinelVal);
      }
    }

    // ── Part B: userFacingMessage — 409/429/503 with HTML or malformed JSON → frozen default heading ──
    const htmlBody = "<html><body><h1>502 Bad Gateway</h1><script>alert(1)</script></body></html>";
    const malformedJson = "{ not valid json }}";

    const frozenDefaults: Record<number, string> = {
      409: "当前执行配置不可用，请联系管理员",
      429: "当前任务较多，请稍后重试",
      503: "文档存储暂不可用，请稍后重试",
    };

    for (const [statusStr, heading] of Object.entries(frozenDefaults)) {
      const status = Number(statusStr);
      const ufHtml = userFacingMessage(status, htmlBody);
      expect(ufHtml.heading, `status ${status} HTML → frozen heading`).toBe(heading);
      expect(ufHtml.detail, `status ${status} HTML → detail null`).toBeNull();

      const ufMalformed = userFacingMessage(status, malformedJson);
      expect(ufMalformed.heading, `status ${status} malformed JSON → frozen heading`).toBe(heading);
      expect(ufMalformed.detail, `status ${status} malformed JSON → detail null`).toBeNull();
    }

    // ── Part C: userFacingMessage — status 0 & 202 → fixed safe heading, no sentinel leak ──
    const sentinelText = JSON.stringify(SENTINELS);

    const uf0 = userFacingMessage(0, sentinelText);
    expect(uf0.heading).toBe("无法连接审核服务，请检查网络后重试");
    expect(uf0.detail).toBeNull();
    const ser0 = JSON.stringify(uf0);
    for (const [key, val] of Object.entries(SENTINELS)) {
      expect(ser0, `status 0: must not leak ${key}`).not.toContain(val);
    }

    const uf202 = userFacingMessage(202, sentinelText);
    expect(uf202.heading).toBe("任务创建响应无效，请稍后重试");
    expect(uf202.detail).toBeNull();
    const ser202 = JSON.stringify(uf202);
    for (const [key, val] of Object.entries(SENTINELS)) {
      expect(ser202, `status 202: must not leak ${key}`).not.toContain(val);
    }
  });

  // ── 11 202成功：仅由显式 task/execution identity 导航 ──

  it("11: 202 success — navigates to the encoded status identity and ignores resultUrl", async () => {
    submitReviewTaskMock.mockResolvedValue({
      taskId: "task /甲",
      executionId: "exec /乙",
      status: "QUEUED",
      resultUrl: "https://evil.example/SECRET_SENTINEL",
    });

    renderPage();

    // Demo preset fills fields
    fireEvent.click(screen.getByText("填入 Demo 样本字段"));
    await waitFor(() => {
      expect(screen.getByTestId("field-contractName")).toHaveValue(
        "奔腾公司企鹅岛项目三标段土建总承包工程合同",
      );
    });

    // Select valid DOCX file
    const fileInput = screen.getByTestId("file-input");
    selectFile(fileInput, createDocxFile("contract-test.docx"));
    await waitFor(() => {
      expect(screen.getByText("contract-test.docx")).toBeInTheDocument();
    });

    // Submit
    fireEvent.click(screen.getByTestId("submit-btn"));

    // Navigation uses only the parsed task and execution identities.
    await waitFor(() => {
      expect(screen.getByTestId("location")).toHaveTextContent(
        "/review/tasks/task%20%2F%E7%94%B2/executions/exec%20%2F%E4%B9%99",
      );
    });
    expect(document.body.textContent).not.toContain("evil.example");
    expect(document.body.textContent).not.toContain("SECRET_SENTINEL");

    // No sentinel leak — common internal patterns must not appear
    expect(screen.queryByText(/sk-abc/)).not.toBeInTheDocument();
    expect(screen.queryByText(/stackTrace/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/RAW-DUMP/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/internal-details/i)).not.toBeInTheDocument();

    // No page-level error and no stale success card.
    expect(screen.queryByTestId("success-card")).not.toBeInTheDocument();
    expect(screen.queryByText(/任务创建失败/)).not.toBeInTheDocument();
    expect(screen.queryByText(/无法连接/)).not.toBeInTheDocument();
    expect(screen.queryByText(/响应无效/)).not.toBeInTheDocument();
  });

  // ── 12 同 tick 双击：只提交 1 次，pending 期间禁用提交按钮及影响请求输入的控件 ──

  it("12: same-tick double click — only 1 submit, controls disabled during pending", async () => {
    // Deferred promise to keep the request pending
    let resolveDeferred!: (value: {
      taskId: string;
      executionId: string;
      status: string;
      resultUrl: string;
    }) => void;
    const deferred = new Promise<{
      taskId: string;
      executionId: string;
      status: string;
      resultUrl: string;
    }>((resolve) => {
      resolveDeferred = resolve;
    });
    submitReviewTaskMock.mockReturnValue(deferred);

    renderPage();

    // Demo preset
    fireEvent.click(screen.getByText("填入 Demo 样本字段"));
    await waitFor(() => {
      expect(screen.getByTestId("field-contractName")).toHaveValue(
        "奔腾公司企鹅岛项目三标段土建总承包工程合同",
      );
    });

    // Select valid DOCX file
    const fileInput = screen.getByTestId("file-input");
    selectFile(fileInput, createDocxFile("contract-double.docx"));
    await waitFor(() => {
      expect(screen.getByText("contract-double.docx")).toBeInTheDocument();
    });

    const submitBtn = screen.getByTestId("submit-btn");

    // Double click the same button without awaiting between clicks
    fireEvent.click(submitBtn);
    fireEvent.click(submitBtn);

    // Guard: submitReviewTask called exactly once
    expect(submitReviewTaskMock).toHaveBeenCalledTimes(1);

    // During pending: submit button shows loading (antd loading prop → ant-btn-loading class) AND is disabled
    await waitFor(() => {
      expect(submitBtn).toHaveClass("ant-btn-loading");
      expect(submitBtn).toBeDisabled();
    });

    // Controls that affect request input are disabled during pending
    expect(screen.getByTestId("field-contractName")).toBeDisabled();
    expect(screen.getByTestId("file-input")).toBeDisabled();
    expect(screen.getByText("填入 Demo 样本字段").closest("button")).toBeDisabled();

    // Resolve the deferred so the component can finish
    resolveDeferred({
      taskId: "task-double",
      executionId: "exec-double",
      status: "QUEUED",
      resultUrl: "/results/double",
    });

    // Wait for navigation to avoid hanging
    await waitFor(() => {
      expect(screen.getByTestId("location")).toHaveTextContent(
        "/review/tasks/task-double/executions/exec-double",
      );
    });
  });

  // ── 13 AC14: 400 full error with two field errors, no sentinel leak ──

  it("13: AC14 400 — displays both field errors in summary, backend card; no sentinel/raw/stackTrace leak", async () => {
    const SENTINEL = "RAW_JSON_SECRET_SENTINEL";

    submitReviewTaskMock.mockRejectedValue(
      new SubmitError(
        400,
        JSON.stringify({
          code: "VALIDATION_ERROR",
          message: "校验失败",
          fieldErrors: [
            { field: "structuredFields.contractName", code: "REQUIRED_FIELD_MISSING", message: "合同名称为必填" },
            { field: "structuredFields.taxRate", code: "INVALID_DECIMAL_SCALE", message: "税率格式无效" },
          ],
          _secret: SENTINEL,
        }),
        true,
      ),
    );

    parseValidationErrorMock.mockReturnValue({
      code: "VALIDATION_ERROR",
      message: "校验失败",
      fieldErrors: [
        { field: "structuredFields.contractName", code: "REQUIRED_FIELD_MISSING", message: "合同名称为必填" },
        { field: "structuredFields.taxRate", code: "INVALID_DECIMAL_SCALE", message: "税率格式无效" },
      ],
    });

    renderPage();

    // Fill demo + select file
    fireEvent.click(screen.getByText("填入 Demo 样本字段"));
    await waitFor(() => {
      expect(screen.getByTestId("field-contractName")).toHaveValue(
        "奔腾公司企鹅岛项目三标段土建总承包工程合同",
      );
    });
    const fileInput = screen.getByTestId("file-input");
    selectFile(fileInput, createDocxFile("valid-demo.docx"));
    await waitFor(() => {
      expect(screen.getByText("valid-demo.docx")).toBeInTheDocument();
    });

    // Submit
    fireEvent.click(screen.getByTestId("submit-btn"));

    // Both field errors visible near respective fields (client-side validation errors card)
    await waitFor(() => {
      expect(screen.getByText("合同名称为必填")).toBeInTheDocument();
    });
    expect(screen.getByText("税率格式无效")).toBeInTheDocument();

    // Page-level summary (client validation errors card) contains both
    const contractNameErrors = screen.getAllByText(/contractName: 合同名称为必填/);
    expect(contractNameErrors).toHaveLength(2);
    const taxRateErrors = screen.getAllByText(/taxRate: 税率格式无效/);
    expect(taxRateErrors).toHaveLength(2);

    // Backend field errors card with exactly two entries
    expect(screen.getByTestId("backend-field-errors")).toBeInTheDocument();
    expect(screen.getByText("structuredFields.contractName: 合同名称为必填")).toBeInTheDocument();
    expect(screen.getByText("structuredFields.taxRate: 税率格式无效")).toBeInTheDocument();

    // ── Sentinel/leak checks ──
    // DOM must not contain the raw sentinel string
    expect(screen.queryByText(SENTINEL)).not.toBeInTheDocument();
    expect(screen.queryByText(/RAW_JSON_SECRET_SENTINEL/)).not.toBeInTheDocument();
    // DOM must not contain raw JSON
    expect(screen.queryByText(/_secret/)).not.toBeInTheDocument();
    expect(screen.queryByText(/VALIDATION_ERROR/)).not.toBeInTheDocument();
    // DOM must not contain stackTrace or secret patterns
    expect(screen.queryByText(/stackTrace/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/secret/i)).not.toBeInTheDocument();

    // No success card
    expect(screen.queryByTestId("success-card")).not.toBeInTheDocument();
  });

  // ── 14 AC17 retry: first reject, fix input, second resolve; errors clear; no stale success ──

  it("14: AC17 retry — first submit rejects 503, second resolves after fix; errors cleared, button restored", async () => {
    // ── First submission: 503 error ──
    const firstError = new SubmitError(
      503,
      "Service Unavailable",
      true,
    );
    submitReviewTaskMock.mockRejectedValueOnce(firstError);
    userFacingMessageMock.mockReturnValueOnce({
      heading: "文档存储暂不可用，请稍后重试",
      detail: null,
    });

    renderPage();

    // Fill demo + select valid file + submit
    fireEvent.click(screen.getByText("填入 Demo 样本字段"));
    await waitFor(() => {
      expect(screen.getByTestId("field-contractName")).toHaveValue(
        "奔腾公司企鹅岛项目三标段土建总承包工程合同",
      );
    });
    const fileInput = screen.getByTestId("file-input");
    selectFile(fileInput, createDocxFile("retry-test.docx"));
    await waitFor(() => {
      expect(screen.getByText("retry-test.docx")).toBeInTheDocument();
    });

    const submitBtn = screen.getByTestId("submit-btn");
    fireEvent.click(submitBtn);

    // Wait for page error to appear
    await waitFor(() => {
      expect(screen.getByText("文档存储暂不可用，请稍后重试")).toBeInTheDocument();
    });

    // No success card
    expect(screen.queryByTestId("success-card")).not.toBeInTheDocument();

    // Loading/disabled guards released after error
    await waitFor(() => {
      expect(submitBtn).not.toBeDisabled();
      expect(submitBtn).not.toHaveClass("ant-btn-loading");
    });

    // ── Fix an input and retry ──
    // Second submission: resolve with QUEUED
    submitReviewTaskMock.mockResolvedValueOnce({
      taskId: "task-retry-ok",
      executionId: "exec-retry-ok",
      status: "QUEUED",
      resultUrl: "/results/retry-ok",
    });

    // Modify contractName to fix the "issue"
    fireEvent.change(screen.getByTestId("field-contractName"), {
      target: { value: "修正后的合同名称" },
    });

    // Old error must have disappeared (updateField clears pageError/successResult)
    await waitFor(() => {
      expect(screen.queryByText("文档存储暂不可用，请稍后重试")).not.toBeInTheDocument();
    });

    // No stale success before retry
    expect(screen.queryByTestId("success-card")).not.toBeInTheDocument();

    // Submit again
    fireEvent.click(submitBtn);

    // Wait for status-route navigation
    await waitFor(() => {
      expect(screen.getByTestId("location")).toHaveTextContent(
        "/review/tasks/task-retry-ok/executions/exec-retry-ok",
      );
    });

    // Old error is gone
    expect(screen.queryByText("文档存储暂不可用，请稍后重试")).not.toBeInTheDocument();

    // Total submissions: exactly 2
    expect(submitReviewTaskMock).toHaveBeenCalledTimes(2);

    // Button is back to enabled
    expect(submitBtn).not.toBeDisabled();
  });

  // ── 15 StrictMode unmount safety: no setState/act warnings after unmount during pending submit ──

  it("15: StrictMode unmount safety — pending promise rejects after unmount, no warnings or leaks", async () => {
    // Deferred promise: resolved via _resolve (unused), rejected via rejectDeferred
    let rejectDeferred!: (reason: unknown) => void;
    const deferred = new Promise<{
      taskId: string;
      executionId: string;
      status: string;
      resultUrl: string;
    }>((_resolve, reject) => {
      rejectDeferred = reject;
    });
    submitReviewTaskMock.mockReturnValue(deferred);

    const { unmount } = render(
      <StrictMode>
        <ConfigProvider>
          <MemoryRouter>
            <ReviewTaskCreationPage />
          </MemoryRouter>
        </ConfigProvider>
      </StrictMode>,
    );

    // Fill demo preset
    fireEvent.click(screen.getByText("填入 Demo 样本字段"));
    await waitFor(() => {
      expect(screen.getByTestId("field-contractName")).toHaveValue(
        "奔腾公司企鹅岛项目三标段土建总承包工程合同",
      );
    });

    // Select valid DOCX file
    const fileInput = screen.getByTestId("file-input");
    selectFile(fileInput, createDocxFile("contract-unmount.docx"));
    await waitFor(() => {
      expect(screen.getByText("contract-unmount.docx")).toBeInTheDocument();
    });

    const submitBtn = screen.getByTestId("submit-btn");

    // Install console.error spy to capture React warnings emitted after unmount
    const errorSpy = vi.fn();
    const originalError = console.error;
    console.error = errorSpy;

    // Submit — keeps the deferred promise pending
    fireEvent.click(submitBtn);

    // Confirm submit called exactly once (inFlightRef + StrictMode-safe)
    expect(submitReviewTaskMock).toHaveBeenCalledTimes(1);

    // Verify loading state while pending
    await waitFor(() => {
      expect(submitBtn).toHaveClass("ant-btn-loading");
      expect(submitBtn).toBeDisabled();
    });

    // Unmount while the submit promise is still pending
    unmount();

    // Reject the deferred promise inside act and flush the microtask queue
    await act(async () => {
      rejectDeferred(new SubmitError(503, "Service Unavailable", true));
      // Drain microtasks so any setState / side-effect warnings fire synchronously
      await Promise.resolve();
    });

    // Restore console.error before assertions to avoid hiding test-runner errors
    console.error = originalError;

    // parseValidationErrorMock must NOT have been called (component unmounted before catch)
    expect(parseValidationErrorMock).not.toHaveBeenCalled();

    // userFacingMessageMock must NOT have been called (early return before error mapping)
    expect(userFacingMessageMock).not.toHaveBeenCalled();

    // Assert no React unmount / setState / act-related console.error warnings
    const unmountWarnings = errorSpy.mock.calls
      .filter((call: unknown[]) => typeof call[0] === "string")
      .map((call: unknown[]) => call[0] as string)
      .filter((msg: string) =>
        msg.includes("unmounted") ||
        msg.includes("act(") ||
        msg.includes("Can't perform a React state update") ||
        msg.includes("setState") ||
        msg.includes("React state update"),
      );
    expect(
      unmountWarnings,
      `Expected no unmount/setState/act warnings, got: ${unmountWarnings.join(" | ")}`,
    ).toHaveLength(0);
  });

});
