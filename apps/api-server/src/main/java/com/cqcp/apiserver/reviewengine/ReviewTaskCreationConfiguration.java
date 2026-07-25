package com.cqcp.apiserver.reviewengine;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the {@link ExecutionBindingCatalog} with a system-UTC clock
 * and configures upload-related settings.
 */
@Configuration
class ReviewTaskCreationConfiguration {

    @Bean
    ExecutionBindingCatalog executionBindingCatalog(JdbcExecutionBindingRepository repository) {
        return new ExecutionBindingCatalog(repository, Clock.systemUTC());
    }

    @Bean
    LocalReviewDocumentStore localReviewDocumentStore(
            @Value("${cqcp.review.upload-root}") java.nio.file.Path uploadRoot) {
        return new LocalReviewDocumentStore(uploadRoot);
    }

    @Bean
    ReviewTaskCreationService reviewTaskCreationService(
            ReviewTaskCreationRepository repository,
            ExecutionBindingCatalog catalog,
            LocalReviewDocumentStore documentStore,
            @Value("${cqcp.review.max-upload-bytes}") long maxUploadBytes) {
        return new ReviewTaskCreationService(
                repository, catalog, documentStore, maxUploadBytes);
    }
}
