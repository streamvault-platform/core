package io.streamvault.core.infra.library;

import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.smallrye.mutiny.Uni;
import io.streamvault.core.domain.library.Album;
import io.streamvault.core.domain.library.AlbumRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class AlbumRepositoryImpl implements AlbumRepository, PanacheRepositoryBase<Album, UUID> {

    @Override
    public Uni<Optional<Album>> findByTitleAndArtist(String title, UUID artistId) {
        return find("title = ?1 and artist.id = ?2", title, artistId)
                .firstResult()
                .map(Optional::ofNullable);
    }

    @Override
    public Uni<List<Album>> listAll(int page, int size) {
        return find("SELECT a FROM Album a LEFT JOIN FETCH a.artist")
                .page(page, size).list();
    }

    @Override
    public Uni<Long> countAll() {
        return count();
    }

    @Override
    public Uni<Album> persist(Album album) {
        return persistAndFlush(album);
    }
}
