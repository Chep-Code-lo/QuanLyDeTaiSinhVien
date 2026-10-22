package com.example.qldetai.feature.evaluation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.math.BigDecimal;

/** Immutable score-sheet evidence captured when a result version is finalized. */
@Entity
@Table(name = "result_inputs")
@Getter
@Setter
@NoArgsConstructor
public class ResultInput {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "result_id", nullable = false)
    private ResultVersion result;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sheet_id", nullable = false)
    private ScoreSheet sheet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "component_id", nullable = false)
    private AssessmentComponent component;

    @Column(name = "sheet_version", nullable = false)
    private int sheetVersion;

    @Column(name = "score_snapshot", nullable = false, precision = 5, scale = 2)
    private BigDecimal scoreSnapshot;

    @Column(name = "rubric_snapshot", nullable = false, columnDefinition = "TEXT")
    private String rubricSnapshot;
}
