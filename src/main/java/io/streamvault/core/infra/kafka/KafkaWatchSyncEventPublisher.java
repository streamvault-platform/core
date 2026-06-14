package io.streamvault.core.infra.kafka;

import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.MutinyEmitter;
import io.streamvault.core.application.watch.WatchSyncEventPublisher;
import io.streamvault.core.application.watch.WatchSyncRequestedEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class KafkaWatchSyncEventPublisher implements WatchSyncEventPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaWatchSyncEventPublisher.class);

    @Inject
    @Channel("watch-sync-requested")
    MutinyEmitter<WatchSyncRequestedEvent> emitter;

    @Override
    public Uni<Void> publishWatchSyncRequested(WatchSyncRequestedEvent event) {
        LOG.debug("action=kafka_publish topic=watch.sync-requested syncRequestId={} deviceId={} trackCount={}",
                event.syncRequestId(), event.deviceId(), event.tracks().size());
        return emitter.send(event)
                .invoke(() -> LOG.info("action=kafka_publish_ok topic=watch.sync-requested syncRequestId={}", event.syncRequestId()))
                .onFailure().invoke(e -> LOG.error("action=kafka_publish_failed topic=watch.sync-requested syncRequestId={}", event.syncRequestId(), e));
    }
}
