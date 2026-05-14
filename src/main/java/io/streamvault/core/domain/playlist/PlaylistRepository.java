package io.streamvault.core.domain.playlist;

import io.smallrye.mutiny.Uni;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlaylistRepository {
    Uni<List<Playlist>> findByUserId(UUID userId);
    Uni<List<Playlist>> findAllPublic();
    Uni<Optional<Playlist>> findByIdWithTracks(UUID id);
    Uni<Optional<Playlist>> findByIdOptional(UUID id);
    Uni<Playlist> persist(Playlist playlist);
    Uni<Long> deletePlaylist(UUID id);
    Uni<Optional<PlaylistTrack>> findPlaylistTrack(UUID playlistId, UUID trackId);
    Uni<Long> deletePlaylistTrack(UUID playlistId, UUID trackId);
    Uni<PlaylistTrack> persistTrack(PlaylistTrack pt);
}
