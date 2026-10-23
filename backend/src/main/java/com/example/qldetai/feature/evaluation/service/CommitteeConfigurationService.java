package com.example.qldetai.feature.evaluation.service;

import com.example.qldetai.common.audit.BusinessAudit;
import com.example.qldetai.exception.BusinessRuleException;
import com.example.qldetai.exception.ConflictException;
import com.example.qldetai.exception.ResourceNotFoundException;
import com.example.qldetai.feature.academic.domain.RegistrationPeriod;
import com.example.qldetai.feature.academic.repository.RegistrationPeriodRepository;
import com.example.qldetai.feature.evaluation.domain.AssessmentComponentType;
import com.example.qldetai.feature.evaluation.domain.CommitteeMember;
import com.example.qldetai.feature.evaluation.domain.CommitteeRole;
import com.example.qldetai.feature.evaluation.domain.CommitteeStatus;
import com.example.qldetai.feature.evaluation.domain.EvaluationCommittee;
import com.example.qldetai.feature.evaluation.domain.Rubric;
import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.CommitteeMemberInput;
import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.EvaluationCommitteeResponse;
import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.SaveCommitteeRequest;
import com.example.qldetai.feature.evaluation.repository.CommitteeMemberRepository;
import com.example.qldetai.feature.evaluation.repository.EvaluationCommitteeRepository;
import com.example.qldetai.feature.evaluation.repository.RubricRepository;
import com.example.qldetai.feature.identity.repository.LecturerRepository;

import jakarta.persistence.EntityManager;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Saves the complete editable committee roster in one transaction. */
@Service
@RequiredArgsConstructor
public class CommitteeConfigurationService {

    private final EvaluationCommitteeRepository committees;
    private final CommitteeMemberRepository members;
    private final RegistrationPeriodRepository periods;
    private final RubricRepository rubrics;
    private final LecturerRepository lecturers;
    private final EvaluationActorAccess actorAccess;
    private final com.example.qldetai.feature.academic.service.AcademicAuthorization academicAuthorization;
    private final EvaluationCommitteeService committeeService;
    private final EntityManager entityManager;

    @Transactional
    @BusinessAudit(action = "COMMITTEE_CONFIGURE", entityType = "EVALUATION_COMMITTEE")
    public EvaluationCommitteeResponse save(Long id, SaveCommitteeRequest request, Long actorId) {
        actorAccess.manager(actorId);
        academicAuthorization.requirePeriodManagement(request.periodId(), actorId);
        RegistrationPeriod period =
                periods.findById(request.periodId())
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Registration period was not found."));
        Rubric rubric =
                rubrics.findById(request.rubricId())
                        .orElseThrow(() -> new ResourceNotFoundException("Rubric was not found."));
        validateRubric(period, rubric);
        validateMembers(period, request.members());

        EvaluationCommittee committee;
        Integer originalVersion = null;
        if (id == null) {
            committee = new EvaluationCommittee();
            committee.setPeriod(period);
            committee.setFaculty(period.getFaculty());
            committee.setCreatedBy(actorAccess.user(actorId));
        } else {
            committee =
                    committees
                            .findByIdForUpdate(id)
                            .orElseThrow(
                                    () ->
                                            new ResourceNotFoundException(
                                                    "Committee was not found."));
            if (!committee.getPeriod().getId().equals(period.getId())) {
                throw new BusinessRuleException("Hội đồng không thuộc đợt đăng ký đã chọn.");
            }
            if (request.version() == null || request.version() != committee.getVersion()) {
                throw new ConflictException(
                        "Hội đồng đã được cập nhật. Vui lòng tải lại trước khi lưu.");
            }
            List<CommitteeMember> current =
                    members.findByCommitteeIdForUpdate(id).stream()
                            .sorted(Comparator.comparingInt(CommitteeMember::getSlotNo))
                            .toList();
            if (sameConfiguration(committee, rubric, request, current)) {
                return committeeService.committee(committee, current);
            }
            if (committee.getStatus() != CommitteeStatus.DRAFT) {
                throw new BusinessRuleException("Chỉ có thể thay đổi hội đồng ở trạng thái nháp.");
            }
            originalVersion = committee.getVersion();
            members.deleteAll(current);
            members.flush();
        }

        committee.setName(request.name().trim());
        committee.setRubric(rubric);
        committee = committees.saveAndFlush(committee);
        for (int index = 0; index < request.members().size(); index++) {
            CommitteeMemberInput input = request.members().get(index);
            CommitteeMember member = new CommitteeMember();
            member.setCommittee(committee);
            member.setLecturer(lecturers.getReferenceById(input.lecturerId()));
            member.setSlotNo(index + 1);
            member.setCommitteeRole(role(input.roleName()));
            member.setRoleLabel(input.roleName().trim());
            members.save(member);
        }
        members.flush();
        if (originalVersion != null && committee.getVersion() == originalVersion) {
            if (committees.advanceVersion(committee.getId(), originalVersion) != 1) {
                throw new ConflictException(
                        "Hội đồng đã được cập nhật. Vui lòng tải lại trước khi lưu.");
            }
            entityManager.refresh(committee);
        }
        return committeeService.committee(committee);
    }

