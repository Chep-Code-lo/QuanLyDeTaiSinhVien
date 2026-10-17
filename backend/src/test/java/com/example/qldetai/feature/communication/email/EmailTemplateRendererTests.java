package com.example.qldetai.feature.communication.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.qldetai.exception.BusinessRuleException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EmailTemplateRendererTests {

    private final EmailTemplateRenderer renderer = new EmailTemplateRenderer();

    @Test
    void rendersOnlyWhitelistedVariablesAndEscapesDynamicHtml() {
        EmailTemplateRenderer.RenderedEmail rendered = renderer.render(
                EmailNotificationEventType.TASK_ASSIGNED,
                "Nhiệm vụ {{taskName}}",
                "<p>Chào {{studentName}}</p><p>{{taskName}}</p>",
                Map.of("studentName", "<b>Lan</b>", "taskName", "Báo cáo & kiểm thử"));

        assertThat(rendered.subject()).isEqualTo("Nhiệm vụ Báo cáo & kiểm thử");
        assertThat(rendered.body()).contains("&lt;b&gt;Lan&lt;/b&gt;").contains("&amp;");
    }

    @Test
    void rejectsUnknownVariablesAndExecutableHtml() {
        assertThatThrownBy(() -> renderer.validate(
                EmailNotificationEventType.TASK_ASSIGNED,
                "{{unknown}}",
                "<p>Nội dung</p>"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not allowed");

        assertThatThrownBy(() -> renderer.validate(
                EmailNotificationEventType.TASK_ASSIGNED,
                "Tiêu đề",
                "<script>alert(1)</script>"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("executable HTML");
    }

    @Test
    void removesLineBreaksFromDynamicSubjectValues() {
        EmailTemplateRenderer.RenderedEmail rendered = renderer.render(
                EmailNotificationEventType.TASK_ASSIGNED,
                "Nhiệm vụ {{taskName}}",
                "<p>{{taskName}}</p>",
                Map.of("taskName", "Báo cáo\r\nBcc: outsider@example.test"));

        assertThat(rendered.subject()).doesNotContain("\r").doesNotContain("\n");
    }
}
