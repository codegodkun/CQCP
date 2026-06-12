package com.cqcp.apiserver.tuning;

public record VersionRefs(
        String ruleSetVersion,
        String modelProfileVersion,
        String parserVersion,
        String promptVersion,
        String schemaVersion,
        String patternLibraryVersion,
        String fieldLexiconVersion,
        String evidenceSelectorVersion) {
}
