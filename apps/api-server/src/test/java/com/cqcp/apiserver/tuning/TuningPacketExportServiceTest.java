package com.cqcp.apiserver.tuning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TuningPacketExportServiceTest {

    private TuningPacketExportService service;

    @BeforeEach
    void setUp() {
        service = new TuningPacketExportService();
    }

    @Test
    void exportsSinglePointDiagnosticOnlyPacket() {
        var export = service.export(
                sampleSource(false),
                new ExportConfig(ExportMode.SINGLE_POINT, "PARTY_A_NAME_CONSISTENCY", List.of()));

        assertThat(export.exportMode()).isEqualTo(ExportMode.SINGLE_POINT);
        assertThat(export.pointDiagnostics()).hasSize(1);
        assertThat(export.pointDiagnostics().getFirst().reviewPointCode()).isEqualTo("PARTY_A_NAME_CONSISTENCY");
        assertThat(export.executionSummary().executionId()).isEqualTo("execution-001");
        assertThat(export.versionRefs().promptVersion()).isEqualTo("prompt-v1");
        assertThat(export.governanceBoundary()).contains("diagnostic-only");
        assertThat(export.excludedContent()).contains(
                "FULL_CONTRACT_TEXT",
                "FULL_PROMPT",
                "FULL_RAW_OUTPUT",
                "ENDPOINT_SECRET",
                "STACK_TRACE");
    }

    @Test
    void exportsFocusedPacketByDiagnosticCodes() {
        var export = service.export(
                sampleSource(false),
                new ExportConfig(
                        ExportMode.FOCUSED,
                        null,
                        List.of("SYS_MODEL_TIMEOUT", "SYS_MODEL_OUTPUT_INCOMPLETE")));

        assertThat(export.exportMode()).isEqualTo(ExportMode.FOCUSED);
        assertThat(export.pointDiagnostics()).hasSize(2);
        assertThat(export.pointDiagnostics())
                .extracting(PointDiagnostic::diagnosticCode)
                .containsExactly("SYS_MODEL_TIMEOUT", "SYS_MODEL_OUTPUT_INCOMPLETE");
    }

    @Test
    void rejectsSensitivePayloadExport() {
        assertThatThrownBy(() -> service.export(
                        sampleSource(true),
                        new ExportConfig(
                                ExportMode.FOCUSED,
                                null,
                                List.of("SYS_MODEL_TIMEOUT", "SYS_MODEL_OUTPUT_INCOMPLETE"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Sensitive payload");
    }

    @Test
    void rejectsEmptySelection() {
        assertThatThrownBy(() -> service.export(
                        sampleSource(false),
                        new ExportConfig(ExportMode.FOCUSED, null, List.of("SYS_RULE_ERROR"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No diagnostics selected");
    }

    private TuningPacketSource sampleSource(boolean withSensitivePayload) {
        return new TuningPacketSource(
                "tuning-001",
                new ExecutionSummary(
                        "task-001",
                        "execution-001",
                        "REVIEWING_MODEL",
                        "mock-standard",
                        List.of("SYS_MODEL_TIMEOUT"),
                        "模型调用超时，当前仅导出诊断摘要。"),
                List.of(
                        new PointDiagnostic(
                                "PARTY_A_NAME_CONSISTENCY",
                                "NOT_CONCLUDED",
                                "SYS_MODEL_TIMEOUT",
                                "模型暂时不可用，未形成正式结论。",
                                List.of("block-1"),
                                "甲方名称候选归属存在歧义。",
                                false),
                        new PointDiagnostic(
                                "PARTY_B_NAME_CONSISTENCY",
                                "NOT_CONCLUDED",
                                "SYS_MODEL_OUTPUT_INCOMPLETE",
                                "模型输出不完整，未形成正式结论。",
                                List.of("block-2"),
                                "乙方名称候选输出缺失必要字段。",
                                withSensitivePayload)),
                new VersionRefs(
                        "ruleset-v1",
                        "model-profile-v1",
                        "parser-v1",
                        "prompt-v1",
                        "schema-v1",
                        "pattern-v1",
                        "lexicon-v1",
                        "selector-v1"));
    }
}
