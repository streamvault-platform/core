package io.streamvault.core.application.playlist;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import io.streamvault.core.api.playlist.dto.ReorderItem;
import io.streamvault.core.domain.library.TrackRepository;
import io.streamvault.core.domain.playlist.Playlist;
import io.streamvault.core.domain.playlist.PlaylistError;
import io.streamvault.core.domain.playlist.PlaylistRepository;
import io.streamvault.core.domain.playlist.PlaylistTrack;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class PlaylistService {

    @Inject PlaylistRepository playlistRepo;
    @Inject TrackRepository tracks;

    public Uni<List<Playlist>> list(UUID userId) {
        return Panache.withTransaction(() -> playlistRepo.findByUserId(userId));
    }

    public Uni<List<Playlist>> listPublic() {
        return Panache.withTransaction(() -> playlistRepo.findAllPublic());
    }

    public Uni<Playlist> get(UUID userId, UUID playlistId) {
        return Panache.withTransaction(() ->
                playlistRepo.findByIdWithTracks(playlistId).flatMap(opt -> {
                    if (opt.isEmpty()) {
                        return Uni.createFrom().failure(
                                new PlaylistException(new PlaylistError.PlaylistNotFound()));
                    }
                    var playlist = opt.get();
                    if (!playlist.userId.equals(userId) && !playlist.isPublic) {
                        return Uni.createFrom().failure(
                                new PlaylistException(new PlaylistError.Forbidden()));
                    }
                    return Uni.createFrom().item(playlist);
                }));
    }

    public Uni<Playlist> create(UUID userId, String ownerName, String name) {
        return Panache.withTransaction(() -> {
            var playlist = new Playlist();
            playlist.userId = userId;
            playlist.ownerName = ownerName;
            playlist.name = name;
            return playlistRepo.persist(playlist);
        });
    }

    public Uni<Playlist> rename(UUID userId, UUID playlistId, String name) {
        return Panache.withTransaction(() ->
                playlistRepo.findByIdWithTracks(playlistId).flatMap(opt -> {
                    if (opt.isEmpty()) {
                        return Uni.createFrom().failure(
                                new PlaylistException(new PlaylistError.PlaylistNotFound()));
                    }
                    var playlist = opt.get();
                    if (!playlist.userId.equals(userId)) {
                        return Uni.createFrom().failure(
                                new PlaylistException(new PlaylistError.Forbidden()));
                    }
                    playlist.name = name;
                    playlist.updatedAt = OffsetDateTime.now();
                    return playlistRepo.persist(playlist);
                }));
    }

    public Uni<Void> setVisibility(UUID userId, UUID playlistId, boolean isPublic) {
        return Panache.withTransaction(() ->
                playlistRepo.findByIdOptional(playlistId).flatMap(opt -> {
                    if (opt.isEmpty()) {
                        return Uni.createFrom().failure(
                                new PlaylistException(new PlaylistError.PlaylistNotFound()));
                    }
                    var playlist = opt.get();
                    if (!playlist.userId.equals(userId)) {
                        return Uni.createFrom().failure(
                                new PlaylistException(new PlaylistError.Forbidden()));
                    }
                    playlist.isPublic = isPublic;
                    playlist.updatedAt = OffsetDateTime.now();
                    return playlistRepo.persist(playlist).replaceWithVoid();
                }));
    }

    public Uni<Playlist> copy(UUID userId, String ownerName, UUID playlistId) {
        return Panache.withTransaction(() ->
                playlistRepo.findByIdWithTracks(playlistId).flatMap(opt -> {
                    if (opt.isEmpty()) {
                        return Uni.createFrom().failure(
                                new PlaylistException(new PlaylistError.PlaylistNotFound()));
                    }
                    var source = opt.get();
                    if (!source.isPublic && !source.userId.equals(userId)) {
                        return Uni.createFrom().failure(
                                new PlaylistException(new PlaylistError.Forbidden()));
                    }
                    var copy = new Playlist();
                    copy.userId = userId;
                    copy.ownerName = ownerName;
                    copy.name = source.name + " (copy)";
                    copy.isPublic = false;
                    return playlistRepo.persist(copy).flatMap(savedPlaylist -> {
                        Uni<Void> chain = Uni.createFrom().voidItem();
                        for (var sourcePt : source.tracks) {
                            var newPt = new PlaylistTrack();
                            newPt.playlist = savedPlaylist;
                            newPt.track = sourcePt.track;
                            newPt.position = sourcePt.position;
                            savedPlaylist.tracks.add(newPt);
                            final var pt = newPt;
                            chain = chain.flatMap(ignored -> playlistRepo.persistTrack(pt).replaceWithVoid());
                        }
                        return chain.replaceWith(savedPlaylist);
                    });
                }));
    }

    public Uni<Void> delete(UUID userId, UUID playlistId) {
        return Panache.withTransaction(() ->
                playlistRepo.findByIdOptional(playlistId).flatMap(opt -> {
                    if (opt.isEmpty()) {
                        return Uni.createFrom().failure(
                                new PlaylistException(new PlaylistError.PlaylistNotFound()));
                    }
                    var playlist = opt.get();
                    if (!playlist.userId.equals(userId)) {
                        return Uni.createFrom().failure(
                                new PlaylistException(new PlaylistError.Forbidden()));
                    }
                    return playlistRepo.deletePlaylist(playlistId).map(v -> null);
                }));
    }

    public Uni<PlaylistTrack> addTrack(UUID userId, UUID playlistId, UUID trackId) {
        return Panache.withTransaction(() ->
                playlistRepo.findByIdWithTracks(playlistId).flatMap(opt -> {
                    if (opt.isEmpty()) {
                        return Uni.createFrom().failure(
                                new PlaylistException(new PlaylistError.PlaylistNotFound()));
                    }
                    var playlist = opt.get();
                    if (!playlist.userId.equals(userId)) {
                        return Uni.createFrom().failure(
                                new PlaylistException(new PlaylistError.Forbidden()));
                    }
                    if (playlist.tracks.stream().anyMatch(pt -> pt.track.id.equals(trackId))) {
                        return Uni.createFrom().failure(
                                new PlaylistException(new PlaylistError.TrackAlreadyInPlaylist()));
                    }
                    return tracks.findTrackById(trackId).flatMap(opt2 -> {
                        if (opt2.isEmpty()) {
                            return Uni.createFrom().failure(
                                    new PlaylistException(new PlaylistError.TrackNotFound()));
                        }
                        var track = opt2.get();
                        int position = playlist.tracks.stream()
                                .mapToInt(pt -> pt.position)
                                .max()
                                .orElse(-1) + 1;
                        var pt = new PlaylistTrack();
                        pt.playlist = playlist;
                        pt.track = track;
                        pt.position = position;
                        playlist.updatedAt = OffsetDateTime.now();
                        return playlistRepo.persistTrack(pt);
                    });
                }));
    }

    public Uni<Void> removeTrack(UUID userId, UUID playlistId, UUID trackId) {
        return Panache.withTransaction(() ->
                playlistRepo.findByIdOptional(playlistId).flatMap(opt -> {
                    if (opt.isEmpty()) {
                        return Uni.createFrom().failure(
                                new PlaylistException(new PlaylistError.PlaylistNotFound()));
                    }
                    var playlist = opt.get();
                    if (!playlist.userId.equals(userId)) {
                        return Uni.createFrom().failure(
                                new PlaylistException(new PlaylistError.Forbidden()));
                    }
                    return playlistRepo.deletePlaylistTrack(playlistId, trackId).flatMap(deleted -> {
                        if (deleted == 0) {
                            return Uni.createFrom().failure(
                                    new PlaylistException(new PlaylistError.TrackNotInPlaylist()));
                        }
                        playlist.updatedAt = OffsetDateTime.now();
                        return playlistRepo.persist(playlist).map(v -> null);
                    });
                }));
    }

    public Uni<Void> reorder(UUID userId, UUID playlistId, List<ReorderItem> items) {
        return Panache.withTransaction(() ->
                playlistRepo.findByIdWithTracks(playlistId).flatMap(opt -> {
                    if (opt.isEmpty()) {
                        return Uni.createFrom().failure(
                                new PlaylistException(new PlaylistError.PlaylistNotFound()));
                    }
                    var playlist = opt.get();
                    if (!playlist.userId.equals(userId)) {
                        return Uni.createFrom().failure(
                                new PlaylistException(new PlaylistError.Forbidden()));
                    }
                    Map<UUID, PlaylistTrack> trackMap = new java.util.HashMap<>();
                    for (var pt : playlist.tracks) {
                        trackMap.put(pt.track.id, pt);
                    }
                    for (var item : items) {
                        var pt = trackMap.get(item.trackId());
                        if (pt != null) {
                            pt.position = item.position();
                        }
                    }
                    playlist.updatedAt = OffsetDateTime.now();
                    return playlistRepo.persist(playlist).map(v -> null);
                }));
    }
}
