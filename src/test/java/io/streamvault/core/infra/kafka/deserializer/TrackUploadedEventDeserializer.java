package io.streamvault.core.infra.kafka.deserializer;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;
import io.streamvault.core.application.pipeline.event.TrackUploadedEvent;

public class TrackUploadedEventDeserializer extends ObjectMapperDeserializer<TrackUploadedEvent> {
    public TrackUploadedEventDeserializer() {
        super(TrackUploadedEvent.class);
    }
}
