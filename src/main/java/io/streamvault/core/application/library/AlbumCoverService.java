package io.streamvault.core.application.library;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import io.streamvault.core.application.storage.StorageBackend;
import io.streamvault.core.domain.library.Album;
import io.streamvault.core.domain.library.AlbumRepository;
import io.streamvault.core.domain.library.LibraryError;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

@ApplicationScoped
public class AlbumCoverService {

    @Inject
    AlbumRepository albums;

    @Inject
    StorageBackend storage;

    public Uni<Album> uploadCover(UUID albumId, Path tempFile, String extension) {
        return Panache.withTransaction(() ->
                albums.findAlbumById(albumId)
                        .map(opt -> opt.orElseThrow(() -> new LibraryException(new LibraryError.AlbumNotFound())))
                        .invoke(album -> {
                            if (album.artworkPath != null) {
                                storage.delete(album.artworkPath);
                            }
                            album.artworkPath = storage.storeCoverArt(tempFile, albumId, extension);
                        })
                        .flatMap(albums::update)
        );
    }

    public Uni<String> getCoverPresignedUrl(UUID albumId) {
        return Panache.withTransaction(() -> albums.findAlbumById(albumId))
                .map(opt -> opt.orElseThrow(() -> new LibraryException(new LibraryError.AlbumNotFound())))
                .map(album -> {
                    if (album.artworkPath == null) {
                        throw new LibraryException(new LibraryError.AlbumNotFound());
                    }
                    return storage.presignDownload(album.artworkPath, Duration.ofHours(1));
                });
    }
}
