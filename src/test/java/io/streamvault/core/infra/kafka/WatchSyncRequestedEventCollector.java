package io.streamvault.core.infra.kafka;

import io.streamvault.core.application.watch.WatchSyncRequestedEvent;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@ApplicationScoped
public class WatchSyncRequestedEventCollector {

    private final List<WatchSyncRequestedEvent> received = new CopyOnWriteArrayList<>();

    @Incoming("test-watch-sync-requested")
    public void collect(WatchSyncRequestedEvent event) {
        received.add(event);
    }

    public List<WatchSyncRequestedEvent> received() {
        return received;
    }

    public void clear() {
        received.clear();
    }
}
