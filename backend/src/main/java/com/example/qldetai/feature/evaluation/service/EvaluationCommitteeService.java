package com.example.qldetai.feature.evaluation.service;

import com.example.qldetai.common.audit.BusinessAudit;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EvaluationCommitteeService {

    private final EvaluationCommitteeRepository committees;
    private final CommitteeMemberRepository committeeMembers;
    private final RegistrationPeriodRepository periods;
    private final LecturerRepository lecturers;
    private final EvaluationActorAccess actorAccess;


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
        Page<EvaluationCommittee> found =
                committees
                        .findAll(
                                specification,
                                PageRequest.of(
                                        page,
                                        size,
                                        Sort.by("createdAt")
                                                .descending()
                                                .and(Sort.by("id").descending())));
        Map<Long, List<CommitteeMember>> membersByCommittee = found.isEmpty() ? Map.of() : committeeMembers
                .findByCommitteeIdIn(found.getContent().stream().map(EvaluationCommittee::getId).toList()).stream()
                .collect(java.util.stream.Collectors.groupingBy(member -> member.getCommittee().getId()));
        return PageResponse.from(found.map(committee -> committee(committee,
                membersByCommittee.getOrDefault(committee.getId(), List.of()))));
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
        if (request.slotNo() > 5 || request.slotNo() > maxMembers) {
            throw new BusinessRuleException(
                    "Committee member slot exceeds this registration period's configured maximum.");
        }
        List<CommitteeMember> currentMembers = committeeMembers.findByCommitteeIdForUpdate(committeeId);
        if (currentMembers.size() >= Math.min(5, maxMembers)) {
            throw new BusinessRuleException(
                    "This committee has reached the configured maximum number of members.");
        }
        if (currentMembers.stream()
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
                .findByIdForUpdate(committeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Committee was not found."));
    }

    private EvaluationCommitteeResponse committee(EvaluationCommittee committee, List<CommitteeMember> currentMembers) {
        List<CommitteeMemberResponse> members =
                currentMembers.stream()
                        .sorted(Comparator.comparingInt(CommitteeMember::getSlotNo))
                        .map(
                                member ->
                                        new CommitteeMemberResponse(
                                                member.getId(),
                                                member.getLecturer().getId(),
                                                member.getLecturer().getLecturerCode(),
                                                member.getLecturer().getUser().getFullName(),
                                                member.getSlotNo(),
                                                member.getCommitteeRole()))
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
                members);
    }
}
