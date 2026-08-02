import { Alert, Button, Input, Space, Typography } from "antd";
import {
  type ReactNode,
  useMemo,
  useState
} from "react";

import {
  type ManagementAccess,
  ManagementAccessContext,
  useManagementAccess
} from "./managementAccess";

export function ManagementAccessProvider({
  children,
  initialToken = ""
}: {
  children: ReactNode;
  initialToken?: string;
}) {
  const [token, setToken] = useState(initialToken);
  const [epoch, setEpoch] = useState(0);
  const value = useMemo<ManagementAccess>(
    () => ({
      token,
      epoch,
      authenticate: (nextToken) => {
        setToken(nextToken);
        setEpoch((current) => current + 1);
      },
      clear: () => {
        setToken("");
        setEpoch((current) => current + 1);
      }
    }),
    [token, epoch]
  );

  return (
    <ManagementAccessContext.Provider value={value}>
      {children}
    </ManagementAccessContext.Provider>
  );
}

export function ManagementAccessControl({
  description = "任务清单与合同原文属于受控管理信息，请先验证访问凭据。"
}: {
  description?: string;
}) {
  const access = useManagementAccess();
  const [enteredToken, setEnteredToken] = useState("");

  if (access.token) {
    return (
      <Space wrap>
        <Alert type="success" showIcon message="管理访问已验证（仅保存在当前页面内存）" />
        <Button onClick={access.clear}>退出受控访问</Button>
      </Space>
    );
  }

  return (
    <Space direction="vertical" size="small" className="management-access-control">
      <Typography.Text>{description}</Typography.Text>
      <Space.Compact block>
        <Input.Password
          aria-label="Management Access Token"
          autoComplete="off"
          value={enteredToken}
          placeholder="Management Access Token（不写入 URL 或浏览器存储）"
          onChange={(event) => setEnteredToken(event.target.value)}
        />
        <Button
          type="primary"
          disabled={!enteredToken || /[\r\n]/.test(enteredToken)}
          onClick={() => {
            access.authenticate(enteredToken);
            setEnteredToken("");
          }}
        >
          验证并进入
        </Button>
      </Space.Compact>
    </Space>
  );
}
