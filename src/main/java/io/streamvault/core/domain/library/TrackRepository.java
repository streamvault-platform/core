package io.streamvault.core.domain.library;

import io.smallrye.mutiny.Uni;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrackRepository {
    Uni<Optional<Track>> findTrackById(UUID id);
    Uni<Optional<Track>> findByFilePath(String filePath);
    Uni<List<Track>> listAll(int page, int size);
    Uni<Long> countAll();
    Uni<Track> persist(Track track);
    Uni<Track> update(Track track);
}
