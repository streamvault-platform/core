package io.streamvault.core.application.watch;

import java.util.UUID;

public interface WatchSyncNotifier {
    void notifyDevice(UUID userId, String deviceId, WatchSyncReadyEvent event);
}
