package io.streamvault.core.api.library.dto;

public record UpdateAlbumMetadataRequest(
        String title,
        Integer year
) {}
