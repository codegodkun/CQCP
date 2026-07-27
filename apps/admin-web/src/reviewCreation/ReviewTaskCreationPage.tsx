import { useEffect, useRef, useState, useCallback } from "react";
import { Alert, Button, Card, Input, Select, Typography } from "antd";

import type { FormFields, FieldError as FieldErrorType } from "./types";
import { EMPTY_FORM, MAX_UPLOAD_BYTES } from "./types";
import { DEMO_PRESET } from "./demoPreset";
import { submitReviewTask, SubmitError, parseValidationError, userFacingMessage } from "./api";

const { Title, Text } = Typography;
const { TextArea } = Input;

type ValidationErrors = Record<string, string>;
type PageError = { heading: string; detail: string | null } | null;

// ── Helpers ──

function isDocxFilename(name: string): boolean {
  return name.toLowerCase().endsWith(".docx");
}

function fileSizeError(file: File): string | null {
  if (file.size === 0) return "文件不能为空";
  if (file.size > MAX_UPLOAD_BYTES) return "文件不能超过 25 MiB";
  return null;
}

function validateForm(form: FormFields): ValidationErrors {
  const errors: ValidationErrors = {};

  // String required, trim
  const requiredTrimmed = (v: string) => v.trim() !== "";
  const trimLen = (v: string, max: number) => v.trim().length <= max;

  if (!requiredTrimmed(form.contractName)) errors.contractName = "合同名称为必填项";
  else if (!trimLen(form.contractName, 255)) errors.contractName = "合同名称不能超过 255 字符";
  if (!requiredTrimmed(form.partyAName)) errors.partyAName = "甲方名称为必填项";
  if (!requiredTrimmed(form.partyBName)) errors.partyBName = "乙方名称为必填项";
  if (!requiredTrimmed(form.projectName)) errors.projectName = "项目名称为必填项";
  if (!form.pricingMode) errors.pricingMode = "计价方式为必填项";
  if (!form.paymentMethod) errors.paymentMethod = "付款方式为必填项";
  if (!form.invoiceType) errors.invoiceType = "发票类型为必填项";

  // Decimal string validation: optional sign + digits + optional 1-2 decimal places
  const amountPattern = /^-?\d+(\.\d{1,2})?$/;
  const ratePattern = /^\d+(\.\d{1,4})?$/; // 0~100, 4dp max
  const ratioPattern = /^\d+(\.\d{1,2})?$/; // 0~100, 2dp max

  const validateDecimal = (v: string, pattern: RegExp, label: string): string | null => {
    if (v.trim() === "") return `${label}为必填项`;
    if (!pattern.test(v.trim())) {
      const dp = pattern === ratePattern ? "4" : "2";
      return `${label}格式无效，最多 ${dp} 位小数`;
    }
    const n = Number(v.trim());
    if (!Number.isFinite(n)) return `${label}格式无效`;
    return null;
  };

  const err1 = validateDecimal(form.contractTotalAmount, amountPattern, "合同总金额（含税）");
  if (err1) errors.contractTotalAmount = err1;
  const err2 = validateDecimal(form.taxExcludedAmount, amountPattern, "不含税金额");
  if (err2) errors.taxExcludedAmount = err2;
  const err3 = validateDecimal(form.taxAmount, amountPattern, "合同税额");
  if (err3) errors.taxAmount = err3;

  // taxRate: 0~100, 4dp
  if (form.taxRate.trim() === "") {
    errors.taxRate = "税率为必填项";
  } else if (!ratePattern.test(form.taxRate.trim())) {
    errors.taxRate = "税率格式无效，最多 4 位小数";
  } else {
    const n = Number(form.taxRate.trim());
    if (!Number.isFinite(n) || n < 0 || n > 100) errors.taxRate = "税率必须在 0~100 之间";
  }

  const pm = form.paymentMethod;
  if (pm === "MONTHLY") {
    ["prepaymentRatio", "progressPaymentRatio", "completionPaymentRatio", "settlementPaymentRatio", "warrantyRetentionRatio"].forEach((key) => {
      const v = (form as unknown as Record<string, string>)[key];
      const label = { prepaymentRatio: "预付款比例", progressPaymentRatio: "进度款比例", completionPaymentRatio: "竣工款比例", settlementPaymentRatio: "结算款比例", warrantyRetentionRatio: "质保款比例" }[key] ?? key;
      if (v.trim() === "") { errors[key] = `${label}为必填项`; return; }
      if (!ratioPattern.test(v.trim())) { errors[key] = `${label}格式无效，最多 2 位小数`; return; }
      const n = Number(v.trim());
      if (!Number.isFinite(n) || n < 0 || n > 100) errors[key] = `${label}必须在 0~100 之间`;
    });
    // MILESTONE field omitted from MONTHLY payload in buildStructuredFields()
  } else if (pm === "MILESTONE") {
    if (form.prepaymentRatio.trim() === "") {
      errors.prepaymentRatio = "预付款比例为必填项";
    } else if (!ratioPattern.test(form.prepaymentRatio.trim())) {
      errors.prepaymentRatio = "预付款比例格式无效，最多 2 位小数";
    } else {
      const n = Number(form.prepaymentRatio.trim());
      if (!Number.isFinite(n) || n < 0 || n > 100) errors.prepaymentRatio = "预付款比例必须在 0~100 之间";
    }
    if (form.milestonePaymentTerms.trim() === "") {
      errors.milestonePaymentTerms = "节点付款信息为必填项";
    }
  }

  return errors;
}

