package com.example.qldetai.feature.evaluation.domain;

import com.example.qldetai.common.persistence.CreatedAtEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "rubrics",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_rubrics_01",
                        columnNames = {"component_id", "version_no"}))
@Getter
@Setter
@NoArgsConstructor
public class Rubric extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "component_id")
    private AssessmentComponent component;

    @Column(nullable = false)
    private String name;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(nullable = false)
    private String status = "DRAFT";
}
