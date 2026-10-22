package com.example.qldetai.feature.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.qldetai.feature.identity.domain.RoleCode;
import com.example.qldetai.feature.identity.dto.IdentityDtos.ChangePasswordRequest;
import com.example.qldetai.feature.identity.dto.IdentityDtos.CreateUserRequest;
import jakarta.validation.Validation;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class PasswordPolicyTests {
    @ParameterizedTest
    @CsvSource({
        "Abcdef1!,true", "abcdefgh1!,false", "Abcdefgh!,false", "Abcdefg1,false",
        "Abc1!,false", "ĐồÁn2026!,true", "ĐềTài١٢!,true", "Abcdef1😀,true"
    })
    void bothBackendDtosEnforceTheFrontendUnicodePolicy(String password, boolean valid) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var create = new CreateUserRequest("user@test.local", password, "User", null, Set.of(RoleCode.ADMIN), null, null);
            var change = new ChangePasswordRequest("OldPassword1!", password);
            assertThat(validator.validate(create).isEmpty()).isEqualTo(valid);
            assertThat(validator.validate(change).isEmpty()).isEqualTo(valid);
        }
    }

    @Test
    void accepts100CharactersButRejects101AndReportsThePasswordField() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(new ChangePasswordRequest("old", "A1!" + "a".repeat(97)))).isEmpty();
            assertThat(validator.validate(new ChangePasswordRequest("old", "A1!" + "a".repeat(98))))
                    .allSatisfy(violation -> assertThat(violation.getPropertyPath().toString()).isEqualTo("newPassword"));
            assertThat(validator.validate(new ChangePasswordRequest("old", "A1!" + "a".repeat(98)))).isNotEmpty();
        }
    }
}
