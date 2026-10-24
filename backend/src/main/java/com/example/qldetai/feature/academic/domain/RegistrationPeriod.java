package com.example.qldetai.feature.academic.domain;

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
import jakarta.persistence.Version;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "registration_periods")
@Getter
@Setter
@NoArgsConstructor
public class RegistrationPeriod extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "faculty_id", nullable = false)
    private Faculty faculty;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "semester_id", nullable = false)
    private Semester semester;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "project_type", nullable = false, length = 20)
    private ProjectType projectType;

    @Column(name = "proposal_start_at", nullable = false)
    private LocalDateTime proposalStartAt;

    @Column(name = "proposal_end_at", nullable = false)
    private LocalDateTime proposalEndAt;

    @Column(name = "registration_start_at", nullable = false)
    private LocalDateTime registrationStartAt;

    @Column(name = "registration_end_at", nullable = false)
    private LocalDateTime registrationEndAt;

    @Column(name = "review_deadline_at")
    private LocalDateTime reviewDeadlineAt;

    @Column(name = "defense_date")
    private LocalDate defenseDate;

    /** Rules which vary by registration period rather than being global constants. */
    @Column(name = "min_group_members", nullable = false)
    private int minGroupMembers = 3;

    @Column(name = "max_group_members", nullable = false)
    private int maxGroupMembers = 5;

    @Column(name = "invitation_expiry_hours", nullable = false)
    private int invitationExpiryHours = 72;

    @Column(name = "min_committee_members", nullable = false)
    private int minCommitteeMembers = 3;

    @Column(name = "max_committee_members", nullable = false)
    private int maxCommitteeMembers = 5;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RegistrationPeriodStatus status = RegistrationPeriodStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Version
    @Column(nullable = false)
    private int version;
}
