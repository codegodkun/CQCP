import assert from "node:assert/strict";
import test from "node:test";

import {
  assertTrustedInterpreterAfterUse,
  attestTrustedInterpreter,
} from "./stable-interpreter-attestation.mjs";

test("attests the fixed helper runtime before capability access", () => {
  const attestation = attestTrustedInterpreter();
  assert.equal(typeof attestation.command, "string");
  assert.match(attestation.runtimeVersion, /^\d+\.\d+\.\d+$/);
  assert.doesNotThrow(() =>
    assertTrustedInterpreterAfterUse(
      attestation,
      attestation.runtimeVersion,
    ),
  );
});

test("rejects a helper runtime version that differs from preflight", () => {
  const attestation = attestTrustedInterpreter();
  assert.throws(
    () => assertTrustedInterpreterAfterUse(attestation, "0.0.0"),
    /CAPABILITY_RUNTIME_ATTESTATION_FAILED/,
  );
});
