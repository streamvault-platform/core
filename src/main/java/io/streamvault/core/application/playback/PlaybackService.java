package io.streamvault.core.application.playback;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import io.streamvault.core.domain.playback.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PlaybackService {

    @Inject PlaybackStateRepository playbackStates;
    @Inject TrackPositionRepository trackPositions;

    public Uni<Void> handleEvent(UUID userId, PlaybackEvent event) {
        return Panache.withTransaction(() -> switch (event) {
            case PlaybackEvent.Play e  -> persist(userId, e.trackId(), e.positionMs(), true);
            case PlaybackEvent.Pause e -> persist(userId, e.trackId(), e.positionMs(), false);
            case PlaybackEvent.Seek e  -> persist(userId, e.trackId(), e.positionMs(), true);
        });
    }

    public Uni<Optional<PlaybackState>> getState(UUID userId) {
        return Panache.withTransaction(() -> playbackStates.findByUserId(userId));
    }

    private Uni<Void> persist(UUID userId, UUID trackId, long positionMs, boolean isPlaying) {
        PlaybackState state = new PlaybackState();
        state.userId = userId;
        state.trackId = trackId;
        state.positionMs = positionMs;
        state.isPlaying = isPlaying;
        state.updatedAt = OffsetDateTime.now();

        return playbackStates.save(state)
                .flatMap(ignored -> updateTrackPosition(userId, trackId, positionMs))
                .replaceWithVoid();
    }

    private Uni<Void> updateTrackPosition(UUID userId, UUID trackId, long positionMs) {
        return trackPositions.findByUserAndTrack(userId, trackId)
                .flatMap(opt -> {
                    TrackPosition pos = opt.orElseGet(TrackPosition::new);
                    pos.userId = userId;
                    pos.trackId = trackId;
                    pos.positionMs = positionMs;
                    pos.updatedAt = OffsetDateTime.now();
                    return trackPositions.save(pos);
                })
                .replaceWithVoid();
    }
}
