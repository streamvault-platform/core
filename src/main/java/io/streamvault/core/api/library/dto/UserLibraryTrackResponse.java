package io.streamvault.core.api.library.dto;

import io.streamvault.core.domain.library.UserLibraryTrack;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserLibraryTrackResponse(
        UUID trackId,
        UUID artistId,
        UUID albumId,
        String title,
        String artist,
        String album,
        Integer durationMs,
        String mimeType,
        OffsetDateTime addedAt
) {
    public static UserLibraryTrackResponse from(UserLibraryTrack ult) {
        var t = ult.track;
        return new UserLibraryTrackResponse(
                t.id,
                t.artist != null ? t.artist.id : null,
                t.album != null ? t.album.id : null,
                t.title,
                t.artist != null ? t.artist.name : null,
                t.album != null ? t.album.title : null,
                t.durationMs,
                t.mimeType,
                ult.addedAt
        );
    }
}
