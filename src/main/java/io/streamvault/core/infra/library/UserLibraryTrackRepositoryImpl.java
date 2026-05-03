package io.streamvault.core.infra.library;

import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.smallrye.mutiny.Uni;
import io.streamvault.core.domain.library.UserLibraryTrack;
import io.streamvault.core.domain.library.UserLibraryTrackRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UserLibraryTrackRepositoryImpl
        implements UserLibraryTrackRepository, PanacheRepositoryBase<UserLibraryTrack, UUID> {

    @Override
    public Uni<List<UserLibraryTrack>> findByUserId(UUID userId, int page, int size) {
        return find("SELECT ult FROM UserLibraryTrack ult " +
                    "JOIN FETCH ult.track t " +
                    "LEFT JOIN FETCH t.artist " +
                    "LEFT JOIN FETCH t.album " +
                    "WHERE ult.userId = ?1", userId)
                .page(page, size)
                .list();
    }

    @Override
    public Uni<Optional<UserLibraryTrack>> findByUserAndTrack(UUID userId, UUID trackId) {
        return find("userId = ?1 and track.id = ?2", userId, trackId)
                .firstResult()
                .map(Optional::ofNullable);
    }

    @Override
    public Uni<UserLibraryTrack> persist(UserLibraryTrack entry) {
        return persistAndFlush(entry);
    }

    @Override
    public Uni<Long> deleteByUserAndTrack(UUID userId, UUID trackId) {
        return delete("userId = ?1 and track.id = ?2", userId, trackId);
    }
}
