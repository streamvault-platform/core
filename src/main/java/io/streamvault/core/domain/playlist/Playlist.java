package io.streamvault.core.domain.playlist;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "playlists")
public class Playlist {

    @Id
    @GeneratedValue
    public UUID id;

    @Column(name = "user_id", nullable = false)
    public UUID userId;

    @Column(length = 255, nullable = false)
    public String name;

    @Column(name = "is_public", nullable = false)
    public boolean isPublic = false;

    @Column(name = "owner_name", nullable = false, length = 50)
    public String ownerName = "";

    @OneToMany(mappedBy = "playlist", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    public List<PlaylistTrack> tracks = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    public OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    public OffsetDateTime updatedAt = OffsetDateTime.now();
}
