package io.streamvault.core.application.library;

public record TrackMetadata(
        String filePath,
        String mimeType,
        long fileSize,
        String title,
        String artist,
        String album,
        Integer year,
        Integer trackNumber,
        Integer discNumber,
        Integer durationMs,
        String genre
) {}
