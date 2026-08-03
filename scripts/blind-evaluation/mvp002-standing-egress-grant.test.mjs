import assert from "node:assert/strict";
import { dirname, resolve } from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

import {
  loadAndValidateMvp002StandingEgressGrant,
  validateMvp002StandingEgressGrant
} from "./mvp002-standing-egress-grant.mjs";

const grantPath = resolve(
  dirname(fileURLToPath(import.meta.url)),
  "mvp002-standing-egress-grant.json"
);

test("accepts only the exact user standing grant boundary", async () => {
  const { grant } = await loadAndValidateMvp002StandingEgressGrant(
    grantPath
  );
  assert.equal(grant.revoked, false);
  assert.equal(grant.provider.ccAuditModel, "deepseek-v4-flash");
  assert.equal(grant.derivedReceipt.perRunChatConfirmationRequired, false);
});

for (const [name, mutate] of [
  ["revocation", (grant) => { grant.revoked = true; }],
  ["redirect", (grant) => { grant.provider.redirectAllowed = true; }],
  ["proxy", (grant) => { grant.provider.proxyAllowed = true; }],
  ["raw response", (grant) => {
    grant.transport.rawResponsePersistenceAllowed = true;
  }],
  ["unknown side effect retry", (grant) => {
    grant.transport.unknownSideEffectAutomaticRetryAllowed = true;
  }],
  ["missing receipt binding", (grant) => {
    grant.derivedReceipt.requiredBindings.pop();
  }],
  ["extra model", (grant) => {
    grant.provider.allowedModels.push("deepseek-chat");
  }]
]) {
  test(`rejects standing-grant weakening: ${name}`, async () => {
    const { grant } = await loadAndValidateMvp002StandingEgressGrant(
      grantPath
    );
    const tampered = structuredClone(grant);
    mutate(tampered);
    assert.throws(() => validateMvp002StandingEgressGrant(tampered));
  });
}
