package io.streamvault.core.infra.kafka.deserializer;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;
import io.streamvault.core.application.watch.WatchSyncRequestedEvent;

public class WatchSyncRequestedEventDeserializer extends ObjectMapperDeserializer<WatchSyncRequestedEvent> {
    public WatchSyncRequestedEventDeserializer() {
        super(WatchSyncRequestedEvent.class);
    }
}
