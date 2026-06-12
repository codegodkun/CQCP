import { Card, Col, Descriptions, List, Row, Tag, Typography } from "antd";

const summary = {
  taskId: "task-001",
  executionId: "execution-003",
  contractName: "企鹅岛项目三标段土建总承包工程合同",
  stage: "REVIEWING_MODEL",
  modelDisplayName: "Local mock fallback",
  plannedPointCount: 9,
  passCount: 5,
  errorCount: 1,
  warningCount: 1,
  notConcludedCount: 1,
  skippedCount: 1,
  diagnosticCodes: ["SYS_MODEL_TIMEOUT", "SYS_PARSE_LOW_CONFIDENCE"],
  pointResults: [
    {
      reviewPointCode: "PARTY_A_NAME_CONSISTENCY",
      pointStatus: "PASS",
      businessMessage: "甲方名称一致。"
    },
    {
      reviewPointCode: "PARTY_B_NAME_CONSISTENCY",
      pointStatus: "NOT_CONCLUDED",
      businessMessage: "模型暂时不可用，未形成正式结论。"
    },
    {
      reviewPointCode: "TAX_AMOUNT_FORMULA_CONSISTENCY",
      pointStatus: "WARNING",
      businessMessage: "税额公式存在弱不一致，请人工复核。"
    }
  ],
  stageLogs: [
    {
      stageName: "PARSING",
      eventType: "COMPLETED",
      status: "SUCCESS",
      businessReason: "文档解析完成。"
    },
    {
      stageName: "REVIEWING_MODEL",
      eventType: "FAILED",
      status: "PARTIAL_SUCCESS",
      businessReason: "模型调用超时。"
    }
  ]
};

const statusColors: Record<string, string> = {
  PASS: "success",
  ERROR: "error",
  WARNING: "warning",
  NOT_CONCLUDED: "default",
  SKIPPED: "processing"
};

export function AdminTaskDiagnosticsPreview() {
  return (
    <section className="content-panel" aria-label="TASK-016 Admin Preview">
      <Typography.Title level={3}>TASK-016 管理台诊断摘要预览</Typography.Title>
      <Typography.Paragraph>
        该区域只验证最小展示契约：摘要、点级状态、阶段日志和诊断码。
        不展示完整 prompt、raw output、endpoint secret 或 stack trace。
      </Typography.Paragraph>

      <Card title="任务顶部概览" className="preview-card">
        <Descriptions column={2} size="small">
          <Descriptions.Item label="taskId">{summary.taskId}</Descriptions.Item>
          <Descriptions.Item label="executionId">{summary.executionId}</Descriptions.Item>
          <Descriptions.Item label="合同名称">{summary.contractName}</Descriptions.Item>
          <Descriptions.Item label="当前 stage">{summary.stage}</Descriptions.Item>
          <Descriptions.Item label="审核模型">{summary.modelDisplayName}</Descriptions.Item>
          <Descriptions.Item label="计划审核点">{summary.plannedPointCount}</Descriptions.Item>
        </Descriptions>
      </Card>

      <Row gutter={[16, 16]} className="preview-grid">
        <Col xs={24} md={12}>
          <Card title="点级摘要计数" className="preview-card">
            <div className="summary-tags">
              <Tag color="green">PASS {summary.passCount}</Tag>
              <Tag color="red">ERROR {summary.errorCount}</Tag>
              <Tag color="orange">WARNING {summary.warningCount}</Tag>
              <Tag>NOT_CONCLUDED {summary.notConcludedCount}</Tag>
              <Tag color="blue">SKIPPED {summary.skippedCount}</Tag>
            </div>
          </Card>
        </Col>
        <Col xs={24} md={12}>
          <Card title="摘要级诊断码" className="preview-card">
            <div className="summary-tags">
              {summary.diagnosticCodes.map((code) => (
                <Tag key={code}>{code}</Tag>
              ))}
            </div>
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]} className="preview-grid">
        <Col xs={24} lg={12}>
          <Card title="点级结果" className="preview-card">
            <List
              dataSource={summary.pointResults}
              renderItem={(item) => (
                <List.Item>
                  <List.Item.Meta
                    title={
                      <span>
                        {item.reviewPointCode}{" "}
                        <Tag color={statusColors[item.pointStatus]}>{item.pointStatus}</Tag>
                      </span>
                    }
                    description={item.businessMessage}
                  />
                </List.Item>
              )}
            />
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card title="阶段日志摘要" className="preview-card">
            <List
              dataSource={summary.stageLogs}
              renderItem={(item) => (
                <List.Item>
                  <List.Item.Meta
                    title={`${item.stageName} / ${item.eventType} / ${item.status}`}
                    description={item.businessReason}
                  />
                </List.Item>
              )}
            />
          </Card>
        </Col>
      </Row>
    </section>
  );
}
