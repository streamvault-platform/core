package io.streamvault.core.infra.kafka.deserializer;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;
import io.streamvault.core.application.watch.WatchSyncReadyEvent;

public class WatchSyncReadyEventDeserializer extends ObjectMapperDeserializer<WatchSyncReadyEvent> {
    public WatchSyncReadyEventDeserializer() {
        super(WatchSyncReadyEvent.class);
    }
}
