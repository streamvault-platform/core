package io.streamvault.core.infra.watch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.websockets.next.OpenConnections;
import io.streamvault.core.application.watch.WatchSyncConnectionRegistry;
import io.streamvault.core.application.watch.WatchSyncNotifier;
import io.streamvault.core.application.watch.WatchSyncReadyEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class WatchSyncNotifierImpl implements WatchSyncNotifier {

    private static final Logger LOG = Logger.getLogger(WatchSyncNotifierImpl.class);

    @Inject OpenConnections openConnections;
    @Inject WatchSyncConnectionRegistry registry;
    @Inject ObjectMapper objectMapper;

    @Override
    public void notifyDevice(UUID userId, String deviceId, WatchSyncReadyEvent event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(new SyncReadyNotification(
                    "SYNC_READY", event.syncRequestId(), event.deviceId(), event.manifest()));
        } catch (JsonProcessingException e) {
            LOG.warnf("action=ws_notify_failed reason=serialization syncRequestId=%s", event.syncRequestId());
            return;
        }

        openConnections.stream()
                .filter(c -> registry.isOwnedBy(c.id(), userId))
                .filter(c -> deviceId.equals(c.pathParam("deviceId")))
                .forEach(c -> c.sendText(payload)
                        .subscribe().with(
                                v -> LOG.debugf("action=ws_notify_ok syncRequestId=%s deviceId=%s", event.syncRequestId(), deviceId),
                                err -> LOG.warnf("action=ws_notify_failed syncRequestId=%s error=%s", event.syncRequestId(), err.getMessage())));
    }

    public record SyncReadyNotification(
            String type,
            UUID syncRequestId,
            String deviceId,
            List<WatchSyncReadyEvent.ManifestEntry> manifest
    ) {}
}
