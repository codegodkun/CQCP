import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import {
  Alert,
  Button,
  Card,
  Col,
  Collapse,
  Empty,
  Flex,
  Form,
  Input,
  Row,
  Select,
  Space,
  Tag,
  Typography
} from "antd";
import { useLocation, useSearchParams } from "react-router-dom";

import {
  ManagementAccessControl
} from "../access/ManagementAccessContext";
import { useManagementAccess } from "../access/managementAccess";
import {
  downloadDocument,
  fetchDocumentPreview,
  fetchTaskResult,
  TaskResultApiError
} from "./api";
import type {
  ConfidenceLevel,
  DocumentPreviewBlock,
  NotConcludedReason,
  PointReviewResult,
  PointStatus,
  ReviewCoverageStatus,
  ReviewPointSnapshot,
  ReviewResultSnapshot,
  SourceAnchorSummary
} from "./types";

const STATUS_COLOR: Record<PointStatus, string> = {
  PASS: "success",
  WARNING: "warning",
  ERROR: "error",
  NOT_CONCLUDED: "processing",
  SKIPPED: "default"
};

const COVERAGE_LABEL: Record<ReviewCoverageStatus, string> = {
  FULL_REVIEWED: "已完整审核",
  PARTIAL_REVIEWED: "部分已审核",
  LOW_CONFIDENCE_REVIEW: "低置信度审核"
};

const CONFIDENCE_LABEL: Record<ConfidenceLevel, string> = {
  HIGH: "高",
  MEDIUM: "中",
  LOW: "低"
};

const NOT_CONCLUDED_REASON_LABEL: Record<NotConcludedReason, string> = {
  PARSE_LOW_CONFIDENCE: "解析置信度不足",
  EVIDENCE_NOT_FOUND: "未找到可靠证据",
  EVIDENCE_AMBIGUOUS: "证据归属不明确",
  MODEL_UNAVAILABLE: "模型暂不可用",
  MODEL_BUDGET_EXCEEDED: "预算不足",
  INTERNAL_RULE_ERROR: "规则处理异常"
};

const STRUCTURED_FIELD_META = [
  ["contractName", "合同名称", "plain"],
  ["partyAName", "甲方名称", "plain"],
  ["partyBName", "乙方名称", "plain"],
  ["projectName", "项目名称", "plain"],
  ["contractTotalAmount", "合同总金额（含税）", "money"],
  ["taxExcludedAmount", "不含税金额", "money"],
  ["taxAmount", "合同税额", "money"],
  ["taxRate", "税率", "ratio"],
  ["pricingMode", "计价方式", "pricingMode"],
  ["paymentMethod", "付款方式", "paymentMethod"],
  ["invoiceType", "发票类型", "invoiceType"],
  ["currency", "币种", "currency"],
  ["prepaymentRatio", "预付款比例", "ratio"],
  ["progressPaymentRatio", "进度付款比例", "ratio"],
  ["completionPaymentRatio", "完工付款比例", "ratio"],
  ["settlementPaymentRatio", "结算付款比例", "ratio"],
  ["warrantyRetentionRatio", "质保金比例", "ratio"],
  ["milestonePaymentTerms", "节点付款信息", "plain"]
] as const;

const ENUM_LABELS: Record<string, Record<string, string>> = {
  pricingMode: {
    FIXED_TOTAL_PRICE: "固定总价",
    PROVISIONAL_TOTAL_PRICE: "暂定总价"
  },
  paymentMethod: {
    MONTHLY: "按月度付款",
    MILESTONE: "按节点付款"
  },
  invoiceType: {
    VAT_GENERAL: "增值税普通发票",
    VAT_SPECIAL: "增值税专用发票"
  },
  currency: {
    CNY: "人民币（CNY）"
  }
};

interface PointCardViewModel {
  result: PointReviewResult;
  snapshot?: ReviewPointSnapshot;
}

interface ActiveEvidence {
  pointCode: string;
  anchorIndex: number;
  targetRef: string | null;
  blockId: string;
}

interface DownloadNotice {
  type: "success" | "error";
  message: string;
}

function displayStructuredValue(
  value: string,
  format: (typeof STRUCTURED_FIELD_META)[number][2]
): string {
  if (format === "money") return `${value} 元`;
  if (format === "ratio") return `${value}%`;
  if (format === "plain") return value;
  return ENUM_LABELS[format]?.[value] ?? "未知值";
}

