package com.example.qldetai.feature.evaluation.domain;

import com.example.qldetai.feature.identity.domain.Student;

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

/** Immutable group-membership evidence captured with a result version. */
@Entity
@Table(name = "result_members")
@Getter
@Setter
@NoArgsConstructor
public class ResultMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "result_id", nullable = false)
    private ResultVersion result;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "student_code_snapshot", nullable = false, length = 20)
    private String studentCodeSnapshot;

    @Column(name = "full_name_snapshot", nullable = false, length = 150)
    private String fullNameSnapshot;
}
