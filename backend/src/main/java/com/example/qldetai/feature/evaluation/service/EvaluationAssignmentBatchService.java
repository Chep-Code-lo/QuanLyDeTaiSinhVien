package com.example.qldetai.feature.evaluation.service;

import com.example.qldetai.exception.BusinessRuleException;
import com.example.qldetai.exception.ResourceNotFoundException;
import com.example.qldetai.feature.evaluation.dto.EvaluationDtos.*;
import com.example.qldetai.feature.evaluation.repository.EvaluationAssignmentRepository;
import com.example.qldetai.feature.evaluation.repository.RubricRepository;
import com.example.qldetai.feature.project.repository.ProjectAssignmentRepository;
import com.example.qldetai.feature.project.service.ProjectAuthorization;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;

/** All assignments succeed together; existing identical active assignments are idempotent. */
@Service
@RequiredArgsConstructor
public class EvaluationAssignmentBatchService {
    private final EvaluationService evaluation;
    private final EvaluationAssignmentRepository evaluations;
    private final RubricRepository rubrics;
    private final ProjectAssignmentRepository projects;
    private final ProjectAuthorization authorization;
    private final EvaluationActorAccess access;

    @Transactional
    public BatchEvaluationResponse assign(BatchEvaluationRequest batch, Long actorId) {
        access.manager(actorId);
        if (batch.assignments() == null
                || batch.assignments().isEmpty()
                || batch.assignments().size() > 100) {
            throw new BusinessRuleException(
                    "Mỗi lần chỉ phân công từ 1 đến 100 giảng viên/đề tài.");
        }
        int assigned = 0;
        int skipped = 0;
        for (CreateEvaluationRequest request :
                batch.assignments().stream()
                        .sorted(
                                Comparator.comparing(CreateEvaluationRequest::assignmentId)
                                        .thenComparing(CreateEvaluationRequest::lecturerId))
                        .toList()) {
            if (!authorization.canManageAssignment(request.assignmentId(), actorId)) {
                throw new AccessDeniedException("Bạn không có quyền phân công đề tài này.");
            }
            projects.findByIdForUpdate(request.assignmentId())
                    .orElseThrow(
                            () ->
                                    new ResourceNotFoundException(
                                            "Project assignment was not found."));
            var rubric =
                    rubrics.findById(request.rubricId())
                            .orElseThrow(
                                    () -> new ResourceNotFoundException("Rubric was not found."));
            var existing =
                    evaluations
                            .findActiveForUpdate(
                                    request.assignmentId(), rubric.getComponent().getId())
                            .stream()
                            .filter(item -> item.getLecturer().getId().equals(request.lecturerId()))
                            .findFirst();
            if (existing.isPresent()
                    && existing.get().getRubric().getId().equals(request.rubricId())
                    && java.util.Objects.equals(
                            existing.get().getDefense() == null
                                    ? null
                                    : existing.get().getDefense().getId(),
                            request.defenseId())) {
                skipped++;
            } else {
                evaluation.assign(request, actorId);
                assigned++;
            }
        }
        return new BatchEvaluationResponse(assigned, skipped);
    }
}
