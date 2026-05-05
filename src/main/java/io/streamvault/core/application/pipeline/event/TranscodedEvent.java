package io.streamvault.core.application.pipeline.event;

import java.util.UUID;

public record TranscodedEvent(UUID trackId, String transcodedPath, String mimeType, long fileSizeBytes) {}
