package io.streamvault.core.infra.kafka.deserializer;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;
import io.streamvault.core.application.pipeline.event.TranscodedEvent;

public class TranscodedEventDeserializer extends ObjectMapperDeserializer<TranscodedEvent> {
    public TranscodedEventDeserializer() {
        super(TranscodedEvent.class);
    }
}
