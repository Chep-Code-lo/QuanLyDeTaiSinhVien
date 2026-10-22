package com.example.qldetai.feature.communication.service;

import com.example.qldetai.feature.academic.service.AcademicAuthorization;
import com.example.qldetai.feature.communication.domain.AnnouncementStatus;
import com.example.qldetai.feature.communication.repository.AnnouncementRepository;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Keeps announcement visibility scoped to the announcement's faculty. */
@Component("announcementAuthorization")
@Transactional(readOnly = true)
public class AnnouncementAuthorization {

    private final AnnouncementRepository announcementRepository;
    private final AcademicAuthorization academicAuthorization;

    public AnnouncementAuthorization(
            AnnouncementRepository announcementRepository,
            AcademicAuthorization academicAuthorization) {
        this.announcementRepository = announcementRepository;
        this.academicAuthorization = academicAuthorization;
    }

    public boolean canManage(Long announcementId, Authentication authentication) {
        return announcementRepository
                .findById(announcementId)
                .map(
                        announcement ->
                                academicAuthorization.canManageFaculty(
                                        announcement.getFaculty().getId(), authentication))
                .orElse(false);
    }

    public boolean canView(Long announcementId, Authentication authentication) {
        return announcementRepository
                .findById(announcementId)
                .map(
                        announcement ->
                                announcement.getStatus() == AnnouncementStatus.PUBLISHED
                                        || academicAuthorization.canManageFaculty(
                                                announcement.getFaculty().getId(), authentication))
                .orElse(false);
    }
}
