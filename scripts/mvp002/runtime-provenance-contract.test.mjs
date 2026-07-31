import assert from "node:assert/strict";
import test from "node:test";

import {
  REDACTED_COMPOSE_VALUE,
  assertRedactedResolvedComposeConfig,
  isSensitiveEnvironmentKey,
  redactResolvedComposeConfig
} from "./runtime-provenance-contract.mjs";

test("resolved Compose evidence redacts credentials but preserves Secret References", () => {
  const source = Buffer.from([
    "services:",
    "  api-server:",
    "    environment:",
    "      CQCP_ADMIN_API_TOKEN: admin-sentinel",
    "      CQCP_ADMIN_READONLY_TOKEN: readonly-sentinel",
    "      CQCP_DB_PASSWORD: database-sentinel",
    "      CQCP_MODEL_DEEPSEEK_API_KEY: provider-sentinel",
    "      CQCP_DEEPSEEK_SECRET_REF: env:CQCP_MODEL_DEEPSEEK_API_KEY",
    "      CQCP_DB_USERNAME: cqcp",
    ""
  ].join("\n"));

  const redacted = redactResolvedComposeConfig(source).toString("utf8");

  assert.equal(redacted.includes("admin-sentinel"), false);
  assert.equal(redacted.includes("readonly-sentinel"), false);
  assert.equal(redacted.includes("database-sentinel"), false);
  assert.equal(redacted.includes("provider-sentinel"), false);
  assert.match(redacted, new RegExp(`CQCP_ADMIN_API_TOKEN: ${REDACTED_COMPOSE_VALUE}`));
  assert.match(
    redacted,
    /CQCP_DEEPSEEK_SECRET_REF: env:CQCP_MODEL_DEEPSEEK_API_KEY/
  );
  assert.match(redacted, /CQCP_DB_USERNAME: cqcp/);
  assert.equal(assertRedactedResolvedComposeConfig(redacted), 4);
});

test("resolved Compose evidence fails closed if one credential field is plaintext", () => {
  assert.throws(
    () =>
      assertRedactedResolvedComposeConfig(
        "environment:\n  CQCP_ADMIN_API_TOKEN: still-plaintext\n"
      ),
    /persisted sensitive value/
  );
});

test("credential classifier does not confuse a server-side Secret Reference with a Secret", () => {
  assert.equal(isSensitiveEnvironmentKey("CQCP_DB_PASSWORD"), true);
  assert.equal(isSensitiveEnvironmentKey("CQCP_DEEPSEEK_SECRET_REF"), false);
  assert.equal(isSensitiveEnvironmentKey("CQCP_DEEPSEEK_SECRET_REFERENCE"), false);
});
