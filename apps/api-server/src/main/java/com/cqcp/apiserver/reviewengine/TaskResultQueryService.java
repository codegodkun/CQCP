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
}

interface TaskResultStore {

    boolean hasTask(String taskId);

    java.util.Optional<ReviewResultSnapshot> findLatestSnapshot(String taskId);
}

final class TaskResultNotFoundException extends RuntimeException {

    TaskResultNotFoundException(String taskId) {
        super("Task result not found: " + taskId);
    }
}

final class TaskResultNotReadyException extends RuntimeException {

    TaskResultNotReadyException(String taskId) {
        super("Task result not ready: " + taskId);
    }
}
