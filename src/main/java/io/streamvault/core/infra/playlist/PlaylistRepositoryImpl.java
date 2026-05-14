package io.streamvault.core.infra.playlist;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.smallrye.mutiny.Uni;
import io.streamvault.core.domain.playlist.Playlist;
import io.streamvault.core.domain.playlist.PlaylistRepository;
import io.streamvault.core.domain.playlist.PlaylistTrack;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PlaylistRepositoryImpl implements PlaylistRepository, PanacheRepositoryBase<Playlist, UUID> {

    @Override
    public Uni<List<Playlist>> findByUserId(UUID userId) {
        return find(
                "SELECT DISTINCT p FROM Playlist p LEFT JOIN FETCH p.tracks WHERE p.userId = ?1 ORDER BY p.createdAt DESC",
                userId).list();
    }

    @Override
    public Uni<List<Playlist>> findAllPublic() {
        return find(
                "SELECT DISTINCT p FROM Playlist p LEFT JOIN FETCH p.tracks WHERE p.isPublic = true ORDER BY p.createdAt DESC"
        ).list();
    }

    @Override
    public Uni<Optional<Playlist>> findByIdWithTracks(UUID id) {
        return find(
                "SELECT DISTINCT p FROM Playlist p " +
                "LEFT JOIN FETCH p.tracks pt " +
                "LEFT JOIN FETCH pt.track t " +
                "LEFT JOIN FETCH t.artist " +
                "LEFT JOIN FETCH t.album " +
                "WHERE p.id = ?1",
                id).firstResult().map(Optional::ofNullable);
    }

    @Override
    public Uni<Optional<Playlist>> findByIdOptional(UUID id) {
        return find("id", id).firstResult().map(Optional::ofNullable);
    }

    @Override
    public Uni<Playlist> persist(Playlist playlist) {
        return persistAndFlush(playlist);
    }

    @Override
    public Uni<Long> deletePlaylist(UUID id) {
        return delete("id = ?1", id);
    }

    @Override
    public Uni<Optional<PlaylistTrack>> findPlaylistTrack(UUID playlistId, UUID trackId) {
        return Panache.getSession().flatMap(session ->
                session.createQuery(
                        "FROM PlaylistTrack pt JOIN FETCH pt.track WHERE pt.playlist.id = ?1 AND pt.track.id = ?2",
                        PlaylistTrack.class)
                        .setParameter(1, playlistId)
                        .setParameter(2, trackId)
                        .getResultList()
                        .map(list -> list.isEmpty() ? Optional.empty() : Optional.of(list.get(0))));
    }

    @Override
    public Uni<Long> deletePlaylistTrack(UUID playlistId, UUID trackId) {
        return Panache.getSession().flatMap(session ->
                session.createMutationQuery(
                        "DELETE FROM PlaylistTrack pt WHERE pt.playlist.id = ?1 AND pt.track.id = ?2")
                        .setParameter(1, playlistId)
                        .setParameter(2, trackId)
                        .executeUpdate()
                        .map(count -> (long) count));
    }

    @Override
    public Uni<PlaylistTrack> persistTrack(PlaylistTrack pt) {
        return Panache.getSession().flatMap(session -> session.persist(pt).replaceWith(pt));
    }
}
