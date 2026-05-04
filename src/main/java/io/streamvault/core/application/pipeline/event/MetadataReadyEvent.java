package io.streamvault.core.application.pipeline.event;

import java.util.UUID;

public record MetadataReadyEvent(
        UUID trackId,
        String title,
        String artist,
        String album,
        Integer year,
        Integer trackNumber,
        Integer discNumber,
        Integer durationMs,
        String genre) {}
