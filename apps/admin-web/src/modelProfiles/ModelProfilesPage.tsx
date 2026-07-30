import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import {
  Alert,
  Button,
  Card,
  Col,
  Form,
  Input,
  InputNumber,
  Row,
  Select,
  Space,
  Tag,
  Typography
} from "antd";

import {
  createModelProfile,
  createModelProfileVersion,
  fetchModelProfiles,
  ModelProfileApiError,
  testModelProfileConnectivity
} from "./api";
import type { ModelProfileView, ModelProfileWriteInput } from "./types";

const READINESS_LABEL: Record<ModelProfileView["readiness"], string> = {
  READY: "已就绪",
  SECRET_MISSING: "Secret 未配置",
  NOT_TESTED: "尚未连通测试",
  CONNECTIVITY_FAILED: "连通测试失败",
  READY_FOR_EVALUATION_CONFIG: "评测配置就绪（未发布）"
};

export function ModelProfilesPage() {
  const [form] = Form.useForm<ModelProfileWriteInput>();
  const queryClient = useQueryClient();
  const [enteredAdminToken, setEnteredAdminToken] = useState("");
  const [adminToken, setAdminToken] = useState("");
  const [authEpoch, setAuthEpoch] = useState(0);
  const profiles = useQuery({
    queryKey: ["model-profiles", authEpoch],
    queryFn: () => fetchModelProfiles(adminToken),
    enabled: adminToken.length > 0,
    retry: false
  });
  const authenticated =
    adminToken.length > 0 && profiles.isSuccess && profiles.error == null;
  useEffect(() => {
    if (
      profiles.error instanceof ModelProfileApiError &&
      (profiles.error.status === 401 || profiles.error.status === 403)
    ) {
      setAdminToken("");
    }
  }, [profiles.error]);
  const revokeOnAuthenticationFailure = (error: unknown) => {
    if (
      error instanceof ModelProfileApiError &&
      (error.status === 401 || error.status === 403)
    ) {
      setAdminToken("");
    }
  };
  const save = useMutation({
    mutationFn: async (input: ModelProfileWriteInput) => {
      const existing = profiles.data?.items.some(
        (item) => item.profileCode === input.profileCode
      );
      return existing
        ? createModelProfileVersion(input.profileCode, input, adminToken)
        : createModelProfile(input, adminToken);
    },
    onSuccess: async () => {
      form.resetFields();
      await queryClient.invalidateQueries({ queryKey: ["model-profiles"] });
    },
    onError: revokeOnAuthenticationFailure
  });
  const connectivity = useMutation({
    mutationFn: (profileCode: string) =>
      testModelProfileConnectivity(profileCode, adminToken),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["model-profiles"] });
    },
    onError: revokeOnAuthenticationFailure
  });

  return (
    <main className="app-shell">
      <section className="hero-panel model-profile-panel">
        <Tag color="cyan">ADR-018</Tag>
        <Typography.Title level={2}>Model Profile</Typography.Title>
        <Typography.Paragraph>
          PUBLIC profile 只创建为 EVALUATION、disabled/unbound。页面不接收或读取 API KEY，
          只保存部署侧 Secret Reference。
        </Typography.Paragraph>
        <Alert
          type="info"
          showIcon
          message="配置就绪不等于 execution 已发布，也不授权合同内容外发。"
        />
        <Space.Compact block className="model-profile-auth">
          <Input.Password
            aria-label="Admin Access Token"
            autoComplete="off"
            value={enteredAdminToken}
            onChange={(event) => setEnteredAdminToken(event.target.value)}
            placeholder="Admin Access Token（仅保存在当前页面内存）"
          />
          <Button
            type="primary"
            disabled={!enteredAdminToken}
            onClick={() => {
              setAdminToken(enteredAdminToken);
              setEnteredAdminToken("");
              setAuthEpoch((value) => value + 1);
            }}
          >
            验证并进入
          </Button>
        </Space.Compact>
      </section>

      <Row gutter={[24, 24]} className="model-profile-grid">
        <Col xs={24} xl={14}>
          <section className="content-panel model-profile-panel">
            <Typography.Title level={4}>当前配置</Typography.Title>
            {profiles.error && (
              <Alert type="error" showIcon message="Model Profile 查询失败。" />
            )}
            <div className="model-profile-list">
              {profiles.data?.items.map((profile) => (
                <Card key={profile.profileCode} className="result-card">
                  <Space direction="vertical" size="small">
                    <Space wrap>
                      <Typography.Text strong>{profile.displayName}</Typography.Text>
                      <Tag>{profile.profileCode}</Tag>
                      <Tag color={profile.enabled ? "success" : "default"}>
                        {profile.enabled ? "enabled" : "disabled"}
                      </Tag>
                    </Space>
                    <Typography.Text type="secondary">
                      {profile.modelName} · {profile.providerType} · {profile.endpointAlias}
                    </Typography.Text>
                    <Typography.Text type="secondary">
                      {profile.configVersion}
                    </Typography.Text>
                    <Space wrap>
                      <Tag color={profile.secretConfigured ? "success" : "warning"}>
                        Secret {profile.secretConfigured ? "已配置" : "未配置"}
                      </Tag>
                      <Tag>{READINESS_LABEL[profile.readiness]}</Tag>
                      <Tag>{profile.usageScope}</Tag>
                    </Space>
                    {profile.latestConnectivityTest && (
                      <Typography.Text type="secondary">
                        最近测试 {profile.latestConnectivityTest.status} ·{" "}
                        {profile.latestConnectivityTest.durationMs} ms
                      </Typography.Text>
                    )}
                    {profile.providerType === "PUBLIC_OPENAI_COMPATIBLE" && (
                      <Button
                        loading={
                          connectivity.isPending &&
                          connectivity.variables === profile.profileCode
                        }
                        onClick={() => connectivity.mutate(profile.profileCode)}
                      >
                        连通测试
                      </Button>
                    )}
                  </Space>
                </Card>
              ))}
            </div>
          </section>
        </Col>

        <Col xs={24} xl={10}>
          <section className="content-panel model-profile-panel">
            <Typography.Title level={4}>创建或发布新 config version</Typography.Title>
            <Form<ModelProfileWriteInput>
              form={form}
              layout="vertical"
              disabled={!authenticated}
              initialValues={{
                providerType: "PUBLIC_OPENAI_COMPATIBLE",
                endpointAlias: "deepseek-official",
                usageScope: "EVALUATION",
                modelName: "deepseek-v4-pro",
                secretRef: "env:CQCP_MODEL_DEEPSEEK_API_KEY",
                timeoutSeconds: 30,
                retryCount: 0
              }}
              onFinish={(values) => {
                if (!authenticated) return;
                save.mutate(values);
              }}
            >
              {!authenticated && (
                <Alert
                  type="warning"
                  showIcon
                  message="请先验证 Admin Access Token，再执行管理操作。"
                />
              )}
              <Form.Item
                label="Profile Code"
                name="profileCode"
                rules={[{ required: true, pattern: /^[A-Z][A-Z0-9_]{2,63}$/ }]}
              >
                <Input placeholder="DEEPSEEK_EVAL" />
              </Form.Item>
              <Form.Item label="显示名称" name="displayName" rules={[{ required: true }]}>
                <Input maxLength={255} />
              </Form.Item>
              <Form.Item label="Provider" name="providerType">
                <Input disabled />
              </Form.Item>
              <Form.Item label="Usage Scope" name="usageScope">
                <Input disabled />
              </Form.Item>
              <Form.Item label="Endpoint Alias" name="endpointAlias">
                <Select
                  options={[
                    { value: "deepseek-official", label: "deepseek-official" }
                  ]}
                />
              </Form.Item>
              <Form.Item label="Model" name="modelName" rules={[{ required: true }]}>
                <Select
                  options={[
                    { value: "deepseek-v4-pro", label: "deepseek-v4-pro" },
                    { value: "deepseek-v4-flash", label: "deepseek-v4-flash" }
                  ]}
                />
              </Form.Item>
              <Form.Item
                label="Secret Reference"
                name="secretRef"
                extra="由服务端限定为 provider-specific Secret Reference；这里不是 KEY 输入框。"
                rules={[
                  {
                    required: true,
                    pattern: /^(env:[A-Z][A-Z0-9_]{2,63}|file:.+)$/
                  }
                ]}
              >
                <Select
                  options={[
                    {
                      value: "env:CQCP_MODEL_DEEPSEEK_API_KEY",
                      label: "env:CQCP_MODEL_DEEPSEEK_API_KEY"
                    }
                  ]}
                />
              </Form.Item>
              <Form.Item label="Timeout（秒）" name="timeoutSeconds">
                <InputNumber min={1} max={120} />
              </Form.Item>
              <Form.Item label="Retry Count" name="retryCount">
                <InputNumber min={0} max={3} />
              </Form.Item>
              {save.isError && (
                <Alert
                  type="error"
                  showIcon
                  message="保存失败，请核对字段和服务端 allowlist。"
                />
              )}
              <Button type="primary" htmlType="submit" loading={save.isPending}>
                保存为新 config version
              </Button>
            </Form>
          </section>
        </Col>
      </Row>
    </main>
  );
}
