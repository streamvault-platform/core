package io.streamvault.core.application.library;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import io.streamvault.core.domain.library.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class UserLibraryService {

    @Inject UserLibraryTrackRepository userLibrary;
    @Inject TrackRepository tracks;

    public Uni<List<UserLibraryTrack>> listLibrary(UUID userId, int page, int size) {
        return Panache.withTransaction(() -> userLibrary.findByUserId(userId, page, size));
    }

    public Uni<UserLibraryTrack> addTrack(UUID userId, UUID trackId) {
        return Panache.withTransaction(() ->
                userLibrary.findByUserAndTrack(userId, trackId).flatMap(existing -> {
                    if (existing.isPresent()) {
                        return Uni.createFrom().<UserLibraryTrack>failure(
                                new LibraryException(new LibraryError.AlreadyInLibrary()));
                    }
                    return tracks.findTrackById(trackId).flatMap(opt -> {
                        if (opt.isEmpty()) {
                            return Uni.createFrom().<UserLibraryTrack>failure(
                                    new LibraryException(new LibraryError.TrackNotFound()));
                        }
                        var entry = new UserLibraryTrack();
                        entry.userId = userId;
                        entry.track = opt.get();
                        return userLibrary.persist(entry);
                    });
                }));
    }

    public Uni<Void> removeTrack(UUID userId, UUID trackId) {
        return Panache.withTransaction(() ->
                userLibrary.deleteByUserAndTrack(userId, trackId).flatMap(deleted -> {
                    if (deleted == 0) {
                        return Uni.createFrom().failure(
                                new LibraryException(new LibraryError.NotInLibrary()));
                    }
                    return Uni.createFrom().voidItem();
                }));
    }
}
