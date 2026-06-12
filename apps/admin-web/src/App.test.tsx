import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { App } from "./App";

describe("App scaffold", () => {
  it("renders scaffold-only message", () => {
    render(<App />);
    expect(screen.getByText("TASK-006 Scaffold Only")).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "TASK-016 管理台诊断摘要预览" })
    ).toBeInTheDocument();
    expect(screen.getByText("PASS 5")).toBeInTheDocument();
    expect(screen.getByText("SYS_MODEL_TIMEOUT")).toBeInTheDocument();
    expect(screen.getByText("模型暂时不可用，未形成正式结论。")).toBeInTheDocument();
    expect(screen.queryByText("FULL_PROMPT")).not.toBeInTheDocument();
  });
});
