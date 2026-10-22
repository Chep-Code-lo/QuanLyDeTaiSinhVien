package com.example.qldetai.feature.evaluation.domain;

import com.example.qldetai.common.persistence.CreatedAtEntity;
import com.example.qldetai.feature.identity.domain.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "grade_change_requests")
@Getter
@Setter
@NoArgsConstructor
public class GradeChangeRequest extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sheet_id")
    private ScoreSheet sheet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by")
    private User requestedBy;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GradeChangeStatus status = GradeChangeStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "review_comment", columnDefinition = "TEXT")
    private String reviewComment;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;
}
