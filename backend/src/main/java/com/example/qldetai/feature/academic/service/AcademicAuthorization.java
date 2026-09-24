package com.example.qldetai.feature.academic.service;

import com.example.qldetai.feature.academic.repository.DepartmentRepository;
import com.example.qldetai.feature.academic.repository.RegistrationPeriodRepository;
import com.example.qldetai.feature.evaluation.repository.AssessmentComponentRepository;
import com.example.qldetai.feature.evaluation.repository.DefenseSessionRepository;
import com.example.qldetai.feature.evaluation.repository.EvaluationAssignmentRepository;
import com.example.qldetai.feature.evaluation.repository.EvaluationCommitteeRepository;
import com.example.qldetai.feature.evaluation.repository.GradeChangeRequestRepository;
import com.example.qldetai.feature.evaluation.repository.ResultVersionRepository;
import com.example.qldetai.feature.evaluation.repository.RubricRepository;
import com.example.qldetai.feature.evaluation.repository.ScoreSheetRepository;
import com.example.qldetai.feature.groupregistration.repository.TopicRegistrationRepository;
import com.example.qldetai.feature.identity.domain.PositionCode;
import com.example.qldetai.feature.identity.repository.AcademicAppointmentRepository;
import com.example.qldetai.feature.progress.repository.MilestoneRepository;
import com.example.qldetai.feature.progress.repository.ReportSubmissionRepository;
import com.example.qldetai.feature.project.repository.ProjectAssignmentRepository;
import com.example.qldetai.feature.topic.repository.TopicRepository;
import com.example.qldetai.security.UserPrincipal;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component("academicAuthorization")
@Transactional(readOnly = true)
public class AcademicAuthorization {

    private final AcademicAppointmentRepository academicAppointmentRepository;
    private final DepartmentRepository departmentRepository;
    private final RegistrationPeriodRepository registrationPeriodRepository;
    private final TopicRepository topicRepository;
    private final TopicRegistrationRepository topicRegistrationRepository;
    private final ProjectAssignmentRepository projectAssignmentRepository;
    private final AssessmentComponentRepository assessmentComponentRepository;
    private final RubricRepository rubricRepository;
    private final EvaluationAssignmentRepository evaluationAssignmentRepository;
    private final EvaluationCommitteeRepository evaluationCommitteeRepository;
    private final DefenseSessionRepository defenseSessionRepository;
    private final ResultVersionRepository resultVersionRepository;
    private final ScoreSheetRepository scoreSheetRepository;
    private final GradeChangeRequestRepository gradeChangeRequestRepository;
    private final MilestoneRepository milestoneRepository;
    private final ReportSubmissionRepository reportSubmissionRepository;
    private final Clock clock;

    public AcademicAuthorization(
            AcademicAppointmentRepository academicAppointmentRepository,
            DepartmentRepository departmentRepository,
            RegistrationPeriodRepository registrationPeriodRepository,
            TopicRepository topicRepository,
            TopicRegistrationRepository topicRegistrationRepository,
            ProjectAssignmentRepository projectAssignmentRepository,
            AssessmentComponentRepository assessmentComponentRepository,
            RubricRepository rubricRepository,
            EvaluationAssignmentRepository evaluationAssignmentRepository,
            EvaluationCommitteeRepository evaluationCommitteeRepository,
            DefenseSessionRepository defenseSessionRepository,
            ResultVersionRepository resultVersionRepository,
            ScoreSheetRepository scoreSheetRepository,
            GradeChangeRequestRepository gradeChangeRequestRepository,
            MilestoneRepository milestoneRepository,
            ReportSubmissionRepository reportSubmissionRepository,
            Clock clock) {
        this.academicAppointmentRepository = academicAppointmentRepository;
        this.departmentRepository = departmentRepository;
        this.registrationPeriodRepository = registrationPeriodRepository;
        this.topicRepository = topicRepository;
        this.topicRegistrationRepository = topicRegistrationRepository;
        this.projectAssignmentRepository = projectAssignmentRepository;
        this.assessmentComponentRepository = assessmentComponentRepository;
        this.rubricRepository = rubricRepository;
        this.evaluationAssignmentRepository = evaluationAssignmentRepository;
        this.evaluationCommitteeRepository = evaluationCommitteeRepository;
        this.defenseSessionRepository = defenseSessionRepository;
        this.resultVersionRepository = resultVersionRepository;
        this.scoreSheetRepository = scoreSheetRepository;
        this.gradeChangeRequestRepository = gradeChangeRequestRepository;
        this.milestoneRepository = milestoneRepository;
        this.reportSubmissionRepository = reportSubmissionRepository;
        this.clock = clock;
    }

