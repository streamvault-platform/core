package io.streamvault.core.infra.playback;

import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.smallrye.mutiny.Uni;
import io.streamvault.core.domain.playback.TrackPosition;
import io.streamvault.core.domain.playback.TrackPositionRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class TrackPositionRepositoryImpl implements TrackPositionRepository, PanacheRepositoryBase<TrackPosition, UUID> {

    @Override
    public Uni<Optional<TrackPosition>> findByUserAndTrack(UUID userId, UUID trackId) {
        return find("userId = ?1 AND trackId = ?2", userId, trackId)
                .firstResult().map(Optional::ofNullable);
    }

    @Override
    public Uni<TrackPosition> save(TrackPosition position) {
        return persist(position);
    }
}
