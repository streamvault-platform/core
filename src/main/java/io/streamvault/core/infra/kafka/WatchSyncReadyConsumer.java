package io.streamvault.core.infra.kafka;

import io.smallrye.mutiny.Uni;
import io.streamvault.core.application.watch.WatchSyncReadyEvent;
import io.streamvault.core.application.watch.WatchSyncService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class WatchSyncReadyConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(WatchSyncReadyConsumer.class);

    @Inject WatchSyncService watchSyncService;

    @Incoming("watch-sync-ready")
    public Uni<Void> consume(WatchSyncReadyEvent event) {
        LOG.debug("action=kafka_consume topic=watch.sync-ready syncRequestId={} deviceId={} manifestEntries={}",
                event.syncRequestId(), event.deviceId(), event.manifest().size());
        return watchSyncService.handleSyncReady(event)
                .onFailure().invoke(e -> LOG.error(
                        "action=watch_sync_ready_failed syncRequestId={}", event.syncRequestId(), e));
    }
}
