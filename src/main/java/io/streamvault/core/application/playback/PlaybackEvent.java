package io.streamvault.core.application.playback;

import java.util.UUID;

public sealed interface PlaybackEvent {
    record Play(UUID trackId, long positionMs) implements PlaybackEvent {}
    record Pause(UUID trackId, long positionMs) implements PlaybackEvent {}
    record Seek(UUID trackId, long positionMs) implements PlaybackEvent {}
    record Heartbeat(UUID trackId, long positionMs) implements PlaybackEvent {}
}
