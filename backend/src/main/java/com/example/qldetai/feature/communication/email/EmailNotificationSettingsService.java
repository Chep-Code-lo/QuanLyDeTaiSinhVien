package com.example.qldetai.feature.communication.email;

import com.example.qldetai.exception.BusinessRuleException;
import com.example.qldetai.exception.ResourceNotFoundException;
import com.example.qldetai.feature.academic.repository.FacultyRepository;
import com.example.qldetai.feature.groupregistration.domain.StudentGroup;
import com.example.qldetai.feature.groupregistration.repository.GroupMemberRepository;
import com.example.qldetai.feature.groupregistration.repository.StudentGroupRepository;
import com.example.qldetai.feature.groupregistration.repository.TopicRegistrationRepository;
import com.example.qldetai.feature.identity.repository.AcademicAppointmentRepository;
import com.example.qldetai.feature.identity.repository.LecturerRepository;
import com.example.qldetai.feature.identity.domain.RoleCode;
import com.example.qldetai.feature.identity.domain.User;
import com.example.qldetai.feature.identity.repository.UserRepository;
import com.example.qldetai.feature.identity.domain.UserStatus;
import com.example.qldetai.feature.project.domain.ProjectAssignment;
import com.example.qldetai.feature.project.repository.ProjectAssignmentRepository;
import com.example.qldetai.feature.topic.domain.Topic;
import com.example.qldetai.feature.topic.repository.TopicRepository;
import com.example.qldetai.feature.topic.repository.TopicSupervisorRepository;
import com.example.qldetai.feature.communication.email.EmailNotificationDtos.EmailNotificationSettingResponse;
import com.example.qldetai.feature.communication.email.EmailNotificationDtos.EmailPreviewResponse;
import com.example.qldetai.feature.communication.email.EmailNotificationDtos.EmailTestQueuedResponse;
import com.example.qldetai.feature.communication.email.EmailNotificationDtos.PreviewNotificationSettingRequest;
import com.example.qldetai.feature.communication.email.EmailNotificationDtos.RecipientCandidateResponse;
import com.example.qldetai.feature.communication.email.EmailNotificationDtos.RecipientCandidatesResponse;
import com.example.qldetai.feature.communication.email.EmailNotificationDtos.SendTestEmailRequest;
import com.example.qldetai.feature.communication.email.EmailNotificationDtos.UpdateNotificationSettingRequest;
import jakarta.mail.internet.InternetAddress;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Settings, template validation, and authorization for all email notification scopes. */
@Service
@RequiredArgsConstructor
public class EmailNotificationSettingsService {

    private static final long SYSTEM_SCOPE_ID = 0L;
    private static final Set<Integer> VALID_REMINDER_HOURS = Set.of(6, 12, 24, 48);

    private final EmailNotificationSettingRepository settingRepository;
    private final UserRepository userRepository;
    private final LecturerRepository lecturerRepository;
    private final FacultyRepository facultyRepository;
    private final AcademicAppointmentRepository academicAppointmentRepository;
    private final StudentGroupRepository studentGroupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final TopicRegistrationRepository topicRegistrationRepository;
    private final TopicRepository topicRepository;
    private final TopicSupervisorRepository topicSupervisorRepository;
    private final ProjectAssignmentRepository projectAssignmentRepository;
    private final EmailTemplateRenderer templateRenderer;
    private final EmailEventContextFactory contextFactory;
    private final EmailRecipientResolver recipientResolver;
    private final EmailOutboxService emailOutboxService;
    private final EmailNotificationProperties properties;
    private final Clock clock;


