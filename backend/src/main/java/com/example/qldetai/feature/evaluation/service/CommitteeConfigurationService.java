package com.example.qldetai.feature.evaluation.service;

import com.example.qldetai.aspect.BusinessAudit;
import com.example.qldetai.exception.BusinessRuleException;
import com.example.qldetai.exception.ConflictException;
import com.example.qldetai.exception.ResourceNotFoundException;
import com.example.qldetai.feature.academic.repository.RegistrationPeriodRepository;
import com.example.qldetai.feature.evaluation.domain.*;
import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.*;
import com.example.qldetai.feature.evaluation.repository.*;
import com.example.qldetai.feature.identity.repository.LecturerRepository;
import com.example.qldetai.feature.topic.repository.TopicSupervisorRepository;
import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommitteeConfigurationService {
    private final EvaluationCommitteeRepository committees;
    private final CommitteeMemberRepository members;
    private final RegistrationPeriodRepository periods;
    private final LecturerRepository lecturers;
    private final RubricRepository rubrics;
    private final EvaluationAssignmentRepository evaluations;
    private final DefenseSessionRepository defenses;
    private final TopicSupervisorRepository supervisors;
    private final EvaluationActorAccess actorAccess;
    private final EvaluationCommitteeService committeeService;
    private final Clock clock;
    private final jakarta.persistence.EntityManager entityManager;

    public CommitteeConfigurationService(EvaluationCommitteeRepository committees,
            CommitteeMemberRepository members, RegistrationPeriodRepository periods,
            LecturerRepository lecturers, RubricRepository rubrics,
            EvaluationAssignmentRepository evaluations, DefenseSessionRepository defenses,
            TopicSupervisorRepository supervisors, EvaluationActorAccess actorAccess,
            EvaluationCommitteeService committeeService, Clock clock,
            jakarta.persistence.EntityManager entityManager) {
        this.committees = committees;
        this.members = members;
        this.periods = periods;
        this.lecturers = lecturers;
        this.rubrics = rubrics;
        this.evaluations = evaluations;
        this.defenses = defenses;
        this.supervisors = supervisors;
        this.actorAccess = actorAccess;
        this.committeeService = committeeService;
        this.clock = clock;
        this.entityManager = entityManager;
    }

    @Transactional
    @BusinessAudit(action = "COMMITTEE_CONFIGURE", entityType = "EVALUATION_COMMITTEE")
    public EvaluationCommitteeResponse save(Long id, SaveCommitteeRequest request, Long actorId) {
        actorAccess.manager(actorId);
        var period = periods.findById(request.periodId())
                .orElseThrow(() -> new ResourceNotFoundException("Registration period was not found."));
        var rubric = rubrics.findById(request.rubricId())
                .orElseThrow(() -> new ResourceNotFoundException("Rubric was not found."));
        if (!rubric.getComponent().getPeriod().getId().equals(period.getId())
                || rubric.getComponent().getComponentType() != AssessmentComponentType.COMMITTEE
                || !"LOCKED".equals(rubric.getStatus())
                || !"LOCKED".equals(rubric.getComponent().getStatus())) {
            throw new BusinessRuleException("Chọn bộ tiêu chí hội đồng đã khóa thuộc đợt đăng ký này.");
        }
        if (request.members().size() < period.getMinCommitteeMembers()
                || request.members().size() > period.getMaxCommitteeMembers()) {
            throw new BusinessRuleException("Hội đồng cần từ " + period.getMinCommitteeMembers()
                    + " đến " + period.getMaxCommitteeMembers() + " thành viên theo cấu hình đợt đăng ký.");
        }
        var uniqueLecturers = new HashSet<Long>();
        int chairs = 0;
        int secretaries = 0;
        for (var input : request.members()) {
            if (!uniqueLecturers.add(input.lecturerId())) {
                throw new BusinessRuleException("Mỗi giảng viên chỉ đảm nhiệm một vai trò trong hội đồng.");
            }
            if (input.roleName() == null || input.roleName().isBlank() || input.roleName().trim().length() > 100) {
                throw new BusinessRuleException("Nhập vai trò cho từng thành viên, tối đa 100 ký tự.");
            }
            if (!lecturers.existsById(input.lecturerId())) {
                throw new ResourceNotFoundException("Lecturer was not found.");
            }
            if (role(input.roleName()) == CommitteeRole.CHAIR) chairs++;
            if (role(input.roleName()) == CommitteeRole.SECRETARY) secretaries++;
        }
        if (chairs != 1 || secretaries != 1) {
            throw new BusinessRuleException("Hội đồng cần đúng một Chủ tịch và một Thư ký.");
        }

        EvaluationCommittee committee;
        if (id == null) {
            committee = new EvaluationCommittee();
            committee.setPeriod(period);
            committee.setFaculty(period.getFaculty());
            committee.setCreatedBy(actorAccess.user(actorId));
            committee.setCreatedAt(LocalDateTime.now(clock));
        } else {
            committee = committees.findForUpdate(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Committee was not found."));
            if (!committee.getPeriod().getId().equals(period.getId())) {
                throw new BusinessRuleException("Hội đồng không thuộc đợt đăng ký đã chọn.");
            }
            if (request.version() == null || request.version() != committee.getVersion()) {
                throw new ConflictException("Hội đồng đã được cập nhật. Vui lòng tải lại trước khi lưu.");
            }
            var current = members.findByCommitteeId(id).stream()
                    .sorted(java.util.Comparator.comparingInt(CommitteeMember::getSlotNo)).toList();
            boolean sameRoster = current.size() == request.members().size();
            for (int index = 0; sameRoster && index < current.size(); index++) {
                var old = current.get(index);
                var input = request.members().get(index);
                sameRoster = old.getLecturer().getId().equals(input.lecturerId())
                        && old.getCommitteeRole() == role(input.roleName())
                        && Objects.equals(roleName(old), input.roleName().trim());
            }
            boolean sameConfiguration = sameRoster && committee.getName().equals(request.name().trim())
                    && committee.getRubric() != null && committee.getRubric().getId().equals(rubric.getId());
            if (sameConfiguration) return committeeService.committee(committee);
            if (committee.getStatus() == CommitteeStatus.COMPLETED || evaluations.existsByDefenseCommitteeId(id)) {
                throw new BusinessRuleException("Hội đồng đã có phân công chấm hoặc đã hoàn tất. Hãy tạo hội đồng mới để thay đổi thành viên hoặc bộ tiêu chí.");
            }
            for (var defense : defenses.findByCommitteeId(id)) {
                if (supervisors.findByTopicIdOrderBySlotNoAsc(defense.getAssignment().getTopic().getId())
                        .stream().anyMatch(supervisor -> uniqueLecturers.contains(supervisor.getLecturer().getId()))) {
                    throw new BusinessRuleException("Giảng viên hướng dẫn không thể tham gia hội đồng chấm đề tài của mình.");
                }
            }
            if (committees.advanceVersion(id, committee.getVersion()) != 1) {
                throw new ConflictException("Hội đồng đã được cập nhật. Vui lòng tải lại trước khi lưu.");
            }
            entityManager.refresh(committee);
            members.deleteAll(current);
            members.flush();
        }
        committee.setName(request.name().trim());
        committee.setRubric(rubric);
        committee = committees.saveAndFlush(committee);
        for (int index = 0; index < request.members().size(); index++) {
            var input = request.members().get(index);
            var member = new CommitteeMember();
            member.setCommittee(committee);
            member.setLecturer(lecturers.getReferenceById(input.lecturerId()));
            member.setSlotNo(index + 1);
            member.setCommitteeRole(role(input.roleName()));
            member.setRoleLabel(input.roleName().trim());
            members.save(member);
        }
        members.flush();
        return committeeService.committee(committee);
    }

    private static CommitteeRole role(String label) {
        String normalized = Normalizer.normalize(label.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return switch (normalized) {
            case "chu tich", "chu tich hoi dong", "chair" -> CommitteeRole.CHAIR;
            case "thu ky", "thu ky hoi dong", "secretary" -> CommitteeRole.SECRETARY;
            default -> CommitteeRole.MEMBER;
        };
    }

    private static String roleName(CommitteeMember member) {
        if (member.getRoleLabel() != null) return member.getRoleLabel();
        return switch (member.getCommitteeRole()) {
            case CHAIR -> "Chủ tịch";
            case SECRETARY -> "Thư ký";
            case MEMBER -> "Ủy viên";
        };
    }
}
