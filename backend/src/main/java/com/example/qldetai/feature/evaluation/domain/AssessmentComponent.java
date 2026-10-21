package com.example.qldetai.feature.evaluation.domain;

import com.example.qldetai.feature.academic.domain.RegistrationPeriod;
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

    // Kept only so existing databases remain compatible.  Assignment counts are now derived
    // from the actual evaluator assignments (or the committee roster), never from this value.
    @Column(name = "expected_evaluators", nullable = false)
    private int legacyExpectedEvaluators = 1;

    @Column(nullable = false)
    private String status = "DRAFT";
}
