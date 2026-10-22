package com.example.qldetai.common.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import lombok.Getter;
import lombok.Setter;

import java.time.Clock;
import java.time.LocalDateTime;

@MappedSuperclass
@Getter
@Setter
public abstract class UpdatedAtEntity {

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void initializeUpdatedAt() {
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now(Clock.systemUTC());
        }
    }

    @PreUpdate
    protected void updateTimestamp() {
        updatedAt = LocalDateTime.now(Clock.systemUTC());
    }
}
