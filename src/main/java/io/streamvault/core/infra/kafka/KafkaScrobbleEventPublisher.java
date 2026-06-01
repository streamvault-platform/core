package io.streamvault.core.infra.kafka;

import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.MutinyEmitter;
import io.streamvault.core.application.playback.ScrobbleEvent;
import io.streamvault.core.application.playback.ScrobbleEventPublisher;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class KafkaScrobbleEventPublisher implements ScrobbleEventPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaScrobbleEventPublisher.class);

    @Inject
    @Channel("scrobble-events")
    MutinyEmitter<ScrobbleEvent> emitter;

    @Override
    public Uni<Void> publish(ScrobbleEvent event) {
        LOG.debug("action=scrobble_publish userId={} trackId={} positionMs={}",
                event.userId(), event.trackId(), event.positionMs());
        return emitter.send(event)
                .invoke(() -> LOG.info("action=scrobble_publish_ok userId={} trackId={}",
                        event.userId(), event.trackId()))
                .onFailure().invoke(e -> LOG.error("action=scrobble_publish_failed userId={} trackId={}",
                        event.userId(), event.trackId(), e));
    }
}
