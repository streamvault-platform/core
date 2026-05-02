package io.streamvault.core.api.admin.dto;

import io.streamvault.core.domain.library.Track;

import java.util.UUID;

public record UploadResponse(
        UUID trackId,
        String title,
        String artist,
        String album,
        Integer durationMs,
        String mimeType
) {
    public static UploadResponse from(Track t) {
        return new UploadResponse(
                t.id,
                t.title,
                t.artist != null ? t.artist.name : null,
                t.album != null ? t.album.title : null,
                t.durationMs,
                t.mimeType
        );
    }
}
