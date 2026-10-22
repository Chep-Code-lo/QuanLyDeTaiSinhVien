package com.example.qldetai.feature.communication.email;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class EmailTemplateDtos {
    private EmailTemplateDtos() {}

    public record SaveEmailTemplateRequest(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 150) String senderName,
            @Email @Size(max = 254) String fromEmail,
            @Email @Size(max = 254) String replyToEmail,
            @NotBlank @Size(max = 500) String subjectTemplate,
            @NotBlank @Size(max = 30000) String bodyTemplate,
            @Min(0) Integer version) {}

    public record EmailTemplateResponse(
            Long id,
            String name,
            String senderName,
            String fromEmail,
            String replyToEmail,
            String subjectTemplate,
            String bodyTemplate,
            int version,
            LocalDateTime updatedAt) {}
}
