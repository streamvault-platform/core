package io.streamvault.core.domain.watch;

import io.smallrye.mutiny.Uni;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WatchSyncRepository {
    Uni<WatchSyncRequest> persist(WatchSyncRequest request);
    Uni<Optional<WatchSyncRequest>> findByIdOptional(UUID id);
    Uni<List<WatchSyncRequest>> findByUserAndDevice(UUID userId, String deviceId);
    Uni<WatchSyncRequest> update(WatchSyncRequest request);
}
