import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Alert, Button, Descriptions, Space, Tag, Typography } from "antd";
import { Link, useLocation, useNavigate } from "react-router-dom";

import {
  fetchReviewExecutionStatus,
  parseStatusRoute,
  safeResultPath
} from "./api";
import { StatusQueryError } from "./types";
import type {
  ExecutionStage,
  PublicTaskStatus,
  ReviewExecutionStatus,
  SupersededReason
} from "./types";

const POLL_INTERVAL_MS = 1000;

const STATUS_LABEL: Record<PublicTaskStatus, string> = {
  QUEUED: "排队中",
  PROCESSING: "审核中",
  SUCCESS: "审核成功",
  PARTIAL_SUCCESS: "部分审核成功",
  FAILED: "审核失败"
};

const STAGE_LABEL: Record<ExecutionStage, string> = {
  CREATED: "已创建",
  QUEUED: "排队中",
  PARSING: "解析合同",
  INDEXING: "建立索引",
  PLANNING: "规划审核",
  BUILDING_EVIDENCE: "构建证据",
  REVIEWING_RULES: "规则审核",
  REVIEWING_MODEL: "模型审核",
  COMPOSING: "汇总结果",
  SUCCESS: "审核成功",
  PARTIAL_SUCCESS: "部分审核成功",
  FAILED: "审核失败",
  CANCELLED: "已取消"
};

const SUPERSEDED_LABEL: Record<SupersededReason, string> = {
  TYPE_CORRECTION: "合同类型已修正",
  BUDGET_UPGRADE: "审核预算已升级",
  MANUAL_RERUN: "已人工重新发起",
  RULESET_RERUN: "规则集已更新并重跑",
  MODEL_UPGRADE: "模型已升级并重跑",
  PARSER_UPGRADE: "解析器已升级并重跑",
  ADMIN_RECOVERY: "管理员已恢复执行"
};

type PageErrorKind = "NOT_FOUND" | "HTTP" | "NETWORK" | "INVALID_DATA" | "INVALID_RESULT_URL";

function errorMessage(kind: PageErrorKind): string {
  switch (kind) {
    case "NOT_FOUND":
      return "未找到指定审核任务或执行，请确认页面地址。";
    case "HTTP":
      return "状态查询暂不可用，请稍后重新查询。";
    case "NETWORK":
      return "无法连接审核服务，请检查网络后重新查询。";
    case "INVALID_RESULT_URL":
      return "结果地址校验失败，请稍后重新查询。";
    default:
      return "状态数据暂不可用，请重新查询。";
  }
}

function toErrorKind(error: unknown): PageErrorKind {
  return error instanceof StatusQueryError ? error.kind : "NETWORK";
}

