package com.example.qldetai.feature.evaluation.service;

import com.example.qldetai.feature.academic.service.AcademicAuthorization;
import com.example.qldetai.feature.evaluation.repository.AssessmentComponentRepository;
import com.example.qldetai.feature.evaluation.repository.DefenseSessionRepository;
import com.example.qldetai.feature.evaluation.repository.EvaluationAssignmentRepository;
import com.example.qldetai.feature.evaluation.repository.EvaluationCommitteeRepository;
import com.example.qldetai.feature.evaluation.repository.GradeChangeRequestRepository;
import com.example.qldetai.feature.evaluation.repository.ResultVersionRepository;
import com.example.qldetai.feature.evaluation.repository.RubricRepository;
import com.example.qldetai.feature.evaluation.repository.ScoreSheetRepository;
import com.example.qldetai.feature.project.service.ProjectAuthorization;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component("evaluationAuthorization")
@Transactional(readOnly = true)
public class EvaluationAuthorization {

    private final AssessmentComponentRepository assessmentComponentRepository;
    private final RubricRepository rubricRepository;
    private final EvaluationAssignmentRepository evaluationAssignmentRepository;
    private final EvaluationCommitteeRepository evaluationCommitteeRepository;
    private final DefenseSessionRepository defenseSessionRepository;
    private final ResultVersionRepository resultVersionRepository;
    private final ScoreSheetRepository scoreSheetRepository;
    private final GradeChangeRequestRepository gradeChangeRequestRepository;
    private final AcademicAuthorization academicAuthorization;
    private final ProjectAuthorization projectAuthorization;

    public EvaluationAuthorization(
            AssessmentComponentRepository assessmentComponentRepository,
            RubricRepository rubricRepository,
            EvaluationAssignmentRepository evaluationAssignmentRepository,
            EvaluationCommitteeRepository evaluationCommitteeRepository,
            DefenseSessionRepository defenseSessionRepository,
            ResultVersionRepository resultVersionRepository,
            ScoreSheetRepository scoreSheetRepository,
            GradeChangeRequestRepository gradeChangeRequestRepository,
            AcademicAuthorization academicAuthorization,
            ProjectAuthorization projectAuthorization) {
        this.assessmentComponentRepository = assessmentComponentRepository;
        this.rubricRepository = rubricRepository;
        this.evaluationAssignmentRepository = evaluationAssignmentRepository;
        this.evaluationCommitteeRepository = evaluationCommitteeRepository;
        this.defenseSessionRepository = defenseSessionRepository;
        this.resultVersionRepository = resultVersionRepository;
        this.scoreSheetRepository = scoreSheetRepository;
        this.gradeChangeRequestRepository = gradeChangeRequestRepository;
        this.academicAuthorization = academicAuthorization;
        this.projectAuthorization = projectAuthorization;
    }

    public boolean canManageAssessmentComponent(Long componentId, Authentication authentication) {
        return assessmentComponentRepository
                .findById(componentId)
                .map(
                        component ->
                                academicAuthorization.canManagePeriod(
                                        component.getPeriod().getId(), authentication))
                .orElse(false);
    }

    public boolean canManageRubric(Long rubricId, Authentication authentication) {
        return rubricRepository
                .findById(rubricId)
                .map(
                        rubric ->
                                canManageAssessmentComponent(
                                        rubric.getComponent().getId(), authentication))
                .orElse(false);
    }

    public boolean canManageEvaluationAssignment(Long evaluationId, Authentication authentication) {
        return evaluationAssignmentRepository
                .findById(evaluationId)
                .map(
                        evaluation ->
                                projectAuthorization.canManageAssignment(
                                        evaluation.getAssignment().getId(), authentication))
                .orElse(false);
    }

    public boolean canManageCommittee(Long committeeId, Authentication authentication) {
        return evaluationCommitteeRepository
                .findById(committeeId)
                .map(
                        committee ->
                                academicAuthorization.canManagePeriod(
                                        committee.getPeriod().getId(), authentication))
                .orElse(false);
    }

    public boolean canManageDefense(Long defenseId, Authentication authentication) {
        return defenseSessionRepository
                .findById(defenseId)
                .map(
                        defense ->
                                projectAuthorization.canManageAssignment(
                                        defense.getAssignment().getId(), authentication))
                .orElse(false);
    }

    public boolean canManageResult(Long resultId, Authentication authentication) {
        return resultVersionRepository
                .findById(resultId)
                .map(
                        result ->
                                projectAuthorization.canManageAssignment(
                                        result.getAssignment().getId(), authentication))
                .orElse(false);
    }

    public boolean canManageScoreSheet(Long sheetId, Authentication authentication) {
        return scoreSheetRepository
                .findById(sheetId)
                .map(
                        sheet ->
                                canManageEvaluationAssignment(
                                        sheet.getEvaluation().getId(), authentication))
                .orElse(false);
    }

    public boolean canManageGradeChange(Long requestId, Authentication authentication) {
        return gradeChangeRequestRepository
                .findById(requestId)
                .map(request -> canManageScoreSheet(request.getSheet().getId(), authentication))
                .orElse(false);
    }
}
