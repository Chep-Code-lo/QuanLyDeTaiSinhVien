package com.example.qldetai.feature.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;

@SpringBootTest
@AutoConfigureMockMvc
class PasswordEncoderIntegrationTests {
    @Autowired private PasswordEncoder encoder;

    @Test
    void newPasswordsUseBcryptWithoutTruncatingTheSuffixWithinItsByteLimit() {
        for (String password : new String[] { "A1!" + "a".repeat(69), "ĐồÁn2026!" }) {
            String encoded = encoder.encode(password);
            assertThat(encoded).startsWith("{bcrypt}$2a$12$").hasSizeLessThanOrEqualTo(255);
            assertThat(encoder.matches(password, encoded)).isTrue();
            assertThat(encoder.matches(password.substring(0, password.length() - 1) + "b", encoded)).isFalse();
        }
    }

    @Test
    void bcryptByteLimitRemainsExplicitAndDoesNotSilentlyTruncate() {
        for (String password : new String[] { "A1!" + "a".repeat(97), "Đ1!" + "ề".repeat(60) }) {
            assertThatThrownBy(() -> encoder.encode(password)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void previouslyStoredPbkdf2HashesRemainReadable() {
        String password = "A1!" + "a".repeat(97);
        String hash = "{pbkdf2}" + Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8().encode(password);
        assertThat(encoder.matches(password, hash)).isTrue();
        assertThat(encoder.matches(password + "wrong", hash)).isFalse();
    }

    @Test
    void existingUnprefixedAndPrefixedBcryptHashesStillAuthenticate() {
        String hash = new BCryptPasswordEncoder(4).encode("LegacyPassword123!");
        assertThat(encoder.matches("LegacyPassword123!", hash)).isTrue();
        assertThat(encoder.matches("LegacyPassword123!", "{bcrypt}" + hash)).isTrue();
        assertThat(encoder.matches("wrong", hash)).isFalse();
    }
}
