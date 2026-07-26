package com.cqcp.apiserver.reviewengine;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
class SingleReviewWorkerConfiguration {

    /** Single system-UTC clock shared by state machine and worker. */
    @Bean
    Clock systemUtcClock() {
        return Clock.systemUTC();
    }

    @Bean
    SingleReviewWorker singleReviewWorker(
            @Value("${cqcp.review.worker.enabled:true}") boolean enabled,
            @Value("${cqcp.review.worker.lease-duration-seconds:3600}") long leaseDurationSeconds,
            SingleReviewWorkerRepository repository,
            JdbcTaskExecutionPersistence persistence,
            LegacyReviewPointSnapshotCatalog legacyCatalog,
            LocalReviewDocumentStore documentStore,
            Clock systemUtcClock) {
        var owner = "worker-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        var stateMachine = new TaskExecutionStateMachine(
                new MinimalReviewEngine(), new ResultComposer(), systemUtcClock);
        return new SingleReviewWorker(
                enabled, owner, leaseDurationSeconds,
                repository, stateMachine, persistence, legacyCatalog, documentStore, systemUtcClock);
    }

    @Bean
    SingleReviewWorkerRepository singleReviewWorkerRepository(JdbcTemplate jdbcTemplate) {
        return new SingleReviewWorkerRepository(jdbcTemplate);
    }

    @Bean
    JdbcTaskExecutionPersistence jdbcTaskExecutionPersistence(
            JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        return new JdbcTaskExecutionPersistence(jdbcTemplate, objectMapper);
    }

    @Bean
    LegacyReviewPointSnapshotCatalog legacyReviewPointSnapshotCatalog() {
        return new LegacyReviewPointSnapshotCatalog();
    }
}
