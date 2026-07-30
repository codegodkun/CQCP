package com.cqcp.apiserver.reviewengine;

import static com.cqcp.apiserver.reviewengine.ReviewTaskListModels.*;

import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
final class ReviewTaskListService {

    private final ReviewTaskListRepository repository;

    ReviewTaskListService(ReviewTaskListRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    TaskExecutionPage getTasks(int page, int size, String rawStatusGroup, String rawQuery) {
        if (page < 0) {
            throw new IllegalArgumentException("page 必须大于或等于 0");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size 必须在 1 到 100 之间");
        }
        try {
            Math.multiplyExact(page, size);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("page 与 size 组合超出支持范围", exception);
        }
        var statusGroup = parseStatusGroup(rawStatusGroup);
        var queryPattern = normalizeQuery(rawQuery);
        return repository.findPage(page, size, statusGroup, queryPattern);
    }

    private StatusGroup parseStatusGroup(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return StatusGroup.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("statusGroup 必须为 PROCESSING、COMPLETED 或 FAILED");
        }
    }

    private String normalizeQuery(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        var normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 200) {
            throw new IllegalArgumentException("q 长度不得超过 200");
        }
        return "%" + normalized
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_") + "%";
    }
}
