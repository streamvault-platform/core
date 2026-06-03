package io.streamvault.core.api.admin.dto;

import io.streamvault.core.domain.auth.InviteLink;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InviteResponse(
        UUID id,
        String token,
        OffsetDateTime createdAt,
        OffsetDateTime expiresAt
) {
    public static InviteResponse from(InviteLink invite) {
        return new InviteResponse(invite.id, invite.token, invite.createdAt, invite.expiresAt);
    }
}
