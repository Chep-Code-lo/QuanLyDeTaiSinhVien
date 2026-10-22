package com.example.qldetai.feature.progress.service;

import com.example.qldetai.feature.communication.repository.AnnouncementRepository;
import com.example.qldetai.feature.communication.service.AnnouncementAuthorization;
import com.example.qldetai.feature.groupregistration.repository.GroupMemberRepository;
import com.example.qldetai.feature.identity.domain.RoleCode;
import com.example.qldetai.feature.identity.repository.StudentRepository;
import com.example.qldetai.feature.progress.repository.ReportSubmissionRepository;
import com.example.qldetai.feature.progress.repository.StoredFileRepository;
import com.example.qldetai.feature.project.domain.ProjectAssignment;
import com.example.qldetai.feature.project.service.ProjectAuthorization;
import com.example.qldetai.feature.topic.repository.TopicSupervisorRepository;
import com.example.qldetai.security.UserPrincipal;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component("storedFileAuthorization")
@Transactional(readOnly = true)
public class StoredFileAuthorization {

    private final StoredFileRepository storedFileRepository;
    private final ReportSubmissionRepository reportSubmissionRepository;
    private final AnnouncementRepository announcementRepository;
    private final AnnouncementAuthorization announcementAuthorization;
    private final StudentRepository studentRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final TopicSupervisorRepository topicSupervisorRepository;
    private final ProjectAuthorization projectAuthorization;

    public StoredFileAuthorization(
            StoredFileRepository storedFileRepository,
            ReportSubmissionRepository reportSubmissionRepository,
            AnnouncementRepository announcementRepository,
            AnnouncementAuthorization announcementAuthorization,
            StudentRepository studentRepository,
            GroupMemberRepository groupMemberRepository,
            TopicSupervisorRepository topicSupervisorRepository,
            ProjectAuthorization projectAuthorization) {
        this.storedFileRepository = storedFileRepository;
        this.reportSubmissionRepository = reportSubmissionRepository;
        this.announcementRepository = announcementRepository;
        this.announcementAuthorization = announcementAuthorization;
        this.studentRepository = studentRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.topicSupervisorRepository = topicSupervisorRepository;
        this.projectAuthorization = projectAuthorization;
    }

    public boolean canView(Long fileId, Authentication authentication) {
        UserPrincipal principal = principal(authentication);
        if (principal == null) {
            return false;
        }
        return storedFileRepository
                .findById(fileId)
                .map(
                        file -> {
                            if (file.getUploadedBy().getId().equals(principal.id())
                                    || principal.roles().contains(RoleCode.ADMIN)) {
                                return true;
                            }
                            if (announcementRepository.findByAttachmentId(fileId).stream()
                                    .anyMatch(
                                            announcement ->
                                                    announcementAuthorization.canView(
                                                            announcement.getId(),
                                                            authentication))) {
                                return true;
                            }
                            return reportSubmissionRepository.findByFileId(fileId).stream()
                                    .anyMatch(
                                            submission ->
                                                    canViewAssignment(
                                                            submission
                                                                    .getMilestone()
                                                                    .getAssignment(),
                                                            principal,
                                                            authentication));
                        })
                .orElse(false);
    }

    private boolean canViewAssignment(
            ProjectAssignment assignment, UserPrincipal principal, Authentication authentication) {
        if (projectAuthorization.canManageAssignment(assignment.getId(), authentication)
                || topicSupervisorRepository.existsByTopicIdAndLecturerUserId(
                        assignment.getTopic().getId(), principal.id())) {
            return true;
        }
        return studentRepository
                .findByUserId(principal.id())
                .map(
                        student ->
                                groupMemberRepository.existsByGroupIdAndStudentIdAndLeftAtIsNull(
                                        assignment.getGroup().getId(), student.getId()))
                .orElse(false);
    }

    private UserPrincipal principal(Authentication authentication) {
        return authentication != null
                        && authentication.getPrincipal() instanceof UserPrincipal userPrincipal
                ? userPrincipal
                : null;
    }
}
