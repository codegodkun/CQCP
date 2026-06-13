package com.cqcp.apiserver.reviewengine;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * TASK-021 的最小内存态结果承接层。
 * 仅用于当前 MVP 最小闭环下把 TASK-020 的 snapshot 保存动作衔接到只读查询接口，
 * 不代表最终基于 review_result_snapshot 的持久化查询实现。
 */
@Component
public final class InMemoryTaskResultStore implements TaskExecutionPersistence, TaskResultStore {

    private final Map<String, TaskExecutionRecord> executionsById = new ConcurrentHashMap<>();
    private final Map<String, ReviewResultSnapshot> latestSnapshotsByTaskId = new ConcurrentHashMap<>();
    private final Set<String> knownTaskIds = ConcurrentHashMap.newKeySet();

    @Override
    public void saveExecution(TaskExecutionRecord execution) {
        executionsById.put(execution.executionId(), execution);
        knownTaskIds.add(execution.taskId());
    }

    @Override
    public void appendStageLog(TaskStageLogEntry entry) {
        knownTaskIds.add(entry.taskId());
    }

    @Override
    public void saveSnapshot(ReviewResultSnapshot snapshot) {
        latestSnapshotsByTaskId.compute(snapshot.taskId(), (taskId, existing) -> {
            if (existing == null || snapshot.createdAt().isAfter(existing.createdAt())) {
                return snapshot;
            }
            return existing;
        });
        knownTaskIds.add(snapshot.taskId());
    }

    @Override
    public boolean hasTask(String taskId) {
        return knownTaskIds.contains(taskId) || latestSnapshotsByTaskId.containsKey(taskId);
    }

    @Override
    public Optional<ReviewResultSnapshot> findLatestSnapshot(String taskId) {
        return Optional.ofNullable(latestSnapshotsByTaskId.get(taskId));
    }

    TaskExecutionRecord findExecution(String executionId) {
        return executionsById.get(executionId);
    }
}
