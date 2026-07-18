package com.cqcp.apiserver.reviewengine;

class RuleSetActivationGate {

    static final String LEGACY_ALLOWED = "LEGACY_ALLOWED";
    static final String POLICY_NOT_READY = "POLICY_NOT_READY";
    static final String UNKNOWN_RULE_SET_VERSION = "UNKNOWN_RULE_SET_VERSION";
    static final String POLICY_ASSET_INVALID = "POLICY_ASSET_INVALID";
    static final String READY = "READY";

    private final RuntimeRuleSetLoader loader;

    RuleSetActivationGate() {
        this(new RuntimeRuleSetLoader());
    }

    RuleSetActivationGate(RuntimeRuleSetLoader loader) {
        this.loader = loader;
    }

    GateResult request(String version, boolean consistencyRuntimeReady) {
        if (version == null || version.isBlank()) {
            return new GateResult(UNKNOWN_RULE_SET_VERSION, null);
        }
        if ("v20260705.1".equals(version)) {
            return new GateResult(LEGACY_ALLOWED, null);
        }
        if ("v20260715.1".equals(version)) {
            if (!consistencyRuntimeReady) {
                return new GateResult(POLICY_NOT_READY, null);
            }
            try {
                RuntimeRuleSetSnapshot snapshot = loader.load(version);
                return new GateResult(READY, snapshot);
            } catch (Exception e) {
                return new GateResult(POLICY_ASSET_INVALID, null);
            }
        }
        return new GateResult(UNKNOWN_RULE_SET_VERSION, null);
    }

    record GateResult(String status, RuntimeRuleSetSnapshot snapshot) {}
}
