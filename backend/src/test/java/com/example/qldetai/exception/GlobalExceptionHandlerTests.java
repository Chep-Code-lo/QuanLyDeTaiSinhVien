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
                                        "The data was changed by another request. Reload and try again."))
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

    @RestController
    static class ProbeController {
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
