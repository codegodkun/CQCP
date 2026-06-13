import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  Alert,
  Button,
  Card,
  Col,
  Empty,
  Flex,
  Form,
  Input,
  Row,
  Space,
  Tag,
  Typography
} from "antd";
import { useSearchParams } from "react-router-dom";

import { fetchTaskResult, TaskResultApiError } from "./api";
import type {
  ConfidenceLevel,
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

interface PointCardViewModel {
  result: PointReviewResult;
  snapshot?: ReviewPointSnapshot;
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

function firstAnchor(pointResult: PointReviewResult): SourceAnchorSummary | null {
  return pointResult.sourceAnchors[0] ?? null;
}

function errorMessage(error: Error | null): string | null {
  if (!(error instanceof TaskResultApiError)) {
    return error ? "结果查询失败，请稍后重试。" : null;
  }

  if (error.status === 404) {
    return "未找到对应任务，请确认 taskId 是否正确。";
  }

  if (error.status === 409) {
    return "任务已创建，但结果尚未生成。";
  }

  return "结果查询失败，请稍后重试。";
}

export function PublicResultPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const taskIdFromUrl = searchParams.get("taskId")?.trim() ?? "";
  const [inputTaskId, setInputTaskId] = useState(taskIdFromUrl);
  const [activeBlockId, setActiveBlockId] = useState<string | null>(null);

  const query = useQuery({
    queryKey: ["task-result", taskIdFromUrl],
    queryFn: () => fetchTaskResult(taskIdFromUrl),
    enabled: taskIdFromUrl.length > 0,
    retry: false
  });

  const pointCards = useMemo(
    () => (query.data ? buildPointCards(query.data) : []),
    [query.data]
  );

  const visibleAnchors = query.data?.sourceAnchors ?? [];
  const currentError = errorMessage(query.error);

  return (
    <div className="app-shell result-page">
      <section className="hero-panel">
        <Tag color="cyan">TASK-023</Tag>
        <Typography.Title level={2}>普通结果页最小展示</Typography.Title>
        <Typography.Paragraph>
          仅展示业务化审核结果，围绕“审核点 - 证据 - 原文定位”解释，不展示系统诊断明细。
        </Typography.Paragraph>
        <Form
          layout="inline"
          onFinish={() => {
            const nextTaskId = inputTaskId.trim();
            setActiveBlockId(null);
            setSearchParams(nextTaskId ? { taskId: nextTaskId } : {});
          }}
        >
          <Form.Item className="result-form-item">
            <Input
              aria-label="taskId"
              placeholder="输入 taskId，例如 task-001"
              value={inputTaskId}
              onChange={(event) => setInputTaskId(event.target.value)}
            />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" disabled={inputTaskId.trim().length === 0}>
              查询结果
            </Button>
          </Form.Item>
        </Form>
      </section>

      {!taskIdFromUrl && (
        <section className="content-panel">
          <Empty description="请输入 taskId 查询普通结果。" />
        </section>
      )}

      {taskIdFromUrl && currentError && (
        <section className="content-panel">
          <Alert type="warning" showIcon message={currentError} />
        </section>
      )}

      {taskIdFromUrl && query.isLoading && (
        <section className="content-panel">
          <Card loading className="result-card" />
        </section>
      )}

      {query.data && (
        <div className="result-content">
          <section className="content-panel">
            <Flex justify="space-between" align="start" gap={16} wrap>
              <div>
                <Typography.Title level={4}>任务 {query.data.taskId}</Typography.Title>
                <Typography.Paragraph>
                  执行 {query.data.executionId} · {query.data.status}
                </Typography.Paragraph>
              </div>
              <Space wrap className="summary-tags">
                <Tag>PLANNED {query.data.summary.plannedPointCount}</Tag>
                <Tag color="success">PASS {query.data.summary.passCount}</Tag>
                <Tag color="error">ERROR {query.data.summary.errorCount}</Tag>
                <Tag color="warning">WARNING {query.data.summary.warningCount}</Tag>
                <Tag color="processing">NOT_CONCLUDED {query.data.summary.notConcludedCount}</Tag>
                <Tag>SKIPPED {query.data.summary.skippedCount}</Tag>
              </Space>
            </Flex>
            <div className="completeness-grid">
              <Card size="small" className="result-card">
                <Typography.Text type="secondary">审核覆盖状态</Typography.Text>
                <Typography.Title level={5}>
                  {COVERAGE_LABEL[query.data.reviewCompleteness.reviewCoverageStatus]}
                </Typography.Title>
              </Card>
              <Card size="small" className="result-card">
                <Typography.Text type="secondary">已形成结论</Typography.Text>
                <Typography.Title level={5}>
                  {query.data.reviewCompleteness.concludedPointCount} /{" "}
                  {query.data.reviewCompleteness.executablePointCount}
                </Typography.Title>
              </Card>
              <Card size="small" className="result-card">
                <Typography.Text type="secondary">未形成结论</Typography.Text>
                <Typography.Title level={5}>
                  {query.data.reviewCompleteness.notConcludedPointCount}
                </Typography.Title>
              </Card>
              <Card size="small" className="result-card">
                <Typography.Text type="secondary">关键证据覆盖</Typography.Text>
                <Typography.Title level={5}>
                  {CONFIDENCE_LABEL[query.data.reviewCompleteness.confidenceLevel]}
                </Typography.Title>
              </Card>
            </div>
          </section>

          <Row gutter={[24, 24]} className="preview-grid">
            <Col xs={24} lg={11}>
              <Card
                title="原文定位（最小定位）"
                extra={<Typography.Text type="secondary">当前结果仅提供 block 级定位摘要。</Typography.Text>}
                className="result-card"
              >
                {visibleAnchors.length === 0 ? (
                  <Empty description="当前结果没有可展示的证据定位摘要。" />
                ) : (
                  <div className="anchor-list">
                    {visibleAnchors.map((anchor) => (
                      <button
                        type="button"
                        key={anchor.blockId}
                        className={`anchor-item${activeBlockId === anchor.blockId ? " is-active" : ""}`}
                        onClick={() => setActiveBlockId(anchor.blockId)}
                      >
                        <div className="anchor-item__header">
                          <strong>{anchor.blockId}</strong>
                          <Tag>{anchor.sourceExtractionMode}</Tag>
                        </div>
                        <Typography.Paragraph>{anchor.evidenceSummary}</Typography.Paragraph>
                        <Typography.Text type="secondary">
                          {anchor.sourceOrigin} / {anchor.contextType}
                        </Typography.Text>
                      </button>
                    ))}
                  </div>
                )}
              </Card>
            </Col>
            <Col xs={24} lg={13}>
              <div className="point-card-list">
                {pointCards.map(({ result, snapshot }) => {
                  const anchor = firstAnchor(result);
                  return (
                    <Card
                      key={result.reviewPointCode}
                      className="result-card point-card"
                      title={
                        <Space wrap>
                          <Tag>{snapshot?.displayCode ?? result.reviewPointCode}</Tag>
                          <Typography.Text strong>
                            {snapshot?.displayName ?? result.reviewPointCode}
                          </Typography.Text>
                        </Space>
                      }
                      extra={<Tag color={STATUS_COLOR[result.pointStatus]}>{result.pointStatus}</Tag>}
                    >
                      <Typography.Paragraph>{result.businessMessage}</Typography.Paragraph>

                      {result.pointStatus === "NOT_CONCLUDED" && result.notConcludedReason && (
                        <Alert
                          type="info"
                          showIcon
                          message={NOT_CONCLUDED_REASON_LABEL[result.notConcludedReason]}
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
                          <Typography.Text strong>证据摘要</Typography.Text>
                          <Typography.Paragraph>{anchor.evidenceSummary}</Typography.Paragraph>
                          <Button type="link" onClick={() => setActiveBlockId(anchor.blockId)}>
                            定位到 {anchor.blockId}
                          </Button>
                        </div>
                      )}
                    </Card>
                  );
                })}
              </div>
            </Col>
          </Row>
        </div>
      )}
    </div>
  );
}