    public boolean canManageFaculty(Long facultyId, Authentication authentication) {
        UserPrincipal principal = principal(authentication);
        return principal != null && academicAppointmentRepository.hasActiveFacultyAppointment(
                principal.id(), PositionCode.DEAN, facultyId, LocalDateTime.now(clock));
    }

    public boolean canManageDepartment(Long departmentId, Authentication authentication) {
        UserPrincipal principal = principal(authentication);
        if (principal == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (academicAppointmentRepository.hasActiveDepartmentAppointment(
                principal.id(), PositionCode.DEPARTMENT_HEAD, departmentId, now)) {
            return true;
        }
        return departmentRepository.findById(departmentId)
                .map(department -> academicAppointmentRepository.hasActiveFacultyAppointment(
                        principal.id(), PositionCode.DEAN, department.getFaculty().getId(), now))
                .orElse(false);
    }

    public boolean canManagePeriod(Long periodId, Authentication authentication) {
        return registrationPeriodRepository.findById(periodId)
                .map(period -> canManageFaculty(period.getFaculty().getId(), authentication))
                .orElse(false);
    }

    /**
     * A department head manages resources owned by that department; a dean manages every
     * department in the dean's faculty.  These methods are deliberately resource based so
     * controller expressions cannot accidentally trust a client-supplied faculty id.
     */
    public boolean canManageTopic(Long topicId, Authentication authentication) {
        return topicRepository.findById(topicId)
                .map(topic -> canManageDepartment(topic.getDepartment().getId(), authentication))
                .orElse(false);
    }

    public boolean canManageRegistration(Long registrationId, Authentication authentication) {
        return topicRegistrationRepository.findById(registrationId)
                .map(registration -> canManageTopic(registration.getTopic().getId(), authentication))
                .orElse(false);
    }

    public boolean canManageAssignment(Long assignmentId, Authentication authentication) {
        return projectAssignmentRepository.findById(assignmentId)
                .map(assignment -> canManageTopic(assignment.getTopic().getId(), authentication))
                .orElse(false);
    }

    public boolean canManageAssessmentComponent(Long componentId, Authentication authentication) {
        return assessmentComponentRepository.findById(componentId)
                .map(component -> canManagePeriod(component.getPeriod().getId(), authentication))
                .orElse(false);
    }

    public boolean canManageRubric(Long rubricId, Authentication authentication) {
        return rubricRepository.findById(rubricId)
                .map(rubric -> canManageAssessmentComponent(rubric.getComponent().getId(), authentication))
                .orElse(false);
    }

    public boolean canManageEvaluationAssignment(Long evaluationId, Authentication authentication) {
        return evaluationAssignmentRepository.findById(evaluationId)
                .map(evaluation -> canManageAssignment(evaluation.getAssignment().getId(), authentication))
                .orElse(false);
    }

    public boolean canManageCommittee(Long committeeId, Authentication authentication) {
        return evaluationCommitteeRepository.findById(committeeId)
                .map(committee -> canManagePeriod(committee.getPeriod().getId(), authentication))
                .orElse(false);
    }

    public boolean canManageDefense(Long defenseId, Authentication authentication) {
        return defenseSessionRepository.findById(defenseId)
                .map(defense -> canManageAssignment(defense.getAssignment().getId(), authentication))
                .orElse(false);
    }

    public boolean canManageResult(Long resultId, Authentication authentication) {
        return resultVersionRepository.findById(resultId)
                .map(result -> canManageAssignment(result.getAssignment().getId(), authentication))
                .orElse(false);
    }

    public boolean canManageScoreSheet(Long sheetId, Authentication authentication) {
        return scoreSheetRepository.findById(sheetId)
                .map(sheet -> canManageEvaluationAssignment(sheet.getEvaluation().getId(), authentication))
                .orElse(false);
    }

    public boolean canManageGradeChange(Long requestId, Authentication authentication) {
        return gradeChangeRequestRepository.findById(requestId)
                .map(request -> canManageScoreSheet(request.getSheet().getId(), authentication))
                .orElse(false);
    }

    public boolean canManageMilestone(Long milestoneId, Authentication authentication) {
        return milestoneRepository.findById(milestoneId)
                .map(milestone -> canManageAssignment(milestone.getAssignment().getId(), authentication))
                .orElse(false);
    }

    public boolean canManageSubmission(Long submissionId, Authentication authentication) {
        return reportSubmissionRepository.findById(submissionId)
                .map(submission -> canManageMilestone(submission.getMilestone().getId(), authentication))
                .orElse(false);
    }

    private UserPrincipal principal(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal
                ? principal
                : null;
    }
}
