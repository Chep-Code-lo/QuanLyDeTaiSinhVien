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
                """
                Chào {{studentName}},

                Bạn có nhiệm vụ mới cho đề tài {{topicName}} trong đợt {{periodName}}.
                Nhiệm vụ: {{taskName}}
                Giảng viên phụ trách: {{lecturerName}}

                Mở hệ thống: {{websiteUrl}}"""));
        values.put(EmailNotificationEventType.TOPIC_APPROVED, new TemplateDefinition(
                Set.of("studentName", "lecturerName", "topicName", "periodName", "facultyName", "websiteUrl"),
                Set.of(EmailRecipientGroup.DIRECT_PARTICIPANTS, EmailRecipientGroup.GROUP_STUDENTS,
                        EmailRecipientGroup.TOPIC_STUDENTS, EmailRecipientGroup.SUPERVISORS),
                "Đề tài đã được phê duyệt: {{topicName}}",
                """
                Chào {{studentName}},

                Đề tài {{topicName}} đã được phê duyệt.

                Mở hệ thống: {{websiteUrl}}"""));
        values.put(EmailNotificationEventType.TOPIC_REJECTED, new TemplateDefinition(
                Set.of("studentName", "lecturerName", "topicName", "periodName", "facultyName", "websiteUrl", "decisionReason"),
                Set.of(EmailRecipientGroup.DIRECT_PARTICIPANTS, EmailRecipientGroup.GROUP_STUDENTS,
                        EmailRecipientGroup.TOPIC_STUDENTS, EmailRecipientGroup.SUPERVISORS),
                "Đề tài chưa được chấp thuận: {{topicName}}",
                """
                Chào {{studentName}},

                Đề tài {{topicName}} chưa được chấp thuận.
                Lý do: {{decisionReason}}

                Mở hệ thống: {{websiteUrl}}"""));
        values.put(EmailNotificationEventType.DEADLINE_REMINDER, new TemplateDefinition(
                Set.of("studentName", "lecturerName", "topicName", "taskName", "deadline", "periodName", "facultyName", "websiteUrl"),
                Set.of(EmailRecipientGroup.DIRECT_PARTICIPANTS, EmailRecipientGroup.GROUP_STUDENTS,
                        EmailRecipientGroup.TOPIC_STUDENTS, EmailRecipientGroup.SUPERVISORS),
                "Nhắc hạn: {{taskName}}",
                """
                Chào {{studentName}},

                Nhiệm vụ {{taskName}} của đề tài {{topicName}} sắp đến hạn lúc {{deadline}}.

                Mở hệ thống: {{websiteUrl}}"""));
        values.put(EmailNotificationEventType.REGISTRATION_OPENED, new TemplateDefinition(
                Set.of("studentName", "periodName", "facultyName", "websiteUrl"),
                Set.of(EmailRecipientGroup.FACULTY_STUDENTS, EmailRecipientGroup.FACULTY_LECTURERS,
                        EmailRecipientGroup.ADMINS),
                "Đợt đăng ký đã mở: {{periodName}}",
                """
                Chào {{studentName}},

                Đợt đăng ký {{periodName}} của {{facultyName}} đã mở.

                Mở hệ thống: {{websiteUrl}}"""));
        values.put(EmailNotificationEventType.RESULT_PUBLISHED, new TemplateDefinition(
                Set.of("studentName", "topicName", "periodName", "facultyName", "finalScore", "resultVersion",
                        "publishedAt", "resultUrl", "websiteUrl"),
                Set.of(EmailRecipientGroup.DIRECT_PARTICIPANTS),
                "Kết quả đánh giá đề tài: {{topicName}}",
                """
                Chào {{studentName}},

                Kết quả đánh giá đề tài {{topicName}} trong đợt {{periodName}} đã được công bố.
                Điểm tổng kết: {{finalScore}}/10.
                Phiên bản kết quả: {{resultVersion}}.
                Thời điểm công bố: {{publishedAt}}.

                Xem kết quả: {{resultUrl}}"""));
        return Map.copyOf(values);
    }

    public record TemplateDefinition(
            Set<String> variables,
            Set<EmailRecipientGroup> recipientGroups,
            String defaultSubject,
            String defaultBody) {
    }
}
