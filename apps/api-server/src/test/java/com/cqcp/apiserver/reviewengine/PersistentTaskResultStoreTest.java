package com.cqcp.apiserver.reviewengine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class PersistentTaskResultStoreTest {

    private final JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
    private final PersistentTaskResultStore store =
            new PersistentTaskResultStore(jdbcTemplate, JsonMapper.builder().findAndAddModules().build());

    @Test
    void returnsLatestSnapshotFromPersistentStore() {
        var snapshot = snapshot();
        when(jdbcTemplate.queryForList(anyString(), eq("task-001")))
                .thenReturn(List.of(snapshotRow(snapshot)));

        var result = store.findLatestSnapshot("task-001");

        assertThat(result).contains(snapshot);
    }

    @Test
    void returnsEmptyWhenPersistentSnapshotDoesNotExist() {
        when(jdbcTemplate.queryForList(anyString(), eq("missing-task"))).thenReturn(List.of());

        var result = store.findLatestSnapshot("missing-task");

        assertThat(result).isEmpty();
    }

    @Test
    void reportsTaskExistenceFromPersistentTaskTable() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), eq("task-001"))).thenReturn(true);
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), eq("missing-task"))).thenReturn(false);

        assertThat(store.hasTask("task-001")).isTrue();
        assertThat(store.hasTask("missing-task")).isFalse();
    }

    @Test
    void adapterRemainsReadOnlyAndDoesNotExposeExecutionWriteContract() {
        assertThat(store).isInstanceOf(TaskResultStore.class);
        assertThat(store).isNotInstanceOf(TaskExecutionPersistence.class);
        verifyNoMoreInteractions(jdbcTemplate);
    }

    @Test
    void toleratesForwardCompatibleSnapshotFieldsFromPersistenceJson() {
        when(jdbcTemplate.queryForList(anyString(), eq("task-001")))
                .thenReturn(List.of(snapshotRowWithForwardCompatibleFields()));

        var result = store.findLatestSnapshot("task-001");

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().pointResults()).hasSize(1);
        assertThat(result.orElseThrow().pointResults().getFirst().pointCoverageStatus())
                .isEqualTo(PointCoverageStatus.COMPLETE);
        assertThat(result.orElseThrow().pointResults().getFirst().notConcludedDetail())
                .isEqualTo("INDEX_MISSING");
        assertThat(result.orElseThrow().pointResults().getFirst().missingOptionalSlots()).hasSize(1);
        assertThat(result.orElseThrow().pointResults().getFirst().sourceAnchors().getFirst().locationLevel())
                .isEqualTo("BLOCK_LEVEL");
        assertThat(result.orElseThrow().pointResults().getFirst().sourceAnchors().getFirst().previewElementRef())
                .isEqualTo("table:table-1/row:0/cell:1");
    }

    @Test
    void multiAnchorPersistenceJsonRemainsQueryable() {
        when(jdbcTemplate.queryForList(anyString(), eq("task-001")))
                .thenReturn(List.of(multiAnchorSnapshotRow()));

        var result = new TaskResultQueryService(store).getResult("task-001");

        assertThat(result.pointResults().getFirst().sourceAnchors())
                .extracting(SourceAnchorSummary::previewElementRef)
                .containsExactly(
                        "table:party-table/row:1/cell:0",
                        "table:party-table/row:1/cell:1");
        assertThat(result.sourceAnchors())
                .extracting(SourceAnchorSummary::previewElementRef)
                .containsExactly(
                        "table:party-table/row:1/cell:0",
                        "table:party-table/row:1/cell:1");
    }

    private ReviewResultSnapshot snapshot() {
        return new ReviewResultSnapshot(
                "task-001",
                "execution-001",
                null,
                null,
                SnapshotStatus.SUCCESS,
                new ReviewSummary(1, 1, 0, 0, 0, 0),
                new ReviewCompleteness(
                        ReviewCoverageStatus.FULL_REVIEWED,
                        1,
                        1,
                        0,
                        BigDecimal.ONE,
                        ConfidenceLevel.HIGH),
                List.of(new PointReviewResult(
                        ReviewPointCode.PARTY_A_NAME_CONSISTENCY,
                        PointStatus.PASS,
                        "甲方名称一致。",
                        null,
                        List.of(new SourceAnchorSummary("block-001", "NATIVE_WORD", "STRUCTURED", "NORMAL", "甲方证据")),
                        null,
                        null,
                        PointCoverageStatus.COMPLETE,
                        null,
                        List.of())),
                List.of(),
                List.of(),
                List.of(new SourceAnchorSummary("block-001", "NATIVE_WORD", "STRUCTURED", "NORMAL", "甲方证据")),
                Map.of("partyAName", "甲方公司"),
                List.of(new ReviewPointSnapshot(
                        ReviewPointCode.PARTY_A_NAME_CONSISTENCY,
                        "P001",
                        "甲方名称一致性",
                        "PARTY_FIELDS",
                        "ENGINEERING_PROCUREMENT",
                        FindingSeverity.ERROR,
                        1)),
                List.of(),
                "contract-type-v1",
                "ruleset-v1",
                "budget-v1",
                "model-v1",
                "parser-v1",
                "prompt-v1",
                "schema-v1",
                "pattern-v1",
                "lexicon-v1",
                "selector-v1",
                Instant.parse("2026-06-13T10:00:00Z"));
    }

    private Map<String, Object> snapshotRow(ReviewResultSnapshot snapshot) {
        return Map.ofEntries(
                Map.entry("task_id", snapshot.taskId()),
                Map.entry("execution_id", snapshot.executionId()),
                Map.entry("status", snapshot.status().name()),
                Map.entry("summary_json", """
                        {"plannedPointCount":1,"passCount":1,"errorCount":0,"warningCount":0,"notConcludedCount":0,"skippedCount":0}
                        """.trim()),
                Map.entry("review_completeness_json", """
                        {"reviewCoverageStatus":"FULL_REVIEWED","executablePointCount":1,"concludedPointCount":1,"notConcludedPointCount":0,"concludedCoverageRate":1,"confidenceLevel":"HIGH"}
                        """.trim()),
                Map.entry("point_results_json", """
                        [{"reviewPointCode":"PARTY_A_NAME_CONSISTENCY","pointStatus":"PASS","businessMessage":"甲方名称一致。","findingSeverity":null,"sourceAnchors":[{"blockId":"block-001","sourceOrigin":"NATIVE_WORD","sourceExtractionMode":"STRUCTURED","contextType":"NORMAL","evidenceSummary":"甲方证据"}],"notConcludedReason":null,"skippedReason":null}]
                        """.trim()),
                Map.entry("findings_json", "[]"),
                Map.entry("diagnostics_json", "[]"),
                Map.entry("source_anchors_json", """
                        [{"blockId":"block-001","sourceOrigin":"NATIVE_WORD","sourceExtractionMode":"STRUCTURED","contextType":"NORMAL","evidenceSummary":"甲方证据"}]
                        """.trim()),
                Map.entry("structured_fields_snapshot_json", """
                        {"partyAName":"甲方公司"}
                        """.trim()),
                Map.entry("enabled_review_points_snapshot_json", """
                        [{"reviewPointCode":"PARTY_A_NAME_CONSISTENCY","displayCode":"P001","displayName":"甲方名称一致性","reviewPointFamily":"PARTY_FIELDS","contractType":"ENGINEERING_PROCUREMENT","defaultSeverity":"ERROR","displayOrder":1}]
                        """.trim()),
                Map.entry("disabled_review_points_snapshot_json", "[]"),
                Map.entry("contract_type_profile_version", snapshot.contractTypeProfileVersion()),
                Map.entry("rule_set_version", snapshot.ruleSetVersion()),
                Map.entry("review_budget_profile_version", snapshot.reviewBudgetProfileVersion()),
                Map.entry("model_profile_version", snapshot.modelProfileVersion()),
                Map.entry("parser_version", snapshot.parserVersion()),
                Map.entry("prompt_version", snapshot.promptVersion()),
                Map.entry("schema_version", snapshot.schemaVersion()),
                Map.entry("pattern_library_version", snapshot.patternLibraryVersion()),
                Map.entry("field_lexicon_version", snapshot.fieldLexiconVersion()),
                Map.entry("evidence_selector_version", snapshot.evidenceSelectorVersion()),
                Map.entry("created_at", Timestamp.from(snapshot.createdAt())));
    }

    private Map<String, Object> snapshotRowWithForwardCompatibleFields() {
        var snapshot = snapshot();
        return Map.ofEntries(
                Map.entry("task_id", snapshot.taskId()),
                Map.entry("execution_id", snapshot.executionId()),
                Map.entry("status", snapshot.status().name()),
                Map.entry("summary_json", """
                        {"plannedPointCount":1,"passCount":1,"errorCount":0,"warningCount":0,"notConcludedCount":0,"skippedCount":0,"futureSummaryField":"ignored"}
                        """.trim()),
                Map.entry("review_completeness_json", """
                        {"reviewCoverageStatus":"FULL_REVIEWED","executablePointCount":1,"concludedPointCount":1,"notConcludedPointCount":0,"concludedCoverageRate":1,"confidenceLevel":"HIGH","futureCoverageDetail":"ignored"}
                        """.trim()),
                Map.entry("point_results_json", """
                        [{"reviewPointCode":"PARTY_A_NAME_CONSISTENCY","pointStatus":"PASS","businessMessage":"甲方名称一致。","findingSeverity":null,"sourceAnchors":[{"blockId":"block-001","sourceOrigin":"NATIVE_WORD","sourceExtractionMode":"STRUCTURED","contextType":"NORMAL","evidenceSummary":"甲方证据","locationLevel":"BLOCK_LEVEL","sectionPath":["第一章"],"previewElementRef":"table:table-1/row:0/cell:1","previewPage":1}],"notConcludedReason":null,"skippedReason":null,"notConcludedDetail":"INDEX_MISSING","missingOptionalSlots":[{"slotName":"secondaryEvidence","cause":"NOT_FOUND"}]}]
                        """.trim()),
                Map.entry("findings_json", "[]"),
                Map.entry("diagnostics_json", "[]"),
                Map.entry("source_anchors_json", """
                        [{"blockId":"block-001","sourceOrigin":"NATIVE_WORD","sourceExtractionMode":"STRUCTURED","contextType":"NORMAL","evidenceSummary":"甲方证据","locationLevel":"BLOCK_LEVEL","relatedBlockIds":["block-002"],"previewPage":1}]
                        """.trim()),
                Map.entry("structured_fields_snapshot_json", """
                        {"partyAName":"甲方公司"}
                        """.trim()),
                Map.entry("enabled_review_points_snapshot_json", """
                        [{"reviewPointCode":"PARTY_A_NAME_CONSISTENCY","displayCode":"P001","displayName":"甲方名称一致性","reviewPointFamily":"PARTY_FIELDS","contractType":"ENGINEERING_PROCUREMENT","defaultSeverity":"ERROR","displayOrder":1,"futureDisplayTag":"ignored"}]
                        """.trim()),
                Map.entry("disabled_review_points_snapshot_json", "[]"),
                Map.entry("contract_type_profile_version", snapshot.contractTypeProfileVersion()),
                Map.entry("rule_set_version", snapshot.ruleSetVersion()),
                Map.entry("review_budget_profile_version", snapshot.reviewBudgetProfileVersion()),
                Map.entry("model_profile_version", snapshot.modelProfileVersion()),
                Map.entry("parser_version", snapshot.parserVersion()),
                Map.entry("prompt_version", snapshot.promptVersion()),
                Map.entry("schema_version", snapshot.schemaVersion()),
                Map.entry("pattern_library_version", snapshot.patternLibraryVersion()),
                Map.entry("field_lexicon_version", snapshot.fieldLexiconVersion()),
                Map.entry("evidence_selector_version", snapshot.evidenceSelectorVersion()),
                Map.entry("created_at", Timestamp.from(snapshot.createdAt())));
    }

    private Map<String, Object> multiAnchorSnapshotRow() {
        var row = new HashMap<>(snapshotRow(snapshot()));
        var anchors = """
                [{"blockId":"block-table-row","sourceOrigin":"NATIVE_WORD","sourceExtractionMode":"STRUCTURED","contextType":"NORMAL","evidenceSummary":"甲方单元格 1","sectionPath":["合同主体"],"regionType":"BODY","confidence":"HIGH","locationLevel":"BLOCK_LEVEL","previewElementRef":"table:party-table/row:1/cell:0"},{"blockId":"block-table-row","sourceOrigin":"NATIVE_WORD","sourceExtractionMode":"STRUCTURED","contextType":"NORMAL","evidenceSummary":"甲方单元格 2","sectionPath":["合同主体"],"regionType":"BODY","confidence":"HIGH","locationLevel":"BLOCK_LEVEL","previewElementRef":"table:party-table/row:1/cell:1"}]
                """.trim();
        row.put("point_results_json", """
                [{"reviewPointCode":"PARTY_A_NAME_CONSISTENCY","pointStatus":"PASS","businessMessage":"甲方名称一致。","findingSeverity":null,"sourceAnchors":%s,"notConcludedReason":null,"skippedReason":null,"pointCoverageStatus":"COMPLETE","notConcludedDetail":null,"missingOptionalSlots":[]}]
                """.formatted(anchors).trim());
        row.put("source_anchors_json", anchors);
        return row;
    }
}
