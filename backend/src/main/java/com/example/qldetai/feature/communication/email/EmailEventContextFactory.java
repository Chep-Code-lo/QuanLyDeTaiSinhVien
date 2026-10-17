package com.example.qldetai.feature.communication.email;

import com.example.qldetai.exception.BusinessRuleException;
import com.example.qldetai.exception.ResourceNotFoundException;
import com.example.qldetai.feature.academic.domain.RegistrationPeriod;
import com.example.qldetai.feature.academic.repository.RegistrationPeriodRepository;
import com.example.qldetai.feature.groupregistration.repository.GroupMemberRepository;
import com.example.qldetai.feature.groupregistration.domain.RegistrationStatus;
import com.example.qldetai.feature.groupregistration.domain.TopicRegistration;
import com.example.qldetai.feature.groupregistration.repository.TopicRegistrationRepository;
import com.example.qldetai.feature.identity.domain.User;
import com.example.qldetai.feature.project.domain.Milestone;
import com.example.qldetai.feature.project.repository.MilestoneRepository;
import com.example.qldetai.feature.project.domain.ProjectAssignment;
import com.example.qldetai.feature.project.repository.ProjectAssignmentRepository;
import com.example.qldetai.feature.topic.domain.Topic;
import com.example.qldetai.feature.topic.repository.TopicRepository;
import com.example.qldetai.feature.topic.domain.TopicReview;
import com.example.qldetai.feature.topic.domain.TopicReviewDecision;
import com.example.qldetai.feature.topic.repository.TopicReviewRepository;
import com.example.qldetai.feature.topic.repository.TopicSupervisorRepository;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Turns existing business entities into an email-safe, finite event snapshot. */
@Component
public class EmailEventContextFactory {

    private static final DateTimeFormatter DEADLINE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm z");

    private final GroupMemberRepository groupMemberRepository;
    private final TopicSupervisorRepository topicSupervisorRepository;
    private final ProjectAssignmentRepository projectAssignmentRepository;
    private final TopicRegistrationRepository topicRegistrationRepository;
    private final TopicRepository topicRepository;
    private final TopicReviewRepository topicReviewRepository;
    private final RegistrationPeriodRepository registrationPeriodRepository;
    private final MilestoneRepository milestoneRepository;
    private final EmailNotificationProperties properties;

    public EmailEventContextFactory(
            GroupMemberRepository groupMemberRepository,
            TopicSupervisorRepository topicSupervisorRepository,
            ProjectAssignmentRepository projectAssignmentRepository,
            TopicRegistrationRepository topicRegistrationRepository,
            TopicRepository topicRepository,
            TopicReviewRepository topicReviewRepository,
            RegistrationPeriodRepository registrationPeriodRepository,
            MilestoneRepository milestoneRepository,
            EmailNotificationProperties properties) {
        this.groupMemberRepository = groupMemberRepository;
        this.topicSupervisorRepository = topicSupervisorRepository;
        this.projectAssignmentRepository = projectAssignmentRepository;
        this.topicRegistrationRepository = topicRegistrationRepository;
        this.topicRepository = topicRepository;
        this.topicReviewRepository = topicReviewRepository;
        this.registrationPeriodRepository = registrationPeriodRepository;
        this.milestoneRepository = milestoneRepository;
        this.properties = properties;
    }

    public EmailEventContext taskAssigned(ProjectAssignment assignment, User actor) {
        Topic topic = assignment.getTopic();
        return context(
                EmailNotificationEventType.TASK_ASSIGNED,
                "task-assigned:" + assignment.getId(),
                assignment.getPeriod(),
                assignment.getGroup().getId(),
                topic,
                assignment.getId(),
                null,
                actor == null ? null : actor.getId(),
                activeGroupUserIds(assignment.getGroup().getId()),
                supervisors(topic.getId()),
                baseVariables(assignment.getPeriod(), topic, actor, topic.getTitle(), null, null));
    }

    public EmailEventContext topicRegistrationDecision(TopicRegistration registration, User actor) {
        if (registration.getStatus() != RegistrationStatus.APPROVED && registration.getStatus() != RegistrationStatus.REJECTED) {
            throw new BusinessRuleException("Only approved or rejected registrations can produce email events.");
        }
        EmailNotificationEventType eventType = registration.getStatus() == RegistrationStatus.APPROVED
                ? EmailNotificationEventType.TOPIC_APPROVED
                : EmailNotificationEventType.TOPIC_REJECTED;
        Topic topic = registration.getTopic();
        return context(
                eventType,
                "topic-registration:" + registration.getId() + ":" + registration.getStatus(),
                registration.getPeriod(),
                registration.getGroup().getId(),
                topic,
                null,
                null,
                actor == null ? null : actor.getId(),
                activeGroupUserIds(registration.getGroup().getId()),
                supervisors(topic.getId()),
                baseVariables(
                        registration.getPeriod(), topic, actor, topic.getTitle(), null, registration.getDecisionReason()));
    }

