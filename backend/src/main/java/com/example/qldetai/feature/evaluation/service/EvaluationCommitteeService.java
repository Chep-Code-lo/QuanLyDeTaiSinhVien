package com.example.qldetai.feature.evaluation.service;

import com.example.qldetai.aspect.BusinessAudit;
import com.example.qldetai.common.api.PageResponse;
import com.example.qldetai.exception.BusinessRuleException;
import com.example.qldetai.exception.ConflictException;
import com.example.qldetai.exception.ResourceNotFoundException;
import com.example.qldetai.feature.academic.domain.RegistrationPeriod;
import com.example.qldetai.feature.academic.repository.RegistrationPeriodRepository;
import com.example.qldetai.feature.evaluation.domain.CommitteeMember;
import com.example.qldetai.feature.evaluation.domain.CommitteeRole;
import com.example.qldetai.feature.evaluation.domain.CommitteeStatus;
import com.example.qldetai.feature.evaluation.domain.EvaluationCommittee;
import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.AddCommitteeMemberRequest;
import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.CommitteeMemberResponse;
import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.CreateCommitteeRequest;
import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.EvaluationCommitteeResponse;
import com.example.qldetai.feature.evaluation.repository.CommitteeMemberRepository;
import com.example.qldetai.feature.evaluation.repository.EvaluationCommitteeRepository;
import com.example.qldetai.feature.identity.repository.LecturerRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class EvaluationCommitteeService {

    private final EvaluationCommitteeRepository committees;
    private final CommitteeMemberRepository committeeMembers;
    private final RegistrationPeriodRepository periods;
    private final LecturerRepository lecturers;
    private final Clock clock;
    private final EvaluationActorAccess actorAccess;

    public EvaluationCommitteeService(
            EvaluationCommitteeRepository committees,
            CommitteeMemberRepository committeeMembers,
            RegistrationPeriodRepository periods,
            LecturerRepository lecturers,
            Clock clock,
            EvaluationActorAccess actorAccess) {
        this.committees = committees;
        this.committeeMembers = committeeMembers;
        this.periods = periods;
        this.lecturers = lecturers;
        this.clock = clock;
        this.actorAccess = actorAccess;
    }

    @Transactional(readOnly = true)
    public PageResponse<EvaluationCommitteeResponse> listCommittees(
            Long periodId, int page, int size) {
        Specification<EvaluationCommittee> specification =
                (root, query, builder) -> builder.conjunction();
        if (periodId != null) {
            specification =
                    specification.and(
                            (root, query, builder) ->
                                    builder.equal(root.join("period").get("id"), periodId));
        }
        return PageResponse.from(
                committees
                        .findAll(
                                specification,
                                PageRequest.of(
                                        page,
                                        size,
                                        Sort.by("createdAt")
                                                .descending()
                                                .and(Sort.by("id").descending())))
                        .map(this::committee));
    }

    @Transactional
    @BusinessAudit(action = "COMMITTEE_CREATE", entityType = "EVALUATION_COMMITTEE")
    public Long createCommittee(CreateCommitteeRequest request, Long actorId) {
        actorAccess.manager(actorId);
        RegistrationPeriod period =
                periods.findById(request.periodId())
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Registration period was not found."));

        EvaluationCommittee committee = new EvaluationCommittee();
        committee.setPeriod(period);
        committee.setFaculty(period.getFaculty());
        committee.setName(request.name().trim());
        committee.setCreatedBy(actorAccess.user(actorId));
        committee.setCreatedAt(LocalDateTime.now(clock));
        return committees.save(committee).getId();
    }

    @Transactional
    @BusinessAudit(
            action = "COMMITTEE_MEMBER_ADD",
            entityType = "EVALUATION_COMMITTEE",
            entityIdArgument = 0)
    public void addMember(Long committeeId, AddCommitteeMemberRequest request, Long actorId) {
        actorAccess.manager(actorId);
        EvaluationCommittee committee = committee(committeeId);
        if (committee.getStatus() != CommitteeStatus.DRAFT) {
            throw new BusinessRuleException("Only a draft committee can be changed.");
        }
        int maxMembers = committee.getPeriod().getMaxCommitteeMembers();
        if (request.slotNo() > maxMembers) {
            throw new BusinessRuleException(
                    "Committee member slot exceeds this registration period's configured maximum.");
        }
        if (committeeMembers.findByCommitteeId(committeeId).size() >= maxMembers) {
            throw new BusinessRuleException(
                    "This committee has reached the configured maximum number of members.");
        }
        if (committeeMembers.findByCommitteeId(committeeId).stream()
                .anyMatch(
                        member ->
                                member.getSlotNo() == request.slotNo()
                                        || member.getLecturer().getId().equals(request.lecturerId())
                                        || (member.getCommitteeRole() == request.role()
                                                && request.role() != CommitteeRole.MEMBER))) {
            throw new ConflictException("Duplicate committee slot, lecturer, or office.");
        }

        CommitteeMember member = new CommitteeMember();
        member.setCommittee(committee);
        member.setLecturer(
                lecturers
                        .findById(request.lecturerId())
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Lecturer was not found.")));
        member.setSlotNo(request.slotNo());
        member.setCommitteeRole(request.role());
        committeeMembers.save(member);
    }

    private EvaluationCommittee committee(Long committeeId) {
        return committees
                .findById(committeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Committee was not found."));
    }

    EvaluationCommitteeResponse committee(EvaluationCommittee committee) {
        List<CommitteeMemberResponse> members =
                committeeMembers.findByCommitteeId(committee.getId()).stream()
                        .sorted(Comparator.comparingInt(CommitteeMember::getSlotNo))
                        .map(
                                member ->
                                        new CommitteeMemberResponse(
                                                member.getId(),
                                                member.getLecturer().getId(),
                                                member.getLecturer().getLecturerCode(),
                                                member.getLecturer().getUser().getFullName(),
                                                member.getSlotNo(),
                                                member.getCommitteeRole(),
                                                member.getRoleLabel()))
                        .toList();
        return new EvaluationCommitteeResponse(
                committee.getId(),
                committee.getPeriod().getId(),
                committee.getPeriod().getName(),
                committee.getFaculty().getId(),
                committee.getFaculty().getName(),
                committee.getName(),
                committee.getStatus(),
                committee.getCreatedBy().getId(),
                committee.getCreatedBy().getFullName(),
                committee.getCreatedAt(),
                committee.getVersion(),
                members,
                committee.getRubric() == null ? null : committee.getRubric().getId());
    }
}
