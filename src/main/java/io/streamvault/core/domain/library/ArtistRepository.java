package io.streamvault.core.domain.library;

import io.smallrye.mutiny.Uni;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ArtistRepository {
    Uni<Optional<Artist>> findArtistById(UUID id);
    Uni<Optional<Artist>> findByName(String name);
    Uni<List<Artist>> listAll(int page, int size);
    Uni<List<Artist>> search(String q, int page, int size);
    Uni<Long> countAll();
    Uni<Artist> persist(Artist artist);
}