export function ReviewExecutionStatusPage() {
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const identity = useMemo(() => parseStatusRoute(pathname), [pathname]);
  const [statusData, setStatusData] = useState<ReviewExecutionStatus | null>(null);
  const [pageError, setPageError] = useState<PageErrorKind | null>(null);
  const [loading, setLoading] = useState(false);
  const generationRef = useRef(0);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const activeRequestRef = useRef<AbortController | null>(null);
  const navigateOnceRef = useRef<string | null>(null);
  const requestRef = useRef<(() => void) | null>(null);

  const clearTimer = useCallback(() => {
    if (timerRef.current !== null) {
      clearTimeout(timerRef.current);
      timerRef.current = null;
    }
  }, []);

  useEffect(() => {
    const generation = ++generationRef.current;
    let disposed = false;
    clearTimer();
    activeRequestRef.current?.abort();
    activeRequestRef.current = null;
    navigateOnceRef.current = null;
    setStatusData(null);
    setPageError(null);
    setLoading(false);

    if (!identity) {
      requestRef.current = null;
      return () => {
        disposed = true;
        generationRef.current += 1;
      };
    }

    const request = () => {
      if (
        disposed ||
        generationRef.current !== generation ||
        activeRequestRef.current !== null
      ) {
        return;
      }
      clearTimer();
      const controller = new AbortController();
      activeRequestRef.current = controller;
      setLoading(true);
      setPageError(null);

      void fetchReviewExecutionStatus(
        identity.taskId,
        identity.executionId,
        controller.signal
      )
        .then((nextStatus) => {
          if (disposed || generationRef.current !== generation) return;
          if (
            nextStatus.taskId !== identity.taskId ||
            nextStatus.executionId !== identity.executionId
          ) {
            setStatusData(null);
            setPageError("INVALID_DATA");
            return;
          }

          setStatusData(nextStatus);
          if (!nextStatus.terminal) {
            timerRef.current = setTimeout(request, POLL_INTERVAL_MS);
            return;
          }

          if (nextStatus.status === "SUCCESS" || nextStatus.status === "PARTIAL_SUCCESS") {
            if (!nextStatus.snapshotAvailable) {
              setPageError("INVALID_DATA");
              return;
            }
            const safePath = safeResultPath(
              nextStatus.resultUrl,
              identity.taskId,
              identity.executionId
            );
            if (!safePath) {
              setPageError("INVALID_RESULT_URL");
              return;
            }
            if (navigateOnceRef.current !== identity.executionId) {
              navigateOnceRef.current = identity.executionId;
              navigate(safePath, { replace: true });
            }
          }
        })
        .catch((error: unknown) => {
          if (
            disposed ||
            generationRef.current !== generation ||
            (error instanceof DOMException && error.name === "AbortError")
          ) {
            return;
          }
          setStatusData(null);
          setPageError(toErrorKind(error));
        })
        .finally(() => {
          if (activeRequestRef.current === controller) {
            activeRequestRef.current = null;
            if (!disposed && generationRef.current === generation) {
              setLoading(false);
            }
          }
        });
    };

    requestRef.current = request;
    queueMicrotask(() => {
      if (!disposed && generationRef.current === generation) request();
    });

    return () => {
      disposed = true;
      generationRef.current += 1;
      clearTimer();
      controllerAbort(activeRequestRef.current);
      activeRequestRef.current = null;
      requestRef.current = null;
    };
  }, [clearTimer, identity, navigate]);

  if (!identity) {
    return (
      <div className="app-shell status-page">
        <section className="content-panel">
          <Alert type="error" showIcon message="状态页地址无效" />
        </section>
      </div>
    );
  }

  const canRetry = !loading && pageError !== null;
  return (
    <div className="app-shell status-page">
      <section className="hero-panel">
        <Tag color="cyan">审核执行</Tag>
        <Typography.Title level={2}>合同审核进度</Typography.Title>
        <Typography.Paragraph>
          页面会在当前请求完成后继续查询；终态或查询异常时自动停止。
        </Typography.Paragraph>
      </section>

      <section className="content-panel">
        <Descriptions bordered column={1} size="small">
          <Descriptions.Item label="Task ID">{identity.taskId}</Descriptions.Item>
          <Descriptions.Item label="Execution ID">{identity.executionId}</Descriptions.Item>
          <Descriptions.Item label="公开状态">
            {statusData ? STATUS_LABEL[statusData.status] : "查询中"}
          </Descriptions.Item>
          <Descriptions.Item label="当前阶段">
            {statusData?.currentStage ? STAGE_LABEL[statusData.currentStage] : "暂无阶段"}
          </Descriptions.Item>
          {statusData && (
            <>
              <Descriptions.Item label="创建时间">{statusData.createdAt}</Descriptions.Item>
              <Descriptions.Item label="更新时间">{statusData.updatedAt}</Descriptions.Item>
              <Descriptions.Item label="模型配置">
                {statusData.reviewModel.modelProfileCode} /{" "}
                {statusData.reviewModel.providerType} / {statusData.reviewModel.modelName} /{" "}
                {statusData.reviewModel.modelConfigVersion}
              </Descriptions.Item>
            </>
          )}
        </Descriptions>

        {loading && !statusData && (
          <Typography.Paragraph className="status-note">正在查询审核状态…</Typography.Paragraph>
        )}

        {statusData?.superseded && (
          <Alert
            className="status-alert"
            type="info"
            showIcon
            message="当前执行已被新的审核执行替代。"
            description={
              statusData.supersededReason
                ? SUPERSEDED_LABEL[statusData.supersededReason]
                : undefined
            }
          />
        )}

        {statusData?.status === "FAILED" && statusData.terminal && (
          <Alert
            className="status-alert"
            type="error"
            showIcon
            message="审核执行失败，请返回新建审核页面重新发起。"
            action={<Link to="/review/new">返回新建审核</Link>}
          />
        )}

        {pageError && (
          <Alert
            className="status-alert"
            type="warning"
            showIcon
            message={errorMessage(pageError)}
            action={
              <Button
                type="link"
                disabled={!canRetry}
                onClick={() => requestRef.current?.()}
              >
                重新查询
              </Button>
            }
          />
        )}

        {!pageError && statusData && !statusData.terminal && (
          <Space className="status-note">
            <Tag color="processing">自动更新中</Tag>
            <Typography.Text type="secondary">每次请求完成 1 秒后再次查询</Typography.Text>
          </Space>
        )}
      </section>
    </div>
  );
}

function controllerAbort(controller: AbortController | null) {
  controller?.abort();
}
