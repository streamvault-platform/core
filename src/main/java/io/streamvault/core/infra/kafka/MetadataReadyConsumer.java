package io.streamvault.core.infra.kafka;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import io.streamvault.core.application.pipeline.event.MetadataReadyEvent;
import io.streamvault.core.domain.library.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

import java.time.OffsetDateTime;

@ApplicationScoped
public class MetadataReadyConsumer {

    private static final Logger LOG = Logger.getLogger(MetadataReadyConsumer.class);

    @Inject TrackRepository tracks;
    @Inject ArtistRepository artists;
    @Inject AlbumRepository albums;

    @Incoming("media-metadata-ready")
    public Uni<Void> consume(MetadataReadyEvent event) {
        return Panache.withTransaction(() -> updateTrack(event));
    }

    private Uni<Void> updateTrack(MetadataReadyEvent event) {
        return tracks.findTrackById(event.trackId())
                .flatMap(opt -> {
                    if (opt.isEmpty()) {
                        LOG.warnf("Received media.metadata-ready for unknown track %s — ignoring", event.trackId());
                        return Uni.createFrom().voidItem();
                    }
                    Track track = opt.get();
                    return resolveArtist(event.artist())
                            .flatMap(artist -> resolveAlbum(event.album(), artist)
                                    .flatMap(album -> applyMetadata(track, event, artist, album)));
                });
    }

    private Uni<Void> applyMetadata(Track track, MetadataReadyEvent event, Artist artist, Album album) {
        if (event.title() != null)       track.title       = event.title();
        if (event.year() != null)        track.year        = event.year();
        if (event.trackNumber() != null) track.trackNumber = event.trackNumber();
        if (event.discNumber() != null)  track.discNumber  = event.discNumber();
        if (event.durationMs() != null)  track.durationMs  = event.durationMs();
        if (event.genre() != null)       track.genre       = event.genre();
        if (artist != null)              track.artist      = artist;
        if (album != null)               track.album       = album;
        track.updatedAt = OffsetDateTime.now();
        return tracks.update(track).replaceWithVoid();
    }

    private Uni<Artist> resolveArtist(String name) {
        if (name == null) return Uni.createFrom().nullItem();
        return artists.findByName(name).flatMap(opt -> {
            if (opt.isPresent()) return Uni.createFrom().item(opt.get());
            var a = new Artist();
            a.name = name;
            return artists.persist(a);
        });
    }

    private Uni<Album> resolveAlbum(String title, Artist artist) {
        if (title == null || artist == null) return Uni.createFrom().nullItem();
        return albums.findByTitleAndArtist(title, artist.id).flatMap(opt -> {
            if (opt.isPresent()) return Uni.createFrom().item(opt.get());
            var a = new Album();
            a.title = title;
            a.artist = artist;
            return albums.persist(a);
        });
    }
}
