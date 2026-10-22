package com.example.qldetai.feature.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.qldetai.feature.identity.domain.RoleCode;
import com.example.qldetai.feature.identity.dto.IdentityDtos.ChangePasswordRequest;
import com.example.qldetai.feature.identity.dto.IdentityDtos.CreateUserRequest;

import jakarta.validation.Validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Set;

class PasswordPolicyTests {
    @ParameterizedTest(name = "Unicode policy case {index}")
    @CsvSource({
        "Abcdef1!,true", "abcdefgh1!,false", "Abcdefgh!,false", "Abcdefg1,false",
        "Abc1!,false", "ĐồÁn2026!,true", "ĐềTài١٢!,true", "Abcdef1😀,true"
    })
    void bothBackendDtosEnforceTheFrontendUnicodePolicy(String password, boolean valid) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var create =
                    new CreateUserRequest(
                            "user@test.local",
                            password,
                            "User",
                            null,
                            Set.of(RoleCode.ADMIN),
                            null,
                            null);
            var change = new ChangePasswordRequest("OldPassword1!", password);
            assertThat(validator.validate(create).isEmpty()).isEqualTo(valid);
            assertThat(validator.validate(change).isEmpty()).isEqualTo(valid);
        }
    }

    @Test
    void enforcesUtf8ByteBoundaryOnBothDtos() {
        for (String prefix :
                new String[] {
                    "A1!" + "a".repeat(69), "A1!" + "ề".repeat(23), "A1!" + "😀".repeat(17) + "a"
                }) {
            bothBackendDtosEnforceTheFrontendUnicodePolicy(prefix, true);
            bothBackendDtosEnforceTheFrontendUnicodePolicy(prefix + "a", false);
        }
        bothBackendDtosEnforceTheFrontendUnicodePolicy("A1!" + "a".repeat(97), false);
    }
}
