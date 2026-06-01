package io.streamvault.core.infra.kafka.deserializer;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;
import io.streamvault.core.application.playback.ScrobbleEvent;

public class ScrobbleEventDeserializer extends ObjectMapperDeserializer<ScrobbleEvent> {
    public ScrobbleEventDeserializer() {
        super(ScrobbleEvent.class);
    }
}
