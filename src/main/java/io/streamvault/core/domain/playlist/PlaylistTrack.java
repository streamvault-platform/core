package io.streamvault.core.domain.playlist;

import io.streamvault.core.domain.library.Track;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "playlist_tracks")
public class PlaylistTrack {

    @Id
    @GeneratedValue
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "playlist_id", nullable = false)
    public Playlist playlist;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "track_id", nullable = false)
    public Track track;

    @Column(nullable = false)
    public int position;

    @Column(name = "added_at", nullable = false, updatable = false)
    public OffsetDateTime addedAt = OffsetDateTime.now();
}