/** Map backend field path (e.g. "structuredFields.contractName") to component key. */
function pathToKey(path: string): string {
  return path.replace(/^structuredFields\./, "");
}

// ── Component ──

export function ReviewTaskCreationPage() {
  const [form, setForm] = useState<FormFields>(EMPTY_FORM);
  const [file, setFile] = useState<File | null>(null);
  const [validationErrors, setValidationErrors] = useState<ValidationErrors>({});
  const [pageError, setPageError] = useState<PageError>(null);
  const [successResult, setSuccessResult] = useState<{ taskId: string; executionId: string; resultUrl: string } | null>(null);
  const [loading, setLoading] = useState(false);
  const [fileError, setFileError] = useState<string | null>(null);
  const [backendFieldErrors, setBackendFieldErrors] = useState<FieldErrorType[]>([]);

  const inFlightRef = useRef(false);
  const mountedRef = useRef(true);
  // StrictMode-safe: React.StrictMode calls setup→cleanup→setup;
  // we must re-set to true on each mount.
  useEffect(() => {
    mountedRef.current = true;
    return () => { mountedRef.current = false; };
  }, []);

  const updateField = useCallback((key: keyof FormFields, value: string) => {
    setForm((prev) => ({ ...prev, [key]: value }));
    // Clear field validation on edit
    setValidationErrors((prev) => {
      const next = { ...prev };
      delete next[key];
      return next;
    });
    setBackendFieldErrors([]);
    setPageError(null);
    setSuccessResult(null);
  }, []);

  const handleDemoPreset = useCallback(() => {
    // Clear existing errors and results
    setValidationErrors({});
    setBackendFieldErrors([]);
    setPageError(null);
    setSuccessResult(null);
    // Apply demo values
    setForm((prev) => ({ ...prev, ...DEMO_PRESET }));
  }, []);

  const handleFileSelect = useCallback((selectedFile: File | null) => {
    setFileError(null);
    setBackendFieldErrors([]);
    setPageError(null);
    setSuccessResult(null);

    if (!selectedFile) {
      setFile(null);
      return;
    }

    if (!isDocxFilename(selectedFile.name)) {
      setFileError("仅支持 DOCX 文件");
      setFile(null);
      return;
    }

    const sizeErr = fileSizeError(selectedFile);
    if (sizeErr) {
      setFileError(sizeErr);
      setFile(null);
      return;
    }

    setFile(selectedFile);
  }, []);

  const handleSubmit = useCallback(async () => {
    if (inFlightRef.current) return;
    if (!file) { setFileError("请选择 DOCX 文件"); return; }

    // Re-validate file at submit time
    if (!isDocxFilename(file.name)) { setFileError("仅支持 DOCX 文件"); return; }
    const sizeErr = fileSizeError(file);
    if (sizeErr) { setFileError(sizeErr); return; }

    // Clear previous state
    setSuccessResult(null);
    setBackendFieldErrors([]);
    setPageError(null);

    // Validate form
    const clientErrors = validateForm(form);
    setValidationErrors(clientErrors);
    if (Object.keys(clientErrors).length > 0) return;

    inFlightRef.current = true;
    setLoading(true);

    try {
      const result = await submitReviewTask(file, form);
      if (!mountedRef.current) return;
      setSuccessResult({ taskId: result.taskId, executionId: result.executionId, resultUrl: result.resultUrl });
    } catch (err) {
      if (!mountedRef.current) return;
      if (err instanceof SubmitError && err.status === 400) {
        const parsed = parseValidationError(err.responseText);
        if (parsed) {
          const mapped: Record<string, FieldErrorType[]> = {};
          parsed.fieldErrors.forEach((fe) => {
            const key = pathToKey(fe.field);
            if (!mapped[key]) mapped[key] = [];
            mapped[key].push(fe);
          });
          setBackendFieldErrors(parsed.fieldErrors);
          // Set validation errors for known fields
          const be: ValidationErrors = {};
          parsed.fieldErrors.forEach((fe) => {
            const p = pathToKey(fe.field);
            if (p === "file") {
              setFileError(fe.message);
            } else if (Object.keys(EMPTY_FORM).includes(p)) {
              be[p] = fe.message;
            } else {
              be[fe.field] = fe.message; // unknown path → page-level summary
            }
          });
          setValidationErrors(be);
          // If some errors can't be mapped, show in page error
          const unhandled = parsed.fieldErrors.filter(
            (fe) => !Object.keys(EMPTY_FORM).includes(pathToKey(fe.field)),
          );
          if (unhandled.length > 0) {
            setPageError({ heading: `提交数据校验失败: ${parsed.message}`, detail: null });
          }
        } else {
          setPageError({ heading: "任务创建失败，请稍后重试", detail: null });
        }
      } else {
        const status = err instanceof SubmitError ? err.status : 0;
        const text = err instanceof SubmitError ? err.responseText : "";
        setPageError(userFacingMessage(status, text));
      }
    } finally {
      if (mountedRef.current) {
        inFlightRef.current = false;
        setLoading(false);
      }
    }
  }, [file, form]);

  const pm = form.paymentMethod;
  const isInFlight = inFlightRef.current;

  return (
    <div className="app-shell">
      <Card className="hero-panel">
        <Title level={3}>新建合同审核</Title>
        <Text type="secondary">当前仅支持 DOCX，DOC 待后续开发。</Text>

        { /* ── File Upload ── */ }
        <div style={{ marginTop: 16 }}>
          <input
            type="file"
            accept=".docx"
            disabled={isInFlight}
            onChange={(e) => handleFileSelect(e.target.files?.[0] ?? null)}
            data-testid="file-input"
          />
          {file && <Text style={{ marginLeft: 8 }}>{file.name}</Text>}
          {fileError && <div style={{ color: "red" }}>{fileError}</div>}
        </div>

        { /* ── Demo Preset ── */ }
        <Button onClick={handleDemoPreset} disabled={isInFlight} style={{ marginTop: 12 }}>
          填入 Demo 样本字段
        </Button>
        <Text style={{ marginLeft: 8, color: "#888" }} data-testid="demo-file-hint">建议选择：1、奔腾公司企鹅岛项目三标段土建总承包工程合同_缩减版.docx</Text>

        { /* ── Fixed Fields ── */ }
        <div style={{ marginTop: 12, display: "flex", gap: 24 }}>
          <Text strong>合同类型：</Text><Text>工程采购合同（ENGINEERING）</Text>
          <Text strong>币种：</Text><Text>人民币（CNY）</Text>
        </div>

        { /* ── Business Document ID ── */ }
        <div style={{ marginTop: 12 }}>
          <div style={{ marginBottom: 4 }}><Text strong>业务单据号（可选）</Text></div>
          <Input
            value={form.businessDocumentId}
            onChange={(e) => updateField("businessDocumentId", e.target.value)}
            disabled={isInFlight}
            data-testid="field-businessDocumentId"
          />
        </div>

        { /* ── General Fields ── */ }
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12, marginTop: 16 }}>
          <FormField label="合同名称" error={validationErrors.contractName}>
            <Input value={form.contractName} onChange={(e) => updateField("contractName", e.target.value)} disabled={isInFlight} data-testid="field-contractName" />
          </FormField>
          <FormField label="甲方名称" error={validationErrors.partyAName}>
            <Input value={form.partyAName} onChange={(e) => updateField("partyAName", e.target.value)} disabled={isInFlight} data-testid="field-partyAName" />
          </FormField>
          <FormField label="乙方名称" error={validationErrors.partyBName}>
            <Input value={form.partyBName} onChange={(e) => updateField("partyBName", e.target.value)} disabled={isInFlight} data-testid="field-partyBName" />
          </FormField>
          <FormField label="项目名称" error={validationErrors.projectName}>
            <Input value={form.projectName} onChange={(e) => updateField("projectName", e.target.value)} disabled={isInFlight} data-testid="field-projectName" />
          </FormField>
          <FormField label="合同总金额（含税）" error={validationErrors.contractTotalAmount}>
            <Input inputMode="decimal" value={form.contractTotalAmount} onChange={(e) => updateField("contractTotalAmount", e.target.value)} disabled={isInFlight} data-testid="field-contractTotalAmount" />
          </FormField>
          <FormField label="不含税金额" error={validationErrors.taxExcludedAmount}>
            <Input inputMode="decimal" value={form.taxExcludedAmount} onChange={(e) => updateField("taxExcludedAmount", e.target.value)} disabled={isInFlight} data-testid="field-taxExcludedAmount" />
          </FormField>
          <FormField label="合同税额" error={validationErrors.taxAmount}>
            <Input inputMode="decimal" value={form.taxAmount} onChange={(e) => updateField("taxAmount", e.target.value)} disabled={isInFlight} data-testid="field-taxAmount" />
          </FormField>
          <FormField label="税率" error={validationErrors.taxRate}>
            <Input inputMode="decimal" value={form.taxRate} onChange={(e) => updateField("taxRate", e.target.value)} disabled={isInFlight} data-testid="field-taxRate" />
          </FormField>
          <FormField label="计价方式" error={validationErrors.pricingMode}>
            <Select value={form.pricingMode || undefined} onChange={(v) => updateField("pricingMode", v)} disabled={isInFlight} data-testid="field-pricingMode" placeholder="选择计价方式">
              <Select.Option value="FIXED_TOTAL_PRICE">固定总价</Select.Option>
              <Select.Option value="PROVISIONAL_TOTAL_PRICE">暂定总价</Select.Option>
            </Select>
          </FormField>
          <FormField label="付款方式" error={validationErrors.paymentMethod}>
            <Select value={form.paymentMethod || undefined} onChange={(v) => updateField("paymentMethod", v as string)} disabled={isInFlight} data-testid="field-paymentMethod" placeholder="选择付款方式">
              <Select.Option value="MONTHLY">按月度付款</Select.Option>
              <Select.Option value="MILESTONE">按节点付款</Select.Option>
            </Select>
          </FormField>
          <FormField label="发票类型" error={validationErrors.invoiceType}>
            <Select value={form.invoiceType || undefined} onChange={(v) => updateField("invoiceType", v)} disabled={isInFlight} data-testid="field-invoiceType" placeholder="选择发票类型">
              <Select.Option value="VAT_GENERAL">增值税普通发票</Select.Option>
              <Select.Option value="VAT_SPECIAL">增值税专用发票</Select.Option>
            </Select>
          </FormField>
        </div>

        { /* ── Conditional: MONTHLY ratios ── */ }
        {pm === "MONTHLY" && (
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12, marginTop: 16 }}>
            <FormField label="预付款比例" error={validationErrors.prepaymentRatio}>
              <Input inputMode="decimal" value={form.prepaymentRatio} onChange={(e) => updateField("prepaymentRatio", e.target.value)} disabled={isInFlight} data-testid="field-prepaymentRatio" />
            </FormField>
            <FormField label="进度款比例" error={validationErrors.progressPaymentRatio}>
              <Input inputMode="decimal" value={form.progressPaymentRatio} onChange={(e) => updateField("progressPaymentRatio", e.target.value)} disabled={isInFlight} data-testid="field-progressPaymentRatio" />
            </FormField>
            <FormField label="竣工款比例" error={validationErrors.completionPaymentRatio}>
              <Input inputMode="decimal" value={form.completionPaymentRatio} onChange={(e) => updateField("completionPaymentRatio", e.target.value)} disabled={isInFlight} data-testid="field-completionPaymentRatio" />
            </FormField>
            <FormField label="结算款比例" error={validationErrors.settlementPaymentRatio}>
              <Input inputMode="decimal" value={form.settlementPaymentRatio} onChange={(e) => updateField("settlementPaymentRatio", e.target.value)} disabled={isInFlight} data-testid="field-settlementPaymentRatio" />
            </FormField>
            <FormField label="质保款比例" error={validationErrors.warrantyRetentionRatio}>
              <Input inputMode="decimal" value={form.warrantyRetentionRatio} onChange={(e) => updateField("warrantyRetentionRatio", e.target.value)} disabled={isInFlight} data-testid="field-warrantyRetentionRatio" />
            </FormField>
          </div>
        )}

        { /* ── Conditional: MILESTONE ── */ }
        {pm === "MILESTONE" && (
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12, marginTop: 16 }}>
            <FormField label="预付款比例" error={validationErrors.prepaymentRatio}>
              <Input inputMode="decimal" value={form.prepaymentRatio} onChange={(e) => updateField("prepaymentRatio", e.target.value)} disabled={isInFlight} data-testid="field-prepaymentRatio" />
            </FormField>
            <FormField label="节点付款信息" error={validationErrors.milestonePaymentTerms}>
              <TextArea rows={3} value={form.milestonePaymentTerms} onChange={(e) => updateField("milestonePaymentTerms", e.target.value)} disabled={isInFlight} data-testid="field-milestonePaymentTerms" />
            </FormField>
          </div>
        )}
      </Card>

      { /* ── Submit Button ── */ }
      <Card className="hero-panel" style={{ marginTop: 16 }}>
        <Button type="primary" loading={loading} disabled={!file || isInFlight} onClick={handleSubmit} size="large" data-testid="submit-btn">
          提交审核
        </Button>
      </Card>

      {/* Client-side field validation errors — page-level summary */}
      {(Object.keys(validationErrors).length > 0 || fileError) && (
        <Card className="hero-panel" style={{ marginTop: 16 }}>
          <Title level={5}>校验错误</Title>
          {Object.entries(validationErrors).map(([ek, ev], i) => (
            <div key={i}><Text type="danger">{ek}: {ev}</Text></div>
          ))}
          {fileError && <div><Text type="danger">文件: {fileError}</Text></div>}
        </Card>
      )}

      { /* ── Backend field errors — page-level summary —─ */ }
      {backendFieldErrors.length > 0 && (
        <Card className="hero-panel" style={{ marginTop: 16 }} data-testid="backend-field-errors">
          <Title level={5}>后端校验错误</Title>
          {backendFieldErrors.map((fe, i) => (
            <div key={i}><Text type="danger">{fe.field}: {fe.message}</Text></div>
          ))}
        </Card>
      )}

      { /* ── Page Error ── */ }
      {pageError && (
        <Card className="hero-panel" style={{ marginTop: 16 }}>
          <Alert
            type="error"
            message={pageError.heading}
            description={pageError.detail ?? undefined}
            showIcon
          />
        </Card>
      )}

      { /* ── Success ── */ }
      {successResult && (
        <Card className="hero-panel" style={{ marginTop: 16 }} data-testid="success-card">
          <Title level={5}>任务创建成功</Title>
          <div>Task ID: {successResult.taskId}</div>
          <div>Execution ID: {successResult.executionId}</div>
          <div>状态: QUEUED</div>
          <div>结果 URL: {successResult.resultUrl}</div>
        </Card>
      )}
    </div>
  );
}

// ── FormField helper ──

function FormField({ label, error, children }: { label: string; error?: string; children: React.ReactNode }) {
  return (
    <div>
      <div style={{ marginBottom: 4 }}><Text strong>{label}</Text></div>
      {children}
      {error && <div style={{ color: "red", fontSize: 12, marginTop: 2 }}>{error}</div>}
    </div>
  );
}
