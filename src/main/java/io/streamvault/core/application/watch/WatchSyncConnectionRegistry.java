package io.streamvault.core.application.watch;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class WatchSyncConnectionRegistry {

    private final ConcurrentHashMap<String, UUID> connectionUser = new ConcurrentHashMap<>();

    public void register(String connectionId, UUID userId) {
        connectionUser.put(connectionId, userId);
    }

    public void unregister(String connectionId) {
        connectionUser.remove(connectionId);
    }

    public boolean isOwnedBy(String connectionId, UUID userId) {
        return userId.equals(connectionUser.get(connectionId));
    }
}
