package com.example.qldetai.feature.communication.service;

import com.example.qldetai.common.api.PageResponse;
import com.example.qldetai.exception.ResourceNotFoundException;
import com.example.qldetai.feature.communication.domain.Notification;
import com.example.qldetai.feature.communication.dto.CommunicationDtos.NotificationResponse;
import com.example.qldetai.feature.communication.dto.CommunicationDtos.UnreadCountResponse;
import com.example.qldetai.feature.communication.repository.CommunicationSearch;
import com.example.qldetai.feature.communication.repository.NotificationRepository;
import com.example.qldetai.feature.identity.domain.User;
import com.example.qldetai.feature.identity.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(Long userId, int page, int size) {
        return list(userId, "", page, size);
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(Long userId, String search, int page, int size) {
        Specification<Notification> scope =
                (root, query, cb) -> cb.equal(root.get("user").get("id"), userId);
        return PageResponse.from(
                notificationRepository
                        .findAll(
                                scope.and(CommunicationSearch.text(search, "title", "content")),
                                PageRequest.of(
                                        page,
                                        size,
                                        Sort.by("createdAt")
                                                .descending()
                                                .and(Sort.by("id").descending())))
                        .map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public java.util.List<NotificationResponse> forAnnouncements(
            Long userId, java.util.List<Long> ids) {
        return notificationRepository.findForAnnouncements(userId, ids).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount(Long userId) {
        return new UnreadCountResponse(notificationRepository.countByUserIdAndReadAtIsNull(userId));
    }

    @Transactional
    public NotificationResponse markRead(Long notificationId, Long userId) {
        Notification notification =
                notificationRepository
                        .findByIdAndUserId(notificationId, userId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Không tìm thấy thông báo cá nhân."));
        if (notification.getReadAt() == null) {
            notification.setReadAt(LocalDateTime.now(clock));
        }
        return toResponse(notification);
    }

    @Transactional
    public void publish(
            Long userId, String eventKey, String title, String content, String targetPath) {
        if (notificationRepository.existsByUserIdAndEventKey(userId, eventKey)) {
            return;
        }
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Không tìm thấy người nhận thông báo."));
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setEventKey(eventKey);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setTargetPath(targetPath);
        notificationRepository.save(notification);
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getTitle(),
                notification.getContent(),
                notification.getTargetPath(),
                notification.getCreatedAt(),
                notification.getReadAt());
    }
}
