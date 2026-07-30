export const decideProviderGate = (gates) => {
  if (
    gates === null ||
    typeof gates !== "object" ||
    Array.isArray(gates) ||
    Object.keys(gates).length === 0 ||
    Object.values(gates).some((value) => typeof value !== "boolean")
  ) {
    throw new Error("PROVIDER_GATE_INPUT_INVALID");
  }
  const blockingGates = Object.entries(gates)
    .filter(([, passed]) => !passed)
    .map(([gate]) => gate);
  const status =
    blockingGates.length === 0 ? "GO" : "NO_GO_BLOCKED";
  return {
    status,
    providerImplementationAuthorized: status === "GO",
    reviewingModelActivationAuthorized: false,
    blockingGates,
  };
};
