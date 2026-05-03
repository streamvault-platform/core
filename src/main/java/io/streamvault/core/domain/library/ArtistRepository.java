package io.streamvault.core.domain.library;

import io.smallrye.mutiny.Uni;

import java.util.List;
import java.util.Optional;

public interface ArtistRepository {
    Uni<Optional<Artist>> findByName(String name);
    Uni<List<Artist>> listAll(int page, int size);
    Uni<Long> countAll();
    Uni<Artist> persist(Artist artist);
}
