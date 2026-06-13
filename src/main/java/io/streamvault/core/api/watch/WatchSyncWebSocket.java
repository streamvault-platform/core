package io.streamvault.core.api.watch;

import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import io.streamvault.core.application.watch.WatchSyncConnectionRegistry;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;

import java.util.UUID;

@WebSocket(path = "/ws/watch-sync/{deviceId}")
@RolesAllowed("**")
public class WatchSyncWebSocket {

    private static final Logger LOG = Logger.getLogger(WatchSyncWebSocket.class);

    @Inject WebSocketConnection connection;
    @Inject JsonWebToken jwt;
    @Inject WatchSyncConnectionRegistry registry;

    @OnOpen
    public void onOpen() {
        UUID userId = UUID.fromString(jwt.getSubject());
        registry.register(connection.id(), userId);
        LOG.debugf("action=ws_watch_sync_open connectionId=%s userId=%s deviceId=%s",
                connection.id(), userId, connection.pathParam("deviceId"));
    }

    @OnClose
    public void onClose() {
        registry.unregister(connection.id());
        LOG.debugf("action=ws_watch_sync_close connectionId=%s", connection.id());
    }
}
