package io.streamvault.core.application.library;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import io.streamvault.core.application.storage.StorageBackend;
import io.streamvault.core.domain.library.LibraryError;
import io.streamvault.core.domain.library.Track;
import io.streamvault.core.domain.library.TrackRepository;
import io.vertx.mutiny.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.OffsetDateTime;
import java.util.UUID;

@ApplicationScoped
public class TrackManagementService {

    @Inject TrackRepository tracks;
    @Inject StorageBackend storage;
    @Inject Vertx vertx;

    public Uni<Track> updateMetadata(UUID trackId, String title, String genre, Integer year,
                                     Integer trackNumber, Integer discNumber,
                                     UUID callerId, String callerRole) {
        return Panache.withTransaction(() ->
                tracks.findTrackById(trackId)
                        .map(opt -> opt.orElseThrow(() -> new LibraryException(new LibraryError.TrackNotFound())))
                        .invoke(track -> {
                            checkOwnership(track, callerId, callerRole);
                            if (title != null) track.title = title;
                            if (genre != null) track.genre = genre;
                            if (year != null) track.year = year;
                            if (trackNumber != null) track.trackNumber = trackNumber;
                            if (discNumber != null) track.discNumber = discNumber;
                            track.updatedAt = OffsetDateTime.now();
                        })
                        .flatMap(tracks::update)
        );
    }

    public Uni<Void> deleteTrack(UUID trackId, UUID callerId, String callerRole) {
        return Panache.withTransaction(() ->
                tracks.findTrackById(trackId)
                        .map(opt -> opt.orElseThrow(() -> new LibraryException(new LibraryError.TrackNotFound())))
                        .invoke(track -> checkOwnership(track, callerId, callerRole))
                        .flatMap(track -> tracks.delete(track.id).replaceWith(track))
        )
        .flatMap(track -> vertx.executeBlocking(() -> {
            if (track.filePath != null) storage.delete(track.filePath);
            if (track.transcodedPath != null) storage.delete(track.transcodedPath);
            return null;
        }))
        .replaceWithVoid();
    }

    private void checkOwnership(Track track, UUID callerId, String callerRole) {
        if ("ADMIN".equals(callerRole)) return;
        UUID ownerId = track.owner != null ? track.owner.id : null;
        if (callerId.equals(ownerId)) return;
        throw new LibraryException(new LibraryError.Forbidden());
    }
}
