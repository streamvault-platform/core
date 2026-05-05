package io.streamvault.core.infra.kafka;

import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.MutinyEmitter;
import io.streamvault.core.application.pipeline.MediaEventPublisher;
import io.streamvault.core.application.pipeline.event.TrackUploadedEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;

@ApplicationScoped
public class KafkaMediaEventPublisher implements MediaEventPublisher {

    @Inject
    @Channel("media-uploaded")
    MutinyEmitter<TrackUploadedEvent> emitter;

    @Override
    public Uni<Void> publishTrackUploaded(TrackUploadedEvent event) {
        return emitter.send(event);
    }
}
