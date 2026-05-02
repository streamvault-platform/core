package io.streamvault.core.infra.library;

import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.smallrye.mutiny.Uni;
import io.streamvault.core.domain.library.Track;
import io.streamvault.core.domain.library.TrackRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class TrackRepositoryImpl implements TrackRepository, PanacheRepositoryBase<Track, UUID> {

    @Override
    public Uni<Optional<Track>> findTrackById(UUID id) {
        return PanacheRepositoryBase.super.findById(id).map(Optional::ofNullable);
    }

    @Override
    public Uni<Optional<Track>> findByFilePath(String filePath) {
        return find("filePath", filePath).firstResult().map(Optional::ofNullable);
    }

    @Override
    public Uni<List<Track>> listAll(int page, int size) {
        return find("SELECT t FROM Track t LEFT JOIN FETCH t.artist LEFT JOIN FETCH t.album")
                .page(page, size).list();
    }

    @Override
    public Uni<Long> countAll() {
        return count();
    }

    @Override
    public Uni<Track> persist(Track track) {
        return persistAndFlush(track);
    }

    @Override
    public Uni<Track> update(Track track) {
        return persistAndFlush(track);
    }
}
