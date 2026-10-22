package com.example.qldetai.feature.evaluation.domain;

import com.example.qldetai.feature.academic.domain.RegistrationPeriod;
import com.example.qldetai.feature.project.domain.ProjectAssignment;

import jakarta.persistence.*;

import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "defense_sessions")
@Getter
@Setter
@NoArgsConstructor
public class DefenseSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id")
    private ProjectAssignment assignment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "committee_id")
    private EvaluationCommittee committee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "period_id")
    private RegistrationPeriod period;

    @Column(nullable = false)
    private String location;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private LocalDateTime endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DefenseStatus status = DefenseStatus.SCHEDULED;

    @Version private int version;
}
