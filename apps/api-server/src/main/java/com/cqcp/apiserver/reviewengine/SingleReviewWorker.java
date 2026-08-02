package com.cqcp.apiserver.reviewengine;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

public class SingleReviewWorker {

    private static final Logger log = LoggerFactory.getLogger(SingleReviewWorker.class);

    private final boolean enabled;
    private final String owner;
    private final long leaseDurationSeconds;
    private final SingleReviewWorkerRepository repository;
    private final TaskExecutionStateMachine stateMachine;
    private final JdbcTaskExecutionPersistence persistence;
    private final LegacyReviewPointSnapshotCatalog legacyCatalog;
    private final LocalReviewDocumentStore documentStore;
    private final Clock clock;

    private final AtomicBoolean running = new AtomicBoolean(false);

    public SingleReviewWorker(boolean enabled, String owner, long leaseDurationSeconds,
            SingleReviewWorkerRepository repository, TaskExecutionStateMachine stateMachine,
            JdbcTaskExecutionPersistence persistence, LegacyReviewPointSnapshotCatalog legacyCatalog,
            LocalReviewDocumentStore documentStore, Clock clock) {
        this.enabled = enabled;
        this.owner = Objects.requireNonNull(owner, "owner");
        this.leaseDurationSeconds = leaseDurationSeconds;
        this.repository = Objects.requireNonNull(repository, "repository");
        this.stateMachine = Objects.requireNonNull(stateMachine, "stateMachine");
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.legacyCatalog = Objects.requireNonNull(legacyCatalog, "legacyCatalog");
        this.documentStore = Objects.requireNonNull(documentStore, "documentStore");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    // ── Scheduled entry — enabled guard only here ──

    @Scheduled(fixedDelayString = "${cqcp.review.worker.poll-delay-ms:1000}")
    void scheduledTrigger() {
        if (!enabled) return;
        runOnce();
    }

    // ── Public entry — ignores enabled ──

    public boolean runOnce() {
        if (!running.compareAndSet(false, true)) {
            log.debug("Worker still processing previous execution; skipping this poll tick");
            return false;
        }
        JdbcTaskExecutionPersistence.setCurrentOwner(owner);
        try {
            var claimed = repository.claimOne(owner, leaseDurationSeconds);
            if (claimed.isEmpty()) return false;
            var executionId = claimed.orElseThrow();
            try {
                process(executionId);
            } catch (Exception e) {
                log.warn("Worker process failed executionId={} category={}", executionId, e.getClass().getSimpleName());
            }
            return true;
        } catch (Exception e) {
            log.warn("Worker runOnce claim failed category={}", e.getClass().getSimpleName());
            return false;
        } finally {
            JdbcTaskExecutionPersistence.clearCurrentOwner();
            running.set(false);
        }
    }

    void process(String executionId) {
        Objects.requireNonNull(executionId, "executionId");
        JdbcTaskExecutionPersistence.setCurrentOwner(owner);
        try {
            var loadResult = persistence.loadExecutionAndTask(executionId, owner);
            if (loadResult.isEmpty()) {
                log.warn("Worker could not load execution={}", executionId);
                return;
            }
            var state = loadResult.orElseThrow();

            var documentSnapshot = documentStore.readDocumentSnapshot(
                    state.task().taskId(),
                    state.documentReference());
            if (documentSnapshot.isEmpty()) {
                failNonTerminal(executionId, state.execution());
                return;
            }
            var snapshot = documentSnapshot.orElseThrow();
            if (!snapshot.matches(state.documentSizeBytes(), state.documentSha256())) {
                failNonTerminal(executionId, state.execution());
                return;
            }

            var enabledPoints = legacyCatalog.enabledSnapshots();
            var disabledPoints = legacyCatalog.disabledSnapshots();

            var taskRecord = new ReviewTaskRecord(
                    state.task().taskId(), state.task().contractName(), state.task().structuredFieldsSnapshot());
            var docxRef = TaskExecutionDocumentReference.forSnapshot(
                    state.documentReference(),
                    state.task().taskId(),
                    snapshot.content());
            var request = TaskExecutionRequest.forDocument(
                    taskRecord, state.execution(), docxRef, enabledPoints, disabledPoints);

            stateMachine.execute(request, persistence, owner);
        } catch (FailExecutionAttempted e) {
            log.warn("Worker FailExecutionAttempted for execution={}", executionId);
        } catch (Exception e) {
            log.warn("Worker process failed execution={} errorType={}", executionId, e.getClass().getSimpleName());
            failNonTerminalOwnerGuard(executionId);
        }
    }

    private void failNonTerminal(String executionId, TaskExecutionRecord knownExec) {
        try {
            if (knownExec.status().isTerminal()) return;
            persistence.failExecution(knownExec.currentStage(), knownExec.status(), knownExec.currentStage(),
                    knownExec, owner, knownExec.taskId(), executionId, "执行阶段失败", Instant.now(clock));
            log.info("Worker failed execution={} errorType=ExecutionFailed", executionId);
        } catch (Exception inner) {
            log.warn("Worker failNonTerminal failed execution={} category={}", executionId, inner.getClass().getSimpleName());
        }
    }

    private void failNonTerminalOwnerGuard(String executionId) {
        try {
            var row = persistence.loadStatus(executionId, owner);
            if (row == null) return;
            if (isTerminalString(row.status())) return;
            persistence.failExecutionRaw(row.currentStage(), ExecutionStatus.valueOf(row.status()),
                    row.currentStage(), owner, row.taskId(), executionId, "执行阶段失败", Instant.now(clock));
            log.info("Worker failed execution={} errorType=OwnerGuardFailed", executionId);
        } catch (Exception inner) {
            log.warn("Worker outer failExecution failed execution={} category={}", executionId, inner.getClass().getSimpleName());
        }
    }

    private static boolean isTerminalString(String s) {
        return "SUCCESS".equals(s) || "PARTIAL_SUCCESS".equals(s) || "FAILED".equals(s) || "CANCELLED".equals(s);
    }

    String owner() { return owner; }
}
