package com.cqcp.apiserver.reviewengine;

import static com.cqcp.apiserver.reviewengine.ReviewDocumentModels.DocumentMetadata;

import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class ReviewDocumentRepository {

    private static final String FIND_DOCUMENT_SQL = """
            SELECT
                t.task_id,
                e.execution_id,
                t.contract_name,
                e.parser_version,
                t.contract_metadata ->> 'originalFileName' AS original_file_name,
                t.contract_metadata ->> 'documentReference' AS document_reference,
                t.contract_metadata ->> 'sizeBytes' AS size_bytes,
                t.contract_metadata ->> 'sha256' AS sha256
            FROM task t
            JOIN execution e ON e.task_id = t.task_id
            WHERE t.task_id = ?
              AND e.execution_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    ReviewDocumentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    Optional<DocumentMetadata> findDocument(String taskId, String executionId) {
        var rows = jdbcTemplate.query(
                FIND_DOCUMENT_SQL,
                (rs, rowNum) -> new DocumentMetadata(
                        rs.getString("task_id"),
                        rs.getString("execution_id"),
                        rs.getString("contract_name"),
                        rs.getString("parser_version"),
                        rs.getString("original_file_name"),
                        rs.getString("document_reference"),
                        parseSize(rs.getString("size_bytes")),
                        rs.getString("sha256")),
                taskId,
                executionId);
        if (rows.size() > 1) {
            throw new IllegalStateException("Multiple documents for exact execution identity");
        }
        return rows.stream().findFirst();
    }

    private static long parseSize(String value) {
        if (value == null) {
            return -1;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return -1;
        }
    }
}
