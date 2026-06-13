package io.streamvault.core.infra.watch;

import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.smallrye.mutiny.Uni;
import io.streamvault.core.domain.watch.WatchSyncRequest;
import io.streamvault.core.domain.watch.WatchSyncRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class WatchSyncRepositoryImpl implements WatchSyncRepository, PanacheRepositoryBase<WatchSyncRequest, UUID> {

    @Override
    public Uni<WatchSyncRequest> persist(WatchSyncRequest request) {
        return persistAndFlush(request);
    }

    @Override
    public Uni<Optional<WatchSyncRequest>> findByIdOptional(UUID id) {
        return find("id", id).firstResult().map(Optional::ofNullable);
    }

    @Override
    public Uni<List<WatchSyncRequest>> findByUserAndDevice(UUID userId, String deviceId) {
        return find("userId = ?1 AND deviceId = ?2 ORDER BY createdAt DESC", userId, deviceId).list();
    }

    @Override
    public Uni<WatchSyncRequest> update(WatchSyncRequest request) {
        return persistAndFlush(request);
    }
}
