package io.streamvault.core.infra.kafka;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import io.streamvault.core.application.pipeline.event.TranscodedEvent;
import io.streamvault.core.domain.library.Track;
import io.streamvault.core.domain.library.TrackRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

import java.time.OffsetDateTime;

@ApplicationScoped
public class TranscodedConsumer {

    private static final Logger LOG = Logger.getLogger(TranscodedConsumer.class);

    @Inject
    TrackRepository tracks;

    @Incoming("media-transcoded")
    public Uni<Void> consume(TranscodedEvent event) {
        return Panache.withTransaction(() ->
                tracks.findTrackById(event.trackId()).flatMap(opt -> {
                    if (opt.isEmpty()) {
                        LOG.warnf("action=transcoded_received result=ignored reason=track_not_found trackId=%s", event.trackId());
                        return Uni.createFrom().voidItem();
                    }
                    Track track = opt.get();
                    track.transcodedPath = event.transcodedPath();
                    track.transcodedMimeType = event.mimeType();
                    track.updatedAt = OffsetDateTime.now();
                    LOG.infof("action=transcoded_stored trackId=%s path=%s", track.id, track.transcodedPath);
                    return tracks.update(track).replaceWithVoid();
                }));
    }
}