function decodeCanonicalSegment(raw: string): string | null {
  try {
    const decoded = decodeURIComponent(raw);
    return decoded.trim().length > 0 && encodeURIComponent(decoded) === raw ? decoded : null;
  } catch {
    return null;
  }
}

function buildPointCards(snapshot: ReviewResultSnapshot): PointCardViewModel[] {
  const snapshotByCode = new Map(
    snapshot.enabledReviewPointsSnapshot.map((item) => [item.reviewPointCode, item])
  );
  return [...snapshot.pointResults]
    .sort((left, right) => {
      const leftOrder =
        snapshotByCode.get(left.reviewPointCode)?.displayOrder ?? Number.MAX_SAFE_INTEGER;
      const rightOrder =
        snapshotByCode.get(right.reviewPointCode)?.displayOrder ?? Number.MAX_SAFE_INTEGER;
      return leftOrder - rightOrder;
    })
    .map((result) => ({
      result,
      snapshot: snapshotByCode.get(result.reviewPointCode)
    }));
}

function errorMessage(error: Error | null): string | null {
  if (!(error instanceof TaskResultApiError)) {
    return error ? "结果查询失败，请稍后重试。" : null;
  }
  if (error.status === 404) return "未找到对应 task/execution。";
  if (error.status === 409) return "任务已创建，但结果尚未生成。";
  return "结果查询失败，请稍后重试。";
}

function anchorTarget(anchor: SourceAnchorSummary): string | null {
  if (anchor.locationLevel === "UNAVAILABLE") return null;
  if (anchor.previewElementRef?.trim()) return anchor.previewElementRef;
  if (anchor.blockId?.trim()) return anchor.blockId;
  return null;
}

function previewDomId(ref: string): string {
  return `preview-${encodeURIComponent(ref)}`;
}

function PreviewBlockView({
  block,
  active,
  relatedTargets
}: {
  block: DocumentPreviewBlock;
  active: ActiveEvidence | null;
  relatedTargets: Set<string>;
}) {
  const blockTargets = [block.blockId, block.previewElementRef].filter(
    (target): target is string => Boolean(target)
  );
  const blockActive =
    active?.targetRef !== null &&
    active?.targetRef !== undefined &&
    blockTargets.includes(active.targetRef);
  const blockRelated = blockTargets.some((target) => relatedTargets.has(target));
  const className = [
    "document-block",
    blockActive ? "is-primary-evidence" : "",
    !blockActive && blockRelated ? "is-related-evidence" : "",
    block.type === "HEADING" || block.type === "APPENDIX_TITLE" ? "is-heading" : ""
  ]
    .filter(Boolean)
    .join(" ");

  if (block.type === "TABLE_ROW" && block.cells.length > 0) {
    return (
      <div
        id={previewDomId(block.blockId)}
        data-block-id={block.blockId}
        data-preview-element-ref={block.previewElementRef ?? undefined}
        className={`${className} document-table-row`}
      >
        {block.cells.map((cell) => {
          const cellActive = active?.targetRef === cell.previewElementRef;
          const cellRelated = relatedTargets.has(cell.previewElementRef);
          return (
            <div
              id={previewDomId(cell.previewElementRef)}
              key={cell.previewElementRef}
              data-preview-element-ref={cell.previewElementRef}
              className={[
                "document-table-cell",
                cellActive ? "is-primary-evidence" : "",
                !cellActive && cellRelated ? "is-related-evidence" : ""
              ]
                .filter(Boolean)
                .join(" ")}
            >
              {cell.text}
            </div>
          );
        })}
      </div>
    );
  }

  return (
    <div id={previewDomId(block.blockId)} data-block-id={block.blockId} className={className}>
      {block.type === "HEADING" || block.type === "APPENDIX_TITLE" ? (
        <Typography.Title level={5}>{block.text}</Typography.Title>
      ) : (
        <Typography.Paragraph>{block.text}</Typography.Paragraph>
      )}
    </div>
  );
}

