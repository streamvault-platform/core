package io.streamvault.core.application.library;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import io.streamvault.core.application.storage.StorageBackend;
import io.streamvault.core.domain.library.Album;
import io.streamvault.core.domain.library.AlbumRepository;
import io.streamvault.core.domain.library.LibraryError;
import io.vertx.mutiny.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.UUID;

@ApplicationScoped
public class AlbumManagementService {

    @Inject AlbumRepository albums;
    @Inject StorageBackend storage;
    @Inject Vertx vertx;

    public Uni<Album> updateMetadata(UUID albumId, String title, Integer year,
                                     UUID callerId, String callerRole) {
        return Panache.withTransaction(() ->
                albums.findAlbumById(albumId)
                        .map(opt -> opt.orElseThrow(() -> new LibraryException(new LibraryError.AlbumNotFound())))
                        .invoke(album -> {
                            checkOwnership(album, callerId, callerRole);
                            if (title != null) album.title = title;
                            if (year != null) album.year = year;
                        })
                        .flatMap(albums::update)
        );
    }

    public Uni<Void> deleteAlbum(UUID albumId, UUID callerId, String callerRole) {
        return Panache.withTransaction(() ->
                albums.findAlbumById(albumId)
                        .map(opt -> opt.orElseThrow(() -> new LibraryException(new LibraryError.AlbumNotFound())))
                        .invoke(album -> checkOwnership(album, callerId, callerRole))
                        .flatMap(album -> albums.delete(album.id).replaceWith(album))
        )
        .flatMap(album -> vertx.executeBlocking(() -> {
            if (album.artworkPath != null) storage.delete(album.artworkPath);
            return null;
        }))
        .replaceWithVoid();
    }

    private void checkOwnership(Album album, UUID callerId, String callerRole) {
        if ("ADMIN".equals(callerRole)) return;
        UUID ownerId = album.owner != null ? album.owner.id : null;
        if (callerId.equals(ownerId)) return;
        throw new LibraryException(new LibraryError.Forbidden());
    }
}
