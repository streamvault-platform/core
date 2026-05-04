package io.streamvault.core.infra.playback;

import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.smallrye.mutiny.Uni;
import io.streamvault.core.domain.playback.PlaybackState;
import io.streamvault.core.domain.playback.PlaybackStateRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PlaybackStateRepositoryImpl implements PlaybackStateRepository, PanacheRepositoryBase<PlaybackState, UUID> {

    @Override
    public Uni<Optional<PlaybackState>> findByUserId(UUID userId) {
        return findById(userId).map(Optional::ofNullable);
    }

    @Override
    public Uni<PlaybackState> save(PlaybackState state) {
        return findById(state.userId)
                .flatMap(existing -> {
                    if (existing != null) {
                        existing.trackId = state.trackId;
                        existing.positionMs = state.positionMs;
                        existing.isPlaying = state.isPlaying;
                        existing.updatedAt = state.updatedAt;
                        return Uni.createFrom().item(existing);
                    }
                    return persist(state);
                });
    }
}