export function PublicResultPage() {
  const access = useManagementAccess();
  const { pathname } = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();
  const formalMode = pathname.startsWith("/review/results/");
  const formalMatch = /^\/review\/results\/([^/]+)$/.exec(pathname);
  const formalTaskId = formalMatch ? decodeCanonicalSegment(formalMatch[1]) : null;
  const formalExecutionValues = searchParams.getAll("executionId");
  const formalExecutionValue =
    formalExecutionValues.length === 1 ? formalExecutionValues[0] : null;
  const formalExecutionId =
    searchParams.size === 1 &&
    formalExecutionValue !== null &&
    formalExecutionValue.length > 0 &&
    formalExecutionValue === formalExecutionValue.trim()
      ? formalExecutionValue
      : null;
  const formalIdentityValid =
    !formalMode || (formalTaskId !== null && formalExecutionId !== null);
  const taskIdFromUrl = formalMode
    ? formalTaskId ?? ""
    : searchParams.get("taskId")?.trim() ?? "";
  const [inputTaskId, setInputTaskId] = useState(taskIdFromUrl);
  const [statusFilter, setStatusFilter] = useState<PointStatus | "ALL">("ALL");
  const [activeEvidence, setActiveEvidence] = useState<ActiveEvidence | null>(null);
  const [downloadNotice, setDownloadNotice] = useState<DownloadNotice | null>(
    null
  );

  const resultQuery = useQuery({
    queryKey: ["task-result", taskIdFromUrl, formalExecutionId ?? "legacy"],
    queryFn: () => fetchTaskResult(taskIdFromUrl, formalMode ? formalExecutionId : null),
    enabled: taskIdFromUrl.length > 0 && formalIdentityValid,
    retry: false
  });

  const previewQuery = useQuery({
    queryKey: [
      "document-preview",
      access.epoch,
      formalTaskId,
      formalExecutionId
    ],
    queryFn: () =>
      fetchDocumentPreview(formalTaskId!, formalExecutionId!, access.token),
    enabled: formalMode && formalIdentityValid && access.token.length > 0,
    retry: false
  });
  const documentDownload = useMutation({
    mutationFn: () =>
      downloadDocument(formalTaskId!, formalExecutionId!, access.token),
    onMutate: () => setDownloadNotice(null),
    onSuccess: (blob) => {
      const objectUrl = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = objectUrl;
      link.download = `${formalTaskId}-${formalExecutionId}.docx`;
      link.style.display = "none";
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.setTimeout(() => URL.revokeObjectURL(objectUrl), 0);
      setDownloadNotice({
        type: "success",
        message: `原始 DOCX 已从受控接口读取（${blob.size} bytes）并交给浏览器下载。`
      });
    },
    onError: () =>
      setDownloadNotice({
        type: "error",
        message: "原始 DOCX 下载失败，请重新验证管理访问后重试。"
      })
  });

  const identityMismatch =
    formalMode &&
    resultQuery.data !== undefined &&
    (resultQuery.data.taskId !== formalTaskId ||
      resultQuery.data.executionId !== formalExecutionId);
  const displayData = identityMismatch ? undefined : resultQuery.data;
  const pointCards = useMemo(
    () => (displayData ? buildPointCards(displayData) : []),
    [displayData]
  );
  const filteredCards = useMemo(
    () =>
      statusFilter === "ALL"
        ? pointCards
        : pointCards.filter(({ result }) => result.pointStatus === statusFilter),
    [pointCards, statusFilter]
  );
  const familyGroups = useMemo(() => {
    const groups = new Map<string, PointCardViewModel[]>();
    for (const card of filteredCards) {
      const family = card.snapshot?.reviewPointFamily ?? "OTHER";
      groups.set(family, [...(groups.get(family) ?? []), card]);
    }
    return [...groups.entries()];
  }, [filteredCards]);
  const activePoint = pointCards.find(
    ({ result }) => result.reviewPointCode === activeEvidence?.pointCode
  )?.result;
  const relatedTargets = useMemo(
    () =>
      new Set(
        (activePoint?.sourceAnchors ?? [])
          .map(anchorTarget)
          .filter((target): target is string => target !== null)
      ),
    [activePoint]
  );

  useEffect(() => {
    if (!activeEvidence?.targetRef) return;
    const target = Array.from(
      document.querySelectorAll<HTMLElement>(
        "[data-block-id], [data-preview-element-ref]"
      )
    ).find(
      (element) =>
        element.dataset.blockId === activeEvidence.targetRef ||
        element.dataset.previewElementRef === activeEvidence.targetRef
    );
    target?.scrollIntoView({ behavior: "smooth", block: "center" });
  }, [activeEvidence]);

  const currentError = !formalIdentityValid
    ? "结果页地址无效。"
    : identityMismatch
      ? "结果身份校验失败，请返回状态页重新查询。"
      : errorMessage(resultQuery.error);
  const structuredFields = displayData
    ? STRUCTURED_FIELD_META.flatMap(([key, label, format]) => {
        const value = displayData.structuredFieldsSnapshot[key];
        return typeof value === "string"
          ? [{ key, label, value: displayStructuredValue(value, format) }]
          : [];
      })
    : [];
  const previewBlocks = Array.isArray(previewQuery.data?.blocks)
    ? previewQuery.data.blocks
    : [];

  const locate = (
    pointCode: string,
    anchors: SourceAnchorSummary[],
    anchorIndex: number
  ) => {
    const anchor = anchors[anchorIndex];
    const targetRef = anchor ? anchorTarget(anchor) : null;
    if (!anchor) return;
    setActiveEvidence({
      pointCode,
      anchorIndex,
      targetRef,
      blockId: anchor.blockId
    });
  };

  return (
    <div className="app-shell result-page">
      <section className="hero-panel result-workbench-header">
        <Flex justify="space-between" align="center" gap={16} wrap>
          <div>
            <Tag color="cyan">MVP-002</Tag>
            <Typography.Title level={2}>合同审核工作台</Typography.Title>
            <Typography.Paragraph>
              按“审核点 → 证据 → 原文定位”核对，合同文本仅作为文本节点显示。
            </Typography.Paragraph>
          </div>
          {formalMode && formalTaskId && formalExecutionId && (
            <Button
              disabled={!access.token}
              loading={documentDownload.isPending}
              onClick={() => documentDownload.mutate()}
            >
              下载原始 DOCX
            </Button>
          )}
        </Flex>
        {!formalMode && (
          <Form
            layout="inline"
            onFinish={() => {
              const nextTaskId = inputTaskId.trim();
              setActiveEvidence(null);
              setSearchParams(nextTaskId ? { taskId: nextTaskId } : {});
            }}
          >
            <Form.Item>
              <Input
                aria-label="taskId"
                placeholder="输入 taskId"
                value={inputTaskId}
                onChange={(event) => setInputTaskId(event.target.value)}
              />
            </Form.Item>
            <Form.Item>
              <Button type="primary" htmlType="submit" disabled={!inputTaskId.trim()}>
                查询结果
              </Button>
            </Form.Item>
          </Form>
        )}
      </section>

      {!formalMode && !taskIdFromUrl && (
        <section className="content-panel">
          <Empty description="请输入 taskId 查询普通结果。" />
        </section>
      )}
      {(taskIdFromUrl || formalMode) && currentError && (
        <section className="content-panel">
          <Alert type="warning" showIcon message={currentError} />
        </section>
      )}
      {downloadNotice && (
        <section className="content-panel">
          <Alert
            type={downloadNotice.type}
            showIcon
            message={downloadNotice.message}
          />
        </section>
      )}
      {taskIdFromUrl && resultQuery.isLoading && (
        <section className="content-panel">
          <Card loading className="result-card" />
        </section>
      )}

      {displayData && (
        <div className="result-content">
          <section className="content-panel result-summary-panel">
            <Flex justify="space-between" align="start" gap={16} wrap>
              <div>
                <Typography.Title level={4}>任务 {displayData.taskId}</Typography.Title>
                <Typography.Paragraph>
                  执行 {displayData.executionId} · {displayData.status}
                </Typography.Paragraph>
              </div>
              <Space wrap>
                <Tag>PLANNED {displayData.summary.plannedPointCount}</Tag>
                <Tag color="success">PASS {displayData.summary.passCount}</Tag>
                <Tag color="error">ERROR {displayData.summary.errorCount}</Tag>
                <Tag color="warning">WARNING {displayData.summary.warningCount}</Tag>
                <Tag color="processing">
                  NOT_CONCLUDED {displayData.summary.notConcludedCount}
                </Tag>
                <Tag>SKIPPED {displayData.summary.skippedCount}</Tag>
              </Space>
            </Flex>
            <div className="completeness-grid">
              <Card size="small">
                <Typography.Text type="secondary">审核覆盖状态</Typography.Text>
                <Typography.Title level={5}>
                  {COVERAGE_LABEL[displayData.reviewCompleteness.reviewCoverageStatus]}
                </Typography.Title>
              </Card>
              <Card size="small">
                <Typography.Text type="secondary">已形成结论</Typography.Text>
                <Typography.Title level={5}>
                  {displayData.reviewCompleteness.concludedPointCount} /{" "}
                  {displayData.reviewCompleteness.executablePointCount}
                </Typography.Title>
              </Card>
              <Card size="small">
                <Typography.Text type="secondary">未形成结论</Typography.Text>
                <Typography.Title level={5}>
                  {displayData.reviewCompleteness.notConcludedPointCount}
                </Typography.Title>
              </Card>
              <Card size="small">
                <Typography.Text type="secondary">关键证据覆盖</Typography.Text>
                <Typography.Title level={5}>
                  {CONFIDENCE_LABEL[displayData.reviewCompleteness.confidenceLevel]}
                </Typography.Title>
              </Card>
            </div>
          </section>

          <section className="content-panel">
            <Typography.Title level={4}>合同结构化信息</Typography.Title>
            {structuredFields.length === 0 ? (
              <Empty description="当前结果没有可展示的结构化字段。" />
            ) : (
              <div className="structured-field-grid">
                {structuredFields.map((field) => (
                  <Card key={field.key} size="small">
                    <Typography.Text type="secondary">{field.label}</Typography.Text>
                    <Typography.Paragraph className="structured-field-value">
                      {field.value}
                    </Typography.Paragraph>
                  </Card>
                ))}
              </div>
            )}
          </section>

          <Row gutter={[24, 24]} className="workbench-grid">
            <Col xs={24} xl={13}>
              <Card
                title="合同原文"
                extra={
                  previewQuery.data ? (
                    <Typography.Text type="secondary">
                      parser {previewQuery.data.parserVersion}
                    </Typography.Text>
                  ) : null
                }
                className="result-card document-preview-card"
              >
                {formalMode && <ManagementAccessControl />}
                {formalMode && access.token && previewQuery.isLoading && <Card loading />}
                {formalMode && previewQuery.error && (
                  <Alert
                    type="warning"
                    showIcon
                    message={
                      previewQuery.error instanceof TaskResultApiError &&
                      previewQuery.error.status === 409
                        ? previewQuery.error.message
                        : previewQuery.error instanceof TaskResultApiError &&
                            previewQuery.error.status === 401
                          ? "管理访问凭据无效，请退出后重新验证。"
                          : "当前合同预览不可用，可下载原始 DOCX 后人工核对。"
                    }
                  />
                )}
                {documentDownload.error && (
                  <Alert
                    type="warning"
                    showIcon
                    message="原始 DOCX 下载失败，请核对管理访问凭据后重试。"
                  />
                )}
                {previewBlocks.length > 0 ? (
                  <article className="document-preview" aria-label="合同原文预览">
                    {previewBlocks.map((block) => (
                      <PreviewBlockView
                        key={block.blockId}
                        block={block}
                        active={activeEvidence}
                        relatedTargets={relatedTargets}
                      />
                    ))}
                  </article>
                ) : !formalMode && displayData.sourceAnchors.length > 0 ? (
                  <div className="anchor-list">
                    {displayData.sourceAnchors.map((anchor) => (
                      <div key={`${anchor.blockId}-${anchor.previewElementRef ?? ""}`} className="anchor-item">
                        <strong>{anchor.blockId}</strong>
                        <Typography.Paragraph>{anchor.evidenceSummary}</Typography.Paragraph>
                      </div>
                    ))}
                  </div>
                ) : (
                  access.token &&
                  !previewQuery.isLoading &&
                  !previewQuery.error && <Empty description="当前没有可展示的合同预览。" />
                )}
              </Card>
            </Col>

            <Col xs={24} xl={11}>
              <Card className="result-card point-filter-card">
                <Flex justify="space-between" align="center" gap={12} wrap>
                  <Typography.Title level={4}>审核点</Typography.Title>
                  <Select<PointStatus | "ALL">
                    aria-label="审核点状态筛选"
                    value={statusFilter}
                    style={{ width: 180 }}
                    onChange={setStatusFilter}
                    options={[
                      { value: "ALL", label: "全部状态" },
                      { value: "PASS", label: "PASS" },
                      { value: "ERROR", label: "ERROR" },
                      { value: "WARNING", label: "WARNING" },
                      { value: "NOT_CONCLUDED", label: "NOT_CONCLUDED" },
                      { value: "SKIPPED", label: "SKIPPED" }
                    ]}
                  />
                </Flex>
              </Card>

              {familyGroups.length === 0 ? (
                <Card className="result-card">
                  <Empty description="当前筛选下没有审核点。" />
                </Card>
              ) : (
                <Collapse
                  className="family-collapse"
                  defaultActiveKey={familyGroups.map(([family]) => family)}
                  items={familyGroups.map(([family, cards]) => ({
                    key: family,
                    label: `${family}（${cards.length}）`,
                    children: (
                      <div className="point-card-list">
                        {cards.map(({ result, snapshot }) => {
                          const activeIndex =
                            activeEvidence?.pointCode === result.reviewPointCode
                              ? activeEvidence.anchorIndex
                              : 0;
                          const anchor = result.sourceAnchors[activeIndex];
                          const target = anchor ? anchorTarget(anchor) : null;
                          return (
                            <Card
                              key={result.reviewPointCode}
                              className="result-card point-card"
                              title={
                                <Space wrap>
                                  <Tag>
                                    {snapshot?.displayCode ?? result.reviewPointCode}
                                  </Tag>
                                  <Typography.Text strong>
                                    {snapshot?.displayName ?? result.reviewPointCode}
                                  </Typography.Text>
                                </Space>
                              }
                              extra={
                                <Tag color={STATUS_COLOR[result.pointStatus]}>
                                  {result.pointStatus}
                                </Tag>
                              }
                            >
                              <Typography.Paragraph>
                                {result.businessMessage}
                              </Typography.Paragraph>

                              {result.pointStatus === "NOT_CONCLUDED" &&
                                result.notConcludedReason && (
                                  <Alert
                                    type="info"
                                    showIcon
                                    message={
                                      NOT_CONCLUDED_REASON_LABEL[
                                        result.notConcludedReason
                                      ]
                                    }
                                    description="请人工核对相关条款或补充证据后再判断。"
                                  />
                                )}
                              {result.pointStatus === "SKIPPED" && (
                                <Alert
                                  type="info"
                                  showIcon
                                  message="该审核点在当前场景下不适用。"
                                />
                              )}

                              {anchor && (
                                <div className="point-evidence">
                                  <Flex justify="space-between" gap={8} wrap>
                                    <Typography.Text strong>
                                      证据 {activeIndex + 1} /{" "}
                                      {result.sourceAnchors.length}
                                    </Typography.Text>
                                    <Space>
                                      <Button
                                        size="small"
                                        disabled={activeIndex === 0}
                                        onClick={() =>
                                          locate(
                                            result.reviewPointCode,
                                            result.sourceAnchors,
                                            activeIndex - 1
                                          )
                                        }
                                      >
                                        上一处
                                      </Button>
                                      <Button
                                        size="small"
                                        disabled={
                                          activeIndex >= result.sourceAnchors.length - 1
                                        }
                                        onClick={() =>
                                          locate(
                                            result.reviewPointCode,
                                            result.sourceAnchors,
                                            activeIndex + 1
                                          )
                                        }
                                      >
                                        下一处
                                      </Button>
                                    </Space>
                                  </Flex>
                                  <Typography.Paragraph>
                                    {anchor.evidenceSummary}
                                  </Typography.Paragraph>
                                  {anchor.locationLevel === "BLOCK_LEVEL" && (
                                    <Tag color="gold">块级定位</Tag>
                                  )}
                                  {target ? (
                                    <Button
                                      type="link"
                                      onClick={() =>
                                        locate(
                                          result.reviewPointCode,
                                          result.sourceAnchors,
                                          activeIndex
                                        )
                                      }
                                    >
                                      定位到原文
                                    </Button>
                                  ) : (
                                    <Typography.Text type="secondary">
                                      当前证据没有可靠定位。
                                    </Typography.Text>
                                  )}
                                </div>
                              )}
                            </Card>
                          );
                        })}
                      </div>
                    )
                  }))}
                />
              )}
            </Col>
          </Row>
        </div>
      )}
    </div>
  );
}
