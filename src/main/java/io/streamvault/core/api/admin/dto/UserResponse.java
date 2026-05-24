package io.streamvault.core.api.admin.dto;

import io.streamvault.core.domain.auth.Role;
import io.streamvault.core.domain.auth.User;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserResponse(UUID id, String username, Role role, OffsetDateTime createdAt) {
    public static UserResponse from(User u) {
        return new UserResponse(u.id, u.username, u.role, u.createdAt);
    }
}
