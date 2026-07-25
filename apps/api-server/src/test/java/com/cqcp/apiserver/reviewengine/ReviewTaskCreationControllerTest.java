package com.cqcp.apiserver.reviewengine;

import static com.cqcp.apiserver.reviewengine.ReviewTaskCreationModels.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Controller-layer tests.
 *
 * <p>Uses a mock {@link ReviewTaskCreationService} to verify HTTP binding,
 * handler mapping, and exact response bodies.  Does not test validation logic.
 */
@WebMvcTest(ReviewTaskCreationController.class)
@Import(ReviewTaskCreationExceptionHandler.class)
class ReviewTaskCreationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReviewTaskCreationService service;

    // ==================== 202 ====================

    @Test
    void monthlyRequest_returns202_withAllFields() throws Exception {
        when(service.createTask(anyString(), any(), anyLong(), anyString()))
                .thenReturn(new CreateReviewTaskResponse(
                        "TASK_abc", "EXEC_abc", "QUEUED", "/review/results/TASK_abc?executionId=EXEC_abc"));

        mockMvc.perform(multipart("/api/review/tasks")
                        .file(docxPart("t.docx"))
                        .file(metadataPart(monthlyJson())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.taskId").value("TASK_abc"))
                .andExpect(jsonPath("$.executionId").value("EXEC_abc"))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.resultUrl").isString());
    }

    @Test
    void milestoneRequest_returns202() throws Exception {
        when(service.createTask(anyString(), any(), anyLong(), anyString()))
                .thenReturn(new CreateReviewTaskResponse(
                        "TASK_x", "EXEC_x", "QUEUED", "/url"));
        mockMvc.perform(multipart("/api/review/tasks")
                        .file(docxPart("m.docx"))
                        .file(metadataPart(milestoneJson())))
                .andExpect(status().isAccepted());
    }

    // ==================== 400 — missing parts (exact body) ====================

    @Test
    void missingFilePart_returns400_withFieldError() throws Exception {
        mockMvc.perform(multipart("/api/review/tasks")
                        .file(metadataPart(monthlyJson())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("请求校验失败"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("file"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("REQUIRED_FIELD_MISSING"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("file 为必填项"))
                // No extra fields
                .andExpect(jsonPath("$.title").doesNotExist())
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.detail").doesNotExist())
                .andExpect(jsonPath("$.instance").doesNotExist());
    }

    @Test
    void missingMetadataPart_returns400_withFieldError() throws Exception {
        mockMvc.perform(multipart("/api/review/tasks")
                        .file(docxPart("t.docx")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("metadata"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("REQUIRED_FIELD_MISSING"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("metadata 为必填项"));
    }

    // ==================== 400 — FILE_TOO_LARGE ====================

    @Test
    void fileTooLarge_returns400() throws Exception {
        when(service.createTask(anyString(), any(), anyLong(), anyString()))
                .thenThrow(new MaxUploadSizeExceededException(26214400L));

        mockMvc.perform(multipart("/api/review/tasks")
                        .file(docxPart("big.docx"))
                        .file(metadataPart(monthlyJson())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("file"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("FILE_TOO_LARGE"));
    }

    // ==================== 409 ====================

    @Test
    void bindingUnavailable_returns409_withExactBody() throws Exception {
        when(service.createTask(anyString(), any(), anyLong(), anyString()))
                .thenThrow(new ExecutionBindingResolutionException(
                        ExecutionBindingFailureReason.NOT_FOUND, "no binding"));

        mockMvc.perform(multipart("/api/review/tasks")
                        .file(docxPart("b.docx"))
                        .file(metadataPart(monthlyJson())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXECUTION_BINDING_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("当前执行绑定不可用"))
                .andExpect(jsonPath("$.reason").value("NOT_FOUND"))
                .andExpect(jsonPath("$.retryable").value(false))
                .andExpect(jsonPath("$.operatorActionRequired").value(true))
                // No extra fields
                .andExpect(jsonPath("$.type").doesNotExist())
                .andExpect(jsonPath("$.title").doesNotExist())
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.detail").doesNotExist())
                .andExpect(jsonPath("$.instance").doesNotExist());
    }

    // ==================== 503 ====================

    @Test
    void documentStorageFailed_returns503_withoutReason() throws Exception {
        when(service.createTask(anyString(), any(), anyLong(), anyString()))
                .thenThrow(new DocumentStorageException("disk full", new java.io.IOException()));

        mockMvc.perform(multipart("/api/review/tasks")
                        .file(docxPart("s.docx"))
                        .file(metadataPart(monthlyJson())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DOCUMENT_STORAGE_FAILED"))
                .andExpect(jsonPath("$.message").value("文档存储失败"))
                .andExpect(jsonPath("$.reason").doesNotExist())
                .andExpect(jsonPath("$.retryable").value(true))
                .andExpect(jsonPath("$.operatorActionRequired").value(false))
                .andExpect(jsonPath("$.type").doesNotExist())
                .andExpect(jsonPath("$.title").doesNotExist());
    }

    // ==================== 400 — invalid content ====================

    @Test
    void invalidDocumentContent_returns400() throws Exception {
        when(service.createTask(anyString(), any(), anyLong(), anyString()))
                .thenThrow(new InvalidDocumentContentException("not docx"));

        mockMvc.perform(multipart("/api/review/tasks")
                        .file(docxPart("bad.docx"))
                        .file(metadataPart(monthlyJson())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].code").value("INVALID_DOCUMENT_CONTENT"));
    }

    // ==================== 400 — validation error body ====================

    @Test
    void validationErrors_returnFullBody_noExtraFields() throws Exception {
        when(service.createTask(anyString(), any(), anyLong(), anyString()))
                .thenThrow(new ValidationException(List.of(
                        new FieldError("file", "EMPTY_FILE", "file 不能为空"))));

        // Must include a file part to reach the mocked service (not blocked by missing-part handler)
        mockMvc.perform(multipart("/api/review/tasks")
                        .file(docxPart("valid.docx"))
                        .file(metadataPart(monthlyJson())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("请求校验失败"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("file"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("EMPTY_FILE"))
                .andExpect(jsonPath("$.title").doesNotExist())
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.detail").doesNotExist())
                .andExpect(jsonPath("$.instance").doesNotExist());
    }

    // ==================== Helpers ====================

    private static MockMultipartFile docxPart(String name) {
        return new MockMultipartFile("file", name,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "dummy".getBytes(StandardCharsets.UTF_8));
    }

    private static MockMultipartFile metadataPart(String json) {
        return new MockMultipartFile("metadata", null,
                MediaType.APPLICATION_JSON_VALUE,
                json.getBytes(StandardCharsets.UTF_8));
    }

    private static String monthlyJson() {
        return "{\"contractType\":\"ENGINEERING\",\"structuredFields\":{"
                + "\"contractName\":\"t\",\"partyAName\":\"A\",\"partyBName\":\"B\","
                + "\"projectName\":\"P\",\"contractTotalAmount\":100,\"taxExcludedAmount\":90,"
                + "\"taxAmount\":10,\"taxRate\":13,\"pricingMode\":\"FIXED_TOTAL_PRICE\","
                + "\"paymentMethod\":\"MONTHLY\",\"invoiceType\":\"VAT_SPECIAL\","
                + "\"prepaymentRatio\":30,\"progressPaymentRatio\":60,"
                + "\"completionPaymentRatio\":80,\"settlementPaymentRatio\":95,"
                + "\"warrantyRetentionRatio\":5,\"currency\":\"CNY\"}}";
    }

    private static String milestoneJson() {
        return "{\"contractType\":\"ENGINEERING\",\"structuredFields\":{"
                + "\"contractName\":\"m\",\"partyAName\":\"A\",\"partyBName\":\"B\","
                + "\"projectName\":\"P\",\"contractTotalAmount\":200,\"taxExcludedAmount\":180,"
                + "\"taxAmount\":20,\"taxRate\":13,\"pricingMode\":\"PROVISIONAL_TOTAL_PRICE\","
                + "\"paymentMethod\":\"MILESTONE\",\"invoiceType\":\"VAT_GENERAL\","
                + "\"prepaymentRatio\":30,\"milestonePaymentTerms\":\"里程碑1时付30%\","
                + "\"currency\":\"CNY\"}}";
    }
}
