package io.streamvault.core.api.playlist.dto;

import jakarta.validation.constraints.NotNull;

public record SetVisibilityRequest(
        @NotNull
        Boolean isPublic
) {}
