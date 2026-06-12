package com.cqcp.apiserver.admindiagnostics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class AdminTaskDiagnosticsViewFactoryTest {

    private final AdminTaskDiagnosticsViewFactory factory = new AdminTaskDiagnosticsViewFactory();

    @Test
    void createsAdminDiagnosticsViewWithoutSensitiveFields() {
        var source = new AdminTaskDiagnosticsSource(
                "task-001",
                "execution-003",
                "企鹅岛项目三标段土建总承包工程合同",
                "REVIEWING_MODEL",
                "Local mock fallback",
                "MOCK",
                new AdminTaskDiagnosticsSource.Summary(9, 5, 1, 1, 1, 1),
                List.of(
                        new AdminTaskDiagnosticsSource.PointResultSource(
                                "PARTY_A_NAME_CONSISTENCY",
                                "PASS",
                                "甲方名称一致。",
                                null),
                        new AdminTaskDiagnosticsSource.PointResultSource(
                                "PARTY_B_NAME_CONSISTENCY",
                                "NOT_CONCLUDED",
                                "模型暂时不可用，未形成正式结论。",
                                "SYS_MODEL_TIMEOUT")),
                List.of(
                        new AdminTaskDiagnosticsSource.StageLogSource(
                                "PARSING",
                                "COMPLETED",
                                "SUCCESS",
                                "文档解析完成。"),
                        new AdminTaskDiagnosticsSource.StageLogSource(
                                "REVIEWING_MODEL",
                                "FAILED",
                                "PARTIAL_SUCCESS",
                                "模型调用超时。")),
                List.of("SYS_MODEL_TIMEOUT", "SYS_PARSE_LOW_CONFIDENCE"),
                "secret prompt",
                "secret raw output",
                "secret endpoint",
                "stack trace");

        var view = factory.create(source);

        assertThat(view.taskId()).isEqualTo("task-001");
        assertThat(view.executionId()).isEqualTo("execution-003");
        assertThat(view.stage()).isEqualTo("REVIEWING_MODEL");
        assertThat(view.model().displayName()).isEqualTo("Local mock fallback");
        assertThat(view.summary().plannedPointCount()).isEqualTo(9);
        assertThat(view.summary().passCount()).isEqualTo(5);
        assertThat(view.summary().notConcludedCount()).isEqualTo(1);
        assertThat(view.pointResults())
                .extracting(AdminTaskDiagnosticsView.PointResultView::pointStatus)
                .containsExactly("PASS", "NOT_CONCLUDED");
        assertThat(view.stageLogs())
                .extracting(AdminTaskDiagnosticsView.StageLogView::stageName)
                .containsExactly("PARSING", "REVIEWING_MODEL");
        assertThat(view.diagnosticCodes()).contains("SYS_MODEL_TIMEOUT");
        assertThat(view.excludedSensitiveFields())
                .containsExactly("promptPreview", "rawOutput", "endpointSecret", "stackTrace");
    }
}
