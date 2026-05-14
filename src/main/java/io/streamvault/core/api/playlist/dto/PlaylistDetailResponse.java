package io.streamvault.core.api.playlist.dto;

import io.streamvault.core.domain.playlist.Playlist;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record PlaylistDetailResponse(
        UUID id,
        UUID ownerId,
        String ownerName,
        String name,
        boolean isPublic,
        List<PlaylistTrackResponse> tracks,
        OffsetDateTime createdAt
) {
    public static PlaylistDetailResponse from(Playlist p) {
        var tracks = p.tracks.stream()
                .sorted((a, b) -> Integer.compare(a.position, b.position))
                .map(PlaylistTrackResponse::from)
                .toList();
        return new PlaylistDetailResponse(
                p.id, p.userId, p.ownerName, p.name, p.isPublic, tracks, p.createdAt);
    }
}