    private void validateRubric(RegistrationPeriod period, Rubric rubric) {
        if (!rubric.getComponent().getPeriod().getId().equals(period.getId())
                || rubric.getComponent().getComponentType() != AssessmentComponentType.COMMITTEE
                || !"LOCKED".equals(rubric.getStatus())
                || !"LOCKED".equals(rubric.getComponent().getStatus())) {
            throw new BusinessRuleException(
                    "Chọn bộ tiêu chí hội đồng đã khóa thuộc đợt đăng ký này.");
        }
    }

    private void validateMembers(
            RegistrationPeriod period, List<CommitteeMemberInput> requestedMembers) {
        if (requestedMembers.size() < period.getMinCommitteeMembers()
                || requestedMembers.size() > period.getMaxCommitteeMembers()) {
            throw new BusinessRuleException(
                    "Hội đồng cần từ "
                            + period.getMinCommitteeMembers()
                            + " đến "
                            + period.getMaxCommitteeMembers()
                            + " thành viên theo cấu hình đợt đăng ký.");
        }

        Set<Long> lecturerIds = new HashSet<>();
        int chairs = 0;
        int secretaries = 0;
        for (CommitteeMemberInput member : requestedMembers) {
            if (!lecturerIds.add(member.lecturerId())) {
                throw new BusinessRuleException(
                        "Mỗi giảng viên chỉ đảm nhiệm một vai trò trong hội đồng.");
            }
            if (!lecturers.existsById(member.lecturerId())) {
                throw new ResourceNotFoundException("Lecturer was not found.");
            }
            CommitteeRole role = role(member.roleName());
            if (role == CommitteeRole.CHAIR) {
                chairs++;
            } else if (role == CommitteeRole.SECRETARY) {
                secretaries++;
            }
        }
        if (chairs != 1 || secretaries != 1) {
            throw new BusinessRuleException("Hội đồng cần đúng một Chủ tịch và một Thư ký.");
        }
    }

    private static boolean sameConfiguration(
            EvaluationCommittee committee,
            Rubric rubric,
            SaveCommitteeRequest request,
            List<CommitteeMember> current) {
        if (!committee.getName().equals(request.name().trim())
                || committee.getRubric() == null
                || !committee.getRubric().getId().equals(rubric.getId())
                || current.size() != request.members().size()) {
            return false;
        }
        for (int index = 0; index < current.size(); index++) {
            CommitteeMember existing = current.get(index);
            CommitteeMemberInput requested = request.members().get(index);
            if (!existing.getLecturer().getId().equals(requested.lecturerId())
                    || existing.getCommitteeRole() != role(requested.roleName())
                    || !Objects.equals(roleName(existing), requested.roleName().trim())) {
                return false;
            }
        }
        return true;
    }

    private static CommitteeRole role(String label) {
        String normalized =
                Normalizer.normalize(label.trim(), Normalizer.Form.NFD)
                        .replaceAll("\\p{M}", "")
                        .toLowerCase(Locale.ROOT)
                        .replaceAll("\\s+", " ");
        return switch (normalized) {
            case "chu tich", "chu tich hoi dong", "chair" -> CommitteeRole.CHAIR;
            case "thu ky", "thu ky hoi dong", "secretary" -> CommitteeRole.SECRETARY;
            default -> CommitteeRole.MEMBER;
        };
    }

    private static String roleName(CommitteeMember member) {
        if (member.getRoleLabel() != null && !member.getRoleLabel().isBlank()) {
            return member.getRoleLabel();
        }
        return switch (member.getCommitteeRole()) {
            case CHAIR -> "Chủ tịch";
            case SECRETARY -> "Thư ký";
            case MEMBER -> "Ủy viên";
        };
    }
}
