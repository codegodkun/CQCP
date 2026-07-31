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
    private final RuleSetActivationGate ruleSetActivationGate;
    private final ConsistencyRuntimeRelease consistencyRuntimeRelease;
    private final Clock clock;

    public TaskExecutionStateMachine(
            MinimalReviewEngine reviewEngine,
            ResultComposer resultComposer,
            Clock clock) {
        this(
                reviewEngine,
                resultComposer,
                new ParserBackedReviewInputPreparer(new DocxWordParserSpike()),
                new RuleSetActivationGate(),
                ConsistencyRuntimeRelease.accepted(),
                clock);
    }

    public TaskExecutionStateMachine(
            MinimalReviewEngine reviewEngine,
            ResultComposer resultComposer,
            ParserBackedReviewInputPreparer reviewInputPreparer,
            Clock clock) {
        this(
                reviewEngine,
                resultComposer,
                reviewInputPreparer,
                new RuleSetActivationGate(),
                ConsistencyRuntimeRelease.accepted(),
                clock);
    }

    TaskExecutionStateMachine(
            MinimalReviewEngine reviewEngine,
            ResultComposer resultComposer,
            ParserBackedReviewInputPreparer reviewInputPreparer,
            RuleSetActivationGate ruleSetActivationGate,
            ConsistencyRuntimeRelease consistencyRuntimeRelease,
            Clock clock) {
        this.reviewEngine = Objects.requireNonNull(reviewEngine, "reviewEngine");
        this.resultComposer = Objects.requireNonNull(resultComposer, "resultComposer");
        this.reviewInputPreparer = Objects.requireNonNull(reviewInputPreparer, "reviewInputPreparer");
        this.ruleSetActivationGate = Objects.requireNonNull(ruleSetActivationGate, "ruleSetActivationGate");
        this.consistencyRuntimeRelease =
                Objects.requireNonNull(consistencyRuntimeRelease, "consistencyRuntimeRelease");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    // ── Public entry (InMemory tests use empty owner) ──

    public TaskExecutionRunResult execute(
            TaskExecutionRequest request,
            TaskExecutionPersistence persistence) {
        return execute(request, persistence, "");
    }

    // ── Package-private entry with explicit stageOwner (JDBC worker path) ──

    TaskExecutionRunResult execute(
            TaskExecutionRequest request,
            TaskExecutionPersistence persistence,
            String stageOwner) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(persistence, "persistence");

        if (request.execution().status().isTerminal()) {
            throw new IllegalStateException(
                    "Cannot execute terminal execution: " + request.execution().executionId());
        }

        var executionStartedAt = Instant.now(clock);

        // ── Phase 1: PARSING → INDEXING → PLANNING → BUILDING_EVIDENCE ──
        PreparedReviewInput preparedInput;
        try {
            preparedInput = request.documentReference() == null
                    ? new PreparedReviewInput(request.reviewInput(), request.execution())
                    : runPreparationStages(request, request.execution(), persistence,
                            executionStartedAt, stageOwner);
        } catch (RuntimeException e) {
            throw e;
        }

        // ── Phase 2: REVIEWING_RULES ──
        TaskExecutionRecord currentExec = preparedInput.execution();
        ReviewEngineResult reviewEngineResult;
        var reviewStartedAt = Instant.now(clock);
        try {
            currentExec = persistence.startStage(
                    currentExec.status(), currentExec.currentStage(),
                    ExecutionStatus.REVIEWING_RULES, "REVIEWING_RULES",
                    currentExec, stageOwner,
                    executionStartedAt, reviewStartedAt);
            reviewEngineResult = runReviewingRulesStage(
                    request, preparedInput.reviewInput(), currentExec,
                    persistence, reviewStartedAt, stageOwner);
        } catch (RuntimeException exception) {
            persistence.failExecution(
                    currentExec.currentStage(),
                    currentExec.status(), currentExec.currentStage(),
                    currentExec, stageOwner,
                    request.task().taskId(), request.execution().executionId(),
                    "执行阶段失败", Instant.now(clock));
            throw exception;
        }

        // ── Phase 3a: COMPOSING startStage ──
        var composingStartedAt = Instant.now(clock);
        TaskExecutionRecord composingExec;
        try {
            composingExec = persistence.startStage(
                    ExecutionStatus.REVIEWING_RULES, "REVIEWING_RULES",
                    ExecutionStatus.COMPOSING, "COMPOSING",
                    currentExec, stageOwner,
                    executionStartedAt, composingStartedAt);
        } catch (RuntimeException e) {
            persistence.failExecution(
                    currentExec.currentStage(),
                    currentExec.status(), currentExec.currentStage(),
                    currentExec, stageOwner,
                    request.task().taskId(), request.execution().executionId(),
                    "执行阶段失败", Instant.now(clock));
            throw e;
        }

        // ── Phase 3b: compose snapshot + terminal persistence ──
        try {
            var snapshot = composeSnapshot(request, composingExec, reviewEngineResult);
            var finishedAt = Instant.now(clock);
            persistence.completeExecution(
                    composingExec, snapshot, stageOwner, composingStartedAt, finishedAt);

            var terminalStatus = snapshot.status() == SnapshotStatus.PARTIAL_SUCCESS
                    ? ExecutionStatus.PARTIAL_SUCCESS
                    : ExecutionStatus.SUCCESS;
            var terminalExec = composingExec.transitionTo(
                    terminalStatus, terminalStatus.name(),
                    composingExec.startedAt(), finishedAt);
            return new TaskExecutionRunResult(terminalExec, snapshot);
        } catch (RuntimeException exception) {
            try {
                persistence.failExecution(
                        "COMPOSING",
                        ExecutionStatus.COMPOSING, "COMPOSING",
                        composingExec, stageOwner,
                        request.task().taskId(), request.execution().executionId(),
                        "执行阶段失败", Instant.now(clock));
            } catch (Exception inner) {
                throw new FailExecutionAttempted(
                        request.execution().executionId(), inner);
            }
            throw exception;
        }
    }

    private PreparedReviewInput runPreparationStages(
            TaskExecutionRequest request,
            TaskExecutionRecord rawExecution,
            TaskExecutionPersistence persistence,
            Instant executionStartedAt,
            String stageOwner) {
        var parsed = runStage(request, rawExecution,
                rawExecution.status(), rawExecution.currentStage(),
                ExecutionStatus.PARSING, persistence,
                executionStartedAt, Instant.now(clock), stageOwner,
                () -> reviewInputPreparer.parse(request.documentReference()),
                r -> "SUCCESS");

        var indexed = runStage(request, parsed.execution(),
                ExecutionStatus.PARSING, "PARSING",
                ExecutionStatus.INDEXING, persistence,
                executionStartedAt, Instant.now(clock), stageOwner,
                () -> reviewInputPreparer.index(parsed.value()),
                r -> "SUCCESS");

        var planned = runStage(request, indexed.execution(),
                ExecutionStatus.INDEXING, "INDEXING",
                ExecutionStatus.PLANNING, persistence,
                executionStartedAt, Instant.now(clock), stageOwner,
                () -> reviewInputPreparer.plan(indexed.value()),
                r -> "SUCCESS");

        var built = runStage(request, planned.execution(),
                ExecutionStatus.PLANNING, "PLANNING",
                ExecutionStatus.BUILDING_EVIDENCE, persistence,
                executionStartedAt, Instant.now(clock), stageOwner,
                () -> buildEvidenceForExecution(request, planned.value()),
                input -> input.pointEvidences().values().stream()
                        .allMatch(e -> e.status() == EvidenceStatus.CONFIRMED)
                        ? "SUCCESS" : "PARTIAL_SUCCESS");

        return new PreparedReviewInput(built.value(), built.execution());
    }

    private ReviewEngineResult runReviewingRulesStage(
            TaskExecutionRequest request,
            ReviewEngineInput reviewInput,
            TaskExecutionRecord runningExecution,
            TaskExecutionPersistence persistence,
            Instant stageStartedAt,
            String stageOwner) {
        validateReviewInputBinding(
                runningExecution.versionReferences().ruleSetVersion(),
                reviewInput);
        var result = reviewEngine.review(reviewInput);
        var completedAt = Instant.now(clock);
        persistence.appendCompletedStageLog(
                TaskStageLogEntry.completed(
                        request.task().taskId(),
                        runningExecution.executionId(),
                        "REVIEWING_RULES", 1,
                        deriveReviewStageSummaryStatus(result),
                        Duration.between(stageStartedAt, completedAt).toMillis(),
                        completedAt),
                stageOwner,
                ExecutionStatus.REVIEWING_RULES, "REVIEWING_RULES");
        return result;
    }

    private ReviewEngineInput buildEvidenceForExecution(
            TaskExecutionRequest request,
            EvidenceBuildPlan plan) {
        var selection = selectRuntimeRuleSet(
                request.execution().versionReferences().ruleSetVersion());
        if (selection == null) {
            return reviewInputPreparer.build(request, plan);
        }
        return reviewInputPreparer.build(request, plan, selection);
    }

    private void validateReviewInputBinding(
            String executionRuleSetVersion,
            ReviewEngineInput reviewInput) {
        var selected = selectRuntimeRuleSet(executionRuleSetVersion);
        var carried = reviewInput.runtimeRuleSetSnapshot();
        if (selected == null) {
            if (carried != null) {
                throw new IllegalStateException(
                        "Legacy execution must not carry a runtime rule-set snapshot");
            }
            return;
        }
        if (carried == null) {
            throw new IllegalStateException(
                    "Consistency execution requires a runtime rule-set snapshot");
        }
        if (!selected.equals(carried)) {
            throw new IllegalStateException(
                    "Execution rule-set version does not match runtime snapshot");
        }
    }

    private RuntimeRuleSetSnapshot selectRuntimeRuleSet(String version) {
        var gateResult = ruleSetActivationGate.request(
                version,
                consistencyRuntimeRelease.readyFor(version));
        return switch (gateResult.status()) {
            case RuleSetActivationGate.LEGACY_ALLOWED -> null;
            case RuleSetActivationGate.READY -> {
                if (gateResult.snapshot() == null) {
                    throw new IllegalStateException(
                            "Rule-set activation returned READY without snapshot");
                }
                yield gateResult.snapshot();
            }
            case RuleSetActivationGate.POLICY_NOT_READY,
                    RuleSetActivationGate.POLICY_ASSET_INVALID,
                    RuleSetActivationGate.UNKNOWN_RULE_SET_VERSION ->
                    throw new IllegalStateException(
                            "Rule-set activation rejected: " + gateResult.status());
            default -> throw new IllegalStateException(
                    "Unknown rule-set activation status: " + gateResult.status());
        };
    }

    private <T> StageValue<T> runStage(
            TaskExecutionRequest request,
            TaskExecutionRecord currentExecution,
            ExecutionStatus fromStatus, String fromCurrentStage,
            ExecutionStatus stageStatus,
            TaskExecutionPersistence persistence,
            Instant executionStartedAt, Instant stageStartedAt,
            String stageOwner,
            StageSupplier<T> supplier,
            StageSummary<T> summary) {
        var running = persistence.startStage(
                fromStatus, fromCurrentStage,
                stageStatus, stageStatus.name(),
                currentExecution, stageOwner,
                executionStartedAt, stageStartedAt);
        try {
            var value = supplier.get();
            var completedAt = Instant.now(clock);
            persistence.appendCompletedStageLog(
                    TaskStageLogEntry.completed(
                            request.task().taskId(),
                            running.executionId(),
                            stageStatus.name(), 1,
                            summary.toSummary(value),
                            Duration.between(stageStartedAt, completedAt).toMillis(),
                            completedAt),
                    stageOwner,
                    stageStatus, stageStatus.name());
            return new StageValue<>(value, running);
        } catch (RuntimeException exception) {
            var failedAt = Instant.now(clock);
            persistence.failExecution(
                    stageStatus.name(),
                    stageStatus, stageStatus.name(),
                    running, stageOwner,
                    request.task().taskId(), request.execution().executionId(),
                    "执行阶段失败", failedAt);
            throw exception;
        }
    }

    private String deriveReviewStageSummaryStatus(ReviewEngineResult result) {
        return result.summary().notConcludedCount() > 0 ? "PARTIAL_SUCCESS" : "SUCCESS";
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

// ── Records ──────────────────────────────────────────────────────

record TaskExecutionRequest(
        ReviewTaskRecord task,
        TaskExecutionRecord execution,
        ReviewEngineInput reviewInput,
        List<ReviewPointSnapshot> enabledReviewPointsSnapshot,
        List<ReviewPointSnapshot> disabledReviewPointsSnapshot,
        TaskExecutionDocumentReference documentReference) {

    TaskExecutionRequest(ReviewTaskRecord task, TaskExecutionRecord execution,
                         ReviewEngineInput reviewInput,
                         List<ReviewPointSnapshot> enabledReviewPointsSnapshot,
                         List<ReviewPointSnapshot> disabledReviewPointsSnapshot) {
        this(task, execution, reviewInput, enabledReviewPointsSnapshot, disabledReviewPointsSnapshot, null);
    }

    static TaskExecutionRequest forDocument(ReviewTaskRecord task, TaskExecutionRecord execution,
                                            TaskExecutionDocumentReference documentReference,
                                            List<ReviewPointSnapshot> enabledReviewPointsSnapshot,
                                            List<ReviewPointSnapshot> disabledReviewPointsSnapshot) {
        return new TaskExecutionRequest(task, execution, null,
                enabledReviewPointsSnapshot, disabledReviewPointsSnapshot, documentReference);
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

record TaskExecutionDocumentReference(Path docxPath, String sampleId, byte[] documentSnapshot) {

    TaskExecutionDocumentReference(Path docxPath, String sampleId) {
        this(docxPath, sampleId, null);
    }

    static TaskExecutionDocumentReference forSnapshot(
            String documentReference,
            String sampleId,
            byte[] documentSnapshot) {
        Objects.requireNonNull(documentReference, "documentReference");
        return new TaskExecutionDocumentReference(
                Path.of(documentReference),
                sampleId,
                Objects.requireNonNull(documentSnapshot, "documentSnapshot"));
    }

    TaskExecutionDocumentReference {
        Objects.requireNonNull(docxPath, "docxPath");
        Objects.requireNonNull(sampleId, "sampleId");
        documentSnapshot = documentSnapshot == null ? null : documentSnapshot.clone();
    }

    @Override
    public byte[] documentSnapshot() {
        return documentSnapshot == null ? null : documentSnapshot.clone();
    }

    boolean hasDocumentSnapshot() {
        return documentSnapshot != null;
    }
}

record ReviewTaskRecord(String taskId, String contractName, Map<String, String> structuredFieldsSnapshot) {
    ReviewTaskRecord { structuredFieldsSnapshot = Map.copyOf(structuredFieldsSnapshot); }
}

record TaskExecutionRecord(
        String executionId, String taskId, ExecutionStatus status, String currentStage,
        VersionReferences versionReferences, String modelProfileCode,
        String providerType, String modelName, String endpointAlias,
        Instant startedAt, Instant finishedAt) {

    TaskExecutionRecord {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(currentStage, "currentStage");
        Objects.requireNonNull(versionReferences, "versionReferences");
    }

    TaskExecutionRecord transitionTo(ExecutionStatus nextStatus, String nextStage,
                                     Instant nextStartedAt, Instant nextFinishedAt) {
        return new TaskExecutionRecord(executionId, taskId, nextStatus, nextStage,
                versionReferences, modelProfileCode, providerType, modelName, endpointAlias,
                nextStartedAt, nextFinishedAt);
    }
}

enum ExecutionStatus {
    CREATED, QUEUED, PARSING, INDEXING, PLANNING, BUILDING_EVIDENCE,
    REVIEWING_RULES, REVIEWING_MODEL, COMPOSING,
    SUCCESS, PARTIAL_SUCCESS, FAILED, CANCELLED;

    boolean isTerminal() {
        return this == SUCCESS || this == PARTIAL_SUCCESS || this == FAILED || this == CANCELLED;
    }
}

record TaskStageLogEntry(
        String taskId, String executionId, String stageName, int attempt,
        String eventType, String summaryStatus, String businessReason,
        String diagnosticCode, Long durationMs, Map<String, Object> detailPayload,
        Instant createdAt) {

    TaskStageLogEntry { detailPayload = Map.copyOf(detailPayload); }

    static TaskStageLogEntry started(String taskId, String executionId, String stageName,
                                     int attempt, Instant createdAt) {
        return new TaskStageLogEntry(taskId, executionId, stageName, attempt,
                "STARTED", "RUNNING", null, null, null, Map.of(), createdAt);
    }

    static TaskStageLogEntry completed(String taskId, String executionId, String stageName,
                                       int attempt, String summaryStatus, long durationMs, Instant createdAt) {
        return new TaskStageLogEntry(taskId, executionId, stageName, attempt,
                "COMPLETED", summaryStatus, null, null, durationMs, Map.of(), createdAt);
    }

    static TaskStageLogEntry failed(String taskId, String executionId, String stageName,
                                    int attempt, String businessReason, long durationMs, Instant createdAt) {
        return new TaskStageLogEntry(taskId, executionId, stageName, attempt,
                "FAILED", "FAILED", businessReason, null, durationMs, Map.of(), createdAt);
    }
}

record TaskExecutionRunResult(TaskExecutionRecord execution, ReviewResultSnapshot snapshot) {}

interface TaskExecutionPersistence {

    void saveExecution(TaskExecutionRecord execution);

    void appendStageLog(TaskStageLogEntry entry);

    void saveSnapshot(ReviewResultSnapshot snapshot);

    // ── Lifecycle default methods (InMemory-compatible) ──────────

    default TaskExecutionRecord startStage(
            ExecutionStatus fromStatus, String fromCurrentStage,
            ExecutionStatus toStatus, String toStageName,
            TaskExecutionRecord current, String stageOwner,
            Instant executionStartedAt, Instant stageStartedAt) {
        var next = current.transitionTo(toStatus, toStageName,
                current.startedAt() != null ? current.startedAt() : executionStartedAt, null);
        saveExecution(next);
        appendStageLog(TaskStageLogEntry.started(
                next.taskId(), next.executionId(), toStageName, 1, stageStartedAt));
        return next;
    }

    default void failExecution(
            String targetStageName,
            ExecutionStatus fromStatus, String fromCurrentStage,
            TaskExecutionRecord current, String stageOwner,
            String taskId, String executionId,
            String businessReason, Instant failedAt) {
        var failed = current.transitionTo(ExecutionStatus.FAILED, targetStageName,
                current.startedAt(), failedAt);
        saveExecution(failed);
        appendStageLog(TaskStageLogEntry.failed(
                taskId, executionId, targetStageName, 1, businessReason,
                Duration.between(current.startedAt() != null ? current.startedAt() : failedAt, failedAt).toMillis(),
                failedAt));
    }

    /** Persist snapshot, COMPOSING COMPLETED log, and terminal execution atomically. */
    default void completeExecution(
            TaskExecutionRecord composingExecution, ReviewResultSnapshot snapshot,
            String stageOwner, Instant composingStartedAt, Instant finishedAt) {
        saveSnapshot(snapshot);
        var terminalStatus = snapshot.status() == SnapshotStatus.PARTIAL_SUCCESS
                ? ExecutionStatus.PARTIAL_SUCCESS : ExecutionStatus.SUCCESS;
        appendStageLog(TaskStageLogEntry.completed(
                composingExecution.taskId(), composingExecution.executionId(),
                "COMPOSING", 1, terminalStatus.name(),
                Duration.between(composingStartedAt, finishedAt).toMillis(), finishedAt));
        var terminal = composingExecution.transitionTo(
                terminalStatus, terminalStatus.name(),
                composingExecution.startedAt(), finishedAt);
        saveExecution(terminal);
    }

    /**
     * Append a COMPLETED stage log with explicit owner and status/current_stage guard.
     * InMemory default delegates to plain appendStageLog; JDBC override uses
     * INSERT...SELECT with owner+status+current_stage guard.
     */
    default void appendCompletedStageLog(TaskStageLogEntry entry, String stageOwner,
                                          ExecutionStatus currentStatus, String currentStage) {
        appendStageLog(entry);
    }
}

record PreparedReviewInput(ReviewEngineInput reviewInput, TaskExecutionRecord execution) {}

record StageValue<T>(T value, TaskExecutionRecord execution) {}

@FunctionalInterface
interface StageSupplier<T> { T get(); }

@FunctionalInterface
interface StageSummary<T> { String toSummary(T value); }

/**
 * Marker thrown when failExecution fails inside COMPOSING catch.
 * Worker must NOT attempt FAILED compensation after this marker.
 */
final class FailExecutionAttempted extends RuntimeException {
    private final String executionId;
    FailExecutionAttempted(String executionId, Throwable cause) {
        super("failExecution attempted for " + executionId, cause);
        this.executionId = executionId;
    }
    String executionId() { return executionId; }
}
