package io.streamvault.core.application.watch;

import java.util.List;
import java.util.UUID;

public sealed interface WatchSyncError {
    record TrackNotFound(List<UUID> missingIds) implements WatchSyncError {}
    record TrackNotTranscoded(List<UUID> ids) implements WatchSyncError {}
    record SyncRequestNotFound(UUID syncRequestId) implements WatchSyncError {}
    record Forbidden() implements WatchSyncError {}
}
