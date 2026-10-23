package com.example.qldetai.feature.academic.service;

import com.example.qldetai.common.api.PageResponse;
import com.example.qldetai.common.audit.BusinessAudit;
import com.example.qldetai.common.time.VietnamTime;
import com.example.qldetai.exception.BusinessRuleException;
import com.example.qldetai.exception.ConflictException;
import com.example.qldetai.exception.ResourceNotFoundException;
import com.example.qldetai.feature.academic.domain.Faculty;
import com.example.qldetai.feature.academic.domain.ProjectType;
import com.example.qldetai.feature.academic.domain.RegistrationPeriod;
import com.example.qldetai.feature.academic.domain.RegistrationPeriodStatus;
import com.example.qldetai.feature.academic.domain.Semester;
import com.example.qldetai.feature.academic.dto.AcademicDtos.ChangePeriodStatusRequest;
import com.example.qldetai.feature.academic.dto.AcademicDtos.CreateRegistrationPeriodRequest;
import com.example.qldetai.feature.academic.dto.AcademicDtos.RegistrationPeriodResponse;
import com.example.qldetai.feature.academic.dto.AcademicDtos.UpdateRegistrationPeriodRequest;
import com.example.qldetai.feature.academic.repository.FacultyRepository;
import com.example.qldetai.feature.academic.repository.RegistrationPeriodRepository;
import com.example.qldetai.feature.academic.repository.SemesterRepository;
import com.example.qldetai.feature.communication.email.EmailNotificationEventService;
import com.example.qldetai.feature.identity.domain.User;
import com.example.qldetai.feature.identity.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RegistrationPeriodService {

    private static final int DEFAULT_MIN_GROUP_MEMBERS = 1;
    private static final int DEFAULT_MAX_GROUP_MEMBERS = 3;
    private static final int DEFAULT_INVITATION_EXPIRY_HOURS = 72;
    private static final int DEFAULT_MIN_COMMITTEE_MEMBERS = 3;
    private static final int DEFAULT_MAX_COMMITTEE_MEMBERS = 5;

    private static final Map<RegistrationPeriodStatus, Set<RegistrationPeriodStatus>> TRANSITIONS =
            Map.of(
                    RegistrationPeriodStatus.DRAFT,
                    EnumSet.of(RegistrationPeriodStatus.ACTIVE, RegistrationPeriodStatus.CANCELLED),
                    RegistrationPeriodStatus.ACTIVE,
                    EnumSet.of(RegistrationPeriodStatus.CLOSED, RegistrationPeriodStatus.CANCELLED),
                    RegistrationPeriodStatus.CLOSED,
                    EnumSet.of(RegistrationPeriodStatus.ACTIVE),
                    RegistrationPeriodStatus.CANCELLED,
                    EnumSet.noneOf(RegistrationPeriodStatus.class));

    private final RegistrationPeriodRepository registrationPeriodRepository;
    private final FacultyRepository facultyRepository;
    private final SemesterRepository semesterRepository;
    private final UserRepository userRepository;
    private final EmailNotificationEventService emailNotificationEventService;
    private final Clock clock;
    private final AcademicAuthorization authorization;

    @Transactional(readOnly = true)
    public PageResponse<RegistrationPeriodResponse> list(Long facultyId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<RegistrationPeriodResponse> periods =
                (facultyId == null
                                ? registrationPeriodRepository.findAll(pageable)
                                : registrationPeriodRepository.findByFacultyId(facultyId, pageable))
                        .map(this::toResponse);
        return PageResponse.from(periods);
    }

    @Transactional(readOnly = true)
    public RegistrationPeriodResponse get(Long id) {
        return toResponse(requirePeriod(id));
    }

    @Transactional
    @BusinessAudit(action = "REGISTRATION_PERIOD_CREATE", entityType = "REGISTRATION_PERIOD")
    public RegistrationPeriodResponse create(
            CreateRegistrationPeriodRequest request, Long creatorId) {
        if (!authorization.canManageFaculty(request.facultyId(), creatorId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "An active dean appointment in this faculty is required.");
        }
        Faculty faculty = requireFaculty(request.facultyId());
        Semester semester = requireSemester(request.semesterId());
        User creator =
                userRepository
                        .findById(creatorId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Creating user was not found."));
        validateSchedule(
                request.projectType(),
                request.proposalStartAt(),
                request.proposalEndAt(),
                request.registrationStartAt(),
                request.registrationEndAt(),
                request.reviewDeadlineAt(),
                request.defenseDate(),
                RegistrationPeriodStatus.DRAFT);
        PeriodRules rules = rulesForCreate(request);
        validateRules(rules);

        RegistrationPeriod period = new RegistrationPeriod();
        period.setFaculty(faculty);
        period.setSemester(semester);
        apply(
                period,
                request.name(),
                request.projectType(),
                request.proposalStartAt(),
                request.proposalEndAt(),
                request.registrationStartAt(),
                request.registrationEndAt(),
                request.reviewDeadlineAt(),
                request.defenseDate(),
                rules);
        period.setStatus(RegistrationPeriodStatus.DRAFT);
        period.setCreatedBy(creator);
        return toResponse(registrationPeriodRepository.save(period));
    }

    @Transactional
    @BusinessAudit(
            action = "REGISTRATION_PERIOD_UPDATE",
            entityType = "REGISTRATION_PERIOD",
            entityIdArgument = 0)
    public RegistrationPeriodResponse update(Long id, UpdateRegistrationPeriodRequest request, Long actorId) {
        authorization.requirePeriodManagement(id, actorId);
        RegistrationPeriod period = requirePeriod(id);
        verifyVersion(period, request.version());
        if (period.getStatus() != RegistrationPeriodStatus.DRAFT) {
            throw new BusinessRuleException("Only a draft registration period can be edited.");
        }
        Semester semester = requireSemester(request.semesterId());
        validateSchedule(
                request.projectType(),
                request.proposalStartAt(),
                request.proposalEndAt(),
                request.registrationStartAt(),
                request.registrationEndAt(),
                request.reviewDeadlineAt(),
                request.defenseDate(),
                period.getStatus());
        PeriodRules rules = rulesForUpdate(period, request);
        validateRules(rules);
        period.setSemester(semester);
        apply(
                period,
                request.name(),
                request.projectType(),
                request.proposalStartAt(),
                request.proposalEndAt(),
                request.registrationStartAt(),
                request.registrationEndAt(),
                request.reviewDeadlineAt(),
                request.defenseDate(),
                rules);
        return toResponse(period);
    }

    @Transactional
    @BusinessAudit(
            action = "REGISTRATION_PERIOD_STATUS_CHANGE",
            entityType = "REGISTRATION_PERIOD",
            entityIdArgument = 0)
    public RegistrationPeriodResponse changeStatus(
            Long id, ChangePeriodStatusRequest request, Long actorId) {
        authorization.requirePeriodManagement(id, actorId);
        RegistrationPeriod period = requirePeriod(id);
        verifyVersion(period, request.version());
        if (!TRANSITIONS.get(period.getStatus()).contains(request.status())) {
            throw new BusinessRuleException(
                    "Registration period cannot change from "
                            + period.getStatus()
                            + " to "
                            + request.status()
                            + ".");
        }
        validateSchedule(
                period.getProjectType(),
                period.getProposalStartAt(),
                period.getProposalEndAt(),
                period.getRegistrationStartAt(),
                period.getRegistrationEndAt(),
                period.getReviewDeadlineAt(),
                period.getDefenseDate(),
                request.status());
        validateRules(rulesFor(period));
        RegistrationPeriodStatus previousStatus = period.getStatus();
        period.setStatus(request.status());
        if (previousStatus == RegistrationPeriodStatus.DRAFT
                && request.status() == RegistrationPeriodStatus.ACTIVE) {
            User actor =
                    actorId == null
                            ? period.getCreatedBy()
                            : userRepository
                                    .findById(actorId)
                                    .orElseThrow(
                                            () ->
                                                    new ResourceNotFoundException(
                                                            "Activating user was not found."));
            emailNotificationEventService.registrationOpened(period, actor);
        }
        registrationPeriodRepository.flush();
        return toResponse(period);
    }

    private void apply(
            RegistrationPeriod period,
            String name,
            ProjectType projectType,
            LocalDateTime proposalStartAt,
            LocalDateTime proposalEndAt,
            LocalDateTime registrationStartAt,
            LocalDateTime registrationEndAt,
            LocalDateTime reviewDeadlineAt,
            java.time.LocalDate defenseDate,
            PeriodRules rules) {
        period.setName(name.trim());
        period.setProjectType(projectType);
        period.setProposalStartAt(proposalStartAt);
        period.setProposalEndAt(proposalEndAt);
        period.setRegistrationStartAt(registrationStartAt);
        period.setRegistrationEndAt(registrationEndAt);
        period.setReviewDeadlineAt(reviewDeadlineAt);
        period.setDefenseDate(defenseDate);
        period.setMinGroupMembers(rules.minGroupMembers());
        period.setMaxGroupMembers(rules.maxGroupMembers());
        period.setInvitationExpiryHours(rules.invitationExpiryHours());
        period.setMinCommitteeMembers(rules.minCommitteeMembers());
        period.setMaxCommitteeMembers(rules.maxCommitteeMembers());
    }

    private void validateSchedule(
            ProjectType projectType,
            LocalDateTime proposalStartAt,
            LocalDateTime proposalEndAt,
            LocalDateTime registrationStartAt,
            LocalDateTime registrationEndAt,
            LocalDateTime reviewDeadlineAt,
            java.time.LocalDate defenseDate,
            RegistrationPeriodStatus status) {
        if (!proposalStartAt.isBefore(proposalEndAt)
                || proposalEndAt.isAfter(registrationStartAt)
                || !registrationStartAt.isBefore(registrationEndAt)) {
            throw new BusinessRuleException(
                    "Required order: proposal start < proposal end <= registration start < registration end.");
        }
        boolean requiresReview =
                projectType == ProjectType.SPECIALIZED || projectType == ProjectType.GRADUATION;
        if (!requiresReview && reviewDeadlineAt != null) {
            throw new BusinessRuleException(
                    "Only specialized or graduation projects may have a review deadline.");
        }
        if (reviewDeadlineAt != null && !reviewDeadlineAt.isAfter(registrationEndAt)) {
            throw new BusinessRuleException("Review deadline must be after registration closes.");
        }
        if (projectType != ProjectType.GRADUATION && defenseDate != null) {
            throw new BusinessRuleException("Only graduation projects may have a defense date.");
        }
        if (projectType == ProjectType.GRADUATION
                && defenseDate != null
                && reviewDeadlineAt != null
                && VietnamTime.dateOf(reviewDeadlineAt).isAfter(defenseDate)) {
            throw new BusinessRuleException(
                    "Hạn phản biện không được sau ngày bảo vệ KLTN theo giờ Việt Nam.");
        }
        if (status != RegistrationPeriodStatus.DRAFT
                && status != RegistrationPeriodStatus.CANCELLED) {
            if (requiresReview && reviewDeadlineAt == null) {
                throw new BusinessRuleException(
                        "Review deadline is required before this period can be activated.");
            }
            if (projectType == ProjectType.GRADUATION && defenseDate == null) {
                throw new BusinessRuleException(
                        "Defense date is required before a graduation period can be activated.");
            }
        }
    }

    private void verifyVersion(RegistrationPeriod period, int requestedVersion) {
        if (period.getVersion() != requestedVersion) {
            throw new ConflictException(
                    "The registration period was changed by another user. Reload and try again.");
        }
    }

    private Faculty requireFaculty(Long id) {
        return facultyRepository
                .findById(id)
                .filter(Faculty::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Active faculty was not found."));
    }

    private Semester requireSemester(Long id) {
        return semesterRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Semester was not found."));
    }

    private RegistrationPeriod requirePeriod(Long id) {
        return registrationPeriodRepository
                .findById(id)
                .orElseThrow(
                        () -> new ResourceNotFoundException("Registration period was not found."));
    }

    private PeriodRules rulesForCreate(CreateRegistrationPeriodRequest request) {
        return new PeriodRules(
                orDefault(request.minGroupMembers(), DEFAULT_MIN_GROUP_MEMBERS),
                orDefault(request.maxGroupMembers(), DEFAULT_MAX_GROUP_MEMBERS),
                orDefault(request.invitationExpiryHours(), DEFAULT_INVITATION_EXPIRY_HOURS),
                orDefault(request.minCommitteeMembers(), DEFAULT_MIN_COMMITTEE_MEMBERS),
                orDefault(request.maxCommitteeMembers(), DEFAULT_MAX_COMMITTEE_MEMBERS));
    }

    private PeriodRules rulesForUpdate(
            RegistrationPeriod period, UpdateRegistrationPeriodRequest request) {
        return new PeriodRules(
                request.minGroupMembers() == null
                        ? period.getMinGroupMembers()
                        : request.minGroupMembers(),
                request.maxGroupMembers() == null
                        ? period.getMaxGroupMembers()
                        : request.maxGroupMembers(),
                request.invitationExpiryHours() == null
                        ? period.getInvitationExpiryHours()
                        : request.invitationExpiryHours(),
                request.minCommitteeMembers() == null
                        ? period.getMinCommitteeMembers()
                        : request.minCommitteeMembers(),
                request.maxCommitteeMembers() == null
                        ? period.getMaxCommitteeMembers()
                        : request.maxCommitteeMembers());
    }

    private PeriodRules rulesFor(RegistrationPeriod period) {
        return new PeriodRules(
                period.getMinGroupMembers(),
                period.getMaxGroupMembers(),
                period.getInvitationExpiryHours(),
                period.getMinCommitteeMembers(),
                period.getMaxCommitteeMembers());
    }

    private void validateRules(PeriodRules rules) {
        if (rules.minGroupMembers() < 1
                || rules.minGroupMembers() > 10
                || rules.maxGroupMembers() < 1
                || rules.maxGroupMembers() > 10) {
            throw new BusinessRuleException("Group size limits must be between 1 and 10.");
        }
        if (rules.invitationExpiryHours() < 1 || rules.invitationExpiryHours() > 720) {
            throw new BusinessRuleException("Invitation expiry must be between 1 and 720 hours.");
        }
        if (rules.minCommitteeMembers() < 3
                || rules.minCommitteeMembers() > 5
                || rules.maxCommitteeMembers() < 3
                || rules.maxCommitteeMembers() > 5) {
            throw new BusinessRuleException("Committee size limits must be between 3 and 5.");
        }
        if (rules.minGroupMembers() > rules.maxGroupMembers()) {
            throw new BusinessRuleException("Minimum group size cannot exceed maximum group size.");
        }
        if (rules.minCommitteeMembers() > rules.maxCommitteeMembers()) {
            throw new BusinessRuleException(
                    "Minimum committee size cannot exceed maximum committee size.");
        }
    }

    private int orDefault(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    private RegistrationPeriodResponse toResponse(RegistrationPeriod period) {
        return new RegistrationPeriodResponse(
                period.getId(),
                period.getFaculty().getId(),
                period.getFaculty().getName(),
                period.getSemester().getId(),
                period.getSemester().getAcademicYear(),
                period.getSemester().getSemesterNo(),
                period.getName(),
                period.getProjectType(),
                period.getProposalStartAt(),
                period.getProposalEndAt(),
                period.getRegistrationStartAt(),
                period.getRegistrationEndAt(),
                period.getReviewDeadlineAt(),
                period.getDefenseDate(),
                period.getMinGroupMembers(),
                period.getMaxGroupMembers(),
                period.getInvitationExpiryHours(),
                period.getMinCommitteeMembers(),
                period.getMaxCommitteeMembers(),
                period.getStatus(),
                period.getCreatedBy().getId(),
                period.getCreatedAt(),
                period.getVersion());
    }

    private record PeriodRules(
            int minGroupMembers,
            int maxGroupMembers,
            int invitationExpiryHours,
            int minCommitteeMembers,
            int maxCommitteeMembers) {}
}
