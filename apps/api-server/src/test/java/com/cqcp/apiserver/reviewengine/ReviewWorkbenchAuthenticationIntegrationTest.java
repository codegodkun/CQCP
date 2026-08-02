package com.cqcp.apiserver.reviewengine;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "cqcp.review.worker.enabled=false",
        "cqcp.admin-api.admin-token=admin-test-token",
        "cqcp.admin-api.readonly-token=readonly-test-token"
})
@AutoConfigureMockMvc
class ReviewWorkbenchAuthenticationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @SpyBean
    private ReviewTaskListService taskListService;

    @SpyBean
    private ReviewDocumentService documentService;

    @BeforeEach
    void resetSpies() {
        clearInvocations(taskListService, documentService);
    }

    @ParameterizedTest(name = "anonymous {0} {1}")
    @MethodSource("anonymousWorkbenchRequests")
    void taskEnumerationAndDocumentReadsRejectAnonymousRequestsBeforeServices(
            String method,
            String path) throws Exception {
        var request = "HEAD".equals(method) ? head(path) : get(path);
        var result = mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("MANAGEMENT_AUTHENTICATION_REQUIRED"));
        if (path.endsWith("/document") || path.endsWith("/document;v=1")) {
            result.andExpect(header().doesNotExist(HttpHeaders.CONTENT_DISPOSITION))
                    .andExpect(header().doesNotExist("X-CQCP-Document-SHA256"));
        }
        verifyNoInteractions(taskListService, documentService);
    }

    @Test
    void unknownCredentialCannotEnumerateTasks() throws Exception {
        mockMvc.perform(get("/api/review/tasks")
                        .header("Authorization", "Bearer unknown-token"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(taskListService);
    }

    @Test
    void readonlyManagementCredentialCanEnumerateTasks() throws Exception {
        mockMvc.perform(get("/api/review/tasks")
                        .header("Authorization", "Bearer readonly-test-token"))
                .andExpect(status().isOk());

        verify(taskListService).getTasks(0, 20, null, null);
    }

    @Test
    void adminCredentialCanEnumerateTasks() throws Exception {
        mockMvc.perform(get("/api/review/tasks")
                        .header("Authorization", "Bearer admin-test-token"))
                .andExpect(status().isOk());

        verify(taskListService).getTasks(0, 20, null, null);
    }

    @Test
    void overflowingPaginationIsRejectedAsBadRequest() throws Exception {
        mockMvc.perform(get("/api/review/tasks")
                        .queryParam("page", Integer.toString(Integer.MAX_VALUE))
                        .queryParam("size", "100")
                        .header("Authorization", "Bearer admin-test-token"))
                .andExpect(status().isBadRequest());

        verify(taskListService).getTasks(Integer.MAX_VALUE, 100, null, null);
    }

    private static java.util.List<String> anonymousWorkbenchPaths() {
        return java.util.List.of(
                "/api/review/tasks",
                "/api;v=1/review/tasks",
                "/api/review;v=1/tasks",
                "/api/review/tasks;v=1",
                "/api/review/tasks/task-001/executions/execution-001/document-preview",
                "/api;v=1/review/tasks/task-001/executions/execution-001/document-preview",
                "/api/review;v=1/tasks/task-001/executions/execution-001/document-preview",
                "/api/review/tasks;v=1/task-001/executions/execution-001/document-preview",
                "/api/review/tasks/task-001;v=1/executions/execution-001/document-preview",
                "/api/review/tasks/task-001/executions;v=1/execution-001/document-preview",
                "/api/review/tasks/task-001/executions/execution-001;v=1/document-preview",
                "/api/review/tasks/task-001/executions/execution-001/document-preview;v=1",
                "/api/review/tasks/task-001/executions/execution-001/document",
                "/api;v=1/review/tasks/task-001/executions/execution-001/document",
                "/api/review;v=1/tasks/task-001/executions/execution-001/document",
                "/api/review/tasks;v=1/task-001/executions/execution-001/document",
                "/api/review/tasks/task-001;v=1/executions/execution-001/document",
                "/api/review/tasks/task-001/executions;v=1/execution-001/document",
                "/api/review/tasks/task-001/executions/execution-001;v=1/document",
                "/api/review/tasks/task-001/executions/execution-001/document;v=1");
    }

    private static Stream<Arguments> anonymousWorkbenchRequests() {
        return anonymousWorkbenchPaths().stream().flatMap(path -> Stream.of(
                Arguments.of("GET", path),
                Arguments.of("HEAD", path)));
    }
}
