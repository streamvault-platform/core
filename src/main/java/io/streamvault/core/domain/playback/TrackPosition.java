package io.streamvault.core.domain.playback;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "track_positions")
public class TrackPosition {

    @Id
    @GeneratedValue
    public UUID id;

    @Column(name = "user_id", nullable = false)
    public UUID userId;

    @Column(name = "track_id", nullable = false)
    public UUID trackId;

    @Column(name = "position_ms", nullable = false)
    public long positionMs;

    @Column(name = "updated_at", nullable = false)
    public OffsetDateTime updatedAt;
}
