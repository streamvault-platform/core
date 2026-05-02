package io.streamvault.core.api.library.dto;

import io.streamvault.core.domain.library.Artist;

import java.util.UUID;

public record ArtistResponse(UUID id, String name) {
    public static ArtistResponse from(Artist a) {
        return new ArtistResponse(a.id, a.name);
    }
}
