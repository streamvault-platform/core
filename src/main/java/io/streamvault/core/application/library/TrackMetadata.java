package io.streamvault.core.application.library;

public record TrackMetadata(
        String filePath,
        String mimeType,
        long fileSize,
        String title
) {}
