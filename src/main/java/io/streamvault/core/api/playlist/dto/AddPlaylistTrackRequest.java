package io.streamvault.core.api.playlist.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AddPlaylistTrackRequest(
        @NotNull
        UUID trackId
) {}