    @Transactional(readOnly = true)
    public List<EmailNotificationSettingResponse> list(
            Long actorId,
            NotificationScopeType requestedScopeType,
            Long requestedScopeId) {
        AccessScope scope = resolveScope(actorId, requestedScopeType, requestedScopeId);
        Map<EmailNotificationEventType, EmailNotificationSetting> byEvent = settingRepository
                .findByScopeTypeAndScopeIdOrderByEventTypeAsc(scope.type(), scope.id()).stream()
                .collect(java.util.stream.Collectors.toMap(EmailNotificationSetting::getEventType, setting -> setting));
        return Arrays.stream(EmailNotificationEventType.values())
                .filter(eventType -> scopeAllowsEvent(scope, eventType))
                .map(eventType -> byEvent.containsKey(eventType)
                        ? toResponse(byEvent.get(eventType))
                        : defaultResponse(eventType, scope))
                .toList();
    }

    @Transactional(readOnly = true)
    public EmailNotificationSettingResponse get(
            Long actorId,
            EmailNotificationEventType eventType,
            NotificationScopeType requestedScopeType,
            Long requestedScopeId) {
        AccessScope scope = resolveScope(actorId, requestedScopeType, requestedScopeId);
        ensureScopeAllowsEvent(scope, eventType);
        return settingRepository.findByEventTypeAndScopeTypeAndScopeId(eventType, scope.type(), scope.id())
                .map(this::toResponse)
                .orElseGet(() -> defaultResponse(eventType, scope));
    }

    @Transactional
    public EmailNotificationSettingResponse update(
            Long actorId,
            EmailNotificationEventType eventType,
            UpdateNotificationSettingRequest request) {
        User actor = requireActiveUser(actorId);
        AccessScope scope = resolveScope(actor, request.scopeType(), request.scopeId());
        ensureScopeAllowsEvent(scope, eventType);
        validateSettingInput(eventType, request);

        EmailNotificationSetting setting = settingRepository
                .findByEventTypeAndScopeTypeAndScopeId(eventType, scope.type(), scope.id())
                .orElseGet(EmailNotificationSetting::new);
        setting.setEventType(eventType);
        setting.setScopeType(scope.type());
        setting.setScopeId(scope.id());
        setting.setEnabled(request.enabled());
        setting.setSenderName(normalizeSenderName(request.senderName()));
        setting.setFromEmail(resolveAndValidateFrom(request.fromEmail()));
        setting.setReplyToEmail(normalizeAndValidateEmail(request.replyToEmail(), "Reply-To email"));
        setting.setSubjectTemplate(request.subjectTemplate().trim());
        setting.setBodyTemplate(request.bodyTemplate().trim());
        setting.setRecipientGroups(encodeRecipientGroups(request.recipientGroups()));
        setting.setReminderHours(request.reminderHours());
        setting.setUpdatedBy(actor);
        setting.setUpdatedAt(LocalDateTime.now(clock));
        return toResponse(settingRepository.save(setting));
    }

