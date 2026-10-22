package com.example.qldetai.feature.communication.service;

import com.example.qldetai.common.api.PageResponse;
import com.example.qldetai.common.audit.BusinessAudit;
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
import com.example.qldetai.feature.communication.repository.CommunicationSearch;
import com.example.qldetai.feature.identity.domain.User;
import com.example.qldetai.feature.identity.repository.UserRepository;
import com.example.qldetai.feature.progress.domain.StoredFile;
import com.example.qldetai.feature.progress.repository.StoredFileRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

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
        return listPublished(facultyId, "", page, size);
    }

    @Transactional(readOnly = true)
    public PageResponse<AnnouncementResponse> listPublished(
            Long facultyId, String search, int page, int size) {
        Specification<Announcement> scope =
                (root, query, cb) -> cb.equal(root.get("status"), AnnouncementStatus.PUBLISHED);
        if (facultyId != null)
            scope =
                    scope.and(
                            (root, query, cb) ->
                                    cb.equal(root.get("faculty").get("id"), facultyId));
        return PageResponse.from(
                announcementRepository
                        .findAll(
                                scope.and(
                                        CommunicationSearch.text(
                                                search, "title", "content", "createdBy.fullName")),
                                PageRequest.of(
                                        page,
                                        size,
                                        Sort.by("publishedAt")
                                                .descending()
                                                .and(Sort.by("createdAt").descending())
                                                .and(Sort.by("id").descending())))
                        .map(this::toResponse));
    }

    /** Authorization for the requested faculty remains at the controller boundary. */
    @Transactional(readOnly = true)
    public PageResponse<AnnouncementResponse> listForManagement(
            Long facultyId, AnnouncementStatus status, int page, int size) {
        return listForManagement(facultyId, status, "", page, size);
    }

    @Transactional(readOnly = true)
    public PageResponse<AnnouncementResponse> listForManagement(
            Long facultyId, AnnouncementStatus status, String search, int page, int size) {
        Specification<Announcement> scope =
                (root, query, cb) -> cb.equal(root.get("faculty").get("id"), facultyId);
        if (status != null)
            scope = scope.and((root, query, cb) -> cb.equal(root.get("status"), status));
        return PageResponse.from(
                announcementRepository
                        .findAll(
                                scope.and(
                                        CommunicationSearch.text(
                                                search, "title", "content", "createdBy.fullName")),
                                PageRequest.of(
                                        page,
                                        size,
                                        Sort.by("createdAt")
                                                .descending()
                                                .and(Sort.by("id").descending())))
                        .map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public AnnouncementResponse get(Long id) {
        return toResponse(requireAnnouncement(id));
    }

    @Transactional
    @BusinessAudit(action = "ANNOUNCEMENT_CREATE", entityType = "ANNOUNCEMENT")
    public AnnouncementResponse create(CreateAnnouncementRequest request, Long actorId) {
        Faculty faculty =
                facultyRepository
                        .findById(request.facultyId())
                        .filter(Faculty::isActive)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Không tìm thấy khoa đang hoạt động."));
        User creator =
                userRepository
                        .findById(actorId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Không tìm thấy tài khoản tạo thông báo."));

        Announcement announcement = new Announcement();
        announcement.setFaculty(faculty);
        announcement.setCreatedBy(creator);
        announcement.setStatus(AnnouncementStatus.DRAFT);
        apply(announcement, request.title(), request.content(), request.attachmentFileId());
        return toResponse(announcementRepository.save(announcement));
    }

    @Transactional
    @BusinessAudit(
            action = "ANNOUNCEMENT_UPDATE",
            entityType = "ANNOUNCEMENT",
            entityIdArgument = 0)
    public AnnouncementResponse update(Long id, UpdateAnnouncementRequest request) {
        Announcement announcement = requireAnnouncement(id);
        if (announcement.getStatus() != AnnouncementStatus.DRAFT) {
            throw new BusinessRuleException("Chỉ có thể sửa thông báo ở trạng thái nháp.");
        }
        apply(announcement, request.title(), request.content(), request.attachmentFileId());
        return toResponse(announcement);
    }

    @Transactional
    @BusinessAudit(
            action = "ANNOUNCEMENT_PUBLISH",
            entityType = "ANNOUNCEMENT",
            entityIdArgument = 0)
    public AnnouncementResponse publish(Long id) {
        Announcement announcement = requireAnnouncement(id);
        if (announcement.getStatus() != AnnouncementStatus.DRAFT) {
            throw new BusinessRuleException("Chỉ có thể công bố thông báo ở trạng thái nháp.");
        }
        announcement.setStatus(AnnouncementStatus.PUBLISHED);
        announcement.setPublishedAt(LocalDateTime.now(clock));
        return toResponse(announcement);
    }

    @Transactional
    @BusinessAudit(
            action = "ANNOUNCEMENT_ARCHIVE",
            entityType = "ANNOUNCEMENT",
            entityIdArgument = 0)
    public AnnouncementResponse archive(Long id) {
        Announcement announcement = requireAnnouncement(id);
        if (announcement.getStatus() != AnnouncementStatus.PUBLISHED) {
            throw new BusinessRuleException("Chỉ có thể lưu trữ thông báo đã công bố.");
        }
        announcement.setStatus(AnnouncementStatus.ARCHIVED);
        return toResponse(announcement);
    }

    @Transactional
    @BusinessAudit(
            action = "ANNOUNCEMENT_DELETE",
            entityType = "ANNOUNCEMENT",
            entityIdArgument = 0)
    public void delete(Long id) {
        Announcement announcement = requireAnnouncement(id);
        if (announcement.getStatus() == AnnouncementStatus.PUBLISHED) {
            throw new BusinessRuleException("Hãy lưu trữ thông báo đã công bố thay vì xóa.");
        }
        announcementRepository.delete(announcement);
    }

    private void apply(
            Announcement announcement, String title, String content, Long attachmentFileId) {
        announcement.setTitle(title.trim());
        announcement.setContent(content.trim());
        announcement.setAttachment(
                attachmentFileId == null ? null : requireAttachment(attachmentFileId));
    }

    private StoredFile requireAttachment(Long id) {
        return storedFileRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tệp đính kèm."));
    }

    private Announcement requireAnnouncement(Long id) {
        return announcementRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông báo."));
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
