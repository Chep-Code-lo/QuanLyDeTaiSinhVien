package com.example.qldetai.feature.communication.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.qldetai.exception.BusinessRuleException;

import org.junit.jupiter.api.Test;

import java.util.Map;

class EmailTemplateRendererTests {

    private final EmailTemplateRenderer renderer = new EmailTemplateRenderer();

    @Test
    void rendersOnlyWhitelistedVariablesAsPlainText() {
        EmailTemplateRenderer.RenderedEmail rendered =
                renderer.render(
                        EmailNotificationEventType.TASK_ASSIGNED,
                        "Nhiệm vụ {{taskName}}",
                        "Chào {{studentName}},\n{{taskName}}",
                        Map.of("studentName", "<b>Lan</b>", "taskName", "Báo cáo & kiểm thử"));

        assertThat(rendered.subject()).isEqualTo("Nhiệm vụ Báo cáo & kiểm thử");
        assertThat(rendered.body()).isEqualTo("Chào <b>Lan</b>,\nBáo cáo & kiểm thử");
    }

    @Test
    void rejectsUnknownAndMalformedVariables() {
        assertThatThrownBy(
                        () ->
                                renderer.validate(
                                        EmailNotificationEventType.TASK_ASSIGNED,
                                        "{{unknown}}",
                                        "Nội dung"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not allowed");

        assertThatThrownBy(
                        () ->
                                renderer.validate(
                                        EmailNotificationEventType.TASK_ASSIGNED,
                                        "Tiêu đề",
                                        "Nội dung {{studentName}"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("invalid variable token");
    }

    @Test
    void removesLineBreaksFromDynamicSubjectValues() {
        EmailTemplateRenderer.RenderedEmail rendered =
                renderer.render(
                        EmailNotificationEventType.TASK_ASSIGNED,
                        "Nhiệm vụ {{taskName}}",
                        "{{taskName}}",
                        Map.of("taskName", "Báo cáo\r\nBcc: outsider@example.test"));

        assertThat(rendered.subject()).doesNotContain("\r").doesNotContain("\n");
    }
}
