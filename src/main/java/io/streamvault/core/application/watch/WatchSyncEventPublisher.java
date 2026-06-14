package io.streamvault.core.application.watch;

import io.smallrye.mutiny.Uni;

public interface WatchSyncEventPublisher {
    Uni<Void> publishWatchSyncRequested(WatchSyncRequestedEvent event);
}
