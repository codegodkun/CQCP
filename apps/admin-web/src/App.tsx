import { Layout, List, Tag, Typography } from "antd";

import { AdminTaskDiagnosticsPreview } from "./AdminTaskDiagnosticsPreview";

const scaffoldScopes = [
  "普通结果页和管理台共用前端入口",
  "React Router / TanStack Query / Zustand 依赖基线",
  "仅保留纯脚手架文案，不接业务 API",
  "后续业务页面按 TASK 独立实现"
];

export function App() {
  return (
    <Layout className="app-shell">
      <section className="hero-panel">
        <Tag color="cyan">TASK-006 Scaffold Only</Tag>
        <Typography.Title level={1}>
          Contract Quality Control Platform
        </Typography.Title>
        <Typography.Paragraph>
          当前页面只用于确认前端脚手架已建立，不承载任何审核逻辑、
          任务流转或结果解释。
        </Typography.Paragraph>
      </section>

      <section className="content-panel">
        <Typography.Title level={3}>本轮脚手架边界</Typography.Title>
        <List
          dataSource={scaffoldScopes}
          renderItem={(item) => <List.Item>{item}</List.Item>}
        />
      </section>

      <AdminTaskDiagnosticsPreview />
    </Layout>
  );
}
