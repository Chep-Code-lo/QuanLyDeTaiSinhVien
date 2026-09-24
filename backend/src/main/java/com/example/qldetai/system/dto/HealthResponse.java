package com.example.qldetai.system.dto;

import java.time.Instant;

/** Minimal health payload that does not depend on database availability. */
public record HealthResponse(String status, Instant checkedAt) {
}
