package io.streamvault.core.infra.kafka;

import io.streamvault.core.application.pipeline.event.TrackUploadedEvent;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@ApplicationScoped
public class UploadedEventCollector {

    private final List<TrackUploadedEvent> received = new CopyOnWriteArrayList<>();

    @Incoming("test-media-uploaded")
    public void collect(TrackUploadedEvent event) {
        received.add(event);
    }

    public List<TrackUploadedEvent> received() {
        return received;
    }

    public void clear() {
        received.clear();
    }
}
