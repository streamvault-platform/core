package io.streamvault.core.domain.library;

import io.smallrye.mutiny.Uni;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlbumRepository {
    Uni<Optional<Album>> findAlbumById(UUID id);
    Uni<Optional<Album>> findByTitleAndArtist(String title, UUID artistId);
    Uni<List<Album>> listAll(int page, int size);
    Uni<List<Album>> listByArtist(UUID artistId, int page, int size);
    Uni<List<Album>> search(String q, int page, int size);
    Uni<Long> countAll();
    Uni<Album> persist(Album album);
}
