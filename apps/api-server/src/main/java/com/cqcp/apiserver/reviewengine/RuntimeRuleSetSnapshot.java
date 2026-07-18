package com.cqcp.apiserver.reviewengine;

import java.util.Collections;
import java.util.Map;

record RuntimeRuleSetSnapshot(
    String assetId,
    String version,
    String reviewPointDefinitionsAssetId,
    String reviewPointDefinitionsVersion,
    Map<ReviewPointCode, ConsistencyPolicySnapshot> policyMap
) {
    RuntimeRuleSetSnapshot {
        policyMap = Map.copyOf(policyMap);
    }

    @Override
    public Map<ReviewPointCode, ConsistencyPolicySnapshot> policyMap() {
        return Collections.unmodifiableMap(policyMap);
    }
}
