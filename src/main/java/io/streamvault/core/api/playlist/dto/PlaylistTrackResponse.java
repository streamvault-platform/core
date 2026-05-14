package io.streamvault.core.api.playlist.dto;

import io.streamvault.core.domain.playlist.PlaylistTrack;
import java.util.UUID;

public record PlaylistTrackResponse(
        UUID trackId,
        String title,
        String artistName,
        String albumTitle,
        Integer durationMs,
        String mimeType,
        int position
) {
    public static PlaylistTrackResponse from(PlaylistTrack pt) {
        var t = pt.track;
        return new PlaylistTrackResponse(
                t.id,
                t.title,
                t.artist != null ? t.artist.name : null,
                t.album != null ? t.album.title : null,
                t.durationMs,
                t.mimeType,
                pt.position
        );
    }
}
