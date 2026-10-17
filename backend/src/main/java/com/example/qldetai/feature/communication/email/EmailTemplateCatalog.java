package com.example.qldetai.feature.communication.email;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Central, finite template vocabulary; no expression language is evaluated. */
public final class EmailTemplateCatalog {

    private static final Map<EmailNotificationEventType, TemplateDefinition> DEFINITIONS = definitions();

    private EmailTemplateCatalog() {
    }

    public static TemplateDefinition definition(EmailNotificationEventType eventType) {
        return DEFINITIONS.get(eventType);
    }

    public static List<String> allowedVariables(EmailNotificationEventType eventType) {
        return definition(eventType).variables().stream().sorted().toList();
    }

    public static Set<EmailRecipientGroup> allowedRecipientGroups(EmailNotificationEventType eventType) {
        return definition(eventType).recipientGroups();
    }

    private static Map<EmailNotificationEventType, TemplateDefinition> definitions() {
        Map<EmailNotificationEventType, TemplateDefinition> values = new EnumMap<>(EmailNotificationEventType.class);
        values.put(EmailNotificationEventType.TASK_ASSIGNED, new TemplateDefinition(
                Set.of("studentName", "lecturerName", "topicName", "taskName", "periodName", "facultyName", "websiteUrl"),
                Set.of(EmailRecipientGroup.DIRECT_PARTICIPANTS, EmailRecipientGroup.GROUP_STUDENTS,
                        EmailRecipientGroup.TOPIC_STUDENTS, EmailRecipientGroup.SUPERVISORS),
                "Nhiệm vụ mới: {{taskName}}",
                "<p>Chào {{studentName}},</p><p>Bạn có nhiệm vụ mới cho đề tài <strong>{{topicName}}</strong> trong đợt {{periodName}}.</p><p>Giảng viên phụ trách: {{lecturerName}}.</p><p><a href=\"{{websiteUrl}}\">Mở hệ thống</a></p>"));
        values.put(EmailNotificationEventType.TOPIC_APPROVED, new TemplateDefinition(
                Set.of("studentName", "lecturerName", "topicName", "periodName", "facultyName", "websiteUrl"),
                Set.of(EmailRecipientGroup.DIRECT_PARTICIPANTS, EmailRecipientGroup.GROUP_STUDENTS,
                        EmailRecipientGroup.TOPIC_STUDENTS, EmailRecipientGroup.SUPERVISORS),
                "Đề tài đã được phê duyệt: {{topicName}}",
                "<p>Chào {{studentName}},</p><p>Đề tài <strong>{{topicName}}</strong> đã được phê duyệt.</p><p><a href=\"{{websiteUrl}}\">Mở hệ thống</a></p>"));
        values.put(EmailNotificationEventType.TOPIC_REJECTED, new TemplateDefinition(
                Set.of("studentName", "lecturerName", "topicName", "periodName", "facultyName", "websiteUrl", "decisionReason"),
                Set.of(EmailRecipientGroup.DIRECT_PARTICIPANTS, EmailRecipientGroup.GROUP_STUDENTS,
                        EmailRecipientGroup.TOPIC_STUDENTS, EmailRecipientGroup.SUPERVISORS),
                "Đề tài chưa được chấp thuận: {{topicName}}",
                "<p>Chào {{studentName}},</p><p>Đề tài <strong>{{topicName}}</strong> chưa được chấp thuận.</p><p>Lý do: {{decisionReason}}</p><p><a href=\"{{websiteUrl}}\">Mở hệ thống</a></p>"));
        values.put(EmailNotificationEventType.DEADLINE_REMINDER, new TemplateDefinition(
                Set.of("studentName", "lecturerName", "topicName", "taskName", "deadline", "periodName", "facultyName", "websiteUrl"),
                Set.of(EmailRecipientGroup.DIRECT_PARTICIPANTS, EmailRecipientGroup.GROUP_STUDENTS,
                        EmailRecipientGroup.TOPIC_STUDENTS, EmailRecipientGroup.SUPERVISORS),
                "Nhắc hạn: {{taskName}}",
                "<p>Chào {{studentName}},</p><p>Nhiệm vụ <strong>{{taskName}}</strong> của đề tài {{topicName}} sắp đến hạn lúc {{deadline}}.</p><p><a href=\"{{websiteUrl}}\">Mở hệ thống</a></p>"));
        values.put(EmailNotificationEventType.REGISTRATION_OPENED, new TemplateDefinition(
                Set.of("studentName", "periodName", "facultyName", "websiteUrl"),
                Set.of(EmailRecipientGroup.FACULTY_STUDENTS, EmailRecipientGroup.FACULTY_LECTURERS,
                        EmailRecipientGroup.ADMINS),
                "Đợt đăng ký đã mở: {{periodName}}",
                "<p>Chào {{studentName}},</p><p>Đợt đăng ký <strong>{{periodName}}</strong> của {{facultyName}} đã mở.</p><p><a href=\"{{websiteUrl}}\">Mở hệ thống</a></p>"));
        return Map.copyOf(values);
    }

    public record TemplateDefinition(
            Set<String> variables,
            Set<EmailRecipientGroup> recipientGroups,
            String defaultSubject,
            String defaultBody) {
    }
}
