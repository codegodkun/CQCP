import { useQuery } from "@tanstack/react-query";
import {
  Alert,
  Button,
  Card,
  Empty,
  Flex,
  Form,
  Input,
  Pagination,
  Select,
  Space,
  Tag,
  Typography
} from "antd";
import { useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";

import {
  ManagementAccessControl
} from "../access/ManagementAccessContext";
import { useManagementAccess } from "../access/managementAccess";
import { fetchReviewTasks } from "./api";
import type { TaskExecutionItem, TaskStatusGroup } from "./types";

const PAGE_SIZE = 20;

function canonicalPage(value: string | null): number {
  if (!value || !/^\d+$/.test(value)) return 0;
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) ? parsed : 0;
}

function canonicalGroup(value: string | null): TaskStatusGroup | undefined {
  return value === "PROCESSING" || value === "COMPLETED" || value === "FAILED"
    ? value
    : undefined;
}

function targetUrl(item: TaskExecutionItem): string {
  if (item.status === "SUCCESS" || item.status === "PARTIAL_SUCCESS") {
    return `/review/results/${encodeURIComponent(item.taskId)}?executionId=${encodeURIComponent(item.executionId)}`;
  }
  return `/review/tasks/${encodeURIComponent(item.taskId)}/executions/${encodeURIComponent(item.executionId)}`;
}

export function ReviewTaskListPage() {
  const access = useManagementAccess();
  const [searchParams, setSearchParams] = useSearchParams();
  const page = canonicalPage(searchParams.get("page"));
  const statusGroup = canonicalGroup(searchParams.get("statusGroup"));
  const queryValue = searchParams.get("q")?.trim() ?? "";
  const [queryInput, setQueryInput] = useState(queryValue);

  const query = useQuery({
    queryKey: [
      "review-tasks",
      access.epoch,
      page,
      statusGroup ?? "ALL",
      queryValue
    ],
    queryFn: () =>
      fetchReviewTasks({
        page,
        size: PAGE_SIZE,
        statusGroup,
        q: queryValue
      }, access.token),
    enabled: access.token.length > 0,
    retry: false
  });

  const filters = useMemo(
    () => ({
      statusGroup,
      q: queryValue
    }),
    [statusGroup, queryValue]
  );

  const updateSearch = (next: {
    page?: number;
    statusGroup?: TaskStatusGroup;
    q?: string;
  }) => {
    const params = new URLSearchParams();
    const nextPage = next.page ?? 0;
    const nextGroup = next.statusGroup;
    const nextQuery = next.q?.trim() ?? "";
    if (nextPage > 0) params.set("page", String(nextPage));
    if (nextGroup) params.set("statusGroup", nextGroup);
    if (nextQuery) params.set("q", nextQuery);
    setSearchParams(params);
  };

  return (
    <main className="app-shell">
      <section className="hero-panel task-list-panel">
        <Flex justify="space-between" align="center" gap={16} wrap>
          <div>
            <Tag color="cyan">MVP-002</Tag>
            <Typography.Title level={2}>审核任务</Typography.Title>
            <Typography.Paragraph>
              每条记录对应一个 execution，状态、模型与统计不会跨 execution 聚合。
            </Typography.Paragraph>
          </div>
          <Link to="/review/new">
            <Button type="primary">新建审核</Button>
          </Link>
        </Flex>

        <Form
          layout="inline"
          onFinish={() =>
            updateSearch({
              page: 0,
              statusGroup,
              q: queryInput
            })
          }
        >
          <Form.Item>
            <Input
              aria-label="搜索合同或任务"
              value={queryInput}
              maxLength={200}
              placeholder="合同名、taskId 或 executionId"
              onChange={(event) => setQueryInput(event.target.value)}
            />
          </Form.Item>
          <Form.Item>
            <Select<TaskStatusGroup | undefined>
              aria-label="状态分组"
              allowClear
              value={statusGroup}
              placeholder="全部状态"
              style={{ width: 160 }}
              options={[
                { value: "PROCESSING", label: "处理中" },
                { value: "COMPLETED", label: "已完成" },
                { value: "FAILED", label: "失败/取消" }
              ]}
              onChange={(value) =>
                updateSearch({ page: 0, statusGroup: value, q: queryValue })
              }
            />
          </Form.Item>
          <Form.Item>
            <Button htmlType="submit">搜索</Button>
          </Form.Item>
        </Form>
        <ManagementAccessControl />
      </section>

      <section className="content-panel task-list-panel">
        {access.token && query.isLoading && <Card loading />}
        {query.error && (
          <Alert
            type="error"
            showIcon
            message={
              query.error instanceof Error &&
              "status" in query.error &&
              query.error.status === 401
                ? "管理访问凭据无效，请退出后重新验证。"
                : "任务列表查询失败，请稍后重试。"
            }
          />
        )}
        {!access.token && <Empty description="验证管理访问凭据后显示 execution 清单。" />}
        {query.data?.items.length === 0 && <Empty description="没有符合条件的 execution。" />}
        <div className="task-execution-list">
          {query.data?.items.map((item) => (
            <Card key={item.executionId} className="result-card">
              <Flex justify="space-between" gap={16} wrap>
                <div>
                  <Typography.Title level={4}>{item.contractName}</Typography.Title>
                  <Typography.Text type="secondary">
                    {item.taskId} · {item.executionId}
                  </Typography.Text>
                  <Typography.Paragraph>
                    {new Date(item.createdAt).toLocaleString("zh-CN")} · 当前阶段 {item.currentStage}
                  </Typography.Paragraph>
                  <Space wrap>
                    <Tag color={item.status === "FAILED" ? "error" : "processing"}>
                      {item.status}
                    </Tag>
                    <Tag>{item.modelProfile.displayName}</Tag>
                    <Tag>{item.modelProfile.providerType}</Tag>
                    <Tag>{item.modelProfile.configVersion}</Tag>
                  </Space>
                </div>
                <div className="task-item-actions">
                  {item.resultStatistics && (
                    <Space wrap>
                      <Tag color="success">PASS {item.resultStatistics.passCount}</Tag>
                      <Tag color="error">ERROR {item.resultStatistics.errorCount}</Tag>
                      <Tag color="warning">WARNING {item.resultStatistics.warningCount}</Tag>
                      <Tag color="processing">
                        NOT_CONCLUDED {item.resultStatistics.notConcludedCount}
                      </Tag>
                    </Space>
                  )}
                  <Link to={targetUrl(item)}>
                    <Button>查看 execution</Button>
                  </Link>
                </div>
              </Flex>
            </Card>
          ))}
        </div>

        {query.data && query.data.totalElements > PAGE_SIZE && (
          <Pagination
            current={page + 1}
            pageSize={PAGE_SIZE}
            total={query.data.totalElements}
            showSizeChanger={false}
            onChange={(nextPage) =>
              updateSearch({
                ...filters,
                page: nextPage - 1
              })
            }
          />
        )}
      </section>
    </main>
  );
}
