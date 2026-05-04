package io.streamvault.core.domain.playback;

import io.smallrye.mutiny.Uni;

import java.util.Optional;
import java.util.UUID;

public interface TrackPositionRepository {
    Uni<Optional<TrackPosition>> findByUserAndTrack(UUID userId, UUID trackId);
    Uni<TrackPosition> save(TrackPosition position);
}
