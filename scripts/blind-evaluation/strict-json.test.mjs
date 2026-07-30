import assert from "node:assert/strict";
import test from "node:test";

import {
  parseJsonBytesRejectDuplicateKeys,
  parseJsonRejectDuplicateKeys,
} from "./strict-json.mjs";

test("strict JSON parses nested valid values", () => {
  assert.deepEqual(
    parseJsonRejectDuplicateKeys(
      '{"decision":"GO","checks":[{"status":"PASS"}],"count":0}',
    ),
    {
      decision: "GO",
      checks: [{ status: "PASS" }],
      count: 0,
    },
  );
});

test("strict JSON rejects duplicate keys at every nesting level", () => {
  for (const value of [
    '{"decision":"NO_GO","decision":"GO"}',
    '{"checks":[{"status":"FAIL","status":"PASS"}]}',
    '{"findings":[{"severity":"P1","severity":"P2"}]}',
  ]) {
    assert.throws(
      () => parseJsonRejectDuplicateKeys(value),
      /STRICT_JSON_DUPLICATE_KEY/,
    );
  }
});

test("strict JSON treats escaped-equivalent keys as duplicates", () => {
  assert.throws(
    () =>
      parseJsonBytesRejectDuplicateKeys(
        Buffer.from('{"decision":"NO_GO","\\u0064ecision":"GO"}', "utf8"),
      ),
    /STRICT_JSON_DUPLICATE_KEY/,
  );
});

test("strict JSON rejects invalid syntax, non-finite numbers and trailing bytes", () => {
  for (const value of [
    '{"a":1,}',
    '{"a":1}x',
    '{"a":1e400}',
    '{"a":"\\u00zz"}',
  ]) {
    assert.throws(
      () => parseJsonRejectDuplicateKeys(value),
      /STRICT_JSON_INVALID/,
    );
  }
});
