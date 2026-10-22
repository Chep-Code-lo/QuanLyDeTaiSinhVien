package com.example.qldetai.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.qldetai.feature.identity.domain.RoleCode;
import com.example.qldetai.feature.identity.domain.UserStatus;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UserPrincipalTests {
    @Test
    void loggingPrincipalDoesNotExposeCredentialsOrPersonalDetails() {
        var principal = new UserPrincipal(1L, "private@test.local", "Private name",
                "sensitive-password-hash", UserStatus.ACTIVE, Set.of(RoleCode.ADMIN));
        assertThat(principal.toString()).contains("id=1", "ACTIVE", "ADMIN")
                .doesNotContain(principal.password(), principal.email(), principal.fullName());
        assertThat(principal.getPassword()).isEqualTo("sensitive-password-hash");
    }
}
