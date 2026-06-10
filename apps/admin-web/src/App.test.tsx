import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { App } from "./App";

describe("App scaffold", () => {
  it("renders scaffold-only message", () => {
    render(<App />);
    expect(screen.getByText("TASK-006 Scaffold Only")).toBeInTheDocument();
  });
});

