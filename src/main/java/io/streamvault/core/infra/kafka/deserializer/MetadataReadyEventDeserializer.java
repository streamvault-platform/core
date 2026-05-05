package io.streamvault.core.infra.kafka.deserializer;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;
import io.streamvault.core.application.pipeline.event.MetadataReadyEvent;

public class MetadataReadyEventDeserializer extends ObjectMapperDeserializer<MetadataReadyEvent> {
    public MetadataReadyEventDeserializer() {
        super(MetadataReadyEvent.class);
    }
}