    public EmailEventContext topicDecision(Topic topic, TopicReview review) {
        if (review.getDecision() != TopicReviewDecision.APPROVED && review.getDecision() != TopicReviewDecision.REJECTED) {
            throw new BusinessRuleException("Only approved or rejected topics can produce email events.");
        }
        EmailNotificationEventType eventType = review.getDecision() == TopicReviewDecision.APPROVED
                ? EmailNotificationEventType.TOPIC_APPROVED
                : EmailNotificationEventType.TOPIC_REJECTED;
        Long groupId = topic.getReservedGroup() == null ? null : topic.getReservedGroup().getId();
        return context(
                eventType,
                "topic-review:" + review.getId(),
                topic.getPeriod(),
                groupId,
                topic,
                null,
                null,
                review.getReviewedBy().getId(),
                List.of(topic.getProposedBy().getId()),
                supervisors(topic.getId()),
                baseVariables(topic.getPeriod(), topic, review.getReviewedBy(), topic.getTitle(), null, review.getComment()));
    }

    public EmailEventContext registrationOpened(RegistrationPeriod period, User actor) {
        return context(
                EmailNotificationEventType.REGISTRATION_OPENED,
                "registration-opened:" + period.getId(),
                period,
                null,
                null,
                null,
                null,
                actor == null ? null : actor.getId(),
                List.of(),
                List.of(),
                baseVariables(period, null, actor, null, null, null));
    }

    public EmailEventContext deadlineReminder(Milestone milestone) {
        return deadlineReminder(milestone, 0);
    }

    public EmailEventContext deadlineReminder(Milestone milestone, int reminderHours) {
        ProjectAssignment assignment = milestone.getAssignment();
        Topic topic = assignment.getTopic();
        return context(
                EmailNotificationEventType.DEADLINE_REMINDER,
                "deadline-reminder:" + milestone.getId() + ":" + reminderHours,
                assignment.getPeriod(),
                assignment.getGroup().getId(),
                topic,
                assignment.getId(),
                milestone.getId(),
                milestone.getCreatedBy().getId(),
                activeGroupUserIds(assignment.getGroup().getId()),
                supervisors(topic.getId()),
                baseVariables(
                        assignment.getPeriod(), topic, milestone.getCreatedBy(), milestone.getTitle(), milestone.getDueAt(), null));
    }

    public EmailEventContext fromReference(
            EmailNotificationEventType eventType,
            EmailNotificationReferenceType referenceType,
            Long referenceId) {
        if (referenceType == null || referenceId == null) {
            throw new BusinessRuleException("A reference type and reference id are required for business-data preview.");
        }
        return switch (referenceType) {
            case PROJECT_ASSIGNMENT -> {
                requireEvent(eventType, EmailNotificationEventType.TASK_ASSIGNED);
                ProjectAssignment assignment = projectAssignmentRepository.findById(referenceId)
                        .orElseThrow(() -> new ResourceNotFoundException("Project assignment was not found."));
                yield taskAssigned(assignment, assignment.getAssignedBy());
            }
            case TOPIC_REGISTRATION -> {
                requireEvent(eventType, EmailNotificationEventType.TOPIC_APPROVED, EmailNotificationEventType.TOPIC_REJECTED);
                TopicRegistration registration = topicRegistrationRepository.findById(referenceId)
                        .orElseThrow(() -> new ResourceNotFoundException("Topic registration was not found."));
                if ((eventType == EmailNotificationEventType.TOPIC_APPROVED
                        && registration.getStatus() != RegistrationStatus.APPROVED)
                        || (eventType == EmailNotificationEventType.TOPIC_REJECTED
                        && registration.getStatus() != RegistrationStatus.REJECTED)) {
                    throw new BusinessRuleException("The registration status does not match the selected email event.");
                }
                yield topicRegistrationDecision(registration, registration.getReviewedBy());
            }
            case TOPIC -> {
                requireEvent(eventType, EmailNotificationEventType.TOPIC_APPROVED, EmailNotificationEventType.TOPIC_REJECTED);
                Topic topic = topicRepository.findById(referenceId)
                        .orElseThrow(() -> new ResourceNotFoundException("Topic was not found."));
                TopicReview review = topicReviewRepository.findByTopicIdOrderByReviewedAtDesc(topic.getId()).stream()
                        .findFirst()
                        .orElseThrow(() -> new ResourceNotFoundException("Topic review was not found."));
                if ((eventType == EmailNotificationEventType.TOPIC_APPROVED
                        && review.getDecision() != TopicReviewDecision.APPROVED)
                        || (eventType == EmailNotificationEventType.TOPIC_REJECTED
                        && review.getDecision() != TopicReviewDecision.REJECTED)) {
                    throw new BusinessRuleException("The topic review does not match the selected email event.");
                }
                yield topicDecision(topic, review);
            }
            case REGISTRATION_PERIOD -> {
                requireEvent(eventType, EmailNotificationEventType.REGISTRATION_OPENED);
                RegistrationPeriod period = registrationPeriodRepository.findById(referenceId)
                        .orElseThrow(() -> new ResourceNotFoundException("Registration period was not found."));
                yield registrationOpened(period, period.getCreatedBy());
            }
            case MILESTONE -> {
                requireEvent(eventType, EmailNotificationEventType.DEADLINE_REMINDER);
                Milestone milestone = milestoneRepository.findById(referenceId)
                        .orElseThrow(() -> new ResourceNotFoundException("Milestone was not found."));
                yield deadlineReminder(milestone);
            }
        };
    }

