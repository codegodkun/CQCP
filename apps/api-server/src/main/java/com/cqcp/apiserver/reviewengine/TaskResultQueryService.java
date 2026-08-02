package com.cqcp.apiserver.reviewengine;

import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public final class TaskResultQueryService {

    private final TaskResultStore store;

    public TaskResultQueryService(TaskResultStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    public ReviewResultSnapshot getResult(String taskId) {
        Objects.requireNonNull(taskId, "taskId");

        return store.findLatestSnapshot(taskId)
                .orElseGet(() -> {
                    if (store.hasTask(taskId)) {
                        throw new TaskResultNotReadyException(taskId);
                    }
                    throw new TaskResultNotFoundException(taskId);
                });
    }

    public ReviewResultSnapshot getResult(String taskId, String executionId) {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(executionId, "executionId");

        return store.findSnapshot(taskId, executionId)
                .orElseGet(() -> {
                    if (store.hasExecution(taskId, executionId)) {
                        throw new TaskResultNotReadyException(taskId, executionId);
                    }
                    throw new TaskResultNotFoundException(taskId, executionId);
                });
    }
}

interface TaskResultStore {

    boolean hasTask(String taskId);

    boolean hasExecution(String taskId, String executionId);

    java.util.Optional<ReviewResultSnapshot> findLatestSnapshot(String taskId);

    java.util.Optional<ReviewResultSnapshot> findSnapshot(String taskId, String executionId);
}

final class TaskResultNotFoundException extends RuntimeException {

    TaskResultNotFoundException(String taskId) {
        super("Task result not found: " + taskId);
    }

    TaskResultNotFoundException(String taskId, String executionId) {
        super("Task result not found: " + taskId + "/" + executionId);
    }
}

final class TaskResultNotReadyException extends RuntimeException {

    TaskResultNotReadyException(String taskId) {
        super("Task result not ready: " + taskId);
    }

    TaskResultNotReadyException(String taskId, String executionId) {
        super("Task result not ready: " + taskId + "/" + executionId);
    }
}
