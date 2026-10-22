package com.example.qldetai.feature.identity;

import static org.assertj.core.api.Assertions.assertThat;
import com.example.qldetai.feature.identity.dto.IdentityDtos.*;
import com.example.qldetai.feature.identity.domain.RoleCode;
import java.util.Set;
import org.junit.jupiter.api.Test;

class IdentityCredentialLoggingTests {
    @Test
    void requestDebugStringsNeverContainPasswords() {
        assertThat(new LoginRequest("user@example.test", "login-secret").toString()).doesNotContain("login-secret");
        assertThat(new ChangePasswordRequest("old-secret", "new-secret").toString()).doesNotContain("old-secret", "new-secret");
        assertThat(new CreateUserRequest("user@example.test", "create-secret", "User", null,
                Set.of(RoleCode.STUDENT), null, null).toString()).doesNotContain("create-secret");
    }
}
