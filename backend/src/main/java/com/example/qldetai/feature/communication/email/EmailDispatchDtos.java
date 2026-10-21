package com.example.qldetai.feature.communication.email;

import com.example.qldetai.feature.identity.domain.RoleCode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.Set;

public final class EmailDispatchDtos {
    private EmailDispatchDtos() { }

    public record EmailRecipientResponse(Long userId, String fullName, String email) { }

    public record SendEmailRequest(
            @Size(max = 2) Set<@NotNull RoleCode> recipientRoles,
            @Size(max = 500) Set<@NotNull @Positive Long> recipientUserIds) { }

    public record EmailQueuedResponse(int recipientCount) { }
}
