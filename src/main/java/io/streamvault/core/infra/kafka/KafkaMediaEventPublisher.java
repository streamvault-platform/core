package io.streamvault.core.infra.kafka;

import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.MutinyEmitter;
import io.streamvault.core.application.pipeline.MediaEventPublisher;
import io.streamvault.core.application.pipeline.event.TrackUploadedEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class KafkaMediaEventPublisher implements MediaEventPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaMediaEventPublisher.class);

    @Inject
    @Channel("media-uploaded")
    MutinyEmitter<TrackUploadedEvent> emitter;

    @ConfigProperty(name = "kafka.bootstrap.servers", defaultValue = "unknown")
    String bootstrapServers;

    @Override
    public Uni<Void> publishTrackUploaded(TrackUploadedEvent event) {
        LOG.debug("action=kafka_publish topic=media.uploaded bootstrap={} trackId={} mimeType={} filename={} downloadUrl={} uploadUrl={} transcodedStoredPath={}",
                bootstrapServers, event.trackId(), event.mimeType(), event.originalFilename(),
                event.downloadUrl(), event.uploadUrl(), event.transcodedStoredPath());
        return emitter.send(event)
                .invoke(() -> LOG.info("action=kafka_publish_ok topic=media.uploaded bootstrap={} trackId={}", bootstrapServers, event.trackId()))
                .onFailure().invoke(e -> LOG.error("action=kafka_publish_failed topic=media.uploaded bootstrap={} trackId={}", bootstrapServers, event.trackId(), e));
    }
}
