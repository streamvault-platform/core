package io.streamvault.core.infra.kafka;

import io.streamvault.core.application.playback.ScrobbleEvent;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@ApplicationScoped
public class ScrobbleEventCollector {

    private final List<ScrobbleEvent> received = new CopyOnWriteArrayList<>();

    @Incoming("test-scrobble-events")
    public void collect(ScrobbleEvent event) {
        received.add(event);
    }

    public List<ScrobbleEvent> received() {
        return received;
    }

    public void clear() {
        received.clear();
    }
}
