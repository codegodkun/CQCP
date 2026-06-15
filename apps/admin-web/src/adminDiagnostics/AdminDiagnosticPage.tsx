import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  Alert,
  Button,
  Card,
  Empty,
  Flex,
  Form,
  Input,
  Space,
  Tag,
  Typography
} from "antd";
import { useSearchParams } from "react-router-dom";

import { TaskResultApiError, fetchAdminDiagnosticResult } from "./api";
import type { AdminDiagnosticSnapshot, PointDiagnostic } from "./types";

const STATUS_COLOR = {
  PASS: "success",
  WARNING: "warning",
  ERROR: "error",
  NOT_CONCLUDED: "processing",
  SKIPPED: "default"
} as const;

const COVERAGE_LABEL = {
  FULL_REVIEWED: "已完整审核",
  PARTIAL_REVIEWED: "部分已审核",
  LOW_CONFIDENCE_REVIEW: "低置信度审核"
} as const;

function errorMessage(error: Error | null): string | null {
  if (!(error instanceof TaskResultApiError)) {
    return error ? "诊断查询失败，请稍后重试。" : null;
  }

  if (error.status === 404) {
    return "未找到对应任务，请确认 taskId 是否正确。";
  }

  if (error.status === 409) {
    return "任务已创建，但诊断结果尚未生成。";
  }

  return "诊断查询失败，请稍后重试。";
}

function buildSnapshotMap(snapshot: AdminDiagnosticSnapshot) {
  return new Map(
    snapshot.enabledReviewPointsSnapshot.map((item) => [item.reviewPointCode, item])
  );
}

function buildDiagnosticsMap(diagnostics: PointDiagnostic[]) {
  const diagnosticsMap = new Map<string, PointDiagnostic[]>();

  diagnostics.forEach((item) => {
    const current = diagnosticsMap.get(item.reviewPointCode) ?? [];
    current.push(item);
    diagnosticsMap.set(item.reviewPointCode, current);
  });

  return diagnosticsMap;
}

export function AdminDiagnosticPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const taskIdFromUrl = searchParams.get("taskId")?.trim() ?? "";
  const [inputTaskId, setInputTaskId] = useState(taskIdFromUrl);

  const query = useQuery({
    queryKey: ["admin-diagnostic-result", taskIdFromUrl],
    queryFn: () => fetchAdminDiagnosticResult(taskIdFromUrl),
    enabled: taskIdFromUrl.length > 0,
    retry: false
  });

  const currentError = errorMessage(query.error);

  const pointViews = useMemo(() => {
    if (!query.data) {
      return [];
    }

    const snapshotMap = buildSnapshotMap(query.data);
    const diagnosticsMap = buildDiagnosticsMap(query.data.diagnostics);

    return [...query.data.pointResults]
      .sort((left, right) => {
        const leftOrder =
          snapshotMap.get(left.reviewPointCode)?.displayOrder ?? Number.MAX_SAFE_INTEGER;
        const rightOrder =
          snapshotMap.get(right.reviewPointCode)?.displayOrder ?? Number.MAX_SAFE_INTEGER;
        return leftOrder - rightOrder;
      })
      .map((result) => ({
        result,
        snapshot: snapshotMap.get(result.reviewPointCode),
        diagnostics: diagnosticsMap.get(result.reviewPointCode) ?? []
      }));
  }, [query.data]);

  return (
    <div className="app-shell admin-diagnostic-page">
      <section className="hero-panel">
        <Tag color="geekblue">TASK-024</Tag>
        <Typography.Title level={2}>管理台诊断详情最小展示</Typography.Title>
        <Typography.Paragraph>
          仅消费现有 ReviewResultSnapshot，展示管理台可见的摘要、点级状态与
          diagnostics，不展示敏感调试内容或技术原始载荷。
        </Typography.Paragraph>
        <Form
          layout="inline"
          onFinish={() => {
            const nextTaskId = inputTaskId.trim();
            setSearchParams(nextTaskId ? { taskId: nextTaskId } : {});
          }}
        >
          <Form.Item className="result-form-item">
            <Input
              aria-label="admin-taskId"
              placeholder="输入 taskId，例如 task-001"
              value={inputTaskId}
              onChange={(event) => setInputTaskId(event.target.value)}
            />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" disabled={inputTaskId.trim().length === 0}>
              查询诊断
            </Button>
          </Form.Item>
        </Form>
      </section>

      {!taskIdFromUrl && (
        <section className="content-panel">
          <Empty description="请输入 taskId 查询管理台诊断详情。" />
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
          </section>

          <section className="content-panel">
            <Typography.Title level={4}>审核摘要</Typography.Title>
            <div className="admin-summary-grid">
              <Card size="small" className="result-card">
                <Typography.Text type="secondary">审核覆盖状态</Typography.Text>
                <Typography.Title level={5}>
                  {COVERAGE_LABEL[query.data.reviewCompleteness.reviewCoverageStatus]}
                </Typography.Title>
              </Card>
              <Card size="small" className="result-card">
                <Typography.Text type="secondary">已形成结论</Typography.Text>
                <Typography.Title level={5}>
                  已形成结论 {query.data.reviewCompleteness.concludedPointCount} /{" "}
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
                <Typography.Text type="secondary">全部点级状态</Typography.Text>
                <Space wrap className="summary-tags">
                  <Tag color="success">PASS</Tag>
                  <Tag color="error">ERROR</Tag>
                  <Tag color="warning">WARNING</Tag>
                  <Tag color="processing">NOT_CONCLUDED</Tag>
                  <Tag>SKIPPED</Tag>
                </Space>
              </Card>
            </div>
          </section>

          <section className="content-panel">
            <Typography.Title level={4}>点级诊断</Typography.Title>
            <div className="point-card-list">
              {pointViews.map(({ result, snapshot, diagnostics }) => (
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

                  {diagnostics.length === 0 ? (
                    <Empty
                      image={Empty.PRESENTED_IMAGE_SIMPLE}
                      description="当前审核点没有可展示的 diagnostics。"
                    />
                  ) : (
                    <div className="diagnostic-list">
                      {diagnostics.map((item) => (
                        <div key={`${item.reviewPointCode}-${item.diagnosticCode}`} className="diagnostic-item">
                          <Space wrap className="summary-tags">
                            <Tag color="blue">{item.diagnosticCode}</Tag>
                            <Tag>{item.pointStatus}</Tag>
                          </Space>
                          <Typography.Paragraph>
                            <strong>businessReason：</strong>
                            {item.businessReason}
                          </Typography.Paragraph>
                          <Typography.Paragraph>
                            <strong>evidenceSummary：</strong>
                            {item.evidenceSummary}
                          </Typography.Paragraph>
                          <Typography.Paragraph>
                            <strong>evidenceBlockIds：</strong>
                            {item.evidenceBlockIds.length > 0
                              ? item.evidenceBlockIds.join(", ")
                              : "无"}
                          </Typography.Paragraph>
                        </div>
                      ))}
                    </div>
                  )}
                </Card>
              ))}
            </div>
          </section>
        </div>
      )}
    </div>
  );
}
