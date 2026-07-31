import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";

import {
  assertCoreChangedPaths,
  assertCoreContentBoundary,
  assertCoreImportClosure,
  coreScriptPaths,
  isCoreSourcePath,
} from "./core-subject.mjs";

const repoRoot = path.resolve(import.meta.dirname, "../..");

test("Core scope admits product paths and rejects Provider/audit/evidence paths", () => {
  assert.equal(isCoreSourcePath(".gitattributes"), true);
  assert.equal(
    isCoreSourcePath(
      "apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/MinimalReviewEngine.java",
    ),
    true,
  );
  assert.equal(
    isCoreSourcePath(
      "apps/api-server/src/test/java/com/cqcp/apiserver/reviewengine/ProviderCanonicalIdentityGoldenTest.java",
    ),
    false,
  );
  assert.equal(
    isCoreSourcePath(
      "scripts/blind-evaluation/provider-contract-audit-contract.mjs",
    ),
    false,
  );
  assert.equal(
    isCoreSourcePath("scripts/mvp002/run-verification.ps1"),
    false,
  );
  assert.equal(
    isCoreSourcePath("outputs/task-eval-002/freeze-manifest.json"),
    false,
  );
  assert.equal(
    isCoreSourcePath("apps/admin-web/src/provider/InnocentRename.ts"),
    false,
  );
});

test("Core changed-path gate fails closed on one excluded path", () => {
  assert.throws(
    () =>
      assertCoreChangedPaths([
        "apps/admin-web/src/App.tsx",
        "tasks/active/TASK-MODEL-002-controlled-deepseek-provider.md",
      ]),
    /non-Core paths/,
  );
});

test("Core import closure rejects a Provider dependency", () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-core-scope-"));
  try {
    const corePath = path.join(root, "scripts/mvp002/core-subject.mjs");
    fs.mkdirSync(path.dirname(corePath), { recursive: true });
    fs.writeFileSync(
      corePath,
      'import "../blind-evaluation/provider-contract-audit-contract.mjs";\n',
      "utf8",
    );
    assert.throws(
      () =>
        assertCoreImportClosure(root, ["scripts/mvp002/core-subject.mjs"]),
      /excluded Provider\/audit code/,
    );
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});

test("Core import closure rejects a TypeScript Provider dependency", () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-core-ts-"));
  try {
    const corePath = path.join(root, "apps/admin-web/src/App.tsx");
    const providerPath = path.join(
      root,
      "apps/admin-web/src/provider/client.ts",
    );
    fs.mkdirSync(path.dirname(providerPath), { recursive: true });
    fs.writeFileSync(corePath, 'import "./provider/client";\n', "utf8");
    fs.writeFileSync(providerPath, "export const call = () => 1;\n", "utf8");
    assert.throws(
      () => assertCoreImportClosure(root, ["apps/admin-web/src/App.tsx"]),
      /excluded Provider\/audit code/,
    );
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});

test("Core import closure rejects a Java Provider dependency", () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-core-java-"));
  try {
    const corePath = path.join(
      root,
      "apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/MinimalReviewEngine.java",
    );
    const providerPath = path.join(
      root,
      "apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/DeepSeekProviderAdapter.java",
    );
    fs.mkdirSync(path.dirname(corePath), { recursive: true });
    fs.writeFileSync(
      corePath,
      [
        "package com.cqcp.apiserver.reviewengine;",
        "import com.cqcp.apiserver.reviewengine.DeepSeekProviderAdapter;",
      ].join("\n"),
      "utf8",
    );
    fs.writeFileSync(
      providerPath,
      "package com.cqcp.apiserver.reviewengine;\n",
      "utf8",
    );
    assert.throws(
      () =>
        assertCoreImportClosure(root, [
          "apps/api-server/src/main/java/com/cqcp/apiserver/reviewengine/MinimalReviewEngine.java",
        ]),
      /excluded Provider\/audit code/,
    );
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});

test("Core content boundary rejects Provider attempt contract leakage", () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "cqcp-core-content-"));
  try {
    const architecturePath = path.join(root, "docs/ARCHITECTURE.md");
    fs.mkdirSync(path.dirname(architecturePath), { recursive: true });
    fs.writeFileSync(
      architecturePath,
      "provider-attempt-outcome-contract-v1.json\n",
      "utf8",
    );
    assert.throws(
      () =>
        assertCoreContentBoundary(root, ["docs/ARCHITECTURE.md"]),
      /excluded Provider contract marker/,
    );
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});

test("Current Core script allowlist has a Provider-free import closure", () => {
  const closure = assertCoreImportClosure(repoRoot, coreScriptPaths());
  assert.ok(closure.includes("scripts/mvp002/core-subject.mjs"));
  assert.equal(
    closure.some((filePath) =>
      /(?:provider-contract-|standing-egress|cc-audit)/.test(filePath),
    ),
    false,
  );
  assert.deepEqual(
    assertCoreContentBoundary(repoRoot, ["docs/ARCHITECTURE.md"]),
    ["docs/ARCHITECTURE.md"],
  );
});
