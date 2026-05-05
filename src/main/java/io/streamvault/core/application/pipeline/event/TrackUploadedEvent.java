package io.streamvault.core.application.pipeline.event;

import java.util.UUID;

public record TrackUploadedEvent(UUID trackId, String filePath, String mimeType, String originalFilename) {}
