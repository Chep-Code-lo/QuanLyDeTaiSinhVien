package com.example.qldetai.feature.evaluation.domain;

import com.example.qldetai.feature.academic.domain.RegistrationPeriod;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "assessment_components")
@Getter
@Setter
@NoArgsConstructor
public class AssessmentComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "period_id")
    private RegistrationPeriod period;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "component_type", nullable = false)
    private AssessmentComponentType componentType;

    @Column(name = "expected_evaluators", nullable = false)
    private int expectedEvaluators;

    @Column(nullable = false)
    private String status = "DRAFT";
}
