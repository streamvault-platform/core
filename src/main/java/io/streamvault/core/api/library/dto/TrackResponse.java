package io.streamvault.core.api.library.dto;

import io.streamvault.core.domain.library.Track;

import java.util.UUID;

public record TrackResponse(
        UUID id,
        String title,
        String filePath,
        UUID artistId,
        String artistName,
        UUID albumId,
        String albumTitle,
        Integer trackNumber,
        Integer discNumber,
        Integer durationMs,
        String genre,
        Integer year,
        String mimeType
) {
    public static TrackResponse from(Track t) {
        return new TrackResponse(
                t.id,
                t.title,
                t.filePath,
                t.artist != null ? t.artist.id : null,
                t.artist != null ? t.artist.name : null,
                t.album != null ? t.album.id : null,
                t.album != null ? t.album.title : null,
                t.trackNumber,
                t.discNumber,
                t.durationMs,
                t.genre,
                t.year,
                t.mimeType
        );
    }
}