    @Transactional(readOnly = true)
    public EmailPreviewResponse preview(
            Long actorId,
            EmailNotificationEventType eventType,
            PreviewNotificationSettingRequest request) {
        AccessScope scope = resolveScope(actorId, request.scopeType(), request.scopeId());
        ensureScopeAllowsEvent(scope, eventType);
        EmailNotificationSetting setting = settingRepository
                .findByEventTypeAndScopeTypeAndScopeId(eventType, scope.type(), scope.id())
                .orElse(null);

        String senderName = normalizeSenderName(firstNonBlank(
                request.senderName(), setting == null ? null : setting.getSenderName(), "Hệ thống quản lý đề tài sinh viên"));
        String fromEmail = resolveAndValidateFrom(firstNonBlank(request.fromEmail(), setting == null ? null : setting.getFromEmail(), null));
        String replyTo = normalizeAndValidateEmail(
                firstNonBlank(request.replyToEmail(), setting == null ? null : setting.getReplyToEmail(), null),
                "Reply-To email");
        String subjectTemplate = firstNonBlank(request.subjectTemplate(), setting == null ? null : setting.getSubjectTemplate(),
                EmailTemplateCatalog.definition(eventType).defaultSubject());
        String bodyTemplate = firstNonBlank(request.bodyTemplate(), setting == null ? null : setting.getBodyTemplate(),
                EmailTemplateCatalog.definition(eventType).defaultBody());
        templateRenderer.validate(eventType, subjectTemplate, bodyTemplate);

        boolean sampleDataUsed = request.eventReferenceId() == null;
        if (request.eventReferenceId() != null && request.sampleData() != null && !request.sampleData().isEmpty()) {
            throw new BusinessRuleException("Use either sample data or a business-data reference for email preview.");
        }
        EmailEventContext context = sampleDataUsed
                ? contextFactory.sample(eventType)
                : contextFactory.fromReference(eventType, request.referenceType(), request.eventReferenceId());
        if (!sampleDataUsed) {
            ensureContextAccessible(scope, context);
        }
        Map<String, String> values = new LinkedHashMap<>(context.variables());
        if (sampleDataUsed && request.sampleData() != null) {
            mergeSampleValues(eventType, values, request.sampleData());
        }
        EmailTemplateRenderer.RenderedEmail rendered = templateRenderer.render(eventType, subjectTemplate, bodyTemplate, values);
        return new EmailPreviewResponse(
                eventType,
                senderName,
                fromEmail,
                replyTo,
                rendered.subject(),
                rendered.body(),
                EmailTemplateCatalog.allowedVariables(eventType),
                sampleDataUsed);
    }

