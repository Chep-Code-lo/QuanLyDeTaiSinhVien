package com.example.qldetai.feature.communication.email;

import com.example.qldetai.exception.BusinessRuleException;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A deliberately small plain-text template renderer with no expression execution. */
@Component
public class EmailTemplateRenderer {

    private static final Pattern VARIABLE =
            Pattern.compile("\\{\\{\\s*([A-Za-z][A-Za-z0-9]*)\\s*}}");
    private static final Pattern UNBALANCED_TEMPLATE_TOKEN = Pattern.compile("\\{\\{|}}");
    private static final String MISSING_VALUE = "(không có dữ liệu)";
    private static final int MAX_SUBJECT_LENGTH = 500;

    public void validate(
            EmailNotificationEventType eventType, String subjectTemplate, String bodyTemplate) {
        if (subjectTemplate == null
                || subjectTemplate.isBlank()
                || bodyTemplate == null
                || bodyTemplate.isBlank()) {
            throw new BusinessRuleException("Email subject and body templates are required.");
        }
        if (subjectTemplate.indexOf('\r') >= 0 || subjectTemplate.indexOf('\n') >= 0) {
            throw new BusinessRuleException("Email subject must not contain line breaks.");
        }
        validateTemplateTokens(eventType, subjectTemplate);
        validateTemplateTokens(eventType, bodyTemplate);
    }

    public RenderedEmail render(
            EmailNotificationEventType eventType,
            String subjectTemplate,
            String bodyTemplate,
            Map<String, String> values) {
        validate(eventType, subjectTemplate, bodyTemplate);
        String subject = normalizeSubject(replace(eventType, subjectTemplate, values));
        String body = replace(eventType, bodyTemplate, values);
        return new RenderedEmail(subject, body);
    }

    private void validateTemplateTokens(EmailNotificationEventType eventType, String template) {
        Set<String> allowed = EmailTemplateCatalog.definition(eventType).variables();
        Matcher matcher = VARIABLE.matcher(template);
        StringBuffer stripped = new StringBuffer();
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!allowed.contains(name)) {
                throw new BusinessRuleException(
                        "Variable {{" + name + "}} is not allowed for " + eventType + ".");
            }
            matcher.appendReplacement(stripped, "");
        }
        matcher.appendTail(stripped);
        if (UNBALANCED_TEMPLATE_TOKEN.matcher(stripped).find()) {
            throw new BusinessRuleException("Email template contains an invalid variable token.");
        }
    }

    private String replace(
            EmailNotificationEventType eventType, String template, Map<String, String> values) {
        Matcher matcher = VARIABLE.matcher(template);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            String raw = values == null ? null : values.get(key);
            String value = raw == null || raw.isBlank() ? MISSING_VALUE : raw;
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /** Dynamic business data must never create an additional SMTP header. */
    private String normalizeSubject(String subject) {
        String normalized = subject.replaceAll("[\\r\\n]+", " ").trim();
        return normalized.length() <= MAX_SUBJECT_LENGTH
                ? normalized
                : normalized.substring(0, MAX_SUBJECT_LENGTH - 3) + "...";
    }

    public record RenderedEmail(String subject, String body) {}
}
