package io.streamvault.core.application.playback;

import java.time.Instant;
import java.util.UUID;

public record ScrobbleEvent(UUID userId, UUID trackId, long positionMs, Instant occurredAt) {}
