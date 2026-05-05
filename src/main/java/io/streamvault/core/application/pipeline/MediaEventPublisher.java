package io.streamvault.core.application.pipeline;

import io.smallrye.mutiny.Uni;
import io.streamvault.core.application.pipeline.event.TrackUploadedEvent;

public interface MediaEventPublisher {
    Uni<Void> publishTrackUploaded(TrackUploadedEvent event);
}
