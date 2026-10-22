package com.example.qldetai.feature.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.CreateDefenseRequest;
import com.example.qldetai.feature.evaluation.service.DefenseLocation;

import jakarta.validation.Validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;

class DefenseInputTests {
    @ParameterizedTest
    @ValueSource(
            strings = {
                "Phòng A101",
                "phòng a101",
                "A101",
                "Phòng A 101",
                "PHONG A101",
                "Phòng A\t101",
                "Phòng A\u00a0101",
                "\tPhòng A101",
                "\u00a0Phòng A101"
            })
    void comparesRoomAliasesWithoutChangingTheirLabels(String label) {
        assertThat(DefenseLocation.key(label)).isEqualTo("a101");
        assertThat(DefenseLocation.key("B101")).isNotEqualTo(DefenseLocation.key(label));
    }

    @Test
    void locationLengthMatchesExistingMysqlColumn() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var start = LocalDateTime.of(2027, 5, 1, 1, 0);
            assertThat(
                            validator.validate(
                                    new CreateDefenseRequest(
                                            1L, 2L, "A".repeat(200), start, start.plusHours(1))))
                    .isEmpty();
            assertThat(
                            validator.validate(
                                    new CreateDefenseRequest(
                                            1L, 2L, "A".repeat(201), start, start.plusHours(1))))
                    .anyMatch(
                            violation -> violation.getPropertyPath().toString().equals("location"));
        }
    }
}
