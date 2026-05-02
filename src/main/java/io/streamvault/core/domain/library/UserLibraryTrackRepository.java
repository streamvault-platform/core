package io.streamvault.core.domain.library;

import io.smallrye.mutiny.Uni;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserLibraryTrackRepository {
    Uni<List<UserLibraryTrack>> findByUserId(UUID userId, int page, int size);
    Uni<Optional<UserLibraryTrack>> findByUserAndTrack(UUID userId, UUID trackId);
    Uni<UserLibraryTrack> persist(UserLibraryTrack entry);
    Uni<Long> deleteByUserAndTrack(UUID userId, UUID trackId);
}
