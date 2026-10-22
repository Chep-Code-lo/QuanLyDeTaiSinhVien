package com.example.qldetai.feature.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
@AutoConfigureMockMvc
class PasswordEncoderIntegrationTests {
    @Autowired private PasswordEncoder encoder;

    @Test
    void newPasswordsSupport100CharactersWithoutTruncatingTheSuffix() {
        for (String password : new String[] { "A1!" + "a".repeat(97), "Đ1!" + "ề".repeat(60) }) {
            String encoded = encoder.encode(password);
            assertThat(encoded).startsWith("{pbkdf2}").hasSizeLessThanOrEqualTo(255);
            assertThat(encoder.matches(password, encoded)).isTrue();
            assertThat(encoder.matches(password.substring(0, password.length() - 1) + "b", encoded)).isFalse();
        }
    }

    @Test
    void existingUnprefixedAndPrefixedBcryptHashesStillAuthenticate() {
        String hash = new BCryptPasswordEncoder(4).encode("LegacyPassword123!");
        assertThat(encoder.matches("LegacyPassword123!", hash)).isTrue();
        assertThat(encoder.matches("LegacyPassword123!", "{bcrypt}" + hash)).isTrue();
        assertThat(encoder.matches("wrong", hash)).isFalse();
    }
}
