package com.cqcp.apiserver.reviewengine;

import static com.cqcp.apiserver.reviewengine.ReviewDocumentModels.DocumentUnavailableException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cqcp.apiserver.wordparser.DocxWordParserSpike;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ReviewDocumentControllerTest {

    private ReviewDocumentService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(ReviewDocumentService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ReviewDocumentController(service))
                .build();
    }

    @Test
    void previewSuccessReturnsCompleteJsonContract() throws Exception {
        when(service.getPreview("task-1", "exec-1"))
                .thenReturn(new ReviewDocumentModels.DocumentPreview(
                        "task-1",
                        "exec-1",
                        "测试合同",
                        "测试合同.docx",
                        "parser-v1",
                        "a".repeat(64),
                        List.of(new ReviewDocumentModels.PreviewBlock(
                                "block-1",
                                "table:table-1/row:2",
                                "TABLE_ROW",
                                "行文本",
                                List.of("付款条款"),
                                "table-1",
                                2,
                                List.of(new ReviewDocumentModels.PreviewCell(
                                        "table:table-1/row:2/cell:0",
                                        0,
                                        "单元格文本"))))));

        mockMvc.perform(get(
                        "/api/review/tasks/task-1/executions/exec-1/document-preview"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.taskId").value("task-1"))
                .andExpect(jsonPath("$.executionId").value("exec-1"))
                .andExpect(jsonPath("$.contractName").value("测试合同"))
                .andExpect(jsonPath("$.originalFileName").value("测试合同.docx"))
                .andExpect(jsonPath("$.parserVersion").value("parser-v1"))
                .andExpect(jsonPath("$.sha256").value("a".repeat(64)))
                .andExpect(jsonPath("$.blocks[0].blockId").value("block-1"))
                .andExpect(jsonPath("$.blocks[0].previewElementRef").value("table:table-1/row:2"))
                .andExpect(jsonPath("$.blocks[0].type").value("TABLE_ROW"))
                .andExpect(jsonPath("$.blocks[0].text").value("行文本"))
                .andExpect(jsonPath("$.blocks[0].sectionPath[0]").value("付款条款"))
                .andExpect(jsonPath("$.blocks[0].tableId").value("table-1"))
                .andExpect(jsonPath("$.blocks[0].rowIndex").value(2))
                .andExpect(jsonPath("$.blocks[0].cells[0].previewElementRef")
                        .value("table:table-1/row:2/cell:0"))
                .andExpect(jsonPath("$.blocks[0].cells[0].cellIndex").value(0))
                .andExpect(jsonPath("$.blocks[0].cells[0].text").value("单元格文本"));
        verify(service).getPreview("task-1", "exec-1");
    }

    @Test
    void downloadSuccessReturnsDocxHeadersHashAndExactBytes() throws Exception {
        byte[] bytes = new byte[] {0x43, 0x51, 0x43, 0x50};
        when(service.getDocument("task-1", "exec-1"))
                .thenReturn(new ReviewDocumentModels.VerifiedDocument(
                        "测试合同.docx",
                        "b".repeat(64),
                        bytes.length,
                        bytes));
        bytes[0] = 0x00;

        var result = mockMvc.perform(get(
                        "/api/review/tasks/task-1/executions/exec-1/document"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .andExpect(content().bytes(new byte[] {0x43, 0x51, 0x43, 0x50}))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, 4))
                .andExpect(header().string(
                        "X-CQCP-Document-SHA256",
                        "b".repeat(64)))
                .andReturn();
        var disposition = ContentDisposition.parse(
                result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION));
        assertThat(disposition.getType()).isEqualTo("attachment");
        assertThat(disposition.getFilename()).isEqualTo("测试合同.docx");
        verify(service).getDocument("task-1", "exec-1");
    }

    @Test
    void missingDocumentMapsToStable404Problem() throws Exception {
        when(service.getDocument("task-1", "missing"))
                .thenThrow(new ReviewDocumentModels.DocumentNotFoundException());

        mockMvc.perform(get(
                        "/api/review/tasks/task-1/executions/missing/document"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:cqcp:problem:document_not_found"))
                .andExpect(jsonPath("$.title").value("DOCUMENT_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("未找到该 task/execution 的合同文档"));
        verify(service).getDocument("task-1", "missing");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("documentConflicts")
    void documentConflictsMapExactCodeAndDetailToStable409Problem(
            String endpoint,
            String code,
            String detail) throws Exception {
        if ("preview".equals(endpoint)) {
            when(service.getPreview("task-1", endpoint))
                    .thenThrow(new ReviewDocumentModels.DocumentConflictException(code, detail));
        } else {
            when(service.getDocument("task-1", endpoint))
                    .thenThrow(new ReviewDocumentModels.DocumentConflictException(code, detail));
        }

        var path = "/api/review/tasks/task-1/executions/" + endpoint
                + ("preview".equals(endpoint) ? "/document-preview" : "/document");
        mockMvc.perform(get(path))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:cqcp:problem:" + code.toLowerCase()))
                .andExpect(jsonPath("$.title").value(code))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value(detail));
    }

    @Test
    void previewStorageFailureIsAStable503Problem() throws Exception {
        when(service.getPreview("task-1", "exec-1"))
                .thenThrow(new DocumentUnavailableException(
                        "sensitive filesystem detail",
                        new java.io.IOException("secret path")));

        mockMvc.perform(get(
                        "/api/review/tasks/task-1/executions/exec-1/document-preview"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("DOCUMENT_UNAVAILABLE"))
                .andExpect(jsonPath("$.detail").value("合同文档暂不可用"));
    }

    @Test
    void downloadStorageFailureIsAStable503Problem() throws Exception {
        when(service.getDocument("task-1", "exec-1"))
                .thenThrow(new DocumentUnavailableException(
                        "sensitive filesystem detail",
                        new java.io.IOException("secret path")));

        mockMvc.perform(get(
                        "/api/review/tasks/task-1/executions/exec-1/document"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("DOCUMENT_UNAVAILABLE"))
                .andExpect(jsonPath("$.detail").value("合同文档暂不可用"));
    }

    @Test
    void crlfOriginalFilenameMapsTo404WithoutDownloadHeaders() throws Exception {
        var repository = mock(ReviewDocumentRepository.class);
        var store = mock(LocalReviewDocumentStore.class);
        var parser = mock(DocxWordParserSpike.class);
        var actualService = new ReviewDocumentService(repository, store, parser);
        var actualMvc = MockMvcBuilders
                .standaloneSetup(new ReviewDocumentController(actualService))
                .build();
        when(repository.findDocument("task-1", "exec-crlf"))
                .thenReturn(Optional.of(new ReviewDocumentModels.DocumentMetadata(
                        "task-1",
                        "exec-crlf",
                        "测试合同",
                        RuntimeArtifactVersions.PARSER_VERSION,
                        "测试合同\r\nX-Evil: injected.docx",
                        "task-1/0123456789abcdef0123456789abcdef.docx",
                        4,
                        "a".repeat(64))));

        actualMvc.perform(get("/api/review/tasks/task-1/executions/exec-crlf/document"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:cqcp:problem:document_not_found"))
                .andExpect(jsonPath("$.title").value("DOCUMENT_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("未找到该 task/execution 的合同文档"))
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_DISPOSITION))
                .andExpect(header().doesNotExist("X-CQCP-Document-SHA256"))
                .andExpect(header().doesNotExist("X-Evil"));
        verifyNoInteractions(store, parser);
    }

    private static Stream<Arguments> documentConflicts() {
        return Stream.of(
                Arguments.of(
                        "preview",
                        "PARSER_VERSION_MISMATCH",
                        "该 execution 的 parserVersion 与当前 parser 不一致，禁止历史重解析"),
                Arguments.of(
                        "checksum-unavailable",
                        "DOCUMENT_CHECKSUM_UNAVAILABLE",
                        "该历史文档没有可核验的上传 SHA-256，禁止读取"),
                Arguments.of(
                        "checksum-mismatch",
                        "DOCUMENT_CHECKSUM_MISMATCH",
                        "合同文档校验失败，禁止读取"),
                Arguments.of(
                        "size-mismatch",
                        "DOCUMENT_SIZE_MISMATCH",
                        "合同文档大小校验失败，禁止读取"));
    }
}
