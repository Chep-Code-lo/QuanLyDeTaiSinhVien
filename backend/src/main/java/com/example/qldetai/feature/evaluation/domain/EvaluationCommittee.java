package com.example.qldetai.feature.evaluation.domain;

import com.example.qldetai.feature.academic.domain.Faculty;
import com.example.qldetai.feature.academic.domain.RegistrationPeriod;
import com.example.qldetai.feature.identity.domain.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(name = "evaluation_committees")
@Getter
@Setter
@NoArgsConstructor
public class EvaluationCommittee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "period_id")
    private RegistrationPeriod period;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "faculty_id")
    private Faculty faculty;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CommitteeStatus status = CommitteeStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rubric_id")
    private Rubric rubric;

    @Version
    private int version;
}
