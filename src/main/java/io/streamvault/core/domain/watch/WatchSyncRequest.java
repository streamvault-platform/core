package io.streamvault.core.domain.watch;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "watch_sync_requests")
public class WatchSyncRequest {

    @Id
    @GeneratedValue
    public UUID id;

    @Column(name = "user_id", nullable = false)
    public UUID userId;

    @Column(name = "device_id", nullable = false, length = 255)
    public String deviceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "watch_sync_status")
    public WatchSyncStatus status = WatchSyncStatus.PENDING;

    @Column(name = "track_ids", nullable = false, columnDefinition = "TEXT")
    public String trackIds;

    @Column(columnDefinition = "TEXT")
    public String manifest;

    @Column(name = "created_at", nullable = false, updatable = false)
    public OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    public OffsetDateTime updatedAt = OffsetDateTime.now();
}
