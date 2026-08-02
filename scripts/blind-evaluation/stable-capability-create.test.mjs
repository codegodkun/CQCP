import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";

import {
  createStableDirectoryWithFiles,
  writeStableBytesDirectChildren,
} from "./stable-capability-create.mjs";

const makeRepo = () => {
  const root = fs.mkdtempSync(
    path.join(os.tmpdir(), "cqcp-stable-create-"),
  );
  fs.mkdirSync(
    path.join(root, "outputs", "task-mvp-002", "egress", "executions"),
    { recursive: true },
  );
  return root;
};

test("creates a new direct-child directory and immutable files", () => {
  const root = makeRepo();
  try {
    const output = createStableDirectoryWithFiles({
      repoRoot: root,
      parentRoot: "outputs/task-mvp-002/egress/executions/",
      directoryName: "MVP002-EGRESS-test-safe-create",
      files: [
        { childName: "request.json", bytes: Buffer.from("{}\n") },
        {
          childName: "provider-call-set.json",
          bytes: Buffer.from('{"calls":[]}\n'),
        },
      ],
    });
    assert.equal(output.status, "CREATED");
    assert.deepEqual(
      output.files.map((entry) => entry.childName),
      ["request.json", "provider-call-set.json"],
    );
    assert.equal(
      fs.readFileSync(
        path.join(
          root,
          "outputs",
          "task-mvp-002",
          "egress",
          "executions",
          "MVP002-EGRESS-test-safe-create",
          "request.json",
        ),
        "utf8",
      ),
      "{}\n",
    );
    assert.throws(
      () =>
        createStableDirectoryWithFiles({
          repoRoot: root,
          parentRoot: "outputs/task-mvp-002/egress/executions/",
          directoryName: "MVP002-EGRESS-test-safe-create",
          files: [{ childName: "again.json", bytes: Buffer.from("{}") }],
        }),
      /CAPABILITY_STABLE_CREATE_FAILED/,
    );
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});

test("writes direct children beneath an existing stable root", () => {
  const root = makeRepo();
  try {
    fs.mkdirSync(
      path.join(
        root,
        "outputs",
        "task-mvp-002",
        "egress",
        "executions",
        "MVP002-EGRESS-existing-root",
      ),
    );
    const output = writeStableBytesDirectChildren({
      repoRoot: root,
      requiredRoot:
        "outputs/task-mvp-002/egress/executions/" +
        "MVP002-EGRESS-existing-root/",
      files: [
        { childName: "authorization.json", bytes: Buffer.from("{}\n") },
      ],
    });
    assert.equal(output.files[0].childName, "authorization.json");
    assert.throws(
      () =>
        writeStableBytesDirectChildren({
          repoRoot: root,
          requiredRoot:
            "outputs/task-mvp-002/egress/executions/" +
            "MVP002-EGRESS-existing-root/",
          files: [
            {
              childName: "authorization.json",
              bytes: Buffer.from('{"changed":true}\n'),
            },
          ],
        }),
      /CAPABILITY_STABLE_CREATE_FAILED/,
    );
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});

test("streams payloads larger than the Windows command-line limit", () => {
  const root = makeRepo();
  try {
    const bytes = Buffer.alloc(128 * 1024, 0x61);
    const output = writeStableBytesDirectChildren({
      repoRoot: root,
      requiredRoot: "outputs/task-mvp-002/egress/executions/",
      files: [{ childName: "large.bin", bytes }],
    });
    assert.equal(output.files[0].size, bytes.length);
    assert.deepEqual(
      fs.readFileSync(
        path.join(root, "outputs/task-mvp-002/egress/executions/large.bin"),
      ),
      bytes,
    );
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});

test("rejects a reparse or symlink root without writing outside the repo", () => {
  const root = makeRepo();
  const outside = fs.mkdtempSync(
    path.join(os.tmpdir(), "cqcp-stable-create-outside-"),
  );
  try {
    const alias = path.join(
      root,
      "outputs",
      "task-mvp-002",
      "egress",
      "executions",
      "MVP002-EGRESS-reparse-root",
    );
    fs.symlinkSync(
      outside,
      alias,
      process.platform === "win32" ? "junction" : "dir",
    );
    assert.throws(
      () =>
        writeStableBytesDirectChildren({
          repoRoot: root,
          requiredRoot:
            "outputs/task-mvp-002/egress/executions/" +
            "MVP002-EGRESS-reparse-root/",
          files: [{ childName: "claim.json", bytes: Buffer.from("{}\n") }],
        }),
      /CAPABILITY_STABLE_CREATE_FAILED/,
    );
    assert.equal(fs.existsSync(path.join(outside, "claim.json")), false);
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
    fs.rmSync(outside, { recursive: true, force: true });
  }
});

test("rejects unsafe roots, names, duplicate files, and oversized files", () => {
  const root = makeRepo();
  try {
    assert.throws(
      () =>
        writeStableBytesDirectChildren({
          repoRoot: root,
          requiredRoot: "outputs/task-mvp-002/egress/executions",
          files: [{ childName: "x", bytes: Buffer.from("x") }],
        }),
      /CAPABILITY_CREATE_ROOT_INVALID/,
    );
    assert.throws(
      () =>
        writeStableBytesDirectChildren({
          repoRoot: root,
          requiredRoot: "outputs/task-mvp-002/egress/executions/",
          files: [{ childName: "../x", bytes: Buffer.from("x") }],
        }),
      /CAPABILITY_CREATE_FILE_INVALID/,
    );
    assert.throws(
      () =>
        writeStableBytesDirectChildren({
          repoRoot: root,
          requiredRoot: "outputs/task-mvp-002/egress/executions/",
          files: [
            { childName: "x", bytes: Buffer.from("1") },
            { childName: "x", bytes: Buffer.from("2") },
          ],
        }),
      /CAPABILITY_CREATE_FILE_INVALID/,
    );
    assert.throws(
      () =>
        writeStableBytesDirectChildren({
          repoRoot: root,
          requiredRoot: "outputs/task-mvp-002/egress/executions/",
          files: [
            {
              childName: "large",
              bytes: Buffer.alloc(16 * 1024 * 1024 + 1),
            },
          ],
        }),
      /CAPABILITY_CREATE_FILE_INVALID/,
    );
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});
