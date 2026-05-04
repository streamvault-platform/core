package io.streamvault.core.api.playback;

import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
import io.smallrye.mutiny.Uni;
import io.streamvault.core.api.playback.dto.PlaybackEventMessage;
import io.streamvault.core.application.playback.PlaybackEvent;
import io.streamvault.core.application.playback.PlaybackService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.UUID;

@WebSocket(path = "/ws/playback")
@RolesAllowed("**")
public class PlaybackWebSocket {

    @Inject PlaybackService playbackService;
    @Inject JsonWebToken jwt;

    @OnTextMessage
    public Uni<Void> onMessage(PlaybackEventMessage msg) {
        UUID userId = UUID.fromString(jwt.getSubject());
        PlaybackEvent event = switch (msg.type()) {
            case "PLAY"  -> new PlaybackEvent.Play(msg.trackId(), msg.positionMs());
            case "PAUSE" -> new PlaybackEvent.Pause(msg.trackId(), msg.positionMs());
            case "SEEK"  -> new PlaybackEvent.Seek(msg.trackId(), msg.positionMs());
            default -> throw new IllegalArgumentException("Unknown event type: " + msg.type());
        };
        return playbackService.handleEvent(userId, event);
    }
}
