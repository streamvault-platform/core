package io.streamvault.core.api.library.dto;

public record UpdateTrackMetadataRequest(
        String title,
        String genre,
        Integer year,
        Integer trackNumber,
        Integer discNumber
) {}
