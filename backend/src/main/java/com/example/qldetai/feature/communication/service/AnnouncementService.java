package com.example.qldetai.feature.communication.service;

import com.example.qldetai.aspect.BusinessAudit;
import com.example.qldetai.common.api.PageResponse;
import com.example.qldetai.exception.BusinessRuleException;
import com.example.qldetai.exception.ResourceNotFoundException;
import com.example.qldetai.feature.academic.domain.Faculty;
import com.example.qldetai.feature.academic.repository.FacultyRepository;
import com.example.qldetai.feature.communication.domain.Announcement;
import com.example.qldetai.feature.communication.domain.AnnouncementStatus;
import com.example.qldetai.feature.communication.dto.CommunicationDtos.AnnouncementResponse;
import com.example.qldetai.feature.communication.dto.CommunicationDtos.CreateAnnouncementRequest;
import com.example.qldetai.feature.communication.dto.CommunicationDtos.UpdateAnnouncementRequest;
import com.example.qldetai.feature.communication.repository.AnnouncementRepository;
import com.example.qldetai.feature.identity.domain.User;
import com.example.qldetai.feature.identity.repository.UserRepository;
import com.example.qldetai.feature.progress.domain.StoredFile;
import com.example.qldetai.feature.progress.repository.StoredFileRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;
    private final FacultyRepository facultyRepository;
    private final UserRepository userRepository;
    private final StoredFileRepository storedFileRepository;
    private final Clock clock;


    /** Public feed: unpublished or archived content is intentionally never returned here. */
    @Transactional(readOnly = true)
    public PageResponse<AnnouncementResponse> listPublished(Long facultyId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("publishedAt").descending().and(Sort.by("createdAt").descending()));
        Page<Announcement> announcements = facultyId == null
                ? announcementRepository.findByStatus(AnnouncementStatus.PUBLISHED, pageable)
                : announcementRepository.findByFacultyIdAndStatus(facultyId, AnnouncementStatus.PUBLISHED, pageable);
        return PageResponse.from(announcements.map(this::toResponse));
    }

    /** Management feed for one faculty. Authorization is enforced at the controller boundary. */
    @Transactional(readOnly = true)
    public PageResponse<AnnouncementResponse> listForManagement(
            Long facultyId, AnnouncementStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Announcement> announcements = status == null
                ? announcementRepository.findByFacultyId(facultyId, pageable)
                : announcementRepository.findByFacultyIdAndStatus(facultyId, status, pageable);
        return PageResponse.from(announcements.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public AnnouncementResponse get(Long id) {
        return toResponse(requireAnnouncement(id));
    }

    @Transactional
    @BusinessAudit(action = "ANNOUNCEMENT_CREATE", entityType = "ANNOUNCEMENT")
    public AnnouncementResponse create(CreateAnnouncementRequest request, Long actorId) {
        Faculty faculty = facultyRepository.findById(request.facultyId())
                .filter(Faculty::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Active faculty was not found."));
        User creator = userRepository.findById(actorId)
                .orElseThrow(() -> new ResourceNotFoundException("Creating user was not found."));

        Announcement announcement = new Announcement();
        announcement.setFaculty(faculty);
        announcement.setCreatedBy(creator);
        announcement.setStatus(AnnouncementStatus.DRAFT);
        announcement.setCreatedAt(LocalDateTime.now(clock));
        apply(announcement, request.title(), request.content(), request.attachmentFileId());
        return toResponse(announcementRepository.save(announcement));
    }

    @Transactional
    @BusinessAudit(action = "ANNOUNCEMENT_UPDATE", entityType = "ANNOUNCEMENT", entityIdArgument = 0)
    public AnnouncementResponse update(Long id, UpdateAnnouncementRequest request) {
        Announcement announcement = requireAnnouncement(id);
        if (announcement.getStatus() != AnnouncementStatus.DRAFT) {
            throw new BusinessRuleException("Only a draft announcement can be edited.");
        }
        apply(announcement, request.title(), request.content(), request.attachmentFileId());
        return toResponse(announcement);
    }

    @Transactional
    @BusinessAudit(action = "ANNOUNCEMENT_PUBLISH", entityType = "ANNOUNCEMENT", entityIdArgument = 0)
    public AnnouncementResponse publish(Long id) {
        Announcement announcement = requireAnnouncement(id);
        if (announcement.getStatus() != AnnouncementStatus.DRAFT) {
            throw new BusinessRuleException("Only a draft announcement can be published.");
        }
        announcement.setStatus(AnnouncementStatus.PUBLISHED);
        announcement.setPublishedAt(LocalDateTime.now(clock));
        return toResponse(announcement);
    }

    @Transactional
    @BusinessAudit(action = "ANNOUNCEMENT_ARCHIVE", entityType = "ANNOUNCEMENT", entityIdArgument = 0)
    public AnnouncementResponse archive(Long id) {
        Announcement announcement = requireAnnouncement(id);
        if (announcement.getStatus() != AnnouncementStatus.PUBLISHED) {
            throw new BusinessRuleException("Only a published announcement can be archived.");
        }
        announcement.setStatus(AnnouncementStatus.ARCHIVED);
        return toResponse(announcement);
    }

    @Transactional
    @BusinessAudit(action = "ANNOUNCEMENT_DELETE", entityType = "ANNOUNCEMENT", entityIdArgument = 0)
    public void delete(Long id) {
        Announcement announcement = requireAnnouncement(id);
        if (announcement.getStatus() == AnnouncementStatus.PUBLISHED) {
            throw new BusinessRuleException("Archive a published announcement instead of deleting it.");
        }
        announcementRepository.delete(announcement);
    }

    private void apply(Announcement announcement, String title, String content, Long attachmentFileId) {
        announcement.setTitle(title.trim());
        announcement.setContent(content.trim());
        announcement.setAttachment(attachmentFileId == null ? null : requireAttachment(attachmentFileId));
    }

    private StoredFile requireAttachment(Long id) {
        return storedFileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment file was not found."));
    }

    private Announcement requireAnnouncement(Long id) {
        return announcementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Announcement was not found."));
    }

    private AnnouncementResponse toResponse(Announcement announcement) {
        StoredFile attachment = announcement.getAttachment();
        return new AnnouncementResponse(
                announcement.getId(),
                announcement.getFaculty().getId(),
                announcement.getFaculty().getName(),
                announcement.getTitle(),
                announcement.getContent(),
                attachment == null ? null : attachment.getId(),
                attachment == null ? null : attachment.getOriginalName(),
                attachment == null ? null : "/api/v1/files/" + attachment.getId(),
                announcement.getCreatedBy().getId(),
                announcement.getCreatedBy().getFullName(),
                announcement.getStatus(),
                announcement.getCreatedAt(),
                announcement.getPublishedAt());
    }
}
