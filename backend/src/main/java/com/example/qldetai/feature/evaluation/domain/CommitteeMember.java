package com.example.qldetai.feature.evaluation.domain;

import com.example.qldetai.feature.identity.domain.Lecturer;

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
@Table(name = "committee_members")
@Getter
@Setter
@NoArgsConstructor
public class CommitteeMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "committee_id")
    private EvaluationCommittee committee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lecturer_id")
    private Lecturer lecturer;

    @Column(name = "slot_no", nullable = false)
    private int slotNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "committee_role", nullable = false)
    private CommitteeRole committeeRole;

    @Column(name = "role_label", length = 100)
    private String roleLabel;
}
