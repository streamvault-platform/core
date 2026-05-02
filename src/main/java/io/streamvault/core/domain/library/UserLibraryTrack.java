package io.streamvault.core.domain.library;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_library_tracks")
public class UserLibraryTrack {

    @Id
    @GeneratedValue
    public UUID id;

    @Column(name = "user_id", nullable = false)
    public UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "track_id", nullable = false)
    public Track track;

    @Column(name = "added_at", nullable = false, updatable = false)
    public OffsetDateTime addedAt = OffsetDateTime.now();
}
