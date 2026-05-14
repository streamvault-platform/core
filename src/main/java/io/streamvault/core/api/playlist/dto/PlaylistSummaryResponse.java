package io.streamvault.core.api.playlist.dto;

import io.streamvault.core.domain.playlist.Playlist;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PlaylistSummaryResponse(
        UUID id,
        UUID ownerId,
        String ownerName,
        String name,
        boolean isPublic,
        int trackCount,
        OffsetDateTime createdAt
) {
    public static PlaylistSummaryResponse from(Playlist p) {
        return new PlaylistSummaryResponse(
                p.id, p.userId, p.ownerName, p.name, p.isPublic, p.tracks.size(), p.createdAt);
    }
}
