package com.example.qldetai.feature.communication;

import com.example.qldetai.common.api.PageResponse;
import com.example.qldetai.exception.ResourceNotFoundException;
import com.example.qldetai.feature.communication.CommunicationDtos.NotificationResponse;
import com.example.qldetai.feature.communication.CommunicationDtos.UnreadCountResponse;
import com.example.qldetai.feature.identity.User;
import com.example.qldetai.feature.identity.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public NotificationService(
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            Clock clock) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(Long userId, int page, int size) {
        return PageResponse.from(notificationRepository.findByUserId(
                        userId, PageRequest.of(page, size, Sort.by("createdAt").descending()))
                .map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount(Long userId) {
        return new UnreadCountResponse(notificationRepository.countByUserIdAndReadAtIsNull(userId));
    }

    @Transactional
    public NotificationResponse markRead(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification was not found."));
        if (notification.getReadAt() == null) {
            notification.setReadAt(LocalDateTime.now(clock));
        }
        return toResponse(notification);
    }

    @Transactional
    public void publish(
            Long userId,
            String eventKey,
            String title,
            String content,
            String targetPath) {
        if (notificationRepository.existsByUserIdAndEventKey(userId, eventKey)) {
            return;
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification recipient was not found."));
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setEventKey(eventKey);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setTargetPath(targetPath);
        notification.setCreatedAt(LocalDateTime.now(clock));
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
