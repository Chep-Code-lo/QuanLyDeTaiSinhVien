package com.example.qldetai.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

class GlobalExceptionHandlerTests {
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc =
                MockMvcBuilders.standaloneSetup(new ProbeController())
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
    }

    @Test
    void oversizedUploadReturns413WithVietnameseApiEnvelope() throws Exception {
        mvc.perform(get("/probe/upload-size"))
                .andExpect(status().is(413))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Tệp quá lớn. Tối đa 20 MB."));
    }

    @Test
    void optimisticConflictKeepsApiEnvelopeAndHidesDiagnostics() throws Exception {
        mvc.perform(get("/probe/optimistic"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Dữ liệu đã được thay đổi bởi yêu cầu khác. Vui lòng tải lại và thử lại."))
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    void databaseLockConflictReturns409() throws Exception {
        mvc.perform(get("/probe/lock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void malformedAndMissingParametersReturn400WithEnvelope() throws Exception {
        mvc.perform(get("/probe/parameter").param("id", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
        mvc.perform(get("/probe/parameter"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void commonErrorsKeepTheirStatusAndNeverReturnInternalDiagnostics() throws Exception {
        for (String[] sample :
                new String[][] {
                    {"business", "400", "Dữ liệu nghiệp vụ chưa hợp lệ."},
                    {"authentication", "401", "Email hoặc mật khẩu không chính xác."},
                    {"denied", "403", "Bạn không có quyền thực hiện thao tác này."},
                    {"missing", "404", "Không tìm thấy thông báo."},
                    {"integrity", "409", "Thay đổi bị trùng hoặc xung đột với dữ liệu hiện có."},
                    {"limited", "429", "Bạn thao tác quá nhanh. Vui lòng thử lại sau."},
                    {"unexpected", "500", "Hệ thống gặp lỗi. Vui lòng thử lại sau."}
                }) {
            mvc.perform(get("/probe/error").param("kind", sample[0]))
                    .andExpect(status().is(Integer.parseInt(sample[1])))
                    .andExpect(jsonPath("$.message").value(sample[2]))
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.stackTrace").doesNotExist())
                    .andExpect(
                            org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                    .content()
                                    .string(
                                            org.hamcrest.Matchers.not(
                                                    org.hamcrest.Matchers.containsString(
                                                            "internal-diagnostic"))));
        }
    }

    @Test
    void validationFieldMessagesAreVietnameseAndDoNotReturnRejectedValues() throws Exception {
        mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                        "/probe/validation")
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"invalid-address\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.email").value("Địa chỉ email không hợp lệ."))
                .andExpect(jsonPath("$.data.name").value("Không được để trống."))
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Thông tin chưa hợp lệ. Vui lòng kiểm tra lại các trường đã nhập."));
    }

    @RestController
    static class ProbeController {
        @GetMapping("/probe/error")
        void error(@RequestParam String kind) {
            switch (kind) {
                case "business" ->
                        throw new BusinessRuleException("Dữ liệu nghiệp vụ chưa hợp lệ.");
                case "authentication" ->
                        throw new org.springframework.security.authentication
                                .BadCredentialsException("internal-diagnostic");
                case "denied" ->
                        throw new org.springframework.security.access.AccessDeniedException(
                                "internal-diagnostic");
                case "missing" -> throw new ResourceNotFoundException("Không tìm thấy thông báo.");
                case "integrity" ->
                        throw new org.springframework.dao.DataIntegrityViolationException(
                                "internal-diagnostic SELECT secret FROM users");
                case "limited" ->
                        throw new TooManyRequestsException(
                                "Bạn thao tác quá nhanh. Vui lòng thử lại sau.");
                default -> throw new IllegalStateException("internal-diagnostic");
            }
        }

        @org.springframework.web.bind.annotation.PostMapping("/probe/validation")
        void validation(
                @jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody
                        ValidationProbe input) {}

        record ValidationProbe(
                @jakarta.validation.constraints.Email String email,
                @jakarta.validation.constraints.NotBlank String name) {}

        @GetMapping("/probe/upload-size")
        void uploadSize() {
            throw new MaxUploadSizeExceededException(20 * 1024 * 1024);
        }

        @GetMapping("/probe/optimistic")
        void optimistic() {
            throw new OptimisticLockingFailureException("internal entity and SQL diagnostic");
        }

        @GetMapping("/probe/lock")
        void lock() {
            throw new CannotAcquireLockException("internal database lock diagnostic");
        }

        @GetMapping("/probe/parameter")
        Long parameter(@RequestParam Long id) {
            return id;
        }
    }
}
