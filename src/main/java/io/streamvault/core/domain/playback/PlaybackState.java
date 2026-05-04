package io.streamvault.core.domain.playback;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "playback_state")
public class PlaybackState {

    @Id
    @Column(name = "user_id")
    public UUID userId;

    @Column(name = "track_id")
    public UUID trackId;

    @Column(name = "position_ms", nullable = false)
    public long positionMs;

    @Column(name = "is_playing", nullable = false)
    public boolean isPlaying;

    @Column(name = "updated_at", nullable = false)
    public OffsetDateTime updatedAt;
}
