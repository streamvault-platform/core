package io.streamvault.core.application.watch;

import java.util.List;
import java.util.UUID;

public record WatchSyncRequestedEvent(
        UUID syncRequestId,
        UUID userId,
        String deviceId,
        List<TrackInfo> tracks
) {
    public record TrackInfo(UUID trackId, String downloadUrl) {}
}
