import assert from "node:assert/strict";
import test from "node:test";
import { decideProviderGate } from "./provider-gate-decision.mjs";

test("all provider pre-implementation gates authorize only A1", () => {
  assert.deepEqual(
    decideProviderGate({
      contractAudited: true,
      admissionEstablished: true,
    }),
    {
      status: "GO",
      providerImplementationAuthorized: true,
      reviewingModelActivationAuthorized: false,
      blockingGates: [],
    },
  );
});

test("any false gate remains fail closed", () => {
  assert.deepEqual(
    decideProviderGate({
      contractAudited: true,
      admissionEstablished: false,
    }),
    {
      status: "NO_GO_BLOCKED",
      providerImplementationAuthorized: false,
      reviewingModelActivationAuthorized: false,
      blockingGates: ["admissionEstablished"],
    },
  );
});

test("non-boolean or empty gate input is rejected", () => {
  assert.throws(
    () => decideProviderGate({ contractAudited: "true" }),
    /PROVIDER_GATE_INPUT_INVALID/,
  );
  assert.throws(
    () => decideProviderGate({}),
    /PROVIDER_GATE_INPUT_INVALID/,
  );
});
