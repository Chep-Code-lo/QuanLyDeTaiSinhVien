package com.example.qldetai.common.i18n;

import static org.assertj.core.api.Assertions.*;

import com.example.qldetai.common.api.ApiResponse;
import jakarta.validation.Validation;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;

class ErrorMessagesTests {
    @Test
    void apiFailuresTranslateSecurityAndBusinessMessages() {
        assertThat(ApiResponse.failure("You do not have permission to perform this action.").message())
                .isEqualTo("Bạn không có quyền thực hiện thao tác này.");
        assertThat(ApiResponse.failure("The new deadline must be in the future.").message())
                .isEqualTo("Hạn nộp mới phải ở tương lai.");
        assertThat(ApiResponse.failure("Email is already in use.", "details").message())
                .isEqualTo("Email này đã được sử dụng.");
    }

    @Test
    void vietnameseMessagesArePreservedAndUnknownTechnicalMessagesAreHidden() {
        assertThat(ErrorMessages.vietnamese("Bộ môn không tồn tại.")).isEqualTo("Bộ môn không tồn tại.");
        assertThat(ErrorMessages.vietnamese("Registration period cannot change from DRAFT to CLOSED."))
                .contains("Không thể chuyển trạng thái đợt đăng ký").doesNotContain("DRAFT", "CLOSED");
        assertThat(ErrorMessages.vietnamese("Internal database error"))
                .isEqualTo("Không thể thực hiện thao tác. Vui lòng thử lại.");
    }

    @Test
    void validationMessagesUseVietnameseEvenWithoutALanguageHeader() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var violations = factory.getValidator().validate(new Input("", "invalid"));
            assertThat(violations).hasSize(2);
            assertThat(violations).extracting(violation -> violation.getMessage())
                    .containsExactlyInAnyOrder("Thông tin này không được để trống.", "Địa chỉ email không hợp lệ.");
        }
    }

    private record Input(@NotBlank String title, @Email String email) {}
}
