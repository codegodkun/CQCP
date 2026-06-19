package com.cqcp.apiserver.reviewengine;

import com.cqcp.apiserver.wordparser.DocxWordParserSpike;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TaskExecutionStateMachine {

    private final MinimalReviewEngine reviewEngine;
    private final ResultComposer resultComposer;
    private final ParserBackedReviewInputPreparer reviewInputPreparer;
    private final Clock clock;

    public TaskExecutionStateMachine(
            MinimalReviewEngine reviewEngine,
            ResultComposer resultComposer,
            Clock clock) {
        this(reviewEngine, resultComposer, new ParserBackedReviewInputPreparer(new DocxWordParserSpike()), clock);
    }

    public TaskExecutionStateMachine(
            MinimalReviewEngine reviewEngine,
            ResultComposer resultComposer,
            ParserBackedReviewInputPreparer reviewInputPreparer,
            Clock clock) {
        this.reviewEngine = Objects.requireNonNull(reviewEngine, "reviewEngine");
        this.resultComposer = Objects.requireNonNull(resultComposer, "resultComposer");
        this.reviewInputPreparer = Objects.requireNonNull(reviewInputPreparer, "reviewInputPreparer");
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
                initialStageFor(request),
                initialStageFor(request).name(),
                startedAt,
                null);
        persistence.saveExecution(runningExecution);

        PreparedReviewInput preparedInput;
        try {
            preparedInput = request.documentReference() == null
                    ? new PreparedReviewInput(request.reviewInput(), runningExecution)
                    : runPreparationStages(request, runningExecution, persistence, startedAt);
            runningExecution = preparedInput.execution();
        } catch (RuntimeException exception) {
            throw exception;
        }

        ReviewEngineResult reviewEngineResult;
        try {
            runningExecution = preparedInput.execution().transitionTo(
                    ExecutionStatus.REVIEWING_RULES,
                    "REVIEWING_RULES",
                    preparedInput.execution().startedAt(),
                    null);
            reviewEngineResult = runReviewingRulesStage(request, preparedInput.reviewInput(), runningExecution, persistence, Instant.now(clock));
        } catch (RuntimeException exception) {
            var failedExecution = runningExecution.transitionTo(
                    ExecutionStatus.FAILED,
                    runningExecution.currentStage(),
                    runningExecution.startedAt(),
                    Instant.now(clock));
            persistence.appendStageLog(TaskStageLogEntry.failed(
                    request.task().taskId(),
                    runningExecution.executionId(),
                    runningExecution.currentStage(),
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

    private ExecutionStatus initialStageFor(TaskExecutionRequest request) {
        return request.documentReference() == null ? ExecutionStatus.REVIEWING_RULES : ExecutionStatus.PARSING;
    }

    private PreparedReviewInput runPreparationStages(
            TaskExecutionRequest request,
            TaskExecutionRecord runningExecution,
            TaskExecutionPersistence persistence,
            Instant startedAt) {
        var parsedDocument = runStage(
                request,
                runningExecution,
                ExecutionStatus.PARSING,
                persistence,
                startedAt,
                () -> reviewInputPreparer.parse(request.documentReference()),
                result -> "SUCCESS");

        var indexed = runStage(
                request,
                parsedDocument.execution(),
                ExecutionStatus.INDEXING,
                persistence,
                Instant.now(clock),
                () -> reviewInputPreparer.index(parsedDocument.value()),
                result -> "SUCCESS");

        var planned = runStage(
                request,
                indexed.execution(),
                ExecutionStatus.PLANNING,
                persistence,
                Instant.now(clock),
                () -> reviewInputPreparer.plan(indexed.value()),
                result -> "SUCCESS");

        var built = runStage(
                request,
                planned.execution(),
                ExecutionStatus.BUILDING_EVIDENCE,
                persistence,
                Instant.now(clock),
                () -> reviewInputPreparer.build(request, planned.value()),
                input -> input.pointEvidences().values().stream().allMatch(evidence -> evidence.status() == EvidenceStatus.CONFIRMED)
                        ? "SUCCESS"
                        : "PARTIAL_SUCCESS");

        return new PreparedReviewInput(built.value(), built.execution());
    }

    private ReviewEngineResult runReviewingRulesStage(
            TaskExecutionRequest request,
            ReviewEngineInput reviewInput,
            TaskExecutionRecord runningExecution,
            TaskExecutionPersistence persistence,
            Instant stageStartedAt) {
        persistence.saveExecution(runningExecution);
        persistence.appendStageLog(TaskStageLogEntry.started(
                request.task().taskId(),
                runningExecution.executionId(),
                "REVIEWING_RULES",
                1,
                stageStartedAt));

        var reviewEngineResult = reviewEngine.review(reviewInput);
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

    private <T> StageValue<T> runStage(
            TaskExecutionRequest request,
            TaskExecutionRecord currentExecution,
            ExecutionStatus stageStatus,
            TaskExecutionPersistence persistence,
            Instant stageStartedAt,
            StageSupplier<T> supplier,
            StageSummary<T> summary) {
        var runningExecution = currentExecution.transitionTo(
                stageStatus,
                stageStatus.name(),
                currentExecution.startedAt(),
                null);
        persistence.saveExecution(runningExecution);
        persistence.appendStageLog(TaskStageLogEntry.started(
                request.task().taskId(),
                runningExecution.executionId(),
                stageStatus.name(),
                1,
                stageStartedAt));
        try {
            var value = supplier.get();
            var completedAt = Instant.now(clock);
            persistence.appendStageLog(TaskStageLogEntry.completed(
                    request.task().taskId(),
                    runningExecution.executionId(),
                    stageStatus.name(),
                    1,
                    summary.toSummary(value),
                    Duration.between(stageStartedAt, completedAt).toMillis(),
                    completedAt));
            return new StageValue<>(value, runningExecution);
        } catch (RuntimeException exception) {
            var failedAt = Instant.now(clock);
            persistence.appendStageLog(TaskStageLogEntry.failed(
                    request.task().taskId(),
                    runningExecution.executionId(),
                    stageStatus.name(),
                    1,
                    exception.getMessage(),
                    Duration.between(stageStartedAt, failedAt).toMillis(),
                    failedAt));
            persistence.saveExecution(runningExecution.transitionTo(
                    ExecutionStatus.FAILED,
                    stageStatus.name(),
                    runningExecution.startedAt(),
                    failedAt));
            throw exception;
        }
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
        List<ReviewPointSnapshot> disabledReviewPointsSnapshot,
        TaskExecutionDocumentReference documentReference) {

    TaskExecutionRequest(
            ReviewTaskRecord task,
            TaskExecutionRecord execution,
            ReviewEngineInput reviewInput,
            List<ReviewPointSnapshot> enabledReviewPointsSnapshot,
            List<ReviewPointSnapshot> disabledReviewPointsSnapshot) {
        this(task, execution, reviewInput, enabledReviewPointsSnapshot, disabledReviewPointsSnapshot, null);
    }

    static TaskExecutionRequest forDocument(
            ReviewTaskRecord task,
            TaskExecutionRecord execution,
            TaskExecutionDocumentReference documentReference,
            List<ReviewPointSnapshot> enabledReviewPointsSnapshot,
            List<ReviewPointSnapshot> disabledReviewPointsSnapshot) {
        return new TaskExecutionRequest(
                task,
                execution,
                null,
                enabledReviewPointsSnapshot,
                disabledReviewPointsSnapshot,
                documentReference);
    }

    TaskExecutionRequest {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(execution, "execution");
        enabledReviewPointsSnapshot = List.copyOf(enabledReviewPointsSnapshot);
        disabledReviewPointsSnapshot = List.copyOf(disabledReviewPointsSnapshot);
        if (reviewInput == null && documentReference == null) {
            throw new IllegalArgumentException("Either reviewInput or documentReference is required");
        }
    }
}

record TaskExecutionDocumentReference(
        Path docxPath,
        String sampleId) {

    TaskExecutionDocumentReference {
        Objects.requireNonNull(docxPath, "docxPath");
        Objects.requireNonNull(sampleId, "sampleId");
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

record PreparedReviewInput(
        ReviewEngineInput reviewInput,
        TaskExecutionRecord execution) {
}

record StageValue<T>(
        T value,
        TaskExecutionRecord execution) {
}

@FunctionalInterface
interface StageSupplier<T> {
    T get();
}

@FunctionalInterface
interface StageSummary<T> {
    String toSummary(T value);
}
