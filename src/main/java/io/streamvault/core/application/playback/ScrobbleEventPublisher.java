package io.streamvault.core.application.playback;

import io.smallrye.mutiny.Uni;

public interface ScrobbleEventPublisher {
    Uni<Void> publish(ScrobbleEvent event);
}
