package com.cqcp.apiserver.reviewengine;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TaskExecutionStateMachine {

    private final MinimalReviewEngine reviewEngine;
    private final ResultComposer resultComposer;
    private final Clock clock;

    public TaskExecutionStateMachine(
            MinimalReviewEngine reviewEngine,
            ResultComposer resultComposer,
            Clock clock) {
        this.reviewEngine = Objects.requireNonNull(reviewEngine, "reviewEngine");
        this.resultComposer = Objects.requireNonNull(resultComposer, "resultComposer");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public TaskExecutionRunResult execute(
            TaskExecutionRequest request,
            TaskExecutionPersistence persistence) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(persistence, "persistence");

        if (request.execution().status().isTerminal()) {
            throw new IllegalStateException("Cannot execute terminal execution: " + request.execution().executionId());
        }

        var startedAt = Instant.now(clock);
        var runningExecution = request.execution().transitionTo(
                ExecutionStatus.REVIEWING_RULES,
                "REVIEWING_RULES",
                startedAt,
                null);
        persistence.saveExecution(runningExecution);

        ReviewEngineResult reviewEngineResult;
        try {
            reviewEngineResult = runReviewingRulesStage(request, runningExecution, persistence, startedAt);
        } catch (RuntimeException exception) {
            var failedExecution = runningExecution.transitionTo(
                    ExecutionStatus.FAILED,
                    "REVIEWING_RULES",
                    runningExecution.startedAt(),
                    Instant.now(clock));
            persistence.appendStageLog(TaskStageLogEntry.failed(
                    request.task().taskId(),
                    runningExecution.executionId(),
                    "REVIEWING_RULES",
                    1,
                    exception.getMessage(),
                    Duration.between(startedAt, Instant.now(clock)).toMillis(),
                    Instant.now(clock)));
            persistence.saveExecution(failedExecution);
            throw exception;
        }

        var composingStartedAt = Instant.now(clock);
        var composingExecution = runningExecution.transitionTo(
                ExecutionStatus.COMPOSING,
                "COMPOSING",
                runningExecution.startedAt(),
                null);
        persistence.saveExecution(composingExecution);
        persistence.appendStageLog(TaskStageLogEntry.started(
                request.task().taskId(),
                composingExecution.executionId(),
                "COMPOSING",
                1,
                composingStartedAt));

        try {
            var snapshot = composeSnapshot(request, composingExecution, reviewEngineResult);
            persistence.saveSnapshot(snapshot);

            var terminalStatus = snapshot.status() == SnapshotStatus.PARTIAL_SUCCESS
                    ? ExecutionStatus.PARTIAL_SUCCESS
                    : ExecutionStatus.SUCCESS;
            var finishedAt = Instant.now(clock);
            persistence.appendStageLog(TaskStageLogEntry.completed(
                    request.task().taskId(),
                    composingExecution.executionId(),
                    "COMPOSING",
                    1,
                    terminalStatus.name(),
                    Duration.between(composingStartedAt, finishedAt).toMillis(),
                    finishedAt));

            var completedExecution = composingExecution.transitionTo(
                    terminalStatus,
                    terminalStatus.name(),
                    composingExecution.startedAt(),
                    finishedAt);
            persistence.saveExecution(completedExecution);

            return new TaskExecutionRunResult(completedExecution, snapshot);
        } catch (RuntimeException exception) {
            var failedAt = Instant.now(clock);
            persistence.appendStageLog(TaskStageLogEntry.failed(
                    request.task().taskId(),
                    composingExecution.executionId(),
                    "COMPOSING",
                    1,
                    exception.getMessage(),
                    Duration.between(composingStartedAt, failedAt).toMillis(),
                    failedAt));
            var failedExecution = composingExecution.transitionTo(
                    ExecutionStatus.FAILED,
                    "COMPOSING",
                    composingExecution.startedAt(),
                    failedAt);
            persistence.saveExecution(failedExecution);
            throw exception;
        }
    }

    private ReviewEngineResult runReviewingRulesStage(
            TaskExecutionRequest request,
            TaskExecutionRecord runningExecution,
            TaskExecutionPersistence persistence,
            Instant stageStartedAt) {
        persistence.appendStageLog(TaskStageLogEntry.started(
                request.task().taskId(),
                runningExecution.executionId(),
                "REVIEWING_RULES",
                1,
                stageStartedAt));

        var reviewEngineResult = reviewEngine.review(request.reviewInput());
        var completedAt = Instant.now(clock);
        persistence.appendStageLog(TaskStageLogEntry.completed(
                request.task().taskId(),
                runningExecution.executionId(),
                "REVIEWING_RULES",
                1,
                deriveReviewStageSummaryStatus(reviewEngineResult),
                Duration.between(stageStartedAt, completedAt).toMillis(),
                completedAt));
        return reviewEngineResult;
    }

    private String deriveReviewStageSummaryStatus(ReviewEngineResult reviewEngineResult) {
        return reviewEngineResult.summary().notConcludedCount() > 0 ? "PARTIAL_SUCCESS" : "SUCCESS";
    }

    private ReviewResultSnapshot composeSnapshot(
            TaskExecutionRequest request,
            TaskExecutionRecord execution,
            ReviewEngineResult reviewEngineResult) {
        return resultComposer.compose(
                new ResultComposerInput(
                        request.task().taskId(),
                        execution.executionId(),
                        request.task().structuredFieldsSnapshot(),
                        request.enabledReviewPointsSnapshot(),
                        request.disabledReviewPointsSnapshot(),
                        execution.versionReferences(),
                        Instant.now(clock)),
                reviewEngineResult);
    }
}

