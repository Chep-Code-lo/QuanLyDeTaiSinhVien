package com.example.qldetai.feature.evaluation.service;

import com.example.qldetai.common.api.PageResponse;
import com.example.qldetai.exception.BusinessRuleException;
import com.example.qldetai.exception.ResourceNotFoundException;
import com.example.qldetai.feature.evaluation.domain.GradeChangeRequest;
import com.example.qldetai.feature.evaluation.domain.GradeChangeStatus;
import com.example.qldetai.feature.evaluation.domain.ScoreSheet;
import com.example.qldetai.feature.evaluation.domain.ScoreSheetStatus;
import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.CreateGradeChangeRequest;
import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.GradeChangeRequestResponse;
import com.example.qldetai.feature.evaluation.dto.EvaluationAdminDtos.ReviewGradeChangeRequest;
import com.example.qldetai.feature.evaluation.repository.GradeChangeRequestRepository;
import com.example.qldetai.feature.evaluation.repository.ResultInputRepository;
import com.example.qldetai.feature.evaluation.repository.ScoreSheetRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GradeChangeService {
    private final com.example.qldetai.feature.project.service.ProjectAuthorization projectAuthorization;

    private final ScoreSheetRepository sheets;
    private final ResultInputRepository resultInputs;
    private final GradeChangeRequestRepository changes;
    private final Clock clock;
    private final EvaluationActorAccess actorAccess;
    private final GradeChangeAccess access;

    @Transactional(readOnly = true)
    public PageResponse<GradeChangeRequestResponse> listChanges(
            GradeChangeStatus status, Long sheetId, int page, int size, Long actorId) {
        actorAccess.manager(actorId);
        Specification<GradeChangeRequest> specification = access.readableBy(actorId);
        if (status != null) {
            specification =
                    specification.and(
                            (root, query, builder) -> builder.equal(root.get("status"), status));
        }
        if (sheetId != null) {
            specification =
                    specification.and(
                            (root, query, builder) ->
                                    builder.equal(root.join("sheet").get("id"), sheetId));
        }
        return PageResponse.from(
                changes.findAll(
                                specification,
                                PageRequest.of(
                                        page,
                                        size,
                                        Sort.by("createdAt")
                                                .descending()
                                                .and(Sort.by("id").descending())))
                        .map(this::gradeChange));
    }

    @Transactional(readOnly = true)
    public GradeChangeRequestResponse getChange(Long id, Long actorId) {
        actorAccess.manager(actorId);
        GradeChangeRequest change =
                changes.findById(id)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Grade change request was not found."));
        if (!changes.exists(
                access.readableBy(actorId)
                        .and((root, query, cb) -> cb.equal(root.get("id"), id)))) {
            throw new AccessDeniedException("You cannot view this grade change request.");
        }
        return gradeChange(change);
    }

    @Transactional
    @com.example.qldetai.common.audit.BusinessAudit(action = "GRADE_CHANGE_REQUEST", entityType = "SCORE_SHEET", entityIdArgument = 0)
    public Long requestChange(Long sheetId, CreateGradeChangeRequest request, Long actorId) {
        ScoreSheet sheet =
                sheets.findById(sheetId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Score sheet was not found."));
        if (!sheet.getEvaluation().getLecturer().getUser().getId().equals(actorId)
                || sheet.getEvaluation().getStatus() != com.example.qldetai.feature.evaluation.domain.EvaluationStatus.ACTIVE) {
            throw new AccessDeniedException("Only the evaluator can request a score change.");
        }
        ensureScoreSheetCanBeChanged(sheet);
        if ((sheet.getStatus() != ScoreSheetStatus.SUBMITTED
                        && sheet.getStatus() != ScoreSheetStatus.LOCKED)
                || changes.existsBySheetIdAndStatusIn(
                        sheetId, List.of(GradeChangeStatus.PENDING, GradeChangeStatus.APPROVED))) {
            throw new BusinessRuleException(
                    "This score sheet cannot receive another change request.");
        }

        GradeChangeRequest changeRequest = new GradeChangeRequest();
        changeRequest.setSheet(sheet);
        changeRequest.setRequestedBy(actorAccess.user(actorId));
        changeRequest.setReason(request.reason().trim());
        return changes.save(changeRequest).getId();
    }

    @Transactional
    @com.example.qldetai.common.audit.BusinessAudit(action = "GRADE_CHANGE_REVIEW", entityType = "GRADE_CHANGE_REQUEST", entityIdArgument = 0)
    public void reviewChange(Long id, ReviewGradeChangeRequest request, Long actorId) {
        actorAccess.manager(actorId);
        GradeChangeRequest changeRequest =
                changes.findByIdForUpdate(id)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Grade change request was not found."));
        if (!projectAuthorization.canManageAssignment(changeRequest.getSheet().getEvaluation().getAssignment().getId(), actorId)
                || !changes.exists(
                access.manageableBy(actorId)
                        .and((root, query, cb) -> cb.equal(root.get("id"), id)))) {
            throw new AccessDeniedException("You cannot review this grade change request.");
        }
        if (changeRequest.getStatus() != GradeChangeStatus.PENDING) {
            throw new BusinessRuleException("Only a pending request may be reviewed.");
        }

        if (Boolean.TRUE.equals(request.approved())) {
            // A final result holds immutable evidence. Re-check here because a
            // request can be submitted before finalization but reviewed later.
            ensureScoreSheetCanBeChanged(changeRequest.getSheet());
        }
        changeRequest.setReviewedBy(actorAccess.user(actorId));
        changeRequest.setReviewedAt(LocalDateTime.now(clock));
        changeRequest.setReviewComment(request.comment());
        if (Boolean.TRUE.equals(request.approved())) {
            changeRequest.setStatus(GradeChangeStatus.APPROVED);
            // MySQL's score-sheet trigger checks the approved request while reopening the
            // sheet, so flush this state transition before changing LOCKED -> DRAFT.
            changes.saveAndFlush(changeRequest);
            changeRequest.getSheet().setStatus(ScoreSheetStatus.DRAFT);
            changeRequest.getSheet().setSubmittedAt(null);
            sheets.saveAndFlush(changeRequest.getSheet());
        } else {
            changeRequest.setStatus(GradeChangeStatus.REJECTED);
        }
    }

    private void ensureScoreSheetCanBeChanged(ScoreSheet sheet) {
        if (sheet.getId() != null && resultInputs.existsBySheetId(sheet.getId())) {
            throw new BusinessRuleException(
                    "Cannot reopen or edit a score sheet captured by a result version; finalized"
                            + " and published evidence is immutable.");
        }
    }

    private GradeChangeRequestResponse gradeChange(GradeChangeRequest changeRequest) {
        ScoreSheet sheet = changeRequest.getSheet();
        return new GradeChangeRequestResponse(
                changeRequest.getId(),
                sheet.getId(),
                sheet.getEvaluation().getId(),
                sheet.getEvaluation().getAssignment().getId(),
                changeRequest.getRequestedBy().getId(),
                changeRequest.getRequestedBy().getFullName(),
                changeRequest.getReason(),
                changeRequest.getStatus(),
                changeRequest.getReviewedBy() == null
                        ? null
                        : changeRequest.getReviewedBy().getId(),
                changeRequest.getReviewedBy() == null
                        ? null
                        : changeRequest.getReviewedBy().getFullName(),
                changeRequest.getReviewComment(),
                changeRequest.getCreatedAt(),
                changeRequest.getReviewedAt(),
                changeRequest.getAppliedAt());
    }
}
