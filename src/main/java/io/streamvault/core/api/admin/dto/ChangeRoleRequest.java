package io.streamvault.core.api.admin.dto;

import io.streamvault.core.domain.auth.Role;
import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(@NotNull Role role) {}
