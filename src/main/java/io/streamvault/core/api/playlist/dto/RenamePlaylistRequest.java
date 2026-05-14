package io.streamvault.core.api.playlist.dto;

import jakarta.validation.constraints.NotBlank;

public record RenamePlaylistRequest(
        @NotBlank
        String name
) {}
