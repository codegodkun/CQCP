package com.cqcp.apiserver.reviewengine;

import java.util.Set;

/**
 * Code-owned activation evidence for the first consistency-set runtime.
 *
 * <p>This release is intentionally not inferred from Spring profiles, environment
 * variables, classpath resource presence, or the B1 asset's inactive metadata.
 * A future version requires a new reviewed code release.
 */
record ConsistencyRuntimeRelease(Set<String> readyRuleSetVersions) {

    static final String FIRST_RULE_SET_VERSION = "v20260715.1";
    static final String SECOND_RULE_SET_VERSION = "v20260729.1";

    ConsistencyRuntimeRelease {
        readyRuleSetVersions = Set.copyOf(readyRuleSetVersions);
        if (!Set.of(FIRST_RULE_SET_VERSION, SECOND_RULE_SET_VERSION)
                .containsAll(readyRuleSetVersions)) {
            throw new IllegalArgumentException(
                    "Unsupported consistency runtime release: " + readyRuleSetVersions);
        }
    }

    static ConsistencyRuntimeRelease accepted() {
        return new ConsistencyRuntimeRelease(
                Set.of(FIRST_RULE_SET_VERSION, SECOND_RULE_SET_VERSION));
    }

    static ConsistencyRuntimeRelease disabledForTest() {
        return new ConsistencyRuntimeRelease(Set.of());
    }

    boolean readyFor(String requestedRuleSetVersion) {
        return readyRuleSetVersions.contains(requestedRuleSetVersion);
    }
}
