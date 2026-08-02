package com.cqcp.apiserver.reviewengine;

import java.util.List;

record ConsistencyPolicySnapshot(
    String cardinalityMode,
    int minCandidates,
    int maxCandidates,
    int occurrenceBudget,
    String scopeVersion,
    List<String> includedRegionTypes,
    List<String> strongExcludedContextTypes,
    List<String> requiredAttributionSignals,
    List<String> strongExcludedSemanticContexts,
    String canonicalizationVersion,
    String valueType,
    String unit,
    String anchorVersion,
    List<String> blockIdentity,
    List<String> tableCellIdentity
) {
    ConsistencyPolicySnapshot {
        includedRegionTypes = List.copyOf(includedRegionTypes);
        strongExcludedContextTypes = List.copyOf(strongExcludedContextTypes);
        requiredAttributionSignals = List.copyOf(requiredAttributionSignals);
        strongExcludedSemanticContexts = List.copyOf(strongExcludedSemanticContexts);
        blockIdentity = List.copyOf(blockIdentity);
        tableCellIdentity = List.copyOf(tableCellIdentity);
    }
}