    public EmailEventContext sample(EmailNotificationEventType eventType) {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("studentName", "Sinh viên mẫu");
        variables.put("lecturerName", "Giảng viên mẫu");
        variables.put("topicName", "Đề tài mẫu");
        variables.put("taskName", "Nhiệm vụ mẫu");
        variables.put("deadline", "31/12/2026 17:00 ICT");
        variables.put("periodName", "Đợt đăng ký mẫu");
        variables.put("facultyName", "Khoa mẫu");
        variables.put("websiteUrl", normalizedBaseUrl());
        variables.put("decisionReason", "Nội dung phản hồi mẫu");
        return new EmailEventContext(eventType, "preview:" + eventType, null, null, null, null,
                null, null, null, List.of(), List.of(), variables);
    }

    private EmailEventContext context(
            EmailNotificationEventType eventType,
            String eventKey,
            RegistrationPeriod period,
            Long groupId,
            Topic topic,
            Long assignmentId,
            Long milestoneId,
            Long actorUserId,
            List<Long> directUsers,
            List<Long> responsibleLecturers,
            Map<String, String> variables) {
        return new EmailEventContext(
                eventType,
                eventKey,
                period.getFaculty().getId(),
                period.getId(),
                groupId,
                topic == null ? null : topic.getId(),
                assignmentId,
                milestoneId,
                actorUserId,
                directUsers,
                responsibleLecturers,
                variables);
    }

    private Map<String, String> baseVariables(
            RegistrationPeriod period,
            Topic topic,
            User actor,
            String taskName,
            java.time.LocalDateTime deadline,
            String decisionReason) {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("lecturerName", actor == null ? null : actor.getFullName());
        variables.put("topicName", topic == null ? null : topic.getTitle());
        variables.put("taskName", taskName);
        variables.put("deadline", deadline == null ? null : deadline.atZone(ZoneOffset.UTC)
                .withZoneSameInstant(properties.displayZoneId()).format(DEADLINE_FORMAT));
        variables.put("periodName", period.getName());
        variables.put("facultyName", period.getFaculty().getName());
        variables.put("websiteUrl", normalizedBaseUrl());
        variables.put("decisionReason", decisionReason);
        return variables;
    }

    private List<Long> activeGroupUserIds(Long groupId) {
        return groupMemberRepository.findByGroupIdAndLeftAtIsNullOrderBySlotNoAsc(groupId).stream()
                .map(member -> member.getStudent().getUser().getId())
                .toList();
    }

    private List<Long> supervisors(Long topicId) {
        return topicSupervisorRepository.findByTopicIdOrderBySlotNoAsc(topicId).stream()
                .map(supervisor -> supervisor.getLecturer().getUser().getId())
                .toList();
    }

    private void requireEvent(EmailNotificationEventType actual, EmailNotificationEventType... allowed) {
        for (EmailNotificationEventType candidate : allowed) {
            if (actual == candidate) {
                return;
            }
        }
        throw new BusinessRuleException("The reference type is not valid for the selected email event.");
    }

    private String normalizedBaseUrl() {
        String url = properties.getBaseUrl() == null ? "" : properties.getBaseUrl().trim();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
