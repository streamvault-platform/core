package io.streamvault.core.api.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 50) String username,
        @NotBlank @Size(min = 8, max = 128)
        @Pattern(regexp = ".*[A-Z].*", message = "must contain at least one uppercase letter")
        @Pattern(regexp = ".*[0-9].*", message = "must contain at least one digit")
        String password,
        String inviteToken
) {}
