package io.streamvault.core.domain.playback;

import io.smallrye.mutiny.Uni;

import java.util.Optional;
import java.util.UUID;

public interface PlaybackStateRepository {
    Uni<Optional<PlaybackState>> findByUserId(UUID userId);
    Uni<PlaybackState> save(PlaybackState state);
}
