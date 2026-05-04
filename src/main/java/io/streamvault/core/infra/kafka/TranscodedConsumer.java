package io.streamvault.core.infra.kafka;

import io.smallrye.mutiny.Uni;
import io.streamvault.core.application.pipeline.event.TranscodedEvent;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

@ApplicationScoped
public class TranscodedConsumer {

    private static final Logger LOG = Logger.getLogger(TranscodedConsumer.class);

    @Incoming("media-transcoded")
    public Uni<Void> consume(TranscodedEvent event) {
        // On-the-fly transcoding is post-MVP — serving originals for now.
        // This consumer exists so the pipeline can publish without error.
        LOG.infof("Track %s transcoded → %s (%s, %d bytes)",
                event.trackId(), event.transcodedPath(), event.mimeType(), event.fileSizeBytes());
        return Uni.createFrom().voidItem();
    }
}
