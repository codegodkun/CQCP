import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import {
  readStableBytesDirectChildren,
  readStableJsonDirectChild,
} from "./stable-capability-file.mjs";

const withRoot = (body) => {
  const root = fs.mkdtempSync(
    path.join(os.tmpdir(), "cqcp-stable-capability-"),
  );
  try {
    body(root);
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
};

test("reads a strict UTF-8 direct-child JSON capability", () => {
  withRoot((root) => {
    const capabilityRoot = path.join(root, "capabilities");
    fs.mkdirSync(capabilityRoot);
    fs.writeFileSync(
      path.join(capabilityRoot, "authorization.json"),
      "{\"authorized\":true}\n",
      "utf8",
    );
    const result = readStableJsonDirectChild({
      repoRoot: root,
      relativePath: "capabilities/authorization.json",
      requiredRoot: "capabilities/",
    });
    assert.deepEqual(result.value, { authorized: true });
  });
});

test("rejects nested paths, BOM and malformed UTF-8", () => {
  withRoot((root) => {
    const capabilityRoot = path.join(root, "capabilities");
    fs.mkdirSync(path.join(capabilityRoot, "nested"), {
      recursive: true,
    });
    fs.writeFileSync(
      path.join(capabilityRoot, "nested", "authorization.json"),
      "{}",
    );
    assert.throws(
      () =>
        readStableJsonDirectChild({
          repoRoot: root,
          relativePath: "capabilities/nested/authorization.json",
          requiredRoot: "capabilities/",
        }),
      /CAPABILITY_PATH_INVALID/,
    );
    fs.writeFileSync(
      path.join(capabilityRoot, "authorization.json"),
      Buffer.from([0xef, 0xbb, 0xbf, 0x7b, 0x7d]),
    );
    assert.throws(
      () =>
        readStableJsonDirectChild({
          repoRoot: root,
          relativePath: "capabilities/authorization.json",
          requiredRoot: "capabilities/",
        }),
      /CAPABILITY_BOM_REJECTED/,
    );
    fs.writeFileSync(
      path.join(capabilityRoot, "authorization.json"),
      Buffer.from([0xc3, 0x28]),
    );
    assert.throws(
      () =>
        readStableJsonDirectChild({
          repoRoot: root,
          relativePath: "capabilities/authorization.json",
          requiredRoot: "capabilities/",
        }),
      /CAPABILITY_UTF8_INVALID/,
    );
  });
});

test("rejects duplicate capability keys, including escaped equivalents", () => {
  withRoot((root) => {
    const capabilityRoot = path.join(root, "capabilities");
    fs.mkdirSync(capabilityRoot);
    const capabilityPath = path.join(
      capabilityRoot,
      "authorization.json",
    );
    fs.writeFileSync(
      capabilityPath,
      '{"decision":"DENY","decision":"ALLOW"}',
      "utf8",
    );
    assert.throws(
      () =>
        readStableJsonDirectChild({
          repoRoot: root,
          relativePath: "capabilities/authorization.json",
          requiredRoot: "capabilities/",
        }),
      /CAPABILITY_JSON_INVALID/,
    );
    fs.writeFileSync(
      capabilityPath,
      '{"decision":"DENY","dec\\u0069sion":"ALLOW"}',
      "utf8",
    );
    assert.throws(
      () =>
        readStableJsonDirectChild({
          repoRoot: root,
          relativePath: "capabilities/authorization.json",
          requiredRoot: "capabilities/",
        }),
      /CAPABILITY_JSON_INVALID/,
    );
  });
});

test("rejects a capability root replaced by a junction or directory symlink", () => {
  withRoot((root) => {
    const target = path.join(root, "relocated");
    const capabilityRoot = path.join(root, "capabilities");
    fs.mkdirSync(target);
    fs.writeFileSync(
      path.join(target, "authorization.json"),
      "{}",
    );
    fs.symlinkSync(
      target,
      capabilityRoot,
      process.platform === "win32" ? "junction" : "dir",
    );
    assert.throws(
      () =>
        readStableJsonDirectChild({
          repoRoot: root,
          relativePath: "capabilities/authorization.json",
          requiredRoot: "capabilities/",
        }),
      /CAPABILITY_STABLE_OPEN_FAILED/,
    );
  });
});

test("rejects a final symlink and an oversized direct child", () => {
  withRoot((root) => {
    const capabilityRoot = path.join(root, "capabilities");
    fs.mkdirSync(capabilityRoot);
    const real = path.join(root, "outside.json");
    fs.writeFileSync(real, "{}");
    const linked = path.join(capabilityRoot, "authorization.json");
    fs.symlinkSync(real, linked, "file");
    assert.throws(
      () =>
        readStableJsonDirectChild({
          repoRoot: root,
          relativePath: "capabilities/authorization.json",
          requiredRoot: "capabilities/",
        }),
      /CAPABILITY_STABLE_OPEN_FAILED/,
    );
    fs.rmSync(linked);
    fs.writeFileSync(linked, "x".repeat(65_537));
    assert.throws(
      () =>
        readStableJsonDirectChild({
          repoRoot: root,
          relativePath: "capabilities/authorization.json",
          requiredRoot: "capabilities/",
        }),
      /CAPABILITY_STABLE_OPEN_FAILED/,
    );
  });
});

test("streams a large capability batch through stdin", () => {
  withRoot((root) => {
    const capabilityRoot = path.join(root, "capabilities");
    fs.mkdirSync(capabilityRoot);
    const operations = Array.from({ length: 256 }, (_, index) => {
      const childName = `capability-${index.toString().padStart(3, "0")}.json`;
      fs.writeFileSync(
        path.join(capabilityRoot, childName),
        `{"index":${index}}\n`,
        "utf8",
      );
      return {
        repoRoot: root,
        relativePath: `capabilities/${childName}`,
        requiredRoot: "capabilities/",
        maxBytes: 1024,
      };
    });
    const results = readStableBytesDirectChildren(operations);
    assert.equal(results.length, operations.length);
    assert.equal(
      results.at(-1).bytes.toString("utf8"),
      '{"index":255}\n',
    );
  });
});
