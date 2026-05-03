package io.streamvault.core.api.library.dto;

import io.streamvault.core.domain.library.Album;

import java.util.UUID;

public record AlbumResponse(UUID id, String title, UUID artistId, String artistName, Integer year) {
    public static AlbumResponse from(Album a) {
        return new AlbumResponse(
                a.id,
                a.title,
                a.artist != null ? a.artist.id : null,
                a.artist != null ? a.artist.name : null,
                a.year
        );
    }
}