    @Transactional(readOnly = true)
    public RecipientCandidatesResponse recipients(
            Long actorId,
            EmailNotificationEventType eventType,
            NotificationScopeType requestedScopeType,
            Long requestedScopeId,
            Long facultyId,
            Long groupId,
            Long topicId) {
        AccessScope scope = resolveScope(actorId, requestedScopeType, requestedScopeId);
        ensureScopeAllowsEvent(scope, eventType);
        EmailEventContext context = recipientContext(eventType, facultyId, groupId, topicId);
        ensureContextAccessible(scope, context);
        List<RecipientCandidateResponse> values = recipientResolver
                .resolve(EmailTemplateCatalog.allowedRecipientGroups(eventType), context)
                .stream()
                .map(recipient -> new RecipientCandidateResponse(
                        recipient.user().getId(),
                        recipient.user().getFullName(),
                        recipient.user().getEmail(),
                        recipient.category()))
                .sorted(Comparator.comparing(RecipientCandidateResponse::fullName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return new RecipientCandidatesResponse(values);
    }

    @Transactional
    public EmailTestQueuedResponse queueTest(
            Long actorId,
            EmailNotificationEventType eventType,
            SendTestEmailRequest request) {
        User actor = requireActiveUser(actorId);
        AccessScope scope = resolveScope(actor, request.scopeType(), request.scopeId());
        ensureScopeAllowsEvent(scope, eventType);
        EmailNotificationSetting setting = settingRepository
                .findByEventTypeAndScopeTypeAndScopeId(eventType, scope.type(), scope.id())
                .orElseThrow(() -> new BusinessRuleException("Save this email setting before sending a test message."));
        EmailEventContext context = request.eventReferenceId() == null
                ? contextFactory.sample(eventType)
                : contextFactory.fromReference(eventType, request.referenceType(), request.eventReferenceId());
        if (request.eventReferenceId() != null) {
            ensureContextAccessible(scope, context);
        }
        EmailOutbox queued = emailOutboxService.enqueueTest(setting, actor, context);
        return new EmailTestQueuedResponse(queued.getId(), queued.getRecipientEmail(), queued.getStatus());
    }

    /** Selects the first matching scope in lecturer -> faculty -> system order. */
    @Transactional(readOnly = true, noRollbackFor = RuntimeException.class)
    public Optional<EmailNotificationSetting> resolveForEvent(EmailEventContext context) {
        LinkedHashSet<Long> lecturerIds = new LinkedHashSet<>(context.responsibleLecturerUserIds() == null
                ? List.of()
                : context.responsibleLecturerUserIds());
        for (Long lecturerUserId : lecturerIds) {
            if (lecturerUserId == null || !userRepository.existsByIdAndStatus(lecturerUserId, UserStatus.ACTIVE)) {
                continue;
            }
            Optional<EmailNotificationSetting> setting = settingRepository.findByEventTypeAndScopeTypeAndScopeId(
                    context.eventType(), NotificationScopeType.LECTURER, lecturerUserId);
            if (setting.isPresent()) {
                return setting;
            }
        }
        if (context.facultyId() != null) {
            Optional<EmailNotificationSetting> setting = settingRepository.findByEventTypeAndScopeTypeAndScopeId(
                    context.eventType(), NotificationScopeType.FACULTY, context.facultyId());
            if (setting.isPresent()) {
                return setting;
            }
        }
        return settingRepository.findByEventTypeAndScopeTypeAndScopeId(
                context.eventType(), NotificationScopeType.SYSTEM, SYSTEM_SCOPE_ID);
    }

    public Set<EmailRecipientGroup> recipientGroups(EmailNotificationSetting setting) {
        if (setting.getRecipientGroups() == null || setting.getRecipientGroups().isBlank()) {
            return Set.of();
        }
        try {
            return Arrays.stream(setting.getRecipientGroups().split(","))
                    .filter(value -> !value.isBlank())
                    .map(String::trim)
                    .map(EmailRecipientGroup::valueOf)
                    .collect(java.util.stream.Collectors.toCollection(() -> EnumSet.noneOf(EmailRecipientGroup.class)));
        } catch (IllegalArgumentException exception) {
            throw new BusinessRuleException("Email recipient configuration contains an unsupported recipient group.");
        }
    }

    private void validateSettingInput(EmailNotificationEventType eventType, UpdateNotificationSettingRequest request) {
        normalizeSenderName(request.senderName());
        templateRenderer.validate(eventType, request.subjectTemplate().trim(), request.bodyTemplate().trim());
        resolveAndValidateFrom(request.fromEmail());
        normalizeAndValidateEmail(request.replyToEmail(), "Reply-To email");
        if (request.recipientGroups() == null || request.recipientGroups().isEmpty()) {
            throw new BusinessRuleException("Choose at least one email recipient group.");
        }
        Set<EmailRecipientGroup> allowed = EmailTemplateCatalog.allowedRecipientGroups(eventType);
        if (!allowed.containsAll(request.recipientGroups())) {
            throw new BusinessRuleException("One or more recipient groups are not allowed for this email event.");
        }
        if (eventType == EmailNotificationEventType.DEADLINE_REMINDER) {
            if (request.reminderHours() == null || !VALID_REMINDER_HOURS.contains(request.reminderHours())) {
                throw new BusinessRuleException("Deadline reminders must use 6, 12, 24, or 48 hours.");
            }
        } else if (request.reminderHours() != null) {
            throw new BusinessRuleException("Reminder hours are only valid for deadline reminders.");
        }
    }

    private AccessScope resolveScope(Long actorId, NotificationScopeType requestedType, Long requestedId) {
        return resolveScope(requireActiveUser(actorId), requestedType, requestedId);
    }

    private AccessScope resolveScope(User actor, NotificationScopeType requestedType, Long requestedId) {
        boolean admin = hasRole(actor, RoleCode.ADMIN);
        List<Long> deanFacultyIds = academicAppointmentRepository.findActiveDeanFacultyIds(
                actor.getId(), LocalDateTime.now(clock));
        boolean lecturer = lecturerRepository.findByUserId(actor.getId()).isPresent();
        NotificationScopeType type = requestedType;

        if (type == null) {
            if (admin) {
                return new AccessScope(NotificationScopeType.SYSTEM, SYSTEM_SCOPE_ID, actor, true);
            }
            if (deanFacultyIds.size() == 1) {
                return new AccessScope(NotificationScopeType.FACULTY, deanFacultyIds.getFirst(), actor, false);
            }
            if (deanFacultyIds.size() > 1) {
                throw new BusinessRuleException("Select the faculty scope to manage its email settings.");
            }
            if (lecturer) {
                return new AccessScope(NotificationScopeType.LECTURER, actor.getId(), actor, false);
            }
            throw new org.springframework.security.access.AccessDeniedException("A lecturer, dean, or administrator account is required.");
        }

        return switch (type) {
            case SYSTEM -> {
                if (!admin) {
                    throw new org.springframework.security.access.AccessDeniedException("Only administrators can manage system email settings.");
                }
                if (requestedId != null && requestedId != SYSTEM_SCOPE_ID) {
                    throw new BusinessRuleException("System scope id must be 0.");
                }
                yield new AccessScope(type, SYSTEM_SCOPE_ID, actor, true);
            }
            case FACULTY -> {
                if (requestedId == null) {
                    throw new BusinessRuleException("Faculty scope requires a faculty id.");
                }
                facultyRepository.findById(requestedId)
                        .orElseThrow(() -> new ResourceNotFoundException("Faculty was not found."));
                if (!admin && !deanFacultyIds.contains(requestedId)) {
                    throw new org.springframework.security.access.AccessDeniedException("You can manage email settings only for your faculty.");
                }
                yield new AccessScope(type, requestedId, actor, admin);
            }
            case LECTURER -> {
                if (!admin && !lecturer) {
                    throw new org.springframework.security.access.AccessDeniedException("A lecturer profile is required for this scope.");
                }
                if (requestedId != null && !requestedId.equals(actor.getId()) && !admin) {
                    throw new org.springframework.security.access.AccessDeniedException("Lecturers can manage only their own email settings.");
                }
                long lecturerUserId = requestedId == null ? actor.getId() : requestedId;
                if (lecturerRepository.findByUserId(lecturerUserId).isEmpty()) {
                    throw new ResourceNotFoundException("Lecturer profile was not found.");
                }
                yield new AccessScope(type, lecturerUserId, actor, admin);
            }
        };
    }

    private boolean scopeAllowsEvent(AccessScope scope, EmailNotificationEventType eventType) {
        return scope.type() != NotificationScopeType.LECTURER
                || eventType == EmailNotificationEventType.TASK_ASSIGNED
                || eventType == EmailNotificationEventType.DEADLINE_REMINDER;
    }

    private void ensureScopeAllowsEvent(AccessScope scope, EmailNotificationEventType eventType) {
        if (!scopeAllowsEvent(scope, eventType)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Lecturer email settings are limited to task assignment and deadline reminder events.");
        }
    }

    private void ensureContextAccessible(AccessScope scope, EmailEventContext context) {
        if (scope.administrator()) {
            return;
        }
        if (scope.type() == NotificationScopeType.FACULTY) {
            if (!scope.id().equals(context.facultyId())) {
                throw new org.springframework.security.access.AccessDeniedException("This data does not belong to your faculty.");
            }
            return;
        }
        if (context.responsibleLecturerUserIds() == null
                || !context.responsibleLecturerUserIds().contains(scope.actor().getId())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "This data is not within a topic or group you directly supervise.");
        }
    }

    private EmailEventContext recipientContext(
            EmailNotificationEventType eventType,
            Long facultyId,
            Long groupId,
            Long topicId) {
        StudentGroup group = groupId == null ? null : studentGroupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Student group was not found."));
        Topic topic = topicId == null ? null : topicRepository.findById(topicId)
                .orElseThrow(() -> new ResourceNotFoundException("Topic was not found."));
        boolean groupDerivedFromTopic = false;
        if (group == null && topic != null && topic.getReservedGroup() != null) {
            group = topic.getReservedGroup();
            groupDerivedFromTopic = true;
        }
        Optional<ProjectAssignment> assignment = group == null
                ? Optional.empty()
                : projectAssignmentRepository.findByGroupId(group.getId());
        boolean topicDerivedFromAssignment = false;
        if (topic == null && assignment.isPresent()) {
            topic = assignment.get().getTopic();
            topicDerivedFromAssignment = true;
        }
        if (group != null && topic != null && !groupDerivedFromTopic && !topicDerivedFromAssignment) {
            boolean related;
            if (assignment.isPresent()) {
                related = assignment.get().getTopic().getId().equals(topic.getId());
            } else {
                related = topicRegistrationRepository.existsByGroupIdAndTopicId(group.getId(), topic.getId());
            }
            if (!related) {
                throw new BusinessRuleException("The group is not related to the requested topic.");
            }
        }
        Long resolvedFacultyId = facultyId;
        Long periodId = null;
        if (group != null) {
            resolvedFacultyId = requireSameOrUse(resolvedFacultyId, group.getPeriod().getFaculty().getId(), "group");
            periodId = group.getPeriod().getId();
        }
        if (topic != null) {
            resolvedFacultyId = requireSameOrUse(resolvedFacultyId, topic.getFaculty().getId(), "topic");
            if (periodId != null && !periodId.equals(topic.getPeriod().getId())) {
                throw new BusinessRuleException("The group and topic must belong to the same registration period.");
            }
            periodId = topic.getPeriod().getId();
        }
        if (resolvedFacultyId == null) {
            throw new BusinessRuleException("Provide facultyId, groupId, or topicId to resolve recipients.");
        }
        facultyRepository.findById(resolvedFacultyId)
                .orElseThrow(() -> new ResourceNotFoundException("Faculty was not found."));
        List<Long> supervisorIds = topic == null ? List.of() : topicSupervisorRepository
                .findByTopicIdOrderBySlotNoAsc(topic.getId()).stream()
                .map(supervisor -> supervisor.getLecturer().getUser().getId()).toList();
        List<Long> directIds = group == null ? List.of() : groupMemberIds(group.getId());
        return new EmailEventContext(eventType, "recipient-preview", resolvedFacultyId, periodId,
                group == null ? null : group.getId(), topic == null ? null : topic.getId(),
                assignment.map(value -> value.getId()).orElse(null),
                null, null, directIds, supervisorIds, Map.of());
    }

    private List<Long> groupMemberIds(Long groupId) {
        return groupMemberRepository.findByGroupIdAndLeftAtIsNullOrderBySlotNoAsc(groupId).stream()
                .map(member -> member.getStudent().getUser().getId())
                .toList();
    }

    private Long requireSameOrUse(Long existing, Long candidate, String description) {
        if (existing != null && !existing.equals(candidate)) {
            throw new BusinessRuleException("The " + description + " does not belong to the requested faculty.");
        }
        return candidate;
    }

    private void mergeSampleValues(
            EmailNotificationEventType eventType,
            Map<String, String> target,
            Map<String, String> sampleData) {
        Set<String> allowed = EmailTemplateCatalog.definition(eventType).variables();
        for (Map.Entry<String, String> entry : sampleData.entrySet()) {
            if (!allowed.contains(entry.getKey())) {
                throw new BusinessRuleException("Variable " + entry.getKey() + " is not allowed for " + eventType + ".");
            }
            target.put(entry.getKey(), entry.getValue());
        }
    }

    private String resolveAndValidateFrom(String requestedFrom) {
        String defaultFrom = normalizeAndValidateEmail(properties.getDefaultFrom(), "MAIL_FROM");
        if (defaultFrom == null) {
            throw new BusinessRuleException("MAIL_FROM must be configured before email settings can be saved.");
        }
        String normalized = normalizeAndValidateEmail(firstNonBlank(requestedFrom, defaultFrom), "From email");
        Set<String> allowed = new LinkedHashSet<>();
        allowed.add(defaultFrom);
        Arrays.stream(Optional.ofNullable(properties.getAllowedFromAddresses()).orElse("").split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(value -> normalizeAndValidateEmail(value, "MAIL_ALLOWED_FROM"))
                .forEach(allowed::add);
        if (!allowed.contains(normalized)) {
            throw new BusinessRuleException("From email is not in the deployment's verified sender allow-list.");
        }
        return normalized;
    }

    private String encodeRecipientGroups(Collection<EmailRecipientGroup> groups) {
        return groups.stream().sorted(Comparator.comparing(Enum::name)).map(Enum::name).collect(java.util.stream.Collectors.joining(","));
    }

    private EmailNotificationSettingResponse defaultResponse(EmailNotificationEventType eventType, AccessScope scope) {
        EmailTemplateCatalog.TemplateDefinition defaults = EmailTemplateCatalog.definition(eventType);
        return new EmailNotificationSettingResponse(
                null,
                eventType,
                false,
                "Hệ thống quản lý đề tài sinh viên",
                resolveAndValidateFrom(null),
                null,
                defaults.defaultSubject(),
                defaults.defaultBody(),
                defaultRecipientGroups(eventType),
                eventType == EmailNotificationEventType.DEADLINE_REMINDER ? 24 : null,
                scope.type(),
                scope.id(),
                null,
                null,
                null,
                0,
                EmailTemplateCatalog.allowedVariables(eventType));
    }

    private Set<EmailRecipientGroup> defaultRecipientGroups(EmailNotificationEventType eventType) {
        return eventType == EmailNotificationEventType.REGISTRATION_OPENED
                ? Set.of(EmailRecipientGroup.FACULTY_STUDENTS)
                : Set.of(EmailRecipientGroup.DIRECT_PARTICIPANTS);
    }

    private EmailNotificationSettingResponse toResponse(EmailNotificationSetting setting) {
        return new EmailNotificationSettingResponse(
                setting.getId(),
                setting.getEventType(),
                setting.isEnabled(),
                setting.getSenderName(),
                setting.getFromEmail(),
                setting.getReplyToEmail(),
                setting.getSubjectTemplate(),
                setting.getBodyTemplate(),
                recipientGroups(setting),
                setting.getReminderHours(),
                setting.getScopeType(),
                setting.getScopeId(),
                setting.getUpdatedBy().getId(),
                setting.getUpdatedBy().getFullName(),
                setting.getUpdatedAt(),
                setting.getVersion(),
                EmailTemplateCatalog.allowedVariables(setting.getEventType()));
    }

    private User requireActiveUser(Long userId) {
        User user = userRepository.findWithRolesById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User account was not found."));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new org.springframework.security.access.AccessDeniedException("An active user account is required.");
        }
        return user;
    }

    private boolean hasRole(User user, RoleCode role) {
        return user.getUserRoles().stream().anyMatch(userRole -> userRole.getRole().getCode() == role);
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeSenderName(String value) {
        String normalized = normalizeNullable(value);
        if (normalized == null || normalized.length() > 150) {
            throw new BusinessRuleException("Sender name must contain 1 to 150 characters.");
        }
        if (normalized.indexOf('\r') >= 0 || normalized.indexOf('\n') >= 0) {
            throw new BusinessRuleException("Sender name must not contain line breaks.");
        }
        return normalized;
    }

    private String normalizeAndValidateEmail(String value, String fieldName) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            return null;
        }
        if (normalized.length() > 254 || normalized.indexOf('\r') >= 0 || normalized.indexOf('\n') >= 0) {
            throw new BusinessRuleException(fieldName + " must be a valid email address.");
        }
        try {
            InternetAddress address = new InternetAddress(normalized, true);
            address.validate();
        } catch (Exception exception) {
            throw new BusinessRuleException(fieldName + " must be a valid email address.");
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private record AccessScope(NotificationScopeType type, Long id, User actor, boolean administrator) {
    }
}
