package io.streamvault.core.infra.kafka;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import io.streamvault.core.application.pipeline.event.TranscodedEvent;
import io.streamvault.core.domain.library.Track;
import io.streamvault.core.domain.library.TrackRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;

@ApplicationScoped
public class TranscodedConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(TranscodedConsumer.class);

    @Inject
    TrackRepository tracks;

    @Incoming("media-transcoded")
    public Uni<Void> consume(TranscodedEvent event) {
        LOG.debug("action=kafka_consume topic=media.transcoded trackId={} transcodedPath={} mimeType={} sizeBytes={}",
                event.trackId(), event.transcodedPath(), event.mimeType(), event.fileSizeBytes());
        return Panache.withTransaction(() ->
                tracks.findTrackById(event.trackId()).flatMap(opt -> {
                    if (opt.isEmpty()) {
                        LOG.warn("action=transcoded_received result=ignored reason=track_not_found trackId={}", event.trackId());
                        return Uni.createFrom().voidItem();
                    }
                    Track track = opt.get();
                    track.transcodedPath = event.transcodedPath();
                    track.transcodedMimeType = event.mimeType();
                    track.updatedAt = OffsetDateTime.now();
                    LOG.info("action=transcoded_stored trackId={} path={}", track.id, track.transcodedPath);
                    return tracks.update(track).replaceWithVoid();
                }));
    }
}
