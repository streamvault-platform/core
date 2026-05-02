package io.streamvault.core.infra.library;

import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.smallrye.mutiny.Uni;
import io.streamvault.core.domain.library.Artist;
import io.streamvault.core.domain.library.ArtistRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ArtistRepositoryImpl implements ArtistRepository, PanacheRepositoryBase<Artist, UUID> {

    @Override
    public Uni<Optional<Artist>> findByName(String name) {
        return find("name", name).firstResult().map(Optional::ofNullable);
    }

    @Override
    public Uni<List<Artist>> listAll(int page, int size) {
        return findAll().page(page, size).list();
    }

    @Override
    public Uni<Long> countAll() {
        return count();
    }

    @Override
    public Uni<Artist> persist(Artist artist) {
        return persistAndFlush(artist);
    }
}