record TaskExecutionRequest(
        ReviewTaskRecord task,
        TaskExecutionRecord execution,
        ReviewEngineInput reviewInput,
        List<ReviewPointSnapshot> enabledReviewPointsSnapshot,
        List<ReviewPointSnapshot> disabledReviewPointsSnapshot) {

    TaskExecutionRequest {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(execution, "execution");
        Objects.requireNonNull(reviewInput, "reviewInput");
        enabledReviewPointsSnapshot = List.copyOf(enabledReviewPointsSnapshot);
        disabledReviewPointsSnapshot = List.copyOf(disabledReviewPointsSnapshot);
    }
}

record ReviewTaskRecord(
        String taskId,
        String contractName,
        Map<String, String> structuredFieldsSnapshot) {

    ReviewTaskRecord {
        structuredFieldsSnapshot = Map.copyOf(structuredFieldsSnapshot);
    }
}

record TaskExecutionRecord(
        String executionId,
        String taskId,
        ExecutionStatus status,
        String currentStage,
        VersionReferences versionReferences,
        String modelProfileCode,
        String providerType,
        String modelName,
        String endpointAlias,
        Instant startedAt,
        Instant finishedAt) {

    TaskExecutionRecord {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(currentStage, "currentStage");
        Objects.requireNonNull(versionReferences, "versionReferences");
    }

    TaskExecutionRecord transitionTo(
            ExecutionStatus nextStatus,
            String nextStage,
            Instant nextStartedAt,
            Instant nextFinishedAt) {
        return new TaskExecutionRecord(
                executionId,
                taskId,
                nextStatus,
                nextStage,
                versionReferences,
                modelProfileCode,
                providerType,
                modelName,
                endpointAlias,
                nextStartedAt,
                nextFinishedAt);
    }
}

enum ExecutionStatus {
    CREATED,
    QUEUED,
    PARSING,
    INDEXING,
    PLANNING,
    BUILDING_EVIDENCE,
    REVIEWING_RULES,
    REVIEWING_MODEL,
    COMPOSING,
    SUCCESS,
    PARTIAL_SUCCESS,
    FAILED,
    CANCELLED;

    boolean isTerminal() {
        return this == SUCCESS || this == PARTIAL_SUCCESS || this == FAILED || this == CANCELLED;
    }
}

record TaskStageLogEntry(
        String taskId,
        String executionId,
        String stageName,
        int attempt,
        String eventType,
        String summaryStatus,
        String businessReason,
        String diagnosticCode,
        Long durationMs,
        Map<String, Object> detailPayload,
        Instant createdAt) {

    TaskStageLogEntry {
        detailPayload = Map.copyOf(detailPayload);
    }

    static TaskStageLogEntry started(
            String taskId,
            String executionId,
            String stageName,
            int attempt,
            Instant createdAt) {
        return new TaskStageLogEntry(
                taskId,
                executionId,
                stageName,
                attempt,
                "STARTED",
                "RUNNING",
                null,
                null,
                null,
                Map.of(),
                createdAt);
    }

    static TaskStageLogEntry completed(
            String taskId,
            String executionId,
            String stageName,
            int attempt,
            String summaryStatus,
            long durationMs,
            Instant createdAt) {
        return new TaskStageLogEntry(
                taskId,
                executionId,
                stageName,
                attempt,
                "COMPLETED",
                summaryStatus,
                null,
                null,
                durationMs,
                Map.of(),
                createdAt);
    }

    static TaskStageLogEntry failed(
            String taskId,
            String executionId,
            String stageName,
            int attempt,
            String businessReason,
            long durationMs,
            Instant createdAt) {
        return new TaskStageLogEntry(
                taskId,
                executionId,
                stageName,
                attempt,
                "FAILED",
                "FAILED",
                businessReason,
                null,
                durationMs,
                Map.of(),
                createdAt);
    }
}

record TaskExecutionRunResult(
        TaskExecutionRecord execution,
        ReviewResultSnapshot snapshot) {
}

interface TaskExecutionPersistence {

    void saveExecution(TaskExecutionRecord execution);

    void appendStageLog(TaskStageLogEntry entry);

    void saveSnapshot(ReviewResultSnapshot snapshot);
}
