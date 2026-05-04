package io.streamvault.core.api.playback.dto;

import java.util.UUID;

public record PlaybackEventMessage(String type, UUID trackId, long positionMs) {}
