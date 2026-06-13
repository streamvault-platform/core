package io.streamvault.core.application.watch;

import java.util.List;
import java.util.UUID;

public record WatchSyncReadyEvent(
        UUID syncRequestId,
        UUID userId,
        String deviceId,
        List<ManifestEntry> manifest
) {
    public record ManifestEntry(UUID trackId, String downloadUrl, long fileSizeBytes) {}
}
