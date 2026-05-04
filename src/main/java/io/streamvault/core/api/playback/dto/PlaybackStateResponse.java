package io.streamvault.core.api.playback.dto;

import io.streamvault.core.domain.playback.PlaybackState;

import java.util.UUID;

public record PlaybackStateResponse(UUID trackId, long positionMs, boolean isPlaying) {

    public static PlaybackStateResponse from(PlaybackState state) {
        return new PlaybackStateResponse(state.trackId, state.positionMs, state.isPlaying);
    }
}
